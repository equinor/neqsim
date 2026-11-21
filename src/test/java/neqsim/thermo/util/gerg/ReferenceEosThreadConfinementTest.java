package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for concurrent use of the GERG-2008 and EOS-CG wrappers.
 *
 * <p>
 * Both reference equations of state keep mutable temperature, composition and work state inside the kernel. Before
 * the kernels were confined to one thread, two independently owned wrappers evaluated on different threads could
 * overwrite each other's caches between the cache update and the Helmholtz-energy evaluation. The cases below are the
 * ones reported by Kirill Shevtsov: 280 K and 5 mol/L for a 90/5/5 mixture, raced against a second worker at a
 * different state and composition. With a shared kernel the residual Helmholtz energy of worker A was off by 84 %
 * (GERG-2008) and 64 % (EOS-CG). Every concurrent result must equal its serial reference bit for bit.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ReferenceEosThreadConfinementTest {
  /** Number of barrier-synchronised rounds per worker. */
  private static final int ROUNDS = 1000;

  /** Calls evaluated back to back in each round, so the workers overlap. */
  private static final int CALLS_PER_ROUND = 50;

  /** Reduced molar density used by both workers, mol/L. */
  private static final double DENSITY_A = 5.0;

  /** Molar density of the competing worker, mol/L. */
  private static final double DENSITY_B = 12.0;

  /** Temperature of worker A, K. */
  private static final double TEMPERATURE_A = 280.0;

  /** Temperature of the competing worker B, K. */
  private static final double TEMPERATURE_B = 340.0;

  /** Reset the shared holders so later tests start from a clean cache. */
  @AfterEach
  void clearCaches() {
    NeqSimGERG2008.clearCache();
    NeqSimEOSCG.clearCache();
  }

  /**
   * Two GERG-2008 wrappers raced on separate threads must reproduce their serial residual Helmholtz energies.
   *
   * @throws Exception if a worker fails
   */
  @Test
  void gergWorkersDoNotShareKernelState() throws Exception {
    final NeqSimGERG2008 workerA = new NeqSimGERG2008(phase(new String[] {"methane", "nitrogen", "CO2"},
        new double[] {0.90, 0.05, 0.05}));
    final NeqSimGERG2008 workerB = new NeqSimGERG2008(phase(new String[] {"ethane", "propane", "n-butane"},
        new double[] {0.50, 0.30, 0.20}));
    double referenceA = workerA.getResidualHelmholtzEnergy(TEMPERATURE_A, DENSITY_A);
    double referenceB = workerB.getResidualHelmholtzEnergy(TEMPERATURE_B, DENSITY_B);
    assertEquals(-0.21828987902261812, referenceA, 1e-12, "isolated GERG-2008 reference from the bug report");

    List<double[]> results = race(new ResidualEvaluator() {
      @Override
      public double evaluate() {
        return workerA.getResidualHelmholtzEnergy(TEMPERATURE_A, DENSITY_A);
      }
    }, new ResidualEvaluator() {
      @Override
      public double evaluate() {
        return workerB.getResidualHelmholtzEnergy(TEMPERATURE_B, DENSITY_B);
      }
    });
    assertAllEqual(referenceA, results.get(0), "GERG-2008 worker A");
    assertAllEqual(referenceB, results.get(1), "GERG-2008 worker B");
  }

  /**
   * Two EOS-CG wrappers raced on separate threads must reproduce their serial residual Helmholtz energies.
   *
   * @throws Exception if a worker fails
   */
  @Test
  void eoscgWorkersDoNotShareKernelState() throws Exception {
    final NeqSimEOSCG workerA = new NeqSimEOSCG(phase(new String[] {"CO2", "nitrogen", "methane"},
        new double[] {0.90, 0.05, 0.05}));
    final NeqSimEOSCG workerB = new NeqSimEOSCG(phase(new String[] {"methane", "ethane", "propane"},
        new double[] {0.70, 0.20, 0.10}));
    double referenceA = workerA.getResidualHelmholtzEnergy(TEMPERATURE_A, DENSITY_A);
    double referenceB = workerB.getResidualHelmholtzEnergy(TEMPERATURE_B, DENSITY_B);
    assertEquals(-0.57502685346774800, referenceA, 1e-12, "isolated EOS-CG reference from the bug report");

    List<double[]> results = race(new ResidualEvaluator() {
      @Override
      public double evaluate() {
        return workerA.getResidualHelmholtzEnergy(TEMPERATURE_A, DENSITY_A);
      }
    }, new ResidualEvaluator() {
      @Override
      public double evaluate() {
        return workerB.getResidualHelmholtzEnergy(TEMPERATURE_B, DENSITY_B);
      }
    });
    assertAllEqual(referenceA, results.get(0), "EOS-CG worker A");
    assertAllEqual(referenceB, results.get(1), "EOS-CG worker B");
  }

  /**
   * An existing wrapper keeps giving the same result after {@code clearCache()}; the next call on each thread builds a
   * fresh kernel.
   */
  @Test
  void clearCacheKeepsExistingWrappersUsable() {
    NeqSimGERG2008 gerg = new NeqSimGERG2008(phase(new String[] {"methane", "nitrogen", "CO2"},
        new double[] {0.90, 0.05, 0.05}));
    NeqSimEOSCG eoscg = new NeqSimEOSCG(phase(new String[] {"CO2", "nitrogen", "methane"},
        new double[] {0.90, 0.05, 0.05}));
    double gergBefore = gerg.getResidualHelmholtzEnergy(TEMPERATURE_A, DENSITY_A);
    double eoscgBefore = eoscg.getResidualHelmholtzEnergy(TEMPERATURE_A, DENSITY_A);
    NeqSimGERG2008.clearCache();
    NeqSimEOSCG.clearCache();
    assertEquals(gergBefore, gerg.getResidualHelmholtzEnergy(TEMPERATURE_A, DENSITY_A), 0.0);
    assertEquals(eoscgBefore, eoscg.getResidualHelmholtzEnergy(TEMPERATURE_A, DENSITY_A), 0.0);
  }

  /**
   * Build a gas phase with the given composition.
   *
   * @param names component names
   * @param moles mole numbers
   * @return the first phase of an initialised SRK system
   */
  private static PhaseInterface phase(String[] names, double[] moles) {
    SystemInterface system = new SystemSrkEos(TEMPERATURE_A, 50.0);
    for (int i = 0; i < names.length; i++) {
      system.addComponent(names[i], moles[i]);
    }
    system.setMixingRule("classic");
    system.init(0);
    return system.getPhase(0);
  }

  /** One residual Helmholtz-energy evaluation owned by a worker. */
  private interface ResidualEvaluator {
    /**
     * Evaluate the residual Helmholtz energy.
     *
     * @return dimensionless residual Helmholtz energy
     */
    double evaluate();
  }

  /**
   * Run two workers at the same time and collect every result.
   *
   * <p>
   * The workers meet at a barrier before each round and then evaluate a burst of calls, so their calculations overlap
   * on the CPU in every round.
   * </p>
   *
   * @param first first worker
   * @param second second worker
   * @return results in worker order
   * @throws Exception if a worker fails
   */
  private static List<double[]> race(ResidualEvaluator first, ResidualEvaluator second) throws Exception {
    final CyclicBarrier start = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<double[]> a = pool.submit(worker(first, start));
      Future<double[]> b = pool.submit(worker(second, start));
      List<double[]> results = new ArrayList<double[]>();
      results.add(a.get(120, TimeUnit.SECONDS));
      results.add(b.get(120, TimeUnit.SECONDS));
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * Wrap an evaluator as a barrier-synchronised worker.
   *
   * @param evaluator calculation to repeat
   * @param start barrier shared by both workers
   * @return worker task returning every evaluated value
   */
  private static Callable<double[]> worker(final ResidualEvaluator evaluator, final CyclicBarrier start) {
    return new Callable<double[]>() {
      @Override
      public double[] call() throws Exception {
        double[] values = new double[ROUNDS * CALLS_PER_ROUND];
        for (int round = 0; round < ROUNDS; round++) {
          start.await(30, TimeUnit.SECONDS);
          for (int call = 0; call < CALLS_PER_ROUND; call++) {
            values[round * CALLS_PER_ROUND + call] = evaluator.evaluate();
          }
        }
        return values;
      }
    };
  }

  /**
   * Assert that every round reproduced the serial reference exactly.
   *
   * @param expected serial reference
   * @param actual concurrent results
   * @param label worker label for the failure message
   */
  private static void assertAllEqual(double expected, double[] actual, String label) {
    for (int i = 0; i < actual.length; i++) {
      assertEquals(expected, actual[i], 0.0, label + " call " + i);
    }
  }
}
