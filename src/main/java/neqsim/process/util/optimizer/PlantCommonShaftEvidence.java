package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorDriver;
import neqsim.process.equipment.energy.Gearbox;
import neqsim.process.equipment.stream.EnergyAllocation;
import neqsim.process.equipment.stream.EnergyNetworkReport;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.MechanicalShaft;

/**
 * Immutable, participant-complete evidence for a common-shaft compressor train.
 *
 * <p>
 * The adapter reads an already solved {@link MechanicalShaft}, the declared driver and gearbox, and each completed
 * compressor operating point. It does not run or retain any mutable equipment. Shaft speed, casing speed agreement,
 * requested shaft load, driver power, gearbox input power, output torque, power balance, and every casing map margin
 * remain separate evidence rows. Missing, unexpected, stale, non-finite, chartless, or unconverged evidence fails
 * closed.
 * </p>
 *
 * <p>
 * Torque is the mechanical identity {@code torque = power / angular speed}. Gearbox input power is calculated from the
 * configured gearbox efficiency, and driver speed from its configured output-to-input speed ratio. No efficiency,
 * torque limit, speed ratio, or map envelope is invented by this class.
 * </p>
 */
public final class PlantCommonShaftEvidence implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final String SCHEMA_VERSION = "1.0";
  private static final double MATCH_TOLERANCE = 1.0e-10;

  /** Overall evidence status. */
  public enum Status {
    /** Every registered observation is available. */
    AVAILABLE,
    /** At least one required observation is unavailable or inconsistent. */
    INCOMPLETE
  }

  /** Availability and validity of one casing operating point. */
  public enum CasingStatus {
    /** A complete casing and map observation is available. */
    AVAILABLE,
    /** The casing is explicitly unavailable and has verified zero shaft load. */
    OUT_OF_SERVICE,
    /** The casing observation is missing. */
    MISSING,
    /** The casing belongs to another calculation. */
    STALE,
    /** The casing calculation did not complete. */
    INCOMPLETE_CONVERGENCE,
    /** A required casing value is non-finite. */
    NON_FINITE_VALUE,
    /** No active compressor chart is available. */
    NO_CHART,
    /** Casing identity or shaft allocation metadata is inconsistent. */
    METADATA_MISMATCH,
    /** Reading the casing observation raised an exception. */
    EXCEPTION
  }

  /** Immutable evidence for one compressor casing. */
  public static final class CasingEvidence implements Serializable, Comparable<CasingEvidence> {
    private static final long serialVersionUID = 1L;
    private final String participantId;
    private final String equipmentName;
    private final CasingStatus status;
    private final double speedRpm;
    private final double shaftPowerKw;
    private final double torqueNm;
    private final boolean chartActive;
    private final boolean withinChart;
    private final double distanceToSurge;
    private final double distanceToStoneWall;
    private final double mapMargin;
    private final String limitingConstraint;
    private final String provenance;
    private final String diagnostic;

    private CasingEvidence(String participantId, String equipmentName, CasingStatus status, double speedRpm,
        double shaftPowerKw, boolean chartActive, boolean withinChart, double distanceToSurge,
        double distanceToStoneWall, String limitingConstraint, String provenance, String diagnostic) {
      this.participantId = PlantConstraintScope.requireText(participantId, "Casing participant id");
      this.equipmentName = PlantConstraintScope.requireText(equipmentName, "Casing equipment name");
      this.status = status;
      this.speedRpm = speedRpm;
      this.shaftPowerKw = shaftPowerKw;
      this.torqueNm = torque(shaftPowerKw, speedRpm);
      this.chartActive = chartActive;
      this.withinChart = withinChart;
      this.distanceToSurge = distanceToSurge;
      this.distanceToStoneWall = distanceToStoneWall;
      this.mapMargin = Math.min(distanceToSurge, distanceToStoneWall);
      this.limitingConstraint = PlantConstraintScope.safeText(limitingConstraint);
      this.provenance = PlantConstraintScope.safeText(provenance);
      this.diagnostic = PlantConstraintScope.safeText(diagnostic);
    }

    /** @return persistent shaft-network participant identity */
    public String getParticipantId() {
      return participantId;
    }

    /** @return stable casing equipment name */
    public String getEquipmentName() {
      return equipmentName;
    }

    /** @return casing evidence status */
    public CasingStatus getStatus() {
      return status;
    }

    /** @return casing speed in rpm, or NaN */
    public double getSpeedRpm() {
      return speedRpm;
    }

    /** @return absorbed casing shaft power in kW, or NaN */
    public double getShaftPowerKw() {
      return shaftPowerKw;
    }

    /** @return absorbed casing torque in N m, or NaN */
    public double getTorqueNm() {
      return torqueNm;
    }

    /** @return whether a compressor map was active */
    public boolean isChartActive() {
      return chartActive;
    }

    /** @return whether the solved point lies between the surge and stonewall limits */
    public boolean isWithinChart() {
      return withinChart;
    }

    /** @return dimensionless signed distance from surge */
    public double getDistanceToSurge() {
      return distanceToSurge;
    }

    /** @return dimensionless signed distance from stonewall */
    public double getDistanceToStoneWall() {
      return distanceToStoneWall;
    }

    /** @return minimum signed map distance, or NaN */
    public double getMapMargin() {
      return mapMargin;
    }

    /** @return casing-local limiting map constraint */
    public String getLimitingConstraint() {
      return limitingConstraint;
    }

    /** @return runtime evidence provenance */
    public String getProvenance() {
      return provenance;
    }

    /** @return fail-closed diagnostic, or empty */
    public String getDiagnostic() {
      return diagnostic;
    }

    /** @return true when the casing observation can participate in constraint evaluation */
    public boolean isUsable() {
      return status == CasingStatus.AVAILABLE || status == CasingStatus.OUT_OF_SERVICE;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(CasingEvidence other) {
      return participantId.compareTo(other.participantId);
    }
  }

  private static final class CasingInput {
    private final String participantId;
    private final Compressor compressor;
    private final boolean outOfService;

    private CasingInput(String participantId, Compressor compressor, boolean outOfService) {
      this.participantId = participantId;
      this.compressor = compressor;
      this.outOfService = outOfService;
    }
  }

  private final String calculationId;
  private final String shaftName;
  private final String driverParticipantId;
  private final String gearboxIdentity;
  private final String provenance;
  private final boolean convergenceComplete;
  private final Status status;
  private final List<CasingEvidence> casings;
  private final List<PlantConstraintDefinition> definitions;
  private final List<PlantConstraintSample> samples;
  private final List<String> diagnostics;
  private final double shaftSpeedRpm;
  private final double driverSpeedRpm;
  private final double totalCasingPowerKw;
  private final double totalShaftLoadKw;
  private final double gearboxInputPowerKw;
  private final double driverPowerLimitKw;
  private final double shaftTorqueNm;
  private final double maximumTorqueNm;
  private final double unmetPowerKw;

  private PlantCommonShaftEvidence(Builder builder) {
    calculationId = PlantConstraintScope.requireText(builder.calculationId, "Calculation id");
    provenance = PlantConstraintScope.requireText(builder.provenance, "Common-shaft provenance");
    shaftName = PlantConstraintScope.requireText(builder.shaft.getName(), "Mechanical shaft name");
    driverParticipantId = PlantConstraintScope.requireText(builder.driverParticipantId, "Driver participant id");
    gearboxIdentity = PlantConstraintScope.requireText(builder.gearboxIdentity, "Gearbox identity");
    convergenceComplete = builder.convergenceComplete;
    maximumTorqueNm = builder.maximumTorqueNm;
    List<String> findings = new ArrayList<String>();

    if (!convergenceComplete) {
      findings.add("INCOMPLETE_CONVERGENCE");
    }
    if (!builder.shaft.hasSolution() || builder.shaft.getLastReport() == null) {
      findings.add("SHAFT_SOLUTION_STALE_OR_MISSING");
    }
    if (builder.shaft.isTripped()) {
      findings.add("SHAFT_TRIPPED");
    }
    shaftSpeedRpm = builder.shaft.getSpeed();
    if (!Double.isFinite(shaftSpeedRpm) || shaftSpeedRpm <= 0.0) {
      findings.add("INVALID_SHAFT_SPEED");
    }
    if (!Double.isFinite(builder.speedToleranceRpm) || builder.speedToleranceRpm <= 0.0) {
      findings.add("INVALID_SPEED_TOLERANCE");
    }
    if (!Double.isFinite(builder.powerBalanceToleranceKw) || builder.powerBalanceToleranceKw <= 0.0) {
      findings.add("INVALID_POWER_BALANCE_TOLERANCE");
    }
    if (!Double.isFinite(maximumTorqueNm) || maximumTorqueNm <= 0.0) {
      findings.add("MISSING_MAXIMUM_TORQUE");
    }

    Map<String, EnergyAllocation> allocations = allocationMap(builder.shaft.getLastReport(), findings);
    double participantDemandW = 0.0;
    double participantSupplyW = 0.0;
    for (EnergyAllocation allocation : allocations.values()) {
      if (allocation.getDirection() == EnergyPortDirection.INPUT) {
        participantDemandW += allocation.getRequestedPower();
      } else if (allocation.getDirection() == EnergyPortDirection.OUTPUT) {
        participantSupplyW += allocation.getRequestedPower();
      }
    }
    List<CasingEvidence> captured = new ArrayList<CasingEvidence>();
    double casingTotal = 0.0;
    double maximumSpeedDeviation = 0.0;
    boolean casingTotalAvailable = true;
    for (CasingInput input : new TreeMap<String, CasingInput>(builder.casings).values()) {
      EnergyAllocation allocation = allocations.remove(input.participantId);
      CasingEvidence casing = captureCasing(input, allocation, calculationId, provenance);
      captured.add(casing);
      if (!casing.isUsable()) {
        findings.add(input.participantId + "=" + casing.getStatus().name() + diagnosticSuffix(casing.getDiagnostic()));
        casingTotalAvailable = false;
      } else {
        casingTotal += casing.getShaftPowerKw();
        if (casing.getStatus() != CasingStatus.OUT_OF_SERVICE) {
          maximumSpeedDeviation = Math.max(maximumSpeedDeviation, Math.abs(casing.getSpeedRpm() - shaftSpeedRpm));
        }
      }
    }

    EnergyAllocation driverAllocation = allocations.remove(driverParticipantId);
    if (driverAllocation == null || driverAllocation.getDirection() != EnergyPortDirection.OUTPUT) {
      findings.add("DRIVER_PARTICIPANT_MISSING_OR_NOT_OUTPUT");
    }
    for (EnergyAllocation allocation : allocations.values()) {
      findings.add(allocation.getParticipantId() + "=UNEXPECTED_SHAFT_PARTICIPANT");
    }
    Collections.sort(captured);
    casings = Collections.unmodifiableList(captured);
    totalCasingPowerKw = casingTotalAvailable ? casingTotal : Double.NaN;

    EnergyNetworkReport report = builder.shaft.getLastReport();
    double reportDemandKw = report == null ? Double.NaN : report.getRequestedDemand() / 1000.0;
    if (report != null && !approximatelyEqual(participantDemandW, report.getRequestedDemand())) {
      findings.add("UNREGISTERED_EXTERNAL_SHAFT_DEMAND");
    }
    if (report != null && !approximatelyEqual(participantSupplyW, report.getOfferedSupply())) {
      findings.add("UNREGISTERED_EXTERNAL_SHAFT_SUPPLY");
    }
    if (casingTotalAvailable && !approximatelyEqual(casingTotal, reportDemandKw)) {
      findings.add("CASING_TOTAL_MISMATCH expected=" + reportDemandKw + " captured=" + casingTotal);
    }
    totalShaftLoadKw = Double.isFinite(reportDemandKw) ? reportDemandKw + builder.shaft.getFrictionLoss() / 1000.0
        : Double.NaN;
    double driverOfferedPowerKw = driverAllocation == null ? Double.NaN : driverAllocation.getRequestedPower() / 1000.0;
    double reportUnmetPowerKw = report == null ? Double.NaN : report.getUnmetDemand() / 1000.0;
    unmetPowerKw = Double.isFinite(totalShaftLoadKw) && Double.isFinite(driverOfferedPowerKw)
        && Double.isFinite(reportUnmetPowerKw)
            ? Math.max(reportUnmetPowerKw, Math.max(0.0, totalShaftLoadKw - driverOfferedPowerKw))
            : Double.NaN;

    double ratio = builder.gearbox.getSpeedRatio();
    double efficiency = builder.gearbox.getEfficiency();
    driverSpeedRpm = Double.isFinite(shaftSpeedRpm) && Double.isFinite(ratio) && ratio > 0.0 ? shaftSpeedRpm / ratio
        : Double.NaN;
    gearboxInputPowerKw = Double.isFinite(totalShaftLoadKw) && Double.isFinite(efficiency) && efficiency > 0.0
        && Double.isFinite(builder.gearbox.getIdleLoss())
            ? totalShaftLoadKw / efficiency + builder.gearbox.getIdleLoss() / 1000.0
            : Double.NaN;
    driverPowerLimitKw = builder.driver.getMaxAvailablePowerAtSpeed(driverSpeedRpm);
    shaftTorqueNm = torque(totalShaftLoadKw, shaftSpeedRpm);

    if (!finiteNonNegative(totalShaftLoadKw) || !finiteNonNegative(gearboxInputPowerKw)
        || !Double.isFinite(driverPowerLimitKw) || driverPowerLimitKw <= 0.0 || !finiteNonNegative(unmetPowerKw)
        || !Double.isFinite(shaftTorqueNm)) {
      findings.add("NON_FINITE_SHAFT_DRIVER_OR_GEARBOX_EVIDENCE");
    }
    if (!Double.isFinite(builder.gearbox.getMaximumInputPower()) || builder.gearbox.getMaximumInputPower() <= 0.0) {
      findings.add("MISSING_GEARBOX_INPUT_POWER_LIMIT");
    }
    if (builder.gearbox.isTripped()) {
      findings.add("GEARBOX_TRIPPED");
    }
    if (!Double.isFinite(builder.shaft.getMaximumSpeed())) {
      findings.add("MISSING_SHAFT_SPEED_LIMIT");
    }
    if (!Double.isFinite(builder.driver.getMinSpeed()) || !Double.isFinite(builder.driver.getMaxSpeed())
        || builder.driver.getMinSpeed() < 0.0 || builder.driver.getMaxSpeed() <= builder.driver.getMinSpeed()) {
      findings.add("INVALID_DRIVER_SPEED_ENVELOPE");
    }

    List<PlantConstraintDefinition> capturedDefinitions = buildDefinitions(builder);
    List<PlantConstraintSample> capturedSamples = buildSamples(builder, capturedDefinitions, maximumSpeedDeviation,
        findings.isEmpty());
    definitions = Collections.unmodifiableList(capturedDefinitions);
    samples = Collections.unmodifiableList(capturedSamples);
    diagnostics = Collections.unmodifiableList(new ArrayList<String>(findings));
    status = findings.isEmpty() ? Status.AVAILABLE : Status.INCOMPLETE;
  }

  /**
   * Starts a callback-free common-shaft evidence builder.
   *
   * @param modelName stable process-model name
   * @param areaName stable process-area name
   * @param groupName stable common-shaft group name
   * @param calculationId exact completed calculation UUID string
   * @param shaft solved mechanical shaft to sample immediately
   * @param provenance source of the completed train state
   * @return new evidence builder
   */
  public static Builder builder(String modelName, String areaName, String groupName, String calculationId,
      MechanicalShaft shaft, String provenance) {
    return new Builder(modelName, areaName, groupName, calculationId, shaft, provenance);
  }

  private static Map<String, EnergyAllocation> allocationMap(EnergyNetworkReport report, List<String> diagnostics) {
    Map<String, EnergyAllocation> result = new TreeMap<String, EnergyAllocation>();
    if (report == null) {
      return result;
    }
    for (EnergyAllocation allocation : report.getAllocations()) {
      if (result.put(allocation.getParticipantId(), allocation) != null) {
        diagnostics.add(allocation.getParticipantId() + "=DUPLICATE_SHAFT_PARTICIPANT");
      }
    }
    return result;
  }

  private static CasingEvidence captureCasing(CasingInput input, EnergyAllocation allocation, String calculationId,
      String provenance) {
    if (input.compressor == null) {
      return unavailableCasing(input.participantId, input.participantId, CasingStatus.MISSING, provenance,
          "Compressor object is missing");
    }
    String equipmentName = input.compressor.getName();
    if (allocation == null || allocation.getDirection() != EnergyPortDirection.INPUT) {
      return unavailableCasing(input.participantId, equipmentName, CasingStatus.METADATA_MISMATCH, provenance,
          "Shaft input allocation is missing");
    }
    try {
      if (!input.compressor.solved()) {
        return unavailableCasing(input.participantId, equipmentName, CasingStatus.INCOMPLETE_CONVERGENCE, provenance,
            "Compressor is not solved");
      }
      if (input.compressor.getCalculationIdentifier() == null
          || !calculationId.equals(input.compressor.getCalculationIdentifier().toString())) {
        return unavailableCasing(input.participantId, equipmentName, CasingStatus.STALE, provenance,
            "Compressor calculation identity differs");
      }
      Map<String, Object> point = input.compressor.getOperatingPoint();
      double speed = number(point.get("speed_rpm"));
      double power = number(point.get("power_kW"));
      boolean chartActive = Boolean.TRUE.equals(point.get("chartActive"));
      boolean withinChart = Boolean.TRUE.equals(point.get("withinChart"));
      double surge = number(point.get("distanceToSurge"));
      double stonewall = number(point.get("distanceToStoneWall"));
      String limiting = String.valueOf(point.get("limitingConstraint"));
      if (!Double.isFinite(speed) || speed < 0.0 || !finiteNonNegative(power)) {
        return unavailableCasing(input.participantId, equipmentName, CasingStatus.NON_FINITE_VALUE, provenance,
            "Speed or shaft power is unavailable");
      }
      if (!approximatelyEqual(power, allocation.getRequestedPower() / 1000.0)) {
        return unavailableCasing(input.participantId, equipmentName, CasingStatus.METADATA_MISMATCH, provenance,
            "Compressor power differs from the shaft request");
      }
      if (input.outOfService) {
        if (power == 0.0 && allocation.getRequestedPower() == 0.0) {
          return new CasingEvidence(input.participantId, equipmentName, CasingStatus.OUT_OF_SERVICE, 0.0, 0.0, false,
              true, 0.0, 0.0, "out_of_service", provenance, "Explicitly unavailable with verified zero load");
        }
        return unavailableCasing(input.participantId, equipmentName, CasingStatus.METADATA_MISMATCH, provenance,
            "Out-of-service casing retained non-zero shaft load");
      }
      if (!chartActive) {
        return unavailableCasing(input.participantId, equipmentName, CasingStatus.NO_CHART, provenance,
            "No active casing map");
      }
      if (!Double.isFinite(surge) || !Double.isFinite(stonewall)) {
        return unavailableCasing(input.participantId, equipmentName, CasingStatus.NON_FINITE_VALUE, provenance,
            "Map margins are unavailable");
      }
      return new CasingEvidence(input.participantId, equipmentName, CasingStatus.AVAILABLE, speed, power, true,
          withinChart, surge, stonewall, limiting, provenance, "");
    } catch (RuntimeException ex) {
      return unavailableCasing(input.participantId, equipmentName, CasingStatus.EXCEPTION, provenance, safeMessage(ex));
    }
  }

  private static CasingEvidence unavailableCasing(String id, String name, CasingStatus status, String provenance,
      String diagnostic) {
    return new CasingEvidence(id, name, status, Double.NaN, Double.NaN, false, false, Double.NaN, Double.NaN,
        "unavailable", provenance, diagnostic);
  }

  private List<PlantConstraintDefinition> buildDefinitions(Builder builder) {
    List<PlantConstraintDefinition> result = new ArrayList<PlantConstraintDefinition>();
    PlantConstraintScope group = PlantConstraintScope.coupledGroup(builder.modelName, builder.areaName,
        builder.groupName);
    PlantConstraintDefinition.Builder speedAgreement = definition("common-speed-deviation", group, "rpm",
        "maximum absolute casing-to-shaft speed deviation", provenance)
        .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.COMMON_SETPOINT);
    for (CasingEvidence casing : casings) {
      speedAgreement.participant(PlantConstraintParticipant.direct(casing.getParticipantId(), "rpm",
          "maximum absolute casing-to-shaft speed deviation"));
    }
    result.add(speedAgreement.build());
    result.add(definition("shaft-maximum-speed", group, "rpm", "mechanical shaft speed", provenance).build());
    result.add(definition("shaft-power-balance", group, "kW", "unmet requested shaft load", provenance).build());
    result.add(definition("driver-speed-envelope", group, "rpm", "gearbox input and driver speed", provenance)
        .limitDirection(PlantConstraintDefinition.LimitDirection.RANGE).build());
    result.add(definition("driver-maximum-power", group, "kW", "driver output required at configured gearbox input",
        provenance).build());
    result.add(definition("gearbox-maximum-input-power", group, "kW",
        "gearbox input power including configured efficiency", provenance).build());
    result.add(definition("shaft-maximum-torque", group, "N m",
        "absorbed shaft load plus configured friction at shaft speed", provenance).build());
    for (CasingEvidence casing : casings) {
      result.add(definition("compressor-map-margin",
          PlantConstraintScope.equipment(builder.modelName, builder.areaName, casing.getEquipmentName()), "fraction",
          "minimum signed distance to surge or stonewall", casing.getProvenance())
          .limitDirection(PlantConstraintDefinition.LimitDirection.MINIMUM).build());
    }
    Collections.sort(result);
    return result;
  }

  private static PlantConstraintDefinition.Builder definition(String id, PlantConstraintScope scope, String unit,
      String basis, String provenance) {
    return PlantConstraintDefinition.builder(id, scope).category(PlantConstraintDefinition.Category.DESIGN)
        .severity(ConstraintSeverity.HARD).unit(unit).basis(basis).provenance(provenance).owner("rotating equipment")
        .calculationMethod("immutable common-shaft post-solve evidence");
  }

  private List<PlantConstraintSample> buildSamples(Builder builder, List<PlantConstraintDefinition> sourceDefinitions,
      double speedDeviation, boolean metadataComplete) {
    Map<String, PlantConstraintDefinition> byId = new LinkedHashMap<String, PlantConstraintDefinition>();
    for (PlantConstraintDefinition definition : sourceDefinitions) {
      byId.put(definition.getQualifiedId(), definition);
    }
    PlantConstraintScope group = PlantConstraintScope.coupledGroup(builder.modelName, builder.areaName,
        builder.groupName);
    List<PlantConstraintSample> result = new ArrayList<PlantConstraintSample>();
    result.add(maximumSample(byId.get(group.getStableId() + "#common-speed-deviation"), speedDeviation,
        builder.speedToleranceRpm, "completed casing speed observations", metadataComplete));
    result.add(maximumSample(byId.get(group.getStableId() + "#shaft-maximum-speed"), shaftSpeedRpm,
        builder.shaft.getMaximumSpeed(), "completed mechanical shaft state", metadataComplete));
    result.add(maximumSample(byId.get(group.getStableId() + "#shaft-power-balance"), unmetPowerKw,
        builder.powerBalanceToleranceKw, "solved mechanical shaft report", metadataComplete));
    result.add(rangeSample(byId.get(group.getStableId() + "#driver-speed-envelope"), driverSpeedRpm,
        builder.driver.getMinSpeed(), builder.driver.getMaxSpeed(), "configured driver envelope", metadataComplete));
    result.add(maximumSample(byId.get(group.getStableId() + "#driver-maximum-power"), gearboxInputPowerKw,
        driverPowerLimitKw, "configured driver curve", metadataComplete));
    result.add(maximumSample(byId.get(group.getStableId() + "#gearbox-maximum-input-power"), gearboxInputPowerKw,
        builder.gearbox.getMaximumInputPower() / 1000.0, "configured gearbox rating", metadataComplete));
    result.add(maximumSample(byId.get(group.getStableId() + "#shaft-maximum-torque"), shaftTorqueNm, maximumTorqueNm,
        "configured shaft train torque limit", metadataComplete));
    for (CasingEvidence casing : casings) {
      String qualifiedId = PlantConstraintScope
          .equipment(builder.modelName, builder.areaName, casing.getEquipmentName()).getStableId()
          + "#compressor-map-margin";
      result.add(
          minimumSample(byId.get(qualifiedId), casing.getMapMargin(), 0.0, casing.getProvenance(), casing.isUsable()));
    }
    Collections.sort(result,
        (first, second) -> first.getQualifiedConstraintId().compareTo(second.getQualifiedConstraintId()));
    return result;
  }

  private PlantConstraintSample maximumSample(PlantConstraintDefinition definition, double value, double limit,
      String sampleProvenance, boolean complete) {
    if (!complete || !Double.isFinite(value) || !Double.isFinite(limit) || limit <= 0.0) {
      return unavailableSample(definition, sampleProvenance);
    }
    double utilization = value / limit;
    return PlantConstraintSample.builder(definition.getQualifiedId(), calculationId).values(value, limit)
        .normalized(utilization, utilization - 1.0).physical(limit - value, Math.max(0.0, value - limit))
        .unit(definition.getUnit()).basis(definition.getBasis()).provenance(sampleProvenance).build();
  }

  private PlantConstraintSample minimumSample(PlantConstraintDefinition definition, double value, double limit,
      String sampleProvenance, boolean complete) {
    if (!complete || !Double.isFinite(value)) {
      return unavailableSample(definition, sampleProvenance);
    }
    double residual = limit - value;
    return PlantConstraintSample.builder(definition.getQualifiedId(), calculationId).values(value, limit)
        .normalized(1.0 + residual, residual).physical(value - limit, Math.max(0.0, limit - value))
        .unit(definition.getUnit()).basis(definition.getBasis()).provenance(sampleProvenance).build();
  }

  private PlantConstraintSample rangeSample(PlantConstraintDefinition definition, double value, double minimum,
      double maximum, String sampleProvenance, boolean complete) {
    if (!complete || !Double.isFinite(value) || !Double.isFinite(minimum) || !Double.isFinite(maximum)
        || maximum <= minimum) {
      return unavailableSample(definition, sampleProvenance);
    }
    double limit;
    double margin;
    double normalizedResidual;
    double scale = maximum - minimum;
    if (value < minimum) {
      limit = minimum;
      margin = value - minimum;
      normalizedResidual = (minimum - value) / scale;
    } else if (value > maximum) {
      limit = maximum;
      margin = maximum - value;
      normalizedResidual = (value - maximum) / scale;
    } else {
      double minimumMargin = value - minimum;
      double maximumMargin = maximum - value;
      limit = minimumMargin <= maximumMargin ? minimum : maximum;
      margin = Math.min(minimumMargin, maximumMargin);
      normalizedResidual = -margin / scale;
    }
    return PlantConstraintSample.builder(definition.getQualifiedId(), calculationId).values(value, limit)
        .normalized(1.0 + normalizedResidual, normalizedResidual).physical(margin, Math.max(0.0, -margin))
        .unit(definition.getUnit()).basis(definition.getBasis()).provenance(sampleProvenance).build();
  }

  private PlantConstraintSample unavailableSample(PlantConstraintDefinition definition, String sampleProvenance) {
    return PlantConstraintSample.builder(definition.getQualifiedId(), calculationId)
        .status(PlantConstraintSample.SampleStatus.NOT_CALCULABLE).unit(definition.getUnit())
        .basis(definition.getBasis()).provenance(sampleProvenance).diagnostic("Common-shaft evidence is incomplete")
        .build();
  }

  private static double torque(double powerKw, double speedRpm) {
    if (!finiteNonNegative(powerKw) || !Double.isFinite(speedRpm) || speedRpm <= 0.0) {
      return Double.NaN;
    }
    return powerKw * 1000.0 / (speedRpm * 2.0 * Math.PI / 60.0);
  }

  private static boolean finiteNonNegative(double value) {
    return Double.isFinite(value) && value >= 0.0;
  }

  private static double number(Object value) {
    return value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
  }

  private static boolean approximatelyEqual(double first, double second) {
    if (!Double.isFinite(first) || !Double.isFinite(second)) {
      return false;
    }
    double scale = Math.max(1.0, Math.max(Math.abs(first), Math.abs(second)));
    return Math.abs(first - second) <= MATCH_TOLERANCE * scale;
  }

  private static String safeMessage(RuntimeException exception) {
    return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
  }

  private static String diagnosticSuffix(String value) {
    return value == null || value.isEmpty() ? "" : ":" + value;
  }

  /** @return common-shaft evidence schema version */
  public String getSchemaVersion() {
    return SCHEMA_VERSION;
  }

  /** @return exact calculation identity */
  public String getCalculationId() {
    return calculationId;
  }

  /** @return mechanical shaft name */
  public String getShaftName() {
    return shaftName;
  }

  /** @return overall evidence status */
  public Status getStatus() {
    return status;
  }

  /** @return true when every required observation is available */
  public boolean isComplete() {
    return status == Status.AVAILABLE;
  }

  /** @return true when the complete common-shaft snapshot is feasible */
  public boolean isFeasible() {
    return isComplete() && toPlantUtilizationSnapshot().isFeasible();
  }

  /** @return immutable deterministic casing evidence */
  public List<CasingEvidence> getCasings() {
    return Collections.unmodifiableList(new ArrayList<CasingEvidence>(casings));
  }

  /** @return immutable definitions for registry integration */
  public List<PlantConstraintDefinition> getDefinitions() {
    return Collections.unmodifiableList(new ArrayList<PlantConstraintDefinition>(definitions));
  }

  /** @return immutable common plant samples */
  public List<PlantConstraintSample> getSamples() {
    return Collections.unmodifiableList(new ArrayList<PlantConstraintSample>(samples));
  }

  /** @return immutable fail-closed diagnostics */
  public List<String> getDiagnostics() {
    return Collections.unmodifiableList(new ArrayList<String>(diagnostics));
  }

  /** @return shaft speed in rpm */
  public double getShaftSpeedRpm() {
    return shaftSpeedRpm;
  }

  /** @return gearbox-input/driver speed in rpm */
  public double getDriverSpeedRpm() {
    return driverSpeedRpm;
  }

  /** @return total casing shaft power in kW, or NaN */
  public double getTotalCasingPowerKw() {
    return totalCasingPowerKw;
  }

  /** @return shaft load including configured friction in kW */
  public double getTotalShaftLoadKw() {
    return totalShaftLoadKw;
  }

  /** @return required gearbox input power in kW */
  public double getGearboxInputPowerKw() {
    return gearboxInputPowerKw;
  }

  /** @return speed-dependent driver power limit in kW */
  public double getDriverPowerLimitKw() {
    return driverPowerLimitKw;
  }

  /** @return absorbed shaft torque in N m */
  public double getShaftTorqueNm() {
    return shaftTorqueNm;
  }

  /** @return configured maximum shaft torque in N m */
  public double getMaximumTorqueNm() {
    return maximumTorqueNm;
  }

  /** @return unmet requested shaft power in kW */
  public double getUnmetPowerKw() {
    return unmetPowerKw;
  }

  /**
   * Builds a complete common plant utilization snapshot from this frozen evidence.
   *
   * @return immutable utilization snapshot
   */
  public PlantUtilizationSnapshot toPlantUtilizationSnapshot() {
    PlantConstraintRegistry registry = new PlantConstraintRegistry();
    for (PlantConstraintDefinition definition : definitions) {
      registry.register(definition);
    }
    PlantUtilizationSnapshot.Builder snapshot = PlantUtilizationSnapshot.builder(registry, calculationId)
        .convergenceComplete(convergenceComplete);
    for (PlantConstraintSample sample : samples) {
      snapshot.sample(sample);
    }
    return snapshot.build();
  }

  /** @return standards-compliant JSON with null for unavailable numbers */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("calculationId", calculationId);
    root.addProperty("shaftName", shaftName);
    root.addProperty("driverParticipantId", driverParticipantId);
    root.addProperty("gearboxIdentity", gearboxIdentity);
    root.addProperty("provenance", provenance);
    root.addProperty("convergenceComplete", convergenceComplete);
    root.addProperty("status", status.name());
    addNumber(root, "shaftSpeedRpm", shaftSpeedRpm);
    addNumber(root, "driverSpeedRpm", driverSpeedRpm);
    addNumber(root, "totalCasingPowerKw", totalCasingPowerKw);
    addNumber(root, "totalShaftLoadKw", totalShaftLoadKw);
    addNumber(root, "gearboxInputPowerKw", gearboxInputPowerKw);
    addNumber(root, "driverPowerLimitKw", driverPowerLimitKw);
    addNumber(root, "shaftTorqueNm", shaftTorqueNm);
    addNumber(root, "maximumTorqueNm", maximumTorqueNm);
    addNumber(root, "unmetPowerKw", unmetPowerKw);
    JsonArray casingRows = new JsonArray();
    for (CasingEvidence casing : casings) {
      JsonObject row = new JsonObject();
      row.addProperty("participantId", casing.getParticipantId());
      row.addProperty("equipmentName", casing.getEquipmentName());
      row.addProperty("status", casing.getStatus().name());
      addNumber(row, "speedRpm", casing.getSpeedRpm());
      addNumber(row, "shaftPowerKw", casing.getShaftPowerKw());
      addNumber(row, "torqueNm", casing.getTorqueNm());
      row.addProperty("chartActive", casing.isChartActive());
      row.addProperty("withinChart", casing.isWithinChart());
      addNumber(row, "distanceToSurge", casing.getDistanceToSurge());
      addNumber(row, "distanceToStoneWall", casing.getDistanceToStoneWall());
      addNumber(row, "mapMargin", casing.getMapMargin());
      row.addProperty("limitingConstraint", casing.getLimitingConstraint());
      row.addProperty("provenance", casing.getProvenance());
      row.addProperty("diagnostic", casing.getDiagnostic());
      casingRows.add(row);
    }
    root.add("casings", casingRows);
    JsonArray diagnosticRows = new JsonArray();
    for (String diagnostic : diagnostics) {
      diagnosticRows.add(diagnostic);
    }
    root.add("diagnostics", diagnosticRows);
    root.add("utilizationSnapshot", utilizationSnapshotJson());
    return root.toString();
  }

  private JsonObject utilizationSnapshotJson() {
    PlantUtilizationSnapshot snapshot = toPlantUtilizationSnapshot();
    JsonObject result = new JsonObject();
    result.addProperty("schemaVersion", snapshot.getSchemaVersion());
    result.addProperty("calculationId", snapshot.getCalculationId());
    result.addProperty("registryIdentityDigest", snapshot.getRegistryIdentityDigest());
    result.addProperty("convergenceComplete", snapshot.isConvergenceComplete());
    result.addProperty("complete", snapshot.isComplete());
    result.addProperty("feasible", snapshot.isFeasible());
    JsonArray rows = new JsonArray();
    for (PlantConstraintEvidence evidence : snapshot.getEvidence()) {
      JsonObject row = new JsonObject();
      row.addProperty("qualifiedConstraintId", evidence.getQualifiedConstraintId());
      row.addProperty("coverageStatus", evidence.getCoverageStatus().name());
      row.addProperty("operatingStatus", evidence.getOperatingStatus().name());
      row.addProperty("hardConstraint", evidence.isHardConstraint());
      row.addProperty("feasible", evidence.isFeasible());
      addNumber(row, "normalizedUtilization", evidence.getNormalizedUtilization());
      addNumber(row, "normalizedResidual", evidence.getNormalizedResidual());
      row.addProperty("diagnostic", evidence.getDiagnostic());
      rows.add(row);
    }
    result.add("evidence", rows);
    return result;
  }

  private static void addNumber(JsonObject target, String name, double value) {
    if (Double.isFinite(value)) {
      target.addProperty(name, value);
    } else {
      target.add(name, JsonNull.INSTANCE);
    }
  }

  /** Callback-free common-shaft evidence builder. */
  public static final class Builder {
    private final String modelName;
    private final String areaName;
    private final String groupName;
    private final String calculationId;
    private final MechanicalShaft shaft;
    private final String provenance;
    private final Map<String, CasingInput> casings = new LinkedHashMap<String, CasingInput>();
    private boolean convergenceComplete;
    private double speedToleranceRpm = Double.NaN;
    private double powerBalanceToleranceKw = Double.NaN;
    private double maximumTorqueNm = Double.NaN;
    private String driverParticipantId = "";
    private CompressorDriver driver;
    private String gearboxIdentity = "";
    private Gearbox gearbox;

    private Builder(String modelName, String areaName, String groupName, String calculationId, MechanicalShaft shaft,
        String provenance) {
      this.modelName = PlantConstraintScope.requireText(modelName, "Model name");
      this.areaName = PlantConstraintScope.requireText(areaName, "Area name");
      this.groupName = PlantConstraintScope.requireText(groupName, "Coupled group name");
      this.calculationId = calculationId;
      if (shaft == null) {
        throw new IllegalArgumentException("Mechanical shaft is required");
      }
      this.shaft = shaft;
      this.provenance = provenance;
    }

    /**
     * Adds an in-service compressor casing keyed by its shaft participant identity.
     *
     * @param participantId persistent shaft-input participant identity
     * @param compressor completed casing compressor
     * @return this builder
     */
    public Builder casing(String participantId, Compressor compressor) {
      return casing(participantId, compressor, false);
    }

    /**
     * Adds a compressor casing with an explicit availability state.
     *
     * @param participantId persistent shaft-input participant identity
     * @param compressor completed casing compressor
     * @param outOfService true only when zero shaft load must be verified
     * @return this builder
     */
    public Builder casing(String participantId, Compressor compressor, boolean outOfService) {
      String id = PlantConstraintScope.requireText(participantId, "Casing participant id");
      if (casings.containsKey(id)) {
        throw new IllegalArgumentException("Duplicate casing participant " + id);
      }
      casings.put(id, new CasingInput(id, compressor, outOfService));
      return this;
    }

    /**
     * Sets the expected shaft-network driver output and configured driver model.
     *
     * @param participantId persistent shaft-output participant identity
     * @param value configured driver model
     * @return this builder
     */
    public Builder driver(String participantId, CompressorDriver value) {
      driverParticipantId = PlantConstraintScope.requireText(participantId, "Driver participant id");
      if (value == null) {
        throw new IllegalArgumentException("Compressor driver is required");
      }
      driver = value;
      return this;
    }

    /**
     * Sets the gearbox identity and configured gearbox model.
     *
     * @param identity stable caller-owned gearbox identity
     * @param value configured gearbox model
     * @return this builder
     */
    public Builder gearbox(String identity, Gearbox value) {
      gearboxIdentity = PlantConstraintScope.requireText(identity, "Gearbox identity");
      if (value == null) {
        throw new IllegalArgumentException("Gearbox is required");
      }
      gearbox = value;
      return this;
    }

    /**
     * Sets the maximum allowed casing-to-shaft speed deviation.
     *
     * @param value positive tolerance in rpm
     * @return this builder
     */
    public Builder speedToleranceRpm(double value) {
      speedToleranceRpm = value;
      return this;
    }

    /**
     * Sets the maximum accepted unmet shaft load.
     *
     * @param value positive tolerance in kW
     * @return this builder
     */
    public Builder powerBalanceToleranceKw(double value) {
      powerBalanceToleranceKw = value;
      return this;
    }

    /**
     * Sets the independently configured maximum shaft torque.
     *
     * @param value positive torque limit in N m
     * @return this builder
     */
    public Builder maximumTorqueNm(double value) {
      maximumTorqueNm = value;
      return this;
    }

    /**
     * Declares whether the complete isolated candidate converged.
     *
     * @param value true only after the full applicable process convergence checks pass
     * @return this builder
     */
    public Builder convergenceComplete(boolean value) {
      convergenceComplete = value;
      return this;
    }

    /** @return immutable common-shaft evidence */
    public PlantCommonShaftEvidence build() {
      if (casings.isEmpty()) {
        throw new IllegalArgumentException("At least one compressor casing is required");
      }
      if (driver == null || gearbox == null) {
        throw new IllegalArgumentException("Driver and gearbox are required");
      }
      return new PlantCommonShaftEvidence(this);
    }
  }
}
