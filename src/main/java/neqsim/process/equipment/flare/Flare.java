package neqsim.process.equipment.flare;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.flare.dto.FlareCapacityDTO;
import neqsim.process.equipment.flare.dto.FlareDispersionSurrogateDTO;
import neqsim.process.equipment.flare.dto.FlarePerformanceDTO;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.unit.PowerUnit;

/**
 * Flare unit operation for combustion of a process stream.
 *
 * @author esol
 */
public class Flare extends TwoPortEquipment {
  private static final long serialVersionUID = 1000;

  private double heatDuty = 0.0; // J/s
  private double co2Emission = 0.0; // kg/s
  private double flameHeight = 30.0; // m, default radiation height
  private double radiantFraction = 0.18; // fraction of heat to radiation
  private double tipDiameter = 0.3; // m, used for dispersion surrogate

  private double designHeatDutyCapacityW = Double.NaN;
  private double designMassFlowCapacityKgS = Double.NaN;
  private double designMolarFlowCapacityMoleS = Double.NaN;

  private transient CapacityCheckResult lastCapacityCheck = CapacityCheckResult.empty();

  // Dynamic/transient operation tracking
  private double cumulativeHeatReleasedGJ = 0.0; // Total heat released in GJ
  private double cumulativeGasBurnedKg = 0.0; // Total gas burned in kg
  private double cumulativeCO2EmissionKg = 0.0; // Total CO2 emissions in kg
  private double lastTransientTime = 0.0; // Last time step for transient tracking

  /**
   * Default constructor.
   *
   * @param name name of the flare
   */
  public Flare(String name) {
    super(name);
  }

  /**
   * Constructor setting inlet stream.
   *
   * @param name name of flare
   * @param inletStream inlet stream
   */
  public Flare(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface stream) {
    this.inStream = stream;
    this.outStream = new Stream(stream.getName() + "_flareout", stream.getFluid().clone());
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface thermoSystem = inStream.getThermoSystem().clone();
    double flowSm3sec = inStream.getFlowRate("Sm3/sec");
    heatDuty = inStream.LCV() * flowSm3sec;

    double molesTotalPerSec = inStream.getFlowRate("mole/sec");
    double molesCarbonPerSec = 0.0;
    for (int i = 0; i < thermoSystem.getNumberOfComponents(); i++) {
      double moleFrac = thermoSystem.getComponent(i).getz();
      double molesCompPerSec = moleFrac * molesTotalPerSec;
      double nC = thermoSystem.getComponent(i).getElements().getNumberOfElements("C");
      molesCarbonPerSec += molesCompPerSec * nC;
    }
    co2Emission = molesCarbonPerSec * 44.01e-3; // kg/s

    outStream.setThermoSystem(thermoSystem);
    setCalculationIdentifier(id);
    lastCapacityCheck = evaluateCapacityInternal(heatDuty, inStream.getFlowRate("kg/sec"),
        inStream.getFlowRate("mole/sec"));
  }

  /**
   * Update cumulative values for dynamic/transient operation.
   * 
   * @param timeStep time step in seconds
   */
  public void updateCumulative(double timeStep) {
    if (timeStep > 0.0) {
      cumulativeHeatReleasedGJ += (heatDuty * 1.0e-9) * timeStep; // W*s -> GJ
      cumulativeGasBurnedKg += inStream.getFlowRate("kg/sec") * timeStep;
      cumulativeCO2EmissionKg += co2Emission * timeStep;
      lastTransientTime += timeStep;
    }
  }

  /**
   * Reset cumulative values (useful when starting a new transient simulation).
   */
  public void resetCumulative() {
    cumulativeHeatReleasedGJ = 0.0;
    cumulativeGasBurnedKg = 0.0;
    cumulativeCO2EmissionKg = 0.0;
    lastTransientTime = 0.0;
  }

  /**
   * Reset cumulative values. Shorthand for resetCumulative().
   */
  public void reset() {
    resetCumulative();
  }

