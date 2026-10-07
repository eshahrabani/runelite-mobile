package org.runelite.mobile.host;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

/**
 * Loads sideloaded third-party plugins (Plugin Hub jars that were dexed on the host by
 * {@code ./gradlew :android:dexHubPlugin} and pushed into {@code files/plugins}).
 *
 * <p>A hub jar is a Java 11 {@code .class} jar, which ART cannot load, so each jar in
 * {@code files/plugins} is expected to be the {@code <internalName>_<jarHash>.jar} the
 * {@code dexHubPlugin} task produces: {@code classes*.dex} plus the hub's resources,
 * including {@code runelite_plugin.json} ({@code {"plugins":["fqcn", ...]}}), which is
 * what names the plugin classes to load.
 *
 * <p>Each jar gets its own {@link DexClassLoader} whose parent is the RuneLite client
 * loader, so hub plugins see the same {@code net.runelite.api} and
 * {@code net.runelite.client} classes the core plugins do. The loaders are held in a
 * static list for the process lifetime: dropping them would let ART unload the classes
 * while plugins are still running.
 */
public final class MobilePluginHub {

    public static final String TAG = "MobilePluginHub";
    public static final String DIR_NAME = "plugins";
    private static final String DESCRIPTOR = "runelite_plugin.json";

    /** Kept for the process lifetime so the plugin classes stay loaded. */
    private static final List<DexClassLoader> LOADERS = new ArrayList<>();
    private static final List<String> LOADED = new ArrayList<>();

    private MobilePluginHub() {
    }

    public static synchronized List<String> loadedPlugins() {
        return new ArrayList<>(LOADED);
    }

    /**
     * Scans {@code files/plugins} and loads every dexed hub jar it finds.
     *
     * @return the number of plugin classes handed to the PluginManager
     */
    public static int loadPlugins(Context context, ClassLoader clientLoader) {
        // Two directories are scanned: the app's private files dir and the app-specific
        // external dir. The latter is what `adb push /sdcard/Android/data/<pkg>/files/plugins/`
        // can write without root, so it is the documented install path for sideloaded
        // plugins (see the dexHubPlugin task's printed command).
        File internal = new File(context.getFilesDir(), DIR_NAME);
        if (!internal.isDirectory() && !internal.mkdirs()) {
            Log.w(TAG, "could not create " + internal.getAbsolutePath());
        }
        List<File> jars = new ArrayList<>();
        for (File dir : new File[]{internal, new File(context.getExternalFilesDir(null), DIR_NAME)}) {
            File[] found = dir.listFiles((d, name) -> name.endsWith(".jar"));
            if (found == null || found.length == 0) {
                Log.i(TAG, "no jars in " + dir.getAbsolutePath());
                continue;
            }
            Log.i(TAG, "found " + found.length + " jar(s) in " + dir.getAbsolutePath());
            for (File jar : found) {
                // ART refuses a dex that is writable by others ("Writable dex file ... is
                // not allowed"), which is exactly what a jar on shared storage is. The
                // app-specific external dir is the adb-push drop box, so jars there are
                // imported into the private dir (and made read-only) before loading.
                if (dir.equals(internal)) {
                    jars.add(jar);
                } else {
                    File imported = new File(internal, jar.getName());
                    try {
                        if (imported.isFile() && imported.length() != jar.length()) {
                            // the previous import was made read-only for ART: make it
                            // writable again before replacing it
                            imported.setWritable(true, true);
                            imported.delete();
                        }
                        if (!imported.isFile() || imported.length() != jar.length()) {
                            try (java.io.InputStream in = new java.io.FileInputStream(jar);
                                 java.io.OutputStream out = new java.io.FileOutputStream(imported)) {
                                byte[] buffer = new byte[8192];
                                int read;
                                while ((read = in.read(buffer)) > 0) {
                                    out.write(buffer, 0, read);
                                }
                            }
                            Log.i(TAG, "imported " + jar.getName() + " into " + internal.getAbsolutePath());
                        }
                        jars.add(imported);
                    } catch (java.io.IOException e) {
                        Log.e(TAG, "could not import " + jar.getName(), e);
                    }
                }
            }
        }
        if (jars.isEmpty()) {
            return 0;
        }
        jars.sort(java.util.Comparator.comparing(File::getName));
        // A raw jar and its on-device-dexed sibling both exist after the first run: load
        // only the dexed one, or PluginManager would instantiate the plugin twice.
        List<File> unique = new ArrayList<>();
        for (File jar : jars) {
            if (!jar.getName().endsWith(".dex.jar")) {
                File dexed = new File(jar.getParentFile(),
                    jar.getName().replaceAll("\\.jar$", "") + ".dex.jar");
                if (dexed.isFile() && dexed.length() > 0) {
                    continue;
                }
            }
            unique.add(jar);
        }
        jars = unique;

        List<Class<?>> classes = new ArrayList<>();
        for (File jar : jars) {
            try {
                List<String> names = readPluginNames(jar);
                if (names.isEmpty()) {
                    Log.w(TAG, "no " + DESCRIPTOR + " in " + jar.getName() + "; skipping");
                    continue;
                }
                // A raw hub jar is Java 11 .class bytecode, which ART cannot load: dex it
                // on the device with the bundled R8/D8 (assets/rl-dexer.jar). Jars that are
                // already dexed (by the host-side dexHubPlugin task) are used as they are.
                File loadable = jar;
                if (containsClasses(jar)) {
                    loadable = dexOnDevice(context, jar);
                    if (loadable == null) {
                        continue;
                    }
                }
                loadable.setWritable(true, true);
                loadable.setReadOnly();
                File dexOut = context.getDir("dex-hub", Context.MODE_PRIVATE);
                DexClassLoader loader = new DexClassLoader(
                    loadable.getAbsolutePath(), dexOut.getAbsolutePath(), null, clientLoader);
                List<String> loadedNames = new ArrayList<>();
                for (String name : names) {
                    try {
                        classes.add(loader.loadClass(name));
                        loadedNames.add(name);
                    } catch (Throwable t) {
                        Log.w(TAG, "could not load " + name + " from " + jar.getName(), t);
                    }
                }
                LOADERS.add(loader);
                LOADED.addAll(loadedNames);
                Log.i(TAG, "loaded " + loadable.getName() + " (" + loadedNames.size() + " classes)");
            } catch (Throwable t) {
                Log.e(TAG, "hub jar failed: " + jar.getName(), t);
            }
        }
        if (classes.isEmpty()) {
            return 0;
        }
        int count = RuneLiteHost.loadAdditionalPlugins(classes);
        Log.i(TAG, "handed " + count + " hub plugin class(es) to PluginManager");
        return count;
    }

