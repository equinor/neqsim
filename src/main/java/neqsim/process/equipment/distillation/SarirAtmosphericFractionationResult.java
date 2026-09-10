package neqsim.process.equipment.distillation;

import java.util.Objects;
import neqsim.process.equipment.distillation.SarirAtmosphericFractionationCase.OperatingInputs;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.SarirAtmosphericReference;
import neqsim.thermo.characterization.SarirAtmosphericReference.ProductYieldReference;

/**
 * Immutable engineering summary for a solved {@link SarirAtmosphericFractionationCase}.
 *
 * <p>
 * Published plant rates are retained as read-only comparison evidence. They are not solver controls, tuning targets, or
 * acceptance thresholds.
 * </p>
 */
public final class SarirAtmosphericFractionationResult {
  private static final String[] PRODUCT_LABELS = { "Total Naphtha", "Kerosene", "Diesel", "Residual" };
  private static final double MATERIAL_FLOW_FRACTION = 1.0e-8;
  private static final double BALANCE_TOLERANCE = 5.0e-2;

  private final ProductResult[] products;
  private final double feedMassFlowKgPerHour;
  private final double productMassFlowKgPerHour;
  private final double massClosureRelativeError;
  private final double columnMassBalanceError;
  private final double columnEnergyBalanceError;
  private final int iterationCount;
  private final String convergenceDiagnostics;

  private SarirAtmosphericFractionationResult(ProductResult[] products, double feedMassFlowKgPerHour,
      double productMassFlowKgPerHour, double massClosureRelativeError, double columnMassBalanceError,
      double columnEnergyBalanceError, int iterationCount, String convergenceDiagnostics) {
    this.products = products.clone();
    this.feedMassFlowKgPerHour = feedMassFlowKgPerHour;
    this.productMassFlowKgPerHour = productMassFlowKgPerHour;
    this.massClosureRelativeError = massClosureRelativeError;
    this.columnMassBalanceError = columnMassBalanceError;
    this.columnEnergyBalanceError = columnEnergyBalanceError;
    this.iterationCount = iterationCount;
    this.convergenceDiagnostics = convergenceDiagnostics;
  }

  /**
   * Evaluate an already solved Sarir atmospheric-fractionation case.
   *
   * @param model solved case to evaluate
   * @return immutable calculated-product and convergence summary
   * @throws NullPointerException if {@code model} is {@code null}
   * @throws IllegalStateException if the case is unsolved, used fallback products, is non-conservative, or contains
   * invalid or unordered material-product results
   */
  public static SarirAtmosphericFractionationResult evaluate(SarirAtmosphericFractionationCase model) {
    Objects.requireNonNull(model, "model");
    DistillationColumn column = model.getColumn();
    if (!column.solved()) {
      throw new IllegalStateException("Sarir atmospheric column must be solved before evaluation");
    }
    if (column.getLastSolverTypeUsed() != DistillationColumn.SolverType.MESH_RESIDUAL) {
      throw new IllegalStateException("Sarir atmospheric column must use the MESH-residual solver");
    }
    if (column.getLastSolveStatus() == DistillationColumn.SolveStatus.FALLBACK_PRODUCTS
        || column.getLastSolveStatus() == DistillationColumn.SolveStatus.FAILED) {
      throw new IllegalStateException("Fallback or failed column results cannot be evaluated");
    }

    double massBalanceError = column.getMassBalanceError();
    double energyBalanceError = column.getEnergyBalanceError();
    requireFiniteBoundedError(massBalanceError, "Column mass-balance error");
    requireFiniteBoundedError(energyBalanceError, "Column energy-balance error");

    double feedMassFlow = model.getFeedStream().getFlowRate("kg/hr");
    if (!Double.isFinite(feedMassFlow) || !(feedMassFlow > 0.0)) {
      throw new IllegalStateException("Feed mass flow must be finite and positive");
    }

    OperatingInputs inputs = model.getOperatingInputs();
    StreamInterface[] streams = { column.getGasOutStream(),
        column.getSideDrawStream(inputs.getKeroseneSideDrawTray(), DistillationColumn.SideDrawPhase.LIQUID),
        column.getSideDrawStream(inputs.getDieselSideDrawTray(), DistillationColumn.SideDrawPhase.LIQUID),
        column.getLiquidOutStream() };

    ProductResult[] productResults = new ProductResult[streams.length];
    double productMassFlow = 0.0;
    double previousMeanBoilingPoint = Double.NEGATIVE_INFINITY;
    int materialProductCount = 0;
    for (int i = 0; i < streams.length; i++) {
      double massFlow = streams[i].getFlowRate("kg/hr");
      if (!Double.isFinite(massFlow) || massFlow < 0.0) {
        throw new IllegalStateException("Product mass flow must be finite and non-negative");
      }
      productMassFlow += massFlow;

      double meanBoilingPoint = Double.NaN;
      ProductBoilingPointDistribution boilingPointDistribution = null;
      if (massFlow > MATERIAL_FLOW_FRACTION * feedMassFlow) {
        boilingPointDistribution = ProductBoilingPointDistribution.from(streams[i]);
        meanBoilingPoint = boilingPointDistribution.getMeanNormalBoilingPointKelvin();
        if (!(meanBoilingPoint > previousMeanBoilingPoint)) {
          throw new IllegalStateException("Material products must become heavier from the column top to the bottoms");
        }
        previousMeanBoilingPoint = meanBoilingPoint;
        materialProductCount++;
      }

      ProductYieldReference reference = SarirAtmosphericReference.getProductYield(PRODUCT_LABELS[i]);
      productResults[i] = new ProductResult(reference.getName(), massFlow, massFlow / feedMassFlow, meanBoilingPoint,
          boilingPointDistribution, reference.getPlantMassFlowRateKgPerHour(),
          reference.calculateAbsoluteRelativeErrorPercentForMassFlowKgPerHour(massFlow));
    }
    if (materialProductCount < 2) {
      throw new IllegalStateException("At least two ordered material products are required");
    }

    double closureError = Math.abs(feedMassFlow - productMassFlow) / feedMassFlow;
    if (!Double.isFinite(closureError) || closureError > BALANCE_TOLERANCE) {
      throw new IllegalStateException("Calculated product mass flow does not close against feed");
    }

    String diagnostics = column.getConvergenceDiagnostics();
    return new SarirAtmosphericFractionationResult(productResults, feedMassFlow, productMassFlow, closureError,
        massBalanceError, energyBalanceError, column.getLastIterationCount(), diagnostics == null ? "" : diagnostics);
  }

