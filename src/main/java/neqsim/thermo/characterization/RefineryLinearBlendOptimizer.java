package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;
import org.apache.commons.math3.optim.linear.LinearObjectiveFunction;
import org.apache.commons.math3.optim.linear.NoFeasibleSolutionException;
import org.apache.commons.math3.optim.linear.NonNegativeConstraint;
import org.apache.commons.math3.optim.linear.Relationship;
import org.apache.commons.math3.optim.linear.SimplexSolver;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;

/**
 * Minimum-cost linear optimizer for resolved refinery blend properties.
 *
 * <p>
 * The decision variables are normalized non-negative source mass fractions. API gravity is linear in reciprocal
 * specific gravity under the existing ideal-additive-volume rule, sulfur and nitrogen are mass-linear, and the Refutas
 * viscosity blending number is mass-linear at one common temperature. The final recipe is reconstructed through the
 * qualified refinery blend classes.
 * </p>
 */
public final class RefineryLinearBlendOptimizer {
  private static final int MAXIMUM_ITERATIONS = 1000;
  private static final double FRACTION_TOLERANCE = 1.0e-9;
  private static final double PROPERTY_TOLERANCE = 1.0e-9;

  private RefineryLinearBlendOptimizer() {
  }

  /**
   * Minimize unit cost subject to refinery quality constraints.
   *
   * @param sourceCostsPerMass finite non-negative costs in one common currency/mass basis
   * @param sourceSpecificGravities resolved source specific gravities
   * @param sourceSulfurMassFractions source total-sulfur mass fractions on a 0-1 basis
   * @param sourceNitrogenMassFractions source total-nitrogen mass fractions on a 0-1 basis
   * @param sourceKinematicViscositiesCSt source viscosities in cSt at the common temperature
   * @param temperatureCelsius common source-viscosity temperature in degrees Celsius
   * @param minimumApiGravity inclusive minimum blend API gravity
   * @param maximumApiGravity inclusive maximum blend API gravity
   * @param maximumSulfurMassFraction inclusive maximum blend sulfur mass fraction
   * @param maximumNitrogenMassFraction inclusive maximum blend nitrogen mass fraction
   * @param minimumKinematicViscosityCSt inclusive minimum blend viscosity in cSt
   * @param maximumKinematicViscosityCSt inclusive maximum blend viscosity in cSt
   * @return immutable minimum-cost blend result
   * @throws IllegalArgumentException for invalid inputs or when no feasible blend exists
   * @throws IllegalStateException when the solver result fails closure or reconstruction checks
   */
  public static Result optimizeMinimumCost(double[] sourceCostsPerMass, double[] sourceSpecificGravities,
      double[] sourceSulfurMassFractions, double[] sourceNitrogenMassFractions, double[] sourceKinematicViscositiesCSt,
      double temperatureCelsius, double minimumApiGravity, double maximumApiGravity, double maximumSulfurMassFraction,
      double maximumNitrogenMassFraction, double minimumKinematicViscosityCSt, double maximumKinematicViscosityCSt) {
    int sourceCount = requireSourceArrays(sourceCostsPerMass, sourceSpecificGravities, sourceSulfurMassFractions,
        sourceNitrogenMassFractions, sourceKinematicViscositiesCSt);
    validateBounds(temperatureCelsius, minimumApiGravity, maximumApiGravity, maximumSulfurMassFraction,
        maximumNitrogenMassFraction, minimumKinematicViscosityCSt, maximumKinematicViscosityCSt);
    for (double cost : sourceCostsPerMass) {
      if (!Double.isFinite(cost) || cost < 0.0) {
        throw new IllegalArgumentException("Source costs must be finite and non-negative");
      }
    }

    double[] equalMasses = new double[sourceCount];
    Arrays.fill(equalMasses, 1.0);
    RefineryAssayBlend.fromBulkProperties(equalMasses, sourceSpecificGravities, sourceSulfurMassFractions,
        sourceNitrogenMassFractions);
    RefineryViscosityBlend.fromMassBasis(equalMasses, sourceKinematicViscositiesCSt, temperatureCelsius);

    double[] apiGravity = new double[sourceCount];
    double[] viscosityBlendingNumber = new double[sourceCount];
    double[] closure = new double[sourceCount];
    Arrays.fill(closure, 1.0);
    for (int i = 0; i < sourceCount; i++) {
      apiGravity[i] = 141.5 / sourceSpecificGravities[i] - 131.5;
      viscosityBlendingNumber[i] = RefineryViscosityBlend
          .calculateViscosityBlendingNumber(sourceKinematicViscositiesCSt[i]);
    }

    List<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
    constraints.add(new LinearConstraint(closure, Relationship.EQ, 1.0));
    addClosedBounds(constraints, apiGravity, minimumApiGravity, maximumApiGravity);
    constraints.add(new LinearConstraint(sourceSulfurMassFractions, Relationship.LEQ, maximumSulfurMassFraction));
    constraints.add(new LinearConstraint(sourceNitrogenMassFractions, Relationship.LEQ, maximumNitrogenMassFraction));
    addClosedBounds(constraints, viscosityBlendingNumber,
        RefineryViscosityBlend.calculateViscosityBlendingNumber(minimumKinematicViscosityCSt),
        RefineryViscosityBlend.calculateViscosityBlendingNumber(maximumKinematicViscosityCSt));

    PointValuePair optimum;
    try {
      optimum = new SimplexSolver().optimize(new MaxIter(MAXIMUM_ITERATIONS),
          new LinearObjectiveFunction(sourceCostsPerMass, 0.0), new LinearConstraintSet(constraints), GoalType.MINIMIZE,
          new NonNegativeConstraint(true));
    } catch (NoFeasibleSolutionException ex) {
      throw new IllegalArgumentException("No feasible refinery blend satisfies the quality constraints", ex);
    } catch (RuntimeException ex) {
      throw new IllegalStateException("Refinery blend linear optimization failed", ex);
    }

    double[] massFractions = normalizeAndValidateFractions(optimum.getPoint(), sourceCount);
    RefineryAssayBlend assayBlend = RefineryAssayBlend.fromBulkProperties(massFractions, sourceSpecificGravities,
        sourceSulfurMassFractions, sourceNitrogenMassFractions);
    RefineryViscosityBlend viscosityBlend = RefineryViscosityBlend.fromMassBasis(massFractions,
        sourceKinematicViscositiesCSt, temperatureCelsius);
    validateReconstructedProperties(assayBlend, viscosityBlend, minimumApiGravity, maximumApiGravity,
        maximumSulfurMassFraction, maximumNitrogenMassFraction, minimumKinematicViscosityCSt,
        maximumKinematicViscosityCSt);

    double unitCostPerMass = 0.0;
    for (int i = 0; i < sourceCount; i++) {
      unitCostPerMass += massFractions[i] * sourceCostsPerMass[i];
    }
    if (!Double.isFinite(unitCostPerMass)) {
      throw new IllegalStateException("Calculated blend unit cost must be finite");
    }
    QualityConstraintReceipt qualityConstraintReceipt = new QualityConstraintReceipt(assayBlend.getApiGravity(),
        minimumApiGravity, maximumApiGravity, assayBlend.getSulfurMassFraction(), maximumSulfurMassFraction,
        assayBlend.getNitrogenMassFraction(), maximumNitrogenMassFraction, viscosityBlend.getKinematicViscosityCSt(),
        minimumKinematicViscosityCSt, maximumKinematicViscosityCSt, temperatureCelsius);
    return new Result(massFractions, assayBlend, viscosityBlend, unitCostPerMass, qualityConstraintReceipt);
  }

