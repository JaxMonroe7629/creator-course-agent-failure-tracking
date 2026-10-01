package learning.agent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiErrors implements FailureCapture {
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern ERROR = Pattern.compile("\\\"error\\\"\\s*:\\s*\\{([^}]*)}");
    private static final Pattern STRING_FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");

    private final AgentTrackingConfig config;
    private final HttpClient http;

    public InfraiErrors(AgentTrackingConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.timeout()).build());
    }

    InfraiErrors(AgentTrackingConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public void capture(AgentFailure failure) throws IOException, InterruptedException {
        Map<String, Object> payload = Map.of(
                "title", failure.stage() + " failed for " + failure.assetId(),
                "message", failure.message(),
                "exception", failure.exception(),
                "level", "error",
                "fingerprint", List.of(failure.courseId(), failure.assetId(), failure.stage()),
                "context", Map.of(
                        "run_id", failure.runId(),
                        "course_id", failure.courseId(),
                        "asset_id", failure.assetId(),
                        "stage", failure.stage()),
                "service", "creator-commerce-agent",
                "idempotency_key", stableKey(failure));
        post("/v1/errors/capture", Json.write(payload));
    }

    private void post(String path, String json) throws IOException, InterruptedException {
        Duration delay = Duration.ofMillis(250);
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUri().resolve(path))
                    .timeout(config.timeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .method("POST", HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Envelope envelope = Envelope.decode(response.body());
            if (response.statusCode() == 429 && attempt < config.maxAttempts()) {
                delay = retryDelay(response, delay);
                Thread.sleep(delay.toMillis());
                delay = delay.multipliedBy(2);
                continue;
            }
            if (!envelope.ok()) {
                throw new InfraiException(envelope.code(), envelope.message(), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Infrai transport status " + response.statusCode());
            }
            return;
        }
    }

    private static Duration retryDelay(HttpResponse<?> response, Duration fallback) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try {
                return java.util.Optional.of(Duration.ofSeconds(Long.parseLong(value)));
            } catch (NumberFormatException ignored) {
                return java.util.Optional.empty();
            }
        }).orElse(fallback);
    }

    private static String stableKey(AgentFailure failure) {
        String source = failure.runId() + ":" + failure.assetId() + ":" + failure.stage();
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private record Envelope(boolean ok, String code, String message) {
        static Envelope decode(String json) throws IOException {
            Matcher okMatch = OK.matcher(json);
            if (!okMatch.find()) throw new IOException("Response is not an Infrai envelope");
            if (Boolean.parseBoolean(okMatch.group(1))) return new Envelope(true, "", "");
            Matcher errorMatch = ERROR.matcher(json);
            if (!errorMatch.find()) return new Envelope(false, "UNKNOWN", "Request rejected");
            String code = "UNKNOWN";
            String message = "Request rejected";
            Matcher fields = STRING_FIELD.matcher(errorMatch.group(1));
            while (fields.find()) {
                if (fields.group(1).equals("code")) code = fields.group(2);
                if (fields.group(1).equals("message") || fields.group(1).equals("hint")) message = fields.group(2);
            }
            return new Envelope(false, code, message);
        }
    }

    private static final class Json {
        static String write(Object value) {
            if (value instanceof String text) return "\"" + escape(text) + "\"";
            if (value instanceof Map<?, ?> map) {
                return map.entrySet().stream()
                        .map(entry -> write(entry.getKey().toString()) + ":" + write(entry.getValue()))
                        .collect(java.util.stream.Collectors.joining(",", "{", "}"));
            }
            if (value instanceof Iterable<?> values) {
                java.util.ArrayList<String> items = new java.util.ArrayList<>();
                values.forEach(item -> items.add(write(item)));
                return String.join(",", items).transform(body -> "[" + body + "]");
            }
            throw new IllegalArgumentException("Unsupported JSON value");
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }
    }
}

final class InfraiException extends IOException {
    private final String code;
    private final int status;

    InfraiException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    String code() { return code; }
    int status() { return status; }
}
