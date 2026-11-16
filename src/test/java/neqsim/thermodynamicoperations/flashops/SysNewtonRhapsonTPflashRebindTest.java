package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * The second-order TP solver is cached on a {@link TPflash} and re-bound for every later flash of that instance. Its
 * Newton unknowns must then describe the current phase split, not the split of the flash it was created for.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class SysNewtonRhapsonTPflashRebindTest {
  /**
   * Build a two-phase gas condensate.
   *
   * @return fluid with classic mixing rule
   */
  private static SystemInterface condensate() {
    SystemInterface fluid = new SystemSrkEos(280.0, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-pentane", 0.03);
    fluid.addComponent("n-heptane", 0.03);
    fluid.addComponent("nC10", 0.02);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /** Re-binding to a state with a different phase split must not write the old split back onto the system. */
  @Test
  void reboundSolverStartsFromTheCurrentPhaseSplit() {
    SystemInterface fluid = condensate();
    new TPflash(fluid).run();
    fluid.init(3);
    assertEquals(2, fluid.getNumberOfPhases());
    double firstBeta = fluid.getBeta();
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, fluid.getPhase(0).getNumberOfComponents());

    fluid.setPressure(20.0);
    fluid.setTemperature(300.0);
    new TPflash(fluid).run();
    fluid.init(3);
    assertEquals(2, fluid.getNumberOfPhases());
    double currentBeta = fluid.getBeta();
    double currentMethaneInGas = fluid.getPhase(0).getComponent("methane").getx();
    assertTrue(Math.abs(currentBeta - firstBeta) > 1.0e-3, "test states must differ in phase split");

    solver.setSystem(fluid);
    // init() rebuilds the phase split from the Newton unknowns; it must reproduce the state the solver was bound to.
    solver.init();
    assertEquals(currentBeta, fluid.getBeta(), 1.0e-12);
    assertEquals(currentMethaneInGas, fluid.getPhase(0).getComponent("methane").getx(), 1.0e-12);
  }
}
