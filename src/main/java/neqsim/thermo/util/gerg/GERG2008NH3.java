package neqsim.thermo.util.gerg;

import org.netlib.util.doubleW;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * GERG-2008-NH3 class.
 *
 * <p>
 * Extension of the GERG-2008 equation of state with ammonia (NH3) as the 22nd component, based on:
 * Neumann, T., Thol, M., Bell, I.H., Lemmon, E.W., Span, R. (2020). "Fundamental Thermodynamic
 * Models for Mixtures Containing Ammonia". Fluid Phase Equilibria, 511, 112496. Corrigendum: Fluid
 * Phase Equilibria, 521, 112749 (2020) — corrects GBS parameter column labels in Tables 4 and 5.
 * </p>
 *
 * <p>
 * Pure ammonia EOS from: Gao, K., Wu, J., Zhang, P., Lemmon, E.W. (2020). JPCRD. The full 20-term
 * Gao reference EOS is evaluated directly for the pure component contribution (8 power + 10
 * Gaussian + 2 GaoB terms), bypassing the standard SFE-12 short-form approximation. This ensures
 * exact consistency between the reducing parameters (Tc=405.56K, rhoc=13.696 mol/L) and the
 * residual Helmholtz energy. Coefficients sourced from CoolProp (Gao-JPCRD-2020).
 * </p>
 *
 * <p>
 * The extension includes:
 * </p>
 * <ul>
 * <li>Pure ammonia residual and ideal gas Helmholtz energy parameters (SFE-12 form)</li>
 * <li>Binary reducing parameters for 6 pairs: NH3-CH4, NH3-N2, NH3-H2, NH3-Ar, NH3-CO, NH3-O2</li>
 * <li>Lorentz-Berthelot combining rules for the remaining 15 NH3 binary pairs</li>
 * <li>GBS (Gaussian Bell-Shaped) departure functions for NH3-Ar and NH3-H2 (Fij=1)</li>
 * <li>No departure functions for CH4, N2, CO, O2 (Fij=0, reducing parameters only)</li>
 * </ul>
 *
 * <p>
 * The GBS departure function form is: n_k * delta^d_k * tau^t_k * exp(-eta_k*(delta-eps_k)^2 -
 * beta_k*(tau-gamma_k)^2) This differs from the standard GERG-2008 exponential form which uses
 * exp(-eta*(delta-eps)^2 - beta*(delta-gamma)) — both Gaussians in delta, with the beta term
 * LINEAR. The GBS form has a QUADRATIC tau-Gaussian, requiring a separate evaluation path with
 * modified tau-derivatives.
 * </p>
 *
 * @author NeqSim team
 * @version 2.0
 */
public class GERG2008NH3 extends GERG2008 {

  /** GERG component index for ammonia. */
  private static final int NH3_IDX = 22;

  /** Number of NH3 departure function pairs (Ar+NH3, H2+NH3). */
  private static final int NH3_DEP_PAIRS = 2;

  /** Maximum polynomial terms per pair. */
  private static final int MAX_POL = 2;

  /** Maximum GBS terms per pair. */
  private static final int MAX_GBS = 2;

  // GBS departure function data — pair indices: 0 = Ar+NH3, 1 = H2+NH3
  /** Lower component index for each departure pair. */
  private int[] nh3DepI = new int[NH3_DEP_PAIRS];
  /** Departure function factor Fij for each pair. */
  private double[] nh3DepFij = new double[NH3_DEP_PAIRS];
  /** Number of polynomial terms per pair. */
  private int[] nh3DepKpol = new int[NH3_DEP_PAIRS];
  /** Number of GBS terms per pair. */
  private int[] nh3DepKgbs = new int[NH3_DEP_PAIRS];

  // Polynomial departure terms (non-integer d allowed)
  /** Polynomial term amplitudes [pair][term]. */
  private double[][] nh3PolN = new double[NH3_DEP_PAIRS][MAX_POL];
  /** Polynomial term d exponents [pair][term]. */
  private double[][] nh3PolD = new double[NH3_DEP_PAIRS][MAX_POL];
  /** Polynomial term t exponents [pair][term]. */
  private double[][] nh3PolT = new double[NH3_DEP_PAIRS][MAX_POL];

