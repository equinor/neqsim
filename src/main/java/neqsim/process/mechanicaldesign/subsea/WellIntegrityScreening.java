package neqsim.process.mechanicaldesign.subsea;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Deterministic well-integrity screening for annulus pressure and barrier degradation.
 *
 * <p>
 * This class performs an offline, rule-based screening of well integrity evidence supplied by the caller. It does
 * <b>not</b> connect to any live data source and does <b>not</b> make operational decisions. The output is a screening
 * classification intended to be reviewed by a competent well-integrity engineer before any action is taken.
 * </p>
 *
 * <p>
 * Screening covers two complementary areas:
 * </p>
 * <ul>
 * <li><b>Annulus pressure</b> per API RP 90 / NORSOK D-010 Section 9 - each monitored annulus is classified relative to
 * its maximum allowable annulus surface pressure (MAASP) and assessed for sustained casing pressure (SCP).</li>
 * <li><b>Barrier degradation</b> per NORSOK D-010 Section 5 - an optional {@link WellBarrierSchematic} is folded in so
 * that failed or degraded barrier elements escalate the overall integrity disposition.</li>
 * </ul>
 *
 * <p>
 * The screening is intentionally conservative: any annulus exceeding MAASP or any failed primary/secondary barrier
 * element drives the overall disposition to {@link IntegrityDisposition#INTERVENTION_REQUIRED}.
 * </p>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * WellIntegrityScreening screening = new WellIntegrityScreening("WELL-A1");
 * screening.setWellType("OIL_PRODUCER");
 * screening.addAnnulus(new AnnulusReading("A", 35.0, 80.0).setBleedsToZero(false).setRebuildsAfterBleed(true));
 * screening.addAnnulus(new AnnulusReading("B", 5.0, 60.0).setBleedsToZero(true));
 * screening.screen();
 * System.out.println(screening.getDisposition());
 * }</pre>
 *
 * @author NeqSim contributors
 * @version 1.0
 * @see WellBarrierSchematic
 * @see WellMechanicalDesign
 */
public class WellIntegrityScreening implements Serializable {
  private static final long serialVersionUID = 1000L;

  private static final Logger logger = LogManager.getLogger(WellIntegrityScreening.class);

  /**
   * Fraction of MAASP above which a non-bleeding annulus is flagged as elevated (API RP 90 diagnostic band).
   */
  private static final double ELEVATED_FRACTION = 0.10;

  /**
   * Overall integrity disposition resulting from the screening.
   */
  public enum IntegrityDisposition {
    /** No screening flags; routine monitoring continues subject to human review. */
    ACCEPTABLE,
    /** One or more elevated indicators; increased monitoring recommended for human review. */
    MONITOR,
    /** One or more severe indicators (MAASP exceedance or failed barrier); intervention review required. */
    INTERVENTION_REQUIRED,
    /** Insufficient evidence supplied to screen. */
    INSUFFICIENT_DATA
  }

  /**
   * Per-annulus classification relative to API RP 90 / NORSOK D-010.
   */
  public enum AnnulusClassification {
    /** Pressure within normal band and no sustained-casing-pressure indication. */
    NORMAL,
    /** Pressure elevated but below MAASP; monitor and trend. */
    ELEVATED,
    /** Sustained casing pressure indication (rebuilds after bleed-down). */
    SUSTAINED_CASING_PRESSURE,
    /** Measured pressure at or above MAASP. */
    EXCEEDS_MAASP,
    /** Not enough information to classify. */
    UNKNOWN
  }

  /**
   * A single monitored annulus reading supplied by the caller.
   *
   * <p>
   * All pressures are surface pressures in bara. Thermal-induced pressure should be excluded by the caller before
   * supplying readings, in line with API RP 90 sustained-casing-pressure diagnostics.
   * </p>
   */
  public static class AnnulusReading implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String id;
    private final double measuredPressureBara;
    private final double maaspBara;
    private boolean bleedsToZero = false;
    private boolean rebuildsAfterBleed = false;
    private boolean thermalEffectsExcluded = true;

