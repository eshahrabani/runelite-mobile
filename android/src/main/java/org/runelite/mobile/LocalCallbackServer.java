package org.runelite.mobile;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal loopback HTTP server for the Jagex consent-leg OAuth callback.
 *
 * Jagex registers the consent client's redirect URI as exactly "http://localhost"
 * (port 80). The launcher redirects the browser there, but a browser navigation
 * never sends the URL fragment to the server — the id_token lives in the fragment.
 * This server serves a page whose JS POSTs location.hash back to it, delivering
 * the fragment to the app.
 *
 * Android apps can bind port 80 on loopback without root (verified on-device).
 * Connections from non-loopback peers are rejected. Only one consent POST is
 * accepted per server instance.
 */
public class LocalCallbackServer {
    private static final String TAG = "RuneLiteMobile";

    public interface Listener {
        void onConsentFragment(String fragment);
    }

    private static final byte[] PAGE = ("<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
        + "<title>RuneLite Mobile</title></head><body>"
        + "<script>fetch('/', {method:'POST', body: location.hash.slice(1)})"
        + ".then(function(){document.body.innerHTML="
        + "'<h2 style=\"font-family:sans-serif;text-align:center;margin-top:40vh\">"
        + "Login complete. Return to RuneLite Mobile.</h2>';});</script>"
        + "</body></html>").getBytes(StandardCharsets.UTF_8);

    private final Listener listener;
    private final List<ServerSocket> sockets = new ArrayList<>();
    private volatile boolean running = false;
    private volatile boolean delivered = false;

    public LocalCallbackServer(Listener listener) {
        this.listener = listener;
    }

    /** Binds loopback listeners on port 80 (IPv4 and IPv6). True if any bound. */
    public boolean start() {
        running = true;
        boolean any = false;
        any |= startListener("127.0.0.1");
        any |= startListener("::1");
        return any;
    }

    private boolean startListener(String addr) {
        final ServerSocket ss;
        try {
            ss = new ServerSocket(80, 4, InetAddress.getByName(addr));
        } catch (IOException e) {
            Log.w(TAG, "Could not bind " + addr + ":80: " + e.getMessage());
            return false;
        }
        synchronized (sockets) {
            sockets.add(ss);
        }
        Thread t = new Thread(() -> {
            Log.i(TAG, "Login callback server listening on " + addr + ":80");
            while (running) {
                try {
                    handleSocket(ss.accept());
                } catch (IOException e) {
                    if (running) {
                        Log.w(TAG, "Callback accept failed on " + addr + ": " + e.getMessage());
                    }
                    break;
                }
            }
        }, "LoginCallback-" + addr);
        t.setDaemon(true);
        t.start();
        return true;
    }

    private void handleSocket(Socket socket) {
        try (Socket s = socket) {
            if (!s.getInetAddress().isLoopbackAddress()) {
                Log.w(TAG, "Rejecting callback connection from " + s.getInetAddress());
                return;
            }
            s.setSoTimeout(8000);
            byte[] body = readRequest(s.getInputStream());
            if (delivered) {
                writeResponse(s.getOutputStream(), "OK");
                return;
            }
            if (body != null && body.length > 0) {
                // Consent POST carrying the URL fragment (id_token=...&code=...&state=...)
                delivered = true;
                String fragment = new String(body, StandardCharsets.UTF_8);
                Log.i(TAG, "[5/6] Consent callback POST received (len=" + fragment.length() + ")");
                writeResponse(s.getOutputStream(), "OK");
                stop();
                listener.onConsentFragment(fragment);
            } else {
                writeResponse(s.getOutputStream(), PAGE);
            }
        } catch (Exception e) {
            Log.w(TAG, "Callback request handling failed: " + e.getMessage());
        }
    }

    /** Reads an HTTP/1.x request (request line + headers + body per Content-Length). */
    private static byte[] readRequest(InputStream in) throws IOException {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        byte[] buf = new byte[1];
        int seen = 0;
        int contentLength = -1;
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("Connection closed before headers");
            }
            header.write(b);
            if (b == '\n') {
                String line = new String(header.toByteArray(), StandardCharsets.ISO_8859_1)
                    .trim();
                header.reset();
                if (line.isEmpty()) {
                    break;
                }
                String lower = line.toLowerCase(java.util.Locale.US);
                if (lower.startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (seen++ > 65536) {
                throw new IOException("Header too large");
            }
        }
        if (contentLength <= 0) {
            return null;
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int remaining = contentLength;
        while (remaining > 0) {
            int n = in.read(chunk, 0, Math.min(chunk.length, remaining));
            if (n < 0) {
                throw new IOException("Connection closed mid-body");
            }
            body.write(chunk, 0, n);
            remaining -= n;
        }
        return body.toByteArray();
    }

    private static void writeResponse(OutputStream out, byte[] content) throws IOException {
        byte[] head = ("HTTP/1.0 200 OK\r\n"
            + "Content-Type: " + (content == PAGE ? "text/html; charset=utf-8" : "text/plain") + "\r\n"
            + "Content-Length: " + content.length + "\r\n"
            + "Connection: close\r\n"
            + "\r\n").getBytes(StandardCharsets.ISO_8859_1);
        out.write(head);
        out.write(content);
        out.flush();
    }

    private static void writeResponse(OutputStream out, String text) throws IOException {
        writeResponse(out, text.getBytes(StandardCharsets.UTF_8));
    }

    public void stop() {
        running = false;
        synchronized (sockets) {
            for (ServerSocket ss : sockets) {
                try {
                    ss.close();
                } catch (IOException ignored) {
                }
            }
            sockets.clear();
        }
    }
}