  /**
   * Get cumulative heat released.
   * 
   * @param unit engineering unit, e.g. "GJ", "MJ", "MMBtu"
   * @return cumulative heat released in specified unit
   */
  public double getCumulativeHeatReleased(String unit) {
    switch (unit) {
      case "MJ":
        return cumulativeHeatReleasedGJ * 1000.0;
      case "MMBtu":
        return cumulativeHeatReleasedGJ * 0.947817; // GJ to MMBtu
      case "GJ":
      default:
        return cumulativeHeatReleasedGJ;
    }
  }

  /**
   * Get cumulative gas burned.
   * 
   * @param unit engineering unit, e.g. "kg", "tonnes"
   * @return cumulative gas burned in specified unit
   */
  public double getCumulativeGasBurned(String unit) {
    switch (unit) {
      case "tonnes":
        return cumulativeGasBurnedKg / 1000.0;
      case "kg":
      default:
        return cumulativeGasBurnedKg;
    }
  }

  /**
   * Get cumulative CO2 emissions.
   * 
   * @param unit engineering unit, e.g. "kg", "tonnes"
   * @return cumulative CO2 emissions in specified unit
   */
  public double getCumulativeCO2Emission(String unit) {
    switch (unit) {
      case "tonnes":
        return cumulativeCO2EmissionKg / 1000.0;
      case "kg":
      default:
        return cumulativeCO2EmissionKg;
    }
  }

  /**
   * Get total transient simulation time tracked.
   * 
   * @return time in seconds
   */
  public double getTransientTime() {
    return lastTransientTime;
  }

  /**
   * Get heat released from flare.
   *
   * @return heat duty in W
   */
  public double getHeatDuty() {
    return heatDuty;
  }

  /**
   * Get heat released in desired unit.
   *
   * @param unit engineering unit, e.g. "MW"
   * @return heat duty in specified unit
   */
  public double getHeatDuty(String unit) {
    PowerUnit conv = new PowerUnit(heatDuty, "W");
    return conv.getValue(unit);
  }

  /**
   * Get CO2 emissions in kg/s.
   *
   * @return CO2 emission rate
   */
  public double getCO2Emission() {
    return co2Emission;
  }

  /**
   * Get CO2 emissions in specified unit. Supported units: kg/sec, kg/hr, kg/day
   *
   * @param unit desired unit
   * @return emission in specified unit
   */
  public double getCO2Emission(String unit) {
    switch (unit) {
      case "kg/hr":
        return co2Emission * 3600.0;
      case "kg/day":
        return co2Emission * 3600.0 * 24.0;
      default:
        return co2Emission;
    }
  }

  /**
   * Set the effective flame centerline height used for radiation calculations.
   *
   * @param flameHeight flame height in meters
   */
  public void setFlameHeight(double flameHeight) {
    this.flameHeight = Math.max(0.0, flameHeight);
  }

  /**
   * Define the radiant fraction of the flare heat release used for point-source radiation.
   *
   * @param radiantFraction fraction (0-1)
   */
  public void setRadiantFraction(double radiantFraction) {
    if (Double.isNaN(radiantFraction)) {
      return;
    }
    this.radiantFraction = Math.min(Math.max(radiantFraction, 0.0), 1.0);
  }

  /**
   * Set flare tip diameter used for exit velocity and dispersion surrogate estimates.
   *
   * @param tipDiameter tip diameter in meters
   */
  public void setTipDiameter(double tipDiameter) {
    this.tipDiameter = Math.max(1.0e-4, tipDiameter);
  }

  /**
   * Configure the design heat-release capacity for capacity validation.
   *
   * @param value heat-duty value
   * @param unit engineering unit (W, kW, MW)
   */
  public void setDesignHeatDutyCapacity(double value, String unit) {
    if (Double.isNaN(value)) {
      designHeatDutyCapacityW = Double.NaN;
      return;
    }
    PowerUnit conv = new PowerUnit(value, unit);
    designHeatDutyCapacityW = conv.getValue("W");
  }

  /**
   * Configure the design mass-flow capacity for capacity validation.
   *
   * @param value mass-flow value
   * @param unit supported units: kg/sec, kg/hr, kg/day
   */
  public void setDesignMassFlowCapacity(double value, String unit) {
    designMassFlowCapacityKgS = convertMassFlowToKgPerSec(value, unit);
  }

