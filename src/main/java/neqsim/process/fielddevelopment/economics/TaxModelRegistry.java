package neqsim.process.fielddevelopment.economics;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Registry of tax model parameters for different countries and regions.
 *
 * <p>
 * This class provides a centralized database of fiscal regime parameters for various oil and gas
 * producing countries. Parameters are loaded from a JSON resource file at startup and can be
 * extended with custom parameters at runtime.
 * </p>
 *
 * <h2>Data Source</h2>
 * <p>
 * Parameters are loaded from {@code data/fiscal/fiscal_parameters.json} in the classpath. If the
 * file is not found or cannot be parsed, hardcoded defaults are used as fallback.
 * </p>
 *
 * <h2>Predefined Countries</h2>
 * <ul>
 * <li><b>NO</b> - Norway (Norwegian Continental Shelf)</li>
 * <li><b>UK</b> - United Kingdom (UKCS)</li>
 * <li><b>US-GOM</b> - United States (Gulf of Mexico)</li>
 * <li><b>BR</b> - Brazil (Concession regime)</li>
 * <li><b>BR-PSA</b> - Brazil (Pre-Salt Production Sharing Agreement)</li>
 * <li><b>BR-DW</b> - Brazil (Deep Water with Special Participation)</li>
 * <li><b>AO</b> - Angola</li>
 * <li><b>NG</b> - Nigeria</li>
 * <li><b>AU</b> - Australia</li>
 * <li><b>MY</b> - Malaysia</li>
 * <li><b>ID</b> - Indonesia</li>
 * <li><b>AE</b> - United Arab Emirates</li>
 * <li><b>CA-AB</b> - Canada (Alberta)</li>
 * <li><b>GY</b> - Guyana</li>
 * <li><b>EG</b> - Egypt</li>
 * <li><b>KZ</b> - Kazakhstan</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Get Norway parameters
 * FiscalParameters norway = TaxModelRegistry.getParameters("NO");
 * 
 * // Create a tax model
 * TaxModel model = TaxModelRegistry.createModel("NO");
 * 
 * // Get all available countries
 * List<String> countries = TaxModelRegistry.getAvailableCountries();
 * 
 * // Register custom parameters
 * FiscalParameters custom = FiscalParameters.builder("CUSTOM").countryName("Custom Country")
 *     .corporateTaxRate(0.30).build();
 * TaxModelRegistry.register(custom);
 * }</pre>
 *
 * <h2>Customization</h2>
 * <p>
 * To add or modify country parameters:
 * </p>
 * <ol>
 * <li>Edit the {@code data/fiscal/fiscal_parameters.json} file in resources</li>
 * <li>Or call {@link #register(FiscalParameters)} at runtime</li>
 * <li>Or call {@link #loadFromJson(InputStream)} to load a custom JSON file</li>
 * </ol>
 *
 * <p>
 * <b>Note:</b> These parameters are for screening purposes only and should be validated against
 * current regulations before making investment decisions.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see FiscalParameters
 * @see GenericTaxModel
 * @see TaxModel
 */
public final class TaxModelRegistry {

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(TaxModelRegistry.class);

  /** Path to the JSON resource file. */
  private static final String JSON_RESOURCE_PATH = "data/fiscal/fiscal_parameters.json";

  // ============================================================================
  // REGISTRY STORAGE
  // ============================================================================

  private static final Map<String, FiscalParameters> REGISTRY =
      new LinkedHashMap<String, FiscalParameters>();

  // Static initialization - try to load from JSON, fallback to hardcoded
  static {
    boolean loaded = loadFromResource();
    if (!loaded) {
      logger.warn("Could not load fiscal parameters from resource, using hardcoded defaults");
      initializeHardcodedDefaults();
    }
  }

  // Private constructor to prevent instantiation
  private TaxModelRegistry() {}

  // ============================================================================
  // PUBLIC API
  // ============================================================================

  /**
   * Gets fiscal parameters for a country.
   *
   * @param countryCode country code (e.g., "NO", "UK", "US-GOM")
   * @return fiscal parameters, or null if not found
   */
  public static FiscalParameters getParameters(String countryCode) {
    if (countryCode == null) {
      return null;
    }
    return REGISTRY.get(countryCode.toUpperCase());
  }

  /**
   * Gets fiscal parameters for a country, with fallback.
   *
   * @param countryCode country code
   * @param fallback fallback parameters if country not found
   * @return fiscal parameters
   */
  public static FiscalParameters getParametersOrDefault(String countryCode,
      FiscalParameters fallback) {
    FiscalParameters params = getParameters(countryCode);
    return params != null ? params : fallback;
  }

  /**
   * Creates a tax model for a country.
   *
   * @param countryCode country code
   * @return tax model instance
   * @throws IllegalArgumentException if country not found
   */
  public static TaxModel createModel(String countryCode) {
    FiscalParameters params = getParameters(countryCode);
    if (params == null) {
      throw new IllegalArgumentException(
          "Unknown country code: " + countryCode + ". Available: " + getAvailableCountries());
    }
    return new GenericTaxModel(params);
  }

  /**
   * Registers custom fiscal parameters.
   *
   * @param parameters parameters to register
   */
  public static void register(FiscalParameters parameters) {
    if (parameters == null || parameters.getCountryCode() == null) {
      throw new IllegalArgumentException("Parameters and country code cannot be null");
    }
    REGISTRY.put(parameters.getCountryCode().toUpperCase(), parameters);
  }

  /**
   * Gets list of available country codes.
   *
   * @return list of country codes
   */
  public static List<String> getAvailableCountries() {
    return new ArrayList<String>(REGISTRY.keySet());
  }

  /**
   * Gets all registered parameters.
   *
   * @return unmodifiable map of all parameters
   */
  public static Map<String, FiscalParameters> getAllParameters() {
    return Collections.unmodifiableMap(REGISTRY);
  }

  /**
   * Checks if a country is registered.
   *
   * @param countryCode country code
   * @return true if registered
   */
  public static boolean isRegistered(String countryCode) {
    return countryCode != null && REGISTRY.containsKey(countryCode.toUpperCase());
  }

  /**
   * Gets the number of registered countries.
   *
   * @return number of countries
   */
  public static int getRegisteredCount() {
    return REGISTRY.size();
  }

  /**
   * Reloads parameters from the resource file.
   *
   * @return true if successfully loaded
   */
  public static boolean reload() {
    REGISTRY.clear();
    boolean loaded = loadFromResource();
    if (!loaded) {
      initializeHardcodedDefaults();
    }
    return loaded;
  }

  /**
   * Loads fiscal parameters from a JSON input stream.
   *
   * <p>
   * This method can be used to load parameters from a custom JSON file.
   * </p>
   *
   * @param inputStream JSON input stream
   * @return number of countries loaded
   * @throws IllegalArgumentException if JSON is invalid
   */
  public static int loadFromJson(InputStream inputStream) {
    if (inputStream == null) {
      throw new IllegalArgumentException("Input stream cannot be null");
    }

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      StringBuilder content = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        content.append(line);
      }

      return parseAndRegisterJson(content.toString());
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to load JSON: " + e.getMessage(), e);
    }
  }

  // ============================================================================
  // JSON LOADING
  // ============================================================================

  /**
   * Loads parameters from the default resource file.
   *
   * @return true if loaded successfully
   */
  private static boolean loadFromResource() {
    try (InputStream is =
        TaxModelRegistry.class.getClassLoader().getResourceAsStream(JSON_RESOURCE_PATH)) {
      if (is == null) {
        logger.debug("Fiscal parameters resource not found: {}", JSON_RESOURCE_PATH);
        return false;
      }

      int count = loadFromJson(is);
      logger.info("Loaded {} fiscal parameter sets from {}", count, JSON_RESOURCE_PATH);
      return count > 0;
    } catch (Exception e) {
      logger.error("Error loading fiscal parameters from resource: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Parses JSON content and registers all countries.
   *
   * @param jsonContent JSON string
   * @return number of countries registered
   */
  private static int parseAndRegisterJson(String jsonContent) {
    JsonObject root = JsonParser.parseString(jsonContent).getAsJsonObject();
    JsonArray countries = root.getAsJsonArray("countries");

    if (countries == null) {
      throw new IllegalArgumentException("JSON must contain a 'countries' array");
    }

    int count = 0;
    for (JsonElement element : countries) {
      try {
        FiscalParameters params = parseCountryJson(element.getAsJsonObject());
        register(params);
        count++;
      } catch (Exception e) {
        logger.warn("Failed to parse country entry: {}", e.getMessage());
      }
    }

    return count;
  }

  /**
   * Parses a single country JSON object into FiscalParameters.
   *
   * @param json country JSON object
   * @return FiscalParameters instance
   */
  private static FiscalParameters parseCountryJson(JsonObject json) {
    String countryCode = getStringOrDefault(json, "countryCode", "UNKNOWN");

    FiscalParameters.Builder builder = FiscalParameters.builder(countryCode)
        .countryName(getStringOrDefault(json, "countryName", countryCode))
        .description(getStringOrDefault(json, "description", ""))
        .validFromYear(getIntOrDefault(json, "validFromYear", 2024))
        .fiscalSystemType(
            parseFiscalSystemType(getStringOrDefault(json, "fiscalSystemType", "CONCESSIONARY")))
        .corporateTaxRate(getDoubleOrDefault(json, "corporateTaxRate", 0.25))
        .resourceTaxRate(getDoubleOrDefault(json, "resourceTaxRate", 0.0))
        .royaltyRate(getDoubleOrDefault(json, "royaltyRate", 0.0))
        .windfallTax(getDoubleOrDefault(json, "windfallTaxRate", 0.0),
            getDoubleOrDefault(json, "windfallTaxThreshold", 0.0))
        .stateParticipation(getDoubleOrDefault(json, "stateParticipation", 0.0))
        .depreciation(
            parseDepreciationMethod(
                getStringOrDefault(json, "depreciationMethod", "STRAIGHT_LINE")),
            getIntOrDefault(json, "depreciationYears", 6))
        .decliningBalanceRate(getDoubleOrDefault(json, "decliningBalanceRate", 0.25))
        .uplift(getDoubleOrDefault(json, "upliftRate", 0.0),
            getIntOrDefault(json, "upliftYears", 0))
        .investmentTaxCredit(getDoubleOrDefault(json, "investmentTaxCredit", 0.0))
        .costRecoveryLimit(getDoubleOrDefault(json, "costRecoveryLimit", 1.0))
        .profitSharing(getDoubleOrDefault(json, "profitShareGovernment", 0.0),
            getDoubleOrDefault(json, "profitShareContractor", 1.0))
        .lossCarryForward(getIntOrDefault(json, "lossCarryForwardYears", 0),
            getDoubleOrDefault(json, "lossCarryForwardInterest", 0.0))
        .ringFenced(parseRingFenceLevel(getStringOrDefault(json, "ringFenceLevel", "COMPANY")))
        .decommissioning(getBooleanOrDefault(json, "decommissioningDeductible", true),
            getBooleanOrDefault(json, "decommissioningFundDeductible", false));

    return builder.build();
  }

  private static String getStringOrDefault(JsonObject json, String key, String defaultValue) {
    return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString()
        : defaultValue;
  }

  private static double getDoubleOrDefault(JsonObject json, String key, double defaultValue) {
    return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsDouble()
        : defaultValue;
  }

  private static int getIntOrDefault(JsonObject json, String key, int defaultValue) {
    return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsInt() : defaultValue;
  }

  private static boolean getBooleanOrDefault(JsonObject json, String key, boolean defaultValue) {
    return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsBoolean()
        : defaultValue;
  }

  private static FiscalParameters.FiscalSystemType parseFiscalSystemType(String value) {
    try {
      return FiscalParameters.FiscalSystemType.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return FiscalParameters.FiscalSystemType.CONCESSIONARY;
    }
  }

  private static FiscalParameters.DepreciationMethod parseDepreciationMethod(String value) {
    try {
      return FiscalParameters.DepreciationMethod.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return FiscalParameters.DepreciationMethod.STRAIGHT_LINE;
    }
  }

  private static FiscalParameters.RingFenceLevel parseRingFenceLevel(String value) {
    try {
      return FiscalParameters.RingFenceLevel.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return FiscalParameters.RingFenceLevel.COMPANY;
    }
  }

  // ============================================================================
  // HARDCODED FALLBACK DEFAULTS
  // ============================================================================

  /**
   * Initializes hardcoded default countries (fallback if JSON fails).
   */
  private static void initializeHardcodedDefaults() {
    // Norway (Norwegian Continental Shelf)
    register(FiscalParameters.builder("NO").countryName("Norway")
        .description("Norwegian Continental Shelf petroleum tax regime (2024)").validFromYear(2024)
        .fiscalSystemType(FiscalParameters.FiscalSystemType.CONCESSIONARY).corporateTaxRate(0.22)
        .resourceTaxRate(0.56).royaltyRate(0.0)
        .depreciation(FiscalParameters.DepreciationMethod.STRAIGHT_LINE, 6).uplift(0.055, 4)
        .lossCarryForward(0, 0.0).ringFenced(FiscalParameters.RingFenceLevel.COMPANY)
        .decommissioning(true, false).build());

    // United Kingdom (UKCS)
    register(FiscalParameters.builder("UK").countryName("United Kingdom")
        .description("UK Continental Shelf petroleum tax regime (2024)").validFromYear(2024)
        .fiscalSystemType(FiscalParameters.FiscalSystemType.CONCESSIONARY).corporateTaxRate(0.30)
        .resourceTaxRate(0.10).royaltyRate(0.0)
        .depreciation(FiscalParameters.DepreciationMethod.STRAIGHT_LINE, 6).lossCarryForward(0, 0.0)
        .ringFenced(FiscalParameters.RingFenceLevel.LICENSE).decommissioning(true, true).build());

    // United States - Gulf of Mexico
    register(FiscalParameters.builder("US-GOM").countryName("United States - Gulf of Mexico")
        .description("US Gulf of Mexico federal waters (OCS)").validFromYear(2024)
        .fiscalSystemType(FiscalParameters.FiscalSystemType.CONCESSIONARY).corporateTaxRate(0.21)
        .resourceTaxRate(0.0).royaltyRate(0.1875)
        .depreciation(FiscalParameters.DepreciationMethod.STRAIGHT_LINE, 7).lossCarryForward(0, 0.0)
        .ringFenced(FiscalParameters.RingFenceLevel.COMPANY).decommissioning(true, false).build());

    // Brazil - Concession
    register(FiscalParameters.builder("BR").countryName("Brazil")
        .description("Brazil concession regime - post-salt and conventional fields (2024)")
        .validFromYear(2024).fiscalSystemType(FiscalParameters.FiscalSystemType.CONCESSIONARY)
        .corporateTaxRate(0.34).resourceTaxRate(0.0).royaltyRate(0.10)
        .depreciation(FiscalParameters.DepreciationMethod.STRAIGHT_LINE, 10)
        .lossCarryForward(0, 0.0).ringFenced(FiscalParameters.RingFenceLevel.FIELD)
        .decommissioning(true, false).build());

    // Angola
    register(FiscalParameters.builder("AO").countryName("Angola")
        .description("Angola PSC regime (deep water)").validFromYear(2024)
        .fiscalSystemType(FiscalParameters.FiscalSystemType.PSC).corporateTaxRate(0.30)
        .royaltyRate(0.0).costRecoveryLimit(0.50).profitSharing(0.60, 0.40)
        .depreciation(FiscalParameters.DepreciationMethod.STRAIGHT_LINE, 4).lossCarryForward(5, 0.0)
        .ringFenced(FiscalParameters.RingFenceLevel.LICENSE).build());
  }

  // ============================================================================
  // UTILITY METHODS
  // ============================================================================

  /**
   * Gets a summary table of all registered countries.
   *
   * @return formatted table string
   */
  public static String getSummaryTable() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("%-8s %-30s %8s %8s %8s %8s%n", "Code", "Country", "Corp%", "Res%",
        "Total%", "Royalty%"));
    sb.append(repeatChar('-', 80)).append("\n");

    for (FiscalParameters params : REGISTRY.values()) {
      sb.append(String.format("%-8s %-30s %8.1f %8.1f %8.1f %8.1f%n", params.getCountryCode(),
          truncate(params.getCountryName(), 30), params.getCorporateTaxRate() * 100,
          params.getResourceTaxRate() * 100, params.getTotalMarginalTaxRate() * 100,
          params.getRoyaltyRate() * 100));
    }

    return sb.toString();
  }

  /**
   * Repeats a character n times (Java 8 compatible).
   *
   * @param c character to repeat
   * @param n number of times
   * @return repeated string
   */
  private static String repeatChar(char c, int n) {
    StringBuilder sb = new StringBuilder(n);
    for (int i = 0; i < n; i++) {
      sb.append(c);
    }
    return sb.toString();
  }

  /**
   * Truncates a string.
   *
   * @param s string to truncate
   * @param maxLen maximum length
   * @return truncated string
   */
  private static String truncate(String s, int maxLen) {
    if (s == null || s.length() <= maxLen) {
      return s;
    }
    return s.substring(0, maxLen - 2) + "..";
  }
}
