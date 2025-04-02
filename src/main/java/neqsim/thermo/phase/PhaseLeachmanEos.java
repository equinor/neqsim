package neqsim.thermo.phase;

import org.netlib.util.doubleW;
import neqsim.thermo.component.ComponentLeachmanEos;

/**
 * <p>
 * PhaseLeachmanEos class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseLeachmanEos extends PhaseFundamentalEOS implements PhaseFundamentalEOSInterface {
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

  doubleW[][] alpharesMatrix = null;

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

    if (initType != 0) {
      
    }    
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);





    if (!okVolume) {
      IPHASE = pt == PhaseType.LIQUID ? -2 : -1;
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    }
    if (initType >= 1) {
      density = solveDensity(temperature, pressure);//mol/m3



      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    }
  }


  /** {@inheritDoc} */
  @Override
  public double solveDensity(double temperature, double pressure) {
    return getDensity_Leachman();
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity() {
    return getDensity_Leachman();
  }


  /** {@inheritDoc} */
  @Override
  public doubleW[] getAlpha0Matrix() {
    return getAlpha0_Leachman();
  }

  /** {@inheritDoc} */
  @Override
  public doubleW[][] getAlpharesMatrix() {
    return getAlphares_Leachman();
  }

}
