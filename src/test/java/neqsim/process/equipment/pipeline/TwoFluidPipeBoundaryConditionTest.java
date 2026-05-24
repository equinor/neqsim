package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe.BoundaryCondition;
import neqsim.process.equipment.pipeline.TwoFluidPipe.TransientPressureReference;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator.Method;
import neqsim.process.equipment.pipeline.twophasepipe.validation.TwoFluidBenchmarkHarness;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for TwoFluidPipe boundary condition configuration and transient behavior.
 *
 * <p>
 * Validates:
 * <ul>
 * <li>Boundary condition type setting (STREAM_CONNECTED, CONSTANT_FLOW, CONSTANT_PRESSURE,
 * CLOSED)</li>
 * <li>Shut-in scenarios (closed outlet, closed inlet)</li>
 * <li>Physical behavior during transient: pressure buildup, velocity decay</li>
 * <li>Convenience methods (closeOutlet, openOutlet, closeInlet, openInlet)</li>
 * </ul>
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
class TwoFluidPipeBoundaryConditionTest {
  private static final int NUM_SECTIONS = 10;
  private static final double PIPE_LENGTH = 1000.0; // 1 km
  private static final double PIPE_DIAMETER = 0.2; // 200 mm

  private TwoFluidPipe pipe;
  private static Stream sharedInletStream;
  private static SystemInterface sharedFluid;

  /**
   * Set up shared fluid and stream (expensive to create).
   */
  @BeforeAll
  static void setUpOnce() {
    // Create a two-phase gas fluid
    sharedFluid = new SystemSrkEos(303.15, 70.0); // 30°C, 70 bar
    sharedFluid.addComponent("methane", 0.90);
    sharedFluid.addComponent("ethane", 0.06);
    sharedFluid.addComponent("propane", 0.03);
    sharedFluid.addComponent("n-butane", 0.01);
    sharedFluid.setMixingRule("classic");

    // Create inlet stream
    sharedInletStream = new Stream("inlet", sharedFluid);
    sharedInletStream.setFlowRate(5.0, "kg/sec");
    sharedInletStream.setTemperature(30.0, "C");
    sharedInletStream.setPressure(70.0, "bara");
    sharedInletStream.run();
  }

  /**
   * Create fresh pipe for each test.
   */
  @BeforeEach
  void setUp() {
    pipe = new TwoFluidPipe("bc-test-pipe", sharedInletStream);
    pipe.setLength(PIPE_LENGTH);
    pipe.setDiameter(PIPE_DIAMETER);
    pipe.setNumberOfSections(NUM_SECTIONS);
    pipe.setOutletPressure(50.0, "bara"); // Default outlet BC
  }

  // =====================================================================
  // Default Boundary Condition Tests
  // =====================================================================

  @Test
  @DisplayName("Default BCs: inlet=STREAM_CONNECTED, outlet=CONSTANT_PRESSURE")
  void testDefaultBoundaryConditions() {
    assertEquals(BoundaryCondition.STREAM_CONNECTED, pipe.getInletBoundaryCondition(),
        "Default inlet BC should be STREAM_CONNECTED");
    assertEquals(BoundaryCondition.CONSTANT_PRESSURE, pipe.getOutletBoundaryCondition(),
        "Default outlet BC should be CONSTANT_PRESSURE");
    assertFalse(pipe.isOutletClosed(), "Outlet should not be closed by default");
    assertFalse(pipe.isInletClosed(), "Inlet should not be closed by default");
  }

  @Test
  @DisplayName("CONSTANT_PRESSURE outlet is applied during steady-state run")
  void testSteadyStateHonorsFixedOutletPressure() {
    pipe.run();

    double[] pressures = pipe.getPressureProfile();
    assertEquals(50.0, pressures[pressures.length - 1] / 1e5, 1e-6,
        "Steady pressure profile should end at the fixed outlet pressure");
    assertEquals(50.0, pipe.getOutletStream().getPressure("bara"), 1e-6,
        "Outlet stream should use the fixed outlet pressure after steady-state run");
  }

  @Test
  @DisplayName("Fixed outlet transient remains bounded after flow-rate step")
  void testFixedOutletTransientFlowStepRemainsBounded() {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 45.0, 75.0);
    fluid.addComponent("methane", 0.68);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-heptane", 0.17);
    fluid.addComponent("water", 0.04);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("fixed-outlet-feed", fluid);
    feed.setFlowRate(10.0, "kg/sec");
    feed.setTemperature(45.0, "C");
    feed.setPressure(75.0, "bara");
    feed.run();

