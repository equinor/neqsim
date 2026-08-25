package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Scientific and process-composability tests for activity-consistent salt precipitation. */
class SaltPrecipitationTest extends neqsim.NeqSimTest {

  @Test
  void supersaturatedAnhydritePrecipitatesToComplementarityAndClosesMaterialLedger() throws Exception {
    SystemPitzer system = createCalciumSulphateBrine(false, 2.0e-1);
    ThermodynamicOperations operations = new ThermodynamicOperations(system);

    double initialCalciumMoles = system.getComponent("Ca++").getNumberOfmoles();
    double initialSulphateMoles = system.getComponent("SO4--").getNumberOfmoles();
    SaltPrecipitationResult result = operations.precipitateScale("CaSO4_A");

    assertTrue(result.hasPrecipitatedSolid());
    assertTrue(result.getInitialSaturationRatio() > 1.0);
    assertEquals(1.0, result.getFinalSaturationRatio(), 1.0e-6);
    assertTrue(result.getComplementarityViolation() <= 1.0e-6);
    assertTrue(result.getMaximumIonBalanceResidualMoles() <= 1.0e-10);
    assertEquals(initialCalciumMoles, system.getComponent("Ca++").getNumberOfmoles() + result.getPrecipitatedMoles(),
        1.0e-10);
    assertEquals(initialSulphateMoles, system.getComponent("SO4--").getNumberOfmoles() + result.getPrecipitatedMoles(),
        1.0e-10);
    assertEquals(result.getPrecipitatedMoles() * 136.14, result.getPrecipitatedMassGrams(),
        result.getPrecipitatedMassGrams() * 2.0e-4);
    assertAqueousChargeAndPhaseState(system);

    SaltPrecipitationResult repeated = operations.precipitateScale("CaSO4_A");
    assertFalse(repeated.hasPrecipitatedSolid());
    assertTrue(repeated.getFinalSaturationRatio() <= 1.0 + 1.0e-6);

    system.addComponent("Ca++", 2.0e-2);
    system.addComponent("SO4--", 2.0e-2);
    system.init(0);
    SaltPrecipitationResult changedState = operations.precipitateScale("CaSO4_A");
    assertTrue(changedState.hasPrecipitatedSolid());
    assertEquals(1.0, changedState.getFinalSaturationRatio(), 1.0e-6);
    assertTrue(changedState.getMaximumIonBalanceResidualMoles() <= 1.0e-10);
  }

  @Test
  void undersaturatedStateSatisfiesAbsentSolidInequality() {
    SystemPitzer system = createCalciumSulphateBrine(false, 1.0e-4);
    SaltPrecipitationResult result = new ThermodynamicOperations(system).precipitateScale("CaSO4_A");

    assertFalse(result.hasPrecipitatedSolid());
    assertTrue(result.getFinalSaturationRatio() < 1.0);
    assertEquals(0.0, result.getComplementarityViolation(), 0.0);
    assertEquals(0.0, result.getMaximumIonBalanceResidualMoles(), 0.0);
    assertAqueousChargeAndPhaseState(system);
  }

