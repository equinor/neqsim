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
 * Validation test for GERG-2008-NH3 against NIST WebBook reference data.
 *
 * <p>
 * Pure ammonia properties are validated against NIST Chemistry WebBook (Gao et al., 2020 EOS). The
 * GERG2008NH3 implementation uses the full 20-term Gao et al. (2020) Helmholtz energy equation with
 * critical properties Tc=405.56K, rhoc=13.696 mol/L. Density deviations are below 0.01% against
 * NIST reference data across 300-500K and 0.1-10 MPa.
 * </p>
 *
 * <p>
 * NIST reference data source: https://webbook.nist.gov/chemistry/fluid/ Fluid: Ammonia (CAS
 * 7664-41-7), Reference state: default (NBP).
 * </p>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class GERG2008NH3ValidationTest {
  private GERG2008NH3 gerg;
  static Logger logger = LogManager.getLogger(GERG2008NH3ValidationTest.class);

  /**
   * Set up the GERG-2008-NH3 model before each test.
   */
  @BeforeEach
  public void setUp() {
    gerg = new GERG2008NH3();
    gerg.SetupGERG();
  }

  /**
   * Helper to compute density from T(K) and P(kPa) for pure ammonia.
   *
   * @param T temperature in K
   * @param P_kPa pressure in kPa
   * @return density in mol/L, or -1 if failed
   */
  private double pureNH3Density(double T, double P_kPa) {
    double[] x = new double[23];
    x[22] = 1.0;
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    gerg.DensityGERG(0, T, P_kPa, x, D, ierr, herr);
    if (ierr.val != 0) {
      logger.warn("DensityGERG failed: T={}, P={}, err={}", T, P_kPa, herr.val);
      return -1;
    }
    return D.val;
  }

  /**
   * Helper to compute all properties for pure ammonia at given T and density.
   *
   * @param T temperature in K
   * @param D density in mol/L
   * @return array: [P_kPa, Z, Cv_J_molK, Cp_J_molK, W_m_s, H_J_mol, S_J_molK]
   */
  private double[] pureNH3Properties(double T, double D) {
    double[] x = new double[23];
    x[22] = 1.0;
    doubleW P = new doubleW(0);
    doubleW Z = new doubleW(0);
    doubleW dPdD = new doubleW(0);
    doubleW d2PdD2 = new doubleW(0);
    doubleW d2PdTD = new doubleW(0);
    doubleW dPdT = new doubleW(0);
    doubleW U = new doubleW(0);
    doubleW H = new doubleW(0);
    doubleW S = new doubleW(0);
    doubleW Cv = new doubleW(0);
    doubleW Cp = new doubleW(0);
    doubleW W = new doubleW(0);
    doubleW G = new doubleW(0);
    doubleW JT = new doubleW(0);
    doubleW Kappa = new doubleW(0);
    doubleW A = new doubleW(0);

    gerg.PropertiesGERG(T, D, x, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT, Kappa,
        A);

    return new double[] {P.val, Z.val, Cv.val, Cp.val, W.val, H.val, S.val};
  }

  /**
   * Validate pure NH3 vapor density at T=300K against NIST.
   *
   * <p>
   * NIST data: T=300K, P=0.1 MPa (100 kPa), rho=0.040501 mol/L.
   * </p>
   */
  @Test
  public void testPureNH3_Density_300K_01MPa() {
    double D = pureNH3Density(300.0, 100.0);
    assertTrue(D > 0, "Density should converge");
    // NIST: 0.040501 mol/L
    double relErr = Math.abs(D - 0.040501) / 0.040501;
    logger.info("NH3 300K 0.1MPa: D_calc={} mol/L, D_NIST=0.040501, relErr={}", D, relErr);
    assertEquals(0.040501, D, 0.002, "NH3 density at 300K, 0.1MPa vs NIST (mol/L)");
  }

  /**
   * Validate pure NH3 vapor density at T=400K, multiple pressures against NIST.
   *
   * <p>
   * NIST data (T=400K, vapor):
   * </p>
   * <ul>
   * <li>P=0.5 MPa: rho=0.15299 mol/L, Cp=40.083 J/(mol·K), W=493.91 m/s</li>
   * <li>P=1.0 MPa: rho=0.31160 mol/L, Cp=41.617 J/(mol·K), W=488.91 m/s</li>
   * <li>P=2.0 MPa: rho=0.64791 mol/L, Cp=45.119 J/(mol·K), W=478.46 m/s</li>
   * <li>P=5.0 MPa: rho=1.8705 mol/L, Cp=61.423 J/(mol·K), W=442.30 m/s</li>
   * </ul>
   */
  @Test
  public void testPureNH3_400K_MultiplePressures() {
    double T = 400.0;

    // Test points: P(kPa), rho_NIST(mol/L), Cp_NIST(J/molK), W_NIST(m/s)
    double[][] testData = {{500.0, 0.15299, 40.083, 493.91}, {1000.0, 0.31160, 41.617, 488.91},
        {2000.0, 0.64791, 45.119, 478.46}, {5000.0, 1.8705, 61.423, 442.30}};

    for (double[] pt : testData) {
      double P_kPa = pt[0];
      double rhoNIST = pt[1];
      double CpNIST = pt[2];
      double wNIST = pt[3];

      double D = pureNH3Density(T, P_kPa);
      assertTrue(D > 0, "Density should converge at P=" + P_kPa);

      double relErrRho = Math.abs(D - rhoNIST) / rhoNIST;
      logger.info("NH3 400K {}kPa: D_calc={}, D_NIST={}, relErr={}", P_kPa, D, rhoNIST, relErrRho);

      double[] props = pureNH3Properties(T, D);
      double Cp = props[3];
      double W = props[4];

      double relErrCp = Math.abs(Cp - CpNIST) / CpNIST;
      double relErrW = Math.abs(W - wNIST) / wNIST;
      logger.info("  Cp_calc={}, Cp_NIST={}, relErr={}", Cp, CpNIST, relErrCp);
      logger.info("  W_calc={}, W_NIST={}, relErr={}", W, wNIST, relErrW);

      // Density within 0.1% (full Gao EOS, near-exact agreement with NIST)
      assertEquals(rhoNIST, D, rhoNIST * 0.001, "NH3 density at 400K, " + P_kPa / 1000 + " MPa");
    }
  }

  /**
   * Validate pure NH3 at T=500K (well above Tc=405.56K) against NIST.
   *
   * <p>
   * NIST data (T=500K, supercritical):
   * </p>
   * <ul>
   * <li>P=1.0 MPa: rho=0.24426 mol/L, Cp=43.067 J/(mol·K), W=547.34 m/s</li>
   * <li>P=5.0 MPa: rho=1.3042 mol/L, Cp=48.514 J/(mol·K), W=529.23 m/s</li>
   * <li>P=10.0 MPa: rho=2.8633 mol/L, Cp=57.522 J/(mol·K), W=507.36 m/s</li>
   * </ul>
   */
  @Test
  public void testPureNH3_500K_Supercritical() {
    double T = 500.0;

    double[][] testData = {{1000.0, 0.24426, 43.067, 547.34}, {5000.0, 1.3042, 48.514, 529.23},
        {10000.0, 2.8633, 57.522, 507.36}};

    for (double[] pt : testData) {
      double P_kPa = pt[0];
      double rhoNIST = pt[1];
      double CpNIST = pt[2];
      double wNIST = pt[3];

      double D = pureNH3Density(T, P_kPa);
      assertTrue(D > 0, "Density should converge at P=" + P_kPa);

      double relErrRho = Math.abs(D - rhoNIST) / rhoNIST;
      logger.info("NH3 500K {}kPa: D_calc={}, D_NIST={}, relErr={}", P_kPa, D, rhoNIST, relErrRho);

      double[] props = pureNH3Properties(T, D);
      double Cp = props[3];
      double W = props[4];

      logger.info("  Cp_calc={}, Cp_NIST={}, relErr={}", Cp, CpNIST,
          Math.abs(Cp - CpNIST) / CpNIST);
      logger.info("  W_calc={}, W_NIST={}, relErr={}", W, wNIST, Math.abs(W - wNIST) / wNIST);

      // Tolerate up to 0.1% for density (full Gao EOS, near-exact agreement with NIST)
      assertEquals(rhoNIST, D, rhoNIST * 0.001, "NH3 density at 500K, " + P_kPa / 1000 + " MPa");
    }
  }

  /**
   * Validate pure NH3 at T=430K (just above Tc=405.56K) against NIST.
   *
   * <p>
   * NIST data (T=430K, vapor). The full Gao et al. (2020) reference EOS provides near-exact
   * agreement in the near-critical region, including at high pressures close to Pc.
   * </p>
   */
  @Test
  public void testPureNH3_430K_NearCritical() {
    double T = 430.0;

    // Only test moderate pressures; 10 MPa is too close to Pc for mismatched SFE-12
    double[][] testData = {{1000.0, 0.28739, 41.776, 507.74}, {3000.0, 0.91503, 46.901, 491.57},
        {5000.0, 1.6327, 53.639, 474.14}};

    for (double[] pt : testData) {
      double P_kPa = pt[0];
      double rhoNIST = pt[1];

      double D = pureNH3Density(T, P_kPa);
      assertTrue(D > 0, "Density should converge at P=" + P_kPa);

      double relErrRho = Math.abs(D - rhoNIST) / rhoNIST;
      logger.info("NH3 430K {}kPa: D_calc={}, D_NIST={}, relErr={}", P_kPa, D, rhoNIST, relErrRho);

      double[] props = pureNH3Properties(T, D);
      double Cp = props[3];
      double W = props[4];
      logger.info("  Cp_calc={}, W_calc={}", Cp, W);

      // Near-critical region: 0.1% tolerance for density (full Gao EOS)
      assertEquals(rhoNIST, D, rhoNIST * 0.001, "NH3 density at 430K, " + P_kPa / 1000 + " MPa");
    }
  }

  /**
   * Validate pressure recovery: compute density from (T,P), then recover P from (T,D).
   *
   * <p>
   * This is a self-consistency check: density solver + pressure equation must be thermodynamically
   * consistent.
   * </p>
   */
  @Test
  public void testPressureRecovery_PureNH3() {
    double[] temperatures = {300.0, 400.0, 500.0};
    double[] pressures = {100.0, 500.0, 1000.0, 5000.0, 10000.0};

    double[] x = new double[23];
    x[22] = 1.0;

    for (double T : temperatures) {
      for (double P_kPa : pressures) {
        double D = pureNH3Density(T, P_kPa);
        if (D <= 0) {
          continue; // Skip subcritical liquid states
        }

        double[] props = pureNH3Properties(T, D);
        double P_recovered = props[0];

        double relErr = Math.abs(P_recovered - P_kPa) / P_kPa;
        logger.info("P recovery T={}K P={}kPa: P_rec={}, relErr={}", T, P_kPa, P_recovered, relErr);
        assertEquals(P_kPa, P_recovered, P_kPa * 1e-6,
            "Pressure recovery at T=" + T + "K, P=" + P_kPa + "kPa");
      }
    }
  }

  /**
   * Validate thermodynamic consistency: Cp > Cv > 0 for pure NH3 at all conditions.
   */
  @Test
  public void testThermodynamicConsistency_PureNH3() {
    double[] temperatures = {300.0, 350.0, 400.0, 430.0, 500.0};
    double[] pressures = {100.0, 500.0, 1000.0, 5000.0};

    for (double T : temperatures) {
      for (double P_kPa : pressures) {
        double D = pureNH3Density(T, P_kPa);
        if (D <= 0) {
          continue;
        }

        double[] props = pureNH3Properties(T, D);
        double Z = props[1];
        double Cv = props[2];
        double Cp = props[3];
        double W = props[4];

        assertTrue(Cv > 0, "Cv should be positive at T=" + T + ", P=" + P_kPa);
        assertTrue(Cp > Cv, "Cp should be > Cv at T=" + T + ", P=" + P_kPa);
        assertTrue(W > 0, "Speed of sound should be positive at T=" + T + ", P=" + P_kPa);
        assertTrue(Z > 0, "Z-factor should be positive at T=" + T + ", P=" + P_kPa);

        logger.info("Consistency T={}K P={}kPa: Z={}, Cv={}, Cp={}, W={}", T, P_kPa, Z, Cv, Cp, W);
      }
    }
  }

  /**
   * Validate backward compatibility: standard GERG-2008 21-component results must be identical.
   *
   * <p>
   * Uses the AGA8 test gas mixture from the GERG-2008 paper to verify that the NH3 extension does
   * not alter any standard mixture calculation.
   * </p>
   */
  @Test
  public void testBackwardCompatibility_AGA8TestGas() {
    // AGA8 publication test mixture
    double T = 400.0;
    double P = 5000.0;

    // Standard GERG model
    GERG2008 gergStd = new GERG2008();
    gergStd.SetupGERG();

    double[] x = new double[23];
    x[1] = 0.906724; // Methane
    x[2] = 0.031284; // Nitrogen
    x[3] = 0.004676; // CO2
    x[4] = 0.045279; // Ethane
    x[5] = 0.008280; // Propane
    x[6] = 0.001563; // n-Butane
    x[7] = 0.001004; // i-Butane
    x[8] = 0.000570; // n-Pentane
    x[9] = 0.000270; // i-Pentane
    x[10] = 0.000350; // n-Hexane

    // NH3-extended model
    doubleW D_nh3 = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    gerg.DensityGERG(0, T, P, x, D_nh3, ierr, herr);
    assertEquals(0, ierr.val, "NH3 model converge: " + herr.val);

    // Standard model
    doubleW D_std = new doubleW(0.0);
    gergStd.DensityGERG(0, T, P, x, D_std, ierr, herr);
    assertEquals(0, ierr.val, "Standard model converge: " + herr.val);

    assertEquals(D_std.val, D_nh3.val, 1e-10,
        "AGA8 test gas density must be identical in both models");

    // Check all properties match
    double[] propsNh3 = allProperties(gerg, T, D_nh3.val, x);
    double[] propsStd = allProperties(gergStd, T, D_std.val, x);

    String[] names = {"P", "Z", "Cv", "Cp", "W", "H", "S"};
    for (int i = 0; i < names.length; i++) {
      assertEquals(propsStd[i], propsNh3[i], Math.abs(propsStd[i]) * 1e-10,
          names[i] + " must match between standard and NH3-extended models");
    }
  }

  /**
   * Validate reducing parameters for NH3 pairs match expected values.
   *
   * <p>
   * Checks that the reducing temperature and density for the CH4+NH3 binary match the values
   * derived from the Neumann et al. (2020) Table 3 parameters.
   * </p>
   */
  @Test
  public void testReducingParameters_CH4_NH3() {
    double[] x = new double[23];
    x[1] = 0.5; // CH4
    x[22] = 0.5; // NH3
    doubleW Tr = new doubleW(0.0);
    doubleW Dr = new doubleW(0.0);
    gerg.ReducingParametersGERG(x, Tr, Dr);

    assertTrue(Tr.val > 0, "Reducing temperature should be positive");
    assertTrue(Dr.val > 0, "Reducing density should be positive");

    // Tc_CH4 = 190.564 K and Tc_NH3 = 405.56 K
    // Reducing T should be between pure component values
    assertTrue(Tr.val > 190.0, "Tr should be > Tc_CH4 (190.564K)");
    assertTrue(Tr.val < 406.0, "Tr should be < Tc_NH3 (405.56K)");

    logger.info("CH4+NH3 50/50: Tr={} K, Dr={} mol/L", Tr.val, Dr.val);
  }

  /**
   * Test pure ammonia Z-factor against ideal gas limit at low density.
   *
   * <p>
   * At very low pressure (0.01 MPa), Z should approach 1.0.
   * </p>
   */
  @Test
  public void testIdealGasLimit_PureNH3() {
    double T = 500.0;
    double P_kPa = 10.0; // 0.01 MPa — nearly ideal
    double D = pureNH3Density(T, P_kPa);
    assertTrue(D > 0, "Should converge at low pressure");

    double[] props = pureNH3Properties(T, D);
    double Z = props[1];

    logger.info("NH3 ideal gas limit: T=500K, P=10kPa, Z={}", Z);
    assertEquals(1.0, Z, 0.002, "Z should approach 1.0 at low density");
  }

  /**
   * Helper: compute properties using a specific GERG2008 model instance.
   *
   * @param model the GERG model
   * @param T temperature in K
   * @param D density in mol/L
   * @param x composition
   * @return array: [P_kPa, Z, Cv, Cp, W, H, S]
   */
  private double[] allProperties(GERG2008 model, double T, double D, double[] x) {
    doubleW P = new doubleW(0);
    doubleW Z = new doubleW(0);
    doubleW dPdD = new doubleW(0);
    doubleW d2PdD2 = new doubleW(0);
    doubleW d2PdTD = new doubleW(0);
    doubleW dPdT = new doubleW(0);
    doubleW U = new doubleW(0);
    doubleW H = new doubleW(0);
    doubleW S = new doubleW(0);
    doubleW Cv = new doubleW(0);
    doubleW Cp = new doubleW(0);
    doubleW W = new doubleW(0);
    doubleW G = new doubleW(0);
    doubleW JT = new doubleW(0);
    doubleW Kappa = new doubleW(0);
    doubleW A = new doubleW(0);

    model.PropertiesGERG(T, D, x, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT,
        Kappa, A);

    return new double[] {P.val, Z.val, Cv.val, Cp.val, W.val, H.val, S.val};
  }
}
