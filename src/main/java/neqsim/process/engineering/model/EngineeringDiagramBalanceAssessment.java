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
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Balance;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Severity;

/**
 * Immutable, deterministic assessment of balance residuals against explicit tolerances.
 *
 * <p>
 * The assessment consumes a governed {@link EngineeringDiagramBalanceTable}. It classifies the existing residuals but
 * never adjusts stream values, reconciles measurements, approves a balance, or infers project acceptance criteria.
 * </p>
 */
public final class EngineeringDiagramBalanceAssessment implements Serializable {
  private static final long serialVersionUID = 1000L;
  public static final String SCHEMA_VERSION = "neqsim_engineering_diagram_balance_assessment.v1";

  /** Assessment status for one governed quantity. */
  public enum Status {
    /** No criterion was supplied for the source balance. */
    NOT_ASSESSED,
    /** Complete residual evidence satisfies both declared tolerance limits. */
    WITHIN_TOLERANCE,
    /** Complete residual evidence exceeds at least one declared tolerance limit. */
    OUTSIDE_TOLERANCE,
    /** The source balance lacks complete governed values. */
    INCOMPLETE
  }

  /** Explicit tolerance criteria for one stable balance identity. */
  public static final class Criteria implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String balanceId;
    private final double massAbsoluteTolerance;
    private final double massRelativeTolerance;
    private final double streamEnthalpyAbsoluteTolerance;
    private final double streamEnthalpyRelativeTolerance;
    private final String sourceReference;
    private final EvidenceState evidenceState;

    /**
     * Creates explicit tolerance criteria.
     *
     * <p>
     * A quantity is within tolerance only when both its absolute and relative residual magnitudes are no greater than
     * the corresponding limits.
     * </p>
     *
     * @param balanceId stable source-balance identity
     * @param massAbsoluteTolerance absolute mass-residual tolerance in kg/s
     * @param massRelativeTolerance dimensionless relative mass-residual tolerance
     * @param streamEnthalpyAbsoluteTolerance absolute stream-enthalpy-residual tolerance in W
     * @param streamEnthalpyRelativeTolerance dimensionless relative stream-enthalpy tolerance
     * @param sourceReference source or register reference for the criteria
     * @param evidenceState review evidence state without design-approval implication
     */
    public Criteria(String balanceId, double massAbsoluteTolerance, double massRelativeTolerance,
        double streamEnthalpyAbsoluteTolerance, double streamEnthalpyRelativeTolerance, String sourceReference,
        EvidenceState evidenceState) {
      this.balanceId = requireText(balanceId, "balanceId");
      this.massAbsoluteTolerance = requireTolerance(massAbsoluteTolerance, "massAbsoluteTolerance");
      this.massRelativeTolerance = requireTolerance(massRelativeTolerance, "massRelativeTolerance");
      this.streamEnthalpyAbsoluteTolerance = requireTolerance(streamEnthalpyAbsoluteTolerance,
          "streamEnthalpyAbsoluteTolerance");
      this.streamEnthalpyRelativeTolerance = requireTolerance(streamEnthalpyRelativeTolerance,
          "streamEnthalpyRelativeTolerance");
      this.sourceReference = requireText(sourceReference, "sourceReference");
      if (evidenceState == null) {
        throw new IllegalArgumentException("evidenceState must not be null");
      }
      this.evidenceState = evidenceState;
    }

    /** @return stable source-balance identity */
    public String getBalanceId() {
      return balanceId;
    }

    /** @return absolute mass-residual tolerance in kg/s */
    public double getMassAbsoluteTolerance() {
      return massAbsoluteTolerance;
    }

    /** @return dimensionless relative mass-residual tolerance */
    public double getMassRelativeTolerance() {
      return massRelativeTolerance;
    }

    /** @return absolute stream-enthalpy-residual tolerance in W */
    public double getStreamEnthalpyAbsoluteTolerance() {
      return streamEnthalpyAbsoluteTolerance;
    }

