package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Closed-domain regressions for TwoFluidPipe transient thermal transport. */
class TwoFluidPipeClosedThermalTest {
  /** Cross-platform tolerance for independently initialised closed-domain temperature histories, in kelvin. */
  private static final double CLOSED_HISTORY_TOLERANCE_K = 1.0e-9;

  /** Absolute tolerance for closed-domain energy balance, in joules. */
  private static final double CLOSED_ENERGY_TOLERANCE_J = 1.0e-5;

  private static final UUID TRANSIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000002792");

  @Test
  void closedTemperatureHistoryIgnoresDisconnectedInletNominalFlow() {
    PipeFixture reference = createInitializedPipe("closed-thermal-reference");
    PipeFixture changed = createInitializedPipe("closed-thermal-changed");
    assertArrayEquals(reference.pipe.getTemperatureProfile(), changed.pipe.getTemperatureProfile(),
        CLOSED_HISTORY_TOLERANCE_K);

    configureClosedCooldown(reference.pipe);
    configureClosedCooldown(changed.pipe);
    changed.inlet.setFlowRate(60.0, "kg/sec");

    for (int step = 0; step < 4; step++) {
      reference.pipe.runTransient(1.0e-3, TRANSIENT_ID);
      changed.pipe.runTransient(1.0e-3, TRANSIENT_ID);
    }

    assertArrayEquals(reference.pipe.getTemperatureProfile(), changed.pipe.getTemperatureProfile(),
        CLOSED_HISTORY_TOLERANCE_K, "A disconnected inlet stream must not drive closed-domain thermal advection");
    assertArrayEquals(reference.pipe.getWallTemperatureProfile(), changed.pipe.getWallTemperatureProfile(),
        CLOSED_HISTORY_TOLERANCE_K);
  }

  @Test
  void closedCooldownIncludesFirstCellAndDoesNotUndershootAmbient() {
    PipeFixture fixture = createInitializedPipe("closed-cooldown-all-cells");
    double[] initial = fixture.pipe.getTemperatureProfile();
    configureClosedCooldown(fixture.pipe);
    fixture.pipe.setHeatTransferCoefficient(500.0);
    fixture.pipe.setWallProperties(1.0e-4, 100.0, 100.0);

    double[] previous = initial.clone();
    for (int step = 0; step < 20; step++) {
      fixture.pipe.runTransient(1.0e-3, TRANSIENT_ID);
      double[] current = fixture.pipe.getTemperatureProfile();
      for (int cell = 0; cell < current.length; cell++) {
        assertTrue(current[cell] <= previous[cell] + 1.0e-10,
            "Cell " + cell + " warmed during a uniform closed cooldown");
        assertTrue(current[cell] >= 280.0 - 1.0e-10, "Cell " + cell + " undershot the ambient temperature");
      }
      previous = current;
    }

    for (int cell = 0; cell < previous.length; cell++) {
      assertTrue(previous[cell] < initial[cell],
          "Every physical cell, including cell zero, must participate in cooldown");
    }
  }

  @Test
  void uniformClosedAdiabaticStateKeepsItsTemperature() {
    PipeFixture fixture = createInitializedPipe("closed-adiabatic");
    double[] initial = fixture.pipe.getTemperatureProfile();
    fixture.pipe.closeInlet();
    fixture.pipe.closeOutlet();
    fixture.pipe.setEnableJouleThomson(false);
    fixture.pipe.setSurfaceTemperature(300.0, "K");
    fixture.pipe.setHeatTransferCoefficient(50.0);
    fixture.inlet.setFlowRate(60.0, "kg/sec");

    fixture.pipe.runTransient(1.0e-3, TRANSIENT_ID);

    assertArrayEquals(initial, fixture.pipe.getTemperatureProfile(), 1.0e-12,
        "A uniform closed adiabatic state must retain its temperature within numerical precision");
  }

