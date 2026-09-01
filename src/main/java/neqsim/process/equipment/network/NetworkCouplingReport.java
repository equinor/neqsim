package neqsim.process.equipment.network;

import java.io.Serializable;
import com.google.gson.GsonBuilder;

/**
 * Convergence diagnostics for coupled hydraulic, composition, and thermal network calculations.
 */
public final class NetworkCouplingReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final boolean converged;
  private final boolean hydraulicConverged;
  private final int outerIterations;
  private final double maxFlowResidualKgS;
  private final double maxPressureResidualPa;
  private final double maxTemperatureResidualK;
  private final NetworkCompositionConvergenceReport compositionReport;
  private final String message;

  /**
   * Create a coupling report.
   *
   * @param converged true when hydraulic and outer coupling criteria are met
   * @param hydraulicConverged true when the inner hydraulic solve converged
   * @param outerIterations number of hydraulic-property outer iterations
   * @param maxFlowResidualKgS maximum edge flow change in kg/s
   * @param maxPressureResidualPa maximum node pressure change in Pa
   * @param maxTemperatureResidualK maximum node mixing temperature change in K
   * @param compositionReport most recent composition convergence report
   * @param message diagnostic summary
   */
  public NetworkCouplingReport(boolean converged, boolean hydraulicConverged, int outerIterations,
      double maxFlowResidualKgS, double maxPressureResidualPa, double maxTemperatureResidualK,
      NetworkCompositionConvergenceReport compositionReport, String message) {
    this.converged = converged;
    this.hydraulicConverged = hydraulicConverged;
    this.outerIterations = outerIterations;
    this.maxFlowResidualKgS = maxFlowResidualKgS;
    this.maxPressureResidualPa = maxPressureResidualPa;
    this.maxTemperatureResidualK = maxTemperatureResidualK;
    this.compositionReport = compositionReport;
    this.message = message;
  }

  /**
   * Check whether the coupled calculation converged.
   *
   * @return true when converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Check whether the final inner hydraulic solve converged.
   *
   * @return true when hydraulically converged
   */
  public boolean isHydraulicConverged() {
    return hydraulicConverged;
  }

  /**
   * Get the number of outer iterations.
   *
   * @return outer iteration count
   */
  public int getOuterIterations() {
    return outerIterations;
  }

  /**
   * Get the maximum edge flow change.
   *
   * @return flow residual in kg/s
   */
  public double getMaxFlowResidualKgS() {
    return maxFlowResidualKgS;
  }

  /**
   * Get the maximum node pressure change.
   *
   * @return pressure residual in Pa
   */
  public double getMaxPressureResidualPa() {
    return maxPressureResidualPa;
  }

  /**
   * Get the maximum mixing temperature change.
   *
   * @return temperature residual in K
   */
  public double getMaxTemperatureResidualK() {
    return maxTemperatureResidualK;
  }

  /**
   * Get the final composition report.
   *
   * @return composition report, or null when composition tracking was unused
   */
  public NetworkCompositionConvergenceReport getCompositionReport() {
    return compositionReport;
  }

  /**
   * Get the diagnostic summary.
   *
   * @return message
   */
  public String getMessage() {
    return message;
  }

  /**
   * Serialize this report to pretty-printed JSON.
   *
   * @return JSON report
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
