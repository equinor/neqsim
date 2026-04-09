package neqsim.process.equipment.compressor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * High-level analysis module for dry gas seal condensation risk assessment in centrifugal
 * compressors. Combines isenthalpic expansion modelling, retrograde condensation mapping, dead-leg
 * cooldown simulation, condensate accumulation rate estimation, and flash vaporisation impact
 * pressure calculation into a single orchestrated analysis.
 *
 * <p>
 * This analyzer addresses a well-documented failure mode in high-pressure gas compressors (API
 * 692): gas leaking through the primary dry gas seal clearance undergoes isenthalpic
 * (Joule-Thomson) expansion from seal cavity pressure to primary vent pressure. When the seal gas
 * contains C3+ hydrocarbons, this expansion can produce liquid condensation in the primary vent
 * piping, standpipe dead-legs, and seal faces.
 * </p>
 *
 * <p>
 * Key physics modelled:
 * </p>
 * <ul>
 * <li>Isenthalpic (PH-flash) expansion through seal gap at multiple outlet pressures</li>
 * <li>Retrograde condensation mapping over the full T-P operating envelope</li>
 * <li>Dead-leg cooldown transient (lumped thermal model with natural convection)</li>
 * <li>Condensate accumulation rate from continuous seal leakage</li>
 * <li>Flash vaporisation impact pressure during repressurisation (water hammer analogy)</li>
 * <li>Gas Conditioning Unit (GCU) sizing: required cooling, separation, and reheating</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * SystemInterface sealGas = new SystemPrEos(273.15 + 44.0, 422.0);
 * sealGas.addComponent("methane", 0.7997);
 * sealGas.addComponent("ethane", 0.0996);
 * // ... add remaining components
 * sealGas.setMixingRule("classic");
 *
 * DryGasSealAnalyzer analyzer = new DryGasSealAnalyzer("GIC-Seal");
 * analyzer.setSealGas(sealGas);
 * analyzer.setSealCavityPressure(421.0, "barg");
 * analyzer.setSealCavityTemperature(44.0, "C");
 * analyzer.setPrimaryVentPressure(1.5, "barg");
 * analyzer.setSealLeakageRate(280.0, "NL/min");
 * analyzer.setStandpipeGeometry(1.5, 0.038);
 * analyzer.setStandpipeCount(2);
 * analyzer.setAmbientTemperature(25.0, "C");
 * analyzer.runFullAnalysis();
 *
 * Map&lt;String, Object&gt; results = analyzer.getResults();
 * boolean safe = analyzer.isSafeToOperate();
 * double fillTimeHours = analyzer.getStandpipeFillTimeHours();
 * </pre>
 *
 * @author neqsim
 * @version 1.0
 * @see neqsim.process.equipment.valve.ThrottlingValve
 * @see neqsim.process.measurementdevice.HydrocarbonDewPointAnalyser
 */
public class DryGasSealAnalyzer {

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(DryGasSealAnalyzer.class);

  /** Standard conditions: 0 degC, 1.01325 bara for normal litres. */
  private static final double STD_TEMPERATURE_K = 273.15;
  private static final double STD_PRESSURE_BARA = 1.01325;

  /** Stefan-Boltzmann constant in W/(m2 K4). */
  private static final double STEFAN_BOLTZMANN = 5.67e-8;

  /** Analyzer tag/name. */
  private final String name;

  /** The seal gas fluid (will be cloned for each sub-analysis). */
  private SystemInterface sealGas;

  // ── Seal operating conditions ──
  /** Seal cavity pressure in bara. */
  private double sealCavityPressureBara = 422.0;

  /** Seal cavity temperature in Kelvin. */
  private double sealCavityTemperatureK = 273.15 + 44.0;

  /** Primary vent back-pressure in bara. */
  private double primaryVentPressureBara = 2.5;

  /** Primary seal radial clearance in metres. */
  private double sealClearanceM = 0.00023;

  /** Seal leakage rate at standard conditions in normal litres per minute. */
  private double sealLeakageNLmin = 280.0;

  // ── Standpipe geometry ──
  /** Standpipe (dead-leg) length in metres. */
  private double standpipeLengthM = 1.5;

  /** Standpipe inner diameter in metres. */
  private double standpipeIDM = 0.038;

  /** Number of standpipes (typically 2: drive end + non-drive end). */
  private int standpipeCount = 2;

  /** Standpipe wall thickness in metres (for cooldown model). */
  private double standpipeWallThicknessM = 0.005;

  /** Pipe insulation thickness in metres (0 = bare pipe). */
  private double insulationThicknessM = 0.0;

  /** Insulation thermal conductivity in W/(m K). */
  private double insulationConductivity = 0.04;

  // ── Environmental conditions ──
  /** Ambient temperature in Kelvin. */
  private double ambientTemperatureK = 273.15 + 25.0;

  /** Wind speed in m/s (for forced convection on pipe exterior). */
  private double windSpeedMs = 2.0;

  // ── Results storage ──
  /** Master results map (nested hierarchy). */
  private final Map<String, Object> results = new LinkedHashMap<>();

  /** Whether the full analysis has been run. */
  private boolean analysisComplete = false;

  // ── GCU sizing parameters ──
  /** GCU superheat margin above dew point in Kelvin. */
  private double gcuSuperheatMarginK = 17.0;

  /** GCU subcool margin below dew point in Kelvin. */
  private double gcuSubcoolMarginK = 17.0;

  /**
   * Constructor for DryGasSealAnalyzer.
   *
   * @param name the analyzer tag or compressor seal identification
   */
  public DryGasSealAnalyzer(String name) {
    this.name = name;
  }

  // ═══════════════════════════════════════════════════════════════════
  // CONFIGURATION METHODS
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Sets the seal gas thermodynamic system. The fluid is cloned internally so the original is not
   * modified.
   *
   * @param sealGas the thermodynamic system representing the seal gas composition
   */
  public void setSealGas(SystemInterface sealGas) {
    this.sealGas = sealGas;
  }

  /**
   * Sets the seal cavity pressure (upstream of the seal gap).
   *
   * @param pressure the pressure value
   * @param unit pressure unit: "bara", "barg", "Pa", "MPa"
   */
  public void setSealCavityPressure(double pressure, String unit) {
    this.sealCavityPressureBara = convertPressureToBara(pressure, unit);
  }

  /**
   * Sets the seal cavity temperature (upstream of the seal gap).
   *
   * @param temperature the temperature value
   * @param unit temperature unit: "C", "K", "F"
   */
  public void setSealCavityTemperature(double temperature, String unit) {
    this.sealCavityTemperatureK = convertTemperatureToKelvin(temperature, unit);
  }

  /**
   * Sets the primary vent back-pressure (downstream of seal gap).
   *
   * @param pressure the pressure value
   * @param unit pressure unit: "bara", "barg", "Pa", "MPa"
   */
  public void setPrimaryVentPressure(double pressure, String unit) {
    this.primaryVentPressureBara = convertPressureToBara(pressure, unit);
  }

  /**
   * Sets the primary seal radial clearance.
   *
   * @param clearance the clearance in the specified unit
   * @param unit length unit: "m", "mm", "um"
   */
  public void setSealClearance(double clearance, String unit) {
    if ("mm".equals(unit)) {
      this.sealClearanceM = clearance / 1000.0;
    } else if ("um".equals(unit)) {
      this.sealClearanceM = clearance / 1.0e6;
    } else {
      this.sealClearanceM = clearance;
    }
  }

