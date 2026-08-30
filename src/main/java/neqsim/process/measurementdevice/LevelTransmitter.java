package neqsim.process.measurementdevice;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.tank.Tank;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * LevelTransmitter class.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class LevelTransmitter extends MeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected Separator separator = null;
  /** Tank whose liquid level is measured, when this transmitter is tank-backed. */
  protected Tank tank = null;

  /**
   * Constructor for LevelTransmitter.
   *
   * @param separator a {@link neqsim.process.equipment.separator.Separator} object
   */
  public LevelTransmitter(Separator separator) {
    this("LevelTransmitter", separator);
  }

  /**
   * Constructor for LevelTransmitter.
   *
   * @param name Name of LevelTransmitter
   * @param separator a {@link neqsim.process.equipment.separator.Separator} object
   */
  public LevelTransmitter(String name, Separator separator) {
    super(name, "");
    this.setMaximumValue(1);
    this.setMinimumValue(0);
    this.separator = separator;
  }

  /**
   * Constructor for a level transmitter backed by supported vessel equipment.
   *
   * <p>
   * This overload accepts {@link Tank} while preserving the more specific separator constructors. Other equipment
   * classes fail closed because they do not provide the authoritative liquid-level state required by this device.
   * </p>
   *
   * @param vessel a {@link Separator} or {@link Tank}
   */
  public LevelTransmitter(ProcessEquipmentInterface vessel) {
    this("LevelTransmitter", vessel);
  }

  /**
   * Constructor for a named level transmitter backed by supported vessel equipment.
   *
   * @param name name of the level transmitter
   * @param vessel a {@link Separator} or {@link Tank}
   * @throws IllegalArgumentException if {@code vessel} is not a supported level-bearing vessel
   */
  public LevelTransmitter(String name, ProcessEquipmentInterface vessel) {
    super(name, "");
    this.setMaximumValue(1);
    this.setMinimumValue(0);
    if (vessel instanceof Separator) {
      this.separator = (Separator) vessel;
    } else if (vessel instanceof Tank) {
      this.tank = (Tank) vessel;
    } else {
      throw new IllegalArgumentException("LevelTransmitter requires a Separator or Tank");
    }
  }

  /**
   * Returns the separator whose liquid level this transmitter measures.
   *
   * @return the associated {@link neqsim.process.equipment.separator.Separator}, or {@code null} if none was set
   */
  public Separator getSeparator() {
    return separator;
  }

  /**
   * Returns the supported vessel whose liquid level this transmitter measures.
   *
   * @return the associated {@link Separator} or {@link Tank}, or {@code null} when no vessel was set
   */
  public ProcessEquipmentInterface getVessel() {
    return separator != null ? separator : tank;
  }

  /**
   * Returns the tank whose liquid level this transmitter measures.
   *
   * @return the associated {@link Tank}, or {@code null} for a separator-backed transmitter
   */
  public Tank getTank() {
    return tank;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("measured level " + getVesselLiquidLevel());
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (!unit.equalsIgnoreCase("")) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "getMeasuredValue", "unit",
          "currently only supports \"\""));
    }
    return getVesselLiquidLevel();
  }

  private double getVesselLiquidLevel() {
    if (separator != null) {
      return separator.getLiquidLevel();
    }
    if (tank != null) {
      return tank.getLiquidLevel();
    }
    throw new IllegalStateException("LevelTransmitter has no supported vessel");
  }
}
