package org.runelite.mobile.host;

import android.util.Log;

import org.runelite.mobile.bridge.AWTBridge;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Boots the real RuneLite client runtime (plugin manager, event bus, config manager,
 * overlay manager and the {@code Hooks} Callbacks implementation) on top of the already
 * running game client.
 *
 * <p>Why this is reflection-heavy: RuneLite and its runtime libraries (Guice, OkHttp,
 * Gson, http-api, ...) all live in {@code assets/runelite-dex.jar}, loaded through the
 * child {@code DexClassLoader} created by {@code MainActivity.bootstrapGameClient}. The
 * app dex (this class) cannot see them, so everything RuneLite-specific -- including the
 * injector itself -- is reached by name through that loader, exactly like the existing
 * {@code Callbacks} proxy does. The split is deliberate: RuneLite's classes, its
 * dependencies and the host shims share one loader, so Guice can resolve every type in
 * the shims' and plugins' signatures (e.g. {@code net.runelite.api.Client}) instead of
 * failing on a type that only the other dex knows.
 *
 * <p>The sequence reproduces the subset of {@code net.runelite.client.RuneLite.start()}
 * that matters here, in RuneLite's own order. Skipped on purpose: session loading (the
 * Jagex account flow is MainActivity's), the updater, external/hub plugins (see
 * {@link MobilePluginHub}), the client-session websocket, Discord RPC, telemetry, the
 * Swing {@code ClientUI} and {@code client.unblockStartup()} (the client is already
 * running when this host starts).
 */
public final class RuneLiteHost {

    public static final String TAG = "RuneLiteHost";
    public static final String INDEX_RESOURCE = "runelite-plugin-index.txt";

    private static final Object LOCK = new Object();
    private static volatile Object injector;
    private static volatile ClassLoader clientLoader;
    private static volatile Object client;
    private static volatile Object hooks;
    private static volatile Object pluginManager;
    private static volatile Object configManager;
    private static volatile Object eventBus;
    private static volatile Object overlayManager;
    private static volatile Object clientThread;
    private static volatile Throwable failure;
    private static volatile String status = "not started";
    private static volatile int indexSize;
    private static volatile int activePlugins;
    private static volatile String clientVersion = "?";
    private static volatile android.content.Context appContext;
    private static final List<String> pluginFailures = new ArrayList<>();

    private RuneLiteHost() {
    }

    /** True once the injector and the plugin lifecycle are up. */
    public static boolean isRunning() {
        return injector != null && failure == null;
    }

    /** The startup failure, or null. Surfaced in the side panel's Host tab. */
    public static Throwable failure() {
        return failure;
    }

    public static String status() {
        return status;
    }

    /** Application context, needed to find {@code files/plugins} for sideloaded plugins. */
    public static void setAppContext(android.content.Context context) {
        appContext = context == null ? null : context.getApplicationContext();
    }

    public static void setClientVersion(String version) {
        clientVersion = version == null ? "?" : version;
    }

    public static String clientVersion() {
        return clientVersion;
    }

    public static int indexSize() {
        return indexSize;
    }

    public static int activePluginCount() {
        return activePlugins;
    }

    /** How many loaded plugins are actually running ({@code PluginManager.isPluginActive}). */
    private static int countActivePlugins() {
        int active = 0;
        for (Object plugin : plugins()) {
            try {
                if (Boolean.TRUE.equals(findMethod(pluginManager.getClass(), "isPluginActive", 1)
                        .invoke(pluginManager, plugin))) {
                    active++;
                }
            } catch (Throwable ignored) {
                // plugin not queryable: not counted
            }
        }
        return active;
    }

    /** Plugins that could not be instantiated, as {@code fqcn: reason}. */
    public static synchronized List<String> pluginFailures() {
        return new ArrayList<>(pluginFailures);
    }

    /** The {@code net.runelite.api.hooks.Callbacks} implementation (RuneLite's Hooks). */
    public static Object hooks() {
        return hooks;
    }

    public static Object pluginManager() {
        return pluginManager;
    }

    public static Object configManager() {
        return configManager;
    }

    public static Object eventBus() {
        return eventBus;
    }

    public static Object overlayManager() {
        return overlayManager;
    }

    public static ClassLoader clientLoader() {
        return clientLoader;
    }

