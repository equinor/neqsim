package neqsim.process.measurementdevice;

import neqsim.process.equipment.separator.Separator;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * LevelTransmitter class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class LevelTransmitter extends MeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected Separator separator = null;

  /**
   * <p>
   * Constructor for LevelTransmitter.
   * </p>
   *
   * @param separator a {@link neqsim.process.equipment.separator.Separator} object
   */
  public LevelTransmitter(Separator separator) {
    this("LevelTransmitter", separator);
  }

  /**
   * <p>
   * Constructor for LevelTransmitter.
   * </p>
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

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("measured level " + separator.getLiquidLevel());
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (!unit.equalsIgnoreCase("")) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "getMeasuredValue", "unit", "currently only supports \"\""));
    }
    return separator.getLiquidLevel();
  }
}