  /**
   * Configure the design molar-flow capacity for capacity validation.
   *
   * @param value molar-flow value
   * @param unit supported units: mole/sec, kmole/hr
   */
  public void setDesignMolarFlowCapacity(double value, String unit) {
    designMolarFlowCapacityMoleS = convertMolarFlowToMolePerSec(value, unit);
  }

  /**
   * Estimate the flame radiation heat flux at a horizontal ground distance using the currently
   * calculated heat duty.
   *
   * @param groundDistanceM horizontal distance from flare base [m]
   * @return radiant heat flux [W/m2]
   */
  public double estimateRadiationHeatFlux(double groundDistanceM) {
    return estimateRadiationHeatFlux(heatDuty, groundDistanceM);
  }

  /**
   * Estimate the flame radiation heat flux at a horizontal ground distance for a specified heat
   * duty.
   *
   * @param scenarioHeatDutyW heat duty in W
   * @param groundDistanceM horizontal distance from flare base [m]
   * @return radiant heat flux [W/m2]
   */
  public double estimateRadiationHeatFlux(double scenarioHeatDutyW, double groundDistanceM) {
    double radialDistance =
        Math.sqrt(Math.max(0.0, groundDistanceM * groundDistanceM + flameHeight * flameHeight));
    if (radialDistance < 1.0e-6) {
      return 0.0;
    }
    return radiantFraction * scenarioHeatDutyW / (4.0 * Math.PI * radialDistance * radialDistance);
  }

  /**
   * Determine the horizontal distance at which the radiation level drops to the specified
   * threshold.
   *
   * @param fluxThresholdWm2 target heat flux [W/m2]
   * @return horizontal distance from flare base [m]
   */
  public double radiationDistanceForFlux(double fluxThresholdWm2) {
    return radiationDistanceForFlux(heatDuty, fluxThresholdWm2);
  }

  /**
   * Determine the horizontal distance at which the radiation level drops to the specified threshold
   * for a scenario heat duty.
   *
   * @param scenarioHeatDutyW heat duty in W
   * @param fluxThresholdWm2 target heat flux [W/m2]
   * @return horizontal distance from flare base [m]
   */
  public double radiationDistanceForFlux(double scenarioHeatDutyW, double fluxThresholdWm2) {
    if (fluxThresholdWm2 <= 0.0) {
      return 0.0;
    }
    double numerator = radiantFraction * scenarioHeatDutyW;
    if (numerator <= 0.0) {
      return 0.0;
    }
    double radialDistanceSquared = numerator / (4.0 * Math.PI * fluxThresholdWm2);
    double horizontalSquared = radialDistanceSquared - flameHeight * flameHeight;
    if (horizontalSquared <= 0.0) {
      return 0.0;
    }
    return Math.sqrt(horizontalSquared);
  }

  /**
   * Build a dispersion surrogate descriptor for the current operating point.
   *
   * @return surrogate DTO with momentum-like metrics
   */
  public FlareDispersionSurrogateDTO getDispersionSurrogate() {
    return getDispersionSurrogate(inStream != null ? inStream.getFlowRate("kg/sec") : 0.0,
        inStream != null ? inStream.getFlowRate("mole/sec") : 0.0);
  }

