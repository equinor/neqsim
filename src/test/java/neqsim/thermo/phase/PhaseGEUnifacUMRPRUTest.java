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

  /**
   * Flash a binary mixture with UMR-PRU-MC and return the mole fraction of a component in the liquid phase.
   *
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @param solute component dissolving in the liquid
   * @param solvent liquid component
   * @return mole fraction of the solute in the liquid phase
   */
  private static double liquidSolubilityUmrPruMc(double temperature, double pressure, String solute, String solvent) {
    SystemInterface fluid = new SystemUMRPRUMCEos(temperature, pressure);
    fluid.addComponent(solute, 1.0);
    fluid.addComponent(solvent, 1.0);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    Assertions.assertTrue(fluid.hasPhaseType(PhaseType.GAS) && fluid.getNumberOfPhases() == 2);
    return fluid.getPhase(1).getComponent(solute).getx();
  }

  /**
   * UMR-PRU-MC treats TEG as one molecular group (Equinor parameter set). Built from CH2, OH and ether groups, ethane
   * was fully miscible with TEG (x = 0.5); SRK-CPA gives 0.061.
   */
  @Test
  void umrPruMcUsesTegMolecularGroup() {
    Assertions.assertEquals(0.061, liquidSolubilityUmrPruMc(298.15, 30.0, "ethane", "TEG"), 0.005);
  }

  /**
   * Equinor H2 group parameters: hydrogen solubility in n-hexane at 298 K and 100 bar is about 0.07 (SRK-CPA 0.073).
   * Without H2 interaction parameters it was 0.017.
   */
  @Test
  void umrPruMcHydrogenSolubilityInHexane() {
    Assertions.assertEquals(0.067, liquidSolubilityUmrPruMc(298.15, 100.0, "hydrogen", "n-hexane"), 0.005);
  }

  /**
   * Mathias-Copeman parameters of mesitylene reproduce its normal boiling point (437.9 K).
   *
   * @throws Exception if the bubble-point calculation fails
   */
  @Test
  void umrPruMcMesityleneNormalBoilingPoint() throws Exception {
    SystemInterface fluid = new SystemUMRPRUMCEos(400.0, 1.01325);
    fluid.addComponent("mesitylene", 1.0);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    new ThermodynamicOperations(fluid).bubblePointTemperatureFlash();
    Assertions.assertEquals(437.9, fluid.getTemperature(), 0.5);
  }
}
