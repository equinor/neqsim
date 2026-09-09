package neqsim.process.equipment.distillation;

import java.util.Objects;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Immutable engineering summary for a solved {@link DoeBigHillVacuumFractionationCase}.
 *
 * <p>
 * The result qualifies numerical convergence, conservation, and product ordering for an explicit
 * screening case. It is not a measured DOE vacuum-column material balance or a product-yield
 * validation.
 * </p>
 */
public final class DoeBigHillVacuumFractionationResult {
  private static final String[] PRODUCT_LABELS = { "Overhead", "Bottoms" };
  private static final double MATERIAL_FLOW_FRACTION = 1.0e-8;
  private static final double BALANCE_TOLERANCE = 5.0e-2;

  private final ProductResult[] products;
  private final double feedMassFlowKgPerHour;
  private final double productMassFlowKgPerHour;
  private final double massClosureRelativeError;
  private final double maximumComponentMolarClosureRelativeError;
  private final double columnMassBalanceError;
  private final double columnEnergyBalanceError;
  private final double meshResidualNorm;
  private final int iterationCount;
  private final double solveTimeSeconds;
  private final String convergenceDiagnostics;

  private DoeBigHillVacuumFractionationResult(ProductResult[] products, double feedMassFlowKgPerHour,
      double productMassFlowKgPerHour, double massClosureRelativeError,
      double maximumComponentMolarClosureRelativeError, double columnMassBalanceError,
      double columnEnergyBalanceError, double meshResidualNorm, int iterationCount,
      double solveTimeSeconds, String convergenceDiagnostics) {
    this.products = products.clone();
    this.feedMassFlowKgPerHour = feedMassFlowKgPerHour;
    this.productMassFlowKgPerHour = productMassFlowKgPerHour;
    this.massClosureRelativeError = massClosureRelativeError;
    this.maximumComponentMolarClosureRelativeError = maximumComponentMolarClosureRelativeError;
    this.columnMassBalanceError = columnMassBalanceError;
    this.columnEnergyBalanceError = columnEnergyBalanceError;
    this.meshResidualNorm = meshResidualNorm;
    this.iterationCount = iterationCount;
    this.solveTimeSeconds = solveTimeSeconds;
    this.convergenceDiagnostics = convergenceDiagnostics;
  }

  /**
   * Evaluate an already solved Big Hill low-pressure screening case.
   *
   * @param model solved case to evaluate
   * @return immutable product and convergence summary
   * @throws NullPointerException if {@code model} is {@code null}
   * @throws IllegalStateException if the case is unsolved, used fallback products, is
   *         non-conservative, or contains invalid or unordered products
   */
  public static DoeBigHillVacuumFractionationResult evaluate(
      DoeBigHillVacuumFractionationCase model) {
    Objects.requireNonNull(model, "model");
    DistillationColumn column = model.getColumn();
    if (!column.solved()) {
      throw new IllegalStateException("Big Hill vacuum screening column must be solved before evaluation");
    }
    if (column.getLastSolverTypeUsed() != DistillationColumn.SolverType.MESH_RESIDUAL) {
      throw new IllegalStateException("Big Hill vacuum screening column must use the MESH-residual solver");
    }
    if (column.getLastSolveStatus() == DistillationColumn.SolveStatus.FALLBACK_PRODUCTS
        || column.getLastSolveStatus() == DistillationColumn.SolveStatus.FAILED) {
      throw new IllegalStateException("Fallback or failed column results cannot be evaluated");
    }

    double massBalanceError = column.getMassBalanceError();
    double energyBalanceError = column.getEnergyBalanceError();
    requireFiniteBoundedError(massBalanceError, "Column mass-balance error",
        BALANCE_TOLERANCE);
    requireFiniteBoundedError(energyBalanceError, "Column energy-balance error",
        BALANCE_TOLERANCE);
    requireFiniteBoundedError(column.getLastTrayMaterialBalanceError(),
        "Maximum tray material-balance error", column.getTrayMaterialBalanceTolerance());
    double meshResidual = column.getLastMeshResidualNorm();
    requireFiniteBoundedError(meshResidual, "MESH residual", column.getMeshResidualTolerance());

    double feedMassFlow = model.getFeedStream().getFlowRate("kg/hr");
    if (!Double.isFinite(feedMassFlow) || !(feedMassFlow > 0.0)) {
      throw new IllegalStateException("Feed mass flow must be finite and positive");
    }

    StreamInterface[] streams = { column.getGasOutStream(), column.getLiquidOutStream() };
    ProductResult[] productResults = new ProductResult[streams.length];
    double productMassFlow = 0.0;
    double previousMeanBoilingPoint = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < streams.length; i++) {
      double massFlow = streams[i].getFlowRate("kg/hr");
      if (!Double.isFinite(massFlow) || !(massFlow > MATERIAL_FLOW_FRACTION * feedMassFlow)) {
        throw new IllegalStateException("Both vacuum screening products must have material positive flow");
      }
      double meanBoilingPoint = meanNormalBoilingPoint(streams[i]);
      if (!(meanBoilingPoint > previousMeanBoilingPoint)) {
        throw new IllegalStateException("Products must become heavier from overhead to bottoms");
      }
      previousMeanBoilingPoint = meanBoilingPoint;
      productMassFlow += massFlow;
      productResults[i] = new ProductResult(PRODUCT_LABELS[i], massFlow,
          massFlow / feedMassFlow, meanBoilingPoint);
    }

