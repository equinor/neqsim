package neqsim.process.safety.depressurization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.safety.overpressure.OverpressureProtectionStudy;
import neqsim.process.safety.overpressure.ReliefScenario;
import neqsim.thermo.system.SystemInterface;
import neqsim.standards.gasquality.Standard_ISO6976;

/** Controlled input joining steady relief scenarios with a transient blowdown and flare model. */
public final class CoupledReliefBlowdownFlareInput implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Event-relative discharge interval, including its start and excluding its end. */
  public static final class DischargeWindow implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String event;
    private final double startSeconds;
    private final Double endSeconds;

    private DischargeWindow(String event, double startSeconds, Double endSeconds) {
      this.event = requireText(event, "initiatingEvent");
      if (!Double.isFinite(startSeconds) || startSeconds < 0.0
          || (endSeconds != null && (!Double.isFinite(endSeconds) || endSeconds <= startSeconds))) {
        throw new IllegalArgumentException("Window requires finite 0 <= start < end in seconds");
      }
      this.startSeconds = startSeconds;
      this.endSeconds = endSeconds;
    }

    /** @return name of the common initiating event */
    public String getEvent() {
      return event;
    }

    /** @return event-relative start time in seconds */
    public double getStartSeconds() {
      return startSeconds;
    }

    /** @return end time in seconds, or null for an unbounded steady case or a complete dynamic history */
    public Double getEndSeconds() {
      return endSeconds;
    }

    /** @return serializable window in engineering units */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("initiatingEvent", event);
      map.put("startSeconds", startSeconds);
      map.put("endSeconds", endSeconds);
      return map;
    }
  }

  /** One protected-item relief study and its simultaneous-load group. */
  public static final class ReliefStudyEntry implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final OverpressureProtectionStudy study;
    private final String concurrencyGroup;
    private final DischargeWindow window;
    private final Double lowerHeatingValueJPerKg;
    private final ReliefScenario selectedScenario;
    private final String pureComponent;
    private final Double criticalTemperatureK;

    private ReliefStudyEntry(OverpressureProtectionStudy study, String concurrencyGroup, DischargeWindow window,
        SystemInterface relievingGas, String scenarioName) {
      if (study == null) {
        throw new IllegalArgumentException("study must not be null");
      }
      this.study = study;
      this.concurrencyGroup = requireText(concurrencyGroup, "concurrencyGroup");
      this.window = window;
      this.lowerHeatingValueJPerKg = relievingGas == null ? null : gasHeatingValue(relievingGas);
      boolean pure = relievingGas != null && relievingGas.getNumberOfComponents() == 1;
      pureComponent = pure ? relievingGas.getComponent(0).getComponentName() : null;
      criticalTemperatureK = pure ? relievingGas.getComponent(0).getTC() : null;
      ReliefScenario selected = null;
      if (scenarioName != null) {
        for (ReliefScenario scenario : study.getScenarios()) {
          if (scenarioName.equals(scenario.getName()) && scenario.isCredible()) {
            if (selected != null) {
              throw new IllegalArgumentException("Ambiguous relief scenario " + scenarioName);
            }
            selected = scenario;
          }
        }
        if (selected == null) {
          throw new IllegalArgumentException("No credible relief scenario named " + scenarioName);
        }
      }
      selectedScenario = selected;
    }

    /** @return optional window for an explicitly timed relief */
    public DischargeWindow getWindow() {
      return window;
    }

    /** @return relieving-gas net heat of combustion in J/kg, or null when unspecified */
    public Double getLowerHeatingValueJPerKg() {
      return lowerHeatingValueJPerKg;
    }

    /** @return explicitly selected credible scenario, or null to use the study maximum */
    public ReliefScenario getSelectedScenario() {
      return selectedScenario;
    }

    String getPureComponent() {
      return pureComponent;
    }

    Double getCriticalTemperatureK() {
      return criticalTemperatureK;
    }

    public OverpressureProtectionStudy getStudy() {
      return study;
    }

    public String getConcurrencyGroup() {
      return concurrencyGroup;
    }
  }

  private final String studyId;
  private final List<ReliefStudyEntry> reliefStudies;
  private final DynamicBlowdownFlareStudyDataSource dynamicStudy;
  private final Map<String, DischargeWindow> blowdownWindows;
  private final List<String> evidenceReferences;
  private final boolean scenarioSelectionReviewed;

  private CoupledReliefBlowdownFlareInput(Builder builder) {
    studyId = requireText(builder.studyId, "studyId");
    reliefStudies = Collections.unmodifiableList(new ArrayList<ReliefStudyEntry>(builder.reliefStudies));
    dynamicStudy = builder.dynamicStudy;
    blowdownWindows = Collections.unmodifiableMap(new LinkedHashMap<String, DischargeWindow>(builder.blowdownWindows));
    for (String source : blowdownWindows.keySet()) {
      if (dynamicStudy == null || dynamicStudy.getSources().stream().noneMatch(s -> s.getSourceId().equals(source))) {
        throw new IllegalArgumentException("No dynamic source named " + source);
      }
    }
    evidenceReferences = Collections.unmodifiableList(new ArrayList<String>(builder.evidenceReferences));
    scenarioSelectionReviewed = builder.scenarioSelectionReviewed;
  }

  public static Builder builder(String studyId) {
    return new Builder(studyId);
  }

  public String getStudyId() {
    return studyId;
  }

  public List<ReliefStudyEntry> getReliefStudies() {
    return reliefStudies;
  }

  public DynamicBlowdownFlareStudyDataSource getDynamicStudy() {
    return dynamicStudy;
  }

  /** @return explicit BDV windows keyed by the dynamic source ID */
  public Map<String, DischargeWindow> getBlowdownWindows() {
    return blowdownWindows == null ? Collections.<String, DischargeWindow>emptyMap() : blowdownWindows;
  }

  public List<String> getEvidenceReferences() {
    return evidenceReferences;
  }

  public boolean isScenarioSelectionReviewed() {
    return scenarioSelectionReviewed;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("studyId", studyId);
    List<Map<String, Object>> studies = new ArrayList<Map<String, Object>>();
    for (ReliefStudyEntry entry : reliefStudies) {
      Map<String, Object> study = new LinkedHashMap<String, Object>();
      study.put("protectedItem", entry.getStudy().getItem().getName());
      study.put("concurrencyGroup", entry.getConcurrencyGroup());
      study.put("selectedScenario", entry.getSelectedScenario() == null ? null : entry.getSelectedScenario().getName());
      study.put("window", entry.getWindow() == null ? null : entry.getWindow().toMap());
      study.put("lowerHeatingValueJPerKg", entry.getLowerHeatingValueJPerKg());
      study.put("scenarioCount", Integer.valueOf(entry.getStudy().getScenarios().size()));
      studies.add(study);
    }
    result.put("reliefStudies", studies);
    Map<String, Object> windows = new LinkedHashMap<String, Object>();
    getBlowdownWindows().forEach((id, window) -> windows.put(id, window.toMap()));
    result.put("blowdownWindows", windows);
    result.put("dynamicStudyId", dynamicStudy == null ? "" : dynamicStudy.getStudyId());
    result.put("evidenceReferences", new ArrayList<String>(evidenceReferences));
    result.put("scenarioSelectionReviewed", Boolean.valueOf(scenarioSelectionReviewed));
    return result;
  }

  /** Builder for a coupled study input. */
  public static final class Builder {
    private final String studyId;
    private final List<ReliefStudyEntry> reliefStudies = new ArrayList<ReliefStudyEntry>();
    private final Map<String, DischargeWindow> blowdownWindows = new LinkedHashMap<String, DischargeWindow>();
    private DynamicBlowdownFlareStudyDataSource dynamicStudy;
    private final List<String> evidenceReferences = new ArrayList<String>();
    private boolean scenarioSelectionReviewed;

    private Builder(String studyId) {
      this.studyId = studyId;
    }

    public Builder addReliefStudy(OverpressureProtectionStudy study, String concurrencyGroup) {
      reliefStudies.add(new ReliefStudyEntry(study, concurrencyGroup, null, null, null));
      return this;
    }

    /**
     * Add a steady relief case with an explicit gas combustion basis.
     *
     * @param study protected-item study
     * @param event initiating event
     * @param relievingGas representative relieving gas composition
     * @return this builder
     */
    public Builder addReliefStudy(OverpressureProtectionStudy study, String event, SystemInterface relievingGas) {
      reliefStudies.add(new ReliefStudyEntry(study, event, null, relievingGas, null));
      return this;
    }

    /**
     * Schedule the maximum credible relief demand from a protected-item study for a named event.
     *
     * @param study protected-item study
     * @param event initiating event shared by simultaneous sources
     * @param startSeconds inclusive start in seconds
     * @param endSeconds exclusive end in seconds
     * @param relievingGas optional gas composition for thermal capacity assessment
     * @return this builder
     */
    public Builder addReliefStudy(OverpressureProtectionStudy study, String event, double startSeconds,
        double endSeconds, SystemInterface relievingGas) {
      reliefStudies.add(
          new ReliefStudyEntry(study, event, new DischargeWindow(event, startSeconds, endSeconds), relievingGas, null));
      return this;
    }

    /**
     * Schedule a specific credible scenario from a multi-contingency study.
     *
     * @param study protected-item study
     * @param scenarioName unique credible scenario in the study
     * @param event initiating event
     * @param startSeconds inclusive start in seconds
     * @param endSeconds exclusive end in seconds
     * @param relievingGas optional gas composition for thermal capacity assessment
     * @return this builder
     */
    public Builder addReliefScenario(OverpressureProtectionStudy study, String scenarioName, String event,
        double startSeconds, double endSeconds, SystemInterface relievingGas) {
      reliefStudies.add(new ReliefStudyEntry(study, event, new DischargeWindow(event, startSeconds, endSeconds),
          relievingGas, requireText(scenarioName, "scenarioName")));
      return this;
    }

    /**
     * Assign the simulated source history to an initiating event and valve-open window.
     *
     * @param sourceId source in the dynamic study
     * @param event initiating event
     * @param startSeconds opening time in seconds
     * @param endSeconds exclusive end in seconds, or null to use the entire simulated history
     * @return this builder
     */
    public Builder scheduleBlowdown(String sourceId, String event, double startSeconds, Double endSeconds) {
      String id = requireText(sourceId, "sourceId");
      if (blowdownWindows.containsKey(id)) {
        throw new IllegalArgumentException("Duplicate BDV schedule for " + id);
      }
      blowdownWindows.put(id, new DischargeWindow(event, startSeconds, endSeconds));
      return this;
    }

    public Builder dynamicStudy(DynamicBlowdownFlareStudyDataSource value) {
      dynamicStudy = value;
      return this;
    }

    public Builder addEvidenceReference(String value) {
      String normalized = value == null ? "" : value.trim();
      if (!normalized.isEmpty() && !evidenceReferences.contains(normalized)) {
        evidenceReferences.add(normalized);
      }
      return this;
    }

    public Builder scenarioSelectionReviewed(boolean value) {
      scenarioSelectionReviewed = value;
      return this;
    }

    public CoupledReliefBlowdownFlareInput build() {
      return new CoupledReliefBlowdownFlareInput(this);
    }
  }

  /**
   * Evaluate the gas combustion basis used by the flare equipment.
   *
   * @param gas discharge gas composition
   * @return net heating value in J/kg
   */
  static double gasHeatingValue(SystemInterface gas) {
    SystemInterface initialized = gas.clone();
    initialized.init(0);
    Standard_ISO6976 standard = new Standard_ISO6976(initialized, 0.0, 15.55, "molar");
    standard.setReferenceState("real");
    standard.calculate();
    return standard.getValue("InferiorCalorificValue") * 1000.0 / initialized.getMolarMass();
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
