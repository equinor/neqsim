package neqsim.process.equipment.energy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.EnergyNetworkReport;

/**
 * Immutable result from a coupled process and energy-network convergence calculation.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class CoupledProcessEnergyResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Reason why the coupled solve stopped. */
  public enum TerminationReason {
    /** Process and energy residuals satisfied their configured tolerances. */
    CONVERGED,
    /** Maximum number of coupled iterations was reached. */
    MAXIMUM_ITERATIONS
  }

  /** Immutable diagnostics for one coupled iteration. */
  public static final class IterationResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final int iteration;
    private final double processResidual;
    private final double powerResidual;
    private final boolean converged;

    /**
     * Creates one iteration record.
     *
     * @param iteration one-based iteration number
     * @param processResidual maximum relative stream-state change
     * @param powerResidual maximum absolute energy-network change in W
     * @param converged whether both configured tolerances were satisfied
     */
    public IterationResult(int iteration, double processResidual, double powerResidual, boolean converged) {
      if (iteration <= 0) {
        throw new IllegalArgumentException("Iteration number must be greater than zero");
      }
      validateResidual("Process residual", processResidual);
      validateResidual("Power residual", powerResidual);
      this.iteration = iteration;
      this.processResidual = processResidual;
      this.powerResidual = powerResidual;
      this.converged = converged;
    }

    /**
     * Gets the one-based iteration number.
     *
     * @return iteration number
     */
    public int getIteration() {
      return iteration;
    }

    /**
     * Gets the maximum relative process-state residual.
     *
     * @return dimensionless residual
     */
    public double getProcessResidual() {
      return processResidual;
    }

    /**
     * Gets the maximum absolute energy-network residual.
     *
     * @return residual in W
     */
    public double getPowerResidual() {
      return powerResidual;
    }

    /**
     * Checks whether this iteration satisfied all tolerances.
     *
     * @return {@code true} when converged
     */
    public boolean isConverged() {
      return converged;
    }
  }

  private final boolean converged;
  private final TerminationReason terminationReason;
  private final int iterations;
  private final double maximumProcessResidual;
  private final double maximumPowerResidual;
  private final List<IterationResult> iterationHistory;
  private final List<EnergyNetworkReport> energyReports;

  /**
   * Creates a coupled convergence result.
   *
   * @param converged whether all configured tolerances were satisfied
   * @param terminationReason reason why iteration stopped
   * @param iterations number of completed process runs
   * @param maximumProcessResidual final maximum relative stream-state change
   * @param maximumPowerResidual final maximum absolute energy-network change in W
   * @param iterationHistory diagnostics for all completed iterations
   * @param energyReports reports from the final energy-network solution
   */
  public CoupledProcessEnergyResult(boolean converged, TerminationReason terminationReason, int iterations,
      double maximumProcessResidual, double maximumPowerResidual, List<IterationResult> iterationHistory,
      List<EnergyNetworkReport> energyReports) {
    if (terminationReason == null) {
      throw new IllegalArgumentException("Termination reason is required");
    }
    if (iterations < 0) {
      throw new IllegalArgumentException("Iterations cannot be negative");
    }
    validateResidual("Maximum process residual", maximumProcessResidual);
    validateResidual("Maximum power residual", maximumPowerResidual);
    if (iterationHistory == null) {
      throw new IllegalArgumentException("Iteration history is required");
    }
    if (energyReports == null) {
      throw new IllegalArgumentException("Energy reports are required");
    }
    if (iterationHistory.size() != iterations) {
      throw new IllegalArgumentException("Iteration history size must equal the completed iteration count");
    }
    if (converged != (terminationReason == TerminationReason.CONVERGED)) {
      throw new IllegalArgumentException("Converged state and termination reason are inconsistent");
    }
    requireNoNullElements(iterationHistory, "Iteration history cannot contain null entries");
    requireNoNullElements(energyReports, "Energy reports cannot contain null entries");

    this.converged = converged;
    this.terminationReason = terminationReason;
    this.iterations = iterations;
    this.maximumProcessResidual = maximumProcessResidual;
    this.maximumPowerResidual = maximumPowerResidual;
    this.iterationHistory = new ArrayList<IterationResult>(iterationHistory);
    this.energyReports = new ArrayList<EnergyNetworkReport>(energyReports);
  }

  /** Validates a residual while allowing positive infinity before a second iteration exists. */
  private static void validateResidual(String name, double residual) {
    if (Double.isNaN(residual) || residual < 0.0) {
      throw new IllegalArgumentException(name + " must be non-negative and not NaN");
    }
  }

  /** Validates that a public result list contains no null elements. */
  private static void requireNoNullElements(List<?> values, String message) {
    for (Object value : values) {
      if (value == null) {
        throw new IllegalArgumentException(message);
      }
    }
  }

  /**
   * Checks whether the coupled calculation converged.
   *
   * @return {@code true} when converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Gets the termination reason.
   *
   * @return termination reason
   */
  public TerminationReason getTerminationReason() {
    return terminationReason;
  }

  /**
   * Gets the number of completed iterations.
   *
   * @return completed iterations
   */
  public int getIterations() {
    return iterations;
  }

  /**
   * Gets the final maximum relative stream-state residual.
   *
   * @return dimensionless process residual
   */
  public double getMaximumProcessResidual() {
    return maximumProcessResidual;
  }

  /**
   * Gets the final maximum absolute energy-network residual.
   *
   * @return power residual in W
   */
  public double getMaximumPowerResidual() {
    return maximumPowerResidual;
  }

  /**
   * Gets immutable iteration diagnostics.
   *
   * @return iteration history
   */
  public List<IterationResult> getIterationHistory() {
    return Collections.unmodifiableList(iterationHistory);
  }

  /**
   * Gets immutable final energy-network reports.
   *
   * @return final reports
   */
  public List<EnergyNetworkReport> getEnergyReports() {
    return Collections.unmodifiableList(energyReports);
  }

  /**
   * Serializes the result as JSON.
   *
   * @return JSON result
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(this);
  }
}
