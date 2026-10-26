package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

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
    assertEquals(enthalpy, system.getEnthalpy(), Math.abs(enthalpy) * 1.0e-5);
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
    assertTrue(phFlash.getLastIterationCount() < 100,
        "PH flash stalled: " + phFlash.getLastIterationCount() + " iterations");
  }
}
