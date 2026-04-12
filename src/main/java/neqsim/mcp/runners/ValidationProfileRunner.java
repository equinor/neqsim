package neqsim.mcp.runners;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Domain-specific validation profiles for engineering simulations.
 *
 * <p>
 * Different jurisdictions, operators, and project phases have different design standards and
 * acceptance criteria. This runner manages validation profiles that configure which rules apply and
 * what tolerances are acceptable.
 * </p>
 *
 * <p>
 * Built-in profiles:
 * <ul>
 * <li><b>ncs</b> — Norwegian Continental Shelf (NORSOK, PSA, DNV)</li>
 * <li><b>ukcs</b> — UK Continental Shelf (API, HSE, PD 8010)</li>
 * <li><b>gom</b> — Gulf of Mexico (API, BSEE, 30 CFR 250)</li>
 * <li><b>brazil</b> — Brazil deepwater (ANP, Petrobras N-series)</li>
 * <li><b>generic</b> — International standards only (ISO, API)</li>
 * </ul>
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ValidationProfileRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Custom profiles created by users. */
  private static final ConcurrentHashMap<String, JsonObject> CUSTOM_PROFILES =
      new ConcurrentHashMap<String, JsonObject>();

  /** Currently active profile. */
  private static volatile String activeProfile = "generic";

  /**
   * Private constructor — all methods are static.
   */
  private ValidationProfileRunner() {}

  /**
   * Main entry point for validation profile operations.
   *
   * @param json JSON with action and parameters
   * @return JSON with results
   */
  public static String run(String json) {
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String action = input.has("action") ? input.get("action").getAsString() : "";

      switch (action) {
        case "listProfiles":
          return listProfiles();
        case "getProfile":
          return getProfile(input);
        case "setActiveProfile":
          return setActiveProfile(input);
        case "createProfile":
          return createProfile(input);
        case "deleteProfile":
          return deleteProfile(input);
        case "validateWithProfile":
          return validateWithProfile(input);
        case "getActiveProfile":
          return getActiveProfile();
        case "getStandardsForEquipment":
          return getStandardsForEquipment(input);
        default:
          return errorJson("UNKNOWN_ACTION", "Unknown validation profile action: " + action,
              "Use: listProfiles, getProfile, setActiveProfile, createProfile, "
                  + "deleteProfile, validateWithProfile, getActiveProfile, "
                  + "getStandardsForEquipment");
      }
    } catch (Exception e) {
      return errorJson("VALIDATION_PROFILE_ERROR", e.getMessage(), "Check JSON format");
    }
  }

  /**
   * Returns the currently active validation profile.
   *
   * @return JSON with active profile details
   */
  public static String getActiveProfile() {
    if (CUSTOM_PROFILES.containsKey(activeProfile)) {
      JsonObject response = new JsonObject();
      response.addProperty("status", "success");
      response.addProperty("activeProfile", activeProfile);
      response.addProperty("type", "custom");
      response.add("profile", CUSTOM_PROFILES.get(activeProfile));
      return GSON.toJson(response);
    }

    JsonObject profile = getBuiltInProfile(activeProfile);
    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("activeProfile", activeProfile);
    response.addProperty("type", "built-in");
    response.add("profile", profile);
    return GSON.toJson(response);
  }

  /**
   * Gets the active profile name.
   *
   * @return profile name
   */
  public static String getActiveProfileName() {
    return activeProfile;
  }

  /**
   * Lists all available profiles (built-in + custom).
   *
   * @return JSON with profile list
   */
  private static String listProfiles() {
    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("activeProfile", activeProfile);

    JsonArray profiles = new JsonArray();

    // Built-in profiles
    for (String name : Arrays.asList("ncs", "ukcs", "gom", "brazil", "generic")) {
      JsonObject entry = new JsonObject();
      entry.addProperty("name", name);
      entry.addProperty("type", "built-in");
      entry.addProperty("active", name.equals(activeProfile));
      JsonObject profile = getBuiltInProfile(name);
      entry.addProperty("description",
          profile.has("description") ? profile.get("description").getAsString() : "");
      entry.addProperty("jurisdiction",
          profile.has("jurisdiction") ? profile.get("jurisdiction").getAsString() : "");
      profiles.add(entry);
    }

    // Custom profiles
    for (Map.Entry<String, JsonObject> entry : CUSTOM_PROFILES.entrySet()) {
      JsonObject custom = new JsonObject();
      custom.addProperty("name", entry.getKey());
      custom.addProperty("type", "custom");
      custom.addProperty("active", entry.getKey().equals(activeProfile));
      JsonObject profile = entry.getValue();
      custom.addProperty("description",
          profile.has("description") ? profile.get("description").getAsString() : "");
      profiles.add(custom);
    }

    response.add("profiles", profiles);
    response.addProperty("totalCount", profiles.size());
    return GSON.toJson(response);
  }

  /**
   * Gets the full details of a named profile.
   *
   * @param input JSON with profile name
   * @return JSON with profile details
   */
  private static String getProfile(JsonObject input) {
    String name = input.has("profileName") ? input.get("profileName").getAsString() : "";
    if (name.isEmpty()) {
      return errorJson("MISSING_NAME", "profileName is required",
          "Provide the name of the profile to retrieve");
    }

    JsonObject profile;
    String type;
    if (CUSTOM_PROFILES.containsKey(name)) {
      profile = CUSTOM_PROFILES.get(name);
      type = "custom";
    } else {
      profile = getBuiltInProfile(name);
      if (profile == null) {
        return errorJson("PROFILE_NOT_FOUND", "Profile not found: " + name,
            "Use 'listProfiles' to see available profiles");
      }
      type = "built-in";
    }

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("name", name);
    response.addProperty("type", type);
    response.add("profile", profile);
    return GSON.toJson(response);
  }

  /**
   * Sets the active validation profile.
   *
   * @param input JSON with profile name
   * @return JSON confirmation
   */
  private static String setActiveProfile(JsonObject input) {
    String name = input.has("profileName") ? input.get("profileName").getAsString() : "";
    if (name.isEmpty()) {
      return errorJson("MISSING_NAME", "profileName is required",
          "Provide the name of the profile to activate");
    }

    // Verify it exists
    if (!CUSTOM_PROFILES.containsKey(name) && getBuiltInProfile(name) == null) {
      return errorJson("PROFILE_NOT_FOUND", "Profile not found: " + name,
          "Use 'listProfiles' to see available profiles, or 'createProfile' to make one");
    }

    activeProfile = name;

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("activeProfile", activeProfile);
    response.addProperty("message", "Validation profile changed to: " + name);
    return GSON.toJson(response);
  }

  /**
   * Creates a custom validation profile.
   *
   * @param input JSON with profile definition
   * @return JSON confirmation
   */
  private static String createProfile(JsonObject input) {
    String name = input.has("profileName") ? input.get("profileName").getAsString() : "";
    if (name.isEmpty()) {
      return errorJson("MISSING_NAME", "profileName is required",
          "Provide a name for the custom profile");
    }

    // Don't allow overwriting built-in profiles
    if (Arrays.asList("ncs", "ukcs", "gom", "brazil", "generic").contains(name)) {
      return errorJson("RESERVED_NAME", "Cannot overwrite built-in profile: " + name,
          "Choose a different name for your custom profile");
    }

    // Build profile from input or from base + overrides
    JsonObject profile;
    if (input.has("profile")) {
      profile = input.getAsJsonObject("profile");
    } else {
      // Start from a base profile and apply overrides
      String baseName = input.has("basedOn") ? input.get("basedOn").getAsString() : "generic";
      profile = getBuiltInProfile(baseName);
      if (profile == null) {
        profile = getBuiltInProfile("generic");
      }

      // Apply overrides
      if (input.has("overrides")) {
        JsonObject overrides = input.getAsJsonObject("overrides");
        for (String key : overrides.keySet()) {
          profile.add(key, overrides.get(key));
        }
      }
    }

    // Add metadata
    profile.addProperty("name", name);
    profile.addProperty("type", "custom");
    if (input.has("description")) {
      profile.addProperty("description", input.get("description").getAsString());
    }

    CUSTOM_PROFILES.put(name, profile);

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("profileName", name);
    response.addProperty("message", "Custom profile created. Use setActiveProfile to activate.");
    return GSON.toJson(response);
  }

  /**
   * Deletes a custom profile.
   *
   * @param input JSON with profile name
   * @return JSON confirmation
   */
  private static String deleteProfile(JsonObject input) {
    String name = input.has("profileName") ? input.get("profileName").getAsString() : "";
    if (Arrays.asList("ncs", "ukcs", "gom", "brazil", "generic").contains(name)) {
      return errorJson("CANNOT_DELETE", "Cannot delete built-in profile: " + name,
          "Only custom profiles can be deleted");
    }

    JsonObject removed = CUSTOM_PROFILES.remove(name);
    if (activeProfile.equals(name)) {
      activeProfile = "generic";
    }

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("deleted", removed != null);
    response.addProperty("activeProfile", activeProfile);
    return GSON.toJson(response);
  }

  /**
   * Validates simulation input against the active (or specified) profile.
   *
   * @param input JSON with simulation parameters and optional profileName
   * @return JSON with validation results
   */
  private static String validateWithProfile(JsonObject input) {
    String profileName =
        input.has("profileName") ? input.get("profileName").getAsString() : activeProfile;

    JsonObject profile;
    if (CUSTOM_PROFILES.containsKey(profileName)) {
      profile = CUSTOM_PROFILES.get(profileName);
    } else {
      profile = getBuiltInProfile(profileName);
    }
    if (profile == null) {
      profile = getBuiltInProfile("generic");
      profileName = "generic";
    }

    // Run the standard engineering validation with profile context
    JsonObject validationInput = new JsonObject();
    if (input.has("processJson")) {
      validationInput.add("processJson", input.get("processJson"));
    }
    if (input.has("equipment")) {
      validationInput.add("equipment", input.get("equipment"));
    }

    // Build enriched validation request
    String validationResult =
        EngineeringValidator.validate(GSON.toJson(validationInput), "general");
    JsonObject valObj = JsonParser.parseString(validationResult).getAsJsonObject();

    // Add profile context to validation results
    valObj.addProperty("validationProfile", profileName);

    // Add applicable standards from profile
    if (profile.has("standards")) {
      valObj.add("applicableStandards", profile.get("standards"));
    }

    // Add design factors from profile
    if (profile.has("designFactors")) {
      valObj.add("requiredDesignFactors", profile.get("designFactors"));
    }

    return GSON.toJson(valObj);
  }

  /**
   * Returns standards that apply to a specific equipment type based on the active profile.
   *
   * @param input JSON with equipmentType
   * @return JSON with applicable standards
   */
  private static String getStandardsForEquipment(JsonObject input) {
    String equipmentType =
        input.has("equipmentType") ? input.get("equipmentType").getAsString() : "";
    String profileName =
        input.has("profileName") ? input.get("profileName").getAsString() : activeProfile;

    if (equipmentType.isEmpty()) {
      return errorJson("MISSING_TYPE", "equipmentType is required",
          "Provide equipment type: separator, pipeline, compressor, heatExchanger, vessel, valve");
    }

    JsonObject profile;
    if (CUSTOM_PROFILES.containsKey(profileName)) {
      profile = CUSTOM_PROFILES.get(profileName);
    } else {
      profile = getBuiltInProfile(profileName);
    }
    if (profile == null) {
      profile = getBuiltInProfile("generic");
    }

    JsonObject response = new JsonObject();
    response.addProperty("status", "success");
    response.addProperty("equipmentType", equipmentType);
    response.addProperty("profile", profileName);

    JsonArray applicable = new JsonArray();
    String lowerType = equipmentType.toLowerCase();

    // Map equipment to standards from profile
    if (profile.has("equipmentStandards")) {
      JsonObject eqStandards = profile.getAsJsonObject("equipmentStandards");
      if (eqStandards.has(lowerType)) {
        response.add("standards", eqStandards.get(lowerType));
        return GSON.toJson(response);
      }
    }

    // Fallback: return generic standards for the equipment type
    response.add("standards", getGenericStandards(lowerType));
    return GSON.toJson(response);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Built-in profiles
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Returns a built-in profile by name.
   *
   * @param name the profile name
   * @return the profile JSON, or null if unknown
   */
  private static JsonObject getBuiltInProfile(String name) {
    switch (name) {
      case "ncs":
        return buildNcsProfile();
      case "ukcs":
        return buildUkcsProfile();
      case "gom":
        return buildGomProfile();
      case "brazil":
        return buildBrazilProfile();
      case "generic":
        return buildGenericProfile();
      default:
        return null;
    }
  }

  /**
   * Norwegian Continental Shelf profile.
   *
   * @return NCS profile
   */
  private static JsonObject buildNcsProfile() {
    JsonObject profile = new JsonObject();
    profile.addProperty("name", "ncs");
    profile.addProperty("description", "Norwegian Continental Shelf — NORSOK, PSA, DNV standards");
    profile.addProperty("jurisdiction", "Norway");
    profile.addProperty("regulator", "PSA (Petroleum Safety Authority)");

    // Primary standards
    JsonArray standards = new JsonArray();
    standards.add("NORSOK P-001 (Process Design)");
    standards.add("NORSOK P-002 (Process System Design)");
    standards.add("NORSOK L-001 (Piping and Valves)");
    standards.add("NORSOK L-002 (Piping System Layout)");
    standards.add("NORSOK M-001 (Materials Selection)");
    standards.add("NORSOK D-010 (Well Integrity)");
    standards.add("NORSOK S-001 (Technical Safety)");
    standards.add("DNV-ST-F101 (Submarine Pipeline Systems)");
    standards.add("DNV-RP-C203 (Fatigue Design)");
    standards.add("DNV-OS-F101 (Submarine Pipelines)");
    profile.add("standards", standards);

    // Design factors
    JsonObject designFactors = new JsonObject();
    designFactors.addProperty("pipelineBurstDF", 1.10);
    designFactors.addProperty("pipelineCollapseDF", 1.00);
    designFactors.addProperty("casingBurstDF", 1.10);
    designFactors.addProperty("casingCollapseDF", 1.00);
    designFactors.addProperty("casingTensionDF", 1.60);
    designFactors.addProperty("vesselDesignPressureMargin", 1.10);
    designFactors.addProperty("corrosionAllowanceMm", 3.0);
    profile.add("designFactors", designFactors);

    // Equipment standards mapping
    profile.add("equipmentStandards", buildNcsEquipmentStandards());

    // Validation rules
    JsonObject rules = new JsonObject();
    rules.addProperty("maxOperatingPressure_bara", 690.0);
    rules.addProperty("maxOperatingTemperature_C", 350.0);
    rules.addProperty("minDesignTemperature_C", -46.0);
    rules.addProperty("requireMDMTCheck", true);
    rules.addProperty("requireHydrateCheck", true);
    rules.addProperty("requireCorrosionAssessment", true);
    rules.addProperty("requireSILAssessment", true);
    profile.add("validationRules", rules);

    return profile;
  }

  /**
   * UK Continental Shelf profile.
   *
   * @return UKCS profile
   */
  private static JsonObject buildUkcsProfile() {
    JsonObject profile = new JsonObject();
    profile.addProperty("name", "ukcs");
    profile.addProperty("description", "UK Continental Shelf — API, HSE, PD 8010 standards");
    profile.addProperty("jurisdiction", "United Kingdom");
    profile.addProperty("regulator", "HSE (Health and Safety Executive)");

    JsonArray standards = new JsonArray();
    standards.add("PD 8010 (Pipeline Code of Practice)");
    standards.add("API RP 14E (Offshore Production Piping)");
    standards.add("API 521 (Pressure-Relieving Systems)");
    standards.add("ASME B31.3 (Process Piping)");
    standards.add("BS EN 13480 (Metallic Industrial Piping)");
    standards.add("BS PD 5500 (Unfired Pressure Vessels)");
    standards.add("API 650 (Welded Tanks)");
    profile.add("standards", standards);

    JsonObject designFactors = new JsonObject();
    designFactors.addProperty("pipelineDesignFactor", 0.72);
    designFactors.addProperty("vesselDesignPressureMargin", 1.10);
    designFactors.addProperty("corrosionAllowanceMm", 3.0);
    profile.add("designFactors", designFactors);

    profile.add("equipmentStandards", buildUkcsEquipmentStandards());

    JsonObject rules = new JsonObject();
    rules.addProperty("maxOperatingPressure_bara", 690.0);
    rules.addProperty("maxOperatingTemperature_C", 350.0);
    rules.addProperty("requireHydrateCheck", true);
    rules.addProperty("requireCorrosionAssessment", true);
    profile.add("validationRules", rules);

    return profile;
  }

  /**
   * Gulf of Mexico profile.
   *
   * @return GoM profile
   */
  private static JsonObject buildGomProfile() {
    JsonObject profile = new JsonObject();
    profile.addProperty("name", "gom");
    profile.addProperty("description", "Gulf of Mexico — API, BSEE, 30 CFR 250 standards");
    profile.addProperty("jurisdiction", "United States");
    profile.addProperty("regulator", "BSEE (Bureau of Safety and Environmental Enforcement)");

    JsonArray standards = new JsonArray();
    standards.add("30 CFR 250 (Oil, Gas, Sulphur in OCS)");
    standards.add("API RP 14C (Safety Analysis)");
    standards.add("API RP 14E (Offshore Production Piping)");
    standards.add("API 6A (Wellhead Equipment)");
    standards.add("API 17D (Subsea Wellhead)");
    standards.add("API 5L (Line Pipe)");
    standards.add("ASME VIII Div.1 (Pressure Vessels)");
    standards.add("ASME B31.4 (Liquid Pipelines)");
    standards.add("ASME B31.8 (Gas Transmission)");
    profile.add("standards", standards);

    JsonObject designFactors = new JsonObject();
    designFactors.addProperty("pipelineDesignFactor", 0.72);
    designFactors.addProperty("vesselDesignPressureMargin", 1.10);
    designFactors.addProperty("apiDesignFactor", 0.60);
    profile.add("designFactors", designFactors);

    profile.add("equipmentStandards", buildGomEquipmentStandards());

    JsonObject rules = new JsonObject();
    rules.addProperty("maxOperatingPressure_bara", 1034.0);
    rules.addProperty("maxOperatingTemperature_C", 350.0);
    rules.addProperty("requireSSSVTesting", true);
    rules.addProperty("requireHydrateCheck", true);
    profile.add("validationRules", rules);

    return profile;
  }

  /**
   * Brazil deepwater profile.
   *
   * @return Brazil profile
   */
  private static JsonObject buildBrazilProfile() {
    JsonObject profile = new JsonObject();
    profile.addProperty("name", "brazil");
    profile.addProperty("description",
        "Brazil deepwater — ANP regulations, Petrobras N-series standards");
    profile.addProperty("jurisdiction", "Brazil");
    profile.addProperty("regulator", "ANP (Agencia Nacional do Petroleo)");

    JsonArray standards = new JsonArray();
    standards.add("ANP Regulation 43 (Safety of Offshore Installations)");
    standards.add("Petrobras N-253 (Process Design)");
    standards.add("Petrobras N-550 (Pipeline Design)");
    standards.add("DNV-ST-F101 (Submarine Pipelines)");
    standards.add("API 17D (Subsea Wellhead)");
    standards.add("API RP 17N (Subsea Reliability)");
    profile.add("standards", standards);

    JsonObject designFactors = new JsonObject();
    designFactors.addProperty("pipelineDesignFactor", 0.72);
    designFactors.addProperty("vesselDesignPressureMargin", 1.10);
    designFactors.addProperty("deepwaterCorrectionFactor", 1.15);
    profile.add("designFactors", designFactors);

    JsonObject rules = new JsonObject();
    rules.addProperty("maxWaterDepth_m", 3000.0);
    rules.addProperty("maxOperatingPressure_bara", 690.0);
    rules.addProperty("requireDeepwaterAnalysis", true);
    rules.addProperty("requireRiserAnalysis", true);
    profile.add("validationRules", rules);

    return profile;
  }

  /**
   * Generic international standards profile.
   *
   * @return generic profile
   */
  private static JsonObject buildGenericProfile() {
    JsonObject profile = new JsonObject();
    profile.addProperty("name", "generic");
    profile.addProperty("description", "Generic international standards — ISO, API, ASME");
    profile.addProperty("jurisdiction", "International");
    profile.addProperty("regulator", "None (international best practice)");

    JsonArray standards = new JsonArray();
    standards.add("ISO 13623 (Petroleum Pipelines)");
    standards.add("ISO 3183 (Line Pipe)");
    standards.add("API 5L (Line Pipe)");
    standards.add("ASME VIII Div.1 (Pressure Vessels)");
    standards.add("ASME B31.3 (Process Piping)");
    standards.add("API 521 (Pressure-Relieving Systems)");
    standards.add("API 520 (Sizing of Relief Devices)");
    profile.add("standards", standards);

    JsonObject designFactors = new JsonObject();
    designFactors.addProperty("pipelineDesignFactor", 0.72);
    designFactors.addProperty("vesselDesignPressureMargin", 1.10);
    designFactors.addProperty("corrosionAllowanceMm", 3.0);
    profile.add("designFactors", designFactors);

    profile.add("equipmentStandards", buildGenericEquipmentStandards());

    JsonObject rules = new JsonObject();
    rules.addProperty("maxOperatingPressure_bara", 690.0);
    rules.addProperty("maxOperatingTemperature_C", 350.0);
    rules.addProperty("requireHydrateCheck", true);
    profile.add("validationRules", rules);

    return profile;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Equipment-to-standard mappings per jurisdiction
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * NCS equipment standards.
   *
   * @return equipment standards map
   */
  private static JsonObject buildNcsEquipmentStandards() {
    JsonObject map = new JsonObject();
    map.add("separator", toJsonArray("NORSOK P-001", "ASME VIII Div.1", "DNV-RP-C203"));
    map.add("pipeline", toJsonArray("DNV-ST-F101", "NORSOK L-001", "ISO 13623"));
    map.add("compressor", toJsonArray("API 617", "NORSOK P-001", "API 614"));
    map.add("heatexchanger", toJsonArray("TEMA", "ASME VIII Div.1", "NORSOK P-001"));
    map.add("vessel", toJsonArray("ASME VIII Div.1", "NORSOK P-001", "NORSOK M-001"));
    map.add("valve", toJsonArray("NORSOK L-001", "API 6D", "API 6A"));
    map.add("well", toJsonArray("NORSOK D-010", "API 5CT", "API Bull 5C3"));
    return map;
  }

  /**
   * UKCS equipment standards.
   *
   * @return equipment standards map
   */
  private static JsonObject buildUkcsEquipmentStandards() {
    JsonObject map = new JsonObject();
    map.add("separator", toJsonArray("BS PD 5500", "ASME VIII Div.1"));
    map.add("pipeline", toJsonArray("PD 8010", "API 5L", "BS EN 14161"));
    map.add("compressor", toJsonArray("API 617", "API 614"));
    map.add("heatexchanger", toJsonArray("TEMA", "BS EN 13445"));
    map.add("vessel", toJsonArray("BS PD 5500", "ASME VIII Div.1"));
    map.add("valve", toJsonArray("API 6D", "BS EN ISO 17292"));
    return map;
  }

  /**
   * GoM equipment standards.
   *
   * @return equipment standards map
   */
  private static JsonObject buildGomEquipmentStandards() {
    JsonObject map = new JsonObject();
    map.add("separator", toJsonArray("ASME VIII Div.1", "API RP 14C"));
    map.add("pipeline", toJsonArray("ASME B31.4", "ASME B31.8", "API 5L", "30 CFR 250"));
    map.add("compressor", toJsonArray("API 617", "API 614", "API RP 14C"));
    map.add("heatexchanger", toJsonArray("TEMA", "ASME VIII Div.1"));
    map.add("vessel", toJsonArray("ASME VIII Div.1", "API RP 14C"));
    map.add("valve", toJsonArray("API 6D", "API 6A", "30 CFR 250"));
    map.add("well", toJsonArray("API 5CT", "API 6A", "API 17D", "30 CFR 250"));
    return map;
  }

  /**
   * Generic equipment standards.
   *
   * @return equipment standards map
   */
  private static JsonObject buildGenericEquipmentStandards() {
    JsonObject map = new JsonObject();
    map.add("separator", toJsonArray("ASME VIII Div.1"));
    map.add("pipeline", toJsonArray("ISO 13623", "API 5L", "ASME B31.3"));
    map.add("compressor", toJsonArray("API 617"));
    map.add("heatexchanger", toJsonArray("TEMA", "ASME VIII Div.1"));
    map.add("vessel", toJsonArray("ASME VIII Div.1"));
    map.add("valve", toJsonArray("API 6D"));
    return map;
  }

  /**
   * Returns generic standards for an equipment type (fallback).
   *
   * @param equipmentType the equipment type
   * @return JSON array of standard names
   */
  private static JsonArray getGenericStandards(String equipmentType) {
    JsonObject generic = buildGenericEquipmentStandards();
    if (generic.has(equipmentType)) {
      return generic.getAsJsonArray(equipmentType);
    }
    JsonArray fallback = new JsonArray();
    fallback.add("No specific standards mapped for: " + equipmentType);
    fallback.add("Check ASME or API standards catalogs");
    return fallback;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Helpers
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Converts a list of strings to a JsonArray.
   *
   * @param values the string values
   * @return JSON array
   */
  private static JsonArray toJsonArray(String... values) {
    JsonArray arr = new JsonArray();
    for (String v : values) {
      arr.add(v);
    }
    return arr;
  }

  /**
   * Creates a standard error JSON response.
   *
   * @param code the error code
   * @param message the error message
   * @param remediation how to fix
   * @return the error JSON string
   */
  private static String errorJson(String code, String message, String remediation) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    JsonArray errors = new JsonArray();
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    err.addProperty("remediation", remediation);
    errors.add(err);
    error.add("errors", errors);
    return GSON.toJson(error);
  }
}