  // GBS departure terms
  /** GBS term amplitudes [pair][term]. */
  private double[][] nh3GbsN = new double[NH3_DEP_PAIRS][MAX_GBS];
  /** GBS term d exponents [pair][term]. */
  private double[][] nh3GbsD = new double[NH3_DEP_PAIRS][MAX_GBS];
  /** GBS term t exponents [pair][term]. */
  private double[][] nh3GbsT = new double[NH3_DEP_PAIRS][MAX_GBS];
  /** GBS term eta (delta Gaussian width) [pair][term]. */
  private double[][] nh3GbsEta = new double[NH3_DEP_PAIRS][MAX_GBS];
  /** GBS term epsilon (delta Gaussian center) [pair][term]. */
  private double[][] nh3GbsEps = new double[NH3_DEP_PAIRS][MAX_GBS];
  /** GBS term beta (tau Gaussian width) [pair][term]. */
  private double[][] nh3GbsBet = new double[NH3_DEP_PAIRS][MAX_GBS];
  /** GBS term gamma (tau Gaussian center) [pair][term]. */
  private double[][] nh3GbsGam = new double[NH3_DEP_PAIRS][MAX_GBS];

  // ========================================================================
  // Gao et al. (2020) full reference EOS for pure NH3
  // Coefficients from CoolProp dev/fluids/Ammonia.json (BibTeX: Gao-JPCRD-2020)
  // Tc=405.56 K, rhoc=13696 mol/m^3, R=8.3144598 J/(mol*K)
  // ========================================================================

  /** Number of power terms in Gao EOS. */
  private static final int GAO_NPOW = 8;
  /** Number of polynomial power terms (l=0). */
  private static final int GAO_NPOL = 5;
  /** Gao power term amplitudes. */
  private static final double[] gaoPowN = {0.006132232, 1.7395866, -2.2261792, -0.30127553,
      0.08967023, -0.076387037, -0.84063963, -0.27026327};
  /** Gao power term d exponents. */
  private static final int[] gaoPowD = {4, 1, 1, 2, 3, 3, 2, 3};
  /** Gao power term t exponents. */
  private static final double[] gaoPowT = {1.0, 0.382, 1.0, 1.0, 0.677, 2.915, 3.51, 1.063};
  /** Gao power term l exponents (0=polynomial, &gt;0=exponential). */
  private static final int[] gaoPowL = {0, 0, 0, 0, 0, 2, 2, 1};

  /** Number of Gaussian terms in Gao EOS. */
  private static final int GAO_NGAUSS = 10;
  /** Gao Gaussian term amplitudes. */
  private static final double[] gaoGaussN = {6.212578, -5.7844357, 2.4817542, -2.3739168,
      0.01493697, -3.7749264, 6.254348E-4, -1.7359E-5, -0.13462033, 0.07749072839};
  /** Gao Gaussian term d exponents. */
  private static final int[] gaoGaussD = {1, 1, 1, 2, 2, 1, 3, 3, 1, 1};
  /** Gao Gaussian term t exponents. */
  private static final double[] gaoGaussT =
      {0.655, 1.3, 3.1, 1.4395, 1.623, 0.643, 1.13, 4.5, 1.0, 4.0};
  /** Gao Gaussian term eta (delta Gaussian width). */
  private static final double[] gaoGaussEta =
      {0.42776, 0.6424, 0.8175, 0.7995, 0.91, 0.3574, 1.21, 4.14, 22.56, 22.68};
  /** Gao Gaussian term beta (tau Gaussian width). */
  private static final double[] gaoGaussBeta =
      {1.708, 1.4865, 2.0915, 2.43, 0.488, 1.1, 0.85, 1.14, 945.64, 993.85};
  /** Gao Gaussian term gamma (tau Gaussian center). */
  private static final double[] gaoGaussGamma =
      {1.036, 1.2777, 1.083, 1.2906, 0.928, 0.934, 0.919, 1.852, 1.05897, 1.05277};
  /** Gao Gaussian term epsilon (delta Gaussian center). */
  private static final double[] gaoGaussEps =
      {-0.0726, -0.1274, 0.7527, 0.57, 2.2, -0.243, 2.96, 3.02, 0.9574, 0.9576};

  /** Number of GaoB near-critical terms. */
  private static final int GAO_NGAOB = 2;
  /** GaoB term amplitudes. */
  private static final double[] gaoBN = {-1.6909858, 0.93739074};
  /** GaoB term d exponents. */
  private static final int[] gaoBD = {1, 1};
  /** GaoB term t exponents. */
  private static final double[] gaoBT = {4.3315, 4.015};
  /** GaoB term eta (stored negative for Gaussian damping). */
  private static final double[] gaoBEta = {-2.8452, -2.8342};
  /** GaoB term beta. */
  private static final double[] gaoBBeta = {0.3696, 0.2962};
  /** GaoB term gamma. */
  private static final double[] gaoBGamma = {1.108, 1.313};
  /** GaoB term epsilon. */
  private static final double[] gaoBEps = {0.4478, 0.44689};
  /** GaoB term b (denominator constant). */
  private static final double[] gaoBb = {1.244, 0.6826};

