package example.fieldservice;

public final class FieldServiceWorkerTest {
    public static void main(String[] args) {
        var missingPhoto = new FieldServiceWorker.WorkOrder("WO-1042", "TECH-7", "COMPLETED", 0);
        var documented = new FieldServiceWorker.WorkOrder("WO-1043", "TECH-8", "COMPLETED", 2);

        assertTrue(FieldServiceWorker.decide(missingPhoto).required(), "completed work without photos needs follow-up");
        assertTrue(!FieldServiceWorker.decide(documented).required(), "photo evidence closes the documentation check");
        System.out.println("PASS: follow-up decision distinguishes missing and supplied work-order photos");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
