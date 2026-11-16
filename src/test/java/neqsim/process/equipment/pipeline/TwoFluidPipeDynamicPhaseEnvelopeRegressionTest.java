package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Phase-inventory transport and infinitesimal handoff checks, distinct from long-horizon dynamic qualification. */
class TwoFluidPipeDynamicPhaseEnvelopeRegressionTest {
  private static final double INITIAL_MASS_FLOW = 0.3;
  private static final String[] PHASE_NAMES = {"gas", "oil", "aqueous"};
  private static final Phase[] REPORT_PHASES = {Phase.GAS, Phase.OIL, Phase.WATER};

  /**
   * Qualify a finite held-boundary interval, rather than only the infinitesimal initialization limit.
   *
   * @param mixture phases present in the reference fixture
   */
  @ParameterizedTest
  @EnumSource(PhaseCombination.class)
  void heldBoundariesRemainStableForAllPhaseCombinations(PhaseCombination mixture) {
    TwoFluidPipe pipe = createPipe(mixture, TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION);
    double[] initialPressure = pipe.getPressureProfile();
    double[] initialHoldup = pipe.getLiquidHoldupProfile();
    for (int step = 0; step < 100; step++) {
      pipe.runTransient(0.1, UUID.randomUUID());
      assertEquals(INITIAL_MASS_FLOW, pipe.getOutletStream().getFlowRate("kg/sec"), 0.01 * INITIAL_MASS_FLOW,
          mixture + " held-boundary rate drift exceeds one percent");
      assertProfilesClose(initialPressure, pipe.getPressureProfile(), 600.0,
          mixture + " pressure drift exceeds 0.01 percent of the 60 bara reference");
      assertProfilesClose(initialHoldup, pipe.getLiquidHoldupProfile(), 0.001,
          mixture + " absolute holdup drift exceeds 0.001");
      assertPhaseBalanceAndBounds(pipe, mixture);
    }
    assertEquals(10.0, pipe.getSimulationTime(), 1.0e-12);
  }

  /**
   * Verify that a checkpoint preserves adaptive history and the subsequent conservative trajectory.
   *
   * @param mixture phases present in the reference fixture
   * @throws Exception if reflection or serialization fails
   */
  @ParameterizedTest
  @EnumSource(PhaseCombination.class)
  void serializedAdaptivePipeMatchesUninterruptedTrajectory(PhaseCombination mixture) throws Exception {
    TwoFluidPipe original = createPipe(mixture, TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION);
    original.runTransient(0.02, UUID.randomUUID());
    Field multiplier = TwoFluidPipe.class.getDeclaredField("adaptiveDtFactor");
    multiplier.setAccessible(true);
    multiplier.setDouble(original, 0.5);
    TwoFluidPipe restored = checkpointRoundTrip(original);
    assertEquals(original.getAdaptiveDtFactor(), restored.getAdaptiveDtFactor(), 0.0);
    assertEquals(original.getSimulationTime(), restored.getSimulationTime(), 0.0);
    assertEquals(original.getTotalMassInventory(), restored.getTotalMassInventory(), 0.0);
    for (int step = 0; step < 10; step++) {
      original.runTransient(0.02, UUID.randomUUID());
      restored.runTransient(0.02, UUID.randomUUID());
      assertEquals(original.getSimulationTime(), restored.getSimulationTime(), 1.0e-14);
      assertProfilesClose(original.getPressureProfile(), restored.getPressureProfile(), 1.0e-7,
          mixture + " checkpoint pressure trajectory");
      assertProfilesClose(original.getLiquidHoldupProfile(), restored.getLiquidHoldupProfile(), 1.0e-12,
          mixture + " checkpoint holdup trajectory");
      assertEquals(original.getTotalMassInventory(), restored.getTotalMassInventory(), 1.0e-10);
      assertPhaseBalanceAndBounds(restored, mixture);
    }
  }

  /**
   * Older streams omitted the adaptive multiplier; its Java deserialization default is zero.
   *
   * @throws Exception if reflection or serialization fails
   */
  @Test
  void missingLegacyAdaptiveMultiplierRestartsAtUnity() throws Exception {
    TwoFluidPipe pipe = createPipe(PhaseCombination.GAS_OIL_WATER, TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION);
    Field multiplier = TwoFluidPipe.class.getDeclaredField("adaptiveDtFactor");
    multiplier.setAccessible(true);
    multiplier.setDouble(pipe, 0.0);
    TwoFluidPipe restored = checkpointRoundTrip(pipe);
    assertEquals(1.0, restored.getAdaptiveDtFactor(), 0.0);
    restored.runTransient(0.02, UUID.randomUUID());
    assertEquals(0.02, restored.getSimulationTime(), 1.0e-14);
    assertPhaseBalanceAndBounds(restored, PhaseCombination.GAS_OIL_WATER);
  }

