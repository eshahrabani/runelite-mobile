package org.runelite.mobile;

import android.content.Context;
import android.util.Log;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Downloads RuneLite's injected client at runtime, applies the same ASM
 * transform as the build pipeline, dexes it on-device with the embedded
 * D8 API and swaps it in, so the game can survive weekly RuneLite releases
 * without an APK update.
 */
public class ClientUpdater {
    private static final String TAG = "RuneLiteMobile";

    private static final String BOOTSTRAP_URL = "https://static.runelite.net/bootstrap.json";
    private static final String REPO_URL = "https://repo.runelite.net/net/runelite/";
    public static final String DEX_ASSET_NAME = "runelite-dex.jar";
    public static final String VERSION_FILE_NAME = "client-version.txt";

    public interface ProgressListener {
        /** @param stage human-readable current phase, @param percent 0-100 overall,
         *               @param etaMillis estimated milliseconds remaining */
        void onProgress(String stage, int percent, long etaMillis);
    }

    // ── Phase tracking: D8 exposes no progress, so we estimate from
    //    per-phase wall-clock against calibrated estimates (blended with
    //    measurements from previous runs, stored in prefs). ──
    private static final String[] PHASES = {
        "Downloading injected client",
        "Downloading runelite-api",
        "Transforming client for Android",
        "Transforming runelite-api",
        "Dexing game client (slowest step)",
        "Dexing runelite-api",
        "Bundling dex + resources",
        "Installing update"
    };
    private static final long[] DEFAULT_PHASE_ESTIMATES_MS = {
        6000, 6000, 60000, 30000, 700000, 250000, 15000, 3000
    };

    private static final class ProgressState {
        volatile int phaseIndex = -1;
        volatile long phaseStart = 0;
        volatile boolean done = false;
        final long[] estimatesMs = DEFAULT_PHASE_ESTIMATES_MS.clone();
        final long[] measuredMs = new long[PHASES.length];
    }

    private static long totalEstimate(ProgressState state) {
        long total = 0;
        for (long e : state.estimatesMs) total += e;
        return total;
    }

