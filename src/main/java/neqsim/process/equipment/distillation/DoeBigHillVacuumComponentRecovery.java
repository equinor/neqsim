package neqsim.process.equipment.distillation;

import java.util.Objects;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Immutable pseudo-component recovery summary for a solved DOE Big Hill vacuum screening case.
 *
 * <p>
 * Recoveries are calculated on a molar basis from the existing qualified feed, overhead, and bottoms streams. This
 * class is numerical partition bookkeeping for the synthetic screening case; it is not measured cut-recovery or ASTM
 * distillation evidence.
 * </p>
 */
public final class DoeBigHillVacuumComponentRecovery {
  private static final double RECOVERY_CLOSURE_TOLERANCE = 5.0e-2;
  private static final String[] PRODUCT_LABELS = { "Overhead", "Bottoms" };

  private final String[] componentNames;
  private final double[] feedComponentMolarFlowsMolPerHour;
  private final ProductRecovery[] products;
  private final double maximumComponentRecoveryClosureError;

  private DoeBigHillVacuumComponentRecovery(String[] componentNames, double[] feedComponentMolarFlowsMolPerHour,
      ProductRecovery[] products, double maximumComponentRecoveryClosureError) {
    this.componentNames = componentNames.clone();
    this.feedComponentMolarFlowsMolPerHour = feedComponentMolarFlowsMolPerHour.clone();
    this.products = products.clone();
    this.maximumComponentRecoveryClosureError = maximumComponentRecoveryClosureError;
  }

  /**
   * Evaluate component recoveries for an already solved Big Hill vacuum screening case.
   *
   * @param model solved screening case
   * @return immutable component-recovery summary
   * @throws NullPointerException if {@code model} is {@code null}
   * @throws IllegalStateException if the existing fractionation-result gates or component-recovery closure fail
   */
  public static DoeBigHillVacuumComponentRecovery evaluate(DoeBigHillVacuumFractionationCase model) {
    Objects.requireNonNull(model, "model");
    DoeBigHillVacuumFractionationResult fractionationResult = DoeBigHillVacuumFractionationResult.evaluate(model);

    StreamInterface feed = model.getFeedStream();
    double[] feedComposition = feed.getThermoSystem().getMolarComposition();
    double feedMolarFlow = feed.getFlowRate("mol/hr");
    String[] names = new String[feedComposition.length];
    double[] feedComponentFlows = new double[feedComposition.length];
    for (int componentIndex = 0; componentIndex < feedComposition.length; componentIndex++) {
      names[componentIndex] = feed.getThermoSystem().getComponent(componentIndex).getComponentName();
      feedComponentFlows[componentIndex] = feedMolarFlow * feedComposition[componentIndex];
      requireFinitePositive(feedComponentFlows[componentIndex], "Feed component molar flow");
    }

    StreamInterface[] productStreams = { model.getColumn().getGasOutStream(), model.getColumn().getLiquidOutStream() };
    DoeBigHillVacuumFractionationResult.ProductResult[] qualifiedProducts = fractionationResult.getProducts();
    ProductRecovery[] recoveries = new ProductRecovery[productStreams.length];
    double[] recoverySums = new double[feedComposition.length];
    for (int productIndex = 0; productIndex < productStreams.length; productIndex++) {
      StreamInterface productStream = productStreams[productIndex];
      double[] productComposition = productStream.getThermoSystem().getMolarComposition();
      if (productComposition.length != feedComposition.length) {
        throw new IllegalStateException("Product composition must align with the feed");
      }

      double productMolarFlow = productStream.getFlowRate("mol/hr");
      requireFinitePositive(productMolarFlow, "Product molar flow");
      double[] componentFlows = new double[feedComposition.length];
      double[] componentRecoveries = new double[feedComposition.length];
      for (int componentIndex = 0; componentIndex < feedComposition.length; componentIndex++) {
        componentFlows[componentIndex] = productMolarFlow * productComposition[componentIndex];
        requireFiniteNonNegative(componentFlows[componentIndex], "Product component molar flow");
        componentRecoveries[componentIndex] = componentFlows[componentIndex] / feedComponentFlows[componentIndex];
        requireFiniteNonNegative(componentRecoveries[componentIndex], "Product component recovery");
        if (componentRecoveries[componentIndex] > 1.0 + RECOVERY_CLOSURE_TOLERANCE) {
          throw new IllegalStateException("Product component recovery exceeds the qualified screening bound");
        }
        recoverySums[componentIndex] += componentRecoveries[componentIndex];
      }

      String productLabel = qualifiedProducts[productIndex].getProductLabel();
      if (!PRODUCT_LABELS[productIndex].equals(productLabel)) {
        throw new IllegalStateException("Qualified product order is inconsistent");
      }
      recoveries[productIndex] = new ProductRecovery(productLabel, names, productMolarFlow, componentFlows,
          componentRecoveries);
    }

    double maximumClosureError = 0.0;
    for (double recoverySum : recoverySums) {
      double closureError = Math.abs(1.0 - recoverySum);
      if (!Double.isFinite(closureError) || closureError > RECOVERY_CLOSURE_TOLERANCE) {
        throw new IllegalStateException("Component recovery closure exceeds the qualified tolerance");
      }
      maximumClosureError = Math.max(maximumClosureError, closureError);
    }

    return new DoeBigHillVacuumComponentRecovery(names, feedComponentFlows, recoveries, maximumClosureError);
  }

