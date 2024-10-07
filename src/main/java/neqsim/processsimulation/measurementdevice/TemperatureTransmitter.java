/*
 * TemperatureTransmitter.java
 *
 * Created on 6. juni 2006, 15:24
 */

package neqsim.processsimulation.measurementdevice;

import neqsim.processsimulation.processequipment.stream.StreamInterface;

/**
 * <p>
 * TemperatureTransmitter class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class TemperatureTransmitter extends StreamMeasurementDeviceBaseClass {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for TemperatureTransmitter.
   * </p>
   *
   * @param stream a {@link neqsim.processsimulation.processequipment.stream.StreamInterface} object
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
   * @param stream a {@link neqsim.processsimulation.processequipment.stream.StreamInterface} object
   */
  public TemperatureTransmitter(String name, StreamInterface stream) {
    super(name, "K", stream);
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    System.out.println("measured temperature " + getMeasuredValue());
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    return stream.getThermoSystem().getTemperature(unit);
  }
}
