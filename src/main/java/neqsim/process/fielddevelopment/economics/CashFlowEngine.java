package neqsim.process.fielddevelopment.economics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full-lifecycle cash flow engine for field development economics.
 *
 * <p>
 * This class generates year-by-year cash flow projections for oil and gas field developments,
 * incorporating:
 * </p>
 * <ul>
 * <li><b>Revenue</b>: From oil, gas, and NGL sales</li>
 * <li><b>CAPEX</b>: Capital expenditures with depreciation schedules</li>
 * <li><b>OPEX</b>: Operating expenditures (fixed and variable)</li>
 * <li><b>Taxes</b>: Country-specific tax models via {@link TaxModel} interface</li>
 * <li><b>Tariffs</b>: Transport and processing tariffs</li>
 * </ul>
 *
 * <h2>Key Metrics</h2>
 * <p>
 * The engine calculates standard project economics:
 * </p>
 * <ul>
 * <li><b>NPV</b>: Net Present Value at specified discount rate</li>
 * <li><b>IRR</b>: Internal Rate of Return</li>
 * <li><b>Payback</b>: Time to recover initial investment</li>
 * <li><b>Breakeven</b>: Oil/gas price for zero NPV</li>
 * </ul>
 *
 * <h2>Tax Model Support</h2>
 * <p>
 * The engine supports any country's fiscal regime via the {@link TaxModel} interface:
 * </p>
 * 
 * <pre>{@code
 * // Use Norwegian tax model (default)
 * CashFlowEngine engine = new CashFlowEngine();
 * 
 * // Use any country from the registry
 * engine.setTaxModel(TaxModelRegistry.createModel("BR-PSA")); // Brazil Pre-Salt
 * engine.setTaxModel(TaxModelRegistry.createModel("UK")); // UK Continental Shelf
 * engine.setTaxModel(TaxModelRegistry.createModel("US-GOM")); // Gulf of Mexico
 * }</pre>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Create engine with Brazilian tax model
 * CashFlowEngine engine = new CashFlowEngine("BR");
 * 
 * // Set project parameters
 * engine.setCapex(800, 2025); // 800 MUSD in 2025
 * engine.setOpexPercentOfCapex(0.04); // 4% of CAPEX per year
 * 
 * // Set price assumptions
 * engine.setOilPrice(75.0); // USD/bbl
 * engine.setGasPrice(0.25); // USD/Sm3
 * 
 * // Add production profile
 * engine.addAnnualProduction(2026, 0, 5.0e6, 0); // 5 MSm3 gas in 2026
 * engine.addAnnualProduction(2027, 0, 10.0e6, 0); // 10 MSm3 gas in 2027
 * // ... more years
 * 
 * // Calculate cash flow
 * CashFlowResult result = engine.calculate(0.08); // 8% discount rate
 * 
 * // Results
 * System.out.println("NPV: " + result.getNpv() + " MUSD");
 * System.out.println("IRR: " + result.getIrr() * 100 + "%");
 * System.out.println("Payback: " + result.getPaybackYears() + " years");
 * }</pre>
 *
 * @author ESOL
 * @version 1.1
 * @see TaxModel
 * @see TaxModelRegistry
 * @see GenericTaxModel
 * @see NorwegianTaxModel
 */
public class CashFlowEngine implements Serializable {
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // DEFAULT CONSTANTS
  // ============================================================================

  /** Default discount rate (8%). */
  public static final double DEFAULT_DISCOUNT_RATE = 0.08;

  /** Default OPEX as percentage of CAPEX (4%). */
  public static final double DEFAULT_OPEX_PERCENT = 0.04;

  /** Days per year for conversions. */
  private static final double DAYS_PER_YEAR = 365.25;

  // ============================================================================
  // INSTANCE VARIABLES - TAX MODEL
  // ============================================================================

  /** Tax model for calculating country-specific taxes. */
  private TaxModel taxModel;

  // ============================================================================
  // INSTANCE VARIABLES - PRICES
  // ============================================================================