  private static int requireSourceArrays(double[] sourceCostsPerMass, double[] sourceSpecificGravities,
      double[] sourceSulfurMassFractions, double[] sourceNitrogenMassFractions,
      double[] sourceKinematicViscositiesCSt) {
    if (sourceCostsPerMass == null || sourceSpecificGravities == null || sourceSulfurMassFractions == null
        || sourceNitrogenMassFractions == null || sourceKinematicViscositiesCSt == null
        || sourceCostsPerMass.length < 2) {
      throw new IllegalArgumentException("At least two complete refinery blend sources are required");
    }
    int sourceCount = sourceCostsPerMass.length;
    if (sourceSpecificGravities.length != sourceCount || sourceSulfurMassFractions.length != sourceCount
        || sourceNitrogenMassFractions.length != sourceCount || sourceKinematicViscositiesCSt.length != sourceCount) {
      throw new IllegalArgumentException("All refinery blend source arrays must have equal length");
    }
    return sourceCount;
  }

  private static void validateBounds(double temperatureCelsius, double minimumApiGravity, double maximumApiGravity,
      double maximumSulfurMassFraction, double maximumNitrogenMassFraction, double minimumKinematicViscosityCSt,
      double maximumKinematicViscosityCSt) {
    if (!Double.isFinite(temperatureCelsius)) {
      throw new IllegalArgumentException("Common viscosity temperature must be finite");
    }
    requireFiniteOrderedBounds(minimumApiGravity, maximumApiGravity, "API-gravity");
    requireMassFraction(maximumSulfurMassFraction, "Maximum sulfur mass fraction");
    requireMassFraction(maximumNitrogenMassFraction, "Maximum nitrogen mass fraction");
    if (!Double.isFinite(minimumKinematicViscosityCSt) || !Double.isFinite(maximumKinematicViscosityCSt)
        || !(minimumKinematicViscosityCSt > 0.2) || maximumKinematicViscosityCSt < minimumKinematicViscosityCSt) {
      throw new IllegalArgumentException(
          "Kinematic-viscosity bounds must be finite, ordered, and greater than 0.2 cSt");
    }
  }

