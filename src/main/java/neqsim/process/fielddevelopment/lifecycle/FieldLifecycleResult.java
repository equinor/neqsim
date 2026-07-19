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

    AnnualResult(int year, double oilSm3, double gasExportSm3, double gasInjectedSm3, double waterProducedSm3,
        double averageOilRateSm3PerDay, double averageWaterCut, double averageReservoirPressureBara, double energyMWh,
        double co2EmissionsTonnes) {
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

  FieldLifecycleResult(String conceptName, List<AnnualResult> annualResults, CashFlowResult cashFlowResult,
      double breakevenOilPriceUsdPerBbl, double breakevenGasPriceUsdPerSm3, double initialReservoirPressureBara,
      double finalReservoirPressureBara, double cumulativeOilSm3, double cumulativeGasExportSm3,
      double cumulativeGasInjectedSm3, double cumulativeWaterProducedSm3, double lifecycleEnergyMWh,
      double lifecycleCo2Tonnes, String stopReason) {
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

  /**
   * Returns a compact comparison table row.
   *
   * @return Markdown table row
   */
  public String toMarkdownRow() {
    return String.format("| %s | %.0f | %.1f | %.1f | %.1f | %.1f | %.1f |", conceptName, getNpvMusd(),
        getIrr() * 100.0, breakevenOilPriceUsdPerBbl, cumulativeOilSm3 / 1.0e6, cumulativeGasInjectedSm3 / 1.0e9,
        lifecycleCo2Tonnes / 1000.0);
  }
}
