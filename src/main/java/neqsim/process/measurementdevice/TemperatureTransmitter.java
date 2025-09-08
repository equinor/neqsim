/*
 * TemperatureTransmitter.java
 *
 * Created on 6. juni 2006, 15:24
 */

package neqsim.process.measurementdevice;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TemperatureTransmitter class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class TemperatureTransmitter extends StreamMeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for TemperatureTransmitter.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public TemperatureTransmitter(StreamInterface stream) {
    this("Temperature Transmitter", stream);
  }

  /**
   * <p>
   * Constructor for TemperatureTransmitter.
   * </p>
   *
   * @param name Name of TemperatureTransmitter
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public TemperatureTransmitter(String name, StreamInterface stream) {
    super(name, "K", stream);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("measured temperature " + getMeasuredValue());
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    return applySignalModifiers(stream.getThermoSystem().getTemperature(unit));
  }
}
