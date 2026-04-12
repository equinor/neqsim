package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Progress tracking for long-running MCP simulations.
 *
 * <p>
 * Provides a simple mechanism for runners to report progress that can be polled by agents via the
 * {@code get_progress} tool. Each long-running operation creates a progress tracker identified by
 * an operation ID. The tracker stores milestone events and percentage completion.
 * </p>
 *
 * <p>
 * Usage in a runner:
 * </p>
 *
 * <pre>
 * String opId = ProgressTracker.start("dynamic_simulation", 100);
 * for (int step = 0; step &lt; 100; step++) {
 *   // ... do work ...
 *   ProgressTracker.update(opId, step + 1, "Completed step " + (step + 1));
 * }
 * ProgressTracker.complete(opId, "Simulation finished successfully");
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ProgressTracker {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Active progress trackers keyed by operation ID. */
  private static final ConcurrentHashMap<String, OperationProgress> OPERATIONS =
      new ConcurrentHashMap<String, OperationProgress>();

  /** Counter for generating unique operation IDs. */
  private static long nextId = 1;

  /**
   * Private constructor.
   */
  private ProgressTracker() {}

  /**
   * Starts tracking a new long-running operation.
   *
   * @param operationType the type of operation (e.g., "dynamic_simulation", "parametric_sweep")
   * @param totalSteps the total number of steps expected
   * @return the operation ID for polling
   */
  public static synchronized String start(String operationType, int totalSteps) {
    String opId = "op_" + (nextId++);
    OperationProgress progress = new OperationProgress(opId, operationType, totalSteps);
    OPERATIONS.put(opId, progress);

    // Evict old completed operations (keep last 100)
    if (OPERATIONS.size() > 100) {
      evictOldOperations();
    }

    return opId;
  }

  /**
   * Updates progress for an operation.
   *
   * @param operationId the operation ID
   * @param currentStep the current step number (1-based)
   * @param message a status message
   */
  public static void update(String operationId, int currentStep, String message) {
    OperationProgress progress = OPERATIONS.get(operationId);
    if (progress != null) {
      progress.currentStep = currentStep;
      progress.lastMessage = message;
      progress.percentComplete =
          progress.totalSteps > 0 ? (currentStep * 100) / progress.totalSteps : 0;
      progress.milestones.add(new Milestone(currentStep, message));

      // Keep only last 20 milestones to bound memory
      if (progress.milestones.size() > 20) {
        progress.milestones.remove(0);
      }
    }
  }

  /**
   * Marks an operation as complete.
   *
   * @param operationId the operation ID
   * @param finalMessage the completion message
   */
  public static void complete(String operationId, String finalMessage) {
    OperationProgress progress = OPERATIONS.get(operationId);
    if (progress != null) {
      progress.currentStep = progress.totalSteps;
      progress.percentComplete = 100;
      progress.lastMessage = finalMessage;
      progress.completed = true;
      progress.completedAt = System.currentTimeMillis();
    }
  }

  /**
   * Marks an operation as failed.
   *
   * @param operationId the operation ID
   * @param errorMessage the error message
   */
  public static void fail(String operationId, String errorMessage) {
    OperationProgress progress = OPERATIONS.get(operationId);
    if (progress != null) {
      progress.lastMessage = "FAILED: " + errorMessage;
      progress.completed = true;
      progress.failed = true;
      progress.completedAt = System.currentTimeMillis();
    }
  }

  /**
   * Gets the current progress for an operation.
   *
   * @param operationId the operation ID
   * @return JSON with progress details
   */
  public static String getProgress(String operationId) {
    OperationProgress progress = OPERATIONS.get(operationId);
    if (progress == null) {
      JsonObject error = new JsonObject();
      error.addProperty("status", "error");
      error.addProperty("message", "Operation not found: " + operationId);
      return GSON.toJson(error);
    }

    return GSON.toJson(progress.toJson());
  }

  /**
   * Lists all active (non-completed) operations.
   *
   * @return JSON with active operations
   */
  public static String listActive() {
    JsonObject response = new JsonObject();
    response.addProperty("status", "success");

    JsonArray active = new JsonArray();
    for (OperationProgress op : OPERATIONS.values()) {
      if (!op.completed) {
        active.add(op.toJson());
      }
    }
    response.addProperty("activeCount", active.size());
    response.add("operations", active);

    return GSON.toJson(response);
  }

  /**
   * Removes completed operations older than 5 minutes.
   */
  private static void evictOldOperations() {
    long cutoff = System.currentTimeMillis() - (5 * 60 * 1000);
    List<String> toRemove = new ArrayList<String>();
    for (java.util.Map.Entry<String, OperationProgress> entry : OPERATIONS.entrySet()) {
      if (entry.getValue().completed && entry.getValue().completedAt < cutoff) {
        toRemove.add(entry.getKey());
      }
    }
    for (String key : toRemove) {
      OPERATIONS.remove(key);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Inner types
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Progress state for a single operation.
   */
  static class OperationProgress {
    /** Operation ID. */
    final String operationId;
    /** Operation type. */
    final String operationType;
    /** Total expected steps. */
    final int totalSteps;
    /** Start time. */
    final long startedAt;
    /** Current step. */
    int currentStep = 0;
    /** Percentage complete. */
    int percentComplete = 0;
    /** Last status message. */
    String lastMessage = "Starting...";
    /** Whether completed. */
    boolean completed = false;
    /** Whether failed. */
    boolean failed = false;
    /** Completion time. */
    long completedAt = 0;
    /** Milestone events. */
    final List<Milestone> milestones = new ArrayList<Milestone>();

    /**
     * Creates operation progress.
     *
     * @param operationId the operation ID
     * @param operationType the type
     * @param totalSteps the total steps
     */
    OperationProgress(String operationId, String operationType, int totalSteps) {
      this.operationId = operationId;
      this.operationType = operationType;
      this.totalSteps = totalSteps;
      this.startedAt = System.currentTimeMillis();
    }

    /**
     * Converts to JSON.
     *
     * @return JsonObject representation
     */
    JsonObject toJson() {
      JsonObject obj = new JsonObject();
      obj.addProperty("operationId", operationId);
      obj.addProperty("operationType", operationType);
      obj.addProperty("currentStep", currentStep);
      obj.addProperty("totalSteps", totalSteps);
      obj.addProperty("percentComplete", percentComplete);
      obj.addProperty("message", lastMessage);
      obj.addProperty("completed", completed);
      obj.addProperty("failed", failed);
      obj.addProperty("elapsedMs", System.currentTimeMillis() - startedAt);

      if (!milestones.isEmpty()) {
        JsonArray ms = new JsonArray();
        for (Milestone m : milestones) {
          JsonObject mObj = new JsonObject();
          mObj.addProperty("step", m.step);
          mObj.addProperty("message", m.message);
          ms.add(mObj);
        }
        obj.add("recentMilestones", ms);
      }

      return obj;
    }
  }

  /**
   * A milestone event.
   */
  static class Milestone {
    /** Step number. */
    final int step;
    /** Milestone message. */
    final String message;

    /**
     * Creates a milestone.
     *
     * @param step the step number
     * @param message the message
     */
    Milestone(int step, String message) {
      this.step = step;
      this.message = message;
    }
  }
}
