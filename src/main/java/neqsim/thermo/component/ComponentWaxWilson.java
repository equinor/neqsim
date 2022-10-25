/*
 * SolidComponent.java
 *
 * Created on 18. august 2001, 12:45
 */

package neqsim.thermo.component;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentWaxWilson class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ComponentWaxWilson extends ComponentSolid {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ComponentWaxWilson.
   * </p>
   *
   * @param component_name a {@link java.lang.String} object
   * @param moles a double
   * @param molesInPhase a double
   * @param compnumber a int
   */
  public ComponentWaxWilson(String component_name, double moles, double molesInPhase,
      int compnumber) {
    super(component_name, moles, molesInPhase, compnumber);
  }

  /**
   * {@inheritDoc}
   *
   * Uses Claperyons equation to calculate the solid fugacity
   */
  @Override
  public double fugcoef(PhaseInterface phase1) {
    if (!isWaxFormer()) {
      // fugacityCoefficient = 1.0e30;
      // logFugacityCoefficient = Math.log(fugacityCoefficient);
      return 1.0e30;
    }
    return fugcoef2(phase1);
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef2(PhaseInterface phase1) {
    refPhase.setTemperature(phase1.getTemperature());
    refPhase.setPressure(phase1.getPressure());
    refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 1, 0, 1.0);
    refPhase.getComponent(0).fugcoef(refPhase);

    double liquidPhaseFugacity =
        refPhase.getComponent(0).getFugacityCoefficient() * refPhase.getPressure();

    // calculating and setting heat of fusion
    double Tf = 374.5 + 0.2617 * getMolarMass() - 20.172 / getMolarMass();
    double Hf = (0.1426 * getMolarMass() * Tf) * 4.184 * 1000;
    setHeatOfFusion(Hf);

    // calculating delta Cp
    double deltaCpSL =
        (0.3033 * getMolarMass() - 4.635e-4 * getMolarMass() * phase1.getTemperature()) * 4.1868;

    // poynting corretion will be insignificant at low pressures ????? skipping that
    // for now
    double solMolVol = 0.0;
    double liqMolVol = 0.0;
    double deltaSolVol = (solMolVol - liqMolVol);

    // calculating activity coefficient according to Wilson
    double solidActivityCoefficient = getWilsonActivityCoefficient(phase1);
    // SolidFug = getx() * liquidPhaseFugacity * Math.exp((-getHeatOfFusion() / (R *
    // phase1.getTemperature()) * (1.0 - phase1.getTemperature() /
    // getTriplePointTemperature())) );

    SolidFug = getx() * liquidPhaseFugacity
        * Math.exp(-getHeatOfFusion() / (R * phase1.getTemperature())
            * (1.0 - phase1.getTemperature() / getTriplePointTemperature())
            + deltaCpSL
                / (R * phase1.getTemperature())
                * (getTriplePointTemperature() - phase1.getTemperature())
            - deltaCpSL / R * Math.log(getTriplePointTemperature() / phase1.getTemperature())
            - deltaSolVol * (1.0 - phase1.getPressure()) / (R * phase1.getTemperature()));

    fugacityCoefficient = solidActivityCoefficient * SolidFug / (phase1.getPressure() * getx());
    logFugacityCoefficient = Math.log(fugacityCoefficient);
    return fugacityCoefficient;
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
      sum1 += phase1.getComponent(i).getx() * ((ComponentWaxWilson) phase1.getComponent(i))
          .getCharEnergyParamter(phase1, this.getComponentNumber(), i);
      tempSum = 0.0;
      for (int j = 0; j < phase1.getNumberOfComponents(); j++) {
        tempSum += phase1.getComponent(j).getx()
            * ((ComponentWaxWilson) phase1.getComponent(j)).getCharEnergyParamter(phase1, i, j);
      }
      sum2 += phase1.getComponent(i).getx() * ((ComponentWaxWilson) phase1.getComponent(i))
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
    double param1 = 0.0; // ((ComponentWaxWilson)
                         // phase1.getComponent(comp1)).getWilsonInteractionEnergy(phase1);
    double param2 = 0.0; // ((ComponentWaxWilson)
                         // phase1.getComponent(comp2)).getWilsonInteractionEnergy(phase1);
    // this need to be corrected accordint to how to select energy of shortest
    // carbon molecule .....
    if ((phase1.getComponent(comp1).getMolarMass() - 1.0e-10) > phase1.getComponent(comp2)
        .getMolarMass()) {
      // enthalpy sublimation nC16 134.9kj/mol; nC14 117.6kj/mol
      // param1 = -2/6*(117.6 * 1e3 - R*phase1.getTemperature());
      // param2 = -2/6*(134.9 * 1e3 - R*phase1.getTemperature());
      param1 = ((ComponentWaxWilson) phase1.getComponent(comp2)).getWilsonInteractionEnergy(phase1);
      param2 = ((ComponentWaxWilson) phase1.getComponent(comp1)).getWilsonInteractionEnergy(phase1);
    } else {
      param1 = 1 - 0;
      param2 = 1.0;
    }

    return Math
        .exp(-(param2 - param1) / (ThermodynamicConstantsInterface.R * phase1.getTemperature()));
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
            - 638.51 * x - 145.12 * Math.pow(x, 2.0) + 74.049 * Math.pow(x, 3.0);
    double deltaHvap2 =
        7.2543 * Math.pow(x, 0.3333) - 346.45 * Math.pow(x, 0.8333) - 610.48 * Math.pow(x, 1.2083)
            + 839.89 * x + 160.05 * Math.pow(x, 2.0) - 50.711 * Math.pow(x, 3.0);

    double omega = 0.0520750 + 0.0448946 * carbonnumber - 0.000185397 * carbonnumber * carbonnumber;

    double deltaHvap =
        R * getTC() * ((deltaHvap0 + omega * deltaHvap1 + omega * omega * deltaHvap2) * 4.1868);

    // calculating transition enthalpy
    double deltaHtot = (3.7791 * carbonnumber - 12.654) * 1000;

    /// should not be a cooma - cirrected Tosin 08.05.2013

    // double Ttrans = 420.42 - 134784.0 * Math.exp(-4.344 * Math.pow(carbonnumber + 6.592,
    /// 0.14627));
    double Tf = 374.5 + 0.2617 * getMolarMass() - 20.172 / getMolarMass();
    double deltaHf = (0.1426 * getMolarMass() * Tf) * 4.1868;
    double deltaHtrans = (deltaHtot - deltaHf);

    double deltaHsub = (deltaHvap + deltaHf + deltaHtrans);

    return -2.0 / coordinationNumber * (deltaHsub - R * phase1.getTemperature());
  }
}
