package neqsim.pvtsimulation.flowassurance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Coupled CO2 corrosion analyzer using electrolyte CPA EOS for rigorous pH prediction and de
 * Waard-Milliams model for corrosion rate estimation.
 *
 * <p>
 * This class bridges NeqSim's thermodynamic engine with corrosion engineering by:
 * </p>
 * <ol>
 * <li>Running an electrolyte CPA flash to determine CO2 dissolution in the aqueous phase and the
 * resulting pH from carbonic acid speciation (H2CO3, HCO3-, CO3-2, H3O+).</li>
 * <li>Extracting the CO2 partial pressure from the gas/dense phase.</li>
 * <li>Feeding the rigorous pH and pCO2 into the de Waard-Milliams (1991) corrosion model.</li>
 * <li>Computing scale prediction (FeCO3, CaCO3) via the ScalePredictionCalculator.</li>
 * </ol>
 *
 * <p>
 * The electrolyte CPA EOS accounts for ion-specific interactions (Na+, Cl-, HCO3-, etc.) and yields
 * thermodynamically consistent pH values, which are more accurate than empirical pH correlations —
 * particularly for CO2-rich CCS streams where pH can be as low as 3.0.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * CO2CorrosionAnalyzer analyzer = new CO2CorrosionAnalyzer();
 * analyzer.setTemperatureCelsius(60.0);
 * analyzer.setPressureBara(100.0);
 * analyzer.setCO2MoleFractionInGas(0.95);
 * analyzer.setWaterMoleFractionInGas(0.05);
 * analyzer.run();
 *
 * double pH = analyzer.getAqueousPH();
 * double corrosionRate = analyzer.getCorrosionRate();
 * String severity = analyzer.getCorrosionSeverity();
 * String json = analyzer.toJson();
 * }
 * </pre>
 *
 * <p>
 * For brines, add ions:
 * </p>
 *
 * <pre>
 * {@code
 * analyzer.setSodiumMoleFraction(0.001);
 * analyzer.setChlorideMoleFraction(0.001);
 * analyzer.run();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 * @see DeWaardMilliamsCorrosion
 * @see ScalePredictionCalculator
 * @see SystemElectrolyteCPAstatoil
 */
public class CO2CorrosionAnalyzer implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // --- Input: conditions ---

  /** Temperature in Celsius. */
  private double temperatureC = 60.0;

  /** Total pressure in bara. */
  private double pressureBara = 100.0;

  // --- Input: gas composition (mole fractions) ---

  /** CO2 mole fraction in the overall feed. */
  private double co2MoleFraction = 0.95;

  /** Water mole fraction in the overall feed. */
  private double waterMoleFraction = 0.05;

  /** Methane mole fraction in the overall feed. */
  private double methaneMoleFraction = 0.0;

  /** H2S mole fraction in the overall feed. */
  private double h2sMoleFraction = 0.0;

  /** N2 mole fraction in the overall feed. */
  private double n2MoleFraction = 0.0;

  // --- Input: ions (mole fractions for brine) ---

  /** Na+ mole fraction. */
  private double naPlusMoleFraction = 0.0;

  /** Cl- mole fraction. */
  private double clMinusMoleFraction = 0.0;

  // --- Input: flow / inhibitor ---

  /** Flow velocity in m/s. */
  private double flowVelocity = 2.0;

  /** Pipe internal diameter in metres. */
  private double pipeDiameter = 0.254;

  /** Chemical inhibitor efficiency (0 to 1). */
  private double inhibitorEfficiency = 0.0;

  /** Glycol weight fraction in aqueous phase (0 to 1). */
  private double glycolFraction = 0.0;

  // --- Results (populated by run()) ---

  /** Whether the analysis has been run. */
  private boolean hasRun = false;

  /** Aqueous phase pH from electrolyte CPA. */
  private double aqueousPH = Double.NaN;

  /** CO2 partial pressure in bar (from flash). */
  private double co2PartialPressure = Double.NaN;

  /** H2S partial pressure in bar (from flash). */
  private double h2sPartialPressure = Double.NaN;

  /** Whether free water is present. */
  private boolean freeWaterPresent = false;

  /** Number of phases from the flash. */
  private int numberOfPhases = 0;

  /** CO2 mole fraction dissolved in aqueous phase. */
  private double co2InAqueous = Double.NaN;

  /** Corrected corrosion rate (mm/yr). */
  private double corrosionRate = Double.NaN;

  /** Baseline (uncorrected) corrosion rate (mm/yr). */
  private double baselineCorrosionRate = Double.NaN;

  /** Corrosion severity. */
  private String corrosionSeverity = "";

  /** The DeWaardMilliams model used internally. */
  private DeWaardMilliamsCorrosion corrosionModel;

  /** The ScalePredictionCalculator used internally. */
  private ScalePredictionCalculator scaleCalculator;

  /** The thermodynamic system created for the flash. */
  private transient SystemInterface thermoSystem;

  /**
   * Creates a new CO2CorrosionAnalyzer with default parameters.
   */
  public CO2CorrosionAnalyzer() {
    this.corrosionModel = new DeWaardMilliamsCorrosion();
    this.scaleCalculator = new ScalePredictionCalculator();
  }

  /**
   * Creates a new CO2CorrosionAnalyzer with specified T and P.
   *
   * @param temperatureC temperature in Celsius
   * @param pressureBara total pressure in bara
   */
  public CO2CorrosionAnalyzer(double temperatureC, double pressureBara) {
    this();
    this.temperatureC = temperatureC;
    this.pressureBara = pressureBara;
  }

  // --- Setters for conditions ---

  /**
   * Sets the temperature.
   *
   * @param temperatureC temperature in Celsius
   */
  public void setTemperatureCelsius(double temperatureC) {
    this.temperatureC = temperatureC;
    this.hasRun = false;
  }

  /**
   * Sets the total pressure.
   *
   * @param pressureBara total pressure in bara
   */
  public void setPressureBara(double pressureBara) {
    this.pressureBara = pressureBara;
    this.hasRun = false;
  }

  /**
   * Sets the CO2 mole fraction in the overall feed.
   *
   * @param moleFraction CO2 mole fraction (0 to 1)
   */
  public void setCO2MoleFractionInGas(double moleFraction) {
    this.co2MoleFraction = moleFraction;
    this.hasRun = false;
  }

  /**
   * Sets the water mole fraction in the overall feed.
   *
   * @param moleFraction water mole fraction (0 to 1)
   */
  public void setWaterMoleFractionInGas(double moleFraction) {
    this.waterMoleFraction = moleFraction;
    this.hasRun = false;
  }

  /**
   * Sets the methane mole fraction in the overall feed.
   *
   * @param moleFraction methane mole fraction (0 to 1)
   */
  public void setMethaneMoleFractionInGas(double moleFraction) {
    this.methaneMoleFraction = moleFraction;
    this.hasRun = false;
  }

  /**
   * Sets the H2S mole fraction in the overall feed.
   *
   * @param moleFraction H2S mole fraction (0 to 1)
   */
  public void setH2SMoleFractionInGas(double moleFraction) {
    this.h2sMoleFraction = moleFraction;
    this.hasRun = false;
  }

  /**
   * Sets the N2 mole fraction in the overall feed.
   *
   * @param moleFraction N2 mole fraction (0 to 1)
   */
  public void setN2MoleFractionInGas(double moleFraction) {
    this.n2MoleFraction = moleFraction;
    this.hasRun = false;
  }

  /**
   * Sets the Na+ ion mole fraction for brine simulation.
   *
   * @param moleFraction Na+ mole fraction
   */
  public void setSodiumMoleFraction(double moleFraction) {
    this.naPlusMoleFraction = moleFraction;
    this.hasRun = false;
  }

  /**
   * Sets the Cl- ion mole fraction for brine simulation.
   *
   * @param moleFraction Cl- mole fraction
   */
  public void setChlorideMoleFraction(double moleFraction) {
    this.clMinusMoleFraction = moleFraction;
    this.hasRun = false;
  }

  /**
   * Sets the flow velocity.
   *
   * @param velocityMs flow velocity in m/s
   */
  public void setFlowVelocity(double velocityMs) {
    this.flowVelocity = velocityMs;
  }

  /**
   * Sets the pipe internal diameter.
   *
   * @param diameterM internal diameter in metres
   */
  public void setPipeDiameter(double diameterM) {
    this.pipeDiameter = diameterM;
  }

  /**
   * Sets the chemical inhibitor efficiency.
   *
   * @param efficiency inhibitor efficiency (0.0 = none, 1.0 = perfect)
   */
  public void setInhibitorEfficiency(double efficiency) {
    this.inhibitorEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
  }

  /**
   * Sets the glycol weight fraction in the aqueous phase.
   *
   * @param fraction glycol weight fraction (0 to 1)
   */
  public void setGlycolFraction(double fraction) {
    this.glycolFraction = Math.max(0.0, Math.min(1.0, fraction));
  }

  /**
   * Runs the coupled analysis: electrolyte CPA flash followed by corrosion rate calculation.
   *
   * <p>
   * This method:
   * </p>
   * <ol>
   * <li>Creates a {@link SystemElectrolyteCPAstatoil} with the specified composition.</li>
   * <li>Runs a TP flash with chemical reaction equilibrium to get aqueous speciation.</li>
   * <li>Extracts pH from the aqueous phase using {@code getpH()}.</li>
   * <li>Extracts CO2 and H2S partial pressures from the gas/dense phase.</li>
   * <li>Configures the {@link DeWaardMilliamsCorrosion} model with rigorous pH and pCO2.</li>
   * <li>Calculates the corrected corrosion rate.</li>
   * </ol>
   */
  public void run() {
    // Build the electrolyte CPA system
    double TK = temperatureC + 273.15;
    thermoSystem = new SystemElectrolyteCPAstatoil(TK, pressureBara);

    // Add components — order matters for proper initialization
    if (co2MoleFraction > 0) {
      thermoSystem.addComponent("CO2", co2MoleFraction);
    }
    if (waterMoleFraction > 0) {
      thermoSystem.addComponent("water", waterMoleFraction);
    }
    if (methaneMoleFraction > 0) {
      thermoSystem.addComponent("methane", methaneMoleFraction);
    }
    if (h2sMoleFraction > 0) {
      thermoSystem.addComponent("H2S", h2sMoleFraction);
    }
    if (n2MoleFraction > 0) {
      thermoSystem.addComponent("nitrogen", n2MoleFraction);
    }

    // Add ions for brine
    if (naPlusMoleFraction > 0) {
      thermoSystem.addComponent("Na+", naPlusMoleFraction);
    }
    if (clMinusMoleFraction > 0) {
      thermoSystem.addComponent("Cl-", clMinusMoleFraction);
    }

    // Initialize chemical reaction equilibrium — auto-adds ionic species
    // (H3O+, OH-, HCO3-, CO3--) from database reactions.
    // Only meaningful when both CO2 and water are present.
    if (waterMoleFraction > 0 && co2MoleFraction > 0) {
      thermoSystem.chemicalReactionInit();
    }
    thermoSystem.createDatabase(true);
    thermoSystem.setMixingRule(10);
    thermoSystem.setMultiPhaseCheck(true);

    // Run TP flash with chemical reaction equilibrium
    thermoSystem.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    ops.TPflash();
    thermoSystem.initProperties();

    numberOfPhases = thermoSystem.getNumberOfPhases();

    // Check for aqueous phase and extract pH
    freeWaterPresent = thermoSystem.hasPhaseType(PhaseType.AQUEOUS);

    if (freeWaterPresent) {
      aqueousPH = thermoSystem.getPhase(PhaseType.AQUEOUS).getpH();

      // CO2 dissolved in aqueous phase
      if (thermoSystem.getPhase(PhaseType.AQUEOUS).hasComponent("CO2")) {
        co2InAqueous = thermoSystem.getPhase(PhaseType.AQUEOUS).getComponent("CO2").getx();
      } else {
        co2InAqueous = 0.0;
      }
    } else {
      aqueousPH = Double.NaN;
      co2InAqueous = 0.0;
    }

    // Extract CO2 partial pressure from non-aqueous phase
    co2PartialPressure = extractCO2PartialPressure();
    h2sPartialPressure = extractH2SPartialPressure();

    // Configure the de Waard-Milliams model
    corrosionModel.setTemperatureCelsius(temperatureC);
    corrosionModel.setCO2PartialPressure(co2PartialPressure);
    corrosionModel.setTotalPressure(pressureBara);
    corrosionModel.setFlowVelocity(flowVelocity);
    corrosionModel.setPipeDiameter(pipeDiameter);
    corrosionModel.setInhibitorEfficiency(inhibitorEfficiency);
    corrosionModel.setGlycolFraction(glycolFraction);

    if (h2sPartialPressure > 0) {
      corrosionModel.setH2SPartialPressure(h2sPartialPressure);
    }

    // Use rigorous pH from electrolyte CPA (key coupling point)
    if (freeWaterPresent && !Double.isNaN(aqueousPH)) {
      corrosionModel.setPH(aqueousPH);
    } else {
      // No free water — estimate pH from empirical correlation
      corrosionModel.setPH(estimateEmpricalPH());
    }

    baselineCorrosionRate = corrosionModel.calculateBaselineRate();
    corrosionRate = corrosionModel.calculateCorrosionRate();
    corrosionSeverity = corrosionModel.getCorrosionSeverity();

    // Configure scale prediction
    scaleCalculator.setTemperatureCelsius(temperatureC);
    scaleCalculator.setPressureBara(pressureBara);
    scaleCalculator.setCO2PartialPressure(co2PartialPressure);
    if (freeWaterPresent && !Double.isNaN(aqueousPH)) {
      scaleCalculator.setPH(aqueousPH);
    }

    hasRun = true;
  }

  /**
   * Extracts the CO2 partial pressure from the gas or dense CO2 phase.
   *
   * @return CO2 partial pressure in bar
   */
  private double extractCO2PartialPressure() {
    // Try gas phase first
    if (thermoSystem.hasPhaseType(PhaseType.GAS)) {
      double co2InGas = thermoSystem.getPhase(PhaseType.GAS).getComponent("CO2").getx();
      return co2InGas * pressureBara;
    }
    // If no gas phase, use the non-aqueous liquid phase (dense CO2)
    for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
      String phaseType = thermoSystem.getPhase(i).getPhaseTypeName();
      if (!phaseType.equals("aqueous")) {
        double co2Frac = thermoSystem.getPhase(i).getComponent("CO2").getx();
        // For dense CO2, the fugacity-based partial pressure is more appropriate
        // but for the empirical de Waard model, we use pCO2 = xCO2 * P
        return co2Frac * pressureBara;
      }
    }
    return co2MoleFraction * pressureBara;
  }

  /**
   * Extracts the H2S partial pressure from the gas phase.
   *
   * @return H2S partial pressure in bar, or 0 if no H2S
   */
  private double extractH2SPartialPressure() {
    if (h2sMoleFraction <= 0) {
      return 0.0;
    }
    if (thermoSystem.hasPhaseType(PhaseType.GAS)) {
      if (thermoSystem.getPhase(PhaseType.GAS).hasComponent("H2S")) {
        double h2sInGas = thermoSystem.getPhase(PhaseType.GAS).getComponent("H2S").getx();
        return h2sInGas * pressureBara;
      }
    }
    return h2sMoleFraction * pressureBara;
  }

  /**
   * Estimates pH empirically when no rigorous electrolyte flash is possible.
   *
   * <p>
   * Uses the simplified NORSOK M-506 approach based on CO2 partial pressure and temperature.
   * </p>
   *
   * @return estimated pH
   */
  private double estimateEmpricalPH() {
    double pCO2 = co2MoleFraction * pressureBara;
    if (pCO2 <= 0) {
      return 7.0;
    }
    // Simplified Oddo-Tomson (referenced in NORSOK M-506)
    // pH ~ 3.71 + 0.5 * log10(T_K) - 0.5 * log10(pCO2)
    double TK = temperatureC + 273.15;
    return 3.71 + 0.5 * Math.log10(TK) - 0.5 * Math.log10(pCO2);
  }

  /**
   * Runs the analysis over a range of temperatures at constant pressure and composition.
   *
   * @param minTempC minimum temperature in Celsius
   * @param maxTempC maximum temperature in Celsius
   * @param steps number of temperature steps
   * @return list of result maps for each temperature point
   */
  public List<Map<String, Object>> runTemperatureSweep(double minTempC, double maxTempC,
      int steps) {
    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
    double savedTemp = this.temperatureC;
    double dT = (maxTempC - minTempC) / Math.max(steps, 1);

    for (int i = 0; i <= steps; i++) {
      double T = minTempC + i * dT;
      this.temperatureC = T;
      this.hasRun = false;
      run();

      Map<String, Object> point = new LinkedHashMap<String, Object>();
      point.put("temperature_C", T);
      point.put("pH", aqueousPH);
      point.put("co2PartialPressure_bar", co2PartialPressure);
      point.put("corrosionRate_mmyr", corrosionRate);
      point.put("baselineRate_mmyr", baselineCorrosionRate);
      point.put("severity", corrosionSeverity);
      point.put("freeWaterPresent", freeWaterPresent);
      results.add(point);
    }

    this.temperatureC = savedTemp;
    this.hasRun = false;
    return results;
  }

  /**
   * Runs the analysis over a range of pressures at constant temperature and composition.
   *
   * @param minPressBar minimum pressure in bara
   * @param maxPressBar maximum pressure in bara
   * @param steps number of pressure steps
   * @return list of result maps for each pressure point
   */
  public List<Map<String, Object>> runPressureSweep(double minPressBar, double maxPressBar,
      int steps) {
    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
    double savedPres = this.pressureBara;
    double dP = (maxPressBar - minPressBar) / Math.max(steps, 1);

    for (int i = 0; i <= steps; i++) {
      double P = minPressBar + i * dP;
      this.pressureBara = P;
      this.hasRun = false;
      run();

      Map<String, Object> point = new LinkedHashMap<String, Object>();
      point.put("pressure_bara", P);
      point.put("pH", aqueousPH);
      point.put("co2PartialPressure_bar", co2PartialPressure);
      point.put("corrosionRate_mmyr", corrosionRate);
      point.put("baselineRate_mmyr", baselineCorrosionRate);
      point.put("severity", corrosionSeverity);
      point.put("freeWaterPresent", freeWaterPresent);
      results.add(point);
    }

    this.pressureBara = savedPres;
    this.hasRun = false;
    return results;
  }

  // --- Getters ---

  /**
   * Returns the aqueous phase pH from the electrolyte CPA flash.
   *
   * @return pH value, or NaN if no free water is present or analysis not run
   */
  public double getAqueousPH() {
    return aqueousPH;
  }

  /**
   * Returns the CO2 partial pressure from the flash.
   *
   * @return CO2 partial pressure in bar
   */
  public double getCO2PartialPressure() {
    return co2PartialPressure;
  }

  /**
   * Returns the H2S partial pressure from the flash.
   *
   * @return H2S partial pressure in bar
   */
  public double getH2SPartialPressure() {
    return h2sPartialPressure;
  }

  /**
   * Returns whether free water was detected in the flash.
   *
   * @return true if an aqueous phase is present
   */
  public boolean isFreeWaterPresent() {
    return freeWaterPresent;
  }

  /**
   * Returns the corrected corrosion rate.
   *
   * @return corrosion rate in mm/yr
   */
  public double getCorrosionRate() {
    return corrosionRate;
  }

  /**
   * Returns the baseline (uncorrected) corrosion rate.
   *
   * @return baseline rate in mm/yr
   */
  public double getBaselineCorrosionRate() {
    return baselineCorrosionRate;
  }

  /**
   * Returns the corrosion severity category per NORSOK M-001.
   *
   * @return severity string: Low, Medium, High, or Very High
   */
  public String getCorrosionSeverity() {
    return corrosionSeverity;
  }

  /**
   * Returns the CO2 mole fraction dissolved in the aqueous phase.
   *
   * @return CO2 mole fraction in aqueous phase
   */
  public double getCO2InAqueous() {
    return co2InAqueous;
  }

  /**
   * Returns the number of phases from the flash.
   *
   * @return number of phases
   */
  public int getNumberOfPhases() {
    return numberOfPhases;
  }

  /**
   * Returns the underlying de Waard-Milliams corrosion model.
   *
   * @return the corrosion model
   */
  public DeWaardMilliamsCorrosion getCorrosionModel() {
    return corrosionModel;
  }

  /**
   * Returns the underlying scale prediction calculator.
   *
   * @return the scale calculator
   */
  public ScalePredictionCalculator getScaleCalculator() {
    return scaleCalculator;
  }

  /**
   * Returns the thermodynamic system used for the flash. May be null if not run yet.
   *
   * @return the thermodynamic system, or null
   */
  public SystemInterface getThermoSystem() {
    return thermoSystem;
  }

  /**
   * Estimates the required corrosion allowance for a given design life.
   *
   * @param designLifeYears pipeline design life in years
   * @return corrosion allowance in mm
   */
  public double estimateCorrosionAllowance(double designLifeYears) {
    return corrosionRate * designLifeYears;
  }

  /**
   * Returns a comprehensive JSON report of the analysis.
   *
   * @return JSON string with all input, flash results, and corrosion predictions
   */
  public String toJson() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    // Input conditions
    Map<String, Object> input = new LinkedHashMap<String, Object>();
    input.put("temperature_C", temperatureC);
    input.put("pressure_bara", pressureBara);
    input.put("co2MoleFraction", co2MoleFraction);
    input.put("waterMoleFraction", waterMoleFraction);
    input.put("methaneMoleFraction", methaneMoleFraction);
    input.put("h2sMoleFraction", h2sMoleFraction);
    input.put("n2MoleFraction", n2MoleFraction);
    input.put("naPlusMoleFraction", naPlusMoleFraction);
    input.put("clMinusMoleFraction", clMinusMoleFraction);
    input.put("flowVelocity_ms", flowVelocity);
    input.put("pipeDiameter_m", pipeDiameter);
    input.put("inhibitorEfficiency", inhibitorEfficiency);
    input.put("glycolFraction", glycolFraction);
    result.put("input", input);

    // Flash results
    Map<String, Object> flash = new LinkedHashMap<String, Object>();
    flash.put("numberOfPhases", numberOfPhases);
    flash.put("freeWaterPresent", freeWaterPresent);
    flash.put("aqueousPH", aqueousPH);
    flash.put("co2PartialPressure_bar", co2PartialPressure);
    flash.put("h2sPartialPressure_bar", h2sPartialPressure);
    flash.put("co2MoleFractionInAqueous", co2InAqueous);
    flash.put("pHMethod", freeWaterPresent ? "Electrolyte CPA (rigorous)" : "Empirical (no water)");
    result.put("flashResults", flash);

    // Corrosion results
    Map<String, Object> corrosion = new LinkedHashMap<String, Object>();
    corrosion.put("baselineRate_mmyr", baselineCorrosionRate);
    corrosion.put("correctedRate_mmyr", corrosionRate);
    corrosion.put("severity", corrosionSeverity);
    corrosion.put("corrosionAllowance_25yr_mm", estimateCorrosionAllowance(25.0));
    corrosion.put("isSourService", corrosionModel.isSourService());
    corrosion.put("pHCorrectionFactor", corrosionModel.getPHCorrectionFactor());
    corrosion.put("scaleCorrectionFactor", corrosionModel.getScaleCorrectionFactor());
    corrosion.put("flowCorrectionFactor", corrosionModel.getFlowCorrectionFactor());
    corrosion.put("glycolCorrectionFactor", corrosionModel.getGlycolCorrectionFactor());
    corrosion.put("feCO3SaturationIndex", corrosionModel.getFeCO3SaturationIndex());
    corrosion.put("feCO3ScaleForming", corrosionModel.getFeCO3SaturationIndex() > 1.0);
    result.put("corrosionResults", corrosion);

    // Model info
    Map<String, Object> model = new LinkedHashMap<String, Object>();
    model.put("thermodynamicModel", "Electrolyte CPA (SystemElectrolyteCPAstatoil)");
    model.put("corrosionModel", "de Waard-Milliams (1991)");
    model.put("applicableStandards", "NORSOK M-506, NACE SP0775");
    model.put("pHCalculation",
        "Rigorous from H3O+ activity in aqueous phase (electrolyte CPA EOS)");
    result.put("modelInfo", model);

    Gson gson =
        new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(result);
  }
}
