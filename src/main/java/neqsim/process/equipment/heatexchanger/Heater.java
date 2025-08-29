/*
 * Heater.java
 *
 * Created on 15. mars 2001, 14:17
 */

package neqsim.process.equipment.heatexchanger;

import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.monitor.HeaterResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Heater class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Heater extends TwoPortEquipment implements HeaterInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  boolean setTemperature = false;

  boolean setOutPressure = false;

  SystemInterface system;
  protected double temperatureOut = 0;

  protected double dT = 0.0;

  protected double pressureOut = 0;

  private boolean setEnergyInput = false;
  private double energyInput = 0.0;
  private double pressureDrop = 0.0;
  private String temperatureUnit = "K";
  private String pressureUnit = "bara";
  double coolingMediumTemperature = 278.15;

  // Results from previous calculation
  protected double lastTemperature = 0.0;
  protected double lastPressure = 0.0;
  protected double lastFlowRate = 0.0;
  protected double lastOutPressure = 0.0;
  protected double lastOutTemperature = 0.0;
  protected double lastDuty = 0.0;
  protected double lastPressureDrop = 0.0;

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
   * @param inStream a {@link neqsim.process.equipment.stream.StreamInterface} object
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
    setTemperature = false;
    setEnergyInput = false;
    this.dT = dT;
  }

  /**
   * <p>
   * setOutPressure.
   * </p>
   *
   * @param pressure Pressure in bara
   */
  public void setOutPressure(double pressure) {
    setOutPressure = true;
    this.pressureUnit = "bara";
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
   * Set the outlet temperature of the heater.
   * </p>
   *
   * @param temperature Temperature in Kelvin
   */
  public void setOutTemperature(double temperature) {
    setTemperature = true;
    setEnergyInput = false;
    this.temperatureUnit = "K";
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
    temperatureUnit = "K";
    this.pressureUnit = "bara";
    setOutPressure = true;
    this.pressureOut = pressure;
  }

  /** {@inheritDoc} */
  @Override
  public boolean needRecalculation() {
    if (inStream == null) {
      return true;
    }
    if (inStream.getFluid().getTemperature() == lastTemperature
        && inStream.getFluid().getPressure() == lastPressure
        && Math.abs(inStream.getFluid().getFlowRate("kg/hr") - lastFlowRate)
            / inStream.getFluid().getFlowRate("kg/hr") < 1e-6
        && lastDuty == getDuty() && lastOutPressure == pressureOut
        && lastOutTemperature == temperatureOut && getPressureDrop() == lastPressureDrop) {
      return false;
    } else {
      return true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    system = inStream.getThermoSystem().clone();
    isActive(true);

    if (inStream.getFlowRate("kg/hr") < getMinimumFlow()) {
      isActive(false);
      getOutletStream().setThermoSystem(system);
      lastTemperature = inStream.getFluid().getTemperature();
      lastPressure = inStream.getFluid().getPressure();
      lastFlowRate = inStream.getFluid().getFlowRate("kg/hr");
      lastDuty = getDuty();
      lastOutPressure = pressureOut;
      lastOutTemperature = temperatureOut;
      lastPressureDrop = pressureDrop;
      setCalculationIdentifier(id);
      return;
    }
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
    system.initProperties();
    getOutletStream().setThermoSystem(system);
    lastTemperature = inStream.getFluid().getTemperature();
    lastPressure = inStream.getFluid().getPressure();
    lastFlowRate = inStream.getFluid().getFlowRate("kg/hr");
    lastDuty = getDuty();
    lastOutPressure = pressureOut;
    lastOutTemperature = temperatureOut;
    lastPressureDrop = pressureDrop;
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      run(id);
      increaseTime(dt);
    } else {
      inStream.setPressure(outStream.getPressure());
      inStream.run();
      run(id);
      increaseTime(dt);
    }
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
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
   * getDuty.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getDuty(String unit) {
    // Use PowerUnit for conversion
    neqsim.util.unit.PowerUnit powerUnit = new neqsim.util.unit.PowerUnit(energyInput, "W");
    return powerUnit.getValue(unit);
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
    UUID id = UUID.randomUUID();
    inStream.run(id);
    inStream.getFluid().init(3);
    outStream.run(id);
    outStream.getFluid().init(3);

    double entrop =
        outStream.getThermoSystem().getEntropy(unit) - inStream.getThermoSystem().getEntropy(unit);

    return entrop;
  }

  /** {@inheritDoc} */
  @Override
  public double getExergyChange(String unit, double surroundingTemperature) {
    UUID id = UUID.randomUUID();
    inStream.run(id);
    inStream.getFluid().init(3);
    outStream.run(id);
    outStream.getFluid().init(3);

    return outStream.getThermoSystem().getExergy(surroundingTemperature, unit)
        - inStream.getThermoSystem().getExergy(surroundingTemperature, unit);
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().create().toJson(new HeaterResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    HeaterResponse res = new HeaterResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().create().toJson(res);
  }
}
