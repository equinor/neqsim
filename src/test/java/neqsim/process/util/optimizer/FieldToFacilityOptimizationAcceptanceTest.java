package neqsim.process.util.optimizer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSource;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorDriver;
import neqsim.process.equipment.compressor.DriverType;
import neqsim.process.equipment.energy.Gearbox;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.network.FieldNetworkTopology;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.network.FieldWellNetworkCoupler;
import neqsim.process.equipment.network.FieldWellNetworkCoupler.WellResult;
import neqsim.process.equipment.network.FieldWellNetworkProcessUnit;
import neqsim.process.equipment.network.LoopedPipeNetwork;
import neqsim.process.equipment.network.NetworkDecisionVariable;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.MechanicalShaft;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicLimitRole;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.CandidateSetEvaluationResult;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.Outcome;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.LeadingConstraintEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.OperatingRegionBoundaryRefinement;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.OperatingRegionTransition;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.QualifiedReliefPortfolioEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.QualifiedReliefCombinationEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.SliceResult;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.CapacityAlternative;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.LimitDirection;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.OperatingActionCandidateListSearch;
import neqsim.process.util.optimizer.ProcessModelDebottleneckStudy.StudyResult;
import neqsim.process.util.optimizer.ProcessModelOperatingPointOptimizer.OperatingPointSearchResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Synthetic end-to-end acceptance for live wells, reversible separator routing, mapped network power and host
 * constraints.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class FieldToFacilityOptimizationAcceptanceTest {
  private static final Logger logger = LogManager.getLogger(FieldToFacilityOptimizationAcceptanceTest.class);

  /** Complete mutable field-to-host fixture. */
  private static final class Fixture {
    private final ProcessModel model;
    private final FieldWellNetworkProcessUnit networkUnit;
    private final LoopedPipeNetwork mappedInjectionPowerNetwork;
    private final Separator highPressureSeparator;
    private final Separator lowPressureSeparator;
    private final Separator highPressureScrubber;
    private final Separator lowPressureScrubber;
    private final Compressor highPressureCompressor;
    private final Compressor lowPressureCompressor;
    private final Mixer exportMixer;
    private final ThrottlingValve exportValve;
    private final MechanicalShaft shaft;
    private final EnergyPort driverPort;
    private final CompressorDriver driver;
    private final Gearbox gearbox;
    private double sharedPowerLimitKw;
    private final double baselineRouteAHigh;
    private final List<String> wellSuffixes;

    /**
     * Create one fixture holder.
     *
     * @param model coupled process model
     * @param networkUnit live well/network unit
     * @param mappedInjectionPowerNetwork curve-backed injection utility included in shared host power
     * @param highPressureSeparator high-pressure separator
     * @param lowPressureSeparator low-pressure separator
     * @param highPressureScrubber high-pressure compressor suction scrubber
     * @param lowPressureScrubber low-pressure compressor suction scrubber
     * @param highPressureCompressor high-pressure compression casing
     * @param lowPressureCompressor low-pressure compression casing
     * @param exportMixer common gas-export boundary mixer
     * @param exportValve gas-export pressure-control valve
     * @param shaft common shaft
     * @param driverPort driver output port
     * @param driver driver rating
     * @param gearbox gearbox rating
     * @param sharedPowerLimitKw shared plant power limit in kW
     * @param baselineRouteAHigh baseline producer-A high-pressure route flow in kg/hr
     * @param wellSuffixes deterministic well identities
     */
    private Fixture(ProcessModel model, FieldWellNetworkProcessUnit networkUnit,
        LoopedPipeNetwork mappedInjectionPowerNetwork, Separator highPressureSeparator, Separator lowPressureSeparator,
        Separator highPressureScrubber, Separator lowPressureScrubber, Compressor highPressureCompressor,
        Compressor lowPressureCompressor, Mixer exportMixer, ThrottlingValve exportValve, MechanicalShaft shaft,
        EnergyPort driverPort, CompressorDriver driver, Gearbox gearbox, double sharedPowerLimitKw,
        double baselineRouteAHigh, List<String> wellSuffixes) {
      this.model = model;
      this.networkUnit = networkUnit;
      this.mappedInjectionPowerNetwork = mappedInjectionPowerNetwork;
      this.highPressureSeparator = highPressureSeparator;
      this.lowPressureSeparator = lowPressureSeparator;
      this.highPressureScrubber = highPressureScrubber;
      this.lowPressureScrubber = lowPressureScrubber;
      this.highPressureCompressor = highPressureCompressor;
      this.lowPressureCompressor = lowPressureCompressor;
      this.exportMixer = exportMixer;
      this.exportValve = exportValve;
      this.shaft = shaft;
      this.driverPort = driverPort;
      this.driver = driver;
      this.gearbox = gearbox;
      this.sharedPowerLimitKw = sharedPowerLimitKw;
      this.baselineRouteAHigh = baselineRouteAHigh;
      this.wellSuffixes = Collections.unmodifiableList(new ArrayList<String>(wellSuffixes));
    }

    /**
     * Freeze common-shaft evidence for one evaluator candidate.
     *
     * @param calculationId evaluator-owned candidate identity
     * @return complete common-shaft evidence
     */
    private PlantCommonShaftEvidence commonShaftEvidence(String calculationId) {
      shaft.solveBalance();
      String sourceCalculationId = highPressureCompressor.getCalculationIdentifier().toString();
      assertEquals(sourceCalculationId, lowPressureCompressor.getCalculationIdentifier().toString());
      PlantCommonShaftEvidence evidence = PlantCommonShaftEvidence
          .builder("synthetic field", "Host", "export common shaft", calculationId, shaft,
              "coupled field-to-facility candidate")
          .sourceCalculationId(sourceCalculationId)
          .casing(highPressureCompressor.getEnergyPort("shaftPower").getParticipantId(), highPressureCompressor)
          .casing(lowPressureCompressor.getEnergyPort("shaftPower").getParticipantId(), lowPressureCompressor)
          .driver(driverPort.getParticipantId(), driver).gearbox("export gearbox", gearbox).speedToleranceRpm(1.0)
          .powerBalanceToleranceKw(1.0e-6).maximumTorqueNm(1.0e6).convergenceComplete(model.isModelConverged()).build();
      assertTrue(evidence.isComplete(), evidence.getDiagnostics().toString());
      return evidence;
    }
  }

  /** Complete accepted-point evidence for one independent field lifecycle period. */
  private static final class LifecyclePeriod {
    private final Fixture fixture;
    private final ProcessModelOperatingEnvelopeStudy study;
    private final OperatingPointSearchResult search;
    private final UtilizationCoverageReport coverage;
    private final SliceResult[] slices;

    private LifecyclePeriod(Fixture fixture, ProcessModelOperatingEnvelopeStudy study,
        OperatingPointSearchResult search, UtilizationCoverageReport coverage, SliceResult[] slices) {
      this.fixture = fixture;
      this.study = study;
      this.search = search;
      this.coverage = coverage;
      this.slices = Arrays.copyOf(slices, slices.length);
    }
  }

  /** Independently ranked alternatives plus one jointly simulated non-additive combination. */
  private static final class ReliefEvidence {
    private final QualifiedReliefPortfolioEvidence portfolio;
    private final QualifiedReliefCombinationEvidence combination;

    private ReliefEvidence(QualifiedReliefPortfolioEvidence portfolio, QualifiedReliefCombinationEvidence combination) {
      this.portfolio = portfolio;
      this.combination = combination;
    }
  }

  /**
   * Execute the source-backed ten-well lifecycle acceptance case and serialize its exact schema-v14 evidence.
   *
   * <p>
   * This test-scope entry point lets the Python living-task acceptance workflow call the same Java fixture and physical
   * constraints as this regression. The current period is bound to {@code currentCycleId}; the earlier period remains
   * an independent model with tighter separator and scrubber capacity. No evidence dictionaries are rebuilt on the
   * Python side.
   *
   * @param currentCycleId living-task cycle identifier for the current lifecycle period
   * @return exact lifecycle evidence JSON emitted by the Java production exporter
   */
  public static String createTenWellLifecycleEvidenceJson(String currentCycleId) {
    return createTenWellLifecycleEvidenceJson(currentCycleId, 1.02);
  }

  /**
   * Execute the source-backed ten-well lifecycle case with a task-selected shared-power limit.
   *
   * <p>
   * The multiplier is applied to the current fixture's validated baseline shaft power. Requiring a finite value above
   * one keeps the baseline feasible while allowing a living task to re-evaluate the same objective after a reviewed
   * operating-limit revision. All optimizer, constraint, replay and restoration gates remain unchanged.
   *
   * @param currentCycleId living-task cycle identifier for the current lifecycle period
   * @param sharedPowerLimitMultiplier current shared-power limit divided by validated baseline shaft power
   * @return exact lifecycle evidence JSON emitted by the Java production exporter
   * @throws IllegalArgumentException if the multiplier is not finite and greater than one
   */
  public static String createTenWellLifecycleEvidenceJson(String currentCycleId, double sharedPowerLimitMultiplier) {
    return createTenWellLifecycleEvidenceJson(currentCycleId, sharedPowerLimitMultiplier, null);
  }

  /**
   * Execute the source-backed lifecycle case from an optional reviewed incumbent action vector.
   *
   * <p>
   * A non-null incumbent is copied into the optimizer seed. The new independent fixture must reproduce its raw
   * objective exactly before the living-task adapter can bind it to the promoted KPI baseline. Candidate feasibility,
   * constraints, replay and restoration are still evaluated afresh against the revised shared-power limit.
   *
   * @param currentCycleId living-task cycle identifier for the current lifecycle period
   * @param sharedPowerLimitMultiplier current shared-power limit divided by validated baseline shaft power
   * @param reviewedIncumbentValues optional ordered accepted action values from the promoted lifecycle receipt
   * @return exact lifecycle evidence JSON emitted by the Java production exporter
   * @throws IllegalArgumentException if the multiplier is invalid or the incumbent action count does not match
   */
  public static String createTenWellLifecycleEvidenceJson(String currentCycleId, double sharedPowerLimitMultiplier,
      double[] reviewedIncumbentValues) {
    if (!Double.isFinite(sharedPowerLimitMultiplier) || sharedPowerLimitMultiplier <= 1.0) {
      throw new IllegalArgumentException("Shared-power limit multiplier must be finite and greater than one");
    }
    FieldToFacilityOptimizationAcceptanceTest acceptance = new FieldToFacilityOptimizationAcceptanceTest();
    Fixture earlyFixture = acceptance.createFixture(10);
    acceptance.configureLifecycleCapacityLimits(earlyFixture, 1.03);
    LifecyclePeriod early = acceptance.evaluateLifecyclePeriod("early-life", earlyFixture,
        earlyFixture.model.getPower("kW") * 1.8);

    Fixture currentFixture = acceptance.createFixture(10);
    acceptance.configureLifecycleCapacityLimits(currentFixture, 10.0);
    LifecyclePeriod current = acceptance.evaluateLifecyclePeriod(currentCycleId, currentFixture,
        currentFixture.model.getPower("kW") * sharedPowerLimitMultiplier, reviewedIncumbentValues);
    current.study.setNearActiveConstraintNormalizedMargin(1.0,
        "synthetic complete installed-capacity follow-up audit threshold");
    ReliefEvidence relief = acceptance.qualifiedReliefEvidence(current);

    return new ProcessModelOperatingEnvelopeLifecycleStudy("ten-well-lifecycle", "Ten-well field lifecycle envelope",
        "synthetic independent reservoir/facility operating definitions", "year")
        .addPeriod("early-life", "Early life", 0.0,
            "tight separator and scrubber operating limits; qualified compressor maps retained", early.coverage,
            early.study, early.search, early.slices)
        .addPeriod(currentCycleId, "Current life", 5.0,
            "expanded separation capacity and tighter shared plant power budget",
            Collections.<ProcessModelOperatingEnvelopeLifecycleStudy.AcceptedStateMetric>emptyList(), current.coverage,
            relief.portfolio.getRankedStudies(), relief.combination, current.study, current.search, current.slices)
        .toEvidenceJson(currentCycleId);
  }

  /** Builds two independent receipts and one jointly evaluated combination at the accepted ten-well point. */
  private ReliefEvidence qualifiedReliefEvidence(LifecyclePeriod period) {
    List<double[]> candidates = new ArrayList<double[]>();
    candidates.add(period.search.getAcceptedCandidateValues());
    OperatingActionCandidateListSearch search = new OperatingActionCandidateListSearch("accepted-point-paired-relief",
        "Accepted-point paired relief", "same accepted ten-well action vector under baseline and capacity alternative",
        candidates, 0, 0.0);
    List<ProcessModelDebottleneckStudy> studies = new ArrayList<ProcessModelDebottleneckStudy>();
    List<StudyResult> results = new ArrayList<StudyResult>();
    List<CapacityAlternative> alternatives = new ArrayList<CapacityAlternative>();
    CapacityConstraint separator = period.fixture.highPressureSeparator.getCapacityConstraints().get("gas handling");
    CapacityConstraint scrubber = period.fixture.lowPressureScrubber.getCapacityConstraints().get("gas handling");
    for (CapacityAlternative alternative : Arrays.asList(
        new CapacityAlternative("hp-separator-gas-relief", "Raise HP separator gas handling limit",
            "synthetic qualified brownfield alternative", "Field", "HP separator", "gas handling",
            separator.getApplicableLimit() * 1.10, separator.getUnit(), LimitDirection.MAXIMUM,
            "synthetic separator internals replacement basis", 0.90, separator.getApplicableLimit(),
            separator.getApplicableLimit() * 1.25),
        new CapacityAlternative("lp-scrubber-gas-relief", "Raise LP scrubber gas handling limit",
            "synthetic qualified brownfield alternative", "Field", "LP suction scrubber", "gas handling",
            scrubber.getApplicableLimit() * 1.08, scrubber.getUnit(), LimitDirection.MAXIMUM,
            "synthetic scrubber internals replacement basis", 0.90, scrubber.getApplicableLimit(),
            scrubber.getApplicableLimit() * 1.20))) {
      ProcessModelDebottleneckStudy paired = new ProcessModelDebottleneckStudy(alternative.getId() + "-paired",
          alternative.getName() + " paired study", "synthetic accepted-baseline linkage acceptance",
          period.study.getCandidateEvaluator(), alternative, search, 0);
      StudyResult result = paired.evaluate();
      assertEquals(ProcessModelDebottleneckStudy.StudyOutcome.COMPLETED, result.getOutcome(),
          result.getDiagnostics().toString());
      assertEquals(0.0, result.getObjectiveDelta(), 0.0);
      studies.add(paired);
      results.add(result);
      alternatives.add(alternative);
    }
    ProcessModelDebottleneckRanking ranking = new ProcessModelDebottleneckRanking("accepted-relief-portfolio",
        "Accepted-point relief portfolio", "synthetic exact accepted-point paired evidence");
    ProcessModelDebottleneckRanking.ObjectiveRankingPolicy policy = new ProcessModelDebottleneckRanking.ObjectiveRankingPolicy(
        "validated-gas-export-delta", "Validated gas export delta",
        "same transactional evaluator objective and accepted baseline", 0, "validated gas export",
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE, "kg/hr", 1.0, 0.0, 1.0e-8);
    QualifiedReliefPortfolioEvidence portfolio = period.study.qualifyReliefPortfolio(period.search, ranking, policy,
        studies, results);
    ProcessModelDebottleneckStudy combinationStudy = new ProcessModelDebottleneckStudy(
        "accepted-relief-combination-paired", "Accepted-point combined relief paired study",
        "synthetic jointly simulated non-additive relief acceptance", period.study.getCandidateEvaluator(),
        alternatives, search, 0);
    StudyResult combinationResult = combinationStudy.evaluate();
    assertEquals(ProcessModelDebottleneckStudy.StudyOutcome.COMPLETED, combinationResult.getOutcome(),
        combinationResult.getDiagnostics().toString());
    QualifiedReliefCombinationEvidence combination = period.study.qualifyReliefCombination(period.search, portfolio,
        combinationStudy, combinationResult);
    return new ReliefEvidence(portfolio, combination);
  }

  /**
   * Build the current ten-well fixture and read its task-baseline gas-export objective.
   *
   * <p>
   * This is the same physical objective and initial action state used by
   * {@link #createTenWellLifecycleEvidenceJson(String)}. It lets the living-task fixture establish a reviewed numeric
   * baseline before a cycle starts instead of inventing a workflow-only marker KPI.
   *
   * @return finite initial validated gas export in kg/hr
   */
  public static double createTenWellBaselineGasExportKgHr() {
    FieldToFacilityOptimizationAcceptanceTest acceptance = new FieldToFacilityOptimizationAcceptanceTest();
    Fixture fixture = acceptance.createFixture(10);
    acceptance.configureLifecycleCapacityLimits(fixture, 10.0);
    double value = Math.abs(fixture.exportValve.getOutletStream().getFlowRate("kg/hr"));
    if (!Double.isFinite(value)) {
      throw new IllegalStateException("Ten-well baseline gas-export objective is not finite");
    }
    return value;
  }

  /**
   * Verify route swaps, choke changes, per-line operating limits, separator limits, mapped compression, shared power,
   * bottleneck attribution and exact baseline restoration in one optimizer workflow.
   */
  @Test
  void evaluatesReversibleMultiWellFieldToFacilityEnvelope() {
    Fixture fixture = createFixture();
    ProcessModelSimulationEvaluator simulation = createSimulationEvaluator(fixture);

    ProcessModelOperatingAction chokeA = ProcessModelOperatingAction.continuous("choke-a", "Producer A choke",
        "Field::field network.choke.choke A.opening", 50.0, 95.0, "%", "synthetic choke travel envelope");
    ProcessModelOperatingAction chokeB = ProcessModelOperatingAction.continuous("choke-b", "Producer B choke",
        "Field::field network.choke.choke B.opening", 50.0, 95.0, "%", "synthetic choke travel envelope");
    ProcessModelOperatingAction routeA = ProcessModelOperatingAction.discrete("route-a",
        "Producer A separator pressure level", "Field::field network.route.producer A pressure level.selection",
        new double[] {0.0, 1.0}, "-", "synthetic qualified HP/LP route line-up");
    ProcessModelOperatingAction routeB = ProcessModelOperatingAction.discrete("route-b",
        "Producer B separator pressure level", "Field::field network.route.producer B pressure level.selection",
        new double[] {0.0, 1.0}, "-", "synthetic qualified HP/LP route line-up");
    ProcessModelOperatingActionSetEvaluator candidates = new ProcessModelOperatingActionSetEvaluator(
        "field-to-facility-actions", "Field-to-facility actions", "synthetic coupled acceptance", simulation,
        Arrays.asList(chokeA, chokeB, routeA, routeB))
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "Field", "field network",
            "producer A deliverability", "synthetic well deliverability")
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "Field", "field network",
            "producer B deliverability", "synthetic well deliverability")
        .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "Field", "field network",
            "A HP route capacity", "synthetic per-line operating limit");

    CandidateSetEvaluationResult swapped = candidates.evaluate(new double[] {60.0, 70.0, 1.0, 0.0});
    assertEquals(Outcome.OTHER_MODEL_CONSTRAINT_VIOLATED, swapped.getOutcome(), swapped.getDiagnostics().toString());
    assertTrue(swapped.isBaselineRestored());
    assertTrue(swapped.isBaselineSimulationConverged());
    assertTrue(swapped.getPlantConstraintEvidence().stream().allMatch(PlantConstraintEvidence::hasAvailableEvidence));
    assertEquals(1, swapped.getProcessBoundaryConstraintEvidence().size());
    assertTrue(swapped.getProcessBoundaryConstraintEvidence().get(0).isCalculable());
    assertTrue(swapped.getInstalledEquipmentCapacityEvidence().stream()
        .anyMatch(evidence -> "HP suction scrubber".equals(evidence.getEquipmentName())
            && evidence.getQualifiedConstraintName().endsWith("/gas handling")));
    assertTrue(swapped.getInstalledEquipmentCapacityEvidence().stream()
        .anyMatch(evidence -> "LP suction scrubber".equals(evidence.getEquipmentName())
            && evidence.getQualifiedConstraintName().endsWith("/gas handling")));
    assertBaselineRestored(fixture);

    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy("field-to-facility-envelope",
        "Field-to-facility envelope", "synthetic coupled field and host acceptance", candidates);
    SliceResult slice = study.evaluateOneDimensional("choke-a", new double[] {70.0, 90.0},
        new double[] {70.0, 70.0, 0.0, 1.0});

    assertTrue(slice.isComplete());
    assertTrue(slice.getPoints().get(0).isFeasible(),
        slice.getPoints().get(0).getEvaluation().getOutcome() + ": "
            + slice.getPoints().get(0).getEvaluation().getDiagnostics() + " constraints="
            + slice.getPoints().get(0).getEvaluation().getConstraintEvidence().stream().filter(e -> !e.isSatisfied())
                .map(e -> e.getName() + " value=" + e.getValue() + " margin=" + e.getMargin())
                .collect(java.util.stream.Collectors.toList()));
    assertFalse(slice.getPoints().get(1).isFeasible());
    LeadingConstraintEvidence leading = slice.getPoints().get(1).getLeadingConstraint();
    assertEquals(LeadingConstraintEvidence.Source.REQUIRED_HYDRAULIC, leading.getSource());
    assertTrue(leading.getQualifiedConstraintName().endsWith("/A HP route capacity"));
    assertEquals("REQUIRED_HYDRAULIC", leading.getApplicableLimitRole());
    assertTrue(slice.getPoints().get(1).getEvaluation().getInstalledEquipmentCapacityEvidence().stream()
        .anyMatch(evidence -> evidence.getQualifiedConstraintName().endsWith("/A HP route capacity")
            && evidence.getApplicableLimitRole() == CapacityConstraint.ApplicableLimitRole.CONFIGURED_OPERATING));
    assertTrue(slice.getBottleneckTransitions().isEmpty());
    assertBaselineRestored(fixture);
  }

  /**
   * Verify mixed choke/routing search and replay scale the complete evidence contract to ten live wells.
   */
  @Test
  void evaluatesTenWellExportBoundaryWithCompleteConstraintEvidence() {
    Fixture fixture = createFixture(10);
    ProcessModelSimulationEvaluator simulation = createSimulationEvaluator(fixture);
    List<ProcessModelOperatingAction> actions = createFieldActions(fixture);
    ProcessModelOperatingActionSetEvaluator candidates = new ProcessModelOperatingActionSetEvaluator(
        "ten-well-field-actions", "Ten-well field actions", "synthetic full-model mixed search", simulation, actions);
    for (String suffix : fixture.wellSuffixes) {
      candidates
          .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "Field", "field network",
              "producer " + suffix + " deliverability", "synthetic well deliverability")
          .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "Field", "field network",
              suffix + " HP route capacity", "synthetic per-line operating limit")
          .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "Field", "field network",
              suffix + " LP route capacity", "synthetic per-line operating limit");
    }
    double[] seed = new double[actions.size()];
    for (int index = 0; index < fixture.wellSuffixes.size(); index++) {
      seed[index] = 70.0;
      seed[index + fixture.wellSuffixes.size()] = index % 2;
    }
    ProcessModelOperatingPointOptimizer optimizer = new ProcessModelOperatingPointOptimizer("ten-well-field-search",
        "Ten-well field search", "synthetic bounded choke and HP/LP routing search", candidates)
        .setInitialCandidate(seed).setMaximumEvaluations(35).setInitialStepFraction(0.5).setRelativeStepTolerance(0.1)
        .setObjectiveImprovementTolerance(1.0e-6, "synthetic export-rate comparison tolerance");
    OperatingPointSearchResult result = optimizer.optimize();

    assertTrue(result.isAcceptedPointReplayed(), result.getOutcome() + ": " + result.getDiagnostics());
    assertTrue(result.getAcceptedPointReplay().getEvaluation().isFeasible());
    assertTrue(result.getAcceptedPointReplay().getRawObjective() > result.getCandidates().get(0).getRawObjective());
    assertTrue(result.isCandidateFinite());
    assertTrue(result.isConstraintEvidenceComplete());
    assertTrue(result.isActionsComplete());
    assertTrue(result.getEvaluationCount() <= 35);
    assertTrue(Double.isFinite(result.getRuntimeSeconds()) && result.getRuntimeSeconds() >= 0.0);
    Map<String, Object> evidence = result.getOptimizerEvidence("synthetic-cycle-1");
    assertEquals("synthetic-cycle-1", evidence.get("cycle_id"));
    assertEquals("validated gas export", evidence.get("objective_name"));
    assertEquals("MAXIMIZE", evidence.get("objective_direction"));
    assertEquals("kg/hr", evidence.get("objective_unit"));
    assertEquals(result.getCandidates().get(0).getRawObjective(), evidence.get("initial_objective_value"));
    assertEquals(result.getAcceptedPointReplay().getRawObjective(), evidence.get("accepted_objective_value"));
    assertEquals(Boolean.TRUE, evidence.get("simulation_converged"));
    assertEquals(Boolean.TRUE, evidence.get("candidate_feasible"));
    assertEquals(Boolean.TRUE, evidence.get("candidate_finite"));
    assertEquals(Boolean.TRUE, evidence.get("constraint_evidence_complete"));
    assertEquals(Boolean.TRUE, evidence.get("state_restore_complete"));
    assertEquals(Boolean.TRUE, evidence.get("accepted_point_replayed"));
    assertEquals(Boolean.TRUE, evidence.get("actions_complete"));
    assertEquals(result.getEvaluationCount(), evidence.get("evaluation_count"));
    assertEquals(10, fixture.networkUnit.getLastCouplingResult().getWellResults().size());
    assertEquals(10, fixture.networkUnit.getExclusiveRouteSelectorNames().size());
    assertTrue(result.getAcceptedPointReplay().getEvaluation().getInstalledEquipmentCapacityEvidence().size() >= 34);
    assertTrue(result.getAcceptedPointReplay().getEvaluation().getInstalledEquipmentCapacityEvidence().stream()
        .allMatch(capacityEvidence -> capacityEvidence.hasFiniteEvidence()
            && Double.isFinite(capacityEvidence.getNormalizedUtilization())));
    assertEquals(1, result.getAcceptedPointReplay().getEvaluation().getProcessBoundaryConstraintEvidence().size());
    assertTrue(
        result.getAcceptedPointReplay().getEvaluation().getProcessBoundaryConstraintEvidence().get(0).isCalculable());
    assertTrue(
        result.getAcceptedPointReplay().getEvaluation().getProcessBoundaryConstraintEvidence().get(0).isFeasible());

    ProcessModelOperatingEnvelopeStudy acceptedPointStudy = new ProcessModelOperatingEnvelopeStudy(
        "ten-well-accepted-envelope", "Ten-well accepted operating envelope",
        "synthetic accepted-point bottleneck and nearby slice", candidates);
    int evaluationsBeforeBottleneck = simulation.getEvaluationCount();
    LeadingConstraintEvidence acceptedBottleneck = acceptedPointStudy.getAcceptedPointBottleneck(result);
    assertEquals(evaluationsBeforeBottleneck, simulation.getEvaluationCount());
    assertTrue(acceptedBottleneck != null && acceptedBottleneck.isFeasible());
    assertFalse(acceptedBottleneck.getQualifiedConstraintName().trim().isEmpty());
    double[] accepted = result.getAcceptedCandidateValues();
    double nearbyChokeA = Math.max(actions.get(0).getLowerBound(), accepted[0] - 5.0);
    SliceResult acceptedSlice = acceptedPointStudy.evaluateOneDimensional(actions.get(0).getId(),
        new double[] {nearbyChokeA, accepted[0]}, result);
    assertTrue(acceptedSlice.isComplete(), acceptedSlice.getDiagnostics().toString());
    assertArrayEquals(accepted, acceptedSlice.getAnchorValues(), 0.0);
    assertEquals(2, acceptedSlice.getCompletedPointCount());
    assertArrayEquals(accepted, acceptedSlice.getPoints().get(1).getCandidateValues(), 0.0);
    assertTrue(acceptedSlice.getPoints().stream().allMatch(
        point -> point.getEvaluation().isBaselineRestored() && point.getEvaluation().isBaselineSimulationConverged()));
    int firstRouteIndex = fixture.wellSuffixes.size();
    double acceptedRouteA = accepted[firstRouteIndex];
    double alternativeRouteA = acceptedRouteA == 0.0 ? 1.0 : 0.0;
    SliceResult routingSlice = acceptedPointStudy.evaluateOneDimensional(actions.get(firstRouteIndex).getId(),
        new double[] {acceptedRouteA, alternativeRouteA}, result);
    assertTrue(routingSlice.isComplete(), routingSlice.getDiagnostics().toString());
    assertTrue(routingSlice.getPoints().stream().allMatch(
        point -> point.getEvaluation().isBaselineRestored() && point.getEvaluation().isBaselineSimulationConverged()));
    double nearbyChokeB = Math.max(actions.get(1).getLowerBound(), accepted[1] - 5.0);
    SliceResult hostPowerSlice = acceptedPointStudy.evaluateOneDimensional(actions.get(1).getId(),
        new double[] {nearbyChokeB, accepted[1]}, result);
    assertTrue(hostPowerSlice.isComplete(), hostPowerSlice.getDiagnostics().toString());
    String sharedPowerId = sharedPowerDefinition().getQualifiedId();
    assertTrue(hostPowerSlice.getPoints().stream().allMatch(
        point -> point.getEvaluation().isBaselineRestored() && point.getEvaluation().isBaselineSimulationConverged()
            && point.getEvaluation().getPlantConstraintEvidence().stream()
                .anyMatch(power -> sharedPowerId.equals(power.getQualifiedConstraintId())
                    && power.hasAvailableEvidence() && Double.isFinite(power.getSample().getSampledValue()))));
    int evaluationsBeforeExport = simulation.getEvaluationCount();
    Map<String, Object> envelopeEvidence = acceptedPointStudy.getAcceptedPointEnvelopeEvidence("synthetic-cycle-1",
        result, acceptedSlice, routingSlice, hostPowerSlice);
    assertEquals(evaluationsBeforeExport, simulation.getEvaluationCount());
    assertEquals(actions.stream().map(ProcessModelOperatingAction::getId).collect(java.util.stream.Collectors.toList()),
        envelopeEvidence.get("accepted_action_ids"));
    assertEquals(actions.size(), ((List<?>) envelopeEvidence.get("accepted_candidate_values")).size());
    assertEquals(Integer.valueOf(3), envelopeEvidence.get("slice_count"));
    assertEquals(Integer.valueOf(6), envelopeEvidence.get("requested_point_count"));
    assertEquals(Integer.valueOf(6), envelopeEvidence.get("completed_point_count"));
    assertEquals(Long.valueOf(6L), envelopeEvidence.get("envelope_evaluation_count"));
    assertEquals(Long.valueOf(result.getEvaluationCount() + 6L), envelopeEvidence.get("total_evaluation_count"));
    assertTrue(Double.isFinite(((Double) envelopeEvidence.get("envelope_runtime_seconds")).doubleValue()));
    assertEquals(acceptedBottleneck.getQualifiedConstraintName(),
        ((Map<?, ?>) envelopeEvidence.get("accepted_point_bottleneck")).get("qualified_constraint_name"));
    assertEquals(Integer.valueOf(2), envelopeEvidence.get("schema_version"));
    Map<?, ?> constraintSummary = (Map<?, ?>) envelopeEvidence.get("cross_slice_constraint_summary");
    assertEquals(Integer.valueOf(2), constraintSummary.get("summary_version"));
    int observedConstraintCount = ((Integer) constraintSummary.get("observed_constraint_count")).intValue();
    int rankedConstraintCount = ((Integer) constraintSummary.get("ranked_constraint_count")).intValue();
    assertTrue(observedConstraintCount > rankedConstraintCount);
    assertTrue(rankedConstraintCount > 0);
    List<?> acceptedSlicePoints = (List<?>) ((Map<?, ?>) ((List<?>) envelopeEvidence.get("slices")).get(0))
        .get("points");
    assertTrue(acceptedSlicePoints.stream().map(Map.class::cast)
        .allMatch(point -> !((List<?>) point.get("constraint_evidence")).isEmpty()));

    assertEquals(100.0, fixture.exportValve.getOutletStream().getPressure("bara"), 1.0e-6);
    assertBaselineRestored(fixture);
  }

  /**
   * Verify independent early- and late-life field definitions retain complete multi-slice evidence and expose a
   * separator-to-shared-power bottleneck migration without carrying mutable model state between periods.
   */
  @Test
  void evaluatesIndependentLifecyclePeriodsWithMovingHostBottlenecks() {
    assertThrows(IllegalArgumentException.class,
        () -> createTenWellLifecycleEvidenceJson("invalid-power-limit", Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> createTenWellLifecycleEvidenceJson("invalid-power-limit", 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> createTenWellLifecycleEvidenceJson("invalid-incumbent", 1.02, new double[] {70.0}));

    Fixture earlyFixture = createFixture(10);
    configureLifecycleCapacityLimits(earlyFixture, 1.03);
    LifecyclePeriod early = evaluateLifecyclePeriod("early-life", earlyFixture,
        earlyFixture.model.getPower("kW") * 1.8);

    Fixture lateFixture = createFixture(10);
    configureLifecycleCapacityLimits(lateFixture, 10.0);
    LifecyclePeriod late = evaluateLifecyclePeriod("late-life", lateFixture, lateFixture.model.getPower("kW") * 1.02);

    String earlyConstraint = early.study.getAcceptedPointBottleneck(early.search).getQualifiedConstraintName();
    String lateConstraint = late.study.getAcceptedPointBottleneck(late.search).getQualifiedConstraintName();
    assertTrue(earlyConstraint.contains("separator") || earlyConstraint.contains("scrubber"), earlyConstraint);
    assertEquals(sharedPowerDefinition().getQualifiedId(), lateConstraint);

    ProcessModelOperatingEnvelopeLifecycleStudy lifecycle = new ProcessModelOperatingEnvelopeLifecycleStudy(
        "ten-well-lifecycle", "Ten-well field lifecycle envelope",
        "synthetic independent reservoir/facility operating definitions", "year")
        .addPeriod("early-life", "Early life", 0.0,
            "tight separator and scrubber operating limits; qualified compressor maps retained", early.coverage,
            early.study, early.search, early.slices)
        .addPeriod("late-life", "Late life", 5.0, "expanded separation capacity and tighter shared plant power budget",
            late.coverage, late.study, late.search, late.slices);
    Map<String, Object> lifecycleEvidence = lifecycle.toEvidenceMap("synthetic-lifecycle-cycle");
    JsonObject lifecycleJson = JsonParser.parseString(lifecycle.toEvidenceJson("late-life")).getAsJsonObject();

    assertEquals(Integer.valueOf(11), lifecycleEvidence.get("schema_version"));
    assertEquals(11, lifecycleJson.get("schema_version").getAsInt());
    assertEquals("late-life", lifecycleJson.get("cycle_id").getAsString());
    JsonArray jsonPeriods = lifecycleJson.getAsJsonArray("periods");
    assertEquals(2, jsonPeriods.size());
    assertEquals("late-life", jsonPeriods.get(1).getAsJsonObject().get("period_id").getAsString());
    JsonObject coverage = jsonPeriods.get(1).getAsJsonObject().getAsJsonObject("utilization_coverage");
    assertTrue(coverage.get("complete").getAsBoolean());
    assertEquals("1.1", coverage.get("schema_version").getAsString());
    assertEquals(34, coverage.getAsJsonArray("expected_constraint_ids").size());
    assertEquals(10, coverage.getAsJsonArray("expected_plant_constraint_ids").size());
    assertEquals(44, coverage.getAsJsonArray("rows").size());
    assertTrue(coverage.getAsJsonArray("rows").asList().stream().map(JsonElement::getAsJsonObject)
        .anyMatch(row -> "PLANT_CONSTRAINT".equals(row.get("evidence_kind").getAsString())
            && row.get("qualified_constraint_id").getAsString().endsWith("#driver-maximum-power")));
    assertTrue(coverage.getAsJsonArray("rows").asList().stream().map(JsonElement::getAsJsonObject)
        .anyMatch(row -> "PLANT_CONSTRAINT".equals(row.get("evidence_kind").getAsString())
            && row.get("qualified_constraint_id").getAsString().endsWith("#total-shaft-power")));
    assertTrue(coverage.getAsJsonArray("diagnostics").isEmpty());
    assertEquals("late-life", jsonPeriods.get(1).getAsJsonObject().getAsJsonObject("envelope_evidence")
        .getAsJsonObject("optimizer_evidence").get("cycle_id").getAsString());
    JsonObject rejectingSummary = jsonPeriods.get(0).getAsJsonObject().getAsJsonObject("envelope_evidence")
        .getAsJsonObject("cross_slice_constraint_summary").getAsJsonArray("ranked_constraints").get(0)
        .getAsJsonObject();
    assertTrue(jsonPeriods.asList().stream().map(JsonElement::getAsJsonObject)
        .flatMap(period -> period.getAsJsonObject("envelope_evidence").getAsJsonObject("cross_slice_constraint_summary")
            .getAsJsonArray("ranked_constraints").asList().stream())
        .map(JsonElement::getAsJsonObject)
        .noneMatch(row -> "REGISTERED_MODEL_CONSTRAINT".equals(row.get("source").getAsString())));
    assertEquals("REJECTING", rejectingSummary.get("classification").getAsString());
    assertTrue(rejectingSummary.get("rejecting_evidence_count").getAsInt() > 0);
    assertTrue(rejectingSummary.has("minimum_normalized_margin"));
    assertTrue(Double.isFinite(rejectingSummary.get("minimum_normalized_margin").getAsDouble()));
    assertTrue(rejectingSummary.has("representative_feasible_constraint"));
    assertTrue(rejectingSummary.get("representative_feasible_constraint").isJsonObject());
    JsonObject opportunitySummary = jsonPeriods.get(0).getAsJsonObject()
        .getAsJsonObject("constraint_opportunity_summary");
    assertEquals(1, opportunitySummary.get("summary_version").getAsInt());
    assertEquals(rejectingSummary.get("rank").getAsInt(),
        opportunitySummary.getAsJsonArray("candidates").get(0).getAsJsonObject().get("rank").getAsInt());
    assertEquals("CURRENT_REJECTING_CONSTRAINT", opportunitySummary.getAsJsonArray("candidates").get(0)
        .getAsJsonObject().get("screening_classification").getAsString());
    assertTrue(opportunitySummary.getAsJsonArray("candidates").asList().stream().map(JsonElement::getAsJsonObject)
        .allMatch(candidate -> candidate.get("paired_relief_study_required").getAsBoolean()
            && "NOT_PROVIDED".equals(candidate.get("paired_relief_study_status").getAsString())
            && Double.isFinite(candidate.get("physical_headroom").getAsDouble())));
    assertThrows(IllegalStateException.class, () -> lifecycle.toEvidenceJson("missing-cycle"));
    assertEquals(Integer.valueOf(2), lifecycleEvidence.get("period_count"));
    assertEquals(Integer.valueOf(12), lifecycleEvidence.get("requested_point_count"));
    assertEquals(Integer.valueOf(12), lifecycleEvidence.get("completed_point_count"));
    assertEquals(1, ((List<?>) lifecycleEvidence.get("bottleneck_transitions")).size());
    List<?> periodChanges = (List<?>) lifecycleEvidence.get("period_change_diagnostics");
    assertEquals(1, periodChanges.size());
    Map<?, ?> periodChange = (Map<?, ?>) periodChanges.get(0);
    assertEquals(Integer.valueOf(3), periodChange.get("comparable_slice_count"));
    assertEquals(Integer.valueOf(3), Integer.valueOf(((List<?>) periodChange.get("slice_changes")).size()));
    assertTrue(((List<?>) periodChange.get("removed_slice_axes")).isEmpty());
    assertTrue(((List<?>) periodChange.get("added_slice_axes")).isEmpty());
    assertEquals(Integer.valueOf(20), Integer.valueOf(((List<?>) periodChange.get("accepted_action_changes")).size()));
    List<?> removedConstraintIdentities = (List<?>) periodChange.get("removed_ranked_constraint_identities");
    List<?> addedConstraintIdentities = (List<?>) periodChange.get("added_ranked_constraint_identities");
    int retainedConstraintIdentityCount = ((Integer) periodChange.get("retained_ranked_constraint_identity_count"))
        .intValue();
    assertEquals(((Integer) periodChange.get("from_ranked_constraint_identity_count")).intValue(),
        retainedConstraintIdentityCount + removedConstraintIdentities.size());
    assertEquals(((Integer) periodChange.get("to_ranked_constraint_identity_count")).intValue(),
        retainedConstraintIdentityCount + addedConstraintIdentities.size());
    assertTrue(removedConstraintIdentities.stream().map(Map.class::cast)
        .anyMatch(identity -> earlyConstraint.equals(identity.get("qualified_constraint_name"))));
    assertTrue(addedConstraintIdentities.stream().map(Map.class::cast)
        .anyMatch(identity -> lateConstraint.equals(identity.get("qualified_constraint_name"))));
    for (Object value : (List<?>) periodChange.get("slice_changes")) {
      Map<?, ?> sliceChange = (Map<?, ?>) value;
      int fromTransitionCount = ((Integer) sliceChange.get("from_operating_region_transition_count")).intValue();
      int toTransitionCount = ((Integer) sliceChange.get("to_operating_region_transition_count")).intValue();
      int retainedTransitionCount = ((Integer) sliceChange.get("retained_operating_region_transition_count"))
          .intValue();
      List<?> removedTransitions = (List<?>) sliceChange.get("removed_operating_region_transitions");
      List<?> addedTransitions = (List<?>) sliceChange.get("added_operating_region_transitions");
      assertEquals(fromTransitionCount, retainedTransitionCount + removedTransitions.size());
      assertEquals(toTransitionCount, retainedTransitionCount + addedTransitions.size());
      for (Object transition : removedTransitions) {
        assertEquals(Boolean.FALSE, ((Map<?, ?>) transition).get("interpolated"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) transition).get("sampled_event_only"));
      }
      for (Object transition : addedTransitions) {
        assertEquals(Boolean.FALSE, ((Map<?, ?>) transition).get("interpolated"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) transition).get("sampled_event_only"));
      }
      int fromRefinementCount = ((Integer) sliceChange.get("from_operating_region_boundary_refinement_count"))
          .intValue();
      int toRefinementCount = ((Integer) sliceChange.get("to_operating_region_boundary_refinement_count")).intValue();
      int retainedRefinementCount = ((Integer) sliceChange.get("retained_operating_region_boundary_refinement_count"))
          .intValue();
      List<?> removedRefinements = (List<?>) sliceChange.get("removed_operating_region_boundary_refinements");
      List<?> addedRefinements = (List<?>) sliceChange.get("added_operating_region_boundary_refinements");
      assertEquals(fromRefinementCount, retainedRefinementCount + removedRefinements.size());
      assertEquals(toRefinementCount, retainedRefinementCount + addedRefinements.size());
    }
    assertEquals(earlyConstraint, lifecycle.getBottleneckTransitions().get(0).getFromConstraint());
    assertEquals(lateConstraint, lifecycle.getBottleneckTransitions().get(0).getToConstraint());
    assertTrue(((Long) lifecycleEvidence.get("total_evaluation_count")).longValue() > 12L);
    assertTrue(Double.isFinite(((Double) lifecycleEvidence.get("envelope_runtime_seconds")).doubleValue()));

    for (LifecyclePeriod period : Arrays.asList(early, late)) {
      assertTrue(period.search.isAcceptedPointReplayed());
      assertTrue(period.search.isCandidateFinite());
      assertTrue(period.search.isConstraintEvidenceComplete());
      assertTrue(period.search.getAcceptedPointReplay().getEvaluation().getInstalledEquipmentCapacityEvidence().stream()
          .anyMatch(evidence -> evidence.getQualifiedConstraintName().endsWith("/surgeMargin")
              && evidence.hasFiniteEvidence()));
      assertTrue(period.search.getAcceptedPointReplay().getEvaluation().getInstalledEquipmentCapacityEvidence().stream()
          .anyMatch(evidence -> evidence.getQualifiedConstraintName().endsWith("/stonewallMargin")
              && evidence.hasFiniteEvidence()));
      assertBaselineRestored(period.fixture);
    }
  }

  /**
   * Verify the live field search refines a recovered driver-power rejection and replays the final feasible choke point.
   */
  @Test
  void refinesCommonShaftDriverLimitInLiveFieldSearch() {
    Fixture fixture = createFixture();
    configureLifecycleCapacityLimits(fixture, 10.0);
    double baselineDriverPower = fixture.commonShaftEvidence("driver-baseline").getGearboxInputPowerKw();
    fixture.networkUnit.getHydraulicNetwork().getPipe("choke A").setChokeOpening(95.0);
    fixture.model.run();
    assertTrue(fixture.model.isModelConverged());
    double highDriverPower = fixture.commonShaftEvidence("driver-high-rate").getGearboxInputPowerKw();
    fixture.networkUnit.getHydraulicNetwork().getPipe("choke A").setChokeOpening(70.0);
    fixture.model.run();
    assertTrue(fixture.model.isModelConverged());
    assertTrue(highDriverPower > baselineDriverPower);

    double driverLimitKw = 0.5 * (baselineDriverPower + highDriverPower);
    fixture.driver.setMaxPower(driverLimitKw);
    ProcessModelSimulationEvaluator simulation = createSimulationEvaluator(fixture, fixture.sharedPowerLimitKw * 10.0);
    ProcessModelOperatingAction chokeA = ProcessModelOperatingAction.continuous("driver-limited-choke-a",
        "Driver-limited producer A choke", "Field::field network.choke.choke A.opening", 70.0, 95.0, "%",
        "synthetic common-shaft driver boundary");
    ProcessModelOperatingActionSetEvaluator candidates = new ProcessModelOperatingActionSetEvaluator(
        "driver-limited-field-actions", "Driver-limited field actions", "synthetic live field driver refinement",
        simulation, Arrays.asList(chokeA))
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "Field", "field network",
            "producer A deliverability", "synthetic well deliverability")
        .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "Field", "field network",
            "A HP route capacity", "synthetic per-line operating limit");
    CandidateSetEvaluationResult seedEvaluation = candidates.evaluate(new double[] {70.0});
    assertTrue(seedEvaluation.isBaselineRestored(), seedEvaluation.getDiagnostics().toString());
    assertTrue(seedEvaluation.isBaselineSimulationConverged(), seedEvaluation.getDiagnostics().toString());
    OperatingPointSearchResult result = new ProcessModelOperatingPointOptimizer("driver-limited-field-search",
        "Driver-limited field search", "synthetic qualified common-shaft driver boundary", candidates)
        .setInitialCandidate(new double[] {70.0}).setInitialStepFraction(1.0).setRelativeStepTolerance(0.01)
        .setMaximumBoundaryRefinementEvaluations(8).setMaximumEvaluations(24)
        .setObjectiveImprovementTolerance(1.0e-6, "synthetic export-rate comparison tolerance").optimize();

    assertTrue(result.isAcceptedPointReplayed(), result.getOutcome() + ": " + result.getDiagnostics());
    assertTrue(result.getBoundaryRefinementEvaluationCount() > 0);
    assertTrue(result.getAcceptedCandidateValues()[0] > 70.0);
    assertTrue(result.getAcceptedCandidateValues()[0] < 95.0);
    assertTrue(result.getCandidates().stream()
        .anyMatch(record -> !record.getEvaluation().isFeasible() && record.getEvaluation().getPlantConstraintEvidence()
            .stream().anyMatch(evidence -> evidence.getQualifiedConstraintId().endsWith("#driver-maximum-power")
                && evidence.hasAvailableEvidence() && !evidence.isFeasible())));
    assertTrue(result.getCandidates().stream().filter(record -> record.isBoundaryRefinement())
        .anyMatch(record -> record.getEvaluation().isFeasible() && record.getEvaluation().getPlantConstraintEvidence()
            .stream().anyMatch(evidence -> evidence.getQualifiedConstraintId().endsWith("#driver-maximum-power")
                && evidence.hasAvailableEvidence() && evidence.isFeasible())));
    PlantConstraintEvidence acceptedDriver = result.getAcceptedPointReplay().getEvaluation()
        .getPlantConstraintEvidence().stream()
        .filter(evidence -> evidence.getQualifiedConstraintId().endsWith("#driver-maximum-power")).findFirst()
        .orElseThrow(() -> new AssertionError("Missing accepted driver-power evidence"));
    assertTrue(acceptedDriver.isFeasible());
    assertEquals(driverLimitKw, acceptedDriver.getSample().getApplicableLimit(), 0.0);

    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy("driver-limited-field-envelope",
        "Driver-limited field envelope", "synthetic common-shaft driver transition diagnostics", candidates)
        .setMaximumBoundaryRefinementEvaluations(8)
        .setBoundaryRefinementRelativeTolerance(0.01, "synthetic one-percent choke-range tolerance");
    SliceResult driverSlice = study.evaluateOneDimensional(chokeA.getId(), new double[] {70.0, 95.0}, result);
    assertTrue(driverSlice.isComplete(), driverSlice.getDiagnostics().toString());
    assertEquals(1, driverSlice.getOperatingRegionTransitions().size());
    OperatingRegionTransition driverTransition = driverSlice.getOperatingRegionTransitions().get(0);
    assertEquals(OperatingRegionTransition.Kind.BECAME_INFEASIBLE, driverTransition.getKind());
    assertEquals(Outcome.FEASIBLE, driverTransition.getFromOutcome());
    assertEquals(Outcome.OTHER_MODEL_CONSTRAINT_VIOLATED, driverTransition.getToOutcome());
    assertEquals(ProcessModelOperatingAction.ValueSemantics.CONTINUOUS, driverTransition.getActionValueSemantics());
    assertTrue(driverTransition.getToConstraint().endsWith("#driver-maximum-power"),
        driverTransition.getToConstraint());
    assertFalse(driverTransition.isDiscreteAction());
    assertEquals(Boolean.FALSE, driverTransition.toEvidenceMap().get("interpolated"));
    assertEquals(1, driverSlice.getBoundaryRefinements().size());
    OperatingRegionBoundaryRefinement driverBoundary = driverSlice.getBoundaryRefinements().get(0);
    assertTrue(driverBoundary.isConverged(), driverBoundary.getStatus().name());
    assertTrue(driverBoundary.getFeasibleAxisValue() < driverBoundary.getInfeasibleAxisValue());
    assertTrue(driverBoundary.getBracketWidth() <= driverBoundary.getAbsoluteTolerance());
    LeadingConstraintEvidence feasibleBoundaryConstraint = driverBoundary.getFeasibleBoundaryConstraint();
    LeadingConstraintEvidence infeasibleBoundaryConstraint = driverBoundary.getInfeasibleBoundaryConstraint();
    assertTrue(feasibleBoundaryConstraint != null);
    assertTrue(feasibleBoundaryConstraint.getQualifiedConstraintName().endsWith("#driver-maximum-power"),
        feasibleBoundaryConstraint.getQualifiedConstraintName());
    assertTrue(feasibleBoundaryConstraint.isFeasible());
    assertTrue(Double.isFinite(feasibleBoundaryConstraint.getPhysicalMargin()));
    assertTrue(feasibleBoundaryConstraint.getPhysicalMargin() >= 0.0);
    assertTrue(infeasibleBoundaryConstraint != null);
    assertTrue(infeasibleBoundaryConstraint.getQualifiedConstraintName().endsWith("#driver-maximum-power"),
        infeasibleBoundaryConstraint.getQualifiedConstraintName());
    assertFalse(infeasibleBoundaryConstraint.isFeasible());
    assertTrue(Double.isFinite(infeasibleBoundaryConstraint.getPhysicalMargin()));
    assertTrue(infeasibleBoundaryConstraint.getPhysicalMargin() < 0.0);
    Map<String, Object> boundaryEvidence = driverBoundary.toEvidenceMap();
    assertEquals(feasibleBoundaryConstraint.toEvidenceMap(), boundaryEvidence.get("feasible_boundary_constraint"));
    assertEquals(infeasibleBoundaryConstraint.toEvidenceMap(), boundaryEvidence.get("infeasible_boundary_constraint"));
    assertTrue(driverBoundary.getSteps().stream().allMatch(step -> step.isCandidateSimulationConverged()
        && step.isBaselineRestored() && step.isBaselineSimulationConverged()));
    assertTrue(driverBoundary.getSteps().stream().filter(step -> !step.isFeasible())
        .allMatch(step -> step.getLeadingConstraint() != null
            && step.getLeadingConstraint().getQualifiedConstraintName().endsWith("#driver-maximum-power")));
    assertBaselineRestored(fixture);
  }

  /** Evaluate one independent ten-well accepted point and its choke, route and host-power slices. */
  private LifecyclePeriod evaluateLifecyclePeriod(String periodId, Fixture fixture, double sharedPowerLimitKw) {
    return evaluateLifecyclePeriod(periodId, fixture, sharedPowerLimitKw, null);
  }

  /**
   * Evaluate one independent ten-well period from the default action state or a reviewed incumbent.
   *
   * @param periodId stable lifecycle period identifier
   * @param fixture independent field-to-facility fixture
   * @param sharedPowerLimitKw shared host-power operating limit in kW
   * @param reviewedIncumbentValues optional ordered incumbent action values
   * @return complete accepted search and operating-envelope slices
   */
  private LifecyclePeriod evaluateLifecyclePeriod(String periodId, Fixture fixture, double sharedPowerLimitKw,
      double[] reviewedIncumbentValues) {
    ProcessModelSimulationEvaluator simulation = createSimulationEvaluator(fixture, sharedPowerLimitKw);
    List<ProcessModelOperatingAction> actions = createFieldActions(fixture);
    ProcessModelOperatingActionSetEvaluator candidates = new ProcessModelOperatingActionSetEvaluator(
        "ten-well-field-actions", "Ten-well field actions", "synthetic lifecycle mixed search", simulation, actions);
    for (String suffix : fixture.wellSuffixes) {
      candidates
          .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "Field", "field network",
              "producer " + suffix + " deliverability", "synthetic well deliverability")
          .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "Field", "field network",
              suffix + " HP route capacity", "synthetic per-line operating limit")
          .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "Field", "field network",
              suffix + " LP route capacity", "synthetic per-line operating limit");
    }
    double[] seed = new double[actions.size()];
    for (int index = 0; index < fixture.wellSuffixes.size(); index++) {
      seed[index] = 70.0;
      seed[index + fixture.wellSuffixes.size()] = index % 2;
    }
    if (reviewedIncumbentValues != null) {
      if (reviewedIncumbentValues.length != seed.length) {
        throw new IllegalArgumentException("Reviewed incumbent action count does not match the current action set");
      }
      seed = Arrays.copyOf(reviewedIncumbentValues, reviewedIncumbentValues.length);
    }
    OperatingPointSearchResult search = new ProcessModelOperatingPointOptimizer(periodId + "-search",
        periodId + " field search", "synthetic independent lifecycle field search", candidates)
        .setInitialCandidate(seed).setMaximumEvaluations(35).setInitialStepFraction(0.5).setRelativeStepTolerance(0.1)
        .setObjectiveImprovementTolerance(1.0e-6, "synthetic export-rate comparison tolerance").optimize();
    assertTrue(search.isAcceptedPointReplayed(), search.getOutcome() + ": " + search.getDiagnostics());

    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy("ten-well-lifecycle-envelope",
        "Ten-well lifecycle operating envelope", "synthetic independent lifecycle period", candidates);
    double[] accepted = search.getAcceptedCandidateValues();
    double nearbyChokeA = Math.max(actions.get(0).getLowerBound(), accepted[0] - 5.0);
    SliceResult choke = study.evaluateOneDimensional(actions.get(0).getId(), new double[] {nearbyChokeA, accepted[0]},
        search);
    int firstRouteIndex = fixture.wellSuffixes.size();
    double acceptedRouteA = accepted[firstRouteIndex];
    SliceResult route = study.evaluateOneDimensional(actions.get(firstRouteIndex).getId(),
        new double[] {acceptedRouteA, acceptedRouteA == 0.0 ? 1.0 : 0.0}, search);
    double nearbyChokeB = Math.max(actions.get(1).getLowerBound(), accepted[1] - 5.0);
    SliceResult hostPower = study.evaluateOneDimensional(actions.get(1).getId(),
        new double[] {nearbyChokeB, accepted[1]}, search);
    assertTrue(choke.isComplete(), choke.getDiagnostics().toString());
    assertTrue(route.isComplete(), route.getDiagnostics().toString());
    assertTrue(hostPower.isComplete(), hostPower.getDiagnostics().toString());
    UtilizationCoverageReport coverage = acceptedCoverage(fixture, search);
    assertTrue(coverage.isComplete(), coverage.getDiagnostics().toString());
    return new LifecyclePeriod(fixture, study, search, coverage, new SliceResult[] {choke, route, hostPower});
  }

  /** Build declared capacity coverage from the accepted replay without resampling the restored fixture. */
  private UtilizationCoverageReport acceptedCoverage(Fixture fixture, OperatingPointSearchResult search) {
    UtilizationCoverageReport.Builder coverage = UtilizationCoverageReport.builder("ten-well-field");
    for (String suffix : fixture.wellSuffixes) {
      coverage.expectConstraint("Field", "field network", "producer " + suffix + " deliverability")
          .expectConstraint("Field", "field network", suffix + " HP route capacity")
          .expectConstraint("Field", "field network", suffix + " LP route capacity");
    }
    for (String equipmentName : Arrays.asList("HP separator", "LP separator", "HP suction scrubber",
        "LP suction scrubber")) {
      coverage.expectConstraint("Field", equipmentName, "gas handling");
    }
    PlantConstraintScope shaftGroup = PlantConstraintScope.coupledGroup("synthetic field", "Host",
        "export common shaft");
    for (String constraintName : Arrays.asList("common-speed-deviation", "shaft-maximum-speed", "shaft-power-balance",
        "driver-speed-envelope", "driver-maximum-power", "gearbox-maximum-input-power", "shaft-maximum-torque")) {
      coverage.expectPlantConstraint(shaftGroup.getStableId() + "#" + PlantConstraintScope.escape(constraintName));
    }
    for (String equipmentName : Arrays.asList("HP compressor", "LP compressor")) {
      PlantConstraintScope compressor = PlantConstraintScope.equipment("synthetic field", "Host", equipmentName);
      coverage
          .expectPlantConstraint(compressor.getStableId() + "#" + PlantConstraintScope.escape("compressor-map-margin"));
    }
    coverage.expectPlantConstraint(sharedPowerDefinition().getQualifiedId());
    return coverage.evidence(search.getAcceptedPointReplay().getEvaluation().getInstalledEquipmentCapacityEvidence())
        .plantEvidence(search.getAcceptedPointReplay().getEvaluation().getPlantConstraintEvidence()).build();
  }

  /**
   * Reuse one ten-well topology across quasi-steady depletion and host derating periods. Each period must obtain fresh
   * constraint evidence, retain the original maps and restore its operating actions, including after rejection. These
   * are synthetic operating snapshots, not a reservoir forecast or a transient integration.
   */
  @Test
  void replaysLifecyclePeriodsWithMovingHostLimits() {
    Fixture fixture = createFixture(10);
    ProcessModelOperatingActionSetEvaluator candidates = new ProcessModelOperatingActionSetEvaluator(
        "lifecycle-field-actions", "Lifecycle field actions", "synthetic quasi-steady depletion and host derating",
        createSimulationEvaluator(fixture), createFieldActions(fixture));
    for (String suffix : fixture.wellSuffixes) {
      candidates
          .requireHydraulicConstraint(HydraulicLimitRole.RESERVOIR_DELIVERABILITY, "Field", "field network",
              "producer " + suffix + " deliverability", "synthetic well deliverability")
          .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "Field", "field network",
              suffix + " HP route capacity", "synthetic per-line operating limit")
          .requireHydraulicConstraint(HydraulicLimitRole.GATHERING_HYDRAULICS, "Field", "field network",
              suffix + " LP route capacity", "synthetic per-line operating limit");
    }
    double[] baseline = new double[20];
    for (int index = 0; index < 10; index++) {
      baseline[index] = 70.0;
      baseline[index + 10] = index % 2;
    }
    double[] reservoirPressures = new double[10];
    for (int index = 0; index < 10; index++) {
      reservoirPressures[index] = fixture.networkUnit.getCoupler().getBindings().get(index).getWellFlow()
          .getInletStream().getPressure("bara");
    }
    Object highPressureChart = fixture.highPressureCompressor.getCompressorChart();
    Object lowPressureChart = fixture.lowPressureCompressor.getCompressorChart();
    double originalPowerLimit = fixture.sharedPowerLimitKw;
    CapacityConstraint separatorLimit = fixture.highPressureSeparator.getCapacityConstraints().get("gas handling");
    CapacityConstraint scrubberLimit = fixture.lowPressureScrubber.getCapacityConstraints().get("gas handling");
    double originalSeparatorLimit = separatorLimit.getOperatingLimit();
    double originalScrubberLimit = scrubberLimit.getOperatingLimit();
    CandidateSetEvaluationResult original = candidates.evaluate(baseline);
    assertTrue(original.isFeasible(), original.getDiagnostics().toString());
    double originalExport = fixture.exportValve.getOutletStream().getFlowRate("kg/hr");
    PlantConstraintEvidence originalPower = sharedPowerEvidence(original);
    double previousExport = originalExport;
    try {
      for (int period = 1; period <= 3; period++) {
        for (int index = 0; index < 10; index++) {
          fixture.networkUnit.getCoupler().getBindings().get(index).getWellFlow().getInletStream()
              .setPressure(reservoirPressures[index] - period, "bara");
        }
        CandidateSetEvaluationResult available = candidates.evaluate(baseline);
        assertTrue(available.isFeasible(), "period=" + period + " " + available.getDiagnostics());
        assertLifecycleRestored(fixture, available);
        double currentExport = fixture.exportValve.getOutletStream().getFlowRate("kg/hr");
        assertTrue(currentExport < previousExport * 0.999, "Depletion must reduce export beyond solver noise");
        previousExport = currentExport;
        double currentPower = fixture.model.getPower("kW");
        assertFalse(originalPower.getSample().getCalculationId()
            .equals(sharedPowerEvidence(available).getSample().getCalculationId()));
        assertEquals(currentPower, sharedPowerEvidence(available).getSample().getSampledValue(),
            Math.max(1.0e-8, currentPower * 1.0e-6));
        assertEquals(fixture.highPressureCompressor.getPower("kW") + fixture.lowPressureCompressor.getPower("kW")
            + fixture.mappedInjectionPowerNetwork.getPower("kW"), currentPower, 1.0e-8);

        // Move one declared operating restriction, not the physical model or its acceptance tolerance.
        if (period == 1) {
          separatorLimit.setOperatingLimit(
              fixture.highPressureSeparator.getInletStreams().get(0).getFlowRate("kg/hr") * 0.95,
              ConstraintSource.USER_RULE, "synthetic period 1 separator derating");
        } else if (period == 2) {
          scrubberLimit.setOperatingLimit(
              fixture.lowPressureScrubber.getInletStreams().get(0).getFlowRate("kg/hr") * 0.95,
              ConstraintSource.USER_RULE, "synthetic period 2 scrubber derating");
        } else {
          fixture.sharedPowerLimitKw = currentPower * 0.95;
        }
        CandidateSetEvaluationResult restricted = candidates.evaluate(baseline);
        assertFalse(restricted.isFeasible(), "A previous feasible snapshot must not mask a new period limit");
        logger.info("Lifecycle period {}: export={} kg/hr, shared power={} kW, restricted feasible={}", period,
            currentExport, currentPower, restricted.isFeasible());
        assertLifecycleRestored(fixture, restricted);
        if (period < 3) {
          String equipment = period == 1 ? "HP separator" : "LP suction scrubber";
          assertTrue(restricted.getInstalledEquipmentCapacityEvidence().stream()
              .anyMatch(e -> equipment.equals(e.getEquipmentName()) && !e.isFeasible()));
        } else {
          assertFalse(sharedPowerEvidence(restricted).isFeasible());
          assertEquals(fixture.sharedPowerLimitKw, sharedPowerEvidence(restricted).getSample().getApplicableLimit(),
              0.0);
        }
        assertTrue(available.isFeasible(), "Previously frozen evidence must remain a historical snapshot");
        separatorLimit.setOperatingLimit(originalSeparatorLimit, ConstraintSource.USER_RULE,
            "synthetic HP separator operating procedure");
        scrubberLimit.setOperatingLimit(originalScrubberLimit, ConstraintSource.USER_RULE,
            "synthetic LP suction scrubber operating procedure");
        fixture.sharedPowerLimitKw = originalPowerLimit;
        assertTrue(candidates.evaluate(baseline).isFeasible(), "Fresh replay must recover after restoring limits");
        assertTrue(highPressureChart == fixture.highPressureCompressor.getCompressorChart());
        assertTrue(lowPressureChart == fixture.lowPressureCompressor.getCompressorChart());
      }
    } finally {
      for (int index = 0; index < 10; index++) {
        fixture.networkUnit.getCoupler().getBindings().get(index).getWellFlow().getInletStream()
            .setPressure(reservoirPressures[index], "bara");
      }
      separatorLimit.setOperatingLimit(originalSeparatorLimit, ConstraintSource.USER_RULE,
          "synthetic HP separator operating procedure");
      scrubberLimit.setOperatingLimit(originalScrubberLimit, ConstraintSource.USER_RULE,
          "synthetic LP suction scrubber operating procedure");
      fixture.sharedPowerLimitKw = originalPowerLimit;
      fixture.model.run();
    }
    assertBaselineRestored(fixture);
    assertEquals(originalExport, fixture.exportValve.getOutletStream().getFlowRate("kg/hr"), originalExport * 1.0e-5);
    assertTrue(originalPower.isFeasible());
    assertEquals(originalPowerLimit, originalPower.getSample().getApplicableLimit(), 0.0);
  }

  /**
   * Retrieve available shared-power evidence from one immutable candidate result.
   *
   * @param result evaluated candidate
   * @return shared host-power evidence
   */
  private PlantConstraintEvidence sharedPowerEvidence(CandidateSetEvaluationResult result) {
    return result.getPlantConstraintEvidence().stream()
        .filter(e -> sharedPowerDefinition().getQualifiedId().equals(e.getQualifiedConstraintId()))
        .filter(PlantConstraintEvidence::hasAvailableEvidence).findFirst()
        .orElseThrow(() -> new AssertionError("Missing finite shared-power evidence"));
  }

  /**
   * Check restoration and conservation at the current period's reservoir boundary.
   *
   * @param fixture same canonical field and facility
   * @param result completed candidate evidence
   */
  private void assertLifecycleRestored(Fixture fixture, CandidateSetEvaluationResult result) {
    assertTrue(result.isCandidateSimulationConverged());
    assertTrue(result.isBaselineRestored());
    assertTrue(result.isBaselineSimulationConverged());
    assertTrue(fixture.networkUnit.getLastCouplingResult().isConverged());
    assertEquals(0.0, fixture.networkUnit.getLastCouplingResult().getMassBalanceResidualKgS(), 1.0e-6);
    for (int index = 0; index < fixture.wellSuffixes.size(); index++) {
      String suffix = fixture.wellSuffixes.get(index);
      assertEquals(70.0, fixture.networkUnit.getHydraulicNetwork().getPipe("choke " + suffix).getChokeOpening(), 0.0);
      assertEquals(index % 2, fixture.networkUnit.getExclusiveRouteSelection("producer " + suffix + " pressure level"));
      assertEquals(LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH,
          fixture.networkUnit.getHydraulicNetwork().getEffectiveHydraulicModelType(suffix + " HP route"));
    }
  }

  /** Create all ten-well choke and HP/LP route actions in deterministic groups. */
  private List<ProcessModelOperatingAction> createFieldActions(Fixture fixture) {
    List<ProcessModelOperatingAction> actions = new ArrayList<ProcessModelOperatingAction>();
    for (String suffix : fixture.wellSuffixes) {
      actions.add(ProcessModelOperatingAction.continuous("choke-" + suffix.toLowerCase(),
          "Producer " + suffix + " choke", "Field::field network.choke.choke " + suffix + ".opening", 50.0, 95.0, "%",
          "synthetic choke travel envelope"));
    }
    for (String suffix : fixture.wellSuffixes) {
      actions.add(ProcessModelOperatingAction.discrete("route-" + suffix.toLowerCase(),
          "Producer " + suffix + " separator pressure level",
          "Field::field network.route.producer " + suffix + " pressure level.selection", new double[] {0.0, 1.0}, "-",
          "synthetic qualified HP/LP route line-up"));
    }
    return actions;
  }

  /** Build and calibrate the complete synthetic field-to-host process. */
  private Fixture createFixture() {
    return createFixture(2);
  }

  /**
   * Build and calibrate a deterministic synthetic field-to-export process.
   *
   * @param wellCount number of live wells between 2 and 20
   * @return complete coupled fixture
   */
  private Fixture createFixture(int wellCount) {
    if (wellCount < 2 || wellCount > 20) {
      throw new IllegalArgumentException("Synthetic acceptance fixture requires 2 to 20 wells");
    }
    FieldNetworkTopology topology = new FieldNetworkTopology("field network hydraulics");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(gas(140.0));
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    List<String> wellSuffixes = new ArrayList<String>();
    for (int index = 0; index < wellCount; index++) {
      String suffix = String.valueOf((char) ('A' + index));
      wellSuffixes.add(suffix);
      addWellRoute(topology, suffix, 75.0, 70.0);
    }
    topology.addFixedPressureSink("HP host", "V-HP", NodeRole.HOST, Service.PRODUCTION, 45.0, 0.0);
    topology.addFixedPressureSink("LP host", "V-LP", NodeRole.HOST, Service.PRODUCTION, 30.0, 0.0);
    for (int index = 0; index < wellSuffixes.size(); index++) {
      addPressureRoutes(topology, wellSuffixes.get(index), index % 2 == 0);
    }

    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setMaximumIterations(100);
    coupler.setTolerances(1.0e-8, 1.0e-6);
    coupler.setRelaxationFactor(0.7);
    for (int index = 0; index < wellSuffixes.size(); index++) {
      String suffix = wellSuffixes.get(index);
      double reservoirPressureBara = index == 0 ? 190.0 : index == 1 ? 185.0 : 184.0 - index;
      double productivityIndex = index == 0 ? 1.1e-7 : index == 1 ? 0.9e-7 : (0.90 - 0.02 * index) * 1.0e-7;
      coupler.bindProductionWell("producer " + suffix,
          well("producer " + suffix, reservoirPressureBara, productivityIndex));
    }
    FieldWellNetworkProcessUnit networkUnit = new FieldWellNetworkProcessUnit("field network", coupler);
    for (String suffix : wellSuffixes) {
      networkUnit.registerExclusiveRouteSelector("producer " + suffix + " pressure level",
          Arrays.asList(suffix + " HP route", suffix + " LP route"), "synthetic producer " + suffix + " HP/LP line-up");
    }
    networkUnit.run(UUID.randomUUID());
    assertTrue(networkUnit.solved(),
        networkUnit.getLastCouplingResult().getMessage() + "; rate residual="
            + networkUnit.getLastCouplingResult().getMaximumRateResidualKgS() + "; pressure residual="
            + networkUnit.getLastCouplingResult().getMaximumPressureResidualBar() + "; network residual="
            + networkUnit.getLastCouplingResult().getNetworkResidualPa() + "; mass balance="
            + networkUnit.getLastCouplingResult().getMassBalanceResidualKgS());
    assertTrue(network.isProductionOptimizationApplicable());

    Separator highPressureSeparator = new Separator("HP separator", networkUnit.getOutletStream("HP host"));
    Separator lowPressureSeparator = new Separator("LP separator", networkUnit.getOutletStream("LP host"));
    Separator highPressureScrubber = new Separator("HP suction scrubber", highPressureSeparator.getGasOutStream());
    Separator lowPressureScrubber = new Separator("LP suction scrubber", lowPressureSeparator.getGasOutStream());
    Compressor highPressureCompressor = new Compressor("HP compressor", highPressureScrubber.getGasOutStream());
    Compressor lowPressureCompressor = new Compressor("LP compressor", lowPressureScrubber.getGasOutStream());
    highPressureCompressor.setOutletPressure(100.0, "bara");
    lowPressureCompressor.setOutletPressure(100.0, "bara");
    Mixer exportMixer = new Mixer("gas export boundary");
    exportMixer.addStream(highPressureCompressor.getOutletStream());
    exportMixer.addStream(lowPressureCompressor.getOutletStream());
    ThrottlingValve exportValve = new ThrottlingValve("gas export pressure control", exportMixer.getOutletStream());
    exportValve.setOutletPressure(100.0, "bara");
    LoopedPipeNetwork mappedInjectionPowerNetwork = createMappedInjectionPowerNetwork();

    ProcessSystem process = new ProcessSystem("field to facility");
    process.add(networkUnit);
    process.add(highPressureSeparator);
    process.add(lowPressureSeparator);
    process.add(highPressureScrubber);
    process.add(lowPressureScrubber);
    process.add(highPressureCompressor);
    process.add(lowPressureCompressor);
    process.add(exportMixer);
    process.add(exportValve);
    process.add(mappedInjectionPowerNetwork);
    ProcessModel model = new ProcessModel();
    model.add("Field", process);
    model.run();
    assertTrue(model.isModelConverged());
    assertTrue(mappedInjectionPowerNetwork.isConverged());
    assertTrue(Double.isFinite(mappedInjectionPowerNetwork.getPower("kW")));
    assertTrue(mappedInjectionPowerNetwork.getPower("kW") > 0.0);
    highPressureCompressor.generateCompressorChart("normal curves", 5);
    lowPressureCompressor.generateCompressorChart("normal curves", 5);
    highPressureCompressor.getCompressorChart().setUseCompressorChart(true);
    lowPressureCompressor.getCompressorChart().setUseCompressorChart(true);
    highPressureCompressor.reinitializeCapacityConstraints();
    lowPressureCompressor.reinitializeCapacityConstraints();
    model.run();
    assertTrue(model.isModelConverged());

    MechanicalShaft shaft = new MechanicalShaft("export common shaft");
    shaft.setSpeed(highPressureCompressor.getSpeed());
    shaft.setMaximumSpeed(highPressureCompressor.getSpeed() * 1.2);
    highPressureCompressor.getEnergyPort("shaftPower").connect(shaft);
    lowPressureCompressor.getEnergyPort("shaftPower").connect(shaft);
    UUID shaftCalculationId = UUID.randomUUID();
    highPressureCompressor.run(shaftCalculationId);
    lowPressureCompressor.run(shaftCalculationId);
    model.run();
    assertTrue(model.isModelConverged());
    EnergyPort driverPort = new EnergyPort("export driver", EnergyType.SHAFT_WORK, EnergyPortDirection.OUTPUT,
        EnergyPortMode.CALCULATED);
    driverPort.connect(shaft);
    double baselinePowerKw = model.getPower("kW");
    double expectedPowerKw = highPressureCompressor.getPower("kW") + lowPressureCompressor.getPower("kW")
        + mappedInjectionPowerNetwork.getPower("kW");
    assertEquals(expectedPowerKw, baselinePowerKw, Math.max(1.0e-9, expectedPowerKw * 1.0e-10));
    double driverLimitKw = baselinePowerKw * 1.8 / 0.98;
    CompressorDriver driver = new CompressorDriver(DriverType.ELECTRIC_MOTOR, driverLimitKw);
    driver.setMaxPower(driverLimitKw);
    driver.setMinSpeed(shaft.getSpeed() * 0.8);
    driver.setMaxSpeed(shaft.getSpeed() * 1.2);
    driverPort.setDuty(driverLimitKw, "kW");
    Gearbox gearbox = new Gearbox("export gearbox");
    gearbox.setEfficiency(0.98);
    gearbox.setSpeedRatio(1.0);
    gearbox.setMaximumInputPower(driverLimitKw * 1.2 * 1000.0);

    double initialRouteAHigh = massFlow(network, "A HP route");
    network.getPipe("choke A").setChokeOpening(90.0);
    model.run();
    assertTrue(model.isModelConverged());
    assertTrue(network.isProductionOptimizationApplicable());
    double highRouteAHigh = massFlow(network, "A HP route");
    assertTrue(highRouteAHigh > initialRouteAHigh);
    network.getPipe("choke A").setChokeOpening(70.0);
    model.run();
    assertTrue(model.isModelConverged());
    double baselineRouteAHigh = massFlow(network, "A HP route");
    assertTrue(highRouteAHigh > baselineRouteAHigh * 1.01,
        "Choke sweep must change route flow by more than one percent, not solver noise");
    double routeAHighOperatingLimit = 0.5 * (baselineRouteAHigh + highRouteAHigh);

    configureInstalledLimits(networkUnit, highPressureSeparator, lowPressureSeparator, highPressureScrubber,
        lowPressureScrubber, network, routeAHighOperatingLimit, wellSuffixes);
    PlantSharedResourceEvidence baselinePowerEvidence = PlantSharedResourceEvidence.fromProcessModelShaftPower(
        sharedPowerDefinition(), "mapped-power-baseline", baselinePowerKw * 1.8, model, model.isModelConverged(),
        "synthetic mapped network and common-shaft power baseline");
    assertTrue(baselinePowerEvidence.isComplete(), baselinePowerEvidence.getDiagnostics().toString());
    assertEquals(baselinePowerKw, baselinePowerEvidence.getAggregateValue(),
        Math.max(1.0e-9, baselinePowerKw * 1.0e-10));
    assertEquals(1, baselinePowerEvidence.getParticipants().size());
    assertEquals("Field", baselinePowerEvidence.getParticipants().get(0).getSourceId());
    assertEquals(baselinePowerKw, baselinePowerEvidence.getParticipants().get(0).getValue(),
        Math.max(1.0e-9, baselinePowerKw * 1.0e-10));
    return new Fixture(model, networkUnit, mappedInjectionPowerNetwork, highPressureSeparator, lowPressureSeparator,
        highPressureScrubber, lowPressureScrubber, highPressureCompressor, lowPressureCompressor, exportMixer,
        exportValve, shaft, driverPort, driver, gearbox, baselinePowerKw * 1.8, baselineRouteAHigh, wellSuffixes);
  }

  /**
   * Build one independent curve-backed injection utility on the canonical field-network graph.
   *
   * @return convergable mapped pump network included in the host shared-power budget
   */
  private LoopedPipeNetwork createMappedInjectionPowerNetwork() {
    SystemInterface liquid = new SystemSrkEos(298.15, 5.0);
    liquid.addComponent("nC10", 0.70);
    liquid.addComponent("nC16", 0.30);
    liquid.setMixingRule("classic");

    FieldNetworkTopology topology = new FieldNetworkTopology("mapped injection utility");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(liquid);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(100);
    network.setTolerance(500.0);
    topology.addPressureSource("pump suction", "HOST-P401", NodeRole.INJECTION_SOURCE, Service.INJECTION, 5.0, 0.0,
        0.0);
    topology.addFixedPressureSink("pump delivery", "P-401-D", NodeRole.HOST, Service.INJECTION, 12.0, 0.0);

    Pump pump = new Pump("mapped injection utility pump");
    pump.setSpeed(2500.0);
    pump.getPumpChart().setCurves(new double[] {0.200, 298.15, 1.0, 1.0, 850.0}, new double[] {2000.0, 3000.0},
        new double[][] {{20.0, 40.0, 60.0}, {30.0, 60.0, 90.0}},
        new double[][] {{80.0, 70.0, 50.0}, {180.0, 158.0, 113.0}},
        new double[][] {{65.0, 75.0, 68.0}, {68.0, 80.0, 72.0}});
    pump.getPumpChart().setHeadUnit("meter");
    pump.getPumpChart().setUsePumpChart(true);
    topology.addPumpWithCurve("mapped injection pump", "P-401", Service.INJECTION, FlowDirection.FROM_TO,
        "pump suction", "suction", "pump delivery", "discharge", pump).setPumpSpeed(2500.0);
    return network;
  }

  /** Add one live well node, header and production choke. */
  private void addWellRoute(FieldNetworkTopology topology, String suffix, double initialPressureBara,
      double chokeOpening) {
    String wellNode = "producer " + suffix;
    String headerNode = suffix + " header";
    topology.addLiveWellNode(wellNode, "XT-" + suffix, NodeRole.PRODUCTION_WELL, Service.PRODUCTION,
        initialPressureBara, -200.0);
    topology.addJunction(headerNode, "MA-" + suffix, NodeRole.MANIFOLD, Service.PRODUCTION, -200.0);
    topology.getHydraulicNetwork().setNodePressure(headerNode, initialPressureBara - 5.0);
    // A finite choke pressure drop makes the operating-limit sweep resolve physical flow changes.
    // Cv=30 leaves the 70-to-90 percent sweep at solver-noise scale for this small synthetic well.
    topology.getHydraulicNetwork().addChoke(wellNode, headerNode, "choke " + suffix, 0.3, chokeOpening);
    topology.registerExistingEdge("choke " + suffix, "XV-" + suffix, EdgeRole.CHOKE, Service.PRODUCTION,
        FlowDirection.FROM_TO, "well", "header");
  }

  /** Add mutually exclusive well-specific routes to distinct fixed-pressure host levels. */
  private void addPressureRoutes(FieldNetworkTopology topology, String suffix, boolean highPressureSelected) {
    String header = suffix + " header";
    topology.addPipe(suffix + " HP route", "FL-" + suffix + "-HP", EdgeRole.FLOWLINE, Service.PRODUCTION,
        FlowDirection.FROM_TO, header, "HP outlet", "HP host", suffix + " arrival", 1200.0, 0.20,
        LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe(suffix + " LP route", "FL-" + suffix + "-LP", EdgeRole.FLOWLINE, Service.PRODUCTION,
        FlowDirection.FROM_TO, header, "LP outlet", "LP host", suffix + " arrival", 1200.0, 0.20,
        LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.getHydraulicNetwork().getPipe(suffix + " HP route").setAvailability(highPressureSelected ? 1.0 : 0.0);
    topology.getHydraulicNetwork().getPipe(suffix + " LP route").setAvailability(highPressureSelected ? 0.0 : 1.0);
  }

  /** Add operating/default limits for wells, lines and both separators. */
  private void configureInstalledLimits(FieldWellNetworkProcessUnit networkUnit, Separator highPressureSeparator,
      Separator lowPressureSeparator, Separator highPressureScrubber, Separator lowPressureScrubber,
      LoopedPipeNetwork network, double routeAHighOperatingLimit, List<String> wellSuffixes) {
    networkUnit.clearCapacityConstraints();
    for (String suffix : wellSuffixes) {
      String nodeId = "producer " + suffix;
      networkUnit.addCapacityConstraint(maximumConstraint(nodeId + " deliverability", "kg/hr",
          wellRate(networkUnit, nodeId) * 1.5, wellRate(networkUnit, nodeId) * 1.3, () -> wellRate(networkUnit, nodeId),
          "synthetic well " + suffix + " operating procedure"));
      double highLimit = "A".equals(suffix) ? routeAHighOperatingLimit
          : Math.max(100.0, massFlow(network, suffix + " HP route") * 1.5);
      addLineConstraint(networkUnit, network, suffix + " HP route", highLimit);
      addLineConstraint(networkUnit, network, suffix + " LP route",
          Math.max(100.0, massFlow(network, suffix + " LP route") * 1.5));
    }

    highPressureSeparator.clearCapacityConstraints();
    lowPressureSeparator.clearCapacityConstraints();
    highPressureScrubber.clearCapacityConstraints();
    lowPressureScrubber.clearCapacityConstraints();
    double highSeparatorFlow = highPressureSeparator.getInletStreams().get(0).getFlowRate("kg/hr");
    double lowSeparatorFlow = lowPressureSeparator.getInletStreams().get(0).getFlowRate("kg/hr");
    double totalSeparatorFlow = highSeparatorFlow + lowSeparatorFlow;
    highPressureSeparator.addCapacityConstraint(maximumConstraint("gas handling", "kg/hr", totalSeparatorFlow * 1.5,
        totalSeparatorFlow * 1.2, () -> highPressureSeparator.getInletStreams().get(0).getFlowRate("kg/hr"),
        "synthetic HP separator operating procedure"));
    lowPressureSeparator.addCapacityConstraint(maximumConstraint("gas handling", "kg/hr", totalSeparatorFlow * 1.5,
        totalSeparatorFlow * 1.2, () -> lowPressureSeparator.getInletStreams().get(0).getFlowRate("kg/hr"),
        "synthetic LP separator operating procedure"));
    highPressureScrubber.addCapacityConstraint(maximumConstraint("gas handling", "kg/hr", totalSeparatorFlow * 1.5,
        totalSeparatorFlow * 1.2, () -> highPressureScrubber.getInletStreams().get(0).getFlowRate("kg/hr"),
        "synthetic HP suction scrubber operating procedure"));
    lowPressureScrubber.addCapacityConstraint(maximumConstraint("gas handling", "kg/hr", totalSeparatorFlow * 1.5,
        totalSeparatorFlow * 1.2, () -> lowPressureScrubber.getInletStreams().get(0).getFlowRate("kg/hr"),
        "synthetic LP suction scrubber operating procedure"));
  }

  /**
   * Reconfigure one independent lifecycle fixture with generous field limits and a selected separation-capacity
   * multiplier.
   */
  private void configureLifecycleCapacityLimits(Fixture fixture, double separationCapacityMultiplier) {
    FieldWellNetworkProcessUnit networkUnit = fixture.networkUnit;
    LoopedPipeNetwork network = networkUnit.getHydraulicNetwork();
    networkUnit.clearCapacityConstraints();
    double totalBaselineRate = 0.0;
    for (String suffix : fixture.wellSuffixes) {
      totalBaselineRate += wellRate(networkUnit, "producer " + suffix);
    }
    for (String suffix : fixture.wellSuffixes) {
      String nodeId = "producer " + suffix;
      networkUnit.addCapacityConstraint(maximumConstraint(nodeId + " deliverability", "kg/hr",
          wellRate(networkUnit, nodeId) * 125.0, wellRate(networkUnit, nodeId) * 100.0,
          () -> wellRate(networkUnit, nodeId), "synthetic lifecycle well limit"));
      addLineConstraint(networkUnit, network, suffix + " HP route", totalBaselineRate * 3.0);
      addLineConstraint(networkUnit, network, suffix + " LP route", totalBaselineRate * 3.0);
    }

    for (Separator separator : Arrays.asList(fixture.highPressureSeparator, fixture.lowPressureSeparator,
        fixture.highPressureScrubber, fixture.lowPressureScrubber)) {
      separator.clearCapacityConstraints();
      double baselineFlow = separator.getInletStreams().get(0).getFlowRate("kg/hr");
      double operatingLimit = baselineFlow * separationCapacityMultiplier;
      separator.addCapacityConstraint(maximumConstraint("gas handling", "kg/hr", operatingLimit * 1.25, operatingLimit,
          () -> separator.getInletStreams().get(0).getFlowRate("kg/hr"), "synthetic lifecycle separation limit"));
    }
  }

  /** Add one per-line capacity constraint with a preserved design rating and configured operating limit. */
  private void addLineConstraint(FieldWellNetworkProcessUnit unit, LoopedPipeNetwork network, String lineName,
      double operatingLimit) {
    unit.addCapacityConstraint(maximumConstraint(lineName + " capacity", "kg/hr", operatingLimit * 1.25, operatingLimit,
        () -> massFlow(network, lineName), "synthetic line operating procedure"));
  }

  /** Build one configured maximum-directed capacity constraint. */
  private CapacityConstraint maximumConstraint(String name, String unit, double designLimit, double operatingLimit,
      java.util.function.DoubleSupplier valueSupplier, String operatingReference) {
    return new CapacityConstraint(name, unit, ConstraintType.HARD).setDesignValue(designLimit)
        .setSource(ConstraintSource.VENDOR_DATASHEET, "synthetic installed rating").setDataSource("synthetic design")
        .setSeverity(ConstraintSeverity.HARD).setConfidence(1.0).setValidityRange(0.0, designLimit * 1.5)
        .setOperatingLimit(operatingLimit, ConstraintSource.USER_RULE, operatingReference)
        .setOperatingLimitConfidence(1.0).setOperatingLimitValidityRange(0.0, designLimit * 1.5)
        .setValueSupplier(valueSupplier);
  }

  /** Build one production WellFlow. */
  private WellFlow well(String name, double reservoirPressureBara, double productivityIndex) {
    Stream reservoir = new Stream(name + " reservoir", gas(reservoirPressureBara));
    reservoir.setFlowRate(0.03, "MSm3/day");
    reservoir.run();
    WellFlow well = new WellFlow(name + " well");
    well.setInletStream(reservoir);
    well.setWellProductionIndex(productivityIndex);
    return well;
  }

  /** Get one well's latest applied production rate in kg/hr. */
  private double wellRate(FieldWellNetworkProcessUnit unit, String nodeId) {
    for (WellResult result : unit.getLastCouplingResult().getWellResults()) {
      if (nodeId.equals(result.getNodeId())) {
        return result.getAppliedRateKgS() * 3600.0;
      }
    }
    throw new IllegalArgumentException("Missing well result " + nodeId);
  }

  /** Get absolute network-edge mass flow in kg/hr. */
  private double massFlow(LoopedPipeNetwork network, String lineName) {
    return Math.abs(network.getPipe(lineName).getFlowRate()) * 3600.0;
  }

  /** Create the exact shared shaft-power definition for the host. */
  private PlantConstraintDefinition sharedPowerDefinition() {
    return PlantConstraintDefinition
        .builder("total-shaft-power", PlantConstraintScope.sharedResource("Host", "shared plant power"))
        .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.SHARED_BUDGET)
        .limitDirection(PlantConstraintDefinition.LimitDirection.MAXIMUM)
        .category(PlantConstraintDefinition.Category.OPERATING).severity(ConstraintSeverity.HARD).unit("kW")
        .basis("compressor and pump shaft power").provenance("synthetic shared plant power budget")
        .participant(PlantConstraintParticipant.direct("Field", "kW", "compressor and pump shaft power")).build();
  }

  /**
   * Configure the common objective, equipment, shaft, shared-power and export-boundary evidence.
   *
   * @param fixture complete coupled field-to-export fixture
   * @return configured fail-closed evaluator
   */
  private ProcessModelSimulationEvaluator createSimulationEvaluator(Fixture fixture) {
    return createSimulationEvaluator(fixture, () -> fixture.sharedPowerLimitKw);
  }

  /**
   * Configure the common evaluator with one period-specific shared-power limit.
   *
   * @param fixture complete coupled field-to-export fixture
   * @param sharedPowerLimitKw period-specific shared plant power limit in kW
   * @return configured fail-closed evaluator
   */
  private ProcessModelSimulationEvaluator createSimulationEvaluator(Fixture fixture, double sharedPowerLimitKw) {
    return createSimulationEvaluator(fixture, () -> sharedPowerLimitKw);
  }

  /**
   * Configure a lower-level evaluator that resamples an explicitly changing host budget.
   *
   * @param fixture complete coupled field-to-export fixture
   * @param sharedPowerLimitKw supplier of the current host budget in kW
   * @return configured fail-closed evaluator; not a reusable accepted lifecycle search
   */
  private ProcessModelSimulationEvaluator createSimulationEvaluator(Fixture fixture,
      DoubleSupplier sharedPowerLimitKw) {
    ProcessModelSimulationEvaluator simulation = new ProcessModelSimulationEvaluator(fixture.model);
    simulation.setIncludeStrategyCapacityConstraints(false);
    simulation.addObjective("validated gas export",
        model -> Math.abs(fixture.exportValve.getOutletStream().getFlowRate("kg/hr")),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    simulation.getObjectives().get(0).setUnit("kg/hr");
    simulation.addEquipmentCapacityConstraints();

    PlantCommonShaftEvidence commonShaftRegistration = fixture.commonShaftEvidence("common-shaft-registration");
    assertTrue(commonShaftRegistration.isComplete(), commonShaftRegistration.getDiagnostics().toString());
    simulation.addCommonShaftConstraintGroup("export-common-shaft", commonShaftRegistration,
        (model, calculationId) -> fixture.commonShaftEvidence(calculationId));
    PlantConstraintDefinition sharedPower = sharedPowerDefinition();
    simulation
        .addPlantConstraint(sharedPower,
            (model, calculationId) -> PlantSharedResourceEvidence.fromProcessModelShaftPower(sharedPower, calculationId,
                sharedPowerLimitKw.getAsDouble(), model, model.isModelConverged(), "synthetic shared plant power limit")
                .toPlantConstraintSample());

    ProcessBoundaryConstraintEvidence.Metadata exportPressure = new ProcessBoundaryConstraintEvidence.Metadata(
        "gas-export-pressure", "Field", "gas export boundary", ProcessBoundaryConstraintEvidence.Kind.EXPORT_CAPACITY,
        ProcessBoundaryConstraintEvidence.FlowDirection.OUT_OF_PROCESS, NetworkDecisionVariable.RateBasis.MASS,
        "synthetic gas export specification", 1.0, null, null,
        ProcessBoundaryConstraintEvidence.ApplicabilityStatus.APPLICABLE, "pressure", "NeqSim process replay", null,
        -1);
    simulation.addBoundaryConstraint("gas export pressure", exportPressure,
        model -> ProcessBoundaryConstraintEvidence.Sample
            .available(fixture.exportValve.getOutletStream().getPressure("bara")),
        ProcessModelSimulationEvaluator.ConstraintDefinition.Type.EQUALITY, 100.0, Double.POSITIVE_INFINITY, 0.1,
        "bara", true, 1.0, 0.1);
    return simulation;
  }

  /** Verify every action and the physical network returned to the baseline route. */
  private void assertBaselineRestored(Fixture fixture) {
    LoopedPipeNetwork network = fixture.networkUnit.getHydraulicNetwork();
    for (int index = 0; index < fixture.wellSuffixes.size(); index++) {
      String suffix = fixture.wellSuffixes.get(index);
      int expectedRoute = index % 2;
      assertEquals(70.0, network.getPipe("choke " + suffix).getChokeOpening(), 0.0);
      assertEquals(expectedRoute,
          fixture.networkUnit.getExclusiveRouteSelection("producer " + suffix + " pressure level"));
      assertEquals(expectedRoute == 0 ? 1.0 : 0.0, network.getPipe(suffix + " HP route").getAvailability(), 0.0);
      assertEquals(expectedRoute == 1 ? 1.0 : 0.0, network.getPipe(suffix + " LP route").getAvailability(), 0.0);
    }
    assertEquals(fixture.baselineRouteAHigh, massFlow(network, "A HP route"), fixture.baselineRouteAHigh * 1.0e-5);
    assertTrue(fixture.highPressureScrubber.solved());
    assertTrue(fixture.lowPressureScrubber.solved());
    assertTrue(fixture.exportMixer.solved());
    assertTrue(fixture.exportValve.solved());
    assertEquals(100.0, fixture.exportValve.getOutletStream().getPressure("bara"), 1.0e-6);
    assertTrue(fixture.model.isModelConverged());
    assertTrue(fixture.networkUnit.getLastCouplingResult().isConverged());
    assertTrue(fixture.mappedInjectionPowerNetwork.isConverged());
    assertTrue(Double.isFinite(fixture.mappedInjectionPowerNetwork.getPower("kW")));
  }

  /** Create a lean SRK gas for every live well and route. */
  private SystemInterface gas(double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(330.15, pressureBara);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return fluid;
  }
}
