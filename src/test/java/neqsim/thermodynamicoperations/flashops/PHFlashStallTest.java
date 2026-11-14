package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * PH flash stall handling for CPA glycol / water / aromatic mixtures.
 *
 * <p>
 * The feed stage of a DRIZO TEG still column (rich TEG, water, BTEX solvent vapour at 1.35 bara) exposed a PH flash
 * that ran to the 200 iteration cap because the relative enthalpy residual never reached 1e-8, even when the iteration
 * was started at the exact solution. The stall detector must accept the solution in a handful of iterations without
 * changing the converged temperature.
 * </p>
 *
 * @author AHNY
 * @version 1.0
 */
class PHFlashStallTest {
  /**
   * Controlled residuals isolate the outer solver's stop condition from TP-flash numerical noise.
   *
   * @author NeqSim
   * @version 1.0
   */
  private static final class ScriptedResidualPHflash extends PHflash {
    private static final long serialVersionUID = 1000L;
    private final double[] relativeResiduals;
    private int completedTpFlashes;

    /**
     * Create a solver with one prescribed residual per completed inner flash.
     *
     * @param relativeResiduals initial residual followed by residuals after successive inner flashes
     */
    private ScriptedResidualPHflash(double[] relativeResiduals) {
      super(residualTestSystem(), 1.0, 0);
      this.relativeResiduals = relativeResiduals.clone();
      tpFlash = new TPflash(system) {
        private static final long serialVersionUID = 1000L;

        /** {@inheritDoc} */
        @Override
        public void run() {
          completedTpFlashes++;
        }
      };
    }

    /** {@inheritDoc} */
    @Override
    public double calcdQdT() {
      return relativeResiduals[Math.min(completedTpFlashes, relativeResiduals.length - 1)];
    }

    /** {@inheritDoc} */
    @Override
    public double calcdQdTT() {
      return -1.0e6;
    }
  }

  /**
   * Create a material system for controlled outer-solver tests.
   *
   * @return two-component cubic-EOS system
   */
  private static SystemInterface residualTestSystem() {
    SystemInterface system = new SystemSrkEos(300.0, 1.0);
    system.addComponent("methane", 0.8);
    system.addComponent("ethane", 0.2);
    system.setMixingRule(2);
    return system;
  }

  /** An acceptable residual plateau must activate the relaxed stop for either residual sign. */
  @Test
  void acceptableResidualPlateauStopsWithoutReachingIterationCap() {
    for (double residual : new double[] {5.0e-7, -5.0e-7}) {
      ScriptedResidualPHflash flash = new ScriptedResidualPHflash(new double[] {residual});

      flash.solveQ();

      assertEquals(residual, flash.calcdQdT(), 0.0);
      assertEquals(PHflash.STALL_ITERATIONS_WITHOUT_IMPROVEMENT + 1, flash.completedTpFlashes);
      assertTrue(flash.getLastIterationCount() < 200);
    }
  }

  /** A historical good residual must not accept a current residual outside the stall tolerance. */
  @Test
  void historicalGoodResidualDoesNotAcceptDeterioratedIterate() {
    double[] residuals = new double[22];
    Arrays.fill(residuals, 0.1);
    residuals[1] = 5.0e-7;
    residuals[20] = 0.0;
    residuals[21] = 0.0;
    ScriptedResidualPHflash flash = new ScriptedResidualPHflash(residuals);

    flash.solveQ();

    assertEquals(0.0, flash.calcdQdT(), 0.0, "The returned iterate must satisfy the enthalpy target");
    assertEquals(21, flash.completedTpFlashes, "The solver must continue until the sequence converges");
    assertTrue(flash.getLastIterationCount() < 200);
  }

  /**
   * Build the DRIZO still feed stage mixture.
   *
   * @return two phase CPA system at 1.35 bara
   */
  private static SystemInterface drizoFeedStageMixture() {
    SystemInterface system = new SystemSrkCPAstatoil(273.15 + 137.0, 1.35);
    system.addComponent("methane", 0.002);
    system.addComponent("CO2", 0.001);
    system.addComponent("water", 0.55);
    system.addComponent("TEG", 0.95);
    system.addComponent("benzene", 0.11);
    system.addComponent("toluene", 0.05);
    system.addComponent("m-Xylene", 0.01);
    system.addComponent("n-hexane", 0.02);
    system.addComponent("n-heptane", 0.01);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(false);
    return system;
  }

  @Test
  void phFlashStartedAtSolutionStopsQuickly() {
    SystemInterface system = drizoFeedStageMixture();
    TPflash tpFlash = new TPflash(system);
    tpFlash.run();
    system.init(2);
    double enthalpy = system.getEnthalpy();
    double temperature = system.getTemperature();
    assertEquals(2, system.getNumberOfPhases());

    PHflash phFlash = new PHflash(system, enthalpy, 0);
    phFlash.run();

    assertEquals(temperature, system.getTemperature(), 1.0e-2);
    assertEquals(enthalpy, system.getEnthalpy(), Math.abs(enthalpy) * PHflash.STALL_RELATIVE_ENTHALPY_TOLERANCE);
    assertTrue(phFlash.getLastIterationCount() < 60,
        "PH flash stalled: " + phFlash.getLastIterationCount() + " iterations");
  }

  @Test
  void phFlashFromRemoteStartConvergesToSameTemperature() {
    SystemInterface reference = drizoFeedStageMixture();
    new TPflash(reference).run();
    reference.init(2);
    double enthalpy = reference.getEnthalpy();
    double temperature = reference.getTemperature();

    SystemInterface system = drizoFeedStageMixture();
    system.setTemperature(273.15 + 100.0);
    PHflash phFlash = new PHflash(system, enthalpy, 0);
    phFlash.run();

    assertEquals(temperature, system.getTemperature(), 5.0e-2);
    assertEquals(enthalpy, system.getEnthalpy(), Math.abs(enthalpy) * PHflash.STALL_RELATIVE_ENTHALPY_TOLERANCE);
    assertTrue(phFlash.getLastIterationCount() < 100,
        "PH flash stalled: " + phFlash.getLastIterationCount() + " iterations");
  }
}
