package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentPCSAFT;

/**
 * <p>
 * PhasePCSAFTRahmat class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhasePCSAFTRahmat extends PhasePCSAFT {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhasePCSAFTRahmat.class);

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
   * <p>
   * Constructor for PhasePCSAFTRahmat.
   * </p>
   */
  public PhasePCSAFTRahmat() {}

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
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    // missing?
    // if (initType > 0) {
    for (int i = 0; i < numberOfComponents; i++) {
      componentArray[i].Finit(this, temperature, pressure, totalNumberOfMoles, beta,
          numberOfComponents, initType);
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
    setNSAFT(1.0 * ThermodynamicConstantsInterface.pi / 6.0
        * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase() / volumeSAFT
        * getDSAFT());
    dnSAFTdV = -1.0 * ThermodynamicConstantsInterface.pi / 6.0
        * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase()
        / Math.pow(volumeSAFT, 2.0) * getDSAFT();
    dnSAFTdVdV = 2.0 * ThermodynamicConstantsInterface.pi / 6.0
        * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase()
        / Math.pow(volumeSAFT, 3.0) * getDSAFT();
    // System.out.println("N SAFT " + getNSAFT());
    dnSAFTdVdVdV = -6.0 * ThermodynamicConstantsInterface.pi / 6.0
        * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase()
        / Math.pow(volumeSAFT, 4.0) * getDSAFT();

    // added by rahmat
    dNSAFTdT = 1.0 * ThermodynamicConstantsInterface.pi / 6.0
        * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase() / volumeSAFT
        * getdDSAFTdT();

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
    daHSSAFTdNdNdN = -36 / Math.pow((1 - getNSAFT()), 3)
        + (18.0 * (4.0 - 6.0 * getNSAFT())) / Math.pow((1 - getNSAFT()), 4.0)
        + (24.0 * (4.0 * getNSAFT() - 3.0 * Math.pow(getNSAFT(), 2.0)))
            / Math.pow((1 - getNSAFT()), 5.0);

    dgHSSAFTdN = (-0.5 * Math.pow(1.0 - getNSAFT(), 3.0)
        - (1.0 - getNSAFT() / 2.0) * 3.0 * Math.pow(1.0 - nSAFT, 2.0) * (-1.0))
        / Math.pow(1.0 - getNSAFT(), 6.0);
    dgHSSAFTdNdN = -3.0 / 2.0 * Math.pow(1.0 - getNSAFT(), 2.0) / Math.pow(1.0 - getNSAFT(), 6.0)
        + (-3.0 / 2.0 * Math.pow(1.0 - getNSAFT(), 4.0)
            + 4.0 * Math.pow(1.0 - getNSAFT(), 3.0) * (3.0 - 3.0 / 2.0 * getNSAFT()))
            / Math.pow(1.0 - getNSAFT(), 8.0);
    dgHSSAFTdNdNdN = -6.0 / Math.pow(1.0 - getNSAFT(), 5.0)
        - (12.0 * (3.0 - 1.5 * getNSAFT())) / Math.pow(1.0 - getNSAFT(), 6.0)
        + (8 * (-1.5 * Math.pow(1.0 - getNSAFT(), 4)
            + 4.0 * Math.pow(1.0 - getNSAFT(), 3) * (3.0 - 1.5 * getNSAFT())))
            / Math.pow(1.0 - getNSAFT(), 9);

    setF1dispVolTerm(ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase()
        / getVolumeSAFT());
    F1dispSumTerm = calcF1dispSumTerm();
    F1dispI1 = calcF1dispI1();
    F1dispVolTermdV = -ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase()
        / Math.pow(getVolumeSAFT(), 2.0);
    F1dispVolTermdVdV = 2.0 * ThermodynamicConstantsInterface.avagadroNumber
        * getNumberOfMolesInPhase() / Math.pow(getVolumeSAFT(), 3.0);
    F1dispVolTermdVdVdV = -6.0 * ThermodynamicConstantsInterface.avagadroNumber
        * getNumberOfMolesInPhase() / Math.pow(getVolumeSAFT(), 4.0);

    dNSAFTdTdV = -1.0 * ThermodynamicConstantsInterface.pi / 6.0
        * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase()
        / Math.pow(volumeSAFT, 2.0) * getdDSAFTdT();
    dNSAFTdTdT = 1.0 * ThermodynamicConstantsInterface.pi / 6.0
        * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase() / volumeSAFT
        * getd2DSAFTdTdT();

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

    F1dispI1dN = calcF1dispI1dN();
    F1dispI1dNdN = calcF1dispI1dNdN();
    F1dispI1dNdNdN = calcF1dispI1dNdNdN();

    F1dispI1dm = calcF1dispI1dm();
    F1dispI1dV = F1dispI1dN * getDnSAFTdV();
    F1dispI1dVdV = F1dispI1dNdN * getDnSAFTdV() * getDnSAFTdV() + F1dispI1dN * dnSAFTdVdV; // F1dispI1dNdN*dnSAFTdVdV;

    // added by Rahmat
    F1dispI1dVdVdV = F1dispI1dNdNdN * getDnSAFTdV() * getDnSAFTdV() * getDnSAFTdV()
        + F1dispI1dN * 2.0 * getDnSAFTdV() * dnSAFTdVdV + F1dispI1dNdN * dnSAFTdVdV
        + F1dispI1dN * dnSAFTdVdVdV; // F1dispI1dNdN*dnSAFTdVdV;

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
        + F2dispI2dN * 2.0 * getDnSAFTdV() * dnSAFTdVdV + F2dispI2dNdN * dnSAFTdVdV
        + F2dispI2dN * dnSAFTdVdVdV;

    F2dispZHC = calcF2dispZHC();
    F2dispZHCdN = calcF2dispZHCdN();
    F2dispZHCdNdN = calcF2dispZHCdNdN();
    F2dispZHCdNdNdN = calcF2dispZHCdNdNdN();

    setF2dispZHCdm(calcF2dispZHCdm());
    F2dispZHCdV = F2dispZHCdN * getDnSAFTdV();
    F2dispZHCdVdV = F2dispZHCdNdN * getDnSAFTdV() * getDnSAFTdV() + F2dispZHCdN * dnSAFTdVdV;
    // F2dispZHCdNdN*dnSAFTdVdV*0;
    F2dispZHCdVdVdV = F2dispZHCdNdNdN * getDnSAFTdV() * getDnSAFTdV() * getDnSAFTdV()
        + F2dispZHCdNdN * 2.0 * getDnSAFTdV() * dnSAFTdVdV + F2dispZHCdNdN * dnSAFTdVdV
        + F2dispZHCdN * dnSAFTdVdVdV;
  }

  /** {@inheritDoc} */
  @Override
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

  /** {@inheritDoc} */
  @Override
  public double calcF2dispZHCdm() {
    double temp = -Math.pow(F2dispZHC, 2.0);
    return temp
        * ((8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2.0)) / Math.pow(1.0 - getNSAFT(), 4.0)
            - (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2.0) + 12 * Math.pow(getNSAFT(), 3.0)
                - 2 * Math.pow(getNSAFT(), 4.0))
                / Math.pow((1.0 - getNSAFT()) * (2.0 - getNSAFT()), 2.0));
  }

  /** {@inheritDoc} */
  @Override
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

  /** {@inheritDoc} */
  @Override
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
   * calcF2dispZHCdNdNdN.
   * </p>
   *
   * @return a double
   */
  public double calcF2dispZHCdNdNdN() {
    double temp = -6 * Math.pow(
        (getmSAFT() * (8.0 - 4.0 * getNSAFT()) / Math.pow((1.0 - getNSAFT()), 4) + 4 * getmSAFT()
            * (8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2) / Math.pow((1.0 - getNSAFT()), 5)
                + (1.0 - getmSAFT())
                    * (20 - 54 * getNSAFT() + 36 * Math.pow(getNSAFT(), 2)
                        - 8 * Math.pow(getNSAFT(), 3))
                    / (Math.pow((1.0 - getNSAFT()), 2) * Math.pow((2.0 - getNSAFT()), 2))
                + (2 * (1.0 - getmSAFT()))
                    * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2) + 12 * Math.pow(getNSAFT(), 3)
                        - 2 * Math.pow(getNSAFT(), 4))
                    / (Math.pow((1.0 - getNSAFT()), 3) * Math.pow((2.0 - getNSAFT()), 2))
                + (2 * (1.0 - getmSAFT()))
                    * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2) + 12 * Math.pow(getNSAFT(), 3)
                        - 2 * Math.pow(getNSAFT(), 4))
                    / (Math.pow((1.0 - getNSAFT()), 2) * Math.pow((2.0 - getNSAFT()), 3)))),
        3)
        / Math.pow((1.0
            + getmSAFT() * (8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2))
                / Math.pow((1.0 - getNSAFT()), 4)
            + (1.0 - getmSAFT())
                * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2) + 12 * Math.pow(getNSAFT(), 3)
                    - 2 * Math.pow(getNSAFT(), 4))
                / (Math.pow((1.0 - getNSAFT()), 2) * Math.pow((2.0 - getNSAFT()), 2))),
            4)
        + (6 * (getmSAFT() * (8.0 - 4.0 * getNSAFT()) / Math.pow((1.0 - getNSAFT()), 4)
            + 4 * getmSAFT() * (8.0 * getNSAFT() - Math.pow(2.0 * getNSAFT(), 2))
                / Math.pow((1.0 - getNSAFT()), 5)
            + (1.0 - getmSAFT())
                * (20 - 54 * getNSAFT() + 36 * Math.pow(getNSAFT(), 2)
                    - 8 * Math.pow(getNSAFT(), 3))
                / (Math.pow((1.0 - getNSAFT()), 2) * Math.pow((2.0 - getNSAFT()), 2))
            + (2 * (1.0 - getmSAFT()))
                * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2) + 12 * Math.pow(getNSAFT(), 3)
                    - 2 * Math.pow(getNSAFT(), 4))
                / (Math.pow((1.0 - getNSAFT()), 3) * Math.pow((2.0 - getNSAFT()), 2))
            + (2 * (1.0 - getmSAFT()))
                * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2) + 12 * Math.pow(getNSAFT(), 3)
                    - 2 * Math.pow(getNSAFT(), 4))
                / (Math.pow((1.0 - getNSAFT()), 2) * Math.pow((2.0 - getNSAFT()), 3))))
            * (-4.0 * getmSAFT() / Math.pow((1.0 - getNSAFT()), 4)
                + 8 * getmSAFT() * (8.0 - 4.0 * getNSAFT()) / Math.pow((1.0 - getNSAFT()), 5)
                + 20 * getmSAFT() * (8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2))
                    / Math.pow((1.0 - getNSAFT()), 6)
                + (1.0 - getmSAFT()) * (-54 + 72 * getNSAFT() - 24 * Math.pow(getNSAFT(), 2))
                    / (Math.pow((1.0 - getNSAFT()), 2) * Math.pow((2.0 - getNSAFT()), 2))
                + (4 * (1.0 - getmSAFT()))
                    * (20 - 54 * getNSAFT() + 36 * Math.pow(getNSAFT(), 2)
                        - 8 * Math.pow(getNSAFT(), 3))
                    / (Math.pow((1.0 - getNSAFT()), 3) * Math.pow((2.0 - getNSAFT()), 2))
                + (4 * (1.0 - getmSAFT()))
                    * (20 - 54 * getNSAFT() + 36 * Math.pow(getNSAFT(), 2)
                        - 8 * Math.pow(getNSAFT(), 3))
                    / (Math.pow((1.0 - getNSAFT()), 2) * Math.pow((2.0 - getNSAFT()), 3))
                + (6 * (1.0 - getmSAFT()))
                    * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2) + 12 * Math.pow(getNSAFT(), 3)
                        - 2 * Math.pow(getNSAFT(), 4))
                    / (Math.pow((1.0 - getNSAFT()), 4) * Math.pow((2.0 - getNSAFT()), 2))
                + (8 * (1.0 - getmSAFT()))
                    * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2) + 12 * Math.pow(getNSAFT(), 3)
                        - 2 * Math.pow(getNSAFT(), 4))
                    / (Math.pow((1.0 - getNSAFT()), 3) * Math.pow((2.0 - getNSAFT()), 3))
                + (6 * (1.0 - getmSAFT()))
                    * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2) + 12 * Math.pow(getNSAFT(), 3)
                        - 2 * Math.pow(getNSAFT(), 4))
                    / (Math.pow((1.0 - getNSAFT()), 2) * Math.pow((2.0 - getNSAFT()), 4)))
            / Math
                .pow(
                    1.0 + getmSAFT() * (8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2))
                        / Math.pow((1.0 - getNSAFT()), 4)
                        + ((1.0 - getmSAFT())
                            * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2)
                                + 12 * Math.pow(getNSAFT(), 3) - 2 * Math.pow(getNSAFT(), 4))
                            / (Math.pow((1.0 - getNSAFT()), 2) * Math.pow((2.0 - getNSAFT()), 2))),
                    3)
        - (-48.0 * getmSAFT() / Math.pow((1.0 - getNSAFT()), 5)
            + 60 * getmSAFT() * (8.0 - 4.0 * getNSAFT()) / Math.pow((1.0 - getNSAFT()), 6)
            + 120 * getmSAFT() * (8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2))
                / Math.pow((1.0 - getNSAFT()), 7)
            + (1.0 - getmSAFT()) * (72 - 48 * getNSAFT())
                / (Math.pow((1.0 - getNSAFT()), 2) * Math.pow((2.0 - getNSAFT()), 2))
            + (6 * (1.0 - getmSAFT())) * (-54 + 72 * getNSAFT() - 24 * Math.pow(getNSAFT(), 2))
                / (Math.pow((1.0 - getNSAFT()), 3) * Math.pow((2.0 - getNSAFT()), 2))
            + (6 * (1.0 - getmSAFT())) * (-54 + 72 * getNSAFT() - 24 * Math.pow(getNSAFT(), 2))
                / (Math.pow((1.0 - getNSAFT()), 2) * Math.pow((2.0 - getNSAFT()), 3))
            + (18 * (1.0 - getmSAFT()))
                * (20 - 54 * getNSAFT() + 36 * Math.pow(getNSAFT(), 2)
                    - 8 * Math.pow(getNSAFT(), 3))
                / (Math.pow((1.0 - getNSAFT()), 4) * Math.pow((2.0 - getNSAFT()), 2))
            + (24 * (1.0 - getmSAFT()))
                * (20 - 54 * getNSAFT() + 36 * Math.pow(getNSAFT(), 2)
                    - 8 * Math.pow(getNSAFT(), 3))
                / (Math.pow((1.0 - getNSAFT()), 3) * Math.pow((2.0 - getNSAFT()), 3))
            + (18 * (1.0 - getmSAFT()))
                * (20 - 54 * getNSAFT() + 36 * Math.pow(getNSAFT(), 2)
                    - 8 * Math.pow(getNSAFT(), 3))
                / (Math.pow((1.0 - getNSAFT()), 2) * Math.pow((2.0 - getNSAFT()), 4))
            + (24 * (1.0 - getmSAFT()))
                * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2) + 12 * Math.pow(getNSAFT(), 3)
                    - 2 * Math.pow(getNSAFT(), 4))
                / (Math.pow((1.0 - getNSAFT()), 5) * Math.pow((2.0 - getNSAFT()), 2))
            + (36 * (1.0 - getmSAFT()))
                * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2) + 12 * Math.pow(getNSAFT(), 3)
                    - 2 * Math.pow(getNSAFT(), 4))
                / (Math.pow((1.0 - getNSAFT()), 4) * Math.pow((2.0 - getNSAFT()), 3))
            + (36 * (1.0 - getmSAFT()))
                * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2) + 12 * Math.pow(getNSAFT(), 3)
                    - 2 * Math.pow(getNSAFT(), 4))
                / (Math.pow((1.0 - getNSAFT()), 3) * Math.pow((2.0 - getNSAFT()), 4))
            + (24 * (1.0 - getmSAFT()))
                * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2) + 12 * Math.pow(getNSAFT(), 3)
                    - 2 * Math.pow(getNSAFT(), 4))
                / (Math.pow((1.0 - getNSAFT()), 2) * Math.pow((2.0 - getNSAFT()), 5)))
            / Math
                .pow(
                    (1.0 + getmSAFT() * (8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2))
                        / Math.pow((1.0 - getNSAFT()), 4)
                        + (1.0 - getmSAFT())
                            * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2)
                                + 12 * Math.pow(getNSAFT(), 3) - 2 * Math.pow(getNSAFT(), 4))
                            / (Math.pow((1.0 - getNSAFT()), 2) * Math.pow((2.0 - getNSAFT()), 2))),
                    2);

    return temp;
  }

  // added by rahmat
  /**
   * <p>
   * calcdF2dispZHCdT.
   * </p>
   *
   * @return a double
   */
  public double calcdF2dispZHCdT() {
    double temp0 = -Math.pow(F2dispZHC, 2.0);
    double temp1 = getmSAFT() * ((8 - 4 * getNSAFT()) * dNSAFTdT * Math.pow(1 - getNSAFT(), 4) + 4
        * Math.pow(1 - getNSAFT(), 3) * dNSAFTdT * (8 * getNSAFT() - 2 * Math.pow(getNSAFT(), 2)))
        / Math.pow(1 - getNSAFT(), 8);
    double temp2a = (1 - getmSAFT())
        * (20 - 54 * getNSAFT() + 36 * Math.pow(getNSAFT(), 2) + 8 * Math.pow(getNSAFT(), 3))
        * dNSAFTdT * Math.pow((1 - getNSAFT()) * (2 - getNSAFT()), 2);
    double temp2b = (1 - getmSAFT()) * 2 * ((1 - getNSAFT()) * (2 - getNSAFT()))
        * (-1 * dNSAFTdT * (2 - getNSAFT()) + (-1) * dNSAFTdT * (1 - getNSAFT()))
        * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2) + 12 * Math.pow(getNSAFT(), 3)
            - 2 * Math.pow(getNSAFT(), 4));
    double temp2 = (temp2a - temp2b) / Math.pow((1 - getNSAFT()) * (2 - getNSAFT()), 4);
    return temp0 * (temp1 + temp2);
  }

  /** {@inheritDoc} */
  @Override
  public double calcmSAFT() {
    double temp2 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp2 += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi()
          / getNumberOfMolesInPhase();
    }

    return temp2;
  }

  /** {@inheritDoc} */
  @Override
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

  // added by rahmat
  /**
   * <p>
   * calcdF1dispSumTermdT.
   * </p>
   *
   * @return a double
   */
  public double calcdF1dispSumTermdT() {
    double temp1 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        temp1 +=
            getComponent(i).getNumberOfMolesInPhase() * getComponent(j).getNumberOfMolesInPhase()
                * getComponent(i).getmSAFTi() * getComponent(j).getmSAFTi()
                * Math.sqrt(getComponent(i).getEpsikSAFT() / temperature
                    * getComponent(j).getEpsikSAFT() / temperature)
                * (1.0 - mixRule.getBinaryInteractionParameter(i, j))
                * Math.pow(
                    0.5 * (getComponent(i).getSigmaSAFTi() + getComponent(j).getSigmaSAFTi()), 3.0)
                * (-1 / temperature);
      }
    }
    return temp1 / Math.pow(getNumberOfMolesInPhase(), 2.0);
  }

  /**
   * <p>
   * calcdF2dispSumTermdT.
   * </p>
   *
   * @return a double
   */
  public double calcdF2dispSumTermdT() {
    double temp1 = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        temp1 +=
            getComponent(i).getNumberOfMolesInPhase() * getComponent(j).getNumberOfMolesInPhase()
                * getComponent(i).getmSAFTi() * getComponent(j).getmSAFTi()
                * (getComponent(i).getEpsikSAFT()
                    / temperature * getComponent(j).getEpsikSAFT() / temperature)
                * Math.pow(1.0 - mixRule.getBinaryInteractionParameter(i, j), 2)
                * Math.pow(
                    0.5 * (getComponent(i).getSigmaSAFTi() + getComponent(j).getSigmaSAFTi()), 3.0)
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
   * <p>
   * calcF1dispI1dNdNdN.
   * </p>
   *
   * @return a double
   */
  public double calcF1dispI1dNdNdN() {
    double temp1 = 0.0;
    for (int i = 2; i < 7; i++) {
      temp1 += (i - 1.0) * (i - 2.0) * i * getaSAFT(i, getmSAFT(), aConstSAFT)
          * Math.pow(getNSAFT(), i - 3.0);
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
   * <p>
   * calcF2dispI2dNdNdN.
   * </p>
   *
   * @return a double
   */
  public double calcF2dispI2dNdNdN() {
    double temp1 = 0.0;
    for (int i = 2; i < 7; i++) {
      temp1 += (i - 1.0) * (i - 2.0) * i * getaSAFT(i, getmSAFT(), bConstSAFT)
          * Math.pow(getNSAFT(), i - 3.0);
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
   * <p>
   * calcdF1dispI1dT.
   * </p>
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
   * calcdF2dispI2dT.
   * </p>
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
      temp2 += getComponent(i).getNumberOfMolesInPhase() / getNumberOfMolesInPhase()
          * getComponent(i).getmSAFTi()
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
    return getNumberOfMolesInPhase()
        * (getmSAFT() * getAHSSAFT() - getMmin1SAFT() * Math.log(getGhsSAFT()));
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
   * <p>
   * dF_HC_SAFTdT.
   * </p>
   *
   * @return a double
   */
  public double dF_HC_SAFTdT() {
    return getNumberOfMolesInPhase() * (getmSAFT() * daHSSAFTdN * dNSAFTdT
        - getMmin1SAFT() * 1.0 / getGhsSAFT() * getDgHSSAFTdN() * dNSAFTdT);
    // (ThermodynamicConstantsInterface.R*temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double dF_HC_SAFTdVdV() {
    return getNumberOfMolesInPhase() * (getmSAFT() * daHSSAFTdNdN * getDnSAFTdV() * getDnSAFTdV()
        + getmSAFT() * daHSSAFTdN * dnSAFTdVdV
        + getMmin1SAFT() * Math.pow(getGhsSAFT(), -2.0) * Math.pow(getDgHSSAFTdN(), 2.0)
            * getDnSAFTdV() * getDnSAFTdV()
        - getMmin1SAFT() * Math.pow(getGhsSAFT(), -1.0) * dgHSSAFTdNdN * dnSAFTdV * dnSAFTdV
        - getMmin1SAFT() * 1.0 / getGhsSAFT() * getDgHSSAFTdN() * dnSAFTdVdV);
    // (ThermodynamicConstantsInterface.R*temperature);
  }
  // additonal dF_HC_SAFTdVdVdV (by Rahmat)

  /** {@inheritDoc} */
  @Override
  public double dF_HC_SAFTdVdVdV() {
    return getNumberOfMolesInPhase()
        * (getmSAFT() * daHSSAFTdNdNdN * getDnSAFTdV() * getDnSAFTdV() * getDnSAFTdV()
            + getmSAFT() * daHSSAFTdNdN * 2.0 * dnSAFTdV * dnSAFTdVdV
            + getmSAFT() * daHSSAFTdNdN * dnSAFTdV * dnSAFTdVdV
            + getmSAFT() * daHSSAFTdN * dnSAFTdVdVdV
            - 2.0 * getMmin1SAFT() * Math.pow(getGhsSAFT(), -3.0) * Math.pow(getDgHSSAFTdN(), 3.0)
                * getDnSAFTdV() * getDnSAFTdV() * getDnSAFTdV()
            + getMmin1SAFT() * Math.pow(getGhsSAFT(), -2.0) * 2.0 * getDgHSSAFTdN() * dgHSSAFTdNdN
                * getDnSAFTdV() * getDnSAFTdV() * getDnSAFTdV()
            + getMmin1SAFT() * Math.pow(getGhsSAFT(), -2.0) * Math.pow(getDgHSSAFTdN(), 2.0) * 2.0
                * getDnSAFTdV() * dnSAFTdVdV
            + getMmin1SAFT() * Math.pow(getGhsSAFT(), -2.0) * getDgHSSAFTdN() * dgHSSAFTdNdN
                * dnSAFTdV * dnSAFTdV * dnSAFTdV
            - getMmin1SAFT() * Math.pow(getGhsSAFT(), -1.0) * dgHSSAFTdNdNdN * dnSAFTdV * dnSAFTdV
                * dnSAFTdV
            - getMmin1SAFT() * Math.pow(getGhsSAFT(), -1.0) * dgHSSAFTdNdN * 2.0 * dnSAFTdV
                * dnSAFTdVdV
            + getMmin1SAFT() * Math.pow(getGhsSAFT(), -2.0) * Math.pow(getDgHSSAFTdN(), 2)
                * dnSAFTdV * dnSAFTdVdV
            - getMmin1SAFT() * Math.pow(getGhsSAFT(), -1.0) * dgHSSAFTdNdN * dnSAFTdV * dnSAFTdVdV
            - getMmin1SAFT() * Math.pow(getGhsSAFT(), -1.0) * getDgHSSAFTdN() * dnSAFTdVdVdV);
  }

  /** {@inheritDoc} */
  @Override
  public double F_DISP1_SAFT() {
    return getNumberOfMolesInPhase() * (-2.0 * ThermodynamicConstantsInterface.pi
        * getF1dispVolTerm() * getF1dispSumTerm() * getF1dispI1()); // (ThermodynamicConstantsInterface.R*temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double dF_DISP1_SAFTdV() {
    return getNumberOfMolesInPhase() * (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV
        * getF1dispSumTerm() * getF1dispI1()
        - 2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTerm * getF1dispSumTerm()
            * F1dispI1dV); // (ThermodynamicConstantsInterface.R*temperature);
  }

  /** {@inheritDoc} */
  @Override
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

  // added by Rahmat
  /**
   * <p>
   * dF_DISP1_SAFTdVdVdV.
   * </p>
   *
   * @return a double
   */
  public double dF_DISP1_SAFTdVdVdV() {
    return getNumberOfMolesInPhase() * ((-2.0 * ThermodynamicConstantsInterface.pi
        * F1dispVolTermdVdVdV * getF1dispSumTerm() * getF1dispI1())
        + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdVdV * getF1dispSumTerm()
            * F1dispI1dV)
        + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdVdV * getF1dispSumTerm()
            * F1dispI1dV)
        + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV * getF1dispSumTerm()
            * F1dispI1dVdV)
        + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdVdV * getF1dispSumTerm()
            * F1dispI1dV)
        + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV * getF1dispSumTerm()
            * F1dispI1dVdV)
        + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV * getF1dispSumTerm()
            * F1dispI1dVdV)
        + (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTerm * getF1dispSumTerm()
            * F1dispI1dVdVdV));
  }

  // added by Rahmat
  /**
   * <p>
   * dF_DISP1_SAFTdT.
   * </p>
   *
   * @return a double
   */
  public double dF_DISP1_SAFTdT() {
    return getNumberOfMolesInPhase() * (-2.0 * ThermodynamicConstantsInterface.pi
        * (dF1dispVolTermdT * getF1dispSumTerm() * getF1dispI1()
            + dF1dispSumTermdT * getF1dispVolTerm() * getF1dispI1()
            + dF1dispI1dT * getF1dispVolTerm() * getF1dispSumTerm()));
  }

  /**
   * <p>
   * dF_DISP2_SAFTdT.
   * </p>
   *
   * @return a double
   */
  public double dF_DISP2_SAFTdT() {
    return getNumberOfMolesInPhase() * (-1 * ThermodynamicConstantsInterface.pi * getmSAFT())
        * getF1dispVolTerm()
        * (dF2dispSumTermdT * getF2dispI2() * getF2dispZHC()
            + dF2dispI2dT * getF2dispSumTerm() * getF2dispZHC()
            + dF2dispZHCdT * getF2dispSumTerm() * getF2dispI2());
  }

  /** {@inheritDoc} */
  @Override
  public double F_DISP2_SAFT() {
    return getNumberOfMolesInPhase() * (-ThermodynamicConstantsInterface.pi * getmSAFT()
        * getF1dispVolTerm() * getF2dispSumTerm() * getF2dispI2() * getF2dispZHC());
    // (ThermodynamicConstantsInterface.R*temperature);
  }

  /** {@inheritDoc} */
  @Override
  public double dF_DISP2_SAFTdV() {
    return getNumberOfMolesInPhase() * (-ThermodynamicConstantsInterface.pi * getmSAFT()
        * F1dispVolTermdV * getF2dispSumTerm() * getF2dispI2() * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * F2dispI2dV * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdV); // (ThermodynamicConstantsInterface.R*temperature);
  }

  /** {@inheritDoc} */
  @Override
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

  /**
   * <p>
   * dF_DISP2_SAFTdVdVdV.
   * </p>
   *
   * @return a double
   */
  public double dF_DISP2_SAFTdVdVdV() {
    return getNumberOfMolesInPhase() * ((-ThermodynamicConstantsInterface.pi * getmSAFT()
        * F1dispVolTermdVdVdV * getF2dispSumTerm() * getF2dispI2() * getF2dispZHC())
        + (-ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV * getF2dispSumTerm()
            * F2dispI2dV * getF2dispZHC())
        + (-ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdV)
        + -(ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV * getF2dispSumTerm()
            * getF2dispZHC() * F2dispI2dV)
        + -(ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * F2dispZHCdV * F2dispI2dV)
        + -(ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * getF2dispZHC() * F2dispI2dVdV)
        + -(ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * F2dispI2dV * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdVdV)
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV * getF2dispSumTerm()
            * F2dispI2dV * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * F2dispI2dVdV * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * F2dispI2dV * F2dispZHCdV
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * F2dispI2dVdV * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * F2dispI2dVdVdV * getF2dispZHC()
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * F2dispI2dVdV * F2dispZHCdV
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * F2dispI2dV * F2dispZHCdV
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * F2dispI2dVdV * F2dispZHCdV
        - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * F2dispI2dV * F2dispZHCdVdV
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * F2dispI2dV * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * F2dispI2dV * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * F2dispI2dVdV * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * F2dispI2dV * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * F2dispI2dV * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdVdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * F2dispI2dV * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * F2dispI2dV * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * F2dispI2dVdV * F2dispZHCdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * F2dispI2dV * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * F2dispI2dV * F2dispZHCdVdV)
        - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm()
            * getF2dispI2() * F2dispZHCdVdVdV));
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
  public double dFdVdVdV() {
    return (useHS * dF_HC_SAFTdVdVdV() + useDISP1 * dF_DISP1_SAFTdVdVdV()
        + useDISP2 * dF_DISP2_SAFTdVdVdV()) * 1.0e-20;
  }

  // added by rahmat
  /**
   * <p>
   * getdDSAFTdT.
   * </p>
   *
   * @return a double
   */
  public double getdDSAFTdT() {
    double temp = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      temp += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi()
          / getNumberOfMolesInPhase() * 3
          * Math.pow(((ComponentPCSAFT) getComponent(i)).getdSAFTi(), 2.0)
          * (-1.08 / Math.pow(temperature, 2)) * Math.pow(getComponent(i).getSigmaSAFTi(), 3)
          * Math.pow(1 - 0.12 * Math.exp(-3 * getComponent(i).getEpsikSAFT() / temperature), 2)
          * getComponent(i).getEpsikSAFT()
          * Math.exp(-3 * getComponent(i).getEpsikSAFT() / temperature);
    }
    return temp;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double BonV = pt == PhaseType.GAS ? pressure * getB() / (numberOfMolesInPhase * temperature * R)
        : 2.0 / (2.0 + temperature / getPseudoCriticalTemperature());
    BonV = Math.max(1.0e-4, Math.min(1.0 - 1.0e-4, BonV));
    // double BonVold = BonV;
    double Btemp = 0;
    double dh = 0;
    double h = 0;
    // double Dtemp = 0, hOld = 0, dhOld = 0, gvvv = 0, fvvv = 0, d2 = 0, dhh = 1;
    double d1 = 0;
    Btemp = getB();
    // Dtemp = getA();
    if (Btemp <= 0) {
      logger.info("b negative in volume calc");
    }
    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    int iterations = 0;
    double oldMolarVolume = 0.0;
    // System.out.println("volume " + getVolume());
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

      /*
       * BonVold = BonV; //BonV = BonVold; h = BonVold -
       * Btemp/numberOfMolesInPhase*dFdV()-pressure*Btemp/(numberOfMolesInPhase*R* temperature); dh
       * = 1.0 + Btemp/Math.pow(BonVold,2.0)*(Btemp/numberOfMolesInPhase*dFdVdV()); //dhh =
       * -2.0*Btemp/Math.pow(BonV,3.0)*(Btemp/numberOfMolesInPhase*dFdVdV())-Math.pow(
       * Btemp,2.0)/Math.pow(BonV,4.0)*(Btemp/numberOfMolesInPhase*dFdVdVdV());
       *
       * //made by Rahmat
       *
       * BonV = BonVold - 0.5* (2* h * dh / ((2* Math.pow(dh,2) - h * dhh)));
       *
       * double dBonV = BonV - BonVold; dhh = (dh - dhOld)/ dBonV; dhOld = dh;
       *
       * hOld = h;
       *
       * //d1 = - h/dh; //d2 = - dh/dhh; //BonV += d1; //*(1.0+0.5*-1.0); /*
       * if(Math.abs(d1/d2)<=1.0){ BonV += d1*(1.0+0.5*d1/d2); } else if(d1/d2<-1){ BonV +=
       * d1*(1.0+0.5*-1.0); } else if(d1/d2>1){ BonV += d2; double hnew = h +d2*-h/d1;
       * if(Math.abs(hnew)>Math.abs(h)){ System.out.println("volume correction needed...."); BonV =
       * phase== 1 ? 2.0/(2.0+temperature/getPseudoCriticalTemperature()):pressure*getB()/(
       * numberOfMolesInPhase*temperature*R); } }
       *
       * if(BonV>1){ BonV=1.0-1.0e-6; BonVold=10; } if (BonV < 0) { BonV = 1.0e-16; BonVold = 10; }
       */
      // setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
    } while (Math.abs((oldMolarVolume - getMolarVolume()) / oldMolarVolume) > 1.0e-10
        && iterations < 100);
    // while(Math.abs((BonV-BonVold)/BonV)>1.0e-10 && iterations<500);

    // while(Math.abs((h-hOld)/h)>1.0e-10 && iterations<6000);
    // System.out.println("error BonV " + Math.abs((BonV-BonVold)/BonV));
    // System.out.println("iterations " + iterations);
    /*
     * if(BonV<0){ BonV = pressure*getB()/(numberOfMolesInPhase*temperature*R); setMolarVolume(1.0 /
     * BonV * Btemp / numberOfMolesInPhase); Z = pressure*getMolarVolume()/(R*temperature); }
     * if(iterations>=6000) throw new util.exception.TooManyIterationsException();
     * if(Double.isNaN(getMolarVolume())) throw new util.exception.IsNaNException();
     *
     * // if(pt==0) System.out.println("density " + getDensity()); //"BonV: " + BonV +
     * " "+"  itert: " + iterations +" " + "  phase " + pt+ "  " + h + " " +dh + " B " + Btemp +
     * "  D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv" + fVV());
     */
    return getMolarVolume();
  }
}
