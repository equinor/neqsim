package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.EnergyAllocation;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyNetworkReport;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.util.unit.PowerUnit;

/**
 * Immutable participant-complete evidence for one maximum shared-resource budget.
 *
 * <p>
 * This evidence is captured only from caller-owned completed state. It retains no process, bus, equipment, callback, or
 * supplier reference. Missing, unexpected, stale, non-finite, metadata-inconsistent, or unconverged observations fail
 * closed. Aggregation uses only the explicit participant conversions registered in the matching
 * {@link PlantConstraintDefinition}; unlike units or bases are never inferred.
 * </p>
 */
public final class PlantSharedResourceEvidence implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final String SCHEMA_VERSION = "1.0";
  private static final double TOTAL_TOLERANCE = 1.0e-10;

  /** Overall evidence status. */
  public enum Status {
    /** Every enabled participant and aggregate check is complete. */
    AVAILABLE,
    /** The registered shared-resource constraint is disabled. */
    DISABLED,
    /** One or more fail-closed diagnostics prevent use as feasible evidence. */
    INCOMPLETE
  }

  private final PlantConstraintDefinition definition;
  private final String calculationId;
  private final double applicableLimit;
  private final boolean convergenceComplete;
  private final List<PlantSharedResourceParticipantSample> participants;
  private final List<String> diagnostics;
  private final Status status;
  private final double aggregateValue;
  private final double normalizedUtilization;
  private final double normalizedResidual;
  private final double physicalMargin;
  private final double requiredRelief;
  private final double sourceTotal;
  private final String sourceTotalUnit;
  private final String sourceTotalBasis;
  private final String sourceTotalProvenance;

  private PlantSharedResourceEvidence(Builder builder) {
    definition = validateDefinition(builder.definition);
    calculationId = PlantConstraintScope.requireText(builder.calculationId, "Calculation id");
    applicableLimit = builder.applicableLimit;
    convergenceComplete = builder.convergenceComplete;
    sourceTotal = builder.sourceTotalSet ? builder.sourceTotal : Double.NaN;
    sourceTotalUnit = builder.sourceTotalUnit;
    sourceTotalBasis = builder.sourceTotalBasis;
    sourceTotalProvenance = builder.sourceTotalProvenance;

    List<String> findings = new ArrayList<String>(builder.diagnostics);
    if (!convergenceComplete) {
      findings.add("INCOMPLETE_CONVERGENCE");
    }
    if (definition.getRegistrationStatus() == PlantConstraintDefinition.RegistrationStatus.INCOMPLETE_BASIS
        || definition
            .getRegistrationStatus() == PlantConstraintDefinition.RegistrationStatus.DISABLED_INCOMPLETE_BASIS) {
      findings.add("INCOMPLETE_REGISTRATION_BASIS");
    }
    if (!Double.isFinite(applicableLimit) || applicableLimit <= 0.0) {
      findings.add("INVALID_APPLICABLE_LIMIT");
    }

    Map<String, PlantSharedResourceParticipantSample> supplied = new TreeMap<String, PlantSharedResourceParticipantSample>(
        builder.samples);
    List<PlantSharedResourceParticipantSample> captured = new ArrayList<PlantSharedResourceParticipantSample>();
    double total = 0.0;
    boolean totalAvailable = true;
    for (PlantConstraintParticipant expected : definition.getParticipants()) {
      PlantSharedResourceParticipantSample sample = supplied.remove(expected.getSourceId());
      if (sample == null) {
        sample = missingSample(expected);
      }
      captured.add(sample);
      String problem = validateParticipant(expected, sample);
      if (!problem.isEmpty()) {
        findings.add(expected.getSourceId() + "=" + problem);
        totalAvailable = false;
      } else {
        try {
          total += expected.convertToTarget(sample.getValue());
        } catch (RuntimeException ex) {
          findings.add(expected.getSourceId() + "=CONVERSION_FAILED:" + safeMessage(ex));
          totalAvailable = false;
        }
      }
    }
    for (PlantSharedResourceParticipantSample unexpected : supplied.values()) {
      captured.add(unexpected);
      findings.add(unexpected.getSourceId() + "=UNEXPECTED_PARTICIPANT");
      totalAvailable = false;
    }
    Collections.sort(captured);
    participants = Collections.unmodifiableList(captured);

    if (totalAvailable && builder.sourceTotalSet) {
      if (!Double.isFinite(builder.sourceTotal) || !definition.getUnit().equals(builder.sourceTotalUnit)
          || !definition.getBasis().equals(builder.sourceTotalBasis) || builder.sourceTotalProvenance.isEmpty()) {
        findings.add("SOURCE_TOTAL_METADATA_MISMATCH");
        totalAvailable = false;
      } else if (!approximatelyEqual(total, builder.sourceTotal)) {
        findings.add("SOURCE_TOTAL_MISMATCH expected=" + builder.sourceTotal + " aggregated=" + total);
        totalAvailable = false;
      }
    }
    if (!builder.sourceTotalSet) {
      findings.add("SOURCE_TOTAL_NOT_CROSS_CHECKED");
      totalAvailable = false;
    }

    diagnostics = Collections.unmodifiableList(new ArrayList<String>(findings));
    if (!definition.isEnabled()) {
      status = Status.DISABLED;
    } else if (findings.isEmpty() && totalAvailable) {
      status = Status.AVAILABLE;
    } else {
      status = Status.INCOMPLETE;
    }

    if (status == Status.AVAILABLE) {
      aggregateValue = total;
      normalizedUtilization = total / applicableLimit;
      normalizedResidual = normalizedUtilization - 1.0;
      physicalMargin = applicableLimit - total;
      requiredRelief = Math.max(0.0, total - applicableLimit);
    } else {
      aggregateValue = Double.NaN;
      normalizedUtilization = Double.NaN;
      normalizedResidual = Double.NaN;
      physicalMargin = Double.NaN;
      requiredRelief = Double.NaN;
    }
  }

  /** Starts an evidence builder for a registered shared budget and one exact calculation. */
  public static Builder builder(PlantConstraintDefinition definition, String calculationId, double applicableLimit) {
    return new Builder(definition, calculationId, applicableLimit);
  }

  /**
   * Captures exact top-level compressor and pump shaft-power participants from a solved process system.
   *
   * @param definition shared-budget registration whose participants are equipment names
   * @param calculationId exact completed calculation identity
   * @param applicableLimit finite positive shared limit in the definition unit and basis
   * @param process solved isolated process system
   * @param convergenceComplete caller's full-candidate convergence decision
   * @param provenance runtime evidence provenance
   * @return immutable callback-free evidence
   */
  public static PlantSharedResourceEvidence fromProcessSystemShaftPower(PlantConstraintDefinition definition,
      String calculationId, double applicableLimit, ProcessSystem process, boolean convergenceComplete,
      String provenance) {
    return fromProcessSystemShaftPower(definition, calculationId, applicableLimit, process, convergenceComplete,
        provenance, Collections.<String>emptyList());
  }

  /**
   * Captures shaft-power evidence with explicit out-of-service equipment identities.
   *
   * <p>
   * An out-of-service declaration is accepted only when the solved equipment power is numerically zero. It never
   * converts a missing or non-zero observation to zero.
   * </p>
   *
   * @param definition shared-budget registration whose participants are equipment names
   * @param calculationId exact completed calculation identity
   * @param applicableLimit finite positive shared limit
   * @param process solved isolated process system
   * @param convergenceComplete caller's full-candidate convergence decision
   * @param provenance runtime evidence provenance
   * @param outOfServiceParticipantIds equipment names explicitly unavailable in this line-up
   * @return immutable callback-free evidence
   */
  public static PlantSharedResourceEvidence fromProcessSystemShaftPower(PlantConstraintDefinition definition,
      String calculationId, double applicableLimit, ProcessSystem process, boolean convergenceComplete,
      String provenance, Collection<String> outOfServiceParticipantIds) {
    if (process == null) {
      throw new IllegalArgumentException("Process system is required");
    }
    Builder result = builder(definition, calculationId, applicableLimit);
    String safeProvenance = PlantConstraintScope.safeText(provenance);
    Map<String, PlantConstraintParticipant> expected = participantMap(definition);
    Collection<String> unavailable = outOfServiceParticipantIds == null ? Collections.<String>emptyList()
        : outOfServiceParticipantIds;
    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      double power;
      if (equipment instanceof Compressor) {
        power = ((Compressor) equipment).getPower(sourceUnit(expected.get(equipment.getName()), definition));
      } else if (equipment instanceof Pump) {
        power = ((Pump) equipment).getPower(sourceUnit(expected.get(equipment.getName()), definition));
      } else {
        continue;
      }
      PlantConstraintParticipant participant = expected.get(equipment.getName());
      String unit = sourceUnit(participant, definition);
      String basis = sourceBasis(participant, definition);
      PlantSharedResourceParticipantSample.Status sampleStatus = sampleStatus(power);
      String diagnostic = "";
      if (unavailable.contains(equipment.getName())) {
        if (Double.isFinite(power) && power == 0.0) {
          sampleStatus = PlantSharedResourceParticipantSample.Status.OUT_OF_SERVICE;
        } else {
          sampleStatus = PlantSharedResourceParticipantSample.Status.METADATA_MISMATCH;
          diagnostic = "Out-of-service equipment retained non-zero or unavailable shaft power";
        }
      }
      result.participant(
          PlantSharedResourceParticipantSample.builder(equipment.getName(), calculationId).status(sampleStatus)
              .value(power).unit(unit).basis(basis).provenance(safeProvenance).diagnostic(diagnostic).build());
    }
    boolean solved = convergenceComplete && process.solved() && process.getRunStatus().isSuccess();
    result.convergenceComplete(solved);
    try {
      result.sourceTotal(process.getPower(definition.getUnit()), definition.getUnit(), definition.getBasis(),
          safeProvenance);
    } catch (RuntimeException ex) {
      result.diagnostic("PROCESS_TOTAL_FAILED:" + safeMessage(ex));
    }
    return result.build();
  }

  /**
   * Captures area-complete compressor and pump shaft-power evidence from a solved process model.
   *
   * @param definition shared-budget registration whose participants are process-area names
   * @param calculationId exact completed calculation identity
   * @param applicableLimit finite positive shared limit
   * @param model solved isolated process model
   * @param convergenceComplete caller's full-candidate convergence decision
   * @param provenance runtime evidence provenance
   * @return immutable callback-free evidence
   */
  public static PlantSharedResourceEvidence fromProcessModelShaftPower(PlantConstraintDefinition definition,
      String calculationId, double applicableLimit, ProcessModel model, boolean convergenceComplete,
      String provenance) {
    if (model == null) {
      throw new IllegalArgumentException("Process model is required");
    }
    Builder result = builder(definition, calculationId, applicableLimit);
    String safeProvenance = PlantConstraintScope.safeText(provenance);
    Map<String, PlantConstraintParticipant> expected = participantMap(definition);
    for (String areaName : model.getProcessSystemNames()) {
      PlantConstraintParticipant participant = expected.get(areaName);
      String unit = sourceUnit(participant, definition);
      String basis = sourceBasis(participant, definition);
      double power;
      try {
        power = model.get(areaName).getPower(unit);
      } catch (RuntimeException ex) {
        result.participant(PlantSharedResourceParticipantSample.builder(areaName, calculationId)
            .status(PlantSharedResourceParticipantSample.Status.EXCEPTION).unit(unit).basis(basis)
            .provenance(safeProvenance).diagnostic(safeMessage(ex)).build());
        continue;
      }
      result.participant(PlantSharedResourceParticipantSample.builder(areaName, calculationId)
          .status(sampleStatus(power)).value(power).unit(unit).basis(basis).provenance(safeProvenance).build());
    }
    result.convergenceComplete(convergenceComplete && model.isModelConverged() && model.isFinished());
    try {
      result.sourceTotal(model.getPower(definition.getUnit()), definition.getUnit(), definition.getBasis(),
          safeProvenance);
    } catch (RuntimeException ex) {
      result.diagnostic("MODEL_TOTAL_FAILED:" + safeMessage(ex));
    }
    return result.build();
  }

  /**
   * Captures requested electrical demand from a current solved energy-bus report.
   *
   * <p>
   * The aggregate is requested demand, not served demand, so unmet load remains visible. Every input port must be
   * registered as a constraint participant. External bus demand and bidirectional demand fail closed because neither
   * has an unambiguous participant contract.
   * </p>
   *
   * @param definition electrical shared-budget registration using stable energy-port participant IDs
   * @param calculationId exact completed calculation identity
   * @param applicableLimit finite positive electrical limit
   * @param bus solved isolated electrical energy bus
   * @param provenance runtime evidence provenance
   * @return immutable callback-free evidence
   */
  public static PlantSharedResourceEvidence fromSolvedEnergyBusRequestedDemand(PlantConstraintDefinition definition,
      String calculationId, double applicableLimit, EnergyBus bus, String provenance) {
    if (bus == null) {
      throw new IllegalArgumentException("Energy bus is required");
    }
    Builder result = builder(definition, calculationId, applicableLimit);
    String safeProvenance = PlantConstraintScope.safeText(provenance);
    if (bus.getEnergyType() != EnergyType.ELECTRICAL) {
      result.diagnostic("ENERGY_BUS_IS_NOT_ELECTRICAL");
    }
    EnergyNetworkReport report = bus.getLastReport();
    if (!bus.hasSolution() || report == null) {
      result.convergenceComplete(false).diagnostic("ENERGY_BUS_SOLUTION_STALE_OR_MISSING");
      return result.build();
    }
    Map<String, PlantConstraintParticipant> expected = participantMap(definition);
    for (EnergyAllocation allocation : report.getAllocations()) {
      if (allocation.getDirection() == EnergyPortDirection.OUTPUT) {
        continue;
      }
      PlantConstraintParticipant participant = expected.get(allocation.getParticipantId());
      String unit = sourceUnit(participant, definition);
      String basis = sourceBasis(participant, definition);
      double value = new PowerUnit(allocation.getRequestedPower(), "W").getValue(unit);
      PlantSharedResourceParticipantSample.Status sampleStatus = sampleStatus(value);
      String diagnostic = "";
      if (allocation.getDirection() == EnergyPortDirection.BIDIRECTIONAL) {
        sampleStatus = PlantSharedResourceParticipantSample.Status.METADATA_MISMATCH;
        diagnostic = "Bidirectional requested demand has no unambiguous load direction";
      }
      result.participant(PlantSharedResourceParticipantSample.builder(allocation.getParticipantId(), calculationId)
          .status(sampleStatus).value(value).unit(unit).basis(basis).provenance(safeProvenance).diagnostic(diagnostic)
          .build());
    }
    result.convergenceComplete(true);
    result.sourceTotal(new PowerUnit(report.getRequestedDemand(), "W").getValue(definition.getUnit()),
        definition.getUnit(), definition.getBasis(), safeProvenance);
    return result.build();
  }

  private static PlantConstraintDefinition validateDefinition(PlantConstraintDefinition value) {
    if (value == null) {
      throw new IllegalArgumentException("Shared-resource definition is required");
    }
    if (value.getScope().getType() != PlantConstraintScope.Type.SHARED_RESOURCE) {
      throw new IllegalArgumentException("Shared-resource evidence requires SHARED_RESOURCE scope");
    }
    if (value.getAggregationPolicy() != PlantConstraintDefinition.AggregationPolicy.SHARED_BUDGET
        || value.getLimitDirection() != PlantConstraintDefinition.LimitDirection.MAXIMUM) {
      throw new IllegalArgumentException("This evidence supports maximum SHARED_BUDGET constraints only");
    }
    return value;
  }

  private PlantSharedResourceParticipantSample missingSample(PlantConstraintParticipant participant) {
    return PlantSharedResourceParticipantSample.builder(participant.getSourceId(), calculationId)
        .status(PlantSharedResourceParticipantSample.Status.MISSING).unit(participant.getUnit())
        .basis(participant.getBasis()).diagnostic("Expected participant observation is missing").build();
  }

  private String validateParticipant(PlantConstraintParticipant expected, PlantSharedResourceParticipantSample sample) {
    if (!calculationId.equals(sample.getCalculationId())) {
      return "CALCULATION_ID_MISMATCH";
    }
    if (!sample.isUsable()) {
      return sample.getStatus().name() + diagnosticSuffix(sample.getDiagnostic());
    }
    if (!expected.getUnit().equals(sample.getUnit()) || !expected.getBasis().equals(sample.getBasis())) {
      return "METADATA_MISMATCH";
    }
    if (sample.getProvenance().isEmpty()) {
      return "MISSING_PROVENANCE";
    }
    return "";
  }

  private static String diagnosticSuffix(String diagnostic) {
    return diagnostic == null || diagnostic.isEmpty() ? "" : ":" + diagnostic;
  }

  private static Map<String, PlantConstraintParticipant> participantMap(PlantConstraintDefinition definition) {
    Map<String, PlantConstraintParticipant> result = new LinkedHashMap<String, PlantConstraintParticipant>();
    for (PlantConstraintParticipant participant : definition.getParticipants()) {
      result.put(participant.getSourceId(), participant);
    }
    return result;
  }

  private static String sourceUnit(PlantConstraintParticipant participant, PlantConstraintDefinition definition) {
    return participant == null ? definition.getUnit() : participant.getUnit();
  }

  private static String sourceBasis(PlantConstraintParticipant participant, PlantConstraintDefinition definition) {
    return participant == null ? definition.getBasis() : participant.getBasis();
  }

  private static PlantSharedResourceParticipantSample.Status sampleStatus(double value) {
    return Double.isFinite(value) ? PlantSharedResourceParticipantSample.Status.AVAILABLE
        : PlantSharedResourceParticipantSample.Status.NON_FINITE_VALUE;
  }

  private static boolean approximatelyEqual(double first, double second) {
    double scale = Math.max(1.0, Math.max(Math.abs(first), Math.abs(second)));
    return Math.abs(first - second) <= TOTAL_TOLERANCE * scale;
  }

  private static String safeMessage(RuntimeException ex) {
    String message = ex.getMessage();
    return message == null ? ex.getClass().getSimpleName() : message;
  }

  /** @return evidence schema version */
  public String getSchemaVersion() {
    return SCHEMA_VERSION;
  }

  /** @return exact registered shared-resource definition */
  public PlantConstraintDefinition getDefinition() {
    return definition;
  }

  /** @return exact calculation identity */
  public String getCalculationId() {
    return calculationId;
  }

  /** @return applicable maximum limit in the registered unit and basis */
  public double getApplicableLimit() {
    return applicableLimit;
  }

  /** @return whether the full candidate calculation was declared converged */
  public boolean isConvergenceComplete() {
    return convergenceComplete;
  }

  /** @return deterministic immutable participant observations */
  public List<PlantSharedResourceParticipantSample> getParticipants() {
    return Collections.unmodifiableList(new ArrayList<PlantSharedResourceParticipantSample>(participants));
  }

  /** @return overall evidence status */
  public Status getStatus() {
    return status;
  }

  /** @return true when every enabled participant and aggregate check is usable */
  public boolean isComplete() {
    return status == Status.AVAILABLE || status == Status.DISABLED;
  }

  /** @return true when complete enabled evidence does not exceed its limit */
  public boolean isFeasible() {
    return status == Status.AVAILABLE && normalizedResidual <= 0.0;
  }

  /** @return aggregate shared-resource value, or NaN when incomplete */
  public double getAggregateValue() {
    return aggregateValue;
  }

  /** @return dimensionless utilization, or NaN when incomplete */
  public double getNormalizedUtilization() {
    return normalizedUtilization;
  }

  /** @return dimensionless residual {@code utilization - 1}, or NaN */
  public double getNormalizedResidual() {
    return normalizedResidual;
  }

  /** @return signed physical headroom, or NaN when incomplete */
  public double getPhysicalMargin() {
    return physicalMargin;
  }

  /** @return non-negative physical relief required, or NaN when incomplete */
  public double getRequiredRelief() {
    return requiredRelief;
  }

  /** @return independently calculated target-basis total, or NaN when not supplied */
  public double getSourceTotal() {
    return sourceTotal;
  }

  /** @return unit of the independent total */
  public String getSourceTotalUnit() {
    return sourceTotalUnit;
  }

  /** @return basis of the independent total */
  public String getSourceTotalBasis() {
    return sourceTotalBasis;
  }

  /** @return provenance of the independent total */
  public String getSourceTotalProvenance() {
    return sourceTotalProvenance;
  }

  /** @return immutable fail-closed diagnostics */
  public List<String> getDiagnostics() {
    return Collections.unmodifiableList(new ArrayList<String>(diagnostics));
  }

  /** Converts this immutable evidence to the common plant utilization sample contract. */
  public PlantConstraintSample toPlantConstraintSample() {
    PlantConstraintSample.SampleStatus sampleStatus;
    if (status == Status.AVAILABLE) {
      sampleStatus = PlantConstraintSample.SampleStatus.AVAILABLE;
    } else if (!convergenceComplete) {
      sampleStatus = PlantConstraintSample.SampleStatus.INCOMPLETE_CONVERGENCE;
    } else if (containsDiagnostic("NON_FINITE")) {
      sampleStatus = PlantConstraintSample.SampleStatus.NON_FINITE_VALUE;
    } else if (containsDiagnostic("STALE")) {
      sampleStatus = PlantConstraintSample.SampleStatus.STALE;
    } else if (containsDiagnostic("OUTSIDE_VALIDITY")) {
      sampleStatus = PlantConstraintSample.SampleStatus.OUTSIDE_VALIDITY;
    } else if (containsDiagnostic("EXCEPTION")) {
      sampleStatus = PlantConstraintSample.SampleStatus.EXCEPTION;
    } else if (containsDiagnostic("METADATA") || containsDiagnostic("MISMATCH") || containsDiagnostic("UNEXPECTED")) {
      sampleStatus = PlantConstraintSample.SampleStatus.METADATA_MISMATCH;
    } else if (containsDiagnostic("MISSING")) {
      sampleStatus = PlantConstraintSample.SampleStatus.MISSING_VALUE;
    } else {
      sampleStatus = PlantConstraintSample.SampleStatus.NOT_CALCULABLE;
    }
    return PlantConstraintSample.builder(definition.getQualifiedId(), calculationId).status(sampleStatus)
        .values(aggregateValue, applicableLimit).normalized(normalizedUtilization, normalizedResidual)
        .physical(physicalMargin, requiredRelief).unit(definition.getUnit()).basis(definition.getBasis())
        .provenance(definition.getProvenance()).diagnostic(joinDiagnostics()).build();
  }

  private boolean containsDiagnostic(String token) {
    for (String diagnostic : diagnostics) {
      if (diagnostic.contains(token)) {
        return true;
      }
    }
    return false;
  }

  private String joinDiagnostics() {
    StringBuilder result = new StringBuilder();
    for (String diagnostic : diagnostics) {
      if (result.length() > 0) {
        result.append(';');
      }
      result.append(diagnostic);
    }
    return result.toString();
  }

  /** @return JSON using null rather than zero or non-standard NaN for unavailable numbers */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("qualifiedConstraintId", definition.getQualifiedId());
    root.addProperty("calculationId", calculationId);
    root.addProperty("status", status.name());
    root.addProperty("unit", definition.getUnit());
    root.addProperty("basis", definition.getBasis());
    root.addProperty("provenance", definition.getProvenance());
    root.addProperty("convergenceComplete", convergenceComplete);
    addNumber(root, "applicableLimit", applicableLimit);
    addNumber(root, "aggregateValue", aggregateValue);
    addNumber(root, "normalizedUtilization", normalizedUtilization);
    addNumber(root, "normalizedResidual", normalizedResidual);
    addNumber(root, "physicalMargin", physicalMargin);
    addNumber(root, "requiredRelief", requiredRelief);
    addNumber(root, "sourceTotal", sourceTotal);
    root.addProperty("sourceTotalUnit", sourceTotalUnit);
    root.addProperty("sourceTotalBasis", sourceTotalBasis);
    root.addProperty("sourceTotalProvenance", sourceTotalProvenance);
    JsonArray participantRows = new JsonArray();
    Map<String, PlantConstraintParticipant> expected = participantMap(definition);
    for (PlantSharedResourceParticipantSample sample : participants) {
      JsonObject row = new JsonObject();
      row.addProperty("sourceId", sample.getSourceId());
      row.addProperty("status", sample.getStatus().name());
      row.addProperty("unit", sample.getUnit());
      row.addProperty("basis", sample.getBasis());
      row.addProperty("provenance", sample.getProvenance());
      row.addProperty("diagnostic", sample.getDiagnostic());
      addNumber(row, "value", sample.getValue());
      addNumber(row, "confidence", sample.getConfidence());
      addNumber(row, "validityMinimum", sample.getValidityMinimum());
      addNumber(row, "validityMaximum", sample.getValidityMaximum());
      PlantConstraintParticipant participant = expected.get(sample.getSourceId());
      double converted = Double.NaN;
      if (participant != null && sample.isUsable()) {
        row.addProperty("conversionExplicit", participant.isConversionExplicit());
        row.addProperty("conversionFactor", participant.getConversionFactor());
        row.addProperty("conversionOffset", participant.getConversionOffset());
        try {
          converted = participant.convertToTarget(sample.getValue());
        } catch (RuntimeException ex) {
          converted = Double.NaN;
        }
      } else if (participant != null) {
        row.addProperty("conversionExplicit", participant.isConversionExplicit());
        row.addProperty("conversionFactor", participant.getConversionFactor());
        row.addProperty("conversionOffset", participant.getConversionOffset());
      } else {
        row.add("conversionExplicit", JsonNull.INSTANCE);
        row.add("conversionFactor", JsonNull.INSTANCE);
        row.add("conversionOffset", JsonNull.INSTANCE);
      }
      addNumber(row, "convertedValue", converted);
      participantRows.add(row);
    }
    root.add("participants", participantRows);
    JsonArray diagnosticRows = new JsonArray();
    for (String diagnostic : diagnostics) {
      diagnosticRows.add(diagnostic);
    }
    root.add("diagnostics", diagnosticRows);
    return root.toString();
  }

  private static void addNumber(JsonObject target, String name, double value) {
    if (Double.isFinite(value)) {
      target.addProperty(name, value);
    } else {
      target.add(name, JsonNull.INSTANCE);
    }
  }

  /** Callback-free shared-resource evidence builder. */
  public static final class Builder {
    private final PlantConstraintDefinition definition;
    private final String calculationId;
    private final double applicableLimit;
    private final Map<String, PlantSharedResourceParticipantSample> samples = new LinkedHashMap<String, PlantSharedResourceParticipantSample>();
    private final List<String> diagnostics = new ArrayList<String>();
    private boolean convergenceComplete;
    private boolean sourceTotalSet;
    private double sourceTotal = Double.NaN;
    private String sourceTotalUnit = "";
    private String sourceTotalBasis = "";
    private String sourceTotalProvenance = "";

    private Builder(PlantConstraintDefinition definition, String calculationId, double applicableLimit) {
      this.definition = validateDefinition(definition);
      this.calculationId = calculationId;
      this.applicableLimit = applicableLimit;
    }

    /** Adds one exact participant observation. */
    public Builder participant(PlantSharedResourceParticipantSample sample) {
      if (sample == null) {
        throw new IllegalArgumentException("Shared-resource participant sample is required");
      }
      if (samples.containsKey(sample.getSourceId())) {
        throw new IllegalArgumentException("Duplicate participant sample " + sample.getSourceId());
      }
      samples.put(sample.getSourceId(), sample);
      return this;
    }

    /** Declares whether the complete process calculation converged. */
    public Builder convergenceComplete(boolean value) {
      convergenceComplete = value;
      return this;
    }

    /**
     * Adds an independently calculated total in the registered target unit and basis.
     *
     * @param value independently calculated total
     * @param unit target unit
     * @param basis target basis
     * @param provenance total source provenance
     * @return this builder
     */
    public Builder sourceTotal(double value, String unit, String basis, String provenance) {
      sourceTotal = value;
      sourceTotalUnit = PlantConstraintScope.safeText(unit);
      sourceTotalBasis = PlantConstraintScope.safeText(basis);
      sourceTotalProvenance = PlantConstraintScope.safeText(provenance);
      sourceTotalSet = true;
      return this;
    }

    /** Adds a fail-closed adapter diagnostic. */
    public Builder diagnostic(String value) {
      String safeValue = PlantConstraintScope.safeText(value);
      if (!safeValue.isEmpty()) {
        diagnostics.add(safeValue);
      }
      return this;
    }

    /** @return immutable participant-complete evidence */
    public PlantSharedResourceEvidence build() {
      return new PlantSharedResourceEvidence(this);
    }
  }
}
