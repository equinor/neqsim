package neqsim.process.equipment.stream;

import java.io.Serializable;
import java.util.Objects;

/**
 * Named, typed connection point between process equipment and an {@link EnergyStream}.
 *
 * <p>An energy port separates three concerns that were previously implicit in the sign of a duty:
 * the physical energy domain, the physical transfer direction, and the calculation role. This lets
 * graph-based schedulers order an energy producer before a consumer without changing the legacy
 * {@code EnergyStream} duty convention.
 *
 * @author NeqSim
 * @version 1.0
 */
public class EnergyPort implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final EnergyType energyType;
  private final EnergyPortDirection direction;
  private EnergyPortMode mode;
  private EnergyStream energyStream;

  /**
   * Creates an unconnected energy port.
   *
   * @param name unique port name within its equipment
   * @param energyType physical energy domain
   * @param direction physical energy direction relative to the equipment
   * @param mode calculation role of the port
   */
  public EnergyPort(String name, EnergyType energyType, EnergyPortDirection direction,
      EnergyPortMode mode) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Energy port name cannot be null or empty");
    }
    this.name = name;
    this.energyType = Objects.requireNonNull(energyType, "energyType cannot be null");
    this.direction = Objects.requireNonNull(direction, "direction cannot be null");
    this.mode = Objects.requireNonNull(mode, "mode cannot be null");
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
  }

  /**
   * Connects an energy stream to this port.
   *
   * <p>A legacy stream with type {@link EnergyType#UNSPECIFIED} adopts the port type. A typed stream
   * can only be connected to a port of the same type or to an unspecified port.
   *
   * @param stream energy stream to connect
   * @throws IllegalArgumentException if the stream and port energy types conflict
   */
  public void connect(EnergyStream stream) {
    Objects.requireNonNull(stream, "stream cannot be null");
    EnergyType streamType = stream.getEnergyType();
    if (streamType != EnergyType.UNSPECIFIED && energyType != EnergyType.UNSPECIFIED
        && streamType != energyType) {
      throw new IllegalArgumentException("Energy stream type " + streamType
          + " is incompatible with port type " + energyType + " for port " + name);
    }
    if (streamType == EnergyType.UNSPECIFIED && energyType != EnergyType.UNSPECIFIED) {
      stream.setEnergyType(energyType);
    }
    energyStream = stream;
  }

  /** Disconnects the current energy stream, if any. */
  public void disconnect() {
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
    return requireConnectedStream().getDuty();
  }

  /**
   * Gets the connected stream duty in a requested unit.
   *
   * @param unit requested power unit
   * @return duty in the requested unit
   * @throws IllegalStateException if no stream is connected
   */
  public double getDuty(String unit) {
    return requireConnectedStream().getDuty(unit);
  }

  /**
   * Sets the connected stream duty in watts.
   *
   * @param duty duty in W
   * @throws IllegalStateException if no stream is connected
   */
  public void setDuty(double duty) {
    requireConnectedStream().setDuty(duty);
  }

  /**
   * Sets the connected stream duty in a specified unit.
   *
   * @param duty duty value
   * @param unit power unit
   * @throws IllegalStateException if no stream is connected
   */
  public void setDuty(double duty, String unit) {
    requireConnectedStream().setDuty(duty, unit);
  }

  private EnergyStream requireConnectedStream() {
    if (energyStream == null) {
      throw new IllegalStateException("Energy port " + name + " is not connected");
    }
    return energyStream;
  }
}
