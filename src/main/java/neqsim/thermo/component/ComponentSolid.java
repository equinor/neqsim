/*
 * SolidComponent.java
 *
 * Created on 18. august 2001, 12:45
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.util.database.NeqSimDataBase;

/**
 * ComponentSolid class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class ComponentSolid extends ComponentSrk {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double dpdt = 1.0;
  double SolidFug = 0.0;
  double PvapSolid = 0.0;
  double PvapSoliddT = 0.0;
  double solvol = 0.0;
  double soldens = 0.0;
  boolean CCequation = true;
  boolean AntoineSolidequation = true;
  double pureCompFug = 0.0;

  /** Explicit opt-in to the sublimation-pressure reference; the liquid reference remains default. */
  private boolean useSolidVaporPressure = false;

  /** Reference phase containing only this single component, i.e., mixing rules are not relevant. */
  PhaseInterface refPhase = null;

  /**
   * Constructor for ComponentSolid.
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentSolid(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Publishes a finite exclusion coefficient when solid checking is disabled or for methane, whose freezing equilibrium
   * is not provided by this empirical phase. The liquid-reference route is the default; sublimation pressure is an
   * explicit per-component choice.
   * </p>
   */
  @Override
  public double fugcoef(PhaseInterface phase1) {
    if (!doSolidCheck() || componentName.equals("methane")) {
      fugacityCoefficient = 1e30;
      dfugdt = 0.0;
      dfugdp = 0.0;
      return fugacityCoefficient;
    }
    if (useSolidVaporPressure) {
      return fugcoef(phase1.getTemperature(), phase1.getPressure());
    }
    return fugcoef2(phase1);
  }

  /**
   * Select the sublimation-pressure reference for the phase-based fugacity entry point.
   *
   * <p>
   * The default is false (liquid reference and fusion properties). This option requires positive sublimation data, a
   * configured pure reference phase, and temperature no higher than the triple point. It does not enable solid checking
   * or methane freezing.
   * </p>
   *
   * @param enabled true to use the sublimation-pressure reference
   */
  public void setUseSolidVaporPressure(boolean enabled) {
    useSolidVaporPressure = enabled;
  }

  /**
   * Return the selected empirical solid reference route.
   *
   * @return true when sublimation pressure is selected
   */
  public boolean isUseSolidVaporPressure() {
    return useSolidVaporPressure;
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdT(PhaseInterface phase) {
    if (useSolidVaporPressure) {
      fugcoef(phase);
      return dfugdt;
    }
    return super.logfugcoefdT(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdP(PhaseInterface phase) {
    if (useSolidVaporPressure) {
      fugcoef(phase);
      return dfugdp;
    }
    return super.logfugcoefdP(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double[] logfugcoefdN(PhaseInterface phase) {
    if (useSolidVaporPressure) {
      java.util.Arrays.fill(dfugdn, 0.0);
      java.util.Arrays.fill(dfugdx, 0.0);
      return dfugdn;
    }
    return super.logfugcoefdN(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdNi(PhaseInterface phase, int component) {
    if (useSolidVaporPressure) {
      dfugdn[component] = 0.0;
      dfugdx[component] = 0.0;
      return 0.0;
    }
    return super.logfugcoefdNi(phase, component);
  }

  /**
   * Evaluate the explicit sublimation-pressure solid reference and publish its coefficient.
   *
   * <p>
   * Uses Clausius-Clapeyron when positive heat of sublimation and triple-point pressure are available, otherwise the
   * solid Antoine correlation. Density is in kg/m3; only an entirely absent density polynomial uses the documented 1000
   * kg/m3 screening fallback. This direct correlation call, like {@link #fugcoef2(PhaseInterface)}, does not apply the
   * phase-entry-point solid-check selection.
   * </p>
   *
   * @param temp temperature in K, above zero and no higher than the triple point
   * @param pres absolute pressure in bara, above zero
   * @return dimensionless solid fugacity coefficient
   * @throws IllegalArgumentException if temperature or pressure is outside the supported domain
   * @throws IllegalStateException if reference data or a finite positive coefficient is unavailable
   */
  public double fugcoef(double temp, double pres) {
    if (!Double.isFinite(temp) || temp <= 0.0 || !Double.isFinite(pres) || pres <= 0.0
        || !Double.isFinite(getTriplePointTemperature()) || getTriplePointTemperature() <= 0.0
        || temp > getTriplePointTemperature()) {
      throw new IllegalArgumentException("Solid vapor-pressure reference requires 0 < T <= triple point and P > 0");
    }
    double step = Math.min(1e-4 * temp, 0.01);
    double lower = temp - step;
    double upper = Math.min(temp + step, getTriplePointTemperature());
    double lowerLogPhi = Math.log(solidVaporPressureCoefficient(lower, pres));
    double upperLogPhi = Math.log(solidVaporPressureCoefficient(upper, pres));
    // Restore the reference phase and public coefficient to the requested state after differencing.
    double coefficient = solidVaporPressureCoefficient(temp, pres);
    dfugdt = (upperLogPhi - lowerLogPhi) / (upper - lower);
    dfugdp = solvol * 1e5 / (R * temp) - 1.0 / pres;
    fugacityCoefficient = coefficient;
    SolidFug = coefficient * pres;
    return coefficient;
  }

  /**
   * Evaluate a sublimation reference without changing the published fugacity coefficient.
   *
   * @param temp temperature in K
   * @param pres absolute pressure in bara
   * @return dimensionless coefficient
   */
  private double solidVaporPressureCoefficient(double temp, double pres) {
    if (refPhase == null) {
      throw new IllegalStateException("Solid reference fluid phase is not configured for " + componentName);
    }
    CCequation = Double.isFinite(Hsub) && Hsub > 0.0 && Double.isFinite(triplePointPressure)
        && triplePointPressure > 0.0;
    AntoineSolidequation = Double.isFinite(AntoineASolid) && Math.abs(AntoineASolid) > 1e-6
        && Double.isFinite(AntoineBSolid) && Double.isFinite(AntoineCSolid);
    if (CCequation) {
      PvapSolid = getCCsolidVaporPressure(temp);
    } else if (AntoineSolidequation) {
      PvapSolid = getSolidVaporPressure(temp);
    } else {
      throw new IllegalStateException("No solid vapor-pressure correlation for " + componentName);
    }
    if (!Double.isFinite(PvapSolid) || PvapSolid <= 0.0) {
      throw new IllegalStateException("Invalid solid vapor pressure for " + componentName);
    }
    boolean hasDensity = false;
    for (double coefficient : solidDensityCoefs) {
      hasDensity |= coefficient != 0.0;
    }
    soldens = hasDensity ? getPureComponentSolidDensity(temp) : 1000.0;
    if (!Double.isFinite(soldens) || soldens <= 0.0) {
      throw new IllegalStateException("Invalid solid density for " + componentName);
    }
    solvol = getMolarMass() / soldens;
    refPhase.setTemperature(temp);
    refPhase.setPressure(PvapSolid);
    refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 1, PhaseType.GAS, 1.0);
    double vaporCoefficient = refPhase.getComponent(0).fugcoef(refPhase);
    double coefficient = PvapSolid / pres * vaporCoefficient * Math.exp(solvol * (pres - PvapSolid) * 1e5 / (R * temp));
    if (!Double.isFinite(coefficient) || coefficient <= 0.0) {
      throw new IllegalStateException("Invalid solid fugacity coefficient for " + componentName);
    }
    return coefficient;
  }

  /**
   * Calculate, set and return fugacity coefficient.
   *
   * @param phase1 a {@link neqsim.thermo.phase.PhaseInterface} object to get fugacity coefficient of.
   * @return Fugacity coefficient
   */
  public double fugcoef2(PhaseInterface phase1) {
    refPhase.setTemperature(phase1.getTemperature());
    refPhase.setPressure(phase1.getPressure());
    try {
      refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 1, PhaseType.LIQUID, 1.0);
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    refPhase.getComponent(0).fugcoef(refPhase);

    double liquidPhaseFugacity = refPhase.getComponent(0).getFugacityCoefficient() * refPhase.getPressure();

    // Calculates delta Cp solid-liquid
    double deltaCpSL = -(getPureComponentCpSolid(getTriplePointTemperature())
        - getPureComponentCpLiquid(getTriplePointTemperature()));
    if (componentName.equals("water")) {
      deltaCpSL = 37.12;
    }

    // System.out.println("deltaCp Sol-liq " + deltaCpSL);
    // Calculates solid-liquid volume change
    double liqMolVol = 0.0;
    double solMolVol = 0.0;
    double temp = getPureComponentLiquidDensity(getTriplePointTemperature());
    if (temp > 1e-20) {
      liqMolVol = 1.0 / temp * getMolarMass();
    } else {
      liqMolVol = 1.0 / refPhase.getDensity() * getMolarMass();
    }
    temp = getPureComponentSolidDensity(getTriplePointTemperature());
    if (temp > 1e-20) {
      solMolVol = 1.0 / temp * getMolarMass();
    } else {
      solMolVol = liqMolVol;
    }
    double deltaSolVol = (solMolVol - liqMolVol);

    // System.out.println("liquid density " + 1.0/liqMolVol*getMolarMass() + " solid
    // density " + 1.0/solMolVol*getMolarMass());

    fugacityCoefficient = liquidPhaseFugacity / phase1.getPressure()
        * Math.exp(-getHeatOfFusion() / (R * phase1.getTemperature())
            * (1.0 - phase1.getTemperature() / getTriplePointTemperature())
            + deltaCpSL / (R * phase1.getTemperature()) * (getTriplePointTemperature() - phase1.getTemperature())
            - deltaCpSL / R * Math.log(getTriplePointTemperature() / phase1.getTemperature())
            - deltaSolVol * (1.0 - phase1.getPressure()) / (R * phase1.getTemperature()));

    // System.out.println("solidfug " + SolidFug);
    SolidFug = getx() * phase1.getPressure() * fugacityCoefficient;
    return fugacityCoefficient;
  }

  // public double dfugdt(PhaseInterface phase, int numberOfComps, double temp, double pres){
  // if(componentName.equals("water")){
  // // double solvol = 1.0/getPureComponentSolidDensity(getMeltingPointTemperature())*molarMass;
  // double solvol = 1.0/(780*0.92)*getMolarMass();

  // dfugdt =
  // Math.log((getSolidVaporPressuredT(temp)*Math.exp(solvol/(R*temp)*(pres-getSolidVaporPressure(temp))))/pres);
  // }
  // else if(componentName.equals("MEG")){
  // double solvol = 1.0/getPureComponentSolidDensity(getMeltingPointTemperature())*molarMass;
  // dfugdt = Math.log((getSolidVaporPressuredT(temp))/pres);
  // }
  // else if(componentName.equals("S8")){
  // double solvol = 1.0/(1800.0)*getMolarMass();
  // dfugdt =
  // Math.log((getSolidVaporPressuredT(temp)*10*Math.exp(solvol/(R*temp)*(pres-getSolidVaporPressure(temp)*10)))/pres);
  // }

  // else dfugdt=0;
  // return dfugdt;
  // }
  // public double getdpdt() {
  // return dpdt;
  // }
  /**
   * getMolarVolumeSolid.
   *
   * @return a double
   */
  public double getMolarVolumeSolid() {
    return getPureComponentSolidDensity(getMeltingPointTemperature()) / molarMass;
  }

  /**
   * setSolidRefFluidPhase.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setSolidRefFluidPhase(PhaseInterface phase) {
    try {
      // if ((!isTBPfraction && !isPlusFraction)
      // || neqsim.util.database.NeqSimDataBase.createTemporaryTables()) {
      refPhase = phase.getClass().getDeclaredConstructor().newInstance();
      refPhase.setTemperature(273.0);
      refPhase.setPressure(1.0);
      try {
        if (NeqSimDataBase.hasComponent(componentName) || NeqSimDataBase.hasTempComponent(componentName)) {
          refPhase.addComponent(componentName, 10.0, 10.0, 0);
        } else {
          refPhase.addComponent("methane", 10.0, 10.0, 0);
          refPhase.getComponent("methane").setComponentName(componentName);
        }
      } catch (Exception ex) {
        logger.error("error occured in setSolidRefFluidPhase ", ex);
        refPhase.addComponent("methane", 10.0, 10.0, 0);
        refPhase.getComponent("methane").setComponentName(componentName);
      }
      refPhase.getComponent(componentName)
          .setAttractiveTerm(phase.getComponent(componentName).getAttractiveTermNumber());
      refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 0, PhaseType.GAS, 1.0);
      refPhase.setMixingRule(null);
      // }
    } catch (Exception ex) {
      logger.error("error occured", ex);
    }
  }

  /**
   * getVolumeCorrection2.
   *
   * @return a double
   */
  public double getVolumeCorrection2() {
    return 0.0;
  }
}