  /** Oil price in USD per barrel. */
  private double oilPriceUsdPerBbl = 75.0;

  /** Gas price in USD per Sm3. */
  private double gasPriceUsdPerSm3 = 0.25;

  /** NGL price in USD per barrel. */
  private double nglPriceUsdPerBbl = 40.0;

  /** Gas transport tariff in USD per Sm3. */
  private double gasTariffUsdPerSm3 = 0.02;

  /** Oil transport tariff in USD per barrel. */
  private double oilTariffUsdPerBbl = 2.0;

  // ============================================================================
  // INSTANCE VARIABLES - CAPEX
  // ============================================================================

  /** CAPEX by year (year -> amount in MUSD). */
  private Map<Integer, Double> capexByYear = new LinkedHashMap<Integer, Double>();

  /** Total CAPEX for depreciation calculation. */
  private double totalCapex = 0.0;

  // ============================================================================
  // INSTANCE VARIABLES - OPEX
  // ============================================================================

  /** OPEX as percentage of total CAPEX per year. */
  private double opexPercentOfCapex = DEFAULT_OPEX_PERCENT;

  /** Fixed OPEX per year (MUSD). */
  private double fixedOpexPerYear = 0.0;

  /** Variable OPEX per barrel of oil equivalent (USD/boe). */
  private double variableOpexPerBoe = 0.0;

  // ============================================================================
  // INSTANCE VARIABLES - PRODUCTION
  // ============================================================================

  /** Annual oil production by year (year -> barrels). */
  private Map<Integer, Double> oilProductionByYear = new LinkedHashMap<Integer, Double>();

  /** Annual gas production by year (year -> Sm3). */
  private Map<Integer, Double> gasProductionByYear = new LinkedHashMap<Integer, Double>();

  /** Annual NGL production by year (year -> barrels). */
  private Map<Integer, Double> nglProductionByYear = new LinkedHashMap<Integer, Double>();

  // ============================================================================
  // INSTANCE VARIABLES - PROJECT TIMING
  // ============================================================================

  /** First year of project (for analysis purposes). */
  private int firstYear = 0;

  /** Last year of project. */
  private int lastYear = 0;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new cash flow engine with default Norwegian tax model.
   */
  public CashFlowEngine() {
    this.taxModel = TaxModelRegistry.createModel("NO");
  }

  /**
   * Creates a new cash flow engine with specified country code.
   *
   * @param countryCode country code (e.g., "NO", "UK", "BR", "US-GOM")
   * @throws IllegalArgumentException if country code is not registered
   */
  public CashFlowEngine(String countryCode) {
    this.taxModel = TaxModelRegistry.createModel(countryCode);
  }

  /**
   * Creates a new cash flow engine with specified tax model.
   *
   * @param taxModel tax model to use for calculations
   */
  public CashFlowEngine(TaxModel taxModel) {
    this.taxModel = taxModel != null ? taxModel : TaxModelRegistry.createModel("NO");
  }

  /**
   * Creates a new cash flow engine with legacy Norwegian tax model.
   *
   * @param taxModel Norwegian tax model to use for calculations
   * @deprecated Use {@link #CashFlowEngine(TaxModel)} or {@link #CashFlowEngine(String)} instead
   */
  @Deprecated
  public CashFlowEngine(NorwegianTaxModel taxModel) {
    this.taxModel = taxModel != null ? taxModel : TaxModelRegistry.createModel("NO");
  }

  // ============================================================================
  // CAPEX METHODS
  // ============================================================================

  /**
   * Sets total CAPEX for a single year.
   *
   * @param capexMusd CAPEX in million USD
   * @param year year of expenditure
   */
  public void setCapex(double capexMusd, int year) {
    capexByYear.clear();
    capexByYear.put(year, capexMusd);
    totalCapex = capexMusd;
    updateProjectYears();
  }

