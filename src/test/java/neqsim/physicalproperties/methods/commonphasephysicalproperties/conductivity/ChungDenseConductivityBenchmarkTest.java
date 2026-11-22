package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.methods.methodinterface.ConductivityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Opt-in microbenchmark of the changed conductivity kernel; times are diagnostic, not CI acceptance thresholds.
 *
 * @author Even Solbraa
 * @version 1.0
 */
@Tag("benchmark")
public class ChungDenseConductivityBenchmarkTest {
  /** Logger for reproducible benchmark receipts. */
  private static final Logger logger = LogManager.getLogger(ChungDenseConductivityBenchmarkTest.class);

  /** Prevents dead-code elimination of repeated calculations. */
  private static volatile double sink;

  /**
   * Reports five measured batches after warmup for pure, binary and eight-component gases.
   *
   * <p>
   * A separately compiled historical class can be chosen with the neqsim.chungBenchmarkClass property. This keeps
   * historical source outside the feature tree while running the same harness on both implementations.
   *
   * @throws ReflectiveOperationException if a requested external benchmark class cannot be constructed
   */
  @Test
  void benchmarkCurrentKernel() throws ReflectiveOperationException {
    String[] names = {"methane", "ethane", "propane", "n-butane", "n-pentane", "nitrogen", "CO2", "hydrogen"};
    String className = System.getProperty("neqsim.chungBenchmarkClass", ChungDenseConductivityMethod.class.getName());
    for (int components : new int[] {1, 2, 8}) {
      SystemInterface fluid = new SystemSrkEos(400.0, 100.0);
      for (int i = 0; i < components; i++) {
        fluid.addComponent(names[i], 1.0 / components);
      }
      fluid.setMixingRule("classic");
      new ThermodynamicOperations(fluid).TPflash();
      fluid.init(3);
      ConductivityInterface method = (ConductivityInterface) Class.forName(className)
          .getConstructor(PhysicalProperties.class).newInstance(fluid.getPhase(0).getPhysicalProperties());
      for (int i = 0; i < 20000; i++) {
        sink = method.calcConductivity();
      }
      for (int batch = 0; batch < 5; batch++) {
        double sum = 0.0;
        long start = System.nanoTime();
        for (int i = 0; i < 50000; i++) {
          sum += method.calcConductivity();
        }
        long elapsed = System.nanoTime() - start;
        sink = sum;
        assertTrue(Double.isFinite(sum) && sum > 0.0);
        logger.info("Chung benchmark class={} components={} batch={} ns/call={} conductivity={}", className, components,
            batch, elapsed / 50000.0, sum / 50000.0);
      }
    }
  }
}