    /**
     * Starts the RuneLite runtime. Idempotent; must be called from a background thread
     * (it performs one blocking HTTP fetch before handing the lifecycle to the UI
     * thread, because {@code PluginManager} insists plugins start on the event dispatch
     * thread, which on this port is the Android main thread).
     */
    public static void start(Object gameClient, ClassLoader loader) {
        synchronized (LOCK) {
            if (injector != null || failure != null) {
                Log.i(TAG, "start() ignored: " + status);
                return;
            }
            status = "starting";
        }
        client = gameClient;
        clientLoader = loader;
        try {
            // OkHttpClient: same shape as RuneLite.buildHttpClient().
            Class<?> okHttpBuilderClass = loader.loadClass("okhttp3.OkHttpClient$Builder");
            Object okHttpBuilder = okHttpBuilderClass.getConstructor().newInstance();
            okHttpBuilderClass.getMethod("connectTimeout", long.class, TimeUnit.class)
                .invoke(okHttpBuilder, 20L, TimeUnit.SECONDS);
            okHttpBuilderClass.getMethod("readTimeout", long.class, TimeUnit.class)
                .invoke(okHttpBuilder, 20L, TimeUnit.SECONDS);
            Object okHttp = okHttpBuilderClass.getMethod("build").invoke(okHttpBuilder);
            Class<?> okHttpClass = okHttp.getClass();
            // The http-api module and every RuneLite HTTP client share this instance.
            loader.loadClass("net.runelite.http.api.RuneLiteAPI").getField("CLIENT").set(null, okHttp);

            // RuntimeConfigLoader.get() does a blocking GET of runelite.config; the
            // constructor itself is cheap. A null result (offline) is fine: the runtime
            // config is only used for feature flags and external plugin settings.
            Class<?> runtimeConfigLoaderClass = loader.loadClass("net.runelite.client.RuntimeConfigLoader");
            Object runtimeConfigLoader = runtimeConfigLoaderClass
                .getConstructor(okHttpClass).newInstance(okHttp);
            try {
                Object config = runtimeConfigLoaderClass.getMethod("get").invoke(runtimeConfigLoader);
                Log.i(TAG, "runtime config: " + (config == null ? "unavailable (offline)" : "ok"));
            } catch (Throwable t) {
                Log.w(TAG, "runtime config fetch failed: " + t);
            }

            Class<?> runeLiteClass = loader.loadClass("net.runelite.client.RuneLite");
            File runeLiteDir = (File) runeLiteClass.getField("RUNELITE_DIR").get(null);
            File sessionFile = new File(runeLiteDir, "session");
            Log.i(TAG, "runeLiteDir=" + runeLiteDir.getAbsolutePath());

            Class<?> moduleClass = loader.loadClass("net.runelite.client.RuneLiteModule");
            Constructor<?> moduleCtor = moduleClass.getConstructor(
                okHttpClass, Supplier.class, runtimeConfigLoaderClass,
                boolean.class, boolean.class, boolean.class, File.class, String.class,
                boolean.class, boolean.class);
            Supplier<Object> clientSupplier = () -> client;
            Object module = moduleCtor.newInstance(okHttp, clientSupplier, runtimeConfigLoader,
                false /* developerMode */, false /* safeMode */, true /* disableTelemetry */,
                sessionFile, null /* profile */, false /* insecureWriteCredentials */, true /* noupdate */);

            // Guice.createInjector(RuneLiteModule) with the module array built through the
            // child loader, because com.google.inject.Module is an asset-dex type now.
            Class<?> guiceClass = loader.loadClass("com.google.inject.Guice");
            Class<?> moduleInterface = loader.loadClass("com.google.inject.Module");
            Object moduleArray = java.lang.reflect.Array.newInstance(moduleInterface, 1);
            java.lang.reflect.Array.set(moduleArray, 0, module);
            Object guice = guiceClass.getMethod("createInjector", moduleArray.getClass())
                .invoke(null, moduleArray);
            injector = guice;
            // PluginManager.instantiate() builds child injectors through this static field.
            runeLiteClass.getMethod("setInjector", loader.loadClass("com.google.inject.Injector"))
                .invoke(null, guice);
            // Deliberately NOT injector.injectMembers(client): RuneLite.start() does that
            // *before* client.initialize(), and on this port the client is already wired
            // by MainActivity (the Callbacks proxy, the ScheduledExecutorService and the
            // OtlTokenRequester). Injecting now would replace the client's Callbacks field
            // with Hooks directly, which bypasses the proxy's frame blit and leaves the
            // render thread waiting on frameSeq forever -- a black screen with a live
            // runtime.
            installNavigationHook(loader);
            Log.i(TAG, "injector ok");

            hooks = get("net.runelite.api.hooks.Callbacks");
            pluginManager = get("net.runelite.client.plugins.PluginManager");
            configManager = get("net.runelite.client.config.ConfigManager");
            eventBus = get("net.runelite.client.eventbus.EventBus");
            overlayManager = get("net.runelite.client.ui.overlay.OverlayManager");
            try {
                clientThread = get("net.runelite.client.callback.ClientThread");
            } catch (Throwable t) {
                Log.w(TAG, "ClientThread unavailable: " + t);
            }
            status = "injector ok; starting plugins";
        } catch (Throwable t) {
            failure = unwrap(t);
            status = "FAILED: " + failure;
            Log.e(TAG, "RuneLite host bootstrap failed", failure);
            return;
        }

        // Plugin lifecycle on the UI thread: PluginManager asserts the EDT and uses
        // SwingUtilities.invokeAndWait internally, and our SwingUtilities routes both to
        // the main thread (see AWTBridge.registerUiThread).
        AWTBridge.post(RuneLiteHost::startPlugins);
    }

