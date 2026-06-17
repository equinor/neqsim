package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * ComponentWaxWilson class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ComponentWaxWilson extends ComponentSolid {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentWaxWilson.class);

  /**
   * <p>
   * Constructor for ComponentWaxWilson.
   * </p>
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
    refPhase.setTemperature(phase1.getTemperature());
    refPhase.setPressure(phase1.getPressure());
    refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 1, PhaseType.LIQUID, 1.0);
    refPhase.getComponent(0).fugcoef(refPhase);

    double liquidPhaseFugacity =
        refPhase.getComponent(0).getFugacityCoefficient() * refPhase.getPressure();

    // Melting temperature and heat of fusion using Pedersen (1991) correlation
    // MW in g/mol for the correlations
    double mw = getMolarMass() * 1000.0; // convert kg/mol to g/mol
    double Tf = 374.5 + 0.02617 * mw - 20172.0 / mw;
    double Hf = 0.1426 * mw * Tf * 4.184; // cal/mol -> J/mol
    setHeatOfFusion(Hf);

    // Heat capacity difference solid-liquid (Pedersen et al., 1991)
    // DeltaCp_SL = 0.3033 * MW - 4.635e-4 * MW * T [cal/mol/K], converted to J/(mol*K)
    double deltaCpSL = (0.3033 * mw - 4.635e-4 * mw * phase1.getTemperature()) * 4.184;

    // Poynting correction: solid-liquid volume change
    double liquidMolarVolume = refPhase.getMolarVolume();
    double solidMolarVolume = liquidMolarVolume * 0.9;
    double deltaSolVol = (solidMolarVolume - liquidMolarVolume);

    // Wilson activity coefficient for solid solution
    double solidActivityCoefficient = getWilsonActivityCoefficient(phase1);

    SolidFug = getx() * liquidPhaseFugacity
        * Math.exp(-getHeatOfFusion() / (R * phase1.getTemperature())
            * (1.0 - phase1.getTemperature() / getTriplePointTemperature())
            + deltaCpSL / R
                * (getTriplePointTemperature() / phase1.getTemperature() - 1.0
                    - Math.log(getTriplePointTemperature() / phase1.getTemperature()))
            - deltaSolVol * (phase1.getPressure() - 1.0) * 1e5 / (R * phase1.getTemperature()));

    fugacityCoefficient = solidActivityCoefficient * SolidFug / (phase1.getPressure() * getx());
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
    double param1 = 0.0;
    // ((ComponentWaxWilson) phase1.getComponent(comp1)).getWilsonInteractionEnergy(phase1);
    double param2 = 0.0;
    // ((ComponentWaxWilson) phase1.getComponent(comp2)).getWilsonInteractionEnergy(phase1);

    // this need to be corrected according to how to select energy of shortest
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
   * Calculates the Wilson interaction energy parameter for this component in the wax phase.
   *
   * <p>
   * Based on the sublimation enthalpy: lambda_ii = -2/z * (DH_sub - RT). Uses Morgan-Kobayashi
   * correlation for vaporization enthalpy and Pedersen correlations for fusion properties. All
   * molecular-weight-dependent correlations use MW in g/mol.
   * </p>
   *
   * @param phase1 a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return Wilson interaction energy parameter in J/mol
   */
  public double getWilsonInteractionEnergy(PhaseInterface phase1) {
    double coordinationNumber = 6.0;

    double mw = getMolarMass() * 1000.0; // convert kg/mol to g/mol
    double carbonnumber = mw / 14.0;

    // Vaporization enthalpy (Morgan-Kobayashi, 1982) using Pitzer correlation
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

    // R * Tc * f(tau, omega) gives deltaHvap in J/mol (since R = 8.314 J/(mol*K))
    double deltaHvap = R * getTC() * (deltaHvap0 + omega * deltaHvap1 + omega * omega * deltaHvap2);

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
