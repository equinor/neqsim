package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentSpanWagnerEos;
import neqsim.thermo.util.spanwagner.NeqSimSpanWagner;

/**
 * Phase implementation using the Span-Wagner reference equation for CO2.
 *
 * @author esol
 */
public class PhaseSpanWagnerEos extends PhaseEos {
  private static final long serialVersionUID = 1000;

  private double enthalpy; // J/mol
  private double entropy; // J/molK
  private double gibbsEnergy; // J/mol
  private double cp; // J/molK
  private double cv; // J/molK
  private double internalEnergy; // J/mol
  private double soundSpeed; // m/s
  private double molarDensity; // mol/m3

  /**
   * <p>
   * Constructor for PhaseSpanWagnerEos.
   * </p>
   */
  public PhaseSpanWagnerEos() {
    thermoPropertyModelName = "Span-Wagner";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseSpanWagnerEos clone() {
    return (PhaseSpanWagnerEos) super.clone();
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentSpanWagnerEos(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    if (initType >= 1) {
      double[] props = NeqSimSpanWagner.getProperties(temperature, pressure * 1e5, getType());
      molarDensity = props[0];
      Z = props[1];
      enthalpy = props[2];
      entropy = props[3];
      cp = props[4];
      cv = props[5];
      internalEnergy = props[6];
      gibbsEnergy = props[7];
      soundSpeed = props[8];
      getComponent(0).setFugacityCoefficient(props[9]);
      if (molarDensity > 1500.0) {
        setType(PhaseType.LIQUID);
      } else {
        setType(PhaseType.GAS);
      }
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
    return internalEnergy * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getCp() {
    return cp * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    return cv * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getSoundSpeed() {
    return soundSpeed;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt) {
    return 1.0 / molarDensity;
  }
}
