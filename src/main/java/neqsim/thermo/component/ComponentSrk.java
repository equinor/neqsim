package neqsim.thermo.component;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.attractiveeosterm.AttractiveTermSrk;

/**
 * <p>
 * ComponentSrk class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentSrk extends ComponentEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double factTemp = Math.pow(2.0, 1.0 / 3.0);

  /**
   * <p>
   * Constructor for ComponentSrk.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentSrk(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);

    a = 1.0 / (9.0 * (Math.pow(2.0, 1.0 / 3.0) - 1.0)) * R * R * criticalTemperature
        * criticalTemperature / criticalPressure;
    b = (Math.pow(2.0, 1.0 / 3.0) - 1.0) / 3.0 * R * criticalTemperature / criticalPressure;
    delta1 = 1.0;
    delta2 = 0.0;
    // System.out.println("a " + a);
    // attractiveParameter = new AttractiveTermSchwartzentruber(this);
    setAttractiveParameter(new AttractiveTermSrk(this));

    double[] surfTensInfluenceParamtemp =
        {-0.7708158524, 0.4990571549, 0.8645478315, -0.3509810630, -0.1611763157};
    this.surfTensInfluenceParam = surfTensInfluenceParamtemp;
  }

  /**
   * <p>
   * Constructor for ComponentSrk.
   * </p>
   *
   * @param number a int. Not used.
   * @param TC Critical temperature [K]
   * @param PC Critical pressure [bara]
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Total number of moles of component.
   */
  public ComponentSrk(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentSrk clone() {
    ComponentSrk clonedComponent = null;
    try {
      clonedComponent = (ComponentSrk) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedComponent;
  }

  /** {@inheritDoc} */
  @Override
  public double getVolumeCorrection() {
    if (hasVolumeCorrection()) {
      return super.getVolumeCorrection();
    }
    if (ionicCharge != 0) {
      return 0.0;
    }
    if (Math.abs(getVolumeCorrectionConst()) > 1.0e-10) {
      return getVolumeCorrectionConst() * b;
    }
    if (Math.abs(this.getRacketZ()) < 1e-10) {
      racketZ = 0.29056 - 0.08775 * getAcentricFactor();
    }
    // System.out.println("racket Z " + racketZ + " vol correction " +
    // (0.40768*(0.29441-this.getRacketZ())*R*criticalTemperature/criticalPressure));
    return 0.40768 * (0.29441 - this.getRacketZ()) * R * criticalTemperature / criticalPressure;
  }

  /** {@inheritDoc} */
  @Override
  public double calca() {
    return 1.0 / (9.0 * (factTemp - 1.0)) * R * R * criticalTemperature * criticalTemperature
        / criticalPressure;
  }

  /** {@inheritDoc} */
  @Override
  public double calcb() {
    return (factTemp - 1.0) / 3.0 * R * criticalTemperature / criticalPressure;
  }

  /**
   * <p>
   * getQpure.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getQpure(double temperature) {
    return this.getaT() / (this.getb() * R * temperature);
  }

  /**
   * <p>
   * getdQpuredT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getdQpuredT(double temperature) {
    return dqPuredT;
  }

  /**
   * <p>
   * getdQpuredTdT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getdQpuredTdT(double temperature) {
    return dqPuredTdT;
  }

  /** {@inheritDoc} */
  @Override
  public double getSurfaceTenisionInfluenceParameter(double temperature) {
    double TR = 1.0 - temperature / getTC();
    if (TR < 0) {
      if (componentName.equals("CO2")) {
        TR = 1.0 - 0.9;
      } else {
        TR = 0.6;
      }
    }
    double AA = (surfTensInfluenceParam[0] + surfTensInfluenceParam[1] * getAcentricFactor());
    double BB = surfTensInfluenceParam[2] + surfTensInfluenceParam[3] * getAcentricFactor()
        + surfTensInfluenceParam[4] * getAcentricFactor() * getAcentricFactor();

    if (componentName.equals("water")) {
      return 0.000000000000000000019939776242117276;
    }
    if (componentName.equals("MEG")) {
      return 0.00000000000000000009784248343727627;
    }
    if (componentName.equals("TEG")) {
      return 0.000000000000000000901662233371129;
    }
    // System.out.println("scale2 " + aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) *
    // Math.pow(AA * TR, BB)/1.0e17);
    // System.out.println("scale2 " +
    // Math.pow(ThermodynamicConstantsInterface.avagadroNumber, 2.0 / 3.0));
    return (aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) * (AA * TR + BB))
        / Math.pow(ThermodynamicConstantsInterface.avagadroNumber, 2.0 / 3.0);
  }
}
