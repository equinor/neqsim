package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;

/** Export-product and produced-water specifications applied to each lifecycle process solve. */
public final class FieldProductSpecifications implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Consequence used by area-development ranking when a specification is violated. */
  public enum ViolationAction {
    /** Retain the option and report every off-spec period. */
    REPORT_ONLY,
    /** Mark the option ineligible for area-development recommendation. */
    REJECT_OPTION
  }

  private final double maximumGasCo2MolePercent;
  private final double maximumGasH2sPpm;
  private final double maximumGasOxygenMolePercent;
  private final double gasDewPointReferencePressureBara;
  private final double maximumGasWaterDewPointC;
  private final double maximumGasHydrocarbonDewPointC;
  private final double minimumGasGrossCalorificValueMjPerSm3;
  private final double maximumGasGrossCalorificValueMjPerSm3;
  private final double minimumGasWobbeIndexMjPerSm3;
  private final double maximumGasWobbeIndexMjPerSm3;
  private final double maximumGasRelativeDensity;
  private final double maximumOilRvpBara;
  private final double maximumOilBswVolumePercent;
  private final double maximumOilInWaterMgPerL;
  private final ViolationAction violationAction;

  private FieldProductSpecifications(Builder builder) {
    maximumGasCo2MolePercent = builder.maximumGasCo2MolePercent;
    maximumGasH2sPpm = builder.maximumGasH2sPpm;
    maximumGasOxygenMolePercent = builder.maximumGasOxygenMolePercent;
    gasDewPointReferencePressureBara = builder.gasDewPointReferencePressureBara;
    maximumGasWaterDewPointC = builder.maximumGasWaterDewPointC;
    maximumGasHydrocarbonDewPointC = builder.maximumGasHydrocarbonDewPointC;
    minimumGasGrossCalorificValueMjPerSm3 = builder.minimumGasGrossCalorificValueMjPerSm3;
    maximumGasGrossCalorificValueMjPerSm3 = builder.maximumGasGrossCalorificValueMjPerSm3;
    minimumGasWobbeIndexMjPerSm3 = builder.minimumGasWobbeIndexMjPerSm3;
    maximumGasWobbeIndexMjPerSm3 = builder.maximumGasWobbeIndexMjPerSm3;
    maximumGasRelativeDensity = builder.maximumGasRelativeDensity;
    maximumOilRvpBara = builder.maximumOilRvpBara;
    maximumOilBswVolumePercent = builder.maximumOilBswVolumePercent;
    maximumOilInWaterMgPerL = builder.maximumOilInWaterMgPerL;
    violationAction = builder.violationAction;
  }

  /** Returns a builder with every specification disabled. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns maximum export-gas CO2 in mole percent. */
  public double getMaximumGasCo2MolePercent() {
    return maximumGasCo2MolePercent;
  }

  /** Returns maximum export-gas H2S in ppm molar. */
  public double getMaximumGasH2sPpm() {
    return maximumGasH2sPpm;
  }

  /** Returns maximum export-gas oxygen in mole percent. */
  public double getMaximumGasOxygenMolePercent() {
    return maximumGasOxygenMolePercent;
  }

  /** Returns pressure in bara used for gas dew-point specifications. */
  public double getGasDewPointReferencePressureBara() {
    return gasDewPointReferencePressureBara;
  }

  /** Returns maximum water dew point in Celsius. */
  public double getMaximumGasWaterDewPointC() {
    return maximumGasWaterDewPointC;
  }

  /** Returns maximum hydrocarbon dew point in Celsius. */
  public double getMaximumGasHydrocarbonDewPointC() {
    return maximumGasHydrocarbonDewPointC;
  }

  /** Returns minimum gas gross calorific value in MJ/Sm3 at 15/25 C reference conditions. */
  public double getMinimumGasGrossCalorificValueMjPerSm3() {
    return minimumGasGrossCalorificValueMjPerSm3;
  }

  /** Returns maximum gas gross calorific value in MJ/Sm3 at 15/25 C reference conditions. */
  public double getMaximumGasGrossCalorificValueMjPerSm3() {
    return maximumGasGrossCalorificValueMjPerSm3;
  }

  /** Returns minimum gas superior Wobbe index in MJ/Sm3. */
  public double getMinimumGasWobbeIndexMjPerSm3() {
    return minimumGasWobbeIndexMjPerSm3;
  }

  /** Returns maximum gas superior Wobbe index in MJ/Sm3. */
  public double getMaximumGasWobbeIndexMjPerSm3() {
    return maximumGasWobbeIndexMjPerSm3;
  }

  /** Returns maximum gas relative density. */
  public double getMaximumGasRelativeDensity() {
    return maximumGasRelativeDensity;
  }

  /** Returns maximum stabilized-oil Reid vapour pressure in bara. */
  public double getMaximumOilRvpBara() {
    return maximumOilRvpBara;
  }

  /** Returns maximum stabilized-oil basic sediment and water in volume percent. */
  public double getMaximumOilBswVolumePercent() {
    return maximumOilBswVolumePercent;
  }

  /** Returns maximum discharged oil-in-water concentration in mg/L. */
  public double getMaximumOilInWaterMgPerL() {
    return maximumOilInWaterMgPerL;
  }

  /** Returns the configured consequence of a violation. */
  public ViolationAction getViolationAction() {
    return violationAction;
  }

  /** Returns whether at least one quality or discharge limit is active. */
  public boolean hasActiveLimits() {
    return Double.isFinite(maximumGasCo2MolePercent) || Double.isFinite(maximumGasH2sPpm)
        || Double.isFinite(maximumGasOxygenMolePercent) || Double.isFinite(maximumGasWaterDewPointC)
        || Double.isFinite(maximumGasHydrocarbonDewPointC) || Double.isFinite(minimumGasGrossCalorificValueMjPerSm3)
        || Double.isFinite(maximumGasGrossCalorificValueMjPerSm3) || Double.isFinite(minimumGasWobbeIndexMjPerSm3)
        || Double.isFinite(maximumGasWobbeIndexMjPerSm3) || Double.isFinite(maximumGasRelativeDensity)
        || Double.isFinite(maximumOilRvpBara) || Double.isFinite(maximumOilBswVolumePercent)
        || Double.isFinite(maximumOilInWaterMgPerL);
  }

  /** Builder for export and discharge specifications. */
  public static final class Builder {
    private double maximumGasCo2MolePercent = Double.POSITIVE_INFINITY;
    private double maximumGasH2sPpm = Double.POSITIVE_INFINITY;
    private double maximumGasOxygenMolePercent = Double.POSITIVE_INFINITY;
    private double gasDewPointReferencePressureBara = 70.0;
    private double maximumGasWaterDewPointC = Double.POSITIVE_INFINITY;
    private double maximumGasHydrocarbonDewPointC = Double.POSITIVE_INFINITY;
    private double minimumGasGrossCalorificValueMjPerSm3 = Double.NEGATIVE_INFINITY;
    private double maximumGasGrossCalorificValueMjPerSm3 = Double.POSITIVE_INFINITY;
    private double minimumGasWobbeIndexMjPerSm3 = Double.NEGATIVE_INFINITY;
    private double maximumGasWobbeIndexMjPerSm3 = Double.POSITIVE_INFINITY;
    private double maximumGasRelativeDensity = Double.POSITIVE_INFINITY;
    private double maximumOilRvpBara = Double.POSITIVE_INFINITY;
    private double maximumOilBswVolumePercent = Double.POSITIVE_INFINITY;
    private double maximumOilInWaterMgPerL = Double.POSITIVE_INFINITY;
    private ViolationAction violationAction = ViolationAction.REPORT_ONLY;

    private Builder() {
    }

    /** Sets export-gas CO2 and H2S maxima. */
    public Builder gasComposition(double maximumCo2MolePercent, double maximumH2sPpm) {
      maximumGasCo2MolePercent = nonNegative(maximumCo2MolePercent, "maximumCo2MolePercent");
      maximumGasH2sPpm = nonNegative(maximumH2sPpm, "maximumH2sPpm");
      return this;
    }

    /** Sets the export-gas oxygen maximum in mole percent. */
    public Builder gasOxygen(double maximumOxygenMolePercent) {
      maximumGasOxygenMolePercent = nonNegative(maximumOxygenMolePercent, "maximumOxygenMolePercent");
      return this;
    }

    /** Sets gas water and hydrocarbon dew-point maxima at a reference pressure. */
    public Builder gasDewPoints(double referencePressureBara, double maximumWaterDewPointC,
        double maximumHydrocarbonDewPointC) {
      if (!Double.isFinite(referencePressureBara) || referencePressureBara <= 0.0) {
        throw new IllegalArgumentException("gas dew-point reference pressure must be finite and positive");
      }
      gasDewPointReferencePressureBara = referencePressureBara;
      maximumGasWaterDewPointC = finite(maximumWaterDewPointC, "maximumWaterDewPointC");
      maximumGasHydrocarbonDewPointC = finite(maximumHydrocarbonDewPointC, "maximumHydrocarbonDewPointC");
      return this;
    }

    /**
     * Sets ISO 6976 gas energy-content limits at 15 C volume and 25 C combustion reference temperatures.
     */
    public Builder gasEnergyContent(double minimumGrossCalorificValueMjPerSm3,
        double maximumGrossCalorificValueMjPerSm3, double minimumWobbeIndexMjPerSm3, double maximumWobbeIndexMjPerSm3,
        double maximumRelativeDensity) {
      minimumGasGrossCalorificValueMjPerSm3 = positive(minimumGrossCalorificValueMjPerSm3,
          "minimumGrossCalorificValueMjPerSm3");
      maximumGasGrossCalorificValueMjPerSm3 = positive(maximumGrossCalorificValueMjPerSm3,
          "maximumGrossCalorificValueMjPerSm3");
      minimumGasWobbeIndexMjPerSm3 = positive(minimumWobbeIndexMjPerSm3, "minimumWobbeIndexMjPerSm3");
      maximumGasWobbeIndexMjPerSm3 = positive(maximumWobbeIndexMjPerSm3, "maximumWobbeIndexMjPerSm3");
      this.maximumGasRelativeDensity = positive(maximumRelativeDensity, "maximumRelativeDensity");
      if (minimumGasGrossCalorificValueMjPerSm3 > maximumGasGrossCalorificValueMjPerSm3
          || minimumGasWobbeIndexMjPerSm3 > maximumGasWobbeIndexMjPerSm3) {
        throw new IllegalArgumentException("gas energy-content minimum cannot exceed maximum");
      }
      return this;
    }

    /** Sets stabilized-oil RVP and BS&amp;W maxima. */
    public Builder oilExport(double maximumRvpBara, double maximumBswVolumePercent) {
      maximumOilRvpBara = nonNegative(maximumRvpBara, "maximumRvpBara");
      maximumOilBswVolumePercent = nonNegative(maximumBswVolumePercent, "maximumBswVolumePercent");
      return this;
    }

    /** Sets the produced-water discharge oil-in-water maximum. */
    public Builder producedWater(double maximumOilInWaterMgPerL) {
      this.maximumOilInWaterMgPerL = nonNegative(maximumOilInWaterMgPerL, "maximumOilInWaterMgPerL");
      return this;
    }

    /** Sets whether off-specification options are reported or rejected from recommendation. */
    public Builder violationAction(ViolationAction action) {
      if (action == null) {
        throw new IllegalArgumentException("violation action is required");
      }
      violationAction = action;
      return this;
    }

    /** Builds immutable specifications. */
    public FieldProductSpecifications build() {
      return new FieldProductSpecifications(this);
    }

    private static double nonNegative(double value, String name) {
      if (!Double.isFinite(value) || value < 0.0) {
        throw new IllegalArgumentException(name + " must be finite and non-negative");
      }
      return value;
    }

    private static double finite(double value, String name) {
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException(name + " must be finite");
      }
      return value;
    }

    private static double positive(double value, String name) {
      if (!Double.isFinite(value) || value <= 0.0) {
        throw new IllegalArgumentException(name + " must be finite and positive");
      }
      return value;
    }
  }
}

