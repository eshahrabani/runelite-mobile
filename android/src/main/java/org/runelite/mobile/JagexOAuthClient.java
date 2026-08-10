package org.runelite.mobile;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Jagex account OAuth client for the "Jagex Launcher" flow.
 *
 * Two-leg authorization-code-with-PKCE flow, reverse-engineered and live-verified
 * by the community (see docs in cross_platform_osrs_launcher / native-linux-jagex-launcher):
 *
 *   Leg 1 - launcher client: authorize -> capture "code" at the launcher redirect URI,
 *           exchange for tokens, decode id_token.login_provider to pick the account path.
 *   Leg 2 - consent client (Jagex accounts only): authorize with response_type
 *           "id_token code" -> id_token arrives in the URL FRAGMENT, create the game
 *           session from it, then list characters.
 *
 * Launch contract (verified): set EXACTLY JX_SESSION_ID / JX_CHARACTER_ID /
 * JX_DISPLAY_NAME. Never set JX_ACCESS_TOKEN / JX_REFRESH_TOKEN alongside them -
 * the client takes the wrong login branch and fails.
 */
public class JagexOAuthClient {
    private static final String TAG = "RuneLiteMobile";

    public static final String AUTHORIZE_ENDPOINT = "https://account.jagex.com/oauth2/auth";
    public static final String TOKEN_ENDPOINT = "https://account.jagex.com/oauth2/token";
    public static final String LAUNCHER_CLIENT_ID = "com_jagex_auth_desktop_launcher";
    public static final String LAUNCHER_REDIRECT_URI = "https://secure.runescape.com/m=weblogin/launcher-redirect";
    public static final String LAUNCHER_SCOPE = "openid offline gamesso.token.create user.profile.read";
    public static final String CONSENT_CLIENT_ID = "1fddee4e-b100-4f4e-b2b0-097f9088f9d2";
    public static final String CONSENT_REDIRECT_URI = "http://localhost";
    public static final String SESSIONS_ENDPOINT = "https://auth.jagex.com/game-session/v1/sessions";
    public static final String ACCOUNTS_ENDPOINT = "https://auth.jagex.com/game-session/v1/accounts";

    public static class Tokens {
        public String accessToken;
        public String refreshToken;
        public String idToken;
        public long expiresAtMillis;
    }

    public static class Account {
        public String accountId;
        public String displayName;
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    // ── PKCE helpers ────────────────────────────────────────────────────────

    public static String generateVerifier() {
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        return base64Url(bytes);
    }

