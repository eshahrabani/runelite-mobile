package org.runelite.mobile.host;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import dalvik.system.DexClassLoader;

/**
 * Dexes Plugin Hub jars on the device (Phase G2) by driving the R8/D8 compiler that ships
 * as {@code assets/rl-dexer.jar}.
 *
 * <p>Why: a hub jar is Java 11 {@code .class} bytecode, which ART cannot load. Dexing it on
 * a build host works (see the {@code dexHubPlugin} Gradle task) but makes every plugin
 * install a developer step; shipping the dexer lets the app accept a raw hub jar.
 *
 * <p>The dexer is a complete Java program (~7200 classes), so it lives in its own asset
 * dex and is loaded on demand through a {@link DexClassLoader} whose parent is the app
 * classloader: {@code com.android.tools.r8.D8} is then reached reflectively and
 * {@code D8.run(D8Command)} is executed with {@code D8Command.Builder}. Everything stays
 * behind {@link #isAvailable()} so a device where the dexer cannot load simply keeps the
 * host-dexing / pre-dexed path.
 */
public final class OnDeviceDexer {

    public static final String TAG = "MobilePluginHub";
    private static final String ASSET = "rl-dexer.jar";

    private static volatile boolean loadAttempted;
    private static volatile ClassLoader dexerLoader;
    private static volatile String unavailableReason;

    private OnDeviceDexer() {
    }

    /** True when the bundled dexer can be loaded (checked once). */
    public static boolean isAvailable(Context context) {
        ensureLoaded(context);
        return dexerLoader != null;
    }

    public static String unavailableReason() {
        return unavailableReason == null ? "" : unavailableReason;
    }

    private static synchronized void ensureLoaded(Context context) {
        if (loadAttempted) {
            return;
        }
        loadAttempted = true;
        try {
            File jar = new File(context.getFilesDir(), ASSET);
            long assetSize;
            try (InputStream in = context.getAssets().open(ASSET)) {
                if (jar.isFile()) {
                    // a previous extraction was made read-only for ART
                    jar.setWritable(true, true);
                }
                assetSize = 0;
                byte[] buffer = new byte[8192];
                try (FileOutputStream out = new FileOutputStream(jar)) {
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                        assetSize += read;
                    }
                }
            }
            jar.setWritable(true, true);
            jar.setReadOnly();
            dexerLoader = new DexClassLoader(jar.getAbsolutePath(),
                context.getDir("dex-r8", Context.MODE_PRIVATE).getAbsolutePath(), null,
                OnDeviceDexer.class.getClassLoader());
            // Force resolution of the entry point now, so "available" is truthful.
            dexerLoader.loadClass("com.android.tools.r8.D8");
            Log.i(TAG, "on-device dexer ready (" + assetSize + " bytes)");
        } catch (Throwable t) {
            unavailableReason = t.toString();
            dexerLoader = null;
            Log.w(TAG, "on-device dexer unavailable: " + t);
        }
    }

    /**
     * Dexes {@code classJar} (Java 11 {@code .class} entries) into {@code outputJar},
     * which receives the produced {@code classes*.dex} plus the input jar's non-class
     * resources (the hub descriptor and icons are read from the same jar at runtime).
     *
     * @return true when the output jar was written
     */
    public static boolean dex(Context context, File classJar, File outputJar) {
        ensureLoaded(context);
        ClassLoader loader = dexerLoader;
        if (loader == null) {
            return false;
        }
        long start = System.nanoTime();
        File outDir = new File(outputJar.getParentFile(), outputJar.getName() + ".dexout");
        try {
            if (outDir.exists() && !outDir.delete()) {
                Log.w(TAG, "could not clear " + outDir);
            }
            if (!outDir.mkdirs()) {
                Log.w(TAG, "could not create " + outDir);
            }

            Class<?> d8Class = loader.loadClass("com.android.tools.r8.D8");
            Class<?> commandClass = loader.loadClass("com.android.tools.r8.D8Command");
            Class<?> outputModeClass = loader.loadClass("com.android.tools.r8.OutputMode");

            // D8Command.builder() -> BaseCommand.Builder#addProgramFiles(Path...)
            //   -> D8Command.Builder#setMinApiLevel(int)/setOutput(Path, OutputMode)
            //   -> BaseCommand.Builder#build() -> D8.run(D8Command).
            // (addProgramFiles/addLibraryFiles/build live on
            // com.android.tools.r8.BaseCommand$Builder, so they are looked up through the
            // concrete builder class and reflection walks the supertypes.)
            Object builder = commandClass.getMethod("builder").invoke(null);
            Class<?> builderClass = builder.getClass();
            builder = builderClass.getMethod("addProgramFiles", java.nio.file.Path[].class)
                .invoke(builder, (Object) new java.nio.file.Path[]{Paths.get(classJar.getAbsolutePath())});
            builder.getClass().getMethod("setMinApiLevel", int.class).invoke(builder, 26);
            builder.getClass().getMethod("setOutput", java.nio.file.Path.class, outputModeClass)
                .invoke(builder, Paths.get(outDir.getAbsolutePath()),
                    outputModeClass.getField("DexIndexed").get(null));
            Object command = builder.getClass().getMethod("build").invoke(builder);

            Method run = d8Class.getMethod("run", commandClass);
            run.invoke(null, command);

            File[] produced = outDir.listFiles((d, name) -> name.endsWith(".dex"));
            if (produced == null || produced.length == 0) {
                Log.e(TAG, "dexer produced no .dex for " + classJar.getName());
                return false;
            }
            outputJar.delete();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputJar))) {
                for (File dex : produced) {
                    zos.putNextEntry(new ZipEntry(dex.getName()));
                    try (InputStream in = new java.io.FileInputStream(dex)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) > 0) {
                            zos.write(buffer, 0, read);
                        }
                    }
                    zos.closeEntry();
                }
                try (ZipFile zip = new ZipFile(classJar)) {
                    java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (entry.isDirectory() || entry.getName().endsWith(".class")
                            || entry.getName().startsWith("META-INF/")) {
                            continue;
                        }
                        zos.putNextEntry(new ZipEntry(entry.getName()));
                        try (InputStream in = zip.getInputStream(entry)) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = in.read(buffer)) > 0) {
                                zos.write(buffer, 0, read);
                            }
                        }
                        zos.closeEntry();
                    }
                }
            }
            long ms = (System.nanoTime() - start) / 1_000_000L;
            Log.i(TAG, "dexed " + classJar.getName() + " in " + ms + "ms -> " + outputJar.getName()
                + " (" + outputJar.length() + " bytes)");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "on-device dexing failed for " + classJar.getName(), t);
            return false;
        } finally {
            File[] leftovers = outDir.listFiles();
            if (leftovers != null) {
                for (File f : leftovers) {
                    f.delete();
                }
            }
            outDir.delete();
        }
    }
}
