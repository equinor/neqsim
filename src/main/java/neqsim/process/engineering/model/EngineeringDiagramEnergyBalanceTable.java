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
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Severity;

/**
 * Immutable, deterministic energy-balance closure projection.
 *
 * <p>
 * The projection adds explicit heat-transfer and shaft-work ports to the stream enthalpy terms in an
 * {@link EngineeringDiagramBalanceTable}. Port direction is always relative to the declared control volume. Values are
 * non-negative energy rates, so their sign is never inferred from an equipment convention or drawing topology. Every
 * declared port requires one explicit zero or non-zero flow value.
 * </p>
 *
 * <p>
 * This class calculates first-law residuals only. It does not read live equipment duties, infer missing ports, apply
 * project tolerances, reconcile values, or promote review evidence to engineering approval.
 * </p>
 */
public final class EngineeringDiagramEnergyBalanceTable implements Serializable {
  private static final long serialVersionUID = 1000L;
  public static final String SCHEMA_VERSION = "neqsim_engineering_diagram_energy_balance_table.v1";

  /** Type of energy crossing a declared control-volume port. */
  public enum EnergyKind {
    /** Heat transfer across the control-volume boundary. */
    HEAT_TRANSFER,
    /** Shaft work across the control-volume boundary. */
    SHAFT_WORK
  }

  /** Explicit direction of an energy rate relative to the control volume. */
  public enum EnergyDirection {
    /** Energy enters the control volume. */
    INTO_CONTROL_VOLUME,
    /** Energy leaves the control volume. */
    OUT_OF_CONTROL_VOLUME
  }

  /** One explicit energy port assigned to a named balance. */
  public static final class EnergyPort implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String balanceId;
    private final String equipmentSemanticObjectId;
    private final String portSemanticObjectId;
    private final EnergyKind energyKind;
    private final EnergyDirection direction;
    private final String sourceReference;
    private final EvidenceState evidenceState;

    /**
     * Creates one explicit heat-transfer or shaft-work port.
     *
     * @param balanceId stable balance identity
     * @param equipmentSemanticObjectId canonical equipment or control-volume identity
     * @param portSemanticObjectId stable port identity, distinct for parallel energy connections
     * @param energyKind kind of energy crossing the port
     * @param direction explicit direction relative to the control volume
     * @param sourceReference source or register reference for the port declaration
     * @param evidenceState review evidence state
     * @throws IllegalArgumentException if an identity, source, kind, direction, or evidence state is missing
     */
    public EnergyPort(String balanceId, String equipmentSemanticObjectId, String portSemanticObjectId,
        EnergyKind energyKind, EnergyDirection direction, String sourceReference, EvidenceState evidenceState) {
      this.balanceId = requireText(balanceId, "balanceId");
      this.equipmentSemanticObjectId = requireText(equipmentSemanticObjectId, "equipmentSemanticObjectId");
      this.portSemanticObjectId = requireText(portSemanticObjectId, "portSemanticObjectId");
      if (energyKind == null) {
        throw new IllegalArgumentException("energyKind must not be null");
      }
      this.energyKind = energyKind;
      if (direction == null) {
        throw new IllegalArgumentException("direction must not be null");
      }
      this.direction = direction;
      this.sourceReference = requireText(sourceReference, "sourceReference");
      if (evidenceState == null) {
        throw new IllegalArgumentException("evidenceState must not be null");
      }
      this.evidenceState = evidenceState;
    }

    /** @return stable balance identity */
    public String getBalanceId() {
      return balanceId;
    }

    /** @return canonical equipment or control-volume identity */
    public String getEquipmentSemanticObjectId() {
      return equipmentSemanticObjectId;
    }

    /** @return stable energy-port identity */
    public String getPortSemanticObjectId() {
      return portSemanticObjectId;
    }

    /** @return kind of energy crossing the port */
    public EnergyKind getEnergyKind() {
      return energyKind;
    }

    /** @return explicit direction relative to the control volume */
    public EnergyDirection getDirection() {
      return direction;
    }