    public static String createChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return base64Url(digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    public static String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return base64Url(bytes);
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ── Authorize URL builders ──────────────────────────────────────────────

    public static String buildLauncherAuthorizeUrl(String state, String nonce, String codeChallenge) {
        return buildAuthorizeUrl(AUTHORIZE_ENDPOINT, LAUNCHER_CLIENT_ID, LAUNCHER_REDIRECT_URI,
            LAUNCHER_SCOPE, "code", state, nonce, codeChallenge, "login");
    }

    public static String buildConsentAuthorizeUrl(String state, String nonce) {
        return buildAuthorizeUrl(AUTHORIZE_ENDPOINT, CONSENT_CLIENT_ID, CONSENT_REDIRECT_URI,
            "openid offline", "id_token code", state, nonce, null, null);
    }

    private static String buildAuthorizeUrl(String endpoint, String clientId, String redirectUri,
                                            String scope, String responseType, String state,
                                            String nonce, String codeChallenge, String prompt) {
        StringBuilder sb = new StringBuilder(endpoint).append('?');
        appendParam(sb, "response_type", responseType);
        appendParam(sb, "client_id", clientId);
        appendParam(sb, "redirect_uri", redirectUri);
        appendParam(sb, "scope", scope);
        appendParam(sb, "state", state);
        appendParam(sb, "nonce", nonce);
        if (codeChallenge != null) {
            appendParam(sb, "code_challenge", codeChallenge);
            appendParam(sb, "code_challenge_method", "S256");
        }
        if (prompt != null) {
            appendParam(sb, "prompt", prompt);
        }
        return sb.toString();
    }

    private static void appendParam(StringBuilder sb, String key, String value) {
        if (sb.charAt(sb.length() - 1) != '?') {
            sb.append('&');
        }
        sb.append(urlEncode(key)).append('=').append(urlEncode(value));
    }

    // ── Token exchange ──────────────────────────────────────────────────────

    public static Tokens exchangeCode(String code, String codeVerifier) throws IOException {
        String form = "grant_type=authorization_code"
            + "&code=" + urlEncode(code)
            + "&redirect_uri=" + urlEncode(LAUNCHER_REDIRECT_URI)
            + "&client_id=" + urlEncode(LAUNCHER_CLIENT_ID)
            + "&code_verifier=" + urlEncode(codeVerifier);
        return postTokenForm(form);
    }

    public static Tokens refreshTokens(String refreshToken) throws IOException {
        String form = "grant_type=refresh_token"
            + "&refresh_token=" + urlEncode(refreshToken)
            + "&client_id=" + urlEncode(LAUNCHER_CLIENT_ID);
        return postTokenForm(form);
    }

    private static Tokens postTokenForm(String form) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(TOKEN_ENDPOINT).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);
        byte[] body = form.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(body.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
        int code = conn.getResponseCode();
        String response = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (code >= 400) {
            throw new IOException("Token endpoint returned " + code + ": " + truncate(response));
        }
        try {
            JSONObject json = new JSONObject(response);
            Tokens tokens = new Tokens();
            tokens.accessToken = json.optString("access_token", "");
            tokens.refreshToken = json.optString("refresh_token", null);
            tokens.idToken = json.optString("id_token", null);
            int expiresIn = json.optInt("expires_in", 0);
            tokens.expiresAtMillis = expiresIn > 0 ? System.currentTimeMillis() + expiresIn * 1000L : 0;
            if (tokens.accessToken.isEmpty() || tokens.idToken == null) {
                throw new IOException("Token response missing fields: " + truncate(response));
            }
            return tokens;
        } catch (org.json.JSONException e) {
            throw new IOException("Malformed token response: " + truncate(response), e);
        }
    }

    // ── id_token claims ─────────────────────────────────────────────────────

    public static String loginProvider(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                return "";
            }
            String payload = parts[1];
            byte[] decoded = Base64.getUrlDecoder().decode(payload);
            JSONObject json = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
            return json.optString("login_provider", "");
        } catch (Exception e) {
            Log.w(TAG, "Failed to decode id_token claims", e);
            return "";
        }
    }

    // ── Game session ────────────────────────────────────────────────────────

    public static String createSession(String idToken) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(SESSIONS_ENDPOINT).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);
        byte[] body = ("{\"idToken\":\"" + escapeJson(idToken) + "\"}").getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(body.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
        int code = conn.getResponseCode();
        String response = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (code >= 400) {
            throw new IOException("Session endpoint returned " + code + ": " + truncate(response));
        }
        try {
            JSONObject json = new JSONObject(response);
            String sessionId = json.optString("sessionId", "");
            if (sessionId.isEmpty()) {
                throw new IOException("Session response missing sessionId: " + truncate(response));
            }
            return sessionId;
        } catch (org.json.JSONException e) {
            throw new IOException("Malformed session response: " + truncate(response), e);
        }
    }

    public static List<Account> listAccounts(String sessionId) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(ACCOUNTS_ENDPOINT).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + sessionId);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        int code = conn.getResponseCode();
        String response = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (code >= 400) {
            throw new IOException("Accounts endpoint returned " + code + ": " + truncate(response));
        }
        try {
            JSONArray array = new JSONArray(response);
            List<Account> accounts = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                Account account = new Account();
                account.accountId = obj.optString("accountId", "");
                account.displayName = obj.optString("displayName", "");
                if (!account.accountId.isEmpty()) {
                    accounts.add(account);
                }
            }
            return accounts;
        } catch (org.json.JSONException e) {
            throw new IOException("Malformed accounts response: " + truncate(response), e);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String readStream(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() > 300 ? value.substring(0, 300) : value;
    }
}
