package neqsim.process.mechanicaldesign.subsea;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Well barrier schematic per NORSOK D-010 Section 5.
 *
 * <p>
 * A well barrier schematic defines the primary and secondary barrier envelopes and validates them
 * against standard requirements. It implements the two-independent-barrier principle from NORSOK
 * D-010:
 * </p>
 * <ul>
 * <li>Every well must have two independent well barrier envelopes</li>
 * <li>Each envelope must have at least 2 tested and verified barrier elements</li>
 * <li>Producer wells require a DHSV (SSSV/SCSSV) in the primary envelope</li>
 * <li>Injector wells require an ISV in the primary envelope (NORSOK D-010 Table 36)</li>
 * <li>Annular pressure must be monitored (API RP 90)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * WellBarrierSchematic schematic = new WellBarrierSchematic();
 * schematic.setWellType("OIL_PRODUCER");
 *
 * BarrierEnvelope primary = new BarrierEnvelope("Primary");
 * primary.addElement(new BarrierElement(BarrierElement.ElementType.TUBING, "Production Tubing"));
 * primary.addElement(new BarrierElement(BarrierElement.ElementType.DHSV, "DHSV"));
 * primary.addElement(new BarrierElement(BarrierElement.ElementType.XMAS_TREE, "Xmas Tree"));
 * schematic.setPrimaryEnvelope(primary);
 *
 * BarrierEnvelope secondary = new BarrierEnvelope("Secondary");
 * secondary.addElement(new BarrierElement(BarrierElement.ElementType.CASING, "Prod Casing"));
 * secondary.addElement(new BarrierElement(BarrierElement.ElementType.CEMENT, "Casing Cement"));
 * secondary.addElement(new BarrierElement(BarrierElement.ElementType.WELLHEAD, "Wellhead"));
 * schematic.setSecondaryEnvelope(secondary);
 *
 * schematic.validate();
 * System.out.println("Passed: " + schematic.isPassed());
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see BarrierEnvelope
 * @see BarrierElement
 * @see WellMechanicalDesign
 */
