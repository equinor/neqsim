package neqsim.thermodynamicoperations.flashops.saturationops;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Immutable numerical and fluid-phase evidence from a hydrate-temperature calculation. Numerical convergence and CO2
 * saturation describe the calculated state, not experimental qualification, drilling-fluid applicability, or the amount
 * of hydrate formed.
 */
public final class HydrateEquilibriumDiagnostics implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final boolean converged;
  private final double temperature;
  private final double pressure;
  private final double hydrateResidual;
  private final double fluidFugacityResidual;
  private final double componentBalanceResidual;
  private final double chargeResidual;
  private final double minimumCo2TrialDistance;
  private final boolean aqueousPhase;
  private final boolean co2RichPhase;
  private final List<String> phaseTypes;

  /**
   * Captures independent values rather than retaining a mutable thermodynamic system.
   *
   * @param system calculated system
   * @param converged whether the hydrate water-fugacity equation converged
   * @param hydrateResidual dimensionless 1 - hydrate-water fugacity / fluid-water fugacity
   * @param trialDistance minimum vapour/liquid CO2 trial distance for the aqueous feed, or NaN if not assessed
   */
  HydrateEquilibriumDiagnostics(SystemInterface system, boolean converged, double hydrateResidual,
      double trialDistance) {
    this.converged = converged;
    this.temperature = system.getTemperature();
    this.pressure = system.getPressure();
    this.hydrateResidual = hydrateResidual;
    this.minimumCo2TrialDistance = trialDistance;
    List<String> types = new ArrayList<String>();
    boolean aqueous = false;
    boolean co2 = false;
    double balance = 0.0;
    double charge = 0.0;
    double fugacity = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      PhaseType type = system.getPhase(phase).getType();
      types.add(type.toString());
      aqueous |= type == PhaseType.AQUEOUS;
      co2 |= type != PhaseType.AQUEOUS && system.getBeta(phase) > 1.0e-10 && system.getPhase(phase).hasComponent("CO2")
          && system.getPhase(phase).getComponent("CO2").getx() > 0.5;
      double phaseCharge = 0.0;
      for (int component = 0; component < system.getNumberOfComponents(); component++) {
        phaseCharge += system.getPhase(phase).getComponent(component).getx()
            * system.getPhase(phase).getComponent(component).getIonicCharge();
      }
      charge = Math.max(charge, Math.abs(phaseCharge));
    }
    for (int component = 0; component < system.getNumberOfComponents(); component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        recovered += system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
      }
      balance = Math.max(balance, Math.abs(recovered - system.getPhase(0).getComponent(component).getz()));
      if (system.getNumberOfPhases() == 2 && system.getPhase(0).getComponent(component).getIonicCharge() == 0
          && !system.getPhase(0).getComponent(component).isIsIon()
          && system.getPhase(0).getComponent(component).getz() > 1.0e-30) {
        double ratio = system.getPhase(0).getFugacity(component) / system.getPhase(1).getFugacity(component);
        fugacity = Math.max(fugacity, Math.abs(Math.log(ratio)));
      }
    }
    this.phaseTypes = Collections.unmodifiableList(types);
    this.aqueousPhase = aqueous;
    this.co2RichPhase = co2;
    this.componentBalanceResidual = balance;
    this.chargeResidual = charge;
    this.fluidFugacityResidual = system.getNumberOfPhases() == 2 ? fugacity : Double.NaN;
  }

  /** @return true when the hydrate water-fugacity equation met its numerical tolerance */
  public boolean isConverged() {
    return converged;
  }

  /** @return calculated temperature in kelvin; consult isConverged before using it */
  public double getTemperature() {
    return temperature;
  }

  /** @return pressure in bara */
  public double getPressure() {
    return pressure;
  }

  /** @return dimensionless hydrate water-fugacity residual */
  public double getHydrateResidual() {
    return hydrateResidual;
  }

  /** @return maximum absolute molecular log-fugacity ratio, or NaN without exactly two fluid phases */
  public double getFluidFugacityResidual() {
    return fluidFugacityResidual;
  }

  /** @return maximum absolute reconstructed overall mole-fraction error */
  public double getComponentBalanceResidual() {
    return componentBalanceResidual;
  }

  /** @return maximum absolute phase charge per mole of phase */
  public double getChargeResidual() {
    return chargeResidual;
  }

  /** @return minimum dimensionless CO2 trial distance of the aqueous feed, or NaN if not assessed */
  public double getMinimumCo2TrialDistance() {
    return minimumCo2TrialDistance;
  }

  /** @return true when an aqueous material phase is present */
  public boolean hasAqueousPhase() {
    return aqueousPhase;
  }

  /** @return true when a material phase other than aqueous has CO2 mole fraction above 0.5 */
  public boolean hasCO2RichPhase() {
    return co2RichPhase;
  }

  /** @return immutable material phase labels, excluding the incipient hydrate reference */
  public List<String> getPhaseTypes() {
    return phaseTypes;
  }

  /**
   * Checks numerical evidence for a saturated CO2/brine boundary.
   *
   * @return true when a converged aqueous/CO2-rich split also closes molecular fugacities and balances
   */
  public boolean isSaturatedCO2Boundary() {
    return converged && aqueousPhase && co2RichPhase && componentBalanceResidual <= 1.0e-9 && chargeResidual <= 1.0e-10
        && fluidFugacityResidual <= 1.0e-8 && Double.isFinite(minimumCo2TrialDistance);
  }
}
