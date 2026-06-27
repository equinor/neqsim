package neqsim.process.util.utilitydesign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.CoolingWaterSystem;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.util.UtilityAirSystem;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Screening-level utility-system designer for oil &amp; gas process flowsheets.
 *
 * <p>
 * This class is an <b>aggregator</b>: it harvests the heating, cooling, shaft-power and instrument-air demands directly
 * from a fully-run {@link ProcessSystem} (or every area of a {@link ProcessModel}) and sizes the supporting utility
 * systems by reusing the existing NeqSim building blocks ({@link CoolingWaterSystem} and {@link UtilityAirSystem}). It
 * then selects steam levels for the heating duties, splits the cooling duties between cooling water and air coolers,
 * estimates fuel-gas demand for fired/steam duty and turbine drivers, and rolls up the resulting electrical load, fuel
 * demand, CO<sub>2</sub> emissions and operating cost.
 * </p>
 *
 * <p>
 * The designer does <b>not</b> introduce any new process equipment classes. It is a read-only analysis layer intended
 * for early-stage utility screening and as the deterministic core that an agentic optimisation loop can drive.
 * </p>
 *
 * <p>
 * Typical usage:
 * </p>
 *
 * <pre>
 * UtilitySystemDesigner designer = UtilitySystemDesigner.fromProcessSystem(process);
 * designer.setAnnualOperatingHours(8000.0);
 * designer.setElectricityCostPerKWh(0.08);
 * designer.design();
 * String json = designer.toJson();
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class UtilitySystemDesigner implements Serializable {
  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Schema version for the emitted JSON / results map. */
  public static final String SCHEMA_VERSION = "1.0";

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(UtilitySystemDesigner.class);

  // ==========================================================================
  // Design basis (configurable)
  // ==========================================================================

  /** Minimum approach temperature for utility matching [°C]. */
  private double minimumApproachTempC = 10.0;

  /** Cooling-water supply temperature [°C]. */
  private double coolingWaterSupplyTempC = 25.0;

  /** Cooling-water return temperature [°C]. */
  private double coolingWaterReturnTempC = 35.0;

  /** Cooling-water approach to the process cold-end [°C]. */
  private double coolingWaterApproachC = 5.0;

  /**
   * Process cold-end outlet temperature below which a cooler must use cooling water rather than an air cooler [°C].
   */
  private double coolingWaterCutoverTempC = 45.0;

  /** Air-cooler fan power as a fraction of rejected duty [-]. */
  private double airCoolerFanFraction = 0.015;

  /** Available steam levels, ordered from hottest to coldest. */
  private final List<SteamLevel> steamLevels = new ArrayList<SteamLevel>();

  /** Boiler thermal efficiency for raising steam [-]. */
  private double boilerEfficiency = 0.85;

  /** Fired-heater thermal efficiency for direct-fired duty [-]. */
  private double firedHeaterEfficiency = 0.82;

  /** Turbine driver thermal efficiency for fuel-gas-driven shaft power [-]. */
  private double driverThermalEfficiency = 0.35;

  /** Whether rotating-equipment drivers are electrically powered (true) or fuel-gas turbines. */
  private boolean electrifyDrivers = false;

  /** Fuel-gas lower heating value [MJ/kg]. */
  private double fuelLowHeatingValueMJperKg = 47.0;

  /** Instrument-air demand per actuator (control valve) [Nm³/hr]. */
  private double airDemandPerActuatorNm3h = 0.3;

  /** Base instrument-air demand independent of actuator count [Nm³/hr]. */
  private double baseInstrumentAirNm3h = 50.0;

  /** Annual operating hours [hr/yr]. */
  private double annualOperatingHours = 8000.0;

  /** Electricity cost [$/kWh]. */
  private double electricityCostPerKWh = 0.10;

  /** Fuel-gas cost [$/kg]. */
  private double fuelGasCostPerKg = 0.25;

  /** CO<sub>2</sub> emission factor for fuel gas [kg CO2 / kg fuel]. */
  private double co2FuelFactorKgPerKg = 2.75;

  /** CO<sub>2</sub> emission factor for imported electricity [kg CO2 / kWh]. */
  private double co2GridFactorKgPerKWh = 0.0;

  /** Carbon tax [$/tonne CO2]. */
  private double carbonTaxPerTonne = 0.0;

  // ==========================================================================
  // Harvested demand
  // ==========================================================================

  /** Harvested heating demands. */
  private final List<HeatingDemand> heatingDemands = new ArrayList<HeatingDemand>();

  /** Harvested cooling demands. */
  private final List<CoolingDemand> coolingDemands = new ArrayList<CoolingDemand>();

  /** Harvested rotating-equipment shaft loads. */
  private final List<ShaftLoad> shaftLoads = new ArrayList<ShaftLoad>();

  /** Number of actuated valves contributing to instrument-air demand. */
  private int actuatorCount = 0;

  // ==========================================================================
  // Results (populated by design())
  // ==========================================================================

  /** Whether {@link #design()} has been run. */
  private boolean designed = false;

  /** Cooling-water sub-system (reused NeqSim component). */
  private transient CoolingWaterSystem coolingWaterSystem;

  /** Instrument-air sub-system (reused NeqSim component). */
  private transient UtilityAirSystem instrumentAirSystem;

  private double totalHeatingDutyKW = 0.0;
  private double totalCoolingDutyKW = 0.0;
  private double totalShaftPowerKW = 0.0;
  private double coolingWaterDutyKW = 0.0;
  private double airCoolerDutyKW = 0.0;
  private double firedHeatingDutyKW = 0.0;
  private double coolingWaterFlowM3h = 0.0;
  private double coolingWaterPumpPowerKW = 0.0;
  private double airCoolerFanPowerKW = 0.0;
  private double instrumentAirDemandNm3h = 0.0;
  private double instrumentAirCompressorKW = 0.0;
  private double fuelThermalDemandKW = 0.0;
  private double fuelMassDemandKgh = 0.0;
  private double totalElectricalLoadKW = 0.0;
  private double fuelCo2TonnePerYear = 0.0;
  private double electricityCo2TonnePerYear = 0.0;
  private double totalCo2TonnePerYear = 0.0;
  private double fuelOpex = 0.0;
  private double electricityOpex = 0.0;
  private double carbonTaxOpex = 0.0;
  private double totalOpex = 0.0;

  /**
   * Creates a designer with default offshore design-basis values and three default steam levels (HP, MP, LP).
   */
  public UtilitySystemDesigner() {
    steamLevels.add(new SteamLevel("HP", 41.0, 252.0));
    steamLevels.add(new SteamLevel("MP", 11.0, 184.0));
    steamLevels.add(new SteamLevel("LP", 4.5, 148.0));
  }

  /**
   * Builds a designer and harvests utility demands from a single process system.
   *
   * @param process the fully-run process system to analyse; must not be {@code null}
   * @return a designer with demands harvested (call {@link #design()} to size utilities)
   * @throws IllegalArgumentException if {@code process} is {@code null}
   */
  public static UtilitySystemDesigner fromProcessSystem(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
    UtilitySystemDesigner designer = new UtilitySystemDesigner();
    designer.harvest(process, process.getName());
    return designer;
  }

  /**
   * Builds a designer and harvests utility demands from every area of a multi-area process model.
   *
   * @param model the fully-run process model to analyse; must not be {@code null}
   * @return a designer with demands harvested from all areas (call {@link #design()} to size utilities)
   * @throws IllegalArgumentException if {@code model} is {@code null}
   */
  public static UtilitySystemDesigner fromProcessModel(ProcessModel model) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    UtilitySystemDesigner designer = new UtilitySystemDesigner();
    for (ProcessSystem process : model.getAllProcesses()) {
      designer.harvest(process, process.getName());
    }
    return designer;
  }

  /**
   * Harvests heating, cooling, shaft-power and actuator demands from one process system.
   *
   * @param process the process system to walk; must not be {@code null}
   * @param area the area label to tag harvested demands with (may be {@code null})
   */
  private void harvest(ProcessSystem process, String area) {
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof Heater) {
        Heater heater = (Heater) unit;
        double dutyKW = heater.getDuty("kW");
        if (Math.abs(dutyKW) < 1.0e-6) {
          continue;
        }
        double outletTempC = Double.NaN;
        if (heater.getOutletStream() != null) {
          outletTempC = heater.getOutletStream().getTemperature("C");
        }
        boolean cooling = (unit instanceof Cooler) || dutyKW < 0.0;
        if (cooling) {
          coolingDemands.add(new CoolingDemand(unit.getName(), area, Math.abs(dutyKW), outletTempC));
        } else {
          heatingDemands.add(new HeatingDemand(unit.getName(), area, dutyKW, outletTempC));
        }
      } else if (unit instanceof Compressor) {
        double powerKW = ((Compressor) unit).getPower("kW");
        if (powerKW > 1.0e-6) {
          shaftLoads.add(new ShaftLoad(unit.getName(), area, powerKW, "compressor"));
        }
      } else if (unit instanceof Pump) {
        double powerKW = ((Pump) unit).getPower("kW");
        if (powerKW > 1.0e-6) {
          shaftLoads.add(new ShaftLoad(unit.getName(), area, powerKW, "pump"));
        }
      } else if (unit instanceof ThrottlingValve) {
        actuatorCount++;
      }
    }
  }

  /**
   * Sizes all utility systems from the harvested demands and rolls up loads, fuel, emissions and cost. Must be called
   * after construction/harvesting and before reading any result getter.
   *
   * @return this designer, for chaining
   */
  public UtilitySystemDesigner design() {
    designHeating();
    designCooling();
    designInstrumentAir();
    designShaftPower();
    designFuelAndEmissions();
    designed = true;
    logger.info("Utility design complete: heating {} kW, cooling {} kW, shaft {} kW, fuel {} kg/hr, CO2 {} t/yr",
        totalHeatingDutyKW, totalCoolingDutyKW, totalShaftPowerKW, fuelMassDemandKgh, totalCo2TonnePerYear);
    return this;
  }

  /**
   * Allocates each heating demand to the lowest viable steam level, or to direct-fired duty when no steam level is hot
   * enough.
   */
  private void designHeating() {
    for (SteamLevel level : steamLevels) {
      level.allocatedDutyKW = 0.0;
    }
    totalHeatingDutyKW = 0.0;
    firedHeatingDutyKW = 0.0;
    for (HeatingDemand demand : heatingDemands) {
      totalHeatingDutyKW += demand.dutyKW;
      SteamLevel selected = selectSteamLevel(demand.outletTempC);
      if (selected != null) {
        selected.allocatedDutyKW += demand.dutyKW;
        demand.assignedUtility = selected.name + " steam";
      } else {
        firedHeatingDutyKW += demand.dutyKW;
        demand.assignedUtility = "fired heater";
      }
    }
  }

  /**
   * Selects the coldest steam level whose saturation temperature exceeds the demand outlet temperature by at least the
   * minimum approach.
   *
   * @param outletTempC the process heating outlet temperature [°C]
   * @return the selected steam level, or {@code null} if none is hot enough
   */
  private SteamLevel selectSteamLevel(double outletTempC) {
    double requiredTempC = Double.isNaN(outletTempC) ? Double.MAX_VALUE : outletTempC;
    SteamLevel best = null;
    for (SteamLevel level : steamLevels) {
      if (level.saturationTempC >= requiredTempC + minimumApproachTempC) {
        if (best == null || level.saturationTempC < best.saturationTempC) {
          best = level;
        }
      }
    }
    return best;
  }

  /**
   * Splits cooling demands between cooling water and air coolers, then sizes the cooling-water system by reusing
   * {@link CoolingWaterSystem}.
   */
  private void designCooling() {
    totalCoolingDutyKW = 0.0;
    coolingWaterDutyKW = 0.0;
    airCoolerDutyKW = 0.0;
    coolingWaterSystem = new CoolingWaterSystem();
    coolingWaterSystem.setCoolingWaterSupplyTemperature(coolingWaterSupplyTempC);
    coolingWaterSystem.setCoolingWaterReturnTemperature(coolingWaterReturnTempC);
    coolingWaterSystem.setElectricityCost(electricityCostPerKWh);
    coolingWaterSystem.setAnnualOperatingHours(annualOperatingHours);

    int cwCount = 0;
    for (CoolingDemand demand : coolingDemands) {
      totalCoolingDutyKW += demand.dutyKW;
      boolean useCoolingWater = Double.isNaN(demand.outletTempC) || demand.outletTempC < coolingWaterCutoverTempC;
      if (useCoolingWater) {
        coolingWaterDutyKW += demand.dutyKW;
        demand.assignedUtility = "cooling water";
        double outletForCw = Double.isNaN(demand.outletTempC) ? coolingWaterReturnTempC + coolingWaterApproachC
            : demand.outletTempC;
        coolingWaterSystem.addCoolingRequirement(demand.name, demand.dutyKW, outletForCw, coolingWaterApproachC);
        cwCount++;
      } else {
        airCoolerDutyKW += demand.dutyKW;
        demand.assignedUtility = "air cooler";
      }
    }

    if (cwCount > 0) {
      coolingWaterSystem.calculate();
      coolingWaterFlowM3h = coolingWaterSystem.getTotalCWFlowRate();
      coolingWaterPumpPowerKW = coolingWaterSystem.getTotalElectricalPower();
    } else {
      coolingWaterFlowM3h = 0.0;
      coolingWaterPumpPowerKW = 0.0;
    }
    airCoolerFanPowerKW = airCoolerDutyKW * airCoolerFanFraction;
  }

  /**
   * Sizes the instrument-air system from the actuator count and base demand, reusing {@link UtilityAirSystem}.
   */
  private void designInstrumentAir() {
    instrumentAirDemandNm3h = baseInstrumentAirNm3h + actuatorCount * airDemandPerActuatorNm3h;
    if (instrumentAirDemandNm3h > 1.0e-6) {
      instrumentAirSystem = new UtilityAirSystem("Instrument Air", instrumentAirDemandNm3h);
      instrumentAirSystem.run(UUID.randomUUID());
      instrumentAirCompressorKW = instrumentAirSystem.getCompressorPowerKW();
    } else {
      instrumentAirCompressorKW = 0.0;
    }
  }

  /** Sums harvested rotating-equipment shaft power. */
  private void designShaftPower() {
    totalShaftPowerKW = 0.0;
    for (ShaftLoad load : shaftLoads) {
      totalShaftPowerKW += load.powerKW;
    }
  }

  /**
   * Computes fuel-gas demand (steam raising, direct-fired duty and turbine drivers), the total electrical load,
   * CO<sub>2</sub> emissions and operating cost.
   */
  private void designFuelAndEmissions() {
    double steamThermalKW = totalSteamDutyKW() / boilerEfficiency;
    double firedThermalKW = firedHeatingDutyKW / firedHeaterEfficiency;
    double driverThermalKW = electrifyDrivers ? 0.0 : totalShaftPowerKW / driverThermalEfficiency;
    fuelThermalDemandKW = steamThermalKW + firedThermalKW + driverThermalKW;

    double lowHeatingValueKJperKg = fuelLowHeatingValueMJperKg * 1000.0;
    fuelMassDemandKgh = lowHeatingValueKJperKg > 0.0 ? fuelThermalDemandKW * 3600.0 / lowHeatingValueKJperKg : 0.0;

    double electricDriverKW = electrifyDrivers ? totalShaftPowerKW : 0.0;
    totalElectricalLoadKW = coolingWaterPumpPowerKW + airCoolerFanPowerKW + instrumentAirCompressorKW
        + electricDriverKW;

    double fuelKgPerYear = fuelMassDemandKgh * annualOperatingHours;
    fuelCo2TonnePerYear = fuelKgPerYear * co2FuelFactorKgPerKg / 1000.0;
    double electricityKWhPerYear = totalElectricalLoadKW * annualOperatingHours;
    electricityCo2TonnePerYear = electricityKWhPerYear * co2GridFactorKgPerKWh / 1000.0;
    totalCo2TonnePerYear = fuelCo2TonnePerYear + electricityCo2TonnePerYear;

    fuelOpex = fuelKgPerYear * fuelGasCostPerKg;
    electricityOpex = electricityKWhPerYear * electricityCostPerKWh;
    carbonTaxOpex = totalCo2TonnePerYear * carbonTaxPerTonne;
    totalOpex = fuelOpex + electricityOpex + carbonTaxOpex;
  }

  /**
   * Total duty allocated to steam across all levels [kW].
   *
   * @return the total steam duty [kW]
   */
  private double totalSteamDutyKW() {
    double sum = 0.0;
    for (SteamLevel level : steamLevels) {
      sum += level.allocatedDutyKW;
    }
    return sum;
  }

  /**
   * Validates the harvested demand and design basis, returning a list of human-readable warnings.
   *
   * @return a list of warnings; empty if no issues were found
   */
  public List<String> validate() {
    List<String> warnings = new ArrayList<String>();
    if (heatingDemands.isEmpty() && coolingDemands.isEmpty() && shaftLoads.isEmpty()) {
      warnings.add("No heating, cooling or shaft-power demands were harvested from the flowsheet.");
    }
    if (coolingWaterReturnTempC <= coolingWaterSupplyTempC) {
      warnings.add("Cooling-water return temperature must exceed the supply temperature.");
    }
    if (boilerEfficiency <= 0.0 || boilerEfficiency > 1.0) {
      warnings.add("Boiler efficiency must be in the interval (0, 1].");
    }
    if (annualOperatingHours <= 0.0 || annualOperatingHours > 8760.0) {
      warnings.add("Annual operating hours must be in the interval (0, 8760].");
    }
    return warnings;
  }

  /**
   * Builds a schema-versioned results map matching the {@code utilities} block of the task {@code results.json} schema.
   *
   * @return an ordered map of utility-design results
   */
  public Map<String, Object> toResultsMap() {
    if (!designed) {
      design();
    }
    Map<String, Object> root = new LinkedHashMap<String, Object>();
    root.put("schemaVersion", SCHEMA_VERSION);

    Map<String, Object> basis = new LinkedHashMap<String, Object>();
    basis.put("minimumApproachTempC", minimumApproachTempC);
    basis.put("coolingWaterSupplyTempC", coolingWaterSupplyTempC);
    basis.put("coolingWaterReturnTempC", coolingWaterReturnTempC);
    basis.put("coolingWaterCutoverTempC", coolingWaterCutoverTempC);
    basis.put("boilerEfficiency", boilerEfficiency);
    basis.put("electrifyDrivers", electrifyDrivers);
    basis.put("annualOperatingHours", annualOperatingHours);
    root.put("designBasis", basis);

    Map<String, Object> demand = new LinkedHashMap<String, Object>();
    demand.put("heatingDuty_kW", totalHeatingDutyKW);
    demand.put("coolingDuty_kW", totalCoolingDutyKW);
    demand.put("shaftPower_kW", totalShaftPowerKW);
    demand.put("instrumentAirActuators", actuatorCount);
    List<Map<String, Object>> heatingBreakdown = new ArrayList<Map<String, Object>>();
    for (HeatingDemand item : heatingDemands) {
      Map<String, Object> entry = new LinkedHashMap<String, Object>();
      entry.put("name", item.name);
      entry.put("area", item.area);
      entry.put("duty_kW", item.dutyKW);
      entry.put("outletTemp_C", item.outletTempC);
      entry.put("utility", item.assignedUtility);
      heatingBreakdown.add(entry);
    }
    demand.put("heatingDemands", heatingBreakdown);
    List<Map<String, Object>> coolingBreakdown = new ArrayList<Map<String, Object>>();
    for (CoolingDemand item : coolingDemands) {
      Map<String, Object> entry = new LinkedHashMap<String, Object>();
      entry.put("name", item.name);
      entry.put("area", item.area);
      entry.put("duty_kW", item.dutyKW);
      entry.put("outletTemp_C", item.outletTempC);
      entry.put("utility", item.assignedUtility);
      coolingBreakdown.add(entry);
    }
    demand.put("coolingDemands", coolingBreakdown);
    List<Map<String, Object>> shaftBreakdown = new ArrayList<Map<String, Object>>();
    for (ShaftLoad item : shaftLoads) {
      Map<String, Object> entry = new LinkedHashMap<String, Object>();
      entry.put("name", item.name);
      entry.put("area", item.area);
      entry.put("type", item.type);
      entry.put("power_kW", item.powerKW);
      shaftBreakdown.add(entry);
    }
    demand.put("shaftLoads", shaftBreakdown);
    root.put("demand", demand);

    Map<String, Object> steam = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> levels = new ArrayList<Map<String, Object>>();
    for (SteamLevel level : steamLevels) {
      Map<String, Object> entry = new LinkedHashMap<String, Object>();
      entry.put("name", level.name);
      entry.put("pressure_bara", level.pressureBara);
      entry.put("saturationTemp_C", level.saturationTempC);
      entry.put("allocatedDuty_kW", level.allocatedDutyKW);
      levels.add(entry);
    }
    steam.put("levels", levels);
    steam.put("totalSteamDuty_kW", totalSteamDutyKW());
    steam.put("firedHeatingDuty_kW", firedHeatingDutyKW);
    root.put("steamSystem", steam);

    Map<String, Object> coolingWater = new LinkedHashMap<String, Object>();
    coolingWater.put("duty_kW", coolingWaterDutyKW);
    coolingWater.put("flowRate_m3h", coolingWaterFlowM3h);
    coolingWater.put("pumpPower_kW", coolingWaterPumpPowerKW);
    root.put("coolingWater", coolingWater);

    Map<String, Object> airCoolers = new LinkedHashMap<String, Object>();
    airCoolers.put("duty_kW", airCoolerDutyKW);
    airCoolers.put("fanPower_kW", airCoolerFanPowerKW);
    root.put("airCoolers", airCoolers);

    Map<String, Object> air = new LinkedHashMap<String, Object>();
    air.put("demand_Nm3h", instrumentAirDemandNm3h);
    air.put("compressorPower_kW", instrumentAirCompressorKW);
    root.put("instrumentAir", air);

    Map<String, Object> fuel = new LinkedHashMap<String, Object>();
    fuel.put("thermalDemand_kW", fuelThermalDemandKW);
    fuel.put("massDemand_kgh", fuelMassDemandKgh);
    fuel.put("lowHeatingValue_MJperKg", fuelLowHeatingValueMJperKg);
    root.put("fuelGas", fuel);

    Map<String, Object> power = new LinkedHashMap<String, Object>();
    power.put("totalElectrical_kW", totalElectricalLoadKW);
    power.put("fuelGasDriven_kW", electrifyDrivers ? 0.0 : totalShaftPowerKW);
    root.put("powerBalance", power);

    Map<String, Object> emissions = new LinkedHashMap<String, Object>();
    emissions.put("fuelCO2_tonnePerYear", fuelCo2TonnePerYear);
    emissions.put("electricityCO2_tonnePerYear", electricityCo2TonnePerYear);
    emissions.put("total_tonnePerYear", totalCo2TonnePerYear);
    root.put("emissions", emissions);

    Map<String, Object> opex = new LinkedHashMap<String, Object>();
    opex.put("fuel", fuelOpex);
    opex.put("electricity", electricityOpex);
    opex.put("carbonTax", carbonTaxOpex);
    opex.put("total", totalOpex);
    root.put("opex", opex);

    return root;
  }

  /**
   * Serialises the utility-design results to pretty-printed JSON.
   *
   * @return a JSON string of the results map
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toResultsMap());
  }

  // ==========================================================================
  // Design-basis setters
  // ==========================================================================

  /**
   * Sets the minimum approach temperature used for utility matching.
   *
   * @param value the minimum approach temperature [°C]
   */
  public void setMinimumApproachTempC(double value) {
    this.minimumApproachTempC = value;
  }

  /**
   * Sets the cooling-water supply temperature.
   *
   * @param value the cooling-water supply temperature [°C]
   */
  public void setCoolingWaterSupplyTempC(double value) {
    this.coolingWaterSupplyTempC = value;
  }

  /**
   * Sets the cooling-water return temperature.
   *
   * @param value the cooling-water return temperature [°C]
   */
  public void setCoolingWaterReturnTempC(double value) {
    this.coolingWaterReturnTempC = value;
  }

  /**
   * Sets the process cold-end outlet temperature below which cooling water is mandatory.
   *
   * @param value the cooling-water cut-over temperature [°C]
   */
  public void setCoolingWaterCutoverTempC(double value) {
    this.coolingWaterCutoverTempC = value;
  }

  /**
   * Sets whether rotating-equipment drivers are electric (true) or fuel-gas turbines (false).
   *
   * @param value {@code true} for electric drivers, {@code false} for fuel-gas turbine drivers
   */
  public void setElectrifyDrivers(boolean value) {
    this.electrifyDrivers = value;
  }

  /**
   * Sets the boiler thermal efficiency used to convert steam duty to fuel demand.
   *
   * @param value the boiler thermal efficiency in the interval (0, 1]
   */
  public void setBoilerEfficiency(double value) {
    this.boilerEfficiency = value;
  }

  /**
   * Sets the fuel-gas lower heating value.
   *
   * @param value the lower heating value [MJ/kg]
   */
  public void setFuelLowHeatingValueMJperKg(double value) {
    this.fuelLowHeatingValueMJperKg = value;
  }

  /**
   * Sets the annual operating hours used for fuel, emission and cost roll-ups.
   *
   * @param value the annual operating hours [hr/yr]
   */
  public void setAnnualOperatingHours(double value) {
    this.annualOperatingHours = value;
  }

  /**
   * Sets the electricity cost.
   *
   * @param value the electricity cost [$/kWh]
   */
  public void setElectricityCostPerKWh(double value) {
    this.electricityCostPerKWh = value;
  }

  /**
   * Sets the fuel-gas cost.
   *
   * @param value the fuel-gas cost [$/kg]
   */
  public void setFuelGasCostPerKg(double value) {
    this.fuelGasCostPerKg = value;
  }

  /**
   * Sets the CO<sub>2</sub> emission factor for imported electricity.
   *
   * @param value the grid emission factor [kg CO2 / kWh]
   */
  public void setCo2GridFactorKgPerKWh(double value) {
    this.co2GridFactorKgPerKWh = value;
  }

  /**
   * Sets the carbon tax applied to total CO<sub>2</sub> emissions.
   *
   * @param value the carbon tax [$/tonne CO2]
   */
  public void setCarbonTaxPerTonne(double value) {
    this.carbonTaxPerTonne = value;
  }

  /**
   * Replaces the available steam levels.
   *
   * @param levels the steam levels to use; must not be {@code null} or empty
   * @throws IllegalArgumentException if {@code levels} is {@code null} or empty
   */
  public void setSteamLevels(List<SteamLevel> levels) {
    if (levels == null || levels.isEmpty()) {
      throw new IllegalArgumentException("at least one steam level is required");
    }
    steamLevels.clear();
    steamLevels.addAll(levels);
  }

  // ==========================================================================
  // Result getters
  // ==========================================================================

  /**
   * Returns the total harvested heating duty.
   *
   * @return the total heating duty [kW]
   */
  public double getTotalHeatingDutyKW() {
    return totalHeatingDutyKW;
  }

  /**
   * Returns the total harvested cooling duty.
   *
   * @return the total cooling duty [kW]
   */
  public double getTotalCoolingDutyKW() {
    return totalCoolingDutyKW;
  }

  /**
   * Returns the total harvested rotating-equipment shaft power.
   *
   * @return the total shaft power [kW]
   */
  public double getTotalShaftPowerKW() {
    return totalShaftPowerKW;
  }

  /**
   * Returns the cooling duty allocated to cooling water.
   *
   * @return the cooling-water duty [kW]
   */
  public double getCoolingWaterDutyKW() {
    return coolingWaterDutyKW;
  }

  /**
   * Returns the cooling duty allocated to air coolers.
   *
   * @return the air-cooler duty [kW]
   */
  public double getAirCoolerDutyKW() {
    return airCoolerDutyKW;
  }

  /**
   * Returns the sized cooling-water circulation rate.
   *
   * @return the cooling-water flow rate [m³/hr]
   */
  public double getCoolingWaterFlowM3h() {
    return coolingWaterFlowM3h;
  }

  /**
   * Returns the instrument-air demand.
   *
   * @return the instrument-air demand [Nm³/hr]
   */
  public double getInstrumentAirDemandNm3h() {
    return instrumentAirDemandNm3h;
  }

  /**
   * Returns the instrument-air compressor power.
   *
   * @return the instrument-air compressor power [kW]
   */
  public double getInstrumentAirCompressorKW() {
    return instrumentAirCompressorKW;
  }

  /**
   * Returns the total fuel-gas thermal demand.
   *
   * @return the fuel-gas thermal demand [kW]
   */
  public double getFuelThermalDemandKW() {
    return fuelThermalDemandKW;
  }

  /**
   * Returns the total fuel-gas mass demand.
   *
   * @return the fuel-gas mass demand [kg/hr]
   */
  public double getFuelMassDemandKgh() {
    return fuelMassDemandKgh;
  }

  /**
   * Returns the total electrical load of the utility systems and electrified drivers.
   *
   * @return the total electrical load [kW]
   */
  public double getTotalElectricalLoadKW() {
    return totalElectricalLoadKW;
  }

  /**
   * Returns the total CO<sub>2</sub> emissions of the utility systems.
   *
   * @return the total CO<sub>2</sub> emissions [tonne/yr]
   */
  public double getTotalCo2TonnePerYear() {
    return totalCo2TonnePerYear;
  }

  /**
   * Returns the total operating cost of the utility systems.
   *
   * @return the total operating cost [$/yr]
   */
  public double getTotalOpex() {
    return totalOpex;
  }

  /**
   * Returns the reused cooling-water sub-system (available after {@link #design()}).
   *
   * @return the cooling-water system, or {@code null} if not yet designed
   */
  public CoolingWaterSystem getCoolingWaterSystem() {
    return coolingWaterSystem;
  }

  /**
   * Returns the reused instrument-air sub-system (available after {@link #design()}).
   *
   * @return the instrument-air system, or {@code null} if not designed or zero demand
   */
  public UtilityAirSystem getInstrumentAirSystem() {
    return instrumentAirSystem;
  }

  /**
   * Returns the number of actuated valves harvested from the flowsheet.
   *
   * @return the actuator count
   */
  public int getActuatorCount() {
    return actuatorCount;
  }

  // ==========================================================================
  // Nested data classes
  // ==========================================================================

  /**
   * A steam level available to satisfy process heating duty.
   */
  public static class SteamLevel implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Level name (e.g. HP, MP, LP). */
    private final String name;

    /** Saturation pressure [bara]. */
    private final double pressureBara;

    /** Saturation temperature [°C]. */
    private final double saturationTempC;

    /** Heating duty allocated to this level [kW]. */
    private double allocatedDutyKW = 0.0;

    /**
     * Creates a steam level.
     *
     * @param name the level name (e.g. HP, MP, LP)
     * @param pressureBara the saturation pressure [bara]
     * @param saturationTempC the saturation temperature [°C]
     */
    public SteamLevel(String name, double pressureBara, double saturationTempC) {
      this.name = name;
      this.pressureBara = pressureBara;
      this.saturationTempC = saturationTempC;
    }

    /**
     * Returns the level name.
     *
     * @return the level name
     */
    public String getName() {
      return name;
    }

    /**
     * Returns the saturation pressure.
     *
     * @return the saturation pressure [bara]
     */
    public double getPressureBara() {
      return pressureBara;
    }

    /**
     * Returns the saturation temperature.
     *
     * @return the saturation temperature [°C]
     */
    public double getSaturationTempC() {
      return saturationTempC;
    }

    /**
     * Returns the heating duty allocated to this level.
     *
     * @return the allocated duty [kW]
     */
    public double getAllocatedDutyKW() {
      return allocatedDutyKW;
    }
  }

  /**
   * A harvested process heating demand.
   */
  private static class HeatingDemand implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final String area;
    private final double dutyKW;
    private final double outletTempC;
    private String assignedUtility = "unassigned";

    HeatingDemand(String name, String area, double dutyKW, double outletTempC) {
      this.name = name;
      this.area = area;
      this.dutyKW = dutyKW;
      this.outletTempC = outletTempC;
    }
  }

  /**
   * A harvested process cooling demand.
   */
  private static class CoolingDemand implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final String area;
    private final double dutyKW;
    private final double outletTempC;
    private String assignedUtility = "unassigned";

    CoolingDemand(String name, String area, double dutyKW, double outletTempC) {
      this.name = name;
      this.area = area;
      this.dutyKW = dutyKW;
      this.outletTempC = outletTempC;
    }
  }

  /**
   * A harvested rotating-equipment shaft load.
   */
  private static class ShaftLoad implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final String area;
    private final double powerKW;
    private final String type;

    ShaftLoad(String name, String area, double powerKW, String type) {
      this.name = name;
      this.area = area;
      this.powerKW = powerKW;
      this.type = type;
    }
  }
}
