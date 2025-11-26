package neqsim.process.measurementdevice;

import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * OilLevelTransmitter class for measuring oil level in three-phase separators.
 * </p>
 *
 * <p>
 * This transmitter measures the total liquid level (water + oil) from the bottom of the separator.
 * In a three-phase separator, the oil phase floats on top of the water phase. The oil level
 * represents the interface between the oil and gas phases.
 * </p>
 *
 * <p>
 * To get the oil layer thickness, subtract the water level from the oil level: oilThickness =
 * oilLevel - waterLevel
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class OilLevelTransmitter extends MeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected ThreePhaseSeparator separator = null;

  /**
   * <p>
   * Constructor for OilLevelTransmitter.
   * </p>
   *
   * @param separator a {@link neqsim.process.equipment.separator.ThreePhaseSeparator} object
   */
  public OilLevelTransmitter(ThreePhaseSeparator separator) {
    this("OilLevelTransmitter", separator);
  }

  /**
   * <p>
   * Constructor for OilLevelTransmitter.
   * </p>
   *
   * @param name Name of OilLevelTransmitter
   * @param separator a {@link neqsim.process.equipment.separator.ThreePhaseSeparator} object
   */
  public OilLevelTransmitter(String name, ThreePhaseSeparator separator) {
    super(name, "m");
    this.separator = separator;
    this.setMaximumValue(separator.getInternalDiameter());
    this.setMinimumValue(0.0);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("measured oil level: " + separator.getOilLevel() + " m");
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (!unit.equalsIgnoreCase("m") && !unit.equalsIgnoreCase("")) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "getMeasuredValue", "unit", "currently only supports \"m\" or \"\""));
    }
    return separator.getOilLevel();
  }

  /**
   * <p>
   * Get the thickness of the oil layer.
   * </p>
   *
   * @return oil layer thickness in meters (oilLevel - waterLevel)
   */
  public double getOilThickness() {
    return separator.getOilLevel() - separator.getWaterLevel();
  }
}
