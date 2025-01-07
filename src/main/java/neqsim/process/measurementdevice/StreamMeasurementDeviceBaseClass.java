package neqsim.process.measurementdevice;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * <p>
 * Abstract MeasurementDeviceBaseClass class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public abstract class StreamMeasurementDeviceBaseClass extends MeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected StreamInterface stream = null;

  /**
   * Constructor for MeasurementDeviceBaseClass.
   *
   * @param name Name of the object
   * @param unit Engineering unit of the measurement
   * @param stream Streaminterface object to measure from
   */
  public StreamMeasurementDeviceBaseClass(String name, String unit, StreamInterface stream) {
    super(name, unit);
    setStream(stream);
  }

  /**
   * Getter for property <code>stream</code>.
   *
   * @return StreamInterface object.
   */
  public StreamInterface getStream() {
    return this.stream;
  }

  /**
   * Setter for property <code>stream</code>.
   *
   * @param stream Stream to set.
   */
  public void setStream(StreamInterface stream) {
    this.stream = stream;
  }
}
