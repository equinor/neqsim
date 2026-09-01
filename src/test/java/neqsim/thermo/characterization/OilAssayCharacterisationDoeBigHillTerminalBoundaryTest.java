package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Public DOE qualification of open-ended refinery-assay boiling boundaries. */
public class OilAssayCharacterisationDoeBigHillTerminalBoundaryTest {
  private static final double DOE_RESIDUE_LOWER_BOUNDARY_F = 1050.0;
  private static final double DOE_RESIDUE_LOWER_BOUNDARY_K = 838.7055555555555;

  @Test
  public void doeResidueRetainsOnlyPublishedLowerBoundary() {
    AssayCut residue = new AssayCut("DOE_BH_1050F_PLUS").withWeightPercent(11.56).withSpecificGravity(1.0089)
        .withLowerBoilingPointFahrenheit(DOE_RESIDUE_LOWER_BOUNDARY_F);

    assertTrue(residue.hasLowerBoilingPoint());
    assertFalse(residue.hasUpperBoilingPoint());
    assertFalse(residue.hasBoilingRange());
    assertEquals(DOE_RESIDUE_LOWER_BOUNDARY_K, residue.getLowerBoilingPointKelvin(), 1.0e-12);
    assertThrows(IllegalStateException.class, residue::getUpperBoilingPointKelvin);
    assertThrows(IllegalStateException.class, residue::resolveAverageBoilingPoint);
  }

  @Test
  public void kelvinCelsiusAndFahrenheitPathsAreEquivalent() {
    AssayCut kelvin = new AssayCut("K").withLowerBoilingPointKelvin(DOE_RESIDUE_LOWER_BOUNDARY_K);
    AssayCut celsius = new AssayCut("C").withLowerBoilingPointCelsius(565.5555555555555);
    AssayCut fahrenheit = new AssayCut("F").withLowerBoilingPointFahrenheit(DOE_RESIDUE_LOWER_BOUNDARY_F);

    assertEquals(kelvin.getLowerBoilingPointKelvin(), celsius.getLowerBoilingPointKelvin(), 1.0e-12);
    assertEquals(kelvin.getLowerBoilingPointKelvin(), fahrenheit.getLowerBoilingPointKelvin(), 1.0e-12);

    AssayCut upperCelsius = new AssayCut("UpperC").withUpperBoilingPointCelsius(79.44444444444444);
    AssayCut upperFahrenheit = new AssayCut("UpperF").withUpperBoilingPointFahrenheit(175.0);
    assertEquals(upperCelsius.getUpperBoilingPointKelvin(), upperFahrenheit.getUpperBoilingPointKelvin(), 1.0e-12);
  }

  @Test
  public void contradictoryInputsFailWithoutPartialMutation() {
    AssayCut lowerFirst = new AssayCut("LowerFirst").withLowerBoilingPointFahrenheit(DOE_RESIDUE_LOWER_BOUNDARY_F);
    assertThrows(IllegalArgumentException.class, () -> lowerFirst.withAverageBoilingPointFahrenheit(1000.0));
    assertThrows(IllegalStateException.class, lowerFirst::resolveAverageBoilingPoint);

    AssayCut representativeFirst = new AssayCut("RepresentativeFirst").withAverageBoilingPointFahrenheit(1100.0);
    assertThrows(IllegalArgumentException.class, () -> representativeFirst.withLowerBoilingPointFahrenheit(1150.0));
    assertFalse(representativeFirst.hasLowerBoilingPoint());
    assertEquals(866.4833333333333, representativeFirst.resolveAverageBoilingPoint(), 1.0e-12);

    AssayCut bounded = new AssayCut("Bounded").withLowerBoilingPointCelsius(100.0);
    assertThrows(IllegalArgumentException.class, () -> bounded.withUpperBoilingPointCelsius(90.0));
    assertFalse(bounded.hasUpperBoilingPoint());
  }

  @Test
  public void oneSidedBoundaryDoesNotInventRepresentativeProperties() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.addCut(new AssayCut("UncharacterizedResidue").withMassFraction(1.0).withSpecificGravity(1.0089)
        .withLowerBoilingPointFahrenheit(DOE_RESIDUE_LOWER_BOUNDARY_F));

    assertThrows(IllegalStateException.class, assay::apply);
    assertEquals(0, system.getNumberOfComponents(), "Failed preparation must not mutate the system");
  }

  @Test
  public void explicitlyCharacterizedTerminalCutClosesMassAndSurvivesClone() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.setTotalAssayMass(1.0);
    assay.addCut(new AssayCut("CharacterizedResidue").withMassFraction(1.0).withSpecificGravity(1.0089)
        .withLowerBoilingPointFahrenheit(DOE_RESIDUE_LOWER_BOUNDARY_F).withMolarMassGramPerMol(650.0));

    SystemInterface clonedSystem = system.clone();
    AssayCut clonedCut = clonedSystem.getOilAssayCharacterisation().getCuts().get(0);
    assertEquals(DOE_RESIDUE_LOWER_BOUNDARY_K, clonedCut.getLowerBoilingPointKelvin(), 1.0e-12);
    assertFalse(clonedCut.hasUpperBoilingPoint());

    assay.apply();
    assertEquals(1, system.getNumberOfComponents());
  }
}
