package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.simulation.DepletionSnapshot;
import neqsim.pvtsimulation.simulation.DifferentialLiberation;
import neqsim.pvtsimulation.util.DepletionPvtHistoryGenerator.NativeResult;
import neqsim.pvtsimulation.util.MaterialBalancePvtHistory.Path;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Native phase appearance, conservation, independent SRK root and failure qualification.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class NativeDepletionHandoffTest {
  /**
   * Creates a dry binary fixture without volume translation.
   *
   * @param methane methane mole fraction
   * @return initialized original fluid
   */
  private SystemInterface fluid(double methane) {
    SystemInterface f = new SystemSrkEos(323.15, 500);
    f.addComponent("methane", methane);
    f.addComponent("n-decane", 1 - methane);
    f.setMixingRule("classic");
    f.useVolumeCorrection(false);
    f.init(0);
    return f;
  }

  /**
   * Freezes synthetic model versions without fitting.
   *
   * @param methane original methane fraction
   * @return checkpoint bundle
   */
  private CalibratedPvtModel model(double methane) {
    return new CalibratedPvtModel(fluid(methane), fluid(methane), "synthetic native sample", "original SRK v1",
        "accepted SRK v1", "synthetic laboratory protocol, not measurements", new String[] {"CCE-1"});
  }

  /**
   * Defines a fixed, explicitly non-default product-volume basis.
   *
   * @return immutable separator process
   */
  private PvtSeparatorPath separator() {
    return new PvtSeparatorPath(new double[] {10, 1.0133}, new double[] {303.15, 288.71},
        "synthetic two-stage train, final 15.56 C / 101.33 kPa absolute");
  }

  /**
   * Closes every native component through cell, withdrawal and all separator products.
   *
   * @param result actual completed protocol
   * @param initial original component moles in named order
   */
  private void assertClosure(NativeResult result, double[] initial) {
    double[] cumulative = new double[initial.length];
    DepletionSnapshot[] snapshots = result.getSnapshots();
    for (int row = 0; row < snapshots.length; row++) {
      double[] amounts = snapshots[row].getProducedComponentMoles();
      double[][] products = result.getRetainedProducts(row).getComponentMoles();
      double[][] output = result.hasWithdrawal(row) ? result.getWithdrawalProducts(row).getComponentMoles() : null;
      SystemInterface retained = snapshots[row].getRetainedFluid();
      for (int i = 0; i < initial.length; i++) {
        cumulative[i] += amounts[i];
        assertEquals(initial[i], cumulative[i] + retained.getComponent(i).getNumberOfmoles(),
            1e-10 * Math.max(1, initial[i]));
        double cell = 0;
        double withdrawal = 0;
        for (int phase = 0; phase < 3; phase++) {
          cell += products[phase][i];
          withdrawal += output == null ? 0 : output[phase][i];
        }
        assertEquals(retained.getComponent(i).getNumberOfmoles(), cell, 1e-10);
        assertEquals(amounts[i], withdrawal, 1e-10);
      }
    }
  }

  /** DLE retains oil-only states before gas appearance and preserves immutable protocol provenance. */
  @Test
  void dleCrossesFromOilOnlyToTwoPhases() {
    double[] pressures = {500, 300, 200, 100, 50};
    CalibratedPvtModel model = model(0.7);
    NativeResult result = DepletionPvtHistoryGenerator.replayNative(model, true, Path.DLE, "whole synthetic DLE-1",
        323.15, pressures, separator());
    assertTrue(result.isTuned());
    assertEquals(Path.DLE, result.getPath());
    assertFalse(result.getSnapshots()[0].getEquilibriumFluid().hasPhaseType("gas"));
    assertFalse(result.hasWithdrawal(0));
    assertThrows(IllegalStateException.class, () -> result.getWithdrawalProducts(0));
    assertTrue(result.getSnapshots()[2].getEquilibriumFluid().hasPhaseType("gas"));
    assertTrue(result.hasWithdrawal(2));
    for (int i = 0; i < pressures.length; i++) {
      assertEquals(pressures[i], result.getSnapshots()[i].getEquilibriumFluid().getPressure(), 1e-12);
      assertFalse(result.getSnapshots()[i].getRetainedFluid().hasPhaseType("gas"));
    }
    assertClosure(result, new double[] {0.7, 0.3});
    pressures[0] = 1;
    result.getSnapshots()[0] = null;
    NativeResult restored = SerializationUtils.clone(result);
    assertEquals(500, restored.getSnapshots()[0].getEquilibriumFluid().getPressure(), 0);
    assertEquals(model.getProvenance(), restored.getModel().getProvenance());
    assertEquals(1.0133, restored.getRetainedProducts(0).getFinalPressureBara(), 0);
    assertEquals(288.71, restored.getRetainedProducts(0).getFinalTemperatureK(), 0);
    double[] volumes = restored.getRetainedProducts(0).getVolumesM3();
    double saved = volumes[0];
    volumes[0] = -1;
    assertEquals(saved, restored.getRetainedProducts(0).getVolumesM3()[0], 0);
  }

  /** CVD begins with gas-only expansion and follows actual restored two-phase cell volumes. */
  @Test
  void cvdCrossesDewAndReplaysBothVersions() {
    CalibratedPvtModel model = model(0.95);
    double[] p = {500, 300, 200, 100};
    NativeResult original = DepletionPvtHistoryGenerator.replayNative(model, false, Path.CVD,
        "complete synthetic CVD-1", 373.15, p, separator());
    NativeResult tuned = DepletionPvtHistoryGenerator.replayNative(model, true, Path.CVD, "complete synthetic CVD-1",
        373.15, p, separator());
    assertFalse(original.isTuned());
    assertFalse(original.getSnapshots()[0].getEquilibriumFluid().hasPhaseType("oil"));
    assertFalse(original.hasWithdrawal(0));
    assertTrue(original.getSnapshots()[1].getEquilibriumFluid().hasPhaseType("oil"));
    assertClosure(original, new double[] {0.95, 0.05});
    assertClosure(tuned, new double[] {0.95, 0.05});
    double volume = original.getSnapshots()[1].getRetainedFluid().getCorrectedVolume();
    for (int row = 1; row < p.length; row++) {
      assertEquals(volume, original.getSnapshots()[row].getRetainedFluid().getCorrectedVolume(), volume * 1e-8);
      assertArrayEquals(original.getSnapshots()[row].getRetainedFluid().getMolarComposition(),
          tuned.getSnapshots()[row].getRetainedFluid().getMolarComposition(), 1e-12);
    }
  }

  /** Original and tuned EOS interactions remain distinct in the actual native replay. */
  @Test
  void selectedModelRetainsItsIndependentEosParameters() {
    SystemInterface a = fluid(0.7);
    SystemInterface b = fluid(0.7);
    for (int phase = 0; phase < a.getMaxNumberOfPhases(); phase++) {
      ((PhaseEosInterface) a.getPhase(phase)).getEosMixingRule().setBinaryInteractionParameter(0, 1, 0);
      ((PhaseEosInterface) b.getPhase(phase)).getEosMixingRule().setBinaryInteractionParameter(0, 1, 0.05);
    }
    CalibratedPvtModel model = new CalibratedPvtModel(a, b, "synthetic BIP versions", "original kij0", "tuned kij0.05",
        "independently specified synthetic laboratory fixture", new String[] {"CCE-1"});
    NativeResult original = DepletionPvtHistoryGenerator.replayNative(model, false, Path.DLE, "complete DLE", 323.15,
        new double[] {500, 300, 200}, separator());
    NativeResult tuned = DepletionPvtHistoryGenerator.replayNative(model, true, Path.DLE, "complete DLE", 323.15,
        new double[] {500, 300, 200}, separator());
    SystemInterface stateA = original.getSnapshots()[0].getEquilibriumFluid();
    SystemInterface stateB = tuned.getSnapshots()[0].getEquilibriumFluid();
    assertEquals(0, ((PhaseEosInterface) stateA.getPhase(0)).getEosMixingRule().getBinaryInteractionParameter(0, 1), 0);
    assertEquals(0.05, ((PhaseEosInterface) stateB.getPhase(0)).getEosMixingRule().getBinaryInteractionParameter(0, 1),
        0);
    assertNotEquals(stateA.getZ(), stateB.getZ(), 1e-8);
    assertClosure(original, new double[] {0.7, 0.3});
    assertClosure(tuned, new double[] {0.7, 0.3});
  }

  /** All-oil DLE is closed expansion with no component withdrawals. */
  @Test
  void closedOilLimit() {
    NativeResult result = DepletionPvtHistoryGenerator.replayNative(model(0.3), true, Path.DLE,
        "closed liquid expansion", 323.15, new double[] {500, 300, 200}, separator());
    for (int row = 0; row < 3; row++) {
      assertFalse(result.hasWithdrawal(row));
      SystemInterface expected = fluid(0.3);
      expected.setPressure(new double[] {500, 300, 200}[row]);
      new ThermodynamicOperations(expected).TPflash();
      expected.initProperties();
      SystemInterface actual = result.getSnapshots()[row].getRetainedFluid();
      actual.initProperties();
      assertEquals(expected.getDensity("kg/m3"), actual.getDensity("kg/m3"), expected.getDensity("kg/m3") * 1e-8);
    }
    assertClosure(result, new double[] {0.3, 0.7});
  }

  /** Pure-liquid molar volumes agree with an independent bisection of the declared SRK pressure equation. */
  @Test
  void independentSrkLiquidRoot() {
    CalibratedPvtModel model = model(0);
    NativeResult result = DepletionPvtHistoryGenerator.replayNative(model, false, Path.DLE,
        "pure n-decane closed expansion", 323.15, new double[] {500, 300, 200}, separator());
    SystemInterface original = model.getOriginalFluid();
    double tc = original.getComponent("n-decane").getTC();
    double pc = original.getComponent("n-decane").getPC() * 1e5;
    double omega = original.getComponent("n-decane").getAcentricFactor();
    double r = 8.3144621; // Declared NeqSim EOS constant, not fitted.
    double c = Math.cbrt(2) - 1;
    double b = c / 3 * r * tc / pc;
    double m = 0.48 + 1.574 * omega - 0.176 * omega * omega;
    double a = r * r * tc * tc / (9 * c * pc) * Math.pow(1 + m * (1 - Math.sqrt(323.15 / tc)), 2);
    for (DepletionSnapshot snapshot : result.getSnapshots()) {
      SystemInterface state = snapshot.getRetainedFluid();
      double pressure = state.getPressure() * 1e5;
      double low = b * (1 + 1e-10);
      double high = 100 * b;
      for (int iteration = 0; iteration < 120; iteration++) {
        double v = (low + high) / 2;
        double p = r * 323.15 / (v - b) - a / (v * (v + b));
        if (p > pressure) {
          low = v;
        } else {
          high = v;
        }
      }
      double expected = (low + high) / 2;
      assertEquals(expected, state.getCorrectedVolume() / state.getTotalNumberOfMoles(), expected * 1e-8);
    }
  }

  /** Wet samples, gas-only DLE, unsampled CVD boundaries and invalid schedules remain unsupported. */
  @Test
  void rejectsUnsupportedDomains() {
    assertThrows(IllegalArgumentException.class, () -> DepletionPvtHistoryGenerator.replayNative(model(0.95), true,
        Path.DLE, "gas feed", 373.15, new double[] {500, 300}, separator()));
    assertThrows(IllegalStateException.class, () -> DepletionPvtHistoryGenerator.replayNative(model(0.95), true,
        Path.CVD, "no sampled boundary", 423.15, new double[] {700, 500}, separator()));
    assertThrows(IllegalArgumentException.class, () -> DepletionPvtHistoryGenerator.replayNative(model(0.3), true,
        Path.DLE, "bad schedule", 323.15, new double[] {200, 300}, separator()));
    SystemInterface wet = fluid(0.3);
    wet.addComponent("water", 0.1);
    wet.setMixingRule("classic");
    wet.init(0);
    CalibratedPvtModel bundle = new CalibratedPvtModel(wet, wet, "wet", "a", "b", "protocol", new String[] {"CCE-1"});
    assertThrows(IllegalArgumentException.class, () -> DepletionPvtHistoryGenerator.replayNative(bundle, true, Path.DLE,
        "wet DLE", 323.15, new double[] {500, 300}, separator()));
  }

  /** Initial and final flash failures propagate the original cause rather than reading stale phases. */
  @Test
  void dleFlashFailuresRemainObservable() {
    for (int failAt : new int[] {1, 3}) {
      DifferentialLiberation simulation = new DifferentialLiberation(fluid(0.3));
      simulation.setTemperature(323.15, "K");
      simulation.setPressures(new double[] {500, 300});
      simulation.setRecordDepletionSnapshots(true);
      simulation.thermoOps = new ThermodynamicOperations(simulation.getThermoSystem()) {
        private static final long serialVersionUID = 1L;
        private int calls;

        /** Injects one failed flash while retaining the solver for other calls. */
        @Override
        public void TPflash() {
          if (++calls == failAt) {
            throw new IllegalStateException("injected native flash failure");
          }
          super.TPflash();
        }
      };
      IllegalStateException failure = assertThrows(IllegalStateException.class, simulation::runCalc);
      assertTrue(failure.getMessage().contains("DLE"));
      assertEquals("injected native flash failure", failure.getCause().getMessage());
    }
  }

  /** A failed post-saturation reflash cannot be swallowed after an otherwise successful saturation scan. */
  @Test
  void dlePostSaturationFailurePreservesCause() {
    DifferentialLiberation simulation = new DifferentialLiberation(fluid(0.7)) {
      /** Completes the real scan, then injects failure into the following reflash. */
      @Override
      public void calcSaturationConditions() {
        super.calcSaturationConditions();
        thermoOps = new ThermodynamicOperations(getThermoSystem()) {
          private static final long serialVersionUID = 1L;

          /** Fails the post-saturation equilibrium call. */
          @Override
          public void TPflash() {
            throw new IllegalStateException("injected post-saturation failure");
          }
        };
      }
    };
    simulation.setTemperature(323.15, "K");
    simulation.setPressures(new double[] {200, 100});
    IllegalStateException failure = assertThrows(IllegalStateException.class, simulation::runCalc);
    assertTrue(failure.getMessage().contains("DLE post-saturation"));
    assertEquals("injected post-saturation failure", failure.getCause().getMessage());
  }

  /** A real gas/oil-to-gas CVD tail stays rejected until single-phase volume restoration is qualified. */
  @Test
  void rejectsUnqualifiedPhaseDisappearanceTail() {
    SystemInterface fluid = new SystemSrkEos(275, 500);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("n-butane", 0.05);
    fluid.setMixingRule("classic");
    fluid.useVolumeCorrection(false);
    fluid.init(0);
    CalibratedPvtModel model = new CalibratedPvtModel(fluid, fluid, "synthetic tail", "original", "tuned", "protocol",
        new String[] {"CCE-1"});
    PvtSeparatorPath separator = new PvtSeparatorPath(new double[] {1.0133}, new double[] {288.71},
        "single final basis");
    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> DepletionPvtHistoryGenerator
        .replayNative(model, true, Path.CVD, "CVD tail", 275, new double[] {500, 200, 100, 50, 20, 5, 2}, separator));
    assertTrue(failure.getMessage().contains("phase-disappearance tail"));
  }
}
