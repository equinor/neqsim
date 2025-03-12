package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentVegaEos;

/**
 * <p>
 * PhaseGERG2004Eos class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseVegaEos extends PhaseEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  int IPHASE = 0;
  boolean okVolume = true;
  double enthalpy = 0.0;

  double entropy = 0.0;

  double gibbsEnergy = 0.0;

  double CpVega = 0.0;

  double CvVega = 0.0;

  double internalEnery = 0.0;

  double JTcoef = 0.0;

  /**
   * <p>
   * Constructor for PhaseGERG2004Eos.
   * </p>
   */
  public PhaseVegaEos() {
    thermoPropertyModelName = "Vega Eos";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseVegaEos clone() {
    PhaseVegaEos clonedPhase = null;
    try {
      clonedPhase = (PhaseVegaEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentVegaEos(name, moles, molesInPhase, compNumber);
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
      temp = getProperties_Vega();


      gibbsEnergy = temp[12];
      internalEnery = temp[6]; // .UOTPX(temperature,pressure/10.0,
                               // xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
      enthalpy = temp[7]; // gergEOS.HOTPX(temperature,pressure/10.0,
                          // xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
      entropy = temp[8]; // gergEOS.SOTPX(temperature,pressure/10.0,
                         // xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
      CpVega = temp[10]; // gergEOS.CPOTPX(temperature,pressure/10.0,
                         // xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
      CvVega = temp[9]; // gergEOS.CPOTPX(temperature,pressure/10.0,
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
    return CpVega;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    return CvVega;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    return getMolarMass() * 1e5 / getDensity_Vega();
  }
}
