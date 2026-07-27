package neqsim.process.equipment.stream;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import neqsim.util.unit.PowerUnit;

/**
 * Named, typed connection point between process equipment and an {@link EnergyStream}.
 *
 * <p>
 * An energy port separates the physical domain, transfer direction, calculation role, requested power, and allocated
 * power. Each port owns a persistent participant identifier so an energy network remains valid when equipment is
 * renamed.
 * </p>
 *
 * @author NeqSim
 * @version 2.0
 */
public class EnergyPort implements Serializable {
  private static final long serialVersionUID = 1000L;

  private String participantId = UUID.randomUUID().toString();
  private final String name;
  private final EnergyType energyType;
  private final EnergyPortDirection direction;
  private EnergyPortMode mode;
  private String ownerName = "";
  private EnergyStream energyStream;
  private int priority = 100;
  private double requestedPower = 0.0;
  private double minimumPower = 0.0;
  private double maximumPower = Double.POSITIVE_INFINITY;
  private double maximumBalanceGeneration = 0.0;
  private double maximumBalanceConsumption = 0.0;
  private double energyPricePerMWh = 0.0;
  private double emissionFactorKgPerMWh = 0.0;
  private double conversionLoss = 0.0;
  private EnergyQuality requiredQuality = new EnergyQuality();