  @Test
  void explicitAdvectionUsesThePreUpdateTemperatureSnapshot() {
    double[][] faceMassFlows = new double[4][3];
    faceMassFlows[1][0] = 1.0;
    faceMassFlows[2][0] = 1.0;
    double[] previousTemperatures = { 300.0, 320.0, 340.0 };

    double source = TwoFluidPipe.calculateExplicitSensibleAdvectionSource(1, faceMassFlows, previousTemperatures, 280.0,
        2000.0, 10.0);

    assertEquals(-4000.0, source, 1.0e-12,
        "Cell one must use the previous cell-zero temperature, not a value written earlier in the update loop");
  }

  @Test
  void jouleThomsonSourceUsesTheUpstreamPressureForEitherFlowDirection() {
    double[][] forwardFaceMassFlows = new double[4][3];
    forwardFaceMassFlows[1][0] = 2.0;
    double[][] reverseFaceMassFlows = new double[4][3];
    reverseFaceMassFlows[2][0] = -2.0;
    double Cp = 2000.0;
    double muJT = 4.0e-6;

    double forwardSource = TwoFluidPipe.calculateLocalJouleThomsonSource(1, forwardFaceMassFlows, 100.0, 95.0, 90.0, Cp,
        muJT, 10.0);
    double reverseSource = TwoFluidPipe.calculateLocalJouleThomsonSource(1, reverseFaceMassFlows, 90.0, 95.0, 100.0, Cp,
        muJT, 10.0);

    assertEquals(-0.008, forwardSource, 1.0e-15);
    assertEquals(forwardSource, reverseSource, 1.0e-15,
        "Equivalent forward and reverse pressure drops must produce the same Joule-Thomson cooling source");
  }

  @Test
  void invalidThermalMassFallbackUsesFinitePositiveFloor() {
    assertArrayEquals(new double[] { 2.0, 3.0, 1.0e-12 },
        new double[] { TwoFluidPipe.selectFinitePositiveFluidMassPerLength(2.0, Double.NaN),
            TwoFluidPipe.selectFinitePositiveFluidMassPerLength(Double.NaN, 3.0),
            TwoFluidPipe.selectFinitePositiveFluidMassPerLength(Double.NaN, Double.NaN) },
        0.0);
  }

  @Test
  void multilayerCellAdvanceRestoresIndependentRadialState() {
    MultilayerThermalCalculator calculator = new MultilayerThermalCalculator(0.10);
    calculator.createSubseaPipeConfig(0.20, 0.01, 0.02, 0.0, RadialThermalLayer.MaterialType.PU_FOAM);
    double[] firstCellState = new double[calculator.getNumberOfLayers()];
    for (int layer = 0; layer < firstCellState.length; layer++) {
      firstCellState[layer] = calculator.getLayers().get(layer).getTemperature();
    }
    double[] secondCellState = firstCellState.clone();

    double firstWallTemperature = TwoFluidPipe.advanceMultilayerCellThermalState(calculator, firstCellState, 300.0,
        280.0, 50.0, 1.0e-3);
    double secondWallTemperature = TwoFluidPipe.advanceMultilayerCellThermalState(calculator, secondCellState, 300.0,
        280.0, 50.0, 1.0e-3);

    assertArrayEquals(firstCellState, secondCellState, 0.0,
        "Sequential cells with identical stored states must not inherit the preceding cell's radial advance");
    assertEquals(firstWallTemperature, secondWallTemperature, 0.0,
        "Restoring identical radial states must produce the same inner-wall temperature");
  }

  @Test
  void injectedMultilayerCalculatorActivatesItsOverallHeatTransferCoefficient() {
    PipeFixture fixture = createInitializedPipe("injected-multilayer-calculator");
    fixture.pipe.setHeatTransferCoefficient(0.0);
    MultilayerThermalCalculator calculator = new MultilayerThermalCalculator(0.10);
    calculator.createSubseaPipeConfig(0.20, 0.01, 0.02, 0.0, RadialThermalLayer.MaterialType.PU_FOAM);

    fixture.pipe.setThermalCalculator(calculator);

    assertTrue(fixture.pipe.isHeatTransferEnabled());
    assertEquals(calculator.calculateOverallUValue(), fixture.pipe.getHeatTransferCoefficient(), 1.0e-12);
  }