  /**
   * Adds CAPEX for a specific year.
   *
   * @param capexMusd CAPEX in million USD
   * @param year year of expenditure
   */
  public void addCapex(double capexMusd, int year) {
    Double existing = capexByYear.get(year);
    if (existing != null) {
      capexByYear.put(year, existing + capexMusd);
    } else {
      capexByYear.put(year, capexMusd);
    }
    totalCapex += capexMusd;
    updateProjectYears();
  }

  /**
   * Sets CAPEX schedule from a map.
   *
   * @param capexSchedule map of year to CAPEX in MUSD
   */
  public void setCapexSchedule(Map<Integer, Double> capexSchedule) {
    this.capexByYear = new LinkedHashMap<Integer, Double>(capexSchedule);
    this.totalCapex = 0.0;
    for (Double value : capexSchedule.values()) {
      this.totalCapex += value;
    }
    updateProjectYears();
  }

  // ============================================================================
  // OPEX METHODS
  // ============================================================================

  /**
   * Sets OPEX as percentage of total CAPEX per year.
   *
   * @param percentage OPEX percentage (e.g., 0.04 for 4%)
   */
  public void setOpexPercentOfCapex(double percentage) {
    this.opexPercentOfCapex = percentage;
  }

  /**
   * Sets fixed OPEX per year.
   *
   * @param fixedOpexMusd fixed OPEX in million USD per year
   */
  public void setFixedOpexPerYear(double fixedOpexMusd) {
    this.fixedOpexPerYear = fixedOpexMusd;
  }

  /**
   * Sets variable OPEX per barrel of oil equivalent.
   *
   * @param opexPerBoe variable OPEX in USD per BOE
   */
  public void setVariableOpexPerBoe(double opexPerBoe) {
    this.variableOpexPerBoe = opexPerBoe;
  }

  // ============================================================================
  // PRICE METHODS
  // ============================================================================

  /**
   * Sets oil price.
   *
   * @param priceUsdPerBbl oil price in USD per barrel
   */
  public void setOilPrice(double priceUsdPerBbl) {
    this.oilPriceUsdPerBbl = priceUsdPerBbl;
  }

  /**
   * Sets gas price.
   *
   * @param priceUsdPerSm3 gas price in USD per Sm3
   */
  public void setGasPrice(double priceUsdPerSm3) {
    this.gasPriceUsdPerSm3 = priceUsdPerSm3;
  }

  /**
   * Sets NGL price.
   *
   * @param priceUsdPerBbl NGL price in USD per barrel
   */
  public void setNglPrice(double priceUsdPerBbl) {
    this.nglPriceUsdPerBbl = priceUsdPerBbl;
  }

  /**
   * Sets gas transport tariff.
   *
   * @param tariffUsdPerSm3 tariff in USD per Sm3
   */
  public void setGasTariff(double tariffUsdPerSm3) {
    this.gasTariffUsdPerSm3 = tariffUsdPerSm3;
  }

  /**
   * Sets oil transport tariff.
   *
   * @param tariffUsdPerBbl tariff in USD per barrel
   */
  public void setOilTariff(double tariffUsdPerBbl) {
    this.oilTariffUsdPerBbl = tariffUsdPerBbl;
  }

  // ============================================================================
  // PRODUCTION METHODS
  // ============================================================================

  /**
   * Adds annual production for a specific year.
   *
   * @param year production year
   * @param oilBbl annual oil production in barrels
   * @param gasSm3 annual gas production in Sm3
   * @param nglBbl annual NGL production in barrels
   */
  public void addAnnualProduction(int year, double oilBbl, double gasSm3, double nglBbl) {
    if (oilBbl > 0) {
      Double existing = oilProductionByYear.get(year);
      oilProductionByYear.put(year, existing != null ? existing + oilBbl : oilBbl);
    }
    if (gasSm3 > 0) {
      Double existing = gasProductionByYear.get(year);
      gasProductionByYear.put(year, existing != null ? existing + gasSm3 : gasSm3);
    }
    if (nglBbl > 0) {
      Double existing = nglProductionByYear.get(year);
      nglProductionByYear.put(year, existing != null ? existing + nglBbl : nglBbl);
    }
    updateProjectYears();
  }

