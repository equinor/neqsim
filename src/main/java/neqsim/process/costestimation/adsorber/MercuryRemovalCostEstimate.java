package neqsim.process.costestimation.adsorber;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.adsorber.MercuryRemovalMechanicalDesign;

/**
 * Cost estimation for mercury removal guard beds.
 *
 * <p>
 * Provides CAPEX and OPEX estimates for a fixed-bed mercury chemisorption unit, including:
 * </p>
 * <ul>
 * <li>Pressure vessel fabrication cost (weight-based)</li>
 * <li>Sorbent purchase cost (initial + replacement)</li>
 * <li>Installation and commissioning</li>
 * <li>Annual sorbent replacement OPEX</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class MercuryRemovalCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1002L;

  // ---- Cost rates (USD) ----

  /** Sorbent unit price (USD / kg of sorbent). Typical PuraSpec ~15-30 USD/kg. */
  private double sorbentUnitPrice = 25.0;

  /** Steel fabrication cost factor (USD / kg of fabricated steel). */
  private double steelCostPerKg = 8.0;

  /** Installation factor (multiplier on purchased equipment cost). */
  private double installationFactor = 1.5;

  /** Annual maintenance factor (fraction of CAPEX). */
  private double maintenanceFactor = 0.03;

  /**
   * Constructor for MercuryRemovalCostEstimate.
   *
   * @param mechanicalDesign the mechanical design for the mercury removal bed
   */
  public MercuryRemovalCostEstimate(MercuryRemovalMechanicalDesign mechanicalDesign) {
    super(mechanicalDesign);
    setEquipmentType("vessel");
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    if (mechanicalEquipment == null) {
      return 0.0;
    }

    MercuryRemovalMechanicalDesign design = (MercuryRemovalMechanicalDesign) mechanicalEquipment;

    // Vessel shell + internals steel cost
    double vesselSteelWeight =
        design.getWeigthVesselShell() + design.getInternalsWeight() + design.getWeightNozzle();

    double vesselCost =
        vesselSteelWeight * steelCostPerKg * getCostCalculator().getMaterialFactor();

    // Initial sorbent charge cost
    double sorbentCost = design.getSorbentChargeWeight() * sorbentUnitPrice;

    return vesselCost + sorbentCost;
  }

  /** {@inheritDoc} */
  @Override
  public double getTotalCost() {
    if (totalModuleCost > 0) {
      return totalModuleCost;
    }
    if (purchasedEquipmentCost <= 0) {
      calculateCostEstimate();
    }
    return totalModuleCost;
  }

  /**
   * Calculate the sorbent replacement cost per change-out.
   *
   * @return sorbent replacement cost in USD
   */
  public double getSorbentReplacementCost() {
    if (mechanicalEquipment == null) {
      return 0.0;
    }
    MercuryRemovalMechanicalDesign design = (MercuryRemovalMechanicalDesign) mechanicalEquipment;
    // Sorbent cost + labour for change-out (typically ~25% of sorbent cost)
    double sorbentCost = design.getSorbentChargeWeight() * sorbentUnitPrice;
    return sorbentCost * 1.25;
  }

  /**
   * Estimate annual sorbent replacement OPEX given a bed lifetime.
   *
   * @param bedLifetimeYears expected bed lifetime in years (typically 3-7 years)
   * @return annual sorbent replacement cost in USD/year
   */
  public double getAnnualSorbentCost(double bedLifetimeYears) {
    if (bedLifetimeYears <= 0) {
      return 0.0;
    }
    return getSorbentReplacementCost() / bedLifetimeYears;
  }

  /** {@inheritDoc} */
  @Override
  public double calcAnnualOperatingCost(double electricityCostPerKWh, double steamCostPerTonne,
      double coolingWaterCostPerM3, int operatingHoursPerYear) {
    // Maintenance
    double capex = getTotalCost();
    double maintenance = capex * maintenanceFactor;

    // Sorbent assumed 5-year life
    double sorbentAnnual = getAnnualSorbentCost(5.0);

    annualOperatingCost = maintenance + sorbentAnnual;
    return annualOperatingCost;
  }

  /**
   * Export a JSON cost report.
   *
   * @return JSON string with cost breakdown
   */
  @Override
  public String toJson() {
    MercuryRemovalMechanicalDesign design = (MercuryRemovalMechanicalDesign) mechanicalEquipment;

    JsonObject json = new JsonObject();
    json.addProperty("equipmentName",
        design != null ? design.getProcessEquipment().getName() : "unknown");
    json.addProperty("equipmentType", "MercuryRemovalBed");

    // CAPEX
    JsonObject capex = new JsonObject();
    capex.addProperty("purchasedEquipmentCost_USD", getPurchasedEquipmentCost());
    capex.addProperty("bareModuleCost_USD", getBareModuleCost());
    capex.addProperty("totalModuleCost_USD", getTotalModuleCost());
    capex.addProperty("grassRootsCost_USD", getGrassRootsCost());
    capex.addProperty("installationManHours", getInstallationManHours());
    json.add("capex", capex);

    // OPEX
    JsonObject opex = new JsonObject();
    opex.addProperty("sorbentReplacementCost_USD", getSorbentReplacementCost());
    opex.addProperty("annualSorbentCost_USD_5yr", getAnnualSorbentCost(5.0));
    opex.addProperty("annualMaintenanceCost_USD", getTotalCost() * maintenanceFactor);
    json.add("opex", opex);

    // Rates
    JsonObject rates = new JsonObject();
    rates.addProperty("sorbentUnitPrice_USD_per_kg", sorbentUnitPrice);
    rates.addProperty("steelCostPerKg_USD", steelCostPerKg);
    rates.addProperty("installationFactor", installationFactor);
    rates.addProperty("maintenanceFactor", maintenanceFactor);
    json.add("costRateAssumptions", rates);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(json);
  }

  // ======================================================================
  // Getters / Setters
  // ======================================================================

  /**
   * Get the sorbent unit price.
   *
   * @return sorbent price in USD/kg
   */
  public double getSorbentUnitPrice() {
    return sorbentUnitPrice;
  }

  /**
   * Set the sorbent unit price.
   *
   * @param price sorbent price in USD/kg (typical 15-30)
   */
  public void setSorbentUnitPrice(double price) {
    this.sorbentUnitPrice = price;
  }

  /**
   * Get the steel cost per kg.
   *
   * @return steel cost in USD/kg
   */
  public double getSteelCostPerKg() {
    return steelCostPerKg;
  }

  /**
   * Set the steel fabrication cost per kg.
   *
   * @param cost steel cost in USD/kg
   */
  public void setSteelCostPerKg(double cost) {
    this.steelCostPerKg = cost;
  }

  /**
   * Get the installation factor.
   *
   * @return installation factor (multiplier on PEC)
   */
  public double getInstallationFactor() {
    return installationFactor;
  }

  /**
   * Set the installation factor.
   *
   * @param factor installation factor (multiplier on PEC)
   */
  public void setInstallationFactor(double factor) {
    this.installationFactor = factor;
  }
}
