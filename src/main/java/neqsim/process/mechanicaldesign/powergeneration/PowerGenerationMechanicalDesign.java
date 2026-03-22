package neqsim.process.mechanicaldesign.powergeneration;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortInterface;
import neqsim.process.equipment.powergeneration.GasTurbine;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Mechanical design for power generation equipment (gas turbines and waste heat recovery units).
 *
 * <p>
 * Covers gas turbine selection based on power rating and fuel type, waste heat recovery unit (WHRU)
 * sizing, exhaust gas characteristics estimation, and weight/footprint estimation. Applicable to
 * {@link GasTurbine} and similar power generation equipment.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class PowerGenerationMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  // ============================================================================
  // Gas Turbine Design Parameters
  // ============================================================================

  /** Turbine class: "AERODERIVATIVE", "INDUSTRIAL", "MICRO". */
  private String turbineClass = "AERODERIVATIVE";

  /** Rated power output in MW. */
  private double ratedPowerMW = 0.0;

  /** Gas turbine thermal efficiency (LHV basis). */
  private double thermalEfficiency = 0.0;

  /** Heat rate in kJ/kWh. */
  private double heatRateKJkWh = 0.0;

  /** Fuel consumption in kg/hr. */
  private double fuelConsumptionKgHr = 0.0;

  /** Exhaust gas temperature in Celsius. */
  private double exhaustTemperatureC = 0.0;

  /** Exhaust gas mass flow in kg/s. */
  private double exhaustMassFlowKgS = 0.0;

  /** Compression ratio. */
  private double compressionRatio = 0.0;

  /** Turbine inlet temperature in Celsius. */
  private double turbineInletTemperatureC = 0.0;

  // ============================================================================
  // WHRU Design Parameters
  // ============================================================================

  /** Whether WHRU is included. */
  private boolean includeWHRU = false;

  /** WHRU heat recovery in MW. */
  private double whruDutyMW = 0.0;

  /** WHRU exhaust outlet temperature in Celsius. */
  private double whruOutletTemperatureC = 180.0;

  /** WHRU weight in kg. */
  private double whruWeightKg = 0.0;

  // ============================================================================
  // Weight and Dimensions
  // ============================================================================

  /** Gas turbine package weight in tonnes. */
  private double turbinePackageWeightTonnes = 0.0;

  /** Total system weight in tonnes. */
  private double totalSystemWeightTonnes = 0.0;

  /** Turbine package length in meters. */
  private double packageLength = 0.0;

  /** Turbine package width in meters. */
  private double packageWidth = 0.0;

  /** Noise level at 1m in dB(A). */
  private double noiseLevelDbA = 0.0;

  /** CO2 emission rate in tonnes/hr. */
  private double co2EmissionTonnesHr = 0.0;

  /** NOx emission in ppm (dry, 15% O2). */
  private double noxPpm = 25.0;

  /**
   * Constructor for PowerGenerationMechanicalDesign.
   *
   * @param equipment the power generation equipment
   */
  public PowerGenerationMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    ProcessEquipmentInterface equipment = getProcessEquipment();

    // Get inlet stream via TwoPortInterface
    StreamInterface inletStream = null;
    if (equipment instanceof TwoPortInterface) {
      inletStream = ((TwoPortInterface) equipment).getInletStream();
    }
    if (inletStream == null || inletStream.getThermoSystem() == null) {
      return;
    }

    // Get fuel characteristics
    double fuelFlowKgS = inletStream.getFlowRate("kg/sec");
    double fuelLHVJKg = 0.0;

    // Approximate LHV from methane content
    try {
      double methaneFrac = inletStream.getThermoSystem().getComponent("methane").getz();
      fuelLHVJKg = methaneFrac * 50.0e6 + (1.0 - methaneFrac) * 45.0e6; // weighted average
    } catch (Exception e) {
      fuelLHVJKg = 48.0e6; // default natural gas LHV
    }

    // === Get Power from Equipment ===
    if (equipment instanceof GasTurbine) {
      GasTurbine gt = (GasTurbine) equipment;
      ratedPowerMW = gt.getPower() / 1.0e6;
    }

    // If power not available, estimate from fuel flow
    if (ratedPowerMW <= 0 && fuelFlowKgS > 0) {
      // Assume 35% efficiency
      thermalEfficiency = selectEfficiency();
      ratedPowerMW = fuelFlowKgS * fuelLHVJKg * thermalEfficiency / 1.0e6;
    }
    ratedPowerMW = Math.max(ratedPowerMW, 0.01);

    // === Classify Turbine ===
    if (ratedPowerMW < 5.0) {
      turbineClass = "MICRO";
    } else if (ratedPowerMW < 50.0) {
      turbineClass = "AERODERIVATIVE";
    } else {
      turbineClass = "INDUSTRIAL";
    }

    // === Efficiency and Heat Rate ===
    thermalEfficiency = selectEfficiency();
    heatRateKJkWh = 3600.0 / thermalEfficiency;

    // === Fuel Consumption ===
    fuelConsumptionKgHr = (ratedPowerMW * 1.0e6 / thermalEfficiency) / fuelLHVJKg * 3600.0;

    // === Exhaust Characteristics ===
    compressionRatio = selectCompressionRatio();
    turbineInletTemperatureC = selectTIT();
    exhaustTemperatureC = turbineInletTemperatureC - 200.0 - compressionRatio * 5.0;
    exhaustTemperatureC = Math.max(exhaustTemperatureC, 400.0);

    // Exhaust mass flow: fuel + air (stoichiometric * excess air ratio)
    double airFuelRatio = 40.0; // typical for GT
    exhaustMassFlowKgS = fuelFlowKgS * (1.0 + airFuelRatio);
    if (exhaustMassFlowKgS <= 0) {
      exhaustMassFlowKgS = fuelConsumptionKgHr / 3600.0 * (1.0 + airFuelRatio);
    }

    // === CO2 Emissions ===
    // Natural gas: ~2.75 kg CO2 per kg fuel
    co2EmissionTonnesHr = fuelConsumptionKgHr * 2.75 / 1000.0;

    // === WHRU Sizing ===
    if (includeWHRU) {
      double cpExhaust = 1100.0; // J/(kg*K) exhaust gas
      double deltaT = exhaustTemperatureC - whruOutletTemperatureC;
      whruDutyMW = exhaustMassFlowKgS * cpExhaust * Math.max(deltaT, 0) / 1.0e6;
      // WHRU weight: ~20 kg/kW for compact designs
      whruWeightKg = whruDutyMW * 1000.0 * 20.0;
    }

    // === Weight Estimation (from manufacturer correlations) ===
    if ("MICRO".equals(turbineClass)) {
      turbinePackageWeightTonnes = 0.5 + ratedPowerMW * 2.0;
      packageLength = 2.0 + ratedPowerMW * 0.5;
      packageWidth = 1.5 + ratedPowerMW * 0.3;
      noiseLevelDbA = 85.0;
    } else if ("AERODERIVATIVE".equals(turbineClass)) {
      turbinePackageWeightTonnes = 10.0 + ratedPowerMW * 1.5;
      packageLength = 8.0 + ratedPowerMW * 0.1;
      packageWidth = 3.0 + ratedPowerMW * 0.05;
      noiseLevelDbA = 105.0;
    } else {
      turbinePackageWeightTonnes = 50.0 + ratedPowerMW * 2.5;
      packageLength = 15.0 + ratedPowerMW * 0.08;
      packageWidth = 5.0 + ratedPowerMW * 0.04;
      noiseLevelDbA = 110.0;
    }

    totalSystemWeightTonnes = turbinePackageWeightTonnes + whruWeightKg / 1000.0;

    // === Set base class fields ===
    moduleLength = packageLength;
    moduleWidth = packageWidth;
    moduleHeight = 4.0;
    setWeightTotal(totalSystemWeightTonnes * 1000.0);
    setMaxDesignPower(ratedPowerMW * 1000.0); // kW
  }

  /**
   * Selects thermal efficiency based on turbine class.
   *
   * @return thermal efficiency (0-1)
   */
  private double selectEfficiency() {
    if ("MICRO".equals(turbineClass)) {
      return 0.28;
    } else if ("AERODERIVATIVE".equals(turbineClass)) {
      return 0.38;
    } else {
      return 0.36;
    }
  }

  /**
   * Selects compression ratio based on turbine class.
   *
   * @return compression ratio
   */
  private double selectCompressionRatio() {
    if ("MICRO".equals(turbineClass)) {
      return 8.0;
    } else if ("AERODERIVATIVE".equals(turbineClass)) {
      return 25.0;
    } else {
      return 18.0;
    }
  }

  /**
   * Selects turbine inlet temperature based on class.
   *
   * @return TIT in Celsius
   */
  private double selectTIT() {
    if ("MICRO".equals(turbineClass)) {
      return 900.0;
    } else if ("AERODERIVATIVE".equals(turbineClass)) {
      return 1250.0;
    } else {
      return 1150.0;
    }
  }

  /**
   * Gets the turbine class.
   *
   * @return "AERODERIVATIVE", "INDUSTRIAL", or "MICRO"
   */
  public String getTurbineClass() {
    return turbineClass;
  }

  /**
   * Gets the rated power output.
   *
   * @return power in MW
   */
  public double getRatedPowerMW() {
    return ratedPowerMW;
  }

  /**
   * Gets the thermal efficiency.
   *
   * @return efficiency (0-1)
   */
  public double getThermalEfficiency() {
    return thermalEfficiency;
  }

  /**
   * Gets the heat rate.
   *
   * @return heat rate in kJ/kWh
   */
  public double getHeatRateKJkWh() {
    return heatRateKJkWh;
  }

  /**
   * Gets the fuel consumption.
   *
   * @return fuel consumption in kg/hr
   */
  public double getFuelConsumptionKgHr() {
    return fuelConsumptionKgHr;
  }

  /**
   * Gets the exhaust temperature.
   *
   * @return temperature in Celsius
   */
  public double getExhaustTemperatureC() {
    return exhaustTemperatureC;
  }

  /**
   * Gets the exhaust mass flow.
   *
   * @return mass flow in kg/s
   */
  public double getExhaustMassFlowKgS() {
    return exhaustMassFlowKgS;
  }

  /**
   * Gets the CO2 emission rate.
   *
   * @return emission in tonnes/hr
   */
  public double getCo2EmissionTonnesHr() {
    return co2EmissionTonnesHr;
  }

  /**
   * Gets the turbine package weight.
   *
   * @return weight in tonnes
   */
  public double getTurbinePackageWeightTonnes() {
    return turbinePackageWeightTonnes;
  }

  /**
   * Gets the noise level.
   *
   * @return noise level in dB(A) at 1m
   */
  public double getNoiseLevelDbA() {
    return noiseLevelDbA;
  }

  /**
   * Sets whether to include WHRU in design.
   *
   * @param include true to include WHRU
   */
  public void setIncludeWHRU(boolean include) {
    this.includeWHRU = include;
  }

  /**
   * Gets the WHRU heat recovery duty.
   *
   * @return duty in MW
   */
  public double getWhruDutyMW() {
    return whruDutyMW;
  }

  /**
   * Sets the WHRU outlet temperature target.
   *
   * @param tempC outlet temperature in Celsius
   */
  public void setWhruOutletTemperatureC(double tempC) {
    this.whruOutletTemperatureC = tempC;
  }

  /**
   * Gets the total system weight.
   *
   * @return weight in tonnes
   */
  public double getTotalSystemWeightTonnes() {
    return totalSystemWeightTonnes;
  }

  /**
   * Gets the NOx emission level.
   *
   * @return NOx in ppm (dry, 15% O2)
   */
  public double getNoxPpm() {
    return noxPpm;
  }

  /**
   * Sets the NOx emission level.
   *
   * @param noxPpm NOx in ppm
   */
  public void setNoxPpm(double noxPpm) {
    this.noxPpm = noxPpm;
  }
}