  /**
   * Default constructor for GERG2008NH3.
   */
  public GERG2008NH3() {
  }

  /**
   * Setup GERG-2008-NH3 equation of state with ammonia as the 22nd component.
   *
   * <p>
   * Initializes the standard GERG-2008 parameters for components 1-21, then adds ammonia pure
   * component parameters, binary reducing parameters for all 21 NH3 pairs, and GBS departure
   * functions for Ar+NH3 and H2+NH3.
   * </p>
   */
  @Override
  public void SetupGERG() {
    super.SetupGERG();
    NcGERG = 22;
    setupAmmoniaParameters();
  }

  /**
   * Set up all ammonia-related parameters.
   *
   * <p>
   * Parameters from Neumann et al. (2020) Tables 2-5 with corrigendum corrections.
   * </p>
   */
  private void setupAmmoniaParameters() {
    setupPureAmmoniaParameters();
    setupBinaryReducingParameters();
    setupNH3DepartureFunctions();
    recalculateAmmoniaParameters();
  }

  /**
   * Set up pure ammonia critical properties and ideal gas parameters.
   *
   * <p>
   * Critical properties from Gao et al. (2020) Table 2 of Neumann et al.: Tc = 405.56 K, rhoc =
   * 13.696 mol/L, M = 17.03052 g/mol. The standard SFE-12 residual contribution is disabled
   * (kpol=kexp=0); the full 20-term Gao reference EOS is evaluated in evaluateGaoPureNH3(). Ideal
   * gas PlanckEinstein parameters from Gao et al. (CoolProp Ammonia.json).
   * </p>
   */
  private void setupPureAmmoniaParameters() {
    double o13 = 1.0 / 3.0;

    // Critical properties from Gao et al. (2020) — Neumann et al. Table 2
    MMiGERG[NH3_IDX] = 17.03052;
    Dc[NH3_IDX] = 13.696;
    Tc[NH3_IDX] = 405.56;

    Vc3[NH3_IDX] = 1 / Math.pow(Dc[NH3_IDX], o13) / 2;
    Tc2[NH3_IDX] = Math.sqrt(Tc[NH3_IDX]);

    // Disable SFE-12: pure NH3 residual is evaluated by evaluateGaoPureNH3()
    kpol[NH3_IDX] = 0;
    kexp[NH3_IDX] = 0;

    // Ideal gas parameters (Gao et al. 2020, GERG notation)
    // LogTau coefficient: a=3 from Gao, raw GERG form = a+1 = 4
    n0i[NH3_IDX][3] = 4.0;
    // PlanckEinstein coefficients from Gao (CoolProp Ammonia.json)
    n0i[NH3_IDX][4] = 2.224;
    n0i[NH3_IDX][5] = 3.148;
    n0i[NH3_IDX][6] = 0.9579;
    n0i[NH3_IDX][7] = 0.0;
    // Lead term (reference state) — kept from original fit
    n0i[NH3_IDX][1] = 22.00218;
    n0i[NH3_IDX][2] = -5946.60;

    // Characteristic temperatures theta_k = t_k * Tc (Gao PlanckEinstein)
    th0i[NH3_IDX][4] = 1646.0;
    th0i[NH3_IDX][5] = 3965.0;
    th0i[NH3_IDX][6] = 7231.0;
    th0i[NH3_IDX][7] = 0.0;

    applyIdealGasTransformation(NH3_IDX);
  }

  /**
   * Apply the ideal gas parameter transformation for a single component.
   *
   * <p>
   * Replicates the transformation from SetupGERG (n0i adjustments for reference state and Rsr
   * scaling) for a newly added component.
   * </p>
   *
   * @param idx component index to transform
   */
  private void applyIdealGasTransformation(int idx) {
    double Rs = 8.31451;
    double Rsr = Rs / RGERG;
    double T0 = 298.15;
    double d0 = 101.325 / RGERG / T0;

    n0i[idx][3] = n0i[idx][3] - 1;
    n0i[idx][2] = n0i[idx][2] + T0;
    for (int j = 1; j <= 7; ++j) {
      n0i[idx][j] = Rsr * n0i[idx][j];
    }
    n0i[idx][2] = n0i[idx][2] - T0;
    n0i[idx][1] = n0i[idx][1] - Math.log(d0);
  }

