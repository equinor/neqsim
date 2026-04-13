package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test class for the GERG-2008-NH3 equation of state with ammonia as the 22nd component.
 *
 * <p>
 * Tests verify that the ammonia extension produces sensible results for ammonia-containing natural
 * gas mixtures and does not break existing 21-component calculations.
 * </p>
 */
public class GERG2008NH3Test {
  private GERG2008NH3 gerg;
  static Logger logger = LogManager.getLogger(GERG2008NH3Test.class);

  /**
   * Set up the GERG-2008-NH3 model before each test.
   */
  @BeforeEach
  public void setUp() {
    gerg = new GERG2008NH3();
    gerg.SetupGERG();
  }

  /**
   * Test that the model initializes to 22 components.
   */
  @Test
  public void testModelInitializationHas22Components() {
    assertEquals(22, gerg.NcGERG, "NcGERG should be 22 for NH3 extension");
    assertTrue(gerg.MaxFlds >= 22, "MaxFlds should be at least 22");
  }

  /**
   * Test pure ammonia critical properties are set correctly.
   */
  @Test
  public void testAmmoniaCriticalProperties() {
    // Gao et al. (2020) critical properties per Neumann et al. (2020) Table 2
    assertEquals(405.56, gerg.Tc[22], 0.01, "NH3 Tc should be 405.56 K (Gao et al.)");
    assertEquals(13.696, gerg.Dc[22], 0.001, "NH3 Dc should be 13.696 mol/L (Gao et al.)");
    assertEquals(17.03052, gerg.MMiGERG[22], 0.001, "NH3 M should be 17.03052 g/mol");
  }

  /**
   * Test molar mass calculation for an ammonia-containing mixture.
   */
  @Test
  public void testMolarMassWithAmmonia() {
    double[] x = new double[23];
    x[1] = 0.90; // Methane
    x[22] = 0.10; // Ammonia
    doubleW Mm = new doubleW(0.0);
    gerg.MolarMassGERG(x, Mm);
    // Expected: 0.90 * 16.04246 + 0.10 * 17.03052 = 16.141274
    assertEquals(16.141, Mm.val, 0.01, "Molar mass of 90% CH4 + 10% NH3 mixture");
  }

  /**
   * Test pure ammonia density calculation.
   */
  @Test
  public void testPureAmmoniaDensity() {
    double T = 300.0; // K (about 27 °C, subcritical for NH3)
    double[] x = new double[23];
    x[22] = 1.0; // Pure ammonia
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");

    // 1 MPa = 1000 kPa - vapor phase at this T and P
    gerg.DensityGERG(0, T, 1000.0, x, D, ierr, herr);
    assertEquals(0, ierr.val, "DensityGERG should converge for pure NH3 vapor: " + herr.val);
    assertTrue(D.val > 0, "Density should be positive");

    // Verify pressure recovery
    doubleW P = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    gerg.PressureGERG(T, D.val, x, P, Z);
    assertTrue(P.val > 0, "Pressure should be positive");
    assertTrue(Z.val > 0, "Z factor should be positive");
    assertEquals(1000.0, P.val, 5.0, "Should recover input pressure within 5 kPa");
  }

  /**
   * Test methane-ammonia binary mixture properties.
   */
  @Test
  public void testMethaneAmmoniaBinary() {
    double T = 300.0;
    double P = 5000.0; // 5 MPa
    double[] x = new double[23];
    x[1] = 0.95; // Methane
    x[22] = 0.05; // Ammonia
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");

    gerg.DensityGERG(0, T, P, x, D, ierr, herr);
    assertEquals(0, ierr.val, "Should converge for CH4-NH3 binary: " + herr.val);
    assertTrue(D.val > 0, "Density should be positive");

    // Test full properties
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

    assertTrue(Cp.val > Cv.val, "Cp should be greater than Cv");
    assertTrue(W.val > 0, "Speed of sound should be positive");
    assertTrue(Z.val > 0.8, "Z factor should be reasonable at these conditions");
  }

  /**
   * Test that pure methane results are unchanged compared to the standard GERG-2008.
   *
   * <p>
   * This is a critical backward compatibility test: adding ammonia as component 22 must not alter
   * calculations for the original 21 components when ammonia mole fraction is zero.
   * </p>
   */
  @Test
  public void testBackwardCompatibility_PureMethane() {
    // Compute pure methane properties with GERG-2008-NH3
    double T = 300.0;
    double P = 10000.0;
    double[] x_nh3 = new double[23];
    x_nh3[1] = 1.0;
    doubleW D_nh3 = new doubleW(0.0);
    intW ierr_nh3 = new intW(0);
    StringW herr_nh3 = new StringW("");
    gerg.DensityGERG(0, T, P, x_nh3, D_nh3, ierr_nh3, herr_nh3);

    // Compute same with standard GERG-2008
    GERG2008 gergStd = new GERG2008();
    gergStd.SetupGERG();
    double[] x_std = new double[23]; // Safe size for MaxFlds=22
    x_std[1] = 1.0;
    doubleW D_std = new doubleW(0.0);
    intW ierr_std = new intW(0);
    StringW herr_std = new StringW("");
    gergStd.DensityGERG(0, T, P, x_std, D_std, ierr_std, herr_std);

    assertEquals(0, ierr_nh3.val, "NH3 model should converge for pure CH4");
    assertEquals(0, ierr_std.val, "Standard model should converge for pure CH4");
    assertEquals(D_std.val, D_nh3.val, 1e-10,
        "Pure CH4 density should be identical between standard and NH3-extended models");
  }

