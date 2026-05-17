package neqsim.process.chemistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encodes a single rule of chemical interaction or chemical / fluid / material incompatibility.
 *
 * <p>
 * Rules are loaded from {@code src/main/resources/data/chemical_compatibility_rules.csv} via
 * {@link #loadDefaultRules()}. Each rule has two operands and an interaction context, plus a
 * severity classification and a recommended mitigation. Wildcards ({@code *}) match any value for
 * an operand field.
 * </p>
 *
 * <p>
 * Severity grading:
 * </p>
 * <ul>
 * <li>{@link Severity#HIGH} — incompatible, do not co-inject without bench validation.</li>
 * <li>{@link Severity#MEDIUM} — caution, expected performance loss or process upset.</li>
 * <li>{@link Severity#LOW} — possible interaction, confirm by lab test.</li>
 * <li>{@link Severity#INFO} — informational, no action required.</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class ChemicalInteractionRule implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Default resource path for the bundled rules CSV. */
  public static final String DEFAULT_RESOURCE = "/data/chemical_compatibility_rules.csv";

  /**
   * Severity classification of a chemical interaction.
   */
  public enum Severity {
    /** Incompatible — do not use together. */
    HIGH,
    /** Caution — verify before use. */
    MEDIUM,
    /** Low risk — bench test recommended. */
    LOW,
    /** Informational — no action. */
    INFO;
  }

  // Fields
  private final String chemical1Type;
  private final String chemical1Ingredient;
  private final String chemical2Type;
  private final String chemical2Ingredient;
  private final String condition;
  private final Severity severity;
  private final String mechanism;
  private final String mitigation;

  /**
   * Constructs a rule.
   *
   * @param chemical1Type type of operand 1 (or wildcard {@code *})
   * @param chemical1Ingredient ingredient family of operand 1 (or wildcard)
   * @param chemical2Type type of operand 2 (or wildcard)
   * @param chemical2Ingredient ingredient family of operand 2 (or wildcard)
   * @param condition free-text condition tag (e.g. {@code co_injection}, {@code produced_water},
   *        {@code operating})
   * @param severity severity classification
   * @param mechanism description of the underlying mechanism
   * @param mitigation recommended mitigation
   */
  public ChemicalInteractionRule(String chemical1Type, String chemical1Ingredient,
      String chemical2Type, String chemical2Ingredient, String condition, Severity severity,
      String mechanism, String mitigation) {
    this.chemical1Type = chemical1Type;
    this.chemical1Ingredient = chemical1Ingredient;
    this.chemical2Type = chemical2Type;
    this.chemical2Ingredient = chemical2Ingredient;
    this.condition = condition;
    this.severity = severity;
    this.mechanism = mechanism;
    this.mitigation = mitigation;
  }

  // ─── Loaders ────────────────────────────────────────────

  /**
   * Loads the default rule set from the bundled resource.
   *
   * @return list of rules (empty if the resource cannot be loaded)
   */
  public static List<ChemicalInteractionRule> loadDefaultRules() {
    return loadFromResource(DEFAULT_RESOURCE);
  }

  /**
   * Loads rules from an arbitrary classpath resource.
   *
   * @param resource path to the CSV resource
   * @return list of parsed rules
   */
  public static List<ChemicalInteractionRule> loadFromResource(String resource) {
    List<ChemicalInteractionRule> rules = new ArrayList<ChemicalInteractionRule>();
    InputStream stream = ChemicalInteractionRule.class.getResourceAsStream(resource);
    if (stream == null) {
      return rules;
    }
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8")));
    try {
      String line = reader.readLine(); // header
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        String[] parts = line.split(",", -1);
        if (parts.length < 8) {
          continue;
        }
        Severity severity;
        try {
          severity = Severity.valueOf(parts[5].trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
          severity = Severity.INFO;
        }
        rules.add(new ChemicalInteractionRule(parts[0].trim(), parts[1].trim(), parts[2].trim(),
            parts[3].trim(), parts[4].trim(), severity, parts[6].trim(), parts[7].trim()));
      }
    } catch (IOException ex) {
      // log and continue with what we have
    } finally {
      try {
        reader.close();
      } catch (IOException ignored) {
        // ignored
      }
    }
    return rules;
  }

  // ─── Matching ───────────────────────────────────────────

  /**
   * Tests whether this rule matches the ordered pair of chemicals (a, b). Matching is symmetric:
   * the rule matches if (a, b) or (b, a) satisfy the operand constraints.
   *
   * @param a first chemical
   * @param b second chemical (may be null for unary rules)
   * @return true if the rule applies
   */
  public boolean matches(ProductionChemical a, ProductionChemical b) {
    if (b == null) {
      return matchOperand(chemical1Type, chemical1Ingredient, a);
    }
    boolean forward = matchOperand(chemical1Type, chemical1Ingredient, a)
        && matchOperand(chemical2Type, chemical2Ingredient, b);
    boolean reverse = matchOperand(chemical1Type, chemical1Ingredient, b)
        && matchOperand(chemical2Type, chemical2Ingredient, a);
    return forward || reverse;
  }

  /**
   * Tests whether the rule's second operand encodes a fluid / operating condition (rather than a
   * chemical type), and the condition is satisfied. Recognised tags include {@code water_high_*}
   * and {@code material_*}.
   *
   * @return true if operand 2 is an environment tag
   */
  public boolean isEnvironmentRule() {
    return chemical2Type != null
        && (chemical2Type.startsWith("water_") || chemical2Type.startsWith("material_"));
  }

  /**
   * Returns true if this rule's environment operand matches the supplied environment fields.
   *
   * @param temperatureC operating temperature in Celsius
   * @param calciumMgL calcium concentration in mg/L
   * @param ironMgL iron concentration in mg/L
   * @param bicarbonateMgL bicarbonate concentration in mg/L
   * @param material material identifier (e.g. {@code carbon_steel}, {@code 316L})
   * @return true if the environment matches
   */
  public boolean environmentMatches(double temperatureC, double calciumMgL, double ironMgL,
      double bicarbonateMgL, String material) {
    if (!isEnvironmentRule()) {
      return false;
    }
    String tag = chemical2Type;
    String thresholdSpec = chemical2Ingredient == null ? "" : chemical2Ingredient.trim();
    if ("water_high_temperature".equals(tag)) {
      return checkThreshold(thresholdSpec, "T", temperatureC);
    }
    if ("water_high_calcium".equals(tag)) {
      return checkThreshold(thresholdSpec, "Ca", calciumMgL);
    }
    if ("water_high_iron".equals(tag)) {
      return checkThreshold(thresholdSpec, "Fe", ironMgL);
    }
    if ("water_high_carbonate".equals(tag)) {
      return checkThreshold(thresholdSpec, "HCO3", bicarbonateMgL);
    }
    if ("material_carbon_steel".equals(tag)) {
      if (material == null || !material.toLowerCase().contains("carbon")) {
        return false;
      }
      // optional temperature gating
      if (thresholdSpec.startsWith("T")) {
        return checkThreshold(thresholdSpec, "T", temperatureC);
      }
      return true;
    }
    return false;
  }

  /**
   * Parses a threshold spec like {@code "Ca>2000mgL"} or {@code "T>80C"}.
   *
   * @param spec specification string
   * @param key expected key prefix
   * @param value the actual numeric value to test
   * @return true if the threshold is exceeded
   */
  private static boolean checkThreshold(String spec, String key, double value) {
    if (spec == null || !spec.startsWith(key)) {
      return false;
    }
    String body = spec.substring(key.length());
    char op = body.length() > 0 ? body.charAt(0) : '>';
    if (op == '>' || op == '<') {
      body = body.substring(1);
    }
    StringBuilder digits = new StringBuilder();
    for (int i = 0; i < body.length(); i++) {
      char c = body.charAt(i);
      if (Character.isDigit(c) || c == '.' || c == '-') {
        digits.append(c);
      } else {
        break;
      }
    }
    double threshold;
    try {
      threshold = Double.parseDouble(digits.toString());
    } catch (NumberFormatException ex) {
      return false;
    }
    if (op == '<') {
      return value < threshold;
    }
    return value > threshold;
  }

  /**
   * Tests whether a chemical matches an operand (type and ingredient with wildcard support).
   *
   * @param typeSpec type token from the rule
   * @param ingredientSpec ingredient token from the rule
   * @param c chemical to test
   * @return true on match
   */
  private static boolean matchOperand(String typeSpec, String ingredientSpec,
      ProductionChemical c) {
    if (c == null) {
      return false;
    }
    if (!"*".equals(typeSpec)) {
      // map THERMODYNAMIC_HHI alias used in CSV to the enum name
      String enumName = c.getType().name();
      String spec = typeSpec;
      if ("THERMODYNAMIC_HHI".equals(spec)) {
        spec = "HYDRATE_INHIBITOR_THERMODYNAMIC";
      }
      if (!enumName.equalsIgnoreCase(spec)) {
        return false;
      }
    }
    if (!"*".equals(ingredientSpec) && ingredientSpec != null && !ingredientSpec.isEmpty()) {
      String ai = c.getActiveIngredient() == null ? "" : c.getActiveIngredient();
      if (!ai.equalsIgnoreCase(ingredientSpec)) {
        return false;
      }
    }
    return true;
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Gets the operand 1 type token.
   *
   * @return type token
   */
  public String getChemical1Type() {
    return chemical1Type;
  }

  /**
   * Gets the operand 1 ingredient token.
   *
   * @return ingredient token
   */
  public String getChemical1Ingredient() {
    return chemical1Ingredient;
  }

  /**
   * Gets the operand 2 type token.
   *
   * @return type token
   */
  public String getChemical2Type() {
    return chemical2Type;
  }

  /**
   * Gets the operand 2 ingredient token.
   *
   * @return ingredient token
   */
  public String getChemical2Ingredient() {
    return chemical2Ingredient;
  }

  /**
   * Gets the condition tag.
   *
   * @return condition tag
   */
  public String getCondition() {
    return condition;
  }

  /**
   * Gets the severity classification.
   *
   * @return severity
   */
  public Severity getSeverity() {
    return severity;
  }

  /**
   * Gets the interaction mechanism description.
   *
   * @return mechanism text
   */
  public String getMechanism() {
    return mechanism;
  }

  /**
   * Gets the recommended mitigation.
   *
   * @return mitigation text
   */
  public String getMitigation() {
    return mitigation;
  }

  /**
   * Returns a map representation (for JSON output).
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("chemical1Type", chemical1Type);
    map.put("chemical1Ingredient", chemical1Ingredient);
    map.put("chemical2Type", chemical2Type);
    map.put("chemical2Ingredient", chemical2Ingredient);
    map.put("condition", condition);
    map.put("severity", severity.name());
    map.put("mechanism", mechanism);
    map.put("mitigation", mitigation);
    return map;
  }
}
