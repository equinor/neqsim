package neqsim.process.safety.rupture;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Governed input package for a blowdown pipe fire-rupture study.
 *
 * <p>
 * A data source binds the numerical NeqSim inputs to the evidence trail that produced them. It lets STID, TR2000,
 * process simulation, depressurization, and fire/PFP agents hand one auditable object to the rupture solver and report
 * generator.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class PipeFireRuptureDataSource implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String studyId;
  private final PipeFireRuptureInput input;
  private final PipeFireRuptureMaterial material;
  private final PipeFireRuptureScenario scenario;
  private final BlowdownPressureProfile pressureProfile;
  private final PidTopologyEvidence pidTopologyEvidence;
  private final List<SafetyEvidenceReference> stidEvidence;
  private final List<SafetyEvidenceReference> tr2000Evidence;
  private final List<SafetyEvidenceReference> processEvidence;
  private final List<SafetyEvidenceReference> fireScenarioEvidence;
  private final List<String> assumptions;
  private final List<String> gaps;
  private final boolean stidDiagramReviewed;
  private final boolean pidTopologyVerified;
  private final boolean tr2000PipeRowsFetched;
  private final boolean materialCertificateReviewed;
  private final boolean blowdownProfileVerified;
  private final boolean fireScenarioReviewed;
  private final boolean standardsReviewed;
  private final boolean humanReviewRequired;

  /**
   * Creates a data source.
   *
   * @param builder populated builder
   */
  private PipeFireRuptureDataSource(Builder builder) {
    this.studyId = clean(builder.studyId);
    this.input = builder.input;
    this.material = builder.material;
    this.scenario = builder.scenario;
    this.pressureProfile = builder.pressureProfile;
    this.pidTopologyEvidence = builder.pidTopologyEvidence;
    this.stidEvidence = immutableEvidence(builder.stidEvidence);
    this.tr2000Evidence = immutableEvidence(builder.tr2000Evidence);
    this.processEvidence = immutableEvidence(builder.processEvidence);
    this.fireScenarioEvidence = immutableEvidence(builder.fireScenarioEvidence);
    this.assumptions = immutableText(builder.assumptions);
    this.gaps = immutableText(builder.gaps);
    this.stidDiagramReviewed = builder.stidDiagramReviewed;
    this.pidTopologyVerified = builder.pidTopologyVerified;
    this.tr2000PipeRowsFetched = builder.tr2000PipeRowsFetched;
    this.materialCertificateReviewed = builder.materialCertificateReviewed;
    this.blowdownProfileVerified = builder.blowdownProfileVerified;
    this.fireScenarioReviewed = builder.fireScenarioReviewed;
    this.standardsReviewed = builder.standardsReviewed;
    this.humanReviewRequired = builder.humanReviewRequired;
  }

  /**
   * Creates a data-source builder.
   *
   * @param studyId study identifier
   * @return data-source builder
   */
  public static Builder builder(String studyId) {
    return new Builder(studyId);
  }

  /**
   * Gets the study identifier.
   *
   * @return study identifier
   */
  public String getStudyId() {
    return studyId;
  }

  /**
   * Gets pipe input.
   *
   * @return pipe input or null
   */
  public PipeFireRuptureInput getInput() {
    return input;
  }

  /**
   * Gets material curve.
   *
   * @return material curve or null
   */
  public PipeFireRuptureMaterial getMaterial() {
    return material;
  }

  /**
   * Gets fire scenario.
   *
   * @return fire scenario or null
   */
  public PipeFireRuptureScenario getScenario() {
    return scenario;
  }

  /**
   * Gets pressure profile.
   *
   * @return pressure profile or null
   */
  public BlowdownPressureProfile getPressureProfile() {
    return pressureProfile;
  }

  /**
   * Gets P&amp;ID topology evidence.
   *
   * @return topology evidence or null
   */
  public PidTopologyEvidence getPidTopologyEvidence() {
    return pidTopologyEvidence;
  }

  /**
   * Checks if detailed STID diagram evidence was reviewed.
   *
   * @return true if STID diagram evidence was reviewed
   */
  public boolean isStidDiagramReviewed() {
    return stidDiagramReviewed;
  }

  /**
   * Checks if P&amp;ID topology was verified.
   *
   * @return true if P&amp;ID topology was verified
   */
  public boolean isPidTopologyVerified() {
    return pidTopologyVerified;
  }

  /**
   * Checks if TR2000 pipe rows were fetched.
   *
   * @return true if TR2000 pipe rows were fetched and joined
   */
  public boolean isTr2000PipeRowsFetched() {
    return tr2000PipeRowsFetched;
  }

  /**
   * Checks if a material certificate or MDS basis was reviewed.
   *
   * @return true if material evidence was reviewed
   */
  public boolean isMaterialCertificateReviewed() {
    return materialCertificateReviewed;
  }

  /**
   * Checks if the blowdown profile was verified.
   *
   * @return true if pressure profile is verified
   */
  public boolean isBlowdownProfileVerified() {
    return blowdownProfileVerified;
  }

  /**
   * Checks if the fire scenario was reviewed.
   *
   * @return true if fire/PFP basis is reviewed
   */
  public boolean isFireScenarioReviewed() {
    return fireScenarioReviewed;
  }

  /**
   * Checks if standards basis was reviewed.
   *
   * @return true if standards were reviewed
   */
  public boolean isStandardsReviewed() {
    return standardsReviewed;
  }

  /**
   * Gets all evidence references.
   *
   * @return immutable evidence list
   */
  public List<SafetyEvidenceReference> getAllEvidenceReferences() {
    List<SafetyEvidenceReference> references = new ArrayList<SafetyEvidenceReference>();
    references.addAll(stidEvidence);
    references.addAll(tr2000Evidence);
    references.addAll(processEvidence);
    references.addAll(fireScenarioEvidence);
    if (input != null) {
      references.addAll(input.getEvidenceReferences());
    }
    return Collections.unmodifiableList(references);
  }

  /**
   * Creates the readiness verdict for this data source.
   *
   * @return readiness verdict with findings and evidence references
   */
  public SafetyStudyReadiness readiness() {
    SafetyStudyReadiness.Builder readiness = SafetyStudyReadiness.builder();
    for (SafetyEvidenceReference reference : getAllEvidenceReferences()) {
      readiness.addEvidenceReference(reference);
    }
    if (pidTopologyEvidence != null) {
      readiness.merge(pidTopologyEvidence.readiness());
    }
    if (input == null) {
      readiness.addBlocker("pipe_input", "Pipe geometry and fluid input are missing.",
	  "Build PipeFireRuptureInput from STID/TR2000/process evidence before running the study.");
    }
    if (material == null) {
      readiness.addBlocker("material", "Pipe material curve is missing.",
	  "Select a governed material curve from MDS/material certificate evidence.");
    }
    if (scenario == null) {
      readiness.addBlocker("fire_scenario", "Fire exposure scenario is missing.",
	  "Define jet/pool fire or PFP-reduced heat flux basis before running the study.");
    }
    if (pressureProfile == null) {
      readiness.addBlocker("pressure_profile", "Blowdown pressure profile is missing.",
	  "Run or import a governed depressurization profile before running the study.");
    }
    if (!stidDiagramReviewed) {
      readiness.addWarning("stid", "Detailed STID diagram evidence has not been marked reviewed.",
	  "Download/read the relevant P&ID/isometric pages and record reviewed tag/line evidence.");
    }
    if (!pidTopologyVerified && (pidTopologyEvidence == null || !pidTopologyEvidence.isSimulationReady())) {
      readiness.addWarning("pid_topology", "P&ID topology or isolation boundary is not verified.",
	  "Trace the segment, valves, nozzles, blowdown/relief path, and battery limits before design use.");
    }
    if (!tr2000PipeRowsFetched) {
      readiness.addWarning("tr2000", "TR2000 pipe size and element rows are not fetched/joined.",
	  "Resolve latest plant Issue revision, fetch PCS rows, and map OD/wall/allowance/material.");
    }
    if (!materialCertificateReviewed) {
      readiness.addWarning("material", "Material certificate or MDS basis has not been reviewed.",
	  "Confirm material grade and high-temperature properties before design-grade conclusions.");
    }
    if (!blowdownProfileVerified) {
      readiness.addWarning("depressurization", "Blowdown pressure profile is not marked verified.",
	  "Verify the pressure profile against a governed NeqSim/API 521 depressurization model.");
    }
    if (!fireScenarioReviewed) {
      readiness.addWarning("fire_scenario", "Fire scenario and PFP basis are not marked reviewed.",
	  "Confirm fire type, heat flux, exposed length, and passive fire protection assumptions.");
    }
    if (!standardsReviewed) {
      readiness.addWarning("standards", "Standards basis has not been marked reviewed.",
	  "Review API 521/ISO 23251/NORSOK S-001/TR2000 applicability before formal use.");
    }
    for (String gap : gaps) {
      readiness.addWarning("gap", gap, "Close or explicitly accept this gap before design-grade use.");
    }
    if (humanReviewRequired) {
      readiness.addInfo("human_review", "Qualified engineering review is required for final acceptance.",
	  "Use this calculation as an auditable handoff into formal process-safety verification.");
    }
    return readiness.build();
  }

  /**
   * Converts data source to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("schemaVersion", "pipe_fire_rupture_data_source.v1");
    map.put("studyId", studyId);
    map.put("input", input == null ? null : input.toMap());
    map.put("material", material == null ? null : material.toMap());
    map.put("scenario", scenario == null ? null : scenario.toMap());
    map.put("pressureProfile", pressureProfile == null ? null : pressureProfile.toMap());
    map.put("pidTopologyEvidence", pidTopologyEvidence == null ? null : pidTopologyEvidence.toMap());
    map.put("stidEvidence", evidenceMaps(stidEvidence));
    map.put("tr2000Evidence", evidenceMaps(tr2000Evidence));
    map.put("processEvidence", evidenceMaps(processEvidence));
    map.put("fireScenarioEvidence", evidenceMaps(fireScenarioEvidence));
    map.put("assumptions", assumptions);
    map.put("gaps", gaps);
    map.put("stidDiagramReviewed", Boolean.valueOf(stidDiagramReviewed));
    map.put("pidTopologyVerified", Boolean.valueOf(pidTopologyVerified));
    map.put("tr2000PipeRowsFetched", Boolean.valueOf(tr2000PipeRowsFetched));
    map.put("materialCertificateReviewed", Boolean.valueOf(materialCertificateReviewed));
    map.put("blowdownProfileVerified", Boolean.valueOf(blowdownProfileVerified));
    map.put("fireScenarioReviewed", Boolean.valueOf(fireScenarioReviewed));
    map.put("standardsReviewed", Boolean.valueOf(standardsReviewed));
    map.put("humanReviewRequired", Boolean.valueOf(humanReviewRequired));
    map.put("readiness", readiness().toMap());
    return map;
  }

  /**
   * Converts data source to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(toMap());
  }

  /**
   * Normalizes nullable text.
   *
   * @param value text value
   * @return trimmed text or empty string
   */
  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * Copies evidence references.
   *
   * @param references references to copy
   * @return immutable copy
   */
  private static List<SafetyEvidenceReference> immutableEvidence(List<SafetyEvidenceReference> references) {
    return Collections.unmodifiableList(new ArrayList<SafetyEvidenceReference>(references));
  }

  /**
   * Copies text values.
   *
   * @param values values to copy
   * @return immutable copy
   */
  private static List<String> immutableText(List<String> values) {
    return Collections.unmodifiableList(new ArrayList<String>(values));
  }

  /**
   * Converts evidence references to maps.
   *
   * @param references references to convert
   * @return map list
   */
  private static List<Map<String, Object>> evidenceMaps(List<SafetyEvidenceReference> references) {
    List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();
    for (SafetyEvidenceReference reference : references) {
      maps.add(reference.toMap());
    }
    return maps;
  }

  /** Builder for {@link PipeFireRuptureDataSource}. */
  public static final class Builder {
    private final String studyId;
    private PipeFireRuptureInput input;
    private PipeFireRuptureMaterial material;
    private PipeFireRuptureScenario scenario;
    private BlowdownPressureProfile pressureProfile;
    private PidTopologyEvidence pidTopologyEvidence;
    private final List<SafetyEvidenceReference> stidEvidence = new ArrayList<SafetyEvidenceReference>();
    private final List<SafetyEvidenceReference> tr2000Evidence = new ArrayList<SafetyEvidenceReference>();
    private final List<SafetyEvidenceReference> processEvidence = new ArrayList<SafetyEvidenceReference>();
    private final List<SafetyEvidenceReference> fireScenarioEvidence = new ArrayList<SafetyEvidenceReference>();
    private final List<String> assumptions = new ArrayList<String>();
    private final List<String> gaps = new ArrayList<String>();
    private boolean stidDiagramReviewed;
    private boolean pidTopologyVerified;
    private boolean tr2000PipeRowsFetched;
    private boolean materialCertificateReviewed;
    private boolean blowdownProfileVerified;
    private boolean fireScenarioReviewed;
    private boolean standardsReviewed;
    private boolean humanReviewRequired = true;

    /**
     * Creates a builder.
     *
     * @param studyId study identifier
     */
    private Builder(String studyId) {
      if (clean(studyId).isEmpty()) {
	throw new IllegalArgumentException("studyId must not be empty");
      }
      this.studyId = studyId;
    }

    /**
     * Sets pipe input.
     *
     * @param input pipe input
     * @return this builder
     */
    public Builder input(PipeFireRuptureInput input) {
      this.input = input;
      return this;
    }

    /**
     * Sets material curve.
     *
     * @param material material curve
     * @return this builder
     */
    public Builder material(PipeFireRuptureMaterial material) {
      this.material = material;
      return this;
    }

    /**
     * Sets fire scenario.
     *
     * @param scenario fire scenario
     * @return this builder
     */
    public Builder scenario(PipeFireRuptureScenario scenario) {
      this.scenario = scenario;
      return this;
    }

    /**
     * Sets pressure profile.
     *
     * @param pressureProfile blowdown pressure profile
     * @return this builder
     */
    public Builder pressureProfile(BlowdownPressureProfile pressureProfile) {
      this.pressureProfile = pressureProfile;
      return this;
    }

    /**
     * Sets P&amp;ID topology evidence.
     *
     * @param pidTopologyEvidence topology evidence
     * @return this builder
     */
    public Builder pidTopologyEvidence(PidTopologyEvidence pidTopologyEvidence) {
      this.pidTopologyEvidence = pidTopologyEvidence;
      return this;
    }

    /**
     * Adds STID evidence.
     *
     * @param reference evidence reference
     * @return this builder
     */
    public Builder addStidEvidence(SafetyEvidenceReference reference) {
      addEvidence(stidEvidence, reference);
      return this;
    }

    /**
     * Adds TR2000 evidence.
     *
     * @param reference evidence reference
     * @return this builder
     */
    public Builder addTr2000Evidence(SafetyEvidenceReference reference) {
      addEvidence(tr2000Evidence, reference);
      return this;
    }

    /**
     * Adds process-state evidence.
     *
     * @param reference evidence reference
     * @return this builder
     */
    public Builder addProcessEvidence(SafetyEvidenceReference reference) {
      addEvidence(processEvidence, reference);
      return this;
    }

    /**
     * Adds fire-scenario evidence.
     *
     * @param reference evidence reference
     * @return this builder
     */
    public Builder addFireScenarioEvidence(SafetyEvidenceReference reference) {
      addEvidence(fireScenarioEvidence, reference);
      return this;
    }

    /**
     * Adds an assumption.
     *
     * @param assumption assumption text
     * @return this builder
     */
    public Builder addAssumption(String assumption) {
      if (!clean(assumption).isEmpty()) {
	assumptions.add(clean(assumption));
      }
      return this;
    }

    /**
     * Adds a gap.
     *
     * @param gap gap text
     * @return this builder
     */
    public Builder addGap(String gap) {
      if (!clean(gap).isEmpty()) {
	gaps.add(clean(gap));
      }
      return this;
    }

    /**
     * Marks detailed STID diagram evidence reviewed.
     *
     * @param stidDiagramReviewed true if reviewed
     * @return this builder
     */
    public Builder stidDiagramReviewed(boolean stidDiagramReviewed) {
      this.stidDiagramReviewed = stidDiagramReviewed;
      return this;
    }

    /**
     * Marks P&amp;ID topology verified.
     *
     * @param pidTopologyVerified true if verified
     * @return this builder
     */
    public Builder pidTopologyVerified(boolean pidTopologyVerified) {
      this.pidTopologyVerified = pidTopologyVerified;
      return this;
    }

    /**
     * Marks TR2000 pipe rows fetched and joined.
     *
     * @param tr2000PipeRowsFetched true if fetched
     * @return this builder
     */
    public Builder tr2000PipeRowsFetched(boolean tr2000PipeRowsFetched) {
      this.tr2000PipeRowsFetched = tr2000PipeRowsFetched;
      return this;
    }

    /**
     * Marks material certificate or MDS evidence reviewed.
     *
     * @param materialCertificateReviewed true if reviewed
     * @return this builder
     */
    public Builder materialCertificateReviewed(boolean materialCertificateReviewed) {
      this.materialCertificateReviewed = materialCertificateReviewed;
      return this;
    }

    /**
     * Marks blowdown profile verified.
     *
     * @param blowdownProfileVerified true if verified
     * @return this builder
     */
    public Builder blowdownProfileVerified(boolean blowdownProfileVerified) {
      this.blowdownProfileVerified = blowdownProfileVerified;
      return this;
    }

    /**
     * Marks fire scenario reviewed.
     *
     * @param fireScenarioReviewed true if reviewed
     * @return this builder
     */
    public Builder fireScenarioReviewed(boolean fireScenarioReviewed) {
      this.fireScenarioReviewed = fireScenarioReviewed;
      return this;
    }

    /**
     * Marks standards reviewed.
     *
     * @param standardsReviewed true if reviewed
     * @return this builder
     */
    public Builder standardsReviewed(boolean standardsReviewed) {
      this.standardsReviewed = standardsReviewed;
      return this;
    }

    /**
     * Sets human-review flag.
     *
     * @param humanReviewRequired true if human review is required
     * @return this builder
     */
    public Builder humanReviewRequired(boolean humanReviewRequired) {
      this.humanReviewRequired = humanReviewRequired;
      return this;
    }

    /**
     * Builds the data source.
     *
     * @return data source
     */
    public PipeFireRuptureDataSource build() {
      return new PipeFireRuptureDataSource(this);
    }

    /**
     * Adds evidence to a target list.
     *
     * @param target target list
     * @param reference evidence reference; ignored when null
     */
    private static void addEvidence(List<SafetyEvidenceReference> target, SafetyEvidenceReference reference) {
      if (reference != null) {
	target.add(reference);
      }
    }
  }
}