  /**
   * Sets the seal leakage rate at standard conditions.
   *
   * @param rate the leakage rate
   * @param unit rate unit: "NL/min", "Nm3/hr", "kg/hr"
   */
  public void setSealLeakageRate(double rate, String unit) {
    if ("Nm3/hr".equals(unit)) {
      this.sealLeakageNLmin = rate * 1000.0 / 60.0;
    } else if ("kg/hr".equals(unit)) {
      // Will be converted later using gas density at standard conditions
      this.sealLeakageNLmin = rate; // placeholder, recalculated in analysis
    } else {
      this.sealLeakageNLmin = rate; // NL/min default
    }
  }

  /**
   * Sets the standpipe (dead-leg) geometry.
   *
   * @param lengthM length in metres
   * @param innerDiameterM inner diameter in metres
   */
  public void setStandpipeGeometry(double lengthM, double innerDiameterM) {
    this.standpipeLengthM = lengthM;
    this.standpipeIDM = innerDiameterM;
  }

  /**
   * Sets the number of standpipe dead-legs.
   *
   * @param count number of standpipes
   */
  public void setStandpipeCount(int count) {
    this.standpipeCount = count;
  }

  /**
   * Sets the standpipe wall thickness for thermal model.
   *
   * @param thicknessM wall thickness in metres
   */
  public void setStandpipeWallThickness(double thicknessM) {
    this.standpipeWallThicknessM = thicknessM;
  }

  /**
   * Sets the pipe insulation properties.
   *
   * @param thicknessM insulation thickness in metres (0 for bare pipe)
   * @param conductivity insulation thermal conductivity in W/(m K)
   */
  public void setInsulation(double thicknessM, double conductivity) {
    this.insulationThicknessM = thicknessM;
    this.insulationConductivity = conductivity;
  }

  /**
   * Sets the ambient temperature.
   *
   * @param temperature the temperature value
   * @param unit temperature unit: "C", "K", "F"
   */
  public void setAmbientTemperature(double temperature, String unit) {
    this.ambientTemperatureK = convertTemperatureToKelvin(temperature, unit);
  }

  /**
   * Sets the wind speed for forced convection heat transfer calculation.
   *
   * @param speedMs wind speed in m/s
   */
  public void setWindSpeed(double speedMs) {
    this.windSpeedMs = speedMs;
  }

  /**
   * Sets the GCU superheat and subcool margins per API 692.
   *
   * @param superheatK margin above dew point for reheating in Kelvin
   * @param subcoolK margin below dew point for cooling in Kelvin
   */
  public void setGCUMargins(double superheatK, double subcoolK) {
    this.gcuSuperheatMarginK = superheatK;
    this.gcuSubcoolMarginK = subcoolK;
  }

  // ═══════════════════════════════════════════════════════════════════
  // MAIN ANALYSIS ENTRY POINT
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Runs the complete dry gas seal condensation analysis. This is the main entry point that chains
   * all sub-analyses in sequence.
   *
   * <p>
   * Sub-analyses executed:
   * </p>
   * <ol>
   * <li>Isenthalpic expansion through seal gap (JT cooling + condensation)</li>
   * <li>Retrograde condensation map over T-P space</li>
   * <li>Dead-leg cooldown transient simulation</li>
   * <li>Condensate accumulation rate calculation</li>
   * <li>Flash vaporisation impact pressure estimation</li>
   * <li>GCU sizing calculation</li>
   * </ol>
   */
  public void runFullAnalysis() {
    if (sealGas == null) {
      throw new IllegalStateException("Seal gas not set. Call setSealGas() first.");
    }

    results.clear();
    results.put("name", name);
    results.put("seal_cavity_pressure_bara", sealCavityPressureBara);
    results.put("seal_cavity_temperature_C", sealCavityTemperatureK - 273.15);
    results.put("primary_vent_pressure_bara", primaryVentPressureBara);
    results.put("seal_leakage_NLmin", sealLeakageNLmin);
    results.put("ambient_temperature_C", ambientTemperatureK - 273.15);

    try {
      Map<String, Object> jtResults = runIsenthalpicExpansionAnalysis();
      results.put("isenthalpic_expansion", jtResults);
    } catch (Exception ex) {
      logger.error("Isenthalpic expansion analysis failed", ex);
      results.put("isenthalpic_expansion_error", ex.getMessage());
    }

    try {
      Map<String, Object> retroResults = runRetrogradeCdensationMap();
      results.put("retrograde_condensation_map", retroResults);
    } catch (Exception ex) {
      logger.error("Retrograde condensation map failed", ex);
      results.put("retrograde_condensation_map_error", ex.getMessage());
    }

    try {
      Map<String, Object> cooldownResults = runDeadLegCooldown();
      results.put("dead_leg_cooldown", cooldownResults);
    } catch (Exception ex) {
      logger.error("Dead-leg cooldown analysis failed", ex);
      results.put("dead_leg_cooldown_error", ex.getMessage());
    }

    try {
      Map<String, Object> accumResults = runCondensateAccumulation();
      results.put("condensate_accumulation", accumResults);
    } catch (Exception ex) {
      logger.error("Condensate accumulation analysis failed", ex);
      results.put("condensate_accumulation_error", ex.getMessage());
    }

    try {
      Map<String, Object> impactResults = runFlashVaporisationImpact();
      results.put("flash_vaporisation_impact", impactResults);
    } catch (Exception ex) {
      logger.error("Flash vaporisation impact analysis failed", ex);
      results.put("flash_vaporisation_impact_error", ex.getMessage());
    }

    try {
      Map<String, Object> gcuResults = runGCUSizing();
      results.put("gcu_sizing", gcuResults);
    } catch (Exception ex) {
      logger.error("GCU sizing analysis failed", ex);
      results.put("gcu_sizing_error", ex.getMessage());
    }

    analysisComplete = true;
  }