  /** @return defensive copy of calculated product rows in top-to-bottom order */
  public ProductResult[] getProducts() {
    return products.clone();
  }

  /**
   * Return one calculated product row by its exact source-table label.
   *
   * @param productLabel exact label
   * @return matching immutable row
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
    throw new IllegalArgumentException("Unsupported Sarir product label: " + productLabel);
  }

  /** @return evaluated feed mass flow in kg/h */
  public double getFeedMassFlowKgPerHour() {
    return feedMassFlowKgPerHour;
  }

  /** @return sum of evaluated product mass flows in kg/h */
  public double getProductMassFlowKgPerHour() {
    return productMassFlowKgPerHour;
  }

  /** @return absolute feed/product mass-closure error divided by feed flow */
  public double getMassClosureRelativeError() {
    return massClosureRelativeError;
  }

  /** @return column-reported relative mass-balance error */
  public double getColumnMassBalanceError() {
    return columnMassBalanceError;
  }

  /** @return column-reported relative energy-balance error */
  public double getColumnEnergyBalanceError() {
    return columnEnergyBalanceError;
  }

  /** @return iterations used by the qualified column solve */
  public int getIterationCount() {
    return iterationCount;
  }

  /** @return convergence diagnostics captured after the solve */
  public String getConvergenceDiagnostics() {
    return convergenceDiagnostics;
  }

  private static void requireFiniteBoundedError(double value, String label) {
    if (!Double.isFinite(value) || value < 0.0 || value > BALANCE_TOLERANCE) {
      throw new IllegalStateException(label + " exceeds the qualified tolerance");
    }
  }

  /** Immutable calculated product row with read-only plant comparison evidence. */
  public static final class ProductResult {
    private final String productLabel;
    private final double calculatedMassFlowKgPerHour;
    private final double calculatedMassFractionOfFeed;
    private final double meanNormalBoilingPointKelvin;
    private final ProductBoilingPointDistribution boilingPointDistribution;
    private final double plantMassFlowKgPerHour;
    private final double absoluteRelativeErrorPercentAgainstPlant;