    /**
     * Create an annulus reading.
     *
     * @param id annulus identifier (e.g. "A", "B", "C")
     * @param measuredPressureBara measured surface pressure in bara
     * @param maaspBara maximum allowable annulus surface pressure in bara
     */
    public AnnulusReading(String id, double measuredPressureBara, double maaspBara) {
      this.id = id == null ? "" : id;
      this.measuredPressureBara = measuredPressureBara;
      this.maaspBara = maaspBara;
    }

    /**
     * Set whether the annulus bleeds down to zero when opened.
     *
     * @param bleedsToZero true if pressure bleeds to zero
     * @return this reading for chaining
     */
    public AnnulusReading setBleedsToZero(boolean bleedsToZero) {
      this.bleedsToZero = bleedsToZero;
      return this;
    }

    /**
     * Set whether the annulus rebuilds pressure after bleed-down (SCP indicator).
     *
     * @param rebuildsAfterBleed true if pressure rebuilds after bleed-down
     * @return this reading for chaining
     */
    public AnnulusReading setRebuildsAfterBleed(boolean rebuildsAfterBleed) {
      this.rebuildsAfterBleed = rebuildsAfterBleed;
      return this;
    }

    /**
     * Set whether thermal effects have been excluded from the reading.
     *
     * @param thermalEffectsExcluded true if thermal-induced pressure is excluded
     * @return this reading for chaining
     */
    public AnnulusReading setThermalEffectsExcluded(boolean thermalEffectsExcluded) {
      this.thermalEffectsExcluded = thermalEffectsExcluded;
      return this;
    }

    /**
     * Get the annulus identifier.
     *
     * @return annulus id
     */
    public String getId() {
      return id;
    }

    /**
     * Get the measured surface pressure.
     *
     * @return measured pressure in bara
     */
    public double getMeasuredPressureBara() {
      return measuredPressureBara;
    }

    /**
     * Get the maximum allowable annulus surface pressure.
     *
     * @return MAASP in bara
     */
    public double getMaaspBara() {
      return maaspBara;
    }

    /**
     * Get whether the annulus bleeds to zero.
     *
     * @return true if it bleeds to zero
     */
    public boolean isBleedsToZero() {
      return bleedsToZero;
    }

    /**
     * Get whether the annulus rebuilds after bleed-down.
     *
     * @return true if it rebuilds after bleed-down
     */
    public boolean isRebuildsAfterBleed() {
      return rebuildsAfterBleed;
    }

