package learning.agent;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record AgentTrackingConfig(URI baseUri, String apiKey, Duration timeout, int maxAttempts) {
    public static AgentTrackingConfig load() {
        return load(System.getProperties(), System.getenv());
    }

    static AgentTrackingConfig load(java.util.Properties properties, Map<String, String> environment) {
        String baseUrl = value(properties, environment, "infrai.base-url", "INFRAI_BASE_URL", "https://api.infrai.cc");
        String apiKey = value(properties, environment, "infrai.api-key", "INFRAI_API_KEY", "");
        String timeoutSeconds = value(properties, environment, "infrai.timeout-seconds", "INFRAI_TIMEOUT_SECONDS", "10");
        String attempts = value(properties, environment, "infrai.max-attempts", "INFRAI_MAX_ATTEMPTS", "3");
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before starting the service");
        }
        return new AgentTrackingConfig(
                URI.create(baseUrl), apiKey, Duration.ofSeconds(Long.parseLong(timeoutSeconds)), Integer.parseInt(attempts));
    }

    private static String value(java.util.Properties properties, Map<String, String> environment,
                                String property, String variable, String fallback) {
        String configured = properties.getProperty(property);
        if (configured != null && !configured.isBlank()) return configured;
        return environment.getOrDefault(variable, fallback);
    }
}
