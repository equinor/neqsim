package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.flowassurance.SilicaScaleSaturation.Mineral;

/** Tests silica saturation against external speciation evidence and checks activity input behavior. */
class SilicaScaleSaturationTest {
  /**
   * Checks IPhreeqc 3.7.3 pitzer.dat runs at 1 atm, pH 6.8, 0.0025 mol Si/kg water and NaCl of 0.000001, 2.5, 0.8, 1.5
   * and 0.5 mol/kg water, respectively. Columns are K, log acid activity, log water activity, and the three SIs.
   */
  @Test
  void matchesPhreeqcPitzerReferenceCases() {
    double[][] reference = {
        {298.15, -2.6024645131680124, -1.9577236205136687e-5, 0.059074480923650974, 0.9475746413043975,
            1.3775746413043977},
        {298.15, -2.4808901183401524, -0.03966418697871293, 0.25993809523652667, 1.1484382556172732,
            1.5784382556172734},
        {328.15, -2.573382762360055, -0.011706918067225952, -0.11939367451399763, 0.6837410935825776,
            1.0286376455224908},
        {353.15, -2.559338291169346, -0.022658115924079264, -0.23809873191532782, 0.49716245764544675,
            0.782184562846215},
        {373.15, -2.5928886682787713, -0.007167049761486513, -0.40747468627737504, 0.27607790478067873,
            0.5189768966008752}};
    Mineral[] minerals = {Mineral.AMORPHOUS, Mineral.CHALCEDONY, Mineral.QUARTZ};
    for (double[] point : reference) {
      for (int i = 0; i < minerals.length; i++) {
        double logK = SilicaScaleSaturation.log10DissolutionConstantAtOneAtmosphere(minerals[i], point[0]);
        assertEquals(point[3 + i], SilicaScaleSaturation.saturationIndex(point[1], point[2], logK), 1e-10,
            minerals[i] + " at " + point[0] + " K");
      }
    }
  }

  /** Verifies the solvent stoichiometry, silica-free limit and rejection of invalid inputs. */
  @Test
  void checksWaterActivityAndInputBounds() {
    assertEquals(0.2, SilicaScaleSaturation.saturationIndex(-3.0, -0.1, -3.0), 1e-12);
    assertEquals(Double.NEGATIVE_INFINITY, SilicaScaleSaturation.saturationIndex(Double.NEGATIVE_INFINITY, 0, -3));
    assertThrows(IllegalArgumentException.class, () -> SilicaScaleSaturation.saturationIndex(Double.NaN, 0, -3));
    assertThrows(IllegalArgumentException.class,
        () -> SilicaScaleSaturation.saturationIndex(-3, Double.NEGATIVE_INFINITY, -3));
    assertThrows(IllegalArgumentException.class,
        () -> SilicaScaleSaturation.saturationIndex(-3, 0, Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class,
        () -> SilicaScaleSaturation.log10DissolutionConstantAtOneAtmosphere(Mineral.AMORPHOUS, 400));
  }
}
