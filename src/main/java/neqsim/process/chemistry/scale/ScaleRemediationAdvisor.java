package neqsim.process.chemistry.scale;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.pvtsimulation.flowassurance.MultiMineralScaleEquilibrium;
import neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator;

/**
 * Recommends dissolver/solvent treatments for cleaning equipment that has scaled or precipitated (fouled valves and
 * trim, bridles, lines, heat exchangers). Intended for the proposed-solution step of a root-cause analysis: given the
 * identified deposit type, it returns candidate chemical remedies with concentration, method, effectiveness, a
 * temperature window, cautions, and an informational standard reference.
 *
 * <p>
 * The knowledge base is loaded from the classpath resource {@code /data/scale_remediation.csv} (pipe-delimited). It
 * covers the common oilfield mineral scales (CaCO3, FeCO3, FeS, BaSO4, SrSO4, CaSO4, NaCl), the amorphous H2S-scavenger
 * reaction product dithiazine, and organic deposits (asphaltene, wax, naphthenate). Common mineral names (calcite,
 * barite, gypsum, siderite, mackinawite, halite, …) are mapped to their canonical formula, so a caller can pass either.
 *
 * <p>
 * This is a screening-level advisory (NACE TM0374 / SP0296 / SP0775 informational). It is not a substitute for a
 * vendor-qualified, site-specific treatment program, deposit sampling (XRD/SEM-EDX), or a compatibility/corrosion
 * review of the selected chemical against the equipment metallurgy.
 *
 * <pre>{@code
 * ScaleRemediationAdvisor advisor = new ScaleRemediationAdvisor();
 * List<ScaleRemediationAdvisor.RemediationOption> options = advisor.recommendFor("calcite");
 * String json = advisor.toJson("CaCO3");
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see ScaleRemediationAdvisor.RemediationOption
 * @see ScalePredictionCalculator
 * @see MultiMineralScaleEquilibrium
 */