  /**
   * Set up binary reducing parameters for all 21 NH3 binary pairs.
   *
   * <p>
   * Six pairs use fitted parameters from Neumann et al. (2020) Table 3: CH4+NH3, N2+NH3, H2+NH3,
   * Ar+NH3, CO+NH3, O2+NH3. The remaining 15 pairs use Lorentz-Berthelot combining rules (bvij =
   * btij = 1, gvij = gtij = 1). Only Ar+NH3 and H2+NH3 have departure functions (Fij=1); the other
   * four have Fij=0 (reducing parameters only). No model number assignments are made in the base
   * class arrays to avoid overwriting the GeneralizedAlkane model (model 10).
   * </p>
   */
  private void setupBinaryReducingParameters() {
    // Lorentz-Berthelot defaults for all NH3 pairs
    for (int i = 1; i <= 21; ++i) {
      bvij[i][NH3_IDX] = 1.0;
      gvij[i][NH3_IDX] = 1.0;
      btij[i][NH3_IDX] = 1.0;
      gtij[i][NH3_IDX] = 1.0;
    }

    // ========================================================================
    // Neumann et al. (2020) Table 3 — binary reducing parameters
    // Format: betaV, gammaV, betaT, gammaT, Fij
    // ========================================================================

    // CH4[1]+NH3 — Fij=0 (no departure function, reducing params only)
    bvij[1][NH3_IDX] = 1.022371;
    gvij[1][NH3_IDX] = 0.940156;
    btij[1][NH3_IDX] = 1.006058;
    gtij[1][NH3_IDX] = 1.069834;

    // N2[2]+NH3 — Fij=0
    bvij[2][NH3_IDX] = 1.057512;
    gvij[2][NH3_IDX] = 0.952705;
    btij[2][NH3_IDX] = 0.739937;
    gtij[2][NH3_IDX] = 1.447261;

    // H2[15]+NH3 — Fij=1 (has GBS departure function)
    bvij[15][NH3_IDX] = 0.98824;
    gvij[15][NH3_IDX] = 1.1266;
    btij[15][NH3_IDX] = 1.0103;
    gtij[15][NH3_IDX] = 0.7298;

    // O2[16]+NH3 — Fij=0, linear combining rule
    bvij[16][NH3_IDX] = 1.0;
    gvij[16][NH3_IDX] = 1.118566;
    btij[16][NH3_IDX] = 1.0;
    gtij[16][NH3_IDX] = 1.000002;

    // CO[17]+NH3 — Fij=0, reducing params transferred from N2+NH3 with fitted gammaT
    bvij[17][NH3_IDX] = 1.057512;
    gvij[17][NH3_IDX] = 0.952705;
    btij[17][NH3_IDX] = 0.739937;
    gtij[17][NH3_IDX] = 1.707;

    // Ar[21]+NH3 — Fij=1 (has GBS departure function)
    bvij[21][NH3_IDX] = 1.146326;
    gvij[21][NH3_IDX] = 0.998353;
    btij[21][NH3_IDX] = 0.756526;
    gtij[21][NH3_IDX] = 1.041113;

    // NOTE: No fij or mNumb assignments in the base class arrays.
    // Departure functions for Ar+NH3 and H2+NH3 are handled entirely in
    // the overridden AlpharGERG method via the GBS evaluation path.
    // This avoids overwriting the GeneralizedAlkane model (model 10)
    // which is shared by multiple standard GERG binary pairs.
  }

