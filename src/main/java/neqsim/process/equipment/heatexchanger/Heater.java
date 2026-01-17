/*
 * Heater.java
 *
 * Created on 15. mars 2001, 14:17
 */

package neqsim.process.equipment.heatexchanger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.design.AutoSizeable;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerMechanicalDesign;
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
public class Heater extends TwoPortEquipment implements HeaterInterface,
    neqsim.process.equipment.capacity.CapacityConstrainedEquipment, AutoSizeable {
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

  protected transient HeatExchangerMechanicalDesign mechanicalDesign;
  private UtilityStreamSpecification utilitySpecification = new UtilityStreamSpecification();

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
  public HeatExchangerMechanicalDesign getMechanicalDesign() {
    if (mechanicalDesign == null) {
      initMechanicalDesign();
    }
    return mechanicalDesign;
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    mechanicalDesign = new HeatExchangerMechanicalDesign(this);
  }

  /** {@inheritDoc} */
  @Override
  public double getCapacityDuty() {
    return getDuty();
  }

  /** {@inheritDoc} */
  @Override
  public double getCapacityMax() {
    return getMechanicalDesign().maxDesignDuty;
  }

  /**
   * Returns the utility-side specification used by the mechanical design calculation.
   *
   * @return the utility specification instance for this heater/cooler
   */
  public UtilityStreamSpecification getUtilitySpecification() {
    return utilitySpecification;
  }

  /**
   * Replace the current utility-side specification.
   *
   * @param specification new utility specification instance
   */
  public void setUtilitySpecification(UtilityStreamSpecification specification) {
    this.utilitySpecification =
        specification != null ? specification : new UtilityStreamSpecification();
  }

  /**
   * Convenience method to set the utility supply temperature.
   *
   * @param temperature utility temperature value
   * @param unit unit of the provided value (e.g. "K" or "C")
   */
  public void setUtilitySupplyTemperature(double temperature, String unit) {
    utilitySpecification.setSupplyTemperature(temperature, unit);
  }

  /**
   * Convenience method to set the utility return temperature.
   *
   * @param temperature utility temperature value
   * @param unit unit of the provided value (e.g. "K" or "C")
   */
  public void setUtilityReturnTemperature(double temperature, String unit) {
    utilitySpecification.setReturnTemperature(temperature, unit);
  }

  /**
   * Convenience method to set the minimum approach temperature between process and utility.
   *
   * @param approach minimum temperature difference
   * @param unit unit of the provided value (e.g. "K" or "C")
   */
  public void setUtilityApproachTemperature(double approach, String unit) {
    utilitySpecification.setApproachTemperature(approach, unit);
  }

  /**
   * Convenience method to set the assumed utility-side heat capacity rate.
   *
   * @param heatCapacityRate utility heat capacity rate in W/K
   */
  public void setUtilityHeatCapacityRate(double heatCapacityRate) {
    utilitySpecification.setHeatCapacityRate(heatCapacityRate);
  }

  /**
   * Convenience method to set the assumed overall heat-transfer coefficient for sizing.
   *
   * @param u overall heat-transfer coefficient in W/(m^2*K)
   */
  public void setUtilityOverallHeatTransferCoefficient(double u) {
    utilitySpecification.setOverallHeatTransferCoefficient(u);
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
   * Sets the maximum design duty (heating or cooling capacity) for capacity constraint checking.
   * 
   * <p>
   * The duty is specified in Watts (W). Positive values indicate heating capacity, negative values
   * indicate cooling capacity. For constraint checking, the absolute value is used.
   * </p>
   *
   * @param maxDuty maximum design duty in Watts [W]
   */
  public void setMaxDesignDuty(double maxDuty) {
    getMechanicalDesign().setMaxDesignDuty(maxDuty);
    // Reinitialize capacity constraints with the new design value
    capacityConstraints.clear();
  }

  /**
   * Sets the maximum design duty with unit specification.
   *
   * @param maxDuty maximum design duty value
   * @param unit unit of the duty value (e.g., "W", "kW", "MW")
   */
  public void setMaxDesignDuty(double maxDuty, String unit) {
    neqsim.util.unit.PowerUnit powerUnit = new neqsim.util.unit.PowerUnit(maxDuty, unit);
    setMaxDesignDuty(powerUnit.getValue("W"));
  }

  /**
   * Gets the maximum design duty for this heater/cooler.
   *
   * @return maximum design duty in Watts [W]
   */
  public double getMaxDesignDuty() {
    return getMechanicalDesign().maxDesignDuty;
  }

  /**
   * Gets the maximum design duty in the specified unit.
   *
   * @param unit unit for the returned value (e.g., "W", "kW", "MW")
   * @return maximum design duty in the specified unit
   */
  public double getMaxDesignDuty(String unit) {
    neqsim.util.unit.PowerUnit powerUnit =
        new neqsim.util.unit.PowerUnit(getMechanicalDesign().maxDesignDuty, "W");
    return powerUnit.getValue(unit);
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
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new HeaterResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    HeaterResponse res = new HeaterResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  // ============================================================================
  // AutoSizeable Implementation
  // ============================================================================

  /** Flag indicating if heater has been auto-sized. */
  private boolean autoSized = false;

  /** {@inheritDoc} */
  @Override
  public void autoSize(double safetyFactor) {
    if (inStream == null || inStream.getThermoSystem() == null) {
      throw new IllegalStateException("Inlet stream must be connected before auto-sizing");
    }

    // Calculate duty required to achieve the specified temperature change
    double calculatedDuty = Math.abs(getDuty());
    if (calculatedDuty <= 0) {
      calculatedDuty = 1000.0; // Default 1 MW if duty not calculable
    }

    // Apply safety factor to duty
    double designDuty = calculatedDuty * safetyFactor;

    // Initialize and calculate mechanical design
    if (mechanicalDesign == null) {
      initMechanicalDesign();
    }

    // Set design parameters
    mechanicalDesign.maxDesignDuty = designDuty;
    mechanicalDesign.calcDesign();

    autoSized = true;
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize() {
    autoSize(1.2);
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize(String company, String trDocument) {
    // Set company standard on mechanical design to load correct design parameters
    if (mechanicalDesign == null) {
      initMechanicalDesign();
    }

    // Set company-specific design standards which triggers database lookup
    mechanicalDesign.setCompanySpecificDesignStandards(company);

    // Read design specifications from database
    mechanicalDesign.readDesignSpecifications();

    // Use default safety factor
    autoSize(1.2);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAutoSized() {
    return autoSized;
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Heater/Cooler Auto-Sizing Report ===\n");
    sb.append("Equipment: ").append(getName()).append("\n");
    sb.append("Auto-sized: ").append(autoSized).append("\n");

    if (inStream != null && outStream != null) {
      double inletTemp = inStream.getTemperature("C");
      double outletTemp = outStream.getTemperature("C");
      double duty = getDuty();

      sb.append("\n--- Operating Conditions ---\n");
      sb.append("Inlet Temperature: ").append(String.format("%.2f C", inletTemp)).append("\n");
      sb.append("Outlet Temperature: ").append(String.format("%.2f C", outletTemp)).append("\n");
      sb.append("Temperature Change: ").append(String.format("%.2f C", outletTemp - inletTemp))
          .append("\n");
      sb.append("Duty: ").append(String.format("%.2f kW", duty / 1000.0)).append("\n");

      if (mechanicalDesign != null) {
        sb.append("\n--- Design Parameters ---\n");
        sb.append("Max Design Duty: ")
            .append(String.format("%.2f kW", mechanicalDesign.maxDesignDuty / 1000.0)).append("\n");
        sb.append("Duty Utilization: ")
            .append(String.format("%.1f%%", Math.abs(duty) / mechanicalDesign.maxDesignDuty * 100))
            .append("\n");
      }
    }

    return sb.toString();
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReportJson() {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("equipmentName", getName());
    report.put("autoSized", autoSized);

    if (inStream != null && outStream != null) {
      double inletTemp = inStream.getTemperature("C");
      double outletTemp = outStream.getTemperature("C");
      double duty = getDuty();

      report.put("inletTemperature_C", inletTemp);
      report.put("outletTemperature_C", outletTemp);
      report.put("temperatureChange_C", outletTemp - inletTemp);
      report.put("duty_kW", duty / 1000.0);

      if (mechanicalDesign != null) {
        report.put("maxDesignDuty_kW", mechanicalDesign.maxDesignDuty / 1000.0);
        report.put("dutyUtilization", Math.abs(duty) / mechanicalDesign.maxDesignDuty);
      }
    }

    return new GsonBuilder().setPrettyPrinting().create().toJson(report);
  }

  // ============================================================================
  // CapacityConstrainedEquipment Implementation
  // ============================================================================

  /** Storage for capacity constraints. */
  private final java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> capacityConstraints =
      new java.util.LinkedHashMap<>();

  /**
   * Initializes default capacity constraints for the heater.
   */
  protected void initializeCapacityConstraints() {
    // Duty constraint (HARD limit)
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("duty", "kW",
        neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.HARD)
            .setDesignValue(getMechanicalDesign().maxDesignDuty).setWarningThreshold(0.9)
            .setValueSupplier(() -> Math.abs(getDuty())));

    // Pressure drop constraint (DESIGN limit)
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("pressureDrop",
        "bara", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.DESIGN)
            .setDesignValue(getMechanicalDesign().maxDesignPressureDrop).setWarningThreshold(0.9)
            .setValueSupplier(() -> pressureDrop));
  }

  /** {@inheritDoc} */
  @Override
  public java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> getCapacityConstraints() {
    if (capacityConstraints.isEmpty()) {
      initializeCapacityConstraints();
    }
    return java.util.Collections.unmodifiableMap(capacityConstraints);
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.process.equipment.capacity.CapacityConstraint getBottleneckConstraint() {
    neqsim.process.equipment.capacity.CapacityConstraint bottleneck = null;
    double maxUtil = 0.0;
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      double util = c.getUtilization();
      if (!Double.isNaN(util) && util > maxUtil) {
        maxUtil = util;
        bottleneck = c;
      }
    }
    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      if (c.isViolated()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      if (c.isHardLimitExceeded()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    double maxUtil = 0.0;
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      double util = c.getUtilization();
      if (!Double.isNaN(util)) {
        maxUtil = Math.max(maxUtil, util);
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(
      neqsim.process.equipment.capacity.CapacityConstraint constraint) {
    if (constraint != null) {
      capacityConstraints.put(constraint.getName(), constraint);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeCapacityConstraint(String constraintName) {
    return capacityConstraints.remove(constraintName) != null;
  }

  /** {@inheritDoc} */
  @Override
  public void clearCapacityConstraints() {
    capacityConstraints.clear();
  }
}
