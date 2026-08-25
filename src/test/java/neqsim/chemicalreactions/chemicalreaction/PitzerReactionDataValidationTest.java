package neqsim.chemicalreactions.chemicalreaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;

/** Validates Pitzer reaction constants against public USGS/PHREEQC expressions. */
class PitzerReactionDataValidationTest {
  private static final double MAX_LOG10_ERROR = 6.0e-4;
  private static final double[] VALIDATION_TEMPERATURES_C = new double[] { 5.0, 15.0, 25.0, 35.0, 45.0, 55.0, 65.0,
      75.0, 85.0 };
  private static final double[] H2S_PRIMARY_VALIDATION_TEMPERATURES_C = new double[] { 5.0, 25.0, 45.0 };

  @Test
  void carbonateConstantsMatchHeldOutPublicReferenceTemperatures() {
    SystemInterface system = new SystemPitzer(298.15, 1.01325);
    system.addComponent("CO2", 0.01);
    system.addComponent("water", 0.99);
    system.chemicalReactionInit();

    assertEquals(ChemicalReactionDataSource.PITZER, system.getChemicalReactionDataSource());
    validateReaction(system, "CO2water", PitzerReactionDataValidationTest::referenceLog10K1);
    validateReaction(system, "carbonate", PitzerReactionDataValidationTest::referenceLog10K2);
    validateReaction(system, "waterreac", PitzerReactionDataValidationTest::referenceLog10Kw);
  }

  /**
   * The active Pitzer H2S reaction follows the public PHREEQC analytical expression and independently agrees with the
   * infinite-dilution correlation of Hershey, Plese and Millero (1988), DOI 10.1016/0016-7037(88)90183-4.
   */
  @Test
  void hydrogenSulfideFirstDissociationMatchesPublicAndIndependentEvidence() {
    SystemInterface system = new SystemPitzer(298.15, 1.01325);
    system.addComponent("H2S", 0.01);
    system.addComponent("water", 0.99);
    system.chemicalReactionInit();

    assertEquals(ChemicalReactionDataSource.PITZER, system.getChemicalReactionDataSource());
    ChemicalReaction firstDissociation = system.getChemicalReactionOperations().getReactionList()
        .getReaction("water-H2S");
    assertNotNull(firstDissociation);
    assertEquals(ChemicalReactionValidationStatus.VALIDATED, firstDissociation.getValidationStatus());
    assertEquals("USGS-PHREEQC3-b0b3be7-Hershey1988-check", firstDissociation.getReference());

    for (double temperatureC : VALIDATION_TEMPERATURES_C) {
      double temperatureK = temperatureC + 273.15;
      system.setTemperature(temperatureK);
      double actualLog10K = Math.log10(firstDissociation.getK(system.getPhase(0)));
      assertEquals(referenceLog10H2sK1(temperatureK), actualLog10K, 5.0e-11,
          "water-H2S PHREEQC expression at " + temperatureC + " degC");
    }

    for (double temperatureC : H2S_PRIMARY_VALIDATION_TEMPERATURES_C) {
      double temperatureK = temperatureC + 273.15;
      system.setTemperature(temperatureK);
      double actualPka = -Math.log10(firstDissociation.getK(system.getPhase(0)));
      assertEquals(hersheyPleseMilleroPka1(temperatureK), actualPka, 0.08,
          "water-H2S independent pK1 at " + temperatureC + " degC");
    }

    assertNull(system.getChemicalReactionOperations().getReactionList().getReaction("water-HS"),
        "The unsupported second dissociation must not be active in the Pitzer source");

    system.setTemperature(298.15);
    double first = firstDissociation.getK(system.getPhase(0));
    system.setTemperature(318.15);
    double changed = firstDissociation.getK(system.getPhase(0));
    system.setTemperature(298.15);
    assertEquals(first, firstDissociation.getK(system.getPhase(0)), 0.0,
        "Repeated state evaluation must be deterministic and free of stale temperature data");
    org.junit.jupiter.api.Assertions.assertTrue(changed > first,
        "Dissociation must increase from 25 to 45 degC within the validated range");
  }

  private static void validateReaction(SystemInterface system, String reactionName, ReferenceExpression reference) {
    ChemicalReaction reaction = system.getChemicalReactionOperations().getReactionList().getReaction(reactionName);
    assertNotNull(reaction);
    assertEquals(ChemicalReactionValidationStatus.VALIDATED, reaction.getValidationStatus());
    for (double temperatureC : VALIDATION_TEMPERATURES_C) {
      double temperatureK = temperatureC + 273.15;
      system.setTemperature(temperatureK);
      double actualLog10K = Math.log10(reaction.getK(system.getPhase(0)));
      assertEquals(reference.log10K(temperatureK), actualLog10K, MAX_LOG10_ERROR,
          reactionName + " at " + temperatureC + " degC");
    }
  }

  private static double referenceLog10K1(double temperatureK) {
    return referenceLog10K2Association(temperatureK) - referenceLog10CombinedCarbonateAssociation(temperatureK);
  }

  private static double referenceLog10K2(double temperatureK) {
    return -referenceLog10K2Association(temperatureK);
  }

  private static double referenceLog10Kw(double temperatureK) {
    return 293.29227 + 0.1360833 * temperatureK - 10576.913 / temperatureK - 123.73158 * Math.log10(temperatureK)
        - 6.996455e-5 * temperatureK * temperatureK;
  }

  private static double referenceLog10H2sK1(double temperatureK) {
    return 11.17 - 3279.0 / temperatureK - 0.02386 * temperatureK;
  }

  private static double hersheyPleseMilleroPka1(double temperatureK) {
    return -98.080 + 5765.4 / temperatureK + 15.0455 * Math.log(temperatureK);
  }

  private static double referenceLog10K2Association(double temperatureK) {
    return 107.8871 + 0.03252849 * temperatureK - 5151.79 / temperatureK - 38.92561 * Math.log10(temperatureK)
        + 563713.9 / (temperatureK * temperatureK);
  }

  private static double referenceLog10CombinedCarbonateAssociation(double temperatureK) {
    return 464.1965 + 0.09344813 * temperatureK - 26986.16 / temperatureK - 165.75951 * Math.log10(temperatureK)
        + 2248628.9 / (temperatureK * temperatureK);
  }

  @FunctionalInterface
  private interface ReferenceExpression {
    double log10K(double temperatureK);
  }
}
