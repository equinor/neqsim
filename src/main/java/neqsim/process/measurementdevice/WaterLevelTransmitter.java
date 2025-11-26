package neqsim.process.measurementdevice;

import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * WaterLevelTransmitter class for measuring water level in three-phase separators.
 * </p>
 *
 * <p>
 * This transmitter measures the water level from the bottom of the separator. In a three-phase
 * separator, water is the heaviest phase and settles at the bottom, with oil floating on top of the
 * water, and gas in the upper portion.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class WaterLevelTransmitter extends MeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected ThreePhaseSeparator separator = null;

  /**
   * <p>
   * Constructor for WaterLevelTransmitter.
   * </p>
   *
   * @param separator a {@link neqsim.process.equipment.separator.ThreePhaseSeparator} object
   */
  public WaterLevelTransmitter(ThreePhaseSeparator separator) {
    this("WaterLevelTransmitter", separator);
  }

  /**
   * <p>
   * Constructor for WaterLevelTransmitter.
   * </p>
   *
   * @param name Name of WaterLevelTransmitter
   * @param separator a {@link neqsim.process.equipment.separator.ThreePhaseSeparator} object
   */
  public WaterLevelTransmitter(String name, ThreePhaseSeparator separator) {
    super(name, "m");
    this.separator = separator;
    this.setMaximumValue(separator.getInternalDiameter());
    this.setMinimumValue(0.0);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("measured water level: " + separator.getWaterLevel() + " m");
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (!unit.equalsIgnoreCase("m") && !unit.equalsIgnoreCase("")) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "getMeasuredValue", "unit", "currently only supports \"m\" or \"\""));
    }
    return separator.getWaterLevel();
  }
}
