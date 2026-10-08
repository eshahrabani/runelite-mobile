package org.runelite.mobile;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Downloads the pre-dexed game client published by CI as a GitHub Release
 * asset, verifies its SHA-256 and installs it. Nothing is dexed on-device:
 * the build pipeline ({@code downloadAndDexJar} in android/build.gradle)
 * produces {@code runelite-dex.jar} and CI publishes it together with
 * {@code client-version.txt} and {@code runelite-dex.jar.sha256}.
 */
public class ClientUpdater {
    private static final String TAG = "RuneLiteMobile";

    public static final String DEX_ASSET_NAME = "runelite-dex.jar";
    public static final String VERSION_FILE_NAME = "client-version.txt";
    private static final String SHA256_FILE = "runelite-dex.jar.sha256";

    /** {@code cmd package compile} invocation that makes this dex AOT again (the app cannot run it). */
    public static final String AOT_FIX_COMMAND =
        "cmd package compile -m speed -f --secondary-dex org.runelite.mobile";

    public static final int AOT_OK = 0;       // usable [status=speed] odex
    public static final int AOT_STALE = 1;    // odex present but older than the APK/jar: ART runs the vdex (verify)
    public static final int AOT_MISSING = 2;  // no odex at all
    public static final int AOT_UNKNOWN = 3;  // files/oat unreadable: do not claim anything

    /**
     * Whether ART has a usable AOT odex for the client dex.
     *
     * <p>ART keys the odex to the invoking class-loader context, which embeds the base APK
     * path plus the checksums of the APK and the dex. A new install therefore invalidates
     * it, and with {@code dalvik.vm.usejit=false} the game then runs interpreted (~10x
     * slower). This is a heuristic: the odex is stale when it is older than both inputs,
     * which covers both the install case and a downloaded client-jar update.
     */
    public static int clientDexAotStatus(Context context) {
        try {
            File jar = new File(context.getFilesDir(), DEX_ASSET_NAME);
            if (!jar.isFile()) {
                return AOT_MISSING;
            }
            String odexName = DEX_ASSET_NAME.substring(0, DEX_ASSET_NAME.length() - 4) + ".odex";
            File oatRoot = new File(context.getFilesDir(), "oat");   // oat/<isa>/runelite-dex.odex
            File[] isaDirs = oatRoot.listFiles();
            if (isaDirs == null) {
                return AOT_UNKNOWN;
            }
            long newestOdex = 0;
            for (File isaDir : isaDirs) {
                File odex = new File(isaDir, odexName);
                if (odex.isFile()) {
                    newestOdex = Math.max(newestOdex, odex.lastModified());
                }
            }
            if (newestOdex == 0) {
                return AOT_MISSING;
            }
            long newestInput = Math.max(jar.lastModified(), new File(context.getPackageCodePath()).lastModified());
            return newestOdex >= newestInput ? AOT_OK : AOT_STALE;
        } catch (Throwable t) {
            Log.w(TAG, "client dex AOT check failed: " + t);
            return AOT_UNKNOWN;
        }
    }

    /** One-line human description of {@link #clientDexAotStatus} for the UI and logcat. */
    public static String clientDexAotText(Context context) {
        switch (clientDexAotStatus(context)) {
            case AOT_OK:      return "compiled (speed)";
            case AOT_STALE:   return "stale - not AOT-compiled, game runs interpreted (~10x slower); fix: adb shell " + AOT_FIX_COMMAND;
            case AOT_MISSING: return "missing - not AOT-compiled, game runs interpreted (~10x slower); fix: adb shell " + AOT_FIX_COMMAND;
            default:          return "unknown (files/oat unreadable)";
        }
    }

    public interface ProgressListener {
        /** @param stage human-readable current phase, @param percent 0-100 overall,
         *               @param etaMillis estimated milliseconds remaining */
        void onProgress(String stage, int percent, long etaMillis);
    }

    /**
     * Version of the client published for download, or null if unreachable.
     */
    public static String fetchAvailableClientVersion() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(BuildConfig.DIST_BASE + VERSION_FILE_NAME).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            int code = conn.getResponseCode();
            if (code >= 400) {
                Log.w(TAG, "client-version.txt returned " + code);
                return null;
            }
            String body;
            try (InputStream is = conn.getInputStream()) {
                body = readStream(is);
            }
            String version = body.trim();
            return version.isEmpty() ? null : version;
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch published client version", e);
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
     * Downloads + hash-verifies + installs the published dex jar for {@code version}.
     * Reports live download progress through the listener.
     */
    public static void downloadAndInstall(Context context, String version, ProgressListener listener) throws Exception {
        File cacheDir = new File(context.getCacheDir(), "client-update");
        if (cacheDir.exists()) {
            deleteRecursive(cacheDir);
        }
        File tempDir = new File(cacheDir, version);
        if (!tempDir.mkdirs()) {
            throw new IOException("Cannot create cache dir");
        }

        File shaFile = new File(tempDir, SHA256_FILE);
        download(BuildConfig.DIST_BASE + SHA256_FILE, shaFile, "Downloading checksum", listener);
        String expected;
        try (InputStream is = new FileInputStream(shaFile)) {
            String body = readStream(is).trim();
            expected = body.isEmpty() ? "" : body.split("\\s+")[0];
        }
        if (!expected.matches("[0-9a-fA-F]{64}")) {
            throw new IOException("Invalid published checksum");
        }

        File jar = new File(tempDir, DEX_ASSET_NAME);
        download(BuildConfig.DIST_BASE + DEX_ASSET_NAME, jar, "Downloading game client", listener);

        String actual = sha256(jar);
        if (!actual.equalsIgnoreCase(expected)) {
            throw new IOException("Checksum mismatch (expected " + expected.toLowerCase() + ", got " + actual + ")");
        }

        listener.onProgress("Installing update", 100, 0);
        installBundled(context, version, jar);
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

    private static void download(String urlString, File dest, String stage, ProgressListener listener) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        int code = conn.getResponseCode();
        if (code >= 400) {
            throw new IOException("Download failed (" + code + "): " + urlString);
        }
        long total = conn.getContentLength();
        long start = System.currentTimeMillis();
        long written = 0;
        try (InputStream is = conn.getInputStream();
             FileOutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
                written += read;
                if (written == read) {
                    // Start the rate clock after the first read (connection setup
                    // and TLS handshake must not skew the throughput estimate).
                    start = System.currentTimeMillis();
                }
                long elapsed = System.currentTimeMillis() - start;
                long ratePerMs = elapsed > 0 ? written / elapsed : 0;
                int percent = total > 0 ? (int) (written * 100 / total) : 0;
                long eta = (total > 0 && ratePerMs > 0) ? (total - written) / ratePerMs : 0;
                listener.onProgress(stage, percent, eta);
            }
        }
        Log.i(TAG, "Downloaded " + dest.getName() + " (" + dest.length() + " bytes)");
    }

    private static String sha256(File f) throws IOException {
        try (InputStream is = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int read;
            while ((read = is.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
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