  /**
   * Creates an unconnected energy port.
   *
   * @param name unique port name within its equipment
   * @param energyType physical energy domain
   * @param direction physical energy direction relative to the equipment
   * @param mode calculation role of the port
   */
  public EnergyPort(String name, EnergyType energyType, EnergyPortDirection direction, EnergyPortMode mode) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Energy port name cannot be null or empty");
    }
    this.name = name;
    this.energyType = Objects.requireNonNull(energyType, "energyType cannot be null");
    this.direction = Objects.requireNonNull(direction, "direction cannot be null");
    this.mode = Objects.requireNonNull(mode, "mode cannot be null");
  }

  /**
   * Restores defaults introduced after the original serialized energy-port format.
   *
   * @param input serialized object input
   * @throws IOException if the stream cannot be read
   * @throws ClassNotFoundException if a serialized class cannot be resolved
   */
  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    if (participantId == null) {
      participantId = UUID.randomUUID().toString();
      priority = 100;
      maximumPower = Double.POSITIVE_INFINITY;
    }
    if (requiredQuality == null) {
      requiredQuality = new EnergyQuality();
    }
  }

  /**
   * Gets the persistent network-participant identifier.
   *
   * @return participant identifier
   */
  public String getParticipantId() {
    return participantId;
  }

  /**
   * Assigns a new participant identifier after a deserialized equipment copy collides with an existing bus participant.
   *
   * <p>
   * Normal serialization preserves participant identity. Regeneration is only performed by
   * {@link EnergyBus#registerPort(EnergyPort)} when two distinct port objects with the same serialized identifier are
   * connected to one bus.
   * </p>
   */
  void regenerateParticipantId() {
    participantId = UUID.randomUUID().toString();
  }

  /**
   * Gets the port name.
   *
   * @return port name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the owning equipment name used for display and reporting.
   *
   * @return owner name, or an empty string when the port is standalone
   */
  public String getOwnerName() {
    return ownerName;
  }

  /**
   * Sets the owning equipment display name.
   *
   * @param ownerName equipment name
   */
  public void setOwnerName(String ownerName) {
    this.ownerName = Objects.requireNonNull(ownerName, "ownerName cannot be null");
  }

  /**
   * Gets a human-readable participant name.
   *
   * @return owner and port name
   */
  public String getParticipantName() {
    return ownerName.isEmpty() ? name : ownerName + "." + name;
  }

  /**
   * Gets the physical energy domain accepted by this port.
   *
   * @return energy type
   */
  public EnergyType getEnergyType() {
    return energyType;
  }

  /**
   * Gets the physical energy direction relative to the equipment.
   *
   * @return port direction
   */
  public EnergyPortDirection getDirection() {
    return direction;
  }

  /**
   * Gets the calculation role of the port.
   *
   * @return calculation mode
   */
  public EnergyPortMode getMode() {
    return mode;
  }

  /**
   * Sets the calculation role of the port.
   *
   * @param mode calculation mode
   */
  public void setMode(EnergyPortMode mode) {
    this.mode = Objects.requireNonNull(mode, "mode cannot be null");
    invalidateBusSolution();
  }

  /**
   * Gets dispatch priority.
   *
   * @return priority, where lower values are dispatched first
   */
  public int getPriority() {
    return priority;
  }

  /**
   * Sets dispatch priority.
   *
   * @param priority priority, where lower values are dispatched first
   */
  public void setPriority(int priority) {
    this.priority = priority;
    invalidateBusSolution();
  }

  /**
   * Gets requested power.
   *
   * @return non-negative request in W
   */
  public double getRequestedPower() {
    return requestedPower;
  }

  /**
   * Gets requested power in a requested unit.
   *
   * @param unit power unit
   * @return request in the requested unit
   */
  public double getRequestedPower(String unit) {
    return new PowerUnit(requestedPower, "W").getValue(unit);
  }

  /**
   * Sets requested power for a specification port.
   *
   * @param requestedPower non-negative request in W
   */
  public void setRequestedPower(double requestedPower) {
    if (!Double.isFinite(requestedPower) || requestedPower < 0.0) {
      throw new IllegalArgumentException("Requested power must be non-negative and finite");
    }
    this.requestedPower = requestedPower;
    invalidateBusSolution();
  }

  /**
   * Sets requested power in a specified unit.
   *
   * @param requestedPower non-negative request
   * @param unit power unit
   */
  public void setRequestedPower(double requestedPower, String unit) {
    setRequestedPower(new PowerUnit(requestedPower, unit).getValue("W"));
  }

  /**
   * Sets normal operating limits.
   *
   * @param minimumPower minimum dispatched power in W
   * @param maximumPower maximum dispatched power in W
   */
  public void setPowerLimits(double minimumPower, double maximumPower) {
    if (!Double.isFinite(minimumPower) || minimumPower < 0.0) {
      throw new IllegalArgumentException("Minimum power must be non-negative and finite");
    }
    if (Double.isNaN(maximumPower) || maximumPower < minimumPower) {
      throw new IllegalArgumentException("Maximum power must be at least the minimum power");
    }
    this.minimumPower = minimumPower;
    this.maximumPower = maximumPower;
    invalidateBusSolution();
  }

  /**
   * Gets minimum operating power.
   *
   * @return minimum power in W
   */
  public double getMinimumPower() {
    return minimumPower;
  }

  /**
   * Gets maximum operating power.
   *
   * @return maximum power in W
   */
  public double getMaximumPower() {
    return maximumPower;
  }

  /**
   * Sets available balancing limits.
   *
   * @param generationLimit maximum power injected into the bus in W
   * @param consumptionLimit maximum power absorbed from the bus in W
   */
  public void setBalanceLimits(double generationLimit, double consumptionLimit) {
    if (!Double.isFinite(generationLimit) || generationLimit < 0.0 || !Double.isFinite(consumptionLimit)
        || consumptionLimit < 0.0) {
      throw new IllegalArgumentException("Balance limits must be non-negative and finite");
    }
    maximumBalanceGeneration = generationLimit;
    maximumBalanceConsumption = consumptionLimit;
    invalidateBusSolution();
  }

  /**
   * Gets maximum balancing generation.
   *
   * @return generation limit in W
   */
  public double getMaximumBalanceGeneration() {
    return maximumBalanceGeneration;
  }

  /**
   * Gets maximum balancing consumption.
   *
   * @return consumption limit in W
   */
  public double getMaximumBalanceConsumption() {
    return maximumBalanceConsumption;
  }

  /**
   * Sets marginal energy price used by network reports.
   *
   * @param price price per MWh
   */
  public void setEnergyPricePerMWh(double price) {
    if (!Double.isFinite(price)) {
      throw new IllegalArgumentException("Energy price must be finite");
    }
    energyPricePerMWh = price;
  }

  /**
   * Gets marginal energy price.
   *
   * @return price per MWh
   */
  public double getEnergyPricePerMWh() {
    return energyPricePerMWh;
  }

  /**
   * Sets the emission factor used by network reports.
   *
   * @param factor CO2-equivalent factor in kg/MWh
   */
  public void setEmissionFactorKgPerMWh(double factor) {
    if (!Double.isFinite(factor) || factor < 0.0) {
      throw new IllegalArgumentException("Emission factor must be non-negative and finite");
    }
    emissionFactorKgPerMWh = factor;
  }

  /**
   * Gets the emission factor.
   *
   * @return CO2-equivalent factor in kg/MWh
   */
  public double getEmissionFactorKgPerMWh() {
    return emissionFactorKgPerMWh;
  }

  /**
   * Sets conversion loss reported by the connected conversion equipment.
   *
   * @param conversionLoss loss in W
   */
  public void setConversionLoss(double conversionLoss) {
    if (!Double.isFinite(conversionLoss) || conversionLoss < 0.0) {
      throw new IllegalArgumentException("Conversion loss must be non-negative and finite");
    }
    this.conversionLoss = conversionLoss;
  }

  /**
   * Gets reported conversion loss.
   *
   * @return loss in W
   */
  public double getConversionLoss() {
    return conversionLoss;
  }

  /**
   * Gets required quality metadata.
   *
   * @return required quality
   */
  public EnergyQuality getRequiredQuality() {
    return requiredQuality;
  }

  /**
   * Sets required quality metadata.
   *
   * @param requiredQuality required quality
   */
  public void setRequiredQuality(EnergyQuality requiredQuality) {
    this.requiredQuality = Objects.requireNonNull(requiredQuality, "requiredQuality cannot be null");
  }

  /**
   * Connects an energy stream to this port.
   *
   * <p>
   * A legacy stream with type {@link EnergyType#UNSPECIFIED} adopts the port type. A typed stream can only be connected
   * to a port of the same type or to an unspecified port.
   * </p>
   *
   * @param stream energy stream to connect
   * @throws IllegalArgumentException if stream type or specified quality conflicts with this port
   */
  public void connect(EnergyStream stream) {
    Objects.requireNonNull(stream, "stream cannot be null");
    EnergyType streamType = stream.getEnergyType();
    if (streamType != EnergyType.UNSPECIFIED && energyType != EnergyType.UNSPECIFIED && streamType != energyType) {
      throw new IllegalArgumentException(
          "Energy stream type " + streamType + " is incompatible with port type " + energyType + " for port " + name);
    }
    if (streamType == EnergyType.UNSPECIFIED && energyType != EnergyType.UNSPECIFIED) {
      stream.setEnergyType(energyType);
    }
    if (!stream.getQuality().satisfies(requiredQuality)) {
      throw new IllegalArgumentException("Energy quality is incompatible with port " + getParticipantName());
    }
    if (energyStream instanceof EnergyBus && energyStream != stream) {
      ((EnergyBus) energyStream).unregisterPort(this);
    }
    energyStream = stream;
    if (stream instanceof EnergyBus) {
      ((EnergyBus) stream).registerPort(this);
    }
  }

  /** Disconnects the current energy stream, if any. */
  public void disconnect() {
    if (energyStream instanceof EnergyBus) {
      ((EnergyBus) energyStream).unregisterPort(this);
    }
    energyStream = null;
  }

  /**
   * Checks whether a stream is connected.
   *
   * @return {@code true} when connected
   */
  public boolean isConnected() {
    return energyStream != null;
  }

  /**
   * Gets the connected energy stream.
   *
   * @return connected stream, or {@code null} when unconnected
   */
  public EnergyStream getEnergyStream() {
    return energyStream;
  }

  /**
   * Gets the connected stream duty in watts.
   *
   * @return duty in W
   * @throws IllegalStateException if no stream is connected
   */
  public double getDuty() {
    EnergyStream stream = requireConnectedStream();
    if (stream instanceof EnergyBus) {
      EnergyBus bus = (EnergyBus) stream;
      if (mode == EnergyPortMode.CALCULATED) {
        return bus.getContribution(participantId);
      }
      if (bus.hasSolution()) {
        return bus.getAllocation(participantId);
      }
      return bus.getNetPowerExcluding(participantId);
    }
    return stream.getDuty();
  }

  /**
   * Gets the non-negative transferred-power magnitude in watts.
   *
   * @return absolute duty in W
   * @throws IllegalStateException if no stream is connected
   */
  public double getPowerMagnitude() {
    return Math.abs(getDuty());
  }

  /**
   * Gets the non-negative transferred-power magnitude in a requested unit.
   *
   * @param unit requested power unit
   * @return absolute duty in the requested unit
   * @throws IllegalStateException if no stream is connected
   */
  public double getPowerMagnitude(String unit) {
    return new PowerUnit(getPowerMagnitude(), "W").getValue(unit);
  }

  /**
   * Gets the connected stream duty in a requested unit.
   *
   * @param unit requested power unit
   * @return duty in the requested unit
   * @throws IllegalStateException if no stream is connected
   */
  public double getDuty(String unit) {
    return new PowerUnit(getDuty(), "W").getValue(unit);
  }

  /**
   * Sets calculated power or a specification request in watts.
   *
   * @param duty duty in W
   * @throws IllegalStateException if no stream is connected
   */
  public void setDuty(double duty) {
    EnergyStream stream = requireConnectedStream();
    if (stream instanceof EnergyBus) {
      if (!Double.isFinite(duty)) {
        throw new IllegalArgumentException("Energy-port duty must be finite on an energy bus");
      }
      EnergyBus bus = (EnergyBus) stream;
      if (mode == EnergyPortMode.SPECIFICATION) {
        if (bus.hasSolution()) {
          requestedPower = Math.abs(duty);
          return;
        }
        setRequestedPower(Math.abs(duty));
        double contribution = direction == EnergyPortDirection.OUTPUT ? Math.abs(duty) : -Math.abs(duty);
        bus.setContribution(participantId, contribution);
        return;
      }
      if (mode == EnergyPortMode.BALANCE) {
        setRequestedPower(Math.abs(duty));
        return;
      }
      double contribution = duty;
      if (direction == EnergyPortDirection.INPUT) {
        contribution = -Math.abs(duty);
      } else if (direction == EnergyPortDirection.OUTPUT) {
        contribution = Math.abs(duty);
      }
      bus.setContribution(participantId, contribution);
    } else {
      stream.setDuty(duty);
    }
  }

  /**
   * Sets the connected stream duty in a specified unit.
   *
   * @param duty duty value
   * @param unit power unit
   * @throws IllegalStateException if no stream is connected
   */
  public void setDuty(double duty, String unit) {
    setDuty(new PowerUnit(duty, unit).getValue("W"));
  }

  /**
   * Reports power physically realized by balance equipment after transient constraints are applied.
   *
   * @param actualPower positive generation or negative consumption in W
   * @return updated energy-network report
   * @throws IllegalStateException if this is not a connected energy-bus balance port
   */
  public EnergyNetworkReport reportRealizedBalancePower(double actualPower) {
    EnergyStream stream = requireConnectedStream();
    if (!(stream instanceof EnergyBus) || mode != EnergyPortMode.BALANCE) {
      throw new IllegalStateException("Realized balance power requires an energy-bus balance port");
    }
    return ((EnergyBus) stream).reportRealizedBalancePower(this, actualPower);
  }

  /**
   * Clears previously realized balance power before a new transient dispatch.
   */
  public void clearRealizedBalancePower() {
    EnergyStream stream = requireConnectedStream();
    if (!(stream instanceof EnergyBus) || mode != EnergyPortMode.BALANCE) {
      throw new IllegalStateException("Realized balance power requires an energy-bus balance port");
    }
    ((EnergyBus) stream).clearRealizedBalancePower(participantId);
  }

  /** Invalidates the connected bus solution after a port setting changes. */
  private void invalidateBusSolution() {
    if (energyStream instanceof EnergyBus) {
      EnergyBus energyBus = (EnergyBus) energyStream;
      energyBus.clearRealizedBalancePower(participantId);
      energyBus.invalidateSolution();
    }
  }

  /**
   * Gets the connected stream or raises a configuration error.
   *
   * @return connected stream
   */
  private EnergyStream requireConnectedStream() {
    if (energyStream == null) {
      throw new IllegalStateException("Energy port " + name + " is not connected");
    }
    return energyStream;
  }
}