    private static void startPlugins() {
        try {
            System.setProperty("jagex.disableBouncyCastle", "true");
            System.setProperty("runelite.pluginhub.version", clientVersion);

            invoke(configManager, "load");
            Log.i(TAG, "config loaded from " + clientVersion + " profile");

            List<Class<?>> index = loadPluginIndex();
            indexSize = index.size();
            Log.i(TAG, "plugin index: " + indexSize + " classes");

            // PluginManager.loadPlugins instantiates every class through a Guice child
            // injector and lets a PluginInstantiationException escape, so one plugin whose
            // injected types cannot be resolved would take the whole runtime down. Bulk
            // load first (that is what builds RuneLite's @PluginDependency ordering), and
            // fall back to one-by-one so a single bad plugin is logged and skipped.
            try {
                invoke(pluginManager, "loadPlugins",
                    new Class<?>[]{List.class, java.util.function.BiConsumer.class}, index, null);
            } catch (Throwable t) {
                Log.w(TAG, "bulk plugin load failed: " + unwrap(t) + "; retrying one by one");
                int ok = 0;
                for (Class<?> candidate : index) {
                    try {
                        invoke(pluginManager, "loadPlugins",
                            new Class<?>[]{List.class, java.util.function.BiConsumer.class},
                            Collections.singletonList(candidate), null);
                        ok++;
                    } catch (Throwable perPlugin) {
                        pluginFailures.add(candidate.getName() + ": " + unwrap(perPlugin));
                        Log.e(TAG, "plugin load failed: " + candidate.getName(), unwrap(perPlugin));
                    }
                }
                Log.i(TAG, "loaded " + ok + "/" + index.size() + " plugins individually");
            }
            invoke(pluginManager, "loadDefaultPluginConfiguration",
                new Class<?>[]{java.util.Collection.class}, plugins());
            Log.i(TAG, "plugins loaded: " + plugins().size() + " ("
                + pluginFailures.size() + " failed)");

            // EventBus.register takes a single subscriber (not varargs).
            invoke(eventBus, "register", new Class<?>[]{Object.class}, pluginManager);
            invoke(eventBus, "register", new Class<?>[]{Object.class}, overlayManager);
            invoke(eventBus, "register", new Class<?>[]{Object.class}, configManager);
            invoke(overlayManager, "init");
            Log.i(TAG, "overlays registered");

            invoke(pluginManager, "startPlugins");
            activePlugins = countActivePlugins();
            status = "running: " + activePlugins + "/" + plugins().size() + " plugin(s) active";
            Log.i(TAG, "RuneLite host running: " + activePlugins + " plugin(s) active");

            // Sideloaded Plugin Hub plugins (raw jars dexed on the device, or pre-dexed on
            // the host). Empty directory = no-op.
            android.content.Context ctx = appContext;
            if (ctx != null) {
                int hub = MobilePluginHub.loadPlugins(ctx, clientLoader);
                if (hub > 0) {
                    status = "running: " + activePlugins + " plugin(s) active, " + hub + " sideloaded";
                }
            }

            // One-shot proof that the java.awt stubs the overlays draw through actually
            // work on this device (shapes, alpha blending, the opaque frame blit, text).
            // Cheap enough to always run, and it is the only automated check of the draw
            // surface that exists on a release build.
            String gfxFailure = GraphicsSelfTest.run();
            if (gfxFailure != null) {
                status = status + "; gfx self-test FAIL: " + gfxFailure;
            }
        } catch (Throwable t) {
            failure = unwrap(t);
            status = "FAILED: " + failure;
            Log.e(TAG, "plugin lifecycle failed", failure);
        }
    }

