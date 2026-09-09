package neqsim.thermodynamicoperations.flashops.saturationops;

import neqsim.thermo.component.ComponentHydrate;
import neqsim.thermo.component.ComponentHydratePitzer;
import neqsim.thermo.component.IapwsHenryLaw;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Bounded incipient hydrate equilibrium calculation coupled to a full Pitzer fluid flash at every trial.
 *
 * <p>
 * Solves ln(fWaterHydrate/fWaterAqueous) = 0. The Pitzer phase supplies both water activity and equilibrium guest
 * fugacities, including when the CO2-rich phase is liquid or has disappeared. Ions never occupy hydrate cages. The
 * default search envelope is 273.15-323.15 K and 1-1000 bara. These are numerical bounds, not a qualification of the
 * parameter database over that envelope. The lower temperature is further restricted to the fitted Henry-reference
 * range of each present guest (274.19 K for CO2, 275.46 K for methane). Ice, salt precipitation and drilling-fluid
 * additives require separate models and validation. On failure the input temperature, pressure and multiphase setting
 * are restored, but phase properties must be reflashed before use. Use a clone to preserve an operating state.
 * </p>
 *
 * @author NeqSim
 */
public class PitzerHydrateFlash extends ConstantDutyTemperatureFlash {
  private static final long serialVersionUID = 1000;
  private static final double RESIDUAL_TOLERANCE = 1.0e-8;
  private final boolean solvePressure;
  private double minimumTemperature = 273.15;
  private double maximumTemperature = 323.15;
  private double minimumPressure = 1.0;
  private double maximumPressure = 1000.0;
  private int structure;
  private double residual = Double.NaN;
  private double waterActivity = Double.NaN;
  private boolean converged;

  /**
   * Creates a temperature or pressure search.
   *
   * @param system Pitzer system, modified to the converged equilibrium state
   * @param solvePressure true to solve pressure at fixed temperature, false to solve temperature at fixed pressure
   */
  public PitzerHydrateFlash(SystemPitzer system, boolean solvePressure) {
    super(system);
    this.solvePressure = solvePressure;
  }

