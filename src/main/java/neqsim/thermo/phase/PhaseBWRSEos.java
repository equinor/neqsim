package neqsim.thermo.phase;

import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentBWRS;

/**
 * <p>
 * PhaseBWRSEos class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseBWRSEos extends PhaseSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseBWRSEos.class);

  /**
   * Unit conversion factor for MBWR-32 parameters. The database coefficients are calibrated in
   * MPa-based units (density in mol/L, pressure in MPa), using R_MPa = 0.008314 L·MPa/(mol·K). The
   * framework uses R_SI = 8.3144621 J/(mol·K). Since getFpol() divides by R_SI·T, the resulting
   * Helmholtz function is 1000× too small (R_SI / R_MPa = 1000). This factor compensates.
   */
  private static final double MBWR_UNIT_FACTOR = 1e3;

  int OP = 9;
  int OE = 6;

  /** Mole-fraction-weighted polynomial MBWR coefficients for the mixture. */
  private double[] mixBP = new double[9];
  /** Mole-fraction-weighted exponential MBWR coefficients for the mixture. */
  private double[] mixBE = new double[6];
  /** Mole-fraction-weighted polynomial temperature derivatives for the mixture. */
  private double[] mixBPdT = new double[9];
  /** Mole-fraction-weighted exponential temperature derivatives for the mixture. */
  private double[] mixBEdT = new double[6];
  /** Mole-fraction-weighted polynomial second temperature derivatives for the mixture. */
  private double[] mixBPdTdT = new double[9];
  /** Mole-fraction-weighted exponential second temperature derivatives for the mixture. */
  private double[] mixBEdTdT = new double[6];
  /** Mixed gamma parameter = 1/rhoc_mix^2 for one-fluid mixing. */
  private double mixGamma;

  /**
   * <p>
   * Constructor for PhaseBWRSEos.
   * </p>
   */
  public PhaseBWRSEos() {}

  /** {@inheritDoc} */
  @Override
  public PhaseBWRSEos clone() {
    PhaseBWRSEos clonedPhase = null;
    try {
      clonedPhase = (PhaseBWRSEos) super.clone();
      clonedPhase.mixBP = mixBP.clone();
      clonedPhase.mixBE = mixBE.clone();
      clonedPhase.mixBPdT = mixBPdT.clone();
      clonedPhase.mixBEdT = mixBEdT.clone();
      clonedPhase.mixBPdTdT = mixBPdTdT.clone();
      clonedPhase.mixBEdTdT = mixBEdTdT.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /**
   * Computes mole-fraction-weighted MBWR parameters for the one-fluid mixture. Linear mixing:
   * mixBP[i] = sum_j x_j * BP_j[i], similarly for BE, BPdT, BEdT. For gamma (critical density),
   * rhoc_mix = sum_j x_j * rhoc_j, then mixGamma = 1/rhoc_mix^2.
   */
  private void computeMixedParameters() {
    Arrays.fill(mixBP, 0.0);
    Arrays.fill(mixBE, 0.0);
    Arrays.fill(mixBPdT, 0.0);
    Arrays.fill(mixBEdT, 0.0);
    Arrays.fill(mixBPdTdT, 0.0);
    Arrays.fill(mixBEdTdT, 0.0);
    double rhocMix = 0.0;
    for (int j = 0; j < numberOfComponents; j++) {
      double xj = componentArray[j].getNumberOfMolesInPhase() / numberOfMolesInPhase;
      ComponentBWRS comp = (ComponentBWRS) componentArray[j];
      double rhocJ = 1.0 / Math.sqrt(comp.getGammaBWRS());
      rhocMix += xj * rhocJ;
      for (int k = 0; k < OP; k++) {
        mixBP[k] += xj * comp.getBP(k);
        mixBPdT[k] += xj * comp.getBPdT(k);
        mixBPdTdT[k] += xj * comp.getBPdTdT(k);
      }
      for (int k = 0; k < OE; k++) {
        mixBE[k] += xj * comp.getBE(k);
        mixBEdT[k] += xj * comp.getBEdT(k);
        mixBEdTdT[k] += xj * comp.getBEdTdT(k);
      }
    }
    mixGamma = 1.0 / (rhocMix * rhocMix);
  }

  /**
   * Lightweight recomputation for numerical derivatives. Updates mole fractions and mixed BWRS
   * parameters without triggering a full init/Finit cycle.
   */
  private void recomputeLight() {
    for (int i = 0; i < numberOfComponents; i++) {
      componentArray[i].setx(componentArray[i].getNumberOfMolesInPhase() / numberOfMolesInPhase);
    }
    computeMixedParameters();
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentBWRS(name, moles, molesInPhase, compNumber);
    ((ComponentBWRS) componentArray[compNumber]).setRefPhaseBWRS(this);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    double oldMolDens = 0;
    if (initType == 0) {
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
      computeMixedParameters();
      super.init(totalNumberOfMoles, numberOfComponents, 3, pt, beta);
      computeMixedParameters();
      return;
    }
    do {
      oldMolDens = getMolarDensity();
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
      computeMixedParameters();
    } while (Math.abs((getMolarDensity() - oldMolDens) / oldMolDens) > 1e-10);
    getF();
    // calcPVT();
  }

  /**
   * <p>
   * getMolarDensity.
   * </p>
   *
   * @return a double
   */
  public double getMolarDensity() {
    return getNumberOfMolesInPhase() / getTotalVolume() * 1.0e2;
  }

  /**
   * <p>
   * getdRhodV.
   * </p>
   *
   * @return a double
   */
  public double getdRhodV() {
    return -getMolarDensity() / (getTotalVolume() * 1e-5);
  }

  /**
   * <p>
   * getdRhodVdV.
   * </p>
   *
   * @return a double
   */
  public double getdRhodVdV() {
    return 2.0 * getMolarDensity() / (Math.pow(getTotalVolume() * 1e-5, 2));
  }

  /**
   * <p>
   * getdRhodVdVdV.
   * </p>
   *
   * @return a double
   */
  public double getdRhodVdVdV() {
    return -6.0 * getMolarDensity() / (Math.pow(getTotalVolume() * 1e-5, 3));
  }

  /**
   * <p>
   * getGammadRho.
   * </p>
   *
   * @return a double
   */
  public double getGammadRho() {
    return 0.0; // -2.0/Math.pow(((ComponentBWRS)componentArray[0]).getRhoc(),3.0);
                // //-1.0/(rhoc*rhoc);
  }

  /**
   * <p>
   * getFpol.
   * </p>
   *
   * @return a double
   */
  public double getFpol() {
    double temp = 0.0;
    for (int i = 1; i < OP; i++) {
      temp += mixBP[i] / (i + 0.0) * Math.pow(getMolarDensity(), i);
    }
    return numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * <p>
   * getFpoldV.
   * </p>
   *
   * @return a double
   */
  public double getFpoldV() {
    double temp = 0.0;
    for (int i = 1; i < OP; i++) {
      temp += (i) * mixBP[i] / (i - 0.0) * Math.pow(getMolarDensity(), i - 1.0) * getdRhodV();
    }
    return numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * Derivative of the polynomial F-term with respect to molar density.
   *
   * @return dF<sub>pol</sub>/dρ
   */
  public double getFpoldRho() {
    double temp = 0.0;
    for (int i = 1; i < OP; i++) {
      temp += mixBP[i] * Math.pow(getMolarDensity(), i - 1.0);
    }
    return numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * <p>
   * getFpoldVdV.
   * </p>
   *
   * @return a double
   */
  public double getFpoldVdV() {
    double temp = 0.0;
    double temp2 = 0.0;
    for (int i = 1; i < OP; i++) {
      temp += (i - 1) * (i) * mixBP[i] / (i - 0.0) * Math.pow(getMolarDensity(), i - 2.0);
      temp2 += (i) * mixBP[i] / (i - 0.0) * Math.pow(getMolarDensity(), i - 1.0);
    }
    return numberOfMolesInPhase / (R * temperature) * temp * Math.pow(getdRhodV(), 2)
        + numberOfMolesInPhase / (R * temperature) * temp2 * getdRhodVdV();
  }

  /**
   * <p>
   * getFpoldVdVdV.
   * </p>
   *
   * @return a double
   */
  public double getFpoldVdVdV() {
    double temp = 0.0;
    double temp2 = 0.0;
    double temp3 = 0.0;
    for (int i = 1; i < OP; i++) {
      temp += (i - 2) * (i - 1) * (i) * mixBP[i] / (i - 0.0) * Math.pow(getMolarDensity(), i - 3);
      temp2 += (i - 1) * (i) * mixBP[i] / (i - 0.0) * Math.pow(getMolarDensity(), i - 2);
      temp3 += (i) * mixBP[i] / (i - 0.0) * Math.pow(getMolarDensity(), i - 1);
    }
    return numberOfMolesInPhase / (R * temperature) * temp * Math.pow(getdRhodV(), 3)
        + 2 * numberOfMolesInPhase / (R * temperature) * temp2 * Math.pow(getdRhodV(), 1)
            * getdRhodVdV()
        + numberOfMolesInPhase / (R * temperature) * temp2 * Math.pow(getdRhodV(), 1)
            * getdRhodVdV()
        + numberOfMolesInPhase / (R * temperature) * temp3 * getdRhodVdVdV();
  }

  /**
   * <p>
   * getFpoldT.
   * </p>
   *
   * @return a double
   */
  public double getFpoldT() {
    double temp = 0.0;
    double temp2 = 0.0;
    for (int i = 1; i < OP; i++) {
      temp += mixBP[i] / (i + 0.0) * Math.pow(getMolarDensity(), i);
      temp2 += mixBPdT[i] / (i + 0.0) * Math.pow(getMolarDensity(), i);
    }
    return -numberOfMolesInPhase / (R * temperature * temperature) * temp
        + numberOfMolesInPhase / (R * temperature) * temp2;
  }

  /**
   * Cross derivative of the polynomial F-term with respect to temperature and molar density.
   *
   * @return d<sup>2</sup>F<sub>pol</sub>/dT dρ
   */
  private double getFpoldTdRho() {
    double term1 = 0.0;
    double term2 = 0.0;
    double rho = getMolarDensity();
    for (int i = 1; i < OP; i++) {
      term1 += mixBP[i] * Math.pow(rho, i - 1.0);
      term2 += mixBPdT[i] * Math.pow(rho, i - 1.0);
    }
    return numberOfMolesInPhase / R * (-term1 / Math.pow(temperature, 2.0) + term2 / temperature);
  }

  /**
   * Cross derivative of the polynomial F-term with respect to temperature and molar volume.
   *
   * @return d<sup>2</sup>F<sub>pol</sub>/dT dV
   */
  private double getFpoldTdV() {
    return getFpoldTdRho() * getdRhodV();
  }

  /**
   * Second derivative of the polynomial F-term with respect to temperature.
   *
   * @return d<sup>2</sup>F<sub>pol</sub>/dT<sup>2</sup>
   */
  private double getFpoldTdT() {
    double term1 = 0.0;
    double term2 = 0.0;
    double term3 = 0.0;
    double rho = getMolarDensity();
    for (int i = 1; i < OP; i++) {
      double rhoPow = Math.pow(rho, i);
      term1 += mixBP[i] / (i + 0.0) * rhoPow;
      term2 += mixBPdT[i] / (i + 0.0) * rhoPow;
      term3 += mixBPdTdT[i] / (i + 0.0) * rhoPow;
    }
    return numberOfMolesInPhase / R * (2.0 * term1 / Math.pow(temperature, 3.0)
        - 2.0 * term2 / Math.pow(temperature, 2.0) + term3 / temperature);
  }

  /**
   * Total derivative of F with respect to molar density.
   *
   * @return dF/dρ
   */
  public double getFdRho() {
    return getFpoldRho() + getFexpdRho();
  }

  /**
   * <p>
   * getEL.
   * </p>
   *
   * @return a double
   */
  public double getEL() {
    return Math.exp(-mixGamma * Math.pow(getMolarDensity(), 2.0));
  }

  /**
   * <p>
   * getELdRho.
   * </p>
   *
   * @return a double
   */
  public double getELdRho() {
    return -2.0 * getMolarDensity() * mixGamma
        * Math.exp(-mixGamma * Math.pow(getMolarDensity(), 2.0));
  }

  /**
   * Second derivative of the exponential EL term with respect to molar density.
   *
   * @return d<sup>2</sup>EL/dρ<sup>2</sup>
   */
  public double getELdRhodedRho() {
    double gamma = mixGamma;
    double rho = getMolarDensity();
    return (-2.0 * gamma + 4.0 * gamma * gamma * rho * rho) * getEL();
  }

  /**
   * Third derivative of the exponential EL term with respect to molar density.
   *
   * @return d<sup>3</sup>EL/dρ<sup>3</sup>
   */
  public double getELdRhodedRhodedRho() {
    double gamma = mixGamma;
    double rho = getMolarDensity();
    return (12.0 * gamma * gamma * rho - 8.0 * Math.pow(gamma, 3.0) * Math.pow(rho, 3.0)) * getEL();
  }

  /**
   * <p>
   * getFexp.
   * </p>
   *
   * @return a double
   */
  public double getFexp() {
    double oldTemp = 0.0;
    double temp = 0.0;
    oldTemp = -mixBE[0] / (2.0 * mixGamma) * (getEL() - 1.0);
    temp += oldTemp;
    for (int i = 1; i < OE; i++) {
      oldTemp = -mixBE[i] / (2.0 * mixGamma)
          * (getEL() * Math.pow(getMolarDensity(), 2.0 * i) - (2.0 * i) / mixBE[i - 1] * oldTemp);
      temp += oldTemp;
    }
    return numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * First derivative of Fexp with respect to molar density.
   *
   * @return dFexp/dρ
   */
  private double getFexpdRho() {
    double gamma = mixGamma;
    double rho = getMolarDensity();
    double oldTemp = -mixBE[0] / (2.0 * gamma) * getELdRho();
    double temp = oldTemp;
    for (int i = 1; i < OE; i++) {
      double prev = oldTemp;
      oldTemp = -mixBE[i] / (2.0 * gamma) * (getELdRho() * Math.pow(rho, 2 * i)
          + getEL() * (2.0 * i) * Math.pow(rho, 2 * i - 1) - (2.0 * i) / mixBE[i - 1] * prev);
      temp += oldTemp;
    }
    return numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * Second derivative of Fexp with respect to molar density.
   *
   * @return d<sup>2</sup>Fexp/dρ<sup>2</sup>
   */
  private double getFexpdRhodRho() {
    double gamma = mixGamma;
    double rho = getMolarDensity();
    double oldTemp = -mixBE[0] / (2.0 * gamma) * getELdRhodedRho();
    double temp = oldTemp;
    for (int i = 1; i < OE; i++) {
      double prev = oldTemp;
      oldTemp = -mixBE[i] / (2.0 * gamma)
          * (getELdRhodedRho() * Math.pow(rho, 2 * i)
              + 2.0 * getELdRho() * (2.0 * i) * Math.pow(rho, 2 * i - 1)
              + getEL() * (2.0 * i) * (2.0 * i - 1) * Math.pow(rho, 2 * i - 2)
              - (2.0 * i) / mixBE[i - 1] * prev);
      temp += oldTemp;
    }
    return numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * Third derivative of Fexp with respect to molar density.
   *
   * @return d<sup>3</sup>Fexp/dρ<sup>3</sup>
   */
  private double getFexpdRhodRhodRho() {
    double gamma = mixGamma;
    double rho = getMolarDensity();
    double oldTemp = -mixBE[0] / (2.0 * gamma) * getELdRhodedRhodedRho();
    double temp = oldTemp;
    for (int i = 1; i < OE; i++) {
      double prev = oldTemp;
      oldTemp = -mixBE[i] / (2.0 * gamma)
          * (getELdRhodedRhodedRho() * Math.pow(rho, 2 * i)
              + 3.0 * getELdRhodedRho() * (2.0 * i) * Math.pow(rho, 2 * i - 1)
              + 3.0 * getELdRho() * (2.0 * i) * (2.0 * i - 1) * Math.pow(rho, 2 * i - 2)
              + getEL() * (2.0 * i) * (2.0 * i - 1) * (2.0 * i - 2) * Math.pow(rho, 2 * i - 3)
              - (2.0 * i) / mixBE[i - 1] * prev);
      temp += oldTemp;
    }
    return numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * First derivative of Fexp with respect to molar volume.
   *
   * @return dFexp/dV
   */
  public double getFexpdV() {
    return getFexpdRho() * getdRhodV();
  }

  /**
   * Second derivative of Fexp with respect to molar volume.
   *
   * @return d<sup>2</sup>Fexp/dV<sup>2</sup>
   */
  public double getFexpdVdV() {
    return getFexpdRhodRho() * Math.pow(getdRhodV(), 2.0) + getFexpdRho() * getdRhodVdV();
  }

  /**
   * Third derivative of Fexp with respect to molar volume.
   *
   * @return d<sup>3</sup>Fexp/dV<sup>3</sup>
   */
  public double getFexpdVdVdV() {
    return getFexpdRhodRhodRho() * Math.pow(getdRhodV(), 3.0)
        + 3.0 * getFexpdRhodRho() * getdRhodV() * getdRhodVdV() + getFexpdRho() * getdRhodVdVdV();
  }

  /**
   * Cross derivative of Fexp with respect to temperature and molar density.
   *
   * @return d<sup>2</sup>Fexp/dT dρ
   */
  private double getFexpdTdRho() {
    double gamma = mixGamma;
    double rho = getMolarDensity();
    double oldTemp = -mixBE[0] / (2.0 * gamma) * getELdRho();
    double doldTemp = -mixBEdT[0] / (2.0 * gamma) * getELdRho();
    double temp = oldTemp;
    double dtemp = doldTemp;
    for (int i = 1; i < OE; i++) {
      double prev = oldTemp;
      double dprev = doldTemp;
      oldTemp = -mixBE[i] / (2.0 * gamma) * (getELdRho() * Math.pow(rho, 2 * i)
          + getEL() * (2.0 * i) * Math.pow(rho, 2 * i - 1) - (2.0 * i) / mixBE[i - 1] * prev);
      doldTemp = -mixBEdT[i] / (2.0 * gamma)
          * (getELdRho() * Math.pow(rho, 2 * i) + getEL() * (2.0 * i) * Math.pow(rho, 2 * i - 1)
              - (2.0 * i) / mixBE[i - 1] * prev)
          - mixBE[i] / (2.0 * gamma)
              * (-(2.0 * i) / Math.pow(mixBE[i - 1], 2.0) * mixBEdT[i - 1] * prev
                  - (2.0 * i) / mixBE[i - 1] * dprev);
      temp += oldTemp;
      dtemp += doldTemp;
    }
    return numberOfMolesInPhase / R * (-temp / Math.pow(temperature, 2.0) + dtemp / temperature);
  }

  /**
   * Cross derivative of Fexp with respect to temperature and molar volume.
   *
   * @return d<sup>2</sup>Fexp/dT dV
   */
  private double getFexpdTdV() {
    return getFexpdTdRho() * getdRhodV();
  }

  /**
   * <p>
   * getFexpdT.
   * </p>
   *
   * @return a double
   */
  public double getFexpdT() {
    double oldTemp = 0.0;
    double temp = 0.0;
    double oldTemp2 = 0;
    oldTemp = -mixBEdT[0] / (2.0 * mixGamma) * (getEL() - 1.0);
    oldTemp2 = -mixBE[0] / (2.0 * mixGamma) * (getEL() - 1.0);
    temp += oldTemp;
    for (int i = 1; i < OE; i++) {
      oldTemp = -mixBEdT[i] / (2.0 * mixGamma)
          * (getEL() * Math.pow(getMolarDensity(), 2.0 * i) - (2.0 * i) / mixBE[i - 1] * oldTemp2)

          +

          -mixBE[i] / (2.0 * mixGamma) * ((2.0 * i) / Math.pow(mixBE[i - 1], 2.0) * oldTemp2)
              * mixBEdT[i - 1]

          +

          mixBE[i] / (2.0 * mixGamma) * ((2.0 * i) / mixBE[i - 1] * oldTemp);

      oldTemp2 = -mixBE[i] / (2.0 * mixGamma)
          * (getEL() * Math.pow(getMolarDensity(), 2.0 * i) - (2.0 * i) / mixBE[i - 1] * oldTemp2);

      temp += oldTemp;
    }
    return -getFexp() / temperature + numberOfMolesInPhase / (R * temperature) * temp;
  }

  /**
   * Second derivative of Fexp with respect to temperature.
   *
   * @return d<sup>2</sup>Fexp/dT<sup>2</sup>
   */
  private double getFexpdTdT() {
    double gamma = mixGamma;
    double rho = getMolarDensity();
    double elMinus1 = getEL() - 1.0;

    double oldTemp = -mixBEdT[0] / (2.0 * gamma) * elMinus1;
    double oldTemp2 = -mixBE[0] / (2.0 * gamma) * elMinus1;
    double doldTemp = -mixBEdTdT[0] / (2.0 * gamma) * elMinus1;
    double doldTemp2 = -mixBEdT[0] / (2.0 * gamma) * elMinus1;
    double temp = oldTemp;
    double dtemp = doldTemp;

    for (int i = 1; i < OE; i++) {
      double term1 = -mixBEdT[i] / (2.0 * gamma)
          * (getEL() * Math.pow(rho, 2.0 * i) - (2.0 * i) / mixBE[i - 1] * oldTemp2);
      double term2 = -mixBE[i] / (2.0 * gamma)
          * ((2.0 * i) / Math.pow(mixBE[i - 1], 2.0) * oldTemp2) * mixBEdT[i - 1];
      double term3 = mixBE[i] / (2.0 * gamma) * ((2.0 * i) / mixBE[i - 1] * oldTemp);
      oldTemp = term1 + term2 + term3;

      double dterm1 = -mixBEdTdT[i] / (2.0 * gamma)
          * (getEL() * Math.pow(rho, 2.0 * i) - (2.0 * i) / mixBE[i - 1] * oldTemp2)
          - mixBEdT[i] / (2.0 * gamma) * (-(2.0 * i) / mixBE[i - 1] * doldTemp2
              + (2.0 * i) / Math.pow(mixBE[i - 1], 2.0) * mixBEdT[i - 1] * oldTemp2);
      double dterm2 = -(mixBEdT[i] / (2.0 * gamma))
          * ((2.0 * i) / Math.pow(mixBE[i - 1], 2.0) * oldTemp2 * mixBEdT[i - 1])
          - (mixBE[i] / (2.0 * gamma)) * ((2.0 * i) * (-2.0) / Math.pow(mixBE[i - 1], 3.0)
              * mixBEdT[i - 1] * oldTemp2 * mixBEdT[i - 1]
              + (2.0 * i) / Math.pow(mixBE[i - 1], 2.0) * doldTemp2 * mixBEdT[i - 1]
              + (2.0 * i) / Math.pow(mixBE[i - 1], 2.0) * oldTemp2 * mixBEdTdT[i - 1]);
      double dterm3 = (mixBEdT[i] / (2.0 * gamma)) * ((2.0 * i) / mixBE[i - 1] * oldTemp)
          + (mixBE[i] / (2.0 * gamma)) * ((2.0 * i) / mixBE[i - 1] * doldTemp
              - (2.0 * i) / Math.pow(mixBE[i - 1], 2.0) * mixBEdT[i - 1] * oldTemp);
      doldTemp = dterm1 + dterm2 + dterm3;

      double termBase = -mixBE[i] / (2.0 * gamma)
          * (getEL() * Math.pow(rho, 2.0 * i) - (2.0 * i) / mixBE[i - 1] * oldTemp2);
      double dtermBase = -(mixBEdT[i] / (2.0 * gamma))
          * (getEL() * Math.pow(rho, 2.0 * i) - (2.0 * i) / mixBE[i - 1] * oldTemp2)
          - (mixBE[i] / (2.0 * gamma)) * (-(2.0 * i) / mixBE[i - 1] * doldTemp2
              + (2.0 * i) / Math.pow(mixBE[i - 1], 2.0) * mixBEdT[i - 1] * oldTemp2);
      oldTemp2 = termBase;
      doldTemp2 = dtermBase;

      temp += oldTemp;
      dtemp += doldTemp;
    }

    double Fexp = getFexp();
    double FexpdT = -Fexp / temperature + numberOfMolesInPhase / (R * temperature) * temp;

    return -FexpdT / temperature + Fexp / Math.pow(temperature, 2.0)
        + numberOfMolesInPhase / R * (-temp / Math.pow(temperature, 2.0) + dtemp / temperature);
  }

  /**
   * <p>
   * calcPressure2.
   * </p>
   *
   * @return a double
   */
  public double calcPressure2() {
    // System.out.println("here............");
    double temp = 0.0;
    logger.info("molar density " + getMolarDensity());
    for (int i = 0; i < OP; i++) {
      temp += mixBP[i] * Math.pow(getMolarDensity(), 1.0 + i);
    }
    for (int i = 0; i < OE; i++) {
      temp += getEL() * mixBE[i] * Math.pow(getMolarDensity(), 3.0 + 2.0 * i);
    }
    calcPVT();
    return temp / 100.0;
  }

  /**
   * <p>
   * calcPVT.
   * </p>
   */
  public void calcPVT() {
    double[] moldens = new double[300];
    double[] pres = new double[300];
    for (int j = 0; j < 300; j++) {
      moldens[j] = 30 - j * 0.1;
      double temp = 0.0;
      for (int i = 0; i < OP; i++) {
        temp += mixBP[i] * Math.pow(moldens[j], 1.0 + i);
      }
      for (int i = 0; i < OE; i++) {
        temp += Math.exp(-mixGamma * Math.pow(moldens[j], 2.0)) * mixBE[i]
            * Math.pow(moldens[j], 3.0 + 2.0 * i);
      }
      pres[j] = temp / 100.0;
      logger.info("moldens " + moldens[j] * 16.01 + "  pres " + pres[j]);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getF() {
    // System.out.println("F " + getFpol()*1e3+ " "+ getFexp()*1e3 + " super " +
    // super.getF() + " pt " +getType());
    return (getFpol() + getFexp()) * MBWR_UNIT_FACTOR;
  }

  /**
   * Numerical dF/dn_i at constant T, V. Perturbs component moles, adjusts molar volume to keep
   * total volume constant, recomputes mix parameters, and evaluates F.
   *
   * @param compNumb component index to perturb
   * @return numerical dF/dn_i
   */
  public double getdFdN(int compNumb) {
    double dn = numberOfMolesInPhase / 10000.0;
    double origN = numberOfMolesInPhase;
    double origV = molarVolume;
    double totalVol = origN * origV;

    getComponent(compNumb).addMoles(dn);
    numberOfMolesInPhase = origN + dn;
    molarVolume = totalVol / numberOfMolesInPhase;
    recomputeLight();
    double fold = getF();

    getComponent(compNumb).addMoles(-2.0 * dn);
    numberOfMolesInPhase = origN - dn;
    molarVolume = totalVol / numberOfMolesInPhase;
    recomputeLight();
    double fnew = getF();

    getComponent(compNumb).addMoles(dn);
    numberOfMolesInPhase = origN;
    molarVolume = origV;
    recomputeLight();
    return (fold - fnew) / (2.0 * dn);
  }

  /**
   * Numerical d2F/(dn_i dV) at constant T, n.
   *
   * @param compNumb component index
   * @return numerical d2F/(dn_i dV)
   */
  public double getdFdNdV(int compNumb) {
    double dV = molarVolume / 10000.0;
    double origV = molarVolume;

    molarVolume = origV + dV;
    double fPlus = getdFdN(compNumb);

    molarVolume = origV - dV;
    double fMinus = getdFdN(compNumb);

    molarVolume = origV;
    recomputeLight();
    return (fPlus - fMinus) / (2.0 * dV);
  }

  /**
   * Numerical d2F/(dn_i dT) at constant V, n.
   *
   * @param compNumb component index
   * @return numerical d2F/(dn_i dT)
   */
  public double getdFdNdT(int compNumb) {
    double dT = temperature / 10000.0;
    double origT = temperature;

    temperature = origT + dT;
    for (int j = 0; j < numberOfComponents; j++) {
      componentArray[j].init(temperature, pressure, numberOfMolesInPhase, 1.0, 0);
    }
    recomputeLight();
    double fPlus = getdFdN(compNumb);

    temperature = origT - dT;
    for (int j = 0; j < numberOfComponents; j++) {
      componentArray[j].init(temperature, pressure, numberOfMolesInPhase, 1.0, 0);
    }
    recomputeLight();
    double fMinus = getdFdN(compNumb);

    temperature = origT;
    for (int j = 0; j < numberOfComponents; j++) {
      componentArray[j].init(temperature, pressure, numberOfMolesInPhase, 1.0, 0);
    }
    recomputeLight();
    return (fPlus - fMinus) / (2.0 * dT);
  }

  /**
   * Numerical d2F/(dn_i dn_j) at constant T, V.
   *
   * @param compI component i index
   * @param compJ component j index
   * @return numerical d2F/(dn_i dn_j)
   */
  public double getdFdNdN(int compI, int compJ) {
    double dn = numberOfMolesInPhase / 10000.0;
    double origN = numberOfMolesInPhase;
    double origV = molarVolume;
    double totalVol = origN * origV;

    getComponent(compJ).addMoles(dn);
    numberOfMolesInPhase = origN + dn;
    molarVolume = totalVol / numberOfMolesInPhase;
    recomputeLight();
    double fPlus = getdFdN(compI);

    getComponent(compJ).addMoles(-2.0 * dn);
    numberOfMolesInPhase = origN - dn;
    molarVolume = totalVol / numberOfMolesInPhase;
    recomputeLight();
    double fMinus = getdFdN(compI);

    getComponent(compJ).addMoles(dn);
    numberOfMolesInPhase = origN;
    molarVolume = origV;
    recomputeLight();
    return (fPlus - fMinus) / (2.0 * dn);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdT() {
    // double dv = temperature/1000.0;
    // temperature = temperature + dv;
    // init(numberOfMolesInPhase, numberOfComponents, 3, pt.getValue(), 1.0);
    // double fold = getF();
    // temperature = temperature - 2*dv;
    // init(numberOfMolesInPhase, numberOfComponents, 3, pt.getValue(), 1.0);
    // double fnew = getF();
    // temperature = temperature + dv;
    // init(numberOfMolesInPhase, numberOfComponents, 3, pt.getValue(), 1.0);
    // System.out.println("dFdT " + ((fold-fnew)/(2*dv)) + " super " +
    // (getFpoldT()+getFexpdT())*1e3+ " pt " +getType());
    return (getFpoldT() + getFexpdT()) * MBWR_UNIT_FACTOR;

    // // System.out.println("FT " + getFpoldT()*1e3+ " "+ getFexpdT()*1e3 + " super
    // " + super.dFdT() + " pt " +getType());
    // return (getFpoldT()+getFexpdT())*1e3;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdT() {
    return (getFpoldTdT() + getFexpdTdT()) * MBWR_UNIT_FACTOR;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdV() {
    return (getFpoldTdV() + getFexpdTdV()) * MBWR_UNIT_FACTOR * 1e-5;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
    // double dv = molarVolume/1000.0;

    // molarVolume = molarVolume + dv;
    // double fold = getF();
    // molarVolume = molarVolume - 2*dv;
    // double fnew = getF();
    // molarVolume = molarVolume + dv;

    // System.out.println("dFdV " + ((fold-fnew)/(2*dv)) + " super " + super.dFdV()+
    // " pt " +getType());
    // // return (fold-fnew)/(2*dv);
    // System.out.println("dFdV " + ((getFpoldV()+getFexpdV()))*1e3*1e-5 + " super "
    // + super.dFdV()+ " pt " +getType());
    // System.out.println("dFdV " + getFpoldV()+getFexpdV()*1e3*1e-5);
    return (getFpoldV() + getFexpdV()) * MBWR_UNIT_FACTOR * 1e-5;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    return (getFpoldVdV() + getFexpdVdV()) * MBWR_UNIT_FACTOR * 1e-10;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdVdV() {
    return (getFpoldVdVdV() + getFexpdVdVdV()) * MBWR_UNIT_FACTOR * 1e-15;
  }

  /** {@inheritDoc} */
  @Override
  public double getJouleThomsonCoefficient() {
    double dP = pressure * 1e-4;
    PhaseBWRSEos plus = this.clone();
    plus.setPressure(pressure + dP);
    plus.init(getNumberOfMolesInPhase(), getNumberOfComponents(), 1, getType(), 1.0);
    double hPlus = plus.getEnthalpy();

    PhaseBWRSEos minus = this.clone();
    minus.setPressure(pressure - dP);
    minus.init(getNumberOfMolesInPhase(), getNumberOfComponents(), 1, getType(), 1.0);
    double hMinus = minus.getEnthalpy();

    return -(hPlus - hMinus) / (2.0 * dP * getCp());
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume2(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double BonV =
        pt == PhaseType.LIQUID ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
            : pressure * getB() / (numberOfMolesInPhase * temperature * R);
    double Btemp = getB();
    setMolarVolume(1.0 / BonV * Btemp); // numberOfMolesInPhase;
    int iterations = 0;
    int maxIterations = 10000;
    double guesPres = pressure;
    double guesPresdV = 0.0;
    do {
      iterations++;
      guesPres = -R * temperature * dFdV() + R * temperature / getMolarVolume();
      guesPresdV = -R * temperature * dFdVdV()
          - getNumberOfMolesInPhase() * R * temperature / Math.pow(getTotalVolume(), 2.0);
      logger.info("gues pres " + guesPres);
      setMolarVolume(getMolarVolume()
          - 1.0 / (guesPresdV * getNumberOfMolesInPhase()) * (guesPres - pressure) / 50.0);
      Z = pressure * getMolarVolume() / (R * temperature);
    } while (Math.abs((guesPres - pressure) / pressure) > 1.0e-10 && iterations < maxIterations);
    // System.out.println("gues pres " + guesPres);
    if (iterations >= maxIterations) {
      throw new neqsim.util.exception.TooManyIterationsException(this, "molarVolume2",
          maxIterations);
    }
    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume2", "Molar Volume");
    }
    // System.out.println("Z: " + Z + " "+" itert: " +iterations);
    // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
    // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
    // + fVV());

    return getMolarVolume();
  }
}
