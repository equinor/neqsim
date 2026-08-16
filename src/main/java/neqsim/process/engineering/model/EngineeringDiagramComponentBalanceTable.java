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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Balance;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Boundary;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Direction;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Severity;

/**
 * Immutable, deterministic component-resolved mass-balance projection.
 *
 * <p>
 * The projection binds explicit component mass-flow records to the explicit stream boundaries in an
 * {@link EngineeringDiagramBalanceTable}. It never infers a component slate, converts composition, or changes a
 * governed stream value. Callers must provide one explicit zero or non-zero value for every component on every boundary
 * stream. Missing, duplicate, unknown, wrongly based, and non-finite values remain visible as structured diagnostics.
 * </p>
 *
 * <p>
 * This class calculates residuals only. It does not apply project tolerances, reconcile data, close equipment heat or
 * work terms, or promote review evidence to engineering approval.
 * </p>
 */
public final class EngineeringDiagramComponentBalanceTable implements Serializable {
  private static final long serialVersionUID = 1000L;
  public static final String SCHEMA_VERSION = "neqsim_engineering_diagram_component_balance_table.v1";

  /** One explicit component mass-flow value on a declared boundary stream. */
  public static final class ComponentFlow implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String balanceId;
    private final String streamSemanticObjectId;
    private final String componentId;
    private final String componentName;
    private final double resultValue;
    private final String resultUnit;
    private final String quantityBasis;
    private final String sourceReference;
    private final String provenance;
    private final EvidenceState evidenceState;

    /**
     * Creates one explicit component mass-flow record.
     *
     * @param balanceId stable balance identity
     * @param streamSemanticObjectId canonical boundary-stream identity
     * @param componentId stable component identity within the source component register
     * @param componentName source component name retained for review
     * @param resultValue component mass flow
     * @param resultUnit explicit result unit; aggregation requires {@code kg/s}
     * @param quantityBasis explicit quantity basis; aggregation requires {@code COMPONENT_MASS}
     * @param sourceReference source calculation or register reference
     * @param provenance calculation or data lineage description
     * @param evidenceState review evidence state
     * @throws IllegalArgumentException if required identity, source, provenance, or evidence is missing
     */
    public ComponentFlow(String balanceId, String streamSemanticObjectId, String componentId, String componentName,
        double resultValue, String resultUnit, String quantityBasis, String sourceReference, String provenance,
        EvidenceState evidenceState) {
      this.balanceId = requireText(balanceId, "balanceId");
      this.streamSemanticObjectId = requireText(streamSemanticObjectId, "streamSemanticObjectId");
      this.componentId = requireText(componentId, "componentId");
      this.componentName = requireText(componentName, "componentName");
      this.resultValue = resultValue;
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

    /** @return canonical boundary-stream identity */
    public String getStreamSemanticObjectId() {
      return streamSemanticObjectId;
    }

    /** @return stable component identity */
    public String getComponentId() {
      return componentId;
    }

    /** @return source component name */
    public String getComponentName() {
      return componentName;
    }

    /** @return component mass-flow result */
    public double getResultValue() {
      return resultValue;
    }

    /** @return explicit result unit */
    public String getResultUnit() {
      return resultUnit;
    }

    /** @return explicit quantity basis */
    public String getQuantityBasis() {
      return quantityBasis;
    }

    /** @return source calculation or register reference */
    public String getSourceReference() {
      return sourceReference;
    }

    /** @return calculation or data lineage description */
    public String getProvenance() {
      return provenance;
    }

    /** @return review evidence state */
    public EvidenceState getEvidenceState() {
      return evidenceState;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("balanceId", balanceId);
      result.put("streamSemanticObjectId", streamSemanticObjectId);
      result.put("componentId", componentId);
      result.put("componentName", componentName);
      if (Double.isFinite(resultValue)) {
        result.put("resultValue", Double.valueOf(resultValue));
      } else {
        result.put("resultValue", null);
        result.put("nonFiniteResult", nonFiniteLabel(resultValue));
      }
      result.put("resultUnit", resultUnit);
      result.put("quantityBasis", quantityBasis);
      result.put("sourceReference", sourceReference);
      result.put("provenance", provenance);
      result.put("evidenceState", evidenceState.name());
      return result;
    }
  }