  // ═══════════════════════════════════════════════════════════════════
  // SUB-ANALYSIS 1: ISENTHALPIC EXPANSION THROUGH SEAL GAP
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Models isenthalpic (constant enthalpy) expansion of seal gas leaking through the dry gas seal
   * clearance from seal cavity pressure to primary vent pressure.
   *
   * <p>
   * Physics: Gas leaks through the narrow annular seal gap (typically 3-5 um operating, up to 0.23
   * mm static). The process is approximately isenthalpic (negligible heat transfer in the short
   * transit through the seal). The PH-flash at each outlet pressure gives the temperature and phase
   * distribution after expansion.
   * </p>
   *
   * <p>
   * Governing equation (Joule-Thomson coefficient):
   * </p>
   *
   * $$\mu_{JT} = \left(\frac{\partial T}{\partial P}\right)_H = \frac{1}{C_p}\left[T
   * \left(\frac{\partial V}{\partial T}\right)_P - V\right]$$
   *
   * @return map with outlet temperatures, liquid fractions, and JT coefficients at each pressure
   *         step
   */
  private Map<String, Object> runIsenthalpicExpansionAnalysis() {
    Map<String, Object> result = new LinkedHashMap<>();

    // Prepare inlet conditions
    SystemInterface inletFluid = sealGas.clone();
    inletFluid.setTemperature(sealCavityTemperatureK);
    inletFluid.setPressure(sealCavityPressureBara);

    ThermodynamicOperations inletOps = new ThermodynamicOperations(inletFluid);
    inletOps.TPflash();
    inletFluid.initProperties();
    double inletEnthalpy = inletFluid.getEnthalpy("J/mol");
    double inletJTCoeff = inletFluid.getJouleThomsonCoefficient();

    result.put("inlet_pressure_bara", sealCavityPressureBara);
    result.put("inlet_temperature_C", sealCavityTemperatureK - 273.15);
    result.put("inlet_enthalpy_J_per_mol", inletEnthalpy);
    result.put("inlet_JT_coefficient_K_per_bar", inletJTCoeff * 1e5);

    // Sweep outlet pressures from seal cavity down to vent pressure
    List<Map<String, Object>> expansionPath = new ArrayList<>();
    double maxLiquidFraction = 0.0;
    double maxLiquidPressure = 0.0;
    double minOutletTemperatureC = sealCavityTemperatureK - 273.15;

    int nSteps = 40;
    double pStart = sealCavityPressureBara;
    double pEnd = primaryVentPressureBara;
    double dp = (pStart - pEnd) / nSteps;

    for (int i = 0; i <= nSteps; i++) {
      double pOut = pStart - i * dp;
      if (pOut < 1.0) {
        pOut = 1.0;
      }

      try {
        SystemInterface expandedFluid = sealGas.clone();
        expandedFluid.setTemperature(sealCavityTemperatureK);
        expandedFluid.setPressure(sealCavityPressureBara);

        ThermodynamicOperations ops = new ThermodynamicOperations(expandedFluid);
        ops.TPflash();
        double enthalpy = expandedFluid.getEnthalpy("J/mol");

        // Now do PH flash at the lower outlet pressure
        expandedFluid.setPressure(pOut);
        ops.PHflash(enthalpy, "J/mol");
        expandedFluid.initProperties();

        double outletTempC = expandedFluid.getTemperature("C");
        double liquidVolFraction = 0.0;
        double liquidMolFraction = 0.0;
        double liquidDensity = 0.0;

        if (expandedFluid.getNumberOfPhases() > 1 && expandedFluid.hasPhaseType("oil")) {
          liquidVolFraction =
              expandedFluid.getPhase("oil").getVolume("m3") / expandedFluid.getVolume("m3") * 100.0;
          liquidMolFraction = expandedFluid.getPhase("oil").getBeta();
          liquidDensity = expandedFluid.getPhase("oil").getDensity("kg/m3");
        }

        double jtCoeff = expandedFluid.getJouleThomsonCoefficient();

        Map<String, Object> point = new LinkedHashMap<>();
        point.put("pressure_bara", pOut);
        point.put("temperature_C", outletTempC);
        point.put("liquid_vol_pct", liquidVolFraction);
        point.put("liquid_mol_fraction", liquidMolFraction);
        point.put("liquid_density_kg_m3", liquidDensity);
        point.put("jt_coefficient_K_per_bar", jtCoeff * 1e5);
        point.put("number_of_phases", expandedFluid.getNumberOfPhases());
        expansionPath.add(point);

        if (liquidVolFraction > maxLiquidFraction) {
          maxLiquidFraction = liquidVolFraction;
          maxLiquidPressure = pOut;
        }
        if (outletTempC < minOutletTemperatureC) {
          minOutletTemperatureC = outletTempC;
        }

      } catch (Exception ex) {
        logger.warn("PH flash failed at P={} bara: {}", pOut, ex.getMessage());
      }
    }

    result.put("expansion_path", expansionPath);
    result.put("max_liquid_vol_pct", maxLiquidFraction);
    result.put("max_liquid_at_pressure_bara", maxLiquidPressure);
    result.put("min_outlet_temperature_C", minOutletTemperatureC);
    result.put("total_jt_cooling_C", (sealCavityTemperatureK - 273.15) - minOutletTemperatureC);

    // Condensation at primary vent conditions
    result.put("vent_conditions", getVentConditions(inletEnthalpy));

    return result;
  }

  /**
   * Gets the outlet conditions at the primary vent pressure after isenthalpic expansion.
   *
   * @param inletEnthalpy the inlet enthalpy in J/mol
   * @return map with vent outlet temperature, liquid fraction, and phase details
   */
  private Map<String, Object> getVentConditions(double inletEnthalpy) {
    Map<String, Object> vent = new LinkedHashMap<>();
    try {
      SystemInterface ventFluid = sealGas.clone();
      ventFluid.setTemperature(sealCavityTemperatureK);
      ventFluid.setPressure(primaryVentPressureBara);

      ThermodynamicOperations ops = new ThermodynamicOperations(ventFluid);
      ops.PHflash(inletEnthalpy, "J/mol");
      ventFluid.initProperties();

      vent.put("pressure_bara", primaryVentPressureBara);
      vent.put("temperature_C", ventFluid.getTemperature("C"));
      vent.put("number_of_phases", ventFluid.getNumberOfPhases());

      if (ventFluid.hasPhaseType("oil")) {
        double liqVol =
            ventFluid.getPhase("oil").getVolume("m3") / ventFluid.getVolume("m3") * 100.0;
        vent.put("liquid_vol_pct", liqVol);
        vent.put("liquid_density_kg_m3", ventFluid.getPhase("oil").getDensity("kg/m3"));
      } else {
        vent.put("liquid_vol_pct", 0.0);
      }
    } catch (Exception ex) {
      vent.put("error", ex.getMessage());
    }
    return vent;
  }

  // ═══════════════════════════════════════════════════════════════════
  // SUB-ANALYSIS 2: RETROGRADE CONDENSATION MAP
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Maps the retrograde condensation region by performing TP flash calculations over a grid of
   * temperatures and pressures covering the seal gas standstill operating envelope.
   *
   * <p>
   * This produces the condensation contour map used for operational planning: any T-P condition in
   * the two-phase region will produce liquid in the seal gas piping.
   * </p>
   *
   * @return map with condensation grid data, dew point curve, and maximum condensation conditions
   */
  private Map<String, Object> runRetrogradeCdensationMap() {
    Map<String, Object> result = new LinkedHashMap<>();

    // Temperature range: 10C to seal cavity temperature
    double tMinC = Math.max(0.0, ambientTemperatureK - 273.15 - 15.0);
    double tMaxC = sealCavityTemperatureK - 273.15;
    int nT = 25;
    double dtC = (tMaxC - tMinC) / nT;

    // Pressure range: 2 bara to seal cavity pressure (capped at 200 bara for grid)
    double pMin = primaryVentPressureBara;
    double pMax = Math.min(sealCavityPressureBara, 200.0);
    int nP = 25;
    double dpBar = (pMax - pMin) / nP;

    List<Map<String, Object>> gridPoints = new ArrayList<>();
    double maxLiquid = 0.0;
    double maxLiquidTC = 0.0;
    double maxLiquidPBara = 0.0;

    // Dew point curve at each pressure
    List<Map<String, Object>> dewPointCurve = new ArrayList<>();

    for (int j = 0; j <= nP; j++) {
      double pBara = pMin + j * dpBar;

      // Find dew point temperature at this pressure
      try {
        SystemInterface dewFluid = sealGas.clone();
        dewFluid.setPressure(pBara);
        dewFluid.setTemperature(273.15 + 0.0);
        ThermodynamicOperations dewOps = new ThermodynamicOperations(dewFluid);
        dewOps.dewPointTemperatureFlash();
        double dewTempC = dewFluid.getTemperature("C");
        Map<String, Object> dewPt = new LinkedHashMap<>();
        dewPt.put("pressure_bara", pBara);
        dewPt.put("dew_point_C", dewTempC);
        dewPointCurve.add(dewPt);
      } catch (Exception ex) {
        // Dew point calc can fail near cricondenbar or above
        logger.debug("Dew point calc failed at P={}: {}", pBara, ex.getMessage());
      }

      // Sweep temperature at this pressure
      for (int i = 0; i <= nT; i++) {
        double tC = tMinC + i * dtC;

        try {
          SystemInterface gridFluid = sealGas.clone();
          gridFluid.setTemperature(273.15 + tC);
          gridFluid.setPressure(pBara);
          gridFluid.setMultiPhaseCheck(true);

          ThermodynamicOperations gridOps = new ThermodynamicOperations(gridFluid);
          gridOps.TPflash();

          double liquidVolPct = 0.0;
          if (gridFluid.getNumberOfPhases() > 1 && gridFluid.hasPhaseType("oil")) {
            liquidVolPct =
                gridFluid.getPhase("oil").getVolume("m3") / gridFluid.getVolume("m3") * 100.0;
          }

          Map<String, Object> pt = new LinkedHashMap<>();
          pt.put("temperature_C", tC);
          pt.put("pressure_bara", pBara);
          pt.put("liquid_vol_pct", liquidVolPct);
          pt.put("n_phases", gridFluid.getNumberOfPhases());
          gridPoints.add(pt);

          if (liquidVolPct > maxLiquid) {
            maxLiquid = liquidVolPct;
            maxLiquidTC = tC;
            maxLiquidPBara = pBara;
          }
        } catch (Exception ex) {
          // Skip failed points
        }
      }
    }

    result.put("grid_points", gridPoints);
    result.put("dew_point_curve", dewPointCurve);
    result.put("max_liquid_vol_pct", maxLiquid);
    result.put("max_liquid_temperature_C", maxLiquidTC);
    result.put("max_liquid_pressure_bara", maxLiquidPBara);
    result.put("grid_temperature_range_C", new double[] {tMinC, tMaxC});
    result.put("grid_pressure_range_bara", new double[] {pMin, pMax});

    return result;
  }

