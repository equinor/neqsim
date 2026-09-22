package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * ComponentWaxWilson class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class ComponentWaxWilson extends ComponentSolid {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for ComponentWaxWilson.
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentWaxWilson(String name, double moles, double molesInPhase, int compIndex) {
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
    return WaxModelCorrelations.solidFugacityCoefficient(this, phase1, Math.log(getWilsonActivityCoefficient(phase1)));
  }

  /**
   * getWilsonActivityCoefficient.
   *
   * @param phase1 a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getWilsonActivityCoefficient(PhaseInterface phase1) {
    if (!isWaxFormer()) {
      return 1.0;
    }
    double[] fractions = WaxModelCorrelations.composition(phase1);
    double sum1 = 0.0;
    double sum2 = 0.0;
    double tempSum = 0.0;

    for (int i = 0; i < phase1.getNumberOfComponents(); i++) {
      if (fractions[i] == 0.0) {
        continue;
      }
      sum1 += fractions[i]
          * ((ComponentWaxWilson) phase1.getComponent(i)).getCharEnergyParamter(phase1, this.getComponentNumber(), i);
      tempSum = 0.0;
      for (int j = 0; j < phase1.getNumberOfComponents(); j++) {
        if (fractions[j] == 0.0) {
          continue;
        }
        tempSum += fractions[j] * ((ComponentWaxWilson) phase1.getComponent(j)).getCharEnergyParamter(phase1, i, j);
      }
      sum2 += fractions[i]
          * ((ComponentWaxWilson) phase1.getComponent(i)).getCharEnergyParamter(phase1, i, this.getComponentNumber())
          / tempSum;
    }

    return WaxModelCorrelations.coefficient(1.0 - Math.log(sum1) - sum2, this, phase1);
  }

  /**
   * getCharEnergyParamter.
   *
   * @param phase1 a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param comp1 a int
   * @param comp2 a int
   * @return a double
   */
  public double getCharEnergyParamter(PhaseInterface phase1, int comp1, int comp2) {
    ComponentWaxWilson reference = (ComponentWaxWilson) phase1.getComponent(comp1);
    ComponentWaxWilson partner = (ComponentWaxWilson) phase1.getComponent(comp2);
    ComponentWaxWilson shorter = reference.getMolarMass() <= partner.getMolarMass() ? reference : partner;
    // Lambda_ij = exp(-(lambda_ij - lambda_ii)/(RT)); cross energy is the shorter chain's self energy.
    double energyDifference = shorter.getWilsonInteractionEnergy(phase1) - reference.getWilsonInteractionEnergy(phase1);
    return WaxModelCorrelations.coefficient(-energyDifference / (R * phase1.getTemperature()), this, phase1);
  }

  /**
   * Calculates the Wilson interaction energy parameter for this component in the wax phase.
   *
   * <p>
   * Based on the sublimation enthalpy: lambda_ii = -2/z * (DH_sub - RT). Uses Morgan-Kobayashi correlation for
   * vaporization enthalpy and Pedersen correlations for fusion properties. All molecular-weight-dependent correlations
   * use MW in g/mol.
   * </p>
   *
   * @param phase1 a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return Wilson interaction energy parameter in J/mol
   */
  public double getWilsonInteractionEnergy(PhaseInterface phase1) {
    double coordinationNumber = 6.0;

    double mw = getMolarMass() * 1000.0; // convert kg/mol to g/mol
    double carbonnumber = mw / 14.0;

    double omega = 0.0520750 + 0.0448946 * carbonnumber - 0.000185397 * carbonnumber * carbonnumber;
    double deltaHvap = WaxModelCorrelations.vaporizationEnthalpy(phase1.getTemperature(), getTC(), omega);

    // Total transition enthalpy (Won, 1989) [J/mol]
    double deltaHtot = (3.7791 * carbonnumber - 12.654) * 1000;

    // Melting temperature (Pedersen, 1991) [K] (MW in g/mol)
    double Tf = 374.5 + 0.02617 * mw - 20172.0 / mw;

    // Heat of fusion (Pedersen, 1991) [J/mol] (MW in g/mol, Hf in cal/mol -> J/mol)
    double deltaHf = 0.1426 * mw * Tf * 4.184;

    // Solid-solid transition enthalpy [J/mol]
    double deltaHtrans = deltaHtot - deltaHf;
    if (deltaHtrans < 0) {
      deltaHtrans = 0.0;
    }

    double deltaHsub = deltaHvap + deltaHf + deltaHtrans;

    return -2.0 / coordinationNumber * (deltaHsub - R * phase1.getTemperature());
  }
}