    /** True when the jar holds Java {@code .class} entries (i.e. it still needs dexing). */
    private static boolean containsClasses(File jar) {
        try (ZipFile zip = new ZipFile(jar)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                if (entries.nextElement().getName().endsWith(".class")) {
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "could not inspect " + jar.getName(), t);
        }
        return false;
    }

    /**
     * Dexes a raw hub jar into {@code files/plugins/<name>.dex.jar} using the bundled
     * on-device dexer.
     *
     * @return the dexed jar, or null when dexing is unavailable or failed
     */
    private static File dexOnDevice(Context context, File jar) {
        if (!OnDeviceDexer.isAvailable(context)) {
            Log.e(TAG, "on-device dexer unavailable (" + OnDeviceDexer.unavailableReason()
                + "); dex " + jar.getName() + " on the host with -PhubPlugin=<name>");
            return null;
        }
        File dexed = new File(jar.getParentFile(),
            jar.getName().replaceAll("\\.jar$", "") + ".dex.jar");
        if (dexed.isFile() && dexed.length() > 0 && dexed.lastModified() >= jar.lastModified()) {
            return dexed;
        }
        return OnDeviceDexer.dex(context, jar, dexed) ? dexed : null;
    }

    /** Reads {@code runelite_plugin.json} -> {@code {"plugins": [...]}} from the jar. */
    private static List<String> readPluginNames(File jar) throws Exception {
        List<String> names = new ArrayList<>();
        // The ZipFile must be closed before the DexClassLoader touches the jar.
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry(DESCRIPTOR);
            if (entry == null) {
                return names;
            }
            String json;
            try (InputStream in = zip.getInputStream(entry)) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[2048];
                InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
                int read;
                while ((read = reader.read(buf)) > 0) {
                    sb.append(buf, 0, read);
                }
                json = sb.toString();
            }
            // Android's own org.json keeps the hub loader free of the asset dex's Gson.
            org.json.JSONObject root = new org.json.JSONObject(json);
            org.json.JSONArray plugins = root.optJSONArray("plugins");
            for (int i = 0; plugins != null && i < plugins.length(); i++) {
                names.add(plugins.getString(i));
            }
        }
        return names;
    }
}
