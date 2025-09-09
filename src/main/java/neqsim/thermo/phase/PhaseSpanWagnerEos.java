package neqsim.thermo.phase;

import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.component.ComponentSpanWagnerEos;
import neqsim.thermo.util.spanwagner.NeqSimSpanWagner;

/**
 * Phase implementation using the Span-Wagner reference equation for CO2.
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

  public PhaseSpanWagnerEos() {
    thermoPropertyModelName = "Span-Wagner";
  }

  @Override
  public PhaseSpanWagnerEos clone() {
    return (PhaseSpanWagnerEos) super.clone();
  }

  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentSpanWagnerEos(name, moles, molesInPhase, compNumber);
  }

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
  @Override
  public double getGibbsEnergy() {
    return gibbsEnergy * numberOfMolesInPhase;
  }

  @Override
  public double getZ() {
    return Z;
  }

  @Override
  public double getEnthalpy() {
    return enthalpy * numberOfMolesInPhase;
  }

  @Override
  public double getEntropy() {
    return entropy * numberOfMolesInPhase;
  }

  @Override
  public double getInternalEnergy() {
    return internalEnergy * numberOfMolesInPhase;
  }

  @Override
  public double getCp() {
    return cp * numberOfMolesInPhase;
  }

  @Override
  public double getCv() {
    return cv * numberOfMolesInPhase;
  }

  @Override
  public double getSoundSpeed() {
    return soundSpeed;
  }

  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt) {
    return 1.0 / molarDensity;
  }
}
