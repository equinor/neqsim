package neqsim.integration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemEos;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Side-by-side comparison of thermodynamic properties across multiple EOS models.
 *
 * <p>
 * Evaluates a fluid composition at specified conditions using multiple equations of state, computes
 * property deviations, and reports results as JSON. Useful for model selection, sensitivity
 * studies, and validating that EOS choice does not significantly affect results.
 * </p>
 *
 * <h2>Usage:</h2>
 *
 * <pre>
 * EOSComparison comp = new EOSComparison();
 * comp.addComponent("methane", 0.85);
 * comp.addComponent("ethane", 0.10);
 * comp.addComponent("propane", 0.05);
 * comp.setConditions(273.15 + 25.0, 60.0);
 *
 * ComparisonResult result = comp.compare();
 * System.out.println(result.toJson());
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class EOSComparison implements Serializable {

  /** Serialization version. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(EOSComparison.class);

  /** Components and their mole fractions. */
  private final Map<String, Double> components = new LinkedHashMap<>();

  /** Temperature in Kelvin. */
  private double temperature = 298.15;

  /** Pressure in bara. */
  private double pressure = 1.01325;

  /** EOS types to compare. */
  private List<EOSType> eosTypes =
      new ArrayList<>(Arrays.asList(EOSType.SRK, EOSType.PR, EOSType.SRK_CPA));

  /** Whether to include multi-phase check. */
  private boolean multiPhaseCheck = false;

  /**
   * Supported equation of state types.
   */
  public enum EOSType {
    /** Soave-Redlich-Kwong. */
    SRK("SRK"),
    /** Peng-Robinson. */
    PR("PR"),
    /** SRK with CPA (Cubic Plus Association). */
    SRK_CPA("SRK-CPA"),
    /** GERG-2008 multi-parameter EOS. */
    GERG2008("GERG-2008");

    private final String label;

    /**
     * Creates an EOS type.
     *
     * @param label display label
     */
    EOSType(String label) {
      this.label = label;
    }

    /**
     * Gets the display label.
     *
     * @return label string
     */
    public String getLabel() {
      return label;
    }
  }

  /**
   * Creates a new EOS comparison utility.
   */
  public EOSComparison() {}

  /**
   * Adds a component with its mole fraction.
   *
   * @param name component name (e.g., "methane")
   * @param moleFraction mole fraction (0-1)
   * @return this for chaining
   */
  public EOSComparison addComponent(String name, double moleFraction) {
    components.put(name, moleFraction);
    return this;
  }

  /**
   * Sets the temperature and pressure conditions.
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return this for chaining
   */
  public EOSComparison setConditions(double temperatureK, double pressureBara) {
    this.temperature = temperatureK;
    this.pressure = pressureBara;
    return this;
  }

  /**
   * Sets which EOS types to compare.
   *
   * @param types EOS types to include
   * @return this for chaining
   */
  public EOSComparison setEOSTypes(EOSType... types) {
    this.eosTypes = Arrays.asList(types);
    return this;
  }

  /**
   * Enables multi-phase check for all models.
   *
   * @param enable true to enable multi-phase check
   * @return this for chaining
   */
  public EOSComparison setMultiPhaseCheck(boolean enable) {
    this.multiPhaseCheck = enable;
    return this;
  }

  /**
   * Runs the comparison across all configured EOS types.
   *
   * @return comparison results for all models
   */
  public ComparisonResult compare() {
    if (components.isEmpty()) {
      throw new IllegalStateException("No components added. Call addComponent() first.");
    }

    List<EOSResult> results = new ArrayList<>();

    for (EOSType type : eosTypes) {
      try {
        EOSResult result = evaluateEOS(type);
        results.add(result);
      } catch (Exception e) {
        logger.warn("EOS {} failed: {}", type.getLabel(), e.getMessage());
        results.add(new EOSResult(type, e.getMessage()));
      }
    }

    return new ComparisonResult(temperature, pressure, components, results);
  }

  /**
   * Evaluates a single EOS type.
   *
   * @param type the EOS type to evaluate
   * @return property results for this EOS
   * @throws Exception if the evaluation fails
   */
  private EOSResult evaluateEOS(EOSType type) throws Exception {
    SystemInterface system = createSystem(type);

    for (Map.Entry<String, Double> entry : components.entrySet()) {
      system.addComponent(entry.getKey(), entry.getValue());
    }

    if (type == EOSType.SRK_CPA) {
      system.setMixingRule(10);
    } else if (type != EOSType.GERG2008) {
      system.setMixingRule("classic");
    }

    if (multiPhaseCheck) {
      system.setMultiPhaseCheck(true);
    }

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    EOSResult result = new EOSResult(type);
    result.numberOfPhases = system.getNumberOfPhases();
    result.compressibilityFactor = system.getZ();
    result.density_kgm3 = system.getDensity("kg/m3");
    result.molarMass_kgmol = system.getMolarMass("kg/mol");
    result.enthalpy_Jmol = system.getEnthalpy("J/mol");
    result.entropy_JmolK = system.getEntropy("J/molK");

    if (system.hasPhaseType("gas")) {
      result.gasDensity_kgm3 = system.getPhase("gas").getDensity("kg/m3");
      result.gasViscosity_cP = system.getPhase("gas").getViscosity("kg/msec") * 1000.0;
      result.gasZfactor = system.getPhase("gas").getZ();
      result.gasCp_JmolK = system.getPhase("gas").getCp("J/molK");
    }

    if (system.hasPhaseType("oil")) {
      result.oilDensity_kgm3 = system.getPhase("oil").getDensity("kg/m3");
      result.oilViscosity_cP = system.getPhase("oil").getViscosity("kg/msec") * 1000.0;
    }

    result.vapourFraction = system.getBeta(0);

    return result;
  }

  /**
   * Creates a SystemInterface for the specified EOS type.
   *
   * @param type the EOS type
   * @return a new SystemInterface instance
   */
  private SystemInterface createSystem(EOSType type) {
    switch (type) {
      case PR:
        return new SystemPrEos(temperature, pressure);
      case SRK_CPA:
        return new SystemSrkCPAstatoil(temperature, pressure);
      case GERG2008:
        return new SystemGERG2008Eos(temperature, pressure);
      case SRK:
      default:
        return new SystemSrkEos(temperature, pressure);
    }
  }

  // ============================================================================
  // Result classes
  // ============================================================================

  /**
   * Results for a single EOS evaluation.
   */
  public static class EOSResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** EOS type used. */
    public final EOSType eosType;
    /** Error message if evaluation failed. */
    public final String error;
    /** Number of phases found. */
    public int numberOfPhases;
    /** Compressibility factor. */
    public double compressibilityFactor = Double.NaN;
    /** Mixture density in kg/m3. */
    public double density_kgm3 = Double.NaN;
    /** Molar mass in kg/mol. */
    public double molarMass_kgmol = Double.NaN;
    /** Molar enthalpy in J/mol. */
    public double enthalpy_Jmol = Double.NaN;
    /** Molar entropy in J/(mol*K). */
    public double entropy_JmolK = Double.NaN;
    /** Gas phase density in kg/m3. */
    public double gasDensity_kgm3 = Double.NaN;
    /** Gas viscosity in cP. */
    public double gasViscosity_cP = Double.NaN;
    /** Gas compressibility factor. */
    public double gasZfactor = Double.NaN;
    /** Gas heat capacity at constant pressure in J/(mol*K). */
    public double gasCp_JmolK = Double.NaN;
    /** Oil phase density in kg/m3. */
    public double oilDensity_kgm3 = Double.NaN;
    /** Oil viscosity in cP. */
    public double oilViscosity_cP = Double.NaN;
    /** Vapour fraction (beta). */
    public double vapourFraction = Double.NaN;

    /**
     * Creates a successful result.
     *
     * @param eosType the EOS type
     */
    EOSResult(EOSType eosType) {
      this.eosType = eosType;
      this.error = null;
    }

    /**
     * Creates a failed result.
     *
     * @param eosType the EOS type
     * @param error the error message
     */
    EOSResult(EOSType eosType, String error) {
      this.eosType = eosType;
      this.error = error;
    }

    /**
     * Returns whether this result was successful.
     *
     * @return true if no error occurred
     */
    public boolean isSuccessful() {
      return error == null;
    }
  }

  /**
   * Comparative results across all EOS models.
   */
  public static class ComparisonResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double temperature;
    private final double pressure;
    private final Map<String, Double> components;
    private final List<EOSResult> results;

    /**
     * Creates a comparison result.
     *
     * @param temperature temperature in Kelvin
     * @param pressure pressure in bara
     * @param components component map
     * @param results list of per-EOS results
     */
    ComparisonResult(double temperature, double pressure, Map<String, Double> components,
        List<EOSResult> results) {
      this.temperature = temperature;
      this.pressure = pressure;
      this.components = components;
      this.results = results;
    }

    /**
     * Gets all EOS results.
     *
     * @return list of results
     */
    public List<EOSResult> getResults() {
      return results;
    }

    /**
     * Gets the result for a specific EOS type.
     *
     * @param type the EOS type
     * @return the result, or null if not found
     */
    public EOSResult getResult(EOSType type) {
      for (EOSResult r : results) {
        if (r.eosType == type) {
          return r;
        }
      }
      return null;
    }

    /**
     * Computes the maximum relative deviation (%) of a property across all successful models.
     *
     * @param propertyName one of: "density", "Z", "gasDensity", "gasViscosity"
     * @return max relative deviation in percent, or NaN if insufficient data
     */
    public double getMaxDeviation(String propertyName) {
      List<Double> values = new ArrayList<>();
      for (EOSResult r : results) {
        if (!r.isSuccessful()) {
          continue;
        }
        double val = getPropertyValue(r, propertyName);
        if (!Double.isNaN(val) && val != 0.0) {
          values.add(val);
        }
      }
      if (values.size() < 2) {
        return Double.NaN;
      }

      double mean = 0;
      for (double v : values) {
        mean += v;
      }
      mean /= values.size();

      double maxDev = 0;
      for (double v : values) {
        double dev = Math.abs((v - mean) / mean) * 100.0;
        if (dev > maxDev) {
          maxDev = dev;
        }
      }
      return maxDev;
    }

    /**
     * Gets a named property value from an EOS result.
     *
     * @param r the EOS result
     * @param name property name
     * @return property value, or NaN
     */
    private double getPropertyValue(EOSResult r, String name) {
      switch (name) {
        case "density":
          return r.density_kgm3;
        case "Z":
          return r.compressibilityFactor;
        case "gasDensity":
          return r.gasDensity_kgm3;
        case "gasViscosity":
          return r.gasViscosity_cP;
        case "gasZ":
          return r.gasZfactor;
        case "gasCp":
          return r.gasCp_JmolK;
        case "oilDensity":
          return r.oilDensity_kgm3;
        case "oilViscosity":
          return r.oilViscosity_cP;
        case "enthalpy":
          return r.enthalpy_Jmol;
        case "entropy":
          return r.entropy_JmolK;
        default:
          return Double.NaN;
      }
    }

    /**
     * Converts results to JSON format.
     *
     * @return JSON representation of the comparison
     */
    public String toJson() {
      JsonObject json = new JsonObject();

      // Conditions
      JsonObject conditions = new JsonObject();
      conditions.addProperty("temperature_K", temperature);
      conditions.addProperty("temperature_C", temperature - 273.15);
      conditions.addProperty("pressure_bara", pressure);
      json.add("conditions", conditions);

      // Composition
      JsonObject comp = new JsonObject();
      for (Map.Entry<String, Double> entry : components.entrySet()) {
        comp.addProperty(entry.getKey(), entry.getValue());
      }
      json.add("composition", comp);

      // Results per EOS
      JsonArray eosArray = new JsonArray();
      for (EOSResult r : results) {
        JsonObject eos = new JsonObject();
        eos.addProperty("eosType", r.eosType.getLabel());
        eos.addProperty("successful", r.isSuccessful());
        if (!r.isSuccessful()) {
          eos.addProperty("error", r.error);
        } else {
          eos.addProperty("numberOfPhases", r.numberOfPhases);
          eos.addProperty("compressibilityFactor", r.compressibilityFactor);
          eos.addProperty("density_kgm3", r.density_kgm3);
          eos.addProperty("molarMass_kgmol", r.molarMass_kgmol);
          eos.addProperty("enthalpy_Jmol", r.enthalpy_Jmol);
          eos.addProperty("entropy_JmolK", r.entropy_JmolK);
          eos.addProperty("vapourFraction", r.vapourFraction);
          if (!Double.isNaN(r.gasDensity_kgm3)) {
            eos.addProperty("gasDensity_kgm3", r.gasDensity_kgm3);
            eos.addProperty("gasViscosity_cP", r.gasViscosity_cP);
            eos.addProperty("gasZfactor", r.gasZfactor);
            eos.addProperty("gasCp_JmolK", r.gasCp_JmolK);
          }
          if (!Double.isNaN(r.oilDensity_kgm3)) {
            eos.addProperty("oilDensity_kgm3", r.oilDensity_kgm3);
            eos.addProperty("oilViscosity_cP", r.oilViscosity_cP);
          }
        }
        eosArray.add(eos);
      }
      json.add("eosResults", eosArray);

      // Deviations
      JsonObject deviations = new JsonObject();
      String[] props = {"density", "Z", "gasDensity", "gasViscosity", "gasZ", "gasCp", "oilDensity",
          "oilViscosity", "enthalpy", "entropy"};
      for (String prop : props) {
        double dev = getMaxDeviation(prop);
        if (!Double.isNaN(dev)) {
          deviations.addProperty(prop + "_maxDevPct", Math.round(dev * 100.0) / 100.0);
        }
      }
      json.add("maxDeviations_pct", deviations);

      return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
          .toJson(json);
    }
  }
}
