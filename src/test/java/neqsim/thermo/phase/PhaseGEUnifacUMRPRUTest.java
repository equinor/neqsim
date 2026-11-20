package neqsim.thermo.phase;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRCPAEoS;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class PhaseGEUnifacUMRPRUTest {
  static PhaseGEUnifacUMRPRU p;

  @BeforeEach
  void setUp() {
    p = new PhaseGEUnifacUMRPRU();
  }

  @Test
  void testAddcomponent() {
    Assertions.assertEquals(0, p.getNumberOfComponents());

    p.addComponent("ethane", 0, 0, 0);
    Assertions.assertEquals(1, p.getNumberOfComponents());

    p.addComponent("methane", 0, 0, 1);
    Assertions.assertEquals(2, p.getNumberOfComponents());
  }

  /**
   * Flash methane with 2 mol% water at 25 C and 70 bara.
   *
   * @param fluid empty fluid of the model to test
   * @return water mole fraction in the gas and methane mole fraction in the aqueous phase
   */
  private static double[] methaneWaterSolubilities(SystemInterface fluid) {
    fluid.addComponent("methane", 0.98);
    fluid.addComponent("water", 0.02);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    Assertions.assertTrue(fluid.hasPhaseType(PhaseType.GAS) && fluid.hasPhaseType(PhaseType.AQUEOUS));
    return new double[] {fluid.getPhase(PhaseType.GAS).getComponent("water").getx(),
        fluid.getPhase(PhaseType.AQUEOUS).getComponent("methane").getx()};
  }

  /**
   * UMR-CPA reads its own group-interaction tables. With the water parameters of UMR-PRU-MC, which carry the hydrogen
   * bonding that the association term already describes, its water content of the gas would be 1.9e-4.
   */
  @Test
  void umrCpaKeepsItsOwnWaterParameters() {
    double[] solubility = methaneWaterSolubilities(new SystemUMRCPAEoS(298.15, 70.0));
    Assertions.assertEquals(6.128e-4, solubility[0], 6.0e-6);
    Assertions.assertEquals(1.387e-3, solubility[1], 1.4e-5);
  }

  /** UMR-PRU-MC uses the water parameters of the mercury calculator, fitted without an association term. */
  @Test
  void umrPruMcUsesWaterParametersFittedWithoutAssociation() {
    double[] solubility = methaneWaterSolubilities(new SystemUMRPRUMCEos(298.15, 70.0));
    Assertions.assertEquals(6.220e-4, solubility[0], 6.0e-6);
    Assertions.assertEquals(1.437e-3, solubility[1], 1.4e-5);
  }
}