  private static void addClosedBounds(List<LinearConstraint> constraints, double[] coefficients, double minimumValue,
      double maximumValue) {
    constraints.add(new LinearConstraint(coefficients, Relationship.GEQ, minimumValue));
    constraints.add(new LinearConstraint(coefficients, Relationship.LEQ, maximumValue));
  }

  private static double[] normalizeAndValidateFractions(double[] solverPoint, int sourceCount) {
    if (solverPoint == null || solverPoint.length != sourceCount) {
      throw new IllegalStateException("Linear optimizer returned an unexpected source-fraction vector");
    }
    double[] massFractions = Arrays.copyOf(solverPoint, sourceCount);
    double sum = 0.0;
    for (int i = 0; i < massFractions.length; i++) {
      if (!Double.isFinite(massFractions[i]) || massFractions[i] < -FRACTION_TOLERANCE) {
        throw new IllegalStateException("Linear optimizer returned an invalid source mass fraction");
      }
      massFractions[i] = Math.max(0.0, massFractions[i]);
      sum += massFractions[i];
    }
    if (!Double.isFinite(sum) || Math.abs(sum - 1.0) > FRACTION_TOLERANCE) {
      throw new IllegalStateException("Linear optimizer did not close the source mass fractions");
    }
    for (int i = 0; i < massFractions.length; i++) {
      massFractions[i] /= sum;
    }
    return massFractions;
  }

  private static void validateReconstructedProperties(RefineryAssayBlend assayBlend,
      RefineryViscosityBlend viscosityBlend, double minimumApiGravity, double maximumApiGravity,
      double maximumSulfurMassFraction, double maximumNitrogenMassFraction, double minimumKinematicViscosityCSt,
      double maximumKinematicViscosityCSt) {
    requireWithinBounds(assayBlend.getApiGravity(), minimumApiGravity, maximumApiGravity, "API gravity");
    requireUpperBound(assayBlend.getSulfurMassFraction(), maximumSulfurMassFraction, "sulfur mass fraction");
    requireUpperBound(assayBlend.getNitrogenMassFraction(), maximumNitrogenMassFraction, "nitrogen mass fraction");
    requireWithinBounds(viscosityBlend.getKinematicViscosityCSt(), minimumKinematicViscosityCSt,
        maximumKinematicViscosityCSt, "kinematic viscosity");
  }

  private static void requireWithinBounds(double value, double minimumValue, double maximumValue, String propertyName) {
    double tolerance = PROPERTY_TOLERANCE * Math.max(1.0, Math.max(Math.abs(minimumValue), Math.abs(maximumValue)));
    if (!Double.isFinite(value) || value < minimumValue - tolerance || value > maximumValue + tolerance) {
      throw new IllegalStateException("Optimized " + propertyName + " violates its constraint");
    }
  }

  private static void requireUpperBound(double value, double maximumValue, String propertyName) {
    double tolerance = PROPERTY_TOLERANCE * Math.max(1.0, Math.abs(maximumValue));
    if (!Double.isFinite(value) || value < -tolerance || value > maximumValue + tolerance) {
      throw new IllegalStateException("Optimized " + propertyName + " violates its constraint");
    }
  }

  private static void requireFiniteOrderedBounds(double minimumValue, double maximumValue, String propertyName) {
    if (!Double.isFinite(minimumValue) || !Double.isFinite(maximumValue) || maximumValue < minimumValue) {
      throw new IllegalArgumentException(propertyName + " bounds must be finite and ordered");
    }
  }