    /** Reads the plugin class list the build wrote into the asset dex. */
    private static List<Class<?>> loadPluginIndex() {
        List<Class<?>> out = new ArrayList<>();
        try (InputStream in = clientLoader.getResourceAsStream(INDEX_RESOURCE)) {
            if (in == null) {
                Log.e(TAG, "resource " + INDEX_RESOURCE + " not found on the client loader");
                return out;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String name = line.trim();
                    if (name.isEmpty() || name.startsWith("#")) {
                        continue;
                    }
                    try {
                        out.add(clientLoader.loadClass(name));
                    } catch (Throwable t) {
                        Log.w(TAG, "plugin class failed to load: " + name, t);
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "plugin index read failed", t);
        }
        return out;
    }

    /**
     * Hands extra plugin classes (dexed Plugin Hub jars) to the running PluginManager and
     * starts the ones whose config is enabled. Must be called on the UI thread.
     */
    public static int loadAdditionalPlugins(List<Class<?>> classes) {
        if (pluginManager == null || classes == null || classes.isEmpty()) {
            return 0;
        }
        try {
            List<Object> before = plugins();
            invoke(pluginManager, "loadPlugins",
                new Class<?>[]{List.class, java.util.function.BiConsumer.class}, classes, null);
            invoke(pluginManager, "loadDefaultPluginConfiguration",
                new Class<?>[]{java.util.Collection.class}, plugins());
            // Start only what just arrived: PluginManager.startPlugins() starts every
            // enabled plugin, and re-running it here would also (re)start plugins the
            // boot sequence had already decided about.
            int started = 0;
            for (Object plugin : plugins()) {
                if (before.contains(plugin)) {
                    continue;
                }
                boolean enabled = isPluginEnabled(plugin);
                if (enabled) {
                    try {
                        findMethod(pluginManager.getClass(), "startPlugin", 1).invoke(pluginManager, plugin);
                    } catch (Throwable t) {
                        Log.e(TAG, "could not start sideloaded plugin " + pluginName(plugin), unwrap(t));
                    }
                } else {
                    Log.i(TAG, "sideloaded plugin " + pluginName(plugin)
                        + " is disabled; enable it in the side panel");
                }
                if (Boolean.TRUE.equals(findMethod(pluginManager.getClass(), "isPluginActive", 1)
                        .invoke(pluginManager, plugin))) {
                    started++;
                }
            }
            activePlugins = countActivePlugins();
            Log.i(TAG, "sideloaded plugins loaded: " + classes.size() + ", active: " + started);
            return classes.size();
        } catch (Throwable t) {
            Log.e(TAG, "loading additional plugins failed", unwrap(t));
            return 0;
        }
    }

    /** All loaded plugins ({@code PluginManager.getPlugins()}), empty when not running. */
    @SuppressWarnings("unchecked")
    public static List<Object> plugins() {
        Object pm = pluginManager;
        if (pm == null) {
            return Collections.emptyList();
        }
        try {
            Object result = invoke(pm, "getPlugins");
            if (result instanceof java.util.Collection) {
                return new ArrayList<>((java.util.Collection<Object>) result);
            }
        } catch (Throwable t) {
            Log.w(TAG, "getPlugins failed", t);
        }
        return Collections.emptyList();
    }

    /**
     * Flushes pending config changes to disk.
     *
     * <p>RuneLite only writes the profile file from {@code ConfigManager.sendConfig()},
     * which it runs from a {@code scheduleWithFixedDelay} task (minutes) and on a profile
     * switch -- on the desktop the process exits gracefully and the periodic flush has
     * usually already run. Android force-stops backgrounded apps without warning, so
     * without an explicit flush a plugin enablement or config edit made in the side panel
     * is lost on the next launch.
     */
    public static void flushConfig() {
        Object manager = configManager;
        if (manager == null) {
            return;
        }
        try {
            invoke(manager, "sendConfig");
        } catch (Throwable t) {
            Log.w(TAG, "config flush failed", unwrap(t));
        }
    }

    /** Enables/disables a plugin; must be called on the UI thread. */
    public static boolean setPluginEnabled(Object plugin, boolean enabled) {
        Object pm = pluginManager;
        if (pm == null || plugin == null) {
            return false;
        }
        try {
            findMethod(pm.getClass(), "setPluginEnabled", 2).invoke(pm, plugin, enabled);
            if (enabled) {
                findMethod(pm.getClass(), "startPlugin", 1).invoke(pm, plugin);
            } else {
                findMethod(pm.getClass(), "stopPlugin", 1).invoke(pm, plugin);
            }
        } catch (Throwable t) {
            Log.w(TAG, (enabled ? "enable" : "disable") + " failed for " + pluginName(plugin), t);
            return false;
        }
        activePlugins = countActivePlugins();
        flushConfig();
        return true;
    }

    public static boolean isPluginEnabled(Object plugin) {
        Object pm = pluginManager;
        if (pm == null || plugin == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(findMethod(pm.getClass(), "isPluginEnabled", 1).invoke(pm, plugin));
        } catch (Throwable t) {
            return false;
        }
    }

    /** The plugin's {@code @PluginDescriptor(name)} or its simple class name. */
    public static String pluginName(Object plugin) {
        if (plugin == null) {
            return "?";
        }
        try {
            Class<?> descriptorClass = clientLoader.loadClass("net.runelite.client.plugins.PluginDescriptor");
            Object descriptor = plugin.getClass().getAnnotation((Class) descriptorClass);
            if (descriptor != null) {
                Object name = descriptor.getClass().getMethod("name").invoke(descriptor);
                if (name instanceof String && !((String) name).isEmpty()) {
                    return (String) name;
                }
            }
        } catch (Throwable ignored) {
            // fall through to the simple class name
        }
        return plugin.getClass().getSimpleName();
    }

    /**
     * The config interface a plugin uses, or {@code null} when it has none.
     *
     * <p>RuneLite does not expose a plugin's config through a method on the plugin: the
     * {@code @ConfigGroup}-annotated interface is the type of an injected field (or the
     * return type of a helper method, e.g. {@code getConfig(ConfigManager)}). The
     * annotation is RUNTIME-retained and TYPE-targeted, so the type alone identifies it.
     */
    public static Class<?> pluginConfigClass(Object plugin) {
        if (plugin == null) {
            return null;
        }
        Class<? extends java.lang.annotation.Annotation> group;
        try {
            group = clientLoader().loadClass("net.runelite.client.config.ConfigGroup")
                .asSubclass(java.lang.annotation.Annotation.class);
        } catch (Throwable t) {
            return null;
        }
        for (Class<?> c = plugin.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (f.getType().isAnnotationPresent(group)) {
                    return f.getType();
                }
            }
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() <= 1 && m.getReturnType().isAnnotationPresent(group)) {
                    return m.getReturnType();
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ reflection
    /** An instance of the named RuneLite class from the injector. */
    public static Object get(String className) throws Exception {
        Class<?> type = clientLoader.loadClass(className);
        return injector.getClass().getMethod("getInstance", Class.class).invoke(injector, type);
    }

    /**
     * Wires {@code ClientToolbar}'s static navigation hook to
     * {@link PluginPanelRegistry}: the toolbar shim lives in the RuneLite dex, the
     * registry in the app dex, so the connection is a field write plus a lambda.
     */
    @SuppressWarnings("unchecked")
    private static void installNavigationHook(ClassLoader loader) {
        try {
            Class<?> toolbarClass = loader.loadClass("net.runelite.client.ui.ClientToolbar");
            java.lang.reflect.Field hook = toolbarClass.getField("navigationListener");
            java.util.function.BiConsumer<String, Object> listener = (action, button) -> {
                if ("add".equals(action)) {
                    PluginPanelRegistry.add(button);
                } else if ("remove".equals(action)) {
                    PluginPanelRegistry.remove(button);
                } else {
                    PluginPanelRegistry.open(button);
                }
            };
            hook.set(null, listener);
            Log.i(TAG, "toolbar navigation hook installed");
        } catch (Throwable t) {
            Log.w(TAG, "toolbar navigation hook not installed", t);
        }
    }

    private static Method findMethod(Class<?> owner, String name, int paramCount) throws NoSuchMethodException {
        for (Method m : owner.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        throw new NoSuchMethodException(name + "/" + paramCount + " on " + owner.getName());
    }

    /** {@code target.name(args...)} with no arguments. */
    private static Object invoke(Object target, String name) throws Exception {
        Method m = findMethod(target.getClass(), name, 0);
        return m.invoke(target);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getMethod(name, types);
        return m.invoke(target, args);
    }

    private static Throwable unwrap(Throwable t) {
        if (t instanceof InvocationTargetException && t.getCause() != null) {
            return unwrap(t.getCause());
        }
        return t;
    }
}
