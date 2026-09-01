package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Public DOE qualification of Watson-derived representative boiling point for a terminal residue cut. */
public class OilAssayCharacterisationDoeBigHillWatsonTerminalTest {
  private static final double DOE_RESIDUE_MASS_PERCENT = 11.56;
  private static final double DOE_RESIDUE_SPECIFIC_GRAVITY = 1.0089;
  private static final double DOE_RESIDUE_WATSON_FACTOR = 11.7;
  private static final double DOE_RESIDUE_LOWER_BOUNDARY_F = 1050.0;
  private static final double EXPECTED_REPRESENTATIVE_BOILING_POINT_K = 913.7543263803914;

  @Test
  public void doeResidueWatsonFactorDerivesRepresentativeBoilingPoint() {
    AssayCut residue = doeResidue("DOE_BH_1050F_PLUS");

    assertTrue(residue.hasWatsonCharacterizationFactor());
    assertEquals(DOE_RESIDUE_WATSON_FACTOR, residue.getWatsonCharacterizationFactor(), 1.0e-15);
    assertEquals(EXPECTED_REPRESENTATIVE_BOILING_POINT_K, residue.resolveAverageBoilingPoint(), 1.0e-12);
    assertTrue(residue.resolveAverageBoilingPoint() > fahrenheitToKelvin(DOE_RESIDUE_LOWER_BOUNDARY_F));
    assertFalse(residue.hasUpperBoilingPoint());

    AssayCut roundTrip = new AssayCut("RoundTrip").withMassFraction(1.0)
        .withSpecificGravity(DOE_RESIDUE_SPECIFIC_GRAVITY)
        .withAverageBoilingPointKelvin(residue.resolveAverageBoilingPoint());
    assertEquals(DOE_RESIDUE_WATSON_FACTOR, roundTrip.getWatsonCharacterizationFactor(), 1.0e-12);
  }

  @Test
  public void specificGravityAndExactApiViewsAreEquivalent() {
    double exactApi = 141.5 / DOE_RESIDUE_SPECIFIC_GRAVITY - 131.5;
    AssayCut specificGravity = doeResidue("SpecificGravity");
    AssayCut apiGravity = new AssayCut("ApiGravity").withWeightPercent(DOE_RESIDUE_MASS_PERCENT)
        .withApiGravity(exactApi).withLowerBoilingPointFahrenheit(DOE_RESIDUE_LOWER_BOUNDARY_F)
        .withWatsonCharacterizationFactor(DOE_RESIDUE_WATSON_FACTOR);

    assertEquals(specificGravity.resolveAverageBoilingPoint(), apiGravity.resolveAverageBoilingPoint(), 1.0e-12);
  }

  @Test
  public void invalidMissingAndAmbiguousInputsFailClosed() {
    assertThrows(IllegalArgumentException.class, () -> new AssayCut("Zero").withWatsonCharacterizationFactor(0.0));
    assertThrows(IllegalArgumentException.class,
        () -> new AssayCut("NaN").withWatsonCharacterizationFactor(Double.NaN));

    AssayCut averageFirst = new AssayCut("AverageFirst").withAverageBoilingPointKelvin(900.0);
    assertThrows(IllegalArgumentException.class,
        () -> averageFirst.withWatsonCharacterizationFactor(DOE_RESIDUE_WATSON_FACTOR));

    AssayCut factorFirst = new AssayCut("FactorFirst").withWatsonCharacterizationFactor(DOE_RESIDUE_WATSON_FACTOR);
    assertThrows(IllegalArgumentException.class, () -> factorFirst.withAverageBoilingPointKelvin(900.0));
    assertThrows(IllegalArgumentException.class, () -> factorFirst.withBoilingRangeKelvin(850.0, 950.0));
    assertThrows(IllegalStateException.class, factorFirst::resolveAverageBoilingPoint);

    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation inconsistent = system.getOilAssayCharacterisation();
    inconsistent
        .addCut(new AssayCut("Inconsistent").withMassFraction(1.0).withSpecificGravity(DOE_RESIDUE_SPECIFIC_GRAVITY)
            .withLowerBoilingPointFahrenheit(1200.0).withWatsonCharacterizationFactor(DOE_RESIDUE_WATSON_FACTOR));
    assertThrows(IllegalStateException.class, inconsistent::apply);
    assertEquals(0, system.getNumberOfComponents(), "Failed preparation must not mutate the system");
  }

  @Test
  public void derivedMetadataSurvivesCloneAndGeneratedMassCloses() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.setTotalAssayMass(1.0);
    assay.addCut(new AssayCut("DOE_BH_RESIDUE").withMassFraction(1.0).withSpecificGravity(DOE_RESIDUE_SPECIFIC_GRAVITY)
        .withLowerBoilingPointFahrenheit(DOE_RESIDUE_LOWER_BOUNDARY_F)
        .withWatsonCharacterizationFactor(DOE_RESIDUE_WATSON_FACTOR));

    SystemInterface clone = system.clone();
    AssayCut clonedCut = clone.getOilAssayCharacterisation().getCuts().get(0);
    assertEquals(DOE_RESIDUE_WATSON_FACTOR, clonedCut.getWatsonCharacterizationFactor(), 1.0e-15);
    assertEquals(EXPECTED_REPRESENTATIVE_BOILING_POINT_K, clonedCut.resolveAverageBoilingPoint(), 1.0e-12);
    assertFalse(clonedCut.hasUpperBoilingPoint());

    assay.apply();
    ComponentInterface component = system.getComponent("DOE_BH_RESIDUE_PC");
    assertNotNull(component);
    assertTrue(Double.isFinite(component.getMolarMass()));
    assertTrue(component.getMolarMass() > 0.0);
    assertTrue(component.getMolarMass() < 1.0);
    assertEquals(1.0, component.getNumberOfmoles() * component.getMolarMass(), 1.0e-10);
  }

  private static AssayCut doeResidue(String name) {
    return new AssayCut(name).withWeightPercent(DOE_RESIDUE_MASS_PERCENT)
        .withSpecificGravity(DOE_RESIDUE_SPECIFIC_GRAVITY).withLowerBoilingPointFahrenheit(DOE_RESIDUE_LOWER_BOUNDARY_F)
        .withWatsonCharacterizationFactor(DOE_RESIDUE_WATSON_FACTOR);
  }

  private static double fahrenheitToKelvin(double fahrenheit) {
    return (fahrenheit - 32.0) * 5.0 / 9.0 + 273.15;
  }
}