  @Test
  void multilayerConfigurationPathsActivateTheirOverallHeatTransferCoefficient() {
    TwoFluidPipe pipe = new TwoFluidPipe("multilayer-configuration-paths");
    pipe.setHeatTransferCoefficient(0.0);

    pipe.setUseMultilayerThermalModel(true);
    assertTrue(pipe.isHeatTransferEnabled());
    assertEquals(pipe.getThermalCalculator().calculateOverallUValue(), pipe.getHeatTransferCoefficient(), 1.0e-12);

    pipe.setHeatTransferCoefficient(0.0);
    pipe.configureSubseaThermalModel(0.02, 0.0, RadialThermalLayer.MaterialType.PU_FOAM);
    assertTrue(pipe.isHeatTransferEnabled());
    assertEquals(pipe.getThermalCalculator().calculateOverallUValue(), pipe.getHeatTransferCoefficient(), 1.0e-12);

    pipe.setHeatTransferCoefficient(0.0);
    pipe.configureBuriedThermalModel(1.0, false);
    assertTrue(pipe.isHeatTransferEnabled());
    assertEquals(pipe.getThermalCalculator().calculateOverallUValue(), pipe.getHeatTransferCoefficient(), 1.0e-12);
  }

  @Test
  void closedMultilayerCooldownIncludesEveryCell() {
    PipeFixture fixture = createInitializedPipe("closed-multilayer-cooldown");
    double[] initial = fixture.pipe.getTemperatureProfile();
    fixture.pipe.closeInlet();
    fixture.pipe.closeOutlet();
    fixture.pipe.setEnableJouleThomson(false);
    fixture.pipe.setSurfaceTemperature(280.0, "K");
    fixture.pipe.configureSubseaThermalModel(0.02, 0.0, RadialThermalLayer.MaterialType.PU_FOAM);

    double[] previous = initial.clone();
    for (int step = 0; step < 20; step++) {
      fixture.pipe.runTransient(1.0e-3, TRANSIENT_ID);
      double[] current = fixture.pipe.getTemperatureProfile();
      for (int cell = 0; cell < current.length; cell++) {
        assertTrue(Double.isFinite(current[cell]));
        assertTrue(current[cell] <= previous[cell] + 1.0e-10,
            "Multilayer cell " + cell + " warmed during a uniform closed cooldown");
        assertTrue(current[cell] >= 280.0 - 1.0e-10, "Multilayer cell " + cell + " undershot the ambient temperature");
      }
      previous = current;
    }

    for (int cell = 0; cell < previous.length; cell++) {
      assertTrue(previous[cell] < initial[cell],
          "Every multilayer cell, including cell zero, must participate in cooldown");
    }
  }

