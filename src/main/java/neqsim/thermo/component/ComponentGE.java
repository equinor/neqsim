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
    if (!usesHenryReference(phase)) {
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
   * @return effective mole-fraction Henry coefficient in bar
   */
  protected double getEffectiveHenryCoefficient(double temperature) {
    double henryCoefficient = getHenryCoef(temperature) / IapwsHenryLaw.WATER_MOLAR_MASS_KG_PER_MOL;
    return isHenryCoefficientCapped(henryCoefficient) ? INSOLUBLE_HENRY_COEFFICIENT : henryCoefficient;
  }

  /**
   * Returns the phase-aware Henry coefficient, preferring the qualified IAPWS pure-water reference.
   *
   * <p>
   * The IAPWS value is selected only for a supported neutral solute in a water-containing phase. Temperatures outside
   * the liquid-water domain fail closed. Species outside the IAPWS table retain the legacy database behavior.
   * </p>
   *
   * @param phase phase containing temperature and solvent topology
   * @return effective mole-fraction Henry coefficient in bar
   */
  protected double getEffectiveHenryCoefficient(PhaseInterface phase) {
    if (!isIsIon() && IapwsHenryLaw.isSupportedSpecies(getComponentName()) && phase.hasComponent("water")) {
      if (IapwsHenryLaw.isUsable(getComponentName(), phase.getTemperature())) {
        return IapwsHenryLaw.getHenryCoefficientBar(getComponentName(), phase.getTemperature());
      }
      return INSOLUBLE_HENRY_COEFFICIENT;
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
    if (isHenryCoefficientCapped(henryCoefficient)
        || getEffectiveHenryCoefficient(temperature) >= INSOLUBLE_HENRY_COEFFICIENT) {
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
    if (!isIsIon() && IapwsHenryLaw.isSupportedSpecies(getComponentName()) && phase.hasComponent("water")) {
      if (IapwsHenryLaw.isUsable(getComponentName(), phase.getTemperature())) {
        return IapwsHenryLaw.getLnHenryCoefficientTemperatureDerivative(getComponentName(), phase.getTemperature());
      }
      return 0.0;
    }
    return getLnHenryCoefficientTemperatureDerivative(phase.getTemperature());
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHydrocarbon() {
    return super.isHydrocarbon() || hasHydrocarbonFormula();
  }

  /**
   * Check the database molecular formula for a pure hydrocarbon.
   *
   * <p>
   * Some GE initialization paths classify a normal database component as {@code normal} instead of {@code HC}. The
   * formula check keeps aqueous-reference and parameter-topology decisions independent of that initialization detail.
   * </p>
   *
   * @return {@code true} when the formula contains carbon and hydrogen only
   */
  private boolean hasHydrocarbonFormula() {
    String formula = getFormulae();
    if (formula == null || formula.isEmpty()) {
      return false;
    }
    boolean carbon = false;
    boolean hydrogen = false;
    for (int index = 0; index < formula.length(); index++) {
      char character = formula.charAt(index);
      if (character == 'C') {
        carbon = true;
      } else if (character == 'H') {
        hydrogen = true;
      } else if (!Character.isDigit(character)) {
        return false;
      }
    }
    return carbon && hydrogen;
  }

  /**
   * Tests whether this component uses a Henry rather than a solvent vapor-pressure reference in a phase.
   *
   * @param phase owning GE phase
   * @return true for a solute Henry reference
   */
  protected boolean usesHenryReference(PhaseInterface phase) {
    // Water correlations are not references for a water-free, subcritical pure liquid or organic solution.
    // Keep the legacy finite unsupported limit for species without an applicable liquid-vapor correlation.
    if (!isIsIon() && phase.getNumberOfComponents() > componentNumber && !phase.hasComponent("water")
        && Double.isFinite(getAntoineVaporPressure(phase.getTemperature()))) {
      return false;
    }
    return !referenceStateType.equals("solvent") || usesAqueousSoluteReference(phase);
  }

  /** Tests whether a dissolved gas or hydrocarbon overrides a legacy solvent classification. */
  private boolean usesAqueousSoluteReference(PhaseInterface phase) {
    return !"water".equalsIgnoreCase(getComponentName())
        && (IapwsHenryLaw.isSupportedSpecies(getComponentName()) || isHydrocarbon()) && phase.hasComponent("water");
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdP(PhaseInterface phase) {
    return fugcoefDiffPres(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdT(PhaseInterface phase) {
    return fugcoefDiffTemp(phase);
  }

  /**
   * Calculates the analytical pressure derivative of the logarithmic GE fugacity coefficient.
   *
   * <p>
   * Every GE reference implemented here has {@code phi = gamma * reference / P}. With no Poynting correction in the
   * model, the pressure derivative is therefore {@code d(ln gamma)/dP - 1/P}. A future pressure-dependent reference
   * must add its own derivative without removing the explicit denominator term.
   * </p>
   *
   * @param phase phase containing pressure in bar
   * @return d(ln(phi))/dP in 1/bar
   */
  public double fugcoefDiffPres(PhaseInterface phase) {
    dfugdp = dlngammadp - 1.0 / phase.getPressure();
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

    if (!usesHenryReference(phase)) {
      dfugdt = getLnActivityTemperatureDerivative(phase)
          + getAntoineVaporPressuredT(temperature) / getAntoineVaporPressure(temperature);
      logger.info("check this dfug dt - antoine");
    } else {
      dfugdt = getLnActivityTemperatureDerivative(phase) + getLnHenryCoefficientTemperatureDerivative(phase);
      if (phase.hasComponent("water") && phase.getNumberOfComponents() > componentNumber) {
        dfugdt -= ((PhaseGE) phase).getLnActivityCoefficientInfDilWaterTemperatureDerivative(componentNumber,
            phase.getComponent("water").getComponentNumber());
      } else if (phase.getNumberOfComponents() > componentNumber) {
        dfugdt -= ((PhaseGE) phase).getLnActivityCoefficientInfDilTemperatureDerivative(componentNumber);
      }
    }
    return dfugdt;
  }

  /**
   * Obtains the derivative from the owning activity model, retaining standalone component evaluations.
   *
   * @param phase owning phase
   * @return d(ln gamma)/dT in 1/K
   */
  protected double getLnActivityTemperatureDerivative(PhaseInterface phase) {
    return phase.getNumberOfComponents() > componentNumber
        ? ((PhaseGE) phase).getLnActivityCoefficientTemperatureDerivative(componentNumber)
        : dlngammadt;
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
