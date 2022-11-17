package neqsim.processSimulation.processEquipment.stream;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;

/**
 * <p>
 * VirtualStream class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class VirtualStream extends ProcessEquipmentBaseClass {
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

  public VirtualStream(String name, StreamInterface stream) {
    super(name);
    refStream = stream;
  }

  public VirtualStream(String name) {
    super(name);
  }

  public void setReferenceStream(StreamInterface stream) {
    refStream = stream;
  }


  public void setFlowRate(double rate, String unit) {
    flowRate = rate;
    flowUnit = unit;
    setFlowRate = true;
  }

  public void setComposition(double[] comps, String unit) {
    composition = comps;
    compositionUnit = unit;
    setComposition = true;
  }

  public void setTemperature(double temp, String unit) {
    temperature = temp;
    temperatureUnit = unit;
    setTemperature = true;
  }


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
    outStream.run();
  }

  public StreamInterface getOutStream() {
    return outStream;
  }

  @Override
  public boolean solved() {
    // TODO Auto-generated method stub
    return false;
  }
}
