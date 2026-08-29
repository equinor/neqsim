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
  /** Finite fail-closed Henry coefficient used for unsupported or invalid solutes, in bar. */
  protected static final double INSOLUBLE_HENRY_COEFFICIENT = 1.0e12;

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
   * Constructor for ComponentGE.
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
    logger.info("fug coef " + gamma * getAntoineVaporPressure(phase.getTemperature()) / phase.getPressure());
    if (referenceStateType.equals("solvent")) {
      fugacityCoefficient = gamma * getAntoineVaporPressure(phase.getTemperature()) / phase.getPressure();
      gammaRefCor = gamma;
    } else {
      double activinf = 1.0;
      if (phase.hasComponent("water")) {
        int waternumb = phase.getComponent("water").getComponentNumber();
        activinf = gamma / ((PhaseGE) phase).getActivityCoefficientInfDilWater(componentNumber, waternumb);
      } else {
        activinf = gamma / ((PhaseGE) phase).getActivityCoefficientInfDil(componentNumber);
      }
      double henryCoef = getEffectiveHenryCoefficient(phase);
      fugacityCoefficient = activinf * henryCoef / phase.getPressure();
      // gamma* benyttes ikke
      gammaRefCor = activinf;
    }

    return fugacityCoefficient;
  }

  /**
   * Returns the Henry coefficient used by the aqueous GE reference state.
   *
   * <p>
   * Invalid, non-positive, unsupported ionic, and excessively large correlations fail closed to a finite insoluble
   * limit. Model-specific GE components may extend the unsupported-species decision.
   * </p>
   *
   * @param temperature temperature in K
   * @return effective Henry coefficient in bar
   */
  protected double getEffectiveHenryCoefficient(double temperature) {
    double henryCoefficient = getHenryCoef(temperature);
    return isHenryCoefficientCapped(henryCoefficient) ? INSOLUBLE_HENRY_COEFFICIENT : henryCoefficient;
  }

  /**
   * Returns the phase-aware Henry coefficient, preferring the qualified IAPWS pure-water reference.
   *
   * <p>The IAPWS value is selected only for a supported neutral solute in a water-containing phase.
   * Temperatures outside the liquid-water domain fail closed. Species outside the IAPWS table retain
   * the legacy database behavior.</p>
   *
   * @param phase phase containing temperature and solvent topology
   * @return effective mole-fraction Henry coefficient in bar
   */
  protected double getEffectiveHenryCoefficient(PhaseInterface phase) {
    if (phase.hasComponent("water") && !isIsIon()) {
      IapwsHenryLaw.Assessment assessment =
          IapwsHenryLaw.assess(getComponentName(), phase.getTemperature());
      if (assessment.isUsable()) {
        return IapwsHenryLaw.getHenryCoefficientBar(getComponentName(), phase.getTemperature());
      }
      if (assessment.isSupportedSpecies()) {
        return INSOLUBLE_HENRY_COEFFICIENT;
      }
    }
    return getEffectiveHenryCoefficient(phase.getTemperature());
  }

  /**
   * Tests whether a raw Henry correlation must fail closed.
   *
   * @param henryCoefficient raw Henry coefficient in bar
   * @return {@code true} when the effective reference is the finite insoluble limit
   */
  protected boolean isHenryCoefficientCapped(double henryCoefficient) {
    return !Double.isFinite(henryCoefficient) || henryCoefficient <= 0.0
        || henryCoefficient > INSOLUBLE_HENRY_COEFFICIENT || isIsIon();
  }

  /**
   * Returns the logarithmic temperature derivative of the effective Henry reference.
   *
   * <p>
   * Fugacity-coefficient derivatives are derivatives of {@code ln(phi)}. The database API returns {@code dH/dT}, so an
   * active correlation contributes {@code (dH/dT)/H}. A fail-closed constant contributes zero.
   * </p>
   *
   * @param temperature temperature in K
   * @return {@code d(ln H)/dT} in 1/K
   */
  protected double getLnHenryCoefficientTemperatureDerivative(double temperature) {
    double henryCoefficient = getHenryCoef(temperature);
    if (isHenryCoefficientCapped(henryCoefficient)) {
      return 0.0;
    }
    return getHenryCoefdT(temperature) / henryCoefficient;
  }

  /**
   * Returns the phase-aware logarithmic derivative for the selected Henry reference.
   *
   * @param phase phase containing temperature and solvent topology
   * @return d(ln H)/dT in 1/K
   */
  protected double getLnHenryCoefficientTemperatureDerivative(PhaseInterface phase) {
    if (phase.hasComponent("water") && !isIsIon()) {
      IapwsHenryLaw.Assessment assessment =
          IapwsHenryLaw.assess(getComponentName(), phase.getTemperature());
      if (assessment.isUsable()) {
        return IapwsHenryLaw.getLnHenryCoefficientTemperatureDerivative(
            getComponentName(), phase.getTemperature());
      }
      if (assessment.isSupportedSpecies()) {
        return 0.0;
      }
    }
    return getLnHenryCoefficientTemperatureDerivative(phase.getTemperature());
  }

  /**
   * fugcoefDiffPres.
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
   * fugcoefDiffTemp.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double fugcoefDiffTemp(PhaseInterface phase) {
    double temperature = phase.getTemperature();
    // double pressure = phase.getPressure();
    // int numberOfComponents = phase.getNumberOfComponents();

    if (referenceStateType.equals("solvent")) {
      dfugdt = dlngammadt + 1.0 / getAntoineVaporPressure(temperature) * getAntoineVaporPressuredT(temperature);
      logger.info("check this dfug dt - antoine");
    } else {
      dfugdt = dlngammadt + getLnHenryCoefficientTemperatureDerivative(phase);
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
  public double getLnGamma() {
    return lngamma;
  }

  /** {@inheritDoc} */
  @Override
  public double getLnGammadt() {
    return dlngammadt;
  }

  /** {@inheritDoc} */
  @Override
  public double getLnGammadtdt() {
    return dlngammadtdt;
  }

  /** {@inheritDoc} */
  @Override
  public double getLnGammadn(int k) {
    return dlngammadn[k];
  }

  /** {@inheritDoc} */
  @Override
  public void setLnGammadn(int k, double val) {
    dlngammadn[k] = val;
  }

  /** {@inheritDoc} */
  @Override
  public double getGammaRefCor() {
    return gammaRefCor;
  }
}
