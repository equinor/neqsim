package neqsim.process.safety.release;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable, caller-selected external heat boundary for {@link ReleaseInventory}.
 *
 * <p>
 * Positive heat flow enters the inventory. The built-in models deliberately describe only a lumped external boundary:
 * they do not infer fire heat flux, wall thermal inertia, interfacial heat transfer, latent heat, or phase-transfer
 * rates. Those effects require separately qualified models. Provenance is mandatory so a live source frame cannot
 * silently hide an assumed heat boundary.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public final class ReleaseInventoryHeatTransfer implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Supported deterministic heat-boundary forms. */
  public enum Type {
    /** No external heat transfer. */
    ADIABATIC,
    /** Constant signed heat input in W. */
    CONSTANT_POWER,
    /** Linear conductance to a constant environment temperature. */
    LINEAR_CONDUCTANCE
  }

  private static final ReleaseInventoryHeatTransfer ADIABATIC = new ReleaseInventoryHeatTransfer(Type.ADIABATIC, 0.0,
      0.0, Double.NaN, "built-in:adiabatic");

  private final Type type;
  private final double constantPowerW;
  private final double conductanceWK;
  private final double environmentTemperatureK;
  private final String provenance;

  /**
   * Creates one already validated immutable boundary.
   *
   * @param type boundary form
   * @param constantPowerW imposed power in W
   * @param conductanceWK conductance in W/K
   * @param environmentTemperatureK environment temperature in K
   * @param provenance parameter provenance
   */
  private ReleaseInventoryHeatTransfer(Type type, double constantPowerW, double conductanceWK,
      double environmentTemperatureK, String provenance) {
    this.type = Objects.requireNonNull(type, "type");
    this.constantPowerW = constantPowerW;
    this.conductanceWK = conductanceWK;
    this.environmentTemperatureK = environmentTemperatureK;
    if (provenance == null || provenance.trim().isEmpty()) {
      throw new IllegalArgumentException("Heat-transfer provenance required");
    }
    this.provenance = provenance;
  }

  /** @return shared immutable adiabatic boundary */
  public static ReleaseInventoryHeatTransfer adiabatic() {
    return ADIABATIC;
  }

  /**
   * Creates a constant signed external heat input.
   *
   * @param powerW heat input in W; positive heats and negative cools the inventory
   * @param provenance source of the imposed power
   * @return immutable heat boundary
   */
  public static ReleaseInventoryHeatTransfer constantPower(double powerW, String provenance) {
    if (!Double.isFinite(powerW)) {
      throw new IllegalArgumentException("Constant heat-transfer power must be finite");
    }
    return new ReleaseInventoryHeatTransfer(Type.CONSTANT_POWER, powerW, 0.0, Double.NaN, provenance);
  }

  /**
   * Creates a lumped linear boundary, {@code Qdot = UA * (T_environment - T_inventory)}.
   *
   * @param conductanceWK positive lumped conductance in W/K
   * @param environmentTemperatureK positive constant environment temperature in K
   * @param provenance source of conductance and environment assumptions
   * @return immutable heat boundary
   */
  public static ReleaseInventoryHeatTransfer linearConductance(double conductanceWK, double environmentTemperatureK,
      String provenance) {
    ReleaseFlowRequest.positive(conductanceWK, "heat-transfer conductance");
    ReleaseFlowRequest.positive(environmentTemperatureK, "environment temperature");
    return new ReleaseInventoryHeatTransfer(Type.LINEAR_CONDUCTANCE, 0.0, conductanceWK, environmentTemperatureK,
        provenance);
  }

  /**
   * Evaluates signed heat input at the immutable beginning-of-substep inventory state.
   *
   * @param state current inventory state
   * @return finite signed heat rate in W
   */
  public double getHeatTransferRateW(ReleaseState state) {
    Objects.requireNonNull(state, "state");
    double rate;
    if (type == Type.ADIABATIC) {
      rate = 0.0;
    } else if (type == Type.CONSTANT_POWER) {
      rate = constantPowerW;
    } else {
      rate = conductanceWK * (environmentTemperatureK - state.getTemperatureK());
    }
    if (!Double.isFinite(rate)) {
      throw new IllegalStateException("INVENTORY_HEAT_TRANSFER_INVALID");
    }
    return rate;
  }

  /** @return selected heat-boundary form */
  public Type getType() {
    return type;
  }

  /** @return imposed power in W, or zero for other forms */
  public double getConstantPowerW() {
    return constantPowerW;
  }

  /** @return lumped conductance in W/K, or zero for other forms */
  public double getConductanceWK() {
    return conductanceWK;
  }

  /** @return environment temperature in K, or NaN for other forms */
  public double getEnvironmentTemperatureK() {
    return environmentTemperatureK;
  }

  /** @return caller-supplied parameter provenance */
  public String getProvenance() {
    return provenance;
  }

  /** @return whether the boundary can add or remove energy */
  public boolean isActive() {
    return type != Type.ADIABATIC && (type != Type.CONSTANT_POWER || constantPowerW != 0.0);
  }
}