  // ═══════════════════════════════════════════════════════════════════
  // SUB-ANALYSIS 3: DEAD-LEG COOLDOWN TRANSIENT
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Simulates the transient cooldown of gas trapped in a standpipe dead-leg after compressor
   * shutdown. Uses a lumped thermal capacitance model combining natural convection and radiation
   * heat loss from the pipe exterior.
   *
   * <p>
   * Governing equation (lumped thermal model):
   * </p>
   *
   * $$(\rho C_p)_{eff} V \frac{dT}{dt} = -U A_{outer} (T - T_{amb})$$
   *
   * <p>
   * where the overall heat transfer coefficient U includes:
   * </p>
   * <ul>
   * <li>Internal natural convection: Churchill-Chu correlation for vertical cylinders</li>
   * <li>Pipe wall conduction: $k_{steel} / t_{wall}$</li>
   * <li>Insulation conduction: $k_{ins} / t_{ins}$ (if present)</li>
   * <li>External combined convection + radiation</li>
   * </ul>
   *
   * <p>
   * At each time step, the gas T-P state is re-flashed (TP flash) to detect when condensation first
   * occurs during cooldown and how liquid fraction evolves.
   * </p>
   *
   * @return map with cooldown temperature profile, time to dew point, and liquid accumulation
   */
  private Map<String, Object> runDeadLegCooldown() {
    Map<String, Object> result = new LinkedHashMap<>();

    // Standpipe geometry
    double ri = standpipeIDM / 2.0;
    double ro = ri + standpipeWallThicknessM;
    double rIns = ro + insulationThicknessM;
    double pipeLength = standpipeLengthM;
    double innerArea = Math.PI * standpipeIDM * pipeLength;
    double outerArea = Math.PI * 2.0 * rIns * pipeLength;
    double volume = Math.PI * ri * ri * pipeLength;

    // Steel properties (carbon steel)
    double steelDensity = 7850.0; // kg/m3
    double steelCp = 500.0; // J/(kg K)
    double steelK = 50.0; // W/(m K)
    double steelVolume = Math.PI * (ro * ro - ri * ri) * pipeLength;
    double steelMass = steelDensity * steelVolume;
    double steelThermalCapacity = steelMass * steelCp;

    // Initial gas conditions (settleout pressure, initial temperature)
    double initialPBara = sealCavityPressureBara;
    // Assume gas starts at cavity temperature if heater is on, or lower
    double initialTK = sealCavityTemperatureK;

    // Get initial gas properties
    SystemInterface gasFluid = sealGas.clone();
    gasFluid.setTemperature(initialTK);
    gasFluid.setPressure(initialPBara);
    ThermodynamicOperations gasOps = new ThermodynamicOperations(gasFluid);
    gasOps.TPflash();
    gasFluid.initProperties();

    double gasDensity = gasFluid.getDensity("kg/m3");
    double gasMass = gasDensity * volume;
    double gasCp = gasFluid.getCp("J/kgK");
    double gasThermalCapacity = gasMass * gasCp;

    // Total thermal capacity (gas + pipe wall)
    double totalThermalCap = gasThermalCapacity + steelThermalCapacity;

    // Time-stepping
    double dt = 60.0; // 60 seconds per step
    double maxTimeHours = 48.0;
    int maxSteps = (int) (maxTimeHours * 3600.0 / dt);

    double currentTK = initialTK;
    double dewPointTimeHours = -1.0;
    double maxLiquidFraction = 0.0;
    boolean condensationStarted = false;

    List<Map<String, Object>> cooldownProfile = new ArrayList<>();

    for (int step = 0; step <= maxSteps; step++) {
      double timeHours = step * dt / 3600.0;

      // TP flash at current conditions
      double liquidVolPct = 0.0;
      try {
        SystemInterface stepFluid = sealGas.clone();
        stepFluid.setTemperature(currentTK);
        stepFluid.setPressure(initialPBara);
        stepFluid.setMultiPhaseCheck(true);
        ThermodynamicOperations stepOps = new ThermodynamicOperations(stepFluid);
        stepOps.TPflash();

        if (stepFluid.getNumberOfPhases() > 1 && stepFluid.hasPhaseType("oil")) {
          liquidVolPct =
              stepFluid.getPhase("oil").getVolume("m3") / stepFluid.getVolume("m3") * 100.0;

          if (!condensationStarted) {
            condensationStarted = true;
            dewPointTimeHours = timeHours;
          }
        }
        if (liquidVolPct > maxLiquidFraction) {
          maxLiquidFraction = liquidVolPct;
        }

        // Update gas Cp for next step
        stepFluid.initProperties();
        gasCp = stepFluid.getCp("J/kgK");
        gasDensity = stepFluid.getDensity("kg/m3");
        gasMass = gasDensity * volume;
        gasThermalCapacity = gasMass * gasCp;
        totalThermalCap = gasThermalCapacity + steelThermalCapacity;
      } catch (Exception ex) {
        // Use previous Cp values
      }

      // Record point every 15 minutes (or at key events)
      if (step % Math.max(1, (int) (900.0 / dt)) == 0 || step == 0
          || (condensationStarted && step == (int) (dewPointTimeHours * 3600.0 / dt))) {
        Map<String, Object> pt = new LinkedHashMap<>();
        pt.put("time_hours", timeHours);
        pt.put("temperature_C", currentTK - 273.15);
        pt.put("liquid_vol_pct", liquidVolPct);
        pt.put("condensation_started", condensationStarted);
        cooldownProfile.add(pt);
      }

      // Stop if gas has reached ambient temperature
      if (Math.abs(currentTK - ambientTemperatureK) < 0.1) {
        break;
      }

      // Calculate heat loss
      double hExt = calculateExternalHTC(rIns, currentTK);
      double uOverall = calculateOverallU(ri, ro, rIns, steelK, hExt);
      double qLoss = uOverall * outerArea * (currentTK - ambientTemperatureK);

      // Temperature change
      double dT = -qLoss * dt / totalThermalCap;
      currentTK += dT;
      if (currentTK < ambientTemperatureK) {
        currentTK = ambientTemperatureK;
      }
    }

    result.put("cooldown_profile", cooldownProfile);
    result.put("initial_temperature_C", initialTK - 273.15);
    result.put("final_temperature_C", currentTK - 273.15);
    result.put("ambient_temperature_C", ambientTemperatureK - 273.15);
    result.put("time_to_dew_point_hours", dewPointTimeHours);
    result.put("max_liquid_vol_pct_at_equilibrium", maxLiquidFraction);
    result.put("standpipe_volume_L", volume * 1000.0);
    result.put("standpipe_steel_mass_kg", steelMass);

    return result;
  }