  /**
   * Build a dispersion surrogate descriptor for a specified mass and molar rate.
   *
   * @param massRateKgS mass flow in kg/s
   * @param molarRateMoleS molar flow in mole/s
   * @return surrogate DTO with momentum-like metrics
   */
  public FlareDispersionSurrogateDTO getDispersionSurrogate(double massRateKgS,
      double molarRateMoleS) {
    double density = (inStream != null) ? inStream.getThermoSystem().getDensity("kg/m3") : 1.0;
    density = Math.max(1.0e-3, density);
    double area = Math.PI * tipDiameter * tipDiameter / 4.0;
    area = Math.max(1.0e-6, area);
    double velocity = Math.max(0.0, massRateKgS / (density * area));
    double momentumFlux = density * velocity * velocity;
    double massPerMomentum = (massRateKgS > 1.0e-12) ? momentumFlux / massRateKgS : 0.0;
    double referenceMassRate = inStream != null ? inStream.getFlowRate("kg/sec") : 0.0;
    double standardVolumeRate = 0.0;
    if (referenceMassRate > 1.0e-12) {
      double refStdVolume = inStream.getFlowRate("Sm3/sec");
      standardVolumeRate = refStdVolume * massRateKgS / referenceMassRate;
    }

    return new FlareDispersionSurrogateDTO(massRateKgS, molarRateMoleS, velocity, momentumFlux,
        massPerMomentum, standardVolumeRate);
  }

  /**
   * Evaluate the current operation against configured design capacities.
   *
   * @return capacity check result
   */
  public CapacityCheckResult evaluateCapacity() {
    lastCapacityCheck =
        evaluateCapacityInternal(heatDuty, inStream != null ? inStream.getFlowRate("kg/sec") : 0.0,
            inStream != null ? inStream.getFlowRate("mole/sec") : 0.0);
    return lastCapacityCheck;
  }

  /**
   * Evaluate a hypothetical load case against configured design capacities.
   *
   * @param scenarioHeatDutyW heat duty in W
   * @param massRateKgS mass flow in kg/s
   * @param molarRateMoleS molar flow in mole/s
   * @return capacity check result for the specified scenario
   */
  public CapacityCheckResult evaluateCapacity(double scenarioHeatDutyW, double massRateKgS,
      double molarRateMoleS) {
    return evaluateCapacityInternal(scenarioHeatDutyW, massRateKgS, molarRateMoleS);
  }

  private CapacityCheckResult evaluateCapacityInternal(double scenarioHeatDutyW, double massRateKgS,
      double molarRateMoleS) {
    double heatCapacity = designHeatDutyCapacityW;
    double massCapacity = designMassFlowCapacityKgS;
    double molarCapacity = designMolarFlowCapacityMoleS;

    double heatUtil =
        (Double.isFinite(heatCapacity) && heatCapacity > 0.0) ? scenarioHeatDutyW / heatCapacity
            : Double.NaN;
    double massUtil =
        (Double.isFinite(massCapacity) && massCapacity > 0.0) ? massRateKgS / massCapacity
            : Double.NaN;
    double molarUtil =
        (Double.isFinite(molarCapacity) && molarCapacity > 0.0) ? molarRateMoleS / molarCapacity
            : Double.NaN;

    return new CapacityCheckResult(scenarioHeatDutyW, heatCapacity, massRateKgS, massCapacity,
        molarRateMoleS, molarCapacity, heatUtil, massUtil, molarUtil);
  }

  /**
   * Latest computed capacity check result.
   *
   * @return last capacity result
   */
  public CapacityCheckResult getLastCapacityCheck() {
    return lastCapacityCheck;
  }

  /**
   * Produce a performance summary DTO for the current operating point.
   *
   * @return performance DTO containing emissions, radiation and capacity data
   */
  public FlarePerformanceDTO getPerformanceSummary() {
    double massRate = inStream != null ? inStream.getFlowRate("kg/sec") : 0.0;
    double molarRate = inStream != null ? inStream.getFlowRate("mole/sec") : 0.0;
    return buildPerformanceSummary(getName(), heatDuty, massRate, molarRate, co2Emission,
        inStream != null ? buildEmissionMap() : Collections.emptyMap());
  }

