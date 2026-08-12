package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe.OLGAModelType;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.FlowRegimeDetector;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for gas-liquid phase appearance and disappearance in {@link TwoFluidPipe}. */
class TwoFluidPipePhaseDegeneracyTest {
  private static final double[] LIQUID_MASS_FLOW_SWEEP = { 0.0, 0.5e-10, 0.99e-10, 1.01e-10, 2.0e-10, 1.0e-9, 1.0e-8 };

  @Test
  void testLocalClosureApproachesPureGasContinuously() throws Exception {
    OLGAModelType[] modelTypes = { OLGAModelType.FULL, OLGAModelType.SIMPLIFIED, OLGAModelType.DRIFT_FLUX };
    FlowRegime[] regimes = { FlowRegime.STRATIFIED_SMOOTH, FlowRegime.ANNULAR, FlowRegime.SLUG,
        FlowRegime.DISPERSED_BUBBLE };
    double[] inclinations = { 0.0, Math.toRadians(2.0), Math.toRadians(-2.0) };

    for (OLGAModelType modelType : modelTypes) {
      for (FlowRegime regime : regimes) {
        for (double inclination : inclinations) {
          assertContinuousSweep(modelType, regime, inclination, true, true, 0.001);
          assertContinuousSweep(modelType, regime, inclination, true, false, 0.001);
          assertContinuousSweep(modelType, regime, inclination, false, true, 0.0);
          assertContinuousSweep(modelType, regime, inclination, false, false, 0.0);
        }
      }
    }
  }

  @Test
  void testPublicApiMassFlowSweepApproachesPureGasContinuously() {
    for (OLGAModelType modelType : OLGAModelType.values()) {
      double previousMaximumHoldup = -1.0;
      double pureGasPressureDrop = 0.0;
      for (double liquidMassFlow : LIQUID_MASS_FLOW_SWEEP) {
        TwoFluidPipe pipe = createControlledTwoPhasePipe(liquidMassFlow);
        pipe.setOLGAModelType(modelType);
        pipe.setUseAdaptiveMinimumOnly(true);
        pipe.setLength(2.0);
        pipe.setNumberOfSections(2);
        pipe.setEnableTerrainTracking(false);
        pipe.setEnableSlugTracking(false);
        pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
        pipe.setSteadyStateMaxWallClockTime(1.0);
        pipe.run();

        double maximumHoldup = maximum(pipe.getLiquidHoldupProfile());
        double[] pressure = pipe.getPressureProfile();
        double pressureDrop = pressure[0] - pressure[pressure.length - 1];
        assertTrue(Double.isFinite(pressureDrop));
        if (liquidMassFlow == 0.0) {
          assertEquals(0.0, maximumHoldup, 0.0);
          pureGasPressureDrop = pressureDrop;
        } else {
          assertTrue(maximumHoldup > 0.0);
          assertTrue(maximumHoldup < 1.0e-4, modelType + " public run imposed a finite trace-liquid inventory");
          assertTrue(Math.abs(pressureDrop - pureGasPressureDrop) < 100.0,
              modelType + " pressure drop did not approach its pure-gas limit");
        }
        assertTrue(maximumHoldup + 1.0e-15 >= previousMaximumHoldup,
            modelType + " public holdup was not monotonic across phase appearance");
        previousMaximumHoldup = maximumHoldup;
      }
    }
  }

  @Test
  void testExplicitFixedFloorRemainsOptInAndNeverCreatesAnAbsentPhase() throws Exception {
    TwoFluidPipe pipe = createPipe();
    pipe.setUseAdaptiveMinimumOnly(false);
    pipe.setEnforceMinimumSlip(true);
    pipe.setMinimumLiquidHoldup(0.002);

    TwoFluidSection section = createSection(0.0, FlowRegime.ANNULAR);
    Method closure = getLocalHoldupClosure();
    double area = Math.PI * 0.2 * 0.2 / 4.0;

    double[] absent = (double[]) closure.invoke(pipe, section, null, 1.0, 0.0, area);
    assertEquals(0.0, absent[0], 0.0);
    double[] present = (double[]) closure.invoke(pipe, section, null, 1.0, 1.0e-8, area);
    assertTrue(present[0] >= 0.002);
  }