    private ProductResult(String productLabel, double calculatedMassFlowKgPerHour, double calculatedMassFractionOfFeed,
        double meanNormalBoilingPointKelvin, ProductBoilingPointDistribution boilingPointDistribution,
        double plantMassFlowKgPerHour, double absoluteRelativeErrorPercentAgainstPlant) {
      this.productLabel = productLabel;
      this.calculatedMassFlowKgPerHour = calculatedMassFlowKgPerHour;
      this.calculatedMassFractionOfFeed = calculatedMassFractionOfFeed;
      this.meanNormalBoilingPointKelvin = meanNormalBoilingPointKelvin;
      this.boilingPointDistribution = boilingPointDistribution;
      this.plantMassFlowKgPerHour = plantMassFlowKgPerHour;
      this.absoluteRelativeErrorPercentAgainstPlant = absoluteRelativeErrorPercentAgainstPlant;
    }

    /** @return exact source-table product label */
    public String getProductLabel() {
      return productLabel;
    }

    /** @return calculated product mass flow in kg/h */
    public double getCalculatedMassFlowKgPerHour() {
      return calculatedMassFlowKgPerHour;
    }

    /** @return calculated product mass flow divided by evaluated feed mass flow */
    public double getCalculatedMassFractionOfFeed() {
      return calculatedMassFractionOfFeed;
    }

    /**
     * Return the calculated mole-weighted mean normal boiling point.
     *
     * @return mean normal boiling point in kelvin, or NaN for a non-material product
     */
    public double getMeanNormalBoilingPointKelvin() {
      return meanNormalBoilingPointKelvin;
    }

    /**
     * Return the calculated mole-weighted mean normal boiling point.
     *
     * @return mean normal boiling point in degrees Celsius, or NaN for a non-material product
     */
    public double getMeanNormalBoilingPointCelsius() {
      return Double.isFinite(meanNormalBoilingPointKelvin) ? meanNormalBoilingPointKelvin - 273.15 : Double.NaN;
    }

    /**
     * Report whether this material product has a discrete boiling-point distribution.
     *
     * @return true for a material product; false for a non-material candidate row
     */
    public boolean hasBoilingPointDistribution() {
      return boilingPointDistribution != null;
    }

    /**
     * Return the ascending positive-component normal-boiling-point support.
     *
     * @return defensive copy of temperatures in kelvin
     * @throws IllegalStateException if this is a non-material product
     */
    public double[] getBoilingPointTemperaturesKelvin() {
      return requireBoilingPointDistribution().getBoilingPointTemperaturesKelvin();
    }

    /**
     * Return the normalized cumulative product mole fractions.
     *
     * @return defensive copy of cumulative mole fractions
     * @throws IllegalStateException if this is a non-material product
     */
    public double[] getCumulativeMoleFractions() {
      return requireBoilingPointDistribution().getCumulativeMoleFractions();
    }

    /**
     * Return a discrete pseudo-component quantile on cumulative product mole basis.
     *
     * @param cumulativeMoleFraction requested cumulative product mole fraction in (0, 1]
     * @return normal boiling point in kelvin
     * @throws IllegalArgumentException if the request is non-finite or outside (0, 1]
     * @throws IllegalStateException if this is a non-material product
     */
    public double getNormalBoilingPointQuantileKelvin(double cumulativeMoleFraction) {
      return requireBoilingPointDistribution().getNormalBoilingPointQuantileKelvin(cumulativeMoleFraction);
    }

    /**
     * Return a discrete pseudo-component quantile on cumulative product mole basis.
     *
     * @param cumulativeMoleFraction requested cumulative product mole fraction in (0, 1]
     * @return normal boiling point in degrees Celsius
     * @throws IllegalArgumentException if the request is non-finite or outside (0, 1]
     * @throws IllegalStateException if this is a non-material product
     */
    public double getNormalBoilingPointQuantileCelsius(double cumulativeMoleFraction) {
      return requireBoilingPointDistribution().getNormalBoilingPointQuantileCelsius(cumulativeMoleFraction);
    }

    private ProductBoilingPointDistribution requireBoilingPointDistribution() {
      if (boilingPointDistribution == null) {
        throw new IllegalStateException("Boiling-point distribution is unavailable for a non-material product");
      }
      return boilingPointDistribution;
    }

    /** @return measured plant product rate in kg/h */
    public double getPlantMassFlowKgPerHour() {
      return plantMassFlowKgPerHour;
    }

    /**
     * Return the absolute calculated-rate difference from the plant rate.
     *
     * @return absolute relative error in percent; this is evidence, not an acceptance threshold
     */
    public double getAbsoluteRelativeErrorPercentAgainstPlant() {
      return absoluteRelativeErrorPercentAgainstPlant;
    }
  }
}
