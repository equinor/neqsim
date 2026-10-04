package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * ComponentWonWax class.
 *
 * @author rahmat
 * @version $Id: $Id
 */
public class ComponentWonWax extends ComponentSolid {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for ComponentWonWax.
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentWonWax(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase1) {
    if (!isWaxFormer()) {
      fugacityCoefficient = 1.0e30;
      return fugacityCoefficient;
    }

    return fugcoef2(phase1);
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef2(PhaseInterface phase1) {
    if (!isWaxFormer()) {
      return WaxModelCorrelations.solidFugacityCoefficient(this, phase1, 0.0);
    }
    return WaxModelCorrelations.solidFugacityCoefficient(this, phase1, Math.log(getWonActivityCoefficient(phase1)));
  }

  /**
   * getWonActivityCoefficient.
   *
   * @param phase1 a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getWonActivityCoefficient(PhaseInterface phase1) {
    if (!isWaxFormer()) {
      return 1.0;
    }
    double[] fractions = WaxModelCorrelations.composition(phase1);
    double volumeSum = 0.0;
    double weightedParameter = 0.0;
    for (int i = 0; i < fractions.length; i++) {
      if (fractions[i] == 0.0) {
        continue;
      }
      ComponentWonWax component = (ComponentWonWax) phase1.getComponent(i);
      double volumeFraction = fractions[i] * component.getWonVolume(phase1);
      volumeSum += volumeFraction;
      weightedParameter += volumeFraction * component.getWonParam(phase1);
    }
    double difference = weightedParameter / volumeSum - getWonParam(phase1);
    double lnGamma = getWonVolume(phase1) * difference * difference / (1.9858775 * phase1.getTemperature());
    return WaxModelCorrelations.coefficient(lnGamma, this, phase1);
  }

  /**
   * Calculates the liquid molar volume at 25 deg C for the Won solubility parameter model.
   *
   * <p>
   * Uses the density correlation: d25 = 0.8155 + 6.273e-5 * MW - 13.06 / MW (g/cm3, MW in g/mol). Returns volume in
   * cm3/mol.
   * </p>
   *
   * @param phase1 a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return molar volume at 25 deg C in cm3/mol
   */
  public double getWonVolume(PhaseInterface phase1) {
    double mw = getMolarMass() * 1000.0; // convert kg/mol to g/mol
    double d25 = 0.8155 + 0.6273e-4 * mw - 13.06 / mw;

    return mw / d25;
  }

  /**
   * Calculates the Won solubility parameter for this component.
   *
   * <p>
   * delta_i = sqrt((DH_vap + DH_fus - RT) / V_i) in (cal/cm3)^0.5. Uses Morgan-Kobayashi correlation for vaporization
   * enthalpy and Pedersen correlations for fusion properties. All molecular-weight-dependent correlations use MW in
   * g/mol.
   * </p>
   *
   * @param phase1 a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return solubility parameter in (cal/cm3)^0.5
   */
  public double getWonParam(PhaseInterface phase1) {
    double mw = getMolarMass() * 1000.0; // convert kg/mol to g/mol

    // Melting temperature (Pedersen, 1991) [K]
    double Tf = 374.5 + 0.02617 * mw - 20172.0 / mw;

    // Heat of fusion (Pedersen, 1991) [cal/mol]
    double Hf = 0.1426 * mw * Tf;

    double carbonnumber = mw / 14.0;
    double omega = 0.0520750 + 0.0448946 * carbonnumber - 0.000185397 * carbonnumber * carbonnumber;
    double Hvap = WaxModelCorrelations.vaporizationEnthalpy(phase1.getTemperature(), getTC(), omega) / 4.184;

    // Molar volume at 25 deg C [cm3/mol]
    double d25 = 0.8155 + 0.6273e-4 * mw - 13.06 / mw;
    double vol = mw / d25;

    // Solid cohesive energy includes sublimation: DHsub = DHvap + DHfus.
    // Solubility parameter: delta = sqrt((Hvap + Hf - RT) / V) in (cal/cm3)^0.5
    double cohesiveEnergy = (Hvap + Hf - 1.9858775 * phase1.getTemperature()) / vol;
    if (!Double.isFinite(cohesiveEnergy) || cohesiveEnergy <= 0.0) {
      throw new IllegalStateException("Invalid Won solid cohesive energy for " + getComponentName());
    }
    return Math.sqrt(cohesiveEnergy);
  }
}