  /**
   * Sets complete production profile from maps.
   *
   * @param oilByYear map of year to oil production in barrels
   * @param gasByYear map of year to gas production in Sm3
   * @param nglByYear map of year to NGL production in barrels (can be null)
   */
  public void setProductionProfile(Map<Integer, Double> oilByYear, Map<Integer, Double> gasByYear,
      Map<Integer, Double> nglByYear) {
    this.oilProductionByYear = oilByYear != null ? new LinkedHashMap<Integer, Double>(oilByYear)
        : new LinkedHashMap<Integer, Double>();
    this.gasProductionByYear = gasByYear != null ? new LinkedHashMap<Integer, Double>(gasByYear)
        : new LinkedHashMap<Integer, Double>();
    this.nglProductionByYear = nglByYear != null ? new LinkedHashMap<Integer, Double>(nglByYear)
        : new LinkedHashMap<Integer, Double>();
    updateProjectYears();
  }

  // ============================================================================
  // CALCULATION METHODS
  // ============================================================================

  /**
   * Calculates cash flow for the project.
   *
   * @param discountRate discount rate for NPV calculation (e.g., 0.08 for 8%)
   * @return cash flow result with all metrics
   */
  public CashFlowResult calculate(double discountRate) {
    if (firstYear == 0 || lastYear == 0) {
      throw new IllegalStateException("No CAPEX or production data set");
    }

    // Reset tax model loss carry-forward for new project
    taxModel.reset();

    List<AnnualCashFlow> annualCashFlows = new ArrayList<AnnualCashFlow>();
    double cumulativeCashFlow = 0.0;
    double npv = 0.0;
    int paybackYear = -1;

    for (int year = firstYear; year <= lastYear; year++) {
      int projectYear = year - firstYear + 1;

      // Get production for this year
      double oilProd = getOrDefault(oilProductionByYear, year, 0.0);
      double gasProd = getOrDefault(gasProductionByYear, year, 0.0);
      double nglProd = getOrDefault(nglProductionByYear, year, 0.0);

      // Calculate revenue (in MUSD)
      double oilRevenue = oilProd * oilPriceUsdPerBbl / 1.0e6;
      double gasRevenue = gasProd * gasPriceUsdPerSm3 / 1.0e6;
      double nglRevenue = nglProd * nglPriceUsdPerBbl / 1.0e6;
      double grossRevenue = oilRevenue + gasRevenue + nglRevenue;

      // Calculate tariffs (in MUSD)
      double oilTariffCost = oilProd * oilTariffUsdPerBbl / 1.0e6;
      double gasTariffCost = gasProd * gasTariffUsdPerSm3 / 1.0e6;
      double tariff = oilTariffCost + gasTariffCost;

      // Net revenue after tariff
      double netRevenue = grossRevenue - tariff;

      // Get CAPEX for this year
      double capex = getOrDefault(capexByYear, year, 0.0);

      // Calculate OPEX
      double boe = oilProd + nglProd + gasProd / 1000.0; // Simplified BOE conversion
      double opex =
          fixedOpexPerYear + (totalCapex * opexPercentOfCapex) + (boe * variableOpexPerBoe / 1.0e6);

      // Calculate depreciation and uplift
      double depreciation = calculateDepreciation(year);
      double uplift = calculateUplift(year);

      // Calculate taxes using country-specific tax model
      TaxModel.TaxResult taxResult = taxModel.calculateTax(netRevenue, opex, depreciation, uplift);

      // Cash flow (before tax = net revenue - opex - capex)
      double preTaxCashFlow = netRevenue - opex - capex;

      // After-tax cash flow = pre-tax cash flow - tax
      double afterTaxCashFlow = preTaxCashFlow - taxResult.getTotalTax();

      // Cumulative cash flow
      cumulativeCashFlow += afterTaxCashFlow;

      // Discounted cash flow
      double discountFactor = 1.0 / Math.pow(1.0 + discountRate, projectYear);
      double discountedCashFlow = afterTaxCashFlow * discountFactor;
      npv += discountedCashFlow;

      // Track payback
      if (paybackYear < 0 && cumulativeCashFlow >= 0) {
        paybackYear = year;
      }

      // Create annual record
      AnnualCashFlow annual = new AnnualCashFlow(year, grossRevenue, tariff, netRevenue, capex,
          opex, depreciation, uplift, taxResult.getCorporateTax(), taxResult.getPetroleumTax(),
          taxResult.getTotalTax(), preTaxCashFlow, afterTaxCashFlow, cumulativeCashFlow,
          discountedCashFlow);
      annualCashFlows.add(annual);
    }

    // Calculate IRR
    double irr = calculateIRR(annualCashFlows);

    // Calculate payback period
    double paybackYears = paybackYear > 0 ? paybackYear - firstYear + 1 : Double.NaN;

    return new CashFlowResult(annualCashFlows, npv, irr, paybackYears, discountRate, totalCapex,
        firstYear, lastYear);
  }

