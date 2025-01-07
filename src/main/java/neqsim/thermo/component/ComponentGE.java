/*
 * ComponentGE.java
 *
 * Created on 10. juli 2000, 21:05
 */

package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseGE;
import neqsim.thermo.phase.PhaseInterface;

/**
 * Abstract class ComponentGE.
 *
 * @author Even Solbraa
 */
public abstract class ComponentGE extends Component implements ComponentGEInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected double gamma = 0;
  protected double gammaRefCor = 0;
  protected double lngamma = 0;
  protected double dlngammadt = 0;
  protected double dlngammadp = 0;
  protected double dlngammadtdt = 0.0;
  protected double[] dlngammadn;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentGE.class);

  /**
   * <p>
   * Constructor for ComponentGE.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentGE(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    logger.info("fug coef "
        + gamma * getAntoineVaporPressure(phase.getTemperature()) / phase.getPressure());
    if (referenceStateType.equals("solvent")) {
      fugacityCoefficient =
          gamma * getAntoineVaporPressure(phase.getTemperature()) / phase.getPressure();
      gammaRefCor = gamma;
    } else {
      double activinf = 1.0;
      if (phase.hasComponent("water")) {
        int waternumb = phase.getComponent("water").getComponentNumber();
        activinf =
            gamma / ((PhaseGE) phase).getActivityCoefficientInfDilWater(componentNumber, waternumb);
      } else {
        activinf = gamma / ((PhaseGE) phase).getActivityCoefficientInfDil(componentNumber);
      }
      fugacityCoefficient = activinf * getHenryCoef(phase.getTemperature()) / phase.getPressure();
      // gamma* benyttes ikke
      gammaRefCor = activinf;
    }

    return fugacityCoefficient;
  }

  /**
   * <p>
   * fugcoefDiffPres.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double fugcoefDiffPres(PhaseInterface phase) {
    // double temperature = phase.getTemperature(), pressure = phase.getPressure();
    // int numberOfComponents = phase.getNumberOfComponents();
    if (referenceStateType.equals("solvent")) {
      dfugdp = 0.0; // forelopig uten pointing
    } else {
      dfugdp = 0.0; // forelopig uten pointing
    }
    return dfugdp;
  }

  /**
   * <p>
   * fugcoefDiffTemp.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double fugcoefDiffTemp(PhaseInterface phase) {
    double temperature = phase.getTemperature();
    // double pressure = phase.getPressure();
    // int numberOfComponents = phase.getNumberOfComponents();

    if (referenceStateType.equals("solvent")) {
      dfugdt = dlngammadt
          + 1.0 / getAntoineVaporPressure(temperature) * getAntoineVaporPressuredT(temperature);
      logger.info("check this dfug dt - antoine");
    } else {
      dfugdt = dlngammadt + getHenryCoefdT(temperature);
    }
    return dfugdt;
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma() {
    return gamma;
  }

  /** {@inheritDoc} */
  @Override
  public double getlnGamma() {
    return lngamma;
  }

  /** {@inheritDoc} */
  @Override
  public double getlnGammadt() {
    return dlngammadt;
  }

  /** {@inheritDoc} */
  @Override
  public double getlnGammadtdt() {
    return dlngammadtdt;
  }

  /** {@inheritDoc} */
  @Override
  public double getlnGammadn(int k) {
    return dlngammadn[k];
  }

  /** {@inheritDoc} */
  @Override
  public void setlnGammadn(int k, double val) {
    dlngammadn[k] = val;
  }

  /** {@inheritDoc} */
  @Override
  public double getGammaRefCor() {
    return gammaRefCor;
  }
}