  private static void requireMassFraction(double value, String propertyName) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(propertyName + " must be finite and between 0 and 1");
    }
  }

  /** Immutable result from a refinery linear blend optimization. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double[] sourceMassFractions;
    private final RefineryAssayBlend assayBlend;
    private final RefineryViscosityBlend viscosityBlend;
    private final double unitCostPerMass;
    private final QualityConstraintReceipt qualityConstraintReceipt;

    private Result(double[] sourceMassFractions, RefineryAssayBlend assayBlend, RefineryViscosityBlend viscosityBlend,
        double unitCostPerMass, QualityConstraintReceipt qualityConstraintReceipt) {
      this.sourceMassFractions = Arrays.copyOf(sourceMassFractions, sourceMassFractions.length);
      this.assayBlend = assayBlend;
      this.viscosityBlend = viscosityBlend;
      this.unitCostPerMass = unitCostPerMass;
      this.qualityConstraintReceipt = qualityConstraintReceipt;
    }

    /** @return defensive copy of optimized source mass fractions */
    public double[] getSourceMassFractions() {
      return Arrays.copyOf(sourceMassFractions, sourceMassFractions.length);
    }

    /** @return immutable ideal-volume and linear-quality blend result */
    public RefineryAssayBlend getAssayBlend() {
      return assayBlend;
    }

    /** @return immutable Refutas viscosity blend result */
    public RefineryViscosityBlend getViscosityBlend() {
      return viscosityBlend;
    }

    /** @return optimized blend cost in the caller's common currency/mass basis */
    public double getUnitCostPerMass() {
      return unitCostPerMass;
    }

    /** @return immutable realized-property, bound, margin, and binding evidence */
    public QualityConstraintReceipt getQualityConstraintReceipt() {
      return qualityConstraintReceipt;
    }
  }

  /** Immutable quality-constraint evidence retained from one qualified optimization. */
  public static final class QualityConstraintReceipt implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double apiGravity;
    private final double minimumApiGravity;
    private final double maximumApiGravity;
    private final double apiLowerMargin;
    private final double apiUpperMargin;
    private final double sulfurMassFraction;
    private final double maximumSulfurMassFraction;
    private final double sulfurMargin;
    private final double nitrogenMassFraction;
    private final double maximumNitrogenMassFraction;
    private final double nitrogenMargin;
    private final double kinematicViscosityCSt;
    private final double minimumKinematicViscosityCSt;
    private final double maximumKinematicViscosityCSt;
    private final double viscosityLowerMarginCSt;
    private final double viscosityUpperMarginCSt;
    private final double temperatureCelsius;

    private QualityConstraintReceipt(double apiGravity, double minimumApiGravity, double maximumApiGravity,
        double sulfurMassFraction, double maximumSulfurMassFraction, double nitrogenMassFraction,
        double maximumNitrogenMassFraction, double kinematicViscosityCSt, double minimumKinematicViscosityCSt,
        double maximumKinematicViscosityCSt, double temperatureCelsius) {
      this.apiGravity = apiGravity;
      this.minimumApiGravity = minimumApiGravity;
      this.maximumApiGravity = maximumApiGravity;
      apiLowerMargin = lowerMargin(apiGravity, minimumApiGravity, "API-gravity lower");
      apiUpperMargin = upperMargin(apiGravity, maximumApiGravity, "API-gravity upper");
      this.sulfurMassFraction = sulfurMassFraction;
      this.maximumSulfurMassFraction = maximumSulfurMassFraction;
      sulfurMargin = upperMargin(sulfurMassFraction, maximumSulfurMassFraction, "sulfur");
      this.nitrogenMassFraction = nitrogenMassFraction;
      this.maximumNitrogenMassFraction = maximumNitrogenMassFraction;
      nitrogenMargin = upperMargin(nitrogenMassFraction, maximumNitrogenMassFraction, "nitrogen");
      this.kinematicViscosityCSt = kinematicViscosityCSt;
      this.minimumKinematicViscosityCSt = minimumKinematicViscosityCSt;
      this.maximumKinematicViscosityCSt = maximumKinematicViscosityCSt;
      viscosityLowerMarginCSt = lowerMargin(kinematicViscosityCSt, minimumKinematicViscosityCSt, "viscosity lower");
      viscosityUpperMarginCSt = upperMargin(kinematicViscosityCSt, maximumKinematicViscosityCSt, "viscosity upper");
      this.temperatureCelsius = temperatureCelsius;
    }

    private static double lowerMargin(double value, double lowerBound, String propertyName) {
      return checkedMargin(value - lowerBound, value, lowerBound, propertyName);
    }

    private static double upperMargin(double value, double upperBound, String propertyName) {
      return checkedMargin(upperBound - value, value, upperBound, propertyName);
    }

    private static double checkedMargin(double margin, double value, double bound, String propertyName) {
      double tolerance = bindingTolerance(value, bound);
      if (!Double.isFinite(margin) || margin < -tolerance) {
        throw new IllegalStateException("Optimized " + propertyName + " margin is invalid");
      }
      return Math.max(0.0, margin);
    }

    private static double bindingTolerance(double value, double bound) {
      return PROPERTY_TOLERANCE * Math.max(1.0, Math.max(Math.abs(value), Math.abs(bound)));
    }

    private static boolean isBinding(double margin, double value, double bound) {
      return margin <= bindingTolerance(value, bound);
    }

    /** @return realized blend API gravity */
    public double getApiGravity() {
      return apiGravity;
    }

    /** @return inclusive minimum API-gravity constraint */
    public double getMinimumApiGravity() {
      return minimumApiGravity;
    }

    /** @return inclusive maximum API-gravity constraint */
    public double getMaximumApiGravity() {
      return maximumApiGravity;
    }

    /** @return non-negative realized API gravity minus its minimum */
    public double getApiLowerMargin() {
      return apiLowerMargin;
    }

    /** @return non-negative maximum API gravity minus its realized value */
    public double getApiUpperMargin() {
      return apiUpperMargin;
    }

    /** @return whether the minimum API-gravity constraint is active within solver tolerance */
    public boolean isMinimumApiGravityBinding() {
      return isBinding(apiLowerMargin, apiGravity, minimumApiGravity);
    }

    /** @return whether the maximum API-gravity constraint is active within solver tolerance */
    public boolean isMaximumApiGravityBinding() {
      return isBinding(apiUpperMargin, apiGravity, maximumApiGravity);
    }

    /** @return realized sulfur mass fraction on a 0-1 basis */
    public double getSulfurMassFraction() {
      return sulfurMassFraction;
    }

    /** @return inclusive maximum sulfur mass-fraction constraint */
    public double getMaximumSulfurMassFraction() {
      return maximumSulfurMassFraction;
    }

    /** @return non-negative sulfur mass-fraction constraint margin */
    public double getSulfurMargin() {
      return sulfurMargin;
    }

    /** @return whether the sulfur constraint is active within solver tolerance */
    public boolean isSulfurBinding() {
      return isBinding(sulfurMargin, sulfurMassFraction, maximumSulfurMassFraction);
    }

    /** @return realized nitrogen mass fraction on a 0-1 basis */
    public double getNitrogenMassFraction() {
      return nitrogenMassFraction;
    }

    /** @return inclusive maximum nitrogen mass-fraction constraint */
    public double getMaximumNitrogenMassFraction() {
      return maximumNitrogenMassFraction;
    }

    /** @return non-negative nitrogen mass-fraction constraint margin */
    public double getNitrogenMargin() {
      return nitrogenMargin;
    }

    /** @return whether the nitrogen constraint is active within solver tolerance */
    public boolean isNitrogenBinding() {
      return isBinding(nitrogenMargin, nitrogenMassFraction, maximumNitrogenMassFraction);
    }

    /** @return realized Refutas-blended kinematic viscosity in cSt */
    public double getKinematicViscosityCSt() {
      return kinematicViscosityCSt;
    }

    /** @return inclusive minimum kinematic-viscosity constraint in cSt */
    public double getMinimumKinematicViscosityCSt() {
      return minimumKinematicViscosityCSt;
    }

    /** @return inclusive maximum kinematic-viscosity constraint in cSt */
    public double getMaximumKinematicViscosityCSt() {
      return maximumKinematicViscosityCSt;
    }

    /** @return non-negative realized viscosity minus its minimum in cSt */
    public double getViscosityLowerMarginCSt() {
      return viscosityLowerMarginCSt;
    }

    /** @return non-negative maximum viscosity minus its realized value in cSt */
    public double getViscosityUpperMarginCSt() {
      return viscosityUpperMarginCSt;
    }

    /** @return whether the minimum viscosity constraint is active within solver tolerance */
    public boolean isMinimumViscosityBinding() {
      return isBinding(viscosityLowerMarginCSt, kinematicViscosityCSt, minimumKinematicViscosityCSt);
    }

    /** @return whether the maximum viscosity constraint is active within solver tolerance */
    public boolean isMaximumViscosityBinding() {
      return isBinding(viscosityUpperMarginCSt, kinematicViscosityCSt, maximumKinematicViscosityCSt);
    }

    /** @return common viscosity temperature in degrees Celsius */
    public double getTemperatureCelsius() {
      return temperatureCelsius;
    }
  }
}
