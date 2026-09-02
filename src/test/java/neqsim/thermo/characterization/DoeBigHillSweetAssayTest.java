package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

/** Tests the reproducible DOE Big Hill Sweet complete modeled assay slate. */
public class DoeBigHillSweetAssayTest {
  private static final String[] STANDARD_COMPONENTS = { "ethane", "propane", "i-butane", "n-butane" };
  private static final String[] PSEUDO_COMPONENTS = { "DOE_BH_C5_175_PC", "DOE_BH_175_250_PC", "DOE_BH_250_375_PC",
      "DOE_BH_375_530_PC", "DOE_BH_530_650_PC", "DOE_BH_650_850_PC", "DOE_BH_850_1050_PC", "DOE_BH_1050_PLUS_PC" };

  @Test
  public void completeSlateClosesAndPreservesEvidenceBoundaries() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = DoeBigHillSweetAssay.create(system);

    assertEquals(0, system.getNumberOfComponents());
    assertEquals(12, assay.getCuts().size());
    assertEquals(1.0, sum(assay.getResolvedMassFractions()), 1.0e-12);
    assertEquals(0.017, sum(assay.getResolvedMassFractions(), 0, 4), 1.0e-12);
    assertEquals(0.40867518, assay.getBulkSulfurMassPercent(), 1.0e-12);
    assertEquals(0.1095129, assay.getBulkNitrogenMassPercent(), 1.0e-12);

    AssayCut c5Cut = assay.getCuts().get(4);
    assertFalse(c5Cut.hasLowerBoilingPoint());
    assertTrue(c5Cut.hasUpperBoilingPoint());
    assertTrue(c5Cut.hasMolarMass());

    AssayCut residue = assay.getCuts().get(11);
    assertTrue(residue.hasLowerBoilingPoint());
    assertFalse(residue.hasUpperBoilingPoint());
    assertTrue(residue.hasWatsonCharacterizationFactor());
    assertEquals(913.7543263803914, residue.resolveAverageBoilingPoint(), 1.0e-9);

    assay.apply();

    assertEquals(12, system.getNumberOfComponents());
    for (String componentName : STANDARD_COMPONENTS) {
      assertPositiveFiniteComponent(system.getComponent(componentName));
    }
    for (String componentName : PSEUDO_COMPONENTS) {
      assertPositiveFiniteComponent(system.getComponent(componentName));
    }
    assertEquals(1.0, reconstructedMassKg(system), 1.0e-10);
  }

  @Test
  public void constructionIsDeterministicAndScalesAppliedMass() {
    SystemInterface firstSystem = new SystemSrkEos(298.15, 1.01325);
    SystemInterface secondSystem = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation first = DoeBigHillSweetAssay.create(firstSystem, 2.5);
    OilAssayCharacterisation second = DoeBigHillSweetAssay.create(secondSystem, 2.5);

    assertArrayEquals(first.getResolvedMassFractions(), second.getResolvedMassFractions(), 0.0);
    assertEquals(2.5, first.getTotalAssayMass(), 0.0);
    assertEquals(2.5, second.getTotalAssayMass(), 0.0);

    first.apply();
    second.apply();
    assertEquals(2.5, reconstructedMassKg(firstSystem), 1.0e-10);
    assertEquals(2.5, reconstructedMassKg(secondSystem), 1.0e-10);
  }

  @Test
  public void invalidMassFailsBeforeChangingExistingAssayOrSystem() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation existing = system.getOilAssayCharacterisation();
    existing.clearCuts();
    existing.addCut(
        new AssayCut("Existing").withMassFraction(1.0).withSpecificGravity(0.8).withAverageBoilingPointKelvin(400.0));

    assertThrows(IllegalArgumentException.class, () -> DoeBigHillSweetAssay.create(system, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> DoeBigHillSweetAssay.create(system, 0.0));
    assertEquals(1, existing.getCuts().size());
    assertEquals("Existing", existing.getCuts().get(0).getName());
    assertEquals(0, system.getNumberOfComponents());
    assertThrows(NullPointerException.class, () -> DoeBigHillSweetAssay.create(null));
  }

  private static void assertPositiveFiniteComponent(ComponentInterface component) {
    assertNotNull(component);
    assertTrue(Double.isFinite(component.getNumberOfmoles()));
    assertTrue(component.getNumberOfmoles() > 0.0);
    assertTrue(Double.isFinite(component.getMolarMass()));
    assertTrue(component.getMolarMass() > 0.0);
  }

  private static double reconstructedMassKg(SystemInterface system) {
    double mass = 0.0;
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      ComponentInterface component = system.getComponent(i);
      mass += component.getNumberOfmoles() * component.getMolarMass();
    }
    return mass;
  }

  private static double sum(double[] values) {
    return sum(values, 0, values.length);
  }

  private static double sum(double[] values, int start, int end) {
    double total = 0.0;
    for (int i = start; i < end; i++) {
      total += values[i];
    }
    return total;
  }
}
