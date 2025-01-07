package neqsim.thermo.phase;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentGERG2004;
import neqsim.thermo.util.jni.GERG2004EOS;

/**
 * <p>
 * PhaseGERG2004Eos class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseGERG2004Eos extends PhaseEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private GERG2004EOS gergEOS = new GERG2004EOS();
  double[] xFracGERG = new double[18];

  int IPHASE = 0;
  boolean okVolume = true;
  double enthalpy = 0.0;

  double entropy = 0.0;

  double gibbsEnergy = 0.0;

  double CpGERG = 0.0;

  double CvGERG = 0.0;

  double internalEnery = 0.0;

  double JTcoef = 0.0;

  /**
   * <p>
   * Constructor for PhaseGERG2004Eos.
   * </p>
   */
  public PhaseGERG2004Eos() {
    thermoPropertyModelName = "GERG-EoS 2008";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseGERG2004Eos clone() {
    PhaseGERG2004Eos clonedPhase = null;
    try {
      clonedPhase = (PhaseGERG2004Eos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentGERG2004(name, moles, molesInPhase, compNumber);
  }

  /**
   * <p>
   * Setter for the field <code>xFracGERG</code>.
   * </p>
   */
  public void setxFracGERG() {
    for (int j = 0; j < gergEOS.getNameList().length; j++) {
      if (hasComponent(gergEOS.getNameList()[j])) {
        xFracGERG[j] = getComponent(gergEOS.getNameList()[j]).getx();
      } else {
        xFracGERG[j] = 0.0;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    IPHASE = pt == PhaseType.LIQUID ? -1 : -2;
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    setxFracGERG();

    if (!okVolume) {
      IPHASE = pt == PhaseType.LIQUID ? -2 : -1;
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    }
    if (initType >= 1) {
      double[] temp = new double[18];
      temp = GERG2004EOS.SPHIOTPX(temperature, pressure / 10.0, xFracGERG[0], xFracGERG[1],
          xFracGERG[2], xFracGERG[3], xFracGERG[4], xFracGERG[5], xFracGERG[6], xFracGERG[7],
          xFracGERG[8], xFracGERG[9], xFracGERG[10], xFracGERG[11], xFracGERG[12], xFracGERG[13],
          xFracGERG[14], xFracGERG[15], xFracGERG[16], xFracGERG[17], IPHASE);

      for (int j = 0; j < gergEOS.getNameList().length; j++) {
        if (hasComponent(gergEOS.getNameList()[j])) {
          if (temp[j] == -1111) {
            IPHASE = -2;
          }
          if (temp[j] == -2222) {
            IPHASE = -1;
          }
        }
      }
      temp = GERG2004EOS.SPHIOTPX(temperature, pressure / 10.0, xFracGERG[0], xFracGERG[1],
          xFracGERG[2], xFracGERG[3], xFracGERG[4], xFracGERG[5], xFracGERG[6], xFracGERG[7],
          xFracGERG[8], xFracGERG[9], xFracGERG[10], xFracGERG[11], xFracGERG[12], xFracGERG[13],
          xFracGERG[14], xFracGERG[15], xFracGERG[16], xFracGERG[17], IPHASE);

      for (int j = 0; j < gergEOS.getNameList().length; j++) {
        if (hasComponent(gergEOS.getNameList()[j])) {
          getComponent(gergEOS.getNameList()[j]).setFugacityCoefficient(temp[j]);
        }
      }

      double[] alloTPX = new double[17];
      alloTPX = GERG2004EOS.SALLOTPX(temperature, pressure / 10.0, xFracGERG[0], xFracGERG[1],
          xFracGERG[2], xFracGERG[3], xFracGERG[4], xFracGERG[5], xFracGERG[6], xFracGERG[7],
          xFracGERG[8], xFracGERG[9], xFracGERG[10], xFracGERG[11], xFracGERG[12], xFracGERG[13],
          xFracGERG[14], xFracGERG[15], xFracGERG[16], xFracGERG[17], IPHASE);

      gibbsEnergy = alloTPX[10]; // gergEOS.GOTPX(temperature,pressure/10.0,
                                 // xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
      internalEnery = alloTPX[9]; // .UOTPX(temperature,pressure/10.0,
                                  // xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
      enthalpy = alloTPX[2]; // gergEOS.HOTPX(temperature,pressure/10.0,
                             // xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
      entropy = alloTPX[3]; // gergEOS.SOTPX(temperature,pressure/10.0,
                            // xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
      CpGERG = alloTPX[4]; // gergEOS.CPOTPX(temperature,pressure/10.0,
                           // xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
      CvGERG = alloTPX[5]; // gergEOS.CPOTPX(temperature,pressure/10.0,
                           // xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    return gibbsEnergy;
  }

  /** {@inheritDoc} */
  @Override
  public double getJouleThomsonCoefficient() {
    return JTcoef;
  }

  /** {@inheritDoc} */
  @Override
  public double getEnthalpy() {
    return enthalpy;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropy() {
    return entropy;
  }

  /** {@inheritDoc} */
  @Override
  public double getInternalEnergy() {
    return internalEnery;
  }

  /** {@inheritDoc} */
  @Override
  public double getCp() {
    return CpGERG;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    return CvGERG;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double temp = GERG2004EOS.ZOTPX(temperature, pressure / 10.0, xFracGERG[0], xFracGERG[1],
        xFracGERG[2], xFracGERG[3], xFracGERG[4], xFracGERG[5], xFracGERG[6], xFracGERG[7],
        xFracGERG[8], xFracGERG[9], xFracGERG[10], xFracGERG[11], xFracGERG[12], xFracGERG[13],
        xFracGERG[14], xFracGERG[15], xFracGERG[16], xFracGERG[17], IPHASE)
        * ThermodynamicConstantsInterface.R * temperature / (pressure);

    temp = GERG2004EOS.ZOTPX(temperature, pressure / 10.0, xFracGERG[0], xFracGERG[1], xFracGERG[2],
        xFracGERG[3], xFracGERG[4], xFracGERG[5], xFracGERG[6], xFracGERG[7], xFracGERG[8],
        xFracGERG[9], xFracGERG[10], xFracGERG[11], xFracGERG[12], xFracGERG[13], xFracGERG[14],
        xFracGERG[15], xFracGERG[16], xFracGERG[17], IPHASE) * ThermodynamicConstantsInterface.R
        * temperature / (pressure);
    okVolume = !(Math.abs(2222 + temp) < 0.1 || Math.abs(1111 + temp) < 0.1);
    return temp;
  }
}