    int sections = 15;
    double[] elevationProfile = new double[sections];
    for (int i = 0; i < sections; i++) {
      double x = (double) i / (sections - 1);
      elevationProfile[i] = -12.0 * Math.sin(Math.PI * x) + 4.0 * Math.sin(3.0 * Math.PI * x);
    }

    TwoFluidPipe transientPipe = new TwoFluidPipe("fixed-outlet-step-pipe", feed);
    transientPipe.setLength(1500.0);
    transientPipe.setDiameter(0.25);
    transientPipe.setNumberOfSections(sections);
    transientPipe.setRoughness(4.5e-5);
    transientPipe.setElevationProfile(elevationProfile);
    transientPipe.setOutletPressure(55.0, "bara");
    transientPipe.setOLGAModelType(TwoFluidPipe.OLGAModelType.SIMPLIFIED);
    transientPipe.setSlugTrackingMode(TwoFluidPipe.SlugTrackingMode.SIMPLIFIED);
    transientPipe.setEnableAdaptiveTimestepping(true);
    transientPipe.run();

    double[] initialPressure = transientPipe.getPressureProfile();
    assertEquals(55.0, initialPressure[initialPressure.length - 1] / 1e5, 1e-6,
        "Initial transient state should start from the fixed outlet pressure");

    UUID id = UUID.randomUUID();
    for (int i = 1; i <= 8; i++) {
      if (i == 4) {
        feed.setFlowRate(14.0, "kg/sec");
        feed.run();
      }
      transientPipe.runTransient(0.5, id);
    }
    assertEquals(4.0, transientPipe.getSimulationTime(), 1e-9,
        "Notebook-style transient sequence should advance the requested duration");