  /**
   * Calculates NPV at specified discount rate.
   *
   * @param discountRate discount rate (e.g., 0.08 for 8%)
   * @return NPV in MUSD
   */
  public double calculateNPV(double discountRate) {
    return calculate(discountRate).getNpv();
  }

  /**
   * Calculates breakeven oil price for zero NPV.
   *
   * @param discountRate discount rate for NPV calculation
   * @return breakeven oil price in USD/bbl
   */
  public double calculateBreakevenOilPrice(double discountRate) {
    double originalPrice = oilPriceUsdPerBbl;

    // Binary search for breakeven price
    double low = 0.0;
    double high = 200.0;
    double tolerance = 0.01;

    while (high - low > tolerance) {
      double mid = (low + high) / 2.0;
      oilPriceUsdPerBbl = mid;
      double npv = calculateNPV(discountRate);

      if (npv < 0) {
        low = mid;
      } else {
        high = mid;
      }
    }

    double breakeven = (low + high) / 2.0;
    oilPriceUsdPerBbl = originalPrice;
    return breakeven;
  }

  /**
   * Calculates breakeven gas price for zero NPV.
   *
   * @param discountRate discount rate for NPV calculation
   * @return breakeven gas price in USD/Sm3
   */
  public double calculateBreakevenGasPrice(double discountRate) {
    double originalPrice = gasPriceUsdPerSm3;

    // Binary search for breakeven price
    double low = 0.0;
    double high = 1.0;
    double tolerance = 0.001;

    while (high - low > tolerance) {
      double mid = (low + high) / 2.0;
      gasPriceUsdPerSm3 = mid;
      double npv = calculateNPV(discountRate);

      if (npv < 0) {
        low = mid;
      } else {
        high = mid;
      }
    }

    double breakeven = (low + high) / 2.0;
    gasPriceUsdPerSm3 = originalPrice;
    return breakeven;
  }

  // ============================================================================
  // PRIVATE HELPER METHODS
  // ============================================================================

  private void updateProjectYears() {
    int minYear = Integer.MAX_VALUE;
    int maxYear = Integer.MIN_VALUE;

    for (Integer year : capexByYear.keySet()) {
      minYear = Math.min(minYear, year);
      maxYear = Math.max(maxYear, year);
    }
    for (Integer year : oilProductionByYear.keySet()) {
      minYear = Math.min(minYear, year);
      maxYear = Math.max(maxYear, year);
    }
    for (Integer year : gasProductionByYear.keySet()) {
      minYear = Math.min(minYear, year);
      maxYear = Math.max(maxYear, year);
    }
    for (Integer year : nglProductionByYear.keySet()) {
      minYear = Math.min(minYear, year);
      maxYear = Math.max(maxYear, year);
    }

    if (minYear != Integer.MAX_VALUE) {
      firstYear = minYear;
    }
    if (maxYear != Integer.MIN_VALUE) {
      lastYear = maxYear;
    }
  }