public class ScaleRemediationAdvisor implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Default classpath resource holding the remediation knowledge base. */
  public static final String DEFAULT_RESOURCE = "/data/scale_remediation.csv";

  /** All loaded remediation options. */
  private final List<RemediationOption> options;

  /** Common mineral/deposit names mapped to the canonical key used in the knowledge base. */
  private static final Map<String, String> ALIASES = buildAliases();

  /**
   * Constructs an advisor using the default bundled knowledge base.
   */
  public ScaleRemediationAdvisor() {
    this(DEFAULT_RESOURCE);
  }

  /**
   * Constructs an advisor from an arbitrary classpath CSV resource.
   *
   * @param resource path to a pipe-delimited remediation CSV on the classpath
   */
  public ScaleRemediationAdvisor(String resource) {
    this.options = loadFromResource(resource);
  }

  /**
   * Builds the alias table mapping common deposit names to canonical knowledge-base keys.
   *
   * @return an immutable-style map of lower-case alias to canonical key
   */
  private static Map<String, String> buildAliases() {
    Map<String, String> map = new HashMap<String, String>();
    map.put("calcite", "CaCO3");
    map.put("aragonite", "CaCO3");
    map.put("calcium carbonate", "CaCO3");
    map.put("siderite", "FeCO3");
    map.put("iron carbonate", "FeCO3");
    map.put("mackinawite", "FeS");
    map.put("pyrrhotite", "FeS");
    map.put("troilite", "FeS");
    map.put("iron sulfide", "FeS");
    map.put("iron sulphide", "FeS");
    map.put("barite", "BaSO4");
    map.put("barytes", "BaSO4");
    map.put("barium sulfate", "BaSO4");
    map.put("barium sulphate", "BaSO4");
    map.put("celestite", "SrSO4");
    map.put("strontium sulfate", "SrSO4");
    map.put("strontium sulphate", "SrSO4");
    map.put("gypsum", "CaSO4");
    map.put("anhydrite", "CaSO4");
    map.put("calcium sulfate", "CaSO4");
    map.put("calcium sulphate", "CaSO4");
    map.put("halite", "NaCl");
    map.put("salt", "NaCl");
    map.put("dithiazine", "Dithiazine");
    map.put("triazine solids", "Dithiazine");
    map.put("scavenger solids", "Dithiazine");
    map.put("asphaltene", "Asphaltene");
    map.put("asphaltenes", "Asphaltene");
    map.put("wax", "Wax");
    map.put("paraffin", "Wax");
    map.put("naphthenate", "Naphthenate");
    map.put("calcium naphthenate", "Naphthenate");
    return map;
  }

  /**
   * Normalises a user-supplied deposit name to the canonical knowledge-base key.
   *
   * @param scaleType the deposit name or formula (case-insensitive)
   * @return the canonical key, or the trimmed input unchanged if no alias matches
   */
  public static String canonicalKey(String scaleType) {
    if (scaleType == null) {
      return "UNKNOWN";
    }
    String trimmed = scaleType.trim();
    if (trimmed.isEmpty()) {
      return "UNKNOWN";
    }
    String alias = ALIASES.get(trimmed.toLowerCase());
    return alias != null ? alias : trimmed;
  }

  /**
   * Loads remediation options from a classpath CSV resource.
   *
   * @param resource the classpath resource path
   * @return the parsed options (empty if the resource is missing or unreadable)
   */
  private static List<RemediationOption> loadFromResource(String resource) {
    List<RemediationOption> result = new ArrayList<RemediationOption>();
    InputStream stream = ScaleRemediationAdvisor.class.getResourceAsStream(resource);
    if (stream == null) {
      return result;
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8")));
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        String[] parts = line.split("\\|", -1);
        if (parts.length < 10) {
          continue;
        }
        double minTemp = parseDouble(parts[6].trim(), 0.0);
        double maxTemp = parseDouble(parts[7].trim(), 200.0);
        result.add(new RemediationOption(parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim(),
            parts[4].trim(), parts[5].trim(), minTemp, maxTemp, parts[8].trim(), parts[9].trim()));
      }
    } catch (IOException ex) {
      // return whatever was parsed so far
    } finally {
      try {
        reader.close();
      } catch (IOException ignored) {
        // ignored
      }
    }
    return result;
  }

  /**
   * Parses a double, returning a fallback on failure.
   *
   * @param value the string to parse
   * @param fallback the value to return if parsing fails
   * @return the parsed double or the fallback
   */
  private static double parseDouble(String value, double fallback) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  /**
   * Returns candidate remediation options for a deposit type, ordered by descending effectiveness.
   *
   * @param scaleType deposit name or formula (aliases such as "calcite" or "barite" are accepted)
   * @return the matching options; if none match, the generic UNKNOWN (sample-and-identify) guidance is returned
   */
  public List<RemediationOption> recommendFor(String scaleType) {
    String key = canonicalKey(scaleType);
    List<RemediationOption> matches = new ArrayList<RemediationOption>();
    for (RemediationOption option : options) {
      if (option.getScaleType().equalsIgnoreCase(key)) {
        matches.add(option);
      }
    }
    if (matches.isEmpty()) {
      for (RemediationOption option : options) {
        if (option.getScaleType().equalsIgnoreCase("UNKNOWN")) {
          matches.add(option);
        }
      }
    }
    matches.sort(new java.util.Comparator<RemediationOption>() {
      @Override
      public int compare(RemediationOption a, RemediationOption b) {
        return Integer.compare(b.effectivenessRank(), a.effectivenessRank());
      }
    });
    return matches;
  }

  /**
   * Returns remediation options for several deposit types (e.g. a co-precipitated BaSO4/SrSO4 or a mixed carbonate +
   * sulfide deposit), keyed by canonical deposit name.
   *
   * @param scaleTypes the list of deposit names or formulae
   * @return a map from canonical deposit key to its ordered remediation options
   */
  public Map<String, List<RemediationOption>> recommendForMinerals(List<String> scaleTypes) {
    Map<String, List<RemediationOption>> result = new LinkedHashMap<String, List<RemediationOption>>();
    if (scaleTypes == null) {
      return result;
    }
    for (String scaleType : scaleTypes) {
      String key = canonicalKey(scaleType);
      if (!result.containsKey(key)) {
        result.put(key, recommendFor(key));
      }
    }
    return result;
  }

  /**
   * Serialises the remediation recommendation for a single deposit type to a JSON string.
   *
   * @param scaleType deposit name or formula
   * @return a pretty-printed JSON representation
   */
  public String toJson(String scaleType) {
    Map<String, Object> root = new LinkedHashMap<String, Object>();
    root.put("scaleType", canonicalKey(scaleType));
    List<Map<String, Object>> optionMaps = new ArrayList<Map<String, Object>>();
    for (RemediationOption option : recommendFor(scaleType)) {
      optionMaps.add(option.toMap());
    }
    root.put("options", optionMaps);
    root.put("disclaimer", "Screening-level (NACE TM0374/SP0296/SP0775 informational). Sample and identify the "
        + "deposit and run a metallurgy/compatibility review before applying any dissolver.");
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    return gson.toJson(root);
  }

  /**
   * Returns all loaded remediation options.
   *
   * @return the full option list
   */
  public List<RemediationOption> getAllOptions() {
    return new ArrayList<RemediationOption>(options);
  }

  /**
   * A single candidate chemical remedy for a scaled/precipitated deposit.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class RemediationOption implements Serializable {

    private static final long serialVersionUID = 1000L;

    private final String scaleType;
    private final String category;
    private final String dissolver;
    private final String concentration;
    private final String method;
    private final String effectiveness;
    private final double minTemperatureC;
    private final double maxTemperatureC;
    private final String cautions;
    private final String standard;

    /**
     * Constructs a remediation option.
     *
     * @param scaleType canonical deposit key (e.g. CaCO3)
     * @param category deposit category (MINERAL_SCALE, SCAVENGER_SOLIDS, ORGANIC_DEPOSIT, UNSPECIFIED)
     * @param dissolver the dissolver/solvent chemistry
     * @param concentration recommended concentration or dilution
     * @param method application method
     * @param effectiveness qualitative effectiveness (HIGH, MEDIUM, LOW)
     * @param minTemperatureC lower bound of the effective temperature window [C]
     * @param maxTemperatureC upper bound of the effective temperature window [C]
     * @param cautions safety/operational cautions
     * @param standard informational standard reference
     */
    public RemediationOption(String scaleType, String category, String dissolver, String concentration, String method,
        String effectiveness, double minTemperatureC, double maxTemperatureC, String cautions, String standard) {
      this.scaleType = scaleType;
      this.category = category;
      this.dissolver = dissolver;
      this.concentration = concentration;
      this.method = method;
      this.effectiveness = effectiveness;
      this.minTemperatureC = minTemperatureC;
      this.maxTemperatureC = maxTemperatureC;
      this.cautions = cautions;
      this.standard = standard;
    }

    /**
     * Ranks the qualitative effectiveness for ordering (HIGH=3, MEDIUM=2, LOW=1, otherwise 0).
     *
     * @return the effectiveness rank
     */
    public int effectivenessRank() {
      if ("HIGH".equalsIgnoreCase(effectiveness)) {
        return 3;
      }
      if ("MEDIUM".equalsIgnoreCase(effectiveness)) {
        return 2;
      }
      if ("LOW".equalsIgnoreCase(effectiveness)) {
        return 1;
      }
      return 0;
    }

    /**
     * Returns the canonical deposit key.
     *
     * @return the deposit type
     */
    public String getScaleType() {
      return scaleType;
    }

    /**
     * Returns the deposit category.
     *
     * @return the category
     */
    public String getCategory() {
      return category;
    }

    /**
     * Returns the dissolver/solvent chemistry.
     *
     * @return the dissolver description
     */
    public String getDissolver() {
      return dissolver;
    }

    /**
     * Returns the recommended concentration or dilution.
     *
     * @return the concentration
     */
    public String getConcentration() {
      return concentration;
    }

    /**
     * Returns the application method.
     *
     * @return the method
     */
    public String getMethod() {
      return method;
    }

    /**
     * Returns the qualitative effectiveness.
     *
     * @return the effectiveness (HIGH, MEDIUM, LOW)
     */
    public String getEffectiveness() {
      return effectiveness;
    }

    /**
     * Returns the lower bound of the effective temperature window.
     *
     * @return minimum temperature [C]
     */
    public double getMinTemperatureC() {
      return minTemperatureC;
    }

    /**
     * Returns the upper bound of the effective temperature window.
     *
     * @return maximum temperature [C]
     */
    public double getMaxTemperatureC() {
      return maxTemperatureC;
    }

    /**
     * Returns the safety/operational cautions.
     *
     * @return the cautions text
     */
    public String getCautions() {
      return cautions;
    }

    /**
     * Returns the informational standard reference.
     *
     * @return the standard reference
     */
    public String getStandard() {
      return standard;
    }

    /**
     * Serialises this option to an ordered map suitable for JSON reporting.
     *
     * @return a map of the option fields
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("scaleType", scaleType);
      map.put("category", category);
      map.put("dissolver", dissolver);
      map.put("concentration", concentration);
      map.put("method", method);
      map.put("effectiveness", effectiveness);
      map.put("minTemperatureC", minTemperatureC);
      map.put("maxTemperatureC", maxTemperatureC);
      map.put("cautions", cautions);
      map.put("standard", standard);
      return map;
    }
  }
}