  @Test
  void closedMultilayerInnerHtcIsIndependentOfOverallCoefficientCallOrder() {
    PipeFixture configureThenOverride = createInitializedPipe("multilayer-configure-then-override");
    PipeFixture overrideThenConfigure = createInitializedPipe("multilayer-override-then-configure");

    prepareClosedMultilayerCooldown(configureThenOverride.pipe);
    configureThenOverride.pipe.setHeatTransferCoefficient(50.0);

    prepareClosedCooldownBoundary(overrideThenConfigure.pipe);
    overrideThenConfigure.pipe.setHeatTransferCoefficient(50.0);
    overrideThenConfigure.pipe.configureSubseaThermalModel(0.02, 0.0, RadialThermalLayer.MaterialType.PU_FOAM);

    assertTrue(
        Math.abs(configureThenOverride.pipe.getHeatTransferCoefficient()
            - overrideThenConfigure.pipe.getHeatTransferCoefficient()) > 1.0e-6,
        "The fixture must exercise different configuration-level overall coefficients");

    double configureThenOverrideHtc = configureThenOverride.pipe.calculateInnerHTC(0.0, 1.0);
    double overrideThenConfigureHtc = overrideThenConfigure.pipe.calculateInnerHTC(0.0, 1.0);

    assertEquals(configureThenOverrideHtc, overrideThenConfigureHtc, 0.0,
        "Closed multilayer cooldown must not reinterpret the overall coefficient as the stagnant inner HTC");
    assertEquals(50.0, configureThenOverrideHtc, 0.0);

    double[] configureThenOverrideState = initialLayerTemperatures(configureThenOverride.pipe.getThermalCalculator());
    double[] overrideThenConfigureState = initialLayerTemperatures(overrideThenConfigure.pipe.getThermalCalculator());
    double configureThenOverrideWall = TwoFluidPipe.advanceMultilayerCellThermalState(
        configureThenOverride.pipe.getThermalCalculator(), configureThenOverrideState, 300.0, 280.0,
        configureThenOverrideHtc, 1.0e-3);
    double overrideThenConfigureWall = TwoFluidPipe.advanceMultilayerCellThermalState(
        overrideThenConfigure.pipe.getThermalCalculator(), overrideThenConfigureState, 300.0, 280.0,
        overrideThenConfigureHtc, 1.0e-3);

    assertArrayEquals(configureThenOverrideState, overrideThenConfigureState, 0.0);
    assertEquals(configureThenOverrideWall, overrideThenConfigureWall, 0.0);
    assertEquals(configureThenOverride.pipe.getThermalCalculator().getLastFluidHeatTransferPerLength(),
        overrideThenConfigure.pipe.getThermalCalculator().getLastFluidHeatTransferPerLength(), 0.0);
  }

