package neqsim.thermo.phase;

import org.netlib.util.doubleW;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.ComponentLeachmanEos;

/**
 * <p>
 * PhaseLeachmanEos class.
 *
 * @version $Id: $Id
 * @author vscode
 */
public class PhaseLeachmanEos extends PhaseEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  int IPHASE = 0;
  boolean okVolume = true;
  double enthalpy = 0.0;

  double entropy = 0.0;

  double gibbsEnergy = 0.0;

  double CpLeachman = 0.0;

  double CvLeachman = 0.0;

  double internalEnery = 0.0;

  double JTcoef = 0.0;

  doubleW[] a0 = null;

  doubleW[][] ar = null;

  double kappa = 0.0;

  double W = 0.0;



  /**
   * <p>
   * Constructor for PhaseLeachmanEos.
   * </p>
   */
  public PhaseLeachmanEos() {
    thermoPropertyModelName = "Leachman Eos";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseLeachmanEos clone() {
    PhaseLeachmanEos clonedPhase = null;
    try {
      clonedPhase = (PhaseLeachmanEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentLeachmanEos(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    IPHASE = pt == PhaseType.LIQUID ? -1 : -2;
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);

    if (!okVolume) {
      IPHASE = pt == PhaseType.LIQUID ? -2 : -1;
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    }
    if (initType >= 1) {
      double[] temp = new double[18];
      temp = getProperties_Leachman();
      a0 = getAlpha0_Leachman();
      ar = getAlphares_Leachman();

      pressure = temp[0] / 100;

      Z = temp[1];

      W = temp[11];

      JTcoef = temp[13];

      kappa = temp[14];
      gibbsEnergy = temp[12];
      internalEnery = temp[6]; // .UOTPX(temperature,pressure/10.0,
                               // xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
      enthalpy = temp[7]; // gergEOS.HOTPX(temperature,pressure/10.0,
                          // xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
      entropy = temp[8]; // gergEOS.SOTPX(temperature,pressure/10.0,
                         // xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
      CpLeachman = temp[10]; // gergEOS.CPOTPX(temperature,pressure/10.0,
      // xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
      CvLeachman = temp[9]; // gergEOS.CPOTPX(temperature,pressure/10.0,
      // xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    return gibbsEnergy * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getZ() {
    return Z;
  }

  /** {@inheritDoc} */
  @Override
  public double getJouleThomsonCoefficient() {
    return JTcoef * 1e3;
  }

  /** {@inheritDoc} */
  @Override
  public double getEnthalpy() {
    return enthalpy * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropy() {
    return entropy * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getInternalEnergy() {
    return internalEnery * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getCp() {
    return CpLeachman * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    return CvLeachman * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    return getMolarMass() * 1e5 / getDensity_Leachman();
  }

  /** {@inheritDoc} */
  @Override
  public double calcPressure() {
    return numberOfMolesInPhase / getVolume() * R * temperature * (1 + ar[0][1].val);
  }

  /** {@inheritDoc} */
  @Override
  public double calcPressuredV() {
    return -Math.pow(getDensity() / getMolarMass(), 2) * R * temperature
        * (1 + 2 * ar[0][1].val + ar[0][2].val) / numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(int i) {
    return ((ComponentEosInterface) getComponent(i)).dFdN(this, this.getNumberOfComponents(),
        temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int i, int j) {
    return ((ComponentEosInterface) getComponent(i)).dFdNdN(j, this, this.getNumberOfComponents(),
        temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(int i) {
    return ((ComponentEosInterface) getComponent(i)).dFdNdV(this, this.getNumberOfComponents(),
        temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(int i) {
    return ((ComponentEosInterface) getComponent(i)).dFdNdT(this, this.getNumberOfComponents(),
        temperature, pressure);
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity() {
    return getDensity_Leachman();
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdTVn() {
    return (getNumberOfMolesInPhase() / getVolume()) * R * (1 + ar[0][1].val + ar[1][1].val);
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdVTn() {
    return -Math.pow(getNumberOfMolesInPhase() / getVolume(), 2) * R * temperature
        * (1 + 2 * ar[0][1].val + ar[0][2].val) / numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getdPdrho() {
    return R * temperature * (1 + 2 * ar[0][1].val + ar[0][2].val);
  }
}
