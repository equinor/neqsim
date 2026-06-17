package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * ComponentWonWax class.
 * </p>
 *
 * @author rahmat
 * @version $Id: $Id
 */
public class ComponentWonWax extends ComponentSolid {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentWonWax.class);

  /**
   * <p>
   * Constructor for ComponentWonWax.
   * </p>
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
    refPhase.setTemperature(phase1.getTemperature());
    refPhase.setPressure(phase1.getPressure());
    refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 1, PhaseType.LIQUID, 1.0);
    refPhase.getComponent(0).fugcoef(refPhase);

    double liquidPhaseFugacity =
        refPhase.getComponent(0).getFugacityCoefficient() * refPhase.getPressure();

    // Solid-liquid volume change (Poynting correction)
    double liquidMolarVolume = refPhase.getMolarVolume();
    double solidMolarVolume = liquidMolarVolume * 0.9;
    double refPressure = 1.0; // bara
    double presTerm = -(liquidMolarVolume - solidMolarVolume) * (phase1.getPressure() - refPressure)
        / R / phase1.getTemperature();

    // Heat capacity difference solid-liquid (Pedersen et al., 1991)
    // DeltaCp_SL = 0.3033 * MW - 4.635e-4 * MW * T [cal/mol/K], converted to J/(mol*K)
    double mw = getMolarMass() * 1000.0; // g/mol
    double deltaCpSL = (0.3033 * mw - 4.635e-4 * mw * phase1.getTemperature()) * 4.184;
    double cpTerm = deltaCpSL / R * (getTriplePointTemperature() / phase1.getTemperature() - 1.0
        - Math.log(getTriplePointTemperature() / phase1.getTemperature()));

    // Won activity coefficient from solubility parameter model
    double solidActivityCoefficient = getWonActivityCoefficient(phase1);

    // Solid fugacity with DeltaCp and Poynting corrections
    SolidFug =
        getx() * liquidPhaseFugacity
            * Math.exp(-getHeatOfFusion() / (R * phase1.getTemperature())
                * (1.0 - phase1.getTemperature() / getTriplePointTemperature()) + cpTerm
                + presTerm);

    fugacityCoefficient = solidActivityCoefficient * SolidFug / (phase1.getPressure() * getx());
    return fugacityCoefficient;
  }

  /**
   * <p>
   * getWonActivityCoefficient.
   * </p>
   *
   * @param phase1 a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getWonActivityCoefficient(PhaseInterface phase1) {
    double TetaAvg = 0.0;
    double gamma = 0.0;
    for (int i = 0; i < phase1.getNumberOfComponents(); i++) {
      double tempSum = 0.0;
      for (int j = 0; j < phase1.getNumberOfComponents(); j++) {
        tempSum += phase1.getComponent(j).getx()
            * (((ComponentWonWax) phase1.getComponent(j)).getWonVolume(phase1));
      }
      TetaAvg += phase1.getComponent(i).getx()
          * (((ComponentWonWax) phase1.getComponent(i)).getWonVolume(phase1)) / tempSum
          * ((ComponentWonWax) phase1.getComponent(i)).getWonParam(phase1);
    }
    gamma = Math.exp(getWonVolume(phase1) * Math.pow((TetaAvg - getWonParam(phase1)), 2)
        / (1.9858775 * phase1.getTemperature()));

    return gamma;
  }

  /**
   * Calculates the liquid molar volume at 25 deg C for the Won solubility parameter model.
   *
   * <p>
   * Uses the density correlation: d25 = 0.8155 + 6.273e-4 * MW - 13.06 / MW (g/cm3, MW in g/mol).
   * Returns volume in cm3/mol.
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
   * delta_i = sqrt((DH_vap - DH_fus - RT) / V_i) in (cal/cm3)^0.5. Uses Morgan-Kobayashi
   * correlation for vaporization enthalpy and Pedersen correlations for fusion properties. All
   * molecular-weight-dependent correlations use MW in g/mol.
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

    // Enthalpy of vaporization (Morgan-Kobayashi, 1982) using Pitzer correlation
    // R = 1.9858775 cal/(mol*K)
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
    double carbonnumber = mw / 14.0;
    double omega = 0.0520750 + 0.0448946 * carbonnumber - 0.000185397 * carbonnumber * carbonnumber;
    double Hvap =
        1.9858775 * getTC() * (deltaHvap0 + omega * deltaHvap1 + omega * omega * deltaHvap2);

    // Molar volume at 25 deg C [cm3/mol]
    double d25 = 0.8155 + 0.6273e-4 * mw - 13.06 / mw;
    double vol = mw / d25;

    // Solubility parameter: delta = sqrt((Hvap - Hf - RT) / V) in (cal/cm3)^0.5
    return Math.sqrt((Hvap - Hf - 1.9858775 * phase1.getTemperature()) / vol);
  }
}
