package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Closed-domain regressions for TwoFluidPipe transient thermal transport. */
class TwoFluidPipeClosedThermalTest {
  /** Cross-platform tolerance for independently initialised closed-domain temperature histories, in kelvin. */
  private static final double CLOSED_HISTORY_TOLERANCE_K = 1.0e-9;

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

    double forwardSource = TwoFluidPipe.calculateLocalJouleThomsonSource(1, forwardFaceMassFlows,
        new double[] { 100.0, 95.0, 90.0 }, Cp, muJT, 10.0);
    double reverseSource = TwoFluidPipe.calculateLocalJouleThomsonSource(1, reverseFaceMassFlows,
        new double[] { 90.0, 95.0, 100.0 }, Cp, muJT, 10.0);

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

  private void configureClosedCooldown(TwoFluidPipe pipe) {
    pipe.closeInlet();
    pipe.closeOutlet();
    pipe.setEnableJouleThomson(false);
    pipe.setSurfaceTemperature(280.0, "K");
    pipe.setHeatTransferCoefficient(50.0);
  }

  private PipeFixture createInitializedPipe(String name) {
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
    pipe.setNumberOfSections(4);
    pipe.setEnableAdaptiveTimestepping(false);
    pipe.setEnableSlugTracking(false);
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
