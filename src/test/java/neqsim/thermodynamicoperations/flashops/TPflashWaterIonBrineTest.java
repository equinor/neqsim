package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression coverage for the stable aqueous endpoint of water-and-ion CPA brines.
 *
 * @author ESOL
 * @version 1.0
 */
class TPflashWaterIonBrineTest {
  /**
   * Creates the charged calcium-sulphate feed used by the precipitation regression.
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara absolute pressure in bara
   * @return initialized reactive brine
   */
  private SystemInterface createBrine(double temperatureK, double pressureBara) {
    SystemInterface brine = new SystemElectrolyteCPAstatoil(temperatureK, pressureBara);
    brine.addComponent("water", 55.508);
    brine.addComponent("Na+", 1.0);
    brine.addComponent("Ca++", 0.2);
    brine.addComponent("Cl-", 1.0);
    brine.addComponent("SO4--", 0.2);
    brine.chemicalReactionInit();
    brine.createDatabase(true);
    brine.setMixingRule(10);
    brine.setMultiPhaseCheck(true);
    return brine;
  }

  /**
   * Requires normalized phase fractions, spectator inventories, and closed chemical equilibrium.
   *
   * @param brine flashed brine
   * @param calciumMoles expected overall calcium and sulphate inventory in mol
   */
  private void assertAqueousEndpoint(SystemInterface brine, double calciumMoles) {
    assertEquals(1, brine.getNumberOfPhases());
    assertEquals(PhaseType.AQUEOUS, brine.getPhase(0).getType());
    assertEquals(1.0, brine.getBeta(0), 1.0e-12);
    assertFalse(brine.isForcePhaseTypes());
    assertTrue(brine.getMaxNumberOfPhases() > 1);
    assertTrue(brine.doMultiPhaseCheck());
    PhaseInterface aqueous = brine.getPhase(0);
    double compositionSum = 0.0;
    double chargeMoles = 0.0;
    for (int index = 0; index < aqueous.getNumberOfComponents(); index++) {
      ComponentInterface component = aqueous.getComponent(index);
      assertTrue(Double.isFinite(component.getx()) && component.getx() >= 0.0 && component.getx() <= 1.0);
      compositionSum += component.getx();
      chargeMoles += component.getIonicCharge() * component.getNumberOfMolesInPhase();
    }
    double waterMoles = aqueous.getComponent("water").getNumberOfMolesInPhase();
    double hydroxideMoles = aqueous.getComponent("OH-").getNumberOfMolesInPhase();
    double hydroniumMoles = aqueous.getComponent("H3O+").getNumberOfMolesInPhase();
    assertEquals(2.0 * 55.508, 2.0 * waterMoles + hydroxideMoles + 3.0 * hydroniumMoles, 1.0e-8);
    assertEquals(55.508, waterMoles + hydroxideMoles + hydroniumMoles, 1.0e-8);
    assertEquals(1.0, compositionSum, 1.0e-12);
    assertEquals(0.0, chargeMoles, 1.0e-8);
    assertEquals(1.0, aqueous.getComponent("Na+").getNumberOfMolesInPhase(), 1.0e-10);
    assertEquals(1.0, aqueous.getComponent("Cl-").getNumberOfMolesInPhase(), 1.0e-10);
    assertEquals(calciumMoles, aqueous.getComponent("Ca++").getNumberOfMolesInPhase(), 1.0e-10);
    assertEquals(calciumMoles, aqueous.getComponent("SO4--").getNumberOfMolesInPhase(), 1.0e-10);
    assertTrue(brine.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() <= 2.0e-6);
    assertTrue(brine.getChemicalReactionOperations().getMaximumAbsoluteElementBalanceResidual() <= 1.0e-8);
  }

  /**
   * Repeated flashes after salt removal must not lose the aqueous phase or create ion inventory.
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara absolute pressure in bara
   */
  @ParameterizedTest
  @CsvSource({"298.15, 1.01325", "303.15, 20.0", "323.15, 100.0"})
  void clonedBrineRetainsItsInventoryAcrossSaltRemoval(double temperatureK, double pressureBara) {
    SystemInterface brine = createBrine(temperatureK, pressureBara);
    new ThermodynamicOperations(brine).TPflash();
    double initialCalcium = brine.getComponent("Ca++").getNumberOfmoles();
    SystemInterface trial = brine.clone();
    double removal = initialCalcium * 0.5 * (1.0 - 1.0e-12);
    trial.addComponent("Ca++", -removal);
    trial.addComponent("SO4--", -removal);
    for (int repetition = 0; repetition < 3; repetition++) {
      new ThermodynamicOperations(trial).TPflash();
      assertAqueousEndpoint(trial, initialCalcium - removal);
    }
    assertAqueousEndpoint(brine, initialCalcium);
  }

  /** The liquid endpoint must be rejected when the water vapor is more stable. */
  @Test
  void boilingBrineRetainsTheGeneralPhaseSearch() {
    SystemInterface brine = createBrine(450.0, 1.01325);
    assertFalse(new TPflash(brine).flashStableWaterIonBrine());
    assertEquals(0.2, brine.getComponent("Ca++").getNumberOfmoles(), 0.0);
    assertEquals(55.508, brine.getComponent("water").getNumberOfmoles(), 0.0);
    assertFalse(brine.isForcePhaseTypes());
    assertTrue(brine.getMaxNumberOfPhases() > 1);
  }

  /** Even a positive trace of a volatile neutral solute must retain the general phase search. */
  @Test
  void positiveNeutralTraceRetainsTheGeneralPhaseSearch() {
    SystemInterface brine = createBrine(298.15, 1.01325);
    brine.addComponent("methane", 1.0e-80);
    brine.chemicalReactionInit();
    brine.createDatabase(true);
    brine.setMixingRule(10);
    assertFalse(new TPflash(brine).flashStableWaterIonBrine());
    assertEquals(1.0e-80, brine.getComponent("methane").getNumberOfmoles(), 0.0);
  }
}
