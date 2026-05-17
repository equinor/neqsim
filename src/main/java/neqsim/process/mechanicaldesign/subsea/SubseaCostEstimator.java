package neqsim.process.mechanicaldesign.subsea;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Cost estimation calculator for subsea SURF equipment.
 *
 * <p>
 * Provides cost estimation capabilities for subsea equipment including:
 * </p>
 * <ul>
 * <li>Equipment procurement costs</li>
 * <li>Material costs</li>
 * <li>Fabrication costs</li>
 * <li>Installation costs (vessel, labor, ROV)</li>
 * <li>Engineering and project management</li>
 * <li>Contingency and risk allowances</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class SubseaCostEstimator {

  /** Region for cost adjustment. */
  public enum Region {
    /** Norway - North Sea. */
    NORWAY,
    /** United Kingdom - North Sea. */
    UK,
    /** Gulf of Mexico. */
    GOM,
    /** Brazil - Santos Basin. */
    BRAZIL,
    /** West Africa. */
    WEST_AFRICA
  }

  /** Cost currency. */
  public enum Currency {
    /** US Dollars. */
    USD,
    /** Euros. */
    EUR,
    /** British Pounds. */
    GBP,
    /** Norwegian Kroner. */
    NOK
  }

  // ============ Cost Components ============
  /** Equipment/material procurement cost. */
  private double equipmentCost = 0.0;

  /** Fabrication cost. */
  private double fabricationCost = 0.0;

  /** Installation cost. */
  private double installationCost = 0.0;

  /** Vessel cost. */
  private double vesselCost = 0.0;

  /** Engineering cost. */
  private double engineeringCost = 0.0;

  /** Project management cost. */
  private double projectManagementCost = 0.0;

  /** Contingency allowance. */
  private double contingency = 0.0;

  /** Total project cost. */
  private double totalCost = 0.0;

  // ============ Labor Estimates ============
  /** Engineering manhours. */
  private double engineeringManhours = 0.0;

  /** Fabrication manhours. */
  private double fabricationManhours = 0.0;

  /** Installation manhours. */
  private double installationManhours = 0.0;

  /** Total manhours. */
  private double totalManhours = 0.0;

  // ============ Installation Parameters ============
  /** Vessel days required. */
  private double vesselDays = 0.0;

  /** ROV hours required. */
  private double rovHours = 0.0;

  /** Diving hours required (if applicable). */
  private double divingHours = 0.0;

  // ============ Settings ============
  /** Region for cost adjustment. */
  private Region region = Region.NORWAY;

  /** Currency for reporting. */
  private Currency currency = Currency.USD;

  /** Currency conversion rate to USD. */
  private double currencyRate = 1.0;

  // ============ Rate Assumptions ============
  /** Engineering rate per hour USD. */
  private double engineeringRate = 150.0;

  /** Fabrication rate per hour USD. */
  private double fabricationRate = 100.0;

  /** Installation crew rate per hour USD. */
  private double installationRate = 250.0;

  /** Vessel day rate USD. */
  private double vesselDayRate = 300000.0;

  /** ROV spread day rate USD. */
  private double rovDayRate = 120000.0;

  /** Engineering percentage of equipment cost. */
  private double engineeringPct = 0.10;

  /** Project management percentage. */
  private double projectMgmtPct = 0.05;

  /** Contingency percentage. */
  private double contingencyPct = 0.15;

  /**
   * Default constructor.
   */
  public SubseaCostEstimator() {}

  /**
   * Constructor with region.
   *
   * @param region cost region
   */
  public SubseaCostEstimator(Region region) {
    this.region = region;
    applyRegionFactor();
  }

  /**
   * Apply region cost factor.
   */
  private void applyRegionFactor() {
    double factor = 1.0;
    switch (region) {
      case NORWAY:
        factor = 1.35;
        break;
      case UK:
        factor = 1.25;
        break;
      case GOM:
        factor = 1.0;
        break;
      case BRAZIL:
        factor = 0.85;
        break;
      case WEST_AFRICA:
        factor = 1.1;
        break;
    }

    engineeringRate *= factor;
    fabricationRate *= factor;
    installationRate *= factor;
  }

  /**
   * Calculate PLET cost estimate.
   *
   * @param dryWeightTonnes dry weight in tonnes
   * @param hubSizeInches hub size in inches
   * @param waterDepthM water depth in meters
   * @param hasIsolationValve has isolation valve
   * @param hasPiggingFacility has pigging facilities
   */
  public void calculatePLETCost(double dryWeightTonnes, double hubSizeInches, double waterDepthM,
      boolean hasIsolationValve, boolean hasPiggingFacility) {
    // Base equipment cost based on size
    double baseCost;
    if (hubSizeInches < 10) {
      baseCost = 500000;
    } else if (hubSizeInches <= 16) {
      baseCost = 800000;
    } else {
      baseCost = 1200000;
    }

    // Material cost
    double materialCostPerTonne = 8000; // High-grade steel
    equipmentCost = baseCost + dryWeightTonnes * materialCostPerTonne;

    // Add valve cost
    if (hasIsolationValve) {
      equipmentCost += hubSizeInches * 25000; // Valve cost scales with size
    }

    // Add pigging cost
    if (hasPiggingFacility) {
      equipmentCost += 150000;
    }

    // Fabrication cost
    fabricationCost = dryWeightTonnes * 15000;

    // Installation cost
    calculateSubseaInstallation(dryWeightTonnes, waterDepthM, false);

    // Engineering and PM
    engineeringCost = equipmentCost * engineeringPct;
    projectManagementCost = equipmentCost * projectMgmtPct;

    // Calculate totals
    calculateTotals();
  }

  /**
   * Calculate Subsea Tree cost estimate.
   *
   * @param pressureRatingPsi pressure rating in psi
   * @param boreSizeInches bore size in inches
   * @param waterDepthM water depth in meters
   * @param isHorizontal is horizontal tree
   * @param isDualBore is dual bore tree
   */
  public void calculateTreeCost(double pressureRatingPsi, double boreSizeInches, double waterDepthM,
      boolean isHorizontal, boolean isDualBore) {
    // Base cost by pressure rating
    if (pressureRatingPsi <= 5000) {
      equipmentCost = 8000000;
    } else if (pressureRatingPsi <= 10000) {
      equipmentCost = 15000000;
    } else if (pressureRatingPsi <= 15000) {
      equipmentCost = 25000000;
    } else {
      equipmentCost = 40000000;
    }

    // Adjust for bore size
    double boreMultiplier = Math.pow(boreSizeInches / 5.0, 1.2);
    equipmentCost *= boreMultiplier;

    // Adjust for tree type
    if (isHorizontal) {
      equipmentCost *= 1.15;
    }
    if (isDualBore) {
      equipmentCost *= 1.70;
    }

    // Typical tree weight
    double estimatedWeight = equipmentCost / 500000; // Rough estimate

    // Installation cost
    calculateSubseaInstallation(estimatedWeight, waterDepthM, true);
    installationCost *= 0.8; // Trees easier to install

    // Higher engineering for trees
    engineeringPct = 0.12;
    projectMgmtPct = 0.06;
    contingencyPct = 0.18;

    // Engineering and PM
    engineeringCost = equipmentCost * engineeringPct;
    projectManagementCost = equipmentCost * projectMgmtPct;

    calculateTotals();
  }

  /**
   * Calculate Subsea Manifold cost estimate.
   *
   * @param numberOfSlots number of well slots
   * @param dryWeightTonnes dry weight in tonnes
   * @param waterDepthM water depth in meters
   * @param hasTestHeader has test header
   */
  public void calculateManifoldCost(int numberOfSlots, double dryWeightTonnes, double waterDepthM,
      boolean hasTestHeader) {
    // Base cost by number of slots
    if (numberOfSlots <= 4) {
      equipmentCost = 3000000;
    } else if (numberOfSlots <= 6) {
      equipmentCost = 5000000;
    } else {
      equipmentCost = 8000000;
    }

    // Material cost
    double materialCostPerTonne = 10000; // Higher grade for manifold
    equipmentCost += dryWeightTonnes * materialCostPerTonne;

    // Add valve skid cost
    double valveCostPerSlot = 500000;
    equipmentCost += numberOfSlots * valveCostPerSlot;

    // Test header cost
    if (hasTestHeader) {
      equipmentCost *= 1.25;
    }

    // Fabrication cost
    fabricationCost = dryWeightTonnes * 20000;

    // Installation cost
    calculateSubseaInstallation(dryWeightTonnes, waterDepthM, true);

    // Engineering and PM
    engineeringPct = 0.12;
    projectMgmtPct = 0.06;
    engineeringCost = equipmentCost * engineeringPct;
    projectManagementCost = equipmentCost * projectMgmtPct;

    calculateTotals();
  }

  /**
   * Calculate Subsea Jumper cost estimate.
   *
   * @param lengthM length in meters
   * @param diameterInches diameter in inches
   * @param isRigid is rigid jumper
   * @param waterDepthM water depth in meters
   */
  public void calculateJumperCost(double lengthM, double diameterInches, boolean isRigid,
      double waterDepthM) {
    if (isRigid) {
      // Rigid jumper cost
      double baseCost;
      if (lengthM < 30) {
        baseCost = 200000;
      } else if (lengthM <= 60) {
        baseCost = 400000;
      } else {
        baseCost = 700000;
      }

      // Material cost
      double weightPerMeter = Math.PI * Math.pow(diameterInches * 0.0254, 2) / 4 * 7850; // kg/m
      double totalWeight = weightPerMeter * lengthM / 1000; // tonnes
      equipmentCost = baseCost + totalWeight * 6000;

      // Hub costs (2 hubs)
      equipmentCost += diameterInches * 50000;

      // Fabrication
      fabricationCost = totalWeight * 12000;

      // Installation
      vesselDays = 2 + lengthM / 50; // Base + length factor
    } else {
      // Flexible jumper - price per meter
      double pricePerMeter;
      if (diameterInches <= 4) {
        pricePerMeter = 600;
      } else if (diameterInches <= 6) {
        pricePerMeter = 800;
      } else {
        pricePerMeter = 1200;
      }

      equipmentCost = lengthM * pricePerMeter;

      // End fittings
      equipmentCost += diameterInches * 40000;

      fabricationCost = 0; // Manufactured by supplier

      // Installation
      vesselDays = 1 + lengthM / 100;
    }

    // Vessel and ROV costs
    vesselCost = vesselDays * vesselDayRate;
    rovHours = vesselDays * 12; // 12 hours ROV per day
    installationCost = vesselCost + rovHours * rovDayRate / 24;

    // Engineering and PM
    engineeringPct = 0.08;
    projectMgmtPct = 0.04;
    contingencyPct = 0.12;

    engineeringCost = equipmentCost * engineeringPct;
    projectManagementCost = equipmentCost * projectMgmtPct;

    calculateTotals();
  }

  /**
   * Calculate Umbilical cost estimate.
   *
   * @param lengthKm length in kilometers
   * @param numberOfHydraulicLines number of hydraulic lines
   * @param numberOfChemicalLines number of chemical lines
   * @param numberOfElectricalCables number of electrical cables
   * @param waterDepthM water depth in meters
   * @param isDynamic is dynamic section
   */
  public void calculateUmbilicalCost(double lengthKm, int numberOfHydraulicLines,
      int numberOfChemicalLines, int numberOfElectricalCables, double waterDepthM,
      boolean isDynamic) {
    // Base cost per km
    double baseCostPerKm = isDynamic ? 3500000 : 2000000;

    // Adjust for complexity
    int totalElements = numberOfHydraulicLines + numberOfChemicalLines + numberOfElectricalCables;
    double complexityFactor = 1.0 + (totalElements - 4) * 0.08; // Base is 4 elements
    complexityFactor = Math.max(complexityFactor, 1.0);

    equipmentCost = lengthKm * baseCostPerKm * complexityFactor;

    // Termination assemblies
    equipmentCost += 2 * 500000; // Two ends

    // Deepwater premium
    if (waterDepthM > 1000) {
      equipmentCost *= 1.15;
    } else if (waterDepthM > 2000) {
      equipmentCost *= 1.35;
    }

    fabricationCost = 0; // Manufactured by supplier

    // Installation - cable lay vessel
    double layRate = 1.5; // km per day
    vesselDays = lengthKm / layRate + 3; // Plus mob/demob and termination
    vesselDayRate = 280000; // Cable lay vessel
    vesselCost = vesselDays * vesselDayRate;

    rovHours = vesselDays * 16;
    installationCost = vesselCost + rovHours * rovDayRate / 24;

    // Engineering and PM
    engineeringCost = equipmentCost * 0.10;
    projectManagementCost = equipmentCost * 0.05;

    calculateTotals();
  }

  /**
   * Calculate Flexible Pipe cost estimate.
   *
   * @param lengthM length in meters
   * @param innerDiameterInches inner diameter in inches
   * @param waterDepthM water depth in meters
   * @param isDynamic is dynamic riser
   * @param hasBuoyancy has buoyancy modules
   */
  public void calculateFlexiblePipeCost(double lengthM, double innerDiameterInches,
      double waterDepthM, boolean isDynamic, boolean hasBuoyancy) {
    // Price per meter by size
    double pricePerMeter;
    if (innerDiameterInches <= 4) {
      pricePerMeter = isDynamic ? 800 : 500;
    } else if (innerDiameterInches <= 6) {
      pricePerMeter = isDynamic ? 1200 : 800;
    } else if (innerDiameterInches <= 8) {
      pricePerMeter = isDynamic ? 1800 : 1200;
    } else {
      pricePerMeter = isDynamic ? 2500 : 1800;
    }

    // Deepwater premium
    if (waterDepthM > 1500) {
      pricePerMeter *= 1.2;
    }

    equipmentCost = lengthM * pricePerMeter;

    // End fittings
    equipmentCost += innerDiameterInches * 80000;

    // Buoyancy modules
    if (hasBuoyancy) {
      double buoyancyLength = lengthM * 0.3; // Assume 30% of length
      equipmentCost += buoyancyLength * 500; // Buoyancy cost per meter
    }

    // Bend stiffeners for dynamic
    if (isDynamic) {
      equipmentCost += innerDiameterInches * 50000;
    }

    fabricationCost = 0;

    // Installation
    double layRate = isDynamic ? 300 : 500; // meters per day
    vesselDays = lengthM / layRate + 2;
    vesselDayRate = isDynamic ? 350000 : 250000;
    vesselCost = vesselDays * vesselDayRate;

    rovHours = vesselDays * 12;
    installationCost = vesselCost + rovHours * rovDayRate / 24;

    // Engineering
    engineeringPct = 0.08;
    projectMgmtPct = 0.04;
    engineeringCost = equipmentCost * engineeringPct;
    projectManagementCost = equipmentCost * projectMgmtPct;

    calculateTotals();
  }

  /**
   * Calculate Subsea Booster cost estimate.
   *
   * @param powerMW power in MW
   * @param isCompressor is compressor (vs pump)
   * @param waterDepthM water depth in meters
   * @param hasRedundancy has redundant motor
   */
  public void calculateBoosterCost(double powerMW, boolean isCompressor, double waterDepthM,
      boolean hasRedundancy) {
    // Base cost by power and type
    double baseCost;
    if (isCompressor) {
      if (powerMW < 5) {
        baseCost = 60000000;
      } else if (powerMW <= 10) {
        baseCost = 100000000;
      } else {
        baseCost = 150000000;
      }
    } else {
      if (powerMW < 3) {
        baseCost = 30000000;
      } else if (powerMW <= 6) {
        baseCost = 50000000;
      } else {
        baseCost = 80000000;
      }
    }

    // Scale with power
    equipmentCost = baseCost * Math.pow(powerMW / 5.0, 0.7);

    // Redundancy
    if (hasRedundancy) {
      equipmentCost *= 1.4;
    }

    // Deepwater premium
    if (waterDepthM > 1500) {
      equipmentCost *= 1.1;
    }

    fabricationCost = 0; // Manufactured as complete module

    // Installation - heavy lift
    vesselDays = 10; // Typical installation duration
    vesselDayRate = 500000; // Heavy lift vessel
    vesselCost = vesselDays * vesselDayRate;

    rovHours = vesselDays * 24;
    divingHours = waterDepthM < 300 ? 100 : 0;
    installationCost = vesselCost + rovHours * rovDayRate / 24;
    if (divingHours > 0) {
      installationCost += divingHours * 300;
    }

    // Higher engineering for boosters
    engineeringPct = 0.15;
    projectMgmtPct = 0.08;
    contingencyPct = 0.20;

    engineeringCost = equipmentCost * engineeringPct;
    projectManagementCost = equipmentCost * projectMgmtPct;

    calculateTotals();
  }

  /**
   * Calculate generic subsea installation cost.
   *
   * @param weightTonnes equipment weight in tonnes
   * @param waterDepthM water depth in meters
   * @param requiresPrecision requires precision landing
   */
  private void calculateSubseaInstallation(double weightTonnes, double waterDepthM,
      boolean requiresPrecision) {
    // Vessel selection based on weight
    if (weightTonnes > 500) {
      vesselDayRate = 600000; // Heavy lift vessel
    } else if (weightTonnes > 100) {
      vesselDayRate = 350000; // Large construction vessel
    } else {
      vesselDayRate = 250000; // Medium construction vessel
    }

    // Installation duration
    vesselDays = 3.0; // Base duration
    vesselDays += weightTonnes / 100; // Weight factor
    if (requiresPrecision) {
      vesselDays *= 1.3;
    }
    if (waterDepthM > 1500) {
      vesselDays *= 1.2; // Deepwater factor
    }

    // ROV hours
    rovHours = vesselDays * 16; // 16 hours per day

    // Calculate costs
    vesselCost = vesselDays * vesselDayRate;
    installationCost = vesselCost + rovHours * rovDayRate / 24;
  }

  /**
   * Calculate total costs.
   */
  private void calculateTotals() {
    double subtotal = equipmentCost + fabricationCost + installationCost + engineeringCost
        + projectManagementCost;
    contingency = subtotal * contingencyPct;
    totalCost = subtotal + contingency;

    // Labor estimates
    if (engineeringRate > 0) {
      engineeringManhours = engineeringCost / engineeringRate;
    }
    if (fabricationRate > 0) {
      fabricationManhours = fabricationCost / fabricationRate;
    }
    installationManhours = vesselDays * 24 * 20; // 20 crew
    totalManhours = engineeringManhours + fabricationManhours + installationManhours;
  }

  /**
   * Get cost breakdown as Map.
   *
   * @return cost breakdown map
   */
  public Map<String, Object> getCostBreakdown() {
    Map<String, Object> costs = new LinkedHashMap<String, Object>();

    Map<String, Object> direct = new LinkedHashMap<String, Object>();
    direct.put("equipmentCostUSD", equipmentCost);
    direct.put("fabricationCostUSD", fabricationCost);
    direct.put("installationCostUSD", installationCost);
    costs.put("directCosts", direct);

    Map<String, Object> indirect = new LinkedHashMap<String, Object>();
    indirect.put("engineeringCostUSD", engineeringCost);
    indirect.put("projectManagementCostUSD", projectManagementCost);
    costs.put("indirectCosts", indirect);

    Map<String, Object> install = new LinkedHashMap<String, Object>();
    install.put("vesselCostUSD", vesselCost);
    install.put("vesselDays", vesselDays);
    install.put("vesselDayRateUSD", vesselDayRate);
    install.put("rovHours", rovHours);
    if (divingHours > 0) {
      install.put("divingHours", divingHours);
    }
    costs.put("installationBreakdown", install);

    costs.put("contingencyUSD", contingency);
    costs.put("contingencyPct", contingencyPct * 100);
    costs.put("totalCostUSD", totalCost);

    Map<String, Object> labor = new LinkedHashMap<String, Object>();
    labor.put("engineeringManhours", engineeringManhours);
    labor.put("fabricationManhours", fabricationManhours);
    labor.put("installationManhours", installationManhours);
    labor.put("totalManhours", totalManhours);
    costs.put("laborEstimate", labor);

    costs.put("region", region.name());
    costs.put("currency", currency.name());

    return costs;
  }

  /**
   * Generate bill of materials.
   *
   * @param equipmentType equipment type name
   * @param details equipment details
   * @return list of BOM items
   */
  public List<Map<String, Object>> generateBillOfMaterials(String equipmentType,
      Map<String, Object> details) {
    List<Map<String, Object>> bom = new ArrayList<Map<String, Object>>();

    // Main equipment
    Map<String, Object> mainItem = new LinkedHashMap<String, Object>();
    mainItem.put("itemNo", 1);
    mainItem.put("description", equipmentType + " Main Assembly");
    mainItem.put("quantity", 1);
    mainItem.put("unit", "EA");
    mainItem.put("unitCostUSD", equipmentCost);
    mainItem.put("totalCostUSD", equipmentCost);
    bom.add(mainItem);

    // Fabrication
    if (fabricationCost > 0) {
      Map<String, Object> fabItem = new LinkedHashMap<String, Object>();
      fabItem.put("itemNo", 2);
      fabItem.put("description", "Fabrication and Assembly");
      fabItem.put("quantity", 1);
      fabItem.put("unit", "LS");
      fabItem.put("unitCostUSD", fabricationCost);
      fabItem.put("totalCostUSD", fabricationCost);
      bom.add(fabItem);
    }

    // Installation vessel
    Map<String, Object> vesselItem = new LinkedHashMap<String, Object>();
    vesselItem.put("itemNo", 3);
    vesselItem.put("description", "Installation Vessel");
    vesselItem.put("quantity", vesselDays);
    vesselItem.put("unit", "DAY");
    vesselItem.put("unitCostUSD", vesselDayRate);
    vesselItem.put("totalCostUSD", vesselCost);
    bom.add(vesselItem);

    // ROV spread
    Map<String, Object> rovItem = new LinkedHashMap<String, Object>();
    rovItem.put("itemNo", 4);
    rovItem.put("description", "ROV Spread");
    rovItem.put("quantity", rovHours);
    rovItem.put("unit", "HR");
    rovItem.put("unitCostUSD", rovDayRate / 24);
    rovItem.put("totalCostUSD", rovHours * rovDayRate / 24);
    bom.add(rovItem);

    // Engineering
    Map<String, Object> engItem = new LinkedHashMap<String, Object>();
    engItem.put("itemNo", 5);
    engItem.put("description", "Engineering Services");
    engItem.put("quantity", engineeringManhours);
    engItem.put("unit", "MH");
    engItem.put("unitCostUSD", engineeringRate);
    engItem.put("totalCostUSD", engineeringCost);
    bom.add(engItem);

    // Project Management
    Map<String, Object> pmItem = new LinkedHashMap<String, Object>();
    pmItem.put("itemNo", 6);
    pmItem.put("description", "Project Management");
    pmItem.put("quantity", 1);
    pmItem.put("unit", "LS");
    pmItem.put("unitCostUSD", projectManagementCost);
    pmItem.put("totalCostUSD", projectManagementCost);
    bom.add(pmItem);

    // Contingency
    Map<String, Object> contItem = new LinkedHashMap<String, Object>();
    contItem.put("itemNo", 7);
    contItem.put("description", "Contingency (" + (contingencyPct * 100) + "%)");
    contItem.put("quantity", 1);
    contItem.put("unit", "LS");
    contItem.put("unitCostUSD", contingency);
    contItem.put("totalCostUSD", contingency);
    bom.add(contItem);

    return bom;
  }

  /**
   * Get cost as JSON string.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getCostBreakdown());
  }

  // Getters

  /**
   * Get equipment cost.
   *
   * @return equipment cost in USD
   */
  public double getEquipmentCost() {
    return equipmentCost;
  }

  /**
   * Get fabrication cost.
   *
   * @return fabrication cost in USD
   */
  public double getFabricationCost() {
    return fabricationCost;
  }

  /**
   * Get installation cost.
   *
   * @return installation cost in USD
   */
  public double getInstallationCost() {
    return installationCost;
  }

  /**
   * Get total cost.
   *
   * @return total cost in USD
   */
  public double getTotalCost() {
    return totalCost;
  }

  /**
   * Get vessel days.
   *
   * @return vessel days
   */
  public double getVesselDays() {
    return vesselDays;
  }

  /**
   * Get total manhours.
   *
   * @return total manhours
   */
  public double getTotalManhours() {
    return totalManhours;
  }

  /**
   * Get region.
   *
   * @return region
   */
  public Region getRegion() {
    return region;
  }

  /**
   * Set region.
   *
   * @param region region
   */
  public void setRegion(Region region) {
    this.region = region;
    applyRegionFactor();
  }

  /**
   * Set contingency percentage.
   *
   * @param contingencyPct contingency percentage (0.0 - 1.0)
   */
  public void setContingencyPct(double contingencyPct) {
    this.contingencyPct = contingencyPct;
  }

  /**
   * Get currency.
   *
   * @return currency
   */
  public Currency getCurrency() {
    return currency;
  }

  /**
   * Set currency.
   *
   * @param currency currency
   */
  public void setCurrency(Currency currency) {
    this.currency = currency;
    // Update conversion rate based on currency
    switch (currency) {
      case EUR:
        currencyRate = 0.92;
        break;
      case GBP:
        currencyRate = 0.79;
        break;
      case NOK:
        currencyRate = 10.5;
        break;
      default:
        currencyRate = 1.0;
    }
  }

  /**
   * Generate bill of materials for subsea equipment.
   *
   * @param equipmentType type of equipment
   * @param weightTonnes equipment weight in tonnes
   * @param waterDepth water depth in meters
   * @return list of BOM items
   */
  public List<Map<String, Object>> generateBOM(String equipmentType, double weightTonnes,
      double waterDepth) {
    List<Map<String, Object>> bom = new ArrayList<Map<String, Object>>();

    // Steel structure
    Map<String, Object> steel = new LinkedHashMap<String, Object>();
    steel.put("item", "Steel Structure");
    steel.put("material", "S355/X65");
    steel.put("quantity", weightTonnes * 0.6);
    steel.put("unit", "tonnes");
    steel.put("unitCost", 5000.0);
    steel.put("totalCost", weightTonnes * 0.6 * 5000.0);
    bom.add(steel);

    // Piping
    Map<String, Object> piping = new LinkedHashMap<String, Object>();
    piping.put("item", "Piping Components");
    piping.put("material", "Duplex SS/CRA");
    piping.put("quantity", weightTonnes * 0.15);
    piping.put("unit", "tonnes");
    piping.put("unitCost", 15000.0);
    piping.put("totalCost", weightTonnes * 0.15 * 15000.0);
    bom.add(piping);

    // Valves
    Map<String, Object> valves = new LinkedHashMap<String, Object>();
    valves.put("item", "Valves and Actuators");
    valves.put("material", "Various");
    valves.put("quantity", Math.max(2, (int) (weightTonnes / 10)));
    valves.put("unit", "ea");
    valves.put("unitCost", 150000.0);
    valves.put("totalCost", Math.max(2, (int) (weightTonnes / 10)) * 150000.0);
    bom.add(valves);

    // Connectors
    Map<String, Object> connectors = new LinkedHashMap<String, Object>();
    connectors.put("item", "Subsea Connectors");
    connectors.put("material", "Forged Steel");
    connectors.put("quantity", Math.max(2, (int) (weightTonnes / 15)));
    connectors.put("unit", "ea");
    connectors.put("unitCost", 200000.0);
    connectors.put("totalCost", Math.max(2, (int) (weightTonnes / 15)) * 200000.0);
    bom.add(connectors);

    // Foundation/mudmat
    double foundationWeight = weightTonnes * 0.25;
    Map<String, Object> foundation = new LinkedHashMap<String, Object>();
    foundation.put("item", "Foundation/Mudmat");
    foundation.put("material", "S355 Steel Plate");
    foundation.put("quantity", foundationWeight);
    foundation.put("unit", "tonnes");
    foundation.put("unitCost", 4000.0);
    foundation.put("totalCost", foundationWeight * 4000.0);
    bom.add(foundation);

    // Coating and protection
    double surfaceArea = Math.pow(weightTonnes, 0.67) * 10;
    Map<String, Object> coating = new LinkedHashMap<String, Object>();
    coating.put("item", "Marine Coating System");
    coating.put("material", "Epoxy/Polyurethane");
    coating.put("quantity", surfaceArea);
    coating.put("unit", "m2");
    coating.put("unitCost", 150.0);
    coating.put("totalCost", surfaceArea * 150.0);
    bom.add(coating);

    // CP anodes
    int anodeCount = (int) Math.max(4, surfaceArea / 50);
    Map<String, Object> anodes = new LinkedHashMap<String, Object>();
    anodes.put("item", "Sacrificial Anodes");
    anodes.put("material", "Aluminum Alloy");
    anodes.put("quantity", anodeCount);
    anodes.put("unit", "ea");
    anodes.put("unitCost", 500.0);
    anodes.put("totalCost", anodeCount * 500.0);
    bom.add(anodes);

    return bom;
  }
}
