package myau.auth;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AuthHttpClient {
    private static final Gson GSON = new Gson();
    private static final String FIXED_BASE_URL = "http://127.0.0.1:23456/";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;

    public String getBaseHttpUrl() {
        return FIXED_BASE_URL;
    }

    public String register(String username, String hwidRaw, String licenseKey) throws IOException, AuthError {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("hwidRaw", hwidRaw);
        body.addProperty("licenseKey", licenseKey);
        JsonObject response = requestObject("POST", "/v1/auth/register", body, null, false);
        return getRequiredString(response, "accessToken");
    }

    public String login(String username, String hwidRaw) throws IOException, AuthError {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("hwidRaw", hwidRaw);
        JsonObject response = requestObject("POST", "/v1/auth/login", body, null, false);
        return getRequiredString(response, "accessToken");
    }

    public Map<String, Object> getMe(AuthSession session) throws IOException, AuthError {
        JsonObject response = requestObject("GET", "/v1/me", null, session, true);
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : response.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                map.put(entry.getKey(), null);
            } else if (value.isJsonPrimitive()) {
                if (value.getAsJsonPrimitive().isBoolean()) {
                    map.put(entry.getKey(), value.getAsBoolean());
                } else if (value.getAsJsonPrimitive().isNumber()) {
                    map.put(entry.getKey(), value.getAsNumber());
                } else {
                    map.put(entry.getKey(), value.getAsString());
                }
            } else {
                map.put(entry.getKey(), value.toString());
            }
        }
        return map;
    }

    public void sendPresenceHeartbeat(AuthSession session, String gameName, String ircName) throws IOException, AuthError {
        JsonObject body = new JsonObject();
        body.addProperty("gameName", gameName);
        body.addProperty("ircName", ircName);
        requestObject("POST", "/v1/irc/presence", body, session, true);
    }

    private JsonObject requestObject(String method, String path, JsonObject body, AuthSession session, boolean requireAuth) throws IOException, AuthError {
        RawResponse response = requestRaw(method, path, body, session, requireAuth);
        if (response.statusCode >= 200 && response.statusCode < 300) {
            if (StringUtils.isBlank(response.body)) {
                return new JsonObject();
            }
            return parseObject(response.body);
        }
        throw parseError(response.statusCode, response.body);
    }

    private RawResponse requestRaw(String method, String path, JsonObject body, AuthSession session, boolean requireAuth) throws IOException, AuthError {
        URI uri = resolve(path);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");

        String upperMethod = method.toUpperCase();
        connection.setRequestMethod(upperMethod);

        if (upperMethod.equals("POST") || upperMethod.equals("PUT")) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        }

        applyAuthHeaders(connection, session, requireAuth);

        if ((upperMethod.equals("POST") || upperMethod.equals("PUT")) && body != null) {
            try (OutputStream os = connection.getOutputStream()) {
                os.write(GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        }

        int statusCode = connection.getResponseCode();
        String responseBody;
        try {
            InputStream is = (statusCode >= 200 && statusCode < 300) ? connection.getInputStream() : connection.getErrorStream();
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    responseBody = sb.toString();
                }
            } else {
                responseBody = "";
            }
        } finally {
            connection.disconnect();
        }

        return new RawResponse(statusCode, responseBody);
    }

    private void applyAuthHeaders(HttpURLConnection connection, AuthSession session, boolean requireAuth) throws AuthError {
        if (session == null || !session.hasToken() || StringUtils.isBlank(session.getHwidRaw())) {
            if (requireAuth) {
                throw new AuthError("CLIENT_NOT_AUTHENTICATED", "Authenticated request requires access token and HWID", null, 0);
            }
            return;
        }
        connection.setRequestProperty("Authorization", "Bearer " + session.getAccessToken());
        connection.setRequestProperty("X-HWID", session.getHwidRaw());
    }

    private URI resolve(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return URI.create(path);
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return URI.create(FIXED_BASE_URL).resolve(normalized);
    }

    private static AuthError parseError(int status, String body) {
        String code = "HTTP_" + status;
        String message = "HTTP " + status;
        String requestId = null;
        try {
            JsonObject obj = parseObject(body);
            if (obj.has("code")) {
                code = obj.get("code").getAsString();
            }
            if (obj.has("message")) {
                message = obj.get("message").getAsString();
            }
            if (obj.has("requestId")) {
                requestId = obj.get("requestId").getAsString();
            }
        } catch (Exception ignored) {
            if (StringUtils.isNotBlank(body)) {
                message = body;
            }
        }
        System.err.println("[MyauAP] Auth API error code=" + code + " status=" + status + (requestId != null ? " requestId=" + requestId : ""));
        return new AuthError(code, message, requestId, status);
    }

    private static JsonObject parseObject(String json) {
        return new JsonParser().parse(json).getAsJsonObject();
    }

    private static String getRequiredString(JsonObject object, String key) throws AuthError {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            throw new AuthError("INVALID_RESPONSE", "Missing field: " + key, null, 0);
        }
        return object.get(key).getAsString();
    }

    private static class RawResponse {
        final int statusCode;
        final String body;

        RawResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}