  @Test
  void stagnantInnerHtcHasDocumentedDefaultAndIndependentSetter() {
    TwoFluidPipe pipe = new TwoFluidPipe("stagnant-inner-htc-api");
    assertEquals(50.0, pipe.getStagnantInnerHeatTransferCoefficient(), 0.0);
    assertEquals(50.0, pipe.calculateInnerHTC(0.0, 1.0), 0.0);

    pipe.setHeatTransferCoefficient(8.0);
    assertEquals(50.0, pipe.calculateInnerHTC(0.0, 1.0), 0.0);

    pipe.setStagnantInnerHeatTransferCoefficient(75.0);
    assertEquals(75.0, pipe.getStagnantInnerHeatTransferCoefficient(), 0.0);
    assertEquals(75.0, pipe.calculateInnerHTC(0.0, 1.0), 0.0);

    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> pipe.setStagnantInnerHeatTransferCoefficient(-1.0));
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> pipe.setStagnantInnerHeatTransferCoefficient(Double.NaN));
  }

  @Test
  void simpleAndMultilayerCooldownCloseFluidWallAmbientEnergyBalance() {
    PipeFixture simple = createInitializedPipe("simple-energy-balance");
    configureClosedCooldown(simple.pipe);
    simple.pipe.runTransient(1.0e-3, TRANSIENT_ID);
    assertThermalReportCloses(simple.pipe.getLastThermalEnergyBalanceReport());

    PipeFixture multilayer = createInitializedPipe("multilayer-energy-balance");
    multilayer.pipe.closeInlet();
    multilayer.pipe.closeOutlet();
    multilayer.pipe.setEnableJouleThomson(false);
    multilayer.pipe.setSurfaceTemperature(280.0, "K");
    multilayer.pipe.configureSubseaThermalModel(0.02, 0.0, RadialThermalLayer.MaterialType.PU_FOAM);
    multilayer.pipe.runTransient(1.0e-3, TRANSIENT_ID);
    assertThermalReportCloses(multilayer.pipe.getLastThermalEnergyBalanceReport());
  }

  @Test
  void explicitAndImexPathsCloseForSimpleAndMultilayerModels() {
    TimeIntegrator.Method[] methods = { TimeIntegrator.Method.EULER, TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION };
    for (TimeIntegrator.Method method : methods) {
      for (boolean multilayer : new boolean[] { false, true }) {
        PipeFixture fixture = createInitializedPipe("thermal-path-" + method + "-" + multilayer);
        double[] initial = fixture.pipe.getTemperatureProfile();
        fixture.pipe.setTimeIntegrationMethod(method);
        configureClosedCooldown(fixture.pipe);
        if (multilayer) {
          fixture.pipe.configureSubseaThermalModel(0.02, 0.0, RadialThermalLayer.MaterialType.PU_FOAM);
        }

        fixture.pipe.runTransient(1.0e-3, TRANSIENT_ID);

        double[] cooled = fixture.pipe.getTemperatureProfile();
        boolean anyCellCooled = false;
        for (int cell = 0; cell < cooled.length; cell++) {
          assertTrue(Double.isFinite(cooled[cell]));
          assertTrue(cooled[cell] <= initial[cell] + 1.0e-10);
          anyCellCooled |= cooled[cell] < initial[cell] - 1.0e-12;
        }
        assertTrue(anyCellCooled,
            method + " must advance the " + (multilayer ? "multilayer" : "simple") + " cooldown path");
        assertThermalReportCloses(fixture.pipe.getLastThermalEnergyBalanceReport());
      }
    }
  }

  @Test
  void closedCooldownIsStableUnderTimeStepAndMeshRefinement() {
    double coarseCooldown = runRefinementCooldown(4, 1.0e-3, 10);
    double refinedCooldown = runRefinementCooldown(8, 5.0e-4, 20);

    assertTrue(coarseCooldown > 0.0);
    assertTrue(refinedCooldown > 0.0);
    double relativeDifference = Math.abs(coarseCooldown - refinedCooldown) / refinedCooldown;
    assertTrue(relativeDifference < 0.05,
        "Halving both cell length and time step changed the uniform cooldown by " + relativeDifference);
  }

  @Test
  void serializedCopyPreservesIndependentMultilayerCooldownState() {
    PipeFixture fixture = createInitializedPipe("serialized-multilayer-source");
    configureClosedCooldown(fixture.pipe);
    fixture.pipe.configureSubseaThermalModel(0.02, 0.0, RadialThermalLayer.MaterialType.PU_FOAM);
    fixture.pipe.setStagnantInnerHeatTransferCoefficient(75.0);
    fixture.pipe.runTransient(1.0e-3, TRANSIENT_ID);

    TwoFluidPipe copied = (TwoFluidPipe) fixture.pipe.copy();
    fixture.pipe.runTransient(1.0e-3, TRANSIENT_ID);
    copied.runTransient(1.0e-3, TRANSIENT_ID);

    assertArrayEquals(fixture.pipe.getTemperatureProfile(), copied.getTemperatureProfile(), 0.0);
    assertArrayEquals(fixture.pipe.getWallTemperatureProfile(), copied.getWallTemperatureProfile(), 0.0);
    assertEquals(75.0, copied.getStagnantInnerHeatTransferCoefficient(), 0.0);
    assertThermalReportCloses(fixture.pipe.getLastThermalEnergyBalanceReport());
    assertThermalReportCloses(copied.getLastThermalEnergyBalanceReport());
  }

  @Test
  void disabledHeatTransferLeavesTemperatureUnchangedAndDoesNotCreateReport() {
    PipeFixture fixture = createInitializedPipe("disabled-heat-transfer");
    double[] initial = fixture.pipe.getTemperatureProfile();
    fixture.pipe.closeInlet();
    fixture.pipe.closeOutlet();
    fixture.pipe.setEnableJouleThomson(false);
    fixture.pipe.setSurfaceTemperature(280.0, "K");
    fixture.pipe.setHeatTransferCoefficient(0.0);

    fixture.pipe.runTransient(1.0e-3, TRANSIENT_ID);

    assertArrayEquals(initial, fixture.pipe.getTemperatureProfile(), 1.0e-12);
    assertNull(fixture.pipe.getLastThermalEnergyBalanceReport());
  }

  private double runRefinementCooldown(int sections, double timeStep, int steps) {
    PipeFixture fixture = createInitializedPipe("closed-refinement-" + sections + "-" + timeStep, sections);
    double initialMean = mean(fixture.pipe.getTemperatureProfile());
    configureClosedCooldown(fixture.pipe);
    fixture.pipe.setHeatTransferCoefficient(500.0);
    fixture.pipe.setWallProperties(1.0e-4, 100.0, 100.0);

    for (int step = 0; step < steps; step++) {
      fixture.pipe.runTransient(timeStep, TRANSIENT_ID);
      assertThermalReportCloses(fixture.pipe.getLastThermalEnergyBalanceReport());
    }
    return initialMean - mean(fixture.pipe.getTemperatureProfile());
  }

  private double mean(double[] values) {
    double sum = 0.0;
    for (double value : values) {
      sum += value;
    }
    return sum / values.length;
  }

  private double[] initialLayerTemperatures(MultilayerThermalCalculator calculator) {
    double[] temperatures = new double[calculator.getNumberOfLayers()];
    for (int layer = 0; layer < temperatures.length; layer++) {
      temperatures[layer] = calculator.getLayers().get(layer).getTemperature();
    }
    return temperatures;
  }

  private void assertThermalReportCloses(TwoFluidThermalEnergyBalanceReport report) {
    assertNotNull(report);
    assertTrue(report.getAcceptedSubsteps() > 0);
    assertEquals(0.0, report.getJouleThomsonEnergyJ(), 1.0e-12);
    assertTrue(report.getAmbientHeatLossJ() > 0.0);
    assertTrue(report.getStoredEnergyChangeJ() < 0.0);
    assertTrue(report.isWithinTolerance(CLOSED_ENERGY_TOLERANCE_J, 1.0e-10),
        "Thermal residual was " + report.getResidualJ() + " J (relative " + report.getRelativeResidual() + ")");
  }

  private void configureClosedCooldown(TwoFluidPipe pipe) {
    prepareClosedCooldownBoundary(pipe);
    pipe.setHeatTransferCoefficient(50.0);
  }

  private void prepareClosedMultilayerCooldown(TwoFluidPipe pipe) {
    prepareClosedCooldownBoundary(pipe);
    pipe.configureSubseaThermalModel(0.02, 0.0, RadialThermalLayer.MaterialType.PU_FOAM);
  }

  private void prepareClosedCooldownBoundary(TwoFluidPipe pipe) {
    pipe.closeInlet();
    pipe.closeOutlet();
    pipe.setEnableJouleThomson(false);
    pipe.setSurfaceTemperature(280.0, "K");
  }

  private PipeFixture createInitializedPipe(String name) {
    return createInitializedPipe(name, 4);
  }

  private PipeFixture createInitializedPipe(String name, int sections) {
    SystemInterface fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream(name + "-inlet", fluid);
    inlet.setFlowRate(6.0, "kg/sec");
    inlet.setTemperature(300.0, "K");
    inlet.setPressure(60.0, "bara");
    inlet.run();

    TwoFluidPipe pipe = new TwoFluidPipe(name + "-pipe", inlet);
    pipe.setLength(40.0);
    pipe.setDiameter(0.20);
    pipe.setRoughness(1.0e-5);
    pipe.setNumberOfSections(sections);
    pipe.setEnableAdaptiveTimestepping(false);
    pipe.setEnableSlugTracking(false);
    // These tests isolate wall and transient thermal behaviour, so the fixture must start from a
    // uniform profile. Joule-Thomson would otherwise impose a small gradient during initialization.
    pipe.setEnableJouleThomson(false);
    pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
    // Disable the wall-clock cutoff so fixture state depends only on convergence or the fixed iteration cap.
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    return new PipeFixture(inlet, pipe);
  }

  private static final class PipeFixture {
    private final Stream inlet;
    private final TwoFluidPipe pipe;

    private PipeFixture(Stream inlet, TwoFluidPipe pipe) {
      this.inlet = inlet;
      this.pipe = pipe;
    }
  }
}