  /**
   * Produce a performance summary DTO for a hypothetical load case.
   *
   * @param scenarioName label for the scenario
   * @param scenarioHeatDutyW heat duty in W (if &lt;=0 the value will be estimated from mass rate)
   * @param massRateKgS mass flow in kg/s
   * @param molarRateMoleS molar flow in mole/s (optional, negative to auto-estimate)
   * @return performance DTO containing emissions, radiation and capacity data
   */
  public FlarePerformanceDTO getPerformanceSummary(String scenarioName, double scenarioHeatDutyW,
      double massRateKgS, double molarRateMoleS) {
    double heat = scenarioHeatDutyW;
    if (heat <= 0.0 && massRateKgS > 0.0) {
      heat = estimateHeatDutyFromMassRate(massRateKgS);
    }
    double molarRate = molarRateMoleS;
    if (molarRate <= 0.0 && massRateKgS > 0.0) {
      molarRate = estimateMolarRateFromMassRate(massRateKgS);
    }
    double co2Rate = estimateCO2EmissionFromMassRate(massRateKgS);
    Map<String, Double> emissions =
        inStream != null ? scaleEmissionMap(massRateKgS) : Collections.emptyMap();
    return buildPerformanceSummary(scenarioName, heat, massRateKgS, molarRate, co2Rate, emissions);
  }

  private FlarePerformanceDTO buildPerformanceSummary(String label, double scenarioHeatDutyW,
      double massRateKgS, double molarRateMoleS, double co2RateKgS,
      Map<String, Double> emissionMap) {
    double flux30m = estimateRadiationHeatFlux(scenarioHeatDutyW, 30.0);
    double distance4kW = radiationDistanceForFlux(scenarioHeatDutyW, 4000.0);
    FlareDispersionSurrogateDTO dispersion = getDispersionSurrogate(massRateKgS, molarRateMoleS);
    CapacityCheckResult capacity =
        evaluateCapacityInternal(scenarioHeatDutyW, massRateKgS, molarRateMoleS);

    return new FlarePerformanceDTO(label, scenarioHeatDutyW, massRateKgS, molarRateMoleS,
        co2RateKgS, flux30m, distance4kW, dispersion, emissionMap, capacity.toDTO());
  }

  private double estimateHeatDutyFromMassRate(double massRateKgS) {
    double baseMassRate = inStream != null ? inStream.getFlowRate("kg/sec") : 0.0;
    if (baseMassRate > 1.0e-12) {
      return heatDuty * (massRateKgS / baseMassRate);
    }
    return heatDuty;
  }

  private double estimateMolarRateFromMassRate(double massRateKgS) {
    double baseMassRate = inStream != null ? inStream.getFlowRate("kg/sec") : 0.0;
    double baseMolarRate = inStream != null ? inStream.getFlowRate("mole/sec") : 0.0;
    if (baseMassRate > 1.0e-12 && baseMolarRate > 0.0) {
      return baseMolarRate * (massRateKgS / baseMassRate);
    }
    double mw = inStream != null ? inStream.getThermoSystem().getMolarMass() : Double.NaN;
    if (Double.isFinite(mw) && mw > 1.0e-12) {
      return massRateKgS / mw;
    }
    return 0.0;
  }

  private double estimateCO2EmissionFromMassRate(double massRateKgS) {
    double baseMassRate = inStream != null ? inStream.getFlowRate("kg/sec") : 0.0;
    if (baseMassRate > 1.0e-12) {
      return co2Emission * (massRateKgS / baseMassRate);
    }
    return co2Emission;
  }

  private Map<String, Double> buildEmissionMap() {
    Map<String, Double> emissionMap = new HashMap<>();
    emissionMap.put("CO2_kg_s", co2Emission);
    emissionMap.put("HeatDuty_MW", heatDuty * 1.0e-6);
    return emissionMap;
  }

  private Map<String, Double> scaleEmissionMap(double massRateKgS) {
    if (inStream == null) {
      return Collections.singletonMap("CO2_kg_s", estimateCO2EmissionFromMassRate(massRateKgS));
    }
    double baseMassRate = inStream.getFlowRate("kg/sec");
    if (baseMassRate <= 1.0e-12) {
      return buildEmissionMap();
    }
    Map<String, Double> emissionMap = new HashMap<>();
    for (Map.Entry<String, Double> entry : buildEmissionMap().entrySet()) {
      emissionMap.put(entry.getKey(), entry.getValue() * massRateKgS / baseMassRate);
    }
    return emissionMap;
  }

