package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.NeqSimTest;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionDataSource;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Reactive electrolyte CPA material balance and calcite screening regressions.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ReactiveElectrolyteCPAScaleTest extends NeqSimTest {
  private static final double WATER_MOLES = 55.508;
  private static final double MOLE_TOLERANCE = 1.0e-8;

  /**
   * Validate the conserved feed inventory and the equilibrium result through TP and PH flashes.
   *
   * @param multiphaseCheck whether additional phase stability analysis is enabled
   * @throws Exception if a flash or scale calculation fails
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void carbonateBrineConservesSpeciesAndShowsCalciteSupersaturation(boolean multiphaseCheck) throws Exception {
    SystemElectrolyteCPAstatoil fluid = reactiveBrine(true, multiphaseCheck);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();
    assertBalanced(fluid);

    // PHREEQC 3.7.3 pitzer.dat, fixed T/P, matching dissolved strong-ion and inorganic-carbon totals.
    // The tolerances compare different aqueous activity models.
    assertCarbonateEquilibrium(fluid, operations, 5.709, 1.215);
    double enthalpy = fluid.getEnthalpy();

    SystemElectrolyteCPAstatoil intermediate = fluid.clone();
    intermediate.setPressure(15.0);
    ThermodynamicOperations intermediateOperations = new ThermodynamicOperations(intermediate);
    intermediateOperations.PHflash(enthalpy);
    intermediate.initProperties();
    assertBalanced(intermediate);
    assertEquals(enthalpy, intermediate.getEnthalpy(), 0.001);
    assertCarbonateEquilibrium(intermediate, intermediateOperations, 6.118, 2.917);

    fluid.setPressure(3.0);
    operations.PHflash(enthalpy);
    fluid.initProperties();
    assertBalanced(fluid);
    assertEquals(enthalpy, fluid.getEnthalpy(), 0.001);
    assertTrue(fluid.getTemperature() > 313.15 && fluid.getTemperature() < 316.15);
    assertCarbonateEquilibrium(fluid, operations, 6.784, 11.700);

    operations.TPflash();
    fluid.initProperties();
    assertBalanced(fluid);
    assertCarbonateEquilibrium(fluid, operations, 6.784, 11.700);
  }

  /** Verify that the legacy reaction data retains the established flash route. */
  @Test
  void legacyCarbonateDataRetainsItsFlashRoute() {
    SystemElectrolyteCPAstatoil fluid = reactiveBrine(false, true);
    assertEquals(ChemicalReactionDataSource.STANDARD, fluid.getChemicalReactionDataSource());
    assertFalse(TPmultiflash.supportsCoupledCarbonateFlash(fluid));
  }

  /** Require the reaction data to be rebuilt if the activity basis is changed on an initialized fluid. */
  @Test
  void changingBasisRequiresReactionReinitialization() {
    SystemElectrolyteCPAstatoil legacy = reactiveBrine(false, false);
    SystemElectrolyteCPAstatoil copy = legacy.clone();
    copy.setUseMolalCarbonateChemistry(true);
    assertThrows(IllegalStateException.class, copy::isChemicalSystem);
    assertTrue(legacy.isChemicalSystem());

    copy.chemicalReactionInit();
    copy.createDatabase(true);
    copy.setMixingRule(10);
    new ThermodynamicOperations(copy).TPflash();
    copy.initProperties();
    assertEquals(ChemicalReactionDataSource.MOLAL_CARBONATE,
        copy.getChemicalReactionOperations().getReactionList().getReactionDataSource());
    assertBalanced(copy);
  }

  /**
   * Validate molal pH reporting and the independently benchmarked calcite saturation classification.
   *
   * @param fluid solved carbonate fluid
   * @param operations flash and scale operations for that fluid
   * @param referencePH PHREEQC pH on the molality basis
   * @param referenceSR PHREEQC calcite saturation ratio
   * @throws Exception if scale screening fails
   */
  private static void assertCarbonateEquilibrium(SystemElectrolyteCPAstatoil fluid, ThermodynamicOperations operations,
      double referencePH, double referenceSR) throws Exception {
    PhaseInterface aqueous = fluid.getPhase("aqueous");
    double sr = operations.getRelativeScalePotential("CaCO3");
    assertTrue(sr > 1.0);
    assertEquals(Math.log10(referenceSR), Math.log10(sr), 0.31);
    assertEquals(referencePH, aqueous.getpH(), 0.30);
    assertEquals(aqueous.getpH(), aqueous.getpH("molality"), 1.0e-12);
    assertTrue(fluid.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 1.0e-5);
  }

  /**
   * Check inert species, reactive C/H/O elements, and charge across all outlet phases.
   *
   * @param fluid completed gas and aqueous flash
   */
  private static void assertBalanced(SystemInterface fluid) {
    assertEquals(2, fluid.getNumberOfPhases());
    assertEquals(1.0, totalAcrossPhases(fluid, "Na+"), MOLE_TOLERANCE);
    assertEquals(1.01, totalAcrossPhases(fluid, "Cl-"), MOLE_TOLERANCE);
    assertEquals(0.020, totalAcrossPhases(fluid, "Ca++"), MOLE_TOLERANCE);
    double methane = totalAcrossPhases(fluid, "methane");
    assertEquals(20.0, methane, MOLE_TOLERANCE);
    double dissolvedAndGasCO2 = totalAcrossPhases(fluid, "CO2");
    double bicarbonate = totalAcrossPhases(fluid, "HCO3-");
    double carbonate = totalAcrossPhases(fluid, "CO3--");
    double water = totalAcrossPhases(fluid, "water");
    double hydronium = totalAcrossPhases(fluid, "H3O+");
    double hydroxide = totalAcrossPhases(fluid, "OH-");
    assertEquals(2.030, dissolvedAndGasCO2 + bicarbonate + carbonate, MOLE_TOLERANCE);
    assertEquals(2.0 * WATER_MOLES + 0.030, 2.0 * water + bicarbonate + 3.0 * hydronium + hydroxide, MOLE_TOLERANCE);
    assertEquals(WATER_MOLES + 4.0 + 0.090,
        water + 2.0 * dissolvedAndGasCO2 + 3.0 * (bicarbonate + carbonate) + hydronium + hydroxide, MOLE_TOLERANCE);

    PhaseInterface aqueous = fluid.getPhase("aqueous");
    double charge = 0.0;
    for (int componentIndex = 0; componentIndex < aqueous.getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = aqueous.getComponent(componentIndex);
      charge += component.getIonicCharge() * component.getNumberOfMolesInPhase();
    }
    assertEquals(0.0, charge, MOLE_TOLERANCE);
    assertTrue(fluid.getPhase("gas").getComponent("Na+").getNumberOfMolesInPhase() < 1.0e-12);
  }

  /**
   * Sum one species over active phases.
   *
   * @param fluid thermodynamic system
   * @param name component name
   * @return moles across all active phases
   */
  private static double totalAcrossPhases(SystemInterface fluid, String name) {
    double amount = 0.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      amount += fluid.getPhase(phaseIndex).getComponent(name).getNumberOfMolesInPhase();
    }
    return amount;
  }

  /**
   * Prepare the charge-balanced synthetic CO2-methane brine that exposed the balance and calcite discrepancies.
   *
   * @param molalChemistry whether to select PHREEQC-derived carbonate reaction constants
   * @param multiphaseCheck whether to search for additional phases
   * @return fluid ready for a flash
   */
  private static SystemElectrolyteCPAstatoil reactiveBrine(boolean molalChemistry, boolean multiphaseCheck) {
    SystemElectrolyteCPAstatoil fluid = new SystemElectrolyteCPAstatoil(323.15, 44.0);
    fluid.setUseMolalCarbonateChemistry(molalChemistry);
    fluid.addComponent("water", WATER_MOLES);
    fluid.addComponent("Na+", 1.0);
    fluid.addComponent("Cl-", 1.01);
    fluid.addComponent("Ca++", 0.020);
    fluid.addComponent("HCO3-", 0.030);
    fluid.addComponent("CO2", 2.0);
    fluid.addComponent("methane", 20.0);
    fluid.chemicalReactionInit();
    fluid.createDatabase(true);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(multiphaseCheck);
    return fluid;
  }
}