  @Test
  void testReverseFlowUsesPhaseFlowMagnitudesForHoldup() throws Exception {
    Method closure = getLocalHoldupClosure();
    double area = Math.PI * 0.2 * 0.2 / 4.0;

    for (OLGAModelType modelType : OLGAModelType.values()) {
      TwoFluidPipe pipe = createPipe();
      pipe.setOLGAModelType(modelType);
      TwoFluidSection section = createSection(0.0, FlowRegime.STRATIFIED_SMOOTH);
      double[] forward = (double[]) closure.invoke(pipe, section, null, 1.0, 1.0e-8, area);
      double[] reverse = (double[]) closure.invoke(pipe, section, null, -1.0, -1.0e-8, area);

      assertEquals(forward[0], reverse[0], 0.0);
      assertEquals(forward[1], reverse[1], 0.0);
      assertTrue(reverse[0] > 0.0, modelType + " reverse flow was misclassified as an absent liquid phase");
    }
  }

  @Test
  void testPureGasPublicRunAndTransientKeepLiquidExactlyAbsent() {
    for (OLGAModelType modelType : OLGAModelType.values()) {
      TwoFluidPipe pipe = createPipe();
      pipe.setOLGAModelType(modelType);
      pipe.setLength(20.0);
      pipe.setNumberOfSections(4);
      pipe.setEnableTerrainTracking(false);
      pipe.setEnableSlugTracking(false);
      pipe.setEnableAdaptiveTimestepping(false);
      pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
      pipe.setSteadyStateMaxWallClockTime(1.0);

      pipe.run();
      assertAllExactlyZero(pipe.getLiquidHoldupProfile());
      assertAllExactlyZero(pipe.getOilHoldupProfile());
      assertAllExactlyZero(pipe.getWaterHoldupProfile());
      assertAllFinite(pipe.getGasVelocityProfile());
      assertAllFinite(pipe.getPressureProfile());

      double[] firstSteadyHoldup = pipe.getLiquidHoldupProfile();
      pipe.run();
      assertEquals(firstSteadyHoldup.length, pipe.getLiquidHoldupProfile().length);
      assertAllExactlyZero(pipe.getLiquidHoldupProfile());

      TwoFluidPipe copy = (TwoFluidPipe) pipe.copy();
      assertAllExactlyZero(copy.getLiquidHoldupProfile());
      assertEquals(pipe.getOLGAModelType(), copy.getOLGAModelType());

      pipe.runTransient(1.0e-4, UUID.fromString("00000000-0000-0000-0000-000000002733"));
      assertAllExactlyZero(pipe.getLiquidHoldupProfile());
      TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
      assertNotNull(report);
      assertEquals(0.0, report.getInitialMassKg(Phase.LIQUID), 0.0);
      assertEquals(0.0, report.getFinalMassKg(Phase.LIQUID), 0.0);
      assertEquals(0.0, report.getResidualKg(Phase.LIQUID), 0.0);
    }
  }

  @Test
  void testTraceLiquidSteadyToTransientHandoffRemainsFiniteAndConservative() {
    TwoFluidPipe pipe = createControlledTwoPhasePipe(1.0e-8);
    pipe.setLength(2.0);
    pipe.setNumberOfSections(2);
    pipe.setEnableTerrainTracking(false);
    pipe.setEnableSlugTracking(false);
    pipe.setEnableAdaptiveTimestepping(false);
    pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
    pipe.setSteadyStateMaxWallClockTime(1.0);

    pipe.run();
    assertTrue(maximum(pipe.getLiquidHoldupProfile()) < 1.0e-4);
    pipe.runTransient(1.0e-6, UUID.fromString("00000000-0000-0000-0000-000000012733"));

    assertTrue(maximum(pipe.getLiquidHoldupProfile()) < 1.0e-4);
    assertAllFinite(pipe.getGasVelocityProfile());
    assertAllFinite(pipe.getLiquidVelocityProfile());
    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    assertNotNull(report);
    assertTrue(report.getInitialMassKg(Phase.LIQUID) > 0.0);
    assertTrue(report.getFinalMassKg(Phase.LIQUID) > 0.0);
    assertTrue(report.isWithinTolerance(Phase.LIQUID, 1.0e-10, 1.0e-8));
  }

