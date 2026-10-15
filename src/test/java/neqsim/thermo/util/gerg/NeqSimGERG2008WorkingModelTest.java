package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.netlib.util.doubleW;
import neqsim.thermo.phase.PhaseGERG2008Eos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemGERG2008Eos;

/**
 * Regression coverage for ownership of native GERG workspaces and native residual curvature.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class NeqSimGERG2008WorkingModelTest {
  /**
   * Creates a gas reference phase with an independently selected composition and model variant.
   *
   * @param index fixture index
   * @return initialized gas phase
   */
  private static PhaseGERG2008Eos phase(int index) {
    SystemGERG2008Eos system = new SystemGERG2008Eos(260.0 + 40.0 * index, 20.0 + 40.0 * index);
    system.setGergModelType(index < 2 ? GERG2008Type.STANDARD
        : index == 2 ? GERG2008Type.HYDROGEN_ENHANCED : GERG2008Type.AMMONIA_EXTENDED);
    system.addComponent("methane", 0.8 - 0.1 * index);
    system.addComponent("nitrogen", 0.1 + 0.05 * index);
    system.addComponent("hydrogen", 0.1 + 0.05 * index);
    system.init(0);
    system.setNumberOfPhases(1);
    system.setBeta(0, 1.0);
    system.setPhaseType(0, PhaseType.GAS);
    system.init(3);
    return (PhaseGERG2008Eos) system.getPhase(0);
  }

  /**
   * Creates an independent native reference model, never shared by concurrent evaluations.
   *
   * @param type native model variant
   * @return initialized reference model
   */
  private static GERG2008 reference(GERG2008Type type) {
    GERG2008 model = type == GERG2008Type.HYDROGEN_ENHANCED ? new GERG2008H2()
        : type == GERG2008Type.AMMONIA_EXTENDED ? new GERG2008NH3() : new GERG2008();
    model.SetupGERG();
    return model;
  }

  /**
   * Evaluates native pressure and its density derivative without a wrapper cache.
   *
   * @param model independent native model
   * @param temperature temperature in K
   * @param density molar density in mol/L
   * @param composition native component fractions
   * @return pressure and derivative in native units
   */
  private static double[] nativeValues(GERG2008 model, double temperature, double density, double[] composition) {
    doubleW pressure = new doubleW(0.0);
    doubleW z = new doubleW(0.0);
    model.PressureGERG(temperature, density, composition, pressure, z);
    return new double[] {pressure.val, model.dPdDsave};
  }

  /**
   * Same-thread reuse must coexist with distinct cross-thread scratch and global invalidation.
   *
   * @throws Exception if model lookup or worker execution fails
   */
  @Test
  void workingModelsAreThreadOwnedAndClearCacheInvalidatesEveryThread() throws Exception {
    Method resolve = NeqSimGERG2008.class.getDeclaredMethod("getCachedModel", GERG2008Type.class);
    resolve.setAccessible(true);
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try {
      for (GERG2008Type type : GERG2008Type.values()) {
        Object callingThread = resolve.invoke(null, type);
        assertSame(callingThread, resolve.invoke(null, type));
        Object otherThread = worker.submit(() -> resolve.invoke(null, type)).get(30, TimeUnit.SECONDS);
        assertNotSame(callingThread, otherThread, "Native GERG temperature/composition scratch must be thread owned");
        assertSame(otherThread, worker.submit(() -> resolve.invoke(null, type)).get(30, TimeUnit.SECONDS));
        NeqSimGERG2008.clearCache();
        assertNotSame(callingThread, resolve.invoke(null, type));
        assertNotSame(otherThread, worker.submit(() -> resolve.invoke(null, type)).get(30, TimeUnit.SECONDS));
      }
    } finally {
      worker.shutdownNow();
    }
  }

  /**
   * Independent phases and a read-only shared wrapper must match serial native results under concurrency.
   *
   * @throws Exception if concurrent evaluation fails
   */
  @Test
  void concurrentPressureAndSlopeRetainNativeResultsDuringCacheInvalidation() throws Exception {
    int count = 4;
    PhaseGERG2008Eos[] phases = new PhaseGERG2008Eos[count];
    NeqSimGERG2008[] utilities = new NeqSimGERG2008[count];
    double[][] expected = new double[count][];
    double[][] sharedExpected = new double[count][];
    for (int i = 0; i < count; i++) {
      phases[i] = phase(i);
      utilities[i] = new NeqSimGERG2008(phases[i], phases[i].getGergModelType());
      expected[i] = nativeValues(reference(utilities[i].getModelType()), phases[i].getTemperature(),
          100.0 / phases[i].getMolarVolume(), utilities[i].normalizedGERGComposition);
    }
    NeqSimGERG2008 shared = utilities[0];
    for (int i = 0; i < count; i++) {
      sharedExpected[i] = nativeValues(reference(shared.getModelType()), 250.0 + i * 50.0, 1.0 + i,
          shared.normalizedGERGComposition);
    }
    CyclicBarrier barrier = new CyclicBarrier(count);
    ExecutorService pool = Executors.newFixedThreadPool(count);
    List<Callable<Void>> tasks = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      final int index = i;
      tasks.add(() -> {
        PhaseGERG2008Eos current = phases[index];
        double density = 100.0 / current.getMolarVolume();
        double expectedSlope = -expected[index][1] * density / (100.0 * current.getTotalVolume());
        for (int iteration = 0; iteration < 300; iteration++) {
          barrier.await(30, TimeUnit.SECONDS);
          if (index == 0 && iteration % 30 == 0) {
            NeqSimGERG2008.clearCache();
          }
          assertEquals(expected[index][0], utilities[index].pressureFromMolarDensity(current.getTemperature(), density),
              Math.abs(expected[index][0]) * 1e-12);
          assertEquals(expected[index][1],
              utilities[index].pressureDerivativeMolarDensity(current.getTemperature(), density),
              Math.abs(expected[index][1]) * 1e-12);
          assertEquals(expected[index][0] / 100.0, current.calcPressure(), Math.abs(expected[index][0]) * 1e-14);
          assertEquals(expectedSlope, current.calcPressuredV(), Math.abs(expectedSlope) * 1e-12);
          assertEquals(sharedExpected[index][0], shared.pressureFromMolarDensity(250.0 + index * 50.0, 1.0 + index),
              Math.abs(sharedExpected[index][0]) * 1e-12);
          assertEquals(sharedExpected[index][1],
              shared.pressureDerivativeMolarDensity(250.0 + index * 50.0, 1.0 + index),
              Math.abs(sharedExpected[index][1]) * 1e-12);
          if (iteration % 10 == 0) {
            assertEquals(expected[index][0], utilities[index].propertiesGERG()[0], Math.abs(expected[index][0]) * 1e-9);
          }
        }
        return null;
      });
    }
    try {
      for (Future<Void> result : pool.invokeAll(tasks, 60, TimeUnit.SECONDS)) {
        result.get();
      }
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * Evaluates native residual Helmholtz derivatives at the actual stored volume.
   *
   * @param model independent native model
   * @param temperature temperature in K
   * @param volume total internal volume
   * @param amount total moles
   * @return native residual derivatives
   */
  private static doubleW[][] nativeResidual(GERG2008 model, double temperature, double volume, double amount) {
    doubleW[][] residual = new doubleW[4][4];
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        residual[i][j] = new doubleW(0.0);
      }
    }
    double[] composition = new double[23];
    composition[1] = 1.0;
    model.AlpharGERG(2, 3, temperature, 100.0 * amount / volume, composition, residual);
    return residual;
  }

  /** Native residual curvature excludes the ideal gas and uses the extensive volume coordinate. */
  @Test
  void residualVolumeCurvatureMatchesNativeHelmholtzFiniteDifferences() {
    GERG2008 model = reference(GERG2008Type.STANDARD);
    for (double amount : new double[] {1e-6, 1.0, 1e6}) {
      for (double[] state : new double[][] {{300.0, 100.0}, {120.0, 10.0}, {300.0, 0.01}}) {
        SystemGERG2008Eos system = new SystemGERG2008Eos(state[0], state[1]);
        system.addComponent("methane", amount);
        system.init(0);
        system.setNumberOfPhases(1);
        system.setBeta(0, 1.0);
        system.setPhaseType(0, state[0] == 120.0 ? PhaseType.LIQUID : PhaseType.GAS);
        system.init(3);
        PhaseGERG2008Eos phase = (PhaseGERG2008Eos) system.getPhase(0);
        for (double scale : new double[] {1.0, 1.01}) {
          phase.setMolarVolume(phase.getMolarVolume() * scale);
          double volume = phase.getTotalVolume();
          doubleW[][] residual = nativeResidual(model, state[0], volume, amount);
          double analytic = amount * (2.0 * residual[0][1].val + residual[0][2].val) / (volume * volume);
          assertEquals(analytic, phase.dFdVdV(), Math.abs(analytic) * 1e-8,
              "Native residual curvature must subtract the ideal n/V^2 term");
          double step = volume * 2e-4;
          double plus = amount * nativeResidual(model, state[0], volume + step, amount)[0][0].val;
          double minus = amount * nativeResidual(model, state[0], volume - step, amount)[0][0].val;
          double finiteDifference = (plus - 2.0 * amount * residual[0][0].val + minus) / (step * step);
          assertEquals(finiteDifference, phase.dFdVdV(), Math.abs(analytic) * 3e-6,
              "Curvature must differentiate n*alphaR at constant temperature and moles");
        }
      }
    }
  }
}
