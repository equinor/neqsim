package neqsim.thermodynamicoperations.flashops.saturationops;

import neqsim.thermo.phase.PhaseCPAInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Calculates the water dew point against a pure condensed phase at fixed pressure.
 *
 * <p>
 * Gas composition is held at the feed composition. For GERG-water, the condensed-phase fugacity uses the ISO 18453 ice
 * correlation below the triple point. Dissolved gas, hydrates and mixed aqueous phases are not included; use the
 * multiphase operation when an aqueous mixture is required. The legacy pure-MEG calculation is retained. CPA uses a
 * separate pure reference phase so that absent dissolved species cannot make the condensed-phase association
 * calculation singular. Below freezing this is a metastable liquid-water dew point.
 * </p>
 *
 * @author asmund
 * @version 1.0
 */
public class WaterDewPointTemperatureFlash extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Relative fugacity equilibrium tolerance, expressed as a logarithmic residual. */
  private static final double FUGACITY_TOLERANCE = 1.0e-9;
  /** Independent pure CPA reference phases, rebuilt for each run. */
  private transient PhaseInterface[] pureCondensedPhases;

  /**
   * Creates a water dew point calculation.
   *
   * @param system the fluid to calculate at its current absolute pressure
   */
  public WaterDewPointTemperatureFlash(SystemInterface system) {
    super(system);
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException if pressure is invalid or neither water nor MEG has positive mole fraction
   * @throws IllegalStateException if a finite fugacity equilibrium cannot be bracketed or converged
   */
  @Override
  public void run() {
    if (!Double.isFinite(system.getPressure()) || system.getPressure() <= 0.0) {
      throw new IllegalArgumentException("Water dew point requires positive finite absolute pressure");
    }
    system.init(0);
    system.setNumberOfPhases(2);
    system.setBeta(1, 1.0e-15);
    pureCondensedPhases = null;
    if (system.getPhase(1) instanceof PhaseCPAInterface) {
      // Build private references rather than retaining association sites for feed components whose
      // incipient liquid mole fractions are zero. Their near-zero site populations can make the
      // mixed-phase CPA solve singular even though the pure water reference is well defined.
      PhaseInterface referenceSource = system.getPhase(1).clone();
      referenceSource.setRefPhase((PhaseInterface[]) null);
      referenceSource.initRefPhases(true);
      pureCondensedPhases = referenceSource.getRefPhase();
      for (int component = 0; component < system.getPhase(1).getNumberOfComponents(); component++) {
        String name = system.getPhase(1).getComponent(component).getComponentName();
        if ("water".equals(name) || "MEG".equals(name)) {
          // Retain caller-owned component tuning rather than replacing it with database defaults.
          PhaseInterface condensed = pureCondensedPhases[component];
          ComponentInterface pureComponent = system.getPhase(1).getComponent(component).clone();
          pureComponent.setComponentNumber(0);
          pureComponent.setNumberOfmoles(1.0);
          pureComponent.setx(1.0);
          pureComponent.setz(1.0);
          condensed.getcomponentArray()[0] = pureComponent;
          condensed.init(1.0, 1, 0, PhaseType.LIQUID, 1.0);
        }
      }
    }
    double maximumTemperature = Double.NEGATIVE_INFINITY;
    int controllingComponent = -1;
    for (int component = 0; component < system.getPhase(0).getNumberOfComponents(); component++) {
      String name = system.getPhase(0).getComponent(component).getComponentName();
      double moleFraction = system.getPhase(0).getComponent(component).getz();
      if (("water".equals(name) || "MEG".equals(name)) && Double.isFinite(moleFraction) && moleFraction > 0.0) {
        preparePhases(component);
        double temperature = solveTemperature(component);
        if (temperature > maximumTemperature) {
          maximumTemperature = temperature;
          controllingComponent = component;
        }
      }
    }
    if (controllingComponent < 0) {
      throw new IllegalArgumentException("Water dew point requires a positive water or MEG mole fraction");
    }
    preparePhases(controllingComponent);
    residual(maximumTemperature, controllingComponent);
  }

  /**
   * Sets the feed gas and pure incipient condensed-phase compositions.
   *
   * @param component index of the condensing component
   */
  private void preparePhases(int component) {
    for (int index = 0; index < system.getPhase(0).getNumberOfComponents(); index++) {
      system.getPhase(0).getComponent(index).setx(system.getPhase(0).getComponent(index).getz());
      system.getPhase(1).getComponent(index).setx(index == component ? 1.0 : 1.0e-30);
    }
  }

  /**
   * Brackets and bisects the fugacity equilibrium without a concentration-dependent iteration step.
   *
   * @param component index of the condensing component
   * @return converged temperature in kelvin
   * @throws IllegalStateException if no finite bracket or converged solution can be found
   */
  private double solveTemperature(int component) {
    double minimum = 100.0;
    double maximum = 0.95 * system.getPhase(0).getComponent(component).getTC();
    double lower = Math.max(minimum,
        Math.min(maximum, system.getPhase(0).getComponent(component).getMeltingPointTemperature()));
    double upper = lower;
    double lowerResidual = residual(lower, component);
    double upperResidual = lowerResidual;
    while (lowerResidual < 0.0 && lower > minimum) {
      lower = Math.max(minimum, lower - 10.0);
      lowerResidual = residual(lower, component);
    }
    while (upperResidual > 0.0 && upper < maximum) {
      upper = Math.min(maximum, upper + 10.0);
      upperResidual = residual(upper, component);
    }
    if (lowerResidual < 0.0 || upperResidual > 0.0) {
      throw new IllegalStateException("Could not bracket water/MEG dew point fugacity equilibrium");
    }
    for (int iteration = 0; iteration < 100; iteration++) {
      double temperature = 0.5 * (lower + upper);
      double value = residual(temperature, component);
      if (Math.abs(value) <= FUGACITY_TOLERANCE) {
        return temperature;
      }
      if (value > 0.0) {
        lower = temperature;
      } else {
        upper = temperature;
      }
    }
    throw new IllegalStateException("Water/MEG dew point did not converge to fugacity equilibrium");
  }

  /**
   * Evaluates ln(fugacity in gas / fugacity in the pure condensed phase).
   *
   * @param temperature trial temperature in kelvin
   * @param component index of the condensing component
   * @return dimensionless logarithmic fugacity residual
   * @throws IllegalStateException if fugacity evaluation is non-finite
   */
  private double residual(double temperature, int component) {
    system.setTemperature(temperature);
    if (pureCondensedPhases == null) {
      system.init(1);
    } else {
      system.init(1, 0);
      PhaseInterface condensed = pureCondensedPhases[component];
      condensed.setTemperature(temperature);
      condensed.setPressure(system.getPressure());
      condensed.init(condensed.getNumberOfMolesInPhase(), 1, 1, PhaseType.LIQUID, 1.0);
      system.getPhase(1).getComponent(component).setFugacityCoefficient(condensed.getComponent(0).fugcoef(condensed));
    }
    double value = Math.log(system.getPhase(0).getComponent(component).getz())
        + system.getPhase(0).getComponent(component).getLogFugacityCoefficient()
        - system.getPhase(1).getComponent(component).getLogFugacityCoefficient();
    if (!Double.isFinite(value)) {
      throw new IllegalStateException("Non-finite fugacity in water/MEG dew point calculation");
    }
    return value;
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {
  }
}