  /**
   * Set up GBS departure functions for Ar+NH3 and H2+NH3.
   *
   * <p>
   * Parameters from Neumann et al. (2020) Tables 4-5 with column label correction from the
   * corrigendum (FPE 521, 112749). The corrected column order for GBS terms is: n, d, t, eta,
   * epsilon, beta, gamma (the original paper had gamma and beta swapped).
   * </p>
   *
   * <p>
   * Ar+NH3 (Table 4): 1 polynomial + 2 GBS terms. H2+NH3 (Table 5): 2 polynomial + 2 GBS terms. All
   * terms use non-integer d exponents.
   * </p>
   */
  private void setupNH3DepartureFunctions() {
    // Pair 0: Ar[21]+NH3
    nh3DepI[0] = 21;
    nh3DepFij[0] = 1.0;
    nh3DepKpol[0] = 1;
    nh3DepKgbs[0] = 2;

    // Ar+NH3 polynomial term (k=1)
    nh3PolN[0][0] = 0.02350785;
    nh3PolD[0][0] = 2.3;
    nh3PolT[0][0] = 3.0;

    // Ar+NH3 GBS terms (k=2,3) — corrected column order per corrigendum
    nh3GbsN[0][0] = -1.913776;
    nh3GbsD[0][0] = 1.65;
    nh3GbsT[0][0] = 1.0;
    nh3GbsEta[0][0] = 1.3;
    nh3GbsEps[0][0] = 0.6;
    nh3GbsBet[0][0] = 0.9;
    nh3GbsGam[0][0] = 0.31;

    nh3GbsN[0][1] = 1.624062;
    nh3GbsD[0][1] = 0.42;
    nh3GbsT[0][1] = 1.0;
    nh3GbsEta[0][1] = 1.5;
    nh3GbsEps[0][1] = 0.5;
    nh3GbsBet[0][1] = 1.5;
    nh3GbsGam[0][1] = 0.39;

    // Pair 1: H2[15]+NH3
    nh3DepI[1] = 15;
    nh3DepFij[1] = 1.0;
    nh3DepKpol[1] = 2;
    nh3DepKgbs[1] = 2;

    // H2+NH3 polynomial terms (k=1,2)
    nh3PolN[1][0] = -3.73558;
    nh3PolD[1][0] = 1.28;
    nh3PolT[1][0] = 1.0;

    nh3PolN[1][1] = -7.47092;
    nh3PolD[1][1] = 2.05;
    nh3PolT[1][1] = 2.0;

    // H2+NH3 GBS terms (k=3,4) — corrected column order per corrigendum
    nh3GbsN[1][0] = 1.98413;
    nh3GbsD[1][0] = 2.6;
    nh3GbsT[1][0] = 1.0;
    nh3GbsEta[1][0] = 0.61;
    nh3GbsEps[1][0] = 2.06;
    nh3GbsBet[1][0] = 0.79;
    nh3GbsGam[1][0] = 0.8;

    nh3GbsN[1][1] = 1.87191;
    nh3GbsD[1][1] = 3.13;
    nh3GbsT[1][1] = 2.0;
    nh3GbsEta[1][1] = 1.6;
    nh3GbsEps[1][1] = 1.74;
    nh3GbsBet[1][1] = 2.1;
    nh3GbsGam[1][1] = 1.62;
  }

  /**
   * Recalculate derived parameters for ammonia.
   *
   * <p>
   * Transforms binary reducing parameters (bvij, gvij, btij, gtij) from paper notation (beta,
   * gamma) to the code's internal form (beta^2, gamma*beta*(Vc3_i+Vc3_j)^3), matching the
   * transformation in the parent SetupGERG. No departure function transformation is needed since
   * NH3 departure functions are evaluated directly from the paper notation in the overridden
   * AlpharGERG.
   * </p>
   */
  private void recalculateAmmoniaParameters() {
    // Diagonal elements for NH3-NH3
    bvij[NH3_IDX][NH3_IDX] = 1;
    btij[NH3_IDX][NH3_IDX] = 1;
    gvij[NH3_IDX][NH3_IDX] = 1 / Dc[NH3_IDX];
    gtij[NH3_IDX][NH3_IDX] = Tc[NH3_IDX];

    // Transform reducing parameters for all NH3 pairs
    for (int i = 1; i <= 21; ++i) {
      double bvOrig = bvij[i][NH3_IDX];
      double gvOrig = gvij[i][NH3_IDX];
      double btOrig = btij[i][NH3_IDX];
      double gtOrig = gtij[i][NH3_IDX];

      gvij[i][NH3_IDX] = gvOrig * bvOrig * Math.pow(Vc3[i] + Vc3[NH3_IDX], 3);
      gtij[i][NH3_IDX] = gtOrig * btOrig * Tc2[i] * Tc2[NH3_IDX];
      bvij[i][NH3_IDX] = Math.pow(bvOrig, 2);
      btij[i][NH3_IDX] = Math.pow(btOrig, 2);
    }
  }

