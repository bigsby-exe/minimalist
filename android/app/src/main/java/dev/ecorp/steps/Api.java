package dev.ecorp.steps;

import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class Api {
    static final class Failure extends Exception {
        final int status;
        Failure(int status, String message) { super(message); this.status = status; }
    }

    private final String token;

    Api(JSONObject state) { this(state.optString("token")); }
    Api(String token) { this.token = token; }

    JSONObject call(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(BuildConfig.SERVER_URL + path).openConnection();
        // Never forward the device credential to a redirect or another host.
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                try (var stream = connection.getOutputStream()) { stream.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int status = connection.getResponseCode();
            if (status == 204) return new JSONObject();
            String contentType = connection.getContentType();
            if (status >= 300 && status < 400 || contentType == null || !contentType.contains("application/json")) {
                throw new Failure(403, "The server did not return an API response. Try again or contact the Steps administrator.");
            }
            JSONObject result;
            try (InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream()) {
                if (stream == null) throw new Failure(status, "The server could not complete the request.");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                    if (bytes.size() > 8 * 1024 * 1024) throw new Failure(413, "Task list is too large to download.");
                }
                result = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            }
            if (status >= 400) throw new Failure(status, result.optString("error", "Request failed. Try again."));
            return result;
        } finally { connection.disconnect(); }
    }
}
