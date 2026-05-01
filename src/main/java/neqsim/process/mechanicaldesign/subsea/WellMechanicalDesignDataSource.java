package neqsim.process.mechanicaldesign.subsea;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.mechanicaldesign.data.StandardBasedCsvDataSource;

/**
 * Loads well design parameters from CSV standards databases.
 *
 * <p>
 * This data source queries NORSOK D-010, API RP 90, API TR 5C3, and ISO 16530 entries from the
 * standards CSV files for well integrity, casing design, and barrier verification parameters.
 * </p>
 *
 * <p>
 * It follows the established NeqSim mechanical design pattern: CSV -&gt; DataSource -&gt;
 * Calculator -&gt; MechanicalDesign -&gt; JSON.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class WellMechanicalDesignDataSource implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(WellMechanicalDesignDataSource.class);

  private static final String NORSOK_CSV = "designdata/standards/norsok_standards.csv";
  private static final String API_CSV = "designdata/standards/api_standards.csv";
  private static final String ISO_CSV = "designdata/standards/dnv_iso_en_standards.csv";

  private static final String EQUIPMENT_TYPE = "SubseaWell";

  /** Cached NORSOK data source. */
  private transient StandardBasedCsvDataSource norsokSource;

  /** Cached API data source. */
  private transient StandardBasedCsvDataSource apiSource;

  /** Cached ISO data source. */
  private transient StandardBasedCsvDataSource isoSource;

  /** Applied standards tracking for JSON reporting. */
  private final List<String> appliedStandards = new ArrayList<String>();

  /**
   * Default constructor.
   */
  public WellMechanicalDesignDataSource() {}

  /**
   * Load NORSOK D-010 design factors into the calculator.
   *
   * <p>
   * Reads burst, collapse, tension, and triaxial design factors from the standards database. If the
   * database is unavailable, the calculator retains its hardcoded NORSOK D-010 defaults.
   * </p>
   *
   * @param calculator the well design calculator to configure
   * @param isInjectionWell true if loading injection-specific design factors
   */
  public void loadNorskD010DesignFactors(WellDesignCalculator calculator, boolean isInjectionWell) {
    StandardBasedCsvDataSource source = getNorsokSource();
    if (source == null) {
      logger.warn("NORSOK standards CSV not available, using calculator defaults");
      return;
    }

    Map<String, double[]> specs =
        source.getSpecificationValues("NORSOK-D-010", "Rev 5", EQUIPMENT_TYPE);
    if (specs.isEmpty()) {
      logger.info("No NORSOK D-010 data found for SubseaWell, using defaults");
      return;
    }

    if (isInjectionWell) {
      applySpecValue(specs, "InjectionBurstDesignFactor", calculator, "burstDF");
      applySpecValue(specs, "InjectionCollapseDesignFactor", calculator, "collapseDF");
      applySpecValue(specs, "InjectionTensionDesignFactor", calculator, "tensionDF");
      appliedStandards.add("NORSOK D-010 Rev 5 Table 18 (injection load cases)");
    } else {
      applySpecValue(specs, "BurstDesignFactor", calculator, "burstDF");
      applySpecValue(specs, "CollapseDesignFactor", calculator, "collapseDF");
      applySpecValue(specs, "TensionDesignFactor", calculator, "tensionDF");
      appliedStandards.add("NORSOK D-010 Rev 5 Table 18 (production load cases)");
    }

    applySpecValue(specs, "TriaxialDesignFactor", calculator, "vmeDF");
    appliedStandards.add("NORSOK D-010 Rev 5 - VME triaxial check");
  }

  /**
   * Load API RP 90 MAASP parameters.
   *
   * @return map of MAASP parameters (safetyFactor, collapseFactor, pressureTolerance,
   *         decayTestDuration, maxDecayRate)
   */
  public Map<String, Double> loadApiRp90Parameters() {
    Map<String, Double> params = new LinkedHashMap<String, Double>();

    // Defaults per API RP 90
    params.put("safetyFactor", 1.10);
    params.put("collapseFactor", 1.00);
    params.put("pressureTolerance", 70.0);
    params.put("decayTestDuration", 24.0);
    params.put("maxDecayRate", 0.07);

    StandardBasedCsvDataSource source = getApiSource();
    if (source == null) {
      return params;
    }

    Map<String, double[]> specs =
        source.getSpecificationValues("API-RP-90", "1st Ed", EQUIPMENT_TYPE);
    if (specs.isEmpty()) {
      return params;
    }

    if (specs.containsKey("MAASPSafetyFactor")) {
      params.put("safetyFactor", specs.get("MAASPSafetyFactor")[0]);
    }
    if (specs.containsKey("MAASPCollapseFactor")) {
      params.put("collapseFactor", specs.get("MAASPCollapseFactor")[0]);
    }
    if (specs.containsKey("AnnularPressureTolerance")) {
      params.put("pressureTolerance", specs.get("AnnularPressureTolerance")[1]);
    }
    if (specs.containsKey("PressureDecayTestDuration")) {
      params.put("decayTestDuration", specs.get("PressureDecayTestDuration")[0]);
    }
    if (specs.containsKey("MaxPressureDecayRate")) {
      params.put("maxDecayRate", specs.get("MaxPressureDecayRate")[0]);
    }

    appliedStandards.add("API RP 90 1st Ed - Annular casing pressure management");
    return params;
  }

  /**
   * Load NORSOK D-010 barrier requirements.
   *
   * @return map of barrier requirements
   */
  public Map<String, Double> loadBarrierRequirements() {
    Map<String, Double> reqs = new LinkedHashMap<String, Double>();

    // Defaults
    reqs.put("minPrimaryElements", 2.0);
    reqs.put("minSecondaryElements", 2.0);
    reqs.put("dhsvRequired", 1.0);
    reqs.put("isvRequiredInjector", 1.0);
    reqs.put("annulusMonitoring", 1.0);
    reqs.put("cementTopAboveShoe", 200.0);
    reqs.put("integrityTestInterval", 1.0);

    StandardBasedCsvDataSource source = getNorsokSource();
    if (source == null) {
      return reqs;
    }

    Map<String, double[]> specs =
        source.getSpecificationValues("NORSOK-D-010", "Rev 5", EQUIPMENT_TYPE);
    if (specs.isEmpty()) {
      return reqs;
    }

    if (specs.containsKey("MinPrimaryBarrierElements")) {
      reqs.put("minPrimaryElements", specs.get("MinPrimaryBarrierElements")[0]);
    }
    if (specs.containsKey("MinSecondaryBarrierElements")) {
      reqs.put("minSecondaryElements", specs.get("MinSecondaryBarrierElements")[0]);
    }
    if (specs.containsKey("DHSVRequired")) {
      reqs.put("dhsvRequired", specs.get("DHSVRequired")[0]);
    }
    if (specs.containsKey("ISVRequiredInjector")) {
      reqs.put("isvRequiredInjector", specs.get("ISVRequiredInjector")[0]);
    }
    if (specs.containsKey("AnnulusPressureMonitoring")) {
      reqs.put("annulusMonitoring", specs.get("AnnulusPressureMonitoring")[0]);
    }
    if (specs.containsKey("CementTopAboveShoe")) {
      reqs.put("cementTopAboveShoe", specs.get("CementTopAboveShoe")[0]);
    }
    if (specs.containsKey("WellIntegrityTestInterval")) {
      reqs.put("integrityTestInterval", specs.get("WellIntegrityTestInterval")[1]);
    }

    appliedStandards.add("NORSOK D-010 Rev 5 Section 5 - Two-barrier principle");
    appliedStandards.add("NORSOK D-010 Rev 5 Section 9 - Annulus monitoring");
    return reqs;
  }

  /**
   * Load ISO 16530 lifecycle requirements.
   *
   * @return map of lifecycle parameters
   */
  public Map<String, Double> loadIso16530Requirements() {
    Map<String, Double> reqs = new LinkedHashMap<String, Double>();

    // Defaults
    reqs.put("integrityTestInterval", 1.0);
    reqs.put("barrierVerificationInterval", 1.0);
    reqs.put("cementEvaluationRequired", 1.0);

    StandardBasedCsvDataSource source = getIsoSource();
    if (source == null) {
      return reqs;
    }

    Map<String, double[]> specs =
        source.getSpecificationValues("ISO-16530-1", "2017", EQUIPMENT_TYPE);
    if (specs.isEmpty()) {
      return reqs;
    }

    if (specs.containsKey("WellIntegrityTestInterval")) {
      reqs.put("integrityTestInterval", specs.get("WellIntegrityTestInterval")[1]);
    }
    if (specs.containsKey("BarrierVerificationInterval")) {
      reqs.put("barrierVerificationInterval", specs.get("BarrierVerificationInterval")[0]);
    }
    if (specs.containsKey("CementEvaluationRequired")) {
      reqs.put("cementEvaluationRequired", specs.get("CementEvaluationRequired")[0]);
    }

    appliedStandards.add("ISO 16530-1:2017 - Well integrity lifecycle governance");
    return reqs;
  }

  /**
   * Get the list of applied standards codes.
   *
   * @return list of standard references
   */
  public List<String> getAppliedStandards() {
    return new ArrayList<String>(appliedStandards);
  }

  /**
   * Clear the applied standards tracking list.
   */
  public void clearAppliedStandards() {
    appliedStandards.clear();
  }

  /**
   * Apply a specification value from the database to the calculator.
   *
   * @param specs the specification map from CSV
   * @param specName CSV specification name
   * @param calculator the calculator to update
   * @param target which calculator field to set ("burstDF", "collapseDF", "tensionDF", "vmeDF")
   */
  private void applySpecValue(Map<String, double[]> specs, String specName,
      WellDesignCalculator calculator, String target) {
    if (!specs.containsKey(specName)) {
      return;
    }
    double value = specs.get(specName)[0]; // Use MINVALUE
    if ("burstDF".equals(target)) {
      calculator.setMinBurstDesignFactor(value);
    } else if ("collapseDF".equals(target)) {
      calculator.setMinCollapseDesignFactor(value);
    } else if ("tensionDF".equals(target)) {
      calculator.setMinTensionDesignFactor(value);
    } else if ("vmeDF".equals(target)) {
      calculator.setMinVmeDesignFactor(value);
    }
  }

  /**
   * Get or create the NORSOK CSV data source.
   *
   * @return the data source, or null if unavailable
   */
  private StandardBasedCsvDataSource getNorsokSource() {
    if (norsokSource == null) {
      try {
        norsokSource = new StandardBasedCsvDataSource(NORSOK_CSV);
      } catch (Exception e) {
        logger.debug("Could not load NORSOK standards CSV: " + e.getMessage());
        return null;
      }
    }
    return norsokSource;
  }

  /**
   * Get or create the API CSV data source.
   *
   * @return the data source, or null if unavailable
   */
  private StandardBasedCsvDataSource getApiSource() {
    if (apiSource == null) {
      try {
        apiSource = new StandardBasedCsvDataSource(API_CSV);
      } catch (Exception e) {
        logger.debug("Could not load API standards CSV: " + e.getMessage());
        return null;
      }
    }
    return apiSource;
  }

  /**
   * Get or create the ISO CSV data source.
   *
   * @return the data source, or null if unavailable
   */
  private StandardBasedCsvDataSource getIsoSource() {
    if (isoSource == null) {
      try {
        isoSource = new StandardBasedCsvDataSource(ISO_CSV);
      } catch (Exception e) {
        logger.debug("Could not load ISO standards CSV: " + e.getMessage());
        return null;
      }
    }
    return isoSource;
  }
}