  /**
   * Calculate dimensionless residual Helmholtz energy and derivatives.
   *
   * <p>
   * Calls the parent GERG-2008 evaluation for all standard components and departure functions, then
   * adds the GBS departure contributions for Ar+NH3 and H2+NH3. The GBS form uses non-integer d
   * exponents and a tau-Gaussian damping term that cannot be represented in the standard GERG
   * departure function arrays.
   * </p>
   *
   * @param itau set to 1 to calculate tau derivatives, 0 otherwise
   * @param idelta reserved for future use
   * @param T temperature in K
   * @param D density in mol/L
   * @param x composition array (mole fractions, 1-indexed)
   * @param ar dimensionless Helmholtz energy derivatives array
   */
  @Override
  void AlpharGERG(int itau, int idelta, double T, double D, double[] x, doubleW[][] ar) {
    // Standard GERG-2008 evaluation (pure components + standard departure functions)
    // NH3 pure component contribution is zero (kpol=kexp=0), replaced by full Gao EOS below
    super.AlpharGERG(itau, idelta, T, D, x, ar);

    // Add full Gao et al. (2020) pure component contribution for NH3
    evaluateGaoPureNH3(itau, T, D, x, ar);

    // Add GBS departure contributions for NH3 pairs
    evaluateNH3Departure(itau, T, D, x, ar);
  }

