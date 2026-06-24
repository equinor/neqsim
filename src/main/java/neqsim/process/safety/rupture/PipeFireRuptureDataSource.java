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
 * A data source binds the numerical NeqSim inputs to the evidence trail that produced them. It lets
 * document, piping-specification, process simulation, depressurization, and fire/PFP agents hand
 * one auditable object to the rupture solver and report generator.
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
  private final List<SafetyEvidenceReference> sourceDocumentEvidence;
  private final List<SafetyEvidenceReference> pipingSpecificationEvidence;
  private final List<SafetyEvidenceReference> processEvidence;
  private final List<SafetyEvidenceReference> fireScenarioEvidence;
  private final List<String> assumptions;
  private final List<String> gaps;
  private final boolean sourceDiagramsReviewed;
  private final boolean pidTopologyVerified;
  private final boolean pipingSpecificationRowsReviewed;
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
    this.sourceDocumentEvidence = immutableEvidence(builder.sourceDocumentEvidence);
    this.pipingSpecificationEvidence = immutableEvidence(builder.pipingSpecificationEvidence);
    this.processEvidence = immutableEvidence(builder.processEvidence);
    this.fireScenarioEvidence = immutableEvidence(builder.fireScenarioEvidence);
    this.assumptions = immutableText(builder.assumptions);
    this.gaps = immutableText(builder.gaps);
    this.sourceDiagramsReviewed = builder.sourceDiagramsReviewed;
    this.pidTopologyVerified = builder.pidTopologyVerified;
    this.pipingSpecificationRowsReviewed = builder.pipingSpecificationRowsReviewed;
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
   * Checks if detailed source diagram evidence was reviewed.
   *
   * @return true if source diagram evidence was reviewed
   */
  public boolean isSourceDiagramsReviewed() {
    return sourceDiagramsReviewed;
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
   * Checks if piping-specification rows were reviewed.
   *
   * @return true if piping-specification rows were reviewed and joined
   */
  public boolean isPipingSpecificationRowsReviewed() {
    return pipingSpecificationRowsReviewed;
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
    references.addAll(sourceDocumentEvidence);
    references.addAll(pipingSpecificationEvidence);
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
          "Build PipeFireRuptureInput from source documents, piping specifications, and process evidence before running the study.");
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
    if (!sourceDiagramsReviewed) {
      readiness.addWarning("source_diagrams",
          "Detailed source diagram evidence has not been marked reviewed.",
          "Download/read the relevant P&ID/isometric pages and record reviewed tag/line evidence.");
    }
    if (!pidTopologyVerified
        && (pidTopologyEvidence == null || !pidTopologyEvidence.isSimulationReady())) {
      readiness.addWarning("pid_topology", "P&ID topology or isolation boundary is not verified.",
          "Trace the segment, valves, nozzles, blowdown/relief path, and battery limits before design use.");
    }
    if (!pipingSpecificationRowsReviewed) {
      readiness.addWarning("piping_specification",
          "Piping specification rows are not reviewed and joined.",
          "Resolve the applicable piping specification revision and map OD/wall/allowance/material.");
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
          "Review API 521, ISO 23251, NORSOK S-001, and applicable piping-design requirements before formal use.");
    }
    for (String gap : gaps) {
      readiness.addWarning("gap", gap,
          "Close or explicitly accept this gap before design-grade use.");
    }
    if (humanReviewRequired) {
      readiness.addInfo("human_review",
          "Qualified engineering review is required for final acceptance.",
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
    map.put("pidTopologyEvidence",
        pidTopologyEvidence == null ? null : pidTopologyEvidence.toMap());
    map.put("sourceDocumentEvidence", evidenceMaps(sourceDocumentEvidence));
    map.put("pipingSpecificationEvidence", evidenceMaps(pipingSpecificationEvidence));
    map.put("processEvidence", evidenceMaps(processEvidence));
    map.put("fireScenarioEvidence", evidenceMaps(fireScenarioEvidence));
    map.put("assumptions", assumptions);
    map.put("gaps", gaps);
    map.put("sourceDiagramsReviewed", Boolean.valueOf(sourceDiagramsReviewed));
    map.put("pidTopologyVerified", Boolean.valueOf(pidTopologyVerified));
    map.put("pipingSpecificationRowsReviewed", Boolean.valueOf(pipingSpecificationRowsReviewed));
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
  private static List<SafetyEvidenceReference> immutableEvidence(
      List<SafetyEvidenceReference> references) {
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
    private final List<SafetyEvidenceReference> sourceDocumentEvidence =
        new ArrayList<SafetyEvidenceReference>();
    private final List<SafetyEvidenceReference> pipingSpecificationEvidence =
        new ArrayList<SafetyEvidenceReference>();
    private final List<SafetyEvidenceReference> processEvidence =
        new ArrayList<SafetyEvidenceReference>();
    private final List<SafetyEvidenceReference> fireScenarioEvidence =
        new ArrayList<SafetyEvidenceReference>();
    private final List<String> assumptions = new ArrayList<String>();
    private final List<String> gaps = new ArrayList<String>();
    private boolean sourceDiagramsReviewed;
    private boolean pidTopologyVerified;
    private boolean pipingSpecificationRowsReviewed;
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
     * Adds source-document evidence.
     *
     * @param reference evidence reference
     * @return this builder
     */
    public Builder addSourceDocumentEvidence(SafetyEvidenceReference reference) {
      addEvidence(sourceDocumentEvidence, reference);
      return this;
    }

    /**
     * Adds piping-specification evidence.
     *
     * @param reference evidence reference
     * @return this builder
     */
    public Builder addPipingSpecificationEvidence(SafetyEvidenceReference reference) {
      addEvidence(pipingSpecificationEvidence, reference);
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
     * Marks detailed source diagram evidence reviewed.
     *
     * @param sourceDiagramsReviewed true if reviewed
     * @return this builder
     */
    public Builder sourceDiagramsReviewed(boolean sourceDiagramsReviewed) {
      this.sourceDiagramsReviewed = sourceDiagramsReviewed;
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
     * Marks piping-specification rows reviewed and joined.
     *
     * @param pipingSpecificationRowsReviewed true if reviewed
     * @return this builder
     */
    public Builder pipingSpecificationRowsReviewed(boolean pipingSpecificationRowsReviewed) {
      this.pipingSpecificationRowsReviewed = pipingSpecificationRowsReviewed;
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
    private static void addEvidence(List<SafetyEvidenceReference> target,
        SafetyEvidenceReference reference) {
      if (reference != null) {
        target.add(reference);
      }
    }
  }
}