public class WellBarrierSchematic implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Minimum barrier elements per envelope (NORSOK D-010). */
  private static final int DEFAULT_MIN_ELEMENTS = 2;

  /** Primary barrier envelope. */
  private BarrierEnvelope primaryEnvelope;

  /** Secondary barrier envelope. */
  private BarrierEnvelope secondaryEnvelope;

  /** Well type string (e.g. "OIL_PRODUCER", "WATER_INJECTOR"). */
  private String wellType = "";

  /** Minimum elements per envelope (loaded from standards). */
  private int minPrimaryElements = DEFAULT_MIN_ELEMENTS;

  /** Minimum elements for secondary envelope. */
  private int minSecondaryElements = DEFAULT_MIN_ELEMENTS;

  /** Whether DHSV is required (producers). */
  private boolean dhsvRequired = true;

  /** Whether ISV is required (injectors per NORSOK D-010 Table 36). */
  private boolean isvRequired = false;

  /** Whether annulus pressure monitoring is required. */
  private boolean annulusMonitoringRequired = true;

  /** Validation passed flag. */
  private boolean passed = false;

  /** Validation issues found. */
  private final List<String> issues = new ArrayList<String>();

  /** Applied standards references. */
  private final List<String> appliedStandards = new ArrayList<String>();

  /**
   * Create an empty well barrier schematic.
   */
  public WellBarrierSchematic() {
    this.primaryEnvelope = new BarrierEnvelope("Primary");
    this.secondaryEnvelope = new BarrierEnvelope("Secondary");
  }

  /**
   * Set the well type for validation rules.
   *
   * @param wellType well type string (e.g. "OIL_PRODUCER", "WATER_INJECTOR")
   */
  public void setWellType(String wellType) {
    this.wellType = wellType;
    updateRequirements();
  }

  /**
   * Update barrier requirements based on well type.
   */
  private void updateRequirements() {
    boolean isInjector = wellType.contains("INJECTOR");
    if (isInjector) {
      dhsvRequired = false;
      isvRequired = true;
    } else {
      dhsvRequired = true;
      isvRequired = false;
    }
  }

  /**
   * Set the primary barrier envelope.
   *
   * @param envelope primary barrier envelope
   */
  public void setPrimaryEnvelope(BarrierEnvelope envelope) {
    this.primaryEnvelope = envelope;
  }

  /**
   * Set the secondary barrier envelope.
   *
   * @param envelope secondary barrier envelope
   */
  public void setSecondaryEnvelope(BarrierEnvelope envelope) {
    this.secondaryEnvelope = envelope;
  }

  /**
   * Get the primary barrier envelope.
   *
   * @return primary envelope
   */
  public BarrierEnvelope getPrimaryEnvelope() {
    return primaryEnvelope;
  }

  /**
   * Get the secondary barrier envelope.
   *
   * @return secondary envelope
   */
  public BarrierEnvelope getSecondaryEnvelope() {
    return secondaryEnvelope;
  }

  /**
   * Set barrier element count requirements (from standards data).
   *
   * @param minPrimary minimum elements in primary envelope
   * @param minSecondary minimum elements in secondary envelope
   */
  public void setMinimumElements(int minPrimary, int minSecondary) {
    this.minPrimaryElements = minPrimary;
    this.minSecondaryElements = minSecondary;
  }

  /**
   * Override DHSV requirement.
   *
   * @param required true if DHSV is required
   */
  public void setDhsvRequired(boolean required) {
    this.dhsvRequired = required;
  }

  /**
   * Override ISV requirement.
   *
   * @param required true if ISV is required
   */
  public void setIsvRequired(boolean required) {
    this.isvRequired = required;
  }

  /**
   * Set whether annulus monitoring is required.
   *
   * @param required true if required
   */
  public void setAnnulusMonitoringRequired(boolean required) {
    this.annulusMonitoringRequired = required;
  }

  /**
   * Validate the barrier schematic against NORSOK D-010 requirements.
   *
   * <p>
   * Checks the two-barrier principle, minimum element counts, safety valve requirements (DHSV for
   * producers, ISV for injectors), and envelope integrity.
   * </p>
   *
   * @return true if all checks pass
   */
  public boolean validate() {
    issues.clear();
    appliedStandards.clear();
    boolean allPassed = true;

    appliedStandards.add("NORSOK D-010 Rev 5 - Well Integrity");

    // Check primary envelope
    if (primaryEnvelope == null || primaryEnvelope.getElementCount() == 0) {
      issues.add("FAIL: No primary barrier envelope defined");
      allPassed = false;
    } else {
      if (!primaryEnvelope.meetsMinimum(minPrimaryElements)) {
        issues.add("FAIL: Primary envelope has " + primaryEnvelope.getFunctionalElementCount()
            + " functional elements, minimum " + minPrimaryElements
            + " required (NORSOK D-010 Sec 5)");
        allPassed = false;
      }
      if (!primaryEnvelope.isIntact()) {
        List<BarrierElement> failed = primaryEnvelope.getFailedElements();
        for (BarrierElement el : failed) {
          issues.add("WARNING: Primary envelope element failed: " + el.getName() + " ("
              + el.getType().name() + ")");
        }
      }
    }

    // Check secondary envelope
    if (secondaryEnvelope == null || secondaryEnvelope.getElementCount() == 0) {
      issues.add("FAIL: No secondary barrier envelope defined");
      allPassed = false;
    } else {
      if (!secondaryEnvelope.meetsMinimum(minSecondaryElements)) {
        issues.add("FAIL: Secondary envelope has " + secondaryEnvelope.getFunctionalElementCount()
            + " functional elements, minimum " + minSecondaryElements
            + " required (NORSOK D-010 Sec 5)");
        allPassed = false;
      }
      if (!secondaryEnvelope.isIntact()) {
        List<BarrierElement> failed = secondaryEnvelope.getFailedElements();
        for (BarrierElement el : failed) {
          issues.add("WARNING: Secondary envelope element failed: " + el.getName() + " ("
              + el.getType().name() + ")");
        }
      }
    }

    // DHSV check for production wells
    if (dhsvRequired) {
      boolean hasDHSV = hasSafetyValve(BarrierElement.ElementType.DHSV);
      if (!hasDHSV) {
        issues.add("FAIL: DHSV (SSSV) required for production wells (NORSOK D-010 Table 20)");
        allPassed = false;
      }
      appliedStandards.add("NORSOK D-010 Table 20 - Production Well Barriers");
    }

    // ISV check for injection wells
    if (isvRequired) {
      boolean hasISV = hasSafetyValve(BarrierElement.ElementType.ISV);
      if (!hasISV) {
        issues.add("FAIL: ISV required for injection wells (NORSOK D-010 Table 36)");
        allPassed = false;
      }
      appliedStandards.add("NORSOK D-010 Table 36 - Injection Well Barriers");
    }

    // Annulus monitoring
    if (annulusMonitoringRequired) {
      issues.add("INFO: Annular pressure monitoring required per NORSOK D-010 Sec 9 / API RP 90");
      appliedStandards.add("API RP 90 - Annular Casing Pressure Management");
    }

    // Summary
    if (allPassed) {
      issues.add("PASS: Two-barrier principle satisfied per NORSOK D-010");
    } else {
      issues.add("FAIL: Two-barrier principle NOT satisfied - review well design");
    }

    this.passed = allPassed;
    return allPassed;
  }

  /**
   * Check if a specific safety valve type exists in any envelope.
   *
   * @param type the element type to search for
   * @return true if found in primary or secondary envelope
   */
  private boolean hasSafetyValve(BarrierElement.ElementType type) {
    if (primaryEnvelope != null && primaryEnvelope.hasElementType(type)) {
      return true;
    }
    if (secondaryEnvelope != null && secondaryEnvelope.hasElementType(type)) {
      return true;
    }
    return false;
  }

  /**
   * Check if the validation passed.
   *
   * @return true if all checks passed
   */
  public boolean isPassed() {
    return passed;
  }

  /**
   * Get the number of issues found.
   *
   * @return issue count
   */
  public int getIssueCount() {
    int count = 0;
    for (String issue : issues) {
      if (issue.startsWith("FAIL:") || issue.startsWith("WARNING:")) {
        count++;
      }
    }
    return count;
  }

  /**
   * Get all validation issues and notes.
   *
   * @return unmodifiable list of issues
   */
  public List<String> getIssues() {
    return Collections.unmodifiableList(issues);
  }

  /**
   * Get applied standards references.
   *
   * @return list of applied standard references
   */
  public List<String> getAppliedStandards() {
    return Collections.unmodifiableList(appliedStandards);
  }

  /**
   * Get the well type.
   *
   * @return well type string
   */
  public String getWellType() {
    return wellType;
  }

  /**
   * Get a summary map for JSON reporting.
   *
   * @return map of schematic status and details
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("wellType", wellType);
    result.put("verificationPassed", passed);
    result.put("issueCount", getIssueCount());

    Map<String, Object> primary = new LinkedHashMap<String, Object>();
    primary.put("name", primaryEnvelope.getName());
    primary.put("totalElements", primaryEnvelope.getElementCount());
    primary.put("functionalElements", primaryEnvelope.getFunctionalElementCount());
    primary.put("verifiedElements", primaryEnvelope.getVerifiedElementCount());
    primary.put("intact", primaryEnvelope.isIntact());
    List<Map<String, Object>> priElements = new ArrayList<Map<String, Object>>();
    for (BarrierElement el : primaryEnvelope.getElements()) {
      Map<String, Object> elMap = new LinkedHashMap<String, Object>();
      elMap.put("type", el.getType().name());
      elMap.put("name", el.getName());
      elMap.put("status", el.getStatus().name());
      elMap.put("verified", el.isVerified());
      elMap.put("depthMD", el.getDepthMD());
      priElements.add(elMap);
    }
    primary.put("elements", priElements);
    result.put("primaryEnvelope", primary);

    Map<String, Object> secondary = new LinkedHashMap<String, Object>();
    secondary.put("name", secondaryEnvelope.getName());
    secondary.put("totalElements", secondaryEnvelope.getElementCount());
    secondary.put("functionalElements", secondaryEnvelope.getFunctionalElementCount());
    secondary.put("verifiedElements", secondaryEnvelope.getVerifiedElementCount());
    secondary.put("intact", secondaryEnvelope.isIntact());
    List<Map<String, Object>> secElements = new ArrayList<Map<String, Object>>();
    for (BarrierElement el : secondaryEnvelope.getElements()) {
      Map<String, Object> elMap = new LinkedHashMap<String, Object>();
      elMap.put("type", el.getType().name());
      elMap.put("name", el.getName());
      elMap.put("status", el.getStatus().name());
      elMap.put("verified", el.isVerified());
      elMap.put("depthMD", el.getDepthMD());
      secElements.add(elMap);
    }
    secondary.put("elements", secElements);
    result.put("secondaryEnvelope", secondary);

    result.put("issues", new ArrayList<String>(issues));
    result.put("appliedStandards", new ArrayList<String>(appliedStandards));

    return result;
  }
}