  /**
   * Evaluate the full Gao et al. (2020) reference EOS for pure ammonia.
   *
   * <p>
   * Replaces the SFE-12 approximation with the complete 20-term Gao EOS: 8 power terms (5
   * polynomial + 3 exponential), 10 Gaussian terms, and 2 GaoB near-critical terms. Coefficients
   * from CoolProp (Gao-JPCRD-2020). The contribution is multiplied by x[NH3] and added to the
   * ar[][] derivatives array.
   * </p>
   * <p>
   * Term forms:
   * </p>
   * <ul>
   * <li>Polynomial: n * delta^d * tau^t</li>
   * <li>Exponential: n * delta^d * tau^t * exp(-delta^l)</li>
   * <li>Gaussian: n * delta^d * tau^t * exp(-eta*(delta-eps)^2 - beta*(tau-gamma)^2)</li>
   * <li>GaoB: n * delta^d * tau^t * exp(eta*(delta-eps)^2) * exp(1/(b+beta*(tau-gamma)^2))</li>
   * </ul>
   *
   * @param itau set to 1 to calculate tau derivatives
   * @param T temperature in K
   * @param D density in mol/L
   * @param x composition array
   * @param ar Helmholtz derivatives array (accumulated)
   */
  private void evaluateGaoPureNH3(int itau, double T, double D, double[] x, doubleW[][] ar) {
    if (x[NH3_IDX] <= epsilon) {
      return;
    }
    doubleW Tr = new doubleW(0.0);
    doubleW Dr = new doubleW(0.0);
    ReducingParametersGERG(x, Tr, Dr);

    double del = D / Dr.val;
    double tau = Tr.val / T;
    double lntau = Math.log(tau);
    double xi = x[NH3_IDX];

    // Precompute delta powers (max d=4 across all Gao terms)
    double del2 = del * del;
    double del3 = del2 * del;
    double del4 = del2 * del2;
    double[] delp = {1.0, del, del2, del3, del4};

    // Precompute exp(-delta^l) for l=1,2
    double expDel1 = Math.exp(-del);
    double expDel2 = Math.exp(-del2);

    // ---- Power terms (8 total: 5 polynomial l=0, 3 exponential l>0) ----
    for (int k = 0; k < GAO_NPOW; k++) {
      int d = gaoPowD[k];
      double t = gaoPowT[k];
      int l = gaoPowL[k];
      double ndt = xi * gaoPowN[k] * delp[d] * Math.exp(t * lntau);

      if (l == 0) {
        // Polynomial: n * delta^d * tau^t
        ar[0][1].val += ndt * d;
        ar[0][2].val += ndt * d * (d - 1);
        if (itau > 0) {
          ar[0][0].val += ndt;
          ar[1][0].val += ndt * t;
          ar[2][0].val += ndt * t * (t - 1);
          ar[1][1].val += ndt * t * d;
          ar[1][2].val += ndt * t * d * (d - 1);
          ar[0][3].val += ndt * d * (d - 1) * (d - 2);
        }
      } else {
        // Exponential: n * delta^d * tau^t * exp(-delta^l)
        ndt *= (l == 1) ? expDel1 : expDel2;
        double ex = l * delp[l];
        double ex2 = d - ex;
        double ex3 = ex2 * (ex2 - 1);
        ar[0][1].val += ndt * ex2;
        ar[0][2].val += ndt * (ex3 - l * ex);
        if (itau > 0) {
          ar[0][0].val += ndt;
          ar[1][0].val += ndt * t;
          ar[2][0].val += ndt * t * (t - 1);
          ar[1][1].val += ndt * t * ex2;
          ar[1][2].val += ndt * t * (ex3 - l * ex);
          ar[0][3].val += ndt * (ex3 * (ex2 - 2) - ex * (3 * ex2 - 3 + l) * l);
        }
      }
    }

    // ---- Gaussian: n*delta^d*tau^t*exp(-eta*(delta-eps)^2 - beta*(tau-gamma)^2) ----
    for (int k = 0; k < GAO_NGAUSS; k++) {
      int d = gaoGaussD[k];
      double t = gaoGaussT[k];
      double eta = gaoGaussEta[k];
      double eps = gaoGaussEps[k];
      double bet = gaoGaussBeta[k];
      double gam = gaoGaussGamma[k];

      double dDiff = del - eps;
      double tDiff = tau - gam;
      double sigDel = Math.exp(-eta * dDiff * dDiff);
      double sigTau = Math.exp(-bet * tDiff * tDiff);

      double ndt = xi * gaoGaussN[k] * delp[d] * Math.exp(t * lntau) * sigDel * sigTau;

      // Delta-derivative multipliers
      double cij0 = -eta * del2;
      double eij0 = 2.0 * eta * eps * del;
      double xid = d + 2.0 * cij0 + eij0;
      double xi2d = xid * xid - d + 2.0 * cij0;

      ar[0][1].val += ndt * xid;
      ar[0][2].val += ndt * xi2d;

      if (itau > 0) {
        double omega = t - 2.0 * bet * tau * tDiff;
        double omega2 = omega * omega - t - 2.0 * bet * tau * tau;

        ar[0][0].val += ndt;
        ar[1][0].val += ndt * omega;
        ar[2][0].val += ndt * omega2;
        ar[1][1].val += ndt * omega * xid;
        ar[1][2].val += ndt * omega * xi2d;
        ar[0][3].val += ndt * (xid * (xi2d - 2.0 * (d + 2.0 * cij0)) + 2.0 * d);
      }
    }

    // ---- GaoB: n*delta^d*tau^t*exp(eta*(delta-eps)^2)*exp(1/(b+beta*(tau-gamma)^2)) ----
    for (int k = 0; k < GAO_NGAOB; k++) {
      int d = gaoBD[k];
      double t = gaoBT[k];
      double eta = gaoBEta[k];
      double eps = gaoBEps[k];
      double bet = gaoBBeta[k];
      double gam = gaoBGamma[k];
      double bCoeff = gaoBb[k];

      double dDiff = del - eps;
      double tDiff = tau - gam;
      double psiDel = Math.exp(eta * dDiff * dDiff);
      double bigD = bCoeff + bet * tDiff * tDiff;
      double psiTau = Math.exp(1.0 / bigD);

      double ndt = xi * gaoBN[k] * delp[d] * Math.exp(t * lntau) * psiDel * psiTau;

      // Delta-derivative multipliers (A = eta, stored negative)
      double cij0 = eta * del2;
      double eij0 = -2.0 * eta * eps * del;
      double xid = d + 2.0 * cij0 + eij0;
      double xi2d = xid * xid - d + 2.0 * cij0;

      ar[0][1].val += ndt * xid;
      ar[0][2].val += ndt * xi2d;

      if (itau > 0) {
        // Tau-derivatives for GaoB: exp(1/D) where D = b + beta*(tau-gamma)^2
        double bigD2 = bigD * bigD;
        double bigD3 = bigD2 * bigD;
        double dhdtau = -2.0 * bet * tDiff / bigD2;
        double ct = t + tau * dhdtau;
        double d2hdtau2 = -2.0 * bet / bigD2 + 8.0 * bet * bet * tDiff * tDiff / bigD3;
        double dctdtau = dhdtau + tau * d2hdtau2;
        double ct2 = ct * ct - ct + tau * dctdtau;

        ar[0][0].val += ndt;
        ar[1][0].val += ndt * ct;
        ar[2][0].val += ndt * ct2;
        ar[1][1].val += ndt * ct * xid;
        ar[1][2].val += ndt * ct * xi2d;
        ar[0][3].val += ndt * (xid * (xi2d - 2.0 * (d + 2.0 * cij0)) + 2.0 * d);
      }
    }
  }