  @Test
  void testPrimitiveExtractionDoesNotSeedAbsentOilOrWater() {
    TwoFluidSection section = createSection(0.0, FlowRegime.STRATIFIED_SMOOTH);
    section.setOilDensity(700.0);
    section.setWaterDensity(1000.0);
    double area = section.getArea();

    double gasMassPerLength = 0.8 * 40.0 * area;
    double waterMassPerLength = 0.2 * 1000.0 * area;
    section.setStateVector(new double[] { gasMassPerLength, 0.0, waterMassPerLength, gasMassPerLength, 0.0,
        waterMassPerLength * 0.1, 0.0 });
    section.extractPrimitiveVariables();
    assertEquals(0.0, section.getOilMassPerLength(), 0.0);
    assertEquals(0.0, section.getOilHoldup(), 0.0);
    assertTrue(section.getWaterHoldup() > 0.0);

    double oilMassPerLength = 0.2 * 700.0 * area;
    section.setStateVector(
        new double[] { gasMassPerLength, oilMassPerLength, 0.0, gasMassPerLength, oilMassPerLength * 0.1, 0.0, 0.0 });
    section.extractPrimitiveVariables();
    assertEquals(0.0, section.getWaterMassPerLength(), 0.0);
    assertEquals(0.0, section.getWaterHoldup(), 0.0);
    assertTrue(section.getOilHoldup() > 0.0);
  }

  @Test
  void testOilWaterInterfacialShearVanishesWithEitherLiquidPhase() {
    TwoFluidSection section = createSection(0.0, FlowRegime.STRATIFIED_SMOOTH);
    section.setOilDensity(700.0);
    section.setWaterDensity(1000.0);
    section.setOilVelocity(0.5);
    section.setWaterVelocity(0.1);

    section.setOilHoldup(0.2);
    section.setWaterHoldup(0.0);
    assertEquals(0.0, section.calcOilWaterInterfacialShear(), 0.0);

    double previousShear = 0.0;
    for (double waterHoldup : new double[] { 1.0e-12, 1.0e-10, 1.0e-8, 1.0e-6 }) {
      section.setWaterHoldup(waterHoldup);
      double shear = section.calcOilWaterInterfacialShear();
      assertTrue(Double.isFinite(shear));
      assertTrue(shear > previousShear);
      previousShear = shear;
    }

    section.setOilHoldup(0.0);
    section.setWaterHoldup(0.2);
    assertEquals(0.0, section.calcOilWaterInterfacialShear(), 0.0);
  }

  @Test
  void testTraceLiquidHoldupIsNotRoundedOutOfSectionState() {
    TwoFluidSection section = createSection(0.0, FlowRegime.STRATIFIED_SMOOTH);
    section.setOilHoldup(1.0e-12);
    section.setWaterHoldup(0.0);
    assertEquals(1.0e-12, section.getLiquidHoldup(), 0.0);

    section.setOilHoldup(0.0);
    section.setWaterCut(1.0);
    section.setLiquidHoldup(5.0e-13);
    assertEquals(0.0, section.getOilHoldup(), 0.0);
    assertEquals(5.0e-13, section.getWaterHoldup(), 0.0);
    assertEquals(5.0e-13, section.getLiquidHoldup(), 0.0);
  }

  @Test
  void testFlowRegimePresenceUsesConservativeHoldupWithoutVelocityThreshold() {
    FlowRegimeDetector detector = new FlowRegimeDetector();
    TwoFluidSection section = createSection(0.0, FlowRegime.STRATIFIED_SMOOTH);
    section.setGasHoldup(1.0);
    section.setGasVelocity(1.0);
    section.setLiquidHoldup(0.0);
    section.setLiquidVelocity(1.0);
    assertEquals(FlowRegime.SINGLE_PHASE_GAS, detector.detectFlowRegime(section));

    section.setLiquidHoldup(1.0e-12);
    section.setGasHoldup(1.0 - 1.0e-12);
    assertTrue(detector.detectFlowRegime(section) != FlowRegime.SINGLE_PHASE_GAS);
  }

