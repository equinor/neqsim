package neqsim.process.chemistry;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Evaluates compatibility of a set of production chemicals with each other and with the produced
 * fluid / equipment material.
 *
 * <p>
 * The assessor combines two information sources:
 * </p>
 * <ol>
 * <li>A rule base loaded by {@link ChemicalInteractionRule#loadDefaultRules()} (CSV-driven).</li>
 * <li>Operating conditions and water chemistry supplied by the caller (temperature, pressure,
 * Ca/Fe/HCO3 concentrations, material).</li>
 * </ol>
 *
 * <p>
 * The output is a structured report with:
 * </p>
 * <ul>
 * <li>An overall verdict: COMPATIBLE / CAUTION / INCOMPATIBLE.</li>
 * <li>A pairwise interaction matrix.</li>
 * <li>An ordered list of identified issues with mechanism and mitigation.</li>
 * <li>Temperature stability flags.</li>
 * <li>A JSON-serialisable map for downstream tools.</li>
 * </ul>
 *
 * <p>
 * Pattern: configure with setters, call {@link #evaluate()}, then read the getters or
 * {@link #toJson()}.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ChemicalCompatibilityAssessor implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Overall verdict for a compatibility study.
   */
  public enum Verdict {
    /** No incompatibilities found. */
    COMPATIBLE,
    /** Medium / low severity interactions; bench testing required. */
    CAUTION,
    /** Severe interactions found; do not deploy without redesign. */
    INCOMPATIBLE;
  }

  // ─── Inputs ─────────────────────────────────────────────

  /** Chemicals under evaluation. */
  private final List<ProductionChemical> chemicals = new ArrayList<ProductionChemical>();

  /** Active rule base. */
  private List<ChemicalInteractionRule> rules = ChemicalInteractionRule.loadDefaultRules();

  /** Operating temperature (Celsius). */
  private double temperatureC = 60.0;

  /** Operating pressure (bara). */
  private double pressureBara = 50.0;

  /** Calcium concentration in produced water (mg/L). */
  private double calciumMgL = 0.0;

  /** Iron (Fe2+) concentration in produced water (mg/L). */
  private double ironMgL = 0.0;

  /** Bicarbonate concentration in produced water (mg/L). */
  private double bicarbonateMgL = 0.0;

  /** Material identifier (e.g. {@code "carbon_steel"}, {@code "316L"}, {@code "duplex"}). */
  private String material = "carbon_steel";

  // ─── Outputs ────────────────────────────────────────────

  /** Identified issues from the rule scan. */
  private final List<Map<String, Object>> issues = new ArrayList<Map<String, Object>>();

  /** Pairwise interaction matrix (chemical name × chemical name → severity label). */
  private final Map<String, Map<String, String>> matrix =
      new LinkedHashMap<String, Map<String, String>>();

  /** Temperature stability flags per chemical. */
  private final Map<String, Boolean> thermalStability = new LinkedHashMap<String, Boolean>();

  /** Overall verdict. */
  private Verdict verdict = Verdict.COMPATIBLE;

  /** Highest severity observed. */
  private ChemicalInteractionRule.Severity highestSeverity = ChemicalInteractionRule.Severity.INFO;

  /** Whether evaluate() has been run successfully. */
  private boolean evaluated = false;

  // ─── Constructors ───────────────────────────────────────

  /**
   * Default constructor.
   */
  public ChemicalCompatibilityAssessor() {}

  /**
   * Convenience factory: builds an assessor and seeds operating conditions and aqueous-phase
   * concentrations from any NeqSim {@link neqsim.process.equipment.stream.StreamInterface} via
   * {@link neqsim.process.chemistry.util.StreamChemistryAdapter}.
   *
   * @param stream produced fluid stream
   * @return assessor pre-loaded with T, P, Ca, Fe, HCO3
   */
  public static ChemicalCompatibilityAssessor fromStream(
      neqsim.process.equipment.stream.StreamInterface stream) {
    ChemicalCompatibilityAssessor a = new ChemicalCompatibilityAssessor();
    neqsim.process.chemistry.util.StreamChemistryAdapter ad =
        new neqsim.process.chemistry.util.StreamChemistryAdapter(stream);
    a.setTemperatureCelsius(ad.getTemperatureCelsius());
    a.setPressureBara(ad.getPressureBara());
    a.setCalciumMgL(ad.getCalciumMgL());
    a.setIronMgL(ad.getIronMgL());
    a.setBicarbonateMgL(ad.getBicarbonateMgL());
    return a;
  }

  // ─── Configuration ──────────────────────────────────────

  /**
   * Adds a chemical to the study.
   *
   * @param chemical chemical to add
   */
  public void addChemical(ProductionChemical chemical) {
    chemicals.add(chemical);
  }

  /**
   * Removes all chemicals from the study.
   */
  public void clearChemicals() {
    chemicals.clear();
  }

  /**
   * Replaces the rule base. Use to load custom rule sets, e.g. from a vendor library.
   *
   * @param rules rule base
   */
  public void setRules(List<ChemicalInteractionRule> rules) {
    this.rules = rules;
  }

  /**
   * Sets the operating temperature.
   *
   * @param temperatureC temperature in Celsius
   */
  public void setTemperatureCelsius(double temperatureC) {
    this.temperatureC = temperatureC;
  }

  /**
   * Sets the operating pressure.
   *
   * @param pressureBara pressure in bara
   */
  public void setPressureBara(double pressureBara) {
    this.pressureBara = pressureBara;
  }

  /**
   * Sets the calcium concentration of produced water.
   *
   * @param mgL calcium in mg/L
   */
  public void setCalciumMgL(double mgL) {
    this.calciumMgL = mgL;
  }

  /**
   * Sets the iron concentration of produced water.
   *
   * @param mgL iron in mg/L
   */
  public void setIronMgL(double mgL) {
    this.ironMgL = mgL;
  }

  /**
   * Sets the bicarbonate concentration of produced water.
   *
   * @param mgL bicarbonate in mg/L
   */
  public void setBicarbonateMgL(double mgL) {
    this.bicarbonateMgL = mgL;
  }

  /**
   * Sets the equipment material.
   *
   * @param material material identifier (free text)
   */
  public void setMaterial(String material) {
    this.material = material;
  }

  // ─── Evaluation ─────────────────────────────────────────

  /**
   * Runs the evaluation. Populates the issues list, pairwise matrix, thermal stability flags and
   * the overall verdict.
   */
  public void evaluate() {
    issues.clear();
    matrix.clear();
    thermalStability.clear();
    highestSeverity = ChemicalInteractionRule.Severity.INFO;

    // Init matrix and stability map
    for (int i = 0; i < chemicals.size(); i++) {
      ProductionChemical a = chemicals.get(i);
      Map<String, String> row = new LinkedHashMap<String, String>();
      for (int j = 0; j < chemicals.size(); j++) {
        ProductionChemical b = chemicals.get(j);
        row.put(b.getName(), i == j ? "SELF" : "OK");
      }
      matrix.put(a.getName(), row);
      thermalStability.put(a.getName(), a.isStableAt(temperatureC));
      if (!a.isStableAt(temperatureC)) {
        addIssue(a, null, "Thermal stability exceeded",
            "Chemical " + a.getName() + " has stability range [" + a.getMinTemperatureC() + ", "
                + a.getMaxTemperatureC() + "] C; operating temperature is " + temperatureC + " C",
            "Switch to a chemistry rated for the operating temperature",
            ChemicalInteractionRule.Severity.HIGH);
      }
    }

    // Pairwise rules
    for (int i = 0; i < chemicals.size(); i++) {
      ProductionChemical a = chemicals.get(i);
      for (int j = i + 1; j < chemicals.size(); j++) {
        ProductionChemical b = chemicals.get(j);
        for (ChemicalInteractionRule rule : rules) {
          if (rule.isEnvironmentRule()) {
            continue;
          }
          if (rule.matches(a, b)) {
            addIssue(a, b, "Chemical-chemical interaction (" + rule.getCondition() + ")",
                rule.getMechanism(), rule.getMitigation(), rule.getSeverity());
            String label = rule.getSeverity().name();
            updateMatrix(a.getName(), b.getName(), label);
          }
        }
      }
    }

    // Environment rules (chemical vs water/material/temperature)
    for (ProductionChemical a : chemicals) {
      for (ChemicalInteractionRule rule : rules) {
        if (!rule.isEnvironmentRule()) {
          continue;
        }
        if (!rule.matches(a, null)) {
          continue;
        }
        if (rule.environmentMatches(temperatureC, calciumMgL, ironMgL, bicarbonateMgL, material)) {
          addIssue(a, null, "Chemical-environment interaction (" + rule.getCondition() + ")",
              rule.getMechanism(), rule.getMitigation(), rule.getSeverity());
        }
      }
    }

    // Verdict
    switch (highestSeverity) {
      case HIGH:
        verdict = Verdict.INCOMPATIBLE;
        break;
      case MEDIUM:
      case LOW:
        verdict = Verdict.CAUTION;
        break;
      default:
        verdict = Verdict.COMPATIBLE;
        break;
    }
    evaluated = true;
  }

  /**
   * Records an issue and bumps the highest severity.
   *
   * @param a primary chemical
   * @param b secondary chemical (may be null)
   * @param category short category label
   * @param mechanism mechanism description
   * @param mitigation recommended mitigation
   * @param severity severity classification
   */
  private void addIssue(ProductionChemical a, ProductionChemical b, String category,
      String mechanism, String mitigation, ChemicalInteractionRule.Severity severity) {
    Map<String, Object> issue = new LinkedHashMap<String, Object>();
    issue.put("category", category);
    issue.put("severity", severity.name());
    issue.put("chemical1", a == null ? "" : a.getName());
    issue.put("chemical2", b == null ? "" : b.getName());
    issue.put("mechanism", mechanism);
    issue.put("mitigation", mitigation);
    issues.add(issue);
    if (severity.ordinal() < highestSeverity.ordinal()) {
      highestSeverity = severity;
    }
  }

  /**
   * Updates a cell in the symmetric pairwise matrix, keeping the worst severity seen.
   *
   * @param a row key
   * @param b column key
   * @param label severity label
   */
  private void updateMatrix(String a, String b, String label) {
    setMatrixCell(a, b, label);
    setMatrixCell(b, a, label);
  }

  /**
   * Sets a single matrix cell, keeping the worst severity seen.
   *
   * @param a row key
   * @param b column key
   * @param label new severity label
   */
  private void setMatrixCell(String a, String b, String label) {
    Map<String, String> row = matrix.get(a);
    if (row == null) {
      return;
    }
    String current = row.get(b);
    if (current == null || "OK".equals(current) || isWorse(label, current)) {
      row.put(b, label);
    }
  }

  /**
   * Returns true if {@code newLabel} is worse than {@code currentLabel} on the severity scale.
   *
   * @param newLabel new severity label
   * @param currentLabel current severity label
   * @return true if newLabel is worse
   */
  private static boolean isWorse(String newLabel, String currentLabel) {
    return rank(newLabel) < rank(currentLabel);
  }

  /**
   * Returns ordinal rank for a severity label (lower ordinal = worse).
   *
   * @param label severity label
   * @return integer rank (HIGH=0, MEDIUM=1, LOW=2, INFO=3, OK=4)
   */
  private static int rank(String label) {
    if ("HIGH".equals(label)) {
      return 0;
    }
    if ("MEDIUM".equals(label)) {
      return 1;
    }
    if ("LOW".equals(label)) {
      return 2;
    }
    if ("INFO".equals(label)) {
      return 3;
    }
    return 4;
  }

  // ─── Output ─────────────────────────────────────────────

  /**
   * Returns the overall verdict.
   *
   * @return verdict (COMPATIBLE / CAUTION / INCOMPATIBLE)
   */
  public Verdict getVerdict() {
    return verdict;
  }

  /**
   * Returns the highest severity observed.
   *
   * @return highest severity
   */
  public ChemicalInteractionRule.Severity getHighestSeverity() {
    return highestSeverity;
  }

  /**
   * Returns the issue list.
   *
   * @return list of issues
   */
  public List<Map<String, Object>> getIssues() {
    return new ArrayList<Map<String, Object>>(issues);
  }

  /**
   * Returns the pairwise interaction matrix.
   *
   * @return matrix (chemical1 → chemical2 → severity label)
   */
  public Map<String, Map<String, String>> getInteractionMatrix() {
    return new LinkedHashMap<String, Map<String, String>>(matrix);
  }

  /**
   * Returns the temperature stability flags.
   *
   * @return map of chemical name to stability flag
   */
  public Map<String, Boolean> getThermalStability() {
    return new LinkedHashMap<String, Boolean>(thermalStability);
  }

  /**
   * Returns whether {@link #evaluate()} has been run.
   *
   * @return true if evaluated
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  /**
   * Returns the structured result map (for JSON serialisation).
   *
   * @return ordered map of result fields
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    Map<String, Object> conditions = new LinkedHashMap<String, Object>();
    conditions.put("temperatureC", temperatureC);
    conditions.put("pressureBara", pressureBara);
    conditions.put("calciumMgL", calciumMgL);
    conditions.put("ironMgL", ironMgL);
    conditions.put("bicarbonateMgL", bicarbonateMgL);
    conditions.put("material", material);
    map.put("operatingConditions", conditions);
    List<Map<String, Object>> chemList = new ArrayList<Map<String, Object>>();
    for (ProductionChemical c : chemicals) {
      chemList.add(c.toMap());
    }
    map.put("chemicals", chemList);
    map.put("verdict", verdict.name());
    map.put("highestSeverity", highestSeverity.name());
    map.put("thermalStability", thermalStability);
    map.put("interactionMatrix", matrix);
    map.put("issues", issues);
    map.put("standardsApplied", getStandardsApplied());
    return map;
  }

  /**
   * Returns the industry standards applied by the compatibility assessor.
   *
   * @return list of standards (each as an ordered map)
   */
  public java.util.List<java.util.Map<String, Object>> getStandardsApplied() {
    return neqsim.process.chemistry.util.StandardsRegistry.toMapList(
        neqsim.process.chemistry.util.StandardsRegistry.NACE_MR0175,
        neqsim.process.chemistry.util.StandardsRegistry.NORSOK_M001);
  }

  /**
   * Returns the result as a pretty-printed JSON string.
   *
   * @return JSON string
   */
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    return gson.toJson(toMap());
  }
}
