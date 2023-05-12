package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 * <p>
 * LevelTransmitter class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class LevelTransmitter extends MeasurementDeviceBaseClass {
  private static final long serialVersionUID = 1000;

  protected Separator separator = null;

  /**
   * <p>
   * Constructor for LevelTransmitter.
   * </p>
   *
   * @param separator a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
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
   * @param separator a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
   */
  public LevelTransmitter(String name, Separator separator) {
    super(name, "");
    this.setMaximumValue(1);
    this.setMinimumValue(0);
    this.separator = separator;
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    System.out.println("measured level " + separator.getLiquidLevel());
  }

  /**
   * Get level as volume fraction.
   */
  @Override
  public double getMeasuredValue(String unit) {
    if (!unit.equalsIgnoreCase("")) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "getMeasuredValue", "unit", "currently only supports \"\""));
    }
    return separator.getLiquidLevel();
  }
}