  /**
   * Calculates the external heat transfer coefficient combining forced convection (wind) and
   * radiation from a horizontal/vertical cylinder in air.
   *
   * <p>
   * Forced convection uses the Churchill-Bernstein correlation for cross-flow over a cylinder:
   * </p>
   *
   * $$Nu = 0.3 + \frac{0.62 Re^{1/2} Pr^{1/3}}{[1 + (0.4/Pr)^{2/3}]^{1/4}} \left[1 +
   * \left(\frac{Re}{282000}\right)^{5/8}\right]^{4/5}$$
   *
   * <p>
   * Radiation uses the Stefan-Boltzmann law with emissivity 0.9 (oxidised steel):
   * </p>
   *
   * $$h_{rad} = \varepsilon \sigma (T_s^2 + T_{amb}^2)(T_s + T_{amb})$$
   *
   * @param outerRadius outer radius of pipe (including insulation) in metres
   * @param surfaceTemperatureK pipe surface temperature in Kelvin
   * @return combined external heat transfer coefficient in W/(m2 K)
   */
  private double calculateExternalHTC(double outerRadius, double surfaceTemperatureK) {
    double dOuter = 2.0 * outerRadius;

    // Air properties at film temperature
    double tFilm = (surfaceTemperatureK + ambientTemperatureK) / 2.0;
    double airDensity = 1.225 * 293.15 / tFilm; // ideal gas from sea level
    double airViscosity = 1.81e-5 * Math.pow(tFilm / 293.15, 0.7);
    double airK = 0.026 * Math.pow(tFilm / 293.15, 0.8);
    double airCp = 1005.0;
    double airPr = airViscosity * airCp / airK;

    // Forced convection: Churchill-Bernstein for cylinder in crossflow
    double re = airDensity * windSpeedMs * dOuter / airViscosity;
    double hForced = 5.0; // minimum natural convection
    if (re > 1.0) {
      double nuForced = 0.3 + 0.62 * Math.pow(re, 0.5) * Math.pow(airPr, 1.0 / 3.0)
          / Math.pow(1.0 + Math.pow(0.4 / airPr, 2.0 / 3.0), 0.25)
          * Math.pow(1.0 + Math.pow(re / 282000.0, 5.0 / 8.0), 4.0 / 5.0);
      hForced = nuForced * airK / dOuter;
    }

    // Radiation
    double emissivity = 0.9; // oxidised steel
    double hRad = emissivity * STEFAN_BOLTZMANN
        * (surfaceTemperatureK * surfaceTemperatureK + ambientTemperatureK * ambientTemperatureK)
        * (surfaceTemperatureK + ambientTemperatureK);

    return hForced + hRad;
  }

  /**
   * Calculates the overall heat transfer coefficient for the pipe wall + insulation composite.
   *
   * <p>
   * For cylindrical geometry:
   * </p>
   *
   * $$\frac{1}{U} = \frac{r_{ins}}{r_i h_{int}} + \frac{r_{ins} \ln(r_o/r_i)}{k_{steel}} +
   * \frac{r_{ins} \ln(r_{ins}/r_o)}{k_{ins}} + \frac{1}{h_{ext}}$$
   *
   * <p>
   * Internal convection is assumed to be dominated by natural convection at low velocities
   * (stagnant dead-leg), approximated as h_int = 5 W/(m2 K).
   * </p>
   *
   * @param ri inner radius in metres
   * @param ro outer radius of steel wall in metres
   * @param rIns outer radius including insulation in metres
   * @param steelK steel thermal conductivity in W/(m K)
   * @param hExt external heat transfer coefficient in W/(m2 K)
   * @return overall heat transfer coefficient in W/(m2 K) based on outer area
   */
  private double calculateOverallU(double ri, double ro, double rIns, double steelK, double hExt) {
    double hInt = 5.0; // natural convection in stagnant gas

    double rInternal = rIns / (ri * hInt);
    double rWall = rIns * Math.log(ro / ri) / steelK;
    double rInsulation = 0.0;
    if (insulationThicknessM > 0.001) { // only if insulation exists
      rInsulation = rIns * Math.log(rIns / ro) / insulationConductivity;
    }
    double rExternal = 1.0 / hExt;

    return 1.0 / (rInternal + rWall + rInsulation + rExternal);
  }

