package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Engineering and economic assumptions for a field-lifecycle simulation.
 *
 * <p>
 * The configuration deliberately separates assumptions from the assembled NeqSim model. This makes it possible to
 * compare development concepts on a common basis while retaining a detailed thermodynamic, reservoir and process model
 * for each concept.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class FieldLifecycleConfiguration implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final int startYear;
  private final double projectYears;
  private final double timeStepDays;
  private final double availability;
  private final int producerCount;
  private final double productivityIndexSm3PerDayBarPerWell;
  private final double minimumBottomHolePressureBara;
  private final double plateauOilRateSm3PerDay;
  private final double maximumLiquidRateSm3PerDay;
  private final double maximumGasRateSm3PerDay;
  private final double maximumWaterRateSm3PerDay;
  private final double economicLimitOilRateSm3PerDay;
  private final double initialWaterCut;
  private final double finalWaterCut;
  private final double waterBreakthroughYear;
  private final double waterCutRampYears;
  private final double oilDensityKgPerSm3;
  private final double waterDensityKgPerSm3;
  private final double gasInjectionStartYear;
  private final double producedGasRecycleFraction;
  private final double maximumGasInjectionRateSm3PerDay;
  private final double gridEmissionFactorKgPerKWh;
  private final double oilPriceUsdPerBbl;
  private final double gasPriceUsdPerSm3;
  private final double discountRate;
  private final double fixedOpexMusdPerYear;
  private final double variableOpexUsdPerBoe;
  private final double oilTariffUsdPerBbl;
  private final double gasTariffUsdPerSm3;
  private final Map<Integer, Double> capexScheduleMusd;
  private final FacilityLifecycleStrategy facilityLifecycleStrategy;

  private FieldLifecycleConfiguration(Builder builder) {
    startYear = builder.startYear;
    projectYears = builder.projectYears;
    timeStepDays = builder.timeStepDays;
    availability = builder.availability;
    producerCount = builder.producerCount;
    productivityIndexSm3PerDayBarPerWell = builder.productivityIndexSm3PerDayBarPerWell;
    minimumBottomHolePressureBara = builder.minimumBottomHolePressureBara;
    plateauOilRateSm3PerDay = builder.plateauOilRateSm3PerDay;
    maximumLiquidRateSm3PerDay = builder.maximumLiquidRateSm3PerDay;
    maximumGasRateSm3PerDay = builder.maximumGasRateSm3PerDay;
    maximumWaterRateSm3PerDay = builder.maximumWaterRateSm3PerDay;
    economicLimitOilRateSm3PerDay = builder.economicLimitOilRateSm3PerDay;
    initialWaterCut = builder.initialWaterCut;
    finalWaterCut = builder.finalWaterCut;
    waterBreakthroughYear = builder.waterBreakthroughYear;
    waterCutRampYears = builder.waterCutRampYears;
    oilDensityKgPerSm3 = builder.oilDensityKgPerSm3;
    waterDensityKgPerSm3 = builder.waterDensityKgPerSm3;
    gasInjectionStartYear = builder.gasInjectionStartYear;
    producedGasRecycleFraction = builder.producedGasRecycleFraction;
    maximumGasInjectionRateSm3PerDay = builder.maximumGasInjectionRateSm3PerDay;
    gridEmissionFactorKgPerKWh = builder.gridEmissionFactorKgPerKWh;
    oilPriceUsdPerBbl = builder.oilPriceUsdPerBbl;
    gasPriceUsdPerSm3 = builder.gasPriceUsdPerSm3;
    discountRate = builder.discountRate;
    fixedOpexMusdPerYear = builder.fixedOpexMusdPerYear;
    variableOpexUsdPerBoe = builder.variableOpexUsdPerBoe;
    oilTariffUsdPerBbl = builder.oilTariffUsdPerBbl;
    gasTariffUsdPerSm3 = builder.gasTariffUsdPerSm3;
    capexScheduleMusd = Collections.unmodifiableMap(new LinkedHashMap<Integer, Double>(builder.capexScheduleMusd));
    facilityLifecycleStrategy = builder.facilityLifecycleStrategy;
  }

  /**
   * Creates a configuration builder with conservative offshore defaults.
   *
   * @return new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Calculates the water cut at a field age using a linear post-breakthrough ramp.
   *
   * @param fieldAgeYears elapsed production time in years
   * @return water cut as a fraction
   */
  public double getWaterCut(double fieldAgeYears) {
    if (fieldAgeYears <= waterBreakthroughYear || waterCutRampYears <= 0.0) {
      return initialWaterCut;
    }
    double fraction = Math.min(1.0, (fieldAgeYears - waterBreakthroughYear) / waterCutRampYears);
    return initialWaterCut + fraction * (finalWaterCut - initialWaterCut);
  }

  /** Returns the first production calendar year. */
  public int getStartYear() {
    return startYear;
  }

  /** Returns the maximum project duration in years. */
  public double getProjectYears() {
    return projectYears;
  }

  /** Returns the simulation time-step length in days. */
  public double getTimeStepDays() {
    return timeStepDays;
  }

  /** Returns the production availability fraction. */
  public double getAvailability() {
    return availability;
  }

  /** Returns the number of aggregate producing wells. */
  public int getProducerCount() {
    return producerCount;
  }

  /** Returns the per-well oil productivity index in Sm3/day/bar. */
  public double getProductivityIndexSm3PerDayBarPerWell() {
    return productivityIndexSm3PerDayBarPerWell;
  }

  /** Returns the minimum flowing bottom-hole pressure in bara. */
  public double getMinimumBottomHolePressureBara() {
    return minimumBottomHolePressureBara;
  }

  /** Returns the plateau oil rate in Sm3/day. */
  public double getPlateauOilRateSm3PerDay() {
    return plateauOilRateSm3PerDay;
  }

  /** Returns the maximum facility liquid rate in Sm3/day. */
  public double getMaximumLiquidRateSm3PerDay() {
    return maximumLiquidRateSm3PerDay;
  }

  /** Returns the maximum facility gas rate in Sm3/day. */
  public double getMaximumGasRateSm3PerDay() {
    return maximumGasRateSm3PerDay;
  }

  /** Returns the maximum produced-water rate in Sm3/day. */
  public double getMaximumWaterRateSm3PerDay() {
    return maximumWaterRateSm3PerDay;
  }

  /** Returns the economic-limit oil rate in Sm3/day. */
  public double getEconomicLimitOilRateSm3PerDay() {
    return economicLimitOilRateSm3PerDay;
  }

  /** Returns the water cut before breakthrough. */
  public double getInitialWaterCut() {
    return initialWaterCut;
  }

  /** Returns the terminal water-cut assumption. */
  public double getFinalWaterCut() {
    return finalWaterCut;
  }

  /** Returns the assumed water-breakthrough field age in years. */
  public double getWaterBreakthroughYear() {
    return waterBreakthroughYear;
  }

  /** Returns the duration of the post-breakthrough water-cut ramp in years. */
  public double getWaterCutRampYears() {
    return waterCutRampYears;
  }

  /** Returns the standard oil density in kg/Sm3. */
  public double getOilDensityKgPerSm3() {
    return oilDensityKgPerSm3;
  }

  /** Returns the standard water density in kg/Sm3. */
  public double getWaterDensityKgPerSm3() {
    return waterDensityKgPerSm3;
  }

  /** Returns the gas-injection start field age in years. */
  public double getGasInjectionStartYear() {
    return gasInjectionStartYear;
  }

  /** Returns the target recovered-gas recycle fraction. */
  public double getProducedGasRecycleFraction() {
    return producedGasRecycleFraction;
  }

  /** Returns the surface gas-injection capacity in Sm3/day. */
  public double getMaximumGasInjectionRateSm3PerDay() {
    return maximumGasInjectionRateSm3PerDay;
  }

  /** Returns the electricity emissions factor in kg CO2/kWh. */
  public double getGridEmissionFactorKgPerKWh() {
    return gridEmissionFactorKgPerKWh;
  }

  /** Returns the oil price in USD/bbl. */
  public double getOilPriceUsdPerBbl() {
    return oilPriceUsdPerBbl;
  }

  /** Returns the gas price in USD/Sm3. */
  public double getGasPriceUsdPerSm3() {
    return gasPriceUsdPerSm3;
  }

  /** Returns the real project discount rate as a fraction. */
  public double getDiscountRate() {
    return discountRate;
  }

  /** Returns fixed annual OPEX in MUSD. */
  public double getFixedOpexMusdPerYear() {
    return fixedOpexMusdPerYear;
  }

  /** Returns variable OPEX in USD/boe. */
  public double getVariableOpexUsdPerBoe() {
    return variableOpexUsdPerBoe;
  }

  /** Returns the oil transport tariff in USD/bbl. */
  public double getOilTariffUsdPerBbl() {
    return oilTariffUsdPerBbl;
  }

  /** Returns the gas transport tariff in USD/Sm3. */
  public double getGasTariffUsdPerSm3() {
    return gasTariffUsdPerSm3;
  }

  /** Returns the immutable calendar-year CAPEX schedule in MUSD. */
  public Map<Integer, Double> getCapexScheduleMusd() {
    return capexScheduleMusd;
  }

  /** Returns the optional greenfield-design or brownfield tieback strategy. */
  public FacilityLifecycleStrategy getFacilityLifecycleStrategy() {
    return facilityLifecycleStrategy;
  }

  /** Builder for {@link FieldLifecycleConfiguration}. */
  public static final class Builder {
    private int startYear = 2028;
    private double projectYears = 25.0;
    private double timeStepDays = 91.3125;
    private double availability = 0.92;
    private int producerCount = 6;
    private double productivityIndexSm3PerDayBarPerWell = 35.0;
    private double minimumBottomHolePressureBara = 120.0;
    private double plateauOilRateSm3PerDay = 25000.0;
    private double maximumLiquidRateSm3PerDay = 45000.0;
    private double maximumGasRateSm3PerDay = 6.0e6;
    private double maximumWaterRateSm3PerDay = 30000.0;
    private double economicLimitOilRateSm3PerDay = 500.0;
    private double initialWaterCut = 0.05;
    private double finalWaterCut = 0.80;
    private double waterBreakthroughYear = 3.0;
    private double waterCutRampYears = 15.0;
    private double oilDensityKgPerSm3 = 835.0;
    private double waterDensityKgPerSm3 = 1025.0;
    private double gasInjectionStartYear = 0.0;
    private double producedGasRecycleFraction = 0.85;
    private double maximumGasInjectionRateSm3PerDay = 5.0e6;
    private double gridEmissionFactorKgPerKWh = 0.018;
    private double oilPriceUsdPerBbl = 70.0;
    private double gasPriceUsdPerSm3 = 0.25;
    private double discountRate = 0.08;
    private double fixedOpexMusdPerYear = 120.0;
    private double variableOpexUsdPerBoe = 8.0;
    private double oilTariffUsdPerBbl = 2.0;
    private double gasTariffUsdPerSm3 = 0.015;
    private final Map<Integer, Double> capexScheduleMusd = new LinkedHashMap<Integer, Double>();
    private FacilityLifecycleStrategy facilityLifecycleStrategy;

    private Builder() {
    }

    /** Sets the first production calendar year. */
    public Builder startYear(int value) {
      startYear = value;
      return this;
    }

    /** Sets the maximum project duration in years. */
    public Builder projectYears(double value) {
      projectYears = value;
      return this;
    }

    /** Sets the simulation time-step length in days. */
    public Builder timeStepDays(double value) {
      timeStepDays = value;
      return this;
    }

    /** Sets the production availability fraction. */
    public Builder availability(double value) {
      availability = value;
      return this;
    }

    /** Sets aggregate producer count and per-well oil productivity index. */
    public Builder producers(int count, double productivityIndexSm3dBarPerWell) {
      producerCount = count;
      productivityIndexSm3PerDayBarPerWell = productivityIndexSm3dBarPerWell;
      return this;
    }

    /** Sets the minimum flowing bottom-hole pressure in bara. */
    public Builder minimumBottomHolePressure(double valueBara) {
      minimumBottomHolePressureBara = valueBara;
      return this;
    }

    /** Sets the plateau oil rate in Sm3/day. */
    public Builder plateauOilRate(double valueSm3PerDay) {
      plateauOilRateSm3PerDay = valueSm3PerDay;
      return this;
    }

    /** Sets total-liquid, gas and produced-water facility capacities. */
    public Builder facilityCapacities(double liquidSm3d, double gasSm3d, double waterSm3d) {
      maximumLiquidRateSm3PerDay = liquidSm3d;
      maximumGasRateSm3PerDay = gasSm3d;
      maximumWaterRateSm3PerDay = waterSm3d;
      return this;
    }

    /** Sets the economic-limit oil rate in Sm3/day. */
    public Builder economicLimitOilRate(double valueSm3PerDay) {
      economicLimitOilRateSm3PerDay = valueSm3PerDay;
      return this;
    }

    /** Sets the linear water-cut schedule. */
    public Builder waterCut(double initial, double end, double breakthroughYear, double rampYears) {
      initialWaterCut = initial;
      finalWaterCut = end;
      waterBreakthroughYear = breakthroughYear;
      waterCutRampYears = rampYears;
      return this;
    }

    /** Sets standard oil and water densities in kg/Sm3. */
    public Builder standardDensities(double oilKgPerSm3, double waterKgPerSm3) {
      oilDensityKgPerSm3 = oilKgPerSm3;
      waterDensityKgPerSm3 = waterKgPerSm3;
      return this;
    }

    /** Sets the produced-gas injection strategy and capacity. */
    public Builder gasInjection(double startYear, double recycleFraction, double maximumRateSm3PerDay) {
      gasInjectionStartYear = startYear;
      producedGasRecycleFraction = recycleFraction;
      maximumGasInjectionRateSm3PerDay = maximumRateSm3PerDay;
      return this;
    }

    /** Sets the electricity emissions factor in kg CO2/kWh. */
    public Builder gridEmissionFactor(double valueKgPerKWh) {
      gridEmissionFactorKgPerKWh = valueKgPerKWh;
      return this;
    }

    /** Sets oil and gas sales prices. */
    public Builder prices(double oilUsdPerBbl, double gasUsdPerSm3) {
      oilPriceUsdPerBbl = oilUsdPerBbl;
      gasPriceUsdPerSm3 = gasUsdPerSm3;
      return this;
    }

    /** Sets the real project discount rate as a fraction. */
    public Builder discountRate(double value) {
      discountRate = value;
      return this;
    }

    /** Sets fixed annual and variable production OPEX. */
    public Builder opex(double fixedMusdPerYear, double variableUsdPerBoe) {
      fixedOpexMusdPerYear = fixedMusdPerYear;
      variableOpexUsdPerBoe = variableUsdPerBoe;
      return this;
    }

    /** Sets oil and gas transport tariffs. */
    public Builder tariffs(double oilUsdPerBbl, double gasUsdPerSm3) {
      oilTariffUsdPerBbl = oilUsdPerBbl;
      gasTariffUsdPerSm3 = gasUsdPerSm3;
      return this;
    }

    /** Adds CAPEX in MUSD for a calendar year. */
    public Builder capex(int year, double valueMusd) {
      capexScheduleMusd.put(year, valueMusd);
      return this;
    }

    /** Sets facility sizing, host profile, shared capacity, allocation and holdback assumptions. */
    public Builder facilityLifecycleStrategy(FacilityLifecycleStrategy strategy) {
      facilityLifecycleStrategy = strategy;
      return this;
    }

    /** Validates the assumptions and creates an immutable configuration. */
    public FieldLifecycleConfiguration build() {
      if (startYear < 1) {
        throw new IllegalArgumentException("startYear must be positive");
      }
      requirePositive(projectYears, "projectYears");
      requirePositive(timeStepDays, "timeStepDays");
      requireFraction(availability, "availability");
      requireFraction(initialWaterCut, "initialWaterCut");
      requireFraction(finalWaterCut, "finalWaterCut");
      requireFraction(producedGasRecycleFraction, "producedGasRecycleFraction");
      if (producerCount < 1) {
        throw new IllegalArgumentException("producerCount must be at least one");
      }
      if (finalWaterCut < initialWaterCut) {
        throw new IllegalArgumentException("finalWaterCut cannot be below initialWaterCut");
      }
      requirePositive(productivityIndexSm3PerDayBarPerWell, "productivityIndexSm3PerDayBarPerWell");
      requirePositive(minimumBottomHolePressureBara, "minimumBottomHolePressureBara");
      requirePositive(plateauOilRateSm3PerDay, "plateauOilRateSm3PerDay");
      requirePositive(maximumLiquidRateSm3PerDay, "maximumLiquidRateSm3PerDay");
      requirePositive(maximumGasRateSm3PerDay, "maximumGasRateSm3PerDay");
      requirePositive(maximumWaterRateSm3PerDay, "maximumWaterRateSm3PerDay");
      requireNonNegative(economicLimitOilRateSm3PerDay, "economicLimitOilRateSm3PerDay");
      requireNonNegative(waterBreakthroughYear, "waterBreakthroughYear");
      requireNonNegative(waterCutRampYears, "waterCutRampYears");
      requirePositive(oilDensityKgPerSm3, "oilDensityKgPerSm3");
      requirePositive(waterDensityKgPerSm3, "waterDensityKgPerSm3");
      requireNonNegative(gasInjectionStartYear, "gasInjectionStartYear");
      requireNonNegative(maximumGasInjectionRateSm3PerDay, "maximumGasInjectionRateSm3PerDay");
      requireNonNegative(gridEmissionFactorKgPerKWh, "gridEmissionFactorKgPerKWh");
      requireNonNegative(oilPriceUsdPerBbl, "oilPriceUsdPerBbl");
      requireNonNegative(gasPriceUsdPerSm3, "gasPriceUsdPerSm3");
      requireNonNegative(discountRate, "discountRate");
      requireNonNegative(fixedOpexMusdPerYear, "fixedOpexMusdPerYear");
      requireNonNegative(variableOpexUsdPerBoe, "variableOpexUsdPerBoe");
      requireNonNegative(oilTariffUsdPerBbl, "oilTariffUsdPerBbl");
      requireNonNegative(gasTariffUsdPerSm3, "gasTariffUsdPerSm3");
      if (capexScheduleMusd.isEmpty()) {
        capexScheduleMusd.put(startYear - 3, 1000.0);
        capexScheduleMusd.put(startYear - 2, 1800.0);
        capexScheduleMusd.put(startYear - 1, 1200.0);
      }
      for (Map.Entry<Integer, Double> capex : capexScheduleMusd.entrySet()) {
        if (capex.getKey() < 1) {
          throw new IllegalArgumentException("CAPEX calendar year must be positive");
        }
        requireNonNegative(capex.getValue(), "CAPEX");
      }
      return new FieldLifecycleConfiguration(this);
    }

    private void requirePositive(double value, String name) {
      if (!(value > 0.0) || !Double.isFinite(value)) {
        throw new IllegalArgumentException(name + " must be finite and positive");
      }
    }

    private void requireNonNegative(double value, String name) {
      if (value < 0.0 || !Double.isFinite(value)) {
        throw new IllegalArgumentException(name + " must be finite and non-negative");
      }
    }

    private void requireFraction(double value, String name) {
      if (value < 0.0 || value > 1.0 || !Double.isFinite(value)) {
        throw new IllegalArgumentException(name + " must be finite and between zero and one");
      }
    }
  }
}