    /**
     * Get whether thermal effects are excluded.
     *
     * @return true if thermal effects are excluded
     */
    public boolean isThermalEffectsExcluded() {
      return thermalEffectsExcluded;
    }
  }

  /** Well identifier for traceability. */
  private final String wellId;

  /** Well type string (e.g. "OIL_PRODUCER", "WATER_INJECTOR"). */
  private String wellType = "";

  /** Monitored annulus readings. */
  private final List<AnnulusReading> annuli = new ArrayList<AnnulusReading>();

  /** Optional barrier schematic folded into the screening. */
  private WellBarrierSchematic barrierSchematic;

  /** Per-annulus classification results, keyed by annulus id. */
  private final Map<String, AnnulusClassification> annulusResults = new LinkedHashMap<String, AnnulusClassification>();

  /** Overall disposition. */
  private IntegrityDisposition disposition = IntegrityDisposition.INSUFFICIENT_DATA;

  /** Screening findings (human-readable). */
  private final List<String> findings = new ArrayList<String>();

  /** Applied standards references. */
  private final List<String> appliedStandards = new ArrayList<String>();

  /**
   * Create a well-integrity screening.
   *
   * @param wellId well identifier for traceability
   */
  public WellIntegrityScreening(String wellId) {
    this.wellId = wellId == null ? "" : wellId;
  }

  /**
   * Set the well type for screening rules.
   *
   * @param wellType well type string (e.g. "OIL_PRODUCER", "WATER_INJECTOR")
   */
  public void setWellType(String wellType) {
    this.wellType = wellType == null ? "" : wellType;
  }

  /**
   * Add a monitored annulus reading.
   *
   * @param reading the annulus reading
   */
  public void addAnnulus(AnnulusReading reading) {
    if (reading != null) {
      annuli.add(reading);
    }
  }

  /**
   * Attach an optional barrier schematic to fold into the screening.
   *
   * @param schematic the well barrier schematic
   */
  public void setBarrierSchematic(WellBarrierSchematic schematic) {
    this.barrierSchematic = schematic;
  }

  /**
   * Run the deterministic screening.
   *
   * <p>
   * Classifies each annulus relative to its MAASP and sustained-casing-pressure indicators, folds in any failed or
   * degraded barrier elements, and produces an overall conservative disposition for human review.
   * </p>
   *
   * @return the overall integrity disposition
   */
  public IntegrityDisposition screen() {
    annulusResults.clear();
    findings.clear();
    appliedStandards.clear();

    if (annuli.isEmpty() && barrierSchematic == null) {
      disposition = IntegrityDisposition.INSUFFICIENT_DATA;
      findings.add("INFO: No annulus readings or barrier schematic supplied; screening cannot proceed.");
      logger.info("Well integrity screening for {} has insufficient data", wellId);
      return disposition;
    }

    appliedStandards.add("API RP 90 - Annular Casing Pressure Management");
    appliedStandards.add("NORSOK D-010 Rev 5 Section 9 - Annulus monitoring");

    boolean anySevere = false;
    boolean anyElevated = false;

    for (AnnulusReading reading : annuli) {
      AnnulusClassification classification = classifyAnnulus(reading);
      annulusResults.put(reading.getId(), classification);
      if (classification == AnnulusClassification.EXCEEDS_MAASP
	  || classification == AnnulusClassification.SUSTAINED_CASING_PRESSURE) {
	anySevere = true;
      } else if (classification == AnnulusClassification.ELEVATED) {
	anyElevated = true;
      }
      if (!reading.isThermalEffectsExcluded()) {
	findings.add("WARNING: Annulus " + reading.getId()
	    + " readings do not exclude thermal effects; SCP diagnosis may be unreliable (API RP 90).");
      }
    }

    if (barrierSchematic != null) {
      appliedStandards.add("NORSOK D-010 Rev 5 Section 5 - Two-barrier principle");
      boolean barrierPassed = barrierSchematic.validate();
      int failed = countFailedElements(barrierSchematic);
      int degraded = countDegradedElements(barrierSchematic);
      if (!barrierPassed || failed > 0) {
	anySevere = true;
	findings.add("FAIL: Barrier schematic screening did not pass (" + failed
	    + " failed element(s)); two-barrier principle review required (NORSOK D-010 Sec 5).");
      }
      if (degraded > 0) {
	anyElevated = true;
	findings.add("WARNING: " + degraded
	    + " degraded barrier element(s) detected; increased verification recommended (NORSOK D-010 Sec 5).");
      }
    }

    if (anySevere) {
      disposition = IntegrityDisposition.INTERVENTION_REQUIRED;
      findings.add("SCREEN: Severe indicator(s) present - intervention review required (subject to human review).");
    } else if (anyElevated) {
      disposition = IntegrityDisposition.MONITOR;
      findings.add("SCREEN: Elevated indicator(s) present - increased monitoring recommended (subject to review).");
    } else {
      disposition = IntegrityDisposition.ACCEPTABLE;
      findings.add("SCREEN: No screening flags raised - routine monitoring continues (subject to human review).");
    }

    logger.info("Well integrity screening for {} disposition {}", wellId, disposition);
    return disposition;
  }

  /**
   * Classify a single annulus reading.
   *
   * @param reading annulus reading
   * @return classification relative to API RP 90 / NORSOK D-010
   */
  private AnnulusClassification classifyAnnulus(AnnulusReading reading) {
    double maasp = reading.getMaaspBara();
    double measured = reading.getMeasuredPressureBara();
    if (maasp <= 0.0 || Double.isNaN(measured)) {
      findings.add("INFO: Annulus " + reading.getId() + " has no usable MAASP/pressure; classified UNKNOWN.");
      return AnnulusClassification.UNKNOWN;
    }
    if (measured >= maasp) {
      findings.add("FAIL: Annulus " + reading.getId() + " pressure " + measured + " bara at/above MAASP " + maasp
	  + " bara (API RP 90).");
      return AnnulusClassification.EXCEEDS_MAASP;
    }
    if (!reading.isBleedsToZero() && reading.isRebuildsAfterBleed()) {
      findings.add("FAIL: Annulus " + reading.getId()
	  + " rebuilds after bleed-down - sustained casing pressure indication (API RP 90).");
      return AnnulusClassification.SUSTAINED_CASING_PRESSURE;
    }
    if (measured > ELEVATED_FRACTION * maasp) {
      findings.add("WARNING: Annulus " + reading.getId() + " pressure " + measured + " bara is elevated (> "
	  + (ELEVATED_FRACTION * 100.0) + "% of MAASP " + maasp + " bara).");
      return AnnulusClassification.ELEVATED;
    }
    return AnnulusClassification.NORMAL;
  }

  /**
   * Count failed elements across both envelopes of a schematic.
   *
   * @param schematic the barrier schematic
   * @return number of failed elements
   */
  private static int countFailedElements(WellBarrierSchematic schematic) {
    return countByStatus(schematic, BarrierElement.Status.FAILED);
  }

  /**
   * Count degraded elements across both envelopes of a schematic.
   *
   * @param schematic the barrier schematic
   * @return number of degraded elements
   */
  private static int countDegradedElements(WellBarrierSchematic schematic) {
    return countByStatus(schematic, BarrierElement.Status.DEGRADED);
  }

  /**
   * Count elements with a given status across both envelopes.
   *
   * @param schematic the barrier schematic
   * @param status the status to count
   * @return number of matching elements
   */
  private static int countByStatus(WellBarrierSchematic schematic, BarrierElement.Status status) {
    int count = 0;
    BarrierEnvelope[] envelopes = new BarrierEnvelope[] { schematic.getPrimaryEnvelope(),
	schematic.getSecondaryEnvelope() };
    for (BarrierEnvelope envelope : envelopes) {
      if (envelope == null) {
	continue;
      }
      for (BarrierElement element : envelope.getElements()) {
	if (element.getStatus() == status) {
	  count++;
	}
      }
    }
    return count;
  }

  /**
   * Get the overall disposition.
   *
   * @return the integrity disposition
   */
  public IntegrityDisposition getDisposition() {
    return disposition;
  }

  /**
   * Get the per-annulus classification results.
   *
   * @return unmodifiable map of annulus id to classification
   */
  public Map<String, AnnulusClassification> getAnnulusResults() {
    return Collections.unmodifiableMap(annulusResults);
  }

  /**
   * Get the screening findings.
   *
   * @return unmodifiable list of findings
   */
  public List<String> getFindings() {
    return Collections.unmodifiableList(findings);
  }

  /**
   * Get the applied standards references.
   *
   * @return unmodifiable list of applied standards
   */
  public List<String> getAppliedStandards() {
    return Collections.unmodifiableList(appliedStandards);
  }

  /**
   * Get the well identifier.
   *
   * @return well id
   */
  public String getWellId() {
    return wellId;
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
   * Build a summary map for JSON reporting.
   *
   * @return ordered map of screening results
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("wellId", wellId);
    result.put("wellType", wellType);
    result.put("disposition", disposition.name());
    result.put("reviewRequired", true);

    List<Map<String, Object>> annulusList = new ArrayList<Map<String, Object>>();
    for (AnnulusReading reading : annuli) {
      Map<String, Object> annulusMap = new LinkedHashMap<String, Object>();
      annulusMap.put("id", reading.getId());
      annulusMap.put("measuredPressureBara", reading.getMeasuredPressureBara());
      annulusMap.put("maaspBara", reading.getMaaspBara());
      annulusMap.put("bleedsToZero", reading.isBleedsToZero());
      annulusMap.put("rebuildsAfterBleed", reading.isRebuildsAfterBleed());
      annulusMap.put("thermalEffectsExcluded", reading.isThermalEffectsExcluded());
      AnnulusClassification classification = annulusResults.get(reading.getId());
      annulusMap.put("classification", classification == null ? "UNKNOWN" : classification.name());
      annulusList.add(annulusMap);
    }
    result.put("annuli", annulusList);

    if (barrierSchematic != null) {
      result.put("barrierSchematic", barrierSchematic.toMap());
    }
    result.put("findings", new ArrayList<String>(findings));
    result.put("appliedStandards", new ArrayList<String>(appliedStandards));
    return result;
  }
}
