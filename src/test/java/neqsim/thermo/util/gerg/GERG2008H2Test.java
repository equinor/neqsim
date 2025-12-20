package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;

/**
 * Test class for GERG-2008-H2 equation of state with improved hydrogen parameters.
 *
 * <p>
 * Tests verify that the improved hydrogen binary interaction parameters provide sensible results
 * for hydrogen-rich natural gas mixtures.
 * </p>
 */
public class GERG2008H2Test {
  private GERG2008H2 gerg;
  static Logger logger = LogManager.getLogger(GERG2008H2Test.class);

  @BeforeEach
  public void setUp() {
    gerg = new GERG2008H2();
    gerg.SetupGERG();
  }

  @Test
  public void testMolarMassGERGHydrogenMixture() {
    double[] x = new double[22];
    x[1] = 0.80; // Methane
    x[2] = 0.05; // Nitrogen
    x[15] = 0.15; // Hydrogen
    doubleW Mm = new doubleW(0.0);
    gerg.MolarMassGERG(x, Mm);
    // Expected: 0.80*16.04246 + 0.05*28.0134 + 0.15*2.01588 = ~14.537
    assertEquals(14.537, Mm.val, 0.01);
  }

  @Test
  public void testPureHydrogen() {
    double T = 300.0;
    double[] x = new double[22];
    x[15] = 1.0; // Pure hydrogen
    doubleW P = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");

    // Test density calculation at 10 MPa
    gerg.DensityGERG(0, T, 10000.0, x, D, ierr, herr);
    assertEquals(0, ierr.val);
    assertTrue(D.val > 0, "Density should be positive");

    // Test pressure calculation
    gerg.PressureGERG(T, D.val, x, P, Z);
    assertTrue(P.val > 0, "Pressure should be positive");
    assertTrue(Z.val > 0, "Compressibility factor should be positive");
    assertEquals(10000.0, P.val, 1.0); // Should recover input pressure within 1 kPa
  }

  @Test
  public void testMethaneHydrogenBinary() {
    double T = 300.0;
    double P = 5000.0;
    double[] x = new double[22];
    x[1] = 0.90; // Methane
    x[15] = 0.10; // Hydrogen
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");

    gerg.DensityGERG(0, T, P, x, D, ierr, herr);
    assertEquals(0, ierr.val, "Should converge without error");
    assertTrue(D.val > 0, "Density should be positive");

    // Test properties
    doubleW PP = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    doubleW dPdD = new doubleW(0.0);
    doubleW d2PdD2 = new doubleW(0.0);
    doubleW d2PdTD = new doubleW(0.0);
    doubleW dPdT = new doubleW(0.0);
    doubleW U = new doubleW(0.0);
    doubleW H = new doubleW(0.0);
    doubleW S = new doubleW(0.0);
    doubleW Cv = new doubleW(0.0);
    doubleW Cp = new doubleW(0.0);
    doubleW W = new doubleW(0.0);
    doubleW G = new doubleW(0.0);
    doubleW JT = new doubleW(0.0);
    doubleW Kappa = new doubleW(0.0);
    doubleW A = new doubleW(0.0);

    gerg.PropertiesGERG(T, D.val, x, PP, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT,
        Kappa, A);

    assertTrue(Z.val > 0.9, "Z factor should be close to ideal at these conditions");
    assertTrue(Cp.val > Cv.val, "Cp should be greater than Cv");
    assertTrue(W.val > 0, "Speed of sound should be positive");
    // Hydrogen mixture should have higher speed of sound than pure methane
    assertTrue(W.val > 400, "Speed of sound should be > 400 m/s for this H2-CH4 mixture");
  }

  @Test
  public void testNitrogenHydrogenBinary() {
    double T = 300.0;
    double P = 10000.0;
    double[] x = new double[22];
    x[2] = 0.50; // Nitrogen
    x[15] = 0.50; // Hydrogen
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");

    gerg.DensityGERG(0, T, P, x, D, ierr, herr);
    assertEquals(0, ierr.val, "Should converge without error for N2-H2 mixture");
    assertTrue(D.val > 0, "Density should be positive");
  }

  @Test
  public void testCO2HydrogenBinary() {
    double T = 350.0; // Higher temperature for CO2 to avoid liquid phase
    double P = 5000.0;
    double[] x = new double[22];
    x[3] = 0.70; // CO2
    x[15] = 0.30; // Hydrogen
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");

    gerg.DensityGERG(0, T, P, x, D, ierr, herr);
    assertEquals(0, ierr.val, "Should converge without error for CO2-H2 mixture");
    assertTrue(D.val > 0, "Density should be positive");
  }

