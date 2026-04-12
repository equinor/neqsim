package neqsim.process.util.fielddevelopment;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Techno-economic analysis (TEA) cost estimator for biorefinery plants.
 *
 * <p>
 * Provides CAPEX and OPEX estimation for biorefinery equipment using capacity-based scaling
 * correlations (six-tenths rule) and technology-specific installation factors. Integrates with
 * {@link DCFCalculator} for full NPV/IRR analysis.
 * </p>
 *
 * <h2>Supported Equipment</h2>
 *
 * <table>
 * <caption>Base cost references for biorefinery equipment</caption>
 * <tr>
 * <th>Equipment</th>
 * <th>Base Size</th>
 * <th>Base Cost (kUSD)</th>
 * <th>Scaling Exponent</th>
 * </tr>
 * <tr>
 * <td>Anaerobic Digester</td>
 * <td>5000 m3</td>
 * <td>2500</td>
 * <td>0.60</td>
 * </tr>
 * <tr>
 * <td>Biogas Upgrader</td>
 * <td>500 Nm3/hr</td>
 * <td>1200</td>
 * <td>0.65</td>
 * </tr>
 * <tr>
 * <td>Biomass Gasifier</td>
 * <td>1000 kg/hr</td>
 * <td>3500</td>
 * <td>0.70</td>
 * </tr>
 * <tr>
 * <td>Pyrolysis Reactor</td>
 * <td>500 kg/hr</td>
 * <td>2800</td>
 * <td>0.65</td>
 * </tr>
 * <tr>
 * <td>Biomass Dryer</td>
 * <td>5000 kg/hr</td>
 * <td>600</td>
 * <td>0.55</td>
 * </tr>
 * <tr>
 * <td>Gas Cleanup</td>
 * <td>1000 Nm3/hr</td>
 * <td>800</td>
 * <td>0.60</td>
 * </tr>
 * </table>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * BiorefineryCostEstimator estimator = new BiorefineryCostEstimator();
 * estimator.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.ANAEROBIC_DIGESTER, 8000.0);
 * estimator.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.BIOGAS_UPGRADER, 750.0);
 * estimator.setBiomassPrice(30.0); // USD/tonne
 * estimator.setBiomassConsumptionTonnesPerYear(50000.0);
 * estimator.setProductPrice(0.80); // USD/Nm3 biomethane
 * estimator.setAnnualProductionNm3(4.0e6);
 * estimator.calculate();
 *
 * double capex = estimator.getTotalCapexUSD();
 * double opex = estimator.getAnnualOpexUSD();
 * double lcoe = estimator.getLCOE();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class BiorefineryCostEstimator implements Serializable {
  private static final long serialVersionUID = 1001L;

  /**
   * Biorefinery equipment types with base cost data for capacity-based scaling.
   */
  public enum BiorefineryEquipment {
    /** Anaerobic digester vessel. Base: 5000 m3. */
    ANAEROBIC_DIGESTER("Anaerobic Digester", 5000.0, "m3", 2500e3, 0.60, 2.5),
    /** Biogas upgrading unit. Base: 500 Nm3/hr raw biogas. */
    BIOGAS_UPGRADER("Biogas Upgrader", 500.0, "Nm3/hr", 1200e3, 0.65, 1.8),
    /** Biomass gasifier. Base: 1000 kg/hr dry biomass. */
    BIOMASS_GASIFIER("Biomass Gasifier", 1000.0, "kg/hr", 3500e3, 0.70, 2.8),
    /** Pyrolysis reactor. Base: 500 kg/hr dry biomass. */
    PYROLYSIS_REACTOR("Pyrolysis Reactor", 500.0, "kg/hr", 2800e3, 0.65, 2.5),
    /** Biomass dryer. Base: 5000 kg/hr wet biomass. */
    BIOMASS_DRYER("Biomass Dryer", 5000.0, "kg/hr", 600e3, 0.55, 1.6),
    /** Syngas cleanup system. Base: 1000 Nm3/hr. */
    GAS_CLEANUP("Gas Cleanup", 1000.0, "Nm3/hr", 800e3, 0.60, 2.0),
    /** CHP (combined heat and power) engine. Base: 1000 kW electrical. */
    CHP_ENGINE("CHP Engine", 1000.0, "kW", 900e3, 0.75, 1.5),
    /** Feedstock storage and handling. Base: 10000 tonnes/yr throughput. */
    FEEDSTOCK_HANDLING("Feedstock Handling", 10000.0, "tonnes/yr", 400e3, 0.50, 1.4);

    /** Display name. */
    private final String displayName;
    /** Base capacity for cost reference. */
    private final double baseCapacity;
    /** Unit of the base capacity. */
    private final String capacityUnit;
    /** Base purchased equipment cost in USD at base capacity. */
    private final double baseCostUSD;
    /** Cost scaling exponent (six-tenths rule variant). */
    private final double scalingExponent;
    /** Installation factor (total installed cost / purchased equipment cost). */
    private final double installationFactor;

    /**
     * Creates a biorefinery equipment constant.
     *
     * @param name display name
     * @param baseCapacity base capacity value
     * @param unit capacity unit
     * @param baseCost base cost in USD
     * @param exponent scaling exponent
     * @param instFactor installation factor
     */
    BiorefineryEquipment(String name, double baseCapacity, String unit, double baseCost,
        double exponent, double instFactor) {
      this.displayName = name;
      this.baseCapacity = baseCapacity;
      this.capacityUnit = unit;
      this.baseCostUSD = baseCost;
      this.scalingExponent = exponent;
      this.installationFactor = instFactor;
    }

    /**
     * Returns the display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Returns the base capacity.
     *
     * @return base capacity
     */
    public double getBaseCapacity() {
      return baseCapacity;
    }

    /**
     * Returns the capacity unit.
     *
     * @return capacity unit string
     */
    public String getCapacityUnit() {
      return capacityUnit;
    }

    /**
     * Returns the base purchased equipment cost in USD.
     *
     * @return base cost in USD
     */
    public double getBaseCostUSD() {
      return baseCostUSD;
    }

    /**
     * Returns the cost scaling exponent.
     *
     * @return scaling exponent
     */
    public double getScalingExponent() {
      return scalingExponent;
    }

    /**
     * Returns the installation factor.
     *
     * @return installation factor
     */
    public double getInstallationFactor() {
      return installationFactor;
    }
  }

  // ── Equipment items ──
  /** Equipment type array. */
  private BiorefineryEquipment[] equipmentTypes;
  /** Actual capacity for each equipment item. */
  private double[] equipmentCapacities;
  /** Number of equipment items added. */
  private int equipmentCount = 0;
  /** Maximum number of equipment items. */
  private static final int MAX_EQUIPMENT = 50;

  // ── Feedstock cost parameters ──
  /** Biomass feedstock price in USD per tonne. */
  private double biomassPriceUSDPerTonne = 30.0;
  /** Annual biomass consumption in tonnes. */
  private double biomassConsumptionTonnesPerYear = 0.0;

  // ── Product revenue parameters ──
  /** Product price per unit (e.g. USD/Nm3 for gas, USD/kg for bio-oil). */
  private double productPrice = 0.0;
  /** Annual product output in product units. */
  private double annualProduction = 0.0;
  /** Product unit description (for display). */
  private String productUnit = "unit";

  // ── Operating cost parameters ──
  /** Labour cost as fraction of CAPEX per year. */
  private double labourCostFraction = 0.03;
  /** Maintenance cost as fraction of CAPEX per year. */
  private double maintenanceCostFraction = 0.025;
  /** Insurance and overhead fraction of CAPEX per year. */
  private double insuranceFraction = 0.015;
  /** Utility cost rate in USD/kWh. */
  private double utilityCostUSDPerKWh = 0.08;
  /** Annual utility consumption in kWh. */
  private double annualUtilityConsumptionKWh = 0.0;

  // ── Project parameters ──
  /** Location factor (1.0 = US Gulf Coast baseline). */
  private double locationFactor = 1.0;
  /** Cost year escalation index (1.0 = base year). */
  private double costEscalationIndex = 1.0;
  /** Project contingency as fraction of installed equipment cost. */
  private double contingencyFraction = 0.15;

  // ── Results ──
  /** Total purchased equipment cost in USD. */
  private double totalPurchasedEquipmentCostUSD = 0.0;
  /** Total installed equipment cost in USD. */
  private double totalInstalledCostUSD = 0.0;
  /** Total CAPEX (installed + contingency) in USD. */
  private double totalCapexUSD = 0.0;
  /** Annual feedstock cost in USD. */
  private double annualFeedstockCostUSD = 0.0;
  /** Annual labour cost in USD. */
  private double annualLabourCostUSD = 0.0;
  /** Annual maintenance cost in USD. */
  private double annualMaintenanceCostUSD = 0.0;
  /** Annual utility cost in USD. */
  private double annualUtilityCostUSD = 0.0;
  /** Total annual OPEX in USD. */
  private double annualOpexUSD = 0.0;
  /** Annual revenue in USD. */
  private double annualRevenueUSD = 0.0;
  /** Levelised cost of energy/product (LCOE/LCOP). */
  private double lcoe = Double.NaN;
  /** Whether the estimation has been calculated. */
  private boolean calculated = false;

  /**
   * Creates a new biorefinery cost estimator.
   */
  public BiorefineryCostEstimator() {
    this.equipmentTypes = new BiorefineryEquipment[MAX_EQUIPMENT];
    this.equipmentCapacities = new double[MAX_EQUIPMENT];
  }

  /**
   * Adds an equipment item with its actual capacity.
   *
   * @param equipment the equipment type
   * @param actualCapacity the actual capacity in the equipment's native units
   */
  public void addEquipment(BiorefineryEquipment equipment, double actualCapacity) {
    if (equipmentCount >= MAX_EQUIPMENT) {
      throw new IllegalStateException("Maximum " + MAX_EQUIPMENT + " equipment items exceeded");
    }
    equipmentTypes[equipmentCount] = equipment;
    equipmentCapacities[equipmentCount] = actualCapacity;
    equipmentCount++;
    calculated = false;
  }

  /**
   * Sets the biomass feedstock price.
   *
   * @param priceUSDPerTonne price in USD per tonne of biomass
   */
  public void setBiomassPrice(double priceUSDPerTonne) {
    this.biomassPriceUSDPerTonne = priceUSDPerTonne;
    calculated = false;
  }

  /**
   * Gets the biomass price.
   *
   * @return biomass price in USD/tonne
   */
  public double getBiomassPrice() {
    return biomassPriceUSDPerTonne;
  }

  /**
   * Sets the annual biomass consumption.
   *
   * @param tonnes annual consumption in tonnes
   */
  public void setBiomassConsumptionTonnesPerYear(double tonnes) {
    this.biomassConsumptionTonnesPerYear = tonnes;
    calculated = false;
  }

  /**
   * Sets the product price and annual production for revenue calculation.
   *
   * @param price product price per unit
   * @param annualProd annual production in product units
   * @param unit product unit description (e.g. "Nm3", "kg", "MWh")
   */
  public void setProduct(double price, double annualProd, String unit) {
    this.productPrice = price;
    this.annualProduction = annualProd;
    this.productUnit = unit;
    calculated = false;
  }

  /**
   * Sets the product price per unit.
   *
   * @param price product price per unit
   */
  public void setProductPrice(double price) {
    this.productPrice = price;
    calculated = false;
  }

  /**
   * Sets the annual production volume.
   *
   * @param production annual production volume
   */
  public void setAnnualProductionNm3(double production) {
    this.annualProduction = production;
    this.productUnit = "Nm3";
    calculated = false;
  }

  /**
   * Sets the labour cost fraction of CAPEX per year.
   *
   * @param fraction labour cost fraction (e.g. 0.03 for 3%)
   */
  public void setLabourCostFraction(double fraction) {
    this.labourCostFraction = fraction;
    calculated = false;
  }

  /**
   * Sets the maintenance cost fraction of CAPEX per year.
   *
   * @param fraction maintenance cost fraction (e.g. 0.025 for 2.5%)
   */
  public void setMaintenanceCostFraction(double fraction) {
    this.maintenanceCostFraction = fraction;
    calculated = false;
  }

  /**
   * Sets the insurance and overhead cost fraction.
   *
   * @param fraction insurance fraction (e.g. 0.015 for 1.5%)
   */
  public void setInsuranceFraction(double fraction) {
    this.insuranceFraction = fraction;
    calculated = false;
  }

  /**
   * Sets the utility cost parameters.
   *
   * @param pricePerKWh utility price in USD/kWh
   * @param annualKWh annual utility consumption in kWh
   */
  public void setUtilityCost(double pricePerKWh, double annualKWh) {
    this.utilityCostUSDPerKWh = pricePerKWh;
    this.annualUtilityConsumptionKWh = annualKWh;
    calculated = false;
  }

  /**
   * Sets the location factor for regional cost adjustment.
   *
   * @param factor location factor (1.0 = US Gulf Coast baseline)
   */
  public void setLocationFactor(double factor) {
    this.locationFactor = factor;
    calculated = false;
  }

  /**
   * Sets the cost escalation index for year adjustment.
   *
   * @param index escalation index (1.0 = base year)
   */
  public void setCostEscalationIndex(double index) {
    this.costEscalationIndex = index;
    calculated = false;
  }

  /**
   * Sets the project contingency fraction.
   *
   * @param fraction contingency as fraction of installed cost (e.g. 0.15 for 15%)
   */
  public void setContingencyFraction(double fraction) {
    this.contingencyFraction = fraction;
    calculated = false;
  }

  /**
   * Calculates the scaled purchased equipment cost for a single item.
   *
   * <p>
   * Uses the power-law scaling correlation: Cost = BaseCost * (Capacity / BaseCapacity)^n
   * </p>
   *
   * @param equipment the equipment type
   * @param actualCapacity actual capacity
   * @return purchased equipment cost in USD
   */
  public double calculateEquipmentCost(BiorefineryEquipment equipment, double actualCapacity) {
    if (actualCapacity <= 0.0) {
      return 0.0;
    }
    double ratio = actualCapacity / equipment.getBaseCapacity();
    double scaledCost =
        equipment.getBaseCostUSD() * Math.pow(ratio, equipment.getScalingExponent());
    return scaledCost * locationFactor * costEscalationIndex;
  }

  /**
   * Runs the cost estimation calculation.
   */
  public void calculate() {
    // ── CAPEX ──
    totalPurchasedEquipmentCostUSD = 0.0;
    totalInstalledCostUSD = 0.0;
    for (int i = 0; i < equipmentCount; i++) {
      double pec = calculateEquipmentCost(equipmentTypes[i], equipmentCapacities[i]);
      totalPurchasedEquipmentCostUSD += pec;
      totalInstalledCostUSD += pec * equipmentTypes[i].getInstallationFactor();
    }
    totalCapexUSD = totalInstalledCostUSD * (1.0 + contingencyFraction);

    // ── OPEX ──
    annualFeedstockCostUSD = biomassConsumptionTonnesPerYear * biomassPriceUSDPerTonne;
    annualLabourCostUSD = totalCapexUSD * labourCostFraction;
    annualMaintenanceCostUSD = totalCapexUSD * maintenanceCostFraction;
    annualUtilityCostUSD = annualUtilityConsumptionKWh * utilityCostUSDPerKWh;
    double insuranceCostUSD = totalCapexUSD * insuranceFraction;

    annualOpexUSD = annualFeedstockCostUSD + annualLabourCostUSD + annualMaintenanceCostUSD
        + annualUtilityCostUSD + insuranceCostUSD;

    // ── Revenue ──
    annualRevenueUSD = annualProduction * productPrice;

    // ── LCOE (levelised cost) ──
    // Simplified: LCOE = (annualised CAPEX + annual OPEX) / annual production
    // Using capital recovery factor with 10% discount over 20 years
    double crf = 0.10 * Math.pow(1.10, 20) / (Math.pow(1.10, 20) - 1.0);
    double annualisedCapex = totalCapexUSD * crf;
    lcoe = annualProduction > 0 ? (annualisedCapex + annualOpexUSD) / annualProduction : Double.NaN;

    calculated = true;
  }

  /**
   * Creates a configured DCFCalculator from this cost estimate for full NPV/IRR analysis.
   *
   * @param projectLifeYears project life in years
   * @param discountRate discount rate (e.g. 0.08)
   * @return configured DCFCalculator ready to calculate
   */
  public DCFCalculator toDCFCalculator(int projectLifeYears, double discountRate) {
    if (!calculated) {
      calculate();
    }
    DCFCalculator dcf = new DCFCalculator();
    dcf.setDiscountRate(discountRate);
    dcf.setProjectLifeYears(projectLifeYears);
    dcf.addCapex(0, totalCapexUSD);

    double[] production = new double[projectLifeYears];
    // Ramp-up: 50% year 1, 80% year 2, 100% from year 3
    if (projectLifeYears > 1) {
      production[1] = annualProduction * 0.50;
    }
    if (projectLifeYears > 2) {
      production[2] = annualProduction * 0.80;
    }
    for (int y = 3; y < projectLifeYears; y++) {
      production[y] = annualProduction;
    }
    dcf.setAnnualProduction(production);
    dcf.setProductPrice(productPrice);
    dcf.setAnnualOpex(annualOpexUSD);
    return dcf;
  }

  // ── Getters ──

  /**
   * Returns the total purchased equipment cost.
   *
   * @return purchased equipment cost in USD
   */
  public double getTotalPurchasedEquipmentCostUSD() {
    return totalPurchasedEquipmentCostUSD;
  }

  /**
   * Returns the total installed cost (equipment + installation).
   *
   * @return installed cost in USD
   */
  public double getTotalInstalledCostUSD() {
    return totalInstalledCostUSD;
  }

  /**
   * Returns the total CAPEX (installed + contingency).
   *
   * @return total CAPEX in USD
   */
  public double getTotalCapexUSD() {
    return totalCapexUSD;
  }

  /**
   * Returns the total annual OPEX.
   *
   * @return annual OPEX in USD
   */
  public double getAnnualOpexUSD() {
    return annualOpexUSD;
  }

  /**
   * Returns the annual feedstock cost.
   *
   * @return feedstock cost in USD/yr
   */
  public double getAnnualFeedstockCostUSD() {
    return annualFeedstockCostUSD;
  }

  /**
   * Returns the annual revenue.
   *
   * @return revenue in USD/yr
   */
  public double getAnnualRevenueUSD() {
    return annualRevenueUSD;
  }

  /**
   * Returns the levelised cost of product (LCOE/LCOP).
   *
   * @return levelised cost per unit of production
   */
  public double getLCOE() {
    return lcoe;
  }

  /**
   * Returns weather the estimation has been calculated.
   *
   * @return true if calculated
   */
  public boolean isCalculated() {
    return calculated;
  }

  /**
   * Returns a detailed results map.
   *
   * @return map of result names to values
   */
  public Map<String, Object> getResults() {
    if (!calculated) {
      calculate();
    }
    Map<String, Object> results = new LinkedHashMap<String, Object>();

    // CAPEX breakdown
    results.put("totalPurchasedEquipmentCost_USD", totalPurchasedEquipmentCostUSD);
    results.put("totalInstalledCost_USD", totalInstalledCostUSD);
    results.put("contingencyFraction", contingencyFraction);
    results.put("totalCapex_USD", totalCapexUSD);

    // Equipment details
    Map<String, Object> equipmentDetails = new LinkedHashMap<String, Object>();
    for (int i = 0; i < equipmentCount; i++) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      double pec = calculateEquipmentCost(equipmentTypes[i], equipmentCapacities[i]);
      item.put("capacity", equipmentCapacities[i]);
      item.put("capacityUnit", equipmentTypes[i].getCapacityUnit());
      item.put("purchasedCost_USD", pec);
      item.put("installedCost_USD", pec * equipmentTypes[i].getInstallationFactor());
      equipmentDetails.put(equipmentTypes[i].getDisplayName(), item);
    }
    results.put("equipmentBreakdown", equipmentDetails);

    // OPEX breakdown
    results.put("annualFeedstockCost_USD", annualFeedstockCostUSD);
    results.put("annualLabourCost_USD", annualLabourCostUSD);
    results.put("annualMaintenanceCost_USD", annualMaintenanceCostUSD);
    results.put("annualUtilityCost_USD", annualUtilityCostUSD);
    results.put("totalAnnualOpex_USD", annualOpexUSD);

    // Revenue and economics
    results.put("annualProduction", annualProduction);
    results.put("productUnit", productUnit);
    results.put("productPrice_USD", productPrice);
    results.put("annualRevenue_USD", annualRevenueUSD);
    results.put("LCOE_USDperUnit", lcoe);

    // Location and scaling
    results.put("locationFactor", locationFactor);
    results.put("costEscalationIndex", costEscalationIndex);

    return results;
  }

  /**
   * Returns a JSON string with the full cost estimation results.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getResults());
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    if (!calculated) {
      return "BiorefineryCostEstimator (not yet calculated)";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("BiorefineryCostEstimator\n");
    sb.append(String.format("  Equipment items: %d%n", equipmentCount));
    sb.append(
        String.format("  Purchased equipment cost: $%,.0f%n", totalPurchasedEquipmentCostUSD));
    sb.append(String.format("  Total installed cost:     $%,.0f%n", totalInstalledCostUSD));
    sb.append(String.format("  Total CAPEX:              $%,.0f%n", totalCapexUSD));
    sb.append(String.format("  Annual OPEX:              $%,.0f%n", annualOpexUSD));
    sb.append(String.format("  Annual Revenue:           $%,.0f%n", annualRevenueUSD));
    sb.append(String.format("  LCOE:                     $%.4f/%s%n", lcoe, productUnit));
    return sb.toString();
  }
}