  private void assertContinuousSweep(OLGAModelType modelType, FlowRegime regime, double inclination,
      boolean adaptiveOnly, boolean enforceMinimumSlip, double minimumLiquidHoldup) throws Exception {
    TwoFluidPipe pipe = createPipe();
    pipe.setOLGAModelType(modelType);
    pipe.setEnableTerrainTracking(false);
    pipe.setUseAdaptiveMinimumOnly(adaptiveOnly);
    pipe.setEnforceMinimumSlip(enforceMinimumSlip);
    pipe.setMinimumLiquidHoldup(minimumLiquidHoldup);

    TwoFluidSection section = createSection(inclination, regime);
    Method closure = getLocalHoldupClosure();

    double area = Math.PI * 0.2 * 0.2 / 4.0;
    double previousHoldup = -1.0;
    for (double liquidMassFlow : LIQUID_MASS_FLOW_SWEEP) {
      double[] result = (double[]) closure.invoke(pipe, section, null, 1.0, liquidMassFlow, area);
      double liquidHoldup = result[0];

      assertTrue(Double.isFinite(liquidHoldup));
      assertTrue(liquidHoldup >= 0.0 && liquidHoldup <= 1.0);
      if (liquidMassFlow == 0.0) {
        assertEquals(0.0, liquidHoldup, 0.0);
        assertEquals(1.0, result[1], 0.0);
      } else {
        assertTrue(liquidHoldup > 0.0);
        assertTrue(liquidHoldup < 1.0e-4,
            modelType + "/" + regime + " imposed a finite trace-liquid inventory at " + liquidMassFlow + " kg/s");
        double inventoryKgPerKilometre = liquidHoldup * 700.0 * area * 1000.0;
        assertTrue(inventoryKgPerKilometre < 2.0,
            modelType + "/" + regime + " created excessive trace-liquid line inventory");
      }
      assertTrue(liquidHoldup + 1.0e-15 >= previousHoldup,
          modelType + "/" + regime + " holdup was not monotonic across phase appearance");
      previousHoldup = liquidHoldup;
    }
  }

  private TwoFluidPipe createPipe() {
    SystemInterface fluid = new SystemSrkEos(293.15, 80.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("phase-degeneracy-inlet", fluid);
    inlet.setFlowRate(1.0, "kg/sec");
    inlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe("phase-degeneracy-pipe", inlet);
    pipe.setDiameter(0.2);
    return pipe;
  }

  private TwoFluidPipe createControlledTwoPhasePipe(double liquidMassFlow) {
    if (liquidMassFlow == 0.0) {
      return createPipe();
    }

    SystemInterface fluid = new SystemSrkEos(293.15, 80.0);
    double methaneMolarMass = 0.016043;
    double decaneMolarMass = 0.142286;
    fluid.addComponent("methane", 1.0 / methaneMolarMass, 0);
    double liquidMoles = liquidMassFlow / decaneMolarMass;
    fluid.addComponent("nC10", liquidMoles, 1);
    fluid.setMixingRule("classic");
    fluid.setPhaseType(0, PhaseType.GAS);
    fluid.setPhaseType(1, PhaseType.OIL);
    fluid.initBeta();
    fluid.init_x_y();
    fluid.init(3);
    fluid.initPhysicalProperties();

    Stream inlet = new Stream("controlled-phase-degeneracy-inlet", fluid);
    // Do not run a TP flash here: this fixture deliberately controls a metastable trace phase.
    // Component mole rates directly define the intended phase mass flows without re-scaling phase 0.
    double initializedLiquidMassFlow = inlet.getFluid().getPhase("oil").getFlowRate("kg/sec");
    assertEquals(liquidMassFlow, initializedLiquidMassFlow, liquidMassFlow * 1.0e-3);
    TwoFluidPipe pipe = new TwoFluidPipe("controlled-phase-degeneracy-pipe", inlet);
    pipe.setDiameter(0.2);
    return pipe;
  }

  private Method getLocalHoldupClosure() throws NoSuchMethodException {
    Method closure = TwoFluidPipe.class.getDeclaredMethod("calculateLocalHoldup", TwoFluidSection.class,
        TwoFluidSection.class, double.class, double.class, double.class);
    closure.setAccessible(true);
    return closure;
  }

  private TwoFluidSection createSection(double inclination, FlowRegime regime) {
    TwoFluidSection section = new TwoFluidSection(0.0, 1.0, 0.2, inclination);
    section.setGasDensity(40.0);
    section.setLiquidDensity(700.0);
    section.setGasViscosity(1.2e-5);
    section.setLiquidViscosity(1.0e-3);
    section.setSurfaceTension(0.025);
    section.setFlowRegime(regime);
    return section;
  }

  private void assertAllExactlyZero(double[] values) {
    assertTrue(values.length > 0);
    for (double value : values) {
      assertEquals(0.0, value, 0.0);
    }
  }

  private void assertAllFinite(double[] values) {
    assertTrue(values.length > 0);
    for (double value : values) {
      assertTrue(Double.isFinite(value));
    }
  }

  private double maximum(double[] values) {
    double maximum = Double.NEGATIVE_INFINITY;
    for (double value : values) {
      maximum = Math.max(maximum, value);
    }
    return maximum;
  }
}
