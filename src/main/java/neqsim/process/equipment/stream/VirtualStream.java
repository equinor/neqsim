package neqsim.process.equipment.stream;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

/**
 * VirtualStream class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class VirtualStream extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(VirtualStream.class);

  protected StreamInterface refStream = null;
  protected StreamInterface outStream = null;
  protected double flowRate;
  protected String flowUnit;
  protected boolean setFlowRate = false;
  protected double temperature;
  protected String temperatureUnit;
  protected boolean setTemperature = false;
  protected double pressure;
  protected String pressureUnit;
  protected boolean setPressure = false;
  protected double[] composition;
  protected String compositionUnit;
  protected boolean setComposition = false;

  /**
   * Constructor for VirtualStream.
   *
   * @param name a {@link java.lang.String} object
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public VirtualStream(String name, StreamInterface stream) {
    super(name);
    refStream = stream;
  }

  /**
   * Constructor for VirtualStream.
   *
   * @param name a {@link java.lang.String} object
   */
  public VirtualStream(String name) {
    super(name);
  }

  /**
   * setReferenceStream.
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setReferenceStream(StreamInterface stream) {
    refStream = stream;
  }

  /**
   * Setter for the field <code>flowRate</code>.
   *
   * @param rate a double
   * @param unit a {@link java.lang.String} object
   */
  public void setFlowRate(double rate, String unit) {
    flowRate = rate;
    flowUnit = unit;
    setFlowRate = true;
  }

  /**
   * Setter for the field <code>composition</code>.
   *
   * @param comps an array of type double
   * @param unit a {@link java.lang.String} object
   */
  public void setComposition(double[] comps, String unit) {
    composition = comps;
    compositionUnit = unit;
    setComposition = true;
  }

  /**
   * Setter for the field <code>temperature</code>.
   *
   * @param temp a double
   * @param unit a {@link java.lang.String} object
   */
  public void setTemperature(double temp, String unit) {
    temperature = temp;
    temperatureUnit = unit;
    setTemperature = true;
  }

  /**
   * Setter for the field <code>pressure</code>.
   *
   * @param pres a double
   * @param unit a {@link java.lang.String} object
   */
  public void setPressure(double pres, String unit) {
    pressure = pres;
    pressureUnit = unit;
    setPressure = true;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    outStream = new Stream("new stram", refStream.getFluid().clone());

    if (setFlowRate) {
      outStream.setFlowRate(flowRate, flowUnit);
    }
    if (setTemperature) {
      outStream.setTemperature(temperature, temperatureUnit);
    }
    if (setPressure) {
      outStream.setPressure(pressure, pressureUnit);
    }
    if (setComposition) {
      outStream.getFluid().setMolarComposition(composition);
    }
    outStream.run(id);

    isSolved = outStream.solved();
  }

  /**
   * Getter for the field <code>outStream</code>.
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getOutStream() {
    return outStream;
  }

  /** {@inheritDoc} */
  @Override
  public boolean solved() {
    return outStream != null ? outStream.solved() : super.solved();
  }
}
