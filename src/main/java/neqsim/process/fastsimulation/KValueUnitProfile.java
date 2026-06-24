package neqsim.process.fastsimulation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Frozen K-value and fallback split-factor profile for one process unit.
 *
 * <p>
 * A two-outlet vapour/liquid unit stores one equilibrium ratio per component, {@code K_i = y_i / x_i}. A three-outlet
 * gas/oil/water unit stores gas-over-oil and water-over-oil distribution ratios. During a fast rerun the unit computes
 * new phase fractions from the incoming component vector and routes the component moles without calling an EOS flash.
 * Units that cannot be represented by cached K-values use the frozen per-outlet component factors as a conservative
 * fallback.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class KValueUnitProfile implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Minimum component flow used to avoid division by zero. */
  private static final double MIN_FLOW = 1.0e-30;

  /** Minimum accepted K-value. */
  private static final double MIN_K = 1.0e-12;

  /** Maximum accepted K-value. */
  private static final double MAX_K = 1.0e12;

  /** Name of the unit represented by this profile. */
  private final String unitName;

  /** Simple class name of the represented unit. */
  private final String equipmentType;

  /** Inlet streams of the unit. */
  private final List<StreamInterface> inletStreams;

  /** Outlet streams of the unit. */
  private final List<StreamInterface> outletStreams;

  /** Component names used by all profile arrays. */
  private final String[] componentNames;

  /** Frozen fallback split factors indexed by outlet and component. */
  private final double[][] fallbackSplitFactors;

  /**
   * Cached vapour/liquid or gas/oil K-values, or {@code null} when the unit uses fallback routing.
   */
  private final double[] vaporLiquidKValues;

  /** Cached water/oil distribution ratios for three-phase routing. */
  private final double[] waterLiquidKValues;

  /** Outlet index of the gas stream when K-value routing is active. */
  private final int gasOutletIndex;

  /** Outlet index of the liquid stream when K-value routing is active. */
  private final int liquidOutletIndex;

  /** Outlet index of the water stream when three-phase K-value routing is active. */
  private final int waterOutletIndex;

  /** Base-case inlet component flow in mole/sec. */
  private final double[] baseInletComponentFlow;

  /** True when the profile can use K-value Rachford-Rice routing. */
  private final boolean usesKValueRouting;

  /** True when the profile can use gas/oil/water K-value routing. */
  private final boolean usesThreePhaseKValueRouting;

  /**
   * Creates a unit profile.
   *
   * @param unitName name of the unit; must be non-null
   * @param equipmentType simple class name of the unit; must be non-null
   * @param inletStreams inlet streams for the unit; must be non-null
   * @param outletStreams outlet streams for the unit; must be non-null
   * @param componentNames component slate used by the profile; must be non-null
   * @param fallbackSplitFactors frozen fallback factors indexed by outlet and component; must match the outlet and
   * component counts
   * @param vaporLiquidKValues cached vapour/liquid or gas/oil K-values, or {@code null} for fallback-only units
   * @param waterLiquidKValues cached water/oil ratios, or {@code null} for two-phase or fallback-only units
   * @param gasOutletIndex gas outlet index, or {@code -1} when fallback-only
   * @param liquidOutletIndex liquid outlet index, or {@code -1} when fallback-only
   * @param waterOutletIndex water outlet index, or {@code -1} when not using three-phase routing
   * @param baseInletComponentFlow base inlet component flow in mole/sec; must match the component count
   */
  public KValueUnitProfile(String unitName, String equipmentType, List<StreamInterface> inletStreams,
      List<StreamInterface> outletStreams, String[] componentNames, double[][] fallbackSplitFactors,
      double[] vaporLiquidKValues, double[] waterLiquidKValues, int gasOutletIndex, int liquidOutletIndex,
      int waterOutletIndex, double[] baseInletComponentFlow) {
    this.unitName = unitName;
    this.equipmentType = equipmentType;
    this.inletStreams = new ArrayList<StreamInterface>(inletStreams);
    this.outletStreams = new ArrayList<StreamInterface>(outletStreams);
    this.componentNames = componentNames.clone();
    this.fallbackSplitFactors = copyMatrix(fallbackSplitFactors);
    this.vaporLiquidKValues = vaporLiquidKValues == null ? null : vaporLiquidKValues.clone();
    this.waterLiquidKValues = waterLiquidKValues == null ? null : waterLiquidKValues.clone();
    this.gasOutletIndex = gasOutletIndex;
    this.liquidOutletIndex = liquidOutletIndex;
    this.waterOutletIndex = waterOutletIndex;
    this.baseInletComponentFlow = baseInletComponentFlow.clone();
    this.usesKValueRouting = vaporLiquidKValues != null && gasOutletIndex >= 0 && liquidOutletIndex >= 0;
    this.usesThreePhaseKValueRouting = usesKValueRouting && waterLiquidKValues != null && waterOutletIndex >= 0;
  }

  /**
   * Gets the unit name.
   *
   * @return unit name
   */
  public String getUnitName() {
    return unitName;
  }

  /**
   * Gets the simple equipment type name.
   *
   * @return equipment type name
   */
  public String getEquipmentType() {
    return equipmentType;
  }

  /**
   * Gets the unit inlet streams.
   *
   * @return unmodifiable inlet stream list
   */
  public List<StreamInterface> getInletStreams() {
    return Collections.unmodifiableList(inletStreams);
  }

  /**
   * Gets the unit outlet streams.
   *
   * @return unmodifiable outlet stream list
   */
  public List<StreamInterface> getOutletStreams() {
    return Collections.unmodifiableList(outletStreams);
  }

  /**
   * Gets the component names used by this profile.
   *
   * @return copy of the component slate
   */
  public String[] getComponentNames() {
    return componentNames.clone();
  }

  /**
   * Gets a copy of the cached K-values.
   *
   * @return cached K-values, or {@code null} for fallback-only units
   */
  public double[] getVaporLiquidKValues() {
    return vaporLiquidKValues == null ? null : vaporLiquidKValues.clone();
  }

  /**
   * Gets a copy of the cached water/liquid ratios.
   *
   * @return cached water/oil ratios, or {@code null} for non-three-phase units
   */
  public double[] getWaterLiquidKValues() {
    return waterLiquidKValues == null ? null : waterLiquidKValues.clone();
  }

  /**
   * Reports whether the unit is routed with cached K-values.
   *
   * @return {@code true} when Rachford-Rice K-value routing is active
   */
  public boolean usesKValueRouting() {
    return usesKValueRouting;
  }

  /**
   * Reports whether the unit is routed with three-phase cached K-values.
   *
   * @return {@code true} when gas/oil/water K-value routing is active
   */
  public boolean usesThreePhaseKValueRouting() {
    return usesThreePhaseKValueRouting;
  }

  /**
   * Gets the base-case inlet component flow.
   *
   * @param componentIndex component index in {@link #getComponentNames()}
   * @return inlet component flow in mole/sec
   */
  public double getBaseInletComponentFlow(int componentIndex) {
    return baseInletComponentFlow[componentIndex];
  }

  /**
   * Gets a stable result key for one outlet stream.
   *
   * @param outletIndex outlet index in {@link #getOutletStreams()}
   * @return key formatted as {@code unitName.outletStreamName}
   */
  public String getOutletKey(int outletIndex) {
    return unitName + "." + outletStreams.get(outletIndex).getName();
  }

  /**
   * Routes an inlet component vector to the unit outlets.
   *
   * @param inletComponentFlow component molar flows entering the unit in mole/sec; must match the component count
   * @return outlet component molar flows indexed by outlet and component
   */
  double[][] route(double[] inletComponentFlow) {
    if (usesThreePhaseKValueRouting) {
      return routeWithThreePhaseKValues(inletComponentFlow);
    } else if (usesKValueRouting) {
      return routeWithKValues(inletComponentFlow);
    }
    return routeWithFrozenFactors(inletComponentFlow);
  }

  /**
   * Routes a component vector with cached gas/oil and water/oil ratios.
   *
   * @param inletComponentFlow component molar flows entering the unit in mole/sec
   * @return outlet component molar flows indexed by outlet and component
   */
  private double[][] routeWithThreePhaseKValues(double[] inletComponentFlow) {
    double[][] routed = new double[outletStreams.size()][componentNames.length];
    double total = sum(inletComponentFlow);
    if (total <= MIN_FLOW) {
      return routed;
    }

    double[] phaseFractions = solveThreePhaseFractions(inletComponentFlow, total);
    if (phaseFractions == null) {
      return routeWithFrozenFactors(inletComponentFlow);
    }

    double vaporFraction = phaseFractions[0];
    double liquidFraction = phaseFractions[1];
    double waterFraction = phaseFractions[2];
    for (int component = 0; component < componentNames.length; component++) {
      double feed = Math.max(0.0, inletComponentFlow[component]);
      if (feed <= MIN_FLOW) {
        continue;
      }
      double z = feed / total;
      double vaporK = clampK(vaporLiquidKValues[component]);
      double waterK = clampK(waterLiquidKValues[component]);
      double denominator = liquidFraction + vaporFraction * vaporK + waterFraction * waterK;
      if (denominator < MIN_FLOW) {
        return routeWithFrozenFactors(inletComponentFlow);
      }
      double liquidMoleFraction = z / denominator;
      routed[gasOutletIndex][component] = vaporFraction * total * vaporK * liquidMoleFraction;
      routed[liquidOutletIndex][component] = liquidFraction * total * liquidMoleFraction;
      routed[waterOutletIndex][component] = waterFraction * total * waterK * liquidMoleFraction;
    }
    return routed;
  }

  /**
   * Routes a component vector with cached K-values and a Rachford-Rice vapour fraction.
   *
   * @param inletComponentFlow component molar flows entering the unit in mole/sec
   * @return outlet component molar flows indexed by outlet and component
   */
  private double[][] routeWithKValues(double[] inletComponentFlow) {
    double[][] routed = new double[outletStreams.size()][componentNames.length];
    double total = sum(inletComponentFlow);
    if (total <= MIN_FLOW) {
      return routed;
    }

    double vaporFraction = solveVaporFraction(inletComponentFlow, total);
    for (int k = 0; k < componentNames.length; k++) {
      double feed = Math.max(0.0, inletComponentFlow[k]);
      if (feed <= MIN_FLOW) {
        continue;
      }
      double z = feed / total;
      double kValue = clampK(vaporLiquidKValues[k]);
      double denominator = 1.0 + vaporFraction * (kValue - 1.0);
      if (denominator < MIN_FLOW) {
        denominator = MIN_FLOW;
      }
      double liquidMoleFraction = z / denominator;
      double vaporMoleFraction = kValue * liquidMoleFraction;
      routed[gasOutletIndex][k] = vaporFraction * total * vaporMoleFraction;
      routed[liquidOutletIndex][k] = (1.0 - vaporFraction) * total * liquidMoleFraction;
    }
    return routed;
  }

  /**
   * Routes a component vector with frozen fallback split factors.
   *
   * @param inletComponentFlow component molar flows entering the unit in mole/sec
   * @return outlet component molar flows indexed by outlet and component
   */
  private double[][] routeWithFrozenFactors(double[] inletComponentFlow) {
    double[][] routed = new double[outletStreams.size()][componentNames.length];
    for (int outlet = 0; outlet < outletStreams.size(); outlet++) {
      for (int component = 0; component < componentNames.length; component++) {
        routed[outlet][component] = Math.max(0.0, inletComponentFlow[component])
            * fallbackSplitFactors[outlet][component];
      }
    }
    return routed;
  }

  /**
   * Solves the two-phase vapour fraction for cached K-values.
   *
   * @param inletComponentFlow component molar flows entering the unit in mole/sec
   * @param total total molar flow in mole/sec
   * @return vapour fraction in the range {@code [0, 1]}
   */
  private double solveVaporFraction(double[] inletComponentFlow, double total) {
    double fAtZero = rachfordRiceFunction(0.0, inletComponentFlow, total);
    double fAtOne = rachfordRiceFunction(1.0, inletComponentFlow, total);
    if (fAtZero <= 0.0) {
      return 0.0;
    }
    if (fAtOne >= 0.0) {
      return 1.0;
    }

    double lower = 0.0;
    double upper = 1.0;
    for (int iteration = 0; iteration < 80; iteration++) {
      double middle = 0.5 * (lower + upper);
      double value = rachfordRiceFunction(middle, inletComponentFlow, total);
      if (Math.abs(value) < 1.0e-14 || upper - lower < 1.0e-12) {
        return middle;
      }
      if (value > 0.0) {
        lower = middle;
      } else {
        upper = middle;
      }
    }
    return 0.5 * (lower + upper);
  }

  /**
   * Solves gas, oil, and water phase fractions for cached three-phase ratios.
   *
   * @param inletComponentFlow component molar flows entering the unit in mole/sec
   * @param total total molar flow in mole/sec
   * @return phase fractions indexed gas, liquid, water, or {@code null} if no stable three-phase solution was found
   */
  private double[] solveThreePhaseFractions(double[] inletComponentFlow, double total) {
    double vaporFraction = estimateBaseOutletFraction(gasOutletIndex);
    double waterFraction = estimateBaseOutletFraction(waterOutletIndex);
    if (!isValidThreePhaseFractions(vaporFraction, waterFraction)) {
      vaporFraction = 0.2;
      waterFraction = 0.2;
    }

    for (int iteration = 0; iteration < 80; iteration++) {
      double[] residualAndJacobian = threePhaseResidualAndJacobian(vaporFraction, waterFraction, inletComponentFlow,
          total);
      if (residualAndJacobian == null) {
        return null;
      }
      double gasResidual = residualAndJacobian[0];
      double waterResidual = residualAndJacobian[1];
      double residualNorm = Math.max(Math.abs(gasResidual), Math.abs(waterResidual));
      if (residualNorm < 1.0e-12) {
        return new double[] { vaporFraction, 1.0 - vaporFraction - waterFraction, waterFraction };
      }

      double jgg = residualAndJacobian[2];
      double jgw = residualAndJacobian[3];
      double jwg = residualAndJacobian[4];
      double jww = residualAndJacobian[5];
      double determinant = jgg * jww - jgw * jwg;
      if (Math.abs(determinant) < 1.0e-30) {
        return null;
      }

      double deltaGas = (-gasResidual * jww + jgw * waterResidual) / determinant;
      double deltaWater = (jwg * gasResidual - jgg * waterResidual) / determinant;
      boolean accepted = false;
      double step = 1.0;
      while (step > 1.0e-8) {
        double trialGas = vaporFraction + step * deltaGas;
        double trialWater = waterFraction + step * deltaWater;
        if (isValidThreePhaseFractions(trialGas, trialWater)
            && threePhaseDenominatorsArePositive(trialGas, trialWater, inletComponentFlow, total)) {
          double[] trialResidualAndJacobian = threePhaseResidualAndJacobian(trialGas, trialWater, inletComponentFlow,
              total);
          if (trialResidualAndJacobian != null && Math.max(Math.abs(trialResidualAndJacobian[0]),
              Math.abs(trialResidualAndJacobian[1])) < residualNorm) {
            vaporFraction = trialGas;
            waterFraction = trialWater;
            accepted = true;
            break;
          }
        }
        step *= 0.5;
      }
      if (!accepted) {
        return null;
      }
    }
    double[] residualAndJacobian = threePhaseResidualAndJacobian(vaporFraction, waterFraction, inletComponentFlow,
        total);
    if (residualAndJacobian != null
        && Math.max(Math.abs(residualAndJacobian[0]), Math.abs(residualAndJacobian[1])) < 1.0e-8) {
      return new double[] { vaporFraction, 1.0 - vaporFraction - waterFraction, waterFraction };
    }
    return null;
  }

  /**
   * Evaluates three-phase residuals and Jacobian terms.
   *
   * @param vaporFraction trial vapour fraction
   * @param waterFraction trial water fraction
   * @param inletComponentFlow component molar flows entering the unit in mole/sec
   * @param total total molar flow in mole/sec
   * @return array containing gas residual, water residual, and four Jacobian terms, or {@code null} for invalid
   * denominators
   */
  private double[] threePhaseResidualAndJacobian(double vaporFraction, double waterFraction,
      double[] inletComponentFlow, double total) {
    double gasResidual = 0.0;
    double waterResidual = 0.0;
    double jgg = 0.0;
    double jgw = 0.0;
    double jwg = 0.0;
    double jww = 0.0;
    for (int component = 0; component < componentNames.length; component++) {
      double z = Math.max(0.0, inletComponentFlow[component]) / total;
      double gasDelta = clampK(vaporLiquidKValues[component]) - 1.0;
      double waterDelta = clampK(waterLiquidKValues[component]) - 1.0;
      double denominator = 1.0 + vaporFraction * gasDelta + waterFraction * waterDelta;
      if (denominator <= MIN_FLOW) {
        return null;
      }
      double denominatorSquared = denominator * denominator;
      gasResidual += z * gasDelta / denominator;
      waterResidual += z * waterDelta / denominator;
      jgg -= z * gasDelta * gasDelta / denominatorSquared;
      jgw -= z * gasDelta * waterDelta / denominatorSquared;
      jwg -= z * waterDelta * gasDelta / denominatorSquared;
      jww -= z * waterDelta * waterDelta / denominatorSquared;
    }
    return new double[] { gasResidual, waterResidual, jgg, jgw, jwg, jww };
  }

  /**
   * Checks whether trial three-phase fractions are inside the feasible simplex.
   *
   * @param vaporFraction trial vapour fraction
   * @param waterFraction trial water fraction
   * @return {@code true} when gas, oil, and water fractions are positive
   */
  private boolean isValidThreePhaseFractions(double vaporFraction, double waterFraction) {
    return vaporFraction > 0.0 && waterFraction > 0.0 && vaporFraction + waterFraction < 1.0;
  }

  /**
   * Checks whether three-phase denominators are positive for every present component.
   *
   * @param vaporFraction trial vapour fraction
   * @param waterFraction trial water fraction
   * @param inletComponentFlow component molar flows entering the unit in mole/sec
   * @param total total molar flow in mole/sec
   * @return {@code true} when all denominators are positive
   */
  private boolean threePhaseDenominatorsArePositive(double vaporFraction, double waterFraction,
      double[] inletComponentFlow, double total) {
    for (int component = 0; component < componentNames.length; component++) {
      double z = Math.max(0.0, inletComponentFlow[component]) / total;
      if (z <= MIN_FLOW) {
        continue;
      }
      double denominator = 1.0 + vaporFraction * (clampK(vaporLiquidKValues[component]) - 1.0)
          + waterFraction * (clampK(waterLiquidKValues[component]) - 1.0);
      if (denominator <= MIN_FLOW) {
        return false;
      }
    }
    return true;
  }

  /**
   * Estimates the base-case mole fraction routed to one outlet.
   *
   * @param outletIndex outlet index
   * @return base-case outlet fraction, or {@code 0.0} when unavailable
   */
  private double estimateBaseOutletFraction(int outletIndex) {
    if (outletIndex < 0 || outletIndex >= fallbackSplitFactors.length) {
      return 0.0;
    }
    double inletTotal = sum(baseInletComponentFlow);
    if (inletTotal <= MIN_FLOW) {
      return 0.0;
    }
    double outletTotal = 0.0;
    for (int component = 0; component < componentNames.length; component++) {
      outletTotal += Math.max(0.0, baseInletComponentFlow[component]) * fallbackSplitFactors[outletIndex][component];
    }
    return outletTotal / inletTotal;
  }

  /**
   * Evaluates the Rachford-Rice function.
   *
   * @param vaporFraction trial vapour fraction
   * @param inletComponentFlow component molar flows entering the unit in mole/sec
   * @param total total molar flow in mole/sec
   * @return Rachford-Rice residual
   */
  private double rachfordRiceFunction(double vaporFraction, double[] inletComponentFlow, double total) {
    double value = 0.0;
    for (int component = 0; component < componentNames.length; component++) {
      double z = Math.max(0.0, inletComponentFlow[component]) / total;
      double kValue = clampK(vaporLiquidKValues[component]);
      double km1 = kValue - 1.0;
      double denominator = 1.0 + vaporFraction * km1;
      if (Math.abs(denominator) < MIN_FLOW) {
        denominator = denominator < 0.0 ? -MIN_FLOW : MIN_FLOW;
      }
      value += z * km1 / denominator;
    }
    return value;
  }

  /**
   * Clamps a K-value to a finite positive range.
   *
   * @param value raw K-value
   * @return clamped K-value
   */
  private double clampK(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return 1.0;
    }
    if (value < MIN_K) {
      return MIN_K;
    }
    if (value > MAX_K) {
      return MAX_K;
    }
    return value;
  }

  /**
   * Sums the positive entries of an array.
   *
   * @param values values to sum
   * @return positive-entry sum
   */
  private double sum(double[] values) {
    double total = 0.0;
    for (int i = 0; i < values.length; i++) {
      total += Math.max(0.0, values[i]);
    }
    return total;
  }

  /**
   * Copies a matrix defensively.
   *
   * @param matrix source matrix
   * @return copied matrix
   */
  private static double[][] copyMatrix(double[][] matrix) {
    double[][] copy = new double[matrix.length][];
    for (int i = 0; i < matrix.length; i++) {
      copy[i] = matrix[i].clone();
    }
    return copy;
  }
}
