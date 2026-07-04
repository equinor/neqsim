package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseDesmukhMather;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.util.amines.AmineKentEisenberg;
import neqsim.thermo.util.amines.AmineKentEisenberg.AmineType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for the Desmukh-Mather electrolyte activity model on amine systems.
 */
class SystemDesmukhMatherTest {
  /** Molar mass of MDEA in g/mol. */
  private static final double MDEA_MOLAR_MASS = 119.1632;
  /** Molar mass of water in g/mol. */
  private static final double WATER_MOLAR_MASS = 18.01528;
  /** Engineering factor used when comparing bubble-point pCO2 to literature data. */
  private static final double BUBBLE_POINT_LITERATURE_FACTOR = 3.0;
  /** Screening factor used for TPflash pCO2 checks at high equilibrated loading. */
  private static final double TPFLASH_LITERATURE_FACTOR = 10.0;

  /**
   * Verifies that MDEA-water-CO2 reactive amine flashes converge and produce finite activities.
   */
  @Test
  void testMdeaWaterCo2ReactiveTPflash() {
    SystemInterface system = new SystemDesmukhMather(313.15, 5.0);
    system.addComponent("methane", 5.0);
    system.addComponent("CO2", 0.2);
    system.addComponent("MDEA", 1.0);
    system.addComponent("water", 9.0);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.TPflash());

    assertTrue(system.isChemicalSystem(), "MDEA-water-CO2 should load aqueous reactions");
    assertTrue(system.getChemicalReactionOperations().hasReactions(), "MDEA-water-CO2 should have chemical reactions");

    PhaseInterface aqueousPhase = null;
    PhaseInterface gasPhase = null;
    for (int phaseNumber = 0; phaseNumber < system.getNumberOfPhases(); phaseNumber++) {
      PhaseInterface phase = system.getPhase(phaseNumber);
      if (phase instanceof PhaseDesmukhMather && phase.hasComponent("water") && phase.hasComponent("MDEA")) {
        aqueousPhase = phase;
      } else if (phase.getType() == PhaseType.GAS) {
        gasPhase = phase;
      }
    }

    assertTrue(aqueousPhase != null, "Reactive MDEA system should retain an aqueous amine phase");
    assertTrue(gasPhase != null, "Reactive MDEA TPflash should retain a gas phase for pCO2 validation");
    assertTrue(aqueousPhase.getComponent("water").getNumberOfMolesInPhase() > 0.0,
        "Aqueous amine phase should contain water");
    assertTrue(aqueousPhase.getComponent("MDEA").getNumberOfMolesInPhase() > 0.0,
        "Aqueous amine phase should contain MDEA");
    assertTrue(aqueousPhase.hasComponent("MDEA+"), "MDEA protonation should add MDEA+");
    assertTrue(aqueousPhase.hasComponent("HCO3-"), "CO2 absorption should add bicarbonate");
    assertTrue(aqueousPhase.getComponent("MDEA+").getNumberOfMolesInPhase() > 0.0,
        "Reactive MDEA flash should form protonated MDEA");
    assertTrue(aqueousPhase.getComponent("HCO3-").getNumberOfMolesInPhase() > 0.0,
        "Reactive MDEA flash should form bicarbonate");

    int mdeaNumber = aqueousPhase.getComponent("MDEA").getComponentNumber();
    int bicarbonateNumber = aqueousPhase.getComponent("HCO3-").getComponentNumber();
    assertTrue(Double.isFinite(aqueousPhase.getActivityCoefficient(mdeaNumber)),
        "MDEA activity coefficient should be finite");
    assertTrue(Double.isFinite(aqueousPhase.getActivityCoefficient(bicarbonateNumber)),
        "Bicarbonate activity coefficient should be finite");

