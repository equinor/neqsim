package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Qualifies independent public fluid clones during concurrent, changed-state TP flashes.
 *
 * <p>
 * Unlike serialization-based process copies, ordinary clones exercise component derivative ownership directly. Each
 * thread owns its fluid and operation; model configuration is complete before the threads start. The cases retain two
 * phases and do not change global warm-start settings or claim that one mutable fluid can be shared between threads.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class TPflashConcurrentCloneIsolationTest {
  /**
   * Compares simultaneous sibling clones with serial controls and checks the untouched parent after every stage.
   *
   * @param model zero for SRK, one for PR, two for CPA
   * @throws Exception if a concurrent flash fails or the worker coordination is interrupted
   */
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void concurrentClonesMatchSerialAndPreserveParentDerivatives(int model) throws Exception {
    SystemInterface source = createFluid(model);
    new TPflash(source).run();
    source.init(3);
    assertQualified(source);
    Map<String, Double> sourceState = snapshot(source);
    SystemInterface[] serial = {source.clone(), source.clone()};
    SystemInterface[] parallel = {source.clone(), source.clone()};
    assertState(sourceState, snapshot(source), 0.0);

    List<List<Map<String, Double>>> expected = new ArrayList<List<Map<String, Double>>>();
    for (int scenario = 0; scenario < serial.length; scenario++) {
      expected.add(solveSequence(serial[scenario], scenario));
      assertState(sourceState, snapshot(source), 0.0);
    }

    ExecutorService workers = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<List<Map<String, Double>>>> results = new ArrayList<Future<List<Map<String, Double>>>>();
      for (int scenario = 0; scenario < parallel.length; scenario++) {
        final int index = scenario;
        results.add(workers.submit(() -> {
          ready.countDown();
          start.await();
          return solveSequence(parallel[index], index);
        }));
      }
      ready.await();
      start.countDown();
      for (int scenario = 0; scenario < results.size(); scenario++) {
        List<Map<String, Double>> actual = results.get(scenario).get();
        assertEquals(expected.get(scenario).size(), actual.size());
        for (int step = 0; step < actual.size(); step++) {
          assertState(expected.get(scenario).get(step), actual.get(step), 1.0e-10);
        }
      }
      assertState(sourceState, snapshot(source), 0.0);
    } finally {
      start.countDown();
      workers.shutdownNow();
    }
  }

  /**
   * Creates a public two-phase hydrocarbon or associating-water fixture without shared configuration changes.
   *
   * @param model zero for SRK, one for PR, two for CPA
   * @return configured, unflashed fluid
   */
  private SystemInterface createFluid(int model) {
    SystemInterface fluid = model == 0 ? new SystemSrkEos(300.0, 50.0)
        : model == 1 ? new SystemPrEos(300.0, 50.0) : new SystemSrkCPAstatoil(298.15, 60.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent(model == 2 ? "water" : "n-decane", 0.1);
    fluid.setMixingRule(model == 2 ? 10 : 2);
    return fluid;
  }

  /**
   * Reuses one operation through three different temperature/pressure states on its privately owned fluid.
   *
   * @param fluid independent scenario fluid
   * @param scenario zero for the cooler/lower-pressure branch, one for the warmer/higher-pressure branch
   * @return complete state and derivative snapshots after each flash
   */
  private List<Map<String, Double>> solveSequence(SystemInterface fluid, int scenario) {
    double direction = scenario == 0 ? -1.0 : 1.0;
    double temperature = fluid.getTemperature();
    double pressure = fluid.getPressure();
    TPflash operation = new TPflash(fluid);
    List<Map<String, Double>> states = new ArrayList<Map<String, Double>>();
    for (int step = 0; step < 3; step++) {
      fluid.setTemperature(temperature + direction * (4.0 + step));
      fluid.setPressure(pressure + direction * (4.0 + 0.5 * step));
      operation.run();
      fluid.init(3);
      assertQualified(fluid);
      states.add(snapshot(fluid));
    }
    return states;
  }

  /**
   * Checks finite, normalized two-phase material and fugacity closure without altering the result.
   *
   * @param fluid flashed scenario fluid
   */
  private void assertQualified(SystemInterface fluid) {
    assertEquals(2, fluid.getNumberOfPhases());
    assertEquals(1.0, fluid.getBeta(0) + fluid.getBeta(1), 1.0e-12);
    for (int phase = 0; phase < 2; phase++) {
      assertTrue(fluid.getBeta(phase) > 0.0 && fluid.getBeta(phase) < 1.0);
      double sum = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        double fraction = fluid.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(fraction) && fraction > 0.0 && fraction <= 1.0);
        sum += fraction;
      }
      assertEquals(1.0, sum, 1.0e-10);
    }
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double first = fluid.getPhase(0).getComponent(component).getx();
      double second = fluid.getPhase(1).getComponent(component).getx();
      assertEquals(fluid.getComponent(component).getz(), fluid.getBeta(0) * first + fluid.getBeta(1) * second, 1.0e-10);
      double firstFugacity = Math.log(first) + fluid.getPhase(0).getComponent(component).getLogFugacityCoefficient();
      double secondFugacity = Math.log(second) + fluid.getPhase(1).getComponent(component).getLogFugacityCoefficient();
      assertTrue(Double.isFinite(firstFugacity) && Double.isFinite(secondFugacity));
      assertEquals(firstFugacity, secondFugacity, 1.0e-8);
    }
  }

  /**
   * Captures physical slots, configured roots, inventories, cached properties and all active derivative entries.
   *
   * @param fluid source or scenario fluid to inspect without initialization
   * @return detached named values, including inactive retained phase slots
   */
  private Map<String, Double> snapshot(SystemInterface fluid) {
    Map<String, Double> values = new LinkedHashMap<String, Double>();
    values.put("phaseCount", (double) fluid.getNumberOfPhases());
    values.put("totalMoles", fluid.getTotalNumberOfMoles());
    values.put("gibbs", fluid.getGibbsEnergy());
    values.put("enthalpy", fluid.getEnthalpy());
    values.put("entropy", fluid.getEntropy());
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      values.put("mapping." + phase, (double) fluid.getPhaseIndex(phase));
      values.put("root." + phase, (double) fluid.getPhaseType(phase).ordinal());
      values.put("beta." + phase, fluid.getBeta(phase));
    }
    PhaseInterface[] phases = fluid.getPhases();
    for (int slot = 0; slot < phases.length; slot++) {
      PhaseInterface phase = phases[slot];
      if (phase == null) {
        continue;
      }
      String prefix = "slot." + slot + ".";
      values.put(prefix + "type", (double) phase.getType().ordinal());
      values.put(prefix + "temperature", phase.getTemperature());
      values.put(prefix + "pressure", phase.getPressure());
      values.put(prefix + "moles", phase.getNumberOfMolesInPhase());
      values.put(prefix + "compressibility", phase.getZ());
      values.put(prefix + "cp", phase.getCp());
      values.put(prefix + "cv", phase.getCv());
      values.put(prefix + "ATT", phase.getATT());
      values.put(prefix + "dFdTdT", phase.dFdTdT());
      for (int component = 0; component < phase.getNumberOfComponents(); component++) {
        ComponentEosInterface entry = (ComponentEosInterface) phase.getComponent(component);
        String key = prefix + "component." + component + ".";
        values.put(key + "x", entry.getx());
        values.put(key + "z", entry.getz());
        values.put(key + "K", entry.getK());
        values.put(key + "moles", entry.getNumberOfmoles());
        values.put(key + "phaseMoles", entry.getNumberOfMolesInPhase());
        values.put(key + "logFugacityCoefficient", entry.getLogFugacityCoefficient());
        values.put(key + "dfugdt", entry.getdfugdt());
        values.put(key + "dfugdp", entry.getdfugdp());
        for (int other = 0; other < phase.getNumberOfComponents(); other++) {
          values.put(key + "dfugdn." + other, entry.getdfugdn(other));
          values.put(key + "dfugdx." + other, entry.getdfugdx(other));
          values.put(key + "Aij." + other, entry.getAij(other));
          values.put(key + "Bij." + other, entry.getBij(other));
          values.put(key + "dAdndn." + other, entry.getdAdndn(other));
          values.put(key + "dBdndn." + other, entry.getdBdndn(other));
        }
      }
    }
    return values;
  }

  /**
   * Compares snapshots with exact parent preservation or a relative tolerance for independent scenario calculations.
   *
   * @param expected reference values
   * @param actual observed values
   * @param tolerance relative/absolute tolerance, zero for unchanged source state
   */
  private void assertState(Map<String, Double> expected, Map<String, Double> actual, double tolerance) {
    assertEquals(expected.keySet(), actual.keySet());
    for (Map.Entry<String, Double> entry : expected.entrySet()) {
      double reference = entry.getValue();
      if (Double.isFinite(reference)) {
        assertEquals(reference, actual.get(entry.getKey()), tolerance * Math.max(1.0, Math.abs(reference)),
            entry.getKey());
      } else {
        assertEquals(entry.getValue(), actual.get(entry.getKey()), entry.getKey());
      }
    }
  }
}