  private double getOrDefault(Map<Integer, Double> map, Integer key, double defaultValue) {
    Double value = map.get(key);
    return value != null ? value : defaultValue;
  }

  private double calculateDepreciation(int year) {
    double totalDepreciation = 0.0;
    int depreciationYears = taxModel.getParameters().getDepreciationYears();

    for (Map.Entry<Integer, Double> entry : capexByYear.entrySet()) {
      int capexYear = entry.getKey();
      double capex = entry.getValue();
      int depYear = year - capexYear + 1;

      if (depYear >= 1 && depYear <= depreciationYears) {
        totalDepreciation += taxModel.calculateDepreciation(capex, depYear);
      }
    }

    return totalDepreciation;
  }

  private double calculateUplift(int year) {
    double totalUplift = 0.0;
    int upliftYears = taxModel.getParameters().getUpliftYears();

    for (Map.Entry<Integer, Double> entry : capexByYear.entrySet()) {
      int capexYear = entry.getKey();
      double capex = entry.getValue();
      int upliftYear = year - capexYear + 1;

      if (upliftYear >= 1 && upliftYear <= upliftYears) {
        totalUplift += taxModel.calculateUplift(capex, upliftYear);
      }
    }

    return totalUplift;
  }

  private double calculateIRR(List<AnnualCashFlow> cashFlows) {
    // Newton-Raphson method for IRR
    double irr = 0.1; // Initial guess
    double tolerance = 0.0001;
    int maxIterations = 100;

    for (int i = 0; i < maxIterations; i++) {
      double npv = 0.0;
      double npvDerivative = 0.0;

      for (int j = 0; j < cashFlows.size(); j++) {
        double cf = cashFlows.get(j).getAfterTaxCashFlow();
        int year = j + 1;
        double factor = Math.pow(1.0 + irr, year);
        npv += cf / factor;
        npvDerivative -= year * cf / (factor * (1.0 + irr));
      }

      if (Math.abs(npvDerivative) < 1e-10) {
        break;
      }

      double newIrr = irr - npv / npvDerivative;

      if (Math.abs(newIrr - irr) < tolerance) {
        return newIrr;
      }

      irr = newIrr;

      // Bound the IRR
      if (irr < -0.99) {
        irr = -0.99;
      }
      if (irr > 10.0) {
        irr = 10.0;
      }
    }

    return irr;
  }

  // ============================================================================
  // GETTERS AND SETTERS
  // ============================================================================

  /**
   * Gets the tax model.
   *
   * @return tax model
   */
  public TaxModel getTaxModel() {
    return taxModel;
  }

  /**
   * Sets the tax model.
   *
   * @param taxModel tax model to use
   */
  public void setTaxModel(TaxModel taxModel) {
    this.taxModel = taxModel != null ? taxModel : TaxModelRegistry.createModel("NO");
  }

  /**
   * Sets the tax model using a country code from the registry.
   *
   * @param countryCode country code (e.g., "NO", "UK", "BR", "US-GOM")
   * @throws IllegalArgumentException if country code is not registered
   */
  public void setTaxModel(String countryCode) {
    this.taxModel = TaxModelRegistry.createModel(countryCode);
  }

  /**
   * Gets the country code of the current tax model.
   *
   * @return country code
   */
  public String getCountryCode() {
    return taxModel.getCountryCode();
  }

  /**
   * Gets the country name of the current tax model.
   *
   * @return country name
   */
  public String getCountryName() {
    return taxModel.getCountryName();
  }

  /**
   * Gets total CAPEX.
   *
   * @return total CAPEX in MUSD
   */
  public double getTotalCapex() {
    return totalCapex;
  }

  // ============================================================================
  // INNER CLASS - ANNUAL CASH FLOW
  // ============================================================================