    /** @return dimensionless relative stream-enthalpy tolerance */
    public double getStreamEnthalpyRelativeTolerance() {
      return streamEnthalpyRelativeTolerance;
    }

    /** @return source or register reference for the criteria */
    public String getSourceReference() {
      return sourceReference;
    }

    /** @return review evidence state without design-approval implication */
    public EvidenceState getEvidenceState() {
      return evidenceState;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("balanceId", balanceId);
      result.put("massAbsoluteTolerance", Double.valueOf(massAbsoluteTolerance));
      result.put("massAbsoluteToleranceUnit", "kg/s");
      result.put("massRelativeTolerance", Double.valueOf(massRelativeTolerance));
      result.put("streamEnthalpyAbsoluteTolerance", Double.valueOf(streamEnthalpyAbsoluteTolerance));
      result.put("streamEnthalpyAbsoluteToleranceUnit", "W");
      result.put("streamEnthalpyRelativeTolerance", Double.valueOf(streamEnthalpyRelativeTolerance));
      result.put("sourceReference", sourceReference);
      result.put("evidenceState", evidenceState.name());
      return result;
    }
  }

  /** One deterministic assessment result. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String balanceId;
    private final double massAbsoluteResidual;
    private final double massRelativeResidual;
    private final double streamEnthalpyAbsoluteResidual;
    private final double streamEnthalpyRelativeResidual;
    private final Status massStatus;
    private final Status streamEnthalpyStatus;

    private Result(Balance balance, Status massStatus, Status streamEnthalpyStatus) {
      this.balanceId = balance.getBalanceId();
      this.massAbsoluteResidual = Math.abs(balance.getMassResidual());
      this.massRelativeResidual = Math.abs(balance.getRelativeMassResidual());
      this.streamEnthalpyAbsoluteResidual = Math.abs(balance.getStreamEnthalpyResidual());
      this.streamEnthalpyRelativeResidual = Math.abs(balance.getRelativeStreamEnthalpyResidual());
      this.massStatus = massStatus;
      this.streamEnthalpyStatus = streamEnthalpyStatus;
    }

    /** @return stable source-balance identity */
    public String getBalanceId() {
      return balanceId;
    }

    /** @return absolute mass-residual magnitude in kg/s */
    public double getMassAbsoluteResidual() {
      return massAbsoluteResidual;
    }

    /** @return dimensionless relative mass-residual magnitude */
    public double getMassRelativeResidual() {
      return massRelativeResidual;
    }

    /** @return absolute stream-enthalpy-residual magnitude in W */
    public double getStreamEnthalpyAbsoluteResidual() {
      return streamEnthalpyAbsoluteResidual;
    }

    /** @return dimensionless relative stream-enthalpy-residual magnitude */
    public double getStreamEnthalpyRelativeResidual() {
      return streamEnthalpyRelativeResidual;
    }

    /** @return mass-residual assessment status */
    public Status getMassStatus() {
      return massStatus;
    }

    /** @return stream-enthalpy-residual assessment status */
    public Status getStreamEnthalpyStatus() {
      return streamEnthalpyStatus;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("balanceId", balanceId);
      result.put("massAbsoluteResidual", Double.valueOf(massAbsoluteResidual));
      result.put("massAbsoluteResidualUnit", "kg/s");
      result.put("massRelativeResidual", Double.valueOf(massRelativeResidual));
      result.put("streamEnthalpyAbsoluteResidual", Double.valueOf(streamEnthalpyAbsoluteResidual));
      result.put("streamEnthalpyAbsoluteResidualUnit", "W");
      result.put("streamEnthalpyRelativeResidual", Double.valueOf(streamEnthalpyRelativeResidual));
      result.put("massStatus", massStatus.name());
      result.put("streamEnthalpyStatus", streamEnthalpyStatus.name());
      return result;
    }
  }

  /** One structured assessment diagnostic. */
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
  private final String sourceBalanceTableFingerprint;
  private final List<Criteria> criteria;
  private final List<Result> results;
  private final List<Diagnostic> diagnostics;

  private EngineeringDiagramBalanceAssessment(EngineeringDiagramBalanceTable balanceTable, List<Criteria> criteria,
      List<Result> results, List<Diagnostic> diagnostics) {
    this.documentSetId = balanceTable.getDocumentSetId();
    this.sourceGraphFingerprint = balanceTable.getSourceGraphFingerprint();
    this.designCaseId = balanceTable.getDesignCaseId();
    this.sourceBalanceTableFingerprint = balanceTable.toMap().get("fingerprint").toString();
    this.criteria = Collections.unmodifiableList(new ArrayList<Criteria>(criteria));
    this.results = Collections.unmodifiableList(new ArrayList<Result>(results));
    this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
  }

  /**
   * Assesses governed balance residuals against explicit criteria.
   *
   * @param balanceTable governed source balance table
   * @param criteria explicit criteria grouped by stable balance identity
   * @return immutable assessment with deterministic diagnostics
   */
  public static EngineeringDiagramBalanceAssessment fromBalanceTable(EngineeringDiagramBalanceTable balanceTable,
      List<Criteria> criteria) {
    if (balanceTable == null) {
      throw new IllegalArgumentException("balanceTable must not be null");
    }
    if (criteria == null) {
      throw new IllegalArgumentException("criteria must not be null");
    }
    List<Criteria> sortedCriteria = new ArrayList<Criteria>(criteria);
    if (containsNull(sortedCriteria)) {
      throw new IllegalArgumentException("criteria must not contain null");
    }
    Collections.sort(sortedCriteria, criteriaComparator());
    List<Diagnostic> diagnostics = sourceDiagnostics(balanceTable);
    if (sortedCriteria.isEmpty()) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "BALANCE_ASSESSMENT_CRITERIA_NOT_DECLARED",
          "At least one explicit balance-tolerance criterion is required", ""));
    }

    Map<String, Balance> balancesById = new LinkedHashMap<String, Balance>();
    for (Balance balance : balanceTable.getBalances()) {
      balancesById.put(balance.getBalanceId(), balance);
    }
    Map<String, Criteria> criteriaById = new LinkedHashMap<String, Criteria>();
    for (Criteria criterion : sortedCriteria) {
      if (!balancesById.containsKey(criterion.getBalanceId())) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "BALANCE_ASSESSMENT_UNKNOWN_BALANCE",
            "Tolerance criteria reference a balance absent from the governed balance table", criterion.getBalanceId()));
        continue;
      }
      if (criteriaById.containsKey(criterion.getBalanceId())) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "BALANCE_ASSESSMENT_DUPLICATE_CRITERIA",
            "A balance may have only one explicit tolerance criterion", criterion.getBalanceId()));
        continue;
      }
      criteriaById.put(criterion.getBalanceId(), criterion);
    }

    List<Result> results = new ArrayList<Result>();
    for (Balance balance : balanceTable.getBalances()) {
      Criteria criterion = criteriaById.get(balance.getBalanceId());
      if (criterion == null) {
        diagnostics.add(new Diagnostic(Severity.WARNING, "BALANCE_ASSESSMENT_CRITERIA_MISSING",
            "No explicit tolerance criterion was supplied for the source balance", balance.getBalanceId()));
        results.add(new Result(balance, Status.NOT_ASSESSED, Status.NOT_ASSESSED));
        continue;
      }
      Status massStatus = assessMass(balance, criterion);
      Status enthalpyStatus = assessStreamEnthalpy(balance, criterion);
      results.add(new Result(balance, massStatus, enthalpyStatus));
      addStatusDiagnostic(balance.getBalanceId(), "MASS", massStatus, diagnostics);
      addStatusDiagnostic(balance.getBalanceId(), "STREAM_ENTHALPY", enthalpyStatus, diagnostics);
    }
    Collections.sort(diagnostics, diagnosticComparator());
    return new EngineeringDiagramBalanceAssessment(balanceTable, sortedCriteria, results, diagnostics);
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

  /** @return deterministic fingerprint of the source balance table */
  public String getSourceBalanceTableFingerprint() {
    return sourceBalanceTableFingerprint;
  }

  /** @return immutable explicit criteria in deterministic order */
  public List<Criteria> getCriteria() {
    return Collections.unmodifiableList(new ArrayList<Criteria>(criteria));
  }

  /** @return immutable assessment results in source-balance order */
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
    result.put("sourceBalanceTableFingerprint", sourceBalanceTableFingerprint);
    List<Map<String, Object>> criteriaMaps = new ArrayList<Map<String, Object>>();
    for (Criteria criterion : criteria) {
      criteriaMaps.add(criterion.toMap());
    }
    result.put("criteria", criteriaMaps);
    List<Map<String, Object>> resultMaps = new ArrayList<Map<String, Object>>();
    for (Result assessmentResult : results) {
      resultMaps.add(assessmentResult.toMap());
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

  private static Status assessMass(Balance balance, Criteria criterion) {
    if (!balance.isMassFlowComplete()) {
      return Status.INCOMPLETE;
    }
    return within(Math.abs(balance.getMassResidual()), criterion.getMassAbsoluteTolerance(),
        Math.abs(balance.getRelativeMassResidual()), criterion.getMassRelativeTolerance()) ? Status.WITHIN_TOLERANCE
            : Status.OUTSIDE_TOLERANCE;
  }

  private static Status assessStreamEnthalpy(Balance balance, Criteria criterion) {
    if (!balance.isStreamEnthalpyFlowComplete()) {
      return Status.INCOMPLETE;
    }
    return within(Math.abs(balance.getStreamEnthalpyResidual()), criterion.getStreamEnthalpyAbsoluteTolerance(),
        Math.abs(balance.getRelativeStreamEnthalpyResidual()), criterion.getStreamEnthalpyRelativeTolerance())
            ? Status.WITHIN_TOLERANCE
            : Status.OUTSIDE_TOLERANCE;
  }

  private static boolean within(double absoluteResidual, double absoluteTolerance, double relativeResidual,
      double relativeTolerance) {
    return absoluteResidual <= absoluteTolerance && relativeResidual <= relativeTolerance;
  }

  private static void addStatusDiagnostic(String balanceId, String quantity, Status status,
      List<Diagnostic> diagnostics) {
    if (status == Status.OUTSIDE_TOLERANCE) {
      diagnostics.add(new Diagnostic(Severity.WARNING, "BALANCE_ASSESSMENT_" + quantity + "_OUTSIDE_TOLERANCE",
          "The governed residual exceeds at least one explicit tolerance limit", balanceId));
    } else if (status == Status.INCOMPLETE) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "BALANCE_ASSESSMENT_" + quantity + "_INCOMPLETE",
          "The governed residual is incomplete and cannot be assessed", balanceId));
    }
  }

  private static List<Diagnostic> sourceDiagnostics(EngineeringDiagramBalanceTable balanceTable) {
    List<Diagnostic> result = new ArrayList<Diagnostic>();
    for (EngineeringDiagramBalanceTable.Diagnostic diagnostic : balanceTable.getDiagnostics()) {
      result.add(new Diagnostic(diagnostic.getSeverity(), "BALANCE_ASSESSMENT_SOURCE_" + diagnostic.getCode(),
          diagnostic.getMessage(), diagnostic.getSubjectId()));
    }
    return result;
  }

  private static boolean containsNull(List<Criteria> criteria) {
    for (Criteria criterion : criteria) {
      if (criterion == null) {
        return true;
      }
    }
    return false;
  }

  private static Comparator<Criteria> criteriaComparator() {
    return new Comparator<Criteria>() {
      @Override
      public int compare(Criteria left, Criteria right) {
        int order = left.getBalanceId().compareTo(right.getBalanceId());
        if (order != 0) {
          return order;
        }
        return left.getSourceReference().compareTo(right.getSourceReference());
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

  private static double requireTolerance(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
    return value;
  }
}
