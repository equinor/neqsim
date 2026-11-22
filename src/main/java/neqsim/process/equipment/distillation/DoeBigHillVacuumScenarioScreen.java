package neqsim.process.equipment.distillation;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationCase.OperatingInputs;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationResult.ProductResult;

/**
 * Immutable multivariable scenario screen for the DOE Big Hill vacuum case.
 *
 * <p>
 * Every scenario supplies a complete set of explicit engineering inputs and a feed mass flow. Each scenario is
 * independently constructed, solved, and evaluated through the qualified Big Hill case and result contracts. This class
 * reports discrete numerical scenarios; it does not define a measured or continuous vacuum-column operating envelope.
 * </p>
 */
public final class DoeBigHillVacuumScenarioScreen {
  private final PointResult[] points;
  private final double minimumOverheadMassFraction;
  private final double maximumOverheadMassFraction;
  private final double maximumMassClosureRelativeError;
  private final double maximumComponentMolarClosureRelativeError;
  private final double maximumComponentRecoveryClosureError;
  private final double maximumColumnEnergyBalanceError;
  private final double maximumMeshResidualNorm;

  private DoeBigHillVacuumScenarioScreen(PointResult[] points) {
    this.points = points.clone();

    double minimumOverheadFraction = Double.POSITIVE_INFINITY;
    double maximumOverheadFraction = Double.NEGATIVE_INFINITY;
    double maximumMassClosure = 0.0;
    double maximumComponentClosure = 0.0;
    double maximumRecoveryClosure = 0.0;
    double maximumEnergyError = 0.0;
    double maximumMeshResidual = 0.0;
    for (PointResult point : points) {
      DoeBigHillVacuumFractionationResult result = point.getFractionationResult();
      double overheadFraction = result.getProduct("Overhead").getMassFractionOfFeed();
      minimumOverheadFraction = Math.min(minimumOverheadFraction, overheadFraction);
      maximumOverheadFraction = Math.max(maximumOverheadFraction, overheadFraction);
      maximumMassClosure = Math.max(maximumMassClosure, result.getMassClosureRelativeError());
      maximumComponentClosure = Math.max(maximumComponentClosure,
          result.getMaximumComponentMolarClosureRelativeError());
      maximumRecoveryClosure = Math.max(maximumRecoveryClosure,
          point.getComponentRecovery().getMaximumComponentRecoveryClosureError());
      maximumEnergyError = Math.max(maximumEnergyError, result.getColumnEnergyBalanceError());
      maximumMeshResidual = Math.max(maximumMeshResidual, result.getMeshResidualNorm());
    }

    minimumOverheadMassFraction = minimumOverheadFraction;
    maximumOverheadMassFraction = maximumOverheadFraction;
    maximumMassClosureRelativeError = maximumMassClosure;
    maximumComponentMolarClosureRelativeError = maximumComponentClosure;
    maximumComponentRecoveryClosureError = maximumRecoveryClosure;
    maximumColumnEnergyBalanceError = maximumEnergyError;
    maximumMeshResidualNorm = maximumMeshResidual;
  }

  /**
   * Run independently constructed cases for explicit multivariable scenarios.
   *
   * @param caseNamePrefix non-blank prefix used for independently constructed case names
   * @param scenarios at least two uniquely named scenarios in caller-defined order
   * @return immutable scenario-screen summary
   * @throws NullPointerException if {@code scenarios} or one scenario is null
   * @throws IllegalArgumentException if the name, scenario count, or scenario names are invalid
   * @throws IllegalStateException if a scenario does not solve or pass the qualified result gates
   */
  public static DoeBigHillVacuumScenarioScreen run(String caseNamePrefix, Scenario[] scenarios) {
    if (caseNamePrefix == null || caseNamePrefix.trim().isEmpty()) {
      throw new IllegalArgumentException("Case-name prefix must be non-blank");
    }
    Objects.requireNonNull(scenarios, "scenarios");
    if (scenarios.length < 2) {
      throw new IllegalArgumentException("Scenario screen requires at least two points");
    }

    Scenario[] requestedScenarios = scenarios.clone();
    validateUniqueNames(requestedScenarios);
    PointResult[] evaluatedPoints = new PointResult[requestedScenarios.length];
    for (int i = 0; i < requestedScenarios.length; i++) {
      Scenario scenario = requestedScenarios[i];
      DoeBigHillVacuumFractionationCase model = DoeBigHillVacuumFractionationCase.create(
          caseNamePrefix + " scenario " + scenario.getName(), scenario.getFeedMassFlowKgPerHour(),
          scenario.getOperatingInputs());
      try {
        model.getColumn().run(UUID.randomUUID());
        DoeBigHillVacuumFractionationResult result = DoeBigHillVacuumFractionationResult.evaluate(model);
        DoeBigHillVacuumComponentRecovery componentRecovery = DoeBigHillVacuumComponentRecovery.evaluate(model);
        evaluatedPoints[i] = new PointResult(scenario, result, componentRecovery);
      } catch (RuntimeException exception) {
        throw new IllegalStateException("Vacuum scenario screen failed at point " + i + " (" + scenario.getName() + ")",
            exception);
      }
    }
    return new DoeBigHillVacuumScenarioScreen(evaluatedPoints);
  }

  /** @return defensive copy of scenario points in caller-defined order */
  public PointResult[] getPoints() {
    return points.clone();
  }

