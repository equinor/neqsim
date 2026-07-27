package neqsim.process.equipment.stream;

import java.io.Serializable;

/**
 * Immutable allocation result for one energy-network participant.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class EnergyAllocation implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String participantId;
  private final String participantName;
  private final EnergyPortMode mode;
  private final EnergyPortDirection direction;
  private final int priority;
  private final double requestedPower;
  private final double allocatedPower;
  private final double unmetPower;
  private final double curtailedPower;

  /**
   * Creates an allocation result.
   *
   * @param participantId stable participant identifier
   * @param participantName display name
   * @param mode calculation mode
   * @param direction physical direction
   * @param priority dispatch priority
   * @param requestedPower requested or offered power in W
   * @param allocatedPower allocated power in W
   * @param unmetPower unmet demand in W
   * @param curtailedPower curtailed supply in W
   */
  public EnergyAllocation(String participantId, String participantName, EnergyPortMode mode,
      EnergyPortDirection direction, int priority, double requestedPower, double allocatedPower, double unmetPower,
      double curtailedPower) {
    this.participantId = participantId;
    this.participantName = participantName;
    this.mode = mode;
    this.direction = direction;
    this.priority = priority;
    this.requestedPower = requestedPower;
    this.allocatedPower = allocatedPower;
    this.unmetPower = unmetPower;
    this.curtailedPower = curtailedPower;
  }

  /**
   * Gets the stable participant identifier.
   *
   * @return participant identifier
   */
  public String getParticipantId() {
    return participantId;
  }

  /**
   * Gets the participant display name.
   *
   * @return display name
   */
  public String getParticipantName() {
    return participantName;
  }

  /**
   * Gets the calculation mode.
   *
   * @return calculation mode
   */
  public EnergyPortMode getMode() {
    return mode;
  }

  /**
   * Gets the physical direction.
   *
   * @return direction
   */
  public EnergyPortDirection getDirection() {
    return direction;
  }

  /**
   * Gets the dispatch priority.
   *
   * @return priority, where a lower value is served first
   */
  public int getPriority() {
    return priority;
  }

  /**
   * Gets requested or offered power.
   *
   * @return power in W
   */
  public double getRequestedPower() {
    return requestedPower;
  }

  /**
   * Gets allocated power.
   *
   * @return power in W
   */
  public double getAllocatedPower() {
    return allocatedPower;
  }

  /**
   * Gets unmet demand.
   *
   * @return power in W
   */
  public double getUnmetPower() {
    return unmetPower;
  }

  /**
   * Gets curtailed supply.
   *
   * @return power in W
   */
  public double getCurtailedPower() {
    return curtailedPower;
  }
}
