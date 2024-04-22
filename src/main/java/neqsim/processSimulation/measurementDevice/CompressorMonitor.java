package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.compressor.Compressor;

/**
 * <p>
 * CompressorMonitor class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class CompressorMonitor extends MeasurementDeviceBaseClass {
  private static final long serialVersionUID = 1000;
  protected Compressor compressor = null;

  /**
   * <p>
   * Constructor for CompressorMonitor.
   * </p>
   *
   * @param compressor a {@link neqsim.processSimulation.processEquipment.compressor.Compressor}
   *        object
   */
  public CompressorMonitor(Compressor compressor) {
    this("Compressor Monitor", compressor);
  }

  /**
   * <p>
   * Constructor for CompressorMonitor.
   * </p>
   *
   * @param name Name of Compressor
   * @param compressor a {@link neqsim.processSimulation.processEquipment.compressor.Compressor}
   */
  public CompressorMonitor(String name, Compressor compressor) {
    super(name, "rpm");
    this.compressor = compressor;
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {}

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (unit.equals("distance to surge")) {
      return compressor.getDistanceToSurge();
    } else {
      return compressor.getDistanceToSurge();
      // return compressor.getSpeed();
    }
  }
}
