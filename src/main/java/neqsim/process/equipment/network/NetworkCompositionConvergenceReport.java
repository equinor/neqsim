package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Convergence and conservation diagnostics for network composition mixing.
 */
public final class NetworkCompositionConvergenceReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final boolean converged;
  private final int iterations;
  private final double maxMoleFractionResidual;
  private final double maxTemperatureResidualK;
  private final double maxComponentBalanceResidualMolS;
  private final double maxComponentMassBalanceResidualKgS;
  private final String message;
  private final Map<String, NetworkMixingResult> nodeResults;

  /**
   * Create a convergence report.
   *
   * @param converged true when all configured residual tolerances were met
   * @param iterations number of synchronous composition iterations
   * @param maxMoleFractionResidual maximum absolute component mole-fraction change
   * @param maxTemperatureResidualK maximum absolute temperature change in K
   * @param maxComponentBalanceResidualMolS maximum component balance residual in mol/s
   * @param maxComponentMassBalanceResidualKgS maximum component mass balance residual in kg/s
   * @param message diagnostic summary
   * @param nodeResults per-node mixing results
   */
  public NetworkCompositionConvergenceReport(boolean converged, int iterations, double maxMoleFractionResidual,
      double maxTemperatureResidualK, double maxComponentBalanceResidualMolS, double maxComponentMassBalanceResidualKgS,
      String message, Map<String, NetworkMixingResult> nodeResults) {
    this.converged = converged;
    this.iterations = iterations;
    this.maxMoleFractionResidual = maxMoleFractionResidual;
    this.maxTemperatureResidualK = maxTemperatureResidualK;
    this.maxComponentBalanceResidualMolS = maxComponentBalanceResidualMolS;
    this.maxComponentMassBalanceResidualKgS = maxComponentMassBalanceResidualKgS;
    this.message = message;
    this.nodeResults = new LinkedHashMap<String, NetworkMixingResult>(nodeResults);
  }

  /**
   * Check whether composition and temperature residuals converged.
   *
   * @return true when converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Get the number of iterations.
   *
   * @return iteration count
   */
  public int getIterations() {
    return iterations;
  }

  /**
   * Get the maximum mole-fraction change.
   *
   * @return maximum absolute mole-fraction residual
   */
  public double getMaxMoleFractionResidual() {
    return maxMoleFractionResidual;
  }

  /**
   * Get the maximum temperature change.
   *
   * @return maximum absolute temperature residual in K
   */
  public double getMaxTemperatureResidualK() {
    return maxTemperatureResidualK;
  }

  /**
   * Get the maximum component balance residual.
   *
   * @return maximum absolute component balance residual in mol/s
   */
  public double getMaxComponentBalanceResidualMolS() {
    return maxComponentBalanceResidualMolS;
  }

  /**
   * Get the maximum component mass balance residual.
   *
   * @return maximum absolute component mass balance residual in kg/s
   */
  public double getMaxComponentMassBalanceResidualKgS() {
    return maxComponentMassBalanceResidualKgS;
  }

  /**
   * Get the diagnostic summary.
   *
   * @return diagnostic message
   */
  public String getMessage() {
    return message;
  }

  /**
   * Get per-node mixing results.
   *
   * @return immutable node result map
   */
  public Map<String, NetworkMixingResult> getNodeResults() {
    return Collections.unmodifiableMap(nodeResults);
  }

  /**
   * Serialize the report to stable, pretty-printed JSON.
   *
   * @return JSON report
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
