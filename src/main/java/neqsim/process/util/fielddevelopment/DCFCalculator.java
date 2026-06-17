package neqsim.process.util.fielddevelopment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Discounted Cash Flow (DCF) calculator for field development economics.
 *
 * <p>
 * Calculates NPV, IRR, payback period, and generates cash flow profiles for oil and gas field
 * development projects. Supports CAPEX scheduling, production-linked revenue, OPEX, royalties, and
 * tax with loss carry-forward.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * DCFCalculator dcf = new DCFCalculator();
 * dcf.setDiscountRate(0.08);
 * dcf.setProjectLifeYears(20);
 * dcf.setTaxRate(0.22);
 * dcf.setRoyaltyRate(0.0);
 * dcf.addCapex(0, 500e6);   // Year 0 investment
 * dcf.addCapex(1, 300e6);   // Year 1 investment
 * dcf.setAnnualProduction(new double[]{0, 0, 10e6, 15e6, 15e6, 12e6, ...}); // Sm3/yr
 * dcf.setProductPrice(1.5); // NOK/Sm3
 * dcf.setAnnualOpex(50e6);  // NOK/yr
 * dcf.calculate();
 * double npv = dcf.getNPV();
 * double irr = dcf.getIRR();
 * int payback = dcf.getPaybackYear();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class DCFCalculator implements Serializable {
  private static final long serialVersionUID = 1000L;

  private double discountRate = 0.10;
  private int projectLifeYears = 25;
  private double taxRate = 0.22;
  private double royaltyRate = 0.0;
  private double deprecationYears = 6.0;
  private double inflationRate = 0.0;

  private double[] capexByYear;
  private double[] annualProduction;
  private double productPrice = 1.0;
  private double annualOpex = 0.0;
  private double[] opexByYear;

  // Results
  private double npv = Double.NaN;
  private double irr = Double.NaN;
  private int paybackYear = -1;
  private double profitabilityIndex = Double.NaN;
  private double[] annualCashFlow;
  private double[] cumulativeCashFlow;
  private double[] discountedCashFlow;
  private boolean calculated = false;

  /**
   * Creates a new DCF calculator with default parameters.
   */
  public DCFCalculator() {
    this.capexByYear = new double[0];
    this.annualProduction = new double[0];
    this.opexByYear = new double[0];
  }

  /**
   * Sets the discount rate for NPV calculation.
   *
   * @param rate discount rate as fraction (e.g. 0.10 for 10%)
   */
  public void setDiscountRate(double rate) {
    this.discountRate = rate;
    this.calculated = false;
  }

  /**
   * Gets the discount rate.
   *
   * @return discount rate as fraction
   */
  public double getDiscountRate() {
    return discountRate;
  }

  /**
   * Sets the project life in years.
   *
   * @param years number of years
   */
  public void setProjectLifeYears(int years) {
    this.projectLifeYears = years;
    this.calculated = false;
  }

  /**
   * Sets the corporate tax rate.
   *
   * @param rate tax rate as fraction (e.g. 0.22 for 22%)
   */
  public void setTaxRate(double rate) {
    this.taxRate = rate;
    this.calculated = false;
  }

  /**
   * Sets the royalty rate.
   *
   * @param rate royalty rate as fraction (e.g. 0.12 for 12%)
   */
  public void setRoyaltyRate(double rate) {
    this.royaltyRate = rate;
    this.calculated = false;
  }

  /**
   * Sets the depreciation period in years (straight-line).
   *
   * @param years depreciation period
   */
  public void setDepreciationYears(double years) {
    this.deprecationYears = years;
    this.calculated = false;
  }

  /**
   * Sets the annual inflation rate for OPEX escalation.
   *
   * @param rate inflation rate as fraction (e.g. 0.02 for 2%)
   */
  public void setInflationRate(double rate) {
    this.inflationRate = rate;
    this.calculated = false;
  }

  /**
   * Adds CAPEX investment for a specific year.
   *
   * @param year the year (0-based)
   * @param amount CAPEX amount in currency units
   */
  public void addCapex(int year, double amount) {
    if (year >= capexByYear.length) {
      capexByYear = Arrays.copyOf(capexByYear, year + 1);
    }
    capexByYear[year] += amount;
    calculated = false;
  }

  /**
   * Sets the complete CAPEX schedule by year.
   *
   * @param capex array of CAPEX by year
   */
  public void setCapexByYear(double[] capex) {
    this.capexByYear = Arrays.copyOf(capex, capex.length);
    this.calculated = false;
  }

  /**
   * Sets the annual production profile.
   *
   * @param production array of annual production volumes (same unit as price)
   */
  public void setAnnualProduction(double[] production) {
    this.annualProduction = Arrays.copyOf(production, production.length);
    this.calculated = false;
  }

  /**
   * Sets the product price per unit volume.
   *
   * @param price price per unit of production
   */
  public void setProductPrice(double price) {
    this.productPrice = price;
    this.calculated = false;
  }

  /**
   * Sets a constant annual OPEX (used if opexByYear is not specified).
   *
   * @param opex annual operating expenditure
   */
  public void setAnnualOpex(double opex) {
    this.annualOpex = opex;
    this.calculated = false;
  }

  /**
   * Sets variable OPEX by year.
   *
   * @param opex array of OPEX by year
   */
  public void setOpexByYear(double[] opex) {
    this.opexByYear = Arrays.copyOf(opex, opex.length);
    this.calculated = false;
  }

  /**
   * Runs the DCF calculation.
   */
  public void calculate() {
    annualCashFlow = new double[projectLifeYears];
    cumulativeCashFlow = new double[projectLifeYears];
    discountedCashFlow = new double[projectLifeYears];

    double totalCapex = 0.0;
    for (double c : capexByYear) {
      totalCapex += c;
    }
    double annualDepreciation = deprecationYears > 0 ? totalCapex / deprecationYears : 0.0;

    double lossCarryForward = 0.0;
    npv = 0.0;
    paybackYear = -1;
    double cumulative = 0.0;

    for (int year = 0; year < projectLifeYears; year++) {
      double capex = year < capexByYear.length ? capexByYear[year] : 0.0;
      double production = year < annualProduction.length ? annualProduction[year] : 0.0;
      double inflationFactor = Math.pow(1.0 + inflationRate, year);

      double revenue = production * productPrice;
      double royalty = revenue * royaltyRate;
      double revenueAfterRoyalty = revenue - royalty;

      double opex;
      if (opexByYear.length > 0 && year < opexByYear.length) {
        opex = opexByYear[year] * inflationFactor;
      } else {
        opex = (production > 0) ? annualOpex * inflationFactor : 0.0;
      }

      double depreciation =
          (year < deprecationYears && deprecationYears > 0) ? annualDepreciation : 0.0;

      // Taxable income
      double taxableIncome = revenueAfterRoyalty - opex - depreciation;

      // Apply loss carry-forward
      if (taxableIncome > 0 && lossCarryForward > 0) {
        double deduction = Math.min(taxableIncome, lossCarryForward);
        taxableIncome -= deduction;
        lossCarryForward -= deduction;
      } else if (taxableIncome < 0) {
        lossCarryForward += Math.abs(taxableIncome);
        taxableIncome = 0.0;
      }

      double tax = taxableIncome * taxRate;

      // After-tax cash flow = Revenue - Royalty - OPEX - Tax - CAPEX
      double cashFlow = revenueAfterRoyalty - opex - tax - capex;
      annualCashFlow[year] = cashFlow;

      cumulative += cashFlow;
      cumulativeCashFlow[year] = cumulative;

      double discountFactor = 1.0 / Math.pow(1.0 + discountRate, year);
      discountedCashFlow[year] = cashFlow * discountFactor;
      npv += discountedCashFlow[year];

      if (paybackYear == -1 && cumulative >= 0 && year > 0) {
        paybackYear = year;
      }
    }

    // Calculate IRR using bisection
    irr = calculateIRR();

    // Profitability index
    double discountedCapex = 0.0;
    for (int y = 0; y < capexByYear.length && y < projectLifeYears; y++) {
      discountedCapex += capexByYear[y] / Math.pow(1.0 + discountRate, y);
    }
    profitabilityIndex = discountedCapex > 0 ? (npv + discountedCapex) / discountedCapex : 0.0;

    calculated = true;
  }

  /**
   * Calculates Internal Rate of Return using bisection method.
   *
   * @return IRR as fraction, or NaN if no solution found
   */
  private double calculateIRR() {
    double low = -0.5;
    double high = 5.0;
    double tolerance = 1e-8;
    int maxIterations = 200;

    double npvLow = calculateNPVAtRate(low);
    double npvHigh = calculateNPVAtRate(high);

    if (npvLow * npvHigh > 0) {
      return Double.NaN;
    }

    for (int i = 0; i < maxIterations; i++) {
      double mid = (low + high) / 2.0;
      double npvMid = calculateNPVAtRate(mid);

      if (Math.abs(npvMid) < tolerance || (high - low) < tolerance) {
        return mid;
      }

      if (npvMid * npvLow < 0) {
        high = mid;
      } else {
        low = mid;
        npvLow = npvMid;
      }
    }
    return (low + high) / 2.0;
  }

  /**
   * Calculates NPV at a specific discount rate.
   *
   * @param rate discount rate
   * @return NPV at the given rate
   */
  private double calculateNPVAtRate(double rate) {
    double result = 0.0;
    for (int y = 0; y < annualCashFlow.length; y++) {
      result += annualCashFlow[y] / Math.pow(1.0 + rate, y);
    }
    return result;
  }

  /**
   * Gets the Net Present Value.
   *
   * @return NPV in currency units
   */
  public double getNPV() {
    if (!calculated) {
      calculate();
    }
    return npv;
  }

  /**
   * Gets the Internal Rate of Return.
   *
   * @return IRR as fraction
   */
  public double getIRR() {
    if (!calculated) {
      calculate();
    }
    return irr;
  }

  /**
   * Gets the payback year (first year cumulative cash flow becomes positive).
   *
   * @return payback year (0-based), or -1 if project never pays back
   */
  public int getPaybackYear() {
    if (!calculated) {
      calculate();
    }
    return paybackYear;
  }

  /**
   * Gets the profitability index (benefit-cost ratio).
   *
   * @return profitability index
   */
  public double getProfitabilityIndex() {
    if (!calculated) {
      calculate();
    }
    return profitabilityIndex;
  }

  /**
   * Gets the annual cash flow array.
   *
   * @return array of after-tax cash flows by year
   */
  public double[] getAnnualCashFlow() {
    if (!calculated) {
      calculate();
    }
    return Arrays.copyOf(annualCashFlow, annualCashFlow.length);
  }

  /**
   * Gets the cumulative cash flow array.
   *
   * @return array of cumulative cash flows by year
   */
  public double[] getCumulativeCashFlow() {
    if (!calculated) {
      calculate();
    }
    return Arrays.copyOf(cumulativeCashFlow, cumulativeCashFlow.length);
  }

  /**
   * Gets the discounted cash flow array.
   *
   * @return array of discounted cash flows by year
   */
  public double[] getDiscountedCashFlow() {
    if (!calculated) {
      calculate();
    }
    return Arrays.copyOf(discountedCashFlow, discountedCashFlow.length);
  }

  /**
   * Returns the results as a JSON string.
   *
   * @return JSON representation of DCF analysis results
   */
  public String toJson() {
    if (!calculated) {
      calculate();
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("analysisType", "Discounted Cash Flow Analysis");

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("discountRate", discountRate);
    params.put("projectLifeYears", projectLifeYears);
    params.put("taxRate", taxRate);
    params.put("royaltyRate", royaltyRate);
    params.put("depreciationYears", deprecationYears);
    params.put("inflationRate", inflationRate);
    params.put("productPrice", productPrice);
    result.put("parameters", params);

    Map<String, Object> results = new LinkedHashMap<>();
    results.put("NPV", npv);
    results.put("IRR", irr);
    results.put("paybackYear", paybackYear);
    results.put("profitabilityIndex", profitabilityIndex);
    result.put("results", results);

    List<Map<String, Object>> cashFlowTable = new ArrayList<>();
    for (int y = 0; y < projectLifeYears; y++) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("year", y);
      row.put("capex", y < capexByYear.length ? capexByYear[y] : 0.0);
      row.put("production", y < annualProduction.length ? annualProduction[y] : 0.0);
      row.put("cashFlow", annualCashFlow[y]);
      row.put("cumulativeCashFlow", cumulativeCashFlow[y]);
      row.put("discountedCashFlow", discountedCashFlow[y]);
      cashFlowTable.add(row);
    }
    result.put("cashFlowTable", cashFlowTable);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(result);
  }
}
