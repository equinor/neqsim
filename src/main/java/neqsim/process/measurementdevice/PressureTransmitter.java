package neqsim.process.measurementdevice;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * PressureTransmitter class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PressureTransmitter extends StreamMeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for PressureTransmitter.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public PressureTransmitter(StreamInterface stream) {
    this("Pressure Transmitter", stream);
  }

  /**
   * <p>
   * Constructor for PressureTransmitter.
   * </p>
   *
   * @param name Name of PressureTransmitter
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public PressureTransmitter(String name, StreamInterface stream) {
    super(name, "bar", stream);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("measured temperature " + stream.getPressure());
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    return stream.getThermoSystem().getPressure(unit);
  }
}