    /** @return source or register reference */
    public String getSourceReference() {
      return sourceReference;
    }

    /** @return review evidence state */
    public EvidenceState getEvidenceState() {
      return evidenceState;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("balanceId", balanceId);
      result.put("equipmentSemanticObjectId", equipmentSemanticObjectId);
      result.put("portSemanticObjectId", portSemanticObjectId);
      result.put("energyKind", energyKind.name());
      result.put("direction", direction.name());
      result.put("sourceReference", sourceReference);
      result.put("evidenceState", evidenceState.name());
      return result;
    }
  }

  /** One explicit energy-rate value on a declared energy port. */
  public static final class EnergyFlow implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String balanceId;
    private final String portSemanticObjectId;
    private final double resultValue;
    private final String resultUnit;
    private final String quantityBasis;
    private final String sourceReference;
    private final String provenance;
    private final EvidenceState evidenceState;

    /**
     * Creates one explicit energy-rate value.
     *
     * @param balanceId stable balance identity
     * @param portSemanticObjectId stable declared energy-port identity
     * @param resultValue non-negative energy rate
     * @param resultUnit explicit result unit; aggregation requires {@code W}
     * @param quantityBasis explicit quantity basis; aggregation requires {@code ENERGY_RATE}
     * @param sourceReference source calculation or register reference
     * @param provenance calculation or data lineage description
     * @param evidenceState review evidence state
     * @throws IllegalArgumentException if an identity, source, provenance, or evidence state is missing
     */
    public EnergyFlow(String balanceId, String portSemanticObjectId, double resultValue, String resultUnit,
        String quantityBasis, String sourceReference, String provenance, EvidenceState evidenceState) {
      this.balanceId = requireText(balanceId, "balanceId");
      this.portSemanticObjectId = requireText(portSemanticObjectId, "portSemanticObjectId");
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

    /** @return stable declared energy-port identity */
    public String getPortSemanticObjectId() {
      return portSemanticObjectId;
    }

    /** @return energy-rate result */
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
      result.put("portSemanticObjectId", portSemanticObjectId);
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

  /** One complete or fail-visible energy-balance result. */
  public static final class EnergyBalance implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String balanceId;
    private final int declaredPortCount;
    private final int suppliedFlowCount;
    private final double inletStreamEnthalpyFlow;
    private final double outletStreamEnthalpyFlow;
    private final double heatTransferIntoControlVolume;
    private final double heatTransferOutOfControlVolume;
    private final double shaftWorkIntoControlVolume;
    private final double shaftWorkOutOfControlVolume;
    private final double totalEnergyIn;
    private final double totalEnergyOut;
    private final double energyResidual;
    private final double relativeEnergyResidual;
    private final boolean complete;

    private EnergyBalance(Balance source, Accumulator accumulator) {
      this.balanceId = source.getBalanceId();
      this.declaredPortCount = accumulator.declaredPortCount;
      this.suppliedFlowCount = accumulator.suppliedFlowCount;
      this.inletStreamEnthalpyFlow = source.getInletStreamEnthalpyFlow();
      this.outletStreamEnthalpyFlow = source.getOutletStreamEnthalpyFlow();
      this.heatTransferIntoControlVolume = accumulator.heatTransferIntoControlVolume;
      this.heatTransferOutOfControlVolume = accumulator.heatTransferOutOfControlVolume;
      this.shaftWorkIntoControlVolume = accumulator.shaftWorkIntoControlVolume;
      this.shaftWorkOutOfControlVolume = accumulator.shaftWorkOutOfControlVolume;
      this.totalEnergyIn = inletStreamEnthalpyFlow + heatTransferIntoControlVolume + shaftWorkIntoControlVolume;
      this.totalEnergyOut = outletStreamEnthalpyFlow + heatTransferOutOfControlVolume + shaftWorkOutOfControlVolume;
      this.energyResidual = totalEnergyIn - totalEnergyOut;
      double scale = Math.max(Math.abs(totalEnergyIn), Math.abs(totalEnergyOut));
      this.relativeEnergyResidual = scale == 0.0 ? 0.0 : energyResidual / scale;
      this.complete = accumulator.complete && source.isStreamEnthalpyFlowComplete() && declaredPortCount > 0
          && declaredPortCount == suppliedFlowCount;
    }

    /** @return stable balance identity */
    public String getBalanceId() {
      return balanceId;
    }

    /** @return number of explicitly declared energy ports */
    public int getDeclaredPortCount() {
      return declaredPortCount;
    }

    /** @return number of declared ports with one usable energy-rate value */
    public int getSuppliedFlowCount() {
      return suppliedFlowCount;
    }

    /** @return inlet stream enthalpy flow in W */
    public double getInletStreamEnthalpyFlow() {
      return inletStreamEnthalpyFlow;
    }

    /** @return outlet stream enthalpy flow in W */
    public double getOutletStreamEnthalpyFlow() {
      return outletStreamEnthalpyFlow;
    }

    /** @return heat transfer into the control volume in W */
    public double getHeatTransferIntoControlVolume() {
      return heatTransferIntoControlVolume;
    }

    /** @return heat transfer out of the control volume in W */
    public double getHeatTransferOutOfControlVolume() {
      return heatTransferOutOfControlVolume;
    }

    /** @return shaft work into the control volume in W */
    public double getShaftWorkIntoControlVolume() {
      return shaftWorkIntoControlVolume;
    }

    /** @return shaft work out of the control volume in W */
    public double getShaftWorkOutOfControlVolume() {
      return shaftWorkOutOfControlVolume;
    }

    /** @return total inlet stream enthalpy, heat transfer, and shaft work in W */
    public double getTotalEnergyIn() {
      return totalEnergyIn;
    }

    /** @return total outlet stream enthalpy, heat transfer, and shaft work in W */
    public double getTotalEnergyOut() {
      return totalEnergyOut;
    }

    /** @return total energy input minus total energy output in W */
    public double getEnergyResidual() {
      return energyResidual;
    }

    /** @return energy residual divided by the larger absolute inlet or outlet total */
    public double getRelativeEnergyResidual() {
      return relativeEnergyResidual;
    }

    /** @return true when source stream terms and all declared energy-port flows are complete */
    public boolean isComplete() {
      return complete;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("balanceId", balanceId);
      result.put("declaredPortCount", Integer.valueOf(declaredPortCount));
      result.put("suppliedFlowCount", Integer.valueOf(suppliedFlowCount));
      result.put("inletStreamEnthalpyFlow", Double.valueOf(inletStreamEnthalpyFlow));
      result.put("outletStreamEnthalpyFlow", Double.valueOf(outletStreamEnthalpyFlow));
      result.put("heatTransferIntoControlVolume", Double.valueOf(heatTransferIntoControlVolume));
      result.put("heatTransferOutOfControlVolume", Double.valueOf(heatTransferOutOfControlVolume));
      result.put("shaftWorkIntoControlVolume", Double.valueOf(shaftWorkIntoControlVolume));
      result.put("shaftWorkOutOfControlVolume", Double.valueOf(shaftWorkOutOfControlVolume));
      result.put("totalEnergyIn", Double.valueOf(totalEnergyIn));
      result.put("totalEnergyOut", Double.valueOf(totalEnergyOut));
      result.put("energyResidual", Double.valueOf(energyResidual));
      result.put("relativeEnergyResidual", Double.valueOf(relativeEnergyResidual));
      result.put("energyFlowUnit", "W");
      result.put("complete", Boolean.valueOf(complete));
      return result;
    }
  }

  /** One structured energy-balance diagnostic. */
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
    private int declaredPortCount;
    private int suppliedFlowCount;
    private double heatTransferIntoControlVolume;
    private double heatTransferOutOfControlVolume;
    private double shaftWorkIntoControlVolume;
    private double shaftWorkOutOfControlVolume;
    private boolean complete = true;
  }

  private final String documentSetId;
  private final String sourceGraphFingerprint;
  private final String designCaseId;
  private final String sourceBalanceTableFingerprint;
  private final List<EnergyPort> energyPorts;
  private final List<EnergyFlow> energyFlows;
  private final List<EnergyBalance> energyBalances;
  private final List<Diagnostic> diagnostics;

  private EngineeringDiagramEnergyBalanceTable(EngineeringDiagramBalanceTable balanceTable,
      List<EnergyPort> energyPorts, List<EnergyFlow> energyFlows, List<EnergyBalance> energyBalances,
      List<Diagnostic> diagnostics) {
    this.documentSetId = balanceTable.getDocumentSetId();
    this.sourceGraphFingerprint = balanceTable.getSourceGraphFingerprint();
    this.designCaseId = balanceTable.getDesignCaseId();
    this.sourceBalanceTableFingerprint = balanceTable.toMap().get("fingerprint").toString();
    this.energyPorts = Collections.unmodifiableList(new ArrayList<EnergyPort>(energyPorts));
    this.energyFlows = Collections.unmodifiableList(new ArrayList<EnergyFlow>(energyFlows));
    this.energyBalances = Collections.unmodifiableList(new ArrayList<EnergyBalance>(energyBalances));
    this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
  }

  /**
   * Closes explicit heat-transfer and shaft-work terms against a source boundary balance table.
   *
   * @param balanceTable governed explicit-boundary balance table
   * @param energyPorts explicit heat-transfer and shaft-work ports for every balance
   * @param energyFlows one explicit energy-rate value for every declared port
   * @return immutable energy-balance closure table with structured diagnostics
   * @throws IllegalArgumentException if an argument is null or a list contains null
   */
  public static EngineeringDiagramEnergyBalanceTable fromBalanceTable(EngineeringDiagramBalanceTable balanceTable,
      List<EnergyPort> energyPorts, List<EnergyFlow> energyFlows) {
    if (balanceTable == null) {
      throw new IllegalArgumentException("balanceTable must not be null");
    }
    if (energyPorts == null) {
      throw new IllegalArgumentException("energyPorts must not be null");
    }
    if (energyFlows == null) {
      throw new IllegalArgumentException("energyFlows must not be null");
    }
    List<EnergyPort> sortedPorts = new ArrayList<EnergyPort>(energyPorts);
    List<EnergyFlow> sortedFlows = new ArrayList<EnergyFlow>(energyFlows);
    if (containsNull(sortedPorts)) {
      throw new IllegalArgumentException("energyPorts must not contain null");
    }
    if (containsNull(sortedFlows)) {
      throw new IllegalArgumentException("energyFlows must not contain null");
    }
    Collections.sort(sortedPorts, energyPortComparator());
    Collections.sort(sortedFlows, energyFlowComparator());

    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    if (!balanceTable.isValid()) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "ENERGY_BALANCE_SOURCE_INVALID",
          "The source explicit-boundary balance table contains error-severity diagnostics", ""));
    }
    if (sortedPorts.isEmpty()) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "ENERGY_PORT_NOT_DECLARED",
          "At least one explicit heat-transfer or shaft-work port is required", ""));
    }
    if (sortedFlows.isEmpty()) {
      diagnostics.add(new Diagnostic(Severity.ERROR, "ENERGY_FLOW_NOT_DECLARED",
          "At least one explicit energy-rate value is required", ""));
    }

    Map<String, Balance> balancesById = new LinkedHashMap<String, Balance>();
    for (Balance balance : balanceTable.getBalances()) {
      balancesById.put(balance.getBalanceId(), balance);
    }
    Map<String, EnergyPort> portsByKey = new LinkedHashMap<String, EnergyPort>();
    Map<String, List<EnergyPort>> portsByBalance = new LinkedHashMap<String, List<EnergyPort>>();
    Set<String> invalidBalances = new LinkedHashSet<String>();
    for (EnergyPort port : sortedPorts) {
      if (!balancesById.containsKey(port.getBalanceId())) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "ENERGY_PORT_UNKNOWN_BALANCE",
            "An energy port references a balance absent from the source balance table", port.getBalanceId()));
        continue;
      }
      String key = portKey(port.getBalanceId(), port.getPortSemanticObjectId());
      if (portsByKey.containsKey(key)) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "ENERGY_PORT_DUPLICATE",
            "A stable energy port may be declared only once for the same balance", key));
        invalidBalances.add(port.getBalanceId());
        continue;
      }
      portsByKey.put(key, port);
      List<EnergyPort> balancePorts = portsByBalance.get(port.getBalanceId());
      if (balancePorts == null) {
        balancePorts = new ArrayList<EnergyPort>();
        portsByBalance.put(port.getBalanceId(), balancePorts);
      }
      balancePorts.add(port);
    }

    Map<String, EnergyFlow> flowsByPort = new LinkedHashMap<String, EnergyFlow>();
    for (EnergyFlow flow : sortedFlows) {
      String key = portKey(flow.getBalanceId(), flow.getPortSemanticObjectId());
      if (!portsByKey.containsKey(key)) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "ENERGY_FLOW_UNKNOWN_PORT",
            "An energy-rate value references a port absent from the named balance", key));
        invalidBalances.add(flow.getBalanceId());
        continue;
      }
      if (flowsByPort.containsKey(key)) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "ENERGY_FLOW_DUPLICATE",
            "Only one energy-rate value may be supplied for a declared port", key));
        invalidBalances.add(flow.getBalanceId());
        continue;
      }
      if (!validFlow(flow)) {
        diagnostics.add(new Diagnostic(Severity.ERROR, "ENERGY_FLOW_VALUE_INVALID",
            "Energy flow must be finite, non-negative, in W, and on an ENERGY_RATE basis", key));
        invalidBalances.add(flow.getBalanceId());
        continue;
      }
      flowsByPort.put(key, flow);
    }

    List<EnergyBalance> balances = new ArrayList<EnergyBalance>();
    for (Balance source : balanceTable.getBalances()) {
      Accumulator accumulator = new Accumulator();
      accumulator.complete = balanceTable.isValid() && !invalidBalances.contains(source.getBalanceId());
      List<EnergyPort> balancePorts = portsByBalance.get(source.getBalanceId());
      if (balancePorts == null || balancePorts.isEmpty()) {
        accumulator.complete = false;
        diagnostics.add(new Diagnostic(Severity.ERROR, "ENERGY_PORT_MISSING_FOR_BALANCE",
            "Every source balance requires an explicit zero or non-zero energy-port declaration",
            source.getBalanceId()));
      } else {
        accumulator.declaredPortCount = balancePorts.size();
        for (EnergyPort port : balancePorts) {
          String key = portKey(port.getBalanceId(), port.getPortSemanticObjectId());
          EnergyFlow flow = flowsByPort.get(key);
          if (flow == null) {
            accumulator.complete = false;
            diagnostics.add(new Diagnostic(Severity.ERROR, "ENERGY_FLOW_MISSING",
                "Every declared energy port requires one explicit zero or non-zero flow value", key));
            continue;
          }
          accumulator.suppliedFlowCount++;
          addEnergyFlow(port, flow, accumulator);
        }
      }
      balances.add(new EnergyBalance(source, accumulator));
    }
    Collections.sort(diagnostics, diagnosticComparator());
    return new EngineeringDiagramEnergyBalanceTable(balanceTable, sortedPorts, sortedFlows, balances, diagnostics);
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

  /** @return immutable explicit energy ports in deterministic order */
  public List<EnergyPort> getEnergyPorts() {
    return Collections.unmodifiableList(new ArrayList<EnergyPort>(energyPorts));
  }

  /** @return immutable explicit energy-rate values in deterministic order */
  public List<EnergyFlow> getEnergyFlows() {
    return Collections.unmodifiableList(new ArrayList<EnergyFlow>(energyFlows));
  }

  /** @return immutable energy-balance results in deterministic order */
  public List<EnergyBalance> getEnergyBalances() {
    return Collections.unmodifiableList(new ArrayList<EnergyBalance>(energyBalances));
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
    List<Map<String, Object>> portMaps = new ArrayList<Map<String, Object>>();
    for (EnergyPort port : energyPorts) {
      portMaps.add(port.toMap());
    }
    result.put("energyPorts", portMaps);
    List<Map<String, Object>> flowMaps = new ArrayList<Map<String, Object>>();
    for (EnergyFlow flow : energyFlows) {
      flowMaps.add(flow.toMap());
    }
    result.put("energyFlows", flowMaps);
    List<Map<String, Object>> balanceMaps = new ArrayList<Map<String, Object>>();
    for (EnergyBalance balance : energyBalances) {
      balanceMaps.add(balance.toMap());
    }
    result.put("energyBalances", balanceMaps);
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

  private static void addEnergyFlow(EnergyPort port, EnergyFlow flow, Accumulator accumulator) {
    double value = flow.getResultValue();
    if (port.getEnergyKind() == EnergyKind.HEAT_TRANSFER) {
      if (port.getDirection() == EnergyDirection.INTO_CONTROL_VOLUME) {
        accumulator.heatTransferIntoControlVolume += value;
      } else {
        accumulator.heatTransferOutOfControlVolume += value;
      }
    } else if (port.getDirection() == EnergyDirection.INTO_CONTROL_VOLUME) {
      accumulator.shaftWorkIntoControlVolume += value;
    } else {
      accumulator.shaftWorkOutOfControlVolume += value;
    }
  }

  private static boolean validFlow(EnergyFlow flow) {
    return Double.isFinite(flow.getResultValue()) && flow.getResultValue() >= 0.0 && "W".equals(flow.getResultUnit())
        && "ENERGY_RATE".equals(flow.getQuantityBasis());
  }

  private static String nonFiniteLabel(double value) {
    if (Double.isNaN(value)) {
      return "NaN";
    }
    return value > 0.0 ? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY";
  }

  private static String portKey(String balanceId, String portId) {
    return balanceId + "|" + portId;
  }

  private static Comparator<EnergyPort> energyPortComparator() {
    return new Comparator<EnergyPort>() {
      @Override
      public int compare(EnergyPort left, EnergyPort right) {
        int order = left.getBalanceId().compareTo(right.getBalanceId());
        if (order != 0) {
          return order;
        }
        order = left.getPortSemanticObjectId().compareTo(right.getPortSemanticObjectId());
        if (order != 0) {
          return order;
        }
        order = left.getEquipmentSemanticObjectId().compareTo(right.getEquipmentSemanticObjectId());
        if (order != 0) {
          return order;
        }
        order = left.getEnergyKind().compareTo(right.getEnergyKind());
        if (order != 0) {
          return order;
        }
        order = left.getDirection().compareTo(right.getDirection());
        if (order != 0) {
          return order;
        }
        order = left.getSourceReference().compareTo(right.getSourceReference());
        if (order != 0) {
          return order;
        }
        return left.getEvidenceState().compareTo(right.getEvidenceState());
      }
    };
  }

  private static Comparator<EnergyFlow> energyFlowComparator() {
    return new Comparator<EnergyFlow>() {
      @Override
      public int compare(EnergyFlow left, EnergyFlow right) {
        int order = left.getBalanceId().compareTo(right.getBalanceId());
        if (order != 0) {
          return order;
        }
        order = left.getPortSemanticObjectId().compareTo(right.getPortSemanticObjectId());
        if (order != 0) {
          return order;
        }
        order = Double.compare(left.getResultValue(), right.getResultValue());
        if (order != 0) {
          return order;
        }
        order = left.getResultUnit().compareTo(right.getResultUnit());
        if (order != 0) {
          return order;
        }
        order = left.getQuantityBasis().compareTo(right.getQuantityBasis());
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

  private static Comparator<Diagnostic> diagnosticComparator() {
    return new Comparator<Diagnostic>() {
      @Override
      public int compare(Diagnostic left, Diagnostic right) {
        int order = left.getSubjectId().compareTo(right.getSubjectId());
        if (order != 0) {
          return order;
        }
        order = left.getCode().compareTo(right.getCode());
        return order != 0 ? order : left.getMessage().compareTo(right.getMessage());
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