  /** One aggregate component balance result. */
  public static final class ComponentBalance implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String balanceId;
    private final String componentId;
    private final String componentName;
    private final int boundaryCount;
    private final int suppliedBoundaryCount;
    private final double inletMassFlow;
    private final double outletMassFlow;
    private final double massResidual;
    private final double relativeMassResidual;
    private final boolean complete;

    private ComponentBalance(String balanceId, String componentId, String componentName, Accumulator accumulator) {
      this.balanceId = balanceId;
      this.componentId = componentId;
      this.componentName = componentName;
      this.boundaryCount = accumulator.boundaryCount;
      this.suppliedBoundaryCount = accumulator.suppliedBoundaryCount;
      this.inletMassFlow = accumulator.inletMassFlow;
      this.outletMassFlow = accumulator.outletMassFlow;
      this.massResidual = inletMassFlow - outletMassFlow;
      double scale = Math.max(Math.abs(inletMassFlow), Math.abs(outletMassFlow));
      this.relativeMassResidual = scale == 0.0 ? 0.0 : massResidual / scale;
      this.complete = accumulator.complete && boundaryCount == suppliedBoundaryCount;
    }

    /** @return stable balance identity */
    public String getBalanceId() {
      return balanceId;
    }

    /** @return stable component identity */
    public String getComponentId() {
      return componentId;
    }

    /** @return source component name */
    public String getComponentName() {
      return componentName;
    }

    /** @return number of declared boundary streams */
    public int getBoundaryCount() {
      return boundaryCount;
    }

    /** @return number of boundary streams with one usable component value */
    public int getSuppliedBoundaryCount() {
      return suppliedBoundaryCount;
    }

    /** @return inlet component mass flow in kg/s */
    public double getInletMassFlow() {
      return inletMassFlow;
    }

    /** @return outlet component mass flow in kg/s */
    public double getOutletMassFlow() {
      return outletMassFlow;
    }

    /** @return inlet minus outlet component mass flow in kg/s */
    public double getMassResidual() {
      return massResidual;
    }

    /** @return residual divided by the larger absolute inlet or outlet total */
    public double getRelativeMassResidual() {
      return relativeMassResidual;
    }

