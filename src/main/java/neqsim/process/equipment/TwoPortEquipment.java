package neqsim.process.equipment;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Abstract class defining ProcessEquipment with one inlet and one outlet.
 *
 * @author ASMF
 * @version $Id: $Id
 */
public abstract class TwoPortEquipment extends ProcessEquipmentBaseClass
    implements TwoPortInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  protected StreamInterface inStream;
  protected StreamInterface outStream;

  /**
   * Constructor for TwoPortEquipment.
   *
   * @param name Name of TwoPortEquipment
   */
  public TwoPortEquipment(String name) {
    super(name);
  }

  /**
   * Constructor for TwoPortEquipment.
   *
   * @param name Name of TwoPortEquipment
   * @param stream Stream to set as inlet Stream. A clone of stream is set as outlet stream.
   */
  public TwoPortEquipment(String name, StreamInterface stream) {
    this(name);
    this.setInletStream(stream);
  }

  /** {@inheritDoc} */
  @Override
  public double getInletPressure() {
    return getInletStream().getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getInletStream() {
    return inStream;
  }

  /** {@inheritDoc} */
  @Override
  public double getInletTemperature() {
    return getInletStream().getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double getOutletPressure() {
    return getOutletStream().getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getOutletStream() {
    return outStream;
  }

  /** {@inheritDoc} */
  @Override
  public double getOutletTemperature() {
    return getOutletStream().getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public void setInletPressure(double pressure) {
    this.inStream.setPressure(pressure);
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface stream) {
    this.inStream = stream;
    this.outStream = inStream.clone(this.getName() + " out stream");
  }

  /** {@inheritDoc} */
  @Override
  public void setInletTemperature(double temperature) {
    this.inStream.setTemperature(temperature, "unit");
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletPressure(double pressure) {
    this.outStream.setPressure(pressure);
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletStream(StreamInterface stream) {
    this.outStream = stream;
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletTemperature(double temperature) {
    this.outStream.setTemperature(temperature, "unit");
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return null;
  }
}