  /**
   * Evaluate GBS departure function contributions for Ar+NH3 and H2+NH3.
   *
   * <p>
   * For each pair, evaluates polynomial terms (n * delta^d * tau^t with non-integer d) and GBS
   * terms (n * delta^d * tau^t * exp(-eta*(delta-eps)^2 - beta*(tau-gamma)^2)). The GBS
   * tau-derivatives are:
   * </p>
   * <ul>
   * <li>omega = t - 2*beta*tau*(tau - gamma)</li>
   * <li>tau*d(ar)/d(tau) uses omega instead of t</li>
   * <li>tau^2*d^2(ar)/d(tau)^2 uses omega^2 - t - 2*beta*tau^2 instead of t*(t-1)</li>
   * </ul>
   *
   * @param itau set to 1 to calculate tau derivatives
   * @param T temperature in K
   * @param D density in mol/L
   * @param x composition array
   * @param ar Helmholtz derivatives array (accumulated)
   */
  private void evaluateNH3Departure(int itau, double T, double D, double[] x, doubleW[][] ar) {
    // Compute reducing parameters (uses cache from super call)
    doubleW Tr = new doubleW(0.0);
    doubleW Dr = new doubleW(0.0);
    ReducingParametersGERG(x, Tr, Dr);

    double del = D / Dr.val;
    double tau = Tr.val / T;
    double lndel = Math.log(del);

    for (int p = 0; p < NH3_DEP_PAIRS; ++p) {
      int i = nh3DepI[p];
      if (x[i] <= epsilon || x[NH3_IDX] <= epsilon) {
        continue;
      }
      double xijf = x[i] * x[NH3_IDX] * nh3DepFij[p];

      // Polynomial terms: n * delta^d * tau^t (non-integer d)
      for (int k = 0; k < nh3DepKpol[p]; ++k) {
        double d = nh3PolD[p][k];
        double t = nh3PolT[p][k];
        double ndt = xijf * nh3PolN[p][k] * Math.exp(d * lndel + t * Math.log(tau));

        ar[0][1].val += ndt * d;
        ar[0][2].val += ndt * d * (d - 1);
        if (itau > 0) {
          ar[0][0].val += ndt;
          ar[1][0].val += ndt * t;
          ar[2][0].val += ndt * t * (t - 1);
          ar[1][1].val += ndt * t * d;
          ar[1][2].val += ndt * t * d * (d - 1);
          ar[0][3].val += ndt * d * (d - 1) * (d - 2);
        }
      }

      // GBS terms: n * delta^d * tau^t * exp(-eta*(delta-eps)^2 - beta*(tau-gamma)^2)
      for (int k = 0; k < nh3DepKgbs[p]; ++k) {
        double d = nh3GbsD[p][k];
        double t = nh3GbsT[p][k];
        double eta = nh3GbsEta[p][k];
        double eps = nh3GbsEps[p][k];
        double bet = nh3GbsBet[p][k];
        double gam = nh3GbsGam[p][k];

        // Delta and tau Gaussian damping factors
        double dDiff = del - eps;
        double tDiff = tau - gam;
        double sigDel = Math.exp(-eta * dDiff * dDiff);
        double sigTau = Math.exp(-bet * tDiff * tDiff);

        double ndt =
            xijf * nh3GbsN[p][k] * Math.exp(d * lndel + t * Math.log(tau)) * sigDel * sigTau;

        // Delta-derivative multipliers (same structure as GERG exp with c=-eta, e=2*eta*eps)
        double cEff = -eta;
        double del2 = del * del;
        double cij0 = cEff * del2;
        double eij0 = 2.0 * eta * eps * del;
        double xi = d + 2.0 * cij0 + eij0;
        double xi2 = xi * xi - d + 2.0 * cij0;

        ar[0][1].val += ndt * xi;
        ar[0][2].val += ndt * xi2;

        if (itau > 0) {
          // Tau-derivative multipliers (GBS-specific: tau-Gaussian)
          double omega = t - 2.0 * bet * tau * tDiff;
          double omega2 = omega * omega - t - 2.0 * bet * tau * tau;

          ar[0][0].val += ndt;
          ar[1][0].val += ndt * omega;
          ar[2][0].val += ndt * omega2;
          ar[1][1].val += ndt * omega * xi;
          ar[1][2].val += ndt * omega * xi2;
          ar[0][3].val += ndt * (xi * (xi2 - 2.0 * (d + 2.0 * cij0)) + 2.0 * d);
        }
      }
    }
  }

  /**
   * Main method for testing GERG-2008-NH3 implementation.
   *
   * @param args command line arguments (not used)
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    GERG2008NH3 model = new GERG2008NH3();
    model.SetupGERG();
    System.out.println("GERG-2008-NH3 model initialized successfully.");
    System.out.println("NcGERG = " + model.NcGERG);
    System.out.println("NH3 Tc = " + model.Tc[NH3_IDX] + " K");
    System.out.println("NH3 Dc = " + model.Dc[NH3_IDX] + " mol/L");
    System.out.println("NH3 M = " + model.MMiGERG[NH3_IDX] + " g/mol");
  }
}
