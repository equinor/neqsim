package neqsim.process.fielddevelopment.concept;

import java.io.Serializable;

/**
 * Probabilistic assumption bundle for a development-case template.
 *
 * <p>
 * The bundle keeps the most common screening uncertainties directly attached to a concept template:
 * resource, CAPEX, schedule, commodity price, and production-factor ranges. These values can be
 * used by notebooks, reports, or a sensitivity engine without recreating assumptions outside the
 * model.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class DevelopmentCaseUncertainty implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final UncertaintyRange resource;
  private final UncertaintyRange capex;
  private final UncertaintyRange scheduleMonths;
  private final UncertaintyRange price;
  private final UncertaintyRange productionFactor;

  /**
   * Creates an uncertainty bundle.
   *
   * @param resource resource range
   * @param capex CAPEX range
   * @param scheduleMonths schedule range in months
   * @param price price range
   * @param productionFactor production-factor range
   */
  private DevelopmentCaseUncertainty(UncertaintyRange resource, UncertaintyRange capex,
      UncertaintyRange scheduleMonths, UncertaintyRange price, UncertaintyRange productionFactor) {
    this.resource = resource;
    this.capex = capex;
    this.scheduleMonths = scheduleMonths;
    this.price = price;
    this.productionFactor = productionFactor;
  }

  /**
   * Creates an empty deterministic uncertainty bundle.
   *
   * @return uncertainty bundle with all values set to zero or one as appropriate
   */
  public static DevelopmentCaseUncertainty empty() {
    return builder().resource(UncertaintyRange.deterministic("Resource", "-", 0.0))
        .capex(UncertaintyRange.deterministic("CAPEX", "MUSD", 0.0))
        .scheduleMonths(UncertaintyRange.deterministic("Schedule", "months", 0.0))
        .price(UncertaintyRange.deterministic("Price", "USD/unit", 0.0))
        .productionFactor(UncertaintyRange.deterministic("Production factor", "-", 1.0)).build();
  }

  /**
   * Creates a builder.
   *
   * @return builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Gets the resource uncertainty.
   *
   * @return resource uncertainty range
   */
  public UncertaintyRange getResource() {
    return resource;
  }

  /**
   * Gets the CAPEX uncertainty.
   *
   * @return CAPEX uncertainty range
   */
  public UncertaintyRange getCapex() {
    return capex;
  }

  /**
   * Gets the schedule uncertainty.
   *
   * @return schedule uncertainty range in months
   */
  public UncertaintyRange getScheduleMonths() {
    return scheduleMonths;
  }

  /**
   * Gets the commodity price uncertainty.
   *
   * @return commodity price uncertainty range
   */
  public UncertaintyRange getPrice() {
    return price;
  }

  /**
   * Gets the production-factor uncertainty.
   *
   * @return production-factor uncertainty range
   */
  public UncertaintyRange getProductionFactor() {
    return productionFactor;
  }

  /**
   * Builds a compact text summary.
   *
   * @return uncertainty summary text
   */
  public String getSummary() {
    return resource + "; " + capex + "; " + scheduleMonths + "; " + price + "; " + productionFactor;
  }

  /**
   * Builder for uncertainty bundles.
   */
  public static final class Builder {
    private UncertaintyRange resource = UncertaintyRange.deterministic("Resource", "-", 0.0);
    private UncertaintyRange capex = UncertaintyRange.deterministic("CAPEX", "MUSD", 0.0);
    private UncertaintyRange scheduleMonths =
        UncertaintyRange.deterministic("Schedule", "months", 0.0);
    private UncertaintyRange price = UncertaintyRange.deterministic("Price", "USD/unit", 0.0);
    private UncertaintyRange productionFactor =
        UncertaintyRange.deterministic("Production factor", "-", 1.0);

    /**
     * Sets resource uncertainty.
     *
     * @param resource resource uncertainty range
     * @return this builder
     */
    public Builder resource(UncertaintyRange resource) {
      this.resource = resource == null ? this.resource : resource;
      return this;
    }

    /**
     * Sets CAPEX uncertainty.
     *
     * @param capex CAPEX uncertainty range
     * @return this builder
     */
    public Builder capex(UncertaintyRange capex) {
      this.capex = capex == null ? this.capex : capex;
      return this;
    }

    /**
     * Sets schedule uncertainty.
     *
     * @param scheduleMonths schedule uncertainty range in months
     * @return this builder
     */
    public Builder scheduleMonths(UncertaintyRange scheduleMonths) {
      this.scheduleMonths = scheduleMonths == null ? this.scheduleMonths : scheduleMonths;
      return this;
    }

    /**
     * Sets commodity price uncertainty.
     *
     * @param price price uncertainty range
     * @return this builder
     */
    public Builder price(UncertaintyRange price) {
      this.price = price == null ? this.price : price;
      return this;
    }

    /**
     * Sets production-factor uncertainty.
     *
     * @param productionFactor production-factor uncertainty range
     * @return this builder
     */
    public Builder productionFactor(UncertaintyRange productionFactor) {
      this.productionFactor = productionFactor == null ? this.productionFactor : productionFactor;
      return this;
    }

    /**
     * Builds an immutable uncertainty bundle.
     *
     * @return uncertainty bundle
     */
    public DevelopmentCaseUncertainty build() {
      return new DevelopmentCaseUncertainty(resource, capex, scheduleMonths, price,
          productionFactor);
    }
  }
}