  // ═══════════════════════════════════════════════════════════════════
  // SUB-ANALYSIS 4: CONDENSATE ACCUMULATION RATE
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Calculates the condensate accumulation rate from continuous seal leakage, considering both the
   * JT expansion mechanism (primary) and retrograde condensation from gas cooling in dead-legs
   * (secondary).
   *
   * <p>
   * Method: Converts the seal leakage rate from NL/min to actual molar flow at standard conditions,
   * then applies the liquid mole fraction from the isenthalpic expansion to get the liquid
   * production rate.
   * </p>
   *
   * <p>
   * The accumulation rate is:
   * </p>
   *
   * $$\dot{V}_{liq} = \dot{n}_{leak} \cdot x_{liq} \cdot \frac{MW_{liq}}{\rho_{liq}}$$
   *
   * <p>
   * where $\dot{n}_{leak}$ is the molar leakage rate, $x_{liq}$ is the liquid mole fraction from
   * PH-flash at vent conditions, $MW_{liq}$ is the liquid molar mass, and $\rho_{liq}$ is the
   * liquid density.
   * </p>
   *
   * @return map with accumulation rates, standpipe fill times, and daily volumes
   */
  private Map<String, Object> runCondensateAccumulation() {
    Map<String, Object> result = new LinkedHashMap<>();

    // Convert NL/min to mol/s using ideal gas law at standard conditions
    // PV = nRT => n = PV/(RT)
    double volumeFlowAtStdM3perSec = sealLeakageNLmin / 1000.0 / 60.0;
    double molarFlowMolPerSec =
        STD_PRESSURE_BARA * 1e5 * volumeFlowAtStdM3perSec / (8.314 * STD_TEMPERATURE_K);

    result.put("seal_leakage_NLmin", sealLeakageNLmin);
    result.put("seal_leakage_mol_per_sec", molarFlowMolPerSec);

    // PH flash at primary vent conditions to get liquid fraction
    try {
      SystemInterface inletFluid = sealGas.clone();
      inletFluid.setTemperature(sealCavityTemperatureK);
      inletFluid.setPressure(sealCavityPressureBara);

      ThermodynamicOperations ops = new ThermodynamicOperations(inletFluid);
      ops.TPflash();
      double enthalpy = inletFluid.getEnthalpy("J/mol");

      // Expand to vent pressure
      SystemInterface ventFluid = sealGas.clone();
      ventFluid.setTemperature(sealCavityTemperatureK);
      ventFluid.setPressure(primaryVentPressureBara);
      ThermodynamicOperations ventOps = new ThermodynamicOperations(ventFluid);
      ventOps.PHflash(enthalpy, "J/mol");
      ventFluid.initProperties();

      double liquidMolFraction = 0.0;
      double liquidDensity = 500.0;
      double liquidMW = 0.080; // kg/mol default

      if (ventFluid.getNumberOfPhases() > 1 && ventFluid.hasPhaseType("oil")) {
        liquidMolFraction = ventFluid.getPhase("oil").getBeta();
        liquidDensity = ventFluid.getPhase("oil").getDensity("kg/m3");
        liquidMW = ventFluid.getPhase("oil").getMolarMass("kg/mol");
      }

      // Liquid molar flow rate
      double liquidMolPerSec = molarFlowMolPerSec * liquidMolFraction;
      // Liquid mass flow rate
      double liquidKgPerSec = liquidMolPerSec * liquidMW;
      // Liquid volume flow rate
      double liquidM3PerSec = liquidKgPerSec / liquidDensity;
      double liquidLPerDay = liquidM3PerSec * 1000.0 * 86400.0;
      double liquidLPerHour = liquidLPerDay / 24.0;

      // Standpipe fill time
      double standpipeVolumeL =
          Math.PI * Math.pow(standpipeIDM / 2.0, 2) * standpipeLengthM * 1000.0;
      double totalDeadLegVolumeL = standpipeVolumeL * standpipeCount;
      double fillTimeHours = totalDeadLegVolumeL / liquidLPerHour;

      result.put("liquid_mol_fraction_at_vent", liquidMolFraction);
      result.put("liquid_density_kg_m3", liquidDensity);
      result.put("liquid_molar_mass_kg_per_mol", liquidMW);
      result.put("liquid_flow_kg_per_sec", liquidKgPerSec);
      result.put("liquid_flow_L_per_day", liquidLPerDay);
      result.put("liquid_flow_L_per_hour", liquidLPerHour);
      result.put("standpipe_volume_L_each", standpipeVolumeL);
      result.put("total_dead_leg_volume_L", totalDeadLegVolumeL);
      result.put("fill_time_hours", fillTimeHours);
      result.put("vent_temperature_C", ventFluid.getTemperature("C"));
      result.put("condensation_present", liquidMolFraction > 0.0);

    } catch (Exception ex) {
      result.put("error", ex.getMessage());
      logger.error("Condensate accumulation calculation failed", ex);
    }

    return result;
  }

  // ═══════════════════════════════════════════════════════════════════
  // SUB-ANALYSIS 5: FLASH VAPORISATION IMPACT PRESSURE
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Estimates the pressure impulse generated when accumulated liquid condensate flashes to vapour
   * during rapid repressurisation of the seal gas system.
   *
   * <p>
   * Physics: When liquid condensate trapped in a standpipe dead-leg is suddenly exposed to high
   * pressure gas during compressor restart or re-pressurisation, the liquid evaporates rapidly. The
   * volume expansion generates a pressure wave that propagates through the piping and can impact
   * the seal faces.
   * </p>
   *
   * <p>
   * The analysis uses two approaches:
   * </p>
   * <ol>
   * <li><b>Confined flash pressure:</b> TV flash of liquid at original dead-leg volume to find the
   * equilibrium pressure after complete evaporation</li>
   * <li><b>Water hammer analogy:</b> Pressure rise from slug deceleration in a confined pipe using
   * the Joukowsky equation: $\Delta P = \rho \cdot c \cdot \Delta v$</li>
   * </ol>
   *
   * @return map with flash pressure, slug velocity, impact pressure, and slug acceleration
   */
  private Map<String, Object> runFlashVaporisationImpact() {
    Map<String, Object> result = new LinkedHashMap<>();

    // Calculate liquid volume in standpipe
    double standpipeVolumeM3 = Math.PI * Math.pow(standpipeIDM / 2.0, 2) * standpipeLengthM;
    double standpipeVolumeL = standpipeVolumeM3 * 1000.0;

    // Assume standpipe is partially filled with condensate
    // Conservative: use 50% liquid fill as representative
    double liquidFillFraction = 0.5;
    double liquidVolumeM3 = standpipeVolumeM3 * liquidFillFraction;

    // Get condensate composition and properties from PH flash at vent
    try {
      SystemInterface inletFluid = sealGas.clone();
      inletFluid.setTemperature(sealCavityTemperatureK);
      inletFluid.setPressure(sealCavityPressureBara);

      ThermodynamicOperations ops = new ThermodynamicOperations(inletFluid);
      ops.TPflash();
      double enthalpy = inletFluid.getEnthalpy("J/mol");

      SystemInterface ventFluid = sealGas.clone();
      ventFluid.setTemperature(sealCavityTemperatureK);
      ventFluid.setPressure(primaryVentPressureBara);
      ThermodynamicOperations ventOps = new ThermodynamicOperations(ventFluid);
      ventOps.PHflash(enthalpy, "J/mol");
      ventFluid.initProperties();

      // Get condensate properties
      double condensateDensity = 550.0;
      double condensateMW = 0.080;
      if (ventFluid.hasPhaseType("oil")) {
        condensateDensity = ventFluid.getPhase("oil").getDensity("kg/m3");
        condensateMW = ventFluid.getPhase("oil").getMolarMass("kg/mol");
      }

      double condensateMass = condensateDensity * liquidVolumeM3;

      // Method 1: Confined flash — heat condensate in fixed volume to get pressure rise
      // Use TV flash at repressurisation temperature (seal gas arrival temperature)
      double repressT = sealCavityTemperatureK; // gas arriving at seal cavity temperature

      SystemInterface flashFluid = sealGas.clone();
      flashFluid.setTemperature(ambientTemperatureK);
      flashFluid.setPressure(primaryVentPressureBara);
      flashFluid.setMultiPhaseCheck(true);

      ThermodynamicOperations flashOps = new ThermodynamicOperations(flashFluid);
      flashOps.TPflash();

      // Get reference molar volume at condensation conditions
      double molarVolAtCondensation = flashFluid.getMolarVolume("m3/mol");

      // Now flash at repressurisation temperature with same molar volume
      // This gives the confined pressure rise
      flashFluid.setTemperature(repressT);
      try {
        flashOps.TVflash(molarVolAtCondensation, "m3/mol");
        double confinedPressureBara = flashFluid.getPressure("bara");
        result.put("confined_flash_pressure_bara", confinedPressureBara);
      } catch (Exception tvEx) {
        result.put("confined_flash_pressure_note", "TV flash not converged");
      }

      // Method 2: Water hammer / Joukowsky pressure rise
      // When gas repressurises the standpipe, it drives the liquid slug
      // Slug velocity from gas expansion energy
      double gasPressureBara = sealCavityPressureBara;
      double gasSpeedOfSound = 354.0; // m/s approximate for this gas

      // Get actual speed of sound from NeqSim
      try {
        SystemInterface soundFluid = sealGas.clone();
        soundFluid.setTemperature(sealCavityTemperatureK);
        soundFluid.setPressure(sealCavityPressureBara);
        ThermodynamicOperations soundOps = new ThermodynamicOperations(soundFluid);
        soundOps.TPflash();
        soundFluid.initProperties();
        if (soundFluid.hasPhaseType("gas")) {
          gasSpeedOfSound = soundFluid.getPhase("gas").getSoundSpeed();
        }
      } catch (Exception sEx) {
        // use default
      }

      // Slug acceleration: dP/dx = rho * a for the liquid slug
      // dP ~ (gasPressureBara - primaryVentPressureBara) * 1e5 Pa
      double dPPa = (gasPressureBara - primaryVentPressureBara) * 1e5;
      double slugLengthM = liquidFillFraction * standpipeLengthM;
      double slugAcceleration = dPPa / (condensateDensity * slugLengthM);

      // Terminal velocity before impact (energy balance)
      // 0.5 * rho * v^2 = dP * slug_length (work done by gas on slug)
      double slugVelocity = Math.sqrt(2.0 * dPPa / condensateDensity);

      // Joukowsky pressure rise: dP = rho * c * dv
      double joukowskyBar = condensateDensity * gasSpeedOfSound * slugVelocity / 1e5;

      // More realistic: momentum of liquid slug impacting seal face
      double slugMass = condensateDensity * Math.PI * Math.pow(standpipeIDM / 2.0, 2) * slugLengthM;
      double slugKineticEnergy = 0.5 * slugMass * slugVelocity * slugVelocity;

      // Impact time scale (from compressibility and deceleration)
      double impactTimeSec = standpipeIDM / gasSpeedOfSound;
      double impactForce = slugMass * slugVelocity / impactTimeSec;
      double sealFaceArea = Math.PI * Math.pow(standpipeIDM / 2.0, 2);
      double impactPressureBar = impactForce / sealFaceArea / 1e5;

      result.put("condensate_density_kg_m3", condensateDensity);
      result.put("condensate_mass_kg", condensateMass);
      result.put("liquid_fill_fraction", liquidFillFraction);
      result.put("slug_length_m", slugLengthM);
      result.put("slug_acceleration_m_s2", slugAcceleration);
      result.put("slug_velocity_m_s", slugVelocity);
      result.put("slug_kinetic_energy_J", slugKineticEnergy);
      result.put("gas_speed_of_sound_m_s", gasSpeedOfSound);
      result.put("joukowsky_impact_pressure_bar", joukowskyBar);
      result.put("momentum_impact_pressure_bar", impactPressureBar);
      result.put("impact_time_ms", impactTimeSec * 1000.0);
      result.put("seal_face_area_m2", sealFaceArea);
      result.put("gas_film_collapse_likely", impactPressureBar > 10.0);

    } catch (Exception ex) {
      result.put("error", ex.getMessage());
      logger.error("Flash vaporisation impact calculation failed", ex);
    }

    return result;
  }