    double aqueousMdeaMoles = componentMoles(aqueousPhase, "MDEA") + componentMoles(aqueousPhase, "MDEA+");
    double aqueousCo2Moles = componentMoles(aqueousPhase, "CO2") + componentMoles(aqueousPhase, "HCO3-")
        + componentMoles(aqueousPhase, "CO3--");
    double mdeaMassFraction = mdeaMassFraction(aqueousMdeaMoles, componentMoles(aqueousPhase, "water"));
    double mdeaMolarity = AmineKentEisenberg.amineMolarity(mdeaMassFraction, MDEA_MOLAR_MASS);
    double feedLoading = aqueousCo2Moles / aqueousMdeaMoles;
    double referencePco2 = AmineKentEisenberg.partialPressureCO2Bara(AmineType.MDEA, 313.15, mdeaMolarity, feedLoading);
    double pco2 = gasPhase.getComponent("CO2").getx() * gasPhase.getPressure("bara");
    assertTrue(pco2 > referencePco2 / TPFLASH_LITERATURE_FACTOR && pco2 < TPFLASH_LITERATURE_FACTOR * referencePco2,
        "Desmukh-Mather pCO2 should be within an engineering factor of the Kent-Eisenberg/Jou-Mather-Otto reference, "
            + "pCO2=" + pco2 + " bara reference=" + referencePco2 + " bara loading=" + feedLoading);
    assertTrue(aqueousPhase.getDensity() > 900.0 && aqueousPhase.getDensity() < 1300.0,
        "Aqueous MDEA density should be finite and physically plausible");
    assertTrue(aqueousPhase.getMolarVolume() > 0.0, "Aqueous MDEA molar volume should be finite");
    assertTrue(Double.isFinite(aqueousPhase.getEnthalpy()), "Aqueous MDEA enthalpy should be finite");
    assertTrue(Double.isFinite(aqueousPhase.getEntropy()), "Aqueous MDEA entropy should be finite");
    assertTrue(Double.isFinite(aqueousPhase.getCp()), "Aqueous MDEA Cp should be finite");
    assertTrue(Double.isFinite(aqueousPhase.getCv()), "Aqueous MDEA Cv should be finite");
  }

  /**
   * Verifies that CO2-MDEA-water bubble-point pressure calculations converge for reactive amine systems.
   *
   * <p>
   * The pCO2 comparison uses the local Kent-Eisenberg implementation as a literature-derived reference. That helper is
   * documented against the Jou, Mather and Otto MDEA vapour-liquid-equilibrium data. The assertion uses an engineering
   * factor because this test checks the Desmukh-Mather bubble-point path against independent amine pCO2 data.
   * </p>
   */
  @Test
  void testCo2MdeaWaterBubblePointPressureFlash() {
    SystemInterface system = new SystemDesmukhMather(313.15, 1.0);
    system.addComponent("CO2", 0.2);
    system.addComponent("MDEA", 1.0);
    system.addComponent("water", 9.0);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    assertDoesNotThrow(() -> ops.bubblePointPressureFlash(false));

    PhaseInterface aqueousPhase = null;
    PhaseInterface gasPhase = null;
    for (int phaseNumber = 0; phaseNumber < system.getNumberOfPhases(); phaseNumber++) {
      PhaseInterface phase = system.getPhase(phaseNumber);
      if (phase instanceof PhaseDesmukhMather && phase.hasComponent("water") && phase.hasComponent("MDEA")) {
        aqueousPhase = phase;
      } else if (phase.getType() == PhaseType.GAS) {
        gasPhase = phase;
      }
    }

    assertTrue(system.isChemicalSystem(), "CO2-MDEA-water bubble point should use aqueous reactions");
    assertTrue(system.getChemicalReactionOperations().hasReactions(), "CO2-MDEA-water should have chemical reactions");
    assertTrue(aqueousPhase != null, "Bubble-point flash should retain an aqueous MDEA phase");
    assertTrue(gasPhase != null, "Bubble-point flash should create an incipient gas phase");
    assertTrue(aqueousPhase.hasComponent("MDEA+"), "Bubble-point flash should include protonated MDEA");
    assertTrue(aqueousPhase.hasComponent("HCO3-"), "Bubble-point flash should include bicarbonate");

    double mdeaMassFraction = mdeaMassFraction(1.0, 9.0);
    double mdeaMolarity = AmineKentEisenberg.amineMolarity(mdeaMassFraction, MDEA_MOLAR_MASS);
    double referencePco2 = AmineKentEisenberg.partialPressureCO2Bara(AmineType.MDEA, 313.15, mdeaMolarity, 0.2);
    double bubblePointPressure = system.getPressure("bara");
    assertTrue(Double.isFinite(bubblePointPressure) && bubblePointPressure > 0.0,
        "Bubble-point pressure should be finite and positive");
    double pco2 = bubblePointPressure * gasPhase.getComponent("CO2").getx();
    double pco2RatioToLiterature = pco2 / referencePco2;
    assertTrue(
        pco2RatioToLiterature > 1.0 / BUBBLE_POINT_LITERATURE_FACTOR
            && pco2RatioToLiterature < BUBBLE_POINT_LITERATURE_FACTOR,
        "Desmukh-Mather bubble-point pCO2 should remain in the broad screening range of the Kent-Eisenberg/"
            + "Jou-Mather-Otto reference, pCO2=" + pco2 + " bara bubblePointPressure=" + bubblePointPressure
            + " bara reference=" + referencePco2 + " bara ratio=" + pco2RatioToLiterature);

    assertTrue(aqueousPhase.getDensity() > 900.0 && aqueousPhase.getDensity() < 1300.0,
        "Bubble-point aqueous MDEA density should be finite and physically plausible");
    assertTrue(aqueousPhase.getMolarVolume() > 0.0, "Bubble-point aqueous MDEA molar volume should be finite");
    assertTrue(Double.isFinite(aqueousPhase.getEnthalpy()), "Bubble-point aqueous MDEA enthalpy should be finite");
    assertTrue(Double.isFinite(aqueousPhase.getEntropy()), "Bubble-point aqueous MDEA entropy should be finite");
    assertTrue(Double.isFinite(aqueousPhase.getCp()), "Bubble-point aqueous MDEA Cp should be finite");
    assertTrue(Double.isFinite(aqueousPhase.getCv()), "Bubble-point aqueous MDEA Cv should be finite");
  }

  /**
   * Calculates MDEA mass fraction for the amine-water feed used in the tests.
   *
   * @param mdeaMoles number of moles MDEA
   * @param waterMoles number of moles water
   * @return MDEA mass fraction on an amine plus water basis
   */
  private static double mdeaMassFraction(double mdeaMoles, double waterMoles) {
    double mdeaMass = mdeaMoles * MDEA_MOLAR_MASS;
    double waterMass = waterMoles * WATER_MOLAR_MASS;
    return mdeaMass / (mdeaMass + waterMass);
  }

  /**
   * Returns component moles in a phase, or zero when the component is absent.
   *
   * @param phase phase to inspect
   * @param componentName component name
   * @return number of moles of the component in the phase
   */
  private static double componentMoles(PhaseInterface phase, String componentName) {
    return phase.hasComponent(componentName) ? phase.getComponent(componentName).getNumberOfMolesInPhase() : 0.0;
  }
}
