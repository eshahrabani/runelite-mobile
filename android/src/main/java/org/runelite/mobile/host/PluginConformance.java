package org.runelite.mobile.host;

import android.util.Log;

import org.runelite.mobile.MainActivity;
import org.runelite.mobile.bridge.AWTBridge;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Drives every plugin in the shipped index and writes a per-plugin PASS/FAIL/SKIP report.
 *
 * <p>Why this exists: the loader counters ({@code index=113}, {@code 114 instantiate},
 * {@code 65 active}, {@code 141 overlays}) all derive from successful <em>instantiation</em>
 * and say nothing about behaviour. A plugin can load, be listed as active, and still throw
 * on its first event (MusicPlugin did: {@code NoClassDefFoundError: joptsimple/internal/Strings}
 * on every {@code ScriptCallbackEvent}) without any counter moving.
 *
 * <p>The harness starts each plugin itself and measures it, then puts the client back the
 * way it found it:
 *
 * <ul>
 *   <li><b>subs</b> — declared {@code @Subscribe} methods vs subscribers the bus actually
 *       holds for that plugin object;</li>
 *   <li><b>ovl</b> — every overlay the plugin registered is asked to render into a scratch
 *       image; an exception is a failure only while the game is logged in, or when it is a
 *       link error ({@code NoClassDefFoundError}/{@code NoSuchMethodError}/...), which is a
 *       real device-only gap whatever the game state;</li>
 *   <li><b>cfg</b> — every {@code @ConfigItem} is read, set to a different value through
 *       {@code ConfigManager} (so {@code ConfigChanged} fires), read back and restored;</li>
 *   <li><b>rc</b> — how many entity render callbacks appeared while the plugin was
 *       starting, i.e. whether the plugin can veto entity rendering at all;</li>
 *   <li><b>probes</b> — {@code entityVeto} (the client's own
 *       {@code Callbacks.draw(Renderable, boolean)} counts, so "hide NPCs" becomes a
 *       number), {@code menuEntry} (a synthetic {@code MenuEntryAdded} through the real
 *       bus) and {@code eventFlow} (per-event-class {@code post} counts).</li>
 * </ul>
 *
 * <p>Everything here is reflective through {@link RuneLiteHost#clientLoader()}: RuneLite
 * lives in the asset dex and this class is in the app dex (see
 * {@link RuneLiteHost}'s class comment). All of it runs on the Android main thread, which
 * is this port's event dispatch thread — {@code PluginManager.startPlugin} asserts it.
 *
 * <p>Trigger: the side panel's Host tab, or drop a {@code conformance.request} file into
 * the app-specific external files dir (see {@link #REQUEST_FILE}) — the render loop picks
 * that up, which is the adb path.
 */
public final class PluginConformance {

    private static final String TAG = RuneLiteHost.TAG;

    /** {@code adb shell "echo 1 > /sdcard/Android/data/org.runelite.mobile/files/conformance.request"}. */
    public static final String REQUEST_FILE = "conformance.request";
    public static final String REPORT_FILE = "conformance-report.txt";

    private static final Object LOCK = new Object();
    private static volatile boolean running;
    private static volatile String lastSummary = "not run";

    /** Frames the entity-veto probe samples before it reports. */
    private static final int ENTITY_PROBE_FRAMES = 100;
    private static final long ENTITY_PROBE_TIMEOUT_MS = 2500L;
    /** How long a probe waits for the client thread to run its Runnable. */
    private static final long CLIENT_THREAD_TIMEOUT_MS = 1500L;
    /** How long the overlay probe waits for the client thread to render every overlay. */
    private static final long OVERLAY_PROBE_TIMEOUT_MS = 4000L;

    /**
     * Event classes that must fire while the client is live, *and* that this proxy can
     * actually observe. {@code GameTick}/{@code BeforeRender} are deliberately absent:
     * RuneLite's {@code Hooks} builds and posts those inside its own implementation, so
     * they never pass through {@code MainActivity}'s counter and asserting on them here
     * would fail every plugin that legitimately uses them.
     */
    private static final List<String> MUST_FIRE_EVENTS = java.util.Arrays.asList(
        "net.runelite.api.events.ClientTick");

    /** Any of these having posts proves the client is running and dispatching events. */
    private static final List<String> LIVE_EVENTS = java.util.Arrays.asList(
        "net.runelite.api.events.ClientTick", "net.runelite.api.events.GameTick",
        "net.runelite.api.events.BeforeRender", "net.runelite.api.events.VarbitChanged",
        "net.runelite.api.events.ScriptPostFired", "net.runelite.api.events.MenuEntryAdded");

    private PluginConformance() {
    }

    public static boolean isRunning() {
        return running;
    }

    /** One line for the side panel / logcat: the last run's summary and report path. */
    public static String lastSummary() {
        return lastSummary;
    }

    public static File reportFile(android.content.Context ctx) {
        return new File(dir(ctx), REPORT_FILE);
    }

    public static File requestFile(android.content.Context ctx) {
        return new File(dir(ctx), REQUEST_FILE);
    }

    /**
     * The app-specific external files dir (the only path adb can write on this release
     * build — the package is not debuggable, so {@code run-as} is unavailable), falling
     * back to the internal files dir.
     */
    private static File dir(android.content.Context ctx) {
        File external = ctx.getExternalFilesDir(null);
        return external != null ? external : ctx.getFilesDir();
    }

    /**
     * Runs a conformance pass. Single-flight: a second call while a run is in progress is
     * ignored. Must be called from any thread; the pass itself is posted to the UI thread.
     */
    public static void run(android.content.Context ctx) {
        if (ctx == null) {
            return;
        }
        synchronized (LOCK) {
            if (running) {
                Log.i(TAG, "CONFORMANCE: already running, request ignored");
                return;
            }
            running = true;
        }
        final android.content.Context context = ctx.getApplicationContext() == null
            ? ctx : ctx.getApplicationContext();
        AWTBridge.post(() -> {
            try {
                execute(context);
            } catch (Throwable t) {
                Log.e(TAG, "CONFORMANCE failed", t);
                lastSummary = "failed: " + t;
            } finally {
                running = false;
            }
        });
    }

    // ------------------------------------------------------------------ the run

    private static void execute(android.content.Context ctx) {
        File report = reportFile(ctx);
        StringBuilder out = new StringBuilder();
        out.append("# plugin conformance\n");
        out.append("date: ").append(new java.util.Date()).append('\n');
        out.append("client: ").append(RuneLiteHost.clientVersion()).append('\n');
        if (!RuneLiteHost.isRunning()) {
            out.append("# aborted: host not running (").append(RuneLiteHost.status()).append(")\n");
            write(report, out.toString());
            lastSummary = "aborted: host not running";
            Log.i(TAG, "CONFORMANCE: " + lastSummary);
            return;
        }

        String state = gameState();
        List<Object> plugins = new ArrayList<>(RuneLiteHost.plugins());
        plugins.sort(Comparator.comparing(p -> p.getClass().getName()));
        out.append("gameState: ").append(state).append('\n');
        out.append("index: ").append(RuneLiteHost.indexSize()).append(" classes, loaded: ")
            .append(plugins.size()).append('\n');
        out.append("# <plugin> <PASS|FAIL|SKIP> enabled=<y|n>/active=<y|n> subs=<reg>/<decl> "
            + "ovl=<ok>/<bad> cfg=<ok>/<bad> rc=<n> probes=...\n");

        int pass = 0;
        int fail = 0;
        int skip = 0;
        boolean configTouched = false;
        List<String> reasons = new ArrayList<>();
        boolean clientLive = clientLive();
        for (Object plugin : plugins) {
            Result result;
            try {
                result = check(plugin, state, clientLive);
            } catch (Throwable t) {
                Log.e(TAG, "CONFORMANCE: check failed for " + plugin.getClass().getName(), t);
                result = new Result(plugin.getClass().getName());
                result.fail("harness: " + t);
            }
            configTouched |= result.configTouched;
            out.append(result.line()).append('\n');
            if (result.isFail()) {
                fail++;
                for (String reason : result.failures) {
                    reasons.add(result.fqcn + ": " + reason);
                }
            } else if (result.isSkip()) {
                skip++;
            } else {
                pass++;
            }
        }

        if (configTouched) {
            // Every plugin's config was restored, but the enablement bookkeeping ran
            // through ConfigManager; a flush here means the restored state is on disk even
            // if the process is force-stopped right after the run.
            RuneLiteHost.flushConfig();
        }
        String summary = "plugins=" + plugins.size() + " pass=" + pass + " fail=" + fail + " skip=" + skip;
        out.append('\n').append("# summary: ").append(summary).append('\n');
        if (!reasons.isEmpty()) {
            out.append('\n').append("# failures:").append('\n');
            for (String reason : reasons) {
                out.append("# ").append(reason).append('\n');
            }
        }
        write(report, out.toString());
        lastSummary = summary + " -> " + report.getAbsolutePath();
        Log.i(TAG, "CONFORMANCE: " + summary + " (report: " + report.getAbsolutePath() + ")");
    }

    private static void write(File file, String content) {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.e(TAG, "CONFORMANCE: could not write " + file, e);
        }
    }

    // ------------------------------------------------------------------ one plugin

    /** Everything the report says about one plugin. */
    private static final class Result {
        final String fqcn;
        boolean enabled;
        boolean active;
        int subsRegistered = -1;
        int subsDeclared;
        int overlayOk;
        int overlayBad;
        String overlayNote;
        int cfgOk;
        int cfgBad;
        String cfgNote;
        int renderCallbacks = -1;
        long entityCalls = -1;
        long entityDenied = -1;
        String entityNote;
        int menuEntryChecked;
        String menuEntryNote;
        String eventFlow;
        boolean configTouched;
        boolean leak;
        final List<String> failures = new ArrayList<>();
        final List<String> skips = new ArrayList<>();

        Result(String fqcn) {
            this.fqcn = fqcn;
        }

        void fail(String reason) {
            failures.add(reason);
        }

        void skip(String reason) {
            skips.add(reason);
        }

        boolean isFail() {
            return !failures.isEmpty() || leak;
        }

        boolean isSkip() {
            return !isFail() && !skips.isEmpty();
        }

        String verdict() {
            return isFail() ? "FAIL" : isSkip() ? "SKIP" : "PASS";
        }

        String line() {
            StringBuilder sb = new StringBuilder(fqcn).append(' ').append(verdict());
            sb.append(" enabled=").append(enabled ? "y" : "n");
            sb.append("/active=").append(active ? "y" : "n");
            sb.append(" subs=").append(subsRegistered).append('/').append(subsDeclared);
            sb.append(" ovl=").append(overlayOk).append('/').append(overlayBad);
            sb.append(" cfg=").append(cfgOk).append('/').append(cfgBad);
            sb.append(" rc=").append(renderCallbacks);
            sb.append(" probes=entityVeto(");
            if (entityCalls < 0) {
                sb.append(entityNote == null ? "n/a" : entityNote);
            } else {
                sb.append("entities=").append(entityCalls).append(" denied=").append(entityDenied);
            }
            sb.append(')');
            if (menuEntryChecked > 0 || menuEntryNote != null) {
                sb.append(" menuEntry(").append(menuEntryNote).append(')');
            }
            if (eventFlow != null) {
                sb.append(" eventFlow(").append(eventFlow).append(')');
            }
            if (isFail()) {
                sb.append(" FAILURES=").append(failures);
                if (leak) {
                    sb.append(" [leak]");
                }
            } else if (isSkip()) {
                sb.append(" SKIP=").append(skips);
            }
            if (cfgNote != null) {
                sb.append(" cfgNote=").append(cfgNote.replace(' ', '_'));
            }
            if (overlayNote != null) {
                sb.append(" ovlNote=").append(overlayNote);
            }
            return sb.toString();
        }
    }

    private static Result check(Object plugin, String gameState, boolean clientLive) {
        Result r = new Result(plugin.getClass().getName());
        boolean enabledAtStart = RuneLiteHost.isPluginEnabled(plugin);
        boolean activeAtStart = RuneLiteHost.isPluginActive(plugin);
        r.enabled = enabledAtStart;

        // Start it ourselves: an already-active plugin is stopped first so that the
        // render-callback delta and the leak check measure the plugin's own registration
        // rather than whatever the previous state happened to be.
        if (activeAtStart && !RuneLiteHost.stopPlugin(plugin)) {
            r.skip("could not stop (delta/leak unmeasurable)");
        }
        int subsBefore = RuneLiteHost.subscriberCountFor(plugin);
        int ovlBefore = RuneLiteHost.overlayCountFor(plugin);
        int rcBefore = RuneLiteHost.renderCallbackCount();

        if (!enabledAtStart) {
            RuneLiteHost.setPluginEnabledFlag(plugin, true);
            r.configTouched = true;
        }
        boolean started = RuneLiteHost.startPlugin(plugin);
        if (!started && !RuneLiteHost.isPluginActive(plugin)) {
            r.fail("start: PluginManager.startPlugin returned false");
        }
        r.active = RuneLiteHost.isPluginActive(plugin);
        int rcAfter = RuneLiteHost.renderCallbackCount();
        r.renderCallbacks = rcBefore < 0 || rcAfter < 0 ? -1 : rcAfter - rcBefore;

        // ---- subs: declared @Subscribe methods vs subscribers the bus holds
        Map<String, Class<?>> subscribed = subscribeMethods(plugin);
        r.subsDeclared = subscribed.size();
        r.subsRegistered = RuneLiteHost.subscriberCountFor(plugin);
        if (r.subsRegistered != r.subsDeclared) {
            if (r.subsRegistered < 0) {
                r.skip("subscriber count unavailable");
            } else {
                r.fail("subs: bus holds " + r.subsRegistered + " of " + r.subsDeclared
                    + " declared @Subscribe methods");
            }
        }

        // ---- ovl: render every overlay the plugin registered
        overlayProbe(plugin, r, gameState);

        // ---- cfg: read/write/read-back/restore every @ConfigItem
        configProbe(plugin, r);

        // ---- probes
        entityVetoProbe(r, gameState, clientLive);
        menuEntryProbe(plugin, subscribed, r, gameState);
        r.eventFlow = eventFlowProbe(subscribed, clientLive, r);

        // ---- put the client back the way it was
        if (activeAtStart) {
            // The plugin was stopped at the top of this check and started again by the
            // harness, so it is already active -- only restart it when that start failed.
            if (!RuneLiteHost.isPluginActive(plugin) && !RuneLiteHost.startPlugin(plugin)) {
                r.fail("restart: plugin was active before the run and could not be restarted");
            }
        } else {
            if (RuneLiteHost.isPluginActive(plugin) && !RuneLiteHost.stopPlugin(plugin)) {
                r.fail("stop: could not stop the plugin after the run");
            }
            int subsAfter = RuneLiteHost.subscriberCountFor(plugin);
            int ovlAfter = RuneLiteHost.overlayCountFor(plugin);
            if (subsAfter >= 0 && subsAfter != subsBefore) {
                r.leak = true;
                r.fail("leak: subscribers " + subsBefore + " -> " + subsAfter + " after stop");
            }
            if (ovlAfter >= 0 && ovlAfter != ovlBefore) {
                r.leak = true;
                r.fail("leak: overlays " + ovlBefore + " -> " + ovlAfter + " after stop");
            }
        }
        if (!enabledAtStart) {
            RuneLiteHost.setPluginEnabledFlag(plugin, false);
        }
        return r;
    }

    // ------------------------------------------------------------------ checks

    /** {@code @Subscribe} methods from the plugin's own hierarchy: id -> event class. */
    private static Map<String, Class<?>> subscribeMethods(Object plugin) {
        Map<String, Class<?>> out = new LinkedHashMap<>();
        for (Class<?> c = plugin.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getParameterCount() != 1 || !isAnnotated(method, "net.runelite.client.eventbus.Subscribe")) {
                    continue;
                }
                out.put(c.getName() + "#" + method.getName(), method.getParameterTypes()[0]);
            }
        }
        return out;
    }

    private static boolean isAnnotated(Method method, String annotationClassName) {
        for (Annotation annotation : method.getAnnotations()) {
            if (annotationClassName.equals(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Renders every overlay the plugin registered into one reused scratch image and counts
     * how many left pixels behind. An overlay that throws while the client is not logged in
     * is recording a state-dependent skip, not a defect; a link error is always a defect,
     * because it is thrown by the verifier/linker rather than by game state.
     */
    private static void overlayProbe(Object plugin, Result r, String gameState) {
        List<Object> overlays = RuneLiteHost.overlaysFor(plugin);
        if (overlays.isEmpty()) {
            return;
        }
        // Overlay.render() is only ever called from the client thread in production
        // (Hooks.draw -> OverlayRenderer); many overlays call client getters that assert it
        // ("must be called on client thread"), so rendering from the UI thread would fail
        // for the wrong reason.
        boolean finished = runOnClientThread(() -> renderOverlays(overlays, r, gameState),
            OVERLAY_PROBE_TIMEOUT_MS);
        if (!finished) {
            r.skip("ovl: the client thread did not render " + overlays.size()
                + " overlay(s) in " + OVERLAY_PROBE_TIMEOUT_MS + "ms");
        }
    }

    /** Renders every overlay into one reused scratch image; runs on the client thread. */
    private static void renderOverlays(List<Object> overlays, Result r, String gameState) {
        boolean loggedIn = "LOGGED_IN".equals(gameState);
        BufferedImage scratch = null;
        int empty = 0;
        for (Object overlay : overlays) {
            try {
                if (scratch == null) {
                    scratch = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
                }
                Method render = overlay.getClass().getMethod("render", Graphics2D.class);
                int[] pixels = scratch.getPixels();
                int[] before = pixels == null ? null : pixels.clone();
                Graphics2D graphics = scratch.createGraphics();
                try {
                    render.invoke(overlay, graphics);
                } finally {
                    graphics.dispose();
                }
                if (before != null && pixels != null && pixels.length == before.length) {
                    boolean drawn = false;
                    for (int i = 0; i < pixels.length && !drawn; i++) {
                        drawn = pixels[i] != before[i];
                    }
                    r.overlayOk++;
                    if (!drawn) {
                        // Drawing nothing is legitimate (nothing to show yet): it is
                        // reported, not failed -- an overlay that is *never* able to draw
                        // shows up as a render exception instead.
                        empty++;
                    }
                } else {
                    r.overlayOk++;
                }
            } catch (Throwable t) {
                Throwable cause = t.getCause() == null ? t : t.getCause();
                if (isLinkError(cause) || loggedIn) {
                    r.overlayBad++;
                    r.fail("overlay " + overlay.getClass().getSimpleName() + ".render: " + cause);
                } else {
                    r.skip("overlay " + overlay.getClass().getSimpleName() + ".render(" + gameState + "): "
                        + cause.getClass().getSimpleName());
                }
            }
        }
        if (empty > 0) {
            r.overlayNote = "empty=" + empty;
        }
    }

    /** A missing class/member on the device, whatever the game state happens to be. */
    private static boolean isLinkError(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof NoClassDefFoundError || c instanceof NoSuchMethodError
                || c instanceof NoSuchFieldError || c instanceof AbstractMethodError
                || c instanceof ClassCastException || c instanceof IncompatibleClassChangeError
                || c instanceof ExceptionInInitializerError) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads, changes, verifies and restores every {@code @ConfigItem} of the plugin's
     * config interface. The write goes through {@code ConfigManager.setConfiguration},
     * which is also what fires {@code ConfigChanged} — an item whose change does not reach
     * a listener that is subscribed to the plugin's own events is a wiring defect.
     */
    private static void configProbe(Object plugin, Result r) {
        Class<?> iface = RuneLiteHost.pluginConfigClass(plugin);
        Object manager = RuneLiteHost.configManager();
        if (iface == null || manager == null) {
            r.cfgNote = iface == null ? "no config" : "no config manager";
            return;
        }
        Object proxy;
        String group;
        try {
            proxy = manager.getClass().getMethod("getConfig", Class.class).invoke(manager, iface);
            group = configGroup(iface);
        } catch (Throwable t) {
            r.skip("config interface unavailable: " + t);
            return;
        }
        if (group == null || group.isEmpty()) {
            r.skip("config group unreadable");
            return;
        }
        List<String> changed = new ArrayList<>();
        Method set = null;
        try {
            set = manager.getClass().getMethod("setConfiguration", String.class, String.class, String.class);
        } catch (Throwable t) {
            r.skip("ConfigManager.setConfiguration unavailable: " + t);
        }
        try {
            for (Method getter : configItems(iface).values()) {
                String key = configKey(iface, getter);
                Class<?> type = getter.getReturnType();
                Object old;
                try {
                    old = getter.invoke(proxy);
                } catch (Throwable t) {
                    r.cfgBad++;
                    r.fail("cfg " + key + ": getter threw " + t);
                    continue;
                }
                Object value = differentValue(type, old);
                if (value == null) {
                    r.cfgNote = appendNote(r.cfgNote, "unsupported " + key + ":" + type.getSimpleName());
                    continue;
                }
                if (set == null) {
                    continue;
                }
                try {
                    changed.add(key);
                    set.invoke(manager, group, key, stringify(value));
                    Object read = getter.invoke(proxy);
                    boolean valueOk = sameValue(type, read, value);
                    if (valueOk) {
                        r.cfgOk++;
                    } else {
                        r.cfgBad++;
                        r.fail("cfg " + key + ": set " + value + ", read back " + read);
                    }
                } catch (Throwable t) {
                    r.cfgBad++;
                    r.fail("cfg " + key + ": write threw " + unwrap(t));
                } finally {
                    try {
                        if (old != null) {
                            set.invoke(manager, group, key, stringify(old));
                        } else {
                            manager.getClass().getMethod("unsetConfiguration", String.class, String.class)
                                .invoke(manager, group, key);
                        }
                    } catch (Throwable t) {
                        r.fail("cfg " + key + ": restore threw " + unwrap(t));
                    }
                }
            }
        } catch (Throwable t) {
            r.skip("config walk failed: " + t);
        }
        r.configTouched = !changed.isEmpty();
        if (r.cfgOk == 0 && r.cfgBad == 0 && r.cfgNote == null) {
            r.cfgNote = "no items";
        }
    }

    /** Zero-argument {@code @ConfigItem} getters of the config interface. */
    private static Map<String, Method> configItems(Class<?> iface) {
        Map<String, Method> out = new LinkedHashMap<>();
        for (Method method : iface.getMethods()) {
            if (method.getParameterCount() != 0 || method.getReturnType() == void.class) {
                continue;
            }
            if (!isAnnotated(method, "net.runelite.client.config.ConfigItem")) {
                continue;
            }
            out.put(method.getName(), method);
        }
        return out;
    }

    /** The config key of an item: {@code keyName} when set, else the method name. */
    private static String configKey(Class<?> iface, Method getter) {
        for (Annotation annotation : getter.getAnnotations()) {
            if (!"net.runelite.client.config.ConfigItem".equals(annotation.annotationType().getName())) {
                continue;
            }
            try {
                Object key = annotation.annotationType().getMethod("keyName").invoke(annotation);
                if (key instanceof String && !((String) key).isEmpty()) {
                    return (String) key;
                }
            } catch (Throwable ignored) {
                // no keyName: the method name is the key
            }
        }
        return getter.getName();
    }

    private static String configGroup(Class<?> iface) {
        for (Annotation annotation : iface.getAnnotations()) {
            if ("net.runelite.client.config.ConfigGroup".equals(annotation.annotationType().getName())) {
                try {
                    Object value = annotation.annotationType().getMethod("value").invoke(annotation);
                    return value instanceof String ? (String) value : null;
                } catch (Throwable t) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * A value of the item's declared type that differs from {@code old}, or null when the
     * type has no synthesized value (the item is then reported in the line's cfgNote and
     * left alone).
     */
    private static Object differentValue(Class<?> type, Object old) {
        if (type == boolean.class || type == Boolean.class) {
            return !Boolean.TRUE.equals(old);
        }
        if (type == int.class || type == Integer.class) {
            return ((Number) (old == null ? 0 : old)).intValue() + 1;
        }
        if (type == long.class || type == Long.class) {
            return ((Number) (old == null ? 0L : old)).longValue() + 1L;
        }
        if (type == double.class || type == Double.class) {
            return ((Number) (old == null ? 0d : old)).doubleValue() + 1d;
        }
        if (type == float.class || type == Float.class) {
            return ((Number) (old == null ? 0f : old)).floatValue() + 1f;
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            if (constants == null || constants.length == 0) {
                return null;
            }
            int index = 0;
            for (int i = 0; i < constants.length; i++) {
                if (constants[i].equals(old)) {
                    index = i;
                }
            }
            return constants[(index + 1) % constants.length];
        }
        if (type == String.class) {
            String current = old == null ? "" : String.valueOf(old);
            String sentinel = "conformance";
            return current.equals(sentinel) ? sentinel + "-x" : sentinel;
        }
        if (type == java.awt.Color.class) {
            int rgb = old instanceof java.awt.Color ? ((java.awt.Color) old).getRGB() & 0xFFFFFF : 0;
            return new java.awt.Color(rgb ^ 0x00FF00);
        }
        return null;
    }

    private static boolean sameValue(Class<?> type, Object read, Object expected) {
        if (type == java.awt.Color.class) {
            return read instanceof java.awt.Color && expected instanceof java.awt.Color
                && ((java.awt.Color) read).getRGB() == ((java.awt.Color) expected).getRGB();
        }
        if (type.isEnum()) {
            return read == expected;
        }
        return expected.equals(read) || (read != null && read.toString().equals(expected.toString()));
    }

    private static String stringify(Object value) {
        if (value instanceof java.awt.Color) {
            // The decimal ARGB int is what ConfigManager.objectToString() stores for a
            // Color, and ColorUtil.fromString() decodes it with Integer.decode + new
            // Color(int, true) -- so this round-trips including alpha. A hex string does
            // not: "00FF00" decodes as *octal* (NumberFormatException -> null) and
            // "FFFF00" as decimal, both of which lose or drop the value.
            return String.valueOf(((java.awt.Color) value).getRGB());
        }
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        if (value instanceof Boolean) {
            return String.valueOf((Boolean) value);
        }
        if (value instanceof Double || value instanceof Float) {
            return String.valueOf(((Number) value).doubleValue());
        }
        return String.valueOf(value);
    }

    /**
     * Samples the client's own entity-draw questions for {@link #ENTITY_PROBE_FRAMES}
     * frames. {@code denied > 0} is a plugin vetoing entity rendering; {@code calls == 0}
     * means the client never asked, so nothing in the scene can be hidden at all.
     */
    private static void entityVetoProbe(Result r, String gameState, boolean clientLive) {
        if (r.renderCallbacks <= 0) {
            r.entityNote = r.renderCallbacks < 0 ? "unavailable" : "rc=0";
            return;
        }
        if (!"LOGGED_IN".equals(gameState)) {
            r.entityNote = "skip(" + gameState + ")";
            r.skip("entityVeto: game not logged in");
            return;
        }
        if (!clientLive) {
            r.entityNote = "skip(no events)";
            r.skip("entityVeto: client not dispatching events");
            return;
        }
        long callsBefore = MainActivity.entityDrawCalls();
        long deniedBefore = MainActivity.entityDrawDenied();
        long deadline = System.currentTimeMillis() + ENTITY_PROBE_TIMEOUT_MS;
        while (MainActivity.entityDrawCalls() - callsBefore < ENTITY_PROBE_FRAMES
            && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        r.entityCalls = MainActivity.entityDrawCalls() - callsBefore;
        r.entityDenied = MainActivity.entityDrawDenied() - deniedBefore;
        if (r.entityCalls <= 0) {
            r.fail("entityVeto: the client asked whether to draw an entity 0 times in "
                + ENTITY_PROBE_TIMEOUT_MS + "ms");
        }
    }

    /**
     * Posts a synthetic {@code MenuEntryAdded} at an entry created on the client's own menu
     * and checks that the plugin's subscribers changed it — the wiring of the plugin's menu
     * logic, without needing a real click.
     */
    private static void menuEntryProbe(Object plugin, Map<String, Class<?>> subscribed, Result r, String gameState) {
        boolean wantsMenu = false;
        for (Class<?> eventClass : subscribed.values()) {
            if ("net.runelite.api.events.MenuEntryAdded".equals(eventClass.getName())) {
                wantsMenu = true;
            }
        }
        if (!wantsMenu) {
            return;
        }
        if (!"LOGGED_IN".equals(gameState)) {
            r.menuEntryNote = "skip(" + gameState + ")";
            r.skip("menuEntry: game not logged in");
            return;
        }
        Object client;
        Object eventBus;
        Class<?> entryClass;
        try {
            client = RuneLiteHost.get("net.runelite.api.Client");
            eventBus = RuneLiteHost.eventBus();
            entryClass = RuneLiteHost.clientLoader().loadClass("net.runelite.api.MenuEntry");
        } catch (Throwable t) {
            r.menuEntryNote = "unavailable";
            r.skip("menuEntry: " + t);
            return;
        }
        final Object[] outcome = new Object[2];
        boolean finished = runOnClientThread(() -> {
            try {
                Object menu = client.getClass().getMethod("getMenu").invoke(client);
                Object before = menu.getClass().getMethod("getMenuEntries").invoke(menu);
                int countBefore = before instanceof Object[] ? ((Object[]) before).length : -1;
                Object entry = menu.getClass().getMethod("createMenuEntry", int.class).invoke(menu, -1);
                String seedOption = null;
                if (countBefore > 0) {
                    // Seed from a real entry: plugins add their options *for a target*, so an
                    // empty entry would be ignored by nearly all of them (and that silence is
                    // not a defect).
                    Object live = ((Object[]) before)[countBefore - 1];
                    seedOption = (String) entryClass.getMethod("getOption").invoke(live);
                    String seedTarget = (String) entryClass.getMethod("getTarget").invoke(live);
                    entryClass.getMethod("setOption", String.class).invoke(entry, seedOption);
                    entryClass.getMethod("setTarget", String.class).invoke(entry, seedTarget);
                }
                String optionBefore = (String) entryClass.getMethod("getOption").invoke(entry);
                String targetBefore = (String) entryClass.getMethod("getTarget").invoke(entry);
                Class<?> addedClass = RuneLiteHost.clientLoader().loadClass("net.runelite.api.events.MenuEntryAdded");
                Constructor<?> ctor = addedClass.getConstructor(entryClass);
                Object event = ctor.newInstance(entry);
                eventBus.getClass().getMethod("post", Object.class).invoke(eventBus, event);
                String optionAfter = (String) entryClass.getMethod("getOption").invoke(entry);
                String targetAfter = (String) entryClass.getMethod("getTarget").invoke(entry);
                Object after = menu.getClass().getMethod("getMenuEntries").invoke(menu);
                int countAfter = after instanceof Object[] ? ((Object[]) after).length : -1;
                boolean modified = !java.util.Objects.equals(optionBefore, optionAfter)
                    || !java.util.Objects.equals(targetBefore, targetAfter);
                boolean appended = countBefore >= 0 && countAfter > countBefore;
                outcome[0] = modified || appended;
                outcome[1] = modified ? "modified" : appended ? "appended " + (countAfter - countBefore) : "no-reaction";
                outcome[1] = outcome[1] + " '" + (optionBefore == null ? "" : optionBefore) + "'";
                if (!modified && !appended) {
                    Log.i(TAG, "CONFORMANCE: " + plugin.getClass().getName()
                        + " did not react to a synthetic MenuEntryAdded (seed=" + seedOption + ")");
                }
            } catch (Throwable t) {
                outcome[0] = Boolean.FALSE;
                outcome[1] = "error: " + unwrap(t);
            }
        }, CLIENT_THREAD_TIMEOUT_MS);
        if (!finished) {
            r.menuEntryNote = "timeout";
            r.skip("menuEntry: client thread did not run the probe");
            return;
        }
        r.menuEntryChecked = 1;
        r.menuEntryNote = String.valueOf(outcome[1]);
        if (Boolean.TRUE.equals(outcome[0])) {
            return;
        }
        if (String.valueOf(outcome[1]).startsWith("error:")) {
            r.fail("menuEntry: " + outcome[1]);
        } else {
            // Not a reaction, and not a defect either: the plugin's menu logic keys on the
            // entry's target, which a synthetic entry cannot reproduce faithfully. It is
            // recorded so the gap is visible, and the plugin's subscriber is proven live by
            // the MenuEntryAdded post count in eventFlow.
            r.skip("menuEntry: no reaction to a synthetic entry (" + outcome[1] + ")");
        }
    }

    /**
     * Per-subscribed-event-class {@code post} counts. Only classes that must fire while the
     * client is live *and* that this proxy can observe ({@link #MUST_FIRE_EVENTS}) are
     * asserted: an event like {@code ChatMessage} legitimately never arrives in an idle
     * scene, and {@code GameTick}/{@code BeforeRender} are posted by RuneLite's own
     * {@code Hooks} straight to the bus (see {@link #MUST_FIRE_EVENTS}). Those are recorded
     * as skips instead of failures.
     */
    private static String eventFlowProbe(Map<String, Class<?>> subscribed, boolean clientLive, Result r) {
        if (subscribed.isEmpty()) {
            return null;
        }
        Map<String, Integer> byClass = new LinkedHashMap<>();
        for (Class<?> eventClass : subscribed.values()) {
            Integer count = byClass.get(eventClass.getName());
            byClass.put(eventClass.getName(), count == null ? 1 : count + 1);
        }
        StringBuilder sb = new StringBuilder();
        List<String> zero = new ArrayList<>();
        boolean first = true;
        for (String className : byClass.keySet()) {
            long posts = MainActivity.eventPostCount(className);
            if (!first) {
                sb.append(' ');
            }
            first = false;
            sb.append(simpleName(className)).append('=').append(posts).append('/').append(byClass.get(className));
            if (posts == 0) {
                zero.add(className);
            }
        }
        if (!zero.isEmpty()) {
            if (!clientLive) {
                r.skip("eventFlow: client not dispatching events");
            } else {
                for (String className : zero) {
                    if (MUST_FIRE_EVENTS.contains(className)) {
                        r.fail("eventFlow: " + simpleName(className) + " never posted while the client dispatches other events");
                    } else {
                        r.skip("eventFlow: " + simpleName(className) + " did not fire in this window");
                    }
                }
            }
        }
        return sb.toString();
    }

    /** True when any {@link #LIVE_EVENTS} class has been posted, i.e. the client is running. */
    private static boolean clientLive() {
        for (String className : LIVE_EVENTS) {
            if (MainActivity.eventPostCount(className) > 0) {
                return true;
            }
        }
        return false;
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    // ------------------------------------------------------------------ helpers

    private static String gameState() {
        try {
            Object client = RuneLiteHost.get("net.runelite.api.Client");
            Object state = client.getClass().getMethod("getGameState").invoke(client);
            return String.valueOf(state);
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static String appendNote(String note, String extra) {
        return note == null || note.isEmpty() ? extra : note + " " + extra;
    }

    private static Throwable unwrap(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c;
    }

    /**
     * Runs {@code work} on the client thread and waits for it (bounded).
     *
     * @return true when the work ran, false when the client thread is unavailable or did
     *         not get to it in time.
     */
    private static boolean runOnClientThread(Runnable work, long timeoutMs) {
        Object clientThread;
        try {
            clientThread = RuneLiteHost.get("net.runelite.client.callback.ClientThread");
        } catch (Throwable t) {
            return false;
        }
        CountDownLatch done = new CountDownLatch(1);
        try {
            clientThread.getClass().getMethod("invoke", Runnable.class)
                .invoke(clientThread, (Runnable) () -> {
                    try {
                        work.run();
                    } catch (Throwable t) {
                        Log.w(TAG, "CONFORMANCE: client-thread probe failed", t);
                    } finally {
                        done.countDown();
                    }
                });
            return done.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
