package neqsim.processSimulation.processEquipment.heatExchanger;

import java.util.UUID;
import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * ReBoiler class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ReBoiler extends TwoPortEquipment {
  private static final long serialVersionUID = 1000;

  boolean setTemperature = false;
  SystemInterface system;
  private double reboilerDuty = 0.0;

  /**
   * <p>
   * Constructor for ReBoiler.
   * </p>
   */
  @Deprecated
  public ReBoiler() {
    super("ReBoiler");
  }

  /**
   * <p>
   * Constructor for ReBoiler.
   * </p>
   *
   * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  @Deprecated
  public ReBoiler(StreamInterface inStream) {
    this("ReBoiler", inStream);
  }

  /**
   * <p>
   * Constructor for ReBoiler.
   * </p>
   *
   * @param name name of reboiler
   * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
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
  public void displayResult() {}

  /**
   * <p>
   * Getter for the field <code>reboilerDuty</code>.
   * </p>
   *
   * @return a double
   */
  public double getReboilerDuty() {
    return reboilerDuty;
  }

  /**
   * <p>
   * Setter for the field <code>reboilerDuty</code>.
   * </p>
   *
   * @param reboilerDuty a double
   */
  public void setReboilerDuty(double reboilerDuty) {
    this.reboilerDuty = reboilerDuty;
  }
}
