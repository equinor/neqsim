package neqsim.process.safety.release;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable, provenance-required interfacial heat-transfer closure for a gas/liquid release inventory.
 *
 * <p>
 * The linear model uses {@code Qdot_gas = UA * (T_liquid - T_gas)}. Positive heat therefore enters the gas and the same
 * energy leaves the liquid. The conductance is a prescribed sensitivity input: this class does not infer a correlation,
 * wall contribution, boiling heat transfer, droplet area or engineering applicability.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public final class ReleaseInventoryInterphaseHeatTransfer implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Supported deterministic interfacial heat-transfer forms. */
  public enum Type {
    /** Thermally isolated retained phases. */
    ADIABATIC,
    /** Linear conductance between retained phase temperatures. */
    LINEAR_CONDUCTANCE
  }

  private static final ReleaseInventoryInterphaseHeatTransfer ADIABATIC = new ReleaseInventoryInterphaseHeatTransfer(
      Type.ADIABATIC, 0.0, "built-in:adiabatic-phases");

  private final Type type;
  private final double conductanceWK;
  private final String provenance;

  /**
   * Creates one already validated immutable closure.
   *
   * @param type closure form
   * @param conductanceWK interfacial conductance in W/K
   * @param provenance parameter provenance
   */
  private ReleaseInventoryInterphaseHeatTransfer(Type type, double conductanceWK, String provenance) {
    this.type = Objects.requireNonNull(type, "type");
    this.conductanceWK = conductanceWK;
    if (provenance == null || provenance.trim().isEmpty()) {
      throw new IllegalArgumentException("INVENTORY_INTERPHASE_HEAT_PROVENANCE_REQUIRED");
    }
    this.provenance = provenance;
  }

  /** @return shared immutable thermally isolated phase closure */
  public static ReleaseInventoryInterphaseHeatTransfer adiabatic() {
    return ADIABATIC;
  }

  /**
   * Creates a prescribed linear interfacial conductance.
   *
   * @param conductanceWK positive conductance in W/K
   * @param provenance source and applicability of the conductance
   * @return immutable interfacial heat-transfer closure
   */
  public static ReleaseInventoryInterphaseHeatTransfer linearConductance(double conductanceWK, String provenance) {
    ReleaseFlowRequest.positive(conductanceWK, "interphase heat-transfer conductance");
    return new ReleaseInventoryInterphaseHeatTransfer(Type.LINEAR_CONDUCTANCE, conductanceWK, provenance);
  }

  /**
   * Evaluates heat entering the gas at the beginning of a substep.
   *
   * @param gasTemperatureK positive gas temperature in K
   * @param liquidTemperatureK positive liquid temperature in K
   * @return finite signed heat rate into the gas in W
   */
  public double getGasHeatTransferRateW(double gasTemperatureK, double liquidTemperatureK) {
    ReleaseFlowRequest.positive(gasTemperatureK, "gas temperature");
    ReleaseFlowRequest.positive(liquidTemperatureK, "liquid temperature");
    double rate = type == Type.ADIABATIC ? 0.0 : conductanceWK * (liquidTemperatureK - gasTemperatureK);
    if (!Double.isFinite(rate)) {
      throw new IllegalStateException("INVENTORY_INTERPHASE_HEAT_TRANSFER_INVALID");
    }
    return rate;
  }

  /** @return selected closure form */
  public Type getType() {
    return type;
  }

  /** @return prescribed conductance in W/K */
  public double getConductanceWK() {
    return conductanceWK;
  }

  /** @return caller-supplied provenance */
  public String getProvenance() {
    return provenance;
  }

  /** @return whether energy can move between phases */
  public boolean isActive() {
    return type != Type.ADIABATIC;
  }
}