  private double convertMassFlowToKgPerSec(double value, String unit) {
    if (Double.isNaN(value)) {
      return Double.NaN;
    }
    switch (unit) {
      case "kg/sec":
      case "kg/s":
        return value;
      case "kg/hr":
        return value / 3600.0;
      case "kg/day":
        return value / (3600.0 * 24.0);
      default:
        throw new IllegalArgumentException("Unsupported mass flow unit: " + unit);
    }
  }

  private double convertMolarFlowToMolePerSec(double value, String unit) {
    if (Double.isNaN(value)) {
      return Double.NaN;
    }
    switch (unit) {
      case "mole/sec":
      case "mol/sec":
        return value;
      case "kmole/hr":
      case "kmol/hr":
        return value * 1000.0 / 3600.0;
      default:
        throw new IllegalArgumentException("Unsupported molar flow unit: " + unit);
    }
  }

  /**
   * Result object containing utilization against the configured design capacities.
   */
  public static class CapacityCheckResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double heatDutyW;
    private final double designHeatDutyW;
    private final double massRateKgS;
    private final double designMassRateKgS;
    private final double molarRateMoleS;
    private final double designMolarRateMoleS;
    private final double heatUtilization;
    private final double massUtilization;
    private final double molarUtilization;

    CapacityCheckResult(double heatDutyW, double designHeatDutyW, double massRateKgS,
        double designMassRateKgS, double molarRateMoleS, double designMolarRateMoleS,
        double heatUtilization, double massUtilization, double molarUtilization) {
      this.heatDutyW = heatDutyW;
      this.designHeatDutyW = designHeatDutyW;
      this.massRateKgS = massRateKgS;
      this.designMassRateKgS = designMassRateKgS;
      this.molarRateMoleS = molarRateMoleS;
      this.designMolarRateMoleS = designMolarRateMoleS;
      this.heatUtilization = heatUtilization;
      this.massUtilization = massUtilization;
      this.molarUtilization = molarUtilization;
    }

    static CapacityCheckResult empty() {
      return new CapacityCheckResult(0.0, Double.NaN, 0.0, Double.NaN, 0.0, Double.NaN, Double.NaN,
          Double.NaN, Double.NaN);
    }

    public double getHeatDutyW() {
      return heatDutyW;
    }

    public double getDesignHeatDutyW() {
      return designHeatDutyW;
    }

    public double getMassRateKgS() {
      return massRateKgS;
    }

    public double getDesignMassRateKgS() {
      return designMassRateKgS;
    }

    public double getMolarRateMoleS() {
      return molarRateMoleS;
    }

    public double getDesignMolarRateMoleS() {
      return designMolarRateMoleS;
    }

    public double getHeatUtilization() {
      return heatUtilization;
    }

    public double getMassUtilization() {
      return massUtilization;
    }

    public double getMolarUtilization() {
      return molarUtilization;
    }

    /**
     * Determine if any configured capacity is overloaded ({@literal >} 1.0 utilization).
     *
     * @return true if overloaded
     */
    public boolean isOverloaded() {
      return exceeds(heatUtilization) || exceeds(massUtilization) || exceeds(molarUtilization);
    }

    private boolean exceeds(double utilization) {
      return Double.isFinite(utilization) && utilization > 1.0 + 1.0e-6;
    }

    /**
     * Convert to a simple DTO for reporting.
     *
     * @return DTO view of the capacity check
     */
    public FlareCapacityDTO toDTO() {
      return new FlareCapacityDTO(heatDutyW, designHeatDutyW, heatUtilization, massRateKgS,
          designMassRateKgS, massUtilization, molarRateMoleS, designMolarRateMoleS,
          molarUtilization, isOverloaded());
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new neqsim.process.util.monitor.FlareResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(neqsim.process.util.report.ReportConfig cfg) {
    if (cfg != null && cfg
        .getDetailLevel(getName()) == neqsim.process.util.report.ReportConfig.DetailLevel.HIDE) {
      return null;
    }
    neqsim.process.util.monitor.FlareResponse res =
        new neqsim.process.util.monitor.FlareResponse(this);
    res.applyConfig(cfg);
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(res);
  }
}
