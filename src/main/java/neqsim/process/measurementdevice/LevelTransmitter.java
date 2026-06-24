package neqsim.process.measurementdevice;

import neqsim.process.equipment.separator.Separator;
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
   * Returns the separator whose liquid level this transmitter measures.
   *
   * @return the associated {@link neqsim.process.equipment.separator.Separator}, or {@code null} if none was set
   */
  public Separator getSeparator() {
    return separator;
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
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "getMeasuredValue", "unit",
	  "currently only supports \"\""));
    }
    return separator.getLiquidLevel();
  }
}
