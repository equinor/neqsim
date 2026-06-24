package neqsim.process.equipment.heatexchanger;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * ReBoiler class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ReBoiler extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  boolean setTemperature = false;
  SystemInterface system;
  private double reboilerDuty = 0.0;

  /**
   * Constructor for ReBoiler.
   *
   * @param name name of reboiler
   * @param inStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public ReBoiler(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    system = inStream.getThermoSystem().clone();
    ThermodynamicOperations testOps = new ThermodynamicOperations(system);
    testOps.TPflash();
    double oldH = system.getEnthalpy();
    testOps = new ThermodynamicOperations(system);
    testOps.TPflash();
    testOps.PHflash(oldH + reboilerDuty, 0);
    outStream.setThermoSystem(system);
    // if(setTemperature) system.setTemperature(temperatureOut);
    // else system.setTemperature(system.getTemperature()+dT);
    // testOps = new ThermodynamicOperations(system);
    // system.setTemperat ure(temperatureOut);
    // testOps.TPflash();
    // double newH = system.getEnthalpy();
    // dH = newH - oldH;
    // // system.setTemperature(temperatureOut);
    // // testOps.TPflash();
    // // system.setTemperature(temperatureOut);
    // outStream.setThermoSystem(system);
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("out Temperature " + reboilerDuty);
  }

  /**
   * Getter for the field <code>reboilerDuty</code>.
   *
   * @return a double
   */
  public double getReboilerDuty() {
    return reboilerDuty;
  }

  /**
   * Setter for the field <code>reboilerDuty</code>.
   *
   * @param reboilerDuty a double
   */
  public void setReboilerDuty(double reboilerDuty) {
    this.reboilerDuty = reboilerDuty;
  }
}