    double closureError = Math.abs(feedMassFlow - productMassFlow) / feedMassFlow;
    requireFiniteBoundedError(closureError, "External mass-closure error", BALANCE_TOLERANCE);
    double maximumComponentError = maximumComponentMolarClosureRelativeError(
        model.getFeedStream(), streams);
    requireFiniteBoundedError(maximumComponentError,
        "Maximum component molar-closure error", BALANCE_TOLERANCE);

    int iterations = column.getLastIterationCount();
    double solveTime = column.getLastSolveTimeSeconds();
    if (iterations <= 0 || !Double.isFinite(solveTime) || solveTime < 0.0) {
      throw new IllegalStateException("Column iteration and solve-time diagnostics must be physical");
    }

    String diagnostics = column.getConvergenceDiagnostics();
    return new DoeBigHillVacuumFractionationResult(productResults, feedMassFlow,
        productMassFlow, closureError, maximumComponentError, massBalanceError,
        energyBalanceError, meshResidual, iterations, solveTime,
        diagnostics == null ? "" : diagnostics);
  }

  /** @return defensive copy of products in overhead-to-bottoms order */
  public ProductResult[] getProducts() {
    return products.clone();
  }

  /**
   * Return one product by its exact label.
   *
   * @param productLabel either {@code Overhead} or {@code Bottoms}
   * @return matching immutable result
   * @throws IllegalArgumentException if the label is null or unsupported
   */
  public ProductResult getProduct(String productLabel) {
    if (productLabel != null) {
      for (ProductResult product : products) {
        if (product.getProductLabel().equals(productLabel)) {
          return product;
        }
      }
    }
    throw new IllegalArgumentException("Unsupported Big Hill vacuum product label: " + productLabel);
  }

  /** @return evaluated feed mass flow in kg/h */
  public double getFeedMassFlowKgPerHour() {
    return feedMassFlowKgPerHour;
  }

  /** @return sum of evaluated product mass flows in kg/h */
  public double getProductMassFlowKgPerHour() {
    return productMassFlowKgPerHour;
  }

  /** @return absolute feed/product mass-closure error divided by feed mass flow */
  public double getMassClosureRelativeError() {
    return massClosureRelativeError;
  }

  /** @return largest per-component molar-flow closure error divided by feed component flow */
  public double getMaximumComponentMolarClosureRelativeError() {
    return maximumComponentMolarClosureRelativeError;
  }

  /** @return column-reported relative mass-balance error */
  public double getColumnMassBalanceError() {
    return columnMassBalanceError;
  }

  /** @return column-reported relative energy-balance error */
  public double getColumnEnergyBalanceError() {
    return columnEnergyBalanceError;
  }

  /** @return final MESH residual norm */
  public double getMeshResidualNorm() {
    return meshResidualNorm;
  }

  /** @return iterations used by the qualified solve */
  public int getIterationCount() {
    return iterationCount;
  }

  /** @return reported solve time in seconds */
  public double getSolveTimeSeconds() {
    return solveTimeSeconds;
  }

  /** @return convergence diagnostics captured after the solve */
  public String getConvergenceDiagnostics() {
    return convergenceDiagnostics;
  }

  private static double meanNormalBoilingPoint(StreamInterface stream) {
    double[] composition = stream.getThermoSystem().getMolarComposition();
    double[] boilingPoints = stream.getThermoSystem().getNormalBoilingPointTemperatures();
    if (composition.length != boilingPoints.length || composition.length == 0) {
      throw new IllegalStateException("Product composition and boiling-point arrays must align");
    }

    double mean = 0.0;
    double compositionSum = 0.0;
    for (int i = 0; i < composition.length; i++) {
      if (!Double.isFinite(composition[i]) || composition[i] < 0.0
          || !Double.isFinite(boilingPoints[i]) || !(boilingPoints[i] > 0.0)) {
        throw new IllegalStateException("Product composition and boiling points must be physical");
      }
      mean += composition[i] * boilingPoints[i];
      compositionSum += composition[i];
    }
    if (!Double.isFinite(compositionSum) || !(compositionSum > 0.0) || !Double.isFinite(mean)) {
      throw new IllegalStateException("Product mean boiling point is undefined");
    }
    return mean / compositionSum;
  }

  private static double maximumComponentMolarClosureRelativeError(StreamInterface feed,
      StreamInterface[] products) {
    double[] feedComposition = feed.getThermoSystem().getMolarComposition();
    double feedMolarFlow = feed.getFlowRate("mol/hr");
    double maximumError = 0.0;
    for (int componentIndex = 0; componentIndex < feedComposition.length; componentIndex++) {
      double feedComponentFlow = feedMolarFlow * feedComposition[componentIndex];
      if (!Double.isFinite(feedComponentFlow) || !(feedComponentFlow > 0.0)) {
        throw new IllegalStateException("Feed component molar flows must be finite and positive");
      }
      double productComponentFlow = 0.0;
      for (StreamInterface product : products) {
        double[] productComposition = product.getThermoSystem().getMolarComposition();
        if (productComposition.length != feedComposition.length) {
          throw new IllegalStateException("Product composition must align with the feed");
        }
        productComponentFlow += product.getFlowRate("mol/hr")
            * productComposition[componentIndex];
      }
      double relativeError = Math.abs(feedComponentFlow - productComponentFlow)
          / feedComponentFlow;
      if (!Double.isFinite(relativeError)) {
        throw new IllegalStateException("Component molar-closure error must be finite");
      }
      maximumError = Math.max(maximumError, relativeError);
    }
    return maximumError;
  }

  private static void requireFiniteBoundedError(double value, String label,
      double tolerance) {
    if (!Double.isFinite(value) || value < 0.0 || !Double.isFinite(tolerance)
        || tolerance < 0.0 || value > tolerance) {
      throw new IllegalStateException(label + " exceeds the qualified tolerance");
    }
  }

  /** Immutable calculated vacuum-screening product row. */
  public static final class ProductResult {
    private final String productLabel;
    private final double massFlowKgPerHour;
    private final double massFractionOfFeed;
    private final double meanNormalBoilingPointKelvin;

    private ProductResult(String productLabel, double massFlowKgPerHour,
        double massFractionOfFeed, double meanNormalBoilingPointKelvin) {
      this.productLabel = productLabel;
      this.massFlowKgPerHour = massFlowKgPerHour;
      this.massFractionOfFeed = massFractionOfFeed;
      this.meanNormalBoilingPointKelvin = meanNormalBoilingPointKelvin;
    }

    /** @return product label */
    public String getProductLabel() {
      return productLabel;
    }

    /** @return product mass flow in kg/h */
    public double getMassFlowKgPerHour() {
      return massFlowKgPerHour;
    }

    /** @return product mass flow divided by feed mass flow */
    public double getMassFractionOfFeed() {
      return massFractionOfFeed;
    }

    /** @return mole-weighted mean normal boiling point in kelvin */
    public double getMeanNormalBoilingPointKelvin() {
      return meanNormalBoilingPointKelvin;
    }

    /** @return mole-weighted mean normal boiling point in degrees Celsius */
    public double getMeanNormalBoilingPointCelsius() {
      return meanNormalBoilingPointKelvin - 273.15;
    }
  }
}
