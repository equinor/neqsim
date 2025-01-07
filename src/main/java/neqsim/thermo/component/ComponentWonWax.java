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
    refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 1, PhaseType.byValue(0), 1.0);
    refPhase.getComponent(0).fugcoef(refPhase);

    double liquidPhaseFugacity =
        refPhase.getComponent(0).getFugacityCoefficient() * refPhase.getPressure();

    double solidActivityCoefficient = getWonActivityCoefficient(phase1);
    logger.info("activity coef Won " + solidActivityCoefficient);
    SolidFug =
        getx() * liquidPhaseFugacity * Math.exp(-getHeatOfFusion() / (R * phase1.getTemperature())
            * (1.0 - phase1.getTemperature() / getTriplePointTemperature()));

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
   * <p>
   * getWonVolume.
   * </p>
   *
   * @param phase1 a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getWonVolume(PhaseInterface phase1) {
    double d25 = 0.8155 + 0.6273e-4 * getMolarMass() - 13.06 / getMolarMass();

    return getMolarMass() / d25;
  }

  /**
   * <p>
   * getWonParam.
   * </p>
   *
   * @param phase1 a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getWonParam(PhaseInterface phase1) {
    // calculation of Heat of fusion
    double Tf = 374.5 * 0.02617 * getMolarMass() - 20172 / getMolarMass();
    double Hf = 0.1426 * getMolarMass() * Tf;

    // calculation of Enthalpy of evaporation
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
    double carbonnumber = getMolarMass() / 0.014;
    double omega = 0.0520750 + 0.0448946 * carbonnumber - 0.000185397 * carbonnumber * carbonnumber;
    double Hvap =
        1.9858775 * getTC() * (deltaHvap0 + omega * deltaHvap1 + omega * omega * deltaHvap2);
    return Math.sqrt((Hvap - Hf - 1.9858775 * phase1.getTemperature())
        / (getMolarMass() / (0.8155 + 0.6273e-4 * getMolarMass() - 13.06 / getMolarMass())));
  }
}