  /** @return defensive copy of feed component names in thermodynamic-system order */
  public String[] getComponentNames() {
    return componentNames.clone();
  }

  /** @return defensive copy of positive feed component molar flows in mol/h */
  public double[] getFeedComponentMolarFlowsMolPerHour() {
    return feedComponentMolarFlowsMolPerHour.clone();
  }

  /** @return defensive copy of products in overhead-to-bottoms order */
  public ProductRecovery[] getProducts() {
    return products.clone();
  }

  /**
   * Return one product by exact label.
   *
   * @param productLabel either {@code Overhead} or {@code Bottoms}
   * @return matching immutable product recovery
   * @throws IllegalArgumentException if the label is null or unsupported
   */
  public ProductRecovery getProduct(String productLabel) {
    if (productLabel != null) {
      for (ProductRecovery product : products) {
        if (product.getProductLabel().equals(productLabel)) {
          return product;
        }
      }
    }
    throw new IllegalArgumentException("Unsupported Big Hill vacuum product label: " + productLabel);
  }

  /** @return largest absolute deviation of summed product recovery from unity */
  public double getMaximumComponentRecoveryClosureError() {
    return maximumComponentRecoveryClosureError;
  }

  private static void requireFinitePositive(double value, String label) {
    if (!Double.isFinite(value) || !(value > 0.0)) {
      throw new IllegalStateException(label + " must be finite and positive");
    }
  }

  private static void requireFiniteNonNegative(double value, String label) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalStateException(label + " must be finite and non-negative");
    }
  }

  /** Immutable molar component-recovery row for one qualified product. */
  public static final class ProductRecovery {
    private final String productLabel;
    private final String[] componentNames;
    private final double productMolarFlowMolPerHour;
    private final double[] componentMolarFlowsMolPerHour;
    private final double[] componentMolarRecoveries;

    private ProductRecovery(String productLabel, String[] componentNames, double productMolarFlowMolPerHour,
        double[] componentMolarFlowsMolPerHour, double[] componentMolarRecoveries) {
      this.productLabel = productLabel;
      this.componentNames = componentNames.clone();
      this.productMolarFlowMolPerHour = productMolarFlowMolPerHour;
      this.componentMolarFlowsMolPerHour = componentMolarFlowsMolPerHour.clone();
      this.componentMolarRecoveries = componentMolarRecoveries.clone();
    }

    /** @return exact qualified product label */
    public String getProductLabel() {
      return productLabel;
    }

    /** @return total product molar flow in mol/h */
    public double getProductMolarFlowMolPerHour() {
      return productMolarFlowMolPerHour;
    }

    /** @return defensive copy of component molar flows in mol/h */
    public double[] getComponentMolarFlowsMolPerHour() {
      return componentMolarFlowsMolPerHour.clone();
    }

    /** @return defensive copy of dimensionless component molar recoveries */
    public double[] getComponentMolarRecoveries() {
      return componentMolarRecoveries.clone();
    }

    /**
     * Return one component molar flow by exact component name.
     *
     * @param componentName exact thermodynamic-system component name
     * @return product component molar flow in mol/h
     */
    public double getComponentMolarFlowMolPerHour(String componentName) {
      return componentMolarFlowsMolPerHour[componentIndex(componentName)];
    }

    /**
     * Return one dimensionless component molar recovery by exact component name.
     *
     * @param componentName exact thermodynamic-system component name
     * @return product component molar flow divided by feed component molar flow
     */
    public double getComponentMolarRecovery(String componentName) {
      return componentMolarRecoveries[componentIndex(componentName)];
    }

    private int componentIndex(String componentName) {
      if (componentName != null) {
        for (int i = 0; i < componentNames.length; i++) {
          if (componentNames[i].equals(componentName)) {
            return i;
          }
        }
      }
      throw new IllegalArgumentException("Unsupported Big Hill vacuum component name: " + componentName);
    }
  }
}
