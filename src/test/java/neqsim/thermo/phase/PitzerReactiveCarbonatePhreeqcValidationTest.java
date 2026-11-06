package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.junit.jupiter.api.Test;

/**
 * Frozen PHREEQC parity and reactive-closure checks for the opt-in NaCl-carbonate parameter mode.
 */
class PitzerReactiveCarbonatePhreeqcValidationTest {
  private static final double WATER_MOLES_PER_KILOGRAM = 1.0 / 0.01801528;
  private static final double[][] IPHREEQC_STATES = {
      {293.15, 0.70, 1.000007991942414, 1.0000079919424134, 0.00044371526667765426,
          0.22197706130109754, 0.0004437146269494226, 3.056706343278015e-10,
          2.8387065613841553e-11, 3.4087023069664832, 0.9624275460498084},
      {293.15, 1.51, 1.0000114142046428, 1.0000114142046423, 0.0006336724625228663,
          0.4525266951574403, 0.0006336718263045957, 3.0815251958983445e-10,
          1.9913349365601717e-11, 3.253913647971924, 0.9578115199891336},
      {293.15, 2.89, 1.0000150367465273, 1.0000150367465277, 0.0008347466811269072,
          0.7831078955896208, 0.0008347460412803007, 3.1233298917704605e-10,
          1.5180774637083134e-11, 3.134155241180846, 0.9512557009646957},
      {293.15, 4.26, 1.0000173495515148, 1.000017349551515, 0.0009631223535477857,
          1.0370556436014684, 0.000963121707378343, 3.1646891071912926e-10,
          1.3231547070097827e-11, 3.071948856343631, 0.946268058894257},
      {293.15, 5.58, 1.000018824999449, 1.0000188249994495, 0.001045019479925357,
          1.2119346582948616, 0.001045018826739464, 3.20454249128898e-10,
          1.2277360472342538e-11, 3.0364226402165095, 0.9428567588098865},
      {323.15, 1.18, 1.0000085641504077, 1.0000085641504077, 0.0004756929756002811,
          0.1834144426490555, 0.0004756917236043179, 5.127053667717165e-10,
          2.2658529400868806e-10, 3.403929199910811, 0.9629284852361328},
      {323.15, 2.65, 1.0000123660106959, 1.0000123660106959, 0.0006867215465684948,
          0.3845383859995111, 0.0006867203502337101, 5.191978375963761e-10,
          1.5793902043100859e-10, 3.244375937406335, 0.9589542971806378},
      {323.15, 3.79, 1.0000143601489087, 1.0000143601489084, 0.0007974099322328227,
          0.5198815158481394, 0.000797408747118185, 5.241905466540354e-10,
          1.3673371765954406e-10, 3.1793892337835112, 0.9563203795085683},
      {323.15, 5.08, 1.000016070548904, 1.000016070548904, 0.0008923492642109998,
          0.651690764549743, 0.0008923480815852999, 5.298272631242071e-10,
          1.2297120815545607e-10, 3.1304343218747794, 0.953785652445506},
      {323.15, 5.90, 1.000016941822941, 1.000016941822941, 0.0009407113470194089,
          0.7239008016773671, 0.0009407101630481798, 5.334089801209483e-10,
          1.1715304672927636e-10, 3.1074464364741137, 0.9524094985491139}};

  @Test
  void exactSourceSemanticsMatchFrozenIPhreeqcStates() {
    for (double[] state : IPHREEQC_STATES) {
      SystemPitzer system = frozenAqueousState(state);
      system.applyPhreeqcReactiveSodiumChlorideCarbonateParameters();
      system.init(0);
      system.init(1);

      PhasePitzer phase = (PhasePitzer) system.getGeLiquidPhase();
      assertEquals(state[9], pH(phase), 1.0e-3);
      assertEquals(state[10], waterActivity(phase), 3.0e-5);
      assertTrue(phase.getPitzerParameterCoverage().isComplete());
      assertTrue(phase.getPitzerReactionParameterCoverage().isComplete());
      assertTrue(phase.auditNeutralPitzerParameterCoverage().isComplete());
    }
  }