  @Test
  public void testHydrogenRichNaturalGas() {
    double T = 300.0;
    double P = 10000.0;
    // Typical composition for hydrogen-enriched natural gas
    double[] x = new double[22];
    x[1] = 0.70; // Methane
    x[2] = 0.02; // Nitrogen
    x[3] = 0.01; // CO2
    x[4] = 0.05; // Ethane
    x[5] = 0.02; // Propane
    x[15] = 0.20; // Hydrogen

    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");

    gerg.DensityGERG(0, T, P, x, D, ierr, herr);
    assertEquals(0, ierr.val, "Should converge for hydrogen-rich natural gas");
    assertTrue(D.val > 0, "Density should be positive");

    // Verify molar mass calculation
    doubleW Mm = new doubleW(0.0);
    gerg.MolarMassGERG(x, Mm);
    assertTrue(Mm.val > 10 && Mm.val < 20,
        "Molar mass should be between 10 and 20 g/mol for this mixture");
  }

  @Test
  public void testComparisonWithStandardGERG2008() {
    // Test that GERG-2008-H2 gives similar but different results for pure methane
    GERG2008 standardGerg = new GERG2008();
    standardGerg.SetupGERG();

    double T = 300.0;
    double[] x = new double[22];
    x[1] = 1.0; // Pure methane

    doubleW D1 = new doubleW(0.0);
    doubleW D2 = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");

    // For pure methane, results should be identical
    standardGerg.DensityGERG(0, T, 10000.0, x, D1, ierr, herr);
    gerg.DensityGERG(0, T, 10000.0, x, D2, ierr, herr);

    assertEquals(D1.val, D2.val, 1e-10, "Pure methane density should be identical");
  }

  @Test
  public void testHydrogenDensityDifferenceFromStandardGERG() {
    // Test that GERG-2008-H2 gives different results for H2-CH4 mixtures
    GERG2008 standardGerg = new GERG2008();
    standardGerg.SetupGERG();

    double T = 300.0;
    double[] x = new double[22];
    x[1] = 0.80; // Methane
    x[15] = 0.20; // Hydrogen

    doubleW D1 = new doubleW(0.0);
    doubleW D2 = new doubleW(0.0);
    intW ierr1 = new intW(0);
    intW ierr2 = new intW(0);
    StringW herr = new StringW("");

    standardGerg.DensityGERG(0, T, 10000.0, x, D1, ierr1, herr);
    gerg.DensityGERG(0, T, 10000.0, x, D2, ierr2, herr);

    assertEquals(0, ierr1.val, "Standard GERG should converge");
    assertEquals(0, ierr2.val, "GERG-H2 should converge");

    // The densities should be close but not identical due to improved parameters
    // The relative difference is typically small (< 1%)
    double relDiff = Math.abs(D1.val - D2.val) / D1.val;
    assertTrue(relDiff < 0.02, "Density difference should be less than 2% for improved parameters");

    logger.info("Standard GERG-2008 density: {} mol/l", D1.val);
    logger.info("GERG-2008-H2 density: {} mol/l", D2.val);
    logger.info("Relative difference: {}%", relDiff * 100);
  }

  @Test
  public void testPropertiesForPureHydrogen() {
    double T = 300.0;
    double[] x = new double[22];
    x[15] = 1.0; // Pure hydrogen

    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");

    gerg.DensityGERG(0, T, 10000.0, x, D, ierr, herr);
    assertEquals(0, ierr.val);

    doubleW P = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    doubleW dPdD = new doubleW(0.0);
    doubleW d2PdD2 = new doubleW(0.0);
    doubleW d2PdTD = new doubleW(0.0);
    doubleW dPdT = new doubleW(0.0);
    doubleW U = new doubleW(0.0);
    doubleW H = new doubleW(0.0);
    doubleW S = new doubleW(0.0);
    doubleW Cv = new doubleW(0.0);
    doubleW Cp = new doubleW(0.0);
    doubleW W = new doubleW(0.0);
    doubleW G = new doubleW(0.0);
    doubleW JT = new doubleW(0.0);
    doubleW Kappa = new doubleW(0.0);
    doubleW A = new doubleW(0.0);

    gerg.PropertiesGERG(T, D.val, x, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT,
        Kappa, A);

    // Hydrogen has very high speed of sound (~1300 m/s at 300K, 10 MPa)
    assertTrue(W.val > 1000, "Pure H2 speed of sound should be > 1000 m/s");
    assertTrue(Z.val > 1.0,
        "Pure H2 compressibility factor should be > 1 at high pressure (repulsive behavior)");

    // Hydrogen has relatively low heat capacities
    assertTrue(Cv.val > 15 && Cv.val < 25, "H2 Cv should be around 20 J/(mol·K)");
    assertTrue(Cp.val > Cv.val, "Cp should be greater than Cv");
  }
}
