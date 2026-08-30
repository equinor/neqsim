package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.Map;
import neqsim.thermo.system.SystemInterface;

/**
 * Reaction-timescale diagnostics for coupling {@link KineticReaction} models to transport.
 *
 * <p>
 * The diagnostic compares the time required to consume the limiting reactant at the current local reaction rate with a
 * caller-provided residence time. The resulting Damkohler number is a screening measure for whether chemistry is
 * effectively frozen, competes with transport, or is fast relative to transport. It does not replace
 * timestep-refinement or kinetic validation.
 * </p>
 *
 * @author NeqSim Team
 * @version 1.0
 */
public final class KineticReactionDiagnostics implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Qualitative reaction/transport timescale classification. */
  public enum Regime {
    /** No finite local reaction rate at the evaluated state. */
    INACTIVE,
    /** Reaction time is at least ten times longer than residence time. */
    TRANSPORT_DOMINATED,
    /** Reaction and transport times are of comparable order. */
    COUPLED,
    /** Reaction time is at least ten times shorter than residence time. */
    REACTION_DOMINATED
  }

  private final String reactionName;
  private final double reactionRate;
  private final KineticReaction.RateBasis rateBasis;
  private final String limitingReactant;
  private final double limitingReactantConcentrationMolPerM3;
  private final double reactionTimeSeconds;
  private final double residenceTimeSeconds;
  private final double damkohlerNumber;
  private final Regime regime;

  private KineticReactionDiagnostics(String reactionName, double reactionRate, KineticReaction.RateBasis rateBasis,
      String limitingReactant, double limitingReactantConcentrationMolPerM3, double reactionTimeSeconds,
      double residenceTimeSeconds, double damkohlerNumber, Regime regime) {
    this.reactionName = reactionName;
    this.reactionRate = reactionRate;
    this.rateBasis = rateBasis;
    this.limitingReactant = limitingReactant;
    this.limitingReactantConcentrationMolPerM3 = limitingReactantConcentrationMolPerM3;
    this.reactionTimeSeconds = reactionTimeSeconds;
    this.residenceTimeSeconds = residenceTimeSeconds;
    this.damkohlerNumber = damkohlerNumber;
    this.regime = regime;
  }

  /**
   * Evaluate a kinetic reaction against a local transport residence time.
   *
   * <p>
   * The timescale estimate is directly meaningful for volume-basis rates. Catalyst-mass and catalyst-area rates are
   * rejected because a reactor-specific catalyst loading or area is needed before a volumetric reactant-consumption
   * timescale can be defined.
   * </p>
   *
   * @param reaction kinetic reaction
   * @param system initialized thermodynamic system
   * @param phaseIndex phase in which the reaction rate is evaluated
   * @param residenceTimeSeconds local transport residence time [s]
   * @return immutable reaction/transport diagnostic
   */
  public static KineticReactionDiagnostics evaluate(KineticReaction reaction, SystemInterface system, int phaseIndex,
      double residenceTimeSeconds) {
    if (reaction == null) {
      throw new IllegalArgumentException("reaction cannot be null");
    }
    if (system == null) {
      throw new IllegalArgumentException("system cannot be null");
    }
    if (!Double.isFinite(residenceTimeSeconds) || residenceTimeSeconds < 0.0) {
      throw new IllegalArgumentException("residence time must be finite and non-negative");
    }
    if (reaction.getRateBasis() != KineticReaction.RateBasis.VOLUME) {
      throw new IllegalArgumentException("transport timescale diagnostic currently requires a VOLUME rate basis");
    }
    if (phaseIndex < 0 || phaseIndex >= system.getNumberOfPhases()) {
      throw new IllegalArgumentException("phase index is outside the active phase range");
    }

    double rate = reaction.calculateRate(system, phaseIndex);
    if (!Double.isFinite(rate)) {
      throw new IllegalStateException("kinetic reaction returned a non-finite rate");
    }
    double absoluteRate = Math.abs(rate);

    String limitingReactant = "";
    double limitingConcentration = Double.POSITIVE_INFINITY;
    double reactionTime = Double.POSITIVE_INFINITY;

    if (absoluteRate > 0.0) {
      for (Map.Entry<String, Double> entry : reaction.getStoichiometry().entrySet()) {
        double stoichiometricCoefficient = entry.getValue().doubleValue();
        if (stoichiometricCoefficient >= 0.0) {
          continue;
        }
        double concentration = getConcentration(system, phaseIndex, entry.getKey());
        double speciesTime = concentration / (-stoichiometricCoefficient * absoluteRate);
        if (speciesTime < reactionTime) {
          reactionTime = speciesTime;
          limitingReactant = entry.getKey();
          limitingConcentration = concentration;
        }
      }
    }

    if (!Double.isFinite(reactionTime) || reactionTime <= 0.0) {
      return new KineticReactionDiagnostics(reaction.getName(), rate, reaction.getRateBasis(), limitingReactant,
          Double.isFinite(limitingConcentration) ? limitingConcentration : 0.0, Double.POSITIVE_INFINITY,
          residenceTimeSeconds, 0.0, Regime.INACTIVE);
    }

    double damkohler = residenceTimeSeconds / reactionTime;
    Regime regime;
    if (damkohler < 0.1) {
      regime = Regime.TRANSPORT_DOMINATED;
    } else if (damkohler > 10.0) {
      regime = Regime.REACTION_DOMINATED;
    } else {
      regime = Regime.COUPLED;
    }

    return new KineticReactionDiagnostics(reaction.getName(), rate, reaction.getRateBasis(), limitingReactant,
        limitingConcentration, reactionTime, residenceTimeSeconds, damkohler, regime);
  }

  private static double getConcentration(SystemInterface system, int phaseIndex, String componentName) {
    if (!system.hasComponent(componentName)) {
      return 0.0;
    }
    double moleFraction = system.getPhase(phaseIndex).getComponent(componentName).getx();
    double molarDensity = system.getPhase(phaseIndex).getDensity("mol/m3");
    return Math.max(0.0, moleFraction * molarDensity);
  }

  /** @return reaction name. */
  public String getReactionName() {
    return reactionName;
  }

  /** @return signed local reaction rate in units implied by {@link #getRateBasis()}. */
  public double getReactionRate() {
    return reactionRate;
  }

  /** @return reaction rate basis. */
  public KineticReaction.RateBasis getRateBasis() {
    return rateBasis;
  }

  /** @return limiting reactant name, or an empty string when inactive. */
  public String getLimitingReactant() {
    return limitingReactant;
  }

  /** @return limiting reactant concentration [mol/m3]. */
  public double getLimitingReactantConcentrationMolPerM3() {
    return limitingReactantConcentrationMolPerM3;
  }

  /** @return local reaction timescale [s], or positive infinity when inactive. */
  public double getReactionTimeSeconds() {
    return reactionTimeSeconds;
  }

  /** @return transport residence time used for the comparison [s]. */
  public double getResidenceTimeSeconds() {
    return residenceTimeSeconds;
  }

  /** @return residence time divided by reaction time. */
  public double getDamkohlerNumber() {
    return damkohlerNumber;
  }

  /** @return qualitative timescale regime. */
  public Regime getRegime() {
    return regime;
  }
}