  @Test
  void coupledReactiveFlashMatchesIndependentReferenceAndClosesBalances() {
    SystemPitzer system = new SystemPitzer(293.15, 7.0);
    system.addComponent("water", 55.5084350618);
    system.addComponent("CO2", 1.0);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.chemicalReactionInit();
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    system.applyPhreeqcReactiveSodiumChlorideCarbonateParameters();

    new ThermodynamicOperations(system).TPflash();
    system.init(1);

    PhasePitzer phase = (PhasePitzer) system.getGeLiquidPhase();
    assertEquals(3.4087023069664832, pH(phase), 5.0e-3);
    assertEquals(0.22197706130109754, phase.getComponent("CO2").getMolality(phase), 3.0e-3);
    assertTrue(system.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 3.0e-6);
    assertTrue(system.getChemicalReactionOperations().getMaximumAbsoluteElementBalanceResidual() < 1.0e-9);
    assertTrue(system.getChemicalReactionOperations().getNormalizedReactivePhaseChargeResidual() < 1.0e-12);
  }

  @Test
  void automaticCatalogPolicyRemainsFailClosed() {
    SystemPitzer system = frozenAqueousState(IPHREEQC_STATES[0]);
    system.init(0);

    assertFalse(system.refreshDefaultPitzerParameterSelection());
    assertFalse(system.getMissingPhreeqcPitzerCatalogRows().isEmpty());
  }

  @Test
  void explicitModeRejectsUnsupportedSpecies() {
    SystemPitzer system = frozenAqueousState(IPHREEQC_STATES[0]);
    system.addComponent("K+", 1.0e-12);

    assertThrows(IllegalArgumentException.class,
        system::applyPhreeqcReactiveSodiumChlorideCarbonateParameters);
  }

  @Test
  void exactSourceDatasetSurvivesCloneAndRepeatedInitialization() {
    SystemPitzer system = frozenAqueousState(IPHREEQC_STATES[0]);
    system.applyPhreeqcReactiveSodiumChlorideCarbonateParameters();
    system.init(0);
    system.init(1);
    SystemPitzer cloned = system.clone();
    cloned.init(0);
    cloned.init(1);

    PhasePitzer originalPhase = (PhasePitzer) system.getGeLiquidPhase();
    PhasePitzer clonedPhase = (PhasePitzer) cloned.getGeLiquidPhase();
    assertEquals(PitzerParameterDatasets.PHREEQC_REACTIVE_NACL_CARBONATE_ID,
        clonedPhase.getParameterDatasetId());
    assertEquals(pH(originalPhase), pH(clonedPhase), 1.0e-12);
    assertEquals(waterActivity(originalPhase), waterActivity(clonedPhase), 1.0e-12);
  }

  private static SystemPitzer frozenAqueousState(double[] state) {
    SystemPitzer system = new SystemPitzer(state[0], state[1] * 10.0);
    system.addComponent("water", WATER_MOLES_PER_KILOGRAM);
    system.addComponent("Na+", state[2]);
    system.addComponent("Cl-", state[3]);
    system.addComponent("H3O+", state[4]);
    system.addComponent("CO2", state[5]);
    system.addComponent("HCO3-", state[6]);
    system.addComponent("CO3--", state[7]);
    system.addComponent("OH-", state[8]);
    system.setAllComponentsInPhase(1);
    return system;
  }

  private static double pH(PhasePitzer phase) {
    double hydrogenMolality = phase.getComponent("H3O+").getMolality(phase);
    double hydrogenActivityCoefficient =
        phase.getActivityCoefficient(phase.getComponent("H3O+").getComponentNumber());
    return -Math.log10(hydrogenMolality * hydrogenActivityCoefficient);
  }

  private static double waterActivity(PhasePitzer phase) {
    int water = phase.getComponent("water").getComponentNumber();
    return phase.getActivityCoefficient(water) * phase.getComponent(water).getx();
  }
}
