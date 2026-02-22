package neqsim.process.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos1978;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Compares thermodynamic property predictions between two equations of state.
 *
 * <p>
 * Per TR1244 best practice, process design should cross-check results between SRK and PR78
 * equations of state to identify cases where EoS choice significantly affects separation
 * predictions, compressor duties, phase envelopes or hydrate curves. This class runs both EoS on
 * the same composition and conditions and flags deviations above configurable thresholds.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * SystemInterface baseFluid = new SystemSrkEos(273.15 + 25, 60.0);
 * baseFluid.addComponent("methane", 0.85);
 * baseFluid.addComponent("ethane", 0.10);
 * baseFluid.addComponent("propane", 0.05);
 * baseFluid.setMixingRule("classic");
 *
 * DualEosComparison comp = new DualEosComparison(baseFluid);
 * comp.addCondition(273.15 + 25, 60.0);
 * comp.addCondition(273.15 + 5, 30.0);
 * comp.run();
 * System.out.println(comp.toJson());
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class DualEosComparison implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Component names. */
  private String[] componentNames;

  /** Component molar compositions. */
  private double[] molarComposition;

  /** Mixing rule string. */
  private String mixingRule = "classic";

  /** Conditions to evaluate: each entry is {T_kelvin, P_bara}. */
  private List<double[]> conditions = new ArrayList<double[]>();

  /** Results for each condition. */
  private List<ComparisonResult> results = new ArrayList<ComparisonResult>();

  /** Deviation threshold for flagging (fraction, e.g., 0.05 = 5%). */
  private double deviationThreshold = 0.05;

  /** Name of primary EoS. */
  private String primaryEosName = "SRK";

  /** Name of secondary EoS. */
  private String secondaryEosName = "PR78";

  /**
   * Creates a DualEosComparison from a base fluid system.
   *
   * <p>
   * The composition is extracted from the supplied system. Both SRK and PR78 fluids will be built
   * internally with the same composition. The base fluid's EoS type does not matter.
   * </p>
   *
   * @param baseFluid the base fluid to extract composition from
   */
  public DualEosComparison(SystemInterface baseFluid) {
    this.componentNames = baseFluid.getComponentNames();
    this.molarComposition = baseFluid.getMolarComposition();
    if (baseFluid.getMixingRuleName() != null && !baseFluid.getMixingRuleName().trim().isEmpty()) {
      this.mixingRule = baseFluid.getMixingRuleName();
    }
  }

  /**
   * Creates a DualEosComparison from explicit composition.
   *
   * @param componentNames array of component names
   * @param molarComposition array of molar fractions (must sum to ~1.0)
   * @param mixingRule mixing rule name (e.g., "classic")
   */
  public DualEosComparison(String[] componentNames, double[] molarComposition, String mixingRule) {
    this.componentNames = componentNames;
    this.molarComposition = molarComposition;
    this.mixingRule = mixingRule;
  }

  /**
   * Sets the deviation threshold for flagging differences.
   *
   * @param threshold fractional deviation threshold (e.g., 0.05 = 5%)
   */
  public void setDeviationThreshold(double threshold) {
    this.deviationThreshold = threshold;
  }

  /**
   * Returns the deviation threshold.
   *
   * @return fractional deviation threshold
   */
  public double getDeviationThreshold() {
    return deviationThreshold;
  }

  /**
   * Adds a temperature-pressure condition to evaluate.
   *
   * @param temperatureKelvin temperature in Kelvin
   * @param pressureBara pressure in bara
   */
  public void addCondition(double temperatureKelvin, double pressureBara) {
    conditions.add(new double[] {temperatureKelvin, pressureBara});
  }

  /**
   * Adds a temperature-pressure condition using Celsius.
   *
   * @param temperatureCelsius temperature in Celsius
   * @param pressureBara pressure in bara
   */
  public void addConditionCelsius(double temperatureCelsius, double pressureBara) {
    conditions.add(new double[] {temperatureCelsius + 273.15, pressureBara});
  }

  /**
   * Runs the dual-EoS comparison at all configured conditions.
   */
  public void run() {
    results.clear();

    for (double[] cond : conditions) {
      double T = cond[0];
      double P = cond[1];

      // Build SRK fluid
      SystemInterface srkFluid = new SystemSrkEos(T, P);
      for (int i = 0; i < componentNames.length; i++) {
        srkFluid.addComponent(componentNames[i], molarComposition[i]);
      }
      srkFluid.setMixingRule(mixingRule);

      // Build PR78 fluid
      SystemInterface pr78Fluid = new SystemPrEos1978(T, P);
      for (int i = 0; i < componentNames.length; i++) {
        pr78Fluid.addComponent(componentNames[i], molarComposition[i]);
      }
      pr78Fluid.setMixingRule(mixingRule);

      // TPflash both
      ThermodynamicOperations srkOps = new ThermodynamicOperations(srkFluid);
      srkOps.TPflash();
      srkFluid.initProperties();

      ThermodynamicOperations pr78Ops = new ThermodynamicOperations(pr78Fluid);
      pr78Ops.TPflash();
      pr78Fluid.initProperties();

      // Collect results
      ComparisonResult cr = new ComparisonResult();
      cr.temperatureK = T;
      cr.pressureBara = P;

      // Number of phases
      cr.srkNumPhases = srkFluid.getNumberOfPhases();
      cr.pr78NumPhases = pr78Fluid.getNumberOfPhases();

      // Overall density
      cr.srkDensity = srkFluid.getDensity("kg/m3");
      cr.pr78Density = pr78Fluid.getDensity("kg/m3");

      // Z factor (gas phase if present)
      if (srkFluid.hasPhaseType("gas") && pr78Fluid.hasPhaseType("gas")) {
        cr.srkGasZ = srkFluid.getPhaseOfType("gas").getZ();
        cr.pr78GasZ = pr78Fluid.getPhaseOfType("gas").getZ();
        cr.srkGasDensity = srkFluid.getPhaseOfType("gas").getDensity("kg/m3");
        cr.pr78GasDensity = pr78Fluid.getPhaseOfType("gas").getDensity("kg/m3");
        cr.srkGasMW = srkFluid.getPhaseOfType("gas").getMolarMass() * 1000.0;
        cr.pr78GasMW = pr78Fluid.getPhaseOfType("gas").getMolarMass() * 1000.0;
        cr.srkGasViscosity = srkFluid.getPhaseOfType("gas").getViscosity("cP");
        cr.pr78GasViscosity = pr78Fluid.getPhaseOfType("gas").getViscosity("cP");
      }

      // Liquid phase
      if (srkFluid.hasPhaseType("oil") && pr78Fluid.hasPhaseType("oil")) {
        cr.srkLiqDensity = srkFluid.getPhaseOfType("oil").getDensity("kg/m3");
        cr.pr78LiqDensity = pr78Fluid.getPhaseOfType("oil").getDensity("kg/m3");
        cr.srkLiqViscosity = srkFluid.getPhaseOfType("oil").getViscosity("cP");
        cr.pr78LiqViscosity = pr78Fluid.getPhaseOfType("oil").getViscosity("cP");
      }

      // Gas mole fraction
      cr.srkGasFraction = srkFluid.getBeta();
      cr.pr78GasFraction = pr78Fluid.getBeta();

      // Enthalpy
      cr.srkEnthalpy = srkFluid.getEnthalpy("kJ/mol");
      cr.pr78Enthalpy = pr78Fluid.getEnthalpy("kJ/mol");

      // Cp
      cr.srkCp = srkFluid.getCp("kJ/kgK");
      cr.pr78Cp = pr78Fluid.getCp("kJ/kgK");

      // Flag deviations
      cr.flagDeviations(deviationThreshold);

      results.add(cr);
    }
  }

  /**
   * Returns the comparison results.
   *
   * @return list of comparison results
   */
  public List<ComparisonResult> getResults() {
    return results;
  }

  /**
   * Returns whether any result has flagged deviations above the threshold.
   *
   * @return true if significant deviations exist
   */
  public boolean hasSignificantDeviations() {
    for (ComparisonResult cr : results) {
      if (!cr.flags.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a summary of all flagged deviations.
   *
   * @return list of flag strings
   */
  public List<String> getAllFlags() {
    List<String> allFlags = new ArrayList<String>();
    for (ComparisonResult cr : results) {
      for (String flag : cr.flags) {
        allFlags
            .add(String.format("T=%.1fK P=%.1fbar: %s", cr.temperatureK, cr.pressureBara, flag));
      }
    }
    return allFlags;
  }

  /**
   * Returns JSON representation of all results.
   *
   * @return JSON string with comparison data
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("primaryEos", primaryEosName);
    root.addProperty("secondaryEos", secondaryEosName);
    root.addProperty("deviationThreshold", deviationThreshold);
    root.addProperty("hasSignificantDeviations", hasSignificantDeviations());
    root.addProperty("mixingRule", mixingRule);

    // Composition
    JsonArray comp = new JsonArray();
    for (int i = 0; i < componentNames.length; i++) {
      JsonObject c = new JsonObject();
      c.addProperty("component", componentNames[i]);
      c.addProperty("moleFraction", molarComposition[i]);
      comp.add(c);
    }
    root.add("composition", comp);

    // Results
    JsonArray resArray = new JsonArray();
    for (ComparisonResult cr : results) {
      resArray.add(cr.toJson());
    }
    root.add("results", resArray);

    Gson gson =
        new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(root);
  }

  /**
   * Holds comparison results for a single T-P condition.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class ComparisonResult implements Serializable {

    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Temperature in Kelvin. */
    public double temperatureK;

    /** Pressure in bara. */
    public double pressureBara;

    /** SRK number of phases. */
    public int srkNumPhases;

    /** PR78 number of phases. */
    public int pr78NumPhases;

    /** SRK overall density kg/m3. */
    public double srkDensity;

    /** PR78 overall density kg/m3. */
    public double pr78Density;

    /** SRK gas compressibility factor. */
    public double srkGasZ = Double.NaN;

    /** PR78 gas compressibility factor. */
    public double pr78GasZ = Double.NaN;

    /** SRK gas density kg/m3. */
    public double srkGasDensity = Double.NaN;

    /** PR78 gas density kg/m3. */
    public double pr78GasDensity = Double.NaN;

    /** SRK gas molecular weight g/mol. */
    public double srkGasMW = Double.NaN;

    /** PR78 gas molecular weight g/mol. */
    public double pr78GasMW = Double.NaN;

    /** SRK gas viscosity cP. */
    public double srkGasViscosity = Double.NaN;

    /** PR78 gas viscosity cP. */
    public double pr78GasViscosity = Double.NaN;

    /** SRK liquid density kg/m3. */
    public double srkLiqDensity = Double.NaN;

    /** PR78 liquid density kg/m3. */
    public double pr78LiqDensity = Double.NaN;

    /** SRK liquid viscosity cP. */
    public double srkLiqViscosity = Double.NaN;

    /** PR78 liquid viscosity cP. */
    public double pr78LiqViscosity = Double.NaN;

    /** SRK gas mole fraction. */
    public double srkGasFraction = Double.NaN;

    /** PR78 gas mole fraction. */
    public double pr78GasFraction = Double.NaN;

    /** SRK enthalpy kJ/mol. */
    public double srkEnthalpy = Double.NaN;

    /** PR78 enthalpy kJ/mol. */
    public double pr78Enthalpy = Double.NaN;

    /** SRK Cp kJ/kgK. */
    public double srkCp = Double.NaN;

    /** PR78 Cp kJ/kgK. */
    public double pr78Cp = Double.NaN;

    /** Deviation flags. */
    public List<String> flags = new ArrayList<String>();

    /**
     * Checks all properties for deviations above threshold and populates flags.
     *
     * @param threshold fractional deviation threshold
     */
    public void flagDeviations(double threshold) {
      flags.clear();

      if (srkNumPhases != pr78NumPhases) {
        flags.add(
            String.format("Phase count mismatch: SRK=%d PR78=%d", srkNumPhases, pr78NumPhases));
      }

      checkDeviation("Density", srkDensity, pr78Density, "kg/m3", threshold);
      checkDeviation("Gas Z-factor", srkGasZ, pr78GasZ, "", threshold);
      checkDeviation("Gas density", srkGasDensity, pr78GasDensity, "kg/m3", threshold);
      checkDeviation("Gas viscosity", srkGasViscosity, pr78GasViscosity, "cP", threshold);
      checkDeviation("Liquid density", srkLiqDensity, pr78LiqDensity, "kg/m3", threshold);
      checkDeviation("Liquid viscosity", srkLiqViscosity, pr78LiqViscosity, "cP", threshold);
      checkDeviation("Gas fraction", srkGasFraction, pr78GasFraction, "", threshold);
      checkDeviation("Enthalpy", srkEnthalpy, pr78Enthalpy, "kJ/mol", threshold);
      checkDeviation("Cp", srkCp, pr78Cp, "kJ/kgK", threshold);
    }

    /**
     * Checks a single property pair for deviation.
     *
     * @param name property name
     * @param srkVal SRK value
     * @param pr78Val PR78 value
     * @param unit unit string
     * @param threshold fractional deviation threshold
     */
    private void checkDeviation(String name, double srkVal, double pr78Val, String unit,
        double threshold) {
      if (Double.isNaN(srkVal) || Double.isNaN(pr78Val)) {
        return;
      }
      double avg = (Math.abs(srkVal) + Math.abs(pr78Val)) / 2.0;
      if (avg < 1e-10) {
        return;
      }
      double dev = Math.abs(srkVal - pr78Val) / avg;
      if (dev > threshold) {
        flags.add(String.format("%s deviation %.1f%%: SRK=%.4g PR78=%.4g %s", name, dev * 100.0,
            srkVal, pr78Val, unit));
      }
    }

    /**
     * Returns JSON representation of this result.
     *
     * @return JSON object
     */
    public JsonObject toJson() {
      JsonObject obj = new JsonObject();
      obj.addProperty("temperature_K", temperatureK);
      obj.addProperty("temperature_C", temperatureK - 273.15);
      obj.addProperty("pressure_bara", pressureBara);

      // Phase count
      obj.addProperty("srkPhases", srkNumPhases);
      obj.addProperty("pr78Phases", pr78NumPhases);

      // Overall properties
      JsonObject overall = new JsonObject();
      addPair(overall, "density_kgm3", srkDensity, pr78Density);
      addPair(overall, "gasFraction", srkGasFraction, pr78GasFraction);
      addPair(overall, "enthalpy_kJmol", srkEnthalpy, pr78Enthalpy);
      addPair(overall, "Cp_kJkgK", srkCp, pr78Cp);
      obj.add("overall", overall);

      // Gas phase
      JsonObject gas = new JsonObject();
      addPair(gas, "Z", srkGasZ, pr78GasZ);
      addPair(gas, "density_kgm3", srkGasDensity, pr78GasDensity);
      addPair(gas, "MW_gmol", srkGasMW, pr78GasMW);
      addPair(gas, "viscosity_cP", srkGasViscosity, pr78GasViscosity);
      obj.add("gasPhase", gas);

      // Liquid phase
      JsonObject liq = new JsonObject();
      addPair(liq, "density_kgm3", srkLiqDensity, pr78LiqDensity);
      addPair(liq, "viscosity_cP", srkLiqViscosity, pr78LiqViscosity);
      obj.add("liquidPhase", liq);

      // Flags
      JsonArray flagArr = new JsonArray();
      for (String f : flags) {
        flagArr.add(f);
      }
      obj.add("deviationFlags", flagArr);

      return obj;
    }

    /**
     * Adds an SRK/PR78/deviation triplet to a JSON object.
     *
     * @param parent parent JSON object
     * @param name property name
     * @param srkVal SRK value
     * @param pr78Val PR78 value
     */
    private void addPair(JsonObject parent, String name, double srkVal, double pr78Val) {
      JsonObject pair = new JsonObject();
      pair.addProperty("SRK", srkVal);
      pair.addProperty("PR78", pr78Val);
      if (!Double.isNaN(srkVal) && !Double.isNaN(pr78Val)) {
        double avg = (Math.abs(srkVal) + Math.abs(pr78Val)) / 2.0;
        if (avg > 1e-10) {
          pair.addProperty("deviation_pct",
              Math.round(Math.abs(srkVal - pr78Val) / avg * 1000.0) / 10.0);
        }
      }
      parent.add(name, pair);
    }
  }
}