  /**
   * Sets the temperature search bounds within the current Henry-reference domain.
   *
   * @param lower lower bound in K, at least 273.15
   * @param upper upper bound in K, at most 323.15
   */
  public void setTemperatureBounds(double lower, double upper) {
    if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower < 273.15 || upper > 323.15 || lower >= upper) {
      throw new IllegalArgumentException(
          "Pitzer hydrate temperature bounds require 273.15 <= lower < upper <= 323.15 K");
    }
    minimumTemperature = lower;
    maximumTemperature = upper;
  }

  /**
   * Sets pressure search bounds.
   *
   * @param lower positive lower bound in bara
   * @param upper upper bound in bara, at most 1000
   */
  public void setPressureBounds(double lower, double upper) {
    if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower <= 0.0 || upper > 1000.0 || lower >= upper) {
      throw new IllegalArgumentException("Pitzer hydrate pressure bounds require 0 < lower < upper <= 1000 bara");
    }
    minimumPressure = lower;
    maximumPressure = upper;
  }

  /**
   * Selects a hydrate structure.
   *
   * @param structure 0 for the stable structure, 1 for sI, 2 for sII
   */
  public void setStructure(int structure) {
    if (structure < 0 || structure > 2) {
      throw new IllegalArgumentException("Hydrate structure must be 0 (automatic), 1 or 2");
    }
    this.structure = structure;
  }

  /** @return whether the last solve satisfied the fugacity residual tolerance */
  public boolean isConverged() {
    return converged;
  }

  /** @return last logarithmic water fugacity residual, dimensionless */
  public double getResidual() {
    return residual;
  }

  /** @return Pitzer water activity at the last evaluated state, dimensionless */
  public double getWaterActivity() {
    return waterActivity;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    converged = false;
    residual = Double.NaN;
    waterActivity = Double.NaN;
    validateFeed();
    double originalTemperature = system.getTemperature();
    double originalPressure = system.getPressure();
    boolean originalMultiPhaseCheck = system.doMultiPhaseCheck();
    try {
      if (!system.getHydrateCheck()) {
        system.setHydrateCheck(true);
      }
      system.setMultiPhaseCheck(true);
      ComponentHydratePitzer water = (ComponentHydratePitzer) system.getPhases()[4].getComponent("water");
      water.setRequestedStructure(structure);
      double lower = solvePressure ? minimumPressure : Math.max(minimumTemperature, minimumHenryTemperature());
      double limit = solvePressure ? maximumPressure : maximumTemperature;
      double fLower = evaluate(lower);
      if (accept(fLower)) {
        return;
      }
      // Pressure can have a second crossing on a dense-fluid branch. Search from low pressure for the first root.
      double upper = lower;
      while (upper < limit) {
        upper = Math.min(limit, solvePressure ? Math.max(lower * 1.3, lower + 0.5) : lower + 5.0);
        double fUpper = evaluate(upper);
        if (accept(fUpper)) {
          return;
        }
        if (Math.signum(fLower) != Math.signum(fUpper)) {
          bisect(lower, upper, fLower);
          return;
        }
        lower = upper;
        fLower = fUpper;
      }
      throw new IllegalStateException("No Pitzer hydrate equilibrium bracket in "
          + (solvePressure ? minimumPressure + "-" + maximumPressure + " bara"
              : Math.max(minimumTemperature, minimumHenryTemperature()) + "-" + maximumTemperature + " K")
          + "; last log fugacity residual=" + residual);
    } finally {
      system.setMultiPhaseCheck(originalMultiPhaseCheck);
      if (!converged) {
        system.setTemperature(originalTemperature);
        system.setPressure(originalPressure);
      }
    }
  }

  /** Validates the feed and fixed state before modifying the system. */
  private void validateFeed() {
    if (!system.getPhase(0).hasComponent("water")
        || !(system.getPhase(0).getComponent("water").getNumberOfmoles() > 0.0)) {
      throw new IllegalArgumentException("Pitzer hydrate equilibrium requires a positive water inventory");
    }
    boolean hasGuest = false;
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).isHydrateFormer()
          && system.getPhase(0).getComponent(i).getNumberOfmoles() > 0.0) {
        hasGuest = true;
      }
    }
    if (!hasGuest) {
      throw new IllegalArgumentException("Pitzer hydrate equilibrium requires a hydrate-forming guest");
    }
    if (!solvePressure && maximumTemperature < minimumHenryTemperature()) {
      throw new IllegalArgumentException("Pitzer hydrate temperature bounds do not overlap the guest Henry range");
    }
    if (solvePressure && system.getTemperature() < minimumHenryTemperature()) {
      throw new IllegalArgumentException(
          "Pitzer hydrate temperature is below the guest Henry reference limit of " + minimumHenryTemperature() + " K");
    }
    double fixed = solvePressure ? system.getTemperature() : system.getPressure();
    if (!Double.isFinite(fixed)
        || (solvePressure ? fixed < 273.15 || fixed > 323.15 : fixed <= 0.0 || fixed > 1000.0)) {
      throw new IllegalArgumentException("Pitzer hydrate calculations require 273.15-323.15 K and 0 < P <= 1000 bara");
    }
  }

  /**
   * Finds the lower fitted Henry-reference limit for the present molecular guests.
   *
   * @return minimum supported trial temperature in K
   */
  private double minimumHenryTemperature() {
    double minimum = 273.15;
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String name = system.getPhase(0).getComponent(i).getComponentName();
      if (system.getPhase(0).getComponent(i).getNumberOfmoles() > 0.0 && IapwsHenryLaw.isSupportedSpecies(name)) {
        minimum = Math.max(minimum, IapwsHenryLaw.assess(name, 298.15).getMinimumFittedTemperature());
      }
    }
    return minimum;
  }

  /**
   * Bisects a bracket, accepting only the thermodynamic residual, never just a small variable step.
   *
   * @param lower lower bound
   * @param upper upper bound
   * @param fLower residual at lower bound
   */
  private void bisect(double lower, double upper, double fLower) {
    for (int iteration = 0; iteration < 70; iteration++) {
      double middle = (lower + upper) / 2.0;
      double fMiddle = evaluate(middle);
      if (accept(fMiddle)) {
        return;
      }
      if (Math.signum(fLower) != Math.signum(fMiddle)) {
        upper = middle;
      } else {
        lower = middle;
        fLower = fMiddle;
      }
    }
    throw new IllegalStateException("Pitzer hydrate equilibrium did not converge; log fugacity residual=" + residual);
  }

  /**
   * Records convergence against the physical residual.
   *
   * @param value logarithmic fugacity residual
   * @return true if converged
   */
  private boolean accept(double value) {
    converged = Math.abs(value) <= RESIDUAL_TOLERANCE;
    return converged;
  }

  /**
   * Flashes fluid phases and updates incipient hydrate fugacities at a trial state.
   *
   * @param trial pressure in bara or temperature in K, according to solve mode
   * @return logarithmic water fugacity residual
   */
  private double evaluate(double trial) {
    if (solvePressure) {
      system.setPressure(trial);
    } else {
      system.setTemperature(trial);
    }
    new ThermodynamicOperations(system).TPflash();
    PhasePitzer aqueous = null;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i) instanceof PhasePitzer) {
        aqueous = (PhasePitzer) system.getPhase(i);
      }
    }
    if (aqueous == null || aqueous.getComponent("water").getNumberOfMolesInPhase() <= 0.0) {
      throw new IllegalStateException("Pitzer hydrate equilibrium requires an active aqueous phase");
    }
    PhaseInterface hydrate = system.getPhases()[4];
    // The hybrid TP flash remaps active phases. Always address the hydrate by its creation-order slot.
    hydrate.setTemperature(system.getTemperature());
    hydrate.setPressure(system.getPressure());
    for (int j = 0; j < hydrate.getNumberOfComponents(); j++) {
      double guestFugacity = hydrate.getComponent(j).isHydrateFormer() ? aqueous.getFugacity(j) : 0.0;
      for (int i = 0; i < hydrate.getNumberOfComponents(); i++) {
        ((ComponentHydrate) hydrate.getComponent(i)).setRefFug(j, guestFugacity);
      }
    }
    hydrate.getComponent("water").setx(1.0);
    double hydrateFugacity = hydrate.getComponent("water").fugcoef(hydrate) * system.getPressure();
    double aqueousFugacity = aqueous.getFugacity("water");
    waterActivity = aqueousFugacity / aqueous.getComponent("water").getAntoineVaporPressure(system.getTemperature());
    residual = Math.log(hydrateFugacity / aqueousFugacity);
    if (!Double.isFinite(residual) || !(waterActivity > 0.0) || waterActivity > 1.0 + 1.0e-8) {
      throw new IllegalStateException("Invalid Pitzer hydrate water activity or fugacity residual");
    }
    return residual;
  }
}