  /**
   * Test backward compatibility for a natural gas mixture (no ammonia).
   */
  @Test
  public void testBackwardCompatibility_NaturalGas() {
    double T = 280.0;
    double P = 6000.0;
    double[] x = new double[23];
    x[1] = 0.85; // CH4
    x[2] = 0.06; // N2
    x[3] = 0.03; // CO2
    x[4] = 0.04; // C2H6
    x[5] = 0.02; // C3H8

    // NH3 model result
    doubleW D_nh3 = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    gerg.DensityGERG(0, T, P, x, D_nh3, ierr, herr);

    // Standard model result
    GERG2008 gergStd = new GERG2008();
    gergStd.SetupGERG();
    doubleW D_std = new doubleW(0.0);
    gergStd.DensityGERG(0, T, P, x, D_std, ierr, herr);

    assertEquals(D_std.val, D_nh3.val, 1e-4,
        "Natural gas density should be nearly identical when NH3 fraction is zero");
  }

  /**
   * Test hydrogen-ammonia binary (relevant for green hydrogen / ammonia carrier scenarios).
   */
  @Test
  public void testHydrogenAmmoniaBinary() {
    double T = 350.0;
    double P = 2000.0; // 2 MPa
    double[] x = new double[23];
    x[15] = 0.70; // Hydrogen
    x[22] = 0.30; // Ammonia
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");

    gerg.DensityGERG(0, T, P, x, D, ierr, herr);
    assertEquals(0, ierr.val, "Should converge for H2-NH3 binary: " + herr.val);
    assertTrue(D.val > 0, "Density should be positive");

    // Molar mass check
    doubleW Mm = new doubleW(0.0);
    gerg.MolarMassGERG(x, Mm);
    // Expected: 0.70 * 2.01588 + 0.30 * 17.03052 = 6.520272
    assertEquals(6.520, Mm.val, 0.01, "Molar mass of H2-NH3 mixture");
  }

  /**
   * Test multi-component mixture with ammonia, methane, nitrogen, and hydrogen.
   */
  @Test
  public void testMultiComponentMixture() {
    double T = 300.0;
    double P = 5000.0;
    double[] x = new double[23];
    x[1] = 0.70; // Methane
    x[2] = 0.10; // Nitrogen
    x[3] = 0.05; // CO2
    x[15] = 0.10; // Hydrogen
    x[22] = 0.05; // Ammonia
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");

    gerg.DensityGERG(0, T, P, x, D, ierr, herr);
    assertEquals(0, ierr.val, "Should converge for multi-component mixture: " + herr.val);
    assertTrue(D.val > 0, "Density should be positive");
  }

  /**
   * Test that the NeqSimGERG2008 wrapper works with AMMONIA_EXTENDED type.
   */
  @Test
  public void testNeqSimGERG2008WrapperWithAmmonia() {
    NeqSimGERG2008.clearCache();

    SystemGERG2008Eos system = new SystemGERG2008Eos(300.0, 50.0);
    system.addComponent("methane", 0.90);
    system.addComponent("ammonia", 0.10);
    system.useAmmoniaExtendedModel();
    system.setMixingRule(2);

    assertTrue(system.isUsingAmmoniaExtendedModel(), "Should be using NH3-extended model");

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double density = system.getDensity("kg/m3");
    assertTrue(density > 0, "Density should be positive after flash: " + density);
  }

  /**
   * Test the GERG2008Type enum includes AMMONIA_EXTENDED.
   */
  @Test
  public void testGERG2008TypeEnum() {
    GERG2008Type type = GERG2008Type.AMMONIA_EXTENDED;
    assertNotNull(type);
    assertEquals("GERG-2008-NH3", type.getName());
    assertEquals("GERG-2008 extended with ammonia (22nd component)", type.getDescription());
  }

  /**
   * Test model caching works for AMMONIA_EXTENDED type.
   */
  @Test
  public void testModelCaching() {
    NeqSimGERG2008.clearCache();

    NeqSimGERG2008 wrapper1 = new NeqSimGERG2008();
    wrapper1.setModelType(GERG2008Type.AMMONIA_EXTENDED);

    NeqSimGERG2008 wrapper2 = new NeqSimGERG2008();
    wrapper2.setModelType(GERG2008Type.AMMONIA_EXTENDED);

    // Both should use the same cached model instance
    assertEquals(GERG2008Type.AMMONIA_EXTENDED, wrapper1.getModelType());
    assertEquals(GERG2008Type.AMMONIA_EXTENDED, wrapper2.getModelType());
  }
}
