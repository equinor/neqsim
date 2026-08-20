package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.ConstraintDefinition;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.EvaluationResult;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.ObjectiveDefinition;
import neqsim.process.util.optimizer.ProcessModelSimulationEvaluator.ParameterDefinition;

/**
 * Evaluates one installed-capacity alternative against a common deterministic production-search
 * policy.
 *
 * <p>
 * The study resolves one direct {@link CapacityConstraint} by its stable
 * {@code area::equipment/constraint} address, freezes its complete observable state, evaluates the
 * baseline, applies and verifies the proposed applicable limit, evaluates the alternative, and
 * finally restores both the installed constraint and the pre-study process operating point. Every
 * result is immutable and serializable; no live model, equipment, constraint, search callback, or
 * metric callback is retained.
 * </p>
 *
 * <p>
 * A result is paired simulator evidence for the declared candidate set. It is not proof of causal
 * production loss, global optimality, a KKT multiplier, design adequacy, certified emissions, or
 * investment approval. Physical, energy, emission, and economic metrics remain separate and are
 * compared only through identical metric definitions and units.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class ProcessModelDebottleneckStudy {
  /** Direction of the installed applicable limit. */
  public enum LimitDirection {
    /** Values above the installed limit are worse. */
    MAXIMUM,
    /** Values below the installed limit are worse. */
    MINIMUM
  }

  /** Outcome of the complete paired study. */
  public enum StudyOutcome {
    /** Both scenarios and all required metrics are qualified and state was recovered. */
    COMPLETED,
    /** The named installed constraint could not be safely resolved or changed. */
    ALTERNATIVE_NOT_APPLICABLE,
    /** No qualified baseline operating point was selected. */
    BASELINE_FAILED,
    /** The alternative scenario did not produce a qualified operating point. */
    ALTERNATIVE_FAILED,
    /** Physical scenarios completed but at least one required metric was unavailable. */
    REQUIRED_METRIC_UNAVAILABLE,
    /** The installed constraint or pre-study process state was not exactly recovered. */
    RESTORATION_FAILED
  }

  /** Status of one completed scenario. */
  public enum ScenarioStatus {
    /** Search and verification produced a converged, feasible operating point. */
    QUALIFIED,
    /** Search threw or did not select a candidate. */
    SEARCH_FAILED,
    /** The selected candidate failed deterministic verification. */
    VERIFICATION_FAILED
  }

  /** Engineering role of a registered scenario metric. */
  public enum MetricKind {
    PRODUCTION,
    POWER,
    ENERGY,
    DIRECT_EMISSIONS,
    INDIRECT_EMISSIONS,
    SCREENING_ECONOMIC,
    OTHER
  }

  /** Availability of one sampled metric. */
  public enum MetricStatus {
    AVAILABLE,
    NON_FINITE,
    SAMPLING_FAILED,
    NOT_SAMPLED
  }

  /** Serializable callback implementing the same search policy for both scenarios. */
  public interface ScenarioSearch extends Serializable {
    /** @return stable search-policy identifier */
    String getId();

    /** @return human-readable search-policy name */
    String getName();

    /** @return source and assumptions for the search policy */
    String getProvenance();

    /**
     * Selects one parameter vector. The study independently verifies the selected vector.
     *
     * @param evaluator live process-model evaluator
     * @return immutable search selection
     */
    SearchSelection search(ProcessModelSimulationEvaluator evaluator);
  }

  /** Serializable metric callback sampled once at each verified scenario point. */
  public interface MetricSampler extends Serializable {
    /**
     * Samples the completed process model.
     *
     * @param model completed scenario model
     * @return metric value in the definition's unit
     */
    double sample(ProcessModel model);
  }

  /** Immutable definition of one installed-capacity alternative. */
  public static final class CapacityAlternative implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String provenance;
    private final String areaName;
    private final String equipmentName;
    private final String constraintName;
    private final double proposedLimit;
    private final String unit;
    private final LimitDirection limitDirection;
    private final String proposalSource;
    private final boolean confidenceSet;
    private final double confidence;
    private final boolean validityRangeSet;
    private final double validityMinimum;
    private final double validityMaximum;

    /**
     * Creates a fully qualified installed-capacity alternative.
     *
     * @param id stable alternative identifier
     * @param name human-readable alternative name
     * @param provenance engineering provenance for the study definition
     * @param areaName exact process-area name
     * @param equipmentName exact equipment name
     * @param constraintName exact direct capacity-constraint name
     * @param proposedLimit proposed applicable limit
     * @param unit engineering unit of the existing and proposed limit
     * @param limitDirection expected installed-limit direction
     * @param proposalSource source of the proposed limit
     * @param confidence evidence-quality confidence, or NaN when unset
     * @param validityMinimum inclusive proposal validity minimum, or NaN when unset
     * @param validityMaximum inclusive proposal validity maximum, or NaN when unset
     */
    public CapacityAlternative(String id, String name, String provenance, String areaName,
        String equipmentName, String constraintName, double proposedLimit, String unit,
        LimitDirection limitDirection, String proposalSource, double confidence,
        double validityMinimum, double validityMaximum) {
      this.id = requireText(id, "Alternative identifier");
      this.name = requireText(name, "Alternative name");
      this.provenance = requireText(provenance, "Alternative provenance");
      this.areaName = requireText(areaName, "Process area name");
      this.equipmentName = requireText(equipmentName, "Equipment name");
      this.constraintName = requireText(constraintName, "Capacity constraint name");
      if (!isFinite(proposedLimit) || proposedLimit <= 0.0) {
        throw new IllegalArgumentException("Proposed capacity limit must be finite and positive");
      }
      this.proposedLimit = proposedLimit;
      this.unit = requireText(unit, "Capacity limit unit");
      if (limitDirection == null) {
        throw new IllegalArgumentException("Capacity limit direction is required");
      }
      this.limitDirection = limitDirection;
      this.proposalSource = requireText(proposalSource, "Capacity proposal source");
      if (Double.isNaN(confidence)) {
        this.confidenceSet = false;
        this.confidence = Double.NaN;
      } else {
        if (!isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
          throw new IllegalArgumentException("Alternative confidence must be in [0, 1] or NaN");
        }
        this.confidenceSet = true;
        this.confidence = confidence;
      }
      boolean minimumSet = isFinite(validityMinimum);
      boolean maximumSet = isFinite(validityMaximum);
      if (minimumSet != maximumSet) {
        throw new IllegalArgumentException("Both alternative validity bounds must be set together");
      }
      if (minimumSet && validityMinimum > validityMaximum) {
        throw new IllegalArgumentException("Alternative validity minimum must not exceed maximum");
      }
      this.validityRangeSet = minimumSet;
      this.validityMinimum = minimumSet ? validityMinimum : Double.NaN;
      this.validityMaximum = maximumSet ? validityMaximum : Double.NaN;
      if (validityRangeSet
          && (proposedLimit < validityMinimum || proposedLimit > validityMaximum)) {
        throw new IllegalArgumentException("Proposed limit lies outside its declared validity range");
      }
    }

    public String getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    public String getProvenance() {
      return provenance;
    }

    public String getAreaName() {
      return areaName;
    }

    public String getEquipmentName() {
      return equipmentName;
    }

    public String getConstraintName() {
      return constraintName;
    }

    public String getQualifiedConstraintName() {
      return areaName + ProcessAutomation.AREA_SEPARATOR + equipmentName + "/" + constraintName;
    }

    public double getProposedLimit() {
      return proposedLimit;
    }

    public String getUnit() {
      return unit;
    }

    public LimitDirection getLimitDirection() {
      return limitDirection;
    }

    public String getProposalSource() {
      return proposalSource;
    }

    public boolean hasConfidence() {
      return confidenceSet;
    }

    public double getConfidence() {
      return confidenceSet ? confidence : Double.NaN;
    }

    public boolean hasValidityRange() {
      return validityRangeSet;
    }

    public double getValidityMinimum() {
      return validityRangeSet ? validityMinimum : Double.NaN;
    }

    public double getValidityMaximum() {
      return validityRangeSet ? validityMaximum : Double.NaN;
    }
  }

  /** Frozen observable state of one installed capacity constraint. */
  public static final class CapacityState implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double designValue;
    private final double maximumValue;
    private final double minimumValue;
    private final double warningThreshold;
    private final String unit;
    private final CapacityConstraint.ConstraintSeverity severity;
    private final boolean enabled;
    private final String dataSource;
    private final boolean confidenceSet;
    private final double confidence;
    private final boolean validityRangeSet;
    private final double validityMinimum;
    private final double validityMaximum;
    private final double shadowPrice;

    private CapacityState(CapacityConstraint constraint) {
      designValue = constraint.getDesignValue();
      maximumValue = constraint.getMaxValue();
      minimumValue = constraint.getMinValue();
      warningThreshold = constraint.getWarningThreshold();
      unit = safeText(constraint.getUnit());
      severity = constraint.getSeverity();
      enabled = constraint.isEnabled();
      dataSource = safeText(constraint.getDataSource());
      confidenceSet = constraint.hasConfidence();
      confidence = confidenceSet ? constraint.getConfidence() : Double.NaN;
      validityRangeSet = constraint.hasValidityRange();
      validityMinimum = validityRangeSet ? constraint.getValidityMinimum() : Double.NaN;
      validityMaximum = validityRangeSet ? constraint.getValidityMaximum() : Double.NaN;
      shadowPrice = constraint.getShadowPrice();
    }

    public double getDesignValue() {
      return designValue;
    }

    public double getMaximumValue() {
      return maximumValue;
    }

    public double getMinimumValue() {
      return minimumValue;
    }

    public double getApplicableLimit() {
      return minimumValue > 0.0 && designValue == Double.MAX_VALUE ? minimumValue : designValue;
    }

    public double getWarningThreshold() {
      return warningThreshold;
    }

    public String getUnit() {
      return unit;
    }

    public CapacityConstraint.ConstraintSeverity getSeverity() {
      return severity;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public String getDataSource() {
      return dataSource;
    }

    public boolean hasConfidence() {
      return confidenceSet;
    }

    public double getConfidence() {
      return confidenceSet ? confidence : Double.NaN;
    }

    public boolean hasValidityRange() {
      return validityRangeSet;
    }

    public double getValidityMinimum() {
      return validityRangeSet ? validityMinimum : Double.NaN;
    }

    public double getValidityMaximum() {
      return validityRangeSet ? validityMaximum : Double.NaN;
    }

    public double getShadowPrice() {
      return shadowPrice;
    }
  }

  /** Immutable search-policy selection. */
  public static final class SearchSelection implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean selected;
    private final double[] parameters;
    private final List<String> diagnostics;

    private SearchSelection(boolean selected, double[] parameters, List<String> diagnostics) {
      this.selected = selected;
      this.parameters = parameters == null ? new double[0] : Arrays.copyOf(parameters, parameters.length);
      this.diagnostics = immutableStrings(diagnostics);
    }

    public static SearchSelection selected(double[] parameters, String diagnostic) {
      return new SearchSelection(true, parameters, Collections.singletonList(safeText(diagnostic)));
    }

    public static SearchSelection failed(String diagnostic) {
      return new SearchSelection(false, null, Collections.singletonList(safeText(diagnostic)));
    }

    public boolean isSelected() {
      return selected;
    }

    public double[] getParameters() {
      return Arrays.copyOf(parameters, parameters.length);
    }

    public List<String> getDiagnostics() {
      return immutableStrings(diagnostics);
    }
  }

  /**
   * Deterministic derivative-free search over an explicit ordered list of parameter vectors.
   *
   * <p>
   * Only converged feasible candidates with finite selected-objective values are eligible. Ties
   * inside the declared absolute tolerance retain the earliest candidate, making repeated Java and
   * JPype execution deterministic.
   * </p>
   */
  public static final class CandidateListSearch implements ScenarioSearch {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String provenance;
    private final List<double[]> candidates;
    private final int objectiveIndex;
    private final double objectiveTolerance;

    public CandidateListSearch(String id, String name, String provenance, List<double[]> candidates,
        int objectiveIndex, double objectiveTolerance) {
      this.id = requireText(id, "Search identifier");
      this.name = requireText(name, "Search name");
      this.provenance = requireText(provenance, "Search provenance");
      if (candidates == null || candidates.isEmpty()) {
        throw new IllegalArgumentException("Candidate-list search requires at least one candidate");
      }
      List<double[]> copied = new ArrayList<double[]>();
      for (double[] candidate : candidates) {
        if (candidate == null) {
          throw new IllegalArgumentException("Search candidates must not be null");
        }
        copied.add(Arrays.copyOf(candidate, candidate.length));
      }
      this.candidates = Collections.unmodifiableList(copied);
      if (objectiveIndex < 0) {
        throw new IllegalArgumentException("Objective index must be non-negative");
      }
      this.objectiveIndex = objectiveIndex;
      if (!isFinite(objectiveTolerance) || objectiveTolerance < 0.0) {
        throw new IllegalArgumentException("Objective tolerance must be finite and non-negative");
      }
      this.objectiveTolerance = objectiveTolerance;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getProvenance() {
      return provenance;
    }

    public List<double[]> getCandidates() {
      List<double[]> copy = new ArrayList<double[]>();
      for (double[] candidate : candidates) {
        copy.add(Arrays.copyOf(candidate, candidate.length));
      }
      return Collections.unmodifiableList(copy);
    }

    public int getObjectiveIndex() {
      return objectiveIndex;
    }

    public double getObjectiveTolerance() {
      return objectiveTolerance;
    }

    @Override
    public SearchSelection search(ProcessModelSimulationEvaluator evaluator) {
      if (evaluator == null) {
        return SearchSelection.failed("Process-model evaluator was null");
      }
      if (objectiveIndex >= evaluator.getObjectiveCount()) {
        return SearchSelection.failed("Search objective index is not registered");
      }
      ObjectiveDefinition objective = evaluator.getObjectives().get(objectiveIndex);
      double[] selected = null;
      double selectedValue = Double.NaN;
      int candidateIndex = 0;
      for (double[] candidate : candidates) {
        String invalid = validateCandidate(evaluator, candidate, candidateIndex);
        if (invalid != null) {
          return SearchSelection.failed(invalid);
        }
        EvaluationResult result = evaluator.evaluate(candidate);
        double[] rawObjectives = result.getObjectivesRaw();
        if (result.isSimulationConverged() && result.isFeasible()
            && rawObjectives != null && objectiveIndex < rawObjectives.length
            && isFinite(rawObjectives[objectiveIndex])) {
          double value = rawObjectives[objectiveIndex];
          if (selected == null || improves(value, selectedValue, objective.getDirection())) {
            selected = Arrays.copyOf(candidate, candidate.length);
            selectedValue = value;
          }
        }
        candidateIndex++;
      }
      if (selected == null) {
        return SearchSelection.failed("No converged feasible candidate with a finite objective was found");
      }
      return SearchSelection.selected(selected,
          "Selected the first direction-aware best feasible candidate from the ordered candidate list");
    }

    private String validateCandidate(ProcessModelSimulationEvaluator evaluator, double[] candidate,
        int candidateIndex) {
      if (candidate.length != evaluator.getParameterCount()) {
        return "Candidate " + candidateIndex + " length does not match evaluator parameter count";
      }
      List<ParameterDefinition> parameters = evaluator.getParameters();
      for (int index = 0; index < candidate.length; index++) {
        if (!isFinite(candidate[index])) {
          return "Candidate " + candidateIndex + " contains a non-finite parameter";
        }
        if (!parameters.get(index).isWithinBounds(candidate[index])) {
          return "Candidate " + candidateIndex + " lies outside declared parameter bounds";
        }
      }
      return null;
    }

    private boolean improves(double candidate, double incumbent,
        ObjectiveDefinition.Direction direction) {
      if (direction == ObjectiveDefinition.Direction.MAXIMIZE) {
        return candidate > incumbent + objectiveTolerance;
      }
      return candidate < incumbent - objectiveTolerance;
    }
  }

  /** Immutable metric definition. */
  public static final class MetricDefinition {
    private final String id;
    private final String name;
    private final MetricKind kind;
    private final String unit;
    private final String basis;
    private final String provenance;
    private final String effectivePeriod;
    private final boolean confidenceSet;
    private final double confidence;
    private final boolean required;
    private final MetricSampler sampler;

    public MetricDefinition(String id, String name, MetricKind kind, String unit, String basis,
        String provenance, String effectivePeriod, double confidence, boolean required,
        MetricSampler sampler) {
      this.id = requireText(id, "Metric identifier");
      this.name = requireText(name, "Metric name");
      if (kind == null) {
        throw new IllegalArgumentException("Metric kind is required");
      }
      this.kind = kind;
      this.unit = requireText(unit, "Metric unit");
      this.basis = requireText(basis, "Metric basis");
      this.provenance = requireText(provenance, "Metric provenance");
      this.effectivePeriod = requireText(effectivePeriod, "Metric effective period");
      if (Double.isNaN(confidence)) {
        confidenceSet = false;
        this.confidence = Double.NaN;
      } else {
        if (!isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
          throw new IllegalArgumentException("Metric confidence must be in [0, 1] or NaN");
        }
        confidenceSet = true;
        this.confidence = confidence;
      }
      this.required = required;
      if (sampler == null) {
        throw new IllegalArgumentException("Metric sampler is required");
      }
      this.sampler = sampler;
    }

    public String getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    public MetricKind getKind() {
      return kind;
    }

    public String getUnit() {
      return unit;
    }

    public String getBasis() {
      return basis;
    }

    public String getProvenance() {
      return provenance;
    }

    public String getEffectivePeriod() {
      return effectivePeriod;
    }

    public boolean hasConfidence() {
      return confidenceSet;
    }

    public double getConfidence() {
      return confidenceSet ? confidence : Double.NaN;
    }

    public boolean isRequired() {
      return required;
    }
  }

  /** Immutable evidence from one metric sample. */
  public static final class MetricEvidence implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final MetricKind kind;
    private final String unit;
    private final String basis;
    private final String provenance;
    private final String effectivePeriod;
    private final boolean confidenceSet;
    private final double confidence;
    private final boolean required;
    private final MetricStatus status;
    private final double value;
    private final String diagnostic;

    private MetricEvidence(MetricDefinition definition, MetricStatus status, double value,
        String diagnostic) {
      id = definition.id;
      name = definition.name;
      kind = definition.kind;
      unit = definition.unit;
      basis = definition.basis;
      provenance = definition.provenance;
      effectivePeriod = definition.effectivePeriod;
      confidenceSet = definition.confidenceSet;
      confidence = definition.confidence;
      required = definition.required;
      this.status = status;
      this.value = value;
      this.diagnostic = safeText(diagnostic);
    }

    public String getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    public MetricKind getKind() {
      return kind;
    }

    public String getUnit() {
      return unit;
    }

    public String getBasis() {
      return basis;
    }

    public String getProvenance() {
      return provenance;
    }

    public String getEffectivePeriod() {
      return effectivePeriod;
    }

    public boolean hasConfidence() {
      return confidenceSet;
    }

    public double getConfidence() {
      return confidenceSet ? confidence : Double.NaN;
    }

    public boolean isRequired() {
      return required;
    }

    public MetricStatus getStatus() {
      return status;
    }

    public double getValue() {
      return value;
    }

    public String getDiagnostic() {
      return diagnostic;
    }

    public boolean isAvailable() {
      return status == MetricStatus.AVAILABLE;
    }
  }

  /** Immutable comparison of one identically defined metric. */
  public static final class MetricComparison implements Serializable {
    private static final long serialVersionUID = 1L;

    private final MetricEvidence baseline;
    private final MetricEvidence alternative;
    private final boolean calculable;
    private final double delta;

    private MetricComparison(MetricEvidence baseline, MetricEvidence alternative) {
      this.baseline = baseline;
      this.alternative = alternative;
      calculable = baseline != null && alternative != null && baseline.isAvailable()
          && alternative.isAvailable() && baseline.getId().equals(alternative.getId())
          && baseline.getUnit().equals(alternative.getUnit());
      delta = calculable ? alternative.getValue() - baseline.getValue() : Double.NaN;
    }

    public MetricEvidence getBaseline() {
      return baseline;
    }

    public MetricEvidence getAlternative() {
      return alternative;
    }

    public boolean isCalculable() {
      return calculable;
    }

    public double getDelta() {
      return delta;
    }
  }

  /** Frozen objective row for one scenario. */
  public static final class ObjectiveEvidence implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int index;
    private final String name;
    private final ObjectiveDefinition.Direction direction;
    private final String unit;
    private final double weight;
    private final double rawValue;
    private final double minimizerValue;

    private ObjectiveEvidence(int index, ObjectiveDefinition definition, double rawValue,
        double minimizerValue) {
      this.index = index;
      name = safeText(definition.getName());
      direction = definition.getDirection();
      unit = safeText(definition.getUnit());
      weight = definition.getWeight();
      this.rawValue = rawValue;
      this.minimizerValue = minimizerValue;
    }

    public int getIndex() {
      return index;
    }

    public String getName() {
      return name;
    }

    public ObjectiveDefinition.Direction getDirection() {
      return direction;
    }

    public String getUnit() {
      return unit;
    }

    public double getWeight() {
      return weight;
    }

    public double getRawValue() {
      return rawValue;
    }

    public double getMinimizerValue() {
      return minimizerValue;
    }
  }

  /** Frozen constraint row for one scenario. */
  public static final class ConstraintEvidence implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int index;
    private final String name;
    private final ConstraintDefinition.Type type;
    private final String unit;
    private final boolean hard;
    private final double value;
    private final double margin;

    private ConstraintEvidence(int index, ConstraintDefinition definition, double value,
        double margin) {
      this.index = index;
      name = safeText(definition.getName());
      type = definition.getType();
      unit = safeText(definition.getUnit());
      hard = definition.isHard();
      this.value = value;
      this.margin = margin;
    }

    public int getIndex() {
      return index;
    }

    public String getName() {
      return name;
    }

    public ConstraintDefinition.Type getType() {
      return type;
    }

    public String getUnit() {
      return unit;
    }

    public boolean isHard() {
      return hard;
    }

    public double getValue() {
      return value;
    }

    public double getMargin() {
      return margin;
    }
  }

  /** Immutable evidence for one baseline or alternative scenario. */
  public static final class ScenarioEvidence implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String label;
    private final String searchId;
    private final String searchName;
    private final String searchProvenance;
    private final ScenarioStatus status;
    private final double[] selectedParameters;
    private final boolean simulationConverged;
    private final boolean feasible;
    private final int evaluationCount;
    private final List<ObjectiveEvidence> objectives;
    private final List<ConstraintEvidence> constraints;
    private final List<InstalledEquipmentCapacityEvidence> installedCapacityEvidence;
    private final List<ProcessBoundaryConstraintEvidence> boundaryEvidence;
    private final List<MetricEvidence> metrics;
    private final List<String> diagnostics;

    private ScenarioEvidence(String label, ScenarioSearch search, ScenarioStatus status,
        double[] selectedParameters, EvaluationResult result, int evaluationCount,
        List<ObjectiveEvidence> objectives, List<ConstraintEvidence> constraints,
        List<MetricEvidence> metrics, List<String> diagnostics) {
      this.label = label;
      searchId = search.getId();
      searchName = search.getName();
      searchProvenance = search.getProvenance();
      this.status = status;
      this.selectedParameters = selectedParameters == null ? new double[0]
          : Arrays.copyOf(selectedParameters, selectedParameters.length);
      simulationConverged = result != null && result.isSimulationConverged();
      feasible = result != null && result.isFeasible();
      this.evaluationCount = evaluationCount;
      this.objectives = immutableList(objectives);
      this.constraints = immutableList(constraints);
      installedCapacityEvidence = result == null ? Collections.<InstalledEquipmentCapacityEvidence>emptyList()
          : immutableList(result.getInstalledEquipmentCapacityEvidence());
      boundaryEvidence = result == null ? Collections.<ProcessBoundaryConstraintEvidence>emptyList()
          : immutableList(result.getProcessBoundaryConstraintEvidence());
      this.metrics = immutableList(metrics);
      this.diagnostics = immutableStrings(diagnostics);
    }

    public String getLabel() {
      return label;
    }

    public String getSearchId() {
      return searchId;
    }

    public String getSearchName() {
      return searchName;
    }

    public String getSearchProvenance() {
      return searchProvenance;
    }

    public ScenarioStatus getStatus() {
      return status;
    }

    public double[] getSelectedParameters() {
      return Arrays.copyOf(selectedParameters, selectedParameters.length);
    }

    public boolean isSimulationConverged() {
      return simulationConverged;
    }

    public boolean isFeasible() {
      return feasible;
    }

    public boolean isQualified() {
      return status == ScenarioStatus.QUALIFIED && simulationConverged && feasible;
    }

    public int getEvaluationCount() {
      return evaluationCount;
    }

    public List<ObjectiveEvidence> getObjectives() {
      return immutableList(objectives);
    }

    public List<ConstraintEvidence> getConstraints() {
      return immutableList(constraints);
    }

    public List<InstalledEquipmentCapacityEvidence> getInstalledCapacityEvidence() {
      return immutableList(installedCapacityEvidence);
    }

    public List<ProcessBoundaryConstraintEvidence> getBoundaryEvidence() {
      return immutableList(boundaryEvidence);
    }

    public List<MetricEvidence> getMetrics() {
      return immutableList(metrics);
    }

    public List<String> getDiagnostics() {
      return immutableStrings(diagnostics);
    }
  }

  /** Immutable paired study result. */
  public static final class StudyResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String provenance;
    private final CapacityAlternative alternativeDefinition;
    private final CapacityState originalCapacityState;
    private final CapacityState appliedCapacityState;
    private final StudyOutcome outcome;
    private final ScenarioEvidence baseline;
    private final ScenarioEvidence alternative;
    private final boolean capacityRestored;
    private final boolean processStateRestored;
    private final boolean recoverySimulationConverged;
    private final List<MetricComparison> metricComparisons;
    private final boolean objectiveDeltaCalculable;
    private final double objectiveDelta;
    private final List<String> diagnostics;

    private StudyResult(String id, String name, String provenance,
        CapacityAlternative alternativeDefinition, CapacityState originalCapacityState,
        CapacityState appliedCapacityState, StudyOutcome outcome, ScenarioEvidence baseline,
        ScenarioEvidence alternative, boolean capacityRestored, boolean processStateRestored,
        boolean recoverySimulationConverged, List<MetricComparison> metricComparisons,
        boolean objectiveDeltaCalculable, double objectiveDelta, List<String> diagnostics) {
      this.id = id;
      this.name = name;
      this.provenance = provenance;
      this.alternativeDefinition = alternativeDefinition;
      this.originalCapacityState = originalCapacityState;
      this.appliedCapacityState = appliedCapacityState;
      this.outcome = outcome;
      this.baseline = baseline;
      this.alternative = alternative;
      this.capacityRestored = capacityRestored;
      this.processStateRestored = processStateRestored;
      this.recoverySimulationConverged = recoverySimulationConverged;
      this.metricComparisons = immutableList(metricComparisons);
      this.objectiveDeltaCalculable = objectiveDeltaCalculable;
      this.objectiveDelta = objectiveDelta;
      this.diagnostics = immutableStrings(diagnostics);
    }

    public String getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    public String getProvenance() {
      return provenance;
    }

    public CapacityAlternative getAlternativeDefinition() {
      return alternativeDefinition;
    }

    public CapacityState getOriginalCapacityState() {
      return originalCapacityState;
    }

    public CapacityState getAppliedCapacityState() {
      return appliedCapacityState;
    }

    public StudyOutcome getOutcome() {
      return outcome;
    }

    public ScenarioEvidence getBaseline() {
      return baseline;
    }

    public ScenarioEvidence getAlternative() {
      return alternative;
    }

    public boolean isCapacityRestored() {
      return capacityRestored;
    }

    public boolean isProcessStateRestored() {
      return processStateRestored;
    }

    public boolean isRecoverySimulationConverged() {
      return recoverySimulationConverged;
    }

    public List<MetricComparison> getMetricComparisons() {
      return immutableList(metricComparisons);
    }

    public boolean isObjectiveDeltaCalculable() {
      return objectiveDeltaCalculable;
    }

    public double getObjectiveDelta() {
      return objectiveDelta;
    }

    public List<String> getDiagnostics() {
      return immutableStrings(diagnostics);
    }
  }

  /** Internal resolved direct installed constraint. */
  private static final class CapacityTarget {
    private final CapacityConstraint constraint;

    private CapacityTarget(CapacityConstraint constraint) {
      this.constraint = constraint;
    }
  }

  private final String id;
  private final String name;
  private final String provenance;
  private final ProcessModelSimulationEvaluator evaluator;
  private final CapacityAlternative alternative;
  private final ScenarioSearch search;
  private final int objectiveIndex;
  private final List<MetricDefinition> metrics = new ArrayList<MetricDefinition>();

  /**
   * Creates a paired debottleneck study.
   *
   * @param id stable study identifier
   * @param name human-readable study name
   * @param provenance engineering provenance for the study configuration
   * @param evaluator configured process-model evaluator
   * @param alternative installed-capacity alternative
   * @param search common search policy used for baseline and alternative
   * @param objectiveIndex objective used for the paired delta
   */
  public ProcessModelDebottleneckStudy(String id, String name, String provenance,
      ProcessModelSimulationEvaluator evaluator, CapacityAlternative alternative,
      ScenarioSearch search, int objectiveIndex) {
    this.id = requireText(id, "Study identifier");
    this.name = requireText(name, "Study name");
    this.provenance = requireText(provenance, "Study provenance");
    if (evaluator == null || alternative == null || search == null) {
      throw new IllegalArgumentException("Evaluator, capacity alternative, and search are required");
    }
    if (objectiveIndex < 0 || objectiveIndex >= evaluator.getObjectiveCount()) {
      throw new IllegalArgumentException("Study objective index is not registered");
    }
    this.evaluator = evaluator;
    this.alternative = alternative;
    this.search = search;
    this.objectiveIndex = objectiveIndex;
    if (search instanceof CandidateListSearch
        && ((CandidateListSearch) search).getObjectiveIndex() != objectiveIndex) {
      throw new IllegalArgumentException(
          "Candidate-list search and study must use the same objective index");
    }
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getProvenance() {
    return provenance;
  }

  public ProcessModelSimulationEvaluator getEvaluator() {
    return evaluator;
  }

  public CapacityAlternative getAlternative() {
    return alternative;
  }

  public ScenarioSearch getSearch() {
    return search;
  }

  public int getObjectiveIndex() {
    return objectiveIndex;
  }

  /** Adds one physical or screening metric sampled at both selected operating points. */
  public ProcessModelDebottleneckStudy addMetric(MetricDefinition metric) {
    if (metric == null) {
      throw new IllegalArgumentException("Metric definition must not be null");
    }
    for (MetricDefinition existing : metrics) {
      if (existing.id.equals(metric.id)) {
        throw new IllegalArgumentException("Metric identifier is already registered: " + metric.id);
      }
    }
    metrics.add(metric);
    return this;
  }

  /** @return fresh immutable metric definitions in registration order */
  public List<MetricDefinition> getMetrics() {
    return Collections.unmodifiableList(new ArrayList<MetricDefinition>(metrics));
  }

  /** Executes the paired study and always attempts exact installed/process-state recovery. */
  public StudyResult evaluate() {
    ProcessModel model = evaluator.getProcessModel();
    double[] recoveryParameters = evaluator.getLastParameters();
    if (recoveryParameters == null) {
      recoveryParameters = evaluator.getInitialValues();
    }
    List<String> diagnostics = new ArrayList<String>();
    diagnostics.add("Results are paired sampled evidence, not causal production loss, global "
        + "optimality, a shadow price, certified emissions, design approval, or investment approval");

    CapacityTarget target;
    try {
      target = resolveTarget(model);
    } catch (RuntimeException exception) {
      diagnostics.add(exception.getMessage());
      return result(StudyOutcome.ALTERNATIVE_NOT_APPLICABLE, null, null, null, null,
          false, false, false, diagnostics);
    }

    CapacityState originalState = new CapacityState(target.constraint);
    CapacityState appliedState = null;
    ScenarioEvidence baseline = null;
    ScenarioEvidence alternativeScenario = null;
    boolean capacityRestored = false;
    boolean processStateRestored = false;
    boolean recoveryConverged = false;
    boolean alternativeApplied = false;
    StudyOutcome provisionalOutcome = StudyOutcome.BASELINE_FAILED;

    try {
      baseline = runScenario("baseline");
      if (!baseline.isQualified()) {
        diagnostics.add("Baseline search or verification did not produce a qualified point");
        provisionalOutcome = StudyOutcome.BASELINE_FAILED;
      } else {
        applyAlternative(target.constraint);
        alternativeApplied = true;
        appliedState = new CapacityState(target.constraint);
        alternativeScenario = runScenario("alternative");
        if (!alternativeScenario.isQualified()) {
          diagnostics.add("Alternative search or verification did not produce a qualified point");
          provisionalOutcome = StudyOutcome.ALTERNATIVE_FAILED;
        } else if (hasUnavailableRequiredMetric(baseline)
            || hasUnavailableRequiredMetric(alternativeScenario)) {
          diagnostics.add("At least one required scenario metric was unavailable");
          provisionalOutcome = StudyOutcome.REQUIRED_METRIC_UNAVAILABLE;
        } else {
          provisionalOutcome = StudyOutcome.COMPLETED;
        }
      }
    } catch (RuntimeException exception) {
      diagnostics.add("Study execution failed: " + safeText(exception.getMessage()));
      provisionalOutcome = baseline == null || !baseline.isQualified()
          ? StudyOutcome.BASELINE_FAILED : StudyOutcome.ALTERNATIVE_FAILED;
    } finally {
      try {
        restoreConstraint(target.constraint, originalState);
        capacityRestored = statesEqual(originalState, new CapacityState(target.constraint));
      } catch (RuntimeException exception) {
        diagnostics.add("Installed-capacity restoration failed: " + safeText(exception.getMessage()));
        capacityRestored = false;
      }
      try {
        EvaluationResult recovery = evaluator.evaluate(recoveryParameters);
        recoveryConverged = recovery.isSimulationConverged();
        processStateRestored = recoveryConverged
            && arraysEqual(recoveryParameters, recovery.getParameters(), 0.0);
      } catch (RuntimeException exception) {
        diagnostics.add("Pre-study process-state recovery failed: " + safeText(exception.getMessage()));
      }
    }

    if (!alternativeApplied && appliedState != null) {
      diagnostics.add("Internal diagnostic: applied capacity state was retained without an applied alternative");
    }
    StudyOutcome outcome = capacityRestored && processStateRestored && recoveryConverged
        ? provisionalOutcome : StudyOutcome.RESTORATION_FAILED;
    if (capacityRestored) {
      diagnostics.add("The complete installed-capacity state was restored and verified");
    }
    if (processStateRestored && recoveryConverged) {
      diagnostics.add("The pre-study parameter vector was restored and the process model reconverged");
    }
    return result(outcome, originalState, appliedState, baseline, alternativeScenario,
        capacityRestored, processStateRestored, recoveryConverged, diagnostics);
  }

  private CapacityTarget resolveTarget(ProcessModel model) {
    ProcessSystem area = model.get(alternative.getAreaName());
    if (area == null) {
      throw new IllegalArgumentException("Alternative process area was not found: "
          + alternative.getAreaName());
    }
    ProcessEquipmentInterface equipment = area.getUnit(alternative.getEquipmentName());
    if (equipment == null) {
      throw new IllegalArgumentException("Alternative equipment was not found: "
          + alternative.getQualifiedConstraintName());
    }
    Map<String, CapacityConstraint> direct = equipment.getCapacityConstraints();
    CapacityConstraint constraint = direct == null ? null : direct.get(alternative.getConstraintName());
    if (constraint == null) {
      throw new IllegalArgumentException("A direct installed capacity constraint was not found: "
          + alternative.getQualifiedConstraintName());
    }
    if (!constraint.isEnabled()) {
      throw new IllegalArgumentException("The installed capacity constraint is disabled");
    }
    if (!alternative.getUnit().equals(constraint.getUnit())) {
      throw new IllegalArgumentException("Alternative unit does not match the installed constraint unit");
    }
    LimitDirection actual = constraint.isMinimumConstraint()
        ? LimitDirection.MINIMUM : LimitDirection.MAXIMUM;
    if (actual != alternative.getLimitDirection()) {
      throw new IllegalArgumentException("Alternative direction does not match the installed constraint");
    }
    if (!isFinite(constraint.getDisplayDesignValue())
        || constraint.getDisplayDesignValue() == Double.MAX_VALUE
        || constraint.getDisplayDesignValue() <= 0.0) {
      throw new IllegalArgumentException("Installed applicable limit must be finite and positive");
    }
    return new CapacityTarget(constraint);
  }

  private void applyAlternative(CapacityConstraint constraint) {
    if (alternative.getLimitDirection() == LimitDirection.MINIMUM) {
      constraint.setMinValue(alternative.getProposedLimit());
    } else {
      constraint.setDesignValue(alternative.getProposedLimit());
    }
    constraint.setDataSource(alternative.getProposalSource());
    if (alternative.hasConfidence()) {
      constraint.setConfidence(alternative.getConfidence());
    } else {
      constraint.clearConfidence();
    }
    if (alternative.hasValidityRange()) {
      constraint.setValidityRange(alternative.getValidityMinimum(),
          alternative.getValidityMaximum());
    } else {
      constraint.clearValidityRange();
    }
    if (Double.doubleToLongBits(constraint.getDisplayDesignValue())
        != Double.doubleToLongBits(alternative.getProposedLimit())) {
      throw new IllegalStateException("Proposed installed limit failed exact read-back verification");
    }
  }

  private ScenarioEvidence runScenario(String label) {
    int before = evaluator.getEvaluationCount();
    List<String> diagnostics = new ArrayList<String>();
    SearchSelection selection;
    try {
      selection = search.search(evaluator);
    } catch (RuntimeException exception) {
      diagnostics.add("Search failed: " + safeText(exception.getMessage()));
      return new ScenarioEvidence(label, search, ScenarioStatus.SEARCH_FAILED, null, null,
          evaluator.getEvaluationCount() - before, Collections.<ObjectiveEvidence>emptyList(),
          Collections.<ConstraintEvidence>emptyList(), notSampledMetrics(), diagnostics);
    }
    diagnostics.addAll(selection.getDiagnostics());
    if (!selection.isSelected()) {
      return new ScenarioEvidence(label, search, ScenarioStatus.SEARCH_FAILED, null, null,
          evaluator.getEvaluationCount() - before, Collections.<ObjectiveEvidence>emptyList(),
          Collections.<ConstraintEvidence>emptyList(), notSampledMetrics(), diagnostics);
    }
    double[] parameters = selection.getParameters();
    String invalid = validateSelectedParameters(parameters);
    if (invalid != null) {
      diagnostics.add(invalid);
      return new ScenarioEvidence(label, search, ScenarioStatus.VERIFICATION_FAILED, parameters,
          null, evaluator.getEvaluationCount() - before,
          Collections.<ObjectiveEvidence>emptyList(), Collections.<ConstraintEvidence>emptyList(),
          notSampledMetrics(), diagnostics);
    }
    EvaluationResult result = evaluator.evaluate(parameters);
    List<ObjectiveEvidence> objectives = snapshotObjectives(result);
    List<ConstraintEvidence> constraints = snapshotConstraints(result);
    ScenarioStatus status = result.isSimulationConverged() && result.isFeasible()
        ? ScenarioStatus.QUALIFIED : ScenarioStatus.VERIFICATION_FAILED;
    if (status != ScenarioStatus.QUALIFIED) {
      diagnostics.add("Selected point did not pass independent convergence and feasibility verification");
    }
    List<MetricEvidence> metricEvidence = status == ScenarioStatus.QUALIFIED
        ? sampleMetrics(evaluator.getProcessModel()) : notSampledMetrics();
    return new ScenarioEvidence(label, search, status, parameters, result,
        evaluator.getEvaluationCount() - before, objectives, constraints, metricEvidence, diagnostics);
  }

  private String validateSelectedParameters(double[] parameters) {
    if (parameters == null || parameters.length != evaluator.getParameterCount()) {
      return "Selected parameter vector does not match evaluator parameter count";
    }
    List<ParameterDefinition> definitions = evaluator.getParameters();
    for (int index = 0; index < parameters.length; index++) {
      if (!isFinite(parameters[index]) || !definitions.get(index).isWithinBounds(parameters[index])) {
        return "Selected parameter vector contains a non-finite or out-of-bounds value";
      }
    }
    return null;
  }

  private List<ObjectiveEvidence> snapshotObjectives(EvaluationResult result) {
    List<ObjectiveEvidence> evidence = new ArrayList<ObjectiveEvidence>();
    double[] raw = result.getObjectivesRaw();
    double[] adjusted = result.getObjectives();
    for (int index = 0; index < evaluator.getObjectives().size(); index++) {
      evidence.add(new ObjectiveEvidence(index, evaluator.getObjectives().get(index), raw[index],
          adjusted[index]));
    }
    return evidence;
  }

  private List<ConstraintEvidence> snapshotConstraints(EvaluationResult result) {
    List<ConstraintEvidence> evidence = new ArrayList<ConstraintEvidence>();
    double[] values = result.getConstraintValues();
    double[] margins = result.getConstraintMargins();
    for (int index = 0; index < evaluator.getConstraints().size(); index++) {
      evidence.add(new ConstraintEvidence(index, evaluator.getConstraints().get(index), values[index],
          margins[index]));
    }
    return evidence;
  }

  private List<MetricEvidence> sampleMetrics(ProcessModel model) {
    List<MetricEvidence> evidence = new ArrayList<MetricEvidence>();
    for (MetricDefinition metric : metrics) {
      try {
        double value = metric.sampler.sample(model);
        if (isFinite(value)) {
          evidence.add(new MetricEvidence(metric, MetricStatus.AVAILABLE, value,
              "Sampled once at the verified completed operating point"));
        } else {
          evidence.add(new MetricEvidence(metric, MetricStatus.NON_FINITE, Double.NaN,
              "Metric sampler returned a non-finite value"));
        }
      } catch (RuntimeException exception) {
        evidence.add(new MetricEvidence(metric, MetricStatus.SAMPLING_FAILED, Double.NaN,
            safeText(exception.getMessage())));
      }
    }
    return evidence;
  }

  private List<MetricEvidence> notSampledMetrics() {
    List<MetricEvidence> evidence = new ArrayList<MetricEvidence>();
    for (MetricDefinition metric : metrics) {
      evidence.add(new MetricEvidence(metric, MetricStatus.NOT_SAMPLED, Double.NaN,
          "Scenario was not qualified for metric sampling"));
    }
    return evidence;
  }

  private boolean hasUnavailableRequiredMetric(ScenarioEvidence scenario) {
    for (MetricEvidence metric : scenario.getMetrics()) {
      if (metric.isRequired() && !metric.isAvailable()) {
        return true;
      }
    }
    return false;
  }

  private StudyResult result(StudyOutcome outcome, CapacityState originalState,
      CapacityState appliedState, ScenarioEvidence baseline, ScenarioEvidence alternativeScenario,
      boolean capacityRestored, boolean processStateRestored, boolean recoveryConverged,
      List<String> diagnostics) {
    List<MetricComparison> comparisons = compareMetrics(baseline, alternativeScenario);
    boolean deltaCalculable = baseline != null && alternativeScenario != null
        && baseline.getObjectives().size() > objectiveIndex
        && alternativeScenario.getObjectives().size() > objectiveIndex
        && isFinite(baseline.getObjectives().get(objectiveIndex).getRawValue())
        && isFinite(alternativeScenario.getObjectives().get(objectiveIndex).getRawValue());
    double delta = deltaCalculable
        ? alternativeScenario.getObjectives().get(objectiveIndex).getRawValue()
            - baseline.getObjectives().get(objectiveIndex).getRawValue()
        : Double.NaN;
    return new StudyResult(id, name, provenance, alternative, originalState, appliedState, outcome,
        baseline, alternativeScenario, capacityRestored, processStateRestored,
        recoveryConverged, comparisons, deltaCalculable, delta, diagnostics);
  }

  private List<MetricComparison> compareMetrics(ScenarioEvidence baseline,
      ScenarioEvidence alternativeScenario) {
    if (baseline == null || alternativeScenario == null) {
      return Collections.emptyList();
    }
    List<MetricEvidence> baselineMetrics = baseline.getMetrics();
    List<MetricEvidence> alternativeMetrics = alternativeScenario.getMetrics();
    List<MetricComparison> comparisons = new ArrayList<MetricComparison>();
    int count = Math.min(baselineMetrics.size(), alternativeMetrics.size());
    for (int index = 0; index < count; index++) {
      comparisons.add(new MetricComparison(baselineMetrics.get(index),
          alternativeMetrics.get(index)));
    }
    return comparisons;
  }

  private static void restoreConstraint(CapacityConstraint constraint, CapacityState state) {
    constraint.setDesignValue(state.getDesignValue());
    constraint.setMaxValue(state.getMaximumValue());
    constraint.setMinValue(state.getMinimumValue());
    constraint.setWarningThreshold(state.getWarningThreshold());
    constraint.setSeverity(state.getSeverity());
    constraint.setEnabled(state.isEnabled());
    constraint.setDataSource(state.getDataSource());
    if (state.hasConfidence()) {
      constraint.setConfidence(state.getConfidence());
    } else {
      constraint.clearConfidence();
    }
    if (state.hasValidityRange()) {
      constraint.setValidityRange(state.getValidityMinimum(), state.getValidityMaximum());
    } else {
      constraint.clearValidityRange();
    }
    constraint.setShadowPrice(state.getShadowPrice());
  }

  private static boolean statesEqual(CapacityState first, CapacityState second) {
    return bitsEqual(first.designValue, second.designValue)
        && bitsEqual(first.maximumValue, second.maximumValue)
        && bitsEqual(first.minimumValue, second.minimumValue)
        && bitsEqual(first.warningThreshold, second.warningThreshold)
        && first.unit.equals(second.unit) && first.severity == second.severity
        && first.enabled == second.enabled && first.dataSource.equals(second.dataSource)
        && first.confidenceSet == second.confidenceSet
        && (!first.confidenceSet || bitsEqual(first.confidence, second.confidence))
        && first.validityRangeSet == second.validityRangeSet
        && (!first.validityRangeSet
            || bitsEqual(first.validityMinimum, second.validityMinimum)
                && bitsEqual(first.validityMaximum, second.validityMaximum))
        && bitsEqual(first.shadowPrice, second.shadowPrice);
  }

  private static boolean arraysEqual(double[] first, double[] second, double tolerance) {
    if (first == null || second == null || first.length != second.length) {
      return false;
    }
    for (int index = 0; index < first.length; index++) {
      if (Math.abs(first[index] - second[index]) > tolerance) {
        return false;
      }
    }
    return true;
  }

  private static boolean bitsEqual(double first, double second) {
    return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
  }

  private static boolean isFinite(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }

  private static String requireText(String value, String label) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value.trim();
  }

  private static String safeText(String value) {
    return value == null ? "" : value;
  }

  private static List<String> immutableStrings(List<String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<String>(values));
  }

  private static <T> List<T> immutableList(List<T> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<T>(values));
  }
}
