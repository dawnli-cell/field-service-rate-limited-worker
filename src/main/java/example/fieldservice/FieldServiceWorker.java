package example.fieldservice;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.ArrayList;

public final class FieldServiceWorker {
    private final InfraiQueueClient infrai;
    private final WorkerConfiguration configuration;

    public FieldServiceWorker(InfraiQueueClient infrai, WorkerConfiguration configuration) {
        this.infrai = infrai;
        this.configuration = configuration;
    }

    public int runOnce() throws IOException, InterruptedException {
        List<Map<String, Object>> messages = infrai.consume(configuration.maxMessages(), configuration.visibilityTimeoutSeconds());
        ExecutorService workers = Executors.newFixedThreadPool(configuration.concurrency());
        try {
            List<Future<Void>> results = new ArrayList<>();
            for (Map<String, Object> message : messages) {
                results.add(workers.submit(() -> {
                    process(message);
                    return null;
                }));
            }
            workers.shutdown();
            for (Future<Void> result : results) await(result);
        } finally {
            workers.shutdownNow();
        }
        return messages.size();
    }

    private void process(Map<String, Object> message) throws IOException, InterruptedException {
        String messageId = requiredString(message, "message_id");
        Object rawPayload = message.get("payload");
        if (!(rawPayload instanceof Map<?, ?> payload)) throw new IllegalArgumentException("payload must be an object");
        WorkOrder order = WorkOrder.from(payload);
        FollowUpDecision decision = decide(order);
        infrai.publish(decision.asPayload(), "follow-up-" + order.workOrderId());
        infrai.acknowledge(messageId);
        System.out.printf("work_order=%s dispatch=%s follow_up=%s%n", order.workOrderId(), order.dispatchStatus(), decision.required());
    }

    private static void await(Future<Void> result) throws IOException, InterruptedException {
        try {
            result.get();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException io) throw io;
            if (cause instanceof InterruptedException interrupted) throw interrupted;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IOException("Queue worker failed", cause);
        }
    }

    public static FollowUpDecision decide(WorkOrder order) {
        boolean required = "COMPLETED".equals(order.dispatchStatus()) && order.photoCount() == 0;
        String reason = required ? "COMPLETED_WITHOUT_PHOTO" : "DOCUMENTATION_ACCEPTED";
        return new FollowUpDecision(order.workOrderId(), order.technicianId(), required, reason);
    }

    private static String requiredString(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(key + " must be a string");
        return text;
    }

    public record WorkOrder(String workOrderId, String technicianId, String dispatchStatus, int photoCount) {
        static WorkOrder from(Map<?, ?> payload) {
            Object photos = payload.get("photos");
            if (!(photos instanceof List<?> list)) throw new IllegalArgumentException("photos must be a list");
            return new WorkOrder(requiredString(payload, "work_order_id"), requiredString(payload, "technician_id"), requiredString(payload, "dispatch_status"), list.size());
        }
    }

    public record FollowUpDecision(String workOrderId, String technicianId, boolean required, String reason) {
        Map<String, Object> asPayload() {
            return Map.of("work_order_id", workOrderId, "technician_id", technicianId, "follow_up_required", required, "reason", reason);
        }
    }
}
