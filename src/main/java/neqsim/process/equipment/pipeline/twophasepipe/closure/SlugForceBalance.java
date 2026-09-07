package neqsim.process.equipment.pipeline.twophasepipe.closure;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;

/**
 * Shared reduced mechanical closure for liquid-wetted slug flow.
 *
 * <p>
 * The liquid continuum wets the wall in both the slug body and the Taylor-bubble film. The existing mixture wall law is
 * evaluated at the wetting liquid velocity and assigned to liquid, rather than divided by occupied phase area. The
 * existing passive interphase drag transfers momentum from gas to liquid. This admits a nonzero equilibrium slip under
 * a common pressure gradient. It is a reduced closure, not a resolved slug-unit or severe-slugging model.
 * </p>
 *
 * <p>
 * Steady and transient callers use the same integrated forces, in N/m. Regime transitions blend forces rather than
 * independently averaging stresses and perimeters. Churn and all non-slug closures retain their original force
 * allocation.
 * </p>
 */
public final class SlugForceBalance implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final WallFriction wallFriction = new WallFriction();
  private final InterfacialFriction interfacialFriction = new InterfacialFriction();

  /** Integrated wall and interphase forces for the current state. */
  public static final class Forces {
    /** Signed gas wall resistance, N/m. */
    public double gasWall;
    /** Signed liquid wall resistance, N/m. */
    public double liquidWall;
    /** Signed momentum transferred from gas to liquid, N/m. */
    public double interfaceForce;
  }

  /**
   * Whether the section has a nonzero slug contribution.
   *
   * @param section local state
   * @return true for slug flow or a transition containing slug flow
   */
  public static boolean applies(TwoFluidSection section) {
    Map<FlowRegime, Double> weights = section.getRegimeWeights();
    return weights == null ? section.getFlowRegime() == FlowRegime.SLUG
        : weights.containsKey(FlowRegime.SLUG) && weights.get(FlowRegime.SLUG) > 0.0;
  }

  /**
   * Evaluate forces without changing phase inventory or velocities.
   *
   * @param section local state
   * @return integrated forces in N/m
   */
  public Forces evaluate(TwoFluidSection section) {
    Map<FlowRegime, Double> weights = section.getRegimeWeights();
    if (weights == null) {
      weights = Collections.singletonMap(section.getFlowRegime(), 1.0);
    }
    Forces result = new Forces();
    for (Map.Entry<FlowRegime, Double> entry : weights.entrySet()) {
      if (entry.getValue() == 0.0) {
        continue;
      }
      boolean liquidWettedSlug = entry.getKey() == FlowRegime.SLUG && section.getLiquidHoldup() > 0.0;
      // The wetting liquid velocity owns the sign of wall work, including fallback.
      double wallGasVelocity = liquidWettedSlug ? section.getLiquidVelocity() : section.getGasVelocity();
      WallFriction.WallFrictionResult wall = wallFriction.calculate(entry.getKey(), wallGasVelocity,
          section.getLiquidVelocity(), section.getGasDensity(), section.getLiquidDensity(), section.getGasViscosity(),
          section.getLiquidViscosity(), section.getLiquidHoldup(), section.getDiameter(), section.getRoughness());
      double gasWall = wall.gasWallForcePerLength;
      double liquidWall = wall.liquidWallForcePerLength;
      if (liquidWettedSlug) {
        liquidWall += gasWall;
        gasWall = 0.0;
      }
      result.gasWall += entry.getValue() * gasWall;
      result.liquidWall += entry.getValue() * liquidWall;
      if (section.getGasHoldup() > 0.0 && section.getLiquidHoldup() > 0.0) {
        InterfacialFriction.InterfacialFrictionResult drag = interfacialFriction.calculate(entry.getKey(),
            section.getGasVelocity(), section.getLiquidVelocity(), section.getGasDensity(), section.getLiquidDensity(),
            section.getGasViscosity(), section.getLiquidViscosity(), section.getLiquidHoldup(), section.getDiameter(),
            section.getSurfaceTension());
        result.interfaceForce += entry.getValue() * drag.interfacialShear * drag.interfacialAreaPerLength;
      }
    }
    return result;
  }

  /**
   * Difference between the pressure gradients required by the two phase balances.
   *
   * @param section local state with both phases present
   * @return gas minus liquid required pressure-loss gradient, Pa/m
   */
  public double residual(TwoFluidSection section) {
    Forces force = evaluate(section);
    return (force.gasWall + force.interfaceForce) / (section.getGasHoldup() * section.getArea())
        - (force.liquidWall - force.interfaceForce) / (section.getLiquidHoldup() * section.getArea())
        + (section.getGasDensity() - section.getLiquidDensity()) * 9.81 * Math.sin(section.getInclination());
  }

  /**
   * Sum of the phase momentum balances, with internal exchange cancelled.
   *
   * @param section local state
   * @return signed friction plus gravity pressure-loss gradient, Pa/m
   */
  public double pressureGradient(TwoFluidSection section) {
    Forces force = evaluate(section);
    return (force.gasWall + force.liquidWall) / section.getArea()
        + (section.getGasHoldup() * section.getGasDensity() + section.getLiquidHoldup() * section.getLiquidDensity())
            * 9.81 * Math.sin(section.getInclination());
  }

  /**
   * Find a local mechanical equilibrium at prescribed forward superficial phase velocities. No minimum-slip or terrain
   * correction is applied after this root.
   *
   * @param section property and regime state, unchanged by this method
   * @param gasFlux gas superficial velocity, m/s
   * @param liquidFlux liquid superficial velocity, m/s
   * @return equilibrium liquid holdup
   * @throws IllegalArgumentException if a phase superficial velocity is not positive and finite
   * @throws IllegalStateException if no finite bracketed mechanical equilibrium exists
   */
  public double solveHoldup(TwoFluidSection section, double gasFlux, double liquidFlux) {
    if (!Double.isFinite(gasFlux) || !Double.isFinite(liquidFlux) || gasFlux <= 0.0 || liquidFlux <= 0.0) {
      throw new IllegalArgumentException("Shared slug equilibrium requires positive phase throughputs");
    }
    TwoFluidSection probe = section.clone();
    double lambda = liquidFlux / (gasFlux + liquidFlux);
    double low = Math.max(Math.ulp(1.0), lambda * 1.0e-6);
    double high = Math.min(1.0 - Math.ulp(1.0), 1.0 - (1.0 - lambda) * 1.0e-6);
    double lowResidual = trialResidual(probe, low, gasFlux, liquidFlux);
    double highResidual = trialResidual(probe, high, gasFlux, liquidFlux);
    if (!Double.isFinite(lowResidual) || !Double.isFinite(highResidual)
        || Math.signum(lowResidual) == Math.signum(highResidual)) {
      throw new IllegalStateException("No bracketed shared slug force equilibrium");
    }
    for (int iteration = 0; iteration < 80; iteration++) {
      double middle = 0.5 * (low + high);
      double value = trialResidual(probe, middle, gasFlux, liquidFlux);
      if (!Double.isFinite(value)) {
        throw new IllegalStateException("Nonfinite shared slug force residual");
      }
      if (Math.abs(value) < 1.0e-8 || high - low < 1.0e-14) {
        return middle;
      }
      if (Math.signum(value) == Math.signum(lowResidual)) {
        low = middle;
        lowResidual = value;
      } else {
        high = middle;
      }
    }
    throw new IllegalStateException("Shared slug force equilibrium did not converge");
  }

  /**
   * Evaluate a holdup trial while preserving superficial phase fluxes.
   *
   * @param probe private scratch state modified by the trial
   * @param liquidHoldup trial holdup
   * @param gasFlux gas superficial velocity, m/s
   * @param liquidFlux liquid superficial velocity, m/s
   * @return gas minus liquid pressure-loss gradient, Pa/m
   */
  private double trialResidual(TwoFluidSection probe, double liquidHoldup, double gasFlux, double liquidFlux) {
    probe.setGasHoldup(1.0 - liquidHoldup);
    probe.setLiquidHoldup(liquidHoldup);
    probe.setGasVelocity(gasFlux / (1.0 - liquidHoldup));
    probe.setLiquidVelocity(liquidFlux / liquidHoldup);
    return residual(probe);
  }
}