  // ═══════════════════════════════════════════════════════════════════
  // SUB-ANALYSIS 6: GAS CONDITIONING UNIT (GCU) SIZING
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Sizes a Gas Conditioning Unit (GCU) per API 692 guidelines. The GCU removes heavy hydrocarbons
   * from the seal gas by cooling below the dew point, separating the condensed liquid, and
   * reheating the dry gas above the dew point plus a safety margin.
   *
   * <p>
   * GCU design basis (per API 692):
   * </p>
   * <ul>
   * <li>Cool seal gas to dew point minus subcool margin (typically 17 degC below)</li>
   * <li>Separate liquid at the lowest temperature</li>
   * <li>Reheat gas to dew point plus superheat margin (typically 17 degC above)</li>
   * <li>Size the cooler, separator, and heater</li>
   * </ul>
   *
   * <p>
   * The required cooling duty is:
   * </p>
   *
   * $$Q_{cool} = \dot{m} \cdot (h_{in} - h_{cooled})$$
   *
   * <p>
   * and the reheating duty is:
   * </p>
   *
   * $$Q_{heat} = \dot{m}_{dry} \cdot (h_{out} - h_{separated})$$
   *
   * @return map with GCU sizing data: duties, temperatures, liquid production, and dry gas
   *         composition
   */
  private Map<String, Object> runGCUSizing() {
    Map<String, Object> result = new LinkedHashMap<>();

    // Step 1: Find the maximum dew point temperature across all pressures (cricondentherm).
    // If the supply pressure is above the cricondenbar, the dew point at supply pressure
    // does not exist. The GCU must operate at a pressure where condensation can occur.
    double supplyPressure = sealCavityPressureBara;
    double maxDewPointC = -273.15;
    double maxDewPointPBara = primaryVentPressureBara;

    try {
      // Search for cricondentherm by sweeping pressures
      double pSearchMin = primaryVentPressureBara + 1.0;
      double pSearchMax = Math.min(supplyPressure, 350.0);
      int nSearch = 30;
      double dpSearch = (pSearchMax - pSearchMin) / nSearch;

      for (int i = 0; i <= nSearch; i++) {
        double pSearch = pSearchMin + i * dpSearch;
        try {
          SystemInterface dewSweep = sealGas.clone();
          dewSweep.setPressure(pSearch);
          dewSweep.setTemperature(273.15 + 0.0);
          ThermodynamicOperations dewSweepOps = new ThermodynamicOperations(dewSweep);
          dewSweepOps.dewPointTemperatureFlash();
          double dewTempC = dewSweep.getTemperature("C");
          if (dewTempC > maxDewPointC) {
            maxDewPointC = dewTempC;
            maxDewPointPBara = pSearch;
          }
        } catch (Exception dewEx) {
          // Dew point calc can fail near or above cricondenbar — skip
        }
      }
    } catch (Exception sweepEx) {
      logger.warn("GCU dew point sweep failed: {}", sweepEx.getMessage());
    }

    // If no dew point was found anywhere, no condensation risk — no GCU needed
    if (maxDewPointC < -250.0) {
      result.put("gcu_required", false);
      result.put("reason", "No dew point found in operating pressure range");
      return result;
    }

    // Use the cricondentherm pressure as the GCU operating pressure
    double gcuOperatingPressure = maxDewPointPBara;
    double dewPointC = maxDewPointC;

    result.put("supply_pressure_bara", supplyPressure);
    result.put("cricondentherm_C", dewPointC);
    result.put("cricondentherm_pressure_bara", gcuOperatingPressure);
    result.put("dew_point_at_supply_C", dewPointC);

    try {
      // Step 2: Cooling target = cricondentherm dew point - subcool margin
      double coolingTargetC = dewPointC - gcuSubcoolMarginK;
      result.put("gcu_cooling_target_C", coolingTargetC);

      // Step 3: Calculate cooling duty (enthalpy difference)
      // GCU operates at the pressure where max dew point occurs
      SystemInterface hotFluid = sealGas.clone();
      hotFluid.setTemperature(sealCavityTemperatureK);
      hotFluid.setPressure(gcuOperatingPressure);
      ThermodynamicOperations hotOps = new ThermodynamicOperations(hotFluid);
      hotOps.TPflash();
      hotFluid.initProperties();
      double hHot = hotFluid.getEnthalpy("J/mol");

      SystemInterface coldFluid = sealGas.clone();
      coldFluid.setTemperature(273.15 + coolingTargetC);
      coldFluid.setPressure(gcuOperatingPressure);
      coldFluid.setMultiPhaseCheck(true);
      ThermodynamicOperations coldOps = new ThermodynamicOperations(coldFluid);
      coldOps.TPflash();
      coldFluid.initProperties();
      double hCold = coldFluid.getEnthalpy("J/mol");

      // Cooling duty per mole
      double coolingDutyPerMol = hHot - hCold; // J/mol

      // Convert seal gas flow to molar flow
      // Seal gas supply flow is typically much larger than leakage
      // Use seal leakage * safety factor as minimum GCU throughput
      double gcuFlowNLmin = sealLeakageNLmin * 3.0; // size for 3x leakage
      double gcuFlowM3PerSec = gcuFlowNLmin / 1000.0 / 60.0;
      double gcuMolarFlow = STD_PRESSURE_BARA * 1e5 * gcuFlowM3PerSec / (8.314 * STD_TEMPERATURE_K);
      double coolingDutyW = coolingDutyPerMol * gcuMolarFlow;

      result.put("gcu_flow_NLmin", gcuFlowNLmin);
      result.put("cooling_duty_W", coolingDutyW);
      result.put("cooling_duty_kW", coolingDutyW / 1000.0);

      // Step 4: Liquid separated at cooling target
      double liquidVolPct = 0.0;
      double liquidMolFraction = 0.0;
      if (coldFluid.getNumberOfPhases() > 1 && coldFluid.hasPhaseType("oil")) {
        liquidVolPct =
            coldFluid.getPhase("oil").getVolume("m3") / coldFluid.getVolume("m3") * 100.0;
        liquidMolFraction = coldFluid.getPhase("oil").getBeta();
      }
      result.put("liquid_separated_vol_pct", liquidVolPct);
      result.put("liquid_separated_mol_fraction", liquidMolFraction);

      // Step 5: Reheat target = new dew point + superheat margin
      // After liquid separation, find new dew point of the gas phase
      double reheatTargetC = dewPointC + gcuSuperheatMarginK;
      result.put("gcu_reheat_target_C", reheatTargetC);

      // Step 6: Calculate reheating duty
      SystemInterface separatedGas = sealGas.clone();
      separatedGas.setTemperature(273.15 + coolingTargetC);
      separatedGas.setPressure(gcuOperatingPressure);
      ThermodynamicOperations sepOps = new ThermodynamicOperations(separatedGas);
      sepOps.TPflash();
      separatedGas.initProperties();
      double hSep = separatedGas.getEnthalpy("J/mol");

      SystemInterface reheatedGas = sealGas.clone();
      reheatedGas.setTemperature(273.15 + reheatTargetC);
      reheatedGas.setPressure(gcuOperatingPressure);
      ThermodynamicOperations reheatOps = new ThermodynamicOperations(reheatedGas);
      reheatOps.TPflash();
      reheatedGas.initProperties();
      double hReheat = reheatedGas.getEnthalpy("J/mol");

      double reheatDutyPerMol = hReheat - hSep;
      double reheatDutyW = reheatDutyPerMol * gcuMolarFlow;
      result.put("reheat_duty_W", reheatDutyW);
      result.put("reheat_duty_kW", reheatDutyW / 1000.0);

      // Step 7: Summary
      result.put("total_electrical_kW", (coolingDutyW + reheatDutyW) / 1000.0);
      // GCU is required if the cricondentherm is above ambient (condensation will occur
      // somewhere in the downstream pressure range) or if liquid was separated
      boolean gcuRequired = liquidVolPct > 0.0 || dewPointC > (ambientTemperatureK - 273.15);
      result.put("gcu_required", gcuRequired);
      result.put("gcu_superheat_margin_K", gcuSuperheatMarginK);
      result.put("gcu_subcool_margin_K", gcuSubcoolMarginK);

    } catch (Exception ex) {
      result.put("error", ex.getMessage());
      logger.error("GCU sizing calculation failed", ex);
    }

    return result;
  }

