package neqsim.pvtsimulation.util;

import java.util.UUID;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Fit;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Result;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Step;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Synthetic compositional replay qualification, independent EOS checks and volume matching.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CompositionalReservoirHistoryTest {
  private static final double TEMPERATURE = 400.0;
  private static final double PRESSURE = 200.0;
  private static final String[] BASIS = {"ethane", "methane"};

  /**
   * Returns independently defined two-component gas EOS.
   *
   * @return independently defined two-component gas EOS
   */
  private SystemInterface fluid() {
    SystemInterface fluid = new SystemPrEos(TEMPERATURE, PRESSURE);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return fluid;
  }

  /**
   * Returns synthetic immutable checkpoints with laboratory provenance, without reservoir tuning.
   *
   * @return synthetic immutable checkpoints with laboratory provenance, without reservoir tuning
   */
  private CalibratedPvtModel model() {
    return new CalibratedPvtModel(fluid(), fluid(), "synthetic sample", "original PR/classic",
        "laboratory accepted PR/classic", "synthetic laboratory calibration fixture", new String[] {"CCE-1"});
  }

  /**
   * Creates one measured interval on the reversed named basis.
   *
   * @param pressure measured pressure or NaN
   * @param sigma supplied uncertainty or NaN
   * @param holdout whether pressure is withheld from fitting
   * @return measured component interval
   */
  private Step step(double pressure, double sigma, boolean holdout) {
    return new Step(86400.0, new double[] {40000.0, 400000.0}, new double[] {100000.0, 10000.0}, pressure, sigma,
        holdout);
  }

  /**
   * Builds a gas-only fixed-temperature history.
   *
   * @param steps measured intervals
   * @return compositional history
   */
  private CompositionalReservoirHistory history(Step... steps) {
    return new CompositionalReservoirHistory(model(), "synthetic molar interval history, no surface conversion", BASIS,
        TEMPERATURE, PRESSURE, new double[] {1.0, 0.0, 0.0}, steps);
  }

  /** Checks exact component accounting, injection enrichment and independent EOS pressure on each interval. */
  @Test
  void replayConservesNamedComponentsAndAgreesWithIndependentEos() {
    double volume = 1000.0;
    CompositionalReservoirHistory history = history(step(Double.NaN, Double.NaN, false),
        step(Double.NaN, Double.NaN, false), step(Double.NaN, Double.NaN, false));
    Result result = history.replay(volume);
    double[][] amounts = result.getComponentMoles();
    SystemInterface initial = fluid();
    new ThermodynamicOperations(initial).TPflash();
    initial.init(1);
    initial.setTotalNumberOfMoles(initial.getTotalNumberOfMoles() * volume / initial.getVolume("m3"));
    double[] pressure = result.getPressuresBara();
    for (int row = 0; row < 3; row++) {
      Assertions.assertEquals(amounts[0][0] + (row + 1) * 60000.0, amounts[row + 1][0], 1.0e-6);
      Assertions.assertEquals(amounts[0][1] - (row + 1) * 390000.0, amounts[row + 1][1], 1.0e-6);
      SystemInterface independent = fluid();
      independent.setTotalNumberOfMoles(amounts[row + 1][0] + amounts[row + 1][1]);
      independent.setMolarComposition(new double[] {amounts[row + 1][1], amounts[row + 1][0]});
      independent.setPressure(pressure[row + 1]);
      new ThermodynamicOperations(independent).TPflash();
      independent.init(1);
      // Independent fixed-pressure EOS evaluation must reproduce the target volume.
      Assertions.assertEquals(volume, independent.getVolume("m3"), volume * 1.0e-7);
      Assertions.assertEquals((row + 1) * 86400.0, result.getTimesSeconds()[row + 1]);
      Assertions.assertTrue(pressure[row + 1] < pressure[row]);
    }
    Assertions.assertEquals(initial.getComponent("ethane").getNumberOfmoles(), amounts[0][0], 1.0e-6);
    Assertions.assertTrue(result.getFinalFluid().getComponent("ethane").getz() > 0.1);
    Assertions.assertArrayEquals(pressure, history.replay(volume).getPressuresBara(), 1.0e-10);
    Assertions.assertEquals(0.0, result.getCalibrationChiSquare());
    Assertions.assertTrue(Double.isNaN(result.getStandardizedResiduals()[0]));
  }

  /** Recovers known volume; changing a held-out measurement does not change the fitted volume. */
  @Test
  void recoversVolumeAndExcludesHoldoutFromObjective() {
    double[] reference = history(step(Double.NaN, Double.NaN, false), step(Double.NaN, Double.NaN, false),
        step(Double.NaN, Double.NaN, false)).replay(1017.3).getPressuresBara();
    CompositionalReservoirHistory history = history(step(reference[1], 0.5, false), step(reference[2], 0.25, false),
        step(reference[3] + 5.0, 0.5, true));
    Fit fit = history.fitVolume(500.0, 2000.0);
    Assertions.assertEquals(1017.3, fit.getResult().getInitialVolumeM3(), 0.001);
    Assertions.assertTrue(fit.getResult().getCalibrationChiSquare() < 1.0e-8);
    Assertions.assertEquals(100.0, fit.getResult().getHoldoutChiSquare(), 0.001);
    Assertions.assertFalse(fit.isAtBound());
    Fit changedHoldout = history(step(reference[1], 0.5, false), step(reference[2], 0.25, false),
        step(reference[3] - 20.0, 0.5, true)).fitVolume(500.0, 2000.0);
    Assertions.assertEquals(fit.getResult().getInitialVolumeM3(), changedHoldout.getResult().getInitialVolumeM3());
  }

  /** Checks supplied pressure-error weighting and bound-limited diagnostics. */
  @Test
  void weightsResidualsAndReportsBounds() {
    double[] reference = history(step(Double.NaN, Double.NaN, false), step(Double.NaN, Double.NaN, false))
        .replay(1000.0).getPressuresBara();
    Result result = history(step(reference[1] + 1.0, 0.5, false), step(reference[2] - 1.0, 2.0, true)).replay(1000.0);
    Assertions.assertEquals(4.0, result.getCalibrationChiSquare(), 1.0e-8);
    Assertions.assertEquals(0.25, result.getHoldoutChiSquare(), 1.0e-8);
    Fit bounded = history(step(reference[1], 1.0, false), step(reference[2], 1.0, false)).fitVolume(1100.0, 2000.0);
    Assertions.assertTrue(bounded.isAtBound());
    Assertions.assertEquals(1100.0, bounded.getResult().getInitialVolumeM3());
  }

  /** Checks defensive ownership, serialized replay and checkpoint immutability. */
  @Test
  void ownsInputsOutputsAndSerializedState() {
    double[] amounts = {40000.0, 400000.0};
    Step step = new Step(86400.0, amounts, new double[2], Double.NaN, Double.NaN, false);
    CompositionalReservoirHistory history = history(step);
    String provenance = history.getModel().getProvenance();
    amounts[0] = Double.NaN;
    Result result = history.replay(1000.0);
    result.getComponentMoles()[0][0] = -1.0;
    result.getPressuresBara()[0] = -1.0;
    result.getFinalFluid().setPressure(1.0);
    Assertions.assertTrue(result.getComponentMoles()[0][0] > 0.0);
    Assertions.assertEquals(PRESSURE, result.getPressuresBara()[0], 1.0e-8);
    Assertions.assertTrue(result.getFinalFluid().getPressure() > 100.0);
    Assertions.assertArrayEquals(result.getPressuresBara(),
        SerializationUtils.clone(history).replay(1000.0).getPressuresBara(), 1.0e-10);
    Assertions.assertEquals(provenance, history.getModel().getProvenance());
    Assertions.assertEquals(1.0, history.getModel().getTunedFluid().getTotalNumberOfMoles(), 1.0e-12);
  }

  /** Checks invalid basis, uninformative matching, infeasibility diagnostics and rejected amounts. */
  @Test
  void rejectsUnsupportedAndUninformativeHistory() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new Step(0.0, new double[2], new double[2], 100.0, 1.0, false));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new Step(1.0, new double[] {-1.0}, new double[1], 100.0, 1.0, false));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new Step(1.0, new double[2], new double[2], 100.0, 0.0, false));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new Step(1.0, new double[2], new double[2], Double.NaN, 1.0, false));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new CompositionalReservoirHistory(model(), "history", new String[] {"methane", "methane"}, TEMPERATURE,
            PRESSURE, new double[] {1.0, 0.0, 0.0}, new Step[] {step(100.0, 1.0, false)}));
    Assertions.assertThrows(IllegalStateException.class,
        () -> new CompositionalReservoirHistory(model(), "history", BASIS, TEMPERATURE, PRESSURE,
            new double[] {0.0, 1.0, 0.0}, new Step[] {step(100.0, 1.0, false)}).replay(1000.0));
    Step flat = new Step(1.0, new double[2], new double[2], PRESSURE, 1.0, false);
    Assertions.assertThrows(IllegalStateException.class, () -> history(flat, flat).fitVolume(500.0, 2000.0));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> history(step(100.0, 1.0, true)).fitVolume(500.0, 2000.0));
    IllegalStateException rejected = Assertions.assertThrows(IllegalStateException.class,
        () -> history(step(100.0, 1.0, false)).replay(1.0));
    Assertions.assertTrue(rejected.getMessage().contains("interval 0"));
    Fit fit = history(step(180.0, 1.0, false), step(160.0, 1.0, false)).fitVolume(1.0, 2000.0);
    Assertions.assertTrue(fit.getRejectedTrials().length > 0);
    Assertions.assertTrue(fit.getRejectedTrials()[0].contains("interval 0"));
  }

  /** Ensures rejected component updates preserve every accepted tank state, time and calculation identity. */
  @Test
  void tankRejectionIsTransactionalAndRequiresDedicatedSetup() {
    SimpleReservoir tank = new SimpleReservoir("transaction");
    tank.setReservoirFluid(fluid(), 1000.0, 0.0, 0.0);
    UUID accepted = UUID.randomUUID();
    Assertions.assertThrows(IllegalStateException.class,
        () -> tank.advanceComponentAmounts(1.0, new double[2], new double[2], accepted));
    tank.setRecordProductionHistory(false);
    tank.advanceComponentAmounts(1.0, new double[2], new double[2], accepted);
    SystemInterface before = tank.getReservoirFluid();
    double time = tank.getTime();
    Assertions.assertThrows(IllegalArgumentException.class, () -> tank.advanceComponentAmounts(1.0,
        new double[] {Double.MAX_VALUE, 0.0}, new double[2], UUID.randomUUID()));
    Assertions.assertSame(before, tank.getReservoirFluid());
    Assertions.assertEquals(time, tank.getTime());
    Assertions.assertEquals(accepted, tank.getCalculationIdentifier());
    Assertions.assertEquals(0, tank.getPressureHistory().length);
    Assertions.assertThrows(IllegalStateException.class, () -> tank.setRecordProductionHistory(true));
    Assertions.assertThrows(IllegalStateException.class, () -> tank.runTransient(1.0));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> tank.advanceComponentAmounts(Double.NaN, new double[2], new double[2], UUID.randomUUID()));
    tank.addGasProducer("connected producer");
    Assertions.assertThrows(IllegalStateException.class,
        () -> tank.advanceComponentAmounts(1.0, new double[2], new double[2], UUID.randomUUID()));
  }

  /** Checks the independent ideal-gas pressure limit below the stream production cutoff. */
  @Test
  void lowDensityReplayAgreesWithIdealGasLimit() {
    SystemInterface sample = new SystemPrEos(500.0, 1.0);
    sample.addComponent("methane", 1.0);
    sample.setMixingRule("classic");
    SimpleReservoir tank = new SimpleReservoir("ideal limit");
    tank.setRecordProductionHistory(false);
    tank.setReservoirFluid(sample, 1000.0, 0.0, 0.0);
    double initial = tank.getReservoirFluid().getTotalNumberOfMoles();
    tank.advanceComponentAmounts(86400.0, new double[] {0.2 * initial}, new double[1], UUID.randomUUID());
    double idealPressure = 0.8 * initial * 8.31446261815324 * 500.0 / 1000.0 / 1.0e5;
    Assertions.assertEquals(idealPressure, tank.getReservoirFluid().getPressure(), idealPressure * 0.001);
    Assertions.assertFalse(tank.isLowPressureLimitReached());
  }

  /** Checks aqueous injection and hydrocarbon withdrawal against all three component ledgers. */
  @Test
  void threePhaseTankConservesWaterAndHydrocarbons() {
    SystemInterface sample = new SystemPrEos(320.0, 80.0);
    sample.addComponent("methane", 0.5);
    sample.addComponent("n-heptane", 0.5);
    sample.addComponent("water", 0.05);
    sample.setMixingRule("classic");
    sample.setMultiPhaseCheck(true);
    SimpleReservoir tank = new SimpleReservoir("three phase history");
    tank.setRecordProductionHistory(false);
    tank.setReservoirFluid(sample, 50.0, 40.0, 10.0);
    Assertions.assertEquals(3, tank.getReservoirFluid().getNumberOfPhases());
    double[] initial = new double[3];
    for (int i = 0; i < 3; i++) {
      initial[i] = tank.getReservoirFluid().getComponent(i).getNumberOfmoles();
    }
    tank.advanceComponentAmounts(86400.0, new double[] {1000.0, 100.0, 0.0}, new double[] {0.0, 0.0, 1000.0},
        UUID.randomUUID());
    double[] delta = {-1000.0, -100.0, 1000.0};
    for (int i = 0; i < 3; i++) {
      Assertions.assertEquals(initial[i] + delta[i], tank.getReservoirFluid().getComponent(i).getNumberOfmoles(),
          initial[i] * 1.0e-10);
    }
    Assertions.assertEquals(100.0, tank.getReservoirFluid().getVolume("m3"), 1.0e-5);
  }

  /** Checks gas appearance during oil depletion and interval refinement without replacing the EOS path. */
  @Test
  void oilDepletionCreatesGasAndRefinesConsistently() {
    SystemInterface sample = new SystemPrEos(350.0, 250.0);
    sample.addComponent("methane", 0.3);
    sample.addComponent("n-heptane", 0.7);
    sample.setMixingRule("classic");
    SimpleReservoir tank = new SimpleReservoir("oil depletion");
    tank.setRecordProductionHistory(false);
    tank.setReservoirFluid(sample, 0.0, 1000.0, 0.0);
    Assertions.assertFalse(tank.getReservoirFluid().hasPhaseType("gas"));
    SimpleReservoir refined = SerializationUtils.clone(tank);
    double[] produced = new double[2];
    for (int i = 0; i < 2; i++) {
      produced[i] = 0.8 * tank.getReservoirFluid().getComponent(i).getNumberOfmoles();
    }
    tank.advanceComponentAmounts(86400.0, produced, new double[2], UUID.randomUUID());
    double[] half = {produced[0] / 2.0, produced[1] / 2.0};
    refined.advanceComponentAmounts(43200.0, half, new double[2], UUID.randomUUID());
    refined.advanceComponentAmounts(43200.0, half, new double[2], UUID.randomUUID());
    Assertions.assertTrue(tank.getReservoirFluid().hasPhaseType("gas"));
    Assertions.assertTrue(tank.getReservoirFluid().hasPhaseType("oil"));
    Assertions.assertEquals(tank.getReservoirFluid().getPressure(), refined.getReservoirFluid().getPressure(),
        tank.getReservoirFluid().getPressure() * 1.0e-6);
    Assertions.assertEquals(tank.getTime(), refined.getTime());
  }

  /** Executes the complete Java workflow shown in the material-balance guide. */
  @Test
  void documentedWorkflow() {
    SystemInterface original = new SystemPrEos(400.0, 200.0);
    original.addComponent("methane", 0.9);
    original.addComponent("ethane", 0.1);
    original.setMixingRule("classic");
    original.init(0);
    CalibratedPvtModel model = new CalibratedPvtModel(original, original.clone(), "synthetic sample v1",
        "original PR/classic", "accepted PR/classic", "synthetic calibration fixture", new String[] {"CCE-1"});
    Step first = new Step(86400.0, new double[] {400000.0, 40000.0}, new double[] {10000.0, 100000.0}, Double.NaN,
        Double.NaN, false);
    CompositionalReservoirHistory forward = new CompositionalReservoirHistory(model, "synthetic molar history v1",
        new String[] {"methane", "ethane"}, 400.0, 200.0, new double[] {1.0, 0.0, 0.0},
        new Step[] {first, first, first});
    double[] pressure = forward.replay(1000.0).getPressuresBara();
    Step[] measured = new Step[3];
    for (int i = 0; i < measured.length; i++) {
      measured[i] = new Step(86400.0, first.getProducedMoles(), first.getInjectedMoles(), pressure[i + 1], 0.5, i == 2);
    }
    CompositionalReservoirHistory inverse = new CompositionalReservoirHistory(model, "synthetic pressure history v1",
        forward.getComponents(), 400.0, 200.0, new double[] {1.0, 0.0, 0.0}, measured);
    Fit fit = inverse.fitVolume(500.0, 2000.0);
    double poreVolumeM3 = fit.getResult().getInitialVolumeM3();
    double heldOutChiSquare = fit.getResult().getHoldoutChiSquare();
    Assertions.assertEquals(1000.0, poreVolumeM3, 0.001);
    Assertions.assertEquals(0.0, heldOutChiSquare, 1.0e-8);
  }
}
