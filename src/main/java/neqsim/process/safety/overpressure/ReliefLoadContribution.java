package neqsim.process.safety.overpressure;

import java.io.Serializable;

/**
 * Immutable description of one relief load contributing to a shared disposal (flare or vent) network.
 *
 * <p>
 * Each contribution captures the governing relief load of a single protected item together with a flag indicating
 * whether that load is concurrent with the other contributions in the network (for example, all items exposed to the
 * same fire zone relieve simultaneously per API STD 521 section 5.3).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ReliefLoadContribution implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String itemName;
  private final ReliefCause cause;
  private final ReliefPhase phase;
  private final double massFlowKgPerS;
  private final boolean simultaneous;

  /**
   * Creates a relief load contribution.
   *
   * @param itemName the protected-item name; not null
   * @param cause the governing relief cause; not null
   * @param phase the relief phase; not null
   * @param massFlowKgPerS the governing relief mass flow [kg/s]
   * @param simultaneous true if this load is concurrent with the other network contributions
   */
  public ReliefLoadContribution(String itemName, ReliefCause cause, ReliefPhase phase, double massFlowKgPerS,
      boolean simultaneous) {
    this.itemName = itemName;
    this.cause = cause;
    this.phase = phase;
    this.massFlowKgPerS = massFlowKgPerS;
    this.simultaneous = simultaneous;
  }

  /**
   * Gets the protected-item name.
   *
   * @return the item name
   */
  public String getItemName() {
    return itemName;
  }

  /**
   * Gets the governing relief cause.
   *
   * @return the relief cause
   */
  public ReliefCause getCause() {
    return cause;
  }

  /**
   * Gets the relief phase.
   *
   * @return the relief phase
   */
  public ReliefPhase getPhase() {
    return phase;
  }

  /**
   * Gets the governing relief mass flow.
   *
   * @return the mass flow [kg/s]
   */
  public double getMassFlowKgPerS() {
    return massFlowKgPerS;
  }

  /**
   * Gets the governing relief mass flow in kg/hr.
   *
   * @return the mass flow [kg/hr]
   */
  public double getMassFlowKgPerHr() {
    return massFlowKgPerS * 3600.0;
  }

  /**
   * Returns whether this load is concurrent with the other network contributions.
   *
   * @return true if simultaneous
   */
  public boolean isSimultaneous() {
    return simultaneous;
  }
}
