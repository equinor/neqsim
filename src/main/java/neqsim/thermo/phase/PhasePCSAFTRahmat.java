package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentPCSAFT;

/**
 * PhasePCSAFTRahmat class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhasePCSAFTRahmat extends PhasePCSAFT {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhasePCSAFTRahmat.class);

  /** Cached molar volume from last converged solution for faster initial guess. */
  private transient double cachedMolarVolume = -1.0;
  /** The cache is valid only for the exact thermodynamic state and requested root. */
  private transient double cachedTemperature = Double.NaN;
  private transient double cachedPressure = Double.NaN;
  private transient PhaseType cachedRootType;
  private transient double[] cachedComponentMoles;

  double dnSAFTdVdVdV = 1.0;

  double daHSSAFTdNdNdN = 1.0;
  double dgHSSAFTdNdNdN = 1.0;

  // by Rahmat
  protected double F2dispI2dVdV;
  protected double F2dispI2dVdVdV = 0.0;
  protected double F2dispZHCdVdV = 1.0;
  protected double F2dispZHCdVdVdV = 1.0;
  protected double F1dispI1dNdNdN = 1.0;
  protected double F2dispZHCdNdNdN = 1.0;
  protected double F2dispI2dNdNdN = 1.0;
  protected double F1dispI1dVdV = 1.0;
  protected double F1dispI1dVdVdV = 1.0;
  protected double F1dispVolTermdVdVdV = 1.0;

  /**
   * Constructor for PhasePCSAFTRahmat.
   */
  public PhasePCSAFTRahmat() {
  }

  /** {@inheritDoc} */
  @Override
  public PhasePCSAFTRahmat clone() {
    PhasePCSAFTRahmat clonedPhase = null;
    try {
      clonedPhase = (PhasePCSAFTRahmat) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentPCSAFT(name, moles, molesInPhase, compNumber);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Calls component.Finit(initType)
   * </p>
   */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt, double beta) {
    // missing?
    // if (initType > 0) {
    for (int i = 0; i < numberOfComponents; i++) {
      componentArray[i].Finit(this, temperature, pressure, totalNumberOfMoles, beta, numberOfComponents, initType);
    }
    // }
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
  }

  /** {@inheritDoc} */
  @Override
  public void volInit() {
    volumeSAFT = getVolume() * 1.0e-5;
    setDmeanSAFT(calcdmeanSAFT());
    setDSAFT(calcdSAFT());
    // System.out.println("saft volume " + getVolumeSAFT());
    // System.out.println("dsaft " + getDSAFT());
    setNSAFT(1.0 * ThermodynamicConstantsInterface.pi / 6.0 * ThermodynamicConstantsInterface.avagadroNumber
        * getNumberOfMolesInPhase() / volumeSAFT * getDSAFT());
    dnSAFTdV = -1.0 * ThermodynamicConstantsInterface.pi / 6.0 * ThermodynamicConstantsInterface.avagadroNumber
        * getNumberOfMolesInPhase() / Math.pow(volumeSAFT, 2.0) * getDSAFT();
    dnSAFTdVdV = 2.0 * ThermodynamicConstantsInterface.pi / 6.0 * ThermodynamicConstantsInterface.avagadroNumber
        * getNumberOfMolesInPhase() / Math.pow(volumeSAFT, 3.0) * getDSAFT();
    // System.out.println("N SAFT " + getNSAFT());
    dnSAFTdVdVdV = -6.0 * ThermodynamicConstantsInterface.pi / 6.0 * ThermodynamicConstantsInterface.avagadroNumber
        * getNumberOfMolesInPhase() / Math.pow(volumeSAFT, 4.0) * getDSAFT();

    // added by rahmat
    dNSAFTdT = 1.0 * ThermodynamicConstantsInterface.pi / 6.0 * ThermodynamicConstantsInterface.avagadroNumber
        * getNumberOfMolesInPhase() / volumeSAFT * getdDSAFTdT();

    setGhsSAFT((1.0 - nSAFT / 2.0) / Math.pow(1.0 - nSAFT, 3.0));
    setmSAFT(calcmSAFT());
    setMmin1SAFT(calcmmin1SAFT());
    setmdSAFT(calcmdSAFT());
    setAHSSAFT((4.0 * getNSAFT() - 3.0 * Math.pow(getNSAFT(), 2.0)) / Math.pow(1.0 - getNSAFT(), 2.0));
    // Exact derivatives of a_HS=(4*eta-3*eta^2)/(1-eta)^2 and g_HS=(1-eta/2)/(1-eta)^3.
    double eta = getNSAFT();
    double om = 1.0 - eta;
    daHSSAFTdN = (4.0 - 2.0 * eta) / Math.pow(om, 3.0);
    daHSSAFTdNdN = (10.0 - 4.0 * eta) / Math.pow(om, 4.0);
    dgHSSAFTdN = (2.5 - eta) / Math.pow(om, 4.0);
    dgHSSAFTdNdN = (9.0 - 3.0 * eta) / Math.pow(om, 5.0);
    daHSSAFTdNdNdN = (36.0 - 12.0 * eta) / Math.pow(om, 5.0);
    dgHSSAFTdNdNdN = (42.0 - 12.0 * eta) / Math.pow(om, 6.0);

    setF1dispVolTerm(ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase() / getVolumeSAFT());
    F1dispSumTerm = calcF1dispSumTerm();
    F1dispI1 = calcF1dispI1();
    F1dispVolTermdV = -ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase()
        / Math.pow(getVolumeSAFT(), 2.0);
    F1dispVolTermdVdV = 2.0 * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase()
        / Math.pow(getVolumeSAFT(), 3.0);
    F1dispVolTermdVdVdV = -6.0 * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase()
        / Math.pow(getVolumeSAFT(), 4.0);

    dNSAFTdTdV = -1.0 * ThermodynamicConstantsInterface.pi / 6.0 * ThermodynamicConstantsInterface.avagadroNumber
        * getNumberOfMolesInPhase() / Math.pow(volumeSAFT, 2.0) * getdDSAFTdT();
    dNSAFTdTdT = 1.0 * ThermodynamicConstantsInterface.pi / 6.0 * ThermodynamicConstantsInterface.avagadroNumber
        * getNumberOfMolesInPhase() / volumeSAFT * getd2DSAFTdTdT();

    F1dispI1dN = calcF1dispI1dN();
    F1dispI1dNdN = calcF1dispI1dNdN();
    F1dispI1dNdNdN = calcF1dispI1dNdNdN();

    F1dispI1dm = calcF1dispI1dm();
    F1dispI1dV = F1dispI1dN * getDnSAFTdV();
    F1dispI1dVdV = F1dispI1dNdN * getDnSAFTdV() * getDnSAFTdV() + F1dispI1dN * dnSAFTdVdV; // F1dispI1dNdN*dnSAFTdVdV;

    // added by Rahmat
    F1dispI1dVdVdV = F1dispI1dNdNdN * getDnSAFTdV() * getDnSAFTdV() * getDnSAFTdV()
        + F1dispI1dN * 2.0 * getDnSAFTdV() * dnSAFTdVdV + F1dispI1dNdN * dnSAFTdVdV + F1dispI1dN * dnSAFTdVdVdV; // F1dispI1dNdN*dnSAFTdVdV;

    setF2dispSumTerm(calcF2dispSumTerm());
    setF2dispI2(calcF2dispI2());
    F2dispI2dN = calcF2dispI2dN();
    F2dispI2dNdN = calcF2dispI2dNdN();
    // added by Rahmat
    F2dispI2dNdNdN = calcF2dispI2dNdNdN();

    F2dispI2dm = calcF2dispI2dm();

    F2dispI2dV = F2dispI2dN * getDnSAFTdV();
    F2dispI2dVdV = F2dispI2dNdN * getDnSAFTdV() * getDnSAFTdV() + F2dispI2dN * dnSAFTdVdV; // F2dispI2dNdN*dnSAFTdVdV;

    // added by Rahmat
    F2dispI2dVdVdV = F2dispI2dNdNdN * getDnSAFTdV() * getDnSAFTdV() * getDnSAFTdV()
        + F2dispI2dN * 2.0 * getDnSAFTdV() * dnSAFTdVdV + F2dispI2dNdN * dnSAFTdVdV + F2dispI2dN * dnSAFTdVdVdV;

    F2dispZHC = calcF2dispZHC();
    F2dispZHCdN = calcF2dispZHCdN();
    F2dispZHCdNdN = calcF2dispZHCdNdN();
    F2dispZHCdNdNdN = calcF2dispZHCdNdNdN();

    setF2dispZHCdm(calcF2dispZHCdm());
    F2dispZHCdV = F2dispZHCdN * getDnSAFTdV();
    F2dispZHCdVdV = F2dispZHCdNdN * getDnSAFTdV() * getDnSAFTdV() + F2dispZHCdN * dnSAFTdVdV;
    // F2dispZHCdNdN*dnSAFTdVdV*0;
    F2dispZHCdVdVdV = F2dispZHCdNdNdN * getDnSAFTdV() * getDnSAFTdV() * getDnSAFTdV()
        + F2dispZHCdNdN * 2.0 * getDnSAFTdV() * dnSAFTdVdV + F2dispZHCdNdN * dnSAFTdVdV + F2dispZHCdN * dnSAFTdVdVdV;

    // added by rahmat
    dF1dispI1dT = calcdF1dispI1dT();
    dF2dispI2dT = calcdF2dispI2dT();
    dF1dispSumTermdT = calcdF1dispSumTermdT();
    dF2dispSumTermdT = calcdF2dispSumTermdT();
    dF2dispZHCdT = calcdF2dispZHCdT();
    dF1dispSumTermdTdT = calcdF1dispSumTermdTdT();
    dF1dispI1dTdV = calcdF1dispI1dTdV();
    dF1dispI1dTdT = calcdF1dispI1dTdT();
    dF2dispSumTermdTdT = calcdF2dispSumTermdTdT();
    dF2dispI2dTdV = calcdF2dispI2dTdV();
    dF2dispI2dTdT = calcdF2dispI2dTdT();
    dF2dispZHCdTdV = calcdF2dispZHCdTdV();
    dF2dispZHCdTdT = calcdF2dispZHCdTdT();

  }

  /** {@inheritDoc} */
  @Override
  public double calcF2dispZHC() {
    double temp = 1.0
        + getmSAFT() * (8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2.0)) / Math.pow(1.0 - getNSAFT(), 4.0)
        + (1.0 - getmSAFT()) * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2.0) + 12 * Math.pow(getNSAFT(), 3.0)
            - 2 * Math.pow(getNSAFT(), 4.0)) / Math.pow((1.0 - getNSAFT()) * (2.0 - getNSAFT()), 2.0);
    return 1.0 / temp;
  }

  /** {@inheritDoc} */
  @Override
  public double calcF2dispZHCdm() {
    double temp = -Math.pow(F2dispZHC, 2.0);
    return temp * ((8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2.0)) / Math.pow(1.0 - getNSAFT(), 4.0)
        - (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2.0) + 12 * Math.pow(getNSAFT(), 3.0)
            - 2 * Math.pow(getNSAFT(), 4.0)) / Math.pow((1.0 - getNSAFT()) * (2.0 - getNSAFT()), 2.0));
  }

  /** {@inheritDoc} */
  @Override
  public double calcF2dispZHCdN() {
    return compressibilityCorrectionDerivative(1);
  }

  /** {@inheritDoc} */
  @Override
  public double calcF2dispZHCdNdN() {
    return compressibilityCorrectionDerivative(2);
  }

  /**
   * calcF2dispZHCdNdNdN.
   *
   * @return a double
   */
  public double calcF2dispZHCdNdNdN() {
    return compressibilityCorrectionDerivative(3);
  }

  // added by rahmat
  /**
   * calcdF2dispZHCdT.
   *
   * @return a double
   */
  @Override
  public double calcdF2dispZHCdT() {
    return super.calcdF2dispZHCdT();
  }

  /** {@inheritDoc} */
  @Override
  public double calcmSAFT() {
    double temp2 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp2 += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi() / getNumberOfMolesInPhase();
    }

    return temp2;
  }

  /** {@inheritDoc} */
  @Override
  public double calcF1dispSumTerm() {
    double temp1 = 0.0;

    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        temp1 += getComponent(i).getNumberOfMolesInPhase() * getComponent(j).getNumberOfMolesInPhase()
            * getComponent(i).getmSAFTi() * getComponent(j).getmSAFTi()
            * Math.sqrt(getComponent(i).getEpsikSAFT() / temperature * getComponent(j).getEpsikSAFT() / temperature)
            * (1.0 - mixRule.getBinaryInteractionParameter(i, j))
            * Math.pow(0.5 * (getComponent(i).getSigmaSAFTi() + getComponent(j).getSigmaSAFTi()), 3.0);
      }
    }
    return temp1 / Math.pow(getNumberOfMolesInPhase(), 2.0);
  }

  // added by rahmat
  /**
   * calcdF1dispSumTermdT.
   *
   * @return a double
   */
  @Override
  public double calcdF1dispSumTermdT() {
    double temp1 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        temp1 += getComponent(i).getNumberOfMolesInPhase() * getComponent(j).getNumberOfMolesInPhase()
            * getComponent(i).getmSAFTi() * getComponent(j).getmSAFTi()
            * Math.sqrt(getComponent(i).getEpsikSAFT() / temperature * getComponent(j).getEpsikSAFT() / temperature)
            * (1.0 - mixRule.getBinaryInteractionParameter(i, j))
            * Math.pow(0.5 * (getComponent(i).getSigmaSAFTi() + getComponent(j).getSigmaSAFTi()), 3.0)
            * (-1 / temperature);
      }
    }
    return temp1 / Math.pow(getNumberOfMolesInPhase(), 2.0);
  }

  /**
   * calcdF2dispSumTermdT.
   *
   * @return a double
   */
  @Override
  public double calcdF2dispSumTermdT() {
    double temp1 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        temp1 += getComponent(i).getNumberOfMolesInPhase() * getComponent(j).getNumberOfMolesInPhase()
            * getComponent(i).getmSAFTi() * getComponent(j).getmSAFTi()
            * (getComponent(i).getEpsikSAFT() / temperature * getComponent(j).getEpsikSAFT() / temperature)
            * Math.pow(1.0 - mixRule.getBinaryInteractionParameter(i, j), 2)
            * Math.pow(0.5 * (getComponent(i).getSigmaSAFTi() + getComponent(j).getSigmaSAFTi()), 3.0)
            * (-2 / temperature);
      }
    }
    return temp1 / Math.pow(getNumberOfMolesInPhase(), 2.0);
  }

  /** {@inheritDoc} */
  @Override
  public double calcF2dispSumTerm() {
    double temp1 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        temp1 += getComponent(i).getNumberOfMolesInPhase() * getComponent(j).getNumberOfMolesInPhase()
            * getComponent(i).getmSAFTi() * getComponent(j).getmSAFTi() * getComponent(i).getEpsikSAFT() / temperature
            * getComponent(j).getEpsikSAFT() / temperature
            * Math.pow((1.0 - mixRule.getBinaryInteractionParameter(i, j)), 2.0)
            * Math.pow(0.5 * (getComponent(i).getSigmaSAFTi() + getComponent(j).getSigmaSAFTi()), 3.0);
      }
    }
    return temp1 / Math.pow(getNumberOfMolesInPhase(), 2.0);
  }

  /** {@inheritDoc} */
  @Override
  public double calcF1dispI1dN() {
    double temp1 = 0.0;
    for (int i = 1; i < 7; i++) {
      temp1 += i * getaSAFT(i, getmSAFT(), aConstSAFT) * Math.pow(getNSAFT(), i - 1.0);
    }
    return temp1;
  }

  /** {@inheritDoc} */
  @Override
  public double calcF1dispI1dNdN() {
    double temp1 = 0.0;
    for (int i = 2; i < 7; i++) {
      temp1 += (i - 1.0) * i * getaSAFT(i, getmSAFT(), aConstSAFT) * Math.pow(getNSAFT(), i - 2.0);
    }
    return temp1;
  }

  // added by Rahmat
  /**
   * calcF1dispI1dNdNdN.
   *
   * @return a double
   */
  public double calcF1dispI1dNdNdN() {
    double temp1 = 0.0;
    for (int i = 2; i < 7; i++) {
      temp1 += (i - 1.0) * (i - 2.0) * i * getaSAFT(i, getmSAFT(), aConstSAFT) * Math.pow(getNSAFT(), i - 3.0);
    }
    return temp1;
  }

  /** {@inheritDoc} */
  @Override
  public double calcF1dispI1dm() {
    double temp1 = 0.0;
    for (int i = 0; i < 7; i++) {
      temp1 += getaSAFTdm(i, getmSAFT(), aConstSAFT) * Math.pow(getNSAFT(), i);
    }
    return temp1;
  }

  /** {@inheritDoc} */
  @Override
  public double calcF2dispI2dN() {
    double temp1 = 0.0;
    for (int i = 1; i < 7; i++) {
      temp1 += i * getaSAFT(i, getmSAFT(), bConstSAFT) * Math.pow(getNSAFT(), i - 1.0);
    }
    return temp1;
  }

  /** {@inheritDoc} */
  @Override
  public double calcF2dispI2dNdN() {
    double temp1 = 0.0;
    for (int i = 2; i < 7; i++) {
      temp1 += (i - 1.0) * i * getaSAFT(i, getmSAFT(), bConstSAFT) * Math.pow(getNSAFT(), i - 2.0);
    }
    return temp1;
  }

  /**
   * calcF2dispI2dNdNdN.
   *
   * @return a double
   */
  public double calcF2dispI2dNdNdN() {
    double temp1 = 0.0;
    for (int i = 2; i < 7; i++) {
      temp1 += (i - 1.0) * (i - 2.0) * i * getaSAFT(i, getmSAFT(), bConstSAFT) * Math.pow(getNSAFT(), i - 3.0);
    }
    return temp1;
  }

  /** {@inheritDoc} */
  @Override
  public double calcF2dispI2dm() {
    double temp1 = 0.0;
    for (int i = 0; i < 7; i++) {
      temp1 += getaSAFTdm(i, getmSAFT(), bConstSAFT) * Math.pow(getNSAFT(), i);
    }
    return temp1;
  }

  /** {@inheritDoc} */
  @Override
  public double calcF1dispI1() {
    double temp1 = 0.0;
    for (int i = 0; i < 7; i++) {
      temp1 += getaSAFT(i, getmSAFT(), aConstSAFT) * Math.pow(getNSAFT(), i);
    }
    return temp1;
  }

  // added by rahmat
  /**
   * calcdF1dispI1dT.
   *
   * @return a double
   */
  @Override
  public double calcdF1dispI1dT() {
    double temp1 = 0.0;
    for (int i = 0; i < 7; i++) {
      temp1 += getaSAFT(i, getmSAFT(), aConstSAFT) * i * Math.pow(getNSAFT(), i - 1) * dNSAFTdT;
    }
    return temp1;
  }

  /**
   * calcdF2dispI2dT.
   *
   * @return a double
   */
  @Override
  public double calcdF2dispI2dT() {
    double temp1 = 0.0;
    for (int i = 0; i < 7; i++) {
      temp1 += getaSAFT(i, getmSAFT(), bConstSAFT) * i * Math.pow(getNSAFT(), i - 1) * dNSAFTdT;
    }
    return temp1;
  }

  /** {@inheritDoc} */
  @Override
  public double calcF2dispI2() {
    double temp1 = 0.0;
    for (int i = 0; i < 7; i++) {
      temp1 += getaSAFT(i, getmSAFT(), bConstSAFT) * Math.pow(getNSAFT(), i);
    }
    return temp1;
  }

  /** {@inheritDoc} */
  @Override
  public double getaSAFT(int i, double m, double[][] ab) {
    return ab[0][i] + (m - 1.0) / m * ab[1][i] + (m - 1.0) / m * (m - 2.0) / m * ab[2][i];
  }

  /** {@inheritDoc} */
  @Override
  public double getaSAFTdm(int i, double m, double[][] ab) {
    return (m - (m - 1.0)) / (m * m) * ab[1][i]
        + ((2.0 * m - 3.0) * m * m - 2 * m * (m * m - 3 * m + 2)) / Math.pow(m, 4.0) * ab[2][i];
  }

  /** {@inheritDoc} */
  @Override
  public double calcmdSAFT() {
    double temp2 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp2 += getComponent(i).getNumberOfMolesInPhase() / getNumberOfMolesInPhase() * getComponent(i).getmSAFTi()
          * Math.pow(((ComponentPCSAFT) getComponent(i)).getdSAFTi(), 3.0);
    }

    return temp2;
  }

  /** {@inheritDoc} */
  @Override
  public double calcmmin1SAFT() {
    double temp2 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp2 += getComponent(i).getNumberOfMolesInPhase() / getNumberOfMolesInPhase()
          * (getComponent(i).getmSAFTi() - 1.0);
    }

    return temp2;
  }

  /** {@inheritDoc} */
  @Override
  public double calcdmeanSAFT() {
    double temp = 0.0;
    double temp2 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi()
          * Math.pow(((ComponentPCSAFT) getComponent(i)).getdSAFTi(), 3.0);
      temp2 += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi();
    }
    return Math.pow(temp / temp2, 1.0 / 3.0);
  }

  // need to check (modified by rahmat)
  /** {@inheritDoc} */
  @Override
  public double calcdSAFT() {
    double temp1 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp1 += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi()
          * Math.pow(((ComponentPCSAFT) getComponent(i)).getdSAFTi(), 3.0);
    }
    // System.out.println("d saft calc " + temp/getNumberOfMolesInPhase());
    return temp1 / getNumberOfMolesInPhase();
  }

  /** {@inheritDoc} */
  @Override
  public double F_HC_SAFT() {
    return getNumberOfMolesInPhase() * (getmSAFT() * getAHSSAFT() - getMmin1SAFT() * Math.log(getGhsSAFT()));
    // (ThermodynamicConstantsInterface.R*temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double dF_HC_SAFTdV() {
    return getNumberOfMolesInPhase() * (getmSAFT() * daHSSAFTdN * getDnSAFTdV()
        - getMmin1SAFT() * 1.0 / getGhsSAFT() * getDgHSSAFTdN() * getDnSAFTdV());
    // (ThermodynamicConstantsInterface.R*temperature);
  }

  // edited by Rahmat
  /** {@inheritDoc} */
  @Override
  public double dFdT() {
    return useHS * dF_HC_SAFTdT() + useDISP1 * dF_DISP1_SAFTdT() + useDISP2 * dF_DISP2_SAFTdT();
  }

  /**
   * dF_HC_SAFTdT.
   *
   * @return a double
   */
  @Override
  public double dF_HC_SAFTdT() {
    return getNumberOfMolesInPhase()
        * (getmSAFT() * daHSSAFTdN * dNSAFTdT - getMmin1SAFT() * 1.0 / getGhsSAFT() * getDgHSSAFTdN() * dNSAFTdT);
    // (ThermodynamicConstantsInterface.R*temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double dF_HC_SAFTdVdV() {
    return super.dF_HC_SAFTdVdV();
  }
  // additonal dF_HC_SAFTdVdVdV (by Rahmat)

  /** {@inheritDoc} */
  @Override
  public double dF_HC_SAFTdVdVdV() {
    return super.dF_HC_SAFTdVdVdV();
  }

  /** {@inheritDoc} */
  @Override
  public double F_DISP1_SAFT() {
    return getNumberOfMolesInPhase()
        * (-2.0 * ThermodynamicConstantsInterface.pi * getF1dispVolTerm() * getF1dispSumTerm() * getF1dispI1()); // (ThermodynamicConstantsInterface.R*temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double dF_DISP1_SAFTdV() {
    return getNumberOfMolesInPhase()
        * (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV * getF1dispSumTerm() * getF1dispI1()
            - 2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTerm * getF1dispSumTerm() * F1dispI1dV); // (ThermodynamicConstantsInterface.R*temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double dF_DISP1_SAFTdVdV() {
    return getNumberOfMolesInPhase()
        * ((-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdVdV * getF1dispSumTerm() * getF1dispI1())
            + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV * getF1dispSumTerm() * F1dispI1dV)
            + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV * getF1dispSumTerm() * F1dispI1dV)
            + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTerm * getF1dispSumTerm() * F1dispI1dVdV));
  }

  // added by Rahmat
  /**
   * dF_DISP1_SAFTdVdVdV.
   *
   * @return a double
   */
  public double dF_DISP1_SAFTdVdVdV() {
    return getNumberOfMolesInPhase()
        * ((-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdVdVdV * getF1dispSumTerm() * getF1dispI1())
            + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdVdV * getF1dispSumTerm() * F1dispI1dV)
            + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdVdV * getF1dispSumTerm() * F1dispI1dV)
            + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV * getF1dispSumTerm() * F1dispI1dVdV)
            + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdVdV * getF1dispSumTerm() * F1dispI1dV)
            + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV * getF1dispSumTerm() * F1dispI1dVdV)
            + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV * getF1dispSumTerm() * F1dispI1dVdV)
            + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTerm * getF1dispSumTerm() * F1dispI1dVdVdV));
  }

  // added by Rahmat
  /**
   * dF_DISP1_SAFTdT.
   *
   * @return a double
   */
  @Override
  public double dF_DISP1_SAFTdT() {
    return getNumberOfMolesInPhase() * (-2.0 * ThermodynamicConstantsInterface.pi
        * (dF1dispVolTermdT * getF1dispSumTerm() * getF1dispI1() + dF1dispSumTermdT * getF1dispVolTerm() * getF1dispI1()
            + dF1dispI1dT * getF1dispVolTerm() * getF1dispSumTerm()));
  }

  /**
   * dF_DISP2_SAFTdT.
   *
   * @return a double
   */
  @Override
  public double dF_DISP2_SAFTdT() {
    return getNumberOfMolesInPhase() * (-1 * ThermodynamicConstantsInterface.pi * getmSAFT()) * getF1dispVolTerm()
        * (dF2dispSumTermdT * getF2dispI2() * getF2dispZHC() + dF2dispI2dT * getF2dispSumTerm() * getF2dispZHC()
            + dF2dispZHCdT * getF2dispSumTerm() * getF2dispI2());
  }

  /** {@inheritDoc} */
  @Override
  public double F_DISP2_SAFT() {
    return getNumberOfMolesInPhase() * (-ThermodynamicConstantsInterface.pi * getmSAFT() * getF1dispVolTerm()
        * getF2dispSumTerm() * getF2dispI2() * getF2dispZHC());
    // (ThermodynamicConstantsInterface.R*temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double dF_DISP2_SAFTdV() {
    return getNumberOfMolesInPhase() * (-ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV
        * getF2dispSumTerm() * getF2dispI2() * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dV
            * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * getF2dispI2()
            * F2dispZHCdV); // (ThermodynamicConstantsInterface.R*temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double dF_DISP2_SAFTdVdV() {
    return getNumberOfMolesInPhase() * ((-ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV
        * getF2dispSumTerm() * getF2dispI2() * getF2dispZHC())
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * getF2dispZHC()
            * F2dispI2dV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * getF2dispI2()
            * F2dispZHCdV)
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * F2dispI2dV
            * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dVdV
            * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dV
            * F2dispZHCdV
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * getF2dispI2()
            * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dV
            * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * getF2dispI2()
            * F2dispZHCdVdV));
  }

  /**
   * dF_DISP2_SAFTdVdVdV.
   *
   * @return a double
   */
  public double dF_DISP2_SAFTdVdVdV() {
    return getNumberOfMolesInPhase() * ((-ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdVdV
        * getF2dispSumTerm() * getF2dispI2() * getF2dispZHC())
        + (-ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV * getF2dispSumTerm() * F2dispI2dV
            * getF2dispZHC())
        + (-ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV * getF2dispSumTerm() * getF2dispI2()
            * F2dispZHCdV)
        + -(ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV * getF2dispSumTerm() * getF2dispZHC()
            * F2dispI2dV)
        + -(ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * F2dispZHCdV
            * F2dispI2dV)
        + -(ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * getF2dispZHC()
            * F2dispI2dVdV)
        + -(ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV * getF2dispSumTerm() * getF2dispI2()
            * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * F2dispI2dV
            * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * getF2dispI2()
            * F2dispZHCdVdV)
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV * getF2dispSumTerm() * F2dispI2dV
            * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * F2dispI2dVdV
            * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * F2dispI2dV
            * F2dispZHCdV
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * F2dispI2dVdV
            * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dVdVdV
            * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dVdV
            * F2dispZHCdV
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * F2dispI2dV
            * F2dispZHCdV
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dVdV
            * F2dispZHCdV
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dV
            * F2dispZHCdVdV
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV * getF2dispSumTerm() * getF2dispI2()
            * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * F2dispI2dV
            * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * getF2dispI2()
            * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * F2dispI2dV
            * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dVdV
            * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dV
            * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * getF2dispI2()
            * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dV
            * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * getF2dispI2()
            * F2dispZHCdVdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV * getF2dispSumTerm() * getF2dispI2()
            * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * F2dispI2dV
            * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * getF2dispI2()
            * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * F2dispI2dV
            * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dVdV
            * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dV
            * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * getF2dispI2()
            * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dV
            * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * getF2dispI2()
            * F2dispZHCdVdVdV));
  }

  /** {@inheritDoc} */
  @Override
  public double getF() {
    // System.out.println("F-HC " + useHS*F_HC_SAFT());

    // System.out.println("F-DISP1 " + useDISP1*F_DISP1_SAFT());

    // System.out.println("F-DISP2 " + useDISP2*F_DISP2_SAFT());
    return useHS * F_HC_SAFT() + useDISP1 * F_DISP1_SAFT() + useDISP2 * F_DISP2_SAFT();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
    // System.out.println("N-saft " + getNSAFT());
    // System.out.println("F-HC " + useHS*F_HC_SAFT());
    // System.out.println("F-DISP1 " + useDISP1*F_DISP1_SAFT());

    // System.out.println("F-DISP2 " + useDISP2*F_DISP2_SAFT());

    return (useHS * dF_HC_SAFTdV() + useDISP1 * dF_DISP1_SAFTdV() + useDISP2 * dF_DISP2_SAFTdV()) * 1.0e-5;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    return (useHS * dF_HC_SAFTdVdV() + useDISP1 * dF_DISP1_SAFTdVdV() + useDISP2 * dF_DISP2_SAFTdVdV()) * 1.0e-10;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdVdV() {
    return (useHS * dF_HC_SAFTdVdVdV() + useDISP1 * dF_DISP1_SAFTdVdVdV() + useDISP2 * dF_DISP2_SAFTdVdVdV()) * 1.0e-20;
  }

  // added by rahmat
  /**
   * getdDSAFTdT.
   *
   * @return a double
   */
  @Override
  public double getdDSAFTdT() {
    return super.getdDSAFTdT();
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {
    if (!Double.isFinite(pressure) || pressure <= 0 || !Double.isFinite(temperature) || temperature <= 0
        || !Double.isFinite(numberOfMolesInPhase) || numberOfMolesInPhase <= 0) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Invalid PC-SAFT state");
    }
    double segmentVolume = Math.PI / 6.0 * ThermodynamicConstantsInterface.avagadroNumber * calcdSAFT() * 1e5;
    if (!Double.isFinite(segmentVolume) || segmentVolume <= 0) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Invalid segment volume");
    }
    double tolerance = 1e-9 * Math.max(1.0, pressure);
    boolean sameState = temperature == cachedTemperature && pressure == cachedPressure && pt == cachedRootType
        && cachedComponentMoles != null && cachedComponentMoles.length == numberOfComponents;
    for (int i = 0; sameState && i < numberOfComponents; i++) {
      sameState = cachedComponentMoles[i] == getComponent(i).getNumberOfMolesInPhase();
    }
    if (sameState && cachedMolarVolume > segmentVolume) {
      setMolarVolume(cachedMolarVolume);
      volInit();
      if (getNSAFT() > 0 && getNSAFT() < 1 && Math.abs(calcPressure() - pressure) <= tolerance) {
        Z = pressure * getMolarVolume() / (R * temperature);
        return getMolarVolume();
      }
    }
    cachedMolarVolume = -1.0;

    // Bracket in packing fraction, where the hard-sphere domain is explicit. Gas selects
    // the first mechanically stable crossing; liquid selects the last. A previous state
    // cannot choose a different root. The cubic mesh also resolves dilute gas roots.
    final int subdivisions = 400;
    final double maxEta = 1.0 - 1e-6;
    double previousEta = pt == PhaseType.GAS ? 1e-14 : maxEta;
    double previousResidual = pressureResidualAtPackingFraction(previousEta, segmentVolume, pressure);
    double low = Double.NaN;
    double high = Double.NaN;
    for (int step = 1; step <= subdivisions; step++) {
      int index = pt == PhaseType.GAS ? step : subdivisions - step;
      double eta = Math.max(1e-14, maxEta * Math.pow((double) index / subdivisions, 3.0));
      double residual = pressureResidualAtPackingFraction(eta, segmentVolume, pressure);
      if (Double.isFinite(previousResidual) && Double.isFinite(residual)
          && (pt == PhaseType.GAS ? previousResidual <= 0 && residual >= 0 : previousResidual >= 0 && residual <= 0)) {
        low = Math.min(previousEta, eta);
        high = Math.max(previousEta, eta);
        break;
      }
      previousEta = eta;
      previousResidual = residual;
    }
    if (!Double.isFinite(low)) {
      throw new neqsim.util.exception.TooManyIterationsException(this, "molarVolume: no physical pressure root",
          subdivisions);
    }
    for (int iteration = 0; iteration < 100; iteration++) {
      double eta = (low + high) / 2.0;
      double residual = pressureResidualAtPackingFraction(eta, segmentVolume, pressure);
      if (Double.isFinite(residual) && Math.abs(residual) <= tolerance) {
        Z = pressure * getMolarVolume() / (R * temperature);
        cachedMolarVolume = getMolarVolume();
        cachedTemperature = temperature;
        cachedPressure = pressure;
        cachedRootType = pt;
        cachedComponentMoles = new double[numberOfComponents];
        for (int i = 0; i < numberOfComponents; i++) {
          cachedComponentMoles[i] = getComponent(i).getNumberOfMolesInPhase();
        }
        return getMolarVolume();
      }
      if (!Double.isFinite(residual)) {
        throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Nonfinite pressure residual");
      }
      if (residual > 0) {
        high = eta;
      } else {
        low = eta;
      }
    }
    throw new neqsim.util.exception.TooManyIterationsException(this, "molarVolume: pressure residual", 100);
  }

  /**
   * Evaluates a trial strictly inside the PC-SAFT hard-sphere domain.
   *
   * @param eta packing fraction, strictly between zero and one
   * @param segmentVolume molar volume at unit packing fraction, in NeqSim volume units
   * @param targetPressure specified pressure in bara
   * @return calculated pressure minus target pressure in bar
   */
  private double pressureResidualAtPackingFraction(double eta, double segmentVolume, double targetPressure) {
    setMolarVolume(segmentVolume / eta);
    volInit();
    return calcPressure() - targetPressure;
  }
}
