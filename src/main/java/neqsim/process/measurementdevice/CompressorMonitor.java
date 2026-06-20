package neqsim.process.measurementdevice;

import neqsim.process.equipment.compressor.Compressor;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * CompressorMonitor class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class CompressorMonitor extends MeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  protected Compressor compressor = null;

  /**
   * <p>
   * Constructor for CompressorMonitor.
   * </p>
   *
   * @param compressor a {@link neqsim.process.equipment.compressor.Compressor} object
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
   * @param compressor a {@link neqsim.process.equipment.compressor.Compressor}
   */
  public CompressorMonitor(String name, Compressor compressor) {
    super(name, "rpm");
    this.compressor = compressor;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("measured speed " + compressor.getSpeed());
  }

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