  /**
   * Represents cash flow for a single year.
   */
  public static final class AnnualCashFlow implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final int year;
    private final double grossRevenue;
    private final double tariff;
    private final double netRevenue;
    private final double capex;
    private final double opex;
    private final double depreciation;
    private final double uplift;
    private final double corporateTax;
    private final double petroleumTax;
    private final double totalTax;
    private final double preTaxCashFlow;
    private final double afterTaxCashFlow;
    private final double cumulativeCashFlow;
    private final double discountedCashFlow;

    /**
     * Creates a new annual cash flow record.
     *
     * @param year the year
     * @param grossRevenue gross revenue (MUSD)
     * @param tariff tariff costs (MUSD)
     * @param netRevenue net revenue after tariff (MUSD)
     * @param capex capital expenditure (MUSD)
     * @param opex operating expenditure (MUSD)
     * @param depreciation depreciation deduction (MUSD)
     * @param uplift uplift deduction (MUSD)
     * @param corporateTax corporate tax (MUSD)
     * @param petroleumTax petroleum tax (MUSD)
     * @param totalTax total tax (MUSD)
     * @param preTaxCashFlow pre-tax cash flow (MUSD)
     * @param afterTaxCashFlow after-tax cash flow (MUSD)
     * @param cumulativeCashFlow cumulative cash flow (MUSD)
     * @param discountedCashFlow discounted cash flow (MUSD)
     */
    public AnnualCashFlow(int year, double grossRevenue, double tariff, double netRevenue,
        double capex, double opex, double depreciation, double uplift, double corporateTax,
        double petroleumTax, double totalTax, double preTaxCashFlow, double afterTaxCashFlow,
        double cumulativeCashFlow, double discountedCashFlow) {
      this.year = year;
      this.grossRevenue = grossRevenue;
      this.tariff = tariff;
      this.netRevenue = netRevenue;
      this.capex = capex;
      this.opex = opex;
      this.depreciation = depreciation;
      this.uplift = uplift;
      this.corporateTax = corporateTax;
      this.petroleumTax = petroleumTax;
      this.totalTax = totalTax;
      this.preTaxCashFlow = preTaxCashFlow;
      this.afterTaxCashFlow = afterTaxCashFlow;
      this.cumulativeCashFlow = cumulativeCashFlow;
      this.discountedCashFlow = discountedCashFlow;
    }

    public int getYear() {
      return year;
    }

    public double getGrossRevenue() {
      return grossRevenue;
    }

    public double getTariff() {
      return tariff;
    }

    public double getNetRevenue() {
      return netRevenue;
    }

    public double getCapex() {
      return capex;
    }

    public double getOpex() {
      return opex;
    }

    public double getDepreciation() {
      return depreciation;
    }

    public double getUplift() {
      return uplift;
    }

    public double getCorporateTax() {
      return corporateTax;
    }

    public double getPetroleumTax() {
      return petroleumTax;
    }

    public double getTotalTax() {
      return totalTax;
    }

    public double getPreTaxCashFlow() {
      return preTaxCashFlow;
    }

    public double getAfterTaxCashFlow() {
      return afterTaxCashFlow;
    }

    public double getCumulativeCashFlow() {
      return cumulativeCashFlow;
    }