    /** @return true when every declared boundary has one usable value */
    public boolean isComplete() {
      return complete;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("balanceId", balanceId);
      result.put("componentId", componentId);
      result.put("componentName", componentName);
      result.put("boundaryCount", Integer.valueOf(boundaryCount));
      result.put("suppliedBoundaryCount", Integer.valueOf(suppliedBoundaryCount));
      result.put("inletMassFlow", Double.valueOf(inletMassFlow));
      result.put("outletMassFlow", Double.valueOf(outletMassFlow));
      result.put("massResidual", Double.valueOf(massResidual));
      result.put("relativeMassResidual", Double.valueOf(relativeMassResidual));
      result.put("massFlowUnit", "kg/s");
      result.put("complete", Boolean.valueOf(complete));
      return result;
    }
  }

  /** One structured component-balance diagnostic. */
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

  private static final class Accumulator {
    private int boundaryCount;
    private int suppliedBoundaryCount;
    private double inletMassFlow;
    private double outletMassFlow;
    private boolean complete = true;
  }

  private final String documentSetId;
  private final String sourceGraphFingerprint;
  private final String designCaseId;
  private final String sourceBalanceTableFingerprint;
  private final List<ComponentFlow> componentFlows;
  private final List<ComponentBalance> componentBalances;
  private final List<Diagnostic> diagnostics;

  private EngineeringDiagramComponentBalanceTable(EngineeringDiagramBalanceTable balanceTable,
      List<ComponentFlow> componentFlows, List<ComponentBalance> componentBalances, List<Diagnostic> diagnostics) {
    this.documentSetId = balanceTable.getDocumentSetId();
    this.sourceGraphFingerprint = balanceTable.getSourceGraphFingerprint();
    this.designCaseId = balanceTable.getDesignCaseId();
    this.sourceBalanceTableFingerprint = balanceTable.toMap().get("fingerprint").toString();
    this.componentFlows = Collections.unmodifiableList(new ArrayList<ComponentFlow>(componentFlows));
    this.componentBalances = Collections.unmodifiableList(new ArrayList<ComponentBalance>(componentBalances));
    this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
  }

  /**
   * Aggregates explicit component mass flows against an existing explicit-boundary balance table.
   *
   * @param balanceTable governed explicit-boundary balance table
   * @param componentFlows explicit component values for every component and boundary stream
   * @return immutable component-resolved balance table
   * @throws IllegalArgumentException if either argument is null or the values contain null
   */
  public static EngineeringDiagramComponentBalanceTable fromBalanceTable(EngineeringDiagramBalanceTable balanceTable,
      List<ComponentFlow> componentFlows) {
    if (balanceTable == null) {
      throw new IllegalArgumentException("balanceTable must not be null");
    }
    if (componentFlows == null) {
      throw new IllegalArgumentException("componentFlows must not be null");
    }
    List<ComponentFlow> sortedFlows = new ArrayList<ComponentFlow>(componentFlows);
    if (containsNull(sortedFlows)) {
      throw new IllegalArgumentException("componentFlows must not contain null");
    }
    Collections.sort(sortedFlows, componentFlowComparator());
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    if (!balanceTable.isValid()) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "COMPONENT_BALANCE_SOURCE_INVALID",
          "The source explicit-boundary balance table contains error-severity diagnostics", ""));
    }
    if (sortedFlows.isEmpty()) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "COMPONENT_FLOW_NOT_DECLARED",
          "At least one explicit component mass-flow value is required", ""));
    }

    Map<String, Boundary> boundariesByKey = new LinkedHashMap<String, Boundary>();
    Map<String, List<Boundary>> boundariesByBalance = new LinkedHashMap<String, List<Boundary>>();
    for (Boundary boundary : balanceTable.getBoundaries()) {
      String key = boundaryKey(boundary.getBalanceId(), boundary.getStreamSemanticObjectId());
      if (!boundariesByKey.containsKey(key)) {
        boundariesByKey.put(key, boundary);
      }
      List<Boundary> balanceBoundaries = boundariesByBalance.get(boundary.getBalanceId());
      if (balanceBoundaries == null) {
        balanceBoundaries = new ArrayList<Boundary>();
        boundariesByBalance.put(boundary.getBalanceId(), balanceBoundaries);
      }
      balanceBoundaries.add(boundary);
    }

    Map<String, ComponentFlow> validFlowsByKey = new LinkedHashMap<String, ComponentFlow>();
    Map<String, Set<String>> componentsByBalance = new LinkedHashMap<String, Set<String>>();
    Map<String, String> componentNamesByKey = new LinkedHashMap<String, String>();
    for (ComponentFlow flow : sortedFlows) {
      Boundary boundary = boundariesByKey.get(boundaryKey(flow.getBalanceId(), flow.getStreamSemanticObjectId()));
      if (boundary == null) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "COMPONENT_FLOW_UNKNOWN_BOUNDARY",
            "A component value references a stream that is not assigned to the named balance",
            flow.getStreamSemanticObjectId()));
        continue;
      }
      String flowKey = componentFlowKey(flow.getBalanceId(), flow.getStreamSemanticObjectId(), flow.getComponentId());
      if (validFlowsByKey.containsKey(flowKey)) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "COMPONENT_FLOW_DUPLICATE",
            "Only one component value may be supplied for a balance, stream, and component", flowKey));
        continue;
      }
      if (!validFlow(flow)) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "COMPONENT_FLOW_VALUE_INVALID",
            "Component mass flow must be finite, non-negative, in kg/s, and on a COMPONENT_MASS basis", flowKey));
        continue;
      }
      String componentKey = flow.getBalanceId() + "|" + flow.getComponentId();
      String existingName = componentNamesByKey.get(componentKey);
      if (existingName != null && !existingName.equals(flow.getComponentName())) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "COMPONENT_NAME_CONFLICT",
            "One stable component identity has conflicting source component names", componentKey));
        continue;
      }
      componentNamesByKey.put(componentKey, flow.getComponentName());
      validFlowsByKey.put(flowKey, flow);
      Set<String> components = componentsByBalance.get(flow.getBalanceId());
      if (components == null) {
        components = new LinkedHashSet<String>();
        componentsByBalance.put(flow.getBalanceId(), components);
      }
      components.add(flow.getComponentId());
    }

    List<ComponentBalance> balances = new ArrayList<ComponentBalance>();
    for (Map.Entry<String, List<Boundary>> entry : boundariesByBalance.entrySet()) {
      String balanceId = entry.getKey();
      Set<String> components = componentsByBalance.get(balanceId);
      if (components == null || components.isEmpty()) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "COMPONENT_BALANCE_COMPONENTS_MISSING",
            "The declared balance has no usable component mass-flow values", balanceId));
        continue;
      }
      List<String> sortedComponents = new ArrayList<String>(components);
      Collections.sort(sortedComponents);
      for (String componentId : sortedComponents) {
        Accumulator accumulator = new Accumulator();
        accumulator.boundaryCount = entry.getValue().size();
        for (Boundary boundary : entry.getValue()) {
          String flowKey = componentFlowKey(balanceId, boundary.getStreamSemanticObjectId(), componentId);
          ComponentFlow flow = validFlowsByKey.get(flowKey);
          if (flow == null) {
            accumulator.complete = false;
            diagnostics.add(new Diagnostic(Severity.ERROR, "COMPONENT_FLOW_MISSING",
                "Every declared component requires an explicit zero or non-zero value on every boundary stream",
                flowKey));
            continue;
          }
          accumulator.suppliedBoundaryCount++;
          if (boundary.getDirection() == Direction.INLET) {
            accumulator.inletMassFlow += flow.getResultValue();
          } else {
            accumulator.outletMassFlow += flow.getResultValue();
          }
        }
        String componentName = componentNamesByKey.get(balanceId + "|" + componentId);
        balances.add(new ComponentBalance(balanceId, componentId, componentName, accumulator));
      }
    }
    Collections.sort(balances, componentBalanceComparator());
    addMassCoverageDiagnostics(balanceTable, balances, diagnostics);
    Collections.sort(diagnostics, diagnosticComparator());
    return new EngineeringDiagramComponentBalanceTable(balanceTable, sortedFlows, balances, diagnostics);
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

  /** @return deterministic fingerprint of the source balance-table representation */
  public String getSourceBalanceTableFingerprint() {
    return sourceBalanceTableFingerprint;
  }

  /** @return immutable explicit component values in deterministic order */
  public List<ComponentFlow> getComponentFlows() {
    return Collections.unmodifiableList(new ArrayList<ComponentFlow>(componentFlows));
  }

  /** @return immutable component balances in deterministic order */
  public List<ComponentBalance> getComponentBalances() {
    return Collections.unmodifiableList(new ArrayList<ComponentBalance>(componentBalances));
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
    List<Map<String, Object>> flowMaps = new ArrayList<Map<String, Object>>();
    for (ComponentFlow flow : componentFlows) {
      flowMaps.add(flow.toMap());
    }
    result.put("componentFlows", flowMaps);
    List<Map<String, Object>> balanceMaps = new ArrayList<Map<String, Object>>();
    for (ComponentBalance balance : componentBalances) {
      balanceMaps.add(balance.toMap());
    }
    result.put("componentBalances", balanceMaps);
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

  private static boolean validFlow(ComponentFlow flow) {
    return Double.isFinite(flow.getResultValue()) && flow.getResultValue() >= 0.0 && "kg/s".equals(flow.getResultUnit())
        && "COMPONENT_MASS".equals(flow.getQuantityBasis());
  }

  private static String nonFiniteLabel(double value) {
    if (Double.isNaN(value)) {
      return "NaN";
    }
    return value > 0.0 ? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY";
  }

  private static void addMassCoverageDiagnostics(EngineeringDiagramBalanceTable balanceTable,
      List<ComponentBalance> componentBalances, List<Diagnostic> diagnostics) {
    Map<String, double[]> componentTotalsByBalance = new LinkedHashMap<String, double[]>();
    for (ComponentBalance componentBalance : componentBalances) {
      double[] totals = componentTotalsByBalance.get(componentBalance.getBalanceId());
      if (totals == null) {
        totals = new double[2];
        componentTotalsByBalance.put(componentBalance.getBalanceId(), totals);
      }
      totals[0] += componentBalance.getInletMassFlow();
      totals[1] += componentBalance.getOutletMassFlow();
    }
    for (Balance balance : balanceTable.getBalances()) {
      double[] componentTotals = componentTotalsByBalance.get(balance.getBalanceId());
      if (componentTotals == null) {
        continue;
      }
      if (materiallyDifferent(balance.getInletMassFlow(), componentTotals[0])
          || materiallyDifferent(balance.getOutletMassFlow(), componentTotals[1])) {
        diagnostics.add(new Diagnostic(Severity.WARNING, "COMPONENT_TOTAL_MASS_MISMATCH",
            "Summed component inlet or outlet mass flow differs from the source total-mass balance; no project "
                + "tolerance was inferred",
            balance.getBalanceId()));
      }
    }
  }

  private static boolean materiallyDifferent(double expected, double actual) {
    double scale = Math.max(Math.max(Math.abs(expected), Math.abs(actual)), 1.0);
    return Math.abs(expected - actual) > 16.0 * Math.ulp(scale);
  }

  private static String boundaryKey(String balanceId, String streamId) {
    return balanceId + "|" + streamId;
  }

  private static String componentFlowKey(String balanceId, String streamId, String componentId) {
    return balanceId + "|" + streamId + "|" + componentId;
  }

  private static Comparator<ComponentFlow> componentFlowComparator() {
    return new Comparator<ComponentFlow>() {
      @Override
      public int compare(ComponentFlow left, ComponentFlow right) {
        int balanceOrder = left.getBalanceId().compareTo(right.getBalanceId());
        if (balanceOrder != 0) {
          return balanceOrder;
        }
        int componentOrder = left.getComponentId().compareTo(right.getComponentId());
        if (componentOrder != 0) {
          return componentOrder;
        }
        int streamOrder = left.getStreamSemanticObjectId().compareTo(right.getStreamSemanticObjectId());
        if (streamOrder != 0) {
          return streamOrder;
        }
        int nameOrder = left.getComponentName().compareTo(right.getComponentName());
        if (nameOrder != 0) {
          return nameOrder;
        }
        int valueOrder = Double.compare(left.getResultValue(), right.getResultValue());
        if (valueOrder != 0) {
          return valueOrder;
        }
        int unitOrder = left.getResultUnit().compareTo(right.getResultUnit());
        if (unitOrder != 0) {
          return unitOrder;
        }
        int basisOrder = left.getQuantityBasis().compareTo(right.getQuantityBasis());
        if (basisOrder != 0) {
          return basisOrder;
        }
        int sourceOrder = left.getSourceReference().compareTo(right.getSourceReference());
        if (sourceOrder != 0) {
          return sourceOrder;
        }
        int provenanceOrder = left.getProvenance().compareTo(right.getProvenance());
        if (provenanceOrder != 0) {
          return provenanceOrder;
        }
        return left.getEvidenceState().name().compareTo(right.getEvidenceState().name());
      }
    };
  }

  private static Comparator<ComponentBalance> componentBalanceComparator() {
    return new Comparator<ComponentBalance>() {
      @Override
      public int compare(ComponentBalance left, ComponentBalance right) {
        int balanceOrder = left.getBalanceId().compareTo(right.getBalanceId());
        return balanceOrder != 0 ? balanceOrder : left.getComponentId().compareTo(right.getComponentId());
      }
    };
  }

  private static Comparator<Diagnostic> diagnosticComparator() {
    return new Comparator<Diagnostic>() {
      @Override
      public int compare(Diagnostic left, Diagnostic right) {
        int subjectOrder = left.getSubjectId().compareTo(right.getSubjectId());
        if (subjectOrder != 0) {
          return subjectOrder;
        }
        int codeOrder = left.getCode().compareTo(right.getCode());
        return codeOrder != 0 ? codeOrder : left.getMessage().compareTo(right.getMessage());
      }
    };
  }

  private static boolean containsNull(List<?> values) {
    for (Object value : values) {
      if (value == null) {
        return true;
      }
    }
    return false;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static String fingerprint(Map<String, Object> value) {
    Map<String, Object> copy = new LinkedHashMap<String, Object>(value);
    copy.remove("fingerprint");
    byte[] bytes = new GsonBuilder().create().toJson(copy).getBytes(StandardCharsets.UTF_8);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(bytes);
      StringBuilder result = new StringBuilder();
      for (byte element : hash) {
        result.append(String.format("%02x", Integer.valueOf(element & 0xff)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
