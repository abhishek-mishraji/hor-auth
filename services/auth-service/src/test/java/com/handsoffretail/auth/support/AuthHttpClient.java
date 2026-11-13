package com.handsoffretail.auth.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AuthHttpClient {
    private final URI baseUri;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, String> cookies = new LinkedHashMap<>();

    public AuthHttpClient(int port) {
        baseUri = URI.create("http://127.0.0.1:" + port);
    }

    public HttpResponse<String> login(String email, String password) throws IOException, InterruptedException {
        return send("POST", "/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", true, null);
    }

    public HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send("GET", path, null, true, null);
    }

    public HttpResponse<String> post(String path) throws IOException, InterruptedException {
        return send("POST", path, null, true, null);
    }

    public HttpResponse<String> postWithoutCsrf(String path) throws IOException, InterruptedException {
        return send("POST", path, null, false, null);
    }

    public HttpResponse<String> postWithOrigin(String path, String origin) throws IOException, InterruptedException {
        return send("POST", path, null, true, origin);
    }

    public HttpResponse<String> send(String method, String path, String body, boolean csrf, String origin)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path)).timeout(Duration.ofSeconds(10));
        request.header("Accept", "application/json, application/problem+json");
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (csrf && isWrite(method)) {
            request.header("X-HOR-Request", "1");
        }
        if (isWrite(method)) {
            request.header("Origin", origin == null ? "http://localhost:5180" : origin);
        }
        String cookieHeader = cookieHeader();
        if (!cookieHeader.isEmpty()) {
            request.header("Cookie", cookieHeader);
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        updateCookies(response.headers().allValues("Set-Cookie"));
        return response;
    }

    public String cookieHeader() {
        return cookies.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    public Map<String, String> snapshotCookies() {
        return Map.copyOf(cookies);
    }

    public java.util.Set<String> cookieNames() {
        return java.util.Set.copyOf(cookies.keySet());
    }

    public String cookieValue(String name) {
        return cookies.get(name);
    }

    public void restoreCookies(Map<String, String> snapshot) {
        cookies.clear();
        cookies.putAll(snapshot);
    }

    private void updateCookies(List<String> headers) {
        for (String header : headers) {
            String pair = header.substring(0, header.indexOf(';') < 0 ? header.length() : header.indexOf(';'));
            int separator = pair.indexOf('=');
            if (separator < 1) {
                continue;
            }
            String name = pair.substring(0, separator);
            String value = pair.substring(separator + 1);
            if (header.toLowerCase(java.util.Locale.ROOT).contains("max-age=0")) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }

    private boolean isWrite(String method) {
        return List.of("POST", "PUT", "PATCH", "DELETE").contains(method);
    }
}
