package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.fielddevelopment.economics.CashFlowEngine.CashFlowResult;

/**
 * Technical, production, emissions and economic results from a field-lifecycle simulation.
 *
 * @author ESOL
 * @version 1.0
 */
public final class FieldLifecycleResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Annual field result on a calendar-year basis. */
  public static final class AnnualResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final int year;
    private final double oilSm3;
    private final double gasExportSm3;
    private final double gasInjectedSm3;
    private final double waterProducedSm3;
    private final double averageOilRateSm3PerDay;
    private final double averageWaterCut;
    private final double averageReservoirPressureBara;
    private final double energyMWh;
    private final double co2EmissionsTonnes;
    private final double potentialOilRateSm3PerDay;
    private final double requestedOilRateSm3PerDay;
    private final double hostOilRateSm3PerDay;
    private final double hostGasRateSm3PerDay;
    private final double hostWaterRateSm3PerDay;
    private final double holdbackOilSm3;
    private final double capacityDeferredOilSm3;
    private final double maximumFacilityUtilization;
    private final String primaryBottleneck;
    private final double unconstrainedFacilityUtilization;
    private final String unconstrainedBottleneck;
    private final ProductSpecificationResult productSpecificationResult;

    AnnualResult(int year, double oilSm3, double gasExportSm3, double gasInjectedSm3, double waterProducedSm3,
        double averageOilRateSm3PerDay, double averageWaterCut, double averageReservoirPressureBara, double energyMWh,
        double co2EmissionsTonnes, double potentialOilRateSm3PerDay, double requestedOilRateSm3PerDay,
        double hostOilRateSm3PerDay, double hostGasRateSm3PerDay, double hostWaterRateSm3PerDay, double holdbackOilSm3,
        double capacityDeferredOilSm3, double maximumFacilityUtilization, String primaryBottleneck) {
      this(year, oilSm3, gasExportSm3, gasInjectedSm3, waterProducedSm3, averageOilRateSm3PerDay, averageWaterCut,
          averageReservoirPressureBara, energyMWh, co2EmissionsTonnes, potentialOilRateSm3PerDay,
          requestedOilRateSm3PerDay, hostOilRateSm3PerDay, hostGasRateSm3PerDay, hostWaterRateSm3PerDay, holdbackOilSm3,
          capacityDeferredOilSm3, maximumFacilityUtilization, primaryBottleneck,
          ProductSpecificationResult.notEvaluated());
    }

    AnnualResult(int year, double oilSm3, double gasExportSm3, double gasInjectedSm3, double waterProducedSm3,
        double averageOilRateSm3PerDay, double averageWaterCut, double averageReservoirPressureBara, double energyMWh,
        double co2EmissionsTonnes, double potentialOilRateSm3PerDay, double requestedOilRateSm3PerDay,
        double hostOilRateSm3PerDay, double hostGasRateSm3PerDay, double hostWaterRateSm3PerDay, double holdbackOilSm3,
        double capacityDeferredOilSm3, double maximumFacilityUtilization, String primaryBottleneck,
        ProductSpecificationResult productSpecificationResult) {
      this(year, oilSm3, gasExportSm3, gasInjectedSm3, waterProducedSm3, averageOilRateSm3PerDay, averageWaterCut,
          averageReservoirPressureBara, energyMWh, co2EmissionsTonnes, potentialOilRateSm3PerDay,
          requestedOilRateSm3PerDay, hostOilRateSm3PerDay, hostGasRateSm3PerDay, hostWaterRateSm3PerDay, holdbackOilSm3,
          capacityDeferredOilSm3, maximumFacilityUtilization, primaryBottleneck, maximumFacilityUtilization,
          primaryBottleneck, productSpecificationResult);
    }

    AnnualResult(int year, double oilSm3, double gasExportSm3, double gasInjectedSm3, double waterProducedSm3,
        double averageOilRateSm3PerDay, double averageWaterCut, double averageReservoirPressureBara, double energyMWh,
        double co2EmissionsTonnes, double potentialOilRateSm3PerDay, double requestedOilRateSm3PerDay,
        double hostOilRateSm3PerDay, double hostGasRateSm3PerDay, double hostWaterRateSm3PerDay, double holdbackOilSm3,
        double capacityDeferredOilSm3, double maximumFacilityUtilization, String primaryBottleneck,
        double unconstrainedFacilityUtilization, String unconstrainedBottleneck,
        ProductSpecificationResult productSpecificationResult) {
      this.year = year;
      this.oilSm3 = oilSm3;
      this.gasExportSm3 = gasExportSm3;
      this.gasInjectedSm3 = gasInjectedSm3;
      this.waterProducedSm3 = waterProducedSm3;
      this.averageOilRateSm3PerDay = averageOilRateSm3PerDay;
      this.averageWaterCut = averageWaterCut;
      this.averageReservoirPressureBara = averageReservoirPressureBara;
      this.energyMWh = energyMWh;
      this.co2EmissionsTonnes = co2EmissionsTonnes;
      this.potentialOilRateSm3PerDay = potentialOilRateSm3PerDay;
      this.requestedOilRateSm3PerDay = requestedOilRateSm3PerDay;
      this.hostOilRateSm3PerDay = hostOilRateSm3PerDay;
      this.hostGasRateSm3PerDay = hostGasRateSm3PerDay;
      this.hostWaterRateSm3PerDay = hostWaterRateSm3PerDay;
      this.holdbackOilSm3 = holdbackOilSm3;
      this.capacityDeferredOilSm3 = capacityDeferredOilSm3;
      this.maximumFacilityUtilization = maximumFacilityUtilization;
      this.primaryBottleneck = primaryBottleneck;
      this.unconstrainedFacilityUtilization = unconstrainedFacilityUtilization;
      this.unconstrainedBottleneck = unconstrainedBottleneck;
      this.productSpecificationResult = productSpecificationResult == null ? ProductSpecificationResult.notEvaluated()
          : productSpecificationResult;
    }

    /** Returns the calendar year. */
    public int getYear() {
      return year;
    }

    /** Returns annual stabilized oil export in Sm3. */
    public double getOilSm3() {
      return oilSm3;
    }

    /** Returns annual sales-gas export in Sm3. */
    public double getGasExportSm3() {
      return gasExportSm3;
    }

    /** Returns annual reservoir gas injection in Sm3. */
    public double getGasInjectedSm3() {
      return gasInjectedSm3;
    }

    /** Returns annual produced water in Sm3. */
    public double getWaterProducedSm3() {
      return waterProducedSm3;
    }

    /** Returns calendar-day average stabilized oil export in Sm3/day. */
    public double getAverageOilRateSm3PerDay() {
      return averageOilRateSm3PerDay;
    }

    /** Returns the time-weighted average water cut. */
    public double getAverageWaterCut() {
      return averageWaterCut;
    }

    /** Returns the time-weighted average reservoir pressure in bara. */
    public double getAverageReservoirPressureBara() {
      return averageReservoirPressureBara;
    }

    /** Returns annual process power consumption in MWh. */
    public double getEnergyMWh() {
      return energyMWh;
    }

    /** Returns annual electricity-related CO2 emissions in tonnes. */
    public double getCo2EmissionsTonnes() {
      return co2EmissionsTonnes;
    }

    /** Returns average unconstrained new-field oil potential in Sm3/day. */
    public double getPotentialOilRateSm3PerDay() {
      return potentialOilRateSm3PerDay;
    }

    /** Returns average new-field oil requested after planned holdback in Sm3/day. */
    public double getRequestedOilRateSm3PerDay() {
      return requestedOilRateSm3PerDay;
    }

    /** Returns average admitted existing-host oil in Sm3/day. */
    public double getHostOilRateSm3PerDay() {
      return hostOilRateSm3PerDay;
    }

    /** Returns average admitted existing-host gas in Sm3/day. */
    public double getHostGasRateSm3PerDay() {
      return hostGasRateSm3PerDay;
    }

    /** Returns average admitted existing-host water in Sm3/day. */
    public double getHostWaterRateSm3PerDay() {
      return hostWaterRateSm3PerDay;
    }

    /** Returns annual new-field oil deliberately held back before allocation in Sm3. */
    public double getHoldbackOilSm3() {
      return holdbackOilSm3;
    }

    /** Returns annual requested new-field oil deferred by nameplate or equipment constraints in Sm3. */
    public double getCapacityDeferredOilSm3() {
      return capacityDeferredOilSm3;
    }

    /** Returns the maximum nameplate or detailed-equipment utilization during the year. */
    public double getMaximumFacilityUtilization() {
      return maximumFacilityUtilization;
    }

    /** Returns the highest-utilization facility or equipment bottleneck in the year. */
    public String getPrimaryBottleneck() {
      return primaryBottleneck;
    }

    /** Returns maximum requested utilization before nameplate/equipment rate reduction. */
    public double getUnconstrainedFacilityUtilization() {
      return unconstrainedFacilityUtilization;
    }

    /** Returns the requested-rate nameplate or equipment bottleneck. */
    public String getUnconstrainedBottleneck() {
      return unconstrainedBottleneck;
    }

    /** Returns worst product-quality measurements and violations recorded during the year. */
    public ProductSpecificationResult getProductSpecificationResult() {
      return productSpecificationResult;
    }
  }

  private final String conceptName;
  private final List<AnnualResult> annualResults;
  private final CashFlowResult cashFlowResult;
  private final double breakevenOilPriceUsdPerBbl;
  private final double breakevenGasPriceUsdPerSm3;
  private final double initialReservoirPressureBara;
  private final double finalReservoirPressureBara;
  private final double cumulativeOilSm3;
  private final double cumulativeGasExportSm3;
  private final double cumulativeGasInjectedSm3;
  private final double cumulativeWaterProducedSm3;
  private final double lifecycleEnergyMWh;
  private final double lifecycleCo2Tonnes;
  private final String stopReason;
  private final FacilityDesignResult facilityDesignResult;
  private final double cumulativeDeferredOilSm3;
  private final double peakFacilityUtilization;

  FieldLifecycleResult(String conceptName, List<AnnualResult> annualResults, CashFlowResult cashFlowResult,
      double breakevenOilPriceUsdPerBbl, double breakevenGasPriceUsdPerSm3, double initialReservoirPressureBara,
      double finalReservoirPressureBara, double cumulativeOilSm3, double cumulativeGasExportSm3,
      double cumulativeGasInjectedSm3, double cumulativeWaterProducedSm3, double lifecycleEnergyMWh,
      double lifecycleCo2Tonnes, String stopReason, FacilityDesignResult facilityDesignResult,
      double cumulativeDeferredOilSm3, double peakFacilityUtilization) {
    this.conceptName = conceptName;
    this.annualResults = Collections.unmodifiableList(new ArrayList<AnnualResult>(annualResults));
    this.cashFlowResult = cashFlowResult;
    this.breakevenOilPriceUsdPerBbl = breakevenOilPriceUsdPerBbl;
    this.breakevenGasPriceUsdPerSm3 = breakevenGasPriceUsdPerSm3;
    this.initialReservoirPressureBara = initialReservoirPressureBara;
    this.finalReservoirPressureBara = finalReservoirPressureBara;
    this.cumulativeOilSm3 = cumulativeOilSm3;
    this.cumulativeGasExportSm3 = cumulativeGasExportSm3;
    this.cumulativeGasInjectedSm3 = cumulativeGasInjectedSm3;
    this.cumulativeWaterProducedSm3 = cumulativeWaterProducedSm3;
    this.lifecycleEnergyMWh = lifecycleEnergyMWh;
    this.lifecycleCo2Tonnes = lifecycleCo2Tonnes;
    this.stopReason = stopReason;
    this.facilityDesignResult = facilityDesignResult;
    this.cumulativeDeferredOilSm3 = cumulativeDeferredOilSm3;
    this.peakFacilityUtilization = peakFacilityUtilization;
  }

  /** Returns the evaluated concept name. */
  public String getConceptName() {
    return conceptName;
  }

  /** Returns immutable annual technical results. */
  public List<AnnualResult> getAnnualResults() {
    return annualResults;
  }

  /** Returns the full after-tax annual cash-flow result. */
  public CashFlowResult getCashFlowResult() {
    return cashFlowResult;
  }

  /** Returns after-tax project NPV in MUSD. */
  public double getNpvMusd() {
    return cashFlowResult.getNpv();
  }

  /** Returns after-tax project IRR as a fraction. */
  public double getIrr() {
    return cashFlowResult.getIrr();
  }

  /** Returns undiscounted payback time in project years. */
  public double getPaybackYears() {
    return cashFlowResult.getPaybackYears();
  }

  /** Returns the zero-NPV oil price in USD/bbl. */
  public double getBreakevenOilPriceUsdPerBbl() {
    return breakevenOilPriceUsdPerBbl;
  }

  /** Returns the zero-NPV gas price in USD/Sm3. */
  public double getBreakevenGasPriceUsdPerSm3() {
    return breakevenGasPriceUsdPerSm3;
  }

  /** Returns initial reservoir pressure in bara. */
  public double getInitialReservoirPressureBara() {
    return initialReservoirPressureBara;
  }

  /** Returns final simulated reservoir pressure in bara. */
  public double getFinalReservoirPressureBara() {
    return finalReservoirPressureBara;
  }

  /** Returns cumulative stabilized oil export in Sm3. */
  public double getCumulativeOilSm3() {
    return cumulativeOilSm3;
  }

  /** Returns cumulative sales-gas export in Sm3. */
  public double getCumulativeGasExportSm3() {
    return cumulativeGasExportSm3;
  }

  /** Returns cumulative reservoir gas injection in Sm3. */
  public double getCumulativeGasInjectedSm3() {
    return cumulativeGasInjectedSm3;
  }

  /** Returns cumulative produced water in Sm3. */
  public double getCumulativeWaterProducedSm3() {
    return cumulativeWaterProducedSm3;
  }

  /** Returns lifecycle process power consumption in MWh. */
  public double getLifecycleEnergyMWh() {
    return lifecycleEnergyMWh;
  }

  /** Returns lifecycle electricity-related CO2 emissions in tonnes. */
  public double getLifecycleCo2Tonnes() {
    return lifecycleCo2Tonnes;
  }

  /** Returns the project termination reason. */
  public String getStopReason() {
    return stopReason;
  }

  /** Returns greenfield sizing or brownfield nameplate/design information, or null for a legacy run. */
  public FacilityDesignResult getFacilityDesignResult() {
    return facilityDesignResult;
  }

  /** Returns cumulative new-field oil deferred by holdback and facility constraints in Sm3. */
  public double getCumulativeDeferredOilSm3() {
    return cumulativeDeferredOilSm3;
  }

  /** Returns peak lifecycle facility/equipment utilization as a fraction. */
  public double getPeakFacilityUtilization() {
    return peakFacilityUtilization;
  }

  /** Returns peak requested utilization before facility-driven rate reduction. */
  public double getPeakUnconstrainedFacilityUtilization() {
    double peak = 0.0;
    for (AnnualResult result : annualResults) {
      peak = Math.max(peak, result.getUnconstrainedFacilityUtilization());
    }
    return peak;
  }

  /** Returns the number of simulated years with at least one product-specification violation. */
  public int getOffSpecificationYears() {
    int count = 0;
    for (AnnualResult result : annualResults) {
      if (result.getProductSpecificationResult().isEvaluated()
          && !result.getProductSpecificationResult().isCompliant()) {
        count++;
      }
    }
    return count;
  }

  /** Returns true when all evaluated annual product specifications are met. */
  public boolean areAllProductSpecificationsMet() {
    return getOffSpecificationYears() == 0;
  }

  /** Returns a compact summary of annual product-specification violations. */
  public String getProductSpecificationSummary() {
    for (AnnualResult result : annualResults) {
      if (!result.getProductSpecificationResult().isCompliant()) {
        return result.getYear() + ": " + result.getProductSpecificationResult().getSummary();
      }
    }
    return annualResults.isEmpty() ? "not evaluated" : "on specification";
  }

  /**
   * Returns a compact comparison table row.
   *
   * @return Markdown table row
   */
  public String toMarkdownRow() {
    return String.format("| %s | %.0f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %d |", conceptName,
        getNpvMusd(), getIrr() * 100.0, breakevenOilPriceUsdPerBbl, cumulativeOilSm3 / 1.0e6,
        cumulativeDeferredOilSm3 / 1.0e6, peakFacilityUtilization * 100.0,
        getPeakUnconstrainedFacilityUtilization() * 100.0, cumulativeGasInjectedSm3 / 1.0e9,
        lifecycleCo2Tonnes / 1000.0, getOffSpecificationYears());
  }
}

