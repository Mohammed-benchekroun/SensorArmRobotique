package roboticam;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class RobotBridgeService {
    private final RobotApi api;
    private final ExecutorService executor;

    public RobotBridgeService() {
        this.api = RobotApi.INSTANCE;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "robot-bridge-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<FlowEvent> initialize() {
        return submit("Startup", Arrays.asList("Initialize native bridge", "Load current state"), () -> {
            api.initialize_bridge();
            return api.get_current_snapshot();
        });
    }

    public CompletableFuture<FlowEvent> refreshSnapshot() {
        return submit("Refresh Snapshot", Arrays.asList("Read current position", "No DB insert"), api::get_current_snapshot);
    }

    public CompletableFuture<FlowEvent> setCoordinates(double x, double y) {
        return submit("Plot Manual", Arrays.asList(
                String.format("X: %.2f", x),
                String.format("Y: %.2f", y),
                "Action: set_coordinates_snapshot"
        ), () -> api.set_coordinates_snapshot(x, y));
    }

    public CompletableFuture<FlowEvent> movePoint(String direction, double dx, double dy) {
        return submit(direction, Arrays.asList(
                String.format("dx: %.2f", dx),
                String.format("dy: %.2f", dy),
                "Action: move_point_snapshot"
        ), () -> api.move_point_snapshot(dx, dy));
    }

    public CompletableFuture<FlowEvent> generateRandom() {
        return submit("Random Sample", Arrays.asList(
                "Source: native RNG",
                "Action: generate_random_snapshot",
                "DB insert: expected"
        ), api::generate_random_snapshot);
    }

    public void shutdown() {
        try {
            executor.submit(api::close_bridge).get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            api.close_bridge();
        } finally {
            executor.shutdownNow();
        }
    }

    private CompletableFuture<FlowEvent> submit(
            String actionName,
            List<String> requestLines,
            Supplier<RobotApi.BridgeSnapshot> supplier
    ) {
        return CompletableFuture.supplyAsync(() -> new FlowEvent(actionName, requestLines, supplier.get()), executor);
    }
}
