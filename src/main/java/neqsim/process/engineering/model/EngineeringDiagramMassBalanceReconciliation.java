package neqsim.process.engineering.model;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.model.EngineeringDiagramBalanceAssessment.Criteria;
import neqsim.process.engineering.model.EngineeringDiagramBalanceAssessment.Status;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Balance;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Boundary;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Direction;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Severity;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Quantity;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Row;
import neqsim.process.engineering.model.EngineeringDiagramStreamTable.Value;
import neqsim.process.util.reconciliation.DataReconciliationEngine;
import neqsim.process.util.reconciliation.ReconciliationResult;
import neqsim.process.util.reconciliation.ReconciliationVariable;

/**
 * Immutable, deterministic mass-balance data-reconciliation evidence.
 *
 * <p>
 * This projection joins a governed stream table, its explicit-boundary balance table, and an
 * {@link EngineeringDiagramBalanceAssessment}. Only balances whose complete mass residual is explicitly classified
 * {@link Status#OUTSIDE_TOLERANCE} are reconciled. Each participating boundary stream requires a sourced positive
 * standard uncertainty in kg/s. NeqSim's existing weighted-least-squares reconciliation engine then enforces the
 * declared inlet-minus-outlet mass constraint.
 * </p>
 *
 * <p>
 * The source snapshots are never modified. Reconciled values are evidence proposals only; they are not written back to
 * a {@code ProcessSystem}, stream table, balance table, PFD, DEXPI export, or P&amp;ID. This class does not reconcile
 * component flows or energy terms, model covariance between variables, eliminate gross errors, or promote review
 * evidence to engineering approval.
 * </p>
 */
public final class EngineeringDiagramMassBalanceReconciliation implements Serializable {
  private static final long serialVersionUID = 1000L;
  public static final String SCHEMA_VERSION = "neqsim_engineering_diagram_mass_balance_reconciliation.v1";

  /** Outcome for one source balance. */
  public enum ResultStatus {
    /** The source residual already satisfies its explicit assessment criteria. */
    UNCHANGED_WITHIN_TOLERANCE,
    /** Weighted-least-squares reconciliation produced a finite, non-negative closed balance. */
    RECONCILED,
    /** The source balance was not eligible for reconciliation. */
    NOT_RECONCILED,
    /** Reconciliation ran but its candidate result is not acceptable evidence. */
    INVALID
  }

  /** Sourced measurement uncertainty for one stable boundary stream. */
  public static final class Uncertainty implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String balanceId;
    private final String streamSemanticObjectId;
    private final double standardUncertainty;
    private final String resultUnit;
    private final String quantityBasis;
    private final String sourceReference;
    private final String provenance;
    private final EvidenceState evidenceState;

    /**
     * Creates explicit uncertainty evidence for one boundary mass-flow value.
     *
     * @param balanceId stable balance identity
     * @param streamSemanticObjectId stable canonical boundary-stream identity
     * @param standardUncertainty positive mass-flow standard uncertainty
     * @param resultUnit explicit unit; reconciliation requires {@code kg/s}
     * @param quantityBasis explicit basis; reconciliation requires {@code MASS}
     * @param sourceReference uncertainty source or register reference
     * @param provenance uncertainty derivation or data lineage
     * @param evidenceState review evidence state without approval implication
     */
    public Uncertainty(String balanceId, String streamSemanticObjectId, double standardUncertainty, String resultUnit,
        String quantityBasis, String sourceReference, String provenance, EvidenceState evidenceState) {
      this.balanceId = requireText(balanceId, "balanceId");
      this.streamSemanticObjectId = requireText(streamSemanticObjectId, "streamSemanticObjectId");
      this.standardUncertainty = standardUncertainty;
      this.resultUnit = requireText(resultUnit, "resultUnit");
      this.quantityBasis = requireText(quantityBasis, "quantityBasis");
      this.sourceReference = requireText(sourceReference, "sourceReference");
      this.provenance = requireText(provenance, "provenance");
      if (evidenceState == null) {
        throw new IllegalArgumentException("evidenceState must not be null");
      }
      this.evidenceState = evidenceState;
    }

    /** @return stable balance identity */
    public String getBalanceId() {
      return balanceId;
    }

    /** @return stable canonical boundary-stream identity */
    public String getStreamSemanticObjectId() {
      return streamSemanticObjectId;
    }

    /** @return declared standard uncertainty */
    public double getStandardUncertainty() {
      return standardUncertainty;
    }

    /** @return explicit uncertainty unit */
    public String getResultUnit() {
      return resultUnit;
    }

    /** @return explicit uncertainty quantity basis */
    public String getQuantityBasis() {
      return quantityBasis;
    }

    /** @return uncertainty source or register reference */
    public String getSourceReference() {
      return sourceReference;
    }

