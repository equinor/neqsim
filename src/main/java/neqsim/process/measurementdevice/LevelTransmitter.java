package neqsim.process.measurementdevice;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
  private static final Logger logger = LogManager.getLogger(LevelTransmitter.class);

  protected Separator separator = null;
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
    this(name, (ProcessEquipmentInterface) separator);
  }

  /**
   * Constructor for a tank level transmitter.
   *
   * @param tank tank whose liquid level is measured
   */
  public LevelTransmitter(Tank tank) {
    this("LevelTransmitter", tank);
  }

  /**
   * Constructor for a named tank level transmitter.
   *
   * @param name name of the level transmitter
   * @param tank tank whose liquid level is measured
   */
  public LevelTransmitter(String name, Tank tank) {
    this(name, (ProcessEquipmentInterface) tank);
  }

  /**
   * Constructor for a level transmitter backed by supported vessel equipment.
   *
   * @param vessel a separator or tank
   * @throws IllegalArgumentException when the equipment does not expose a supported vessel level
   */
  public LevelTransmitter(ProcessEquipmentInterface vessel) {
    this("LevelTransmitter", vessel);
  }

  /**
   * Constructor for a named level transmitter backed by supported vessel equipment.
   *
   * @param name name of the level transmitter
   * @param vessel a separator or tank
   * @throws IllegalArgumentException when the equipment does not expose a supported vessel level
   */
  public LevelTransmitter(String name, ProcessEquipmentInterface vessel) {
    super(name, "");
    this.setMaximumValue(1);
    this.setMinimumValue(0);
    if (vessel instanceof Separator) {
      separator = (Separator) vessel;
    } else if (vessel instanceof Tank) {
      tank = (Tank) vessel;
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
   * Returns the tank whose liquid level this transmitter measures.
   *
   * @return associated tank, or {@code null} when this transmitter measures a separator
   */
  public Tank getTank() {
    return tank;
  }

  /**
   * Returns the vessel carrying the measured liquid inventory.
   *
   * @return associated separator or tank, or {@code null} if no source was configured
   */
  public ProcessEquipmentInterface getLevelEquipment() {
    return separator != null ? separator : tank;
  }

  /**
   * Returns the vessel carrying the measured liquid inventory.
   *
   * @return associated separator or tank
   */
  public ProcessEquipmentInterface getVessel() {
    return getLevelEquipment();
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    logger.info("measured level {}", measuredLevel());
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (!unit.equalsIgnoreCase("")) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "getMeasuredValue", "unit",
          "currently only supports \"\""));
    }
    return measuredLevel();
  }

  private double measuredLevel() {
    if (separator != null) {
      return separator.getLiquidLevel();
    }
    if (tank != null) {
      return tank.getLiquidLevel();
    }
    throw new IllegalStateException("Level transmitter has no separator or tank source");
  }
}
