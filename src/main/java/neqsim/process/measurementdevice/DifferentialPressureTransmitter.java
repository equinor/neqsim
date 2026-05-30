package neqsim.process.measurementdevice;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Differential-pressure transmitter that reports the pressure difference between two streams in
 * bar. Useful for orifice meters, filter ΔP monitoring, and across-equipment health checks. The
 * device convention is ΔP = P(high) − P(low); negative readings indicate reversed flow.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class DifferentialPressureTransmitter extends MeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private StreamInterface highPressureStream;
  private StreamInterface lowPressureStream;

  /**
   * Constructor with default name "DP Transmitter".
   *
   * @param highPressureStream the upstream / high-pressure stream
   * @param lowPressureStream the downstream / low-pressure stream
   */
  public DifferentialPressureTransmitter(StreamInterface highPressureStream,
      StreamInterface lowPressureStream) {
    this("DP Transmitter", highPressureStream, lowPressureStream);
  }

  /**
   * Constructor.
   *
   * @param name device tag (non-null)
   * @param highPressureStream upstream stream
   * @param lowPressureStream downstream stream
   */
  public DifferentialPressureTransmitter(String name, StreamInterface highPressureStream,
      StreamInterface lowPressureStream) {
    super(name, "bar");
    if (highPressureStream == null || lowPressureStream == null) {
      throw new IllegalArgumentException("both streams must be non-null");
    }
    this.highPressureStream = highPressureStream;
    this.lowPressureStream = lowPressureStream;
  }

  /**
   * Returns the upstream (high) stream.
   *
   * @return stream
   */
  public StreamInterface getHighPressureStream() {
    return highPressureStream;
  }

  /**
   * Returns the downstream (low) stream.
   *
   * @return stream
   */
  public StreamInterface getLowPressureStream() {
    return lowPressureStream;
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    double pHigh = highPressureStream.getThermoSystem().getPressure(unit);
    double pLow = lowPressureStream.getThermoSystem().getPressure(unit);
    return applySignalModifiers(pHigh - pLow);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println(getName() + ": ΔP = " + getMeasuredValue("bar") + " bar");
  }
}