  /**
   * Round-trip the complete pipe graph through the public Java serialization contract.
   *
   * @param pipe accepted pipe state
   * @return independent restored pipe
   * @throws Exception if serialization fails
   */
  private static TwoFluidPipe checkpointRoundTrip(TwoFluidPipe pipe) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(pipe);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (TwoFluidPipe) input.readObject();
    }
  }

  /** Check that an equilibrium gas split cannot destabilize source-free acoustic transport of oil. */
  @Test
  void liquidFullWellPreservesAcousticDensityAcrossEquilibriumFlashRefresh() throws Exception {
    SystemInterface fluid = new SystemPrEos(353.15, 200.0);
    String[] names = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10"};
    double[] amounts = {0.5, 1.0, 45.0, 5.0, 5.0, 2.0, 3.0, 2.0, 2.0, 5.0, 8.0, 10.0, 6.0, 5.5};
    for (int component = 0; component < names.length; component++) {
      fluid.addComponent(names[component], amounts[component]);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("well feed", fluid);
    feed.setFlowRate(20000.0, "kg/hr");
    feed.run();
    assertFalse(feed.getFluid().hasPhaseType("gas"));

    TwoFluidPipe pipe = new TwoFluidPipe("liquid-full well", feed);
    pipe.setLength(2000.0);
    pipe.setDiameter(0.1);
    pipe.setRoughness(1.0e-5);
    pipe.setNumberOfSections(20);
    double[] elevations = new double[21];
    for (int face = 0; face < elevations.length; face++) {
      elevations[face] = face * 100.0;
    }
    pipe.setCellFaceElevationProfile(elevations);
    pipe.setOutletPressure(102.9531305, "bara");
    pipe.setEnableAdaptiveTimestepping(true);
    pipe.setThermodynamicUpdateInterval(1);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    assertTrue(pipe.isSteadyStateConverged());
    TwoFluidSection top = sections(pipe)[19];
    assertTrue(localEquilibriumState(pipe, top).hasPhaseType("gas"));
    assertEquals(0.0, top.getGasMassPerLength(), 0.0);
    double[] initialPressure = pipe.getPressureProfile();

    for (int step = 0; step < 20; step++) {
      pipe.runTransient(0.01, UUID.randomUUID());
      assertProfilesClose(initialPressure, pipe.getPressureProfile(), 1.0e4,
          "Repeated equilibrium flashes must not amplify held-boundary pressure errors");
    }
    assertEquals(0.2, pipe.getSimulationTime(), 1.0e-12);
    assertEquals(20000.0, pipe.getOutletStream().getFlowRate("kg/hr"), 200.0);
    assertTrue(pipe.isCoupledPressureMomentumEnabled());
    assertFalse(pipe.isTransientCoupledPressureMomentumFailureDetected());

    feed.setFlowRate(15000.0, "kg/hr");
    feed.run();
    for (int step = 0; step < 15; step++) {
      pipe.runTransient(1.0, UUID.randomUUID());
      TwoFluidMassBalanceReport balance = pipe.getLastMassBalanceReport();
      assertEquals(0.0, balance.getInitialMassKg(Phase.GAS), 0.0);
      assertEquals(0.0, balance.getFinalMassKg(Phase.GAS), 0.0);
      assertTrue(balance.isWithinTolerance(Phase.OIL, 1.0e-8, 1.0e-10));
    }
    assertEquals(15.2, pipe.getSimulationTime(), 1.0e-10);
    assertTrue(pipe.getOutletStream().getFlowRate("kg/hr") < 19500.0);
    assertFalse(pipe.isTransientCoupledPressureMomentumFailureDetected());
  }

  enum PhaseCombination {
    GAS(true, false, false), OIL(false, true, false), WATER(false, false, true), GAS_OIL(true, true, false),
    GAS_WATER(true, false, true), OIL_WATER(false, true, true), GAS_OIL_WATER(true, true, true);

    private final boolean[] present;

    PhaseCombination(boolean gas, boolean oil, boolean water) {
      present = new boolean[] {gas, oil, water};
    }
  }

  @ParameterizedTest
  @EnumSource(PhaseCombination.class)
  void unchangedBoundariesPreserveSteadySolutionAcrossDynamicInitialization(PhaseCombination mixture) {
    for (TimeIntegrator.Method method : new TimeIntegrator.Method[] {TimeIntegrator.Method.RK2,
        TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION}) {
      TwoFluidPipe pipe = createPipe(mixture, method);
      double[] initialPressure = pipe.getPressureProfile();
      double[] initialLiquidHoldup = pipe.getLiquidHoldupProfile();
      double[] initialOilHoldup = pipe.getOilHoldupProfile();
      double[] initialWaterHoldup = pipe.getWaterHoldupProfile();
      double initialMass = pipe.getTotalMassInventory();

      pipe.runTransient(1.0e-8, UUID.randomUUID());

      assertEquals(1.0e-8, pipe.getSimulationTime(), 1.0e-20);
      assertTrue(pipe.isCoupledPressureMomentumConverged(), mixture + " " + method);
      assertProfilesClose(initialPressure, pipe.getPressureProfile(), 1.0,
          mixture + " " + method + " pressure jumped at the steady/transient handoff");
      assertProfilesClose(initialLiquidHoldup, pipe.getLiquidHoldupProfile(), 1.0e-7,
          mixture + " " + method + " liquid inventory jumped at the steady/transient handoff");
      assertProfilesClose(initialOilHoldup, pipe.getOilHoldupProfile(), 1.0e-7,
          mixture + " " + method + " oil inventory jumped at the steady/transient handoff");
      assertProfilesClose(initialWaterHoldup, pipe.getWaterHoldupProfile(), 1.0e-7,
          mixture + " " + method + " water inventory jumped at the steady/transient handoff");
      assertEquals(initialMass, pipe.getTotalMassInventory(), 4.0 * INITIAL_MASS_FLOW * 1.0e-8);
      assertPhaseBalanceAndBounds(pipe, mixture);
    }
  }

  @ParameterizedTest
  @EnumSource(PhaseCombination.class)
  void inletFlowStepConservesEachPhaseIncludingLiquidOnlyLimits(PhaseCombination mixture) {
    for (TimeIntegrator.Method method : new TimeIntegrator.Method[] {TimeIntegrator.Method.RK2,
        TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION}) {
      TwoFluidPipe pipe = createPipe(mixture, method);
      Stream feed = (Stream) pipe.getInletStream();
      double increasedFlow = 1.1 * INITIAL_MASS_FLOW;
      feed.setFlowRate(increasedFlow, "kg/sec");
      feed.run();
      double[] phaseFractions = new double[3];
      for (int phase = 0; phase < 3; phase++) {
        if (mixture.present[phase]) {
          phaseFractions[phase] = feed.getFluid().getPhase(PHASE_NAMES[phase]).getFlowRate("kg/sec") / increasedFlow;
        }
      }

      double interval = 0.02;
      for (int step = 0; step < 50; step++) {
        pipe.runTransient(interval, UUID.randomUUID());
        assertTrue(pipe.isCoupledPressureMomentumConverged(), mixture + " " + method);
        assertEquals((step + 1) * interval, pipe.getSimulationTime(), 1.0e-14);
        TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
        for (int phase = 0; phase < 3; phase++) {
          assertEquals(increasedFlow * phaseFractions[phase] * interval, report.getInletMassKg(REPORT_PHASES[phase]),
              1.0e-10, mixture + " " + method + " must integrate the prescribed " + PHASE_NAMES[phase] + " feed");
        }
        assertPhaseBalanceAndBounds(pipe, mixture);
      }
    }
  }

  @Test
  void liquidSpecificEnthalpyUsesMassWeightsAtThreePhaseInitialization() throws Exception {
    TwoFluidPipe pipe = createUnrunPipe(PhaseCombination.GAS_OIL_WATER, TimeIntegrator.Method.RK2);
    initializeSections(pipe);
    SystemInterface fluid = pipe.getInletStream().getFluid();
    double oilMassFlow = fluid.getPhase("oil").getFlowRate("kg/sec");
    double waterMassFlow = fluid.getPhase("aqueous").getFlowRate("kg/sec");
    double expectedLiquidEnthalpy = (oilMassFlow * fluid.getPhase("oil").getEnthalpy("J/kg")
        + waterMassFlow * fluid.getPhase("aqueous").getEnthalpy("J/kg")) / (oilMassFlow + waterMassFlow);
    for (TwoFluidSection section : sections(pipe)) {
      assertEquals(expectedLiquidEnthalpy, section.getLiquidEnthalpy(), 1.0e-8,
          "A liquid specific enthalpy in J/kg must conserve oil plus water energy, with mass weights");
    }
  }

  @Test
  void steadyFlashPreservesHydraulicLiquidSplitWhileRefreshingTransportedFraction() throws Exception {
    TwoFluidPipe pipe = createUnrunPipe(PhaseCombination.GAS_OIL_WATER, TimeIntegrator.Method.RK2);
    initializeSections(pipe);
    for (TwoFluidSection section : sections(pipe)) {
      section.setGasHoldup(0.2);
      section.setLiquidHoldup(0.8);
      section.setWaterCut(0.4);
      section.setOilFractionInLiquid(0.6);
      section.setWaterHoldup(0.32);
      section.setOilHoldup(0.48);
      section.setInputWaterVolumeFraction(0.9);
    }

    refreshSteadyThermodynamics(pipe);

    for (TwoFluidSection section : sections(pipe)) {
      assertEquals(0.4, section.getWaterCut(), 1.0e-14,
          "A periodic flash must not reset the solved in-situ water cut to the transported no-slip fraction");
      assertEquals(0.32, section.getWaterHoldup(), 1.0e-14);
      assertEquals(0.48, section.getOilHoldup(), 1.0e-14);
      assertTrue(section.getInputWaterVolumeFraction() > 0.0 && section.getInputWaterVolumeFraction() < 0.3,
          "The input split must still follow the local equilibrium phase flows");
    }
  }

  @Test
  void steadyFlashCanIntroduceTheSecondLiquidAtEitherExactEndpoint() throws Exception {
    for (double previousWaterCut : new double[] {0.0, 1.0}) {
      TwoFluidPipe pipe = createUnrunPipe(PhaseCombination.GAS_OIL_WATER, TimeIntegrator.Method.RK2);
      initializeSections(pipe);
      for (TwoFluidSection section : sections(pipe)) {
        section.setGasHoldup(0.2);
        section.setLiquidHoldup(0.8);
        section.setWaterCut(previousWaterCut);
        section.setOilFractionInLiquid(1.0 - previousWaterCut);
        section.setWaterHoldup(0.8 * previousWaterCut);
        section.setOilHoldup(0.8 * (1.0 - previousWaterCut));
      }

      refreshSteadyThermodynamics(pipe);

      for (TwoFluidSection section : sections(pipe)) {
        assertTrue(section.getWaterHoldup() > 0.0, "A flashed aqueous phase must enter an oil-only endpoint");
        assertTrue(section.getOilHoldup() > 0.0, "A flashed oil phase must enter a water-only endpoint");
        assertEquals(0.8, section.getLiquidHoldup(), 1.0e-14);
        assertEquals(0.8, section.getOilHoldup() + section.getWaterHoldup(), 1.0e-14);
      }
    }
  }

  @Test
  void steadyFlashLiquidDisappearancePreservesTotalHydraulicHoldup() throws Exception {
    for (PhaseCombination mixture : new PhaseCombination[] {PhaseCombination.GAS_OIL, PhaseCombination.GAS_WATER}) {
      TwoFluidPipe pipe = createUnrunPipe(mixture, TimeIntegrator.Method.RK2);
      initializeSections(pipe);
      for (TwoFluidSection section : sections(pipe)) {
        section.setGasHoldup(0.2);
        section.setLiquidHoldup(0.8);
        section.setWaterCut(0.4);
        section.setWaterHoldup(0.32);
        section.setOilHoldup(0.48);
      }

      refreshSteadyThermodynamics(pipe);

      for (TwoFluidSection section : sections(pipe)) {
        assertEquals(0.8, section.getLiquidHoldup(), 1.0e-14,
            "Updating phase identity must preserve total hydraulic holdup before the next momentum closure");
        assertEquals(mixture.present[1] ? 0.8 : 0.0, section.getOilHoldup(), 1.0e-14);
        assertEquals(mixture.present[2] ? 0.8 : 0.0, section.getWaterHoldup(), 1.0e-14);
      }
    }
  }

  @ParameterizedTest
  @EnumSource(value = PhaseCombination.class, names = {"OIL_WATER", "GAS_OIL_WATER"})
  void steadySlipClosureTransportsEachLocalEquilibriumPhaseMassFlow(PhaseCombination mixture) throws Exception {
    TwoFluidPipe pipe = createPipe(mixture, TimeIntegrator.Method.RK2);
    for (TwoFluidSection section : sections(pipe)) {
      SystemInterface flash = localEquilibriumState(pipe, section);
      double[] actualMassFlow = {section.getGasMassPerLength() * section.getGasVelocity(),
          section.getOilMassPerLength() * section.getOilVelocity(),
          section.getWaterMassPerLength() * section.getWaterVelocity()};
      for (int phase = 0; phase < 3; phase++) {
        double expectedMassFlow = flash.hasPhaseType(PHASE_NAMES[phase])
            ? INITIAL_MASS_FLOW * flash.getPhase(PHASE_NAMES[phase]).getMass() / flash.getMass("kg")
            : 0.0;
        assertEquals(expectedMassFlow, actualMassFlow[phase], 1.0e-4 * INITIAL_MASS_FLOW, mixture
            + " slip closure changed transported " + PHASE_NAMES[phase] + " mass flow at x=" + section.getPosition());
      }
      double expectedBulkVelocity = (actualMassFlow[1] + actualMassFlow[2])
          / (section.getOilMassPerLength() + section.getWaterMassPerLength());
      assertEquals(expectedBulkVelocity, section.getLiquidVelocity(), 1.0e-10,
          "The steady bulk liquid velocity must match the conservative mixture recovered on the first dynamic step");
    }
  }

  @ParameterizedTest
  @EnumSource(value = PhaseCombination.class, names = {"OIL_WATER", "GAS_OIL_WATER"})
  void finalSteadyLiquidEnthalpyMatchesTheActualSlippingPhaseInventories(PhaseCombination mixture) throws Exception {
    TwoFluidPipe pipe = createPipe(mixture, TimeIntegrator.Method.RK2);
    for (TwoFluidSection section : sections(pipe)) {
      SystemInterface flash = localEquilibriumState(pipe, section);
      double expectedEnthalpy = (section.getOilMassPerLength() * flash.getPhase("oil").getEnthalpy("J/kg")
          + section.getWaterMassPerLength() * flash.getPhase("aqueous").getEnthalpy("J/kg"))
          / (section.getOilMassPerLength() + section.getWaterMassPerLength());
      assertEquals(expectedEnthalpy, section.getLiquidEnthalpy(), 1.0e-8 * Math.max(1.0, Math.abs(expectedEnthalpy)),
          "The final conserved energy must use the final in-situ oil/water masses at x=" + section.getPosition());
    }
  }

  private static TwoFluidPipe createPipe(PhaseCombination mixture, TimeIntegrator.Method method) {
    TwoFluidPipe pipe = createUnrunPipe(mixture, method);
    pipe.run();
    assertTrue(pipe.isSteadyStateConverged(), mixture + " requires a converged stationary reference");
    assertFalse(pipe.isSteadyStateWallClockLimited(), mixture + " stationary reference exceeded its time budget");
    assertFalse(pipe.isSteadyStatePressureFloorLimited(), mixture + " stationary reference reached its pressure floor");
    assertPhaseBounds(pipe, mixture);
    return pipe;
  }

  private static TwoFluidPipe createUnrunPipe(PhaseCombination mixture, TimeIntegrator.Method method) {
    SystemInterface fluid = new SystemSrkEos(298.15, 60.0);
    if (mixture.present[0]) {
      fluid.addComponent("methane", 1.0);
    }
    if (mixture.present[1]) {
      fluid.addComponent("n-decane", 1.0);
    }
    if (mixture.present[2]) {
      fluid.addComponent("water", 1.0);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream(mixture + " feed", fluid);
    feed.setFlowRate(INITIAL_MASS_FLOW, "kg/sec");
    feed.run();
    for (int phase = 0; phase < 3; phase++) {
      assertEquals(mixture.present[phase], feed.getFluid().hasPhaseType(PHASE_NAMES[phase]),
          mixture + " fixture must contain the expected physical phases");
    }

    TwoFluidPipe pipe = new TwoFluidPipe(mixture + " dynamic regression", feed);
    pipe.setLength(40.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfSections(4);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setEnableInterfacialPressure(true);
    pipe.setTimeIntegrationMethod(method);
    pipe.setEnableAdaptiveTimestepping(true);
    pipe.setEnableSlugTracking(false);
    pipe.setEnableTerrainTracking(false);
    pipe.setIncludeMassTransfer(false);
    pipe.setIncludeEnergyEquation(false);
    pipe.setEnableJouleThomson(false);
    // Phase change and thermal accuracy are covered separately. Freeze flash refresh here
    // to isolate conservative mechanical transport and the missing-phase limits.
    pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    return pipe;
  }

  private static void initializeSections(TwoFluidPipe pipe) throws Exception {
    Method initialize = TwoFluidPipe.class.getDeclaredMethod("initializeSections");
    initialize.setAccessible(true);
    initialize.invoke(pipe);
  }

  private static TwoFluidSection[] sections(TwoFluidPipe pipe) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    return (TwoFluidSection[]) field.get(pipe);
  }

  /**
   * Refresh steady properties in the equilibrium phase-transfer mode exercised by the flash tests.
   *
   * @param pipe initialized test pipe
   * @throws Exception if reflective invocation fails
   */
  private static void refreshSteadyThermodynamics(TwoFluidPipe pipe) throws Exception {
    pipe.setIncludeMassTransfer(true);
    Method refresh = TwoFluidPipe.class.getDeclaredMethod("updateThermodynamicsWithCondensation", double.class,
        double[].class, double[].class, double.class, double.class);
    refresh.setAccessible(true);
    int sectionCount = sections(pipe).length;
    refresh.invoke(pipe, INITIAL_MASS_FLOW, new double[sectionCount], new double[sectionCount], 0.0, 0.0);
  }

  private static SystemInterface localEquilibriumState(TwoFluidPipe pipe, TwoFluidSection section) {
    SystemInterface flash = pipe.getInletStream().getFluid().clone();
    flash.setPressure(section.getPressure(), "Pa");
    flash.setTemperature(section.getTemperature(), "K");
    new ThermodynamicOperations(flash).TPflash();
    flash.initPhysicalProperties();
    return flash;
  }

  private static void assertPhaseBalanceAndBounds(TwoFluidPipe pipe, PhaseCombination mixture) {
    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    for (int phase = 0; phase < 3; phase++) {
      assertTrue(report.isWithinTolerance(REPORT_PHASES[phase], 1.0e-9, 1.0e-10),
          mixture + " " + PHASE_NAMES[phase] + " mass residual = " + report.getResidualKg(REPORT_PHASES[phase]));
      if (mixture.present[phase]) {
        assertTrue(report.getFinalMassKg(REPORT_PHASES[phase]) > 0.0,
            mixture + " lost a present phase: " + PHASE_NAMES[phase]);
      } else {
        assertEquals(0.0, report.getInitialMassKg(REPORT_PHASES[phase]), 0.0);
        assertEquals(0.0, report.getFinalMassKg(REPORT_PHASES[phase]), 0.0,
            mixture + " created an absent phase: " + PHASE_NAMES[phase]);
      }
    }
    assertPhaseBounds(pipe, mixture);
  }

  private static void assertPhaseBounds(TwoFluidPipe pipe, PhaseCombination mixture) {
    double[] liquid = pipe.getLiquidHoldupProfile();
    double[] oil = pipe.getOilHoldupProfile();
    double[] water = pipe.getWaterHoldupProfile();
    double[] pressure = pipe.getPressureProfile();
    for (int cell = 0; cell < liquid.length; cell++) {
      assertTrue(Double.isFinite(pressure[cell]) && pressure[cell] > 0.0, mixture + " nonphysical pressure");
      assertTrue(Double.isFinite(liquid[cell]) && liquid[cell] >= 0.0 && liquid[cell] <= 1.0);
      assertTrue(Double.isFinite(oil[cell]) && oil[cell] >= 0.0 && oil[cell] <= 1.0);
      assertTrue(Double.isFinite(water[cell]) && water[cell] >= 0.0 && water[cell] <= 1.0);
      assertEquals(liquid[cell], oil[cell] + water[cell], 1.0e-12);
      if (!mixture.present[0]) {
        assertEquals(1.0, liquid[cell], 1.0e-12, mixture + " created a gas void");
      }
      if (!mixture.present[1]) {
        assertEquals(0.0, oil[cell], 0.0, mixture + " created oil holdup");
      }
      if (!mixture.present[2]) {
        assertEquals(0.0, water[cell], 0.0, mixture + " created water holdup");
      }
    }
  }

  private static void assertProfilesClose(double[] expected, double[] actual, double tolerance, String message) {
    assertEquals(expected.length, actual.length);
    for (int cell = 0; cell < expected.length; cell++) {
      assertEquals(expected[cell], actual[cell], tolerance, message + " at cell " + cell);
    }
  }
}
