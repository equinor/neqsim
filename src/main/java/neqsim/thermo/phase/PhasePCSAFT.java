package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentPCSAFT;

/**
 * <p>
 * PhasePCSAFT class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhasePCSAFT extends PhaseSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhasePCSAFT.class);

  double nSAFT = 1.0;
  double dnSAFTdV = 1.0;
  double dnSAFTdVdV = 1.0;
  double dNSAFTdT = 0.0;
  double dF1dispVolTermdT = 0.0;
  double dF1dispSumTermdT = 0.0;
  double dF1dispI1dT = 0.0;
  double dF2dispSumTermdT = 0.0;
  double dF2dispI2dT = 0.0;
  double dF2dispZHCdT = 0.0;

  double dmeanSAFT = 0.0;
  double dSAFT = 1.0;
  double mSAFT = 1.0;
  double mdSAFT = 1.0;
  double nmSAFT = 1.0;
  double mmin1SAFT = 1.0;
  double ghsSAFT = 1.0;
  double aHSSAFT = 1.0;
  double volumeSAFT = 1.0;
  double daHCSAFTdN = 1.0;
  double daHSSAFTdN = 1.0;
  double dgHSSAFTdN = 1.0;
  double daHSSAFTdNdN = 1.0;
  double dgHSSAFTdNdN = 1.0;

  int useHS = 1;
  int useDISP1 = 1;
  int useDISP2 = 1;

  protected double[][] aConstSAFT = {
      {0.9105631445, 0.6361281449, 2.6861347891, -26.547362491, 97.759208784, -159.59154087,
          91.297774084},
      {-0.3084016918, 0.1860531159, -2.5030047259, 21.419793629, -65.255885330, 83.318680481,
          -33.746922930},
      {-0.0906148351, 0.4527842806, 0.5962700728, -1.7241829131, -4.1302112531, 13.776631870,
          -8.6728470368}};
  protected double[][] bConstSAFT = {
      {0.7240946941, 2.2382791861, -4.0025849485, -21.003576815, 26.855641363, 206.55133841,
          -355.60235612},
      {-0.5755498075, 0.6995095521, 3.8925673390, -17.215471648, 192.67226447, -161.82646165,
          -165.20769346},
      {0.0976883116, -0.2557574982, -9.1558561530, 20.642075974, -38.804430052, 93.626774077,
          -29.666905585}};
  protected double F1dispVolTerm = 1.0;
  protected double F1dispSumTerm = 1.0;
  protected double F1dispI1 = 1.0;
  protected double F2dispI2 = 1.0;
  protected double F2dispZHC = 1.0;
  protected double F2dispZHCdN = 1.0;
  protected double F2dispZHCdm = 1.0;
  protected double F2dispZHCdV = 1.0;
  protected double F2dispI2dVdV = 0.0;
  protected double F2dispZHCdVdV = 0.0;
  protected double F1dispI1dNdN = 1.0;
  protected double F1dispVolTermdV = 1.0;
  protected double F1dispVolTermdVdV = 1.0;
  protected double F1dispI1dN = 1.0;
  protected double F1dispI1dm = 1.0;
  protected double F1dispI1dV = 1.0;
  protected double F2dispI2dV = 1.0;
  protected double F2dispI2dN = 1.0;
  protected double F2dispI2dm = 1.0;
  protected double F2dispSumTerm = 0.0;
  protected double F2dispZHCdNdN = 1.0;
  protected double F2dispI2dNdN = 1.0;
  protected double F1dispI1dVdV = 0.0;

  /**
   * <p>
   * Constructor for PhasePCSAFT.
   * </p>
   */
  public PhasePCSAFT() {}

  /** {@inheritDoc} */
  @Override
  public PhasePCSAFT clone() {
    PhasePCSAFT clonedPhase = null;
    try {
      clonedPhase = (PhasePCSAFT) super.clone();
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
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    if (initType > 0) {
      for (int i = 0; i < numberOfComponents; i++) {
        componentArray[i].Finit(this, temperature, pressure, totalNumberOfMoles, beta,
            numberOfComponents, initType);
      }
    }
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
  }

  /**
   * <p>
   * volInit.
   * </p>
   */
  public void volInit() {
    volumeSAFT = getVolume() * 1.0e-5;
    setDmeanSAFT(calcdmeanSAFT());
    setDSAFT(calcdSAFT());
    // System.out.println("saft volume " + getVolumeSAFT());
    // System.out.println("dsaft " + getDSAFT());
    setNSAFT(1.0 * ThermodynamicConstantsInterface.pi / 6.0
        * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase() / volumeSAFT
        * getDSAFT());
    dnSAFTdV = -1.0 * ThermodynamicConstantsInterface.pi / 6.0
        * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase()
        / Math.pow(volumeSAFT, 2.0) * getDSAFT();
    dnSAFTdVdV = 2.0 * ThermodynamicConstantsInterface.pi / 6.0
        * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase()
        / Math.pow(volumeSAFT, 3.0) * getDSAFT();
    dNSAFTdT = 1.0 * ThermodynamicConstantsInterface.pi / 6.0
        * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase() / volumeSAFT
        * getdDSAFTdT();
    // System.out.println("N SAFT " + getNSAFT());
    setGhsSAFT((1.0 - nSAFT / 2.0) / Math.pow(1.0 - nSAFT, 3.0));
    setmSAFT(calcmSAFT());
    setMmin1SAFT(calcmmin1SAFT());
    setmdSAFT(calcmdSAFT());
    setAHSSAFT(
        (4.0 * getNSAFT() - 3.0 * Math.pow(getNSAFT(), 2.0)) / Math.pow(1.0 - getNSAFT(), 2.0));
    daHSSAFTdN = ((4.0 - 6.0 * getNSAFT()) * Math.pow(1.0 - getNSAFT(), 2.0)
        - (4.0 * getNSAFT() - 3 * Math.pow(getNSAFT(), 2.0)) * 2.0 * (1.0 - getNSAFT()) * (-1.0))
        / Math.pow(1.0 - getNSAFT(), 4.0);
    daHSSAFTdNdN =
        (-6.0 * Math.pow(1.0 - getNSAFT(), 2.0) + 2.0 * (1.0 - getNSAFT()) * (4.0 - 6 * getNSAFT()))
            / Math.pow(1.0 - getNSAFT(), 4.0)
            + ((8.0 - 12.0 * getNSAFT()) * Math.pow(1.0 - getNSAFT(), 3.0)
                + (8.0 - 6.0 * Math.pow(getNSAFT(), 2.0)) * 3.0 * Math.pow(1.0 - getNSAFT(), 2.0))
                / Math.pow(1.0 - getNSAFT(), 6.0);
    dgHSSAFTdN = (-0.5 * Math.pow(1.0 - getNSAFT(), 3.0)
        - (1.0 - getNSAFT() / 2.0) * 3.0 * Math.pow(1.0 - nSAFT, 2.0) * (-1.0))
        / Math.pow(1.0 - getNSAFT(), 6.0);
    dgHSSAFTdNdN = -3.0 / 2.0 * Math.pow(1.0 - getNSAFT(), 2.0) / Math.pow(1.0 - getNSAFT(), 6.0)
        + (-3.0 / 2.0 * Math.pow(1.0 - getNSAFT(), 4.0)
            + 4.0 * Math.pow(1.0 - getNSAFT(), 3.0) * (3.0 - 3.0 / 2.0 * getNSAFT()))
            / Math.pow(1.0 - getNSAFT(), 8.0);

    setF1dispVolTerm(ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase()
        / getVolumeSAFT());
    F1dispSumTerm = calcF1dispSumTerm();
    F1dispI1 = calcF1dispI1();
    F1dispVolTermdV = -ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase()
        / Math.pow(getVolumeSAFT(), 2.0);
    F1dispVolTermdVdV = 2.0 * ThermodynamicConstantsInterface.avagadroNumber
        * getNumberOfMolesInPhase() / Math.pow(getVolumeSAFT(), 3.0);
    F1dispI1dN = calcF1dispI1dN();
    F1dispI1dNdN = calcF1dispI1dNdN();
    F1dispI1dm = calcF1dispI1dm();
    F1dispI1dV = F1dispI1dN * getDnSAFTdV();
    F1dispI1dVdV = F1dispI1dNdN * getDnSAFTdV() * getDnSAFTdV() + F1dispI1dN * dnSAFTdVdV; // F1dispI1dNdN*dnSAFTdVdV;
    dF1dispSumTermdT = calcdF1dispSumTermdT();
    dF1dispI1dT = calcdF1dispI1dT();
    setF2dispSumTerm(calcF2dispSumTerm());
    setF2dispI2(calcF2dispI2());
    F2dispI2dN = calcF2dispI2dN();
    F2dispI2dNdN = calcF2dispI2dNdN();
    F2dispI2dm = calcF2dispI2dm();
    F2dispI2dV = F2dispI2dN * getDnSAFTdV();
    F2dispI2dVdV = F2dispI2dNdN * getDnSAFTdV() * getDnSAFTdV() + F2dispI2dN * dnSAFTdVdV; // F2dispI2dNdN*dnSAFTdVdV;
    dF2dispSumTermdT = calcdF2dispSumTermdT();
    dF2dispI2dT = calcdF2dispI2dT();

    F2dispZHC = calcF2dispZHC();
    F2dispZHCdN = calcF2dispZHCdN();
    F2dispZHCdNdN = calcF2dispZHCdNdN();
    setF2dispZHCdm(calcF2dispZHCdm());
    F2dispZHCdV = F2dispZHCdN * getDnSAFTdV();
    F2dispZHCdVdV = F2dispZHCdNdN * getDnSAFTdV() * getDnSAFTdV() + F2dispZHCdN * dnSAFTdVdV;
    dF2dispZHCdT = calcdF2dispZHCdT();
  }

  /**
   * <p>
   * calcF2dispZHC.
   * </p>
   *
   * @return a double
   */
  public double calcF2dispZHC() {
    double temp = 1.0
        + getmSAFT() * (8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2.0))
            / Math.pow(1.0 - getNSAFT(), 4.0)
        + (1.0 - getmSAFT())
            * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2.0) + 12 * Math.pow(getNSAFT(), 3.0)
                - 2 * Math.pow(getNSAFT(), 4.0))
            / Math.pow((1.0 - getNSAFT()) * (2.0 - getNSAFT()), 2.0);
    return 1.0 / temp;
  }

  /**
   * <p>
   * calcF2dispZHCdm.
   * </p>
   *
   * @return a double
   */
  public double calcF2dispZHCdm() {
    double temp = -Math.pow(F2dispZHC, 2.0);
    return temp
        * ((8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2.0)) / Math.pow(1.0 - getNSAFT(), 4.0)
            - (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2.0) + 12 * Math.pow(getNSAFT(), 3.0)
                - 2 * Math.pow(getNSAFT(), 4.0))
                / Math.pow((1.0 - getNSAFT()) * (2.0 - getNSAFT()), 2.0));
  }

  /**
   * <p>
   * calcF2dispZHCdN.
   * </p>
   *
   * @return a double
   */
  public double calcF2dispZHCdN() {
    double temp0 = -Math.pow(F2dispZHC, 2.0);
    double temp1 = Math.pow((1.0 - getNSAFT()) * (2.0 - getNSAFT()), 2.0);
    double temp2 = 20.0 * getNSAFT() - 27.0 * Math.pow(getNSAFT(), 2.0)
        + 12.0 * Math.pow(getNSAFT(), 3.0) - 2.0 * Math.pow(getNSAFT(), 4.0);
    // ikke rett implementert
    return temp0 * (getmSAFT()
        * ((8.0 - 4.0 * getNSAFT()) * Math.pow(1.0 - getNSAFT(), 4.0)
            - 4.0 * Math.pow(1.0 - getNSAFT(), 3.0) * (-1.0)
                * (8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2.0)))
        / Math.pow(1.0 - getNSAFT(), 8.0)
        + (1.0 - getmSAFT()) * ((20.0 - (2.0 * 27.0) * getNSAFT()
            + (12.0 * 3.0) * Math.pow(getNSAFT(), 2.0) - 8.0 * Math.pow(getNSAFT(), 3.0)) * temp1
            - (2.0 * (2.0 - 3.0 * getNSAFT() + Math.pow(getNSAFT(), 2.0))
                * (-3.0 + 2.0 * getNSAFT())) * temp2)
            / Math.pow(temp1, 2.0));
  }

  /**
   * <p>
   * calcF2dispZHCdNdN.
   * </p>
   *
   * @return a double
   */
  public double calcF2dispZHCdNdN() {
    double temp0 = 2.0 * Math.pow(F2dispZHC, 3.0);
    double temp1 = Math.pow((1.0 - getNSAFT()) * (2.0 - getNSAFT()), 2.0);
    double temp11 = Math.pow((1.0 - getNSAFT()) * (2.0 - getNSAFT()), 3.0);
    double temp2 = 20.0 * getNSAFT() - 27.0 * Math.pow(getNSAFT(), 2.0)
        + 12.0 * Math.pow(getNSAFT(), 3.0) - 2.0 * Math.pow(getNSAFT(), 4.0);

    double temp1der =
        2.0 * (2.0 - 3.0 * getNSAFT() + Math.pow(getNSAFT(), 2.0)) * (-3.0 + 2.0 * getNSAFT());
    double temp11der = 3.0 * Math.pow(2.0 - 3.0 * getNSAFT() + Math.pow(getNSAFT(), 2.0), 2.0)
        * (-3.0 + 2.0 * getNSAFT());
    // ikke rett implementert
    double temp3 = (getmSAFT()
        * ((8.0 - 4.0 * getNSAFT()) * Math.pow(1.0 - getNSAFT(), 4.0)
            - 4.0 * Math.pow(1.0 - getNSAFT(), 3.0) * (-1.0)
                * (8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2.0)))
        / Math.pow(1.0 - getNSAFT(), 8.0)
        + (1.0 - getmSAFT()) * ((20.0 - (2.0 * 27.0) * getNSAFT()
            + (12.0 * 3.0) * Math.pow(getNSAFT(), 2.0) - 8.0 * Math.pow(getNSAFT(), 3.0)) * temp1
            - (2.0 * (2.0 - 3.0 * getNSAFT() + Math.pow(getNSAFT(), 2.0))
                * (-3.0 + 2.0 * getNSAFT())) * temp2)
            / Math.pow(temp1, 2.0));

    double temp4 = -Math.pow(F2dispZHC, 2.0);
    double dZdndn = getmSAFT()
        * ((-4.0 * Math.pow(1.0 - getNSAFT(), 4.0)
            - 4.0 * Math.pow(1.0 - getNSAFT(), 3.0) * (-1.0) * (8.0 - 4.0 * getNSAFT()))
            / Math.pow(1.0 - getNSAFT(), 8.0)
            + ((32.0 - 16.0 * getNSAFT()) * Math.pow(1.0 - getNSAFT(), 5.0)
                - 5.0 * Math.pow(1.0 - getNSAFT(), 4.0) * (-1.0)
                    * (32.0 * getNSAFT() - 8.0 * Math.pow(getNSAFT(), 2.0)))
                / Math.pow(1.0 - getNSAFT(), 10.0))
        + (1.0 - getmSAFT())
            * (((-54.0 + 72.0 * getNSAFT() - 24.0 * Math.pow(getNSAFT(), 2.0)) * temp1
                - temp1der * (20.0 - 54.0 * getNSAFT() + 36.0 * Math.pow(getNSAFT(), 2.0)
                    - 8.0 * Math.pow(getNSAFT(), 3.0)))
                / Math.pow(temp1, 2.0)
                - ((-40.0 * Math.pow(getNSAFT(), 4.0) + 240.0 * Math.pow(getNSAFT(), 3.0)
                    - 3.0 * 180.0 * Math.pow(getNSAFT(), 2.0) + 242.0 * 2.0 * getNSAFT() - 120.0)
                    * temp11
                    - temp11der * (-8.0 * Math.pow(getNSAFT(), 5.0)
                        + 60.0 * Math.pow(getNSAFT(), 4.0) - 180.0 * Math.pow(getNSAFT(), 3.0)
                        + 242.0 * Math.pow(getNSAFT(), 2.0) - 120.0 * getNSAFT()))
                    / Math.pow(temp11, 2.0));

    return temp0 * Math.pow(temp3, 2.0) + temp4 * dZdndn;
  }

  /**
   * <p>
   * calcmSAFT.
   * </p>
   *
   * @return a double
   */
  public double calcmSAFT() {
    double temp2 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp2 += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi()
          / getNumberOfMolesInPhase();
    }

    return temp2;
  }

  /**
   * <p>
   * calcF1dispSumTerm.
   * </p>
   *
   * @return a double
   */
  public double calcF1dispSumTerm() {
    double temp1 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        temp1 +=
            getComponent(i).getNumberOfMolesInPhase() * getComponent(j).getNumberOfMolesInPhase()
                * getComponent(i).getmSAFTi() * getComponent(j).getmSAFTi()
                * Math.sqrt(getComponent(i).getEpsikSAFT() / temperature
                    * getComponent(j).getEpsikSAFT() / temperature)
                * (1.0 - mixRule.getBinaryInteractionParameter(i, j)) * Math.pow(
                    0.5 * (getComponent(i).getSigmaSAFTi() + getComponent(j).getSigmaSAFTi()), 3.0);
      }
    }
    return temp1 / Math.pow(getNumberOfMolesInPhase(), 2.0);
  }

  /**
   * <p>calcdF1dispSumTermdT.</p>
   *
   * @return a double
   */
  public double calcdF1dispSumTermdT() {
    double temp1 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        temp1 += getComponent(i).getNumberOfMolesInPhase()
            * getComponent(j).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi()
            * getComponent(j).getmSAFTi()
            * Math.sqrt(getComponent(i).getEpsikSAFT() / temperature
                * getComponent(j).getEpsikSAFT() / temperature)
            * (1.0 - mixRule.getBinaryInteractionParameter(i, j))
            * Math.pow(0.5 * (getComponent(i).getSigmaSAFTi() + getComponent(j).getSigmaSAFTi()), 3.0)
            * (-1.0 / temperature);
      }
    }
    return temp1 / Math.pow(getNumberOfMolesInPhase(), 2.0);
  }

  /**
   * <p>
   * calcF2dispSumTerm.
   * </p>
   *
   * @return a double
   */
  public double calcF2dispSumTerm() {
    double temp1 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        temp1 += getComponent(i).getNumberOfMolesInPhase()
            * getComponent(j).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi()
            * getComponent(j).getmSAFTi() * getComponent(i).getEpsikSAFT() / temperature
            * getComponent(j).getEpsikSAFT() / temperature
            * Math.pow((1.0 - mixRule.getBinaryInteractionParameter(i, j)), 2.0) * Math.pow(
                0.5 * (getComponent(i).getSigmaSAFTi() + getComponent(j).getSigmaSAFTi()), 3.0);
      }
    }
    return temp1 / Math.pow(getNumberOfMolesInPhase(), 2.0);
  }

  /**
   * <p>calcdF2dispSumTermdT.</p>
   *
   * @return a double
   */
  public double calcdF2dispSumTermdT() {
    double temp1 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        temp1 += getComponent(i).getNumberOfMolesInPhase()
            * getComponent(j).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi()
            * getComponent(j).getmSAFTi()
            * Math.sqrt(getComponent(i).getEpsikSAFT() / temperature
                * getComponent(j).getEpsikSAFT() / temperature)
            * (1.0 - mixRule.getBinaryInteractionParameter(i, j))
            * Math.pow(0.5 * (getComponent(i).getSigmaSAFTi() + getComponent(j).getSigmaSAFTi()), 3.0)
            * (-1.0 / temperature);
      }
    }
    return temp1 / Math.pow(getNumberOfMolesInPhase(), 2.0);
  }

  /**
   * <p>
   * calcF1dispI1dN.
   * </p>
   *
   * @return a double
   */
  public double calcF1dispI1dN() {
    double temp1 = 0.0;
    for (int i = 1; i < 7; i++) {
      temp1 += i * getaSAFT(i, getmSAFT(), aConstSAFT) * Math.pow(getNSAFT(), i - 1.0);
    }
    return temp1;
  }

  /**
   * <p>calcdF2dispZHCdT.</p>
   *
   * @return a double
   */
  public double calcdF2dispZHCdT() {
    double term1 = getmSAFT()
        * ((8 - 4 * getNSAFT()) * dNSAFTdT * Math.pow(1 - getNSAFT(), 4)
            + 4 * Math.pow(1 - getNSAFT(), 3) * dNSAFTdT * (8 * getNSAFT() - 2 * Math.pow(getNSAFT(), 2)))
        / Math.pow(1 - getNSAFT(), 8);
    double term2 = (1.0 - getmSAFT())
        * ((20 - 54 * getNSAFT() + 36 * Math.pow(getNSAFT(), 2)) * dNSAFTdT)
        / Math.pow((1 - getNSAFT()) * (2 - getNSAFT()), 2);
    double term3 = (1.0 - getmSAFT())
        * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2) + 12 * Math.pow(getNSAFT(), 3)
            - 2 * Math.pow(getNSAFT(), 4))
        * (-2 * dNSAFTdT * (2 - getNSAFT()) - 2 * dNSAFTdT * (1 - getNSAFT()))
        / Math.pow((1 - getNSAFT()) * (2 - getNSAFT()), 3);
    return -Math.pow(F2dispZHC, 2.0) * (term1 + term2 + term3);
  }

  /**
   * <p>
   * calcF1dispI1dNdN.
   * </p>
   *
   * @return a double
   */
  public double calcF1dispI1dNdN() {
    double temp1 = 0.0;
    for (int i = 2; i < 7; i++) {
      temp1 += (i - 1.0) * i * getaSAFT(i, getmSAFT(), aConstSAFT) * Math.pow(getNSAFT(), i - 2.0);
    }
    return temp1;
  }

  /**
   * <p>
   * calcF1dispI1dm.
   * </p>
   *
   * @return a double
   */
  public double calcF1dispI1dm() {
    double temp1 = 0.0;
    for (int i = 0; i < 7; i++) {
      temp1 += getaSAFTdm(i, getmSAFT(), aConstSAFT) * Math.pow(getNSAFT(), i);
    }
    return temp1;
  }

  /**
   * <p>
   * calcF2dispI2dN.
   * </p>
   *
   * @return a double
   */
  public double calcF2dispI2dN() {
    double temp1 = 0.0;
    for (int i = 1; i < 7; i++) {
      temp1 += i * getaSAFT(i, getmSAFT(), bConstSAFT) * Math.pow(getNSAFT(), i - 1.0);
    }
    return temp1;
  }

  /**
   * <p>
   * calcF2dispI2dNdN.
   * </p>
   *
   * @return a double
   */
  public double calcF2dispI2dNdN() {
    double temp1 = 0.0;
    for (int i = 2; i < 7; i++) {
      temp1 += (i - 1.0) * i * getaSAFT(i, getmSAFT(), bConstSAFT) * Math.pow(getNSAFT(), i - 2.0);
    }
    return temp1;
  }

  /**
   * <p>
   * calcF2dispI2dm.
   * </p>
   *
   * @return a double
   */
  public double calcF2dispI2dm() {
    double temp1 = 0.0;
    for (int i = 0; i < 7; i++) {
      temp1 += getaSAFTdm(i, getmSAFT(), bConstSAFT) * Math.pow(getNSAFT(), i);
    }
    return temp1;
  }

  /**
   * <p>
   * calcF1dispI1.
   * </p>
   *
   * @return a double
   */
  public double calcF1dispI1() {
    double temp1 = 0.0;
    for (int i = 0; i < 7; i++) {
      temp1 += getaSAFT(i, getmSAFT(), aConstSAFT) * Math.pow(getNSAFT(), i);
    }
    return temp1;
  }

  /**
   * <p>calcdF1dispI1dT.</p>
   *
   * @return a double
   */
  public double calcdF1dispI1dT() {
    double temp1 = 0.0;
    for (int i = 0; i < 7; i++) {
      temp1 += getaSAFT(i, getmSAFT(), aConstSAFT) * i * Math.pow(getNSAFT(), i - 1) * dNSAFTdT;
    }
    return temp1;
  }

  /**
   * <p>
   * calcF2dispI2.
   * </p>
   *
   * @return a double
   */
  public double calcF2dispI2() {
    double temp1 = 0.0;
    for (int i = 0; i < 7; i++) {
      temp1 += getaSAFT(i, getmSAFT(), bConstSAFT) * Math.pow(getNSAFT(), i);
    }
    return temp1;
  }

  /**
   * <p>calcdF2dispI2dT.</p>
   *
   * @return a double
   */
  public double calcdF2dispI2dT() {
    double temp1 = 0.0;
    for (int i = 0; i < 7; i++) {
      temp1 += getaSAFT(i, getmSAFT(), bConstSAFT) * i * Math.pow(getNSAFT(), i - 1) * dNSAFTdT;
    }
    return temp1;
  }

  /**
   * <p>
   * getaSAFT.
   * </p>
   *
   * @param i a int
   * @param m a double
   * @param ab an array of type double
   * @return a double
   */
  public double getaSAFT(int i, double m, double[][] ab) {
    return ab[0][i] + (m - 1.0) / m * ab[1][i] + (m - 1.0) / m * (m - 2.0) / m * ab[2][i];
  }

  /**
   * <p>
   * getaSAFTdm.
   * </p>
   *
   * @param i a int
   * @param m a double
   * @param ab an array of type double
   * @return a double
   */
  public double getaSAFTdm(int i, double m, double[][] ab) {
    return (m - (m - 1.0)) / (m * m) * ab[1][i]
        + ((2.0 * m - 3.0) * m * m - 2 * m * (m * m - 3 * m + 2)) / Math.pow(m, 4.0) * ab[2][i];
  }

  /**
   * <p>
   * calcmdSAFT.
   * </p>
   *
   * @return a double
   */
  public double calcmdSAFT() {
    double temp2 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp2 += getComponent(i).getNumberOfMolesInPhase() / getNumberOfMolesInPhase()
          * getComponent(i).getmSAFTi()
          * Math.pow(((ComponentPCSAFT) getComponent(i)).getdSAFTi(), 3.0);
    }

    return temp2;
  }

  /**
   * <p>
   * calcmmin1SAFT.
   * </p>
   *
   * @return a double
   */
  public double calcmmin1SAFT() {
    double temp2 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp2 += getComponent(i).getNumberOfMolesInPhase() / getNumberOfMolesInPhase()
          * (getComponent(i).getmSAFTi() - 1.0);
    }

    return temp2;
  }

  /**
   * <p>
   * calcdmeanSAFT.
   * </p>
   *
   * @return a double
   */
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

  /**
   * <p>
   * calcdSAFT.
   * </p>
   *
   * @return a double
   */
  public double calcdSAFT() {
    double temp = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi()
          * Math.pow(((ComponentPCSAFT) getComponent(i)).getdSAFTi(), 3.0);
    }
    // System.out.println("d saft calc " + temp/getNumberOfMolesInPhase());
    return temp / getNumberOfMolesInPhase();
  }

  /**
   * <p>getdDSAFTdT.</p>
   *
   * @return a double
   */
  public double getdDSAFTdT() {
    double temp = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi()
          / getNumberOfMolesInPhase() * 3.0
          * Math.pow(((ComponentPCSAFT) getComponent(i)).getdSAFTi(), 2.0)
          * (-1.08 / Math.pow(temperature, 2.0))
          * Math.pow(getComponent(i).getSigmaSAFTi(), 3.0)
          * Math.pow(1.0 - 0.12 * Math.exp(-3.0 * getComponent(i).getEpsikSAFT() / temperature), 2.0)
          * getComponent(i).getEpsikSAFT()
          * Math.exp(-3.0 * getComponent(i).getEpsikSAFT() / temperature);
    }
    return temp;
  }

  /**
   * <p>
   * Getter for the field <code>nSAFT</code>.
   * </p>
   *
   * @return a double
   */
  public double getNSAFT() {
    return nSAFT;
  }

  /**
   * <p>
   * Setter for the field <code>nSAFT</code>.
   * </p>
   *
   * @param nSAFT a double
   */
  public void setNSAFT(double nSAFT) {
    this.nSAFT = nSAFT;
  }

  /**
   * <p>
   * Getter for the field <code>dSAFT</code>.
   * </p>
   *
   * @return a double
   */
  public double getDSAFT() {
    return dSAFT;
  }

  /**
   * <p>
   * Setter for the field <code>dSAFT</code>.
   * </p>
   *
   * @param dSAFT a double
   */
  public void setDSAFT(double dSAFT) {
    this.dSAFT = dSAFT;
  }

  /**
   * <p>
   * Getter for the field <code>ghsSAFT</code>.
   * </p>
   *
   * @return a double
   */
  public double getGhsSAFT() {
    return ghsSAFT;
  }

  /**
   * <p>
   * Setter for the field <code>ghsSAFT</code>.
   * </p>
   *
   * @param ghsSAFT a double
   */
  public void setGhsSAFT(double ghsSAFT) {
    this.ghsSAFT = ghsSAFT;
  }

  /**
   * <p>
   * F_HC_SAFT.
   * </p>
   *
   * @return a double
   */
  public double F_HC_SAFT() {
    return getNumberOfMolesInPhase()
        * (getmSAFT() * getAHSSAFT() - getMmin1SAFT() * Math.log(getGhsSAFT()));
    // (ThermodynamicConstantsInterface.R*temperature);
  }

  /**
   * <p>dF_HC_SAFTdT.</p>
   *
   * @return a double
   */
  public double dF_HC_SAFTdT() {
    return getNumberOfMolesInPhase()
        * (getmSAFT() * daHSSAFTdN * dNSAFTdT
            - getMmin1SAFT() * 1.0 / getGhsSAFT() * getDgHSSAFTdN() * dNSAFTdT);
  }

  /**
   * <p>
   * dF_HC_SAFTdV.
   * </p>
   *
   * @return a double
   */
  public double dF_HC_SAFTdV() {
    return getNumberOfMolesInPhase() * (getmSAFT() * daHSSAFTdN * getDnSAFTdV()
        - getMmin1SAFT() * 1.0 / getGhsSAFT() * getDgHSSAFTdN() * getDnSAFTdV());
    // (ThermodynamicConstantsInterface.R*temperature);
  }

  /**
   * <p>
   * dF_HC_SAFTdVdV.
   * </p>
   *
   * @return a double
   */
  public double dF_HC_SAFTdVdV() {
    return getNumberOfMolesInPhase() * (getmSAFT() * daHSSAFTdNdN * getDnSAFTdV() * getDnSAFTdV()
        + getmSAFT() * daHSSAFTdN * dnSAFTdVdV
        + getMmin1SAFT() * Math.pow(getGhsSAFT(), -2.0) * Math.pow(getDgHSSAFTdN(), 2.0)
            * getDnSAFTdV()
        - getMmin1SAFT() * Math.pow(getGhsSAFT(), -1.0) * dgHSSAFTdNdN * dnSAFTdV * dnSAFTdV
        - getMmin1SAFT() * 1.0 / getGhsSAFT() * getDgHSSAFTdN() * dnSAFTdVdV);
    // (ThermodynamicConstantsInterface.R*temperature);
  }

  /**
   * <p>
   * dF_HC_SAFTdVdVdV.
   * </p>
   *
   * @return a double
   */
  public double dF_HC_SAFTdVdVdV() {
    return 0.0;
  }

  /**
   * <p>
   * F_DISP1_SAFT.
   * </p>
   *
   * @return a double
   */
  public double F_DISP1_SAFT() {
    return getNumberOfMolesInPhase() * (-2.0 * ThermodynamicConstantsInterface.pi
        * getF1dispVolTerm() * getF1dispSumTerm() * getF1dispI1()); // (ThermodynamicConstantsInterface.R*temperature);
  }

  /**
   * <p>dF_DISP1_SAFTdT.</p>
   *
   * @return a double
   */
  public double dF_DISP1_SAFTdT() {
    return getNumberOfMolesInPhase() * (-2.0 * ThermodynamicConstantsInterface.pi)
        * (dF1dispVolTermdT * getF1dispSumTerm() * getF1dispI1()
            + dF1dispSumTermdT * getF1dispVolTerm() * getF1dispI1()
            + dF1dispI1dT * getF1dispVolTerm() * getF1dispSumTerm());
  }

  /**
   * <p>
   * dF_DISP1_SAFTdV.
   * </p>
   *
   * @return a double
   */
  public double dF_DISP1_SAFTdV() {
    return getNumberOfMolesInPhase() * (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV
        * getF1dispSumTerm() * getF1dispI1()
        - 2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTerm * getF1dispSumTerm()
            * F1dispI1dV); // (ThermodynamicConstantsInterface.R*temperature);
  }

  /**
   * <p>
   * dF_DISP1_SAFTdVdV.
   * </p>
   *
   * @return a double
   */
  public double dF_DISP1_SAFTdVdV() {
    return getNumberOfMolesInPhase() * ((-2.0 * ThermodynamicConstantsInterface.pi
        * F1dispVolTermdVdV * getF1dispSumTerm() * getF1dispI1())
        + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV * getF1dispSumTerm()
            * F1dispI1dV)
        + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV * getF1dispSumTerm()
            * F1dispI1dV)
        + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTerm * getF1dispSumTerm()
            * F1dispI1dVdV));
  }

  /**
   * <p>
   * F_DISP2_SAFT.
   * </p>
   *
   * @return a double
   */
  public double F_DISP2_SAFT() {
    return getNumberOfMolesInPhase() * (-ThermodynamicConstantsInterface.pi * getmSAFT()
        * getF1dispVolTerm() * getF2dispSumTerm() * getF2dispI2() * getF2dispZHC());
    // (ThermodynamicConstantsInterface.R*temperature);
  }

  /**
   * <p>dF_DISP2_SAFTdT.</p>
   *
   * @return a double
   */
  public double dF_DISP2_SAFTdT() {
    return getNumberOfMolesInPhase() * (-1.0 * ThermodynamicConstantsInterface.pi * getmSAFT())
        * getF1dispVolTerm()
        * (dF2dispSumTermdT * getF2dispI2() * getF2dispZHC()
            + dF2dispI2dT * getF2dispSumTerm() * getF2dispZHC()
            + dF2dispZHCdT * getF2dispSumTerm() * getF2dispI2());
  }

  /**
   * <p>
   * dF_DISP2_SAFTdV.
   * </p>
   *
   * @return a double
   */
  public double dF_DISP2_SAFTdV() {
    return getNumberOfMolesInPhase() * (-ThermodynamicConstantsInterface.pi * getmSAFT()
        * F1dispVolTermdV * getF2dispSumTerm() * getF2dispI2() * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * F2dispI2dV * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdV); // (ThermodynamicConstantsInterface.R*temperature);
  }

  /**
   * <p>
   * dF_DISP2_SAFTdVdV.
   * </p>
   *
   * @return a double
   */
  public double dF_DISP2_SAFTdVdV() {
    return getNumberOfMolesInPhase() * ((-ThermodynamicConstantsInterface.pi * getmSAFT()
        * F1dispVolTermdVdV * getF2dispSumTerm() * getF2dispI2() * getF2dispZHC())
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * getF2dispZHC() * F2dispI2dV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdV)
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * F2dispI2dV * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * F2dispI2dVdV * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * F2dispI2dV * F2dispZHCdV
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * F2dispI2dV * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdVdV));
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

    return (useHS * dF_HC_SAFTdV() + useDISP1 * dF_DISP1_SAFTdV() + useDISP2 * dF_DISP2_SAFTdV())
        * 1.0e-5;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    return (useHS * dF_HC_SAFTdVdV() + useDISP1 * dF_DISP1_SAFTdVdV()
        + useDISP2 * dF_DISP2_SAFTdVdV()) * 1.0e-10;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdT() {
    return useHS * dF_HC_SAFTdT() + useDISP1 * dF_DISP1_SAFTdT() + useDISP2 * dF_DISP2_SAFTdT();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdV() {
    double oldT = temperature;
    double h = oldT * 1e-6;
    if (h == 0) {
      h = 1e-6;
    }
    PhasePCSAFT plus = this.clone();
    plus.setTemperature(oldT + h);
    plus.volInit();
    double dFdVplus = plus.dFdV();
    PhasePCSAFT minus = this.clone();
    minus.setTemperature(oldT - h);
    minus.volInit();
    double dFdVminus = minus.dFdV();
    return (dFdVplus - dFdVminus) / (2.0 * h);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdT() {
    double oldT = temperature;
    double h = oldT * 1e-6;
    if (h == 0) {
      h = 1e-6;
    }
    PhasePCSAFT plus = this.clone();
    plus.setTemperature(oldT + h);
    plus.volInit();
    double Fplus = plus.getF();
    PhasePCSAFT base = this.clone();
    base.setTemperature(oldT);
    base.volInit();
    double F0 = base.getF();
    PhasePCSAFT minus = this.clone();
    minus.setTemperature(oldT - h);
    minus.volInit();
    double Fminus = minus.getF();
    return (Fplus - 2.0 * F0 + Fminus) / (h * h);
  }

  /**
   * <p>
   * Getter for the field <code>mdSAFT</code>.
   * </p>
   *
   * @return a double
   */
  public double getmdSAFT() {
    return mdSAFT;
  }

  /**
   * <p>
   * Setter for the field <code>mdSAFT</code>.
   * </p>
   *
   * @param mdSAFT a double
   */
  public void setmdSAFT(double mdSAFT) {
    this.mdSAFT = mdSAFT;
  }

  /**
   * <p>
   * Getter for the field <code>mSAFT</code>.
   * </p>
   *
   * @return a double
   */
  public double getmSAFT() {
    return mSAFT;
  }

  /**
   * <p>
   * Setter for the field <code>mSAFT</code>.
   * </p>
   *
   * @param mSAFT a double
   */
  public void setmSAFT(double mSAFT) {
    this.mSAFT = mSAFT;
  }

  /**
   * <p>
   * Getter for the field <code>aHSSAFT</code>.
   * </p>
   *
   * @return a double
   */
  public double getAHSSAFT() {
    return aHSSAFT;
  }

  /**
   * <p>
   * Setter for the field <code>aHSSAFT</code>.
   * </p>
   *
   * @param aHSSAFT a double
   */
  public void setAHSSAFT(double aHSSAFT) {
    this.aHSSAFT = aHSSAFT;
  }

  /**
   * <p>
   * Getter for the field <code>mmin1SAFT</code>.
   * </p>
   *
   * @return a double
   */
  public double getMmin1SAFT() {
    return mmin1SAFT;
  }

  /**
   * <p>
   * Setter for the field <code>mmin1SAFT</code>.
   * </p>
   *
   * @param mmin1SAFT a double
   */
  public void setMmin1SAFT(double mmin1SAFT) {
    this.mmin1SAFT = mmin1SAFT;
  }

  /**
   * <p>
   * Getter for the field <code>volumeSAFT</code>.
   * </p>
   *
   * @return a double
   */
  public double getVolumeSAFT() {
    return volumeSAFT;
  }

  /**
   * <p>
   * Setter for the field <code>volumeSAFT</code>.
   * </p>
   *
   * @param volumeSAFT a double
   */
  public void setVolumeSAFT(double volumeSAFT) {
    this.volumeSAFT = volumeSAFT;
  }

  /**
   * <p>
   * Getter for the field <code>dgHSSAFTdN</code>.
   * </p>
   *
   * @return a double
   */
  public double getDgHSSAFTdN() {
    return dgHSSAFTdN;
  }

  /**
   * <p>
   * Setter for the field <code>dgHSSAFTdN</code>.
   * </p>
   *
   * @param dgHSSAFTdN a double
   */
  public void setDgHSSAFTdN(double dgHSSAFTdN) {
    this.dgHSSAFTdN = dgHSSAFTdN;
  }

  /**
   * <p>
   * Getter for the field <code>dnSAFTdV</code>.
   * </p>
   *
   * @return a double
   */
  public double getDnSAFTdV() {
    return dnSAFTdV;
  }

  /**
   * <p>
   * Setter for the field <code>dnSAFTdV</code>.
   * </p>
   *
   * @param dnSAFTdV a double
   */
  public void setDnSAFTdV(double dnSAFTdV) {
    this.dnSAFTdV = dnSAFTdV;
  }

  /**
   * <p>
   * getF1dispVolTerm.
   * </p>
   *
   * @return a double
   */
  public double getF1dispVolTerm() {
    return F1dispVolTerm;
  }

  /**
   * <p>
   * setF1dispVolTerm.
   * </p>
   *
   * @param F1dispVolTerm a double
   */
  public void setF1dispVolTerm(double F1dispVolTerm) {
    this.F1dispVolTerm = F1dispVolTerm;
  }

  /**
   * <p>
   * getF1dispSumTerm.
   * </p>
   *
   * @return a double
   */
  public double getF1dispSumTerm() {
    return F1dispSumTerm;
  }

  /**
   * <p>
   * getF1dispI1.
   * </p>
   *
   * @return a double
   */
  public double getF1dispI1() {
    return F1dispI1;
  }

  /**
   * <p>
   * getF2dispI2.
   * </p>
   *
   * @return a double
   */
  public double getF2dispI2() {
    return F2dispI2;
  }

  /**
   * <p>
   * setF2dispI2.
   * </p>
   *
   * @param F2dispI2 a double
   */
  public void setF2dispI2(double F2dispI2) {
    this.F2dispI2 = F2dispI2;
  }

  /**
   * <p>
   * getF2dispZHC.
   * </p>
   *
   * @return a double
   */
  public double getF2dispZHC() {
    return F2dispZHC;
  }

  /**
   * <p>
   * setF2dispZHC.
   * </p>
   *
   * @param F2dispZHC a double
   */
  public void setF2dispZHC(double F2dispZHC) {
    this.F2dispZHC = F2dispZHC;
  }

  /**
   * <p>
   * getF2dispZHCdN.
   * </p>
   *
   * @return a double
   */
  public double getF2dispZHCdN() {
    return F2dispZHCdN;
  }

  /**
   * <p>
   * getF2dispZHCdm.
   * </p>
   *
   * @return a double
   */
  public double getF2dispZHCdm() {
    return F2dispZHCdm;
  }

  /**
   * <p>
   * molarVolume22.
   * </p>
   *
   * @param pressure a double
   * @param temperature a double
   * @param A a double
   * @param B a double
   * @param phaseNum a int
   * @return a double
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public double molarVolume22(double pressure, double temperature, double A, double B, int phaseNum)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double volume =
        phaseNum == 0 ? getB() / (2.0 / (2.0 + temperature / getPseudoCriticalTemperature()))
            : (numberOfMolesInPhase * temperature * R) / pressure;
    setMolarVolume(volume / numberOfMolesInPhase);
    double oldMolarVolume = 0;
    int iterations = 0;
    double h = 0;
    double dh = 0.0;
    double d1 = 0.0;
    do {
      iterations++;
      this.volInit();
      oldMolarVolume = getMolarVolume();
      h = pressure - calcPressure();
      dh = -calcPressuredV();
      d1 = -h / dh;
      double newVolume = getMolarVolume() + 0.9 * d1 / numberOfMolesInPhase;
      if (newVolume > 1e-100) {
        setMolarVolume(newVolume);
      } else {
        setMolarVolume(oldMolarVolume / 10.0);
      }
      Z = pressure * getMolarVolume() / (R * temperature);
    } while (Math.abs((oldMolarVolume - getMolarVolume()) / oldMolarVolume) > 1.0e-10
        && iterations < 200);
    // System.out.println("Z " + Z + " iterations " + iterations);
    return getMolarVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double BonV =
        pt == PhaseType.LIQUID ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
            : pressure * getB() / (numberOfMolesInPhase * temperature * R);
    // double BonV = phase== 0 ? 0.99:1e-5;
    if (BonV < 0) {
      BonV = 1.0e-6;
    }
    if (BonV > 1.0) {
      BonV = 1.0 - 1.0e-6;
    }
    double BonVold = BonV;
    double Btemp = 0;
    double h = 0;
    double dh = 0;
    // double gvvv = 0, fvvv = 0, dhh = 0, d2 = 0;
    double d1 = 0;
    Btemp = getB();
    if (Btemp <= 0) {
      logger.info("b negative in volume calc");
    }
    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    int iterations = 0;
    int maxIterations = 2000;
    do {
      iterations++;
      this.volInit();

      // System.out.println("saft volume " + getVolumeSAFT());
      BonVold = BonV;
      h = BonV - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      dh = 1.0 + Btemp / Math.pow(BonV, 2.0) * (Btemp / numberOfMolesInPhase * dFdVdV());
      // dhh = -2.0 * Btemp / Math.pow(BonV, 3.0) * (Btemp / numberOfMolesInPhase * dFdVdV()) -
      // Math.pow(Btemp, 2.0) / Math.pow(BonV, 4.0)* (Btemp / numberOfMolesInPhase * dFdVdVdV());

      d1 = -h / dh;
      // d2 = -dh / dhh;
      BonV += d1;

      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
      // System.out.println("Z " + Z);
    } while (Math.abs((BonV - BonVold) / BonV) > 1.0e-10 && iterations < maxIterations);
    // System.out.println("error BonV " + Math.abs((BonV-BonVold)/BonV));
    // System.out.println("iterations " + iterations);
    if (BonV < 0) {
      BonV = pressure * getB() / (numberOfMolesInPhase * temperature * R);
      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
    }

    if (iterations >= maxIterations) {
      throw new neqsim.util.exception.TooManyIterationsException(this, "molarVolume",
          maxIterations);
    }
    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Molar volume");
    }

    // if(pt==0) System.out.println("density " + getDensity()); //"BonV: " +
    // BonV + " "+" itert: " + iterations +" " + " phase " + pt+ " " + h + "
    // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
    // + fVV());

    return getMolarVolume();
  }

  /**
   * <p>
   * Getter for the field <code>dmeanSAFT</code>.
   * </p>
   *
   * @return a double
   */
  public double getDmeanSAFT() {
    return dmeanSAFT;
  }

  /**
   * <p>
   * Setter for the field <code>dmeanSAFT</code>.
   * </p>
   *
   * @param dmeanSAFT a double
   */
  public void setDmeanSAFT(double dmeanSAFT) {
    this.dmeanSAFT = dmeanSAFT;
  }

  /**
   * <p>
   * Getter for the field <code>nmSAFT</code>.
   * </p>
   *
   * @return a double
   */
  public double getNmSAFT() {
    return nmSAFT;
  }

  /**
   * <p>
   * Setter for the field <code>nmSAFT</code>.
   * </p>
   *
   * @param nmSAFT a double
   */
  public void setNmSAFT(double nmSAFT) {
    this.nmSAFT = nmSAFT;
  }

  /**
   * <p>
   * getF2dispSumTerm.
   * </p>
   *
   * @return a double
   */
  public double getF2dispSumTerm() {
    return F2dispSumTerm;
  }

  /**
   * <p>
   * setF2dispSumTerm.
   * </p>
   *
   * @param F2dispSumTerm a double
   */
  public void setF2dispSumTerm(double F2dispSumTerm) {
    this.F2dispSumTerm = F2dispSumTerm;
  }

  /**
   * <p>
   * setF2dispZHCdm.
   * </p>
   *
   * @param F2dispZHCdm a double
   */
  public void setF2dispZHCdm(double F2dispZHCdm) {
    this.F2dispZHCdm = F2dispZHCdm;
  }
}