    /** @return uncertainty derivation or data lineage */
    public String getProvenance() {
      return provenance;
    }

    /** @return review evidence state without approval implication */
    public EvidenceState getEvidenceState() {
      return evidenceState;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("balanceId", balanceId);
      result.put("streamSemanticObjectId", streamSemanticObjectId);
      if (Double.isFinite(standardUncertainty)) {
        result.put("standardUncertainty", Double.valueOf(standardUncertainty));
      } else {
        result.put("standardUncertainty", null);
        result.put("nonFiniteStandardUncertainty", nonFiniteLabel(standardUncertainty));
      }
      result.put("resultUnit", resultUnit);
      result.put("quantityBasis", quantityBasis);
      result.put("sourceReference", sourceReference);
      result.put("provenance", provenance);
      result.put("evidenceState", evidenceState.name());
      return result;
    }
  }

  /** One immutable source-to-reconciled boundary-flow adjustment. */
  public static final class Adjustment implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String balanceId;
    private final String streamSemanticObjectId;
    private final Direction direction;
    private final double measuredValue;
    private final double standardUncertainty;
    private final double reconciledValue;
    private final double adjustment;
    private final double normalizedResidual;
    private final boolean grossError;
    private final String sourceReference;
    private final String provenance;
    private final EvidenceState evidenceState;

    private Adjustment(Boundary boundary, Uncertainty uncertainty, ReconciliationVariable variable) {
      this.balanceId = boundary.getBalanceId();
      this.streamSemanticObjectId = boundary.getStreamSemanticObjectId();
      this.direction = boundary.getDirection();
      this.measuredValue = variable.getMeasuredValue();
      this.standardUncertainty = variable.getUncertainty();
      this.reconciledValue = variable.getReconciledValue();
      this.adjustment = variable.getAdjustment();
      this.normalizedResidual = variable.getNormalizedResidual();
      this.grossError = variable.isGrossError();
      this.sourceReference = uncertainty.getSourceReference();
      this.provenance = uncertainty.getProvenance();
      this.evidenceState = uncertainty.getEvidenceState();
    }

    /** @return stable balance identity */
    public String getBalanceId() {
      return balanceId;
    }

    /** @return stable canonical boundary-stream identity */
    public String getStreamSemanticObjectId() {
      return streamSemanticObjectId;
    }

    /** @return explicit boundary direction */
    public Direction getDirection() {
      return direction;
    }

    /** @return governed source mass flow in kg/s */
    public double getMeasuredValue() {
      return measuredValue;
    }

    /** @return sourced standard uncertainty in kg/s */
    public double getStandardUncertainty() {
      return standardUncertainty;
    }

    /** @return weighted-least-squares candidate mass flow in kg/s */
    public double getReconciledValue() {
      return reconciledValue;
    }

    /** @return reconciled minus measured mass flow in kg/s */
    public double getAdjustment() {
      return adjustment;
    }

    /** @return normalized adjustment reported by the reconciliation engine */
    public double getNormalizedResidual() {
      return normalizedResidual;
    }

    /** @return true when the reconciliation engine flags this observation as a gross error */
    public boolean isGrossError() {
      return grossError;
    }

    /** @return uncertainty source or register reference */
    public String getSourceReference() {
      return sourceReference;
    }

    /** @return uncertainty derivation or data lineage */
    public String getProvenance() {
      return provenance;
    }

    /** @return review evidence state without approval implication */
    public EvidenceState getEvidenceState() {
      return evidenceState;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("balanceId", balanceId);
      result.put("streamSemanticObjectId", streamSemanticObjectId);
      result.put("direction", direction.name());
      result.put("measuredValue", Double.valueOf(measuredValue));
      result.put("standardUncertainty", Double.valueOf(standardUncertainty));
      result.put("reconciledValue", Double.valueOf(reconciledValue));
      result.put("adjustment", Double.valueOf(adjustment));
      result.put("normalizedResidual", Double.valueOf(normalizedResidual));
      result.put("grossError", Boolean.valueOf(grossError));
      result.put("resultUnit", "kg/s");
      result.put("quantityBasis", "MASS");
      result.put("sourceReference", sourceReference);
      result.put("provenance", provenance);
      result.put("evidenceState", evidenceState.name());
      return result;
    }
  }

  /** One deterministic balance-level reconciliation result. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String balanceId;
    private final ResultStatus status;
    private final double measuredResidual;
    private final double measuredRelativeResidual;
    private final double reconciledResidual;
    private final double reconciledRelativeResidual;
    private final double objectiveValue;
    private final boolean globalTestPassed;
    private final boolean grossErrorsPresent;
    private final int adjustmentCount;

    private Result(Balance balance, ResultStatus status, double reconciledResidual, double reconciledRelativeResidual,
        double objectiveValue, boolean globalTestPassed, boolean grossErrorsPresent, int adjustmentCount) {
      this.balanceId = balance.getBalanceId();
      this.status = status;
      this.measuredResidual = balance.getMassResidual();
      this.measuredRelativeResidual = balance.getRelativeMassResidual();
      this.reconciledResidual = reconciledResidual;
      this.reconciledRelativeResidual = reconciledRelativeResidual;
      this.objectiveValue = objectiveValue;
      this.globalTestPassed = globalTestPassed;
      this.grossErrorsPresent = grossErrorsPresent;
      this.adjustmentCount = adjustmentCount;
    }

    /** @return stable balance identity */
    public String getBalanceId() {
      return balanceId;
    }

    /** @return reconciliation outcome */
    public ResultStatus getStatus() {
      return status;
    }

    /** @return source inlet-minus-outlet mass residual in kg/s */
    public double getMeasuredResidual() {
      return measuredResidual;
    }

    /** @return source relative mass residual */
    public double getMeasuredRelativeResidual() {
      return measuredRelativeResidual;
    }

    /** @return reconciled inlet-minus-outlet mass residual in kg/s */
    public double getReconciledResidual() {
      return reconciledResidual;
    }

    /** @return reconciled relative mass residual */
    public double getReconciledRelativeResidual() {
      return reconciledRelativeResidual;
    }

    /** @return weighted sum of squared normalized adjustments */
    public double getObjectiveValue() {
      return objectiveValue;
    }

    /** @return global statistical test result reported by the reconciliation engine */
    public boolean isGlobalTestPassed() {
      return globalTestPassed;
    }

    /** @return true when at least one adjustment is flagged as a gross error */
    public boolean hasGrossErrors() {
      return grossErrorsPresent;
    }

    /** @return number of boundary-flow adjustments for this balance */
    public int getAdjustmentCount() {
      return adjustmentCount;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("balanceId", balanceId);
      result.put("status", status.name());
      result.put("measuredResidual", Double.valueOf(measuredResidual));
      result.put("measuredRelativeResidual", Double.valueOf(measuredRelativeResidual));
      result.put("reconciledResidual", Double.valueOf(reconciledResidual));
      result.put("reconciledRelativeResidual", Double.valueOf(reconciledRelativeResidual));
      result.put("objectiveValue", Double.valueOf(objectiveValue));
      result.put("globalTestPassed", Boolean.valueOf(globalTestPassed));
      result.put("grossErrorsPresent", Boolean.valueOf(grossErrorsPresent));
      result.put("adjustmentCount", Integer.valueOf(adjustmentCount));
      result.put("massFlowUnit", "kg/s");
      return result;
    }
  }

  /** One structured reconciliation diagnostic. */
  public static final class Diagnostic implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Severity severity;
    private final String code;
    private final String message;
    private final String subjectId;

    private Diagnostic(Severity severity, String code, String message, String subjectId) {
      this.severity = severity;
      this.code = code;
      this.message = message;
      this.subjectId = subjectId;
    }

    /** @return diagnostic severity */
    public Severity getSeverity() {
      return severity;
    }

    /** @return stable machine-readable diagnostic code */
    public String getCode() {
      return code;
    }

    /** @return human-readable diagnostic message */
    public String getMessage() {
      return message;
    }

    /** @return affected stable identity */
    public String getSubjectId() {
      return subjectId;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("severity", severity.name());
      result.put("code", code);
      result.put("message", message);
      result.put("subjectId", subjectId);
      return result;
    }
  }

  private final String documentSetId;
  private final String sourceGraphFingerprint;
  private final String designCaseId;
  private final String sourceStreamTableFingerprint;
  private final String sourceBalanceTableFingerprint;
  private final String sourceAssessmentFingerprint;
  private final List<Uncertainty> uncertainties;
  private final List<Adjustment> adjustments;
  private final List<Result> results;
  private final List<Diagnostic> diagnostics;

  private EngineeringDiagramMassBalanceReconciliation(EngineeringDiagramStreamTable streamTable,
      EngineeringDiagramBalanceTable balanceTable, EngineeringDiagramBalanceAssessment assessment,
      List<Uncertainty> uncertainties, List<Adjustment> adjustments, List<Result> results,
      List<Diagnostic> diagnostics) {
    this.documentSetId = balanceTable.getDocumentSetId();
    this.sourceGraphFingerprint = balanceTable.getSourceGraphFingerprint();
    this.designCaseId = balanceTable.getDesignCaseId();
    this.sourceStreamTableFingerprint = streamTable.toMap().get("fingerprint").toString();
    this.sourceBalanceTableFingerprint = balanceTable.toMap().get("fingerprint").toString();
    this.sourceAssessmentFingerprint = assessment.toMap().get("fingerprint").toString();
    this.uncertainties = Collections.unmodifiableList(new ArrayList<Uncertainty>(uncertainties));
    this.adjustments = Collections.unmodifiableList(new ArrayList<Adjustment>(adjustments));
    this.results = Collections.unmodifiableList(new ArrayList<Result>(results));
    this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
  }

  /**
   * Reconciles explicitly assessed outside-tolerance boundary mass flows.
   *
   * @param streamTable governed source stream table
   * @param balanceTable explicit-boundary balance table derived from {@code streamTable}
   * @param assessment explicit tolerance assessment derived from {@code balanceTable}
   * @param uncertainties sourced standard uncertainties for every reconciled boundary stream
   * @return immutable reconciliation evidence with deterministic diagnostics
   */
  public static EngineeringDiagramMassBalanceReconciliation fromSources(EngineeringDiagramStreamTable streamTable,
      EngineeringDiagramBalanceTable balanceTable, EngineeringDiagramBalanceAssessment assessment,
      List<Uncertainty> uncertainties) {
    requireSources(streamTable, balanceTable, assessment, uncertainties);
    List<Uncertainty> sortedUncertainties = new ArrayList<Uncertainty>(uncertainties);
    if (containsNull(sortedUncertainties)) {
      throw new IllegalArgumentException("uncertainties must not contain null");
    }
    Collections.sort(sortedUncertainties, uncertaintyComparator());

    List<Diagnostic> diagnostics = validateSourceIdentity(streamTable, balanceTable, assessment);
    Map<String, Row> rowsById = rowsById(streamTable);
    Map<String, Balance> balancesById = balancesById(balanceTable);
    Map<String, Status> massStatusById = massStatusById(assessment);
    Map<String, Criteria> criteriaById = criteriaById(assessment);
    Map<String, List<Boundary>> boundariesByBalance = boundariesByBalance(balanceTable);
    Map<String, Uncertainty> uncertaintyByKey = new LinkedHashMap<String, Uncertainty>();
    for (Uncertainty uncertainty : sortedUncertainties) {
      String key = key(uncertainty.getBalanceId(), uncertainty.getStreamSemanticObjectId());
      if (!balancesById.containsKey(uncertainty.getBalanceId())) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "MASS_RECONCILIATION_UNCERTAINTY_UNKNOWN_BALANCE",
            "Uncertainty evidence references a balance absent from the governed source", uncertainty.getBalanceId()));
      } else if (!containsBoundary(boundariesByBalance.get(uncertainty.getBalanceId()),
          uncertainty.getStreamSemanticObjectId())) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "MASS_RECONCILIATION_UNCERTAINTY_UNKNOWN_BOUNDARY",
            "Uncertainty evidence references a stream absent from the declared balance boundary", key));
      } else if (uncertaintyByKey.containsKey(key)) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "MASS_RECONCILIATION_UNCERTAINTY_DUPLICATE",
            "A boundary stream may have only one uncertainty record for the same balance", key));
      } else {
        uncertaintyByKey.put(key, uncertainty);
      }
      if (!validUncertainty(uncertainty)) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "MASS_RECONCILIATION_UNCERTAINTY_INVALID",
            "Standard uncertainty must be finite, positive, in kg/s, and on a MASS basis", key));
      }
    }

    boolean sourcesValid = !hasErrors(diagnostics);
    List<Adjustment> adjustments = new ArrayList<Adjustment>();
    List<Result> results = new ArrayList<Result>();
    for (Balance balance : balanceTable.getBalances()) {
      Status massStatus = massStatusById.get(balance.getBalanceId());
      Criteria criterion = criteriaById.get(balance.getBalanceId());
      if (!sourcesValid) {
        results.add(new Result(balance, ResultStatus.NOT_RECONCILED, balance.getMassResidual(),
            balance.getRelativeMassResidual(), 0.0, false, false, 0));
      } else if (massStatus == Status.WITHIN_TOLERANCE) {
        results.add(new Result(balance, ResultStatus.UNCHANGED_WITHIN_TOLERANCE, balance.getMassResidual(),
            balance.getRelativeMassResidual(), 0.0, true, false, 0));
      } else if (massStatus != Status.OUTSIDE_TOLERANCE || criterion == null || !balance.isMassFlowComplete()) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "MASS_RECONCILIATION_SOURCE_NOT_ELIGIBLE",
            "A balance must have complete residual evidence and an explicit outside-tolerance assessment",
            balance.getBalanceId()));
        results.add(new Result(balance, ResultStatus.NOT_RECONCILED, balance.getMassResidual(),
            balance.getRelativeMassResidual(), 0.0, false, false, 0));
      } else {
        reconcileBalance(balance, boundariesByBalance.get(balance.getBalanceId()), rowsById, uncertaintyByKey,
            criterion, adjustments, results, diagnostics);
      }
    }
    Collections.sort(adjustments, adjustmentComparator());
    Collections.sort(diagnostics, diagnosticComparator());
    return new EngineeringDiagramMassBalanceReconciliation(streamTable, balanceTable, assessment, sortedUncertainties,
        adjustments, results, diagnostics);
  }

  /** @return source controlled-document identity */
  public String getDocumentSetId() {
    return documentSetId;
  }

  /** @return canonical source-graph fingerprint */
  public String getSourceGraphFingerprint() {
    return sourceGraphFingerprint;
  }

  /** @return selected operating-case identity */
  public String getDesignCaseId() {
    return designCaseId;
  }

  /** @return deterministic fingerprint of the source stream table */
  public String getSourceStreamTableFingerprint() {
    return sourceStreamTableFingerprint;
  }

  /** @return deterministic fingerprint of the source balance table */
  public String getSourceBalanceTableFingerprint() {
    return sourceBalanceTableFingerprint;
  }

  /** @return deterministic fingerprint of the source tolerance assessment */
  public String getSourceAssessmentFingerprint() {
    return sourceAssessmentFingerprint;
  }

  /** @return immutable uncertainty evidence in deterministic order */
  public List<Uncertainty> getUncertainties() {
    return Collections.unmodifiableList(new ArrayList<Uncertainty>(uncertainties));
  }

  /** @return immutable source-to-reconciled adjustments in deterministic order */
  public List<Adjustment> getAdjustments() {
    return Collections.unmodifiableList(new ArrayList<Adjustment>(adjustments));
  }

  /** @return immutable balance-level results in source-balance order */
  public List<Result> getResults() {
    return Collections.unmodifiableList(new ArrayList<Result>(results));
  }

  /** @return immutable structured diagnostics */
  public List<Diagnostic> getDiagnostics() {
    return Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
  }

  /** @return true when no error-severity diagnostic is present */
  public boolean isValid() {
    for (Diagnostic diagnostic : diagnostics) {
      if (diagnostic.getSeverity() == Severity.ERROR) {
        return false;
      }
    }
    return true;
  }

  /** @return deterministic machine-readable representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", SCHEMA_VERSION);
    result.put("documentSetId", documentSetId);
    result.put("sourceGraphFingerprint", sourceGraphFingerprint);
    result.put("designCaseId", designCaseId);
    result.put("sourceStreamTableFingerprint", sourceStreamTableFingerprint);
    result.put("sourceBalanceTableFingerprint", sourceBalanceTableFingerprint);
    result.put("sourceAssessmentFingerprint", sourceAssessmentFingerprint);
    List<Map<String, Object>> uncertaintyMaps = new ArrayList<Map<String, Object>>();
    for (Uncertainty uncertainty : uncertainties) {
      uncertaintyMaps.add(uncertainty.toMap());
    }
    result.put("uncertainties", uncertaintyMaps);
    List<Map<String, Object>> adjustmentMaps = new ArrayList<Map<String, Object>>();
    for (Adjustment adjustment : adjustments) {
      adjustmentMaps.add(adjustment.toMap());
    }
    result.put("adjustments", adjustmentMaps);
    List<Map<String, Object>> resultMaps = new ArrayList<Map<String, Object>>();
    for (Result reconciliationResult : results) {
      resultMaps.add(reconciliationResult.toMap());
    }
    result.put("results", resultMaps);
    List<Map<String, Object>> diagnosticMaps = new ArrayList<Map<String, Object>>();
    for (Diagnostic diagnostic : diagnostics) {
      diagnosticMaps.add(diagnostic.toMap());
    }
    result.put("diagnostics", diagnosticMaps);
    result.put("fingerprint", fingerprint(result));
    return result;
  }

  /** @return deterministic pretty-printed JSON */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  private static void reconcileBalance(Balance balance, List<Boundary> boundaries, Map<String, Row> rowsById,
      Map<String, Uncertainty> uncertaintyByKey, Criteria criterion, List<Adjustment> adjustments, List<Result> results,
      List<Diagnostic> diagnostics) {
    if (boundaries == null || boundaries.size() < 2 || !hasBothDirections(boundaries)) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "MASS_RECONCILIATION_BOUNDARY_UNDERSPECIFIED",
          "Reconciliation requires at least two boundaries including one inlet and one outlet",
          balance.getBalanceId()));
      results.add(new Result(balance, ResultStatus.NOT_RECONCILED, balance.getMassResidual(),
          balance.getRelativeMassResidual(), 0.0, false, false, 0));
      return;
    }

    DataReconciliationEngine engine = new DataReconciliationEngine();
    List<Boundary> participatingBoundaries = new ArrayList<Boundary>();
    List<Uncertainty> participatingUncertainties = new ArrayList<Uncertainty>();
    boolean inputValid = true;
    for (Boundary boundary : boundaries) {
      String subject = key(boundary.getBalanceId(), boundary.getStreamSemanticObjectId());
      Row row = rowsById.get(boundary.getStreamSemanticObjectId());
      Value massFlow = row == null ? null : row.getValues().get(Quantity.MASS_FLOW);
      Uncertainty uncertainty = uncertaintyByKey.get(subject);
      if (!validMassFlow(massFlow)) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "MASS_RECONCILIATION_SOURCE_VALUE_INVALID",
            "Every reconciled boundary requires a finite non-negative mass flow in kg/s on a MASS basis", subject));
        inputValid = false;
      }
      if (uncertainty == null) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "MASS_RECONCILIATION_UNCERTAINTY_MISSING",
            "Every reconciled boundary requires one sourced standard uncertainty", subject));
        inputValid = false;
      } else if (!validUncertainty(uncertainty)) {
        inputValid = false;
      }
      if (validMassFlow(massFlow) && uncertainty != null && validUncertainty(uncertainty)) {
        ReconciliationVariable variable = new ReconciliationVariable(subject, massFlow.getResultValue(),
            uncertainty.getStandardUncertainty()).setUnit("kg/s");
        engine.addVariable(variable);
        participatingBoundaries.add(boundary);
        participatingUncertainties.add(uncertainty);
      }
    }
    if (!inputValid || participatingBoundaries.size() != boundaries.size()) {
      results.add(new Result(balance, ResultStatus.NOT_RECONCILED, balance.getMassResidual(),
          balance.getRelativeMassResidual(), 0.0, false, false, 0));
      return;
    }

    double[] coefficients = new double[participatingBoundaries.size()];
    for (int i = 0; i < participatingBoundaries.size(); i++) {
      coefficients[i] = participatingBoundaries.get(i).getDirection() == Direction.INLET ? 1.0 : -1.0;
    }
    engine.addConstraint(coefficients, balance.getBalanceId() + ":mass");
    ReconciliationResult engineResult = engine.reconcile();
    if (!engineResult.isConverged()) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "MASS_RECONCILIATION_SOLVER_FAILED",
          "Weighted-least-squares reconciliation failed: " + engineResult.getErrorMessage(), balance.getBalanceId()));
      results.add(new Result(balance, ResultStatus.INVALID, balance.getMassResidual(),
          balance.getRelativeMassResidual(), 0.0, false, false, 0));
      return;
    }

    double inlet = 0.0;
    double outlet = 0.0;
    boolean candidateValid = true;
    List<Adjustment> balanceAdjustments = new ArrayList<Adjustment>();
    List<ReconciliationVariable> variables = engineResult.getVariables();
    for (int i = 0; i < variables.size(); i++) {
      ReconciliationVariable variable = variables.get(i);
      Boundary boundary = participatingBoundaries.get(i);
      Adjustment adjustment = new Adjustment(boundary, participatingUncertainties.get(i), variable);
      balanceAdjustments.add(adjustment);
      if (!Double.isFinite(adjustment.getReconciledValue()) || adjustment.getReconciledValue() < 0.0) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "MASS_RECONCILIATION_CANDIDATE_NEGATIVE_OR_NONFINITE",
            "A reconciled mass-flow candidate must be finite and non-negative",
            key(balance.getBalanceId(), boundary.getStreamSemanticObjectId())));
        candidateValid = false;
      }
      if (adjustment.isGrossError()) {
        diagnostics.add(new Diagnostic(Severity.WARNING, "MASS_RECONCILIATION_GROSS_ERROR_CANDIDATE",
            "The reconciliation engine flagged this observation for gross-error review",
            key(balance.getBalanceId(), boundary.getStreamSemanticObjectId())));
      }
      if (boundary.getDirection() == Direction.INLET) {
        inlet += adjustment.getReconciledValue();
      } else {
        outlet += adjustment.getReconciledValue();
      }
    }
    double residual = inlet - outlet;
    double scale = Math.max(Math.abs(inlet), Math.abs(outlet));
    double relativeResidual = scale == 0.0 ? 0.0 : residual / scale;
    if (!within(Math.abs(residual), criterion.getMassAbsoluteTolerance(), Math.abs(relativeResidual),
        criterion.getMassRelativeTolerance())) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "MASS_RECONCILIATION_CANDIDATE_OUTSIDE_TOLERANCE",
          "The reconciled candidate does not satisfy the explicit mass-residual criteria", balance.getBalanceId()));
      candidateValid = false;
    }
    adjustments.addAll(balanceAdjustments);
    if (!engineResult.isGlobalTestPassed()) {
      diagnostics.add(new Diagnostic(Severity.WARNING, "MASS_RECONCILIATION_GLOBAL_TEST_FAILED",
          "The reconciled candidate requires statistical review because the global test failed",
          balance.getBalanceId()));
    }
    ResultStatus status = candidateValid ? ResultStatus.RECONCILED : ResultStatus.INVALID;
    results.add(new Result(balance, status, residual, relativeResidual, engineResult.getObjectiveValue(),
        engineResult.isGlobalTestPassed(), engineResult.hasGrossErrors(), balanceAdjustments.size()));
  }

  private static void requireSources(EngineeringDiagramStreamTable streamTable,
      EngineeringDiagramBalanceTable balanceTable, EngineeringDiagramBalanceAssessment assessment,
      List<Uncertainty> uncertainties) {
    if (streamTable == null) {
      throw new IllegalArgumentException("streamTable must not be null");
    }
    if (balanceTable == null) {
      throw new IllegalArgumentException("balanceTable must not be null");
    }
    if (assessment == null) {
      throw new IllegalArgumentException("assessment must not be null");
    }
    if (uncertainties == null) {
      throw new IllegalArgumentException("uncertainties must not be null");
    }
  }

  private static List<Diagnostic> validateSourceIdentity(EngineeringDiagramStreamTable streamTable,
      EngineeringDiagramBalanceTable balanceTable, EngineeringDiagramBalanceAssessment assessment) {
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    String streamFingerprint = streamTable.toMap().get("fingerprint").toString();
    String balanceFingerprint = balanceTable.toMap().get("fingerprint").toString();
    if (!balanceTable.getSourceStreamTableFingerprint().equals(streamFingerprint)) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "MASS_RECONCILIATION_STREAM_SOURCE_MISMATCH",
          "The balance table was not derived from the supplied governed stream table", ""));
    }
    if (!assessment.getSourceBalanceTableFingerprint().equals(balanceFingerprint)) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "MASS_RECONCILIATION_ASSESSMENT_SOURCE_MISMATCH",
          "The tolerance assessment was not derived from the supplied balance table", ""));
    }
    if (!sameContext(streamTable, balanceTable, assessment)) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "MASS_RECONCILIATION_CONTEXT_MISMATCH",
          "Stream, balance, and assessment snapshots must share document, graph, and design-case identity", ""));
    }
    if (!streamTable.isValid() || !balanceTable.isValid() || !assessment.isValid()) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "MASS_RECONCILIATION_SOURCE_INVALID",
          "Every governed source snapshot must be valid before reconciliation", ""));
    }
    return diagnostics;
  }

  private static boolean sameContext(EngineeringDiagramStreamTable streamTable,
      EngineeringDiagramBalanceTable balanceTable, EngineeringDiagramBalanceAssessment assessment) {
    return streamTable.getDocumentSetId().equals(balanceTable.getDocumentSetId())
        && streamTable.getDocumentSetId().equals(assessment.getDocumentSetId())
        && streamTable.getSourceGraphFingerprint().equals(balanceTable.getSourceGraphFingerprint())
        && streamTable.getSourceGraphFingerprint().equals(assessment.getSourceGraphFingerprint())
        && streamTable.getDesignCaseId().equals(balanceTable.getDesignCaseId())
        && streamTable.getDesignCaseId().equals(assessment.getDesignCaseId());
  }

  private static Map<String, Row> rowsById(EngineeringDiagramStreamTable streamTable) {
    Map<String, Row> result = new LinkedHashMap<String, Row>();
    for (Row row : streamTable.getRows()) {
      result.put(row.getSemanticObjectId(), row);
    }
    return result;
  }

  private static Map<String, Balance> balancesById(EngineeringDiagramBalanceTable balanceTable) {
    Map<String, Balance> result = new LinkedHashMap<String, Balance>();
    for (Balance balance : balanceTable.getBalances()) {
      result.put(balance.getBalanceId(), balance);
    }
    return result;
  }

  private static Map<String, Status> massStatusById(EngineeringDiagramBalanceAssessment assessment) {
    Map<String, Status> result = new LinkedHashMap<String, Status>();
    for (EngineeringDiagramBalanceAssessment.Result assessmentResult : assessment.getResults()) {
      result.put(assessmentResult.getBalanceId(), assessmentResult.getMassStatus());
    }
    return result;
  }

  private static Map<String, Criteria> criteriaById(EngineeringDiagramBalanceAssessment assessment) {
    Map<String, Criteria> result = new LinkedHashMap<String, Criteria>();
    for (Criteria criterion : assessment.getCriteria()) {
      if (!result.containsKey(criterion.getBalanceId())) {
        result.put(criterion.getBalanceId(), criterion);
      }
    }
    return result;
  }

  private static Map<String, List<Boundary>> boundariesByBalance(EngineeringDiagramBalanceTable balanceTable) {
    Map<String, List<Boundary>> result = new LinkedHashMap<String, List<Boundary>>();
    for (Boundary boundary : balanceTable.getBoundaries()) {
      List<Boundary> boundaries = result.get(boundary.getBalanceId());
      if (boundaries == null) {
        boundaries = new ArrayList<Boundary>();
        result.put(boundary.getBalanceId(), boundaries);
      }
      boundaries.add(boundary);
    }
    return result;
  }

  private static boolean hasBothDirections(List<Boundary> boundaries) {
    boolean inlet = false;
    boolean outlet = false;
    for (Boundary boundary : boundaries) {
      inlet |= boundary.getDirection() == Direction.INLET;
      outlet |= boundary.getDirection() == Direction.OUTLET;
    }
    return inlet && outlet;
  }

  private static boolean containsBoundary(List<Boundary> boundaries, String streamSemanticObjectId) {
    if (boundaries == null) {
      return false;
    }
    for (Boundary boundary : boundaries) {
      if (boundary.getStreamSemanticObjectId().equals(streamSemanticObjectId)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasErrors(List<Diagnostic> diagnostics) {
    for (Diagnostic diagnostic : diagnostics) {
      if (diagnostic.getSeverity() == Severity.ERROR) {
        return true;
      }
    }
    return false;
  }

  private static boolean validMassFlow(Value value) {
    return value != null && Double.isFinite(value.getResultValue()) && value.getResultValue() >= 0.0
        && "kg/s".equals(value.getResultUnit()) && "MASS".equals(value.getQuantityBasis());
  }

  private static boolean validUncertainty(Uncertainty uncertainty) {
    return Double.isFinite(uncertainty.getStandardUncertainty()) && uncertainty.getStandardUncertainty() > 0.0
        && "kg/s".equals(uncertainty.getResultUnit()) && "MASS".equals(uncertainty.getQuantityBasis());
  }

  private static boolean within(double absoluteResidual, double absoluteTolerance, double relativeResidual,
      double relativeTolerance) {
    return absoluteResidual <= absoluteTolerance && relativeResidual <= relativeTolerance;
  }

  private static boolean containsNull(List<Uncertainty> uncertainties) {
    for (Uncertainty uncertainty : uncertainties) {
      if (uncertainty == null) {
        return true;
      }
    }
    return false;
  }

  private static String key(String balanceId, String streamId) {
    return balanceId + "|" + streamId;
  }

  private static String nonFiniteLabel(double value) {
    if (Double.isNaN(value)) {
      return "NaN";
    }
    return value > 0.0 ? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY";
  }

  private static Comparator<Uncertainty> uncertaintyComparator() {
    return new Comparator<Uncertainty>() {
      @Override
      public int compare(Uncertainty left, Uncertainty right) {
        int order = left.getBalanceId().compareTo(right.getBalanceId());
        if (order != 0) {
          return order;
        }
        order = left.getStreamSemanticObjectId().compareTo(right.getStreamSemanticObjectId());
        if (order != 0) {
          return order;
        }
        order = Double.compare(left.getStandardUncertainty(), right.getStandardUncertainty());
        if (order != 0) {
          return order;
        }
        order = left.getSourceReference().compareTo(right.getSourceReference());
        if (order != 0) {
          return order;
        }
        order = left.getProvenance().compareTo(right.getProvenance());
        if (order != 0) {
          return order;
        }
        return left.getEvidenceState().compareTo(right.getEvidenceState());
      }
    };
  }

  private static Comparator<Adjustment> adjustmentComparator() {
    return new Comparator<Adjustment>() {
      @Override
      public int compare(Adjustment left, Adjustment right) {
        int order = left.getBalanceId().compareTo(right.getBalanceId());
        if (order != 0) {
          return order;
        }
        return left.getStreamSemanticObjectId().compareTo(right.getStreamSemanticObjectId());
      }
    };
  }

  private static Comparator<Diagnostic> diagnosticComparator() {
    return new Comparator<Diagnostic>() {
      @Override
      public int compare(Diagnostic left, Diagnostic right) {
        int order = left.getSubjectId().compareTo(right.getSubjectId());
        if (order != 0) {
          return order;
        }
        order = left.getCode().compareTo(right.getCode());
        if (order != 0) {
          return order;
        }
        return left.getMessage().compareTo(right.getMessage());
      }
    };
  }

  private static String fingerprint(Map<String, Object> value) {
    String canonical = new GsonBuilder().create().toJson(value);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte item : hash) {
        result.append(String.format("%02x", item & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
