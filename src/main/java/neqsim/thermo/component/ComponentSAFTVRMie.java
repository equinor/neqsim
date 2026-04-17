package neqsim.thermo.component;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseSAFTVRMie;

/**
 * Component class for the SAFT-VR Mie equation of state.
 *
 * <p>
 * Implements the Lafitte et al. (2013) SAFT-VR Mie formulation with variable-range Mie potential.
 * The Mie potential generalizes the Lennard-Jones potential with adjustable repulsive (lambda_r)
 * and attractive (lambda_a) exponents.
 * </p>
 *
 * <p>
 * Reference: Lafitte, T., Apostolakou, A., Avendano, C., Galindo, A., Adjiman, C.S., Mueller, E.A.,
 * Jackson, G. (2013). Accurate statistical associating fluid theory for chain molecules formed from
 * Mie segments. J. Chem. Phys., 139, 154504.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentSAFTVRMie extends ComponentSrk {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Temperature-dependent effective diameter for component i. */
  protected double dSAFTi = 0.0;

  /** Temperature at which dSAFTi was last computed. */
  protected double componentTemperature = 300.0;

  /** Derivative of packing fraction with respect to moles of component i. */
  protected double dnSAFTdi = 0.0;

  /** Derivative of hard-sphere RDF with respect to moles of component i. */
  protected double dghsSAFTdi = 0.0;

  /** Derivative of hard-sphere Helmholtz energy with respect to moles of component i. */
  protected double dahsSAFTdi = 0.0;

  /** Derivative of mean segment number with respect to moles of component i. */
  protected double dmSAFTdi = 0.0;

  /** Derivative of log(ghs) with respect to moles of component i. */
  protected double dlogghsSAFTdi = 0.0;

  // Component-level dFdN contributions
  private double dF_HC_SAFTdN = 0.0;
  private double dF_DISP_SAFTdN = 0.0;

  // Dispersion sum derivatives w.r.t. ni
  private double F1dispSumTermdn = 0.0;
  private double F1dispVolTermdn = 0.0;
  private double F1dispI1dn = 0.0;
  private double F2dispSumTermdn = 0.0;
  private double F2dispVolTermdn = 0.0;
  private double F2dispI2dn = 0.0;
  private double F2dispZHCdn = 0.0;

  // ===== Association site fractions =====
  /** Association site fractions X_A for this component. */
  private double[] xsiteAssoc = null;

  /** Number of association sites on this component (as used in SAFT-VR Mie). */
  private int nAssocSites = 0;

  /**
   * Constructor for ComponentSAFTVRMie.
   *
   * @param name component name
   * @param moles number of moles
   * @param molesInPhase number of moles in phase
   * @param compNumber component number
   */
  public ComponentSAFTVRMie(String name, double moles, double molesInPhase, int compNumber) {
    super(name, moles, molesInPhase, compNumber);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns the SAFT-VR Mie specific segment number, not the PC-SAFT value.
   * </p>
   */
  @Override
  public double getmSAFTi() {
    double mVR = getmSAFTVRMie();
    return (mVR > 0) ? mVR : super.getmSAFTi();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns the SAFT-VR Mie specific segment diameter, not the PC-SAFT value.
   * </p>
   */
  @Override
  public double getSigmaSAFTi() {
    double sigVR = getSigmaSAFTVRMie();
    return (sigVR > 0) ? sigVR : super.getSigmaSAFTi();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns the SAFT-VR Mie specific energy parameter, not the PC-SAFT value.
   * </p>
   */
  @Override
  public double getEpsikSAFT() {
    double epsVR = getEpsikSAFTVRMie();
    return (epsVR > 0) ? epsVR : super.getEpsikSAFT();
  }

  /** {@inheritDoc} */
  @Override
  public ComponentSAFTVRMie clone() {
    ComponentSAFTVRMie clonedComponent = null;
    try {
      clonedComponent = (ComponentSAFTVRMie) super.clone();
      if (xsiteAssoc != null) {
        clonedComponent.xsiteAssoc = xsiteAssoc.clone();
      }
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedComponent;
  }

  // ===== Association methods =====

  /**
   * Initializes the association site fraction arrays. Called from
   * PhaseSAFTVRMie.initAssociationSchemes.
   *
   * @param nSites number of association sites for this component
   */
  public void initAssociationArrays(int nSites) {
    nAssocSites = nSites;
    xsiteAssoc = new double[nSites];
    for (int i = 0; i < nSites; i++) {
      xsiteAssoc[i] = 1.0; // Initial guess: no association
    }
  }

  /**
   * Returns the association site fraction array.
   *
   * @return xsiteAssoc array
   */
  public double[] getXsiteAssoc() {
    return xsiteAssoc;
  }

  /**
   * Sets a site fraction value.
   *
   * @param siteIndex site index
   * @param value new XA value
   */
  public void setXsiteAssoc(int siteIndex, double value) {
    if (xsiteAssoc != null && siteIndex < xsiteAssoc.length) {
      xsiteAssoc[siteIndex] = value;
    }
  }

  /**
   * Returns the number of association sites used in SAFT-VR Mie.
   *
   * @return number of sites
   */
  public int getNAssocSites() {
    return nAssocSites;
  }

  /**
   * Calculates the association contribution to dF/dNi. dFCPAdN = sum_A [ln(X_A^i)] - hcpatot/2 *
   * d(ln I)/d(ni), where I is the Dufal 2015 association integral used for the SAFT-VR Mie
   * association strength.
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in Pa
   * @return dF_ASSOC/dNi
   */
  public double dFCPAdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseSAFTVRMie sp = (PhaseSAFTVRMie) phase;
    if (sp.getUseASSOC() == 0 || nAssocSites == 0) {
      return 0.0;
    }

    // sum_A ln(X_A^i)
    double sumLnXA = 0.0;
    for (int a = 0; a < nAssocSites; a++) {
      sumLnXA += Math.log(xsiteAssoc[a]);
    }

    // d(ln I)/dNi using Dufal 2015 polynomial
    double NA = ThermodynamicConstantsInterface.avagadroNumber;
    double V_SI = sp.getVolumeSAFT();

    // Recompute segment density and reduced density
    double totalSegMoles = 0.0;
    double sigma3x = 0.0;
    double epsRef = 0.0;
    for (int k = 0; k < numberOfComponents; k++) {
      ComponentSAFTVRMie ck = (ComponentSAFTVRMie) phase.getComponent(k);
      double nk = ck.getNumberOfMolesInPhase();
      double mk = ck.getmSAFTi();
      double xSk = nk * mk;
      totalSegMoles += xSk;
      double sigk = ck.getSigmaSAFTi();
      sigma3x += xSk * xSk * sigk * sigk * sigk;
      if (epsRef == 0.0 && ck.getNumberOfAssociationSites() > 0
          && ck.getAssociationEnergySAFTVRMie() != 0.0) {
        epsRef = ck.getEpsikSAFT();
      }
    }
    if (totalSegMoles > 0) {
      sigma3x /= (totalSegMoles * totalSegMoles);
    }

    double rhoS = NA * totalSegMoles / V_SI;
    double rhoStar = rhoS * sigma3x;
    double Tr = (epsRef > 0) ? temperature / epsRef : 1.0;

    double I_val = PhaseSAFTVRMie.calcDufalI(Tr, rhoStar);
    double dIdRhoStar = PhaseSAFTVRMie.calcDufalIdRhoStar(Tr, rhoStar);

    // dRhoStar/dNi = NA * mi * sigma^3 / V_SI (pure component; neglects dsigma3x/dNi)
    double mi = getmSAFTi();
    double sigi = getSigmaSAFTi();
    double dRhoStarDNi = NA * mi * sigi * sigi * sigi / V_SI;

    double dlnIdNi = (I_val > 1.0e-30) ? (dIdRhoStar / I_val) * dRhoStarDNi : 0.0;

    return sumLnXA - sp.getHcpatot() / 2.0 * dlnIdNi;
  }

  /**
   * Calculates the Mie potential prefactor C.
   *
   * @param lr repulsive exponent
   * @param la attractive exponent
   * @return Mie potential prefactor
   */
  public static double calcMiePrefactor(double lr, double la) {
    return lr / (lr - la) * Math.pow(lr / la, la / (lr - la));
  }

  /**
   * Initializes component temperature-dependent diameter using the Barker-Henderson integral for
   * the Mie potential (evaluated analytically following Lafitte et al. 2013).
   *
   * @param temperature temperature in K
   * @param pressure pressure in Pa
   * @param totalNumberOfMoles total moles
   * @param beta phase fraction
   * @param initType initialization type
   */
  @Override
  public void init(double temperature, double pressure, double totalNumberOfMoles, double beta,
      int initType) {
    super.init(temperature, pressure, totalNumberOfMoles, beta, initType);
    componentTemperature = temperature;
    double sigma = getSigmaSAFTi();
    double epsk = getEpsikSAFT();
    double lr = getLambdaRSAFTVRMie();
    double la = getLambdaASAFTVRMie();

    // Barker-Henderson effective diameter via numerical quadrature
    // d = integral_0^sigma [1 - exp(-u(r)/(kT))] dr
    // Use the analytical approximation from Lafitte 2013 (Eq. 10):
    // d/sigma = sum_{k=0}^{K} c_k * (eps/kT)^k using series expansion
    // For efficiency, use the effective approximation:
    dSAFTi = calcEffectiveDiameter(sigma, epsk, temperature, lr, la);
  }

  /**
   * Recalculates the temperature-dependent effective BH diameter without running the full
   * Component.init(). Used by numerical derivatives that perturb temperature on cloned phases.
   *
   * @param temperature temperature in K
   */
  public void recalcSAFTDiameter(double temperature) {
    componentTemperature = temperature;
    dSAFTi = calcEffectiveDiameter(getSigmaSAFTi(), getEpsikSAFT(), temperature,
        getLambdaRSAFTVRMie(), getLambdaASAFTVRMie());
  }

  /**
   * Calculates effective BH diameter by numerical Gauss-Legendre quadrature of the Mie potential.
   *
   * @param sigma segment diameter in m
   * @param epsk energy parameter eps/k in K
   * @param temperature temperature in K
   * @param lr repulsive exponent
   * @param la attractive exponent
   * @return effective diameter in m
   */
  public static double calcEffectiveDiameter(double sigma, double epsk, double temperature,
      double lr, double la) {
    double cMie = calcMiePrefactor(lr, la);
    double theta = cMie * epsk / temperature;

    // 10-point Gauss-Legendre nodes and weights on [0,1]
    double[] glNodes10 = {0.01304673574, 0.06746831665, 0.16029521585, 0.28330230294, 0.42556283050,
        0.57443716950, 0.71669769706, 0.83970478415, 0.93253168335, 0.98695326426};
    double[] glWeights10 = {0.03333567215, 0.07472567458, 0.10954318126, 0.13463335965,
        0.14776211236, 0.14776211236, 0.13463335965, 0.10954318126, 0.07472567458, 0.03333567215};

    if (theta <= 1.0) {
      // Weak coupling: standard 10-point GL on [0, sigma]
      double sum = 0.0;
      for (int i = 0; i < 10; i++) {
        double x = glNodes10[i];
        if (x < 1.0e-20) {
          sum += glWeights10[i];
          continue;
        }
        double xInv = 1.0 / x;
        double uRed = theta * (Math.pow(xInv, lr) - Math.pow(xInv, la));
        sum += glWeights10[i] * (1.0 - Math.exp(-uRed));
      }
      return sum * sigma;
    }

    // Strong coupling (theta > 1): find cut-off point where exp(-u/kT) first becomes
    // significant, then split integration into [0, xCut] (≈ sigma) and [xCut, sigma] (GL).
    // Following Clapeyron's approach of finding where f(x)=exp(-u(x)/kT) ≈ eps_machine.
    double xCut = Math.exp(-Math.log(theta + 20.0) / lr);
    xCut = Math.max(0.5, Math.min(xCut, 0.999));

    // [0, xCut]: integrand ≈ 1 everywhere, contribution = xCut
    double sumLow = xCut;

    // [xCut, 1]: use 10-point GL on this narrower interval
    double a = xCut;
    double b = 1.0;
    double halfWidth = (b - a) / 2.0;
    double midPoint = (b + a) / 2.0;
    double sumHigh = 0.0;
    for (int i = 0; i < 10; i++) {
      double x = midPoint + halfWidth * (2.0 * glNodes10[i] - 1.0);
      if (x < 1.0e-20) {
        sumHigh += glWeights10[i] * (b - a);
        continue;
      }
      double xInv = 1.0 / x;
      double uRed = theta * (Math.pow(xInv, lr) - Math.pow(xInv, la));
      double fVal = 1.0 - Math.exp(-uRed);
      sumHigh += glWeights10[i] * fVal * (b - a);
    }

    return (sumLow + sumHigh) * sigma;
  }

  /**
   * Initializes SAFT-VR Mie component derivatives within the phase context.
   *
   * @param phase the phase
   * @param temperature temperature in K
   * @param pressure pressure in Pa
   * @param totalNumberOfMoles total moles
   * @param beta phase fraction
   * @param numberOfComponents number of components
   * @param initType initialization type
   */
  @Override
  public void Finit(PhaseInterface phase, double temperature, double pressure,
      double totalNumberOfMoles, double beta, int numberOfComponents, int initType) {
    PhaseSAFTVRMie saftPhase = (PhaseSAFTVRMie) phase;

    dnSAFTdi = calcdnSAFTdi(phase, numberOfComponents, temperature, pressure);
    dghsSAFTdi = calcdghsSAFTdi(phase, numberOfComponents, temperature, pressure);
    dahsSAFTdi = calcdahsSAFTdi(phase, numberOfComponents, temperature, pressure);
    dmSAFTdi = calcdmSAFTdi(phase, numberOfComponents, temperature, pressure);
    dlogghsSAFTdi = dghsSAFTdi / saftPhase.getGhsSAFT();

    dF_HC_SAFTdN = dF_HC_SAFTdN(phase, numberOfComponents, temperature, pressure);
    dF_DISP_SAFTdN = dF_DISP_SAFTdN(phase, numberOfComponents, temperature, pressure);

    // Compute EOS-level quantities (Bi, Ai, voli) needed for logfugcoefdT/dP/dN
    super.Finit(phase, temperature, pressure, totalNumberOfMoles, beta, numberOfComponents,
        initType);
  }

  /**
   * Returns the derivative of the reduced residual Helmholtz energy with respect to moles of
   * component i at constant T and total volume V.
   *
   * <p>
   * For a pure component, uses the exact thermodynamic identity: dF/dN = F/n - v * dF/dV_neqsim,
   * where v is the molar volume and dF/dV is the already-computed analytical volume derivative from
   * the phase. This avoids numerical differentiation issues where perturbing moles at constant
   * total volume causes the packing fraction eta to cancel.
   * </p>
   *
   * @param phase the phase
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in Pa
   * @return dF/dNi
   */
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseSAFTVRMie sp = (PhaseSAFTVRMie) phase;
    double n = phase.getNumberOfMolesInPhase();
    double v = phase.getMolarVolume();

    if (numberOfComponents == 1) {
      // Pure component: dF/dN = F/N - v * dF/dV (exact)
      return sp.getF() / n - v * sp.dFdV();
    }

    // Mixture: analytical dF/dN_i = dF_HC/dN_i + dF_DISP/dN_i + dF_ASSOC/dN_i
    // Compute on-the-fly (not from cached fields) so this works on cloned phases
    return dF_HC_SAFTdN(phase, numberOfComponents, temperature, pressure)
        + dF_DISP_SAFTdN(phase, numberOfComponents, temperature, pressure)
        + dFCPAdN(phase, numberOfComponents, temperature, pressure);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Analytical formula: dFdNdT = dFdT/n - v * dFdTdV, derived from d/dT of dFdN = F/n - v*dFdV.
   * </p>
   */
  @Override
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseSAFTVRMie sp = (PhaseSAFTVRMie) phase;
    double n = phase.getNumberOfMolesInPhase();
    double v = phase.getMolarVolume();

    if (numberOfComponents == 1) {
      return sp.dFdT() / n - v * sp.dFdTdV();
    }

    // Numerical dT derivative of dFdN
    double dT = temperature * 1.0e-5;
    double totalVolume = v * n;

    PhaseSAFTVRMie pPlus = sp.clone();
    pPlus.setTemperature(temperature + dT);
    for (int i = 0; i < numberOfComponents; i++) {
      ((ComponentSAFTVRMie) pPlus.getComponent(i)).recalcSAFTDiameter(temperature + dT);
    }
    pPlus.volInit();
    double dFdNplus = ((ComponentSAFTVRMie) pPlus.getComponent(getComponentNumber())).dFdN(pPlus,
        numberOfComponents, temperature + dT, pressure);

    PhaseSAFTVRMie pMinus = sp.clone();
    pMinus.setTemperature(temperature - dT);
    for (int i = 0; i < numberOfComponents; i++) {
      ((ComponentSAFTVRMie) pMinus.getComponent(i)).recalcSAFTDiameter(temperature - dT);
    }
    pMinus.volInit();
    double dFdNminus = ((ComponentSAFTVRMie) pMinus.getComponent(getComponentNumber())).dFdN(pMinus,
        numberOfComponents, temperature - dT, pressure);

    return (dFdNplus - dFdNminus) / (2.0 * dT);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Analytical formula: dFdNdV = -v * dFdVdV, derived from d/dV of dFdN = F/n - v*dFdV at constant
   * N (where v = V/n, so dv/dV = 1/n).
   * </p>
   */
  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseSAFTVRMie sp = (PhaseSAFTVRMie) phase;
    double n = phase.getNumberOfMolesInPhase();
    double v = phase.getMolarVolume();

    if (numberOfComponents == 1) {
      return -v * sp.dFdVdV();
    }

    // Numerical dV derivative of dFdN: perturb total volume
    double totalVolume = v * n;
    double dV = totalVolume * 1.0e-6;

    PhaseSAFTVRMie pPlus = sp.clone();
    pPlus.setMolarVolume((totalVolume + dV) / n);
    pPlus.volInit();
    double dFdNplus = ((ComponentSAFTVRMie) pPlus.getComponent(getComponentNumber())).dFdN(pPlus,
        numberOfComponents, temperature, pressure);

    PhaseSAFTVRMie pMinus = sp.clone();
    pMinus.setMolarVolume((totalVolume - dV) / n);
    pMinus.volInit();
    double dFdNminus = ((ComponentSAFTVRMie) pMinus.getComponent(getComponentNumber())).dFdN(pMinus,
        numberOfComponents, temperature, pressure);

    return (dFdNplus - dFdNminus) / (2.0 * dV);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Numerical differentiation of dFdN with respect to moles of component j. Clones the phase,
   * perturbs N_j while keeping total volume constant, and computes the central difference of the
   * analytical dFdN. The reinit helper only calls init + volInit (no Finit) to avoid infinite
   * recursion.
   * </p>
   */
  @Override
  public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double nj = phase.getComponent(j).getNumberOfMolesInPhase();
    double totalVolume = phase.getMolarVolume() * phase.getNumberOfMolesInPhase();
    double totalMoles = phase.getNumberOfMolesInPhase();
    double h = Math.max(Math.abs(nj) * 1.0e-6, 1.0e-15);
    // Ensure h is small relative to total phase moles
    h = Math.min(h, Math.max(totalMoles * 0.4, 1.0e-20));

    PhaseSAFTVRMie plus = ((PhaseSAFTVRMie) phase).clone();
    plus.getComponent(j).setNumberOfMolesInPhase(nj + h);
    double newTotalPlus = totalMoles + h;
    plus.numberOfMolesInPhase = newTotalPlus;
    plus.setMolarVolume(totalVolume / newTotalPlus);
    reinitSAFTOnPhase(plus, numberOfComponents, temperature, pressure);
    double dFdNplus = ((ComponentSAFTVRMie) plus.getComponent(getComponentNumber())).dFdN(plus,
        numberOfComponents, temperature, pressure);

    if (nj > h * 1.5) {
      // Central difference
      PhaseSAFTVRMie minus = ((PhaseSAFTVRMie) phase).clone();
      minus.getComponent(j).setNumberOfMolesInPhase(nj - h);
      double newTotalMinus = totalMoles - h;
      minus.numberOfMolesInPhase = newTotalMinus;
      minus.setMolarVolume(totalVolume / newTotalMinus);
      reinitSAFTOnPhase(minus, numberOfComponents, temperature, pressure);
      double dFdNminus = ((ComponentSAFTVRMie) minus.getComponent(getComponentNumber())).dFdN(minus,
          numberOfComponents, temperature, pressure);
      return (dFdNplus - dFdNminus) / (2.0 * h);
    } else {
      // Forward difference (when nj is too small for central)
      double dFdNcenter = ((ComponentSAFTVRMie) phase.getComponent(getComponentNumber()))
          .dFdN(phase, numberOfComponents, temperature, pressure);
      return (dFdNplus - dFdNcenter) / h;
    }
  }

  /**
   * Reinitializes SAFT quantities on a cloned phase for numerical differentiation. Updates
   * component mole fractions based on the perturbed numberOfMolesInPhase values and recalculates
   * SAFT variables via volInit. Does NOT call Component.init to avoid corrupting system-level
   * moles.
   *
   * @param phase the phase to reinitialize
   * @param numberOfComponents number of components
   * @param temperature temperature in K
   * @param pressure pressure in Pa
   */
  private void reinitSAFTOnPhase(PhaseSAFTVRMie phase, int numberOfComponents, double temperature,
      double pressure) {
    double totalMolesInPhase = phase.getNumberOfMolesInPhase();
    if (totalMolesInPhase <= 0.0) {
      totalMolesInPhase = 1.0e-50;
      phase.numberOfMolesInPhase = totalMolesInPhase;
    }
    for (int i = 0; i < numberOfComponents; i++) {
      double xi = phase.getComponent(i).getNumberOfMolesInPhase() / totalMolesInPhase;
      phase.getComponent(i).setx(xi);
    }
    phase.volInit();
  }

  // ===== Composition derivatives for dFdN =====

  /**
   * Derivative of packing fraction with respect to moles of component i.
   *
   * @param phase the phase
   * @param nc number of components
   * @param temp temperature
   * @param pres pressure
   * @return dnSAFT/dNi
   */
  public double calcdnSAFTdi(PhaseInterface phase, int nc, double temp, double pres) {
    PhaseSAFTVRMie sp = (PhaseSAFTVRMie) phase;
    double nMoles = phase.getNumberOfMolesInPhase();
    double term1 = nMoles / sp.getVolumeSAFT() * getmSAFTi() * Math.pow(getdSAFTi(), 3.0) / nMoles;
    double term2 = 1.0 / sp.getVolumeSAFT() * sp.getDSAFT();
    double term3 = -nMoles / sp.getVolumeSAFT() * sp.getDSAFT() / nMoles;
    return ThermodynamicConstantsInterface.pi / 6.0 * ThermodynamicConstantsInterface.avagadroNumber
        * (term1 + term2 + term3);
  }

  /**
   * Derivative of hard-sphere RDF with respect to moles of component i.
   *
   * @param phase the phase
   * @param nc number of components
   * @param temp temperature
   * @param pres pressure
   * @return dghs/dNi
   */
  public double calcdghsSAFTdi(PhaseInterface phase, int nc, double temp, double pres) {
    PhaseSAFTVRMie sp = (PhaseSAFTVRMie) phase;
    return sp.getDgHSSAFTdN() * getDnSAFTdi();
  }

  /**
   * Derivative of hard-sphere Helmholtz energy with respect to moles of component i.
   *
   * @param phase the phase
   * @param nc number of components
   * @param temp temperature
   * @param pres pressure
   * @return dahs/dNi
   */
  public double calcdahsSAFTdi(PhaseInterface phase, int nc, double temp, double pres) {
    PhaseSAFTVRMie sp = (PhaseSAFTVRMie) phase;
    double n = sp.getNSAFT();
    double dAdN =
        (4.0 - 6.0 * n) * Math.pow(1.0 - n, 2.0) - (4.0 * n - 3.0 * n * n) * (-2.0) * (1.0 - n);
    return dAdN / Math.pow(1.0 - n, 4.0) * getDnSAFTdi();
  }

  /**
   * Derivative of segment number with respect to moles of component i.
   *
   * @param phase the phase
   * @param nc number of components
   * @param temp temperature
   * @param pres pressure
   * @return dm/dNi
   */
  public double calcdmSAFTdi(PhaseInterface phase, int nc, double temp, double pres) {
    PhaseSAFTVRMie sp = (PhaseSAFTVRMie) phase;
    return mSAFTi / phase.getNumberOfMolesInPhase()
        - sp.getmSAFT() / phase.getNumberOfMolesInPhase();
  }

  /**
   * Derivative of dispersion volume term with respect to moles of component i.
   *
   * @param phase the phase
   * @param nc number of components
   * @param temp temperature
   * @param pres pressure
   * @return dVolTerm/dNi
   */
  public double calcdF1dispVolTermdn(PhaseInterface phase, int nc, double temp, double pres) {
    PhaseSAFTVRMie sp = (PhaseSAFTVRMie) phase;
    double nMoles = phase.getNumberOfMolesInPhase();
    return sp.getF1dispVolTerm() / nMoles
        + nMoles / sp.getVolumeSAFT() * sp.getdDSAFTdTprime(getmSAFTi(), getdSAFTi(), nMoles);
  }

  /**
   * Calculates derivative of first-order dispersion sum term w.r.t. moles.
   *
   * @param phase the phase
   * @param nc number of components
   * @param temp temperature
   * @param pres pressure
   * @return d(F1dispSumTerm)/dNi
   */
  public double calcF1dispSumTermdn(PhaseInterface phase, int nc, double temp, double pres) {
    PhaseSAFTVRMie sp = (PhaseSAFTVRMie) phase;
    double nMoles = phase.getNumberOfMolesInPhase();
    double sum = 0.0;
    for (int j = 0; j < phase.getNumberOfComponents(); j++) {
      double xj = phase.getComponent(j).getNumberOfMolesInPhase() / nMoles;
      double mj = phase.getComponent(j).getmSAFTi();
      double epsj = phase.getComponent(j).getEpsikSAFT();
      double sigj = phase.getComponent(j).getSigmaSAFTi();
      double sigij = 0.5 * (getSigmaSAFTi() + sigj);
      double epsij = Math.sqrt(getEpsikSAFT() * epsj);
      sum += xj * getmSAFTi() * mj * epsij / temp * Math.pow(sigij, 3.0);
    }
    return -2.0 / nMoles * sp.getF1dispSumTerm() + 2.0 * sum / nMoles;
  }

  /**
   * Calculates derivative of first-order dispersion integral (I1) w.r.t. moles.
   *
   * @param phase the phase
   * @param nc number of components
   * @param temp temperature
   * @param pres pressure
   * @return dI1/dNi
   */
  public double calcF1dispI1dn(PhaseInterface phase, int nc, double temp, double pres) {
    PhaseSAFTVRMie sp = (PhaseSAFTVRMie) phase;
    return sp.calcF1dispI1dN() * getDnSAFTdi() + sp.calcF1dispI1dm() * getDmSAFTdi();
  }

  /**
   * Calculates derivative of second-order dispersion sum term w.r.t. moles.
   *
   * @param phase the phase
   * @param nc number of components
   * @param temp temperature
   * @param pres pressure
   * @return d(F2dispSumTerm)/dNi
   */
  public double calcF2dispSumTermdn(PhaseInterface phase, int nc, double temp, double pres) {
    PhaseSAFTVRMie sp = (PhaseSAFTVRMie) phase;
    double nMoles = phase.getNumberOfMolesInPhase();
    double sum = 0.0;
    for (int j = 0; j < phase.getNumberOfComponents(); j++) {
      double xj = phase.getComponent(j).getNumberOfMolesInPhase() / nMoles;
      double mj = phase.getComponent(j).getmSAFTi();
      double epsj = phase.getComponent(j).getEpsikSAFT();
      double sigj = phase.getComponent(j).getSigmaSAFTi();
      double sigij = 0.5 * (getSigmaSAFTi() + sigj);
      double epsij = Math.sqrt(getEpsikSAFT() * epsj);
      sum += xj * getmSAFTi() * mj * Math.pow(epsij / temp, 2.0) * Math.pow(sigij, 3.0);
    }
    return -2.0 / nMoles * sp.getF2dispSumTerm() + 2.0 * sum / nMoles;
  }

  /**
   * Calculates derivative of second-order compression factor correction w.r.t. moles.
   *
   * @param phase the phase
   * @param nc number of components
   * @param temp temperature
   * @param pres pressure
   * @return d(F2dispZHC)/dNi
   */
  public double calcF2dispZHCdn(PhaseInterface phase, int nc, double temp, double pres) {
    PhaseSAFTVRMie sp = (PhaseSAFTVRMie) phase;
    double n = sp.getNSAFT();
    double m = sp.getmSAFT();

    double g1 = (8.0 + 20.0 * n - 4.0 * n * n) / Math.pow(1.0 - n, 5.0);
    double g2 = (20.0 - 54.0 * n + 36.0 * n * n) / Math.pow((1.0 - n) * (2.0 - n), 2.0);
    double g3 = (20.0 * n - 27.0 * n * n + 12.0 * Math.pow(n, 3.0) - 2.0 * Math.pow(n, 4.0))
        * (-6.0 + 4.0 * n) / Math.pow((1.0 - n) * (2.0 - n), 3.0);

    double F = m * g1 + (1.0 - m) * (g2 + g3);
    double Fp = m * (60.0 + 72.0 * n - 12.0 * n * n) / Math.pow(1.0 - n, 6.0)
        + (1.0 - m) * (2.0 * (-36.0 * Math.pow(n, 3.0) + 81.0 * n * n - 49.0 * n + 6.0)
            / (Math.pow(1.0 - n, 4.0) * Math.pow(2.0 - n, 4.0))
            + 2.0
                * (4.0 * Math.pow(n, 6.0) - 36.0 * Math.pow(n, 5.0) + 140.0 * Math.pow(n, 4.0)
                    - 244.0 * Math.pow(n, 3.0) + 123.0 * n * n + 124.0 * n - 120.0)
                / (Math.pow(1.0 - n, 4.0) * Math.pow(2.0 - n, 4.0)));

    double ZHC = sp.getF2dispZHC();
    return -Math.pow(ZHC, 2.0) * (F * dnSAFTdi + (g1 - g2 - g3) * dmSAFTdi);
  }

  // ===== dFdN contributions =====

  /**
   * Hard-chain contribution to dF/dNi. Computes all intermediate derivatives from the phase to work
   * correctly on cloned phases (no reliance on cached component fields).
   *
   * @param phase the phase
   * @param nc number of components
   * @param temp temperature
   * @param pres pressure
   * @return dF_HC/dNi
   */
  public double dF_HC_SAFTdN(PhaseInterface phase, int nc, double temp, double pres) {
    PhaseSAFTVRMie sp = (PhaseSAFTVRMie) phase;
    double nMoles = phase.getNumberOfMolesInPhase();
    double mi = getmSAFTi();
    double di = getdSAFTi();
    double aHS = sp.getAHSSAFT();
    double lnGHS = Math.log(sp.getGhsSAFT());
    double mmin1 = sp.getMmin1SAFT();

    // Compute deta/dn_i = pi/6 * NA * mi * di^3 / V
    double volSAFT = sp.getVolumeSAFT();
    double dndi = ThermodynamicConstantsInterface.pi / 6.0
        * ThermodynamicConstantsInterface.avagadroNumber * mi * di * di * di / volSAFT;

    // dahsSAFTdi = (daHS/deta) * dndi
    double dahsdi = sp.getDaHSSAFTdN() * dndi;

    // dlogghsSAFTdi = (1/gHS) * (dgHS/deta) * dndi
    double dlogghsdi = sp.getDgHSSAFTdN() / sp.getGhsSAFT() * dndi;

    return mi * aHS + nMoles * sp.getmSAFT() * dahsdi - (mi - 1.0) * lnGHS
        - nMoles * mmin1 * dlogghsdi;
  }

  /**
   * Dispersion contribution to dF/dNi. Uses the pair-summed quantities from volInit. Computes
   * deta/dn_i inline to work on cloned phases.
   *
   * @param phase the phase
   * @param nc number of components
   * @param temp temperature
   * @param pres pressure
   * @return dF_DISP/dNi
   */
  public double dF_DISP_SAFTdN(PhaseInterface phase, int nc, double temp, double pres) {
    PhaseSAFTVRMie sp = (PhaseSAFTVRMie) phase;
    double nMoles = phase.getNumberOfMolesInPhase();
    double mi = getmSAFTi();
    double di = getdSAFTi();
    double aDisp = sp.getA1Disp() + sp.getA2Disp() + sp.getA3Disp();
    double aDispI = sp.getADispPerComp(getComponentNumber());
    double daDispDeta = sp.getDa1DispDeta() + sp.getDa2DispDeta() + sp.getDa3DispDeta();

    // Compute deta/dn_i = pi/6 * NA * mi * di^3 / V
    double volSAFT = sp.getVolumeSAFT();
    double dndi = ThermodynamicConstantsInterface.pi / 6.0
        * ThermodynamicConstantsInterface.avagadroNumber * mi * di * di * di / volSAFT;

    return mi * (2.0 * aDispI - aDisp) + nMoles * sp.getmSAFT() * daDispDeta * dndi;
  }

  // ===== Getters =====

  /**
   * Gets the temperature-dependent effective segment diameter.
   *
   * @return effective diameter in m
   */
  public double getdSAFTi() {
    return dSAFTi;
  }

  /**
   * Gets the packing fraction derivative w.r.t. moles.
   *
   * @return dnSAFT/dNi
   */
  public double getDnSAFTdi() {
    return dnSAFTdi;
  }

  /**
   * Gets the mean segment number derivative w.r.t. moles.
   *
   * @return dm/dNi
   */
  public double getDmSAFTdi() {
    return dmSAFTdi;
  }

  /**
   * Gets the temperature derivative of the BH effective diameter for this component. Computed by
   * central finite difference of the Gauss-Legendre BH quadrature.
   *
   * @return dd_i/dT in m/K
   */
  public double getDdSAFTidT() {
    double sigma = getSigmaSAFTi();
    double epsk = getEpsikSAFT();
    double lr = getLambdaRSAFTVRMie();
    double la = getLambdaASAFTVRMie();
    double temp = getTemperature();
    double dT = temp * 1.0e-5;
    double dPlus = calcEffectiveDiameter(sigma, epsk, temp + dT, lr, la);
    double dMinus = calcEffectiveDiameter(sigma, epsk, temp - dT, lr, la);
    return (dPlus - dMinus) / (2.0 * dT);
  }

  /**
   * Gets the temperature stored for this component (for derivative calculations).
   *
   * @return temperature in K
   */
  private double getTemperature() {
    return componentTemperature;
  }
}
