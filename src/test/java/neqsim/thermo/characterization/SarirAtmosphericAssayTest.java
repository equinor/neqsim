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

/** Tests the constrained Sarir pseudo-component assay input factory. */
public class SarirAtmosphericAssayTest {
  private static final double[] SPECIFIC_GRAVITY = { 0.641826000, 0.671826000, 0.691826000, 0.711826000, 0.741826000,
      0.761826000, 0.781826000, 0.801826000, 0.821826000, 0.841826000, 0.861826000, 0.881826000, 0.901826000,
      0.921826000, 0.941826000, 0.961826000, 0.981826000, 1.021826000 };
  private static final double[] MOLAR_MASS_KG_PER_MOL = { 0.092957679997, 0.105352037330, 0.117746394663,
      0.136337930662, 0.161126645328, 0.179718181327, 0.204506895993, 0.223098431993, 0.241689967992, 0.272675861324,
      0.303661754657, 0.334647647989, 0.384225077321, 0.421408149319, 0.458591221318, 0.495774293317, 0.545351722649,
      0.743661439976 };

  @Test
  public void sourceDerivedCutYieldsCloseAndCannotBeMutated() {
    double[] expected = { 7.44, 3.03, 3.36, 7.33, 7.36, 3.02, 6.49, 3.73, 2.92, 7.29, 7.22, 4.31, 9.02, 3.09, 3.05,
        2.39, 2.65, 16.30 };
    assertArrayEquals(expected, SarirAtmosphericAssay.getCutVolumePercent(), 0.0);
    assertEquals(100.0, sum(expected), 1.0e-12);

    double[] returned = SarirAtmosphericAssay.getCutVolumePercent();
    returned[0] = 0.0;
    assertEquals(7.44, SarirAtmosphericAssay.getCutVolumePercent()[0], 0.0);
  }

  @Test
  public void suppliedProfilesReconcilePublishedWholeCrudeProperties() {
    assertEquals(0.8415, SarirAtmosphericAssay.calculateBulkSpecificGravity(SPECIFIC_GRAVITY), 1.0e-12);
    assertEquals(0.2447, SarirAtmosphericAssay.calculateBulkMolarMassKgPerMol(SPECIFIC_GRAVITY, MOLAR_MASS_KG_PER_MOL),
        1.0e-12);
  }

  @Test
  public void completeVolumeSlatePreservesTerminalEvidenceAndAppliedMass() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = SarirAtmosphericAssay.create(system, 2.5, SPECIFIC_GRAVITY, MOLAR_MASS_KG_PER_MOL);

    assertEquals(0, system.getNumberOfComponents());
    assertEquals(SarirAtmosphericAssay.CUT_COUNT, assay.getCuts().size());
    assertEquals(1.0, sumVolumeFractions(assay), 1.0e-12);
    assertEquals(2.5, assay.getTotalAssayMass(), 0.0);
    assertEquals(0.8415, assay.getBulkSpecificGravity(), 1.0e-12);

    AssayCut first = assay.getCuts().get(0);
    assertEquals("SARIR_TBP_70_MINUS", first.getName());
    assertFalse(first.hasLowerBoilingPoint());
    assertTrue(first.hasUpperBoilingPoint());
    assertEquals(343.15, first.getUpperBoilingPointKelvin(), 1.0e-12);
    assertTrue(first.hasMolarMass());
    assertFalse(first.isStandardComponent());

    AssayCut bounded = assay.getCuts().get(1);
    assertTrue(bounded.hasBoilingRange());
    assertEquals(343.15, bounded.getLowerBoilingPointKelvin(), 1.0e-12);
    assertEquals(363.15, bounded.getUpperBoilingPointKelvin(), 1.0e-12);

    AssayCut residue = assay.getCuts().get(SarirAtmosphericAssay.CUT_COUNT - 1);
    assertEquals("SARIR_TBP_550_PLUS", residue.getName());
    assertTrue(residue.hasLowerBoilingPoint());
    assertFalse(residue.hasUpperBoilingPoint());
    assertEquals(823.15, residue.getLowerBoilingPointKelvin(), 1.0e-12);
    assertTrue(residue.hasMolarMass());

    assay.apply();
    assertEquals(SarirAtmosphericAssay.CUT_COUNT, system.getNumberOfComponents());
    assertEquals(2.5, reconstructedMassKg(system), 1.0e-10);
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      assertPositiveFiniteComponent(system.getComponent(i));
    }
  }

  @Test
  public void invalidProfilesFailBeforeChangingExistingAssayOrSystem() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation existing = system.getOilAssayCharacterisation();
    existing.clearCuts();
    existing.addCut(
        new AssayCut("Existing").withMassFraction(1.0).withSpecificGravity(0.8).withAverageBoilingPointKelvin(400.0));

    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericAssay.create(system, new double[0], MOLAR_MASS_KG_PER_MOL));
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericAssay.create(system, SPECIFIC_GRAVITY, new double[0]));

    double[] invalidDensity = SPECIFIC_GRAVITY.clone();
    invalidDensity[0] = Double.NaN;
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericAssay.create(system, invalidDensity, MOLAR_MASS_KG_PER_MOL));

    double[] inconsistentDensity = SPECIFIC_GRAVITY.clone();
    inconsistentDensity[0] += 0.1;
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericAssay.create(system, inconsistentDensity, MOLAR_MASS_KG_PER_MOL));

    double[] inconsistentMolarMass = MOLAR_MASS_KG_PER_MOL.clone();
    inconsistentMolarMass[17] *= 0.5;
    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericAssay.create(system, SPECIFIC_GRAVITY, inconsistentMolarMass));

    assertThrows(IllegalArgumentException.class,
        () -> SarirAtmosphericAssay.create(system, 0.0, SPECIFIC_GRAVITY, MOLAR_MASS_KG_PER_MOL));
    assertThrows(NullPointerException.class,
        () -> SarirAtmosphericAssay.create(null, SPECIFIC_GRAVITY, MOLAR_MASS_KG_PER_MOL));

    assertEquals(1, existing.getCuts().size());
    assertEquals("Existing", existing.getCuts().get(0).getName());
    assertEquals(0, system.getNumberOfComponents());
  }

  private static double sumVolumeFractions(OilAssayCharacterisation assay) {
    double total = 0.0;
    for (AssayCut cut : assay.getCuts()) {
      total += cut.getVolumeFraction();
    }
    return total;
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
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return total;
  }
}
