package neqsim.thermodynamicoperations.flashops.saturationops;

import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Calculates the water dew point against a pure condensed phase or a water-rich liquid at fixed pressure.
 *
 * <p>
 * Gas composition is held at the feed composition. By default, the condensed phase is pure water or pure MEG. For
 * GERG-water, its fugacity uses the ISO 18453 ice correlation below the triple point. Hydrates are not included.
 * </p>
 *
 * <p>
 * The optional dissolved-component mode equilibrates every feed species in an incipient liquid with more than 50 mol%
 * water. It uses liquid water, including metastable liquid below freezing. It does not perform phase-stability searches
 * for other liquids; use the full multiphase operation when a hydrocarbon liquid or glycol-rich liquid is present.
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
  /** Whether the incipient water-rich liquid includes dissolved feed components. */
  private final boolean allowDissolvedComponents;

  /**
   * Creates a water dew point calculation.
   *
   * @param system the fluid to calculate at its current absolute pressure
   */
  public WaterDewPointTemperatureFlash(SystemInterface system) {
    this(system, false);
  }

  /**
   * Creates a fixed-feed-gas water dew point calculation.
   *
   * @param system fluid at the required absolute pressure
   * @param allowDissolvedComponents true to equilibrate dissolved components in a water-rich liquid; false for the
   * legacy pure condensed reference
   */
  public WaterDewPointTemperatureFlash(SystemInterface system, boolean allowDissolvedComponents) {
    super(system);
    this.allowDissolvedComponents = allowDissolvedComponents;
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
    boolean hasWater = hasPositiveOverallAmount("water");
    if (allowDissolvedComponents) {
      if (system.isChemicalSystem() || !hasWater) {
        throw new IllegalArgumentException("Mixed-liquid water dew point requires water in a non-reacting gas");
      }
    } else if (!hasWater && !hasPositiveOverallAmount("MEG")) {
      throw new IllegalArgumentException("Water dew point requires a positive water or MEG mole fraction");
    }
    system.init(0);
    system.setNumberOfPhases(2);
    system.setBeta(1, 1.0e-15);
    if (allowDissolvedComponents) {
      int water = system.getPhase(0).getComponent("water").getComponentNumber();
      system.setBeta(0, 1.0 - 1.0e-10);
      system.setBeta(1, 1.0e-10);
      system.setPhaseType(0, PhaseType.GAS);
      system.setPhaseType(1, PhaseType.LIQUID);
      double dryFraction = 1.0 - system.getPhase(0).getComponent(water).getz();
      for (int component = 0; component < system.getNumberOfComponents(); component++) {
        double feed = system.getPhase(0).getComponent(component).getz();
        system.getPhase(0).getComponent(component).setx(feed);
        system.getPhase(1).getComponent(component).setx(component == water ? (dryFraction > 0.0 ? 0.9999 : 1.0)
            : (dryFraction > 0.0 ? 1.0e-4 * feed / dryFraction : 0.0));
      }
      double temperature = solveTemperature(water);
      residual(temperature, water);
      for (int phase = 0; phase < 2; phase++) {
        if (system.getPhase(phase) instanceof PhaseEosInterface) {
          double calculatedPressure = ((PhaseEosInterface) system.getPhase(phase)).calcPressure();
          if (!Double.isFinite(calculatedPressure)
              || Math.abs(calculatedPressure - system.getPressure()) > 1.0e-7 * Math.max(1.0, system.getPressure())) {
            throw new IllegalStateException("Mixed-liquid dew point did not converge to an EOS volume root");
          }
        }
      }
      return;
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
   * Checks the supplied inventory before initialization or phase-state mutation.
   *
   * @param name component name
   * @return true when the component has a positive finite overall mole amount
   */
  private boolean hasPositiveOverallAmount(String name) {
    if (!system.getPhase(0).hasComponent(name)) {
      return false;
    }
    double moles = system.getPhase(0).getComponent(name).getNumberOfmoles();
    return Double.isFinite(moles) && moles > 0.0;
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
    if (allowDissolvedComponents) {
      return mixedLiquidResidual(component);
    }
    system.init(1);
    double value = Math.log(system.getPhase(0).getComponent(component).getz())
        + system.getPhase(0).getComponent(component).getLogFugacityCoefficient()
        - system.getPhase(1).getComponent(component).getLogFugacityCoefficient();
    if (!Double.isFinite(value)) {
      throw new IllegalStateException("Non-finite fugacity in water/MEG dew point calculation");
    }
    return value;
  }

  /**
   * Equilibrates dissolved species and evaluates the remaining water fugacity residual.
   *
   * <p>
   * Water closes the liquid composition while each dissolved component satisfies its gas/liquid fugacity equation.
   * Normalizing all trial fugacities instead would admit the trivial gas-composition root in very dry gas. The
   * water-rich branch is required explicitly. Only fugacity initialization is needed; no TP flashes or solid/hydrate
   * searches are performed.
   * </p>
   *
   * @param water water component index
   * @return logarithmic water fugacity residual
   * @throws IllegalStateException if the water-rich branch or finite equilibrium cannot be found
   */
  private double mixedLiquidResidual(int water) {
    system.init(1, 0);
    double[] dissolved = new double[system.getNumberOfComponents()];
    for (int iteration = 0; iteration < 100; iteration++) {
      system.init(1, 1);
      double totalDissolved = 0.0;
      for (int component = 0; component < dissolved.length; component++) {
        if (component != water) {
          double feed = system.getPhase(0).getComponent(component).getz();
          dissolved[component] = feed == 0.0 ? 0.0
              : Math.exp(Math.log(feed) + system.getPhase(0).getComponent(component).getLogFugacityCoefficient()
                  - system.getPhase(1).getComponent(component).getLogFugacityCoefficient());
          totalDissolved += dissolved[component];
        }
      }
      if (!Double.isFinite(totalDissolved) || totalDissolved >= 0.5) {
        throw new IllegalStateException("No water-rich incipient liquid; use the full multiphase operation");
      }
      dissolved[water] = 1.0 - totalDissolved;
      double difference = 0.0;
      for (int component = 0; component < dissolved.length; component++) {
        double previous = system.getPhase(1).getComponent(component).getx();
        difference = Math.max(difference, Math.abs(dissolved[component] - previous) / (1.0e-12 + dissolved[component]));
        system.getPhase(1).getComponent(component).setx(dissolved[component]);
      }
      if (difference <= 1.0e-9) {
        system.init(1, 1);
        double value = Math.log(system.getPhase(0).getComponent(water).getz())
            + system.getPhase(0).getComponent(water).getLogFugacityCoefficient() - Math.log(dissolved[water])
            - system.getPhase(1).getComponent(water).getLogFugacityCoefficient();
        if (!Double.isFinite(value)) {
          throw new IllegalStateException("Non-finite water fugacity in mixed-liquid dew point");
        }
        return value;
      }
    }
    throw new IllegalStateException("Dissolved components did not converge in water-rich liquid");
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {
  }
}
