package neqsim.thermo.phase;

import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentSAFTVRMie;

/**
 * Phase class for the SAFT-VR Mie equation of state following Lafitte et al. (2013).
 *
 * <p>
 * Implements the Helmholtz free energy as: A = A_ideal + A_mono + A_chain where A_mono comprises
 * hard-sphere reference plus first-order (a1) and second-order (a2) Barker-Henderson perturbation
 * terms for the Mie potential, and A_chain is the chain-connectivity correction. The perturbation
 * terms use the Sutherland mean-field energy a1S with effective packing fraction mapping and the B
 * correction, following the exact formulation of Lafitte et al. J. Chem. Phys. 139, 154504 (2013).
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseSAFTVRMie extends PhaseSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseSAFTVRMie.class);

  /** Cached molar volume for successive initial guess. */
  private transient double cachedMolarVolume = -1.0;

  /** Controls whether g_Mie perturbation corrections are used in chain term. */
  private transient boolean gMieCorrectionEnabled = true;

  /**
   * Blending fraction for g_Mie perturbation correction in chain term. 0.0 = g_HS(x0) only, 1.0 =
   * full g_Mie. Used by the continuation/homotopy solver.
   */
  private transient double gMieBlendFraction = 1.0;

  /** Toggle hard-sphere plus chain contribution (1 = on, 0 = off). */
  int useHS = 1;
  /** Toggle dispersion contribution (1 = on, 0 = off). */
  int useDISP = 1;

  // Core SAFT variables
  double volumeSAFT = 1.0;
  double nSAFT = 0.0;
  double dSAFT = 0.0;
  double dmeanSAFT = 0.0;
  double mSAFT = 1.0;
  double mmin1SAFT = 0.0;
  double ghsSAFT = 1.0;
  double aHSSAFT = 0.0;

  // Volume derivatives of packing fraction (w.r.t. V_m3, NO 1e-5 factor)
  double dnSAFTdV = 0.0;
  double dnSAFTdVdV = 0.0;

  // Hard-sphere derivatives w.r.t. eta
  double daHSSAFTdN = 0.0;
  double daHSSAFTdNdN = 0.0;
  double dgHSSAFTdN = 0.0;
  double dgHSSAFTdNdN = 0.0;

  // Temperature derivatives of packing fraction
  double dNSAFTdT = 0.0;
  double dNSAFTdTdT = 0.0;
  double dNSAFTdTdV = 0.0;

  // Lafitte 2013 first-order perturbation (a1 per NkT)
  double a1Disp = 0.0;
  double da1DispDeta = 0.0;
  double d2a1DispDeta2 = 0.0;
  double da1DispDT = 0.0;
  double d2a1DispDT2 = 0.0;
  double d2a1DispDetaDT = 0.0;

  // Lafitte 2013 second-order perturbation (a2 per NkT)
  double a2Disp = 0.0;
  double da2DispDeta = 0.0;
  double d2a2DispDeta2 = 0.0;
  double da2DispDT = 0.0;
  double d2a2DispDT2 = 0.0;
  double d2a2DispDetaDT = 0.0;

  // Lafitte 2013 third-order perturbation (a3 per NkT)
  double a3Disp = 0.0;
  double da3DispDeta = 0.0;
  double d2a3DispDeta2 = 0.0;
  double da3DispDT = 0.0;
  double d2a3DispDT2 = 0.0;
  double d2a3DispDetaDT = 0.0;

  /**
   * Per-component weighted pair-sum of dispersion terms: aDispPerComp[i] = sum_l xs_l *
   * (a1_il+a2_il+a3_il). Used for analytical dF_DISP/dNi computation. Only allocated for
   * multi-component systems.
   */
  double[] aDispPerComp = null;

  // ===== Association (CPA-style) fields =====
  /** Toggle association contribution (1 = on, 0 = off). */
  int useASSOC = 0;

  /** Total number of association sites across all components. */
  int totalNumberOfAssociationSites = 0;

  /** Site offset for component i: first site of comp i is siteOffset[i]. */
  int[] siteOffset = null;

  /**
   * Self-association scheme indicator. selfAssocScheme[comp][siteA][siteB] = 1 if site A can bond
   * with site B on the same component (0 otherwise).
   */
  int[][][] selfAssocScheme = null;

  /**
   * Cross-association scheme indicator. crossAssocScheme[compI][compJ][siteA][siteB] = 1 if site A
   * on comp I can bond with site B on comp J.
   */
  int[][][][] crossAssocScheme = null;

  /**
   * Association strength delta[siteI][siteJ] between flattened sites (global indexing). Delta =
   * scheme * (exp(eps_HB/RT) - 1) * sigma_ij^3 * NA * 1e5 * kappa_ij * g_HS.
   */
  double[][] deltaAssoc = null;

  /** Temperature derivative of delta. */
  double[][] deltadTAssoc = null;

  /** Sum of (1-XA)*ni over all sites: hcpatot = sum_i ni * sum_A (1 - X_Ai). */
  double hcpatot = 0.0;
  /** dh/dT. */
  double hcpatotdT = 0.0;
  /** d2h/dT2. */
  double hcpatotdTdT = 0.0;

  /** Association g (hard-sphere RDF used in delta). For SAFT-VR Mie this equals ghsSAFT. */
  double gcpaAssoc = 1.0;
  /** d(ln g)/dV in SI units (m^3). */
  double gcpavAssoc = 0.0;
  /** d2(ln g)/dV2 in SI units (m^6). */
  double gcpavvAssoc = 0.0;
  /** d3(ln g)/dV3 in SI units. */
  double gcpavvvAssoc = 0.0;

  /** Cached F_ASSOC value from volInit. */
  double cachedFAssoc = 0.0;
  /** Cached dF_ASSOC/dV_SI from volInit (m^-3). */
  double cacheddFAssocDV = 0.0;
  /** Cached d2F_ASSOC/dV_SI2 from volInit (m^-6), computed numerically. */
  double cacheddFAssocDVDV = 0.0;
  /** Whether cacheddFAssocDVDV is valid (computed after molar volume convergence). */
  boolean assocDVDVValid = false;

  /**
   * Effective packing fraction parameterization coefficients from Lafitte 2013. c_i(lambda) =
   * A[i][0] + A[i][1]/(lambda-3) + A[i][2]/(lambda-3)^2 + A[i][3]/(lambda-3)^3 Rows: c1, c2, c3,
   * c4.
   */
  static final double[][] etaEffCoeffs =
      {{0.81096, 1.7888, -37.578, 92.284}, {1.0205, -19.341, 151.26, -463.50},
          {-1.9057, 22.845, -228.14, 973.92}, {1.08850, -6.1962, 106.98, -677.64}};

  /**
   * Pad\u00e9 coefficient matrix for f1-f6 functions (Lafitte 2013 Table 3).
   *
   * <p>
   * Each column m (0-5) corresponds to function f_{m+1}. Rows 0-3: numerator coefficients (alpha^0
   * to alpha^3). Rows 4-6: denominator coefficients (alpha^1 to alpha^3; alpha^0 = 1). f_m(alpha) =
   * (phi[0][m] + phi[1][m]*alpha + phi[2][m]*alpha^2 + phi[3][m]*alpha^3) / (1 + phi[4][m]*alpha +
   * phi[5][m]*alpha^2 + phi[6][m]*alpha^3).
   * </p>
   */
  static final double[][] phiPade = {{7.5365557, -359.440, 1550.9, -1.199320, -1911.2800, 9236.9},
      {-37.604630, 1825.60, -5070.1, 9.063632, 21390.175, -129430.0},
      {71.745953, -3168.00, 6534.6, -17.94820, -51320.700, 357230.0},
      {-46.835520, 1884.20, -3288.7, 11.34027, 37064.540, -315530.0},
      {-2.4679820, -0.82376, -2.7171, 20.52142, 1103.7420, 1390.2},
      {-0.5027200, -3.19350, 2.0883, -56.63770, -3264.6100, -4518.2},
      {8.0956883, 3.70900, 0.0000, 40.53683, 2556.1810, 4241.6}};

  /**
   * Constructor for PhaseSAFTVRMie.
   */
  public PhaseSAFTVRMie() {}

  /** {@inheritDoc} */
  @Override
  public PhaseSAFTVRMie clone() {
    PhaseSAFTVRMie clonedPhase = null;
    try {
      clonedPhase = (PhaseSAFTVRMie) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentSAFTVRMie(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    if (initType == 0) {
      initAssociationSchemes(numberOfComponents);
    }

    // Note: PhaseEos.init() (via super) calls molarVolume() then Finit() for each component.
    // Do NOT call Finit() here before super.init() because the SAFT state (eta, aDisp, etc.)
    // has not been updated for the current composition/volume. The second Finit inside
    // PhaseEos.init() runs with correct state after molarVolume/volInit.
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);

    // Override phase type classification using SAFT packing fraction (eta).
    // PhaseEos.init() uses V/b > 1.75, which is a cubic EOS heuristic that
    // doesn't work for SAFT-VR Mie where getB() has different physical meaning.
    // Instead use eta: gas has eta < 0.15, liquid has eta > 0.15.
    if (initType != 0 && nSAFT > 0) {
      if (nSAFT < 0.15) {
        setType(PhaseType.GAS);
      }
      // For liquid/oil/aqueous, keep the classification from PhaseEos
      // (it correctly distinguishes OIL vs AQUEOUS by composition)
    }
  }

  /**
   * Detects associating components and builds the site-site bonding scheme arrays. Called once at
   * initType=0. Sets useASSOC=1 if any component has association sites.
   *
   * @param numberOfComponents number of components
   */
  private void initAssociationSchemes(int numberOfComponents) {
    // Count total association sites
    totalNumberOfAssociationSites = 0;
    siteOffset = new int[numberOfComponents];
    for (int i = 0; i < numberOfComponents; i++) {
      siteOffset[i] = totalNumberOfAssociationSites;
      // Only count sites for components that have VR Mie params AND association params
      if (hasAssociationParams(i)) {
        totalNumberOfAssociationSites += getComponent(i).getNumberOfAssociationSites();
      }
    }

    if (totalNumberOfAssociationSites == 0) {
      useASSOC = 0;
      return;
    }
    useASSOC = 1;

    // Build self-association scheme
    selfAssocScheme = new int[numberOfComponents][][];
    for (int i = 0; i < numberOfComponents; i++) {
      int nSitesI = hasAssociationParams(i) ? getComponent(i).getNumberOfAssociationSites() : 0;
      selfAssocScheme[i] = new int[nSitesI][nSitesI];
      if (nSitesI > 0) {
        setupAssociationScheme(selfAssocScheme[i], getComponent(i).getAssociationScheme(), nSitesI);
      }
    }

    // Build cross-association scheme
    crossAssocScheme = new int[numberOfComponents][numberOfComponents][][];
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        int nSitesI = hasAssociationParams(i) ? getComponent(i).getNumberOfAssociationSites() : 0;
        int nSitesJ = hasAssociationParams(j) ? getComponent(j).getNumberOfAssociationSites() : 0;
        crossAssocScheme[i][j] = new int[nSitesI][nSitesJ];
        if (i == j) {
          // Self interaction: copy self scheme
          for (int a = 0; a < nSitesI; a++) {
            for (int b = 0; b < nSitesJ; b++) {
              crossAssocScheme[i][j][a][b] = selfAssocScheme[i][a][b];
            }
          }
        } else if (nSitesI > 0 && nSitesJ > 0) {
          // Cross-association: use CR-1 combining rule
          setupCrossAssociationScheme(crossAssocScheme[i][j],
              getComponent(i).getAssociationScheme(), nSitesI,
              getComponent(j).getAssociationScheme(), nSitesJ);
        }
      }
    }

    // Allocate delta arrays (global site indexing)
    deltaAssoc = new double[totalNumberOfAssociationSites][totalNumberOfAssociationSites];
    deltadTAssoc = new double[totalNumberOfAssociationSites][totalNumberOfAssociationSites];

    // Initialize XA=1.0 on each component
    for (int i = 0; i < numberOfComponents; i++) {
      ComponentSAFTVRMie ci = (ComponentSAFTVRMie) getComponent(i);
      int nSites = hasAssociationParams(i) ? getComponent(i).getNumberOfAssociationSites() : 0;
      ci.initAssociationArrays(nSites);
    }
  }

  /**
   * Checks whether component i has valid SAFT-VR Mie association parameters (both VR Mie segment
   * params and association energy/volume must be set).
   *
   * @param compIndex component index
   * @return true if component has association params
   */
  private boolean hasAssociationParams(int compIndex) {
    return getComponent(compIndex).getNumberOfAssociationSites() > 0
        && getComponent(compIndex).getAssociationEnergySAFTVRMie() != 0.0
        && getComponent(compIndex).getAssociationVolumeSAFT() != 0.0
        && ((ComponentSAFTVRMie) getComponent(compIndex)).getSigmaSAFTVRMie() > 0;
  }

  /**
   * Sets up self-association scheme matrix for a given scheme type. Following the standard CPA/SAFT
   * convention: sites are split into electron donors and acceptors.
   *
   * @param scheme output matrix [nSites][nSites] to fill with 0/1
   * @param schemeName scheme name from database: "4C", "2B", "3B", "1A", "2A"
   * @param nSites number of sites
   */
  private void setupAssociationScheme(int[][] scheme, String schemeName, int nSites) {
    if (schemeName == null) {
      return;
    }

    if ("4C".equals(schemeName) && nSites >= 4) {
      // Sites 0,1 = electron donors (e.g. H), sites 2,3 = acceptors (e.g. lone pairs)
      // Donors bond with acceptors only
      scheme[0][2] = 1;
      scheme[0][3] = 1;
      scheme[1][2] = 1;
      scheme[1][3] = 1;
      scheme[2][0] = 1;
      scheme[2][1] = 1;
      scheme[3][0] = 1;
      scheme[3][1] = 1;
    } else if ("2B".equals(schemeName) && nSites >= 2) {
      // Site 0 = donor, site 1 = acceptor
      scheme[0][1] = 1;
      scheme[1][0] = 1;
    } else if ("3B".equals(schemeName) && nSites >= 3) {
      // Sites 0,1 = donors, site 2 = acceptor; donor-acceptor bonding
      scheme[0][2] = 1;
      scheme[1][2] = 1;
      scheme[2][0] = 1;
      scheme[2][1] = 1;
    } else if ("1A".equals(schemeName) && nSites >= 1) {
      // Single site: self-bonds
      scheme[0][0] = 1;
    } else if ("2A".equals(schemeName) && nSites >= 2) {
      // Two identical sites: all bond with all
      scheme[0][0] = 1;
      scheme[0][1] = 1;
      scheme[1][0] = 1;
      scheme[1][1] = 1;
    }
  }

  /**
   * Sets up cross-association scheme between two components using CR-1 combining rule. Donors on
   * one component bond with acceptors on the other.
   *
   * @param scheme output matrix [nSitesI][nSitesJ]
   * @param schemeI scheme name of component I
   * @param nSitesI number of sites on I
   * @param schemeJ scheme name of component J
   * @param nSitesJ number of sites on J
   */
  private void setupCrossAssociationScheme(int[][] scheme, String schemeI, int nSitesI,
      String schemeJ, int nSitesJ) {
    // Simple approach: identify donor/acceptor sites for each component
    // and allow cross donor-acceptor bonding
    boolean[] donorsI = getSiteDonors(schemeI, nSitesI);
    boolean[] acceptorsI = getSiteAcceptors(schemeI, nSitesI);
    boolean[] donorsJ = getSiteDonors(schemeJ, nSitesJ);
    boolean[] acceptorsJ = getSiteAcceptors(schemeJ, nSitesJ);

    for (int a = 0; a < nSitesI; a++) {
      for (int b = 0; b < nSitesJ; b++) {
        if ((donorsI[a] && acceptorsJ[b]) || (acceptorsI[a] && donorsJ[b])) {
          scheme[a][b] = 1;
        }
      }
    }
  }

  /**
   * Returns donor mask for sites of a given scheme type.
   *
   * @param schemeName scheme name
   * @param nSites number of sites
   * @return boolean array where true = donor
   */
  private boolean[] getSiteDonors(String schemeName, int nSites) {
    boolean[] donors = new boolean[nSites];
    if ("4C".equals(schemeName) && nSites >= 4) {
      donors[0] = true;
      donors[1] = true; // sites 0,1 are donors
    } else if ("2B".equals(schemeName) && nSites >= 2) {
      donors[0] = true;
    } else if ("3B".equals(schemeName) && nSites >= 3) {
      donors[0] = true;
      donors[1] = true;
    } else if ("1A".equals(schemeName) && nSites >= 1) {
      donors[0] = true;
    } else if ("2A".equals(schemeName) && nSites >= 2) {
      donors[0] = true;
      donors[1] = true;
    }
    return donors;
  }

  /**
   * Returns acceptor mask for sites of a given scheme type.
   *
   * @param schemeName scheme name
   * @param nSites number of sites
   * @return boolean array where true = acceptor
   */
  private boolean[] getSiteAcceptors(String schemeName, int nSites) {
    boolean[] acceptors = new boolean[nSites];
    if ("4C".equals(schemeName) && nSites >= 4) {
      acceptors[2] = true;
      acceptors[3] = true; // sites 2,3 are acceptors
    } else if ("2B".equals(schemeName) && nSites >= 2) {
      acceptors[1] = true;
    } else if ("3B".equals(schemeName) && nSites >= 3) {
      acceptors[2] = true;
    } else if ("1A".equals(schemeName) && nSites >= 1) {
      acceptors[0] = true;
    } else if ("2A".equals(schemeName) && nSites >= 2) {
      acceptors[0] = true;
      acceptors[1] = true;
    }
    return acceptors;
  }

  // ===== Volume initialization =====

  /**
   * Initializes all SAFT variables for the current molar volume. Called at each iteration of the
   * volume solver and after volume is converged.
   */
  public void volInit() {
    volumeSAFT = getVolume() * 1.0e-5;
    dmeanSAFT = calcdmeanSAFT();
    dSAFT = calcdSAFT();
    mSAFT = calcmSAFT();
    mmin1SAFT = calcmmin1SAFT();

    double nMoles = getNumberOfMolesInPhase();
    double naConst = ThermodynamicConstantsInterface.avagadroNumber;
    double piOver6 = ThermodynamicConstantsInterface.pi / 6.0;

    // Packing fraction eta = pi/6 * N_A * n_seg / V * d^3
    // where dSAFT = sum(xi * mi * di^3)
    nSAFT = piOver6 * naConst * nMoles / volumeSAFT * dSAFT;

    // Volume derivatives (w.r.t. V_m3, NO 1e-5 conversion)
    dnSAFTdV = -piOver6 * naConst * nMoles / (volumeSAFT * volumeSAFT) * dSAFT;
    dnSAFTdVdV = 2.0 * piOver6 * naConst * nMoles / Math.pow(volumeSAFT, 3.0) * dSAFT;

    // Temperature derivatives of packing fraction (through d(T))
    double dDdT = getdDSAFTdT();
    dNSAFTdT = piOver6 * naConst * nMoles / volumeSAFT * dDdT;
    dNSAFTdTdV = -piOver6 * naConst * nMoles / (volumeSAFT * volumeSAFT) * dDdT;
    dNSAFTdTdT = piOver6 * naConst * nMoles / volumeSAFT * getd2DSAFTdTdT();

    // Hard-sphere Helmholtz energy per mole: a_HS = (4eta - 3eta^2)/(1-eta)^2
    double eta = nSAFT;
    double om = 1.0 - eta;
    aHSSAFT = (4.0 * eta - 3.0 * eta * eta) / (om * om);
    daHSSAFTdN =
        (4.0 - 6.0 * eta) / (om * om) + 2.0 * (4.0 * eta - 3.0 * eta * eta) / (om * om * om);
    daHSSAFTdNdN = -6.0 / (om * om) + 2.0 * (4.0 - 6.0 * eta) / (om * om * om)
        + 2.0 * (4.0 - 6.0 * eta) / (om * om * om)
        + 6.0 * (4.0 * eta - 3.0 * eta * eta) / (om * om * om * om);

    // Hard-sphere radial distribution function at contact: g_HS
    // NOTE: the simple CS contact value is used as a base, then replaced by g_Mie below
    ghsSAFT = (1.0 - eta / 2.0) / (om * om * om);
    dgHSSAFTdN = -0.5 / (om * om * om) + 3.0 * (1.0 - eta / 2.0) / (om * om * om * om);
    dgHSSAFTdNdN = -0.5 * 3.0 / (om * om * om * om) + 3.0 * (-0.5) / (om * om * om * om)
        + 12.0 * (1.0 - eta / 2.0) / Math.pow(om, 5.0);

    // Compute Lafitte 2013 dispersion terms
    computeDispersionTerms();

    // === Chain contribution: Lafitte 2013 Eq. 20, 35 ===
    // F_chain = -n * sum_i x_i*(m_i - 1)*ln(g_Mie_ii(sigma_ii))
    // For mixtures, each component i uses its own sigma_ii/d_ii, epsilon_ii, lambda_r/a_ii
    // evaluated at the mixture packing fraction eta.
    // We compute the effective chain RDF as a weighted geometric mean:
    // ghsSAFT = exp(sum_i w_i * ln(g_Mie_ii)) where w_i = x_i*(m_i-1)/mmin1SAFT
    // so that F_chain = -n * mmin1SAFT * ln(ghsSAFT) is exact.
    if (mmin1SAFT > 1.0e-10 && eta > 1.0e-10 && eta < 0.55) {
      double alpha = gMieCorrectionEnabled ? gMieBlendFraction : 0.0;

      double lnGChainEff = calcLnGChainEffective(eta, alpha);

      if (Double.isFinite(lnGChainEff)) {
        ghsSAFT = Math.exp(lnGChainEff);

        if (ghsSAFT > 0.1) {
          // Numerical eta-derivatives for volume/temperature derivatives
          double dEtaG = Math.max(Math.abs(eta) * 1.0e-4, 1.0e-12);
          double etaPG = eta + dEtaG;
          double etaMG = Math.max(eta - dEtaG, 1.0e-15);
          dEtaG = (etaPG - etaMG) / 2.0;

          double lnGP = calcLnGChainEffective(etaPG, alpha);
          double lnGM = calcLnGChainEffective(etaMG, alpha);
          double gP = Math.exp(lnGP);
          double gM = Math.exp(lnGM);

          if (Double.isFinite(gP) && Double.isFinite(gM) && gP > 0 && gM > 0) {
            dgHSSAFTdN = (gP - gM) / (2.0 * dEtaG);
            dgHSSAFTdNdN = (gP - 2.0 * ghsSAFT + gM) / (dEtaG * dEtaG);
          }
        }
      }
    }

    // Association: compute g derivatives and delta for current volume, solve XA, compute hcpatot
    if (useASSOC > 0) {
      initAssocGDerivatives();
      computeDeltaAssoc();
      solveAssociation();
      calcAssocHCPA();

      // Cache F_ASSOC value
      cachedFAssoc = F_ASSOC_SAFT();

      // Analytical dF/dV from Q-function stationarity (Michelsen-Hendriks):
      // dF_assoc/dV = 0.5 * (1/V - d(ln g)/dV) * hcpatot
      // This is exact because at the converged XA, partial derivatives w.r.t. XA vanish.
      cacheddFAssocDV = 0.5 / volumeSAFT * (1.0 - volumeSAFT * gcpavAssoc) * hcpatot;

      // NOTE: cacheddFAssocDVDV is NOT computed here. It is computed on-demand
      // in dF_ASSOC_SAFTdVdV() via cloning + numerical differentiation, because the
      // Michelsen-Hendriks Q-function correction for d2F/dV2 requires the full implicit
      // XA response which cannot be captured by the product rule of the dFdV formula
      // (hcpatot is nearly constant for strong association like water).
      assocDVDVValid = false;
    }
  }

  /**
   * Recomputes the base SAFT quantities (nSAFT, dnSAFTdV, etc.) for the current molarVolume. This
   * is used during numerical association derivative computation to update the SAFT state without
   * running a full volInit (which would recurse).
   */
  private void recomputeSAFTBaseQuantities() {
    // Recompute the packing fraction and its volume derivatives for the current molarVolume
    volumeSAFT = getVolume() * 1.0e-5;
    double nMoles = getNumberOfMolesInPhase();
    double dAvg3 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      double xi = getComponent(i).getNumberOfMolesInPhase() / nMoles;
      dAvg3 += xi * getComponent(i).getmSAFTi()
          * Math.pow(((ComponentSAFTVRMie) getComponent(i)).getdSAFTi(), 3.0);
    }
    nSAFT = Math.PI / 6.0 * ThermodynamicConstantsInterface.avagadroNumber * dAvg3 / volumeSAFT;
    if (nSAFT < 0 || nSAFT > 0.55) {
      nSAFT = Math.max(1e-15, Math.min(0.54, nSAFT));
    }
    dnSAFTdV = -nSAFT / volumeSAFT;
    dnSAFTdVdV = 2.0 * nSAFT / (volumeSAFT * volumeSAFT);
  }

  /**
   * Computes the effective chain ln(g) as a composition-weighted average over components. ln(g_eff)
   * = sum_i [w_i * ln(g_Mie_ii)] where w_i = x_i*(m_i-1) / sum_j x_j*(m_j-1). For a
   * single-component system this reduces to ln(g_Mie_ii). For mixtures, components with m_i = 1 do
   * not contribute (their chain weight is zero).
   *
   * @param etaVal packing fraction
   * @param alpha g_Mie blend fraction (0 = g_HS only, 1 = full g_Mie)
   * @return weighted average ln(g)
   */
  private double calcLnGChainEffective(double etaVal, double alpha) {
    double lnGWeighted = 0.0;
    double totalWeight = 0.0;

    for (int i = 0; i < numberOfComponents; i++) {
      ComponentSAFTVRMie ci = (ComponentSAFTVRMie) getComponent(i);
      double xi = ci.getNumberOfMolesInPhase() / getNumberOfMolesInPhase();
      double wi = xi * (ci.getmSAFTi() - 1.0);
      if (wi < 1.0e-30) {
        continue;
      }
      double sigmaI = ci.getSigmaSAFTi();
      double dI = ci.getdSAFTi();
      double x0I = (dI > 0) ? sigmaI / dI : 1.0;
      double epsOvKTI = ci.getEpsikSAFT() / temperature;
      double lrI = ci.getLambdaRSAFTVRMie();
      double laI = ci.getLambdaASAFTVRMie();
      double cMieI = ComponentSAFTVRMie.calcMiePrefactor(lrI, laI);

      double gMieI = calcGMieBlended(etaVal, x0I, lrI, laI, epsOvKTI, cMieI, alpha);
      if (!Double.isFinite(gMieI) || gMieI <= 0) {
        continue;
      }
      lnGWeighted += wi * Math.log(gMieI);
      totalWeight += wi;
    }

    if (totalWeight < 1.0e-30) {
      return 0.0;
    }
    return lnGWeighted / totalWeight;
  }

  // ===== Lafitte 2013 Effective Packing Fraction =====

  /**
   * Effective packing fraction coefficient c_i(lambda) (Lafitte 2013 Table 5). c_i(lambda) =
   * A[i][0] + A[i][1]/lambda + A[i][2]/lambda^2 + A[i][3]/lambda^3.
   *
   * @param i coefficient index (0-3 for c1-c4)
   * @param lambda Mie exponent
   * @return c_i(lambda)
   */
  static double calcEtaEffCoeff(int i, double lambda) {
    double invL = 1.0 / lambda;
    return etaEffCoeffs[i][0] + etaEffCoeffs[i][1] * invL + etaEffCoeffs[i][2] * invL * invL
        + etaEffCoeffs[i][3] * invL * invL * invL;
  }

  /**
   * Effective packing fraction eta_eff = c1*eta + c2*eta^2 + c3*eta^3 + c4*eta^4.
   *
   * @param eta actual packing fraction
   * @param lambda Mie exponent
   * @return eta_eff
   */
  static double calcEtaEff(double eta, double lambda) {
    double c1 = calcEtaEffCoeff(0, lambda);
    double c2 = calcEtaEffCoeff(1, lambda);
    double c3 = calcEtaEffCoeff(2, lambda);
    double c4 = calcEtaEffCoeff(3, lambda);
    return c1 * eta + c2 * eta * eta + c3 * Math.pow(eta, 3.0) + c4 * Math.pow(eta, 4.0);
  }

  /**
   * d(eta_eff)/d(eta).
   *
   * @param eta actual packing fraction
   * @param lambda Mie exponent
   * @return derivative
   */
  static double calcDEtaEffDeta(double eta, double lambda) {
    double c1 = calcEtaEffCoeff(0, lambda);
    double c2 = calcEtaEffCoeff(1, lambda);
    double c3 = calcEtaEffCoeff(2, lambda);
    double c4 = calcEtaEffCoeff(3, lambda);
    return c1 + 2.0 * c2 * eta + 3.0 * c3 * eta * eta + 4.0 * c4 * Math.pow(eta, 3.0);
  }

  // ===== Lafitte 2013 Sutherland Mean-Field =====

  /**
   * First-order Sutherland mean-field energy a1S/(kT) (Lafitte 2013 Eq. 25).
   *
   * @param eta packing fraction
   * @param lambda Mie exponent
   * @param epsOverKT epsilon/(kT)
   * @return a1S_bar (dimensionless)
   */
  static double calcA1Sutherland(double eta, double lambda, double epsOverKT) {
    double etaEff = calcEtaEff(eta, lambda);
    double omEff = 1.0 - etaEff;
    return -12.0 * epsOverKT * eta / (lambda - 3.0) * (1.0 - etaEff / 2.0)
        / (omEff * omEff * omEff);
  }

  /**
   * B correction term (Lafitte 2013 Eq. 36) using I/J integrals and gHS contact value. B = 12 * eta
   * * (eps/kT) * [gHS(eta)*I(x0,lambda) - 9*eta*(1+eta)/(2*(1-eta)^3)*J(x0,lambda)] where I = (1 -
   * x0^(3-lambda))/(lambda-3) and J = (1 - (lambda-3)*x0^(4-lambda) + (lambda-4)*x0^(3-lambda)) /
   * ((lambda-3)*(lambda-4)).
   *
   * @param eta packing fraction
   * @param lambda Mie exponent
   * @param epsOverKT epsilon/(kT)
   * @param x0 sigma/d ratio
   * @return B (dimensionless, including 12*eta*eps/kT factor)
   */
  static double calcBCorrection(double eta, double lambda, double epsOverKT, double x0) {
    double x0_3ml = Math.pow(x0, 3.0 - lambda);
    double x0_4ml = Math.pow(x0, 4.0 - lambda);
    double capI = (1.0 - x0_3ml) / (lambda - 3.0);
    double capJ = (1.0 - (lambda - 3.0) * x0_4ml + (lambda - 4.0) * x0_3ml)
        / ((lambda - 3.0) * (lambda - 4.0));
    double om = 1.0 - eta;
    double om3 = om * om * om;
    double gHScontact = (1.0 - eta / 2.0) / om3;
    double bBar = gHScontact * capI - 9.0 * eta * (1.0 + eta) / (2.0 * om3) * capJ;
    return 12.0 * eta * epsOverKT * bBar;
  }

  /**
   * Full first-order Mie perturbation a1/(NkT) at a given eta value (Lafitte 2013 Eq. 40).
   *
   * @param eta packing fraction
   * @param lambdaR repulsive exponent
   * @param lambdaA attractive exponent
   * @param epsOverKT epsilon/(kT)
   * @param cMie Mie prefactor
   * @param x0 sigma/d ratio
   * @return a1_Mie (dimensionless)
   */
  public static double calcA1MieAtEta(double eta, double lambdaR, double lambdaA, double epsOverKT,
      double cMie, double x0) {
    double a1sA = calcA1Sutherland(eta, lambdaA, epsOverKT);
    double bA = calcBCorrection(eta, lambdaA, epsOverKT, x0);
    double a1sR = calcA1Sutherland(eta, lambdaR, epsOverKT);
    double bR = calcBCorrection(eta, lambdaR, epsOverKT, x0);
    return cMie * (Math.pow(x0, lambdaA) * (a1sA + bA) - Math.pow(x0, lambdaR) * (a1sR + bR));
  }

  // ===== Lafitte 2013 Second-Order Perturbation =====

  /**
   * Hard-sphere isothermal compressibility KHS = (1-eta)^4 / (1+4eta+4eta^2-4eta^3+eta^4).
   *
   * @param eta packing fraction
   * @return KHS
   */
  public static double calcKHS(double eta) {
    double num = Math.pow(1.0 - eta, 4.0);
    double den = 1.0 + 4.0 * eta + 4.0 * eta * eta - 4.0 * Math.pow(eta, 3.0) + Math.pow(eta, 4.0);
    return num / den;
  }

  /**
   * Alpha parameter for the Mie potential: alpha = C * [1/(lam_a-3) - 1/(lam_r-3)].
   *
   * @param lambdaR repulsive exponent
   * @param lambdaA attractive exponent
   * @return alpha
   */
  public static double calcMieAlpha(double lambdaR, double lambdaA) {
    double cMie = ComponentSAFTVRMie.calcMiePrefactor(lambdaR, lambdaA);
    return cMie * (1.0 / (lambdaA - 3.0) - 1.0 / (lambdaR - 3.0));
  }

  /**
   * Padé approximant function f_m(alpha) from Lafitte 2013 Table 3.
   *
   * @param funcIndex function index: 0=f1, 1=f2, 2=f3, 3=f4, 4=f5, 5=f6
   * @param alpha Mie alpha parameter
   * @return f_m(alpha)
   */
  public static double calcPadeF(int funcIndex, double alpha) {
    double a2 = alpha * alpha;
    double a3 = a2 * alpha;
    double num = phiPade[0][funcIndex] + phiPade[1][funcIndex] * alpha + phiPade[2][funcIndex] * a2
        + phiPade[3][funcIndex] * a3;
    double den = 1.0 + phiPade[4][funcIndex] * alpha + phiPade[5][funcIndex] * a2
        + phiPade[6][funcIndex] * a3;
    return num / den;
  }

  /**
   * Chi correction for second-order perturbation (Lafitte 2013 Eq. 42). chi = f1(alpha) * zetaSt +
   * f2(alpha) * zetaSt^5 + f3(alpha) * zetaSt^8, where zetaSt is the sigma-based packing fraction.
   *
   * @param zetaSt sigma-based packing fraction (pi/6 * rhoS * sigma^3)
   * @param lambdaR repulsive exponent
   * @param lambdaA attractive exponent
   * @return chi
   */
  static double calcChi(double zetaSt, double lambdaR, double lambdaA) {
    double alpha = calcMieAlpha(lambdaR, lambdaA);
    double f1 = calcPadeF(0, alpha);
    double f2 = calcPadeF(1, alpha);
    double f3 = calcPadeF(2, alpha);
    return f1 * zetaSt + f2 * Math.pow(zetaSt, 5) + f3 * Math.pow(zetaSt, 8);
  }

  /**
   * Full second-order Mie perturbation a2/(NkT) at a given eta value.
   *
   * @param eta packing fraction (d-based)
   * @param zetaSt sigma-based packing fraction
   * @param lambdaR repulsive exponent
   * @param lambdaA attractive exponent
   * @param epsOverKT epsilon/(kT)
   * @param cMie Mie prefactor
   * @param x0 sigma/d ratio
   * @return a2_Mie (dimensionless)
   */
  public static double calcA2MieAtEta(double eta, double zetaSt, double lambdaR, double lambdaA,
      double epsOverKT, double cMie, double x0) {
    double khs = calcKHS(eta);
    double chi = calcChi(zetaSt, lambdaR, lambdaA);

    double a1s2La = calcA1Sutherland(eta, 2.0 * lambdaA, epsOverKT);
    double b2La = calcBCorrection(eta, 2.0 * lambdaA, epsOverKT, x0);
    double a1sLaLr = calcA1Sutherland(eta, lambdaA + lambdaR, epsOverKT);
    double bLaLr = calcBCorrection(eta, lambdaA + lambdaR, epsOverKT, x0);
    double a1s2Lr = calcA1Sutherland(eta, 2.0 * lambdaR, epsOverKT);
    double b2Lr = calcBCorrection(eta, 2.0 * lambdaR, epsOverKT, x0);

    double inner = Math.pow(x0, 2.0 * lambdaA) * (a1s2La + b2La)
        - 2.0 * Math.pow(x0, lambdaA + lambdaR) * (a1sLaLr + bLaLr)
        + Math.pow(x0, 2.0 * lambdaR) * (a1s2Lr + b2Lr);

    return 0.5 * khs * (1.0 + chi) * epsOverKT * cMie * cMie * inner;
  }

  /**
   * Third-order Mie perturbation a3/(NkT) (Lafitte 2013 Eq. 50). a3 = -(eps/kT)^3 * f4(alpha) *
   * zetaSt * exp(f5(alpha) * zetaSt + f6(alpha) * zetaSt^2).
   *
   * @param zetaSt sigma-based packing fraction
   * @param lambdaR repulsive exponent
   * @param lambdaA attractive exponent
   * @param epsOverKT epsilon/(kT)
   * @return a3 (dimensionless)
   */
  public static double calcA3Mie(double zetaSt, double lambdaR, double lambdaA, double epsOverKT) {
    double alpha = calcMieAlpha(lambdaR, lambdaA);
    double f4 = calcPadeF(3, alpha);
    double f5 = calcPadeF(4, alpha);
    double f6 = calcPadeF(5, alpha);
    return -Math.pow(epsOverKT, 3.0) * f4 * zetaSt * Math.exp(f5 * zetaSt + f6 * zetaSt * zetaSt);
  }

  // ===== Chain contribution: g_Mie(σ) following Lafitte 2013 Eqs. 35-39 =====

  /**
   * Bare first-order Sutherland mean-field (without 12*eps*eta/kT prefactor). Following Clapeyron
   * convention: aS1_bare = -1/(lambda-3) * gCS(zetaEff).
   *
   * @param eta packing fraction
   * @param lambda Mie exponent
   * @return aS1 bare (dimensionless)
   */
  public static double calcAS1Bare(double eta, double lambda) {
    double zetaEff = calcEtaEff(eta, lambda);
    double om = 1.0 - zetaEff;
    double gCS = (1.0 - zetaEff / 2.0) / (om * om * om);
    return -gCS / (lambda - 3.0);
  }

  /**
   * Bare B correction (without 12*eps*eta/kT prefactor). Following Clapeyron convention.
   *
   * @param eta packing fraction
   * @param lambda Mie exponent
   * @param x0 sigma/d ratio
   * @return B bare (dimensionless)
   */
  public static double calcBBare(double eta, double lambda, double x0) {
    double x03l = Math.pow(x0, 3.0 - lambda);
    double capI = (1.0 - x03l) / (lambda - 3.0);
    double capJ = (1.0 - (lambda - 3.0) * Math.pow(x0, 4.0 - lambda) + (lambda - 4.0) * x03l)
        / ((lambda - 3.0) * (lambda - 4.0));
    double om = 1.0 - eta;
    double om3 = om * om * om;
    return capI * (1.0 - eta / 2.0) / om3 - 9.0 * capJ * eta * (eta + 1.0) / (2.0 * om3);
  }

  /**
   * Hard-sphere RDF at separation x0 = sigma/d using the parametric form from Lafitte 2013. At x0=1
   * this approximates the CS contact value. At x0 &gt; 1 it gives the correct RDF at sigma.
   *
   * @param eta packing fraction
   * @param x0 sigma/d ratio
   * @return g_d^HS(sigma)
   */
  public static double calcGHS_x0(double eta, double x0) {
    double om = 1.0 - eta;
    double om2 = om * om;
    double om3 = om2 * om;
    double eta2 = eta * eta;
    double eta3 = eta2 * eta;
    double eta4 = eta3 * eta;

    double k0 = -Math.log(om) + (42.0 * eta - 39.0 * eta2 + 9.0 * eta3 - 2.0 * eta4) / (6.0 * om3);
    double k1 = (-12.0 * eta + 6.0 * eta2 + eta4) / (2.0 * om3);
    double k2 = -3.0 * eta2 / (8.0 * om2);
    double k3 = (3.0 * eta + 3.0 * eta2 - eta4) / (6.0 * om3);

    return Math.exp(k0 + k1 * x0 + k2 * x0 * x0 + k3 * x0 * x0 * x0);
  }

  /**
   * First-order perturbation correction g1 for the chain RDF at sigma (Lafitte 2013 Eq. 37). Uses
   * the bare (Clapeyron-convention) perturbation terms.
   *
   * @param eta packing fraction
   * @param lambdaR repulsive exponent
   * @param lambdaA attractive exponent
   * @param cMie Mie prefactor
   * @param x0 sigma/d ratio
   * @return g1 (dimensionless)
   */
  public static double calcG1Chain(double eta, double lambdaR, double lambdaA, double cMie,
      double x0) {
    double as1A = calcAS1Bare(eta, lambdaA);
    double bA = calcBBare(eta, lambdaA, x0);
    double as1R = calcAS1Bare(eta, lambdaR);
    double bR = calcBBare(eta, lambdaR, x0);

    // Numerical derivative: d(eta * (aS1+B)_lambda) / d(eta)
    // This equals (aS1+B) + eta * d(aS1+B)/deta = rhoS * d/drhoS convention
    double dEta = Math.max(Math.abs(eta) * 1.0e-5, 1.0e-12);
    double etaP = eta + dEta;
    double etaM = Math.max(eta - dEta, 1.0e-15);
    dEta = (etaP - etaM) / 2.0;

    double fullAP = calcAS1Bare(etaP, lambdaA) + calcBBare(etaP, lambdaA, x0);
    double fullAM = calcAS1Bare(etaM, lambdaA) + calcBBare(etaM, lambdaA, x0);
    double fullRP = calcAS1Bare(etaP, lambdaR) + calcBBare(etaP, lambdaR, x0);
    double fullRM = calcAS1Bare(etaM, lambdaR) + calcBBare(etaM, lambdaR, x0);

    double dFullA = (etaP * fullAP - etaM * fullAM) / (2.0 * dEta);
    double dFullR = (etaP * fullRP - etaM * fullRM) / (2.0 * dEta);

    double da1DrhoS = cMie * (Math.pow(x0, lambdaA) * dFullA - Math.pow(x0, lambdaR) * dFullR);

    return 3.0 * da1DrhoS - cMie * (lambdaA * Math.pow(x0, lambdaA) * (as1A + bA)
        - lambdaR * Math.pow(x0, lambdaR) * (as1R + bR));
  }

  /**
   * Second-order perturbation correction g2 for the chain RDF at sigma (Lafitte 2013 Eq. 38). Uses
   * the bare perturbation terms and gamma_c correction.
   *
   * @param eta packing fraction
   * @param zetaSt sigma-based packing fraction
   * @param lambdaR repulsive exponent
   * @param lambdaA attractive exponent
   * @param epsOverKT epsilon/(kT)
   * @param cMie Mie prefactor
   * @param x0 sigma/d ratio
   * @return g2 (dimensionless)
   */
  public static double calcG2Chain(double eta, double zetaSt, double lambdaR, double lambdaA,
      double epsOverKT, double cMie, double x0) {
    double khs = calcKHS(eta);
    double alpha = calcMieAlpha(lambdaR, lambdaA);

    // Bare a2 inner terms at current eta
    double as12a = calcAS1Bare(eta, 2.0 * lambdaA);
    double b2a = calcBBare(eta, 2.0 * lambdaA, x0);
    double as1ar = calcAS1Bare(eta, lambdaA + lambdaR);
    double bar = calcBBare(eta, lambdaA + lambdaR, x0);
    double as12r = calcAS1Bare(eta, 2.0 * lambdaR);
    double b2r = calcBBare(eta, 2.0 * lambdaR, x0);

    double innerA2 = Math.pow(x0, 2.0 * lambdaA) * (as12a + b2a)
        - 2.0 * Math.pow(x0, lambdaA + lambdaR) * (as1ar + bar)
        + Math.pow(x0, 2.0 * lambdaR) * (as12r + b2r);

    // Numerical derivative of (eta * KHS * innerA2)
    double dEta = Math.max(Math.abs(eta) * 1.0e-5, 1.0e-12);
    double etaP = eta + dEta;
    double etaM = Math.max(eta - dEta, 1.0e-15);
    dEta = (etaP - etaM) / 2.0;

    double khsP = calcKHS(etaP);
    double khsM = calcKHS(etaM);

    double innerA2P = Math.pow(x0, 2.0 * lambdaA)
        * (calcAS1Bare(etaP, 2.0 * lambdaA) + calcBBare(etaP, 2.0 * lambdaA, x0))
        - 2.0 * Math.pow(x0, lambdaA + lambdaR)
            * (calcAS1Bare(etaP, lambdaA + lambdaR) + calcBBare(etaP, lambdaA + lambdaR, x0))
        + Math.pow(x0, 2.0 * lambdaR)
            * (calcAS1Bare(etaP, 2.0 * lambdaR) + calcBBare(etaP, 2.0 * lambdaR, x0));

    double innerA2M = Math.pow(x0, 2.0 * lambdaA)
        * (calcAS1Bare(etaM, 2.0 * lambdaA) + calcBBare(etaM, 2.0 * lambdaA, x0))
        - 2.0 * Math.pow(x0, lambdaA + lambdaR)
            * (calcAS1Bare(etaM, lambdaA + lambdaR) + calcBBare(etaM, lambdaA + lambdaR, x0))
        + Math.pow(x0, 2.0 * lambdaR)
            * (calcAS1Bare(etaM, 2.0 * lambdaR) + calcBBare(etaM, 2.0 * lambdaR, x0));

    // rhoS-derivative: d(rhoS * KHS * inner) / drhoS = d(eta * KHS * inner) / deta
    double da2prod = (etaP * khsP * innerA2P - etaM * khsM * innerA2M) / (2.0 * dEta);
    double da2DrhoS = 0.5 * cMie * cMie * da2prod;

    // gMCA2 = 3*da2/drhoS - KHS*C^2*(lr*x0^2lr*(as1_2r+B_2r) - (la+lr)*... + la*...)
    double gMCA2 = 3.0 * da2DrhoS - khs * cMie * cMie
        * (lambdaR * Math.pow(x0, 2.0 * lambdaR) * (as12r + b2r)
            - (lambdaA + lambdaR) * Math.pow(x0, lambdaA + lambdaR) * (as1ar + bar)
            + lambdaA * Math.pow(x0, 2.0 * lambdaA) * (as12a + b2a));

    // gamma_c correction (Lafitte 2013 Eq. 39)
    double theta = Math.exp(epsOverKT) - 1.0;
    double gammac = 10.0 * (-Math.tanh(10.0 * (0.57 - alpha)) + 1.0) * zetaSt * theta
        * Math.exp(-6.7 * zetaSt - 8.0 * zetaSt * zetaSt);

    return (1.0 + gammac) * gMCA2;
  }

  /**
   * Full Mie-corrected RDF at distance sigma for the chain contribution (Lafitte 2013 Eq. 35).
   * g_Mie(sigma) = g_HS(x0; eta) * exp(tau * g1/g0 + tau^2 * g2/g0).
   *
   * @param eta packing fraction
   * @param zetaSt sigma-based packing fraction
   * @param lambdaR repulsive exponent
   * @param lambdaA attractive exponent
   * @param epsOverKT epsilon/(kT)
   * @param cMie Mie prefactor
   * @param x0 sigma/d ratio
   * @return g_Mie(sigma)
   */
  public static double calcGMie(double eta, double zetaSt, double lambdaR, double lambdaA,
      double epsOverKT, double cMie, double x0) {
    // Safety bounds - g_MIE only valid for physical packing fractions
    if (eta < 1.0e-10 || eta > 0.7 || !Double.isFinite(eta)) {
      // Fall back to CS contact value for extreme eta
      double om = 1.0 - Math.max(eta, 0.0);
      return (1.0 - Math.max(eta, 0.0) / 2.0) / (om * om * om);
    }
    double gHS0 = calcGHS_x0(eta, x0);
    double g1 = calcG1Chain(eta, lambdaR, lambdaA, cMie, x0);
    double g2 = calcG2Chain(eta, zetaSt, lambdaR, lambdaA, epsOverKT, cMie, x0);
    double tau = epsOverKT;
    double exponent = tau * g1 / gHS0 + tau * tau * g2 / gHS0;
    // Clamp exponent to prevent numerical overflow
    exponent = Math.max(-20.0, Math.min(20.0, exponent));
    double result = gHS0 * Math.exp(exponent);
    return Double.isFinite(result) && result > 0.0 ? result : gHS0;
  }

  /**
   * Blended g for the chain contribution: g_HS(x0) * exp(alpha * correction). When alpha=0, returns
   * g_HS(x0). When alpha=1, returns full g_Mie.
   *
   * @param eta packing fraction
   * @param x0 sigma/d ratio
   * @param lambdaR repulsive exponent
   * @param lambdaA attractive exponent
   * @param epsOverKT epsilon/(kT)
   * @param cMie Mie prefactor
   * @param alpha blending fraction (0 to 1)
   * @return blended g chain
   */
  static double calcGMieBlended(double eta, double x0, double lambdaR, double lambdaA,
      double epsOverKT, double cMie, double alpha) {
    if (eta < 1.0e-10 || eta > 0.7 || !Double.isFinite(eta)) {
      double om = 1.0 - Math.max(eta, 0.0);
      return (1.0 - Math.max(eta, 0.0) / 2.0) / (om * om * om);
    }
    double gHS0 = calcGHS_x0(eta, x0);
    if (alpha < 1.0e-10) {
      return gHS0;
    }
    double zetaSt = eta * x0 * x0 * x0;
    double g1 = calcG1Chain(eta, lambdaR, lambdaA, cMie, x0);
    double g2 = calcG2Chain(eta, zetaSt, lambdaR, lambdaA, epsOverKT, cMie, x0);
    double tau = epsOverKT;
    double exponent = alpha * (tau * g1 / gHS0 + tau * tau * g2 / gHS0);
    exponent = Math.max(-20.0, Math.min(20.0, exponent));
    double result = gHS0 * Math.exp(exponent);
    return Double.isFinite(result) && result > 0.0 ? result : gHS0;
  }

  // ===== Main dispersion computation =====

  /**
   * Computes first, second, and third-order perturbation terms and all their eta and T derivatives
   * using pair summation per Lafitte 2013 Eqs. 37-40. For multi-component mixtures, a_k = sum_ij
   * xs_i * xs_j * a_k^ij where a_k^ij uses pair-specific cross parameters (sigma_ij, eps_ij,
   * lambda_ij). For single-component systems, falls back to direct evaluation.
   */
  private void computeDispersionTerms() {
    double eta = nSAFT;

    if (numberOfComponents == 1) {
      // Pure component: direct evaluation (no pair sum needed)
      ComponentSAFTVRMie comp = (ComponentSAFTVRMie) getComponent(0);
      double sigma = comp.getSigmaSAFTi();
      double epsk = comp.getEpsikSAFT();
      double d = comp.getdSAFTi();
      double lambdaR = comp.getLambdaRSAFTVRMie();
      double lambdaA = comp.getLambdaASAFTVRMie();
      double epsOverKT = epsk / temperature;
      double cMie = ComponentSAFTVRMie.calcMiePrefactor(lambdaR, lambdaA);
      double x0 = (d > 0) ? sigma / d : 1.0;

      computeDispTermsSingleFluid(eta, epsOverKT, cMie, x0, lambdaR, lambdaA, sigma, epsk);
    } else {
      // Multi-component: pair summation (Lafitte 2013 Eqs. 37-40)
      computeDispTermsPairSum(eta);
    }
  }

  /**
   * Computes dispersion terms for a single effective fluid (pure component or one-fluid mapped).
   *
   * @param eta packing fraction
   * @param epsOverKT reduced energy parameter
   * @param cMie Mie prefactor
   * @param x0 sigma/d ratio
   * @param lambdaR repulsive exponent
   * @param lambdaA attractive exponent
   * @param sigma segment diameter in m
   * @param epsk energy parameter eps/k in K
   */
  private void computeDispTermsSingleFluid(double eta, double epsOverKT, double cMie, double x0,
      double lambdaR, double lambdaA, double sigma, double epsk) {
    a1Disp = calcA1MieAtEta(eta, lambdaR, lambdaA, epsOverKT, cMie, x0);

    double dEta = Math.max(Math.abs(eta) * 1.0e-5, 1.0e-12);
    double etaP = eta + dEta;
    double etaM = Math.max(eta - dEta, 1.0e-15);
    dEta = (etaP - etaM) / 2.0;

    double a1P = calcA1MieAtEta(etaP, lambdaR, lambdaA, epsOverKT, cMie, x0);
    double a1M = calcA1MieAtEta(etaM, lambdaR, lambdaA, epsOverKT, cMie, x0);
    da1DispDeta = (a1P - a1M) / (2.0 * dEta);
    d2a1DispDeta2 = (a1P - 2.0 * a1Disp + a1M) / (dEta * dEta);

    double dT = temperature * 1.0e-5;
    double tP = temperature + dT;
    double tM = temperature - dT;
    double epsOverKTp = epsk / tP;
    double epsOverKTm = epsk / tM;
    double dP = ComponentSAFTVRMie.calcEffectiveDiameter(sigma, epsk, tP, lambdaR, lambdaA);
    double dM = ComponentSAFTVRMie.calcEffectiveDiameter(sigma, epsk, tM, lambdaR, lambdaA);
    double x0p = (dP > 0) ? sigma / dP : 1.0;
    double x0m = (dM > 0) ? sigma / dM : 1.0;

    double a1Tp = calcA1MieAtEta(eta, lambdaR, lambdaA, epsOverKTp, cMie, x0p);
    double a1Tm = calcA1MieAtEta(eta, lambdaR, lambdaA, epsOverKTm, cMie, x0m);
    da1DispDT = (a1Tp - a1Tm) / (2.0 * dT);
    d2a1DispDT2 = (a1Tp - 2.0 * a1Disp + a1Tm) / (dT * dT);

    double a1PpTp = calcA1MieAtEta(etaP, lambdaR, lambdaA, epsOverKTp, cMie, x0p);
    double a1MpTp = calcA1MieAtEta(etaM, lambdaR, lambdaA, epsOverKTp, cMie, x0p);
    double a1PpTm = calcA1MieAtEta(etaP, lambdaR, lambdaA, epsOverKTm, cMie, x0m);
    double a1MpTm = calcA1MieAtEta(etaM, lambdaR, lambdaA, epsOverKTm, cMie, x0m);
    d2a1DispDetaDT = (a1PpTp - a1MpTp - a1PpTm + a1MpTm) / (4.0 * dEta * dT);

    double x03 = x0 * x0 * x0;
    double zetaSt = eta * x03;

    a2Disp = calcA2MieAtEta(eta, zetaSt, lambdaR, lambdaA, epsOverKT, cMie, x0);
    double zetaStP = etaP * x03;
    double zetaStM = etaM * x03;
    double a2P = calcA2MieAtEta(etaP, zetaStP, lambdaR, lambdaA, epsOverKT, cMie, x0);
    double a2M = calcA2MieAtEta(etaM, zetaStM, lambdaR, lambdaA, epsOverKT, cMie, x0);
    da2DispDeta = (a2P - a2M) / (2.0 * dEta);
    d2a2DispDeta2 = (a2P - 2.0 * a2Disp + a2M) / (dEta * dEta);

    double x0p3 = x0p * x0p * x0p;
    double x0m3 = x0m * x0m * x0m;
    double a2Tp = calcA2MieAtEta(eta, eta * x0p3, lambdaR, lambdaA, epsOverKTp, cMie, x0p);
    double a2Tm = calcA2MieAtEta(eta, eta * x0m3, lambdaR, lambdaA, epsOverKTm, cMie, x0m);
    da2DispDT = (a2Tp - a2Tm) / (2.0 * dT);
    d2a2DispDT2 = (a2Tp - 2.0 * a2Disp + a2Tm) / (dT * dT);

    double a2PpTp = calcA2MieAtEta(etaP, etaP * x0p3, lambdaR, lambdaA, epsOverKTp, cMie, x0p);
    double a2MpTp = calcA2MieAtEta(etaM, etaM * x0p3, lambdaR, lambdaA, epsOverKTp, cMie, x0p);
    double a2PpTm = calcA2MieAtEta(etaP, etaP * x0m3, lambdaR, lambdaA, epsOverKTm, cMie, x0m);
    double a2MpTm = calcA2MieAtEta(etaM, etaM * x0m3, lambdaR, lambdaA, epsOverKTm, cMie, x0m);
    d2a2DispDetaDT = (a2PpTp - a2MpTp - a2PpTm + a2MpTm) / (4.0 * dEta * dT);

    a3Disp = calcA3Mie(zetaSt, lambdaR, lambdaA, epsOverKT);
    double a3eP = calcA3Mie(zetaStP, lambdaR, lambdaA, epsOverKT);
    double a3eM = calcA3Mie(zetaStM, lambdaR, lambdaA, epsOverKT);
    da3DispDeta = (a3eP - a3eM) / (2.0 * dEta);
    d2a3DispDeta2 = (a3eP - 2.0 * a3Disp + a3eM) / (dEta * dEta);

    double a3Tp = calcA3Mie(eta * x0p3, lambdaR, lambdaA, epsOverKTp);
    double a3Tm = calcA3Mie(eta * x0m3, lambdaR, lambdaA, epsOverKTm);
    da3DispDT = (a3Tp - a3Tm) / (2.0 * dT);
    d2a3DispDT2 = (a3Tp - 2.0 * a3Disp + a3Tm) / (dT * dT);

    double a3PpTp = calcA3Mie(etaP * x0p3, lambdaR, lambdaA, epsOverKTp);
    double a3MpTp = calcA3Mie(etaM * x0p3, lambdaR, lambdaA, epsOverKTp);
    double a3PpTm = calcA3Mie(etaP * x0m3, lambdaR, lambdaA, epsOverKTm);
    double a3MpTm = calcA3Mie(etaM * x0m3, lambdaR, lambdaA, epsOverKTm);
    d2a3DispDetaDT = (a3PpTp - a3MpTp - a3PpTm + a3MpTm) / (4.0 * dEta * dT);
  }

  /**
   * Computes pair-summed dispersion terms for multi-component mixtures. a_k = sum_ij xs_i * xs_j *
   * a_k^ij per Lafitte 2013 Eqs. 37-40. Cross parameters follow Eq. 36: sigma_ij = arithmetic mean,
   * eps_ij = geometric mean with sigma^3 correction, lambda_ij = 3 + geometric mean of (lambda-3),
   * d_ij = BH diameter of cross potential.
   *
   * @param eta packing fraction
   */
  private void computeDispTermsPairSum(double eta) {
    // Base values
    double[] base = calcPairDispSum(eta, temperature);
    a1Disp = base[0];
    a2Disp = base[1];
    a3Disp = base[2];

    // Compute per-component weighted sums for analytical dF_DISP/dNi
    aDispPerComp = calcPairDispSumPerComp(eta, temperature);

    // Eta derivatives via central differences
    double dEta = Math.max(Math.abs(eta) * 1.0e-5, 1.0e-12);
    double etaP = eta + dEta;
    double etaM = Math.max(eta - dEta, 1.0e-15);
    dEta = (etaP - etaM) / 2.0;

    double[] vP = calcPairDispSum(etaP, temperature);
    double[] vM = calcPairDispSum(etaM, temperature);
    da1DispDeta = (vP[0] - vM[0]) / (2.0 * dEta);
    d2a1DispDeta2 = (vP[0] - 2.0 * base[0] + vM[0]) / (dEta * dEta);
    da2DispDeta = (vP[1] - vM[1]) / (2.0 * dEta);
    d2a2DispDeta2 = (vP[1] - 2.0 * base[1] + vM[1]) / (dEta * dEta);
    da3DispDeta = (vP[2] - vM[2]) / (2.0 * dEta);
    d2a3DispDeta2 = (vP[2] - 2.0 * base[2] + vM[2]) / (dEta * dEta);

    // Temperature derivatives
    double dT = temperature * 1.0e-5;
    double tP = temperature + dT;
    double tM = temperature - dT;

    double[] tpv = calcPairDispSum(eta, tP);
    double[] tmv = calcPairDispSum(eta, tM);
    da1DispDT = (tpv[0] - tmv[0]) / (2.0 * dT);
    d2a1DispDT2 = (tpv[0] - 2.0 * base[0] + tmv[0]) / (dT * dT);
    da2DispDT = (tpv[1] - tmv[1]) / (2.0 * dT);
    d2a2DispDT2 = (tpv[1] - 2.0 * base[1] + tmv[1]) / (dT * dT);
    da3DispDT = (tpv[2] - tmv[2]) / (2.0 * dT);
    d2a3DispDT2 = (tpv[2] - 2.0 * base[2] + tmv[2]) / (dT * dT);

    // Mixed derivatives d^2/deta/dT
    double[] ePtP = calcPairDispSum(etaP, tP);
    double[] eMtP = calcPairDispSum(etaM, tP);
    double[] ePtM = calcPairDispSum(etaP, tM);
    double[] eMtM = calcPairDispSum(etaM, tM);
    d2a1DispDetaDT = (ePtP[0] - eMtP[0] - ePtM[0] + eMtM[0]) / (4.0 * dEta * dT);
    d2a2DispDetaDT = (ePtP[1] - eMtP[1] - ePtM[1] + eMtM[1]) / (4.0 * dEta * dT);
    d2a3DispDetaDT = (ePtP[2] - eMtP[2] - ePtM[2] + eMtM[2]) / (4.0 * dEta * dT);
  }

  /**
   * Evaluates pair-summed a1, a2, a3 at given eta and temperature. For each pair (i,j), computes
   * cross parameters per Lafitte 2013 Eq. 36 including the sigma^3-corrected epsilon and BH
   * diameter from the cross-potential.
   *
   * @param eta packing fraction
   * @param temp temperature in K
   * @return double[3] = {a1_sum, a2_sum, a3_sum}
   */
  private double[] calcPairDispSum(double eta, double temp) {
    double nMoles = getNumberOfMolesInPhase();
    double mbar = mSAFT;
    double a1sum = 0.0;
    double a2sum = 0.0;
    double a3sum = 0.0;

    for (int i = 0; i < numberOfComponents; i++) {
      ComponentSAFTVRMie ci = (ComponentSAFTVRMie) getComponent(i);
      double xi = ci.getNumberOfMolesInPhase() / nMoles;
      double mi = ci.getmSAFTi();
      double xsi = (mbar > 0) ? xi * mi / mbar : 0.0;

      for (int j = 0; j < numberOfComponents; j++) {
        ComponentSAFTVRMie cj = (ComponentSAFTVRMie) getComponent(j);
        double xj = cj.getNumberOfMolesInPhase() / nMoles;
        double mj = cj.getmSAFTi();
        double xsj = (mbar > 0) ? xj * mj / mbar : 0.0;

        double w = xsi * xsj;
        if (w < 1.0e-30) {
          continue;
        }

        // Cross parameters (Lafitte 2013 Eq. 36)
        double sigi = ci.getSigmaSAFTi();
        double sigj = cj.getSigmaSAFTi();
        double sigij = 0.5 * (sigi + sigj);

        // Epsilon with sigma^3 correction: eps_ij = sqrt(eps_i*eps_j) * sqrt(sig_i^3*sig_j^3) /
        // sig_ij^3
        double si3 = sigi * sigi * sigi;
        double sj3 = sigj * sigj * sigj;
        double sij3 = sigij * sigij * sigij;
        double epsij =
            Math.sqrt(ci.getEpsikSAFT() * cj.getEpsikSAFT()) * Math.sqrt(si3 * sj3) / sij3;

        double lrij =
            3.0 + Math.sqrt((ci.getLambdaRSAFTVRMie() - 3.0) * (cj.getLambdaRSAFTVRMie() - 3.0));
        double laij =
            3.0 + Math.sqrt((ci.getLambdaASAFTVRMie() - 3.0) * (cj.getLambdaASAFTVRMie() - 3.0));

        // BH diameter from cross potential (NOT arithmetic average of pure diameters)
        double dij = ComponentSAFTVRMie.calcEffectiveDiameter(sigij, epsij, temp, lrij, laij);

        double cMieij = ComponentSAFTVRMie.calcMiePrefactor(lrij, laij);
        double x0ij = (dij > 0) ? sigij / dij : 1.0;
        double betaij = epsij / temp;
        double zetaStij = eta * x0ij * x0ij * x0ij;

        a1sum += w * calcA1MieAtEta(eta, lrij, laij, betaij, cMieij, x0ij);
        a2sum += w * calcA2MieAtEta(eta, zetaStij, lrij, laij, betaij, cMieij, x0ij);
        a3sum += w * calcA3Mie(zetaStij, lrij, laij, betaij);
      }
    }

    return new double[] {a1sum, a2sum, a3sum};
  }

  /**
   * Computes per-component weighted dispersion sums: aDispPerComp[i] = sum_l xs_l *
   * (a1_il+a2_il+a3_il). Used for the analytical dF_DISP/dNi formula.
   *
   * @param eta packing fraction
   * @param temp temperature in K
   * @return array of per-component dispersion sums
   */
  private double[] calcPairDispSumPerComp(double eta, double temp) {
    double nMoles = getNumberOfMolesInPhase();
    double mbar = mSAFT;
    double[] result = new double[numberOfComponents];

    for (int i = 0; i < numberOfComponents; i++) {
      ComponentSAFTVRMie ci = (ComponentSAFTVRMie) getComponent(i);
      double sigi = ci.getSigmaSAFTi();
      double epsi = ci.getEpsikSAFT();
      double lri = ci.getLambdaRSAFTVRMie();
      double lai = ci.getLambdaASAFTVRMie();
      double sum = 0.0;

      for (int l = 0; l < numberOfComponents; l++) {
        ComponentSAFTVRMie cl = (ComponentSAFTVRMie) getComponent(l);
        double xl = cl.getNumberOfMolesInPhase() / nMoles;
        double ml = cl.getmSAFTi();
        double xsl = (mbar > 0) ? xl * ml / mbar : 0.0;

        if (xsl < 1.0e-30) {
          continue;
        }

        double sigl = cl.getSigmaSAFTi();
        double sigil = 0.5 * (sigi + sigl);
        double si3 = sigi * sigi * sigi;
        double sl3 = sigl * sigl * sigl;
        double sil3 = sigil * sigil * sigil;
        double epsil = Math.sqrt(epsi * cl.getEpsikSAFT()) * Math.sqrt(si3 * sl3) / sil3;
        double lril = 3.0 + Math.sqrt((lri - 3.0) * (cl.getLambdaRSAFTVRMie() - 3.0));
        double lail = 3.0 + Math.sqrt((lai - 3.0) * (cl.getLambdaASAFTVRMie() - 3.0));
        double dil = ComponentSAFTVRMie.calcEffectiveDiameter(sigil, epsil, temp, lril, lail);
        double cMieil = ComponentSAFTVRMie.calcMiePrefactor(lril, lail);
        double x0il = (dil > 0) ? sigil / dil : 1.0;
        double betail = epsil / temp;
        double zetaStil = eta * x0il * x0il * x0il;

        double ail = calcA1MieAtEta(eta, lril, lail, betail, cMieil, x0il)
            + calcA2MieAtEta(eta, zetaStil, lril, lail, betail, cMieil, x0il)
            + calcA3Mie(zetaStil, lril, lail, betail);
        sum += xsl * ail;
      }
      result[i] = sum;
    }
    return result;
  }

  // ===== Association contribution (SAFT-VR Mie) =====

  /**
   * Initializes association-related quantities for the current volume. Computes the association
   * radial distribution function and its volume derivatives from the SAFT hard-sphere model.
   */
  private void initAssocGDerivatives() {
    if (useASSOC == 0) {
      return;
    }
    double eta = nSAFT;

    // g for association = hard-sphere contact RDF (Carnahan-Starling)
    double om = 1.0 - eta;
    gcpaAssoc = (1.0 - eta / 2.0) / (om * om * om);

    // d(ln g)/d(eta) = -1/(2-eta) + 3/(1-eta) = (5 - 2*eta)/((2-eta)*(1-eta))
    double dlngDeta = (5.0 - 2.0 * eta) / ((2.0 - eta) * (1.0 - eta));

    // d(ln g)/dV_SI = d(ln g)/d(eta) * d(eta)/dV_SI
    // d(eta)/dV_SI = dnSAFTdV (already in SI units, m^-3)
    gcpavAssoc = dlngDeta * dnSAFTdV;

    // d2(ln g)/dV2: product rule d/dV[dlng/deta * deta/dV]
    double d2lngDeta2 = calcD2LngDeta2(eta);
    gcpavvAssoc = d2lngDeta2 * dnSAFTdV * dnSAFTdV + dlngDeta * dnSAFTdVdV;

    // d3(ln g)/dV3: numerical
    double dEtaV = Math.max(Math.abs(eta) * 1.0e-5, 1.0e-12);
    double etaP = eta + dEtaV;
    double etaM = Math.max(eta - dEtaV, 1.0e-15);
    dEtaV = (etaP - etaM) / 2.0;
    double dlngDeP = (5.0 - 2.0 * etaP) / ((2.0 - etaP) * (1.0 - etaP));
    double dlngDeM = (5.0 - 2.0 * etaM) / ((2.0 - etaM) * (1.0 - etaM));
    double d3lngDeta3 = (dlngDeP - 2.0 * dlngDeta + dlngDeM) / (dEtaV * dEtaV);
    // Approximation using third volume derivative:
    // dnSAFTdVdVdV = -6 * pi/6 * N_A * n / V^4 * dSAFT
    double dnSAFTdVdVdV = -3.0 * dnSAFTdVdV / volumeSAFT;
    gcpavvvAssoc = d3lngDeta3 * dnSAFTdV * dnSAFTdV * dnSAFTdV
        + 3.0 * d2lngDeta2 * dnSAFTdV * dnSAFTdVdV + dlngDeta * dnSAFTdVdVdV;
  }

  /**
   * Second derivative of ln(g_HS) with respect to packing fraction eta.
   *
   * @param eta packing fraction
   * @return d2(ln g)/d(eta)2
   */
  private double calcD2LngDeta2(double eta) {
    // ln(g) = ln(2-eta) - ln(2) - 3*ln(1 - eta)
    // d(ln g)/d(eta) = -1/(2-eta) + 3/(1-eta) = (5 - 2*eta)/((2-eta)*(1-eta))
    // d2(ln g)/d(eta)2: numerical for robustness
    double dE = Math.max(Math.abs(eta) * 1.0e-5, 1.0e-12);
    double eP = eta + dE;
    double eM = Math.max(eta - dE, 1.0e-15);
    dE = (eP - eM) / 2.0;
    double fP = (5.0 - 2.0 * eP) / ((2.0 - eP) * (1.0 - eP));
    double fM = (5.0 - 2.0 * eM) / ((2.0 - eM) * (1.0 - eM));
    return (fP - fM) / (2.0 * dE);
  }

  /**
   * Computes the association strength delta for all site-site pairs. Uses the SAFT-VR Mie combining
   * rules: epsilon_HB_ij = (eps_i + eps_j)/2 (CR-1), kappa_ij = sqrt(kappa_i * kappa_j) *
   * Wolbach-Sandler correction, sigma_ij = (sigma_i + sigma_j)/2.
   */
  private void computeDeltaAssoc() {
    if (useASSOC == 0) {
      return;
    }
    double NA = ThermodynamicConstantsInterface.avagadroNumber;
    double RGas = ThermodynamicConstantsInterface.R;

    for (int i = 0; i < numberOfComponents; i++) {
      int nSitesI = hasAssociationParams(i) ? getComponent(i).getNumberOfAssociationSites() : 0;
      for (int j = 0; j < numberOfComponents; j++) {
        int nSitesJ = hasAssociationParams(j) ? getComponent(j).getNumberOfAssociationSites() : 0;
        if (nSitesI == 0 || nSitesJ == 0) {
          continue;
        }

        // Cross parameters
        double sigI = getComponent(i).getSigmaSAFTi();
        double sigJ = getComponent(j).getSigmaSAFTi();
        double sigIJ = (sigI + sigJ) / 2.0;
        double sigIJ3 = sigIJ * sigIJ * sigIJ;

        double epsHBI = getComponent(i).getAssociationEnergySAFTVRMie(); // J/mol
        double epsHBJ = getComponent(j).getAssociationEnergySAFTVRMie();
        double epsHBIJ = (epsHBI + epsHBJ) / 2.0; // CR-1

        double kappaI = getComponent(i).getAssociationVolumeSAFT();
        double kappaJ = getComponent(j).getAssociationVolumeSAFT();
        // Wolbach-Sandler correction: sqrt(sigma_i * sigma_j) / sigmaIJ
        double kappaIJ = Math.sqrt(kappaI * kappaJ) * Math.pow(Math.sqrt(sigI * sigJ) / sigIJ, 3.0);

        // Delta = (exp(eps_HB/(RT)) - 1) * sigma_ij^3 * N_A * kappa * g_HS
        // Note: NO 1e5 factor here because the XA solver divides by volumeSAFT (=V_m3),
        // not getVolume() (=V_neqsim = V_m3*1e5). The density is n/V_m3 [mol/m^3]
        // and we need molecular density n*NA/V_m3, hence the NA factor.
        double expTerm = Math.exp(epsHBIJ / (RGas * temperature)) - 1.0;
        double deltaBase = expTerm * sigIJ3 * NA * kappaIJ * gcpaAssoc;

        // Temperature derivative: d(delta)/dT
        double dExpTermDT = -epsHBIJ / (RGas * temperature * temperature)
            * Math.exp(epsHBIJ / (RGas * temperature));
        double deltadTBase = dExpTermDT * sigIJ3 * NA * kappaIJ * gcpaAssoc;

        // Fill global site arrays with scheme indicator
        for (int a = 0; a < nSitesI; a++) {
          for (int b = 0; b < nSitesJ; b++) {
            int globalA = siteOffset[i] + a;
            int globalB = siteOffset[j] + b;
            deltaAssoc[globalA][globalB] = crossAssocScheme[i][j][a][b] * deltaBase;
            deltadTAssoc[globalA][globalB] = crossAssocScheme[i][j][a][b] * deltadTBase;
          }
        }
      }
    }
  }

  /**
   * Solves the association fraction X_A for all sites using successive substitution. X_A = 1 / (1 +
   * (1/V) * sum_j nj * sum_B delta_AB * X_B).
   *
   * @return true if converged
   */
  private boolean solveAssociation() {
    if (useASSOC == 0) {
      return true;
    }

    int maxIter = 500;
    double tol = 1.0e-12;

    for (int iter = 0; iter < maxIter; iter++) {
      double maxChange = 0.0;

      for (int i = 0; i < numberOfComponents; i++) {
        ComponentSAFTVRMie ci = (ComponentSAFTVRMie) getComponent(i);
        int nSitesI = hasAssociationParams(i) ? getComponent(i).getNumberOfAssociationSites() : 0;

        for (int a = 0; a < nSitesI; a++) {
          double sum = 0.0;
          for (int j = 0; j < numberOfComponents; j++) {
            ComponentSAFTVRMie cj = (ComponentSAFTVRMie) getComponent(j);
            int nSitesJ =
                hasAssociationParams(j) ? getComponent(j).getNumberOfAssociationSites() : 0;
            double nj = cj.getNumberOfMolesInPhase();
            for (int b = 0; b < nSitesJ; b++) {
              int globalA = siteOffset[i] + a;
              int globalB = siteOffset[j] + b;
              sum += nj / volumeSAFT * deltaAssoc[globalA][globalB] * cj.getXsiteAssoc()[b];
            }
          }
          double xNew = 1.0 / (1.0 + sum);
          double xOld = ci.getXsiteAssoc()[a];
          maxChange = Math.max(maxChange, Math.abs(xNew - xOld));
          ci.setXsiteAssoc(a, xNew);
        }
      }

      if (maxChange < tol) {
        return true;
      }
    }
    return false; // Did not converge
  }

  /**
   * Calculates hcpatot = sum_i ni * sum_A (1 - X_Ai). Also computes T derivatives.
   */
  private void calcAssocHCPA() {
    if (useASSOC == 0) {
      hcpatot = 0.0;
      hcpatotdT = 0.0;
      hcpatotdTdT = 0.0;
      return;
    }

    hcpatot = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      ComponentSAFTVRMie ci = (ComponentSAFTVRMie) getComponent(i);
      int nSites = hasAssociationParams(i) ? getComponent(i).getNumberOfAssociationSites() : 0;
      double ni = ci.getNumberOfMolesInPhase();
      for (int a = 0; a < nSites; a++) {
        hcpatot += ni * (1.0 - ci.getXsiteAssoc()[a]);
      }
    }

    // Temperature derivative of hcpatot: numerical (requires solving X at perturbed T)
    // For now, use analytical approximation through deltadT
    calcHcpatotDerivatives();
  }

  /**
   * Calculates temperature derivatives of hcpatot using perturbation of delta. dh/dT = sum_i ni *
   * sum_A (-dX_A/dT), where the XA T-derivative comes from implicit differentiation of the
   * association equation.
   */
  private void calcHcpatotDerivatives() {
    // Numerical approach: perturb temperature, re-solve XA, take finite difference
    double dT = temperature * 1.0e-6;

    // Save current XA
    double[][] xSave = new double[numberOfComponents][];
    for (int i = 0; i < numberOfComponents; i++) {
      ComponentSAFTVRMie ci = (ComponentSAFTVRMie) getComponent(i);
      int nSites = hasAssociationParams(i) ? getComponent(i).getNumberOfAssociationSites() : 0;
      xSave[i] = new double[nSites];
      for (int a = 0; a < nSites; a++) {
        xSave[i][a] = ci.getXsiteAssoc()[a];
      }
    }

    // Perturb T+ : recompute delta and solve
    double origTemp = temperature;
    temperature = origTemp + dT;
    computeDeltaAssoc();
    solveAssociation();

    double hPlus = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      ComponentSAFTVRMie ci = (ComponentSAFTVRMie) getComponent(i);
      int nSites = hasAssociationParams(i) ? getComponent(i).getNumberOfAssociationSites() : 0;
      for (int a = 0; a < nSites; a++) {
        hPlus += ci.getNumberOfMolesInPhase() * (1.0 - ci.getXsiteAssoc()[a]);
      }
    }

    // Restore and perturb T-
    for (int i = 0; i < numberOfComponents; i++) {
      ComponentSAFTVRMie ci = (ComponentSAFTVRMie) getComponent(i);
      for (int a = 0; a < xSave[i].length; a++) {
        ci.setXsiteAssoc(a, xSave[i][a]);
      }
    }
    temperature = origTemp - dT;
    computeDeltaAssoc();
    solveAssociation();

    double hMinus = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      ComponentSAFTVRMie ci = (ComponentSAFTVRMie) getComponent(i);
      int nSites = hasAssociationParams(i) ? getComponent(i).getNumberOfAssociationSites() : 0;
      for (int a = 0; a < nSites; a++) {
        hMinus += ci.getNumberOfMolesInPhase() * (1.0 - ci.getXsiteAssoc()[a]);
      }
    }

    hcpatotdT = (hPlus - hMinus) / (2.0 * dT);
    hcpatotdTdT = (hPlus - 2.0 * hcpatot + hMinus) / (dT * dT);

    // Restore original state
    temperature = origTemp;
    computeDeltaAssoc();
    for (int i = 0; i < numberOfComponents; i++) {
      ComponentSAFTVRMie ci = (ComponentSAFTVRMie) getComponent(i);
      for (int a = 0; a < xSave[i].length; a++) {
        ci.setXsiteAssoc(a, xSave[i][a]);
      }
    }
    solveAssociation();
  }

  /**
   * Performs the full association calculation sequence: compute g and its derivatives, compute
   * delta, solve for XA, and compute hcpatot. Called inside the molarVolume solver at each
   * iteration.
   */
  private void solveAssociationFull() {
    if (useASSOC == 0) {
      return;
    }
    initAssocGDerivatives();
    computeDeltaAssoc();
    solveAssociation();
    calcAssocHCPA();
  }

  // ===== Association Helmholtz free energy and derivatives =====

  /**
   * Association Helmholtz free energy following PCSAFTa/CPA convention. F_ASSOC = sum_i ni * sum_A
   * [ln(X_Ai) - X_Ai/2 + 1/2].
   *
   * @return F_ASSOC
   */
  public double F_ASSOC_SAFT() {
    if (useASSOC == 0) {
      return 0.0;
    }
    double sum = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      ComponentSAFTVRMie ci = (ComponentSAFTVRMie) getComponent(i);
      int nSites = hasAssociationParams(i) ? getComponent(i).getNumberOfAssociationSites() : 0;
      double ni = ci.getNumberOfMolesInPhase();
      for (int a = 0; a < nSites; a++) {
        double xa = ci.getXsiteAssoc()[a];
        sum += ni * (Math.log(xa) - xa / 2.0 + 0.5);
      }
    }
    return sum;
  }

  /**
   * dF_ASSOC/dV (w.r.t. V_m3). Returns cached value computed during volInit.
   *
   * @return dF_ASSOC/dV_m3
   */
  public double dF_ASSOC_SAFTdV() {
    if (useASSOC == 0) {
      return 0.0;
    }
    return cacheddFAssocDV;
  }

  /**
   * d2F_ASSOC/dV2 (w.r.t. V_m3). Computed via cloned-phase numerical differentiation to correctly
   * capture the full implicit XA response (Michelsen-Hendriks Q-function correction). The
   * analytical product-rule of dFdV fails for strong association (e.g., water) because hcpatot is
   * nearly V-independent while F_ASSOC (through ln XA) varies steeply.
   *
   * @return second volume derivative in SI units (m^-6)
   */
  public double dF_ASSOC_SAFTdVdV() {
    if (useASSOC == 0) {
      return 0.0;
    }
    if (!assocDVDVValid) {
      computeAssocDVDV();
    }
    return cacheddFAssocDVDV;
  }

  /**
   * Computes d2F_ASSOC/dV_SI2 numerically via cloned phases. Clones the phase, perturbs molar
   * volume, runs volInit on the clone (which re-solves XA), and takes central difference of
   * F_ASSOC. This avoids corrupting the original phase state.
   */
  private void computeAssocDVDV() {
    double vm = getMolarVolume();
    double dvm = vm * 1.0e-6;
    double n = numberOfMolesInPhase;
    double dVneq = dvm * n;
    double dVSI = dVneq * 1.0e-5;
    double F0 = F_ASSOC_SAFT();

    PhaseSAFTVRMie clonePlus = (PhaseSAFTVRMie) this.clone();
    clonePlus.setMolarVolume(vm + dvm);
    clonePlus.volInit();
    double FPlus = clonePlus.F_ASSOC_SAFT();

    PhaseSAFTVRMie cloneMinus = (PhaseSAFTVRMie) this.clone();
    cloneMinus.setMolarVolume(vm - dvm);
    cloneMinus.volInit();
    double FMinus = cloneMinus.F_ASSOC_SAFT();

    // Use fresh F0, not cachedFAssoc, to ensure consistency
    cacheddFAssocDVDV = (FPlus - 2.0 * F0 + FMinus) / (dVSI * dVSI);
    assocDVDVValid = true;
  }

  /**
   * dF_ASSOC/dT.
   *
   * @return temperature derivative
   */
  public double dF_ASSOC_SAFTdT() {
    if (useASSOC == 0) {
      return 0.0;
    }
    return -0.5 * hcpatotdT;
  }

  /**
   * d2F_ASSOC/dT2.
   *
   * @return second temperature derivative
   */
  public double dF_ASSOC_SAFTdTdT() {
    if (useASSOC == 0) {
      return 0.0;
    }
    return -0.5 * hcpatotdTdT;
  }

  /**
   * d2F_ASSOC/dTdV (w.r.t. V_m3). Numerical via central difference.
   *
   * @return cross derivative
   */
  public double dF_ASSOC_SAFTdTdV() {
    if (useASSOC == 0) {
      return 0.0;
    }
    // Numerical: perturb volume, compute dFdT at V+dV and V-dV
    double Vtotal = getMolarVolume() * getNumberOfMolesInPhase();
    double dV = Math.max(Math.abs(Vtotal) * 1.0e-6, 1.0e-15);
    double n = getNumberOfMolesInPhase();
    double vmOrig = getMolarVolume();

    setMolarVolume((Vtotal + dV) / n);
    volInit();
    solveAssociationFull();
    double dFdTplus = dF_ASSOC_SAFTdT();

    setMolarVolume((Vtotal - dV) / n);
    volInit();
    solveAssociationFull();
    double dFdTminus = dF_ASSOC_SAFTdT();

    // Restore
    setMolarVolume(vmOrig);
    volInit();
    solveAssociationFull();

    return (dFdTplus - dFdTminus) / (2.0 * dV);
  }

  /**
   * Returns total number of association sites in this phase.
   *
   * @return total sites
   */
  public int getTotalNumberOfAssociationSites() {
    return totalNumberOfAssociationSites;
  }

  /**
   * Returns association toggle.
   *
   * @return useASSOC
   */
  public int getUseASSOC() {
    return useASSOC;
  }

  /**
   * Returns hcpatot (sum of (1-XA)*n over all sites).
   *
   * @return hcpatot
   */
  public double getHcpatot() {
    return hcpatot;
  }

  /**
   * Returns association g (hard-sphere contact RDF used in delta).
   *
   * @return gcpaAssoc
   */
  public double getGcpaAssoc() {
    return gcpaAssoc;
  }

  /**
   * Returns the cross-association scheme indicator between sites of two components.
   *
   * @param comp1 component 1 index
   * @param comp2 component 2 index
   * @param site1 site index on comp1
   * @param site2 site index on comp2
   * @return 1 if sites can bond, 0 otherwise
   */
  public int getCrossAssociationScheme(int comp1, int comp2, int site1, int site2) {
    if (crossAssocScheme == null || comp1 >= crossAssocScheme.length
        || comp2 >= crossAssocScheme[comp1].length || site1 >= crossAssocScheme[comp1][comp2].length
        || site2 >= crossAssocScheme[comp1][comp2][site1].length) {
      return 0;
    }
    return crossAssocScheme[comp1][comp2][site1][site2];
  }

  /**
   * Returns the global delta between two sites (global indexing).
   *
   * @param globalSiteA global site index A
   * @param globalSiteB global site index B
   * @return delta value
   */
  public double getDeltaAssoc(int globalSiteA, int globalSiteB) {
    if (deltaAssoc == null) {
      return 0.0;
    }
    return deltaAssoc[globalSiteA][globalSiteB];
  }

  /**
   * Returns the site offset for component i (first global site index of component i).
   *
   * @param compIndex component index
   * @return site offset
   */
  public int getSiteOffset(int compIndex) {
    if (siteOffset == null) {
      return 0;
    }
    return siteOffset[compIndex];
  }

  // ===== Helmholtz free energy and derivatives =====

  /** {@inheritDoc} */
  @Override
  public double getF() {
    return useHS * F_HC_SAFT() + useDISP * F_DISP_SAFT() + useASSOC * F_ASSOC_SAFT();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
    return (useHS * dF_HC_SAFTdV() + useDISP * dF_DISP_SAFTdV() + useASSOC * dF_ASSOC_SAFTdV())
        * 1.0e-5;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    return (useHS * dF_HC_SAFTdVdV() + useDISP * dF_DISP_SAFTdVdV()
        + useASSOC * dF_ASSOC_SAFTdVdV()) * 1.0e-10;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdVdV() {
    // Numerical third volume derivative via central difference of dFdVdV
    double Vtotal = getMolarVolume() * getNumberOfMolesInPhase(); // neqsim volume
    double dV = Math.max(Math.abs(Vtotal) * 1.0e-5, 1.0e-15);
    double n = getNumberOfMolesInPhase();

    double vmOrig = getMolarVolume();

    setMolarVolume((Vtotal + dV) / n);
    volInit();
    double dFdVdVplus = dFdVdV();

    setMolarVolume((Vtotal - dV) / n);
    volInit();
    double dFdVdVminus = dFdVdV();

    // Restore original state
    setMolarVolume(vmOrig);
    volInit();

    return (dFdVdVplus - dFdVdVminus) / (2.0 * dV) * 1.0e-5;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdT() {
    return useHS * dF_HC_SAFTdT() + useDISP * dF_DISP_SAFTdT() + useASSOC * dF_ASSOC_SAFTdT();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdT() {
    return useHS * dF_HC_SAFTdTdT() + useDISP * dF_DISP_SAFTdTdT() + useASSOC * dF_ASSOC_SAFTdTdT();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdV() {
    return (useHS * dF_HC_SAFTdTdV() + useDISP * dF_DISP_SAFTdTdV()
        + useASSOC * dF_ASSOC_SAFTdTdV()) * 1.0e-5;
  }

  // ===== Hard-chain contribution =====

  /**
   * Hard-chain Helmholtz free energy. F_HC = n * [m * a_HS - (m-1) * ln(g_HS)]
   *
   * @return F_HC
   */
  public double F_HC_SAFT() {
    return getNumberOfMolesInPhase() * (mSAFT * aHSSAFT - mmin1SAFT * Math.log(ghsSAFT));
  }

  /**
   * dF_HC/dV (w.r.t. V_m3).
   *
   * @return derivative
   */
  public double dF_HC_SAFTdV() {
    return getNumberOfMolesInPhase()
        * (mSAFT * daHSSAFTdN * dnSAFTdV - mmin1SAFT / ghsSAFT * dgHSSAFTdN * dnSAFTdV);
  }

  /**
   * d2F_HC/dV2 (w.r.t. V_m3).
   *
   * @return derivative
   */
  public double dF_HC_SAFTdVdV() {
    double n = getNumberOfMolesInPhase();
    return n * (mSAFT * daHSSAFTdNdN * dnSAFTdV * dnSAFTdV + mSAFT * daHSSAFTdN * dnSAFTdVdV
        + mmin1SAFT * Math.pow(ghsSAFT, -2.0) * Math.pow(dgHSSAFTdN, 2.0) * dnSAFTdV * dnSAFTdV
        - mmin1SAFT / ghsSAFT * dgHSSAFTdNdN * dnSAFTdV * dnSAFTdV
        - mmin1SAFT / ghsSAFT * dgHSSAFTdN * dnSAFTdVdV);
  }

  /**
   * dF_HC/dT.
   *
   * @return derivative
   */
  public double dF_HC_SAFTdT() {
    return getNumberOfMolesInPhase()
        * (mSAFT * daHSSAFTdN * dNSAFTdT - mmin1SAFT / ghsSAFT * dgHSSAFTdN * dNSAFTdT);
  }

  /**
   * d2F_HC/dT2.
   *
   * @return derivative
   */
  public double dF_HC_SAFTdTdT() {
    double n = getNumberOfMolesInPhase();
    return n * (mSAFT * daHSSAFTdNdN * dNSAFTdT * dNSAFTdT + mSAFT * daHSSAFTdN * dNSAFTdTdT
        + mmin1SAFT * Math.pow(ghsSAFT, -2.0) * Math.pow(dgHSSAFTdN, 2.0) * dNSAFTdT * dNSAFTdT
        - mmin1SAFT / ghsSAFT * dgHSSAFTdNdN * dNSAFTdT * dNSAFTdT
        - mmin1SAFT / ghsSAFT * dgHSSAFTdN * dNSAFTdTdT);
  }

  /**
   * d2F_HC/dTdV.
   *
   * @return derivative
   */
  public double dF_HC_SAFTdTdV() {
    double n = getNumberOfMolesInPhase();
    return n * (mSAFT * daHSSAFTdNdN * dNSAFTdT * dnSAFTdV + mSAFT * daHSSAFTdN * dNSAFTdTdV
        + mmin1SAFT * Math.pow(ghsSAFT, -2.0) * Math.pow(dgHSSAFTdN, 2.0) * dNSAFTdT * dnSAFTdV
        - mmin1SAFT / ghsSAFT * dgHSSAFTdNdN * dNSAFTdT * dnSAFTdV
        - mmin1SAFT / ghsSAFT * dgHSSAFTdN * dNSAFTdTdV);
  }

  // ===== Dispersion contribution (Lafitte 2013) =====

  /**
   * Total dispersion Helmholtz free energy. F_disp = n * m_bar * (a1 + a2 + a3) where m_bar is the
   * mean segment number.
   *
   * @return F_DISP
   */
  public double F_DISP_SAFT() {
    return getNumberOfMolesInPhase() * mSAFT * (a1Disp + a2Disp + a3Disp);
  }

  /**
   * dF_DISP/dV (w.r.t. V_m3).
   *
   * @return derivative
   */
  public double dF_DISP_SAFTdV() {
    return getNumberOfMolesInPhase() * mSAFT
        * ((da1DispDeta + da2DispDeta + da3DispDeta) * dnSAFTdV);
  }

  /**
   * d2F_DISP/dV2 (w.r.t. V_m3).
   *
   * @return derivative
   */
  public double dF_DISP_SAFTdVdV() {
    return getNumberOfMolesInPhase() * mSAFT
        * ((d2a1DispDeta2 + d2a2DispDeta2 + d2a3DispDeta2) * dnSAFTdV * dnSAFTdV
            + (da1DispDeta + da2DispDeta + da3DispDeta) * dnSAFTdVdV);
  }

  /**
   * dF_DISP/dT.
   *
   * @return derivative
   */
  public double dF_DISP_SAFTdT() {
    return getNumberOfMolesInPhase() * mSAFT * (da1DispDT + da2DispDT + da3DispDT
        + (da1DispDeta + da2DispDeta + da3DispDeta) * dNSAFTdT);
  }

  /**
   * d2F_DISP/dT2.
   *
   * @return derivative
   */
  public double dF_DISP_SAFTdTdT() {
    return getNumberOfMolesInPhase() * mSAFT
        * (d2a1DispDT2 + d2a2DispDT2 + d2a3DispDT2
            + 2.0 * (d2a1DispDetaDT + d2a2DispDetaDT + d2a3DispDetaDT) * dNSAFTdT
            + (d2a1DispDeta2 + d2a2DispDeta2 + d2a3DispDeta2) * dNSAFTdT * dNSAFTdT
            + (da1DispDeta + da2DispDeta + da3DispDeta) * dNSAFTdTdT);
  }

  /**
   * d2F_DISP/dTdV.
   *
   * @return derivative
   */
  public double dF_DISP_SAFTdTdV() {
    return getNumberOfMolesInPhase() * mSAFT
        * ((d2a1DispDetaDT + d2a2DispDetaDT + d2a3DispDetaDT) * dnSAFTdV
            + (d2a1DispDeta2 + d2a2DispDeta2 + d2a3DispDeta2) * dNSAFTdT * dnSAFTdV
            + (da1DispDeta + da2DispDeta + da3DispDeta) * dNSAFTdTdV);
  }

  // ===== Molar volume solver =====

  /**
   * {@inheritDoc}
   *
   * <p>
   * Uses pressure-residual Newton-Raphson solver following PhasePCSAFTRahmat pattern.
   * </p>
   */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    // SAFT-specific initial guess using segment diameter and packing fraction.
    // The SRK-based BonV guess doesn't work well for SAFT-VR Mie near spinodals.
    double initialVmNeqsim = -1.0;

    if (cachedMolarVolume > 1.0e-10) {
      // Validate cached volume is consistent with requested phase type.
      // Estimate eta from cached volume to check if liquid-like or gas-like.
      double dCheck = 0.0;
      double nMolesCheck = getNumberOfMolesInPhase();
      for (int i = 0; i < numberOfComponents; i++) {
        double xi = getComponent(i).getNumberOfMolesInPhase() / nMolesCheck;
        double di = 0.0;
        if (getComponent(i) instanceof ComponentSAFTVRMie) {
          di = ((ComponentSAFTVRMie) getComponent(i)).getdSAFTi();
        }
        if (di < 1.0e-15) {
          di = getComponent(i).getSigmaSAFTi();
        }
        if (di > 1.0e-15) {
          dCheck += xi * getComponent(i).getmSAFTi() * Math.pow(di, 3.0);
        }
      }
      double segVolCheck = Math.PI / 6.0 * 6.023e23 * dCheck;
      double VmSI_cached = cachedMolarVolume * 1.0e-5;
      double etaCached = (VmSI_cached > 0 && segVolCheck > 0) ? segVolCheck / VmSI_cached : -1.0;
      // Gas should have eta < 0.1, liquid should have eta > 0.15
      boolean cacheMatchesType =
          (pt == PhaseType.GAS && etaCached < 0.15) || (pt != PhaseType.GAS && etaCached > 0.1);
      if (cacheMatchesType) {
        initialVmNeqsim = cachedMolarVolume;
      }
      // If cache doesn't match, fall through to generate fresh initial guess
    } else {
      // Compute SAFT-based initial molar volume from target packing fraction.
      // eta = pi/6 * N_A * m * d^3 / Vm_SI => Vm_SI = pi/6 * N_A * m * d^3 / eta
      double dAvg = 0.0;
      double nMoles = getNumberOfMolesInPhase();
      for (int i = 0; i < numberOfComponents; i++) {
        double xi = getComponent(i).getNumberOfMolesInPhase() / nMoles;
        double di = 0.0;
        if (getComponent(i) instanceof ComponentSAFTVRMie) {
          di = ((ComponentSAFTVRMie) getComponent(i)).getdSAFTi();
        }
        if (di < 1.0e-15) {
          // BH diameter not yet computed — use sigma as fallback
          di = getComponent(i).getSigmaSAFTi();
        }
        if (di > 1.0e-15) {
          dAvg += xi * getComponent(i).getmSAFTi() * Math.pow(di, 3.0);
        }
      }
      double segVol = Math.PI / 6.0 * 6.023e23 * dAvg; // N_A * sum(xi*mi*di^3) * pi/6
      if (segVol > 1.0e-15 && pt != PhaseType.GAS) {
        // Liquid: target eta ~ 0.35 (typical liquid packing)
        double targetEta = 0.35;
        double VmSI = segVol / targetEta; // m3/mol
        initialVmNeqsim = VmSI * 1.0e5;
      } else if (segVol > 1.0e-15 && pt == PhaseType.GAS) {
        // Gas: use ideal gas as starting point, then fall back to SRK if needed
        double VmSI_ideal = R * temperature / (pressure * 1.0e5); // R*T/P in m3/mol
        initialVmNeqsim = VmSI_ideal * 1.0e5 * nMoles;
      }
    }

    if (initialVmNeqsim < 1.0e-10) {
      // Fallback to SRK-based initial guess
      double BonV =
          pt == PhaseType.GAS ? pressure * getB() / (numberOfMolesInPhase * temperature * R)
              : 2.0 / (2.0 + temperature / getPseudoCriticalTemperature());
      BonV = Math.max(1.0e-4, Math.min(1.0 - 1.0e-4, BonV));
      double Btemp = getB();
      if (Btemp <= 0) {
        logger.info("b negative in SAFT-VR Mie volume calc");
      }
      initialVmNeqsim = 1.0 / BonV * Btemp / numberOfMolesInPhase;
    }

    setMolarVolume(initialVmNeqsim);

    // Volume solver for SAFT-VR Mie.
    // For chain molecules (m > 1) or associated fluids, use scan+bisect for robustness.
    // Gas roots and non-associated monomer (m=1) fluids use homotopy continuation + Newton.
    boolean needsChainCorrection = mmin1SAFT > 1.0e-10 || calcmmin1SAFT() > 1.0e-10;
    boolean needsScanBisect = needsChainCorrection || useASSOC > 0;
    gMieCorrectionEnabled = true;

    // Compute SAFT segment volume for packing fraction (eta) calculations
    double segVolForCheck = 0.0;
    {
      double nMolesCheck2 = getNumberOfMolesInPhase();
      for (int i = 0; i < numberOfComponents; i++) {
        double xi = getComponent(i).getNumberOfMolesInPhase() / nMolesCheck2;
        double di = 0.0;
        if (getComponent(i) instanceof ComponentSAFTVRMie) {
          di = ((ComponentSAFTVRMie) getComponent(i)).getdSAFTi();
        }
        if (di < 1.0e-15) {
          di = getComponent(i).getSigmaSAFTi();
        }
        if (di > 1.0e-15) {
          segVolForCheck += xi * getComponent(i).getmSAFTi() * Math.pow(di, 3.0);
        }
      }
      segVolForCheck *= Math.PI / 6.0 * 6.023e23;
    }

    if (needsScanBisect && pt != PhaseType.GAS && segVolForCheck > 1e-15) {
      // --- LIQUID ROOT for chain molecules or associated fluids: scan + bisect ---
      gMieBlendFraction = 1.0;

      // Scan P(V) from dense (eta=0.52) to dilute (eta=0.05) to find liquid bracket
      int nScan = 40;
      double logVLo = Math.log(segVolForCheck / 0.52 * 1.0e5);
      double logVHi = Math.log(segVolForCheck / 0.05 * 1.0e5);
      double dLogV = (logVHi - logVLo) / nScan;

      double prevP = Double.NaN;
      double prevLogV = Double.NaN;
      double bracketLo = Double.NaN;
      double bracketHi = Double.NaN;

      for (int i = 0; i <= nScan; i++) {
        double logV = logVLo + i * dLogV;
        setMolarVolume(Math.exp(logV));
        this.volInit();
        double pScan = calcPressure();

        if (!Double.isNaN(prevP)) {
          // First crossing: P drops from above target to below (liquid branch)
          if (prevP >= pressure && pScan < pressure) {
            bracketLo = prevLogV;
            bracketHi = logV;
            break;
          }
        }
        prevP = pScan;
        prevLogV = logV;
      }

      if (!Double.isNaN(bracketLo)) {
        // Bisect in log-V space to machine precision
        for (int bisIter = 0; bisIter < 60; bisIter++) {
          double logVMid = 0.5 * (bracketLo + bracketHi);
          setMolarVolume(Math.exp(logVMid));
          this.volInit();
          double pMid = calcPressure();

          if (pMid > pressure) {
            bracketLo = logVMid;
          } else {
            bracketHi = logVMid;
          }
          if (bracketHi - bracketLo < 1.0e-13) {
            break;
          }
        }
        double logVFinal = 0.5 * (bracketLo + bracketHi);
        setMolarVolume(Math.exp(logVFinal));
        this.volInit();
        Z = pressure * getMolarVolume() / (R * temperature);
      } else {
        // No bracket found (P below spinodal minimum — no liquid root at this P).
        // Fall back to homotopy: best-effort volume for the VLE solver.
        setMolarVolume(initialVmNeqsim);
        double[] alphaStepsFb = new double[] {0.0, 0.1, 0.2, 0.3, 0.5, 0.7, 0.9, 1.0};
        for (double alpha : alphaStepsFb) {
          gMieBlendFraction = alpha;
          double logVm = Math.log(getMolarVolume());
          double oldLogVm = logVm;
          int iter = 0;
          do {
            iter++;
            this.volInit();
            oldLogVm = logVm;
            double Vtotal = getMolarVolume() * numberOfMolesInPhase;
            double pCalc = calcPressure();
            double dPdVtotal = calcPressuredV();
            double h = pressure - pCalc;
            if (Math.abs(dPdVtotal) < 1.0e-100) {
              break;
            }
            double deltaLogV = h / (Vtotal * dPdVtotal);
            deltaLogV = Math.max(-2.0, Math.min(2.0, deltaLogV));
            logVm = logVm + 0.9 * deltaLogV;
            setMolarVolume(Math.exp(logVm));
            Z = pressure * getMolarVolume() / (R * temperature);
          } while (Math.abs(logVm - oldLogVm) > 1.0e-8 && iter < 300);
        }
      }
    } else if (needsScanBisect && pt == PhaseType.GAS && segVolForCheck > 1e-15) {
      // --- GAS ROOT for chain molecules or associated fluids: scan + bisect ---
      gMieBlendFraction = 1.0;

      // Scan P(V) from dilute (large V) to moderate density.
      // Gas branch: P increases as V decreases until the gas spinodal.
      // Restrict to gas-like densities (eta < 0.10) to avoid unstable region.
      // The upper volume bound must encompass the ideal gas solution:
      // V_ideal = n*R*T/P (in NeqSim units). Use 2x ideal gas as safety margin.
      int nScan = 50;
      double logVLo = Math.log(segVolForCheck / 0.10 * 1.0e5); // moderate gas density
      double idealGasVol = R * temperature / pressure; // per-mole ideal gas V in NeqSim units
      double logVFromEta = Math.log(segVolForCheck / 0.001 * 1.0e5);
      double logVFromIdeal = Math.log(idealGasVol * 2.0); // 2x ideal gas
      double logVHi = Math.max(logVFromEta, logVFromIdeal); // take the larger bound
      double dLogV = (logVHi - logVLo) / nScan;

      double prevP = Double.NaN;
      double prevLogV = Double.NaN;
      double bracketLo = Double.NaN;
      double bracketHi = Double.NaN;

      // Track the gas spinodal (max P on gas branch) for fallback
      double maxPScan = -1e30;
      double logVAtMaxP = logVHi;

      // Scan from dilute (high V, high index) to dense (low V, low index)
      for (int i = nScan; i >= 0; i--) {
        double logV = logVLo + i * dLogV;
        setMolarVolume(Math.exp(logV));
        this.volInit();
        double pScan = calcPressure();

        if (pScan > maxPScan) {
          maxPScan = pScan;
          logVAtMaxP = logV;
        }

        if (!Double.isNaN(prevP)) {
          // Gas root crossing: P rises through target as V decreases
          if (prevP <= pressure && pScan > pressure) {
            bracketHi = prevLogV; // larger V, lower P
            bracketLo = logV; // smaller V, higher P
            break;
          }
        }
        prevP = pScan;
        prevLogV = logV;
      }

      if (!Double.isNaN(bracketLo)) {
        // Bisect: bracketLo has P > target (smaller V), bracketHi has P < target (larger V)
        for (int bisIter = 0; bisIter < 60; bisIter++) {
          double logVMid = 0.5 * (bracketLo + bracketHi);
          setMolarVolume(Math.exp(logVMid));
          this.volInit();
          double pMid = calcPressure();

          if (pMid > pressure) {
            bracketLo = logVMid;
          } else {
            bracketHi = logVMid;
          }
          if (bracketHi - bracketLo < 1.0e-13) {
            break;
          }
        }
        double logVFinal = 0.5 * (bracketLo + bracketHi);
        setMolarVolume(Math.exp(logVFinal));
        this.volInit();
        Z = pressure * getMolarVolume() / (R * temperature);
      } else {
        // No gas root found: P > gas spinodal max (above Psat).
        // Use the gas spinodal volume as fallback. This gives a physically
        // meaningful gas-like state whose fugacity coefficient properly reflects
        // that the pressure is too high for a gas to exist, driving the VLE
        // solver to reduce pressure.
        setMolarVolume(Math.exp(logVAtMaxP));
        this.volInit();
        Z = pressure * getMolarVolume() / (R * temperature);
      }
    } else {
      // --- Non-associated MONOMER (m=1): homotopy continuation + Newton ---
      double[] alphaSteps =
          needsChainCorrection ? new double[] {0.0, 0.1, 0.2, 0.3, 0.5, 0.7, 0.9, 1.0}
              : new double[] {0.0};
      double logVmTol = needsChainCorrection ? 1.0e-8 : 1.0e-10;

      for (double alpha : alphaSteps) {
        gMieBlendFraction = alpha;

        double logVm = Math.log(getMolarVolume());
        double oldLogVm = logVm;
        int iterations = 0;
        int maxIterations = 300;

        do {
          iterations++;
          this.volInit();
          oldLogVm = logVm;
          double Vtotal = getMolarVolume() * numberOfMolesInPhase;
          double pCalc = calcPressure();
          double dPdVtotal = calcPressuredV();
          double h = pressure - pCalc;

          if (Math.abs(dPdVtotal) < 1.0e-100) {
            break;
          }

          double deltaLogV = h / (Vtotal * dPdVtotal);
          deltaLogV = Math.max(-2.0, Math.min(2.0, deltaLogV));

          logVm = logVm + 0.9 * deltaLogV;
          setMolarVolume(Math.exp(logVm));
          Z = pressure * getMolarVolume() / (R * temperature);
        } while (Math.abs(logVm - oldLogVm) > logVmTol && iterations < maxIterations);

        if (iterations >= maxIterations) {
          throw new neqsim.util.exception.TooManyIterationsException(this, "molarVolume",
              maxIterations);
        }
      }
    }

    // Ensure full g_Mie is active for subsequent property calculations
    gMieBlendFraction = 1.0;

    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Molar volume");
    }
    cachedMolarVolume = getMolarVolume();
    return getMolarVolume();
  }

  // ===== Composition helpers =====

  /**
   * Calculates mean segment number m-bar = sum(xi * mi).
   *
   * @return mean segment number
   */
  public double calcmSAFT() {
    double temp = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp += getComponent(i).getNumberOfMolesInPhase() / getNumberOfMolesInPhase()
          * getComponent(i).getmSAFTi();
    }
    return temp;
  }

  /**
   * Calculates sum(xi * (mi - 1)).
   *
   * @return m-1 average
   */
  public double calcmmin1SAFT() {
    double temp = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp += getComponent(i).getNumberOfMolesInPhase() / getNumberOfMolesInPhase()
          * (getComponent(i).getmSAFTi() - 1.0);
    }
    return temp;
  }

  /**
   * Calculates sum(xi * mi * di^3).
   *
   * @return d^3 weighted sum
   */
  public double calcdSAFT() {
    double temp = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp += getComponent(i).getNumberOfMolesInPhase() / getNumberOfMolesInPhase()
          * getComponent(i).getmSAFTi()
          * Math.pow(((ComponentSAFTVRMie) getComponent(i)).getdSAFTi(), 3.0);
    }
    return temp;
  }

  /**
   * Calculates mean diameter from (sum ni*mi*di^3 / sum ni*mi)^(1/3).
   *
   * @return mean diameter in m
   */
  public double calcdmeanSAFT() {
    double num = 0.0;
    double den = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      num += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi()
          * Math.pow(((ComponentSAFTVRMie) getComponent(i)).getdSAFTi(), 3.0);
      den += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi();
    }
    if (den < 1.0e-100) {
      return 0.0;
    }
    return Math.pow(num / den, 1.0 / 3.0);
  }

  /**
   * Temperature derivative of dSAFT = sum(xi * mi * 3*di^2 * ddi/dT).
   *
   * @return d(dSAFT)/dT
   */
  public double getdDSAFTdT() {
    double temp = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      ComponentSAFTVRMie comp = (ComponentSAFTVRMie) getComponent(i);
      double ddi = comp.getdSAFTi();
      double ddidT = comp.getDdSAFTidT();
      temp += getComponent(i).getNumberOfMolesInPhase() / getNumberOfMolesInPhase()
          * getComponent(i).getmSAFTi() * 3.0 * ddi * ddi * ddidT;
    }
    return temp;
  }

  /**
   * Second temperature derivative of dSAFT (zero approx).
   *
   * @return d2(dSAFT)/dT2
   */
  public double getd2DSAFTdTdT() {
    return 0.0;
  }

  // ===== Legacy / compatibility getters needed by ComponentSAFTVRMie =====

  /**
   * Returns the d^3 weighted sum for component access.
   *
   * @return dSAFT
   */
  public double getDSAFT() {
    return dSAFT;
  }

  /**
   * Returns daHS/deta.
   *
   * @return daHSSAFTdN
   */
  public double getDaHSSAFTdN() {
    return daHSSAFTdN;
  }

  /**
   * Returns dgHS/deta.
   *
   * @return dgHSSAFTdN
   */
  public double getDgHSSAFTdN() {
    return dgHSSAFTdN;
  }

  /**
   * Returns the SAFT volume in m3.
   *
   * @return volumeSAFT
   */
  public double getVolumeSAFT() {
    return volumeSAFT;
  }

  /**
   * Returns the packing fraction.
   *
   * @return nSAFT (eta)
   */
  public double getNSAFT() {
    return nSAFT;
  }

  /**
   * Returns mean segment number.
   *
   * @return m-bar
   */
  public double getmSAFT() {
    return mSAFT;
  }

  /**
   * Returns the per-component weighted dispersion sum: aDispPerComp[i] = sum_l xs_l *
   * (a1_il+a2_il+a3_il). Used for analytical dF_DISP/dNi. Returns 0 if not computed (single
   * component).
   *
   * @param compIndex component index
   * @return per-component dispersion sum
   */
  public double getADispPerComp(int compIndex) {
    if (aDispPerComp != null && compIndex >= 0 && compIndex < aDispPerComp.length) {
      return aDispPerComp[compIndex];
    }
    return a1Disp + a2Disp + a3Disp; // For pure component, equals total aDisp
  }

  /**
   * Returns hard-sphere RDF at contact.
   *
   * @return ghs
   */
  public double getGhsSAFT() {
    return ghsSAFT;
  }

  /**
   * Returns hard-sphere Helmholtz energy per mole.
   *
   * @return aHS
   */
  public double getAHSSAFT() {
    return aHSSAFT;
  }

  /**
   * Returns sum(xi*(mi-1)).
   *
   * @return mmin1SAFT
   */
  public double getMmin1SAFT() {
    return mmin1SAFT;
  }

  /**
   * Returns useHS toggle.
   *
   * @return useHS
   */
  public int getUseHS() {
    return useHS;
  }

  /**
   * Returns useDISP toggle.
   *
   * @return useDISP
   */
  public int getUseDISP() {
    return useDISP;
  }

  /**
   * Returns first-order dispersion perturbation a1/(NkT).
   *
   * @return a1Disp
   */
  public double getA1Disp() {
    return a1Disp;
  }

  /**
   * Returns second-order dispersion perturbation a2/(NkT).
   *
   * @return a2Disp
   */
  public double getA2Disp() {
    return a2Disp;
  }

  /**
   * Returns third-order dispersion perturbation a3/(NkT).
   *
   * @return a3Disp
   */
  public double getA3Disp() {
    return a3Disp;
  }

  /**
   * Returns da1Disp/deta derivative.
   *
   * @return da1Disp/deta
   */
  public double getDa1DispDeta() {
    return da1DispDeta;
  }

  /**
   * Returns da2Disp/deta derivative.
   *
   * @return da2Disp/deta
   */
  public double getDa2DispDeta() {
    return da2DispDeta;
  }

  /**
   * Returns da3Disp/deta derivative.
   *
   * @return da3Disp/deta
   */
  public double getDa3DispDeta() {
    return da3DispDeta;
  }

  /**
   * Returns the d(dSAFT)/dN_i helper for composition differentiation.
   *
   * @param mi segment number of component i
   * @param di diameter of component i
   * @param nMoles total moles
   * @return d(dSAFT)/dN_i contribution (partial through d^3 sum)
   */
  public double getdDSAFTdTprime(double mi, double di, double nMoles) {
    return mi * Math.pow(di, 3.0) / nMoles - dSAFT / nMoles;
  }

  // ===== Legacy dispersion getters (stubs for ComponentSAFTVRMie compatibility) =====
  // These are used by ComponentSAFTVRMie.Finit() in the old decomposed derivative approach.
  // With the new Lafitte formulation, dFdN is computed numerically in the component.

  /**
   * Returns the dispersion volume term (legacy, stub).
   *
   * @return 0.0
   */
  public double getF1dispVolTerm() {
    return 0.0;
  }

  /**
   * Returns F1 dispersion sum term (legacy, stub).
   *
   * @return 0.0
   */
  public double getF1dispSumTerm() {
    return 0.0;
  }

  /**
   * Returns F2 dispersion sum term (legacy, stub).
   *
   * @return 0.0
   */
  public double getF2dispSumTerm() {
    return 0.0;
  }

  /**
   * Returns I1 integral derivative w.r.t. eta (legacy, stub).
   *
   * @return 0.0
   */
  public double calcF1dispI1dN() {
    return 0.0;
  }

  /**
   * Returns I1 integral derivative w.r.t. m (legacy, stub).
   *
   * @return 0.0
   */
  public double calcF1dispI1dm() {
    return 0.0;
  }

  /**
   * Returns I2 integral derivative w.r.t. eta (legacy, stub).
   *
   * @return 0.0
   */
  public double calcF2dispI2dN() {
    return 0.0;
  }

  /**
   * Returns I2 integral derivative w.r.t. m (legacy, stub).
   *
   * @return 0.0
   */
  public double calcF2dispI2dm() {
    return 0.0;
  }

  /**
   * Returns the ZHC factor (legacy, stub returns 1.0 to avoid division by zero).
   *
   * @return 1.0
   */
  public double getF2dispZHC() {
    return 1.0;
  }
}
