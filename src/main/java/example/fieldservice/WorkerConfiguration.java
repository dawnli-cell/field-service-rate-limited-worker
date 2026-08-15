package example.fieldservice;

public record WorkerConfiguration(String apiKey, String queue, int concurrency, int maxMessages, int visibilityTimeoutSeconds) {
    public static WorkerConfiguration fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY before starting the worker");
        String queue = System.getenv("QUEUE_NAME");
        if (queue == null || queue.isBlank()) throw new IllegalStateException("Set QUEUE_NAME before starting the worker");
        return new WorkerConfiguration(key, queue, integer("WORKER_CONCURRENCY", 4), integer("MAX_MESSAGES", 4), integer("VISIBILITY_TIMEOUT", 60));
    }

    private static int integer(String name, int fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : Integer.parseInt(value);
    }
}
