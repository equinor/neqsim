package neqsim.thermo.component;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * ComponentGEWilson class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentGEWilson extends ComponentGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ComponentGEWilson.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentGEWilson(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /**
   * <p>
   * Calculate, set and return fugacity coefficient.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object to get fugacity coefficient
   *        of.
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param pt the PhaseType of the phase
   * @return Fugacity coefficient
   */
  public double fugcoef(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt) {
    fugacityCoefficient = (this.getGamma(phase, numberOfComponents, temperature, pressure, pt)
        * this.getAntoineVaporPressure(temperature) / pressure);
    return fugacityCoefficient;
  }

  /**
   * <p>
   * getGamma.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param pt the PhaseType of the phase
   * @return a double
   */
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt) {
    return getWilsonActivityCoefficient(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt, double[][] HValpha, double[][] HVgij, double[][] intparam,
      String[][] mixRule) {

    return 0.0;
  }

  /**
   * <p>
   * getWilsonActivityCoefficient.
   * </p>
   *
   * @param phase1 a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getWilsonActivityCoefficient(PhaseInterface phase1) {
    double sum1 = 0.0;
    double sum2 = 0.0;
    double tempSum = 0.0;
    for (int i = 0; i < phase1.getNumberOfComponents(); i++) {
      sum1 += phase1.getComponent(i).getx() * ((ComponentGEWilson) phase1.getComponent(i))
          .getCharEnergyParamter(phase1, this.getComponentNumber(), i);
      tempSum = 0.0;
      for (int j = 0; j < phase1.getNumberOfComponents(); j++) {
        tempSum += phase1.getComponent(j).getx()
            * ((ComponentGEWilson) phase1.getComponent(j)).getCharEnergyParamter(phase1, i, j);
      }
      sum2 += phase1.getComponent(i).getx() * ((ComponentGEWilson) phase1.getComponent(i))
          .getCharEnergyParamter(phase1, i, this.getComponentNumber()) / tempSum;
    }
    return Math.exp(1.0 - Math.log(sum1) - sum2);
  }

  /**
   * <p>
   * getCharEnergyParamter.
   * </p>
   *
   * @param phase1 a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param comp1 a int
   * @param comp2 a int
   * @return a double
   */
  public double getCharEnergyParamter(PhaseInterface phase1, int comp1, int comp2) {
    double param1 = 0.0;
    // ((ComponentWaxWilson) phase1.getComponent(comp1)).getWilsonInteractionEnergy(phase1);
    double param2 = 0.0;
    // ((ComponentWaxWilson) phase1.getComponent(comp2)).getWilsonInteractionEnergy(phase1);
    if (comp1 == comp2) {
      return 1.0;
    }
    // this need to be corrected according to how to select energy of shortest
    // carbon molecule .....
    if (phase1.getComponent(comp1).getMolarMass() > phase1.getComponent(comp2).getMolarMass()) {
      param1 = ((ComponentGEWilson) phase1.getComponent(comp2)).getWilsonInteractionEnergy(phase1);
      param2 = ((ComponentGEWilson) phase1.getComponent(comp2)).getWilsonInteractionEnergy(phase1);
      // } else if (comp1 < comp2) {
      // param1 = -2.0 / 6.0 * (154.9 * 1e3 - R * phase1.getTemperature());
      // param2 = -2.0 / 6.0 * (107.6 * 1e3 - R * phase1.getTemperature());
      // param1 = ((ComponentWaxWilson)
      // phase1.getComponent(comp1)).getWilsonInteractionEnergy(phase1);
      // param2 = ((ComponentWaxWilson)
      // phase1.getComponent(comp2)).getWilsonInteractionEnergy(phase1);
    } else {
      param1 = ((ComponentGEWilson) phase1.getComponent(comp1)).getWilsonInteractionEnergy(phase1);
      param2 = ((ComponentGEWilson) phase1.getComponent(comp2)).getWilsonInteractionEnergy(phase1);
    }
    double energyParameter = Math
        .exp(-(param2 - param1) / (ThermodynamicConstantsInterface.R * phase1.getTemperature()));
    // System.out.println("energyy parameter " +energyParameter);
    return energyParameter;
  }

  /**
   * <p>
   * getWilsonInteractionEnergy.
   * </p>
   *
   * @param phase1 a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getWilsonInteractionEnergy(PhaseInterface phase1) {
    double coordinationNumber = 6.0;

    double carbonnumber = getMolarMass() / 0.014;
    // calculating vaporization enthalpy
    double x = 1.0 - phase1.getTemperature() / getTC();
    double deltaHvap0 =
        5.2804 * Math.pow(x, 0.3333) + 12.865 * Math.pow(x, 0.8333) + 1.171 * Math.pow(x, 1.2083)
            - 13.166 * x + 0.4858 * Math.pow(x, 2.0) - 1.088 * Math.pow(x, 3.0);
    double deltaHvap1 =
        0.80022 * Math.pow(x, 0.3333) + 273.23 * Math.pow(x, 0.8333) + 465.08 * Math.pow(x, 1.2083)
            - 638.51 * x - 145.12 * Math.pow(x, 2.0) - 74.049 * Math.pow(x, 3.0);
    double deltaHvap2 =
        7.2543 * Math.pow(x, 0.3333) - 346.45 * Math.pow(x, 0.8333) - 610.48 * Math.pow(x, 1.2083)
            + 839.89 * x + 160.05 * Math.pow(x, 2.0) - 50.711 * Math.pow(x, 3.0);

    double omega = 0.0520750 + 0.0448946 * carbonnumber - 0.000185397 * carbonnumber * carbonnumber;

    double deltaHvap =
        R * getTC() * (deltaHvap0 + omega * deltaHvap1 + omega * omega * deltaHvap2) * 4.1868;

    // calculating transition enthalpy

    double deltaHtot = (3.7791 * carbonnumber - 12.654) * 1000;
    // double Ttrans = 420.42 - 134.784 * Math.exp(-4.344 * Math.pow(carbonnumber + 6.592,
    // 0.14627));

    double Tf = 374.5 + 0.2617 * getMolarMass() - 20.172 / getMolarMass();
    double deltaHf = (0.1426 * getMolarMass() * Tf) * 4.1868;
    double deltaHtrans = (deltaHtot - deltaHf);

    double deltaHsub = (deltaHvap + deltaHf + deltaHtrans);

    return -2.0 / coordinationNumber * (deltaHsub - R * phase1.getTemperature());
  }
}