  /**
   * Return one scenario point by zero-based index.
   *
   * @param index zero-based point index
   * @return immutable point result
   * @throws IndexOutOfBoundsException if {@code index} is outside the screen
   */
  public PointResult getPoint(int index) {
    if (index < 0 || index >= points.length) {
      throw new IndexOutOfBoundsException("Scenario-screen point index is outside the result");
    }
    return points[index];
  }

  /** @return smallest overhead mass fraction across the qualified scenarios */
  public double getMinimumOverheadMassFraction() {
    return minimumOverheadMassFraction;
  }

  /** @return largest overhead mass fraction across the qualified scenarios */
  public double getMaximumOverheadMassFraction() {
    return maximumOverheadMassFraction;
  }

  /** @return largest external mass-closure relative error across the qualified scenarios */
  public double getMaximumMassClosureRelativeError() {
    return maximumMassClosureRelativeError;
  }

  /** @return largest component molar-closure relative error across the qualified scenarios */
  public double getMaximumComponentMolarClosureRelativeError() {
    return maximumComponentMolarClosureRelativeError;
  }

  /** @return largest component-recovery closure error across the qualified scenarios */
  public double getMaximumComponentRecoveryClosureError() {
    return maximumComponentRecoveryClosureError;
  }

  /** @return largest column energy-balance error across the qualified scenarios */
  public double getMaximumColumnEnergyBalanceError() {
    return maximumColumnEnergyBalanceError;
  }

  /** @return largest final MESH residual norm across the qualified scenarios */
  public double getMaximumMeshResidualNorm() {
    return maximumMeshResidualNorm;
  }

  private static void validateUniqueNames(Scenario[] scenarios) {
    Set<String> normalizedNames = new HashSet<String>();
    for (Scenario scenario : scenarios) {
      Objects.requireNonNull(scenario, "scenario");
      String normalizedName = scenario.getName().toLowerCase(Locale.ROOT);
      if (!normalizedNames.add(normalizedName)) {
        throw new IllegalArgumentException("Scenario names must be unique");
      }
    }
  }

  /** Immutable complete input definition for one numerical scenario. */
  public static final class Scenario {
    private final String name;
    private final double feedMassFlowKgPerHour;
    private final OperatingInputs operatingInputs;

    /**
     * Create one explicit scenario.
     *
     * @param name non-blank unique scenario name
     * @param feedMassFlowKgPerHour finite positive feed mass flow in kg/h
     * @param operatingInputs complete immutable column operating inputs
     * @throws NullPointerException if {@code operatingInputs} is null
     * @throws IllegalArgumentException if the name or feed mass flow is invalid
     */
    public Scenario(String name, double feedMassFlowKgPerHour, OperatingInputs operatingInputs) {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Scenario name must be non-blank");
      }
      if (!Double.isFinite(feedMassFlowKgPerHour) || !(feedMassFlowKgPerHour > 0.0)) {
        throw new IllegalArgumentException("Feed mass flow must be finite and positive");
      }
      this.name = name.trim();
      this.feedMassFlowKgPerHour = feedMassFlowKgPerHour;
      this.operatingInputs = Objects.requireNonNull(operatingInputs, "operatingInputs");
    }

    /** @return trimmed scenario name */
    public String getName() {
      return name;
    }

    /** @return feed mass flow in kg/h */
    public double getFeedMassFlowKgPerHour() {
      return feedMassFlowKgPerHour;
    }

    /** @return immutable explicit column operating inputs */
    public OperatingInputs getOperatingInputs() {
      return operatingInputs;
    }
  }

  /** Immutable result for one independently solved scenario. */
  public static final class PointResult {
    private final Scenario scenario;
    private final DoeBigHillVacuumFractionationResult fractionationResult;
    private final DoeBigHillVacuumComponentRecovery componentRecovery;

    private PointResult(Scenario scenario, DoeBigHillVacuumFractionationResult fractionationResult,
        DoeBigHillVacuumComponentRecovery componentRecovery) {
      this.scenario = Objects.requireNonNull(scenario, "scenario");
      this.fractionationResult = Objects.requireNonNull(fractionationResult, "fractionationResult");
      this.componentRecovery = Objects.requireNonNull(componentRecovery, "componentRecovery");
    }

    /** @return immutable scenario definition applied at this point */
    public Scenario getScenario() {
      return scenario;
    }

    /** @return qualified immutable fractionation result for this scenario */
    public DoeBigHillVacuumFractionationResult getFractionationResult() {
      return fractionationResult;
    }

    /** @return immutable pseudo-component recovery evidence from the same solved scenario */
    public DoeBigHillVacuumComponentRecovery getComponentRecovery() {
      return componentRecovery;
    }

    /** @return overhead mass fraction of feed for this scenario */
    public double getOverheadMassFraction() {
      return fractionationResult.getProduct("Overhead").getMassFractionOfFeed();
    }

    /**
     * Return a discrete overhead normal-boiling-point diagnostic.
     *
     * @param cumulativeMoleFraction cumulative product mole fraction in (0, 1]
     * @return overhead pseudo-component quantile in kelvin
     */
    public double getOverheadBoilingPointQuantileKelvin(double cumulativeMoleFraction) {
      ProductResult overhead = fractionationResult.getProduct("Overhead");
      return overhead.getNormalBoilingPointQuantileKelvin(cumulativeMoleFraction);
    }
  }
}
