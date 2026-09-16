package neqsim.process.equipment.distillation;

import java.util.Objects;
import neqsim.process.equipment.distillation.SarirAtmosphericFractionationCase.OperatingInputs;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.standards.oilquality.SarirD86ProductComparison;
import neqsim.standards.oilquality.Standard_ASTM_D86;
import neqsim.thermo.system.SystemInterface;

/**
 * Source-bounded strict T95 screen for material Sarir atmospheric kerosene and diesel side draws.
 *
 * <p>
 * The screen first delegates convergence, solver, conservation, and material-product qualification
 * to {@link SarirAtmosphericFractionationResult}. It then calculates ASTM D86 curves from the two
 * live liquid side-draw fluids and delegates each strict 95 liquid-volume-percent comparison to
 * {@link SarirD86ProductComparison}. Published laboratory, HYSYS, and specification values remain
 * read-only evidence and are never solver controls or tuning targets.
 * </p>
 *
 * <p>
 * Total naphtha is excluded because the atmospheric case does not resolve the published light/heavy
 * naphtha split. Residual is excluded because its published specification is the nonnumeric,
 * open-ended {@code 550+} boundary.
 * </p>
 */
public final class SarirAtmosphericProductQualityScreen {
  private static final String[] PRODUCT_LABELS = { "Kerosene", "Diesel" };

  private SarirAtmosphericProductQualityScreen() {\n  }

  /**
   * Evaluate strict T95 evidence for a rigorously solved Sarir atmospheric case.
   *
   * @param model solved source-bounded Sarir atmospheric case
   * @return immutable fractionation and product-quality evidence
   * @throws NullPointerException if {@code model} is {@code null}
   * @throws IllegalStateException if the case is not qualified, either side draw is non-material,
   *         or a finite strict T95 cannot be calculated
   */
  public static Result evaluate(SarirAtmosphericFractionationCase model) {
    Objects.requireNonNull(model, "model");
    SarirAtmosphericFractionationResult fractionationResult =
        SarirAtmosphericFractionationResult.evaluate(model);

    OperatingInputs inputs = model.getOperatingInputs();
    DistillationColumn column = model.getColumn();
    StreamInterface[] productStreams = {
        column.getSideDrawStream(
            inputs.getKeroseneSideDrawTray(), DistillationColumn.SideDrawPhase.LIQUID),
        column.getSideDrawStream(
            inputs.getDieselSideDrawTray(), DistillationColumn.SideDrawPhase.LIQUID)
    };

    SarirD86ProductComparison.Result[] comparisons =
        new SarirD86ProductComparison.Result[PRODUCT_LABELS.length];
    for (int i = 0; i < PRODUCT_LABELS.length; i++) {
      double productMassFlow =
          fractionationResult.getProduct(PRODUCT_LABELS[i]).getCalculatedMassFlowKgPerHour();
      if (!Double.isFinite(productMassFlow) || !(productMassFlow > 0.0)) {
        throw new IllegalStateException(
            "Sarir product-quality screening requires a material " + PRODUCT_LABELS[i] + " draw");
      }

      SystemInterface productFluid = productStreams[i].getThermoSystem();
      if (productFluid == null) {
        throw new IllegalStateException(
            "Sarir product-quality screening requires a thermodynamic product fluid");
      }
      Standard_ASTM_D86 standard = new Standard_ASTM_D86(productFluid.clone());
      standard.calculate();
      comparisons[i] = SarirD86ProductComparison.compareT95(standard, PRODUCT_LABELS[i]);
    }

    return new Result(fractionationResult, comparisons);
  }

  /** Immutable calculated fractionation and strict T95 evidence. */
  public static final class Result {
    private final SarirAtmosphericFractionationResult fractionationResult;
    private final SarirD86ProductComparison.Result[] comparisons;

    private Result(
        SarirAtmosphericFractionationResult fractionationResult,
        SarirD86ProductComparison.Result[] comparisons) {
      this.fractionationResult = fractionationResult;
      this.comparisons = comparisons.clone();
    }

    /** @return the immutable qualified fractionation result used by this screen */
    public SarirAtmosphericFractionationResult getFractionationResult() {
      return fractionationResult;
    }

    /** @return defensive copy in kerosene, diesel order */
    public SarirD86ProductComparison.Result[] getComparisons() {
      return comparisons.clone();
    }

    /**
     * Return strict T95 evidence by exact source-table product label.
     *
     * @param productLabel {@code Kerosene} or {@code Diesel}
     * @return matching immutable comparison
     * @throws IllegalArgumentException if the label is null or unsupported
     */
    public SarirD86ProductComparison.Result getComparison(String productLabel) {
      if (productLabel != null) {
        for (SarirD86ProductComparison.Result comparison : comparisons) {
          if (comparison.getProductName().equals(productLabel)) {
            return comparison;
          }
        }
      }
      throw new IllegalArgumentException(
          "Unsupported Sarir product-quality label: " + productLabel);
    }
  }
}
