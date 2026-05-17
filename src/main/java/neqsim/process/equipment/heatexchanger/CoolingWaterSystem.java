package neqsim.process.equipment.heatexchanger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Cooling water system designer for process plant utility sizing.
 *
 * <p>
 * Collects cooling duty requirements from process coolers, sizes the cooling water circulation
 * system (pump, network, cooling tower), and estimates operating cost.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * CoolingWaterSystem cws = new CoolingWaterSystem();
 * cws.addCoolingRequirement("After-Cooler 1", 5000.0, 40.0, 30.0); // kW, outlet C, approach C
 * cws.addCoolingRequirement("Condenser", 3000.0, 55.0, 25.0);
 * cws.setCoolingWaterSupplyTemperature(25.0); // C
 * cws.setCoolingWaterReturnTemperature(35.0); // C
 * cws.setElectricityCost(0.10); // $/kWh
 * cws.calculate();
 * double cwFlow = cws.getTotalCWFlowRate(); // m3/hr
 * double pumpPower = cws.getPumpPower(); // kW
 * double annualCost = cws.getAnnualOperatingCost(); // $
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class CoolingWaterSystem implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Specific heat capacity of water in kJ/(kg*K). */
  private static final double CP_WATER = 4.186;

  /** Water density in kg/m3. */
  private static final double DENSITY_WATER = 1000.0;

  /** CW supply temperature in C (default 25 C). */
  private double cwSupplyTemperatureC = 25.0;

  /** CW return temperature in C (default 35 C). */
  private double cwReturnTemperatureC = 35.0;

  /** CW system pressure drop in bar (default 3 bar). */
  private double systemPressureDrop = 3.0;

  /** CW pump efficiency (default 0.75). */
  private double pumpEfficiency = 0.75;

  /** Cooling tower fan power as fraction of duty (default 0.01 = 1%). */
  private double towerFanPowerFraction = 0.01;

  /** Electricity cost in $/kWh (default 0.10). */
  private double electricityCost = 0.10;

  /** Annual operating hours (default 8000). */
  private double annualOperatingHours = 8000.0;

  /** Cooling requirements. */
  private final List<CoolingRequirement> requirements = new ArrayList<>();

  // Results
  private double totalDutyKW = 0.0;
  private double totalCWFlowRateM3PerHr = 0.0;
  private double pumpPowerKW = 0.0;
  private double towerFanPowerKW = 0.0;
  private double totalElectricalPowerKW = 0.0;
  private double annualOperatingCostDollars = 0.0;
  private boolean calculated = false;

  /**
   * Represents a single cooling requirement from a process cooler.
   */
  public static class CoolingRequirement implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Cooler name. */
    public final String name;
    /** Required cooling duty in kW. */
    public final double dutyKW;
    /** Process outlet temperature in C. */
    public final double processOutletTempC;
    /** Minimum approach temperature in C. */
    public final double approachDeltaTC;

    /**
     * Creates a cooling requirement.
     *
     * @param name cooler name
     * @param dutyKW cooling duty in kW
     * @param processOutletTempC process outlet temperature in C
     * @param approachDeltaTC minimum approach temperature in C
     */
    public CoolingRequirement(String name, double dutyKW, double processOutletTempC,
        double approachDeltaTC) {
      this.name = name;
      this.dutyKW = dutyKW;
      this.processOutletTempC = processOutletTempC;
      this.approachDeltaTC = approachDeltaTC;
    }
  }

  /**
   * Creates a new cooling water system.
   */
  public CoolingWaterSystem() {
    // default constructor
  }

  /**
   * Adds a cooling duty requirement.
   *
   * @param name cooler name
   * @param dutyKW required cooling duty in kW
   * @param processOutletTempC process outlet temperature in C
   * @param approachDeltaTC minimum approach temperature in C
   */
  public void addCoolingRequirement(String name, double dutyKW, double processOutletTempC,
      double approachDeltaTC) {
    requirements.add(new CoolingRequirement(name, dutyKW, processOutletTempC, approachDeltaTC));
    calculated = false;
  }

  /**
   * Sets the cooling water supply temperature.
   *
   * @param tempC supply temperature in Celsius
   */
  public void setCoolingWaterSupplyTemperature(double tempC) {
    this.cwSupplyTemperatureC = tempC;
    calculated = false;
  }

  /**
   * Sets the cooling water return temperature.
   *
   * @param tempC return temperature in Celsius
   */
  public void setCoolingWaterReturnTemperature(double tempC) {
    this.cwReturnTemperatureC = tempC;
    calculated = false;
  }

  /**
   * Sets the CW system pressure drop for pump sizing.
   *
   * @param pressureDropBar system pressure drop in bar
   */
  public void setSystemPressureDrop(double pressureDropBar) {
    this.systemPressureDrop = pressureDropBar;
    calculated = false;
  }

  /**
   * Sets the CW pump efficiency.
   *
   * @param efficiency pump efficiency as fraction (0.0 to 1.0)
   */
  public void setPumpEfficiency(double efficiency) {
    this.pumpEfficiency = efficiency;
    calculated = false;
  }

  /**
   * Sets the electricity unit cost.
   *
   * @param costPerKWh cost per kWh in currency units
   */
  public void setElectricityCost(double costPerKWh) {
    this.electricityCost = costPerKWh;
    calculated = false;
  }

  /**
   * Sets the annual operating hours for cost estimation.
   *
   * @param hours operating hours per year
   */
  public void setAnnualOperatingHours(double hours) {
    this.annualOperatingHours = hours;
    calculated = false;
  }

  /**
   * Runs the cooling water system design calculation.
   */
  public void calculate() {
    totalDutyKW = 0.0;
    for (CoolingRequirement req : requirements) {
      totalDutyKW += req.dutyKW;
    }

    // CW flow rate: Q = m * Cp * deltaT => m = Q / (Cp * deltaT)
    double deltaTK = cwReturnTemperatureC - cwSupplyTemperatureC;
    if (deltaTK <= 0) {
      deltaTK = 10.0; // Default 10 K rise
    }

    // Total mass flow in kg/sec
    double massFlowKgPerSec = (totalDutyKW) / (CP_WATER * deltaTK);

    // Volumetric flow in m3/hr
    totalCWFlowRateM3PerHr = (massFlowKgPerSec / DENSITY_WATER) * 3600.0;

    // Pump power: P = Q * dP / efficiency
    // Q in m3/s, dP in Pa
    double volumetricFlowM3PerSec = massFlowKgPerSec / DENSITY_WATER;
    double pressureDropPa = systemPressureDrop * 1.0e5; // bar to Pa
    pumpPowerKW = (volumetricFlowM3PerSec * pressureDropPa) / (pumpEfficiency * 1000.0);

    // Cooling tower fan power
    towerFanPowerKW = totalDutyKW * towerFanPowerFraction;

    // Total electrical power
    totalElectricalPowerKW = pumpPowerKW + towerFanPowerKW;

    // Annual operating cost
    annualOperatingCostDollars = totalElectricalPowerKW * annualOperatingHours * electricityCost;

    calculated = true;
  }

  /**
   * Gets the total cooling duty.
   *
   * @return total duty in kW
   */
  public double getTotalDuty() {
    if (!calculated) {
      calculate();
    }
    return totalDutyKW;
  }

  /**
   * Gets the total cooling water flow rate.
   *
   * @return CW flow rate in m3/hr
   */
  public double getTotalCWFlowRate() {
    if (!calculated) {
      calculate();
    }
    return totalCWFlowRateM3PerHr;
  }

  /**
   * Gets the CW pump power.
   *
   * @return pump power in kW
   */
  public double getPumpPower() {
    if (!calculated) {
      calculate();
    }
    return pumpPowerKW;
  }

  /**
   * Gets the cooling tower fan power.
   *
   * @return fan power in kW
   */
  public double getTowerFanPower() {
    if (!calculated) {
      calculate();
    }
    return towerFanPowerKW;
  }

  /**
   * Gets the total electrical power for the cooling water system.
   *
   * @return total power in kW
   */
  public double getTotalElectricalPower() {
    if (!calculated) {
      calculate();
    }
    return totalElectricalPowerKW;
  }

  /**
   * Gets the annual operating cost.
   *
   * @return annual cost in currency units
   */
  public double getAnnualOperatingCost() {
    if (!calculated) {
      calculate();
    }
    return annualOperatingCostDollars;
  }

  /**
   * Returns the design results as a JSON string.
   *
   * @return JSON representation of cooling water system design
   */
  public String toJson() {
    if (!calculated) {
      calculate();
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("systemType", "Cooling Water System");

    Map<String, Object> design = new LinkedHashMap<>();
    design.put("cwSupplyTemperature_C", cwSupplyTemperatureC);
    design.put("cwReturnTemperature_C", cwReturnTemperatureC);
    design.put("systemPressureDrop_bar", systemPressureDrop);
    design.put("pumpEfficiency", pumpEfficiency);
    result.put("designParameters", design);

    List<Map<String, Object>> reqList = new ArrayList<>();
    for (CoolingRequirement req : requirements) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("name", req.name);
      m.put("duty_kW", req.dutyKW);
      m.put("processOutletTemp_C", req.processOutletTempC);
      m.put("approachDeltaT_C", req.approachDeltaTC);
      reqList.add(m);
    }
    result.put("coolingRequirements", reqList);

    Map<String, Object> results = new LinkedHashMap<>();
    results.put("totalDuty_kW", totalDutyKW);
    results.put("totalCWFlowRate_m3perHr", totalCWFlowRateM3PerHr);
    results.put("pumpPower_kW", pumpPowerKW);
    results.put("towerFanPower_kW", towerFanPowerKW);
    results.put("totalElectricalPower_kW", totalElectricalPowerKW);
    results.put("annualOperatingCost", annualOperatingCostDollars);
    results.put("electricityCost_perKWh", electricityCost);
    results.put("annualOperatingHours", annualOperatingHours);
    result.put("results", results);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(result);
  }
}
