package neqsim.process.equipment.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Diagnoses convergence issues in process systems with recycle loops and adjusters.
 *
 * <p>
 * Inspects all {@link Recycle} and {@link Adjuster} units in a ProcessSystem to identify which
 * loops are converged, which are stuck, and provides actionable remediation suggestions. Designed
 * for both programmatic use and AI agent troubleshooting.
 * </p>
 *
 * <h2>Usage:</h2>
 *
 * <pre>
 * ProcessSystem process = ...;
 * process.run();
 *
 * ConvergenceDiagnostics diag = new ConvergenceDiagnostics(process);
 * ConvergenceDiagnostics.DiagnosticReport report = diag.analyze();
 *
 * if (!report.isConverged()) {
 *   System.out.println(report.toJson());
 *   for (String suggestion : report.getSuggestions()) {
 *     System.out.println("  - " + suggestion);
 *   }
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see Recycle
 * @see Adjuster
 */
public class ConvergenceDiagnostics implements Serializable {

  /** Serialization version. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(ConvergenceDiagnostics.class);

  /** The process system to diagnose. */
  private final ProcessSystem process;

  /**
   * Creates a convergence diagnostics analyzer for the given process.
   *
   * @param process the process system to analyze
   */
  public ConvergenceDiagnostics(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("ProcessSystem cannot be null");
    }
    this.process = process;
  }

  /**
   * Analyzes convergence of all recycle and adjuster units.
   *
   * @return diagnostic report with per-unit status and suggestions
   */
  public DiagnosticReport analyze() {
    List<RecycleStatus> recycleStatuses = new ArrayList<>();
    List<AdjusterStatus> adjusterStatuses = new ArrayList<>();
    List<String> suggestions = new ArrayList<>();
    boolean allConverged = true;

    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof Recycle) {
        Recycle recycle = (Recycle) unit;
        RecycleStatus status = analyzeRecycle(recycle);
        recycleStatuses.add(status);
        if (!status.converged) {
          allConverged = false;
          suggestions.addAll(generateRecycleSuggestions(status));
        }
      }

      if (unit instanceof Adjuster) {
        Adjuster adjuster = (Adjuster) unit;
        AdjusterStatus status = analyzeAdjuster(adjuster);
        adjusterStatuses.add(status);
        if (!status.converged) {
          allConverged = false;
          suggestions.addAll(generateAdjusterSuggestions(status));
        }
      }
    }

    if (recycleStatuses.isEmpty() && adjusterStatuses.isEmpty()) {
      suggestions.add("No recycle or adjuster units found — this is a feed-forward process.");
    }

    return new DiagnosticReport(allConverged, recycleStatuses, adjusterStatuses, suggestions);
  }

  /**
   * Analyzes a single recycle unit.
   *
   * @param recycle the recycle to analyze
   * @return status of the recycle
   */
  private RecycleStatus analyzeRecycle(Recycle recycle) {
    double flowError = recycle.getErrorFlow();
    double tempError = recycle.getErrorTemperature();
    double pressError = recycle.getErrorPressure();
    double compError = recycle.getErrorComposition();
    int iterations = recycle.getIterations();

    double flowTol = recycle.getFlowTolerance();
    double tempTol = recycle.getTemperatureTolerance();

    boolean converged = Math.abs(flowError) <= flowTol && Math.abs(tempError) <= tempTol;

    String dominantError = "flow";
    double maxError = Math.abs(flowError);
    if (Math.abs(tempError) > maxError) {
      dominantError = "temperature";
      maxError = Math.abs(tempError);
    }
    if (Math.abs(pressError) > maxError) {
      dominantError = "pressure";
      maxError = Math.abs(pressError);
    }
    if (Math.abs(compError) > maxError) {
      dominantError = "composition";
    }

    return new RecycleStatus(recycle.getName(), converged, flowError, tempError, pressError,
        compError, iterations, dominantError, flowTol, tempTol);
  }

  /**
   * Analyzes a single adjuster unit.
   *
   * @param adjuster the adjuster to analyze
   * @return status of the adjuster
   */
  private AdjusterStatus analyzeAdjuster(Adjuster adjuster) {
    double error = adjuster.getError();
    double tolerance = adjuster.getTolerance();
    boolean converged = Math.abs(error) <= tolerance;

    return new AdjusterStatus(adjuster.getName(), converged, error, tolerance, -1);
  }

  /**
   * Generates remediation suggestions for an unconverged recycle.
   *
   * @param status the recycle status
   * @return list of suggestions
   */
  private List<String> generateRecycleSuggestions(RecycleStatus status) {
    List<String> suggestions = new ArrayList<>();

    suggestions.add("Recycle '" + status.name + "' did not converge (dominant error: "
        + status.dominantError + ").");

    if ("flow".equals(status.dominantError)) {
      suggestions.add("  -> Try increasing flow tolerance with setFlowTolerance() " + "(current: "
          + status.flowTolerance + ").");
      suggestions.add("  -> Check for accumulation or depletion in connected equipment.");
    }

    if ("temperature".equals(status.dominantError)) {
      suggestions.add("  -> Try increasing temperature tolerance with setTemperatureTolerance() "
          + "(current: " + status.tempTolerance + ").");
      suggestions.add("  -> Add or adjust heat exchangers in the recycle loop.");
    }

    if ("composition".equals(status.dominantError)) {
      suggestions.add("  -> Check that all components are properly initialized in feed streams.");
      suggestions
          .add("  -> Try adding compositional damping by reducing recycle update acceleration.");
    }

    if (status.iterations >= 10) {
      suggestions.add("  -> Max iterations reached (" + status.iterations
          + "). Try increasing maxIterations on the Recycle.");
    }

    return suggestions;
  }

  /**
   * Generates remediation suggestions for an unconverged adjuster.
   *
   * @param status the adjuster status
   * @return list of suggestions
   */
  private List<String> generateAdjusterSuggestions(AdjusterStatus status) {
    List<String> suggestions = new ArrayList<>();

    suggestions.add("Adjuster '" + status.name + "' did not converge (error: " + status.error
        + ", tolerance: " + status.tolerance + ").");
    suggestions.add("  -> Try widening the search range or increasing max iterations.");
    suggestions.add("  -> Verify that the adjusted variable actually affects the target variable.");

    if (status.iterations >= 50) {
      suggestions.add("  -> Adjuster may be oscillating. Try adding damping or narrowing range.");
    }

    return suggestions;
  }

  // ============================================================
  // Result data classes
  // ============================================================

  /**
   * Status of a single Recycle unit.
   */
  public static class RecycleStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Recycle unit name. */
    public final String name;
    /** Whether the recycle has converged. */
    public final boolean converged;
    /** Flow error (relative). */
    public final double flowError;
    /** Temperature error (relative). */
    public final double tempError;
    /** Pressure error (relative). */
    public final double pressError;
    /** Composition error (relative). */
    public final double compError;
    /** Number of iterations used. */
    public final int iterations;
    /** Which error type is dominant. */
    public final String dominantError;
    /** Flow tolerance setting. */
    public final double flowTolerance;
    /** Temperature tolerance setting. */
    public final double tempTolerance;

    /**
     * Creates a recycle status.
     *
     * @param name recycle name
     * @param converged whether converged
     * @param flowError flow error
     * @param tempError temperature error
     * @param pressError pressure error
     * @param compError composition error
     * @param iterations iteration count
     * @param dominantError dominant error type
     * @param flowTolerance flow tolerance
     * @param tempTolerance temperature tolerance
     */
    RecycleStatus(String name, boolean converged, double flowError, double tempError,
        double pressError, double compError, int iterations, String dominantError,
        double flowTolerance, double tempTolerance) {
      this.name = name;
      this.converged = converged;
      this.flowError = flowError;
      this.tempError = tempError;
      this.pressError = pressError;
      this.compError = compError;
      this.iterations = iterations;
      this.dominantError = dominantError;
      this.flowTolerance = flowTolerance;
      this.tempTolerance = tempTolerance;
    }
  }

  /**
   * Status of a single Adjuster unit.
   */
  public static class AdjusterStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Adjuster unit name. */
    public final String name;
    /** Whether the adjuster has converged. */
    public final boolean converged;
    /** Current error. */
    public final double error;
    /** Convergence tolerance. */
    public final double tolerance;
    /** Number of iterations used. */
    public final int iterations;

    /**
     * Creates an adjuster status.
     *
     * @param name adjuster name
     * @param converged whether converged
     * @param error current error
     * @param tolerance convergence tolerance
     * @param iterations iteration count
     */
    AdjusterStatus(String name, boolean converged, double error, double tolerance, int iterations) {
      this.name = name;
      this.converged = converged;
      this.error = error;
      this.tolerance = tolerance;
      this.iterations = iterations;
    }
  }

  /**
   * Complete diagnostic report for a process system.
   */
  public static class DiagnosticReport implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean allConverged;
    private final List<RecycleStatus> recycleStatuses;
    private final List<AdjusterStatus> adjusterStatuses;
    private final List<String> suggestions;

    /**
     * Creates a diagnostic report.
     *
     * @param allConverged whether all recycles and adjusters converged
     * @param recycleStatuses list of recycle statuses
     * @param adjusterStatuses list of adjuster statuses
     * @param suggestions list of remediation suggestions
     */
    DiagnosticReport(boolean allConverged, List<RecycleStatus> recycleStatuses,
        List<AdjusterStatus> adjusterStatuses, List<String> suggestions) {
      this.allConverged = allConverged;
      this.recycleStatuses = recycleStatuses;
      this.adjusterStatuses = adjusterStatuses;
      this.suggestions = suggestions;
    }

    /**
     * Whether all recycle and adjuster units converged.
     *
     * @return true if all units converged
     */
    public boolean isConverged() {
      return allConverged;
    }

    /**
     * Gets remediation suggestions for unconverged units.
     *
     * @return list of human-readable suggestions
     */
    public List<String> getSuggestions() {
      return suggestions;
    }

    /**
     * Gets the recycle status list.
     *
     * @return list of recycle statuses
     */
    public List<RecycleStatus> getRecycleStatuses() {
      return recycleStatuses;
    }

    /**
     * Gets the adjuster status list.
     *
     * @return list of adjuster statuses
     */
    public List<AdjusterStatus> getAdjusterStatuses() {
      return adjusterStatuses;
    }

    /**
     * Converts the report to JSON.
     *
     * @return JSON representation of the diagnostic report
     */
    public String toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("allConverged", allConverged);

      JsonArray recycleArray = new JsonArray();
      for (RecycleStatus rs : recycleStatuses) {
        JsonObject rj = new JsonObject();
        rj.addProperty("name", rs.name);
        rj.addProperty("converged", rs.converged);
        rj.addProperty("flowError", rs.flowError);
        rj.addProperty("tempError", rs.tempError);
        rj.addProperty("pressError", rs.pressError);
        rj.addProperty("compError", rs.compError);
        rj.addProperty("iterations", rs.iterations);
        rj.addProperty("dominantError", rs.dominantError);
        recycleArray.add(rj);
      }
      json.add("recycles", recycleArray);

      JsonArray adjArray = new JsonArray();
      for (AdjusterStatus as : adjusterStatuses) {
        JsonObject aj = new JsonObject();
        aj.addProperty("name", as.name);
        aj.addProperty("converged", as.converged);
        aj.addProperty("error", as.error);
        aj.addProperty("tolerance", as.tolerance);
        aj.addProperty("iterations", as.iterations);
        adjArray.add(aj);
      }
      json.add("adjusters", adjArray);

      JsonArray sugArray = new JsonArray();
      for (String s : suggestions) {
        sugArray.add(s);
      }
      json.add("suggestions", sugArray);

      return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
          .toJson(json);
    }
  }
}
