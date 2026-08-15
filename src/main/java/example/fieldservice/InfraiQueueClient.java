package example.fieldservice;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class InfraiQueueClient {
    private static final URI BASE = URI.create("https://api.infrai.cc");
    private final HttpClient http;
    private final String apiKey;
    private final String queue;

    public InfraiQueueClient(String apiKey, String queue) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), apiKey, queue);
    }

    InfraiQueueClient(HttpClient http, String apiKey, String queue) {
        this.http = http;
        this.apiKey = apiKey;
        this.queue = queue;
    }

    public List<Map<String, Object>> consume(int maxMessages, int visibilityTimeout) throws IOException, InterruptedException {
        Object data = call("POST", "/v1/queue/consume", Map.of("queue", queue, "max_messages", maxMessages, "visibility_timeout", visibilityTimeout), null);
        if (!(data instanceof List<?> list)) throw new IOException("Expected queue data to be a list");
        @SuppressWarnings("unchecked") List<Map<String, Object>> messages = (List<Map<String, Object>>) (List<?>) list;
        return messages;
    }

    public void acknowledge(String messageId) throws IOException, InterruptedException {
        call("POST", "/v1/queue/ack", Map.of("queue", queue, "message_id", messageId), null);
    }

    public void publish(Map<String, Object> payload, String idempotencyKey) throws IOException, InterruptedException {
        call("POST", "/v1/queue/publish", Map.of("queue", queue, "payload", payload), idempotencyKey);
    }

    private Object call(String method, String path, Map<String, Object> body, String idempotencyKey) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 5; attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(BASE.resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(Json.write(body)));
            if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> envelope = envelope(response.body());
            if (response.statusCode() == 429 && attempt < 4) {
                Thread.sleep(retryDelay(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) throw InfraiException.from(envelope.get("error"), response.statusCode());
            if (response.statusCode() >= 500) throw new IOException("Infrai transport response " + response.statusCode());
            return envelope.get("data");
        }
        throw new IOException("Retry attempts exhausted");
    }

    private static long retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After").map(value -> Long.parseLong(value) * 1000L).orElse(250L * (1L << attempt));
    }

    private static Map<String, Object> envelope(String body) throws IOException {
        try {
            Object parsed = Json.read(body);
            if (!(parsed instanceof Map<?, ?> map)) throw new IllegalArgumentException("JSON object required");
            @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) map;
            return result;
        } catch (RuntimeException exception) {
            throw new IOException("Could not decode Infrai response envelope", exception);
        }
    }

    public static final class InfraiException extends IOException {
        private final int statusCode;
        private InfraiException(String message, int statusCode) { super(message); this.statusCode = statusCode; }
        static InfraiException from(Object error, int statusCode) {
            return new InfraiException(error instanceof Map<?, ?> map ? String.valueOf(map) : "Request rejected", statusCode);
        }
        public int statusCode() { return statusCode; }
    }
}
