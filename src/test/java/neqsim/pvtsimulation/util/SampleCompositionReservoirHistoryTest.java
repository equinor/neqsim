package neqsim.pvtsimulation.util;

import java.lang.reflect.Proxy;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Result;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Step;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Sample-preserving initialization, phase limits and transactional ownership checks.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SampleCompositionReservoirHistoryTest {
  /**
   * Builds a caller-configured representative sample.
   *
   * @param gas methane amount in mol
   * @param oil heptane amount in mol
   * @param water water amount in mol
   * @return PR/classic sample with explicit multiphase settings
   */
  private SystemInterface sample(double gas, double oil, double water) {
    SystemInterface fluid = new SystemPrEos(323.15, 80.0);
    fluid.addComponent("methane", gas);
    fluid.addComponent("n-heptane", oil);
    if (water > 0.0) {
      fluid.addComponent("water", water);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(water > 0.0);
    fluid.init(0);
    return fluid;
  }

  /**
   * Freezes separate original/tuned sample ownership without reservoir tuning.
   *
   * @param sample accepted sample
   * @return immutable model bundle
   */
  private CalibratedPvtModel model(SystemInterface sample) {
    return new CalibratedPvtModel(sample, sample, "synthetic representative sample", "original", "laboratory tuned",
        "synthetic independent calibration reference", new String[] {"lab-1"});
  }

  /**
   * Builds a no-flow interval on a complete named basis.
   *
   * @param size number of named components
   * @return one immutable interval
   */
  private Step[] noFlow(int size) {
    return new Step[] {new Step(1.0, new double[size], new double[size], Double.NaN, Double.NaN, true)};
  }

  /** Preserves named sample composition and proportional molar inventory through every volume trial. */
  @Test
  void preservesTwoPhaseSampleAcrossVolumesAndRestarts() {
    CalibratedPvtModel model = model(sample(0.8, 0.2, 0.0));
    byte[] before = SerializationUtils.serialize(model);
    CompositionalReservoirHistory history = CompositionalReservoirHistory.fromSampleComposition(model,
        "same sample, reversed named basis", new String[] {"n-heptane", "methane"}, 323.15, 80.0, noFlow(2));
    double[] fractions = history.getInitialPhaseVolumeFractions();
    Assertions.assertTrue(fractions[0] > 0.0 && fractions[1] > 0.0);
    Assertions.assertEquals(0.0, fractions[2]);
    Assertions.assertNotEquals(0.8, fractions[0], 0.01);
    double referenceMoles = 0.0;
    for (double volume : new double[] {80.0, 100.0, 120.0}) {
      Result result = history.replay(volume);
      double[][] amounts = result.getComponentMoles();
      double total = amounts[0][0] + amounts[0][1];
      Assertions.assertEquals(0.2, amounts[0][0] / total, 1.0e-10);
      Assertions.assertEquals(0.8, amounts[0][1] / total, 1.0e-10);
      Assertions.assertArrayEquals(amounts[0], amounts[1], total * 1.0e-9);
      Assertions.assertEquals(volume, result.getFinalFluid().getVolume("m3"), volume * 1.0e-9);
      if (referenceMoles == 0.0) {
        referenceMoles = total / volume;
      }
      Assertions.assertEquals(referenceMoles, total / volume, referenceMoles * 1.0e-10);
    }
    fractions[0] = 0.0;
    Assertions.assertTrue(history.getInitialPhaseVolumeFractions()[0] > 0.0);
    double[] pressures = history.replay(100.0).getPressuresBara();
    Assertions.assertThrows(IllegalArgumentException.class, () -> history.replay(-1.0));
    Assertions.assertArrayEquals(pressures, history.replay(100.0).getPressuresBara());
    CompositionalReservoirHistory copy = SerializationUtils.clone(history);
    Assertions.assertArrayEquals(pressures, copy.replay(100.0).getPressuresBara());
    Assertions.assertArrayEquals(before, SerializationUtils.serialize(model));
  }

  /** Rejects a provably exhausting full interval before its first flash and preserves the next trial. */
  @Test
  void rejectsTotalIntervalExhaustionBeforeSubsteps() {
    CalibratedPvtModel model = model(sample(0.8, 0.2, 0.0));
    byte[] before = SerializationUtils.serialize(model);
    Step production = Step.phaseWithdrawal(86400.0, new double[] {8000.0, 2000.0, 0.0}, new double[] {200.0, 10.0},
        Double.NaN, Double.NaN, false, 4);
    CompositionalReservoirHistory history = CompositionalReservoirHistory.fromSampleComposition(model,
        "conservation admission", new String[] {"methane", "n-heptane"}, 323.15, 80.0, new Step[] {production});
    double[] expected = history.replay(100.0).getPressuresBara();
    IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, () -> history.replay(1.0));
    Assertions.assertTrue(failure.getMessage().contains("substep 0"));
    Assertions.assertTrue(failure.getMessage().contains("total component inventory"));
    Assertions.assertArrayEquals(expected, history.replay(100.0).getPressuresBara());
    Assertions.assertArrayEquals(before, SerializationUtils.serialize(model));
  }

  /** Exact interval admission avoids cancellation and overflow without promising substep feasibility. */
  @Test
  void qualifiesIntervalAdmissionArithmetic() {
    for (double[] amounts : new double[][] {{1.0, 1.0e16, 1.0e16}, {1.0e16, 1.0, 1.0e16},
        {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, {1.0, 2.0, 2.0}}) {
      Step interval = Step.phaseWithdrawal(1.0, new double[] {amounts[2], 0.0, 0.0}, new double[] {amounts[1], 0.0},
          Double.NaN, Double.NaN, false, 1);
      CompositionalReservoirHistory.requireFeasiblePhaseInterval(amounts[0], interval);
    }
    Step exhausted = Step.phaseWithdrawal(1.0, new double[] {1.0, 0.0, 0.0}, new double[] {0.0, 0.0}, Double.NaN,
        Double.NaN, false, 1);
    Assertions.assertThrows(IllegalStateException.class,
        () -> CompositionalReservoirHistory.requireFeasiblePhaseInterval(1.0, exhausted));
  }

  /** A positive full-interval balance never makes later injection available for the first withdrawal. */
  @Test
  void retainsWithdrawalBeforeInjectionAvailability() {
    CalibratedPvtModel model = model(sample(0.8, 0.2, 0.0));
    CompositionalReservoirHistory quiet = CompositionalReservoirHistory.fromSampleComposition(model, "inventory",
        new String[] {"methane", "n-heptane"}, 323.15, 80.0, noFlow(2));
    double[] initial = quiet.replay(1.0).getComponentMoles()[0];
    double amount = 2.0 * (initial[0] + initial[1]);
    Step interval = Step.phaseWithdrawal(1.0, new double[] {amount, 0.0, 0.0}, new double[] {amount, 0.0}, Double.NaN,
        Double.NaN, false, 1);
    CompositionalReservoirHistory.requireFeasiblePhaseInterval(initial[0] + initial[1], interval);
    CompositionalReservoirHistory impossible = CompositionalReservoirHistory.fromSampleComposition(model,
        "later injection unavailable", new String[] {"methane", "n-heptane"}, 323.15, 80.0, new Step[] {interval});
    Assertions.assertThrows(IllegalStateException.class, () -> impossible.replay(1.0));
  }

  /** Gas-only and oil-only sample limits require no fabricated absent-phase composition. */
  @Test
  void preservesSinglePhaseLimits() {
    for (double[] setting : new double[][] {{0.999999, 0.000001, 450.0, 20.0, 0.0},
        {0.000001, 0.999999, 300.0, 100.0, 1.0}}) {
      CompositionalReservoirHistory history = CompositionalReservoirHistory.fromSampleComposition(
          model(sample(setting[0], setting[1], 0.0)), "single phase limit", new String[] {"methane", "n-heptane"},
          setting[2], setting[3], noFlow(2));
      double[] fractions = history.getInitialPhaseVolumeFractions();
      Assertions.assertEquals(1.0, fractions[(int) setting[4]]);
      Assertions.assertEquals(0.0, fractions[2]);
      double[] amounts = history.replay(100.0).getComponentMoles()[0];
      Assertions.assertEquals(setting[0], amounts[0] / (amounts[0] + amounts[1]), 1.0e-10);
    }
  }

  /** Caller-accepted wet EOS settings retain every named water and hydrocarbon component. */
  @Test
  void preservesWetSample() {
    CompositionalReservoirHistory history = CompositionalReservoirHistory.fromSampleComposition(
        model(sample(0.7, 0.2, 0.1)), "wet sample", new String[] {"water", "n-heptane", "methane"}, 323.15, 80.0,
        noFlow(3));
    Assertions.assertTrue(history.getInitialPhaseVolumeFractions()[2] > 0.0);
    Result result = history.replay(100.0);
    double[] amounts = result.getComponentMoles()[0];
    double total = amounts[0] + amounts[1] + amounts[2];
    Assertions.assertArrayEquals(new double[] {0.1, 0.2, 0.7},
        new double[] {amounts[0] / total, amounts[1] / total, amounts[2] / total}, 1.0e-10);
    Assertions.assertArrayEquals(amounts, result.getComponentMoles()[1], total * 1.0e-9);
  }

  /** Invalid inputs reject before reservoir fitting; existing complete-basis validation remains active. */
  @Test
  void rejectsInvalidInputs() {
    CalibratedPvtModel model = model(sample(0.8, 0.2, 0.0));
    Assertions.assertThrows(IllegalArgumentException.class, () -> CompositionalReservoirHistory
        .fromSampleComposition(null, "null model", new String[] {"methane"}, 323.15, 80.0, noFlow(1)));
    for (double bad : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      Assertions.assertThrows(IllegalArgumentException.class, () -> CompositionalReservoirHistory
          .fromSampleComposition(model, "bad temperature", new String[] {"methane"}, bad, 80.0, noFlow(1)));
      Assertions.assertThrows(IllegalArgumentException.class, () -> CompositionalReservoirHistory
          .fromSampleComposition(model, "bad pressure", new String[] {"methane"}, 323.15, bad, noFlow(1)));
    }
    Assertions.assertThrows(IllegalArgumentException.class, () -> CompositionalReservoirHistory
        .fromSampleComposition(model, "incomplete basis", new String[] {"methane"}, 323.15, 80.0, noFlow(1)));
  }

  /**
   * Supplies isolated phase metadata to exercise guards without asking the flash to create unphysical states.
   *
   * @param names reported phase types
   * @param volumes reported EOS phase volumes in m3
   * @return metadata-only interface; unexpected calls fail
   */
  private SystemInterface phaseMetadata(String[] names, double[] volumes) {
    return (SystemInterface) Proxy.newProxyInstance(SystemInterface.class.getClassLoader(),
        new Class<?>[] {SystemInterface.class}, (proxy, method, args) -> {
          if ("getNumberOfPhases".equals(method.getName())) {
            return names.length;
          }
          if ("getPhase".equals(method.getName())) {
            int phase = (Integer) args[0];
            return Proxy.newProxyInstance(PhaseInterface.class.getClassLoader(), new Class<?>[] {PhaseInterface.class},
                (phaseProxy, phaseMethod, phaseArgs) -> {
                  if ("getPhaseTypeName".equals(phaseMethod.getName())) {
                    return names[phase];
                  }
                  if ("getVolume".equals(phaseMethod.getName()) && "m3".equals(phaseArgs[0])) {
                    return volumes[phase];
                  }
                  throw new UnsupportedOperationException(phaseMethod.getName());
                });
          }
          throw new UnsupportedOperationException(method.getName());
        });
  }

  /** Guards fail closed for unsupported/duplicate phases, invalid volumes and empty/overflowed totals. */
  @Test
  void rejectsUnsupportedPhaseMetadata() {
    for (String[] names : new String[][] {{"solid"}, {"oil", "oil"}, {"gas", "gas"}, {}}) {
      Assertions.assertThrows(IllegalStateException.class, () -> CompositionalReservoirHistory
          .samplePhaseVolumeFractions(phaseMetadata(names, new double[] {1.0, 1.0})));
    }
    for (double bad : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      Assertions.assertThrows(IllegalStateException.class, () -> CompositionalReservoirHistory
          .samplePhaseVolumeFractions(phaseMetadata(new String[] {"gas"}, new double[] {bad})));
    }
    Assertions.assertThrows(IllegalStateException.class, () -> CompositionalReservoirHistory.samplePhaseVolumeFractions(
        phaseMetadata(new String[] {"gas", "oil"}, new double[] {Double.MAX_VALUE, Double.MAX_VALUE})));
    Assertions.assertThrows(IllegalStateException.class, () -> CompositionalReservoirHistory.samplePhaseVolumeFractions(
        phaseMetadata(new String[] {"gas", "oil"}, new double[] {Double.MAX_VALUE, Double.MIN_VALUE})));
  }
}
