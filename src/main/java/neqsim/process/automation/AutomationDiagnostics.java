package neqsim.process.automation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Provides self-diagnosis, fuzzy matching, and error-recovery capabilities for the
 * {@link ProcessAutomation} API. When an agent uses an incorrect address, unit name, or property,
 * this class supplies structured remediation hints including closest-match suggestions and
 * auto-correction.
 *
 * <p>
 * Also tracks operation history to learn from failures and provide accumulated insights.
 * </p>
 *
 * <h2>Key Capabilities:</h2>
 * <ul>
 * <li><strong>Fuzzy name resolution</strong> &mdash; finds closest unit/property when exact match
 * fails</li>
 * <li><strong>Auto-correction</strong> &mdash; fixes common mistakes (casing, whitespace, partial
 * names)</li>
 * <li><strong>Structured diagnostics</strong> &mdash; returns JSON with error category,
 * suggestions, and fix actions</li>
 * <li><strong>Operation history</strong> &mdash; tracks successes/failures to improve over
 * time</li>
 * <li><strong>Physical validation</strong> &mdash; warns when values are outside reasonable
 * bounds</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class AutomationDiagnostics implements Serializable {
  private static final long serialVersionUID = 1000L;

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Classification of automation errors for structured remediation.
   */
  public enum ErrorCategory {
    /** Unit name not found in the process. */
    UNIT_NOT_FOUND,
    /** Property/variable address not found on the unit. */
    PROPERTY_NOT_FOUND,
    /** Stream port not found on the unit. */
    PORT_NOT_FOUND,
    /** Variable is read-only but write was attempted. */
    READ_ONLY_VARIABLE,
    /** Value is outside physically reasonable bounds. */
    VALUE_OUT_OF_BOUNDS,
    /** Unit of measurement not recognized. */
    UNKNOWN_UNIT,
    /** Address format is invalid. */
    INVALID_ADDRESS_FORMAT,
    /** Simulation did not converge after value change. */
    CONVERGENCE_FAILURE
  }

  /**
   * A diagnostic result returned when an operation fails, containing the error analysis, closest
   * suggestions, and auto-correction attempt.
   */
  public static class DiagnosticResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final ErrorCategory category;
    private final String originalInput;
    private final String errorMessage;
    private final List<String> suggestions;
    private final String autoCorrection;
    private final String remediation;
    private final Map<String, Object> context;

    /**
     * Creates a diagnostic result.
     *
     * @param category the error category
     * @param originalInput what the agent provided
     * @param errorMessage human-readable error description
     * @param suggestions ranked list of closest valid alternatives
     * @param autoCorrection the auto-corrected value (null if not possible)
     * @param remediation actionable fix instruction for the agent
     * @param context additional context information
     */
    public DiagnosticResult(ErrorCategory category, String originalInput, String errorMessage,
        List<String> suggestions, String autoCorrection, String remediation,
        Map<String, Object> context) {
      this.category = category;
      this.originalInput = originalInput;
      this.errorMessage = errorMessage;
      this.suggestions = suggestions != null ? suggestions : new ArrayList<String>();
      this.autoCorrection = autoCorrection;
      this.remediation = remediation;
      this.context = context != null ? context : new LinkedHashMap<String, Object>();
    }

    /**
     * Gets the error category.
     *
     * @return the error category
     */
    public ErrorCategory getCategory() {
      return category;
    }

    /**
     * Gets the original input that caused the error.
     *
     * @return the original input string
     */
    public String getOriginalInput() {
      return originalInput;
    }

    /**
     * Gets the error message.
     *
     * @return human-readable error message
     */
    public String getErrorMessage() {
      return errorMessage;
    }

    /**
     * Gets the ranked suggestions for closest valid alternatives.
     *
     * @return list of suggestions, closest match first
     */
    public List<String> getSuggestions() {
      return Collections.unmodifiableList(suggestions);
    }

    /**
     * Gets the auto-corrected value, or null if auto-correction was not possible.
     *
     * @return the auto-corrected value, or null
     */
    public String getAutoCorrection() {
      return autoCorrection;
    }

    /**
     * Gets the actionable remediation instruction.
     *
     * @return remediation instruction for the agent
     */
    public String getRemediation() {
      return remediation;
    }

    /**
     * Gets additional context about the error.
     *
     * @return context map
     */
    public Map<String, Object> getContext() {
      return Collections.unmodifiableMap(context);
    }

    /**
     * Returns true if an auto-correction was found.
     *
     * @return true if autoCorrection is not null
     */
    public boolean hasAutoCorrection() {
      return autoCorrection != null;
    }

    /**
     * Serializes this diagnostic result to JSON.
     *
     * @return JSON string
     */
    public String toJson() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("category", category.name());
      map.put("originalInput", originalInput);
      map.put("errorMessage", errorMessage);
      map.put("suggestions", suggestions);
      map.put("autoCorrection", autoCorrection);
      map.put("remediation", remediation);
      map.put("context", context);
      return GSON.toJson(map);
    }
  }

  /**
   * Tracks a single automation operation for learning.
   */
  static class OperationRecord implements Serializable {
    private static final long serialVersionUID = 1000L;

    final String operationType;
    final String address;
    final boolean success;
    final String errorCategory;
    final String correction;
    final long timestamp;

    /**
     * Creates an operation record.
     *
     * @param operationType the type of operation (get, set, list)
     * @param address the address used
     * @param success whether the operation succeeded
     * @param errorCategory the error category if failed, null if succeeded
     * @param correction the correction applied, null if none
     */
    OperationRecord(String operationType, String address, boolean success, String errorCategory,
        String correction) {
      this.operationType = operationType;
      this.address = address;
      this.success = success;
      this.errorCategory = errorCategory;
      this.correction = correction;
      this.timestamp = System.currentTimeMillis();
    }
  }

  private final List<OperationRecord> history;
  private final Map<String, String> learnedCorrections;
  private int maxHistorySize;

  /**
   * Creates an AutomationDiagnostics instance with default history size (1000).
   */
  public AutomationDiagnostics() {
    this(1000);
  }

  /**
   * Creates an AutomationDiagnostics instance with a specified history size.
   *
   * @param maxHistorySize maximum number of operation records to keep
   */
  public AutomationDiagnostics(int maxHistorySize) {
    this.history = new ArrayList<OperationRecord>();
    this.learnedCorrections = new LinkedHashMap<String, String>();
    this.maxHistorySize = maxHistorySize;
  }

  // ========================== Fuzzy Matching ==========================

  /**
   * Finds the closest matching unit name from a list of valid names using edit distance.
   *
   * @param input the input name that was not found
   * @param validNames the list of valid names to search
   * @param maxResults maximum number of suggestions to return
   * @return ranked list of closest matches
   */
  public List<String> findClosestNames(String input, List<String> validNames, int maxResults) {
    if (input == null || validNames == null || validNames.isEmpty()) {
      return new ArrayList<String>();
    }

    String inputLower = input.toLowerCase().trim();
    List<ScoredName> scored = new ArrayList<ScoredName>();

    for (String valid : validNames) {
      double score = computeMatchScore(inputLower, valid.toLowerCase().trim());
      scored.add(new ScoredName(valid, score));
    }

    Collections.sort(scored);

    List<String> results = new ArrayList<String>();
    int count = Math.min(maxResults, scored.size());
    for (int i = 0; i < count; i++) {
      if (scored.get(i).score > 0.0) {
        results.add(scored.get(i).name);
      }
    }
    return results;
  }

  /**
   * Attempts auto-correction of a unit name by trying common transformations.
   *
   * <p>
   * Tries: case-insensitive match, trimmed whitespace, partial substring match, and previously
   * learned corrections.
   * </p>
   *
   * @param input the unit name to correct
   * @param validNames the list of valid names
   * @return the corrected name, or null if no confident correction found
   */
  public String autoCorrectName(String input, List<String> validNames) {
    if (input == null || validNames == null) {
      return null;
    }

    // Check learned corrections first
    String learned = learnedCorrections.get(input.toLowerCase().trim());
    if (learned != null && validNames.contains(learned)) {
      return learned;
    }

    String trimmed = input.trim();
    // Exact match after trim
    for (String valid : validNames) {
      if (valid.equals(trimmed)) {
        return valid;
      }
    }

    // Case-insensitive match
    for (String valid : validNames) {
      if (valid.equalsIgnoreCase(trimmed)) {
        return valid;
      }
    }

    // Whitespace-normalized match (e.g., "HP  Sep" -> "HP Sep")
    String normalized = trimmed.replaceAll("\\s+", " ");
    for (String valid : validNames) {
      if (valid.replaceAll("\\s+", " ").equalsIgnoreCase(normalized)) {
        return valid;
      }
    }

    // Substring containment (input is a substring of a valid name or vice versa)
    List<String> substringMatches = new ArrayList<String>();
    for (String valid : validNames) {
      if (valid.toLowerCase().contains(trimmed.toLowerCase())
          || trimmed.toLowerCase().contains(valid.toLowerCase())) {
        substringMatches.add(valid);
      }
    }
    if (substringMatches.size() == 1) {
      return substringMatches.get(0);
    }

    // Very close edit distance (1 edit for short names, 2 for longer)
    int maxDist = trimmed.length() <= 5 ? 1 : 2;
    String bestMatch = null;
    int bestDist = Integer.MAX_VALUE;
    for (String valid : validNames) {
      int dist = editDistance(trimmed.toLowerCase(), valid.toLowerCase());
      if (dist <= maxDist && dist < bestDist) {
        bestDist = dist;
        bestMatch = valid;
      }
    }
    return bestMatch;
  }

  // ========================== Diagnostics ==========================

  /**
   * Diagnoses a unit-not-found error and returns structured remediation.
   *
   * @param unitName the name that was not found
   * @param validUnits list of valid unit names in the process
   * @return diagnostic result with suggestions and auto-correction
   */
  public DiagnosticResult diagnoseUnitNotFound(String unitName, List<String> validUnits) {
    String corrected = autoCorrectName(unitName, validUnits);
    List<String> suggestions = findClosestNames(unitName, validUnits, 5);

    String remediation;
    if (corrected != null) {
      remediation = "Auto-corrected to '" + corrected
          + "'. Use this name in future calls. Available units: " + validUnits;
    } else if (!suggestions.isEmpty()) {
      remediation = "Unit '" + unitName + "' not found. Did you mean: " + suggestions
          + "? Use listSimulationUnits to discover valid names.";
    } else {
      remediation = "Unit '" + unitName + "' not found. Available units: " + validUnits
          + ". Use listSimulationUnits to discover names.";
    }

    Map<String, Object> context = new LinkedHashMap<String, Object>();
    context.put("availableUnits", validUnits);
    context.put("unitCount", validUnits.size());

    return new DiagnosticResult(ErrorCategory.UNIT_NOT_FOUND, unitName,
        "Unit '" + unitName + "' not found in the process", suggestions, corrected, remediation,
        context);
  }

  /**
   * Diagnoses a property-not-found error and returns structured remediation.
   *
   * @param address the full address that was not resolved
   * @param unitName the unit name
   * @param propertyName the property that was not found
   * @param validVariables the valid variables for that unit
   * @return diagnostic result with suggestions and auto-correction
   */
  public DiagnosticResult diagnosePropertyNotFound(String address, String unitName,
      String propertyName, List<SimulationVariable> validVariables) {
    List<String> propertyNames = new ArrayList<String>();
    for (SimulationVariable v : validVariables) {
      propertyNames.add(v.getName());
    }
    List<String> addressNames = new ArrayList<String>();
    for (SimulationVariable v : validVariables) {
      addressNames.add(v.getAddress());
    }

    String corrected = autoCorrectName(propertyName, propertyNames);
    List<String> suggestions = findClosestNames(propertyName, propertyNames, 5);

    // Also check address-based suggestions
    List<String> addressSuggestions = findClosestNames(address, addressNames, 3);

    String correctedAddress = null;
    if (corrected != null) {
      // Find the full address that uses this property name
      for (SimulationVariable v : validVariables) {
        if (v.getName().equals(corrected)) {
          correctedAddress = v.getAddress();
          break;
        }
      }
    }

    String remediation;
    if (correctedAddress != null) {
      remediation = "Auto-corrected property to '" + correctedAddress
          + "'. Use listUnitVariables('" + unitName + "') to see all variables.";
    } else if (!suggestions.isEmpty()) {
      remediation = "Property '" + propertyName + "' not found on unit '" + unitName
          + "'. Did you mean: " + suggestions + "? Use listUnitVariables('" + unitName
          + "') for valid addresses.";
    } else {
      remediation = "Property '" + propertyName + "' not found on unit '" + unitName
          + "'. Valid addresses: " + addressNames;
    }

    Map<String, Object> context = new LinkedHashMap<String, Object>();
    context.put("unitName", unitName);
    context.put("validVariableCount", validVariables.size());
    if (!addressSuggestions.isEmpty()) {
      context.put("closestAddresses", addressSuggestions);
    }

    return new DiagnosticResult(ErrorCategory.PROPERTY_NOT_FOUND, address,
        "Property '" + propertyName + "' not found on unit '" + unitName + "'", suggestions,
        correctedAddress, remediation, context);
  }

  /**
   * Diagnoses a port-not-found error.
   *
   * @param address the full address
   * @param unitName the unit name
   * @param portName the port that was not found
   * @param validPorts known valid port names for the equipment type
   * @return diagnostic result with suggestions
   */
  public DiagnosticResult diagnosePortNotFound(String address, String unitName, String portName,
      List<String> validPorts) {
    String corrected = autoCorrectName(portName, validPorts);
    List<String> suggestions = findClosestNames(portName, validPorts, 5);

    String remediation;
    if (corrected != null) {
      remediation = "Auto-corrected port to '" + corrected + "'. Valid ports for this unit: "
          + validPorts;
    } else {
      remediation = "Port '" + portName + "' not found on unit '" + unitName + "'. Valid ports: "
          + validPorts + ". Use listUnitVariables('" + unitName + "') to see all addresses.";
    }

    Map<String, Object> context = new LinkedHashMap<String, Object>();
    context.put("unitName", unitName);
    context.put("validPorts", validPorts);

    return new DiagnosticResult(ErrorCategory.PORT_NOT_FOUND, address,
        "Stream port '" + portName + "' not found on unit '" + unitName + "'", suggestions,
        corrected, remediation, context);
  }

  /**
   * Validates a value against known physical bounds for a property.
   *
   * @param propertyName the property being set
   * @param value the value to validate
   * @param unit the unit of the value
   * @return diagnostic result if out of bounds, null if valid
   */
  public DiagnosticResult validatePhysicalBounds(String propertyName, double value, String unit) {
    PhysicalBound bound = getPhysicalBound(propertyName, unit);
    if (bound == null) {
      return null;
    }

    if (value < bound.hardMin || value > bound.hardMax) {
      String remediation = "Value " + value + " " + unit + " for '" + propertyName
          + "' is outside physical limits [" + bound.hardMin + ", " + bound.hardMax + "] " + unit
          + ". Check the value and unit.";
      Map<String, Object> context = new LinkedHashMap<String, Object>();
      context.put("hardMin", bound.hardMin);
      context.put("hardMax", bound.hardMax);
      context.put("softMin", bound.softMin);
      context.put("softMax", bound.softMax);
      context.put("unit", unit);
      return new DiagnosticResult(ErrorCategory.VALUE_OUT_OF_BOUNDS, String.valueOf(value),
          "Value " + value + " " + unit + " is outside physical limits for " + propertyName,
          new ArrayList<String>(), null, remediation, context);
    }

    if (value < bound.softMin || value > bound.softMax) {
      String remediation = "Value " + value + " " + unit + " for '" + propertyName
          + "' is unusual (typical range: [" + bound.softMin + ", " + bound.softMax + "] " + unit
          + "). This may be intentional for special cases.";
      Map<String, Object> context = new LinkedHashMap<String, Object>();
      context.put("hardMin", bound.hardMin);
      context.put("hardMax", bound.hardMax);
      context.put("softMin", bound.softMin);
      context.put("softMax", bound.softMax);
      context.put("unit", unit);
      context.put("severity", "WARNING");
      return new DiagnosticResult(ErrorCategory.VALUE_OUT_OF_BOUNDS, String.valueOf(value),
          "Value " + value + " " + unit + " is unusual for " + propertyName,
          new ArrayList<String>(), null, remediation, context);
    }

    return null;
  }

  // ========================== Operation Tracking ==========================

  /**
   * Records a successful operation.
   *
   * @param operationType the type of operation (get, set, list)
   * @param address the address used
   */
  public void recordSuccess(String operationType, String address) {
    addRecord(new OperationRecord(operationType, address, true, null, null));
  }

  /**
   * Records a failed operation with its correction.
   *
   * @param operationType the type of operation (get, set, list)
   * @param address the address that failed
   * @param category the error category
   * @param correction the correction that was applied, or null
   */
  public void recordFailure(String operationType, String address, ErrorCategory category,
      String correction) {
    addRecord(new OperationRecord(operationType, address, false, category.name(), correction));
    if (correction != null) {
      learnedCorrections.put(address.toLowerCase().trim(), correction);
    }
  }

  /**
   * Gets the success rate for recent operations.
   *
   * @return success rate as fraction (0.0 to 1.0)
   */
  public double getSuccessRate() {
    if (history.isEmpty()) {
      return 1.0;
    }
    int successes = 0;
    for (OperationRecord r : history) {
      if (r.success) {
        successes++;
      }
    }
    return (double) successes / history.size();
  }

  /**
   * Gets the most common error categories from operation history.
   *
   * @return map of error category to count, sorted by frequency
   */
  public Map<String, Integer> getErrorCategoryCounts() {
    Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    for (OperationRecord r : history) {
      if (!r.success && r.errorCategory != null) {
        Integer prev = counts.get(r.errorCategory);
        counts.put(r.errorCategory, prev != null ? prev + 1 : 1);
      }
    }
    return counts;
  }

  /**
   * Gets the learned corrections that can be applied automatically in future calls.
   *
   * @return unmodifiable map of input to corrected values
   */
  public Map<String, String> getLearnedCorrections() {
    return Collections.unmodifiableMap(learnedCorrections);
  }

  /**
   * Gets the total number of operations recorded.
   *
   * @return operation count
   */
  public int getOperationCount() {
    return history.size();
  }

  /**
   * Generates a learning report summarizing patterns from operation history.
   *
   * @return JSON string with operation statistics and learned corrections
   */
  public String getLearningReport() {
    Map<String, Object> report = new LinkedHashMap<String, Object>();
    report.put("totalOperations", history.size());
    report.put("successRate", getSuccessRate());
    report.put("errorCategories", getErrorCategoryCounts());
    report.put("learnedCorrections", learnedCorrections);
    report.put("recentFailures", getRecentFailures(10));
    report.put("recommendations", generateRecommendations());
    return GSON.toJson(report);
  }

  /**
   * Clears the operation history and learned corrections.
   */
  public void reset() {
    history.clear();
    learnedCorrections.clear();
  }

  // ========================== Private Helpers ==========================

  /**
   * Computes a match score between two strings (higher is better).
   *
   * @param input the input string (already lowercased)
   * @param valid the valid string (already lowercased)
   * @return score from 0.0 (no match) to 1.0 (exact match)
   */
  private double computeMatchScore(String input, String valid) {
    if (input.equals(valid)) {
      return 1.0;
    }

    // Substring containment bonus
    double substringBonus = 0.0;
    if (valid.contains(input)) {
      substringBonus = 0.5 * ((double) input.length() / valid.length());
    } else if (input.contains(valid)) {
      substringBonus = 0.5 * ((double) valid.length() / input.length());
    }

    // Edit distance score
    int maxLen = Math.max(input.length(), valid.length());
    if (maxLen == 0) {
      return 0.0;
    }
    int dist = editDistance(input, valid);
    double editScore = 1.0 - ((double) dist / maxLen);

    // Common token overlap (split on spaces, dots, underscores)
    String[] inputTokens = input.split("[\\s._-]+");
    String[] validTokens = valid.split("[\\s._-]+");
    int commonTokens = 0;
    for (String it : inputTokens) {
      for (String vt : validTokens) {
        if (it.equals(vt)) {
          commonTokens++;
          break;
        }
      }
    }
    double tokenScore =
        inputTokens.length > 0 ? (double) commonTokens / Math.max(inputTokens.length,
            validTokens.length) : 0.0;

    return Math.max(editScore, Math.max(substringBonus, tokenScore));
  }

  /**
   * Computes the Levenshtein edit distance between two strings.
   *
   * @param a first string
   * @param b second string
   * @return minimum number of edits (insert, delete, substitute)
   */
  static int editDistance(String a, String b) {
    int lenA = a.length();
    int lenB = b.length();
    int[][] dp = new int[lenA + 1][lenB + 1];
    for (int i = 0; i <= lenA; i++) {
      dp[i][0] = i;
    }
    for (int j = 0; j <= lenB; j++) {
      dp[0][j] = j;
    }
    for (int i = 1; i <= lenA; i++) {
      for (int j = 1; j <= lenB; j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        dp[i][j] = Math.min(dp[i - 1][j] + 1, Math.min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost));
      }
    }
    return dp[lenA][lenB];
  }

  /**
   * Adds an operation record to history, trimming if over capacity.
   *
   * @param record the record to add
   */
  private void addRecord(OperationRecord record) {
    history.add(record);
    while (history.size() > maxHistorySize) {
      history.remove(0);
    }
  }

  /**
   * Gets the most recent failures for diagnostics.
   *
   * @param count maximum number of failures to return
   * @return list of recent failure descriptions
   */
  private List<Map<String, String>> getRecentFailures(int count) {
    List<Map<String, String>> failures = new ArrayList<Map<String, String>>();
    int added = 0;
    for (int i = history.size() - 1; i >= 0 && added < count; i--) {
      OperationRecord r = history.get(i);
      if (!r.success) {
        Map<String, String> entry = new LinkedHashMap<String, String>();
        entry.put("operation", r.operationType);
        entry.put("address", r.address);
        entry.put("errorCategory", r.errorCategory);
        if (r.correction != null) {
          entry.put("correction", r.correction);
        }
        failures.add(entry);
        added++;
      }
    }
    return failures;
  }

  /**
   * Generates recommendations based on failure patterns.
   *
   * @return list of recommendation strings
   */
  private List<String> generateRecommendations() {
    List<String> recommendations = new ArrayList<String>();
    Map<String, Integer> errors = getErrorCategoryCounts();

    Integer unitNotFound = errors.get(ErrorCategory.UNIT_NOT_FOUND.name());
    if (unitNotFound != null && unitNotFound > 2) {
      recommendations.add(
          "Frequent unit-not-found errors (" + unitNotFound + "x). "
              + "Always call listSimulationUnits first to discover valid names.");
    }

    Integer propNotFound = errors.get(ErrorCategory.PROPERTY_NOT_FOUND.name());
    if (propNotFound != null && propNotFound > 2) {
      recommendations.add(
          "Frequent property-not-found errors (" + propNotFound + "x). "
              + "Call listUnitVariables(unitName) before accessing variables.");
    }

    Integer outOfBounds = errors.get(ErrorCategory.VALUE_OUT_OF_BOUNDS.name());
    if (outOfBounds != null && outOfBounds > 1) {
      recommendations.add(
          "Values out of bounds detected (" + outOfBounds + "x). "
              + "Check units match the expected unit (e.g., Kelvin vs Celsius).");
    }

    if (!learnedCorrections.isEmpty()) {
      recommendations.add(
          "Learned " + learnedCorrections.size() + " corrections automatically. "
              + "These will be applied in future calls.");
    }

    if (recommendations.isEmpty() && !history.isEmpty()) {
      double rate = getSuccessRate();
      if (rate > 0.9) {
        recommendations.add("Operations running well (success rate: "
            + String.format("%.0f%%", rate * 100) + ").");
      }
    }

    return recommendations;
  }

  /**
   * Represents physical bounds for a property value.
   */
  private static class PhysicalBound {
    final double hardMin;
    final double hardMax;
    final double softMin;
    final double softMax;

    /**
     * Creates a physical bound.
     *
     * @param hardMin absolute minimum (physically impossible below this)
     * @param softMin typical minimum (unusual below this)
     * @param softMax typical maximum (unusual above this)
     * @param hardMax absolute maximum (physically impossible above this)
     */
    PhysicalBound(double hardMin, double softMin, double softMax, double hardMax) {
      this.hardMin = hardMin;
      this.softMin = softMin;
      this.softMax = softMax;
      this.hardMax = hardMax;
    }
  }

  /**
   * Returns physical bounds for common properties.
   *
   * @param propertyName the property name
   * @param unit the unit of measurement
   * @return physical bounds, or null if unknown
   */
  private PhysicalBound getPhysicalBound(String propertyName, String unit) {
    if (propertyName == null || unit == null) {
      return null;
    }
    String prop = propertyName.toLowerCase();
    String u = unit.toLowerCase().trim();

    // Temperature
    if (prop.contains("temperature")) {
      if ("c".equals(u)) {
        return new PhysicalBound(-273.15, -100.0, 600.0, 3000.0);
      }
      if ("k".equals(u)) {
        return new PhysicalBound(0.0, 173.15, 873.15, 3273.15);
      }
      if ("f".equals(u)) {
        return new PhysicalBound(-459.67, -148.0, 1112.0, 5432.0);
      }
    }

    // Pressure
    if (prop.contains("pressure")) {
      if ("bara".equals(u) || "bar".equals(u)) {
        return new PhysicalBound(0.0, 0.5, 700.0, 10000.0);
      }
      if ("pa".equals(u)) {
        return new PhysicalBound(0.0, 50000.0, 70000000.0, 1000000000.0);
      }
      if ("psi".equals(u)) {
        return new PhysicalBound(0.0, 7.0, 10000.0, 145000.0);
      }
    }

    // Flow rate
    if (prop.contains("flowrate") || prop.contains("flow")) {
      return new PhysicalBound(0.0, 0.0, 1.0E9, 1.0E12);
    }

    // Efficiency
    if (prop.contains("efficiency")) {
      return new PhysicalBound(0.0, 0.3, 1.0, 1.0);
    }

    // Valve opening
    if (prop.contains("valveopening") || prop.contains("opening")) {
      return new PhysicalBound(0.0, 0.0, 100.0, 100.0);
    }

    return null;
  }

  /**
   * Helper for sorting names by match score.
   */
  private static class ScoredName implements Comparable<ScoredName> {
    final String name;
    final double score;

    /**
     * Creates a scored name entry.
     *
     * @param name the name
     * @param score the match score
     */
    ScoredName(String name, double score) {
      this.name = name;
      this.score = score;
    }

    @Override
    public int compareTo(ScoredName other) {
      return Double.compare(other.score, this.score);
    }
  }
}
