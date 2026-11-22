package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/**
 * Tests for the generic dual-root ionic aqueous phase search.
 *
 * @author OpenAI
 * @version 1.0
 */
class IonicAqueousPhaseEquilibriumTest {
  /** Verifies the independently reproduced dense-NaCl missing-aqueous state. */
  @Test
  void recoversDenseNaClOilAqueousSplit() {
    double naclMoles = 22.0 / 78.0 / 0.05844277;
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(323.15, 572.41);
    fluid.addComponent("CO2", 10.0);
    fluid.addComponent("water", 1.0 / 0.01801528);
    fluid.addComponent("Na+", naclMoles);
    fluid.addComponent("Cl-", naclMoles);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    assertTrue(IonicAqueousPhaseEquilibrium.isApplicable(fluid));
    new IonicAqueousPhaseEquilibrium(fluid).run();
    fluid.init(1);

    assertEquals(2, fluid.getNumberOfPhases());
    assertEquals(PhaseType.OIL, fluid.getPhase(0).getType());
    assertEquals(PhaseType.AQUEOUS, fluid.getPhase(1).getType());
  }
}
