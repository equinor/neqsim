/*
 * Heater.java
 *
 * Created on 15. mars 2001, 14:17
 */

package neqsim.processSimulation.processEquipment.heatExchanger;

import java.util.UUID;
import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Heater class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Heater extends TwoPortEquipment implements HeaterInterface {
  private static final long serialVersionUID = 1000;

  boolean setTemperature = false, setOutPressure = false;
  SystemInterface system;
  protected double temperatureOut = 0, dT = 0.0, pressureOut = 0;
  private boolean setEnergyInput = false;
  private double energyInput = 0.0;
  private double pressureDrop = 0.0;
  private String temperatureUnit = "K";
  private String pressureUnit = "bara";
  double coolingMediumTemperature = 278.15;

  /**
   * <p>
   * Constructor for Heater.
   * </p>
   */
  @Deprecated
  public Heater() {
    super("Heater");
  }

  /**
   * <p>
   * Constructor for Heater.
   * </p>
   *
   * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  @Deprecated
  public Heater(StreamInterface inStream) {
    this("Heater", inStream);
  }

  /**
   * Constructor for Heater.
   * 
   * @param name name of heater
   */
  public Heater(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for Heater.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  public Heater(String name, StreamInterface inStream) {
    super(name);
    this.inStream = inStream;
    system = inStream.getThermoSystem().clone();
    outStream = new Stream("outStream", system);
  }

  /** {@inheritDoc} */
  @Override
  public void setdT(double dT) {
    this.dT = dT;
  }

  /**
   * <p>
   * setOutPressure.
   * </p>
   *
   * @param pressure a double
   */
  public void setOutPressure(double pressure) {
    setOutPressure = true;
    this.pressureOut = pressure;
  }

  /** {@inheritDoc} */
  @Override
  public void setOutPressure(double pressure, String unit) {
    setOutPressure = true;
    this.pressureOut = pressure;
    this.pressureUnit = unit;
  }

  /**
   * <p>
   * setOutTemperature.
   * </p>
   *
   * @param temperature a double
   */
  public void setOutTemperature(double temperature) {
    setTemperature = true;
    setEnergyInput = false;
    this.temperatureOut = temperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setOutTemperature(double temperature, String unit) {
    setTemperature = true;
    setEnergyInput = false;
    this.temperatureUnit = unit;
    this.temperatureOut = temperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setOutTP(double temperature, double pressure) {
    setTemperature = true;
    setEnergyInput = false;
    this.temperatureOut = temperature;
    setOutPressure = true;
    this.pressureOut = pressure;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    system = inStream.getThermoSystem().clone();
    system.init(3);
    double oldH = system.getEnthalpy();
    if (isSetEnergyStream()) {
      energyInput = -energyStream.getDuty();
    }
    double newEnthalpy = energyInput + oldH;
    system.setPressure(system.getPressure() - pressureDrop, pressureUnit);
    if (setOutPressure) {
      system.setPressure(pressureOut, pressureUnit);
    }
    ThermodynamicOperations testOps = new ThermodynamicOperations(system);
    if (getSpecification().equals("out stream")) {
      getOutletStream().setFlowRate(getInletStream().getFlowRate("kg/sec"), "kg/sec");
      getOutletStream().run(id);
      temperatureOut = getOutletStream().getTemperature();
      system = getOutletStream().getThermoSystem().clone();
    } else if (setTemperature) {
      system.setTemperature(temperatureOut, temperatureUnit);
      testOps.TPflash();
    } else if (setEnergyInput || isSetEnergyStream()) {
      testOps.PHflash(newEnthalpy, 0);
    } else {
      // System.out.println("temperaturee out " + inStream.getTemperature());
      system.setTemperature(inStream.getTemperature() + dT, temperatureUnit);
      testOps.TPflash();
    }

    // system.setTemperature(temperatureOut);
    system.init(3);
    double newH = system.getEnthalpy();
    energyInput = newH - oldH;
    if (!isSetEnergyStream()) {
      getEnergyStream().setDuty(energyInput);
    }
    // system.setTemperature(temperatureOut);
    // testOps.TPflash();
    // system.setTemperature(temperatureOut);
    getOutletStream().setThermoSystem(system);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    // System.out.println("heater dH: " + energyInput);
    getOutletStream().displayResult();
  }

  /**
   * <p>
   * Getter for the field <code>energyInput</code>.
   * </p>
   *
   * @return a double
   */
  public double getEnergyInput() {
    return energyInput;
  }

  /**
   * <p>
   * getDuty.
   * </p>
   *
   * @return a double
   */
  public double getDuty() {
    return energyInput;
  }

  /**
   * <p>
   * Setter for the field <code>energyInput</code>.
   * </p>
   *
   * @param energyInput a double
   */
  public void setEnergyInput(double energyInput) {
    this.energyInput = energyInput;
    setTemperature = false;
    setEnergyInput = true;
  }

  /**
   * <p>
   * setDuty.
   * </p>
   *
   * @param energyInput a double
   */
  public void setDuty(double energyInput) {
    setEnergyInput(energyInput);
  }

  /**
   * <p>
   * isSetEnergyInput.
   * </p>
   *
   * @return a boolean
   */
  public boolean isSetEnergyInput() {
    return setEnergyInput;
  }

  /**
   * <p>
   * Setter for the field <code>setEnergyInput</code>.
   * </p>
   *
   * @param setEnergyInput a boolean
   */
  public void setSetEnergyInput(boolean setEnergyInput) {
    this.setEnergyInput = setEnergyInput;
  }

  /**
   * <p>
   * Getter for the field <code>pressureDrop</code>.
   * </p>
   *
   * @return the pressureDrop
   */
  public double getPressureDrop() {
    return pressureDrop;
  }

  /**
   * <p>
   * Setter for the field <code>pressureDrop</code>.
   * </p>
   *
   * @param pressureDrop the pressureDrop to set
   */
  public void setPressureDrop(double pressureDrop) {
    this.pressureDrop = pressureDrop;
  }

  /**
   * <p>
   * Setter for the field <code>outStream</code>.
   * </p>
   *
   * @param outStream the outStream to set
   */
  @Deprecated
  public void setOutStream(StreamInterface outStream) {
    setOutletStream(outStream);
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    double entrop = 0.0;

    inStream.run();
    inStream.getFluid().init(3);
    outStream.run();
    outStream.getFluid().init(3);

    entrop +=
        outStream.getThermoSystem().getEntropy(unit) - inStream.getThermoSystem().getEntropy(unit);

    return entrop;
  }

  /** {@inheritDoc} */
  @Override
  public double getExergyChange(String unit, double surroundingTemperature) {
    inStream.run();
    inStream.getFluid().init(3);
    outStream.run();
    outStream.getFluid().init(3);

    return outStream.getThermoSystem().getExergy(surroundingTemperature, unit)
        - inStream.getThermoSystem().getExergy(surroundingTemperature, unit);
  }
}
