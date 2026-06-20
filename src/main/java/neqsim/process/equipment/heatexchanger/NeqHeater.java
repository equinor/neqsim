package neqsim.process.equipment.heatexchanger;

import java.util.UUID;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * NeqHeater class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class NeqHeater extends Heater {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  SystemInterface system;
  double dH = 0.0;

  /**
   * Constructor for NeqHeater.
   *
   * @param name name of heater
   */
  public NeqHeater(String name) {
    super(name);
  }

  /**
   * Constructor for NeqHeater.
   *
   * @param name name of heater
   * @param inStream input stream
   */
  public NeqHeater(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /** {@inheritDoc} */
  @Override
  public void setOutTemperature(double temperature) {
    this.setTemperature = true;
    this.temperatureOut = temperature;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    system = inStream.getThermoSystem().clone();
    double oldH = system.getEnthalpy();
    if (setTemperature) {
      system.setTemperature(temperatureOut);
    } else {
      system.setTemperature(system.getTemperature() + dT);
    }
    system.init(3);
    double newH = system.getEnthalpy();
    dH = newH - oldH;
    // system.setTemperature(temperatureOut);
    // testOps.TPflash();
    // system.setTemperature(temperatureOut);
    outStream.setThermoSystem(system);

    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("heater dH: " + dH);
  }
}
