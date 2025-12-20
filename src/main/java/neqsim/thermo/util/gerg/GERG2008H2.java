package neqsim.thermo.util.gerg;

import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * GERG-2008-H2 class.
 *
 * <p>
 * Extension of the GERG-2008 equation of state with improved hydrogen parameters as described in:
 * "Extension of the equation of state for natural gases GERG-2008 with improved hydrogen
 * parameters" by Beckmüller et al. (2022). This class provides more accurate thermodynamic property
 * calculations for hydrogen-rich natural gas mixtures.
 * </p>
 *
 * <p>
 * The improvements include:
 * </p>
 * <ul>
 * <li>Updated binary reducing parameters for hydrogen with methane, nitrogen, CO2, and other
 * hydrocarbons</li>
 * <li>Revised departure function parameters for hydrogen binary mixtures</li>
 * <li>Extended validation range for hydrogen-containing mixtures</li>
 * </ul>
 *
 * <p>
 * Reference: Beckmüller, R., Thol, M., Sampson, I., Lemmon, E.W., Span, R. (2022). "Extension of
 * the equation of state for natural gases GERG-2008 with improved hydrogen parameters". Fluid Phase
 * Equilibria, 557, 113411.
 * </p>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class GERG2008H2 extends GERG2008 {

  // Extended model numbers for hydrogen departure functions
  // Model 8 = H2-N2, Model 9 = H2-CO2
  private static final int MODEL_H2_N2 = 8;
  private static final int MODEL_H2_CO2 = 9;

  /**
   * Default constructor for GERG2008H2.
   */
  public GERG2008H2() {
    super();
  }

  /**
   * Setup GERG-2008-H2 equation of state with improved hydrogen parameters.
   *
   * <p>
   * This method initializes all the constants and parameters in the GERG-2008-H2 model, including
   * the updated hydrogen binary interaction parameters and departure function coefficients.
   * </p>
   */
  @Override
  public void SetupGERG() {
    // First call the parent class setup to initialize all standard GERG-2008 parameters
    super.SetupGERG();

    // Now override with improved hydrogen parameters from GERG-2008-H2
    setupImprovedHydrogenParameters();
  }

  /**
   * Set up the improved hydrogen binary interaction parameters and departure functions.
   *
   * <p>
   * These parameters are from Beckmüller et al. (2022) and provide improved accuracy for
   * hydrogen-containing natural gas mixtures.
   * </p>
   */
  private void setupImprovedHydrogenParameters() {
    // Hydrogen is component 15 in GERG-2008

    // ========================================================================
    // Updated binary reducing parameters for hydrogen mixtures
    // Format: bvij, gvij, btij, gtij
    // ========================================================================

    // CH4-H2 (Methane-Hydrogen) - Index [1][15]
    bvij[1][15] = 0.996891814;
    gvij[1][15] = 1.016844787;
    btij[1][15] = 1.025175401;
    gtij[1][15] = 1.330577088;

    // N2-H2 (Nitrogen-Hydrogen) - Index [2][15]
    bvij[2][15] = 0.988448262;
    gvij[2][15] = 0.936989021;
    btij[2][15] = 0.963541444;
    gtij[2][15] = 1.162839224;

    // CO2-H2 (Carbon dioxide-Hydrogen) - Index [3][15]
    bvij[3][15] = 0.898378766;
    gvij[3][15] = 1.131683697;
    btij[3][15] = 0.945958207;
    gtij[3][15] = 1.778653182;

    // C2H6-H2 (Ethane-Hydrogen) - Index [4][15]
    bvij[4][15] = 0.948916448;
    gvij[4][15] = 1.106435287;
    btij[4][15] = 0.989269676;
    gtij[4][15] = 1.847174327;

    // C3H8-H2 (Propane-Hydrogen) - Index [5][15]
    bvij[5][15] = 0.987587162;
    gvij[5][15] = 1.074539206;
    btij[5][15] = 1.002540626;
    gtij[5][15] = 2.208231628;

    // i-C4H10-H2 (Isobutane-Hydrogen) - Index [6][15]
    bvij[6][15] = 1.0;
    gvij[6][15] = 1.147595688;
    btij[6][15] = 1.0;
    gtij[6][15] = 1.895305393;

    // n-C4H10-H2 (n-Butane-Hydrogen) - Index [7][15]
    bvij[7][15] = 1.0;
    gvij[7][15] = 1.232939523;
    btij[7][15] = 1.0;
    gtij[7][15] = 2.509259945;

    // i-C5H12-H2 (Isopentane-Hydrogen) - Index [8][15]
    bvij[8][15] = 1.0;
    gvij[8][15] = 1.184340443;
    btij[8][15] = 1.0;
    gtij[8][15] = 1.996386669;

    // n-C5H12-H2 (n-Pentane-Hydrogen) - Index [9][15]
    bvij[9][15] = 1.0;
    gvij[9][15] = 1.188334783;
    btij[9][15] = 1.0;
    gtij[9][15] = 2.013859174;

    // C6H14-H2 (Hexane-Hydrogen) - Index [10][15]
    bvij[10][15] = 1.0;
    gvij[10][15] = 1.243461678;
    btij[10][15] = 1.0;
    gtij[10][15] = 3.021197546;

    // C7H16-H2 (Heptane-Hydrogen) - Index [11][15]
    bvij[11][15] = 1.0;
    gvij[11][15] = 1.159131722;
    btij[11][15] = 1.0;
    gtij[11][15] = 3.169143057;

    // C8H18-H2 (Octane-Hydrogen) - Index [12][15]
    bvij[12][15] = 1.0;
    gvij[12][15] = 1.305249405;
    btij[12][15] = 1.0;
    gtij[12][15] = 2.191555216;

    // C9H20-H2 (Nonane-Hydrogen) - Index [13][15]
    bvij[13][15] = 1.0;
    gvij[13][15] = 1.342647661;
    btij[13][15] = 1.0;
    gtij[13][15] = 2.23435404;

    // C10H22-H2 (Decane-Hydrogen) - Index [14][15]
    bvij[14][15] = 1.695358382;
    gvij[14][15] = 1.120233729;
    btij[14][15] = 1.064818089;
    gtij[14][15] = 3.786003724;

    // ========================================================================
    // Updated departure function parameters (fij) for hydrogen mixtures
    // ========================================================================

    // CH4-H2 departure function factor
    fij[1][15] = 1.0;
    fij[15][1] = 1.0;

    // N2-H2 departure function factor (new in GERG-2008-H2)
    fij[2][15] = 1.0;
    fij[15][2] = 1.0;

    // CO2-H2 departure function factor (new in GERG-2008-H2)
    fij[3][15] = 1.0;
    fij[15][3] = 1.0;

    // ========================================================================
    // Updated model numbers for hydrogen departure functions
    // ========================================================================

    // Model 7 = CH4-H2 (same as GERG-2008)
    mNumb[1][15] = 7;

    // Model 8 = N2-H2 (new in GERG-2008-H2)
    mNumb[2][15] = MODEL_H2_N2;

    // Model 9 = CO2-H2 (new in GERG-2008-H2)
    mNumb[3][15] = MODEL_H2_CO2;

    // ========================================================================
    // Setup departure function terms for CH4-H2 (Model 7 - updated coefficients)
    // ========================================================================
    kpolij[7] = 4;
    kexpij[7] = 0;

    // CH4-H2 departure function coefficients
    dijk[7][1] = 1;
    tijk[7][1] = 2.0;
    cijk[7][1] = 0;
    eijk[7][1] = 0;
    gijk[7][1] = 0;
    nijk[7][1] = -0.25157134971934;

    dijk[7][2] = 3;
    tijk[7][2] = -1.0;
    cijk[7][2] = 0;
    eijk[7][2] = 0;
    gijk[7][2] = 0;
    nijk[7][2] = -6.2203841111983E-03;

    dijk[7][3] = 3;
    tijk[7][3] = 1.75;
    cijk[7][3] = 0;
    eijk[7][3] = 0;
    gijk[7][3] = 0;
    nijk[7][3] = 0.088850315184396;

    dijk[7][4] = 4;
    tijk[7][4] = 1.4;
    cijk[7][4] = 0;
    eijk[7][4] = 0;
    gijk[7][4] = 0;
    nijk[7][4] = -0.035592212573239;

    // ========================================================================
    // Setup departure function terms for N2-H2 (Model 8 - new in GERG-2008-H2)
    // ========================================================================
    kpolij[MODEL_H2_N2] = 4;
    kexpij[MODEL_H2_N2] = 4;

    // N2-H2 polynomial terms
    dijk[MODEL_H2_N2][1] = 1;
    tijk[MODEL_H2_N2][1] = 0.5;
    cijk[MODEL_H2_N2][1] = 0;
    eijk[MODEL_H2_N2][1] = 0;
    gijk[MODEL_H2_N2][1] = 0;
    nijk[MODEL_H2_N2][1] = 0.115469943;

    dijk[MODEL_H2_N2][2] = 2;
    tijk[MODEL_H2_N2][2] = 1.0;
    cijk[MODEL_H2_N2][2] = 0;
    eijk[MODEL_H2_N2][2] = 0;
    gijk[MODEL_H2_N2][2] = 0;
    nijk[MODEL_H2_N2][2] = -0.282509987;

    dijk[MODEL_H2_N2][3] = 3;
    tijk[MODEL_H2_N2][3] = 0.75;
    cijk[MODEL_H2_N2][3] = 0;
    eijk[MODEL_H2_N2][3] = 0;
    gijk[MODEL_H2_N2][3] = 0;
    nijk[MODEL_H2_N2][3] = 0.041989239;

    dijk[MODEL_H2_N2][4] = 4;
    tijk[MODEL_H2_N2][4] = 2.5;
    cijk[MODEL_H2_N2][4] = 0;
    eijk[MODEL_H2_N2][4] = 0;
    gijk[MODEL_H2_N2][4] = 0;
    nijk[MODEL_H2_N2][4] = 0.043587315;

    // N2-H2 exponential terms
    dijk[MODEL_H2_N2][5] = 2;
    tijk[MODEL_H2_N2][5] = 2.5;
    cijk[MODEL_H2_N2][5] = 1.0;
    eijk[MODEL_H2_N2][5] = 0.5;
    gijk[MODEL_H2_N2][5] = 0.5;
    nijk[MODEL_H2_N2][5] = -0.235379238;

    dijk[MODEL_H2_N2][6] = 2;
    tijk[MODEL_H2_N2][6] = 3.5;
    cijk[MODEL_H2_N2][6] = 0.5;
    eijk[MODEL_H2_N2][6] = 0.5;
    gijk[MODEL_H2_N2][6] = 0.5;
    nijk[MODEL_H2_N2][6] = 0.295267356;

    dijk[MODEL_H2_N2][7] = 3;
    tijk[MODEL_H2_N2][7] = 1.5;
    cijk[MODEL_H2_N2][7] = 0.25;
    eijk[MODEL_H2_N2][7] = 0.5;
    gijk[MODEL_H2_N2][7] = 0.5;
    nijk[MODEL_H2_N2][7] = -0.088131779;

    dijk[MODEL_H2_N2][8] = 1;
    tijk[MODEL_H2_N2][8] = 4.0;
    cijk[MODEL_H2_N2][8] = 0.75;
    eijk[MODEL_H2_N2][8] = 0.5;
    gijk[MODEL_H2_N2][8] = 0.5;
    nijk[MODEL_H2_N2][8] = 0.068170879;

    // ========================================================================
    // Setup departure function terms for CO2-H2 (Model 9 - new in GERG-2008-H2)
    // ========================================================================
    kpolij[MODEL_H2_CO2] = 4;
    kexpij[MODEL_H2_CO2] = 4;

    // CO2-H2 polynomial terms
    dijk[MODEL_H2_CO2][1] = 1;
    tijk[MODEL_H2_CO2][1] = 0.25;
    cijk[MODEL_H2_CO2][1] = 0;
    eijk[MODEL_H2_CO2][1] = 0;
    gijk[MODEL_H2_CO2][1] = 0;
    nijk[MODEL_H2_CO2][1] = 0.387689831;

    dijk[MODEL_H2_CO2][2] = 2;
    tijk[MODEL_H2_CO2][2] = 1.125;
    cijk[MODEL_H2_CO2][2] = 0;
    eijk[MODEL_H2_CO2][2] = 0;
    gijk[MODEL_H2_CO2][2] = 0;
    nijk[MODEL_H2_CO2][2] = -0.674138583;

    dijk[MODEL_H2_CO2][3] = 3;
    tijk[MODEL_H2_CO2][3] = 0.5;
    cijk[MODEL_H2_CO2][3] = 0;
    eijk[MODEL_H2_CO2][3] = 0;
    gijk[MODEL_H2_CO2][3] = 0;
    nijk[MODEL_H2_CO2][3] = 0.093893736;

    dijk[MODEL_H2_CO2][4] = 4;
    tijk[MODEL_H2_CO2][4] = 2.0;
    cijk[MODEL_H2_CO2][4] = 0;
    eijk[MODEL_H2_CO2][4] = 0;
    gijk[MODEL_H2_CO2][4] = 0;
    nijk[MODEL_H2_CO2][4] = 0.078899518;

    // CO2-H2 exponential terms
    dijk[MODEL_H2_CO2][5] = 2;
    tijk[MODEL_H2_CO2][5] = 3.0;
    cijk[MODEL_H2_CO2][5] = 1.0;
    eijk[MODEL_H2_CO2][5] = 0.5;
    gijk[MODEL_H2_CO2][5] = 0.5;
    nijk[MODEL_H2_CO2][5] = -0.413481533;

    dijk[MODEL_H2_CO2][6] = 2;
    tijk[MODEL_H2_CO2][6] = 4.0;
    cijk[MODEL_H2_CO2][6] = 0.5;
    eijk[MODEL_H2_CO2][6] = 0.5;
    gijk[MODEL_H2_CO2][6] = 0.5;
    nijk[MODEL_H2_CO2][6] = 0.518836202;

    dijk[MODEL_H2_CO2][7] = 3;
    tijk[MODEL_H2_CO2][7] = 2.0;
    cijk[MODEL_H2_CO2][7] = 0.25;
    eijk[MODEL_H2_CO2][7] = 0.5;
    gijk[MODEL_H2_CO2][7] = 0.5;
    nijk[MODEL_H2_CO2][7] = -0.116925521;

    dijk[MODEL_H2_CO2][8] = 1;
    tijk[MODEL_H2_CO2][8] = 5.0;
    cijk[MODEL_H2_CO2][8] = 0.75;
    eijk[MODEL_H2_CO2][8] = 0.5;
    gijk[MODEL_H2_CO2][8] = 0.5;
    nijk[MODEL_H2_CO2][8] = 0.089453488;

    // ========================================================================
    // Recalculate binary parameters with the new hydrogen values
    // ========================================================================
    recalculateBinaryParameters();
  }

  /**
   * Recalculate the derived binary parameters after updating the hydrogen coefficients.
   *
   * <p>
   * This method recalculates gvij, gtij, bvij, btij for hydrogen pairs using the same formulas as
   * in the parent SetupGERG method.
   * </p>
   */
  private void recalculateBinaryParameters() {
    double o13 = 1.0 / 3.0;

    // Recalculate for hydrogen pairs (j=15)
    int j = 15;
    for (int i = 1; i <= 14; ++i) {
      // Store the original values for recalculation
      double bvijOrig = bvij[i][j];
      double gvijOrig = gvij[i][j];
      double btijOrig = btij[i][j];
      double gtijOrig = gtij[i][j];

      // Calculate Vc3 and Tc2 if not already done
      double Vc3i = 1 / Math.pow(Dc[i], o13) / 2;
      double Vc3j = 1 / Math.pow(Dc[j], o13) / 2;
      double Tc2i = Math.sqrt(Tc[i]);
      double Tc2j = Math.sqrt(Tc[j]);

      // Apply the transformations as in parent SetupGERG
      gvij[i][j] = gvijOrig * bvijOrig * Math.pow(Vc3i + Vc3j, 3);
      gtij[i][j] = gtijOrig * btijOrig * Tc2i * Tc2j;
      bvij[i][j] = Math.pow(bvijOrig, 2);
      btij[i][j] = Math.pow(btijOrig, 2);
    }

    // Apply transformations for the new departure function models
    double[][] bijk = new double[MaxMdl + 1][MaxTrmM + 1];

    // Process model 8 (N2-H2)
    for (int k = 1; k <= kpolij[MODEL_H2_N2] + kexpij[MODEL_H2_N2]; ++k) {
      bijk[MODEL_H2_N2][k] = 0; // These are already 0 from initialization
      double origCijk = cijk[MODEL_H2_N2][k];
      double origEijk = eijk[MODEL_H2_N2][k];
      double origGijk = gijk[MODEL_H2_N2][k];

      gijk[MODEL_H2_N2][k] = -origCijk * Math.pow(origEijk, 2) + bijk[MODEL_H2_N2][k] * origGijk;
      eijk[MODEL_H2_N2][k] = 2 * origCijk * origEijk - bijk[MODEL_H2_N2][k];
      cijk[MODEL_H2_N2][k] = -origCijk;
    }

    // Process model 9 (CO2-H2)
    for (int k = 1; k <= kpolij[MODEL_H2_CO2] + kexpij[MODEL_H2_CO2]; ++k) {
      bijk[MODEL_H2_CO2][k] = 0;
      double origCijk = cijk[MODEL_H2_CO2][k];
      double origEijk = eijk[MODEL_H2_CO2][k];
      double origGijk = gijk[MODEL_H2_CO2][k];

      gijk[MODEL_H2_CO2][k] = -origCijk * Math.pow(origEijk, 2) + bijk[MODEL_H2_CO2][k] * origGijk;
      eijk[MODEL_H2_CO2][k] = 2 * origCijk * origEijk - bijk[MODEL_H2_CO2][k];
      cijk[MODEL_H2_CO2][k] = -origCijk;
    }
  }

  /**
   * Main method for testing GERG-2008-H2 implementation.
   *
   * @param args command line arguments (not used)
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    GERG2008H2 test = new GERG2008H2();
    test.SetupGERG();

    double T = 300.0;
    doubleW D = new doubleW(0.0);
    doubleW P = new doubleW(10000.0);
    intW ierr = new intW(0);
    doubleW Mm = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    int iFlag = 0;
    StringW herr = new StringW("");

    // Hydrogen-rich mixture test case
    // 80% methane, 15% hydrogen, 5% nitrogen
    double[] x = new double[22];
    x[1] = 0.80; // Methane
    x[2] = 0.05; // Nitrogen
    x[15] = 0.15; // Hydrogen

    test.MolarMassGERG(x, Mm);
    System.out.println("GERG-2008-H2 Test Results for Hydrogen-Rich Mixture");
    System.out.println("====================================================");
    System.out.println("Composition: 80% CH4, 5% N2, 15% H2");
    System.out.println("Temperature: " + T + " K");
    System.out.println("Pressure: " + P.val + " kPa");
    System.out.println();
    System.out.println("Molar mass [g/mol]: " + Mm.val);

    test.DensityGERG(iFlag, T, P.val, x, D, ierr, herr);
    System.out.println("Molar density [mol/l]: " + D.val);
    System.out.println("Error code: " + ierr.val);
    if (ierr.val != 0) {
      System.out.println("Error message: " + herr.val);
    }

    test.PressureGERG(T, D.val, x, P, Z);
    System.out.println("Calculated pressure [kPa]: " + P.val);
    System.out.println("Compressibility factor Z: " + Z.val);

    doubleW dPdD = new doubleW(0.0);
    doubleW d2PdD2 = new doubleW(0.0);
    doubleW d2PdTD = new doubleW(0.0);
    doubleW dPdT = new doubleW(0.0);
    doubleW U = new doubleW(0.0);
    doubleW H = new doubleW(0.0);
    doubleW S = new doubleW(0.0);
    doubleW A = new doubleW(0.0);
    doubleW Cv = new doubleW(0.0);
    doubleW Cp = new doubleW(0.0);
    doubleW W = new doubleW(0.0);
    doubleW G = new doubleW(0.0);
    doubleW JT = new doubleW(0.0);
    doubleW Kappa = new doubleW(0.0);

    test.PropertiesGERG(T, D.val, x, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT,
        Kappa, A);

    System.out.println();
    System.out.println("Thermodynamic Properties:");
    System.out.println("Internal energy [J/mol]: " + U.val);
    System.out.println("Enthalpy [J/mol]: " + H.val);
    System.out.println("Entropy [J/(mol·K)]: " + S.val);
    System.out.println("Cv [J/(mol·K)]: " + Cv.val);
    System.out.println("Cp [J/(mol·K)]: " + Cp.val);
    System.out.println("Speed of sound [m/s]: " + W.val);
    System.out.println("Gibbs energy [J/mol]: " + G.val);
    System.out.println("Joule-Thomson coefficient [K/kPa]: " + JT.val);
    System.out.println("Isentropic exponent: " + Kappa.val);

    // Pure hydrogen test
    System.out.println();
    System.out.println("====================================================");
    System.out.println("Pure Hydrogen Test");
    System.out.println("====================================================");

    double[] xH2 = new double[22];
    xH2[15] = 1.0; // Pure hydrogen

    test.MolarMassGERG(xH2, Mm);
    System.out.println("Molar mass [g/mol]: " + Mm.val);

    D.val = 0.0;
    test.DensityGERG(iFlag, T, 10000.0, xH2, D, ierr, herr);
    System.out.println("Molar density [mol/l]: " + D.val);

    test.PropertiesGERG(T, D.val, xH2, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT,
        Kappa, A);
    System.out.println("Compressibility factor Z: " + Z.val);
    System.out.println("Speed of sound [m/s]: " + W.val);
  }
}
