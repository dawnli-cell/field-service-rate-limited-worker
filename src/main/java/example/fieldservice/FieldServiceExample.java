package example.fieldservice;

public final class FieldServiceExample {
    private FieldServiceExample() {}

    public static void main(String[] args) throws Exception {
        WorkerConfiguration configuration = WorkerConfiguration.fromEnvironment();
        InfraiQueueClient infrai = new InfraiQueueClient(configuration.apiKey(), configuration.queue());
        int consumed = new FieldServiceWorker(infrai, configuration).runOnce();
        System.out.println("processed_messages=" + consumed);
    }
}
