package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorDriver;
import neqsim.process.equipment.compressor.DriverType;
import neqsim.process.equipment.energy.Gearbox;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.MechanicalShaft;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelOperatingActionEvaluator.HydraulicLimitRole;
import neqsim.process.util.optimizer.ProcessModelOperatingActionSetEvaluator.Outcome;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.LeadingConstraintEvidence;
import neqsim.process.util.optimizer.ProcessModelOperatingEnvelopeStudy.SliceResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration tests for typed plant/shared constraints in process-model candidates and operating envelopes.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessModelPlantConstraintIntegrationTest {
  /** Mutable two-area compression fixture. */
  private static final class Fixture {
    private final ProcessModel model;
    private final Stream feedA;
    private final Stream feedB;
    private final double sharedPowerLimitKw;

    /** Creates one fixture holder. */
    private Fixture(ProcessModel model, Stream feedA, Stream feedB, double sharedPowerLimitKw) {
      this.model = model;
      this.feedA = feedA;
      this.feedB = feedB;
      this.sharedPowerLimitKw = sharedPowerLimitKw;
    }
  }

  /** Mutable common-shaft compression fixture. */
  private static final class CommonShaftFixture {
    private final ProcessModel model;
    private final Stream feedA;
    private final Stream feedB;
    private final Compressor compressorA;
    private final Compressor compressorB;
    private final MechanicalShaft shaft;
    private final EnergyPort driverPort;
    private final CompressorDriver driver;
    private final Gearbox gearbox;

    /**
     * Creates one common-shaft fixture holder.
     *
     * @param model mutable process model
     * @param feedA first producer feed
     * @param feedB second producer feed
     * @param compressorA first mapped compressor casing
     * @param compressorB second mapped compressor casing
     * @param shaft shared mechanical shaft
     * @param driverPort shaft driver output port
     * @param driver configured driver rating
     * @param gearbox configured gearbox rating
     */
    private CommonShaftFixture(ProcessModel model, Stream feedA, Stream feedB, Compressor compressorA,
        Compressor compressorB, MechanicalShaft shaft, EnergyPort driverPort, CompressorDriver driver,
        Gearbox gearbox) {
      this.model = model;
      this.feedA = feedA;
      this.feedB = feedB;
      this.compressorA = compressorA;
      this.compressorB = compressorB;
      this.shaft = shaft;
      this.driverPort = driverPort;
      this.driver = driver;
      this.gearbox = gearbox;
    }

    /**
     * Captures one immutable common-shaft candidate after solving its energy balance.
     *
     * @param calculationId evaluator-owned evidence identity
     * @return complete or fail-closed common-shaft evidence
     */
    private PlantCommonShaftEvidence evidence(String calculationId) {
      shaft.solveBalance();
      String sourceCalculationId = compressorA.getCalculationIdentifier().toString();
      assertEquals(sourceCalculationId, compressorB.getCalculationIdentifier().toString());
      return PlantCommonShaftEvidence
          .builder("synthetic field", "compression", "export common shaft", calculationId, shaft,
              "synthetic mapped two-casing candidate")
          .sourceCalculationId(sourceCalculationId)
          .casing(compressorA.getEnergyPort("shaftPower").getParticipantId(), compressorA)
          .casing(compressorB.getEnergyPort("shaftPower").getParticipantId(), compressorB)
          .driver(driverPort.getParticipantId(), driver).gearbox("export gearbox", gearbox).speedToleranceRpm(1.0)
          .powerBalanceToleranceKw(1.0e-6).maximumTorqueNm(1.0e6).convergenceComplete(model.isModelConverged()).build();
    }
  }

  /**
   * Verifies one shared-power sample is evaluated once and becomes the exact rejection identity in an envelope slice.
   */
  @Test
  void sharedPowerViolationBecomesEnvelopeRejectionIdentity() {
    Fixture fixture = createFixture();
    PlantConstraintDefinition sharedPower = sharedPowerDefinition();
    AtomicInteger sampleCalls = new AtomicInteger();

    ProcessModelSimulationEvaluator simulation = new ProcessModelSimulationEvaluator(fixture.model);
    simulation.setIncludeStrategyCapacityConstraints(false);
    simulation.addObjective("total production",
        model -> fixture.feedA.getFlowRate("kg/hr") + fixture.feedB.getFlowRate("kg/hr"),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    simulation.addPlantConstraint(sharedPower, (model, calculationId) -> {
      sampleCalls.incrementAndGet();
      return PlantSharedResourceEvidence.fromProcessModelShaftPower(sharedPower, calculationId,
          fixture.sharedPowerLimitKw, model, model.isModelConverged(), "synthetic completed candidate")
          .toPlantConstraintSample();
    });

    ProcessModelOperatingAction rateA = ProcessModelOperatingAction.continuous("rate-a", "Producer A rate",
        "A::feed A.flowRate", 300.0, 1000.0, "kg/hr", "synthetic well A operating range");
    ProcessModelOperatingAction rateB = ProcessModelOperatingAction.continuous("rate-b", "Producer B rate",
        "B::feed B.flowRate", 300.0, 1000.0, "kg/hr", "synthetic well B operating range");
    ProcessModelOperatingActionSetEvaluator candidates = new ProcessModelOperatingActionSetEvaluator("field-actions",
        "Field actions", "synthetic field-to-host power acceptance", simulation, Arrays.asList(rateA, rateB))
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "A", "feed A", "well deliverability",
            "synthetic well A limit")
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "B", "feed B", "well deliverability",
            "synthetic well B limit");
    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy("shared-power-slice",
        "Shared power slice", "synthetic field-to-host acceptance", candidates);

    SliceResult result = study.evaluateOneDimensional("rate-a", new double[] {400.0, 900.0},
        new double[] {400.0, 400.0});

    assertTrue(result.isComplete());
    assertEquals(2, sampleCalls.get(), "plant sampler must run once for each candidate, not once per constraint");
    assertTrue(result.getPoints().get(0).isFeasible());
    assertFalse(result.getPoints().get(1).isFeasible());
    assertEquals(Outcome.OTHER_MODEL_CONSTRAINT_VIOLATED, result.getPoints().get(1).getEvaluation().getOutcome());
    LeadingConstraintEvidence rejection = result.getPoints().get(1).getLeadingConstraint();
    assertEquals(LeadingConstraintEvidence.Source.PLANT_CONSTRAINT, rejection.getSource());
    assertEquals(sharedPower.getQualifiedId(), rejection.getQualifiedConstraintName());
    assertEquals("kW", rejection.getPhysicalUnit());
    assertTrue(rejection.getPhysicalMargin() < 0.0);
    assertEquals(1, result.getBottleneckTransitions().size());
    assertEquals(sharedPower.getQualifiedId(), result.getBottleneckTransitions().get(0).getToConstraint());
    assertEquals(400.0, fixture.feedA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(400.0, fixture.feedB.getFlowRate("kg/hr"), 1.0e-8);
  }

  /** Verifies a coupled group produces multiple typed rows from one exact callback. */
  @Test
  void groupedPlantConstraintsAreSampledOnceAndFailClosed() {
    Fixture fixture = createFixture();
    PlantConstraintDefinition power = sharedPowerDefinition();
    PlantConstraintDefinition hostRate = PlantConstraintDefinition
        .builder("host-rate", PlantConstraintScope.sharedResource("Plant", "host inlet"))
        .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.SUM)
        .limitDirection(PlantConstraintDefinition.LimitDirection.MAXIMUM)
        .category(PlantConstraintDefinition.Category.OPERATING).severity(ConstraintSeverity.HARD).unit("kg/hr")
        .basis("mass rate").provenance("synthetic host operating limit")
        .participant(PlantConstraintParticipant.direct("A", "kg/hr", "mass rate"))
        .participant(PlantConstraintParticipant.direct("B", "kg/hr", "mass rate")).build();
    AtomicInteger sampleCalls = new AtomicInteger();
    ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(fixture.model);
    evaluator.addPlantConstraintGroup("host-coupling", Arrays.asList(power, hostRate), (model, calculationId) -> {
      sampleCalls.incrementAndGet();
      double totalRate = fixture.feedA.getFlowRate("kg/hr") + fixture.feedB.getFlowRate("kg/hr");
      PlantConstraintSample powerSample = PlantSharedResourceEvidence.fromProcessModelShaftPower(power, calculationId,
          fixture.sharedPowerLimitKw * 2.0, model, model.isModelConverged(), "synthetic completed candidate")
          .toPlantConstraintSample();
      PlantConstraintSample rateSample = PlantConstraintSample.builder(hostRate.getQualifiedId(), calculationId)
          .values(totalRate, 700.0).normalized(totalRate / 700.0, totalRate / 700.0 - 1.0)
          .physical(700.0 - totalRate, Math.max(0.0, totalRate - 700.0)).unit("kg/hr").basis("mass rate")
          .provenance("synthetic completed candidate").build();
      return Arrays.asList(powerSample, rateSample);
    });

    ProcessModelSimulationEvaluator.EvaluationResult result = evaluator.evaluate(new double[0]);

    assertEquals(1, sampleCalls.get());
    assertFalse(result.isFeasible());
    assertEquals(2, result.getPlantConstraintEvidence().size());
    assertEquals(2, result.getConstraintValues().length);
    assertTrue(result.getPlantConstraintEvidence().get(0).hasAvailableEvidence());
    assertEquals(PlantConstraintEvidence.OperatingStatus.VIOLATED,
        result.getPlantConstraintEvidence().get(1).getOperatingStatus());

    AtomicInteger missingCalls = new AtomicInteger();
    ProcessModelSimulationEvaluator missingEvaluator = new ProcessModelSimulationEvaluator(fixture.model);
    missingEvaluator.addPlantConstraint(hostRate, (model, calculationId) -> {
      missingCalls.incrementAndGet();
      return null;
    });
    ProcessModelSimulationEvaluator.EvaluationResult missing = missingEvaluator.evaluate(new double[0]);
    assertEquals(1, missingCalls.get());
    assertFalse(missing.isFeasible());
    assertTrue(Double.isNaN(missing.getConstraintValues()[0]));
    assertEquals(PlantConstraintEvidence.CoverageStatus.MISSING_SAMPLE,
        missing.getPlantConstraintEvidence().get(0).getCoverageStatus());
  }

  /**
   * Verifies real mapped compressor casings can use process-run UUIDs while optimizer evidence keeps its own exact
   * identity, and that the shared driver limit becomes a restorable field-to-host bottleneck.
   */
  @Test
  void mappedCommonShaftDriverLimitRejectsHighFieldRateAndRestoresBaseline() {
    CommonShaftFixture fixture = createCommonShaftFixture();
    PlantCommonShaftEvidence registration = fixture.evidence("common-shaft-registration");
    assertTrue(registration.isComplete(), registration.getDiagnostics().toString());

    AtomicInteger sampleCalls = new AtomicInteger();
    ProcessModelSimulationEvaluator simulation = new ProcessModelSimulationEvaluator(fixture.model);
    simulation.setIncludeStrategyCapacityConstraints(false);
    simulation.addObjective("total production",
        model -> fixture.feedA.getFlowRate("kg/hr") + fixture.feedB.getFlowRate("kg/hr"),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    simulation.addCommonShaftConstraintGroup("export-common-shaft", registration, (model, calculationId) -> {
      sampleCalls.incrementAndGet();
      return fixture.evidence(calculationId);
    });

    ProcessModelOperatingAction rateA = ProcessModelOperatingAction.continuous("rate-a", "Producer A rate",
        "Compression::feed A.flowRate", 4000.0, 8000.0, "kg/hr", "synthetic well A operating range");
    ProcessModelOperatingAction rateB = ProcessModelOperatingAction.continuous("rate-b", "Producer B rate",
        "Compression::feed B.flowRate", 4000.0, 8000.0, "kg/hr", "synthetic well B operating range");
    ProcessModelOperatingActionSetEvaluator candidates = new ProcessModelOperatingActionSetEvaluator(
        "common-shaft-actions", "Common shaft actions", "synthetic mapped field-to-host acceptance", simulation,
        Arrays.asList(rateA, rateB))
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "Compression", "feed A",
            "well deliverability", "synthetic well A limit")
        .requireHydraulicConstraint(HydraulicLimitRole.WELL_INFLOW_OUTFLOW, "Compression", "feed B",
            "well deliverability", "synthetic well B limit");
    ProcessModelOperatingEnvelopeStudy study = new ProcessModelOperatingEnvelopeStudy("common-shaft-slice",
        "Common shaft slice", "synthetic mapped common-shaft acceptance", candidates);

    SliceResult result = study.evaluateOneDimensional("rate-a", new double[] {6000.0, 7500.0},
        new double[] {6000.0, 6000.0});

    assertTrue(result.isComplete());
    assertEquals(2, sampleCalls.get());
    assertTrue(result.getPoints().get(0).isFeasible());
    assertFalse(result.getPoints().get(1).isFeasible());
    LeadingConstraintEvidence rejection = result.getPoints().get(1).getLeadingConstraint();
    assertEquals(LeadingConstraintEvidence.Source.PLANT_CONSTRAINT, rejection.getSource());
    assertTrue(rejection.getQualifiedConstraintName().endsWith("#driver-maximum-power")
        || rejection.getQualifiedConstraintName().endsWith("#shaft-power-balance"));
    assertEquals(6000.0, fixture.feedA.getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(6000.0, fixture.feedB.getFlowRate("kg/hr"), 1.0e-8);
    assertTrue(fixture.evidence("restored-candidate").isComplete());
  }

  /** Verifies the typed common-shaft registration rejects evidence bound to another candidate. */
  @Test
  void commonShaftCandidateIdentityDriftFailsClosed() {
    CommonShaftFixture fixture = createCommonShaftFixture();
    PlantCommonShaftEvidence registration = fixture.evidence("common-shaft-registration");
    ProcessModelSimulationEvaluator simulation = new ProcessModelSimulationEvaluator(fixture.model);
    simulation.setIncludeStrategyCapacityConstraints(false);
    simulation.addCommonShaftConstraintGroup("export-common-shaft", registration,
        (model, calculationId) -> fixture.evidence("wrong-candidate"));

    ProcessModelSimulationEvaluator.EvaluationResult result = simulation.evaluate(new double[0]);

    assertFalse(result.isFeasible());
    assertEquals(registration.getDefinitions().size(), result.getPlantConstraintEvidence().size());
    assertTrue(result.getPlantConstraintEvidence().stream()
        .allMatch(row -> row.getCoverageStatus() == PlantConstraintEvidence.CoverageStatus.EXCEPTION));
  }

  /** Builds the deterministic two-area compression model and calibrates an intermediate shared-power limit. */
  private Fixture createFixture() {
    Stream feedA = new Stream("feed A", gas());
    Stream feedB = new Stream("feed B", gas());
    feedA.setFlowRate(400.0, "kg/hr");
    feedB.setFlowRate(400.0, "kg/hr");
    Compressor compressorA = new Compressor("compressor A", feedA);
    Compressor compressorB = new Compressor("compressor B", feedB);
    compressorA.setOutletPressure(80.0, "bara");
    compressorB.setOutletPressure(80.0, "bara");
    addWellConstraint(feedA);
    addWellConstraint(feedB);

    ProcessSystem areaA = new ProcessSystem("area A");
    areaA.add(feedA);
    areaA.add(compressorA);
    ProcessSystem areaB = new ProcessSystem("area B");
    areaB.add(feedB);
    areaB.add(compressorB);
    ProcessModel model = new ProcessModel();
    model.add("A", areaA);
    model.add("B", areaB);
    model.run();
    double baselinePower = model.getPower("kW");
    feedA.setFlowRate(900.0, "kg/hr");
    model.run();
    double highPower = model.getPower("kW");
    feedA.setFlowRate(400.0, "kg/hr");
    model.run();
    assertTrue(highPower > baselinePower);
    return new Fixture(model, feedA, feedB, 0.5 * (baselinePower + highPower));
  }

  /**
   * Builds two mapped compressor casings on one driver-limited mechanical shaft.
   *
   * @return initialized and restored common-shaft fixture
   */
  private CommonShaftFixture createCommonShaftFixture() {
    Stream feedA = new Stream("feed A", gas());
    Stream feedB = new Stream("feed B", gas());
    feedA.setFlowRate(6000.0, "kg/hr");
    feedB.setFlowRate(6000.0, "kg/hr");
    Compressor compressorA = new Compressor("compressor A", feedA);
    Compressor compressorB = new Compressor("compressor B", feedB);
    compressorA.setOutletPressure(80.0, "bara");
    compressorB.setOutletPressure(80.0, "bara");
    addWellConstraint(feedA, 9000.0, 4000.0, 8000.0);
    addWellConstraint(feedB, 9000.0, 4000.0, 8000.0);

    ProcessSystem compression = new ProcessSystem("compression");
    compression.add(feedA);
    compression.add(compressorA);
    compression.add(feedB);
    compression.add(compressorB);
    ProcessModel model = new ProcessModel();
    model.add("Compression", compression);
    model.run();
    compressorA.generateCompressorChart("normal curves", 5);
    compressorB.generateCompressorChart("normal curves", 5);
    compressorA.getCompressorChart().setUseCompressorChart(true);
    compressorB.getCompressorChart().setUseCompressorChart(true);

    MechanicalShaft shaft = new MechanicalShaft("export common shaft");
    shaft.setSpeed(compressorA.getSpeed());
    shaft.setMaximumSpeed(compressorA.getSpeed() * 1.2);
    compressorA.getEnergyPort("shaftPower").connect(shaft);
    compressorB.getEnergyPort("shaftPower").connect(shaft);
    EnergyPort driverPort = new EnergyPort("export driver", EnergyType.SHAFT_WORK, EnergyPortDirection.OUTPUT,
        EnergyPortMode.CALCULATED);
    driverPort.connect(shaft);

    model.run();
    double baselinePowerKw = compressorA.getPower("kW") + compressorB.getPower("kW");
    feedA.setFlowRate(7500.0, "kg/hr");
    model.run();
    double highPowerKw = compressorA.getPower("kW") + compressorB.getPower("kW");
    assertTrue(highPowerKw > baselinePowerKw, "expected increasing mapped shaft power, baseline=" + baselinePowerKw
        + " high=" + highPowerKw + " point=" + compressorA.getOperatingPoint());
    double driverLimitKw = 0.5 * (baselinePowerKw + highPowerKw) / 0.98;

    CompressorDriver driver = new CompressorDriver(DriverType.ELECTRIC_MOTOR, driverLimitKw);
    driver.setMaxPower(driverLimitKw);
    driver.setMinSpeed(shaft.getSpeed() * 0.8);
    driver.setMaxSpeed(shaft.getSpeed() * 1.2);
    driverPort.setDuty(driverLimitKw, "kW");
    Gearbox gearbox = new Gearbox("export gearbox");
    gearbox.setEfficiency(0.98);
    gearbox.setSpeedRatio(1.0);
    gearbox.setMaximumInputPower(driverLimitKw * 1.2 * 1000.0);

    feedA.setFlowRate(6000.0, "kg/hr");
    model.run();
    return new CommonShaftFixture(model, feedA, feedB, compressorA, compressorB, shaft, driverPort, driver, gearbox);
  }

  /** Adds a conservative synthetic well deliverability limit to one producer stream. */
  private void addWellConstraint(Stream feed) {
    addWellConstraint(feed, 1200.0, 300.0, 1000.0);
  }

  /**
   * Adds a configurable synthetic well deliverability limit to one producer stream.
   *
   * @param feed producer stream
   * @param designRate installed maximum rate in kg/hr
   * @param minimumRate minimum action validity in kg/hr
   * @param maximumRate maximum action validity in kg/hr
   */
  private void addWellConstraint(Stream feed, double designRate, double minimumRate, double maximumRate) {
    CapacityConstraint constraint = new CapacityConstraint("well deliverability", "kg/hr", ConstraintType.HARD)
        .setDesignValue(designRate).setSeverity(ConstraintSeverity.HARD)
        .setDataSource("synthetic well operating envelope").setConfidence(1.0)
        .setValidityRange(minimumRate, maximumRate).setValueSupplier(() -> feed.getFlowRate("kg/hr"));
    feed.addCapacityConstraint(constraint);
  }

  /** Creates the exact shared shaft-power definition for both process areas. */
  private PlantConstraintDefinition sharedPowerDefinition() {
    return PlantConstraintDefinition
        .builder("total-shaft-power", PlantConstraintScope.sharedResource("Plant", "compression shaft power"))
        .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.SHARED_BUDGET)
        .limitDirection(PlantConstraintDefinition.LimitDirection.MAXIMUM)
        .category(PlantConstraintDefinition.Category.OPERATING).severity(ConstraintSeverity.HARD).unit("kW")
        .basis("compressor and pump shaft power").provenance("synthetic shared power budget")
        .participant(PlantConstraintParticipant.direct("A", "kW", "compressor and pump shaft power"))
        .participant(PlantConstraintParticipant.direct("B", "kW", "compressor and pump shaft power")).build();
  }

  /** Creates the shared dry-gas fluid. */
  private SystemInterface gas() {
    SystemInterface fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    return fluid;
  }
}
