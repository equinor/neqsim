package neqsim.pvtsimulation.regression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.DifferentialLiberation;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Laboratory protocol identity checks for actual optimizer samples, without fitted parameters.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PVTRegressionDepletionHistoryTest {
  /**
   * Full CVD gas-removal history must survive the optimizer sample registration.
   *
   * @throws Exception if sample registration cannot be inspected
   */
  @Test
  void cvdSamplesMatchTheCompleteExperiment() throws Exception {
    double[] pressures = {300.0, 150.0, 50.0, 20.0};
    ConstantVolumeDepletion direct = new ConstantVolumeDepletion(fluid());
    direct.setTemperature(323.15, "K");
    direct.setPressures(pressures.clone());
    direct.runCalc();
    PVTRegression regression = new PVTRegression(fluid());
    regression.addCVDData(pressures, direct.getLiquidRelativeVolume(), direct.getZgas(), 323.15);
    assertSamplesMatchObservations(samples(regression, "addCVDSamples"));
  }

  /**
   * DLE samples must share the depleted fluid and final stock-tank oil basis.
   *
   * @throws Exception if sample registration cannot be inspected
   */
  @Test
  void dleSamplesMatchTheCompleteExperiment() throws Exception {
    double[] pressures = {300.0, 150.0, 50.0, 20.0};
    DifferentialLiberation direct = new DifferentialLiberation(fluid());
    direct.setTemperature(323.15, "K");
    direct.setPressures(pressures.clone());
    direct.runCalc();
    PVTRegression regression = new PVTRegression(fluid());
    regression.addDLEData(pressures, direct.getRs(), direct.getBo(), direct.getOilDensity(), 323.15);
    assertSamplesMatchObservations(samples(regression, "addDLESamples"));
  }

  /**
   * Repeated registrations retain separate temperatures, pressure paths and stock-tank reference volumes.
   *
   * @throws Exception if objective/sample inspection fails
   */
  @Test
  void distinctExperimentsAndFinalObjectivesKeepTheirOwnHistories() throws Exception {
    PVTRegression regression = new PVTRegression(fluid());
    for (double temperature : new double[] {323.15, 343.15}) {
      double[] schedule = temperature == 323.15 ? new double[] {300.0, 150.0, 50.0, 20.0}
          : new double[] {250.0, 100.0, 30.0};
      DifferentialLiberation dle = new DifferentialLiberation(fluid());
      dle.setTemperature(temperature, "K");
      dle.setPressures(schedule.clone());
      dle.runCalc();
      regression.addDLEData(schedule, dle.getRs(), dle.getBo(), dle.getOilDensity(), temperature);
      ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(fluid());
      cvd.setTemperature(temperature, "K");
      cvd.setPressures(schedule.clone());
      cvd.runCalc();
      regression.addCVDData(schedule, cvd.getLiquidRelativeVolume(), cvd.getZgas(), temperature);
      schedule[0] = 999.0;
    }
    assertSamplesMatchObservations(samples(regression, "addDLESamples"));
    assertSamplesMatchObservations(samples(regression, "addCVDSamples"));
    for (String method : new String[] {"calculateDLEObjective", "calculateCVDObjective"}) {
      Method objective = PVTRegression.class.getDeclaredMethod(method);
      objective.setAccessible(true);
      assertEquals(0.0, (double) objective.invoke(regression), 1e-16, method);
    }
    regression.clearData();
    assertTrue(samples(regression, "addDLESamples").isEmpty());
    assertTrue(samples(regression, "addCVDSamples").isEmpty());
  }

  /** Complete experiments are reused across observation clones but recomputed for changed parameter vectors. */
  @Test
  void cachedTrialsFollowTheCompleteParameterVectorAndPreserveTheSource() {
    SystemInterface source = fluid();
    source.addComponent("CO2", 0.04);
    source.setMixingRule("classic");
    double originalBip = ((PhaseEosInterface) source.getPhase(0)).getEosMixingRule().getBinaryInteractionParameter(0,
        4);
    RegressionParameter parameter = RegressionParameter.BIP_CO2_HC;
    PVTRegressionFunction function = new PVTRegressionFunction(source,
        Arrays.asList(new RegressionParameterConfig(parameter, 0.08, 0.18, 0.12)),
        new EnumMap<ExperimentType, Double>(ExperimentType.class));
    double[] pressures = {300.0, 150.0, 50.0, 20.0};
    int id = function.registerDepletionExperiment(ExperimentType.DLE, pressures, 323.15);
    PVTRegressionFunction sibling = function.clone();
    double originalMoles = source.getTotalNumberOfMoles();
    for (double value : new double[] {0.10, 0.16, 0.10}) {
      function.setFittingParams(0, value);
      sibling.setFittingParams(0, value);
      SystemInterface trial = SerializationUtils.clone(source);
      parameter.applyToFluid(trial, value);
      trial.init(0);
      trial.init(1);
      DifferentialLiberation direct = new DifferentialLiberation(trial);
      direct.setTemperature(323.15, "K");
      direct.setPressures(pressures.clone());
      direct.runCalc();
      double[][] expected = {direct.getRs(), direct.getBo(), direct.getOilDensity()};
      for (int row = 0; row < pressures.length; row++) {
        for (int property = 0; property < 3; property++) {
          double[] observation = {pressures[row], 323.15, ExperimentType.DLE.ordinal(), property, id, row};
          assertEquals(expected[property][row], function.calcValue(observation), 1e-8);
          assertEquals(expected[property][row], sibling.calcValue(observation), 1e-8);
        }
      }
    }
    assertEquals(323.15, source.getTemperature(), 0.0);
    assertEquals(300.0, source.getPressure(), 0.0);
    assertEquals(originalMoles, source.getTotalNumberOfMoles(), 0.0);
    assertEquals(originalBip,
        ((PhaseEosInterface) source.getPhase(0)).getEosMixingRule().getBinaryInteractionParameter(0, 4), 0.0);
    double[] observation = {50.0, 323.15, ExperimentType.DLE.ordinal(), 0, id, 2};
    double accepted = function.calcValue(observation);
    function.getFittingParams()[0] = Double.NaN;
    assertThrows(IllegalArgumentException.class, () -> function.calcValue(observation));
    function.getFittingParams()[0] = 0.10;
    assertEquals(accepted, function.calcValue(observation), 0.0);
    source.setTemperature(500.0);
    assertEquals(accepted, function.calcValue(observation), 0.0);
  }

  /** Each full calculation runs once across its pressure/property observation functions at an unchanged trial. */
  @Test
  void observationClonesReuseOneCompleteExperiment() {
    CountingFluid source = new CountingFluid();
    PVTRegressionFunction function = new PVTRegressionFunction(source,
        Collections.<RegressionParameterConfig>emptyList(), new EnumMap<ExperimentType, Double>(ExperimentType.class));
    int id = function.registerDepletionExperiment(ExperimentType.DLE, new double[] {300.0, 150.0, 50.0}, 323.15);
    PVTRegressionFunction clone = function.clone();
    function.calcValue(new double[] {300.0, 323.15, ExperimentType.DLE.ordinal(), 0, id, 0});
    int afterFullExperiment = CountingFluid.copies.get();
    clone.calcValue(new double[] {50.0, 323.15, ExperimentType.DLE.ordinal(), 2, id, 2});
    function.calcValue(new double[] {150.0, 323.15, ExperimentType.DLE.ordinal(), 1, id, 1});
    assertEquals(afterFullExperiment, CountingFluid.copies.get(),
        "A cached observation must not copy or rerun a fluid");
  }

  /** A failed experiment must propagate and leave its protocol available for a fresh successful attempt. */
  @Test
  void failedExperimentCannotPublishOrReusePartialResults() {
    PVTRegressionFunction function = new PVTRegressionFunction(new CountingFluid(),
        Collections.<RegressionParameterConfig>emptyList(), new EnumMap<ExperimentType, Double>(ExperimentType.class));
    int first = function.registerDepletionExperiment(ExperimentType.DLE, new double[] {300.0, 150.0, 50.0}, 323.15);
    function.calcValue(new double[] {50.0, 323.15, ExperimentType.DLE.ordinal(), 0, first, 2});
    int second = function.registerDepletionExperiment(ExperimentType.DLE, new double[] {300.0, 100.0, 30.0}, 323.15);
    double[] observation = {30.0, 323.15, ExperimentType.DLE.ordinal(), 0, second, 2};
    CountingFluid.rejectInitialization.set(true);
    try {
      assertThrows(IllegalStateException.class, () -> function.calcValue(observation));
    } finally {
      CountingFluid.rejectInitialization.set(false);
    }
    double accepted = function.calcValue(observation);
    assertTrue(Double.isFinite(accepted));
    int copies = CountingFluid.copies.get();
    assertEquals(accepted, function.calcValue(observation), 0.0);
    assertEquals(copies, CountingFluid.copies.get());
  }

  /** Malformed protocol identities cannot silently fall back to singleton experiments or truncate indexes. */
  @Test
  void malformedSchedulesAndRowsFailBeforeSimulation() {
    PVTRegressionFunction function = new PVTRegressionFunction(fluid(),
        Collections.<RegressionParameterConfig>emptyList(), new EnumMap<ExperimentType, Double>(ExperimentType.class));
    for (double[] invalid : new double[][] {null, {}, {100.0, 200.0}, {Double.NaN}, {-1.0}}) {
      assertThrows(IllegalArgumentException.class,
          () -> function.registerDepletionExperiment(ExperimentType.DLE, invalid, 323.15));
    }
    function.registerDepletionExperiment(ExperimentType.DLE, new double[] {300.0, 150.0, 50.0}, 323.15);
    double[][] invalidRows = {{50, 323.15, 2, 0, 0}, {50, 323.15, ExperimentType.DLE.ordinal(), 0, 0, 1.5},
        {50, 323.15, ExperimentType.DLE.ordinal(), 0, 0, 3}, {50, 323.15, ExperimentType.DLE.ordinal(), 0, 1, 2},
        {50, 323.15, ExperimentType.DLE.ordinal(), 0, Double.NaN, 2},
        {50, 323.15, ExperimentType.DLE.ordinal(), 0.5, 0, 2}, {51, 323.15, ExperimentType.DLE.ordinal(), 0, 0, 2},
        {50, 343.15, ExperimentType.DLE.ordinal(), 0, 0, 2}, {50, 323.15, ExperimentType.CVD.ordinal(), 0, 0, 2}};
    for (double[] row : invalidRows) {
      assertThrows(IllegalArgumentException.class, () -> function.calcValue(row));
    }
    PVTRegression regression = new PVTRegression(fluid());
    assertThrows(IllegalArgumentException.class,
        () -> regression.addDLEData(new double[] {300, 50}, new double[] {1}, new double[2], new double[2], 323.15));
    assertTrue(regression.getDLEData().isEmpty());
  }

  /**
   * Counts real fluid-cloning work to detect repeated full simulations across observation functions.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class CountingFluid extends SystemSrkEos {
    private static final long serialVersionUID = 1L;
    private static final AtomicInteger copies = new AtomicInteger();
    private static final AtomicBoolean rejectInitialization = new AtomicBoolean();

    /** Create the same synthetic hydrocarbon fluid used by the protocol oracle. */
    private CountingFluid() {
      super(323.15, 300.0);
      addComponent("methane", 0.70);
      addComponent("ethane", 0.10);
      addComponent("propane", 0.05);
      addComponent("n-decane", 0.15);
      setMixingRule("classic");
      setMultiPhaseCheck(true);
    }

    /** {@inheritDoc} */
    @Override
    public CountingFluid clone() {
      copies.incrementAndGet();
      return (CountingFluid) super.clone();
    }

    /** {@inheritDoc} */
    @Override
    public void init(int type) {
      if (rejectInitialization.get()) {
        throw new IllegalStateException("Injected experiment initialization failure");
      }
      super.init(type);
    }

    /**
     * Count deep graph copies as well as ordinary clones.
     *
     * @param input serialized model stream
     * @throws IOException if model deserialization fails
     * @throws ClassNotFoundException if a model class is unavailable
     */
    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
      input.defaultReadObject();
      copies.incrementAndGet();
    }
  }

  /**
   * Exercise the same private sample builder used by runRegression, without optimizer iterations.
   *
   * @param regression configured public registration facade
   * @param builder actual sample-registration method
   * @return optimizer samples
   * @throws Exception if the private builder cannot be invoked
   */
  private static ArrayList<SampleValue> samples(PVTRegression regression, String builder) throws Exception {
    Method create = PVTRegression.class.getDeclaredMethod("createRegressionFunction");
    create.setAccessible(true);
    PVTRegressionFunction function = (PVTRegressionFunction) create.invoke(regression);
    Method add = PVTRegression.class.getDeclaredMethod(builder, ArrayList.class, PVTRegressionFunction.class);
    add.setAccessible(true);
    ArrayList<SampleValue> samples = new ArrayList<>();
    add.invoke(regression, samples, function);
    return samples;
  }

  /**
   * Compare every registered property at every pressure, then repeat in reverse order.
   *
   * @param samples real optimizer observation rows
   */
  private static void assertSamplesMatchObservations(ArrayList<SampleValue> samples) {
    for (int pass = 0; pass < 2; pass++) {
      for (int i = 0; i < samples.size(); i++) {
        SampleValue sample = samples.get(pass == 0 ? i : samples.size() - 1 - i);
        double expected = sample.getSampleValue();
        assertEquals(expected, sample.getFunction().calcValue(sample.getDependentValues()),
            1e-9 * Math.max(1.0, Math.abs(expected)), "Full protocol row " + i);
      }
    }
  }

  /**
   * Original public synthetic SRK case; this proves protocol identity, not measured accuracy.
   *
   * @return fresh untuned fluid
   */
  private static SystemInterface fluid() {
    SystemInterface fluid = new SystemSrkEos(323.15, 300.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-decane", 0.15);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }
}