    public double getDiscountedCashFlow() {
      return discountedCashFlow;
    }
  }

  // ============================================================================
  // INNER CLASS - CASH FLOW RESULT
  // ============================================================================

  /**
   * Complete result of cash flow calculation.
   */
  public static final class CashFlowResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<AnnualCashFlow> annualCashFlows;
    private final double npv;
    private final double irr;
    private final double paybackYears;
    private final double discountRate;
    private final double totalCapex;
    private final int firstYear;
    private final int lastYear;

    /**
     * Creates a new cash flow result.
     *
     * @param annualCashFlows list of annual cash flows
     * @param npv net present value (MUSD)
     * @param irr internal rate of return
     * @param paybackYears payback period in years
     * @param discountRate discount rate used
     * @param totalCapex total capital expenditure (MUSD)
     * @param firstYear first year of project
     * @param lastYear last year of project
     */
    public CashFlowResult(List<AnnualCashFlow> annualCashFlows, double npv, double irr,
        double paybackYears, double discountRate, double totalCapex, int firstYear, int lastYear) {
      this.annualCashFlows =
          Collections.unmodifiableList(new ArrayList<AnnualCashFlow>(annualCashFlows));
      this.npv = npv;
      this.irr = irr;
      this.paybackYears = paybackYears;
      this.discountRate = discountRate;
      this.totalCapex = totalCapex;
      this.firstYear = firstYear;
      this.lastYear = lastYear;
    }

    /**
     * Gets all annual cash flows.
     *
     * @return unmodifiable list of annual cash flows
     */
    public List<AnnualCashFlow> getAnnualCashFlows() {
      return annualCashFlows;
    }

    /**
     * Gets the NPV.
     *
     * @return NPV in MUSD
     */
    public double getNpv() {
      return npv;
    }

    /**
     * Gets the IRR.
     *
     * @return internal rate of return (e.g., 0.15 for 15%)
     */
    public double getIrr() {
      return irr;
    }

    /**
     * Gets the payback period.
     *
     * @return payback period in years
     */
    public double getPaybackYears() {
      return paybackYears;
    }

    /**
     * Gets the discount rate used.
     *
     * @return discount rate
     */
    public double getDiscountRate() {
      return discountRate;
    }

    /**
     * Gets total CAPEX.
     *
     * @return total CAPEX in MUSD
     */
    public double getTotalCapex() {
      return totalCapex;
    }

    /**
     * Gets total revenue over project life.
     *
     * @return total revenue in MUSD
     */
    public double getTotalRevenue() {
      double total = 0.0;
      for (AnnualCashFlow cf : annualCashFlows) {
        total += cf.getGrossRevenue();
      }
      return total;
    }

    /**
     * Gets total tax paid over project life.
     *
     * @return total tax in MUSD
     */
    public double getTotalTax() {
      double total = 0.0;
      for (AnnualCashFlow cf : annualCashFlows) {
        total += cf.getTotalTax();
      }
      return total;
    }

    /**
     * Gets project duration.
     *
     * @return project duration in years
     */
    public int getProjectDuration() {
      return lastYear - firstYear + 1;
    }

    /**
     * Generates a summary string.
     *
     * @return formatted summary
     */
    public String getSummary() {
      StringBuilder sb = new StringBuilder();
      sb.append("=== Cash Flow Summary ===\n");
      sb.append(String.format("Project period: %d - %d (%d years)\n", firstYear, lastYear,
          getProjectDuration()));
      sb.append(String.format("Total CAPEX: %.1f MUSD\n", totalCapex));
      sb.append(String.format("Total Revenue: %.1f MUSD\n", getTotalRevenue()));
      sb.append(String.format("Total Tax: %.1f MUSD\n", getTotalTax()));
      sb.append(String.format("NPV @ %.1f%%: %.1f MUSD\n", discountRate * 100, npv));
      sb.append(String.format("IRR: %.1f%%\n", irr * 100));
      if (!Double.isNaN(paybackYears)) {
        sb.append(String.format("Payback: %.1f years\n", paybackYears));
      }
      return sb.toString();
    }

    /**
     * Generates markdown table of annual cash flows.
     *
     * @return markdown formatted table
     */
    public String toMarkdownTable() {
      StringBuilder sb = new StringBuilder();
      sb.append("| Year | Revenue | CAPEX | OPEX | Tax | Cash Flow | Cumulative | DCF |\n");
      sb.append("|------|---------|-------|------|-----|-----------|------------|-----|\n");

      for (AnnualCashFlow cf : annualCashFlows) {
        sb.append(String.format("| %d | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f |\n",
            cf.getYear(), cf.getGrossRevenue(), cf.getCapex(), cf.getOpex(), cf.getTotalTax(),
            cf.getAfterTaxCashFlow(), cf.getCumulativeCashFlow(), cf.getDiscountedCashFlow()));
      }

      return sb.toString();
    }

    @Override
    public String toString() {
      return getSummary();
    }
  }
}