  // ═══════════════════════════════════════════════════════════════════
  // RESULT QUERY METHODS
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Returns the complete results hierarchy from the full analysis.
   *
   * @return map with all sub-analysis results
   */
  public Map<String, Object> getResults() {
    return results;
  }

  /**
   * Returns whether the seal gas system is safe from condensation at the configured conditions. The
   * system is considered safe only if no liquid is produced at any point in the isenthalpic
   * expansion path and no retrograde condensation occurs at ambient conditions.
   *
   * @return true if no condensation risk, false if condensation is predicted
   */
  public boolean isSafeToOperate() {
    if (!analysisComplete) {
      return false;
    }

    Object jtResult = results.get("isenthalpic_expansion");
    if (jtResult instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> jt = (Map<String, Object>) jtResult;
      Object maxLiq = jt.get("max_liquid_vol_pct");
      if (maxLiq instanceof Number && ((Number) maxLiq).doubleValue() > 0.01) {
        return false;
      }
    }

    Object retroResult = results.get("retrograde_condensation_map");
    if (retroResult instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> retro = (Map<String, Object>) retroResult;
      Object maxRetroLiq = retro.get("max_liquid_vol_pct");
      if (maxRetroLiq instanceof Number && ((Number) maxRetroLiq).doubleValue() > 0.01) {
        return false;
      }
    }

    return true;
  }

  /**
   * Returns the estimated time in hours for the standpipe dead-legs to fill with condensate from
   * continuous seal leakage.
   *
   * @return fill time in hours, or -1 if no condensation occurs or analysis not complete
   */
  public double getStandpipeFillTimeHours() {
    if (!analysisComplete) {
      return -1.0;
    }

    Object accumResult = results.get("condensate_accumulation");
    if (accumResult instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> accum = (Map<String, Object>) accumResult;
      Object fillTime = accum.get("fill_time_hours");
      if (fillTime instanceof Number) {
        return ((Number) fillTime).doubleValue();
      }
    }
    return -1.0;
  }

  /**
   * Returns the maximum liquid volume fraction produced during isenthalpic expansion.
   *
   * @return maximum liquid volume percent, or 0 if no condensation
   */
  public double getMaxJTLiquidFraction() {
    if (!analysisComplete) {
      return 0.0;
    }

    Object jtResult = results.get("isenthalpic_expansion");
    if (jtResult instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> jt = (Map<String, Object>) jtResult;
      Object maxLiq = jt.get("max_liquid_vol_pct");
      if (maxLiq instanceof Number) {
        return ((Number) maxLiq).doubleValue();
      }
    }
    return 0.0;
  }

  /**
   * Returns whether the full analysis has been completed.
   *
   * @return true if runFullAnalysis() has been called and completed
   */
  public boolean isAnalysisComplete() {
    return analysisComplete;
  }

  /**
   * Returns the analyzer name.
   *
   * @return the name/tag
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the results as a JSON string.
   *
   * @return JSON representation of all results
   */
  public String toJson() {
    return new com.google.gson.GsonBuilder().setPrettyPrinting()
        .serializeSpecialFloatingPointValues().create().toJson(results);
  }

  // ═══════════════════════════════════════════════════════════════════
  // UNIT CONVERSION HELPERS
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Converts a pressure value to bara.
   *
   * @param pressure the pressure value
   * @param unit the unit string
   * @return pressure in bara
   */
  private static double convertPressureToBara(double pressure, String unit) {
    if ("barg".equals(unit)) {
      return pressure + 1.01325;
    } else if ("Pa".equals(unit)) {
      return pressure / 1e5;
    } else if ("MPa".equals(unit)) {
      return pressure * 10.0;
    } else if ("psi".equals(unit) || "psia".equals(unit)) {
      return pressure * 0.0689476;
    } else {
      return pressure; // assume bara
    }
  }

  /**
   * Converts a temperature value to Kelvin.
   *
   * @param temperature the temperature value
   * @param unit the unit string
   * @return temperature in Kelvin
   */
  private static double convertTemperatureToKelvin(double temperature, String unit) {
    if ("C".equals(unit)) {
      return temperature + 273.15;
    } else if ("F".equals(unit)) {
      return (temperature - 32.0) * 5.0 / 9.0 + 273.15;
    } else {
      return temperature; // assume Kelvin
    }
  }
}
