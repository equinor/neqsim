package neqsim.process.equipment.network;

import java.io.Serializable;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

/** Immutable convergence and conservation diagnostics for one transient gas-network step. */
public final class TransientGasNetworkStepReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final double elapsedTimeSeconds;
  private final int hydraulicIterations;
  private final double maximumNodeMassResidualKgS;
  private final double maximumRelativeHydraulicResidual;
  private final double totalMassResidualKg;
  private final double relativeTotalMassResidual;
  private final double maximumComponentRelativeResidual;
  private final double maximumJunctionRelativeResidual;
  private final double sinkMassFlowKgS;
  private final boolean converged;
  private final String message;

  /**
   * Create a transient step report.
   *
   * @param elapsedTimeSeconds accepted step-end time in s
   * @param hydraulicIterations nonlinear hydraulic iteration count
   * @param maximumNodeMassResidualKgS largest absolute solved-node balance residual in kg/s
   * @param maximumRelativeHydraulicResidual largest relative solved-node balance residual
   * @param totalMassResidualKg cumulative whole-network mass residual in kg
   * @param relativeTotalMassResidual cumulative relative whole-network mass residual
   * @param maximumComponentRelativeResidual largest cumulative component residual
   * @param maximumJunctionRelativeResidual largest cumulative component-mixing residual
   * @param sinkMassFlowKgS positive delivered mass rate in kg/s
   * @param converged whether every configured gate passed
   * @param message diagnostic summary
   */
  TransientGasNetworkStepReport(double elapsedTimeSeconds, int hydraulicIterations, double maximumNodeMassResidualKgS,
      double maximumRelativeHydraulicResidual, double totalMassResidualKg, double relativeTotalMassResidual,
      double maximumComponentRelativeResidual, double maximumJunctionRelativeResidual, double sinkMassFlowKgS,
      boolean converged, String message) {
    this.elapsedTimeSeconds = elapsedTimeSeconds;
    this.hydraulicIterations = hydraulicIterations;
    this.maximumNodeMassResidualKgS = maximumNodeMassResidualKgS;
    this.maximumRelativeHydraulicResidual = maximumRelativeHydraulicResidual;
    this.totalMassResidualKg = totalMassResidualKg;
    this.relativeTotalMassResidual = relativeTotalMassResidual;
    this.maximumComponentRelativeResidual = maximumComponentRelativeResidual;
    this.maximumJunctionRelativeResidual = maximumJunctionRelativeResidual;
    this.sinkMassFlowKgS = sinkMassFlowKgS;
    this.converged = converged;
    this.message = message;
  }

  /** @return accepted step-end time in s */
  public double getElapsedTimeSeconds() {
    return elapsedTimeSeconds;
  }

  /** @return nonlinear hydraulic iteration count */
  public int getHydraulicIterations() {
    return hydraulicIterations;
  }

  /** @return largest absolute solved-node balance residual in kg/s */
  public double getMaximumNodeMassResidualKgS() {
    return maximumNodeMassResidualKgS;
  }

  /** @return largest relative solved-node balance residual */
  public double getMaximumRelativeHydraulicResidual() {
    return maximumRelativeHydraulicResidual;
  }

  /** @return cumulative whole-network mass residual in kg */
  public double getTotalMassResidualKg() {
    return totalMassResidualKg;
  }

  /** @return cumulative relative whole-network mass residual */
  public double getRelativeTotalMassResidual() {
    return relativeTotalMassResidual;
  }

  /** @return largest cumulative component residual */
  public double getMaximumComponentRelativeResidual() {
    return maximumComponentRelativeResidual;
  }

  /** @return largest cumulative component-mixing residual */
  public double getMaximumJunctionRelativeResidual() {
    return maximumJunctionRelativeResidual;
  }

  /** @return positive delivered mass rate in kg/s */
  public double getSinkMassFlowKgS() {
    return sinkMassFlowKgS;
  }

  /** @return whether every configured gate passed */
  public boolean isConverged() {
    return converged;
  }

  /** @return diagnostic summary */
  public String getMessage() {
    return message;
  }

  /**
   * Serialize this immutable report for Python/JPype capture.
   *
   * @return stable, pretty-printed JSON
   */
  public String toJson() {
    JsonSerializer<Double> finiteDoubleSerializer = (value, type,
        context) -> value != null && Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapter(Double.class, finiteDoubleSerializer);
    gsonBuilder.registerTypeAdapter(Double.TYPE, finiteDoubleSerializer);
    gsonBuilder.serializeNulls();
    gsonBuilder.setPrettyPrinting();
    return gsonBuilder.create().toJson(this);
  }
}
