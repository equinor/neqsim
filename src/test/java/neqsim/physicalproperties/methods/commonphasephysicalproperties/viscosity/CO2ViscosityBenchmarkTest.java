package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.methods.methodinterface.ViscosityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Opt-in same-harness timing of the phase-bound CO2 viscosity calculation at fixed published density states.
 *
 * @author Even Solbraa
 * @version 1.0
 */
@Tag("benchmark")
public class CO2ViscosityBenchmarkTest {
  /** Logger for benchmark evidence. */
  private static final Logger logger = LogManager.getLogger(CO2ViscosityBenchmarkTest.class);
  /** Observable result to prevent elimination of the timed calculation. */
  private static volatile double sink;

  /**
   * Reports five measured batches per state after warmup; timings are diagnostic, not CI thresholds.
   *
   * @throws ReflectiveOperationException if an explicitly requested historical class cannot be constructed
   */
  @Test
  void benchmarkBackgroundCorrelation() throws ReflectiveOperationException {
    String className = System.getProperty("neqsim.co2BenchmarkClass", CO2ViscosityMethod.class.getName());
    double[][] states = {{220, 3}, {225, 1150}, {300, 65}, {300, 1400}, {700, 100}, {700, 1200}};
    for (double[] state : states) {
      SystemInterface fluid = new SystemSrkEos(state[0], 1.0);
      fluid.addComponent("CO2", 1.0);
      fluid.setMixingRule("classic");
      fluid.init(0);
      fluid.init(1);
      PhaseInterface phase = fluid.getPhase(0);
      phase.setMolarVolume(1e5 * phase.getMolarMass() / state[1]);
      ViscosityInterface method = (ViscosityInterface) Class.forName(className).getConstructor(PhysicalProperties.class)
          .newInstance(phase.getPhysicalProperties());
      for (int i = 0; i < 20000; i++) {
        sink = method.calcViscosity();
      }
      for (int batch = 0; batch < 5; batch++) {
        long start = System.nanoTime();
        double sum = 0.0;
        for (int i = 0; i < 200000; i++) {
          sum += method.calcViscosity();
        }
        long elapsed = System.nanoTime() - start;
        sink = sum;
        assertTrue(Double.isFinite(sum) && sum > 0.0);
        logger.info("CO2 benchmark class={} T={} rho={} batch={} ns/call={} viscosity={}", className, state[0],
            state[1], batch, elapsed / 200000.0, sum / 200000.0);
      }
    }
  }
}