    private static void startProgressReporter(Context context, ProgressState state, ProgressListener listener) {
        Thread reporter = new Thread(() -> {
            long total = totalEstimate(state);
            while (!state.done) {
                int idx = state.phaseIndex;
                if (idx >= 0 && idx < PHASES.length) {
                    long elapsed = System.currentTimeMillis() - state.phaseStart;
                    long phaseEstimate = state.estimatesMs[idx];
                    long doneMs = 0;
                    for (int i = 0; i < idx; i++) doneMs += state.estimatesMs[i];
                    double fraction = Math.min(1.0, (double) elapsed / phaseEstimate);
                    doneMs += (long) (phaseEstimate * fraction);
                    int percent = (int) Math.min(99, Math.max(1, doneMs * 100 / total));
                    long eta = Math.max(0, (total - doneMs));
                    try {
                        listener.onProgress(PHASES[idx], percent, eta);
                    } catch (Exception ignored) {}
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "UpdateProgressReporter");
        reporter.setDaemon(true);
        reporter.start();
        // Save measured phase durations when the run completes
        Thread calibrationSaver = new Thread(() -> {
            try {
                reporter.join();
                long[] measured = new long[PHASES.length];
                boolean complete = state.phaseIndex >= PHASES.length - 1;
                if (!complete) return;
                // measured deltas are tracked in state.measuredMs by begin/endPhase
                for (int i = 0; i < PHASES.length; i++) {
                    measured[i] = state.measuredMs[i];
                }
                android.content.SharedPreferences prefs = context.getSharedPreferences("update_calibration", Context.MODE_PRIVATE);
                android.content.SharedPreferences.Editor editor = prefs.edit();
                for (int i = 0; i < PHASES.length; i++) {
                    if (measured[i] > 0) {
                        long old = prefs.getLong("phase_" + i, DEFAULT_PHASE_ESTIMATES_MS[i]);
                        editor.putLong("phase_" + i, (old + measured[i]) / 2);
                    }
                }
                editor.apply();
                Log.i(TAG, "Calibrated update phase timings");
            } catch (Exception e) {
                Log.w(TAG, "Calibration save failed: " + e.getMessage());
            }
        }, "UpdateCalibration");
        calibrationSaver.setDaemon(true);
        calibrationSaver.start();
    }

    /**
     * Returns the newest injected-client version from RuneLite's bootstrap,
     * or null if the fetch failed.
     */
    public static String fetchLatestClientVersion() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(BOOTSTRAP_URL).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            int code = conn.getResponseCode();
            if (code >= 400) {
                Log.w(TAG, "bootstrap.json returned " + code);
                return null;
            }
            String body = readStream(conn.getInputStream());
            JSONObject bootstrap = new JSONObject(body);
            JSONArray artifacts = bootstrap.optJSONArray("artifacts");
            if (artifacts == null) return null;
            for (int i = 0; i < artifacts.length(); i++) {
                JSONObject artifact = artifacts.optJSONObject(i);
                if (artifact == null) continue;
                String name = artifact.optString("name", "");
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("client-(.+\\.jar)$").matcher(name);
                if (m.find()) {
                    String version = m.group(1).replace(".jar", "");
                    return version;
                }
            }
            return null;
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch bootstrap.json", e);
            return null;
        }
    }

    /**
     * Version of the client baked into the APK's assets.
     */
    public static String currentClientVersion(Context context) {
        try (InputStream is = context.getAssets().open(VERSION_FILE_NAME)) {
            return readStream(is).trim();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Version of the client currently installed in the app files dir.
     */
    public static String installedClientVersion(Context context) {
        File versionFile = new File(context.getFilesDir(), VERSION_FILE_NAME);
        if (!versionFile.exists()) {
            return currentClientVersion(context);
        }
        try {
            byte[] bytes = new byte[(int) versionFile.length()];
            try (FileInputStream fis = new FileInputStream(versionFile)) {
                int read = fis.read(bytes);
                return new String(bytes, 0, Math.max(read, 0), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            return currentClientVersion(context);
        }
    }

    /**
     * Downloads + transforms + dexes the given client version and atomically
     * replaces the dex jar the bootstrapper loads. Reports live progress
     * (percent + ETA) through the listener.
     */
    public static void runUpdate(Context context, String version, ProgressListener listener) throws Exception {
        File cacheDir = new File(context.getCacheDir(), "client-update");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IOException("Cannot create cache dir");
        }
        File tempDir = new File(cacheDir, version);
        if (tempDir.exists()) {
            deleteRecursive(tempDir);
        }
        if (!tempDir.mkdirs()) {
            throw new IOException("Cannot create temp dir");
        }

        ProgressState progress = new ProgressState();
        android.content.SharedPreferences prefs = context.getSharedPreferences("update_calibration", Context.MODE_PRIVATE);
        for (int i = 0; i < PHASES.length; i++) {
            long calibrated = prefs.getLong("phase_" + i, DEFAULT_PHASE_ESTIMATES_MS[i]);
            if (calibrated > 0) progress.estimatesMs[i] = calibrated;
        }
        startProgressReporter(context, progress, listener);
        try {
            runPhases(context, version, tempDir, progress, listener);
        } finally {
            progress.done = true;
            listener.onProgress("Done", 100, 0);
        }
    }

    private static void runPhases(Context context, String version, File tempDir, ProgressState progress,
                                  ProgressListener listener) throws Exception {
        File injectedJar = phase(0, progress, listener, () ->
            download(REPO_URL + "injected-client/" + version + "/injected-client-" + version + ".jar",
                new File(tempDir, "injected-client.jar")));

        File apiJar = phase(1, progress, listener, () ->
            download(REPO_URL + "runelite-api/" + version + "/runelite-api-" + version + ".jar",
                new File(tempDir, "runelite-api.jar")));

        File cleanedClient = new File(tempDir, "injected-client-cleaned.jar");
        File cleanedApi = new File(tempDir, "runelite-api-cleaned.jar");
        phase(2, progress, listener, () -> {
            transformJar(injectedJar, cleanedClient);
            return null;
        });
        phase(3, progress, listener, () -> {
            transformJar(apiJar, cleanedApi);
            stripDuplicates(cleanedClient, cleanedApi);
            return null;
        });

        // Two passes (client, then api) into separate dirs to halve D8's peak
        // memory on-device; the dex files are merged into one jar afterwards.
        // A 2-thread executor keeps D8's memory/GC pressure in check on phones
        // (its default worker pool = availableProcessors and thrashes the heap).
        File dexClientDir = new File(tempDir, "dex-client");
        File dexApiDir = new File(tempDir, "dex-api");
        if (!dexClientDir.mkdirs() || !dexApiDir.mkdirs()) {
            throw new IOException("Cannot create dex dirs");
        }
        java.util.concurrent.ExecutorService dexExecutor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            phase(4, progress, listener, () -> {
                D8.run(D8Command.builder()
                    .addProgramFiles(cleanedClient.toPath())
                    .setOutput(dexClientDir.toPath(), OutputMode.DexIndexed)
                    .setMinApiLevel(26)
                    .build(), dexExecutor);
                return null;
            });
            phase(5, progress, listener, () -> {
                D8.run(D8Command.builder()
                    .addProgramFiles(cleanedApi.toPath())
                    .setOutput(dexApiDir.toPath(), OutputMode.DexIndexed)
                    .setMinApiLevel(26)
                    .build(), dexExecutor);
                return null;
            });
        } finally {
            dexExecutor.shutdown();
        }

        File bundled = new File(tempDir, "runelite-dex.jar");
        phase(6, progress, listener, () -> {
            bundleDexJar(dexClientDir, dexApiDir, cleanedClient, cleanedApi, bundled);
            return null;
        });

        phase(7, progress, listener, () -> {
            installBundled(context, version, bundled);
            return null;
        });
    }

    /** Runs one pipeline phase, updating progress state around it. */
    private static <T> T phase(int index, ProgressState progress, ProgressListener listener, PhaseCall<T> call) throws Exception {
        progress.phaseIndex = index;
        progress.phaseStart = System.currentTimeMillis();
        listener.onProgress(PHASES[index], 1, totalEstimate(progress));
        T result = call.run();
        progress.measuredMs[index] = System.currentTimeMillis() - progress.phaseStart;
        Log.i(TAG, "Phase '" + PHASES[index] + "' took " + progress.measuredMs[index] / 1000 + "s");
        return result;
    }

    private interface PhaseCall<T> {
        T run() throws Exception;
    }

    private static void installBundled(Context context, String version, File bundled) throws IOException {
        File target = new File(context.getFilesDir(), DEX_ASSET_NAME);
        File backup = new File(context.getFilesDir(), DEX_ASSET_NAME + ".old");
        if (backup.exists() && !backup.delete()) {
            Log.w(TAG, "Could not delete old backup");
        }
        if (target.exists() && !target.renameTo(backup)) {
            Log.w(TAG, "Could not move current dex jar aside");
        }
        if (!bundled.renameTo(target)) {
            if (!bundled.renameTo(target)) {
                // Direct copy fallback
                try (FileInputStream fis = new FileInputStream(bundled);
                     FileOutputStream fos = new FileOutputStream(target)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                }
            }
        }
        target.setReadOnly();

        File versionFile = new File(context.getFilesDir(), VERSION_FILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(versionFile)) {
            fos.write(version.getBytes(StandardCharsets.UTF_8));
        }

        if (backup.exists() && !backup.delete()) {
            Log.w(TAG, "Could not delete backup dex jar");
        }
        Log.i(TAG, "Client update installed: " + target.length() + " bytes, version " + version);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private static File download(String urlString, File dest) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        int code = conn.getResponseCode();
        if (code >= 400) {
            throw new IOException("Download failed (" + code + "): " + urlString);
        }
        try (InputStream is = conn.getInputStream();
             FileOutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        }
        Log.i(TAG, "Downloaded " + dest.getName() + " (" + dest.length() + " bytes)");
        return dest;
    }

    private static void transformJar(File input, File output) throws IOException {
        try (ZipFile zip = new ZipFile(input);
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                ZipEntry newEntry = new ZipEntry(entry.getName());
                zos.putNextEntry(newEntry);
                if (!entry.isDirectory()) {
                    byte[] bytes = readAll(zip.getInputStream(entry));
                    if (entry.getName().endsWith(".class")) {
                        bytes = ClientClassTransformer.transformClassBytes(bytes);
                    }
                    zos.write(bytes);
                }
                zos.closeEntry();
            }
        }
    }

    /**
     * Mirrors the build pipeline: classes that exist in the injected client
     * (e.g. com.jagex.oldscape.pub.OAuthApi) must be stripped from the API jar
     * or D8 rejects the duplicate type definition.
     */
    private static void stripDuplicates(File clientJar, File apiJar) throws IOException {
        java.util.Set<String> clientEntries = new java.util.HashSet<>();
        try (ZipFile zip = new ZipFile(clientJar)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                clientEntries.add(entries.nextElement().getName());
            }
        }
        File temp = new File(apiJar.getParentFile(), apiJar.getName() + ".tmp");
        try (ZipFile zip = new ZipFile(apiJar);
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(temp))) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (clientEntries.contains(entry.getName())) {
                    continue;
                }
                zos.putNextEntry(new ZipEntry(entry.getName()));
                if (!entry.isDirectory()) {
                    zos.write(readAll(zip.getInputStream(entry)));
                }
                zos.closeEntry();
            }
        }
        if (!apiJar.delete() || !temp.renameTo(apiJar)) {
            throw new IOException("Could not replace API jar after stripping duplicates");
        }
    }

    private static void bundleDexJar(File dexClientDir, File dexApiDir, File cleanedClient, File cleanedApi, File output) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {
            // Both passes emit classes.dex / classes2.dex ... - collect and
            // renumber sequentially so entries don't collide.
            java.util.List<File> dexFiles = new java.util.ArrayList<>();
            addDexFiles(dexClientDir, dexFiles);
            addDexFiles(dexApiDir, dexFiles);
            dexFiles.sort(java.util.Comparator.comparing(File::getName));
            int index = 1;
            for (File dexFile : dexFiles) {
                String entryName = index == 1 ? "classes.dex" : "classes" + index + ".dex";
                zos.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream fis = new FileInputStream(dexFile)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        zos.write(buffer, 0, read);
                    }
                }
                zos.closeEntry();
                index++;
            }
            copyResources(cleanedClient, zos);
            copyResources(cleanedApi, zos);
            zos.putNextEntry(new ZipEntry("runelite/index"));
            zos.write(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
            zos.closeEntry();
        }
    }

    private static void addDexFiles(File dir, java.util.List<File> out) {
        File[] dexFiles = dir.listFiles();
        if (dexFiles == null) return;
        for (File dexFile : dexFiles) {
            if (dexFile.getName().endsWith(".dex")) {
                out.add(dexFile);
            }
        }
    }

    private static void copyResources(File jar, ZipOutputStream zos) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                try {
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    zos.write(readAll(zip.getInputStream(entry)));
                    zos.closeEntry();
                } catch (java.util.zip.ZipException ze) {
                    // duplicate entry, ignore
                }
            }
        }
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static String readStream(InputStream is) throws IOException {
        return new String(readAll(is), StandardCharsets.UTF_8);
    }

    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
