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
   */
  public LevelTransmitter() {}

  /**
   * <p>
   * Constructor for LevelTransmitter.
   * </p>
   *
   * @param separator a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
   */
  public LevelTransmitter(Separator separator) {
    this.separator = separator;
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    System.out.println("measured level " + separator.getLiquidLevel());
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue() {
    return separator.getLiquidLevel();
  }
}
