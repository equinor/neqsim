package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.characterization.OilAssayCharacterisation.PianoHydrocarbonFamily;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Public DOE qualification of PIANO-derived C5-175 degF representative molar mass. */
public class OilAssayCharacterisationDoeBigHillPianoTest {
  private static final PianoHydrocarbonFamily P = PianoHydrocarbonFamily.PARAFFIN;
  private static final PianoHydrocarbonFamily I = PianoHydrocarbonFamily.ISO_PARAFFIN;
  private static final PianoHydrocarbonFamily A = PianoHydrocarbonFamily.AROMATIC;
  private static final PianoHydrocarbonFamily N = PianoHydrocarbonFamily.NAPHTHENE;
  private static final PianoHydrocarbonFamily[] FAMILIES = { P, P, P, P, P, I, I, I, I, I, A, A, N, N, N, N };
  private static final int[] CARBON_NUMBERS = { 3, 4, 5, 6, 7, 4, 5, 6, 7, 8, 6, 7, 5, 6, 7, 8 };
  private static final double[] MASS_PERCENT = { 0.32, 3.49, 21.47, 15.28, 0.94, 0.59, 10.96, 17.99, 4.04, 0.05, 3.34,
      0.12, 3.39, 14.38, 3.60, 0.05 };
  private static final double EXPECTED_MOLAR_MASS_KG_PER_MOL = 0.07915383665629189;

  @Test
  public void doePianoGroupsCloseAndCalculateRepresentativeMolarMass() {
    assertEquals(41.50, sumFamily(P), 1.0e-12);
    assertEquals(33.63, sumFamily(I), 1.0e-12);
    assertEquals(3.46, sumFamily(A), 1.0e-12);
    assertEquals(21.42, sumFamily(N), 1.0e-12);
    assertEquals(100.01, sum(MASS_PERCENT), 1.0e-12);

    double molarMass = OilAssayCharacterisation.calculatePianoMolarMassKgPerMol(FAMILIES, CARBON_NUMBERS, MASS_PERCENT);
    assertEquals(EXPECTED_MOLAR_MASS_KG_PER_MOL, molarMass, 1.0e-14);

    PianoHydrocarbonFamily[] reversedFamilies = reverse(FAMILIES);
    int[] reversedCarbonNumbers = reverse(CARBON_NUMBERS);
    double[] reversedMassPercent = reverse(MASS_PERCENT);
    assertEquals(molarMass, OilAssayCharacterisation.calculatePianoMolarMassKgPerMol(reversedFamilies,
        reversedCarbonNumbers, reversedMassPercent), 1.0e-14);
  }

  @Test
  public void doeC5To175CutUsesPianoMolarMassWithoutInventingLowerBoundary() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
    assay.setTotalAssayMass(0.0522);
    AssayCut cut = new AssayCut("DOE_BH_C5_175").withMassFraction(1.0).withSpecificGravity(0.6731)
        .withMolarMassKgPerMol(EXPECTED_MOLAR_MASS_KG_PER_MOL).withUpperBoilingPointFahrenheit(175.0);
    assay.addCut(cut);

    assay.apply();

    ComponentInterface component = system.getComponent("DOE_BH_C5_175_PC");
    assertEquals(1, system.getNumberOfComponents());
    assertEquals(EXPECTED_MOLAR_MASS_KG_PER_MOL, component.getMolarMass(), 1.0e-14);
    assertEquals(0.0522, component.getNumberOfmoles() * component.getMolarMass(), 1.0e-10);
    assertTrue(cut.hasUpperBoilingPoint());
    assertFalse(cut.hasLowerBoilingPoint());
    assertEquals(352.59444444444443, cut.getUpperBoilingPointKelvin(), 1.0e-12);
  }

  @Test
  public void invalidPianoInputsFailClosed() {
    assertThrows(IllegalArgumentException.class,
        () -> OilAssayCharacterisation.calculatePianoMolarMassKgPerMol(null, CARBON_NUMBERS, MASS_PERCENT));
    assertThrows(IllegalArgumentException.class, () -> OilAssayCharacterisation
        .calculatePianoMolarMassKgPerMol(new PianoHydrocarbonFamily[] { P }, new int[] {}, new double[] { 100.0 }));
    assertThrows(IllegalArgumentException.class, () -> OilAssayCharacterisation
        .calculatePianoMolarMassKgPerMol(new PianoHydrocarbonFamily[] {}, new int[] {}, new double[] {}));
    assertThrows(IllegalArgumentException.class,
        () -> OilAssayCharacterisation.calculatePianoMolarMassKgPerMol(new PianoHydrocarbonFamily[] { null },
            new int[] { 5 }, new double[] { 100.0 }));
    assertThrows(IllegalArgumentException.class, () -> OilAssayCharacterisation
        .calculatePianoMolarMassKgPerMol(new PianoHydrocarbonFamily[] { A }, new int[] { 5 }, new double[] { 100.0 }));
    assertThrows(IllegalArgumentException.class, () -> OilAssayCharacterisation
        .calculatePianoMolarMassKgPerMol(new PianoHydrocarbonFamily[] { P }, new int[] { 5 }, new double[] { -1.0 }));
    assertThrows(IllegalArgumentException.class,
        () -> OilAssayCharacterisation.calculatePianoMolarMassKgPerMol(new PianoHydrocarbonFamily[] { P },
            new int[] { 5 }, new double[] { Double.NaN }));
    assertThrows(IllegalArgumentException.class, () -> OilAssayCharacterisation
        .calculatePianoMolarMassKgPerMol(new PianoHydrocarbonFamily[] { P }, new int[] { 5 }, new double[] { 99.0 }));
  }

  private static double sumFamily(PianoHydrocarbonFamily family) {
    double total = 0.0;
    for (int i = 0; i < FAMILIES.length; i++) {
      if (FAMILIES[i] == family) {
        total += MASS_PERCENT[i];
      }
    }
    return total;
  }

  private static double sum(double[] values) {
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return total;
  }

  private static PianoHydrocarbonFamily[] reverse(PianoHydrocarbonFamily[] values) {
    PianoHydrocarbonFamily[] reversed = new PianoHydrocarbonFamily[values.length];
    for (int i = 0; i < values.length; i++) {
      reversed[i] = values[values.length - 1 - i];
    }
    return reversed;
  }

  private static int[] reverse(int[] values) {
    int[] reversed = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      reversed[i] = values[values.length - 1 - i];
    }
    return reversed;
  }

  private static double[] reverse(double[] values) {
    double[] reversed = new double[values.length];
    for (int i = 0; i < values.length; i++) {
      reversed[i] = values[values.length - 1 - i];
    }
    return reversed;
  }
}
