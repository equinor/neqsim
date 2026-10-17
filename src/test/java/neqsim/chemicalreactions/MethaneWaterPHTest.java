package neqsim.chemicalreactions;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test pH calculation for methane-water system at 50 bara and 20°C.
 */
@Tag("slow")
public class MethaneWaterPHTest {
  /** Verifies a methane-water flash retains both phases and reports a finite equilibrium pH. */
  @Test
  public void testMethaneWaterPH() {
    SystemInterface system = new SystemElectrolyteCPAstatoil(293.15, 50.0);
    system.addComponent("methane", 1.0);
    system.addComponent("water", 10.0);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);
    system.init(0);

    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();

    assertTrue(system.hasPhaseType("gas"), "Methane-rich gas must remain present");
    assertTrue(system.hasPhaseType("aqueous"), "Water-rich aqueous phase must remain present");
    double aqueousPH = system.getPhase("aqueous").getpH();
    assertTrue(Double.isFinite(aqueousPH), "Aqueous pH must be finite");
    assertTrue(system.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() <= 2.0e-6,
        "The reported aqueous pH must come from a converged chemical equilibrium");
  }
}