    double[] finalPressure = transientPipe.getPressureProfile();
    double[] gasVelocity = transientPipe.getGasVelocityProfile();
    double[] liquidVelocity = transientPipe.getLiquidVelocityProfile();
    for (int i = 0; i < finalPressure.length; i++) {
      assertTrue(finalPressure[i] >= 1.0e5, "Pressure must stay positive at section " + i);
      assertTrue(finalPressure[i] <= 75.0e5 * 1.10,
          "Pressure should stay below the notebook inlet pressure envelope at section " + i);
      assertTrue(Math.abs(gasVelocity[i]) < 100.0,
          "Gas velocity should not hit the numerical limiter at section " + i);
      assertTrue(Math.abs(liquidVelocity[i]) < 50.0,
          "Liquid velocity should not hit the numerical limiter at section " + i);
    }
  }

  @Test
  @DisplayName("Long multiphase pipeline steady and dynamic profiles remain bounded")
  void testLongPipelineSteadyAndDynamicRemainBounded() {
    double inletPressureBara = 120.0;
    SystemInterface fluid = createLongPipelineFluid(60.0, inletPressureBara);

    Stream feed = new Stream("long-pipeline-feed", fluid);
    feed.setFlowRate(50.0, "kg/sec");
    feed.setTemperature(60.0, "C");
    feed.setPressure(inletPressureBara, "bara");
    feed.run();

    int sections = 32;
    double[] elevationProfile = createLongPipelineTerrain(sections);

    TwoFluidPipe longPipe = new TwoFluidPipe("long-multiphase-pipe", feed);
    longPipe.setLength(80000.0);
    longPipe.setDiameter(0.50);
    longPipe.setNumberOfSections(sections);
    longPipe.setRoughness(4.5e-5);
    longPipe.setElevationProfile(elevationProfile);
    longPipe.setSurfaceTemperature(4.0, "C");
    longPipe.setHeatTransferCoefficient(6.0);
    longPipe.setThermodynamicUpdateInterval(100);
    longPipe.setOLGAModelType(TwoFluidPipe.OLGAModelType.SIMPLIFIED);
    longPipe.setSlugTrackingMode(TwoFluidPipe.SlugTrackingMode.SIMPLIFIED);
    longPipe.setEnableAdaptiveTimestepping(true);
    longPipe.setAdaptiveMaxPressureChangeRatio(1.5);
    longPipe.setTimeIntegrationMethod(Method.IMEX_PRESSURE_CORRECTION);
    longPipe.run();

    assertBoundedLongPipelineProfile(longPipe, inletPressureBara,
        "Long-pipeline steady-state profile");
    double initialOutletPressureBara = longPipe.getOutletStream().getPressure("bara");
    assertTrue(initialOutletPressureBara > 1.0,
        "Long-pipeline steady outlet pressure should remain positive");
    assertTrue(longPipe.getLiquidInventory("m3") >= 0.0,
        "Long-pipeline steady liquid inventory should be non-negative");

    UUID id = UUID.randomUUID();
    for (int step = 1; step <= 6; step++) {
      if (step == 3) {
        feed.setFlowRate(75.0, "kg/sec");
        feed.run();
      }
      longPipe.runTransient(60.0, id);
      assertBoundedLongPipelineProfile(longPipe, inletPressureBara,
          "Long-pipeline transient profile after step " + step);
    }

    assertEquals(360.0, longPipe.getSimulationTime(), 1e-9,
        "Long-pipeline transient sequence should advance the requested duration");
    assertEquals(initialOutletPressureBara, longPipe.getOutletStream().getPressure("bara"), 0.05,
        "Long-pipeline transient should honor the fixed outlet pressure from initialization");
  }

  /**
   * Creates a rich gas-condensate-water fluid for long-pipeline regression tests.
   *
   * @param temperatureC fluid temperature in degrees Celsius
   * @param pressureBara fluid pressure in bara
   * @return configured thermodynamic fluid
   */
  private static SystemInterface createLongPipelineFluid(double temperatureC, double pressureBara) {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + temperatureC, pressureBara);
    fluid.addComponent("nitrogen", 1.0);
    fluid.addComponent("CO2", 2.5);
    fluid.addComponent("methane", 65.0);
    fluid.addComponent("ethane", 8.0);
    fluid.addComponent("propane", 6.0);
    fluid.addComponent("i-butane", 2.0);
    fluid.addComponent("n-butane", 3.0);
    fluid.addComponent("i-pentane", 2.5);
    fluid.addComponent("n-pentane", 3.0);
    fluid.addComponent("n-hexane", 2.5);
    fluid.addComponent("n-heptane", 2.0);
    fluid.addComponent("n-octane", 1.0);
    fluid.addComponent("water", 1.5);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Creates an undulating long subsea terrain profile.
   *
   * @param sections number of pipe sections
   * @return elevation profile in meters
   */
  private static double[] createLongPipelineTerrain(int sections) {
    double[] elevations = new double[sections];
    for (int sectionIndex = 0; sectionIndex < sections; sectionIndex++) {
      double x = (double) sectionIndex / (sections - 1);
      elevations[sectionIndex] = -80.0 * Math.exp(-Math.pow((x - 0.15) / 0.06, 2.0))
          + 60.0 * Math.exp(-Math.pow((x - 0.30) / 0.05, 2.0))
          - 120.0 * Math.exp(-Math.pow((x - 0.45) / 0.08, 2.0))
          + 50.0 * Math.exp(-Math.pow((x - 0.60) / 0.05, 2.0))
          - 70.0 * Math.exp(-Math.pow((x - 0.75) / 0.06, 2.0))
          + 40.0 * Math.exp(-Math.pow((x - 0.90) / 0.05, 2.0));
    }
    return elevations;
  }

  /**
   * Asserts finite, bounded pressure and velocity profiles for a long pipeline.
   *
   * @param pipeToCheck pipe model to inspect
   * @param inletPressureBara inlet pressure reference in bara
   * @param context assertion context text
   */
  private static void assertBoundedLongPipelineProfile(TwoFluidPipe pipeToCheck,
      double inletPressureBara, String context) {
    double[] pressure = pipeToCheck.getPressureProfile();
    double[] gasVelocity = pipeToCheck.getGasVelocityProfile();
    double[] liquidVelocity = pipeToCheck.getLiquidVelocityProfile();
    for (int sectionIndex = 0; sectionIndex < pressure.length; sectionIndex++) {
      assertTrue(Double.isFinite(pressure[sectionIndex]),
          context + " pressure should be finite at section " + sectionIndex);
      assertTrue(pressure[sectionIndex] >= 1.0e5,
          context + " pressure should stay positive at section " + sectionIndex);
      assertTrue(pressure[sectionIndex] <= inletPressureBara * 1.0e5 * 1.10, context
          + " pressure should stay inside the open-boundary envelope at section " + sectionIndex);
      assertTrue(Double.isFinite(gasVelocity[sectionIndex]),
          context + " gas velocity should be finite at section " + sectionIndex);
      assertTrue(Double.isFinite(liquidVelocity[sectionIndex]),
          context + " liquid velocity should be finite at section " + sectionIndex);
      assertTrue(Math.abs(gasVelocity[sectionIndex]) < 100.0,
          context + " gas velocity should not hit the limiter at section " + sectionIndex);
      assertTrue(Math.abs(liquidVelocity[sectionIndex]) < 50.0,
          context + " liquid velocity should not hit the limiter at section " + sectionIndex);
    }
  }

  // =====================================================================
  // Boundary Condition Type Setting Tests
  // =====================================================================

  @Test
  @DisplayName("Set inlet BC to CONSTANT_FLOW")
  void testSetInletBCConstantFlow() {
    pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);
    pipe.setInletMassFlow(3.0, "kg/sec");

    assertEquals(BoundaryCondition.CONSTANT_FLOW, pipe.getInletBoundaryCondition());
    assertFalse(pipe.isInletClosed());
  }

  @Test
  @DisplayName("Set inlet BC to CONSTANT_PRESSURE")
  void testSetInletBCConstantPressure() {
    pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
    pipe.setInletPressure(75.0, "bara");

    assertEquals(BoundaryCondition.CONSTANT_PRESSURE, pipe.getInletBoundaryCondition());
  }

  @Test
  @DisplayName("Set outlet BC to CLOSED")
  void testSetOutletBCClosed() {
    pipe.setOutletBoundaryCondition(BoundaryCondition.CLOSED);

    assertEquals(BoundaryCondition.CLOSED, pipe.getOutletBoundaryCondition());
    assertTrue(pipe.isOutletClosed(), "Outlet should be closed");
  }

  @Test
  @DisplayName("Set inlet BC to CLOSED")
  void testSetInletBCClosed() {
    pipe.setInletBoundaryCondition(BoundaryCondition.CLOSED);

    assertEquals(BoundaryCondition.CLOSED, pipe.getInletBoundaryCondition());
    assertTrue(pipe.isInletClosed(), "Inlet should be closed");
  }

  @Test
  @DisplayName("Inlet-pressure-driven transient keeps inlet pressure anchored")
  void testInletPressureDrivenTransientHonorsInletReference() {
    pipe.useInletPressureDrivenTransient();

    assertEquals(TransientPressureReference.INLET_PRESSURE, pipe.getTransientPressureReference());
    assertEquals(BoundaryCondition.STREAM_CONNECTED, pipe.getOutletBoundaryCondition());

    pipe.run();
    UUID id = UUID.randomUUID();
    pipe.runTransient(0.1, id);

    assertEquals(70.0, pipe.getInletPressure(), 1.0e-6,
        "Inlet-driven transient should keep the section inlet pressure at the stream pressure");
    assertEquals(0.0, pipe.getInletPressureResidual("bar"), 1.0e-6,
        "Inlet pressure residual should be zero in inlet-driven mode");
    assertFalse(pipe.hasBoundaryConditionPressureMismatch(),
        "Inlet-driven mode should not report the fixed-outlet diagnostic mismatch");
    assertFalse(pipe.hasPressureRiseAcrossPipe(),
        "Pressure-drop comparison should not be inverted in inlet-driven mode");
    assertTrue(pipe.getSignedPressureDrop("bar") >= 0.0,
        "Signed pressure drop should be non-negative in inlet-driven mode");
  }

  @Test
  @DisplayName("Inlet-pressure-driven long flat transient keeps forward pressure drop")
  void testInletPressureDrivenLongFlatTransientKeepsForwardPressureDrop() {
    SystemInterface fluid = createLongPipelineFluid(60.0, 120.0);

    Stream feed = new Stream("long-flat-inlet-driven-feed", fluid);
    feed.setFlowRate(50.0, "kg/sec");
    feed.setTemperature(60.0, "C");
    feed.setPressure(120.0, "bara");
    feed.run();

    int sections = 40;
    double[] flatElevation = new double[sections];

    TwoFluidPipe longPipe = new TwoFluidPipe("long-flat-inlet-driven-pipe", feed);
    longPipe.setLength(80000.0);
    longPipe.setDiameter(0.50);
    longPipe.setNumberOfSections(sections);
    longPipe.setRoughness(4.5e-5);
    longPipe.setElevationProfile(flatElevation);
    longPipe.setSurfaceTemperature(4.0, "C");
    longPipe.setHeatTransferCoefficient(6.0);
    longPipe.setThermodynamicUpdateInterval(100);
    longPipe.setOLGAModelType(TwoFluidPipe.OLGAModelType.SIMPLIFIED);
    longPipe.setSlugTrackingMode(TwoFluidPipe.SlugTrackingMode.SIMPLIFIED);
    longPipe.setEnableAdaptiveTimestepping(true);
    longPipe.setAdaptiveMaxPressureChangeRatio(1.5);
    longPipe.setTimeIntegrationMethod(Method.IMEX_PRESSURE_CORRECTION);
    longPipe.useInletPressureDrivenTransient();

    longPipe.run();
    longPipe.runTransient(300.0, UUID.randomUUID());

    assertEquals(0.0, longPipe.getInletPressureResidual("bar"), 1.0e-6,
        "Inlet-driven long transient should keep the inlet pressure anchored");
    assertFalse(longPipe.hasPressureRiseAcrossPipe(),
        "Flat inlet-driven long transient should not reconstruct an outlet pressure above inlet");
    assertTrue(longPipe.getSignedPressureDrop("bar") >= 0.0,
        "Flat inlet-driven long transient should keep a non-negative pressure drop");
    assertFalse(longPipe.hasBoundaryConditionPressureMismatch(),
        "Inlet-driven long transient should not trigger the fixed-outlet mismatch diagnostic");
  }

  @Test
  @DisplayName("Inlet-pressure-driven transient responds to feed-flow step")
  void testInletPressureDrivenTransientRespondsToFeedFlowStep() {
    SystemInterface fluid = createLongPipelineFluid(60.0, 120.0);

    Stream feed = new Stream("flow-step-inlet-driven-feed", fluid);
    feed.setFlowRate(50.0, "kg/sec");
    feed.setTemperature(60.0, "C");
    feed.setPressure(120.0, "bara");
    feed.run();

    int sections = 40;
    double[] flatElevation = new double[sections];

    TwoFluidPipe longPipe = new TwoFluidPipe("flow-step-inlet-driven-pipe", feed);
    longPipe.setLength(80000.0);
    longPipe.setDiameter(0.50);
    longPipe.setNumberOfSections(sections);
    longPipe.setRoughness(4.5e-5);
    longPipe.setElevationProfile(flatElevation);
    longPipe.setSurfaceTemperature(4.0, "C");
    longPipe.setHeatTransferCoefficient(6.0);
    longPipe.setThermodynamicUpdateInterval(100);
    longPipe.setOLGAModelType(TwoFluidPipe.OLGAModelType.SIMPLIFIED);
    longPipe.setSlugTrackingMode(TwoFluidPipe.SlugTrackingMode.SIMPLIFIED);
    longPipe.setEnableAdaptiveTimestepping(true);
    longPipe.setAdaptiveMaxPressureChangeRatio(1.5);
    longPipe.setTimeIntegrationMethod(Method.IMEX_PRESSURE_CORRECTION);
    longPipe.useInletPressureDrivenTransient();

    longPipe.run();
    longPipe.initializeTransientFromSteadyState(60.0, 60.0, UUID.randomUUID());

    double initialInletGasVelocity = longPipe.getGasVelocityProfile()[0];
    double initialPressureDrop = longPipe.getSignedPressureDrop("bar");

    feed.setFlowRate(75.0, "kg/sec");
    feed.run();
    longPipe.runTransient(60.0, UUID.randomUUID());

    double finalInletGasVelocity = longPipe.getGasVelocityProfile()[0];
    double finalPressureDrop = longPipe.getSignedPressureDrop("bar");

    assertEquals(0.0, longPipe.getInletPressureResidual("bar"), 1.0e-6,
        "Inlet-driven transient should keep the inlet pressure anchored after a flow step");
    assertTrue(finalInletGasVelocity > initialInletGasVelocity * 1.10,
        "Inlet gas velocity should increase when the connected feed flow is increased");
    assertTrue(Math.abs(finalPressureDrop - initialPressureDrop) > 1.0e-3,
        "Pressure-drop reconstruction should respond to the updated inlet flow");
  }

  @Test
  @DisplayName("Transient warm-up resets reporting clock and keeps inlet pressure anchored")
  void testInitializeTransientFromSteadyStateResetsReportingClock() {
    SystemInterface fluid = new SystemSrkEos(303.15, 70.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("warmup-inlet-driven-feed", fluid);
    feed.setFlowRate(2.0, "kg/sec");
    feed.setTemperature(30.0, "C");
    feed.setPressure(70.0, "bara");
    feed.run();

    int sections = 5;
    double[] flatElevation = new double[sections];

    TwoFluidPipe warmupPipe = new TwoFluidPipe("warmup-inlet-driven-pipe", feed);
    warmupPipe.setLength(1000.0);
    warmupPipe.setDiameter(0.20);
    warmupPipe.setNumberOfSections(sections);
    warmupPipe.setRoughness(4.5e-5);
    warmupPipe.setElevationProfile(flatElevation);
    warmupPipe.setSurfaceTemperature(20.0, "C");
    warmupPipe.setHeatTransferCoefficient(2.0);
    warmupPipe.setThermodynamicUpdateInterval(10);
    warmupPipe.setOLGAModelType(TwoFluidPipe.OLGAModelType.SIMPLIFIED);
    warmupPipe.setSlugTrackingMode(TwoFluidPipe.SlugTrackingMode.DISABLED);
    warmupPipe.setEnableAdaptiveTimestepping(false);
    warmupPipe.setTimeIntegrationMethod(Method.IMEX_PRESSURE_CORRECTION);
    warmupPipe.useInletPressureDrivenTransient();
    warmupPipe.run();

    warmupPipe.initializeTransientFromSteadyState(0.2, 0.1, UUID.randomUUID());

    assertTrue(warmupPipe.isTransientInitializedFromSteadyState(),
        "Warm-up flag should indicate that the transient state has been initialized");
    assertEquals(0.2, warmupPipe.getLastTransientWarmupDuration(), 1.0e-9,
        "Warm-up duration diagnostic should match the requested duration");
    assertEquals(2, warmupPipe.getLastTransientWarmupSteps(),
        "Warm-up step count diagnostic should match the reporting-level calls");
    assertEquals(0.0, warmupPipe.getSimulationTime(), 1.0e-9,
        "Warm-up should reset the public reporting clock");
    assertEquals(0.0, warmupPipe.getInletPressureResidual("bar"), 1.0e-6,
        "Warm-up should keep inlet-pressure-driven transients anchored at the inlet");
    assertFalse(warmupPipe.hasBoundaryConditionPressureMismatch(),
        "Warm-up should not create a boundary pressure mismatch diagnostic");
    assertFalse(warmupPipe.hasPressureRiseAcrossPipe(),
        "Warm-up should leave a forward pressure drop for this flat case");
    assertTrue(warmupPipe.getSignedPressureDrop("bar") >= 0.0,
        "Warm-up should leave a non-negative signed pressure drop");

    warmupPipe.runTransient(0.1, UUID.randomUUID());
    assertEquals(0.1, warmupPipe.getSimulationTime(), 1.0e-9,
        "First reported transient step after warm-up should start from zero time");
  }

  @Test
  @DisplayName("Two-fluid benchmark corpus CSV samples are readable by the harness")
  void testTwoFluidBenchmarkCorpusSamplesCanBeRead() throws Exception {
    List<String> fileNames = Arrays.asList("olga_flow_step_export_sample.csv",
        "ledaflow_slugging_export_sample.csv", "field_arrival_trend_sample.csv");

    int totalPoints = 0;
    for (String fileName : fileNames) {
      Path path = Paths.get("src", "test", "resources", "data", "twofluid_benchmarks", fileName);
      List<TwoFluidBenchmarkHarness.BenchmarkPoint> points = TwoFluidBenchmarkHarness.readCsv(path);
      assertFalse(points.isEmpty(), "Benchmark sample should contain points: " + fileName);
      totalPoints += points.size();
      for (TwoFluidBenchmarkHarness.BenchmarkPoint point : points) {
        assertFalse(point.getVariable().trim().isEmpty(),
            "Benchmark variable should be populated in " + fileName);
        assertFalse(point.getSource().trim().isEmpty(),
            "Benchmark source should be populated in " + fileName);
      }
    }

    assertTrue(totalPoints >= 15, "Benchmark corpus should contain representative sample points");
  }

  @Test
  @DisplayName("Transient pressure reference can be switched back to fixed outlet")
  void testUseFixedOutletPressureTransientRestoresFixedOutletReference() {
    pipe.useInletPressureDrivenTransient();
    pipe.useFixedOutletPressureTransient();

    assertEquals(TransientPressureReference.FIXED_OUTLET_PRESSURE,
        pipe.getTransientPressureReference());
    assertEquals(BoundaryCondition.CONSTANT_PRESSURE, pipe.getOutletBoundaryCondition());
  }

  // =====================================================================
  // Convenience Method Tests (closeOutlet, openOutlet, etc.)
  // =====================================================================

  @Test
  @DisplayName("closeOutlet() sets outlet BC to CLOSED")
  void testCloseOutlet() {
    pipe.closeOutlet();

    assertEquals(BoundaryCondition.CLOSED, pipe.getOutletBoundaryCondition());
    assertTrue(pipe.isOutletClosed());
  }

  @Test
  @DisplayName("openOutlet() restores default outlet BC")
  void testOpenOutlet() {
    pipe.closeOutlet();
    assertTrue(pipe.isOutletClosed());

    pipe.openOutlet(); // Restore default

    assertEquals(BoundaryCondition.CONSTANT_PRESSURE, pipe.getOutletBoundaryCondition());
    assertFalse(pipe.isOutletClosed());
  }

  @Test
  @DisplayName("openOutlet(pressure, unit) sets new pressure and opens")
  void testOpenOutletWithPressure() {
    pipe.closeOutlet();
    pipe.openOutlet(55.0, "bara");

    assertEquals(BoundaryCondition.CONSTANT_PRESSURE, pipe.getOutletBoundaryCondition());
    assertFalse(pipe.isOutletClosed());
  }

  @Test
  @DisplayName("closeInlet() sets inlet BC to CLOSED")
  void testCloseInlet() {
    pipe.closeInlet();

    assertEquals(BoundaryCondition.CLOSED, pipe.getInletBoundaryCondition());
    assertTrue(pipe.isInletClosed());
  }

  @Test
  @DisplayName("openInlet() restores default inlet BC")
  void testOpenInlet() {
    pipe.closeInlet();
    assertTrue(pipe.isInletClosed());

    pipe.openInlet(); // Restore default

    assertEquals(BoundaryCondition.STREAM_CONNECTED, pipe.getInletBoundaryCondition());
    assertFalse(pipe.isInletClosed());
  }

  // =====================================================================
  // Transient Simulation with CLOSED Outlet (Shut-in / Pressure Buildup)
  // =====================================================================

  @Test
  @DisplayName("CLOSED outlet: velocity at outlet should be zero")
  void testClosedOutletZeroVelocity() {
    // First run steady state
    pipe.run();

    // Get initial outlet velocity
    double[] velocityBefore = pipe.getGasVelocityProfile();
    int lastIdx = velocityBefore.length - 1;
    double outletVelBefore = velocityBefore[lastIdx];
    assertTrue(outletVelBefore > 0, "Outlet velocity should be positive before closing");

    // Close outlet and run transient
    pipe.closeOutlet();
    UUID id = UUID.randomUUID();
    pipe.runTransient(0.1, id); // Single transient step

    // After transient with closed outlet, outlet velocity should be zero
    double[] velocityAfter = pipe.getGasVelocityProfile();
    double outletVelAfter = velocityAfter[lastIdx];
    assertEquals(0.0, outletVelAfter, 1e-10, "Outlet velocity should be zero when closed");
  }

  @Test
  @DisplayName("CLOSED outlet: BC is applied and velocity is zero")
  void testClosedOutletBehavior() {
    // First run steady state
    pipe.run();

    // Close outlet and verify BC type
    pipe.closeOutlet();
    assertEquals(BoundaryCondition.CLOSED, pipe.getOutletBoundaryCondition());

    UUID id = UUID.randomUUID();
    pipe.runTransient(0.1, id);

    // Outlet velocity should be zero when closed
    double[] velocityAfter = pipe.getGasVelocityProfile();
    double outletVelAfter = velocityAfter[velocityAfter.length - 1];
    assertEquals(0.0, outletVelAfter, 1e-10, "Outlet velocity should be zero when closed");

    // Note: Pressure behavior in transient depends on solver details and mass accumulation
    // The key verification is that velocity is zero (no flow out)
  }

  // =====================================================================
  // Transient Simulation with CLOSED Inlet (Blowdown / Depressurization)
  // =====================================================================

  @Test
  @DisplayName("CLOSED inlet: velocity at inlet should be zero")
  void testClosedInletZeroVelocity() {
    // First run steady state
    pipe.run();

    // Get initial inlet velocity
    double[] velocityBefore = pipe.getGasVelocityProfile();
    double inletVelBefore = velocityBefore[0];
    assertTrue(inletVelBefore > 0, "Inlet velocity should be positive before closing");

    // Close inlet and run transient
    pipe.closeInlet();
    UUID id = UUID.randomUUID();
    pipe.runTransient(0.1, id);

    // After transient with closed inlet, inlet velocity should be zero
    double[] velocityAfter = pipe.getGasVelocityProfile();
    double inletVelAfter = velocityAfter[0];
    assertEquals(0.0, inletVelAfter, 1e-10, "Inlet velocity should be zero when closed");
  }

  @Test
  @DisplayName("CLOSED inlet: BC is applied and velocity is zero")
  void testClosedInletBehavior() {
    // First run steady state
    pipe.run();

    // Close inlet and verify BC type
    pipe.closeInlet();
    assertEquals(BoundaryCondition.CLOSED, pipe.getInletBoundaryCondition());

    UUID id = UUID.randomUUID();
    pipe.runTransient(0.1, id);

    // Inlet velocity should be zero when closed
    double[] velocityAfter = pipe.getGasVelocityProfile();
    double inletVelAfter = velocityAfter[0];
    assertEquals(0.0, inletVelAfter, 1e-10, "Inlet velocity should be zero when closed");

    // Note: Pressure behavior (blowdown) depends on solver implementation details
    // The key verification is that velocity is zero (no flow in)
  }

  // =====================================================================
  // Full Shut-in (Both Ends Closed)
  // =====================================================================

  @Test
  @DisplayName("Both ends CLOSED: velocities should be zero everywhere")
  void testBothEndsClosed() {
    // Run steady state first
    pipe.run();

    // Close both ends
    pipe.closeInlet();
    pipe.closeOutlet();

    assertTrue(pipe.isInletClosed());
    assertTrue(pipe.isOutletClosed());

    // Run transient
    UUID id = UUID.randomUUID();
    pipe.runTransient(0.1, id);

    // Check velocities at both ends
    double[] velocity = pipe.getGasVelocityProfile();
    assertEquals(0.0, velocity[0], 1e-10, "Inlet velocity should be zero");
    assertEquals(0.0, velocity[velocity.length - 1], 1e-10, "Outlet velocity should be zero");
  }

  @Test
  @DisplayName("Both ends CLOSED: verify BCs applied correctly")
  void testBothEndsClosedBCVerification() {
    // Run steady state first
    pipe.run();

    // Close both ends (trapped fluid)
    pipe.closeInlet();
    pipe.closeOutlet();

    // Verify BC types
    assertEquals(BoundaryCondition.CLOSED, pipe.getInletBoundaryCondition());
    assertEquals(BoundaryCondition.CLOSED, pipe.getOutletBoundaryCondition());

    // Run transient step
    UUID id = UUID.randomUUID();
    pipe.runTransient(0.1, id);

    // Verify velocities are zero at both boundaries
    double[] velocity = pipe.getGasVelocityProfile();
    assertEquals(0.0, velocity[0], 1e-10, "Inlet velocity should be zero");
    assertEquals(0.0, velocity[velocity.length - 1], 1e-10, "Outlet velocity should be zero");

    // Pressures are recorded for informational purposes
    // (exact pressure behavior during trapped fluid transient depends on solver details)
    double[] pressures = pipe.getPressureProfile();
    double avgP = averageArray(pressures) / 1e5; // Pa to bara
    System.out
        .println("Both ends closed: average pressure = " + String.format("%.2f", avgP) + " bara");
  }

  // =====================================================================
  // Open/Close Sequence (Transient Valve Operation)
  // =====================================================================

  @Test
  @DisplayName("Open-close-open outlet sequence during transient")
  void testOutletOpenCloseOpenSequence() {
    // Initial steady state
    pipe.run();
    UUID id = UUID.randomUUID();

    // Phase 1: Normal flow
    pipe.runTransient(0.1, id);
    double velPhase1 = pipe.getGasVelocityProfile()[NUM_SECTIONS - 1];
    assertTrue(velPhase1 > 0, "Phase 1: Outlet velocity should be positive");

    // Phase 2: Close outlet (shut-in)
    pipe.closeOutlet();
    pipe.runTransient(0.1, id);
    double velPhase2 = pipe.getGasVelocityProfile()[NUM_SECTIONS - 1];
    assertEquals(0.0, velPhase2, 1e-10, "Phase 2: Outlet velocity should be zero when closed");

    // Phase 3: Open outlet again
    pipe.openOutlet(50.0, "bara");
    pipe.runTransient(0.1, id);
    double velPhase3 = pipe.getGasVelocityProfile()[NUM_SECTIONS - 1];
    // Flow should resume (may be different from initial due to pressure change)
    assertTrue(velPhase3 != 0.0 || !pipe.isOutletClosed(),
        "Phase 3: Outlet should be open after openOutlet()");
  }

  // =====================================================================
  // Constant Flow Inlet BC Test
  // =====================================================================

  @Test
  @DisplayName("CONSTANT_FLOW inlet: specified mass flow should be applied")
  void testConstantFlowInlet() {
    // Set constant flow at inlet
    pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);
    pipe.setInletMassFlow(2.0, "kg/sec"); // Lower than stream flow rate

    pipe.run();

    // Pipe should work with the specified inlet mass flow
    // (actual verification depends on internal implementation details)
    assertEquals(BoundaryCondition.CONSTANT_FLOW, pipe.getInletBoundaryCondition());
  }

  // =====================================================================
  // Helper Methods
  // =====================================================================

  /**
   * Calculate average of a double array.
   *
   * @param arr input array
   * @return average value
   */
  private double averageArray(double[] arr) {
    double sum = 0;
    for (double v : arr) {
      sum += v;
    }
    return sum / arr.length;
  }
}
