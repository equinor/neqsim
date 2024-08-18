package neqsim.thermo.component;

import neqsim.thermo.component.attractiveEosTerm.AttractiveTermPr1978;

/**
 * <p>
 * ComponentPR_5CompModel class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentPR_5CompModel extends ComponentPR {
  private static final long serialVersionUID = 1000;
  private double omegaA = .457243333330;


  private double omegaB = .077803333;

  /**
   * <p>
   * Constructor for ComponentPR_5CompModel.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentPR_5CompModel(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);

    if (name.equals("CO2")) {
      setMolarMass(44.01, "lbm/lbmol");
      setTC(574.0, "R");
      setPC(1069.51, "psia");
      setAcentricFactor(0.12256);
      setVolumeCorrectionConst(-0.27593 * 6.242796057614462E-05 * b);
      setOmegaA(0.427705);
      setOmegaB(0.0696460);
    } else if (name.equals("H2S")) {
      setMolarMass(34.082, "lbm/lbmol");
      setTC(672.12, "R");
      setPC(1299.97, "psia");
      setAcentricFactor(0.04916);
      setVolumeCorrectionConst(-0.22896 * 6.242796057614462E-05 * b);
      setOmegaA(0.436743);
      setOmegaB(0.0724373);
    } else if (name.equals("nitrogen")) {
      setMolarMass(28.014, "lbm/lbmol");
      setTC(227.16, "R");
      setPC(492.84, "psia");
      setAcentricFactor(0.03700);
      setVolumeCorrectionConst(-0.21067 * 6.242796057614462E-05 * b);
      setOmegaA(0.457236);
      setOmegaB(0.0777961);
    } else {

      double Tca = 0.1;
      double Tcb = 0.1;
      double Tcc = 0.1;

      double Pca = -0.00273458;
      double Pcb = 0.1;
      double Pcc = 0.1;

      setTC(227.16, "R");
      setPC(492.84, "psia");
      setAcentricFactor(0.03700);
      setVolumeCorrectionConst(-0.21067 * 6.242796057614462E-05 * b);
      setOmegaA(0.457236);
      setOmegaB(0.0777961);
    }

    a = getOmegaA() * R * R * criticalTemperature * criticalTemperature / criticalPressure;
    b = getOmegaB() * R * criticalTemperature / criticalPressure;

    setAttractiveParameter(new AttractiveTermPr1978(this));
  }

  /**
   * <p>
   * Constructor for ComponeComponentPR_5CompModelntPR.
   * </p>
   *
   * @param number a int
   * @param TC Critical temperature
   * @param PC Critical pressure
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Number of moles
   */
  public ComponentPR_5CompModel(int number, double TC, double PC, double M, double a,
      double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentPR_5CompModel clone() {
    ComponentPR_5CompModel clonedComponent = null;
    try {
      clonedComponent = (ComponentPR_5CompModel) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedComponent;
  }

  /** {@inheritDoc} */
  @Override
  public double calca() {
    return getOmegaA() * R * R * criticalTemperature * criticalTemperature / criticalPressure;
  }

  /** {@inheritDoc} */
  @Override
  public double calcb() {
    return getOmegaB() * R * criticalTemperature / criticalPressure;
  }

  /** {@inheritDoc} */
  @Override
  public double getVolumeCorrection() {
    if (ionicCharge != 0) {
      return 0.0;
    }
    if (Math.abs(getVolumeCorrectionConst()) > 1.0e-10) {
      return getVolumeCorrectionConst() * b;
    } else if (Math.abs(this.getRacketZ()) < 1e-10) {
      racketZ = 0.29056 - 0.08775 * getAcentricFactor();
    }
    return 0.50033 * (0.25969 - this.getRacketZ()) * R * criticalTemperature / criticalPressure;
  }

  public double getOmegaA() {
    return omegaA;
  }

  public void setOmegaA(double omegaA) {
    this.omegaA = omegaA;
  }

  public double getOmegaB() {
    return omegaB;
  }

  public void setOmegaB(double omegaB) {
    this.omegaB = omegaB;
  }

}
