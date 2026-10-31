package neqsim.pvtsimulation.util;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Result;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Step;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent phase-extraction, component accounting and refinement qualification of the existing tank replay.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PhaseWithdrawalReservoirHistoryTest {
  private static final Logger logger = LogManager.getLogger(PhaseWithdrawalReservoirHistoryTest.class);
  private static final String[] BASIS = {"n-heptane", "methane"};

  /**
   * Creates an untuned public synthetic PR mixture with a two-phase reservoir state.
   *
   * @return explicitly configured sample
   */
  private SystemInterface sample() {
    SystemInterface fluid = new SystemPrEos(323.15, 80.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("n-heptane", 0.3);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Freezes a synthetic laboratory acceptance without fitting against reservoir history.
   *
   * @param fluid original and independently accepted synthetic fixture
   * @return immutable model bundle
   */
  private CalibratedPvtModel model(SystemInterface fluid) {
    fluid.init(0);
    return new CalibratedPvtModel(fluid, fluid, "public synthetic sample", "original PR/classic",
        "unchanged accepted PR/classic", "synthetic laboratory fixture; no experimental accuracy claim",
        new String[] {"synthetic-CCE"});
  }

  /**
   * Defines a complete two-phase interval on the reversed named component basis.
   *
   * @param subdivisions explicit timestep refinement
   * @return existing history with opt-in phase extraction
   */
  private CompositionalReservoirHistory history(int subdivisions) {
    return new CompositionalReservoirHistory(model(sample()), "synthetic molar phase-rate schedule", BASIS, 323.15,
        80.0, new double[] {0.7, 0.3, 0.0},
        new Step[] {Step.phaseWithdrawal(86400.0, new double[] {80000.0, 20000.0, 0.0}, new double[] {100.0, 2000.0},
            Double.NaN, Double.NaN, false, subdivisions)});
  }

  /** Checks a phase withdrawal against independent TP phase compositions and a direct TV flash. */
  @Test
  void predictsProductionAndPressureFromIndependentEquilibrium() {
    Result result = history(1).replay(100.0);
    double[][] inventory = result.getComponentMoles();
    SystemInterface reference = sample();
    reference.setTotalNumberOfMoles(inventory[0][0] + inventory[0][1]);
    reference.setMolarComposition(new double[] {inventory[0][1], inventory[0][0]});
    new ThermodynamicOperations(reference).TPflash();
    Assertions.assertTrue(reference.hasPhaseType("gas"));
    Assertions.assertTrue(reference.hasPhaseType("oil"));
    double[] withdrawn = new double[2];
    for (int i = 0; i < BASIS.length; i++) {
      withdrawn[i] = 80000.0 * reference.getPhase("gas").getComponent(BASIS[i]).getx()
          + 20000.0 * reference.getPhase("oil").getComponent(BASIS[i]).getx();
    }
    Assertions.assertArrayEquals(withdrawn, result.getProducedComponentMoles()[0], 1.0e-5);
    Assertions.assertEquals(100000.0, withdrawn[0] + withdrawn[1], 1.0e-5);
    Assertions.assertEquals(inventory[0][0] - withdrawn[0] + 100.0, inventory[1][0], 1.0e-5);
    Assertions.assertEquals(inventory[0][1] - withdrawn[1] + 2000.0, inventory[1][1], 1.0e-5);
    reference.setTotalNumberOfMoles(inventory[1][0] + inventory[1][1]);
    reference.setMolarComposition(new double[] {inventory[1][1], inventory[1][0]});
    new ThermodynamicOperations(reference).TVflash(100.0, "m3");
    Assertions.assertEquals(reference.getPressure(), result.getPressuresBara()[1], 1.0e-6);
    SystemInterface produced = result.getProducedFluid(0);
    for (int i = 0; i < BASIS.length; i++) {
      Assertions.assertEquals(withdrawn[i], produced.getComponent(BASIS[i]).getNumberOfmoles(), 1.0e-5);
    }
    Assertions.assertEquals(100.0, result.getFinalFluid().getVolume("m3"), 1.0e-5);
  }

  /** Checks component conservation and first-order convergence in pressure, composition and produced GOR. */
  @Test
  void refinesPhaseExtractionAndSeparatorPredictions() {
    int[] counts = {1, 2, 4, 8, 16};
    double[] pressure = new double[counts.length];
    double[] heavy = new double[counts.length];
    double[] gor = new double[counts.length];
    PvtSeparatorPath separator = new PvtSeparatorPath(new double[] {10.0, 1.01325}, new double[] {303.15, 288.15},
        "synthetic fixed separator; final 15 C, 1.01325 bara");
    for (int i = 0; i < counts.length; i++) {
      long started = System.nanoTime();
      Result result = history(counts[i]).replay(100.0);
      pressure[i] = result.getPressuresBara()[1];
      heavy[i] = result.getProducedComponentMoles()[0][0] / 100000.0;
      gor[i] = separator.separate(result.getProducedFluid(0)).getGasOilRatio();
      double[][] n = result.getComponentMoles();
      double[] produced = result.getProducedComponentMoles()[0];
      Assertions.assertEquals(n[0][0] + 100.0 - produced[0], n[1][0], n[0][0] * 1.0e-9);
      Assertions.assertEquals(n[0][1] + 2000.0 - produced[1], n[1][1], n[0][1] * 1.0e-9);
      Assertions.assertEquals(100000.0, produced[0] + produced[1], 1.0e-5);
      Assertions.assertEquals(86400.0, result.getTimesSeconds()[1], 1.0e-8);
      logger.info("phase-withdrawal substeps={} pressureBara={} producedHeavyFraction={} GOR={} runtimeMs={}",
          counts[i], pressure[i], heavy[i], gor[i], (System.nanoTime() - started) / 1.0e6);
    }
    for (int i = 2; i < counts.length; i++) {
      Assertions.assertTrue(Math.abs(pressure[i] - pressure[i - 1]) < Math.abs(pressure[i - 1] - pressure[i - 2]));
      Assertions.assertTrue(Math.abs(heavy[i] - heavy[i - 1]) < Math.abs(heavy[i - 1] - heavy[i - 2]));
      Assertions.assertTrue(Math.abs(gor[i] - gor[i - 1]) < Math.abs(gor[i - 1] - gor[i - 2]));
    }
    Assertions.assertEquals(pressure[4], pressure[3], pressure[4] * 0.005);
    Assertions.assertEquals(heavy[4], heavy[3], 0.001);
    Assertions.assertEquals(gor[4], gor[3], gor[4] * 0.005);
  }

  /** Checks the dilute-gas analytical pressure and unchanged gas composition under subdivision. */
  @Test
  void recoversIdealGasLimit() {
    SystemInterface gas = new SystemPrEos(400.0, 0.02);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");
    CompositionalReservoirHistory history = new CompositionalReservoirHistory(model(gas), "dilute gas control",
        gas.getComponentNames(), 400.0, 0.02, new double[] {1.0, 0.0, 0.0}, new Step[] {Step.phaseWithdrawal(1000.0,
            new double[] {100.0, 0.0, 0.0}, new double[2], Double.NaN, Double.NaN, false, 8)});
    Result result = history.replay(1000.0);
    double[] produced = result.getProducedComponentMoles()[0];
    Assertions.assertArrayEquals(new double[] {90.0, 10.0}, produced, 1.0e-8);
    double expected = 0.02 - 100.0 * 8.314462618 * 400.0 / (1000.0 * 1.0e5);
    Assertions.assertEquals(expected, result.getPressuresBara()[1], expected * 2.0e-4);
  }

  /** Checks unavailable phases and exhaustion fail without contaminating later replays or checkpoints. */
  @Test
  void rejectsAbsentAndExhaustedPhases() {
    CompositionalReservoirHistory baseline = history(2);
    Result expected = baseline.replay(100.0);
    Step absent = Step.phaseWithdrawal(100.0, new double[] {0.0, 0.0, 1.0}, new double[2], Double.NaN, Double.NaN,
        false, 2);
    Step exhausted = Step.phaseWithdrawal(100.0, new double[] {1.0e20, 0.0, 0.0}, new double[2], Double.NaN, Double.NaN,
        false, 2);
    for (Step step : new Step[] {absent, exhausted}) {
      CompositionalReservoirHistory invalid = new CompositionalReservoirHistory(model(sample()), "invalid history",
          BASIS, 323.15, 80.0, new double[] {0.7, 0.3, 0.0}, new Step[] {step});
      IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, () -> invalid.replay(100.0));
      Assertions.assertTrue(failure.getMessage().contains("interval 0"));
    }
    Assertions.assertArrayEquals(expected.getPressuresBara(), baseline.replay(100.0).getPressuresBara(), 1.0e-9);
  }

  /** Checks immutable schedule/result ownership and explicit distinctions from measured-component inputs. */
  @Test
  void preservesOwnershipAndSerialization() {
    CompositionalReservoirHistory history = history(4);
    Step step = history.getSteps()[0];
    Assertions.assertTrue(step.isPhaseWithdrawal());
    Assertions.assertEquals(4, step.getSubsteps());
    Assertions.assertThrows(IllegalStateException.class, step::getProducedMoles);
    step.getProducedPhaseMoles()[0] = 0.0;
    Assertions.assertEquals(80000.0, step.getProducedPhaseMoles()[0]);
    Result result = history.replay(100.0);
    Result restored = SerializationUtils.clone(history).replay(100.0);
    Assertions.assertArrayEquals(result.getPressuresBara(), restored.getPressuresBara(), 1.0e-9);
    result.getProducedComponentMoles()[0][0] = -1.0;
    Assertions.assertTrue(result.getProducedComponentMoles()[0][0] > 0.0);
    SystemInterface output = result.getProducedFluid(0);
    output.setTotalNumberOfMoles(1.0);
    Assertions.assertEquals(100000.0, SerializationUtils.clone(result).getProducedFluid(0).getTotalNumberOfMoles(),
        1.0e-5);
    Assertions.assertThrows(IllegalArgumentException.class, () -> Step.phaseWithdrawal(1.0,
        new double[] {1.0, -1.0, 0.0}, new double[2], Double.NaN, Double.NaN, false, 1));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> Step.phaseWithdrawal(1.0, new double[3], new double[2], Double.NaN, Double.NaN, false, 0));
    Assertions.assertThrows(IllegalArgumentException.class, () -> Step.phaseWithdrawal(1.0,
        new double[] {Double.MIN_VALUE, 0.0, 0.0}, new double[2], Double.NaN, Double.NaN, false, 2));
  }

  /** Zero production has a zero component ledger and no fabricated produced stream. */
  @Test
  void zeroProductionHasNoMixture() {
    Step empty = Step.phaseWithdrawal(100.0, new double[3], new double[2], Double.NaN, Double.NaN, false, 2);
    Result result = new CompositionalReservoirHistory(model(sample()), "zero-production interval", BASIS, 323.15, 80.0,
        new double[] {0.7, 0.3, 0.0}, new Step[] {empty}).replay(100.0);
    Assertions.assertArrayEquals(new double[2], result.getProducedComponentMoles()[0]);
    Assertions.assertThrows(IllegalStateException.class, () -> result.getProducedFluid(0));
  }

  /** Fits only pressure observations while leaving produced composition as a forward-model result. */
  @Test
  void phaseModeRecoversVolumeWithSeparatePressureHoldout() {
    SystemInterface gas = new SystemPrEos(400.0, 200.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");
    CalibratedPvtModel model = model(gas);
    Step[] steps = new Step[3];
    for (int i = 0; i < steps.length; i++) {
      steps[i] = Step.phaseWithdrawal(86400.0, new double[] {200000.0, 0.0, 0.0}, new double[2], Double.NaN, Double.NaN,
          false, 2);
    }
    CompositionalReservoirHistory forward = new CompositionalReservoirHistory(model, "synthetic gas phase schedule",
        gas.getComponentNames(), 400.0, 200.0, new double[] {1.0, 0.0, 0.0}, steps);
    double[] reference = forward.replay(1017.3).getPressuresBara();
    for (int i = 0; i < steps.length; i++) {
      steps[i] = Step.phaseWithdrawal(86400.0, new double[] {200000.0, 0.0, 0.0}, new double[2],
          reference[i + 1] + (i == 2 ? 5.0 : 0.0), 0.5, i == 2, 2);
    }
    CompositionalReservoirHistory inverse = new CompositionalReservoirHistory(model, "synthetic held-out pressure",
        gas.getComponentNames(), 400.0, 200.0, new double[] {1.0, 0.0, 0.0}, steps);
    CompositionalReservoirHistory.Fit fit = inverse.fitVolume(800.0, 1300.0);
    Assertions.assertEquals(1017.3, fit.getResult().getInitialVolumeM3(), 0.001);
    Assertions.assertEquals(100.0, fit.getResult().getHoldoutChiSquare(), 0.001);
    Assertions.assertArrayEquals(new double[] {180000.0, 20000.0}, fit.getResult().getProducedComponentMoles()[2],
        1.0e-5);
    logger.info("phase-mode fitVolumeM3={} heldoutChiSquare={}", fit.getResult().getInitialVolumeM3(),
        fit.getResult().getHoldoutChiSquare());
  }

  /** Checks internal extraction substeps share exactly the existing implicit aquifer time path. */
  @Test
  void aquiferSubstepsMatchExplicitlySplitHistory() {
    SystemInterface gas = new SystemPrEos(400.0, 200.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    CalibratedPvtModel model = model(gas);
    neqsim.pvtsimulation.reservoirproperties.materialbalance.FetkovichAquifer aquifer = new neqsim.pvtsimulation.reservoirproperties.materialbalance.FetkovichAquifer(
        100000.0, 0.00005, 0.00001, 200.0);
    Step full = Step.phaseWithdrawal(1000.0, new double[] {200000.0, 0.0, 0.0}, new double[1], Double.NaN, Double.NaN,
        false, 2);
    Step half = Step.phaseWithdrawal(500.0, new double[] {100000.0, 0.0, 0.0}, new double[1], Double.NaN, Double.NaN,
        false, 1);
    Result combined = new CompositionalReservoirHistory(model, "one subdivided interval", gas.getComponentNames(),
        400.0, 200.0, new double[] {1.0, 0.0, 0.0}, new Step[] {full}).replay(1000.0, aquifer);
    Result split = new CompositionalReservoirHistory(model, "two explicit intervals", gas.getComponentNames(), 400.0,
        200.0, new double[] {1.0, 0.0, 0.0}, new Step[] {half, half}).replay(1000.0, aquifer);
    Assertions.assertEquals(split.getPressuresBara()[2], combined.getPressuresBara()[1], 1.0e-8);
    Assertions.assertEquals(split.getAquiferInfluxM3()[2], combined.getAquiferInfluxM3()[1], 1.0e-8);
    Assertions.assertEquals(1000.0, combined.getFinalFluid().getVolume("m3") + combined.getAquiferInfluxM3()[1],
        1.0e-5);
    Assertions.assertEquals(combined.getComponentMoles()[0][0] - 200000.0, combined.getComponentMoles()[1][0], 1.0e-5);
  }

  /** Checks tracked three-phase production, including water injection, on a complete reversed named basis. */
  @Test
  void trackedAqueousWithdrawalClosesAllComponents() {
    SystemInterface sample = new SystemPrEos(350.0, 100.0);
    sample.addComponent("methane", 0.5);
    sample.addComponent("n-heptane", 0.5);
    sample.addComponent("water", 0.5);
    sample.setMixingRule("classic");
    sample.setMultiPhaseCheck(true);
    String[] basis = {"water", "n-heptane", "methane"};
    Result result = new CompositionalReservoirHistory(model(sample), "synthetic tracked aqueous extraction", basis,
        350.0, 100.0, new double[] {0.5, 0.4, 0.1}, new Step[] {Step.phaseWithdrawal(100.0,
            new double[] {1000.0, 100.0, 200.0}, new double[] {50.0, 0.0, 0.0}, Double.NaN, Double.NaN, false, 2)})
        .replay(100.0);
    double[][] n = result.getComponentMoles();
    double[] produced = result.getProducedComponentMoles()[0];
    for (int i = 0; i < basis.length; i++) {
      Assertions.assertEquals(n[0][i] + (i == 0 ? 50.0 : 0.0) - produced[i], n[1][i], n[0][i] * 1.0e-9);
    }
    Assertions.assertEquals(1300.0, produced[0] + produced[1] + produced[2], 1.0e-5);
  }
}
