package neqsim.process.safety.release;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Fail-closed mixture-specific solid and hydrate applicability assessment for release stations.
 *
 * <p>
 * This class does not add solid or hydrate flow physics. It asks the configured NeqSim EOS whether a selected pure
 * solid is stable at the station and, for water-containing hydrate-former mixtures, compares the station temperature
 * with the calculated hydrate equilibrium temperature. A detected risk or an unresolved required calculation keeps a
 * solid-free release model outside its applicability.
 * </p>
 */
public final class ReleaseSolidRiskAssessment implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final double COMPONENT_MOLE_FRACTION_TOLERANCE = 1e-12;
  private static final double SOLID_MASS_FRACTION_TOLERANCE = 1e-12;
  private static final Set<String> HYDRATE_FORMERS = new HashSet<String>(
      Arrays.asList("methane", "ethane", "propane", "i-butane", "n-butane", "CO2", "H2S", "nitrogen"));

  /** Assessment outcome. */
  public enum Status {
    /** All applicable checks completed without identifying a solid-like phase. */
    CLEAR,
    /** An equilibrium solid phase was found for a component in the actual mixture. */
    SOLID_RISK,
    /** The station is at or below the mixture hydrate equilibrium temperature. */
    HYDRATE_RISK,
    /** A required solid or hydrate calculation did not resolve. */
    UNRESOLVED
  }

  private final Status status;
  private final String component;
  private final double stationTemperatureK;
  private final double boundaryTemperatureK;
  private final double solidMassFraction;
  private final String message;

  private ReleaseSolidRiskAssessment(Status status, String component, double stationTemperatureK,
      double boundaryTemperatureK, double solidMassFraction, String message) {
    this.status = status;
    this.component = component;
    this.stationTemperatureK = stationTemperatureK;
    this.boundaryTemperatureK = boundaryTemperatureK;
    this.solidMassFraction = solidMassFraction;
    this.message = message;
  }

  /**
   * Assesses one equilibrated station without modifying the supplied state.
   *
   * @param fluid station state with composition, temperature and absolute pressure
   * @return immutable assessment
   */
  public static ReleaseSolidRiskAssessment assess(SystemInterface fluid) {
    if (fluid == null) {
      throw new IllegalArgumentException("Station fluid is required");
    }
    double temperature = fluid.getTemperature();
    try {
      for (int componentIndex = 0; componentIndex < fluid.getNumberOfComponents(); componentIndex++) {
        ComponentInterface candidate = fluid.getComponent(componentIndex);
        if (!requiresSolidCheck(candidate, fluid.getTotalNumberOfMoles(), temperature)) {
          continue;
        }
        SystemInterface trial = fluid.clone();
        trial.setHydrateCheck(false);
        trial.setSolidPhaseCheck(false);
        trial.setSolidPhaseCheck(candidate.getComponentName());
        trial.setMultiPhaseCheck(true);
        new ThermodynamicOperations(trial).TPSolidflash();
        if (trial.hasPhaseType(PhaseType.SOLID)) {
          double solidFraction = trial.getPhase(PhaseType.SOLID).getMass() / trial.getMass("kg");
          if (!Double.isFinite(solidFraction) || solidFraction < 0.0) {
            return unresolved(temperature, "Nonfinite solid fraction for " + candidate.getComponentName());
          }
          if (solidFraction > SOLID_MASS_FRACTION_TOLERANCE) {
            return new ReleaseSolidRiskAssessment(Status.SOLID_RISK, candidate.getComponentName(), temperature,
                candidate.getTriplePointTemperature(), solidFraction, "Equilibrium solid phase detected for "
                    + candidate.getComponentName() + " with mass fraction " + solidFraction);
          }
        }
      }
      if (requiresHydrateCheck(fluid)) {
        SystemInterface hydrate = fluid.clone();
        hydrate.setSolidPhaseCheck(false);
        hydrate.setHydrateCheck(true);
        new ThermodynamicOperations(hydrate).hydrateFormationTemperature();
        double boundary = hydrate.getTemperature();
        if (!Double.isFinite(boundary) || boundary <= 0.0) {
          return unresolved(temperature, "Hydrate equilibrium temperature is unavailable");
        }
        if (temperature <= boundary) {
          return new ReleaseSolidRiskAssessment(Status.HYDRATE_RISK, "water", temperature, boundary, Double.NaN,
              "Station temperature " + temperature + " K is at or below mixture hydrate equilibrium " + boundary
                  + " K");
        }
      }
      return new ReleaseSolidRiskAssessment(Status.CLEAR, null, temperature, Double.NaN, 0.0,
          "Mixture-specific solid and hydrate applicability checks passed");
    } catch (Exception ex) {
      return unresolved(temperature, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
    }
  }

  private static boolean requiresSolidCheck(ComponentInterface component, double totalMoles, double temperature) {
    double fraction = component.getNumberOfmoles() / totalMoles;
    double tripleTemperature = component.getTriplePointTemperature();
    return Double.isFinite(fraction) && fraction > COMPONENT_MOLE_FRACTION_TOLERANCE
        && Double.isFinite(tripleTemperature) && tripleTemperature > 0.0 && tripleTemperature < 1000.0
        && temperature <= tripleTemperature;
  }

  private static boolean requiresHydrateCheck(SystemInterface fluid) {
    if (!fluid.getPhase(0).hasComponent("water")) {
      return false;
    }
    double waterFraction = fluid.getComponent("water").getNumberOfmoles() / fluid.getTotalNumberOfMoles();
    if (!Double.isFinite(waterFraction) || waterFraction <= COMPONENT_MOLE_FRACTION_TOLERANCE) {
      return false;
    }
    for (String former : HYDRATE_FORMERS) {
      if (fluid.getPhase(0).hasComponent(former) && fluid.getComponent(former).getNumberOfmoles()
          / fluid.getTotalNumberOfMoles() > COMPONENT_MOLE_FRACTION_TOLERANCE) {
        return true;
      }
    }
    return false;
  }

  private static ReleaseSolidRiskAssessment unresolved(double temperature, String message) {
    return new ReleaseSolidRiskAssessment(Status.UNRESOLVED, null, temperature, Double.NaN, Double.NaN,
        "Solid-risk assessment failed closed: " + message);
  }

  /** @return assessment outcome */
  public Status getStatus() {
    return status;
  }

  /** @return whether a solid-free release model may use this station */
  public boolean isClear() {
    return status == Status.CLEAR;
  }

  /** @return implicated component, or null when not applicable */
  public String getComponent() {
    return component;
  }

  /** @return station temperature in K */
  public double getStationTemperatureK() {
    return stationTemperatureK;
  }

  /** @return equilibrium or component boundary temperature in K, or NaN when unavailable */
  public double getBoundaryTemperatureK() {
    return boundaryTemperatureK;
  }

  /** @return calculated solid mass fraction, or NaN when not applicable */
  public double getSolidMassFraction() {
    return solidMassFraction;
  }

  /** @return diagnostic detail */
  public String getMessage() {
    return message;
  }
}