  @Test
  void gasOilAqueousPitzerPostStateRetainsRolesPropertiesAndIonConfinement() throws Exception {
    SystemPitzer system = createCalciumSulphateBrine(true, 2.0e-1);
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();
    system.initProperties();
    double methaneMoles = system.getComponent("methane").getNumberOfmoles();
    double heptaneMoles = system.getComponent("n-heptane").getNumberOfmoles();

    SaltPrecipitationResult result = operations.precipitateScale("CaSO4_A");

    assertTrue(result.hasPrecipitatedSolid());
    assertTrue(system.hasPhaseType("gas"));
    assertTrue(system.hasPhaseType("oil"));
    assertTrue(system.hasPhaseType("aqueous"));
    assertEquals(methaneMoles, system.getComponent("methane").getNumberOfmoles(), 1.0e-12);
    assertEquals(heptaneMoles, system.getComponent("n-heptane").getNumberOfmoles(), 1.0e-12);
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = system.getPhase(phaseIndex);
      assertTrue(Double.isFinite(phase.getDensity()) && phase.getDensity() > 0.0);
      assertTrue(Double.isFinite(phase.getEnthalpy()));
      assertTrue(Double.isFinite(phase.getCp()) && phase.getCp() > 0.0);
      for (int componentIndex = 0; componentIndex < phase.getNumberOfComponents(); componentIndex++) {
        ComponentInterface component = phase.getComponent(componentIndex);
        if ((component.isIsIon() || component.getIonicCharge() != 0.0) && phase.getType() != PhaseType.AQUEOUS) {
          assertTrue(component.getx() <= 1.0e-40, component.getComponentName() + " escaped the aqueous phase");
        }
      }
    }
    assertAqueousChargeAndPhaseState(system);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(result);
    }
    SaltPrecipitationResult restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (SaltPrecipitationResult) input.readObject();
    }
    assertEquals(result.getPrecipitatedMoles(), restored.getPrecipitatedMoles(), 0.0);
    assertEquals(result.getFinalSaturationRatio(), restored.getFinalSaturationRatio(), 0.0);
  }

  @Test
  void electrolyteEosUsesItsOwnAqueousActivitiesForPrecipitation() throws Exception {
    SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 1.0);
    system.addComponent("Ca++", 0.2);
    system.addComponent("Cl-", 1.0);
    system.addComponent("SO4--", 0.2);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);

    double initialCalciumMoles = system.getComponent("Ca++").getNumberOfmoles();
    double initialSulphateMoles = system.getComponent("SO4--").getNumberOfmoles();
    SaltPrecipitationResult result = new ThermodynamicOperations(system).precipitateScale("CaSO4_A");

    assertTrue(result.hasPrecipitatedSolid());
    assertEquals(1.0, result.getFinalSaturationRatio(), 1.0e-6);
    assertTrue(result.getMaximumIonBalanceResidualMoles() <= 1.0e-10);
    assertEquals(initialCalciumMoles, system.getComponent("Ca++").getNumberOfmoles() + result.getPrecipitatedMoles(),
        1.0e-10);
    assertEquals(initialSulphateMoles, system.getComponent("SO4--").getNumberOfmoles() + result.getPrecipitatedMoles(),
        1.0e-10);
  }

  @Test
  void sulfateMineralKspCorrelationsAgreeWithPhreeqc390AtReferenceTemperature() throws Exception {
    assertSaturationLogKMatchesPhreeqc("BaSO4", -9.97, 0.04);
    assertSaturationLogKMatchesPhreeqc("SrSO4", -6.63, 0.01);
    assertSaturationLogKMatchesPhreeqc("CaSO4_A", -4.362, 0.01);
  }

  private static SystemPitzer createCalciumSulphateBrine(boolean includeHydrocarbons, double scaleIonMolality) {
    SystemPitzer system = new SystemPitzer(298.15, includeHydrocarbons ? 50.0 : 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 1.0);
    system.addComponent("Ca++", scaleIonMolality);
    system.addComponent("Mg++", 0.0);
    system.addComponent("Cl-", 1.0);
    system.addComponent("SO4--", scaleIonMolality);
    system.init(0);
    system.applyPhreeqcCalciumMagnesiumChlorideSulfateParameters();
    if (includeHydrocarbons) {
      system.addComponent("methane", 5.0);
      system.addComponent("n-heptane", 2.0);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    return system;
  }

  private static void assertSaturationLogKMatchesPhreeqc(String saltName, double phreeqcLogK, double toleranceLogUnits)
      throws Exception {
    SystemPitzer system = new SystemPitzer(298.15, 1.01325);
    system.addComponent("water", 55.508);
    if ("BaSO4".equals(saltName)) {
      system.addComponent("Ba++", 1.0e-20);
    } else if ("SrSO4".equals(saltName)) {
      system.addComponent("Sr++", 1.0e-20);
    } else {
      system.addComponent("Ca++", 1.0e-20);
    }
    system.addComponent("SO4--", 1.0e-20);
    system.init(0);
    system.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.calcSaltSaturation(saltName);
    int aqueousPhaseNumber = system.getPhaseNumberOfPhase("aqueous");
    PhaseInterface aqueous = system.getPhase(aqueousPhaseNumber >= 0 ? aqueousPhaseNumber : 1);
    int water = aqueous.getComponent("water").getComponentNumber();
    String cation = "BaSO4".equals(saltName) ? "Ba++" : "SrSO4".equals(saltName) ? "Sr++" : "Ca++";
    double waterDenominator = aqueous.getComponent("water").getx() * aqueous.getComponent("water").getMolarMass();
    double cationActivity = aqueous.getActivityCoefficient(aqueous.getComponent(cation).getComponentNumber(), water)
        * aqueous.getComponent(cation).getx() / waterDenominator;
    double sulphateActivity = aqueous.getActivityCoefficient(aqueous.getComponent("SO4--").getComponentNumber(), water)
        * aqueous.getComponent("SO4--").getx() / waterDenominator;
    assertEquals(phreeqcLogK, Math.log10(cationActivity * sulphateActivity), toleranceLogUnits);
  }

  private static void assertAqueousChargeAndPhaseState(SystemPitzer system) {
    int aqueousPhaseNumber = system.getPhaseNumberOfPhase("aqueous");
    PhaseInterface aqueous = system.getPhase(aqueousPhaseNumber >= 0 ? aqueousPhaseNumber : 1);
    double moleFractionSum = 0.0;
    double chargeMolality = 0.0;
    for (int componentIndex = 0; componentIndex < aqueous.getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = aqueous.getComponent(componentIndex);
      assertTrue(Double.isFinite(component.getx()) && component.getx() >= 0.0);
      moleFractionSum += component.getx();
      chargeMolality += component.getMolality(aqueous) * component.getIonicCharge();
    }
    assertEquals(1.0, moleFractionSum, 1.0e-12);
    assertEquals(0.0, chargeMolality, 1.0e-10);
  }
}
