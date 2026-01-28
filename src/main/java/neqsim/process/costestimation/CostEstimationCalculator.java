package neqsim.process.costestimation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Cost estimation calculator for process equipment.
 *
 * <p>
 * This class provides standardized cost estimation methods based on chemical engineering cost
 * correlations from standard references including:
 * </p>
 * <ul>
 * <li>Peters, Timmerhaus &amp; West - Plant Design and Economics for Chemical Engineers</li>
 * <li>Turton, Bailie, Whiting &amp; Shaeiwitz - Analysis, Synthesis and Design of Chemical
 * Processes</li>
 * <li>Ulrich &amp; Vasudevan - Chemical Engineering Process Design and Economics</li>
 * <li>Seider, Seader &amp; Lewin - Product and Process Design Principles</li>
 * </ul>
 *
 * <p>
 * Cost indices supported:
 * </p>
 * <ul>
 * <li>CEPCI (Chemical Engineering Plant Cost Index) - default reference year 2019</li>
 * <li>Marshall &amp; Swift Equipment Cost Index</li>
 * <li>Nelson-Farrar Refinery Construction Index</li>
 * </ul>
 *
 * @author AGAS
 * @version 1.0
 */
public class CostEstimationCalculator implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // Cost Index Constants
  // ============================================================================

  /** CEPCI for 2019 (base year for correlations). */
  public static final double CEPCI_2019 = 607.5;

  /** CEPCI for 2020. */
  public static final double CEPCI_2020 = 596.2;

  /** CEPCI for 2021. */
  public static final double CEPCI_2021 = 708.0;

  /** CEPCI for 2022. */
  public static final double CEPCI_2022 = 816.0;

  /** CEPCI for 2023. */
  public static final double CEPCI_2023 = 800.0;

  /** CEPCI for 2024 (estimated). */
  public static final double CEPCI_2024 = 820.0;

  /** CEPCI for 2025 (estimated). */
  public static final double CEPCI_2025 = 840.0;

  // ============================================================================
  // Material Factors
  // ============================================================================

  /** Material factor for carbon steel. */
  public static final double FM_CARBON_STEEL = 1.0;

  /** Material factor for stainless steel 304. */
  public static final double FM_SS304 = 1.8;

  /** Material factor for stainless steel 316. */
  public static final double FM_SS316 = 2.1;

  /** Material factor for stainless steel 316L. */
  public static final double FM_SS316L = 2.3;

  /** Material factor for Monel. */
  public static final double FM_MONEL = 3.2;

  /** Material factor for Hastelloy C. */
  public static final double FM_HASTELLOY_C = 3.8;

  /** Material factor for Inconel. */
  public static final double FM_INCONEL = 3.5;

  /** Material factor for titanium. */
  public static final double FM_TITANIUM = 4.5;

  /** Material factor for nickel. */
  public static final double FM_NICKEL = 3.0;

  // ============================================================================
  // Currency Support
  // ============================================================================

  /** Currency code for US Dollar. */
  public static final String CURRENCY_USD = "USD";

  /** Currency code for Euro. */
  public static final String CURRENCY_EUR = "EUR";

  /** Currency code for Norwegian Krone. */
  public static final String CURRENCY_NOK = "NOK";

  /** Currency code for British Pound. */
  public static final String CURRENCY_GBP = "GBP";

  /** Currency code for Chinese Yuan. */
  public static final String CURRENCY_CNY = "CNY";

  /** Currency code for Japanese Yen. */
  public static final String CURRENCY_JPY = "JPY";

  // ============================================================================
  // Location Factors (relative to US Gulf Coast = 1.0)
  // ============================================================================

  /** Location factor for US Gulf Coast (base). */
  public static final double LOC_US_GULF_COAST = 1.0;

  /** Location factor for US Midwest. */
  public static final double LOC_US_MIDWEST = 1.05;

  /** Location factor for US West Coast. */
  public static final double LOC_US_WEST_COAST = 1.15;

  /** Location factor for Western Europe. */
  public static final double LOC_WESTERN_EUROPE = 1.20;

  /** Location factor for North Sea / Norway. */
  public static final double LOC_NORTH_SEA = 1.35;

  /** Location factor for Middle East. */
  public static final double LOC_MIDDLE_EAST = 1.10;

  /** Location factor for Southeast Asia. */
  public static final double LOC_SOUTHEAST_ASIA = 0.90;

  /** Location factor for China. */
  public static final double LOC_CHINA = 0.75;

  /** Location factor for Australia. */
  public static final double LOC_AUSTRALIA = 1.30;

  /** Location factor for Brazil. */
  public static final double LOC_BRAZIL = 1.15;

  /** Location factor for West Africa. */
  public static final double LOC_WEST_AFRICA = 1.40;

  // ============================================================================
  // Pressure Factors
  // ============================================================================

  /**
   * Get pressure factor for vessels based on design pressure.
   *
   * @param designPressure design pressure in barg
   * @return pressure factor Fp
   */
  public static double getPressureFactor(double designPressure) {
    if (designPressure <= 5.0) {
      return 1.0;
    } else if (designPressure <= 10.0) {
      return 1.05;
    } else if (designPressure <= 20.0) {
      return 1.15;
    } else if (designPressure <= 50.0) {
      return 1.30;
    } else if (designPressure <= 100.0) {
      return 1.50;
    } else if (designPressure <= 150.0) {
      return 1.65;
    } else if (designPressure <= 200.0) {
      return 1.90;
    } else {
      return 2.5;
    }
  }

  // ============================================================================
  // Instance Variables
  // ============================================================================

  /** Current CEPCI for cost escalation. */
  private double currentCepci = CEPCI_2025;

  /** Reference CEPCI for correlations (2019). */
  private double referenceCepci = CEPCI_2019;

  /** Currency code (default USD). */
  private String currencyCode = "USD";

  /** Exchange rate from USD. */
  private double exchangeRate = 1.0;

  /** Location factor for different regions. */
  private double locationFactor = 1.0;

  /** Material of construction. */
  private String materialOfConstruction = "Carbon Steel";

  /** Material factor. */
  private double materialFactor = FM_CARBON_STEEL;

  /** Installation factor (Lang factor). */
  private double installationFactor = 3.0;

  /** Contingency factor. */
  private double contingencyFactor = 0.15;

  /** Engineering factor. */
  private double engineeringFactor = 0.10;

  // ============================================================================
  // Cost Results
  // ============================================================================

  /** Purchased equipment cost (PEC) in USD. */
  private double purchasedEquipmentCost = 0.0;

  /** Bare module cost (BMC) in USD. */
  private double bareModuleCost = 0.0;

  /** Total module cost (TMC) in USD. */
  private double totalModuleCost = 0.0;

  /** Total grass roots cost in USD. */
  private double grassRootsCost = 0.0;

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor with 2025 CEPCI.
   */
  public CostEstimationCalculator() {
    this.currentCepci = CEPCI_2025;
  }

  /**
   * Constructor with specified CEPCI.
   *
   * @param cepci current Chemical Engineering Plant Cost Index
   */
  public CostEstimationCalculator(double cepci) {
    this.currentCepci = cepci;
  }

  // ============================================================================
  // Equipment Cost Methods - Vessels
  // ============================================================================

  /**
   * Calculate purchased equipment cost for vertical pressure vessel.
   *
   * <p>
   * Based on Turton et al. correlation (2018): log10(Cp) = K1 + K2*log10(W) + K3*(log10(W))^2 where
   * W is shell weight in kg. For vertical vessels: K1 = 3.4974, K2 = 0.4485, K3 = 0.1074
   * </p>
   *
   * @param shellWeight vessel shell weight in kg
   * @return purchased equipment cost in USD (2019 basis)
   */
  public double calcVerticalVesselCost(double shellWeight) {
    if (shellWeight <= 0) {
      return 0.0;
    }
    // Turton correlation coefficients for vertical vessel
    double k1 = 3.4974;
    double k2 = 0.4485;
    double k3 = 0.1074;

    double logW = Math.log10(Math.max(shellWeight, 250.0)); // min 250 kg
    double logCp = k1 + k2 * logW + k3 * logW * logW;
    double baseCost = Math.pow(10, logCp);

    // Apply CEPCI escalation
    return baseCost * (currentCepci / referenceCepci);
  }

  /**
   * Calculate purchased equipment cost for horizontal pressure vessel.
   *
   * @param shellWeight vessel shell weight in kg
   * @return purchased equipment cost in USD (current CEPCI basis)
   */
  public double calcHorizontalVesselCost(double shellWeight) {
    if (shellWeight <= 0) {
      return 0.0;
    }
    // Turton correlation coefficients for horizontal vessel
    double k1 = 3.5565;
    double k2 = 0.3776;
    double k3 = 0.0905;

    double logW = Math.log10(Math.max(shellWeight, 250.0));
    double logCp = k1 + k2 * logW + k3 * logW * logW;
    double baseCost = Math.pow(10, logCp);

    return baseCost * (currentCepci / referenceCepci);
  }

  // ============================================================================
  // Equipment Cost Methods - Heat Exchangers
  // ============================================================================

  /**
   * Calculate purchased equipment cost for shell and tube heat exchanger.
   *
   * @param area heat transfer area in m2
   * @return purchased equipment cost in USD (current CEPCI basis)
   */
  public double calcShellTubeHeatExchangerCost(double area) {
    if (area <= 0) {
      return 0.0;
    }
    // Turton correlation: log10(Cp) = K1 + K2*log10(A) + K3*(log10(A))^2
    // For fixed tube sheet: K1 = 4.3247, K2 = -0.3030, K3 = 0.1634
    double k1 = 4.3247;
    double k2 = -0.3030;
    double k3 = 0.1634;

    double logA = Math.log10(Math.max(area, 10.0)); // min 10 m2
    double logCp = k1 + k2 * logA + k3 * logA * logA;
    double baseCost = Math.pow(10, logCp);

    return baseCost * (currentCepci / referenceCepci);
  }

  /**
   * Calculate purchased equipment cost for plate heat exchanger.
   *
   * @param area heat transfer area in m2
   * @return purchased equipment cost in USD (current CEPCI basis)
   */
  public double calcPlateHeatExchangerCost(double area) {
    if (area <= 0) {
      return 0.0;
    }
    // Simplified correlation for plate exchangers
    double baseCost = 1000.0 * Math.pow(area, 0.68);
    return baseCost * (currentCepci / referenceCepci);
  }

  /**
   * Calculate purchased equipment cost for air cooler.
   *
   * @param area heat transfer area in m2
   * @return purchased equipment cost in USD (current CEPCI basis)
   */
  public double calcAirCoolerCost(double area) {
    if (area <= 0) {
      return 0.0;
    }
    // Turton correlation for air cooler
    double k1 = 4.0336;
    double k2 = 0.2341;
    double k3 = 0.0497;

    double logA = Math.log10(Math.max(area, 10.0));
    double logCp = k1 + k2 * logA + k3 * logA * logA;
    double baseCost = Math.pow(10, logCp);

    return baseCost * (currentCepci / referenceCepci);
  }

  // ============================================================================
  // Equipment Cost Methods - Columns
  // ============================================================================

  /**
   * Calculate purchased equipment cost for distillation column shell.
   *
   * @param weight column shell weight in kg
   * @return purchased equipment cost in USD (current CEPCI basis)
   */
  public double calcColumnShellCost(double weight) {
    return calcVerticalVesselCost(weight);
  }

  /**
   * Calculate purchased equipment cost for sieve trays.
   *
   * @param diameter column diameter in meters
   * @param numberOfTrays number of trays
   * @return purchased equipment cost in USD (current CEPCI basis)
   */
  public double calcSieveTraysCost(double diameter, int numberOfTrays) {
    if (diameter <= 0 || numberOfTrays <= 0) {
      return 0.0;
    }
    // Turton correlation for sieve trays: Cp = 468*exp(0.1739*D)
    // where D is diameter in meters, valid for D = 0.3-5 m
    double d = Math.max(0.3, Math.min(diameter, 5.0));
    double costPerTray = 468.0 * Math.exp(0.1739 * d);
    double baseCost = costPerTray * numberOfTrays;

    return baseCost * (currentCepci / referenceCepci);
  }

  /**
   * Calculate purchased equipment cost for valve trays.
   *
   * @param diameter column diameter in meters
   * @param numberOfTrays number of trays
   * @return purchased equipment cost in USD (current CEPCI basis)
   */
  public double calcValveTraysCost(double diameter, int numberOfTrays) {
    // Valve trays typically 1.2x sieve tray cost
    return calcSieveTraysCost(diameter, numberOfTrays) * 1.2;
  }

  /**
   * Calculate purchased equipment cost for bubble cap trays.
   *
   * @param diameter column diameter in meters
   * @param numberOfTrays number of trays
   * @return purchased equipment cost in USD (current CEPCI basis)
   */
  public double calcBubbleCapTraysCost(double diameter, int numberOfTrays) {
    // Bubble cap trays typically 1.5x sieve tray cost
    return calcSieveTraysCost(diameter, numberOfTrays) * 1.5;
  }

  /**
   * Calculate purchased equipment cost for structured packing.
   *
   * @param volume packing volume in m3
   * @param packingType type of packing ("metal", "plastic", "ceramic")
   * @return purchased equipment cost in USD (current CEPCI basis)
   */
  public double calcPackingCost(double volume, String packingType) {
    if (volume <= 0) {
      return 0.0;
    }
    // Cost per m3 of packing
    double costPerM3;
    if ("metal".equalsIgnoreCase(packingType)) {
      costPerM3 = 8000.0; // Metal structured packing
    } else if ("ceramic".equalsIgnoreCase(packingType)) {
      costPerM3 = 3000.0; // Ceramic packing
    } else {
      costPerM3 = 2000.0; // Plastic packing
    }
    return costPerM3 * volume * (currentCepci / referenceCepci);
  }

  // ============================================================================
  // Equipment Cost Methods - Pumps and Compressors
  // ============================================================================

  /**
   * Calculate purchased equipment cost for centrifugal pump.
   *
   * @param power pump power in kW
   * @return purchased equipment cost in USD (current CEPCI basis)
   */
  public double calcCentrifugalPumpCost(double power) {
    if (power <= 0) {
      return 0.0;
    }
    // Turton correlation: log10(Cp) = K1 + K2*log10(S) + K3*(log10(S))^2
    // where S is size factor (power in kW for pumps)
    double k1 = 3.3892;
    double k2 = 0.0536;
    double k3 = 0.1538;

    double logS = Math.log10(Math.max(power, 1.0));
    double logCp = k1 + k2 * logS + k3 * logS * logS;
    double baseCost = Math.pow(10, logCp);

    return baseCost * (currentCepci / referenceCepci);
  }

  /**
   * Calculate purchased equipment cost for centrifugal compressor.
   *
   * @param power compressor power in kW
   * @return purchased equipment cost in USD (current CEPCI basis)
   */
  public double calcCentrifugalCompressorCost(double power) {
    if (power <= 0) {
      return 0.0;
    }
    // Turton correlation for centrifugal compressor
    double k1 = 2.2897;
    double k2 = 1.3604;
    double k3 = -0.1027;

    double logS = Math.log10(Math.max(power, 10.0));
    double logCp = k1 + k2 * logS + k3 * logS * logS;
    double baseCost = Math.pow(10, logCp);

    return baseCost * (currentCepci / referenceCepci);
  }

  /**
   * Calculate purchased equipment cost for reciprocating compressor.
   *
   * @param power compressor power in kW
   * @return purchased equipment cost in USD (current CEPCI basis)
   */
  public double calcReciprocatingCompressorCost(double power) {
    if (power <= 0) {
      return 0.0;
    }
    // Turton correlation for reciprocating compressor
    double k1 = 2.2897;
    double k2 = 1.3604;
    double k3 = -0.1027;

    double logS = Math.log10(Math.max(power, 10.0));
    double logCp = k1 + k2 * logS + k3 * logS * logS;
    double baseCost = Math.pow(10, logCp) * 1.3; // 1.3x centrifugal

    return baseCost * (currentCepci / referenceCepci);
  }

  // ============================================================================
  // Equipment Cost Methods - Piping and Valves
  // ============================================================================

  /**
   * Calculate cost for piping.
   *
   * @param diameter pipe diameter in meters
   * @param length pipe length in meters
   * @param schedule pipe schedule (40, 80, 160)
   * @return cost in USD (current CEPCI basis)
   */
  public double calcPipingCost(double diameter, double length, int schedule) {
    if (diameter <= 0 || length <= 0) {
      return 0.0;
    }
    // Approximate piping cost per meter based on diameter
    // Cost increases with D^1.5 approximately
    double diamInches = diameter / 0.0254;
    double baseCostPerMeter = 50.0 * Math.pow(diamInches, 1.5);

    // Schedule factor
    double scheduleFactor = 1.0;
    if (schedule == 80) {
      scheduleFactor = 1.5;
    } else if (schedule == 160) {
      scheduleFactor = 2.5;
    }

    return baseCostPerMeter * length * scheduleFactor * materialFactor
        * (currentCepci / referenceCepci);
  }

  /**
   * Calculate cost for control valve.
   *
   * @param cv valve Cv
   * @return cost in USD (current CEPCI basis)
   */
  public double calcControlValveCost(double cv) {
    if (cv <= 0) {
      return 0.0;
    }
    // Simplified correlation: cost = 2500 * Cv^0.55
    double baseCost = 2500.0 * Math.pow(cv, 0.55);
    return baseCost * materialFactor * (currentCepci / referenceCepci);
  }

  // ============================================================================
  // Module Cost Methods
  // ============================================================================

  /**
   * Calculate bare module cost from purchased equipment cost.
   *
   * @param purchasedCost purchased equipment cost
   * @param fpFactor pressure factor
   * @param fmFactor material factor
   * @return bare module cost
   */
  public double calcBareModuleCost(double purchasedCost, double fpFactor, double fmFactor) {
    // Bare Module Cost = Cp * FBM where FBM = B1 + B2*Fp*Fm
    // B1 and B2 depend on equipment type, typical values: B1=1.89, B2=1.35
    double b1 = 1.89;
    double b2 = 1.35;
    double fbm = b1 + b2 * fpFactor * fmFactor;
    return purchasedCost * fbm;
  }

  /**
   * Calculate bare module cost with default factors.
   *
   * @param purchasedCost purchased equipment cost
   * @param designPressure design pressure in barg
   * @return bare module cost
   */
  public double calcBareModuleCost(double purchasedCost, double designPressure) {
    double fp = getPressureFactor(designPressure);
    return calcBareModuleCost(purchasedCost, fp, materialFactor);
  }

  /**
   * Calculate total module cost including contingency and engineering.
   *
   * @param bareModuleCost bare module cost
   * @return total module cost
   */
  public double calcTotalModuleCost(double bareModuleCost) {
    return bareModuleCost * (1.0 + contingencyFactor + engineeringFactor);
  }

  /**
   * Calculate grass roots cost for new facility.
   *
   * @param totalModuleCost total module cost
   * @return grass roots cost
   */
  public double calcGrassRootsCost(double totalModuleCost) {
    // Grass roots includes site development, buildings, offsites
    // Typically 1.5x total module cost
    return totalModuleCost * 1.5;
  }

  // ============================================================================
  // Labor Cost Methods
  // ============================================================================

  /**
   * Calculate installation labor man-hours for equipment.
   *
   * @param equipmentWeight equipment weight in kg
   * @param equipmentType equipment type ("vessel", "exchanger", "pump", "compressor")
   * @return labor man-hours
   */
  public double calcInstallationManHours(double equipmentWeight, String equipmentType) {
    if (equipmentWeight <= 0) {
      return 0.0;
    }
    // Man-hours per tonne of equipment
    double manHoursPerTonne;
    if ("vessel".equalsIgnoreCase(equipmentType)) {
      manHoursPerTonne = 30.0;
    } else if ("exchanger".equalsIgnoreCase(equipmentType)) {
      manHoursPerTonne = 40.0;
    } else if ("pump".equalsIgnoreCase(equipmentType)) {
      manHoursPerTonne = 50.0;
    } else if ("compressor".equalsIgnoreCase(equipmentType)) {
      manHoursPerTonne = 60.0;
    } else if ("column".equalsIgnoreCase(equipmentType)) {
      manHoursPerTonne = 35.0;
    } else {
      manHoursPerTonne = 35.0;
    }
    return manHoursPerTonne * equipmentWeight / 1000.0;
  }

  /**
   * Calculate piping installation man-hours.
   *
   * @param pipingWeight piping weight in kg
   * @return labor man-hours
   */
  public double calcPipingInstallationManHours(double pipingWeight) {
    // Typically 80-120 man-hours per tonne of piping
    return 100.0 * pipingWeight / 1000.0;
  }

  // ============================================================================
  // Bill of Materials Methods
  // ============================================================================

  /**
   * Generate bill of materials for vessel.
   *
   * @param shellWeight shell weight in kg
   * @param headsWeight heads weight in kg
   * @param nozzleCount number of nozzles
   * @param internalsWeight internals weight in kg
   * @return list of BOM items as maps
   */
  public List<Map<String, Object>> generateVesselBOM(double shellWeight, double headsWeight,
      int nozzleCount, double internalsWeight) {
    List<Map<String, Object>> bom = new ArrayList<Map<String, Object>>();

    if (shellWeight > 0) {
      Map<String, Object> shell = new LinkedHashMap<String, Object>();
      shell.put("item", "Vessel Shell");
      shell.put("material", materialOfConstruction);
      shell.put("weight_kg", shellWeight);
      shell.put("unit_cost", calcVerticalVesselCost(shellWeight) * 0.6); // Shell portion
      bom.add(shell);
    }

    if (headsWeight > 0) {
      Map<String, Object> heads = new LinkedHashMap<String, Object>();
      heads.put("item", "Vessel Heads (2)");
      heads.put("material", materialOfConstruction);
      heads.put("weight_kg", headsWeight);
      heads.put("unit_cost", headsWeight * 8.0 * materialFactor);
      bom.add(heads);
    }

    if (nozzleCount > 0) {
      Map<String, Object> nozzles = new LinkedHashMap<String, Object>();
      nozzles.put("item", "Nozzles");
      nozzles.put("quantity", nozzleCount);
      nozzles.put("unit_cost", 500.0 * nozzleCount * materialFactor);
      bom.add(nozzles);
    }

    if (internalsWeight > 0) {
      Map<String, Object> internals = new LinkedHashMap<String, Object>();
      internals.put("item", "Internals");
      internals.put("material", materialOfConstruction);
      internals.put("weight_kg", internalsWeight);
      internals.put("unit_cost", internalsWeight * 15.0 * materialFactor);
      bom.add(internals);
    }

    return bom;
  }

  // ============================================================================
  // Cost Summary Methods
  // ============================================================================

  /**
   * Calculate complete cost estimate for equipment.
   *
   * @param purchasedCost purchased equipment cost
   * @param designPressure design pressure in barg
   * @param weightKg equipment weight in kg
   * @param equipmentType equipment type
   */
  public void calculateCostEstimate(double purchasedCost, double designPressure, double weightKg,
      String equipmentType) {
    this.purchasedEquipmentCost = purchasedCost * materialFactor * locationFactor;

    double fp = getPressureFactor(designPressure);
    this.bareModuleCost = calcBareModuleCost(purchasedEquipmentCost, fp, 1.0);
    this.totalModuleCost = calcTotalModuleCost(bareModuleCost);
    this.grassRootsCost = calcGrassRootsCost(totalModuleCost);
  }

  /**
   * Convert cost results to map for JSON export.
   *
   * @return map of cost data
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    // Cost basis
    Map<String, Object> basis = new LinkedHashMap<String, Object>();
    basis.put("referenceCepci", referenceCepci);
    basis.put("currentCepci", currentCepci);
    basis.put("currencyCode", currencyCode);
    basis.put("exchangeRate", exchangeRate);
    basis.put("locationFactor", locationFactor);
    basis.put("materialOfConstruction", materialOfConstruction);
    basis.put("materialFactor", materialFactor);
    result.put("costBasis", basis);

    // Costs
    Map<String, Object> costs = new LinkedHashMap<String, Object>();
    costs.put("purchasedEquipmentCost_USD", purchasedEquipmentCost);
    costs.put("bareModuleCost_USD", bareModuleCost);
    costs.put("totalModuleCost_USD", totalModuleCost);
    costs.put("grassRootsCost_USD", grassRootsCost);
    result.put("costEstimates", costs);

    // Factors
    Map<String, Object> factors = new LinkedHashMap<String, Object>();
    factors.put("installationFactor", installationFactor);
    factors.put("contingencyFactor", contingencyFactor);
    factors.put("engineeringFactor", engineeringFactor);
    result.put("costFactors", factors);

    return result;
  }

  /**
   * Export cost data to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  /**
   * Gets the current CEPCI.
   *
   * @return current CEPCI
   */
  public double getCurrentCepci() {
    return currentCepci;
  }

  /**
   * Sets the current CEPCI.
   *
   * @param cepci current CEPCI
   */
  public void setCurrentCepci(double cepci) {
    this.currentCepci = cepci;
  }

  /**
   * Gets the material factor.
   *
   * @return material factor
   */
  public double getMaterialFactor() {
    return materialFactor;
  }

  /**
   * Sets the material factor.
   *
   * @param factor material factor
   */
  public void setMaterialFactor(double factor) {
    this.materialFactor = factor;
  }

  /**
   * Sets material of construction and updates factor.
   *
   * @param material material name
   */
  public void setMaterialOfConstruction(String material) {
    this.materialOfConstruction = material;
    // Set factor based on material
    if ("Carbon Steel".equalsIgnoreCase(material) || "CS".equalsIgnoreCase(material)) {
      this.materialFactor = FM_CARBON_STEEL;
    } else if ("SS304".equalsIgnoreCase(material) || "304SS".equalsIgnoreCase(material)) {
      this.materialFactor = FM_SS304;
    } else if ("SS316".equalsIgnoreCase(material) || "316SS".equalsIgnoreCase(material)) {
      this.materialFactor = FM_SS316;
    } else if ("SS316L".equalsIgnoreCase(material) || "316LSS".equalsIgnoreCase(material)) {
      this.materialFactor = FM_SS316L;
    } else if ("Monel".equalsIgnoreCase(material)) {
      this.materialFactor = FM_MONEL;
    } else if ("Hastelloy".equalsIgnoreCase(material) || "Hastelloy C".equalsIgnoreCase(material)) {
      this.materialFactor = FM_HASTELLOY_C;
    } else if ("Inconel".equalsIgnoreCase(material)) {
      this.materialFactor = FM_INCONEL;
    } else if ("Titanium".equalsIgnoreCase(material)) {
      this.materialFactor = FM_TITANIUM;
    } else if ("Nickel".equalsIgnoreCase(material)) {
      this.materialFactor = FM_NICKEL;
    }
  }

  /**
   * Gets the material of construction.
   *
   * @return material of construction
   */
  public String getMaterialOfConstruction() {
    return materialOfConstruction;
  }

  /**
   * Gets the location factor.
   *
   * @return location factor
   */
  public double getLocationFactor() {
    return locationFactor;
  }

  /**
   * Sets the location factor.
   *
   * @param factor location factor
   */
  public void setLocationFactor(double factor) {
    this.locationFactor = factor;
  }

  /**
   * Gets the purchased equipment cost.
   *
   * @return purchased equipment cost in USD
   */
  public double getPurchasedEquipmentCost() {
    return purchasedEquipmentCost;
  }

  /**
   * Gets the bare module cost.
   *
   * @return bare module cost in USD
   */
  public double getBareModuleCost() {
    return bareModuleCost;
  }

  /**
   * Gets the total module cost.
   *
   * @return total module cost in USD
   */
  public double getTotalModuleCost() {
    return totalModuleCost;
  }

  /**
   * Gets the grass roots cost.
   *
   * @return grass roots cost in USD
   */
  public double getGrassRootsCost() {
    return grassRootsCost;
  }

  /**
   * Gets the installation factor.
   *
   * @return installation factor
   */
  public double getInstallationFactor() {
    return installationFactor;
  }

  /**
   * Sets the installation factor.
   *
   * @param factor installation factor
   */
  public void setInstallationFactor(double factor) {
    this.installationFactor = factor;
  }

  /**
   * Gets the contingency factor.
   *
   * @return contingency factor
   */
  public double getContingencyFactor() {
    return contingencyFactor;
  }

  /**
   * Sets the contingency factor.
   *
   * @param factor contingency factor
   */
  public void setContingencyFactor(double factor) {
    this.contingencyFactor = factor;
  }

  /**
   * Gets the engineering factor.
   *
   * @return engineering factor
   */
  public double getEngineeringFactor() {
    return engineeringFactor;
  }

  /**
   * Sets the engineering factor.
   *
   * @param factor engineering factor
   */
  public void setEngineeringFactor(double factor) {
    this.engineeringFactor = factor;
  }

  /**
   * Gets the currency code.
   *
   * @return currency code
   */
  public String getCurrencyCode() {
    return currencyCode;
  }

  /**
   * Sets the currency code and exchange rate.
   *
   * @param code currency code
   * @param rate exchange rate from USD
   */
  public void setCurrency(String code, double rate) {
    this.currencyCode = code;
    this.exchangeRate = rate;
  }

  /**
   * Gets the exchange rate from USD.
   *
   * @return exchange rate
   */
  public double getExchangeRate() {
    return exchangeRate;
  }

  /**
   * Set currency using predefined currency code with default exchange rates.
   *
   * <p>
   * Default exchange rates (approximate January 2026):
   * </p>
   * <ul>
   * <li>EUR: 0.92</li>
   * <li>NOK: 11.0</li>
   * <li>GBP: 0.79</li>
   * <li>CNY: 7.25</li>
   * <li>JPY: 155.0</li>
   * </ul>
   *
   * @param code currency code (USD, EUR, NOK, GBP, CNY, JPY)
   */
  public void setCurrencyCode(String code) {
    this.currencyCode = code;
    if (CURRENCY_EUR.equalsIgnoreCase(code)) {
      this.exchangeRate = 0.92;
    } else if (CURRENCY_NOK.equalsIgnoreCase(code)) {
      this.exchangeRate = 11.0;
    } else if (CURRENCY_GBP.equalsIgnoreCase(code)) {
      this.exchangeRate = 0.79;
    } else if (CURRENCY_CNY.equalsIgnoreCase(code)) {
      this.exchangeRate = 7.25;
    } else if (CURRENCY_JPY.equalsIgnoreCase(code)) {
      this.exchangeRate = 155.0;
    } else {
      this.exchangeRate = 1.0; // USD default
    }
  }

  /**
   * Convert USD cost to current currency.
   *
   * @param usdCost cost in USD
   * @return cost in current currency
   */
  public double convertFromUSD(double usdCost) {
    return usdCost * exchangeRate;
  }

  /**
   * Convert current currency cost to USD.
   *
   * @param localCost cost in local currency
   * @return cost in USD
   */
  public double convertToUSD(double localCost) {
    if (exchangeRate <= 0) {
      return localCost;
    }
    return localCost / exchangeRate;
  }

  /**
   * Set location factor by region name.
   *
   * @param region region name ("US Gulf Coast", "North Sea", "Middle East", etc.)
   */
  public void setLocationByRegion(String region) {
    if (region == null) {
      this.locationFactor = LOC_US_GULF_COAST;
      return;
    }

    String regionLower = region.toLowerCase();
    if (regionLower.contains("gulf coast") || regionLower.contains("usgc")) {
      this.locationFactor = LOC_US_GULF_COAST;
    } else if (regionLower.contains("midwest")) {
      this.locationFactor = LOC_US_MIDWEST;
    } else if (regionLower.contains("west coast") || regionLower.contains("california")) {
      this.locationFactor = LOC_US_WEST_COAST;
    } else if (regionLower.contains("north sea") || regionLower.contains("norway")
        || regionLower.contains("norwegian")) {
      this.locationFactor = LOC_NORTH_SEA;
    } else if (regionLower.contains("western europe") || regionLower.contains("uk")
        || regionLower.contains("netherlands") || regionLower.contains("germany")) {
      this.locationFactor = LOC_WESTERN_EUROPE;
    } else if (regionLower.contains("middle east") || regionLower.contains("qatar")
        || regionLower.contains("saudi") || regionLower.contains("uae")
        || regionLower.contains("abu dhabi")) {
      this.locationFactor = LOC_MIDDLE_EAST;
    } else if (regionLower.contains("southeast asia") || regionLower.contains("singapore")
        || regionLower.contains("malaysia") || regionLower.contains("indonesia")) {
      this.locationFactor = LOC_SOUTHEAST_ASIA;
    } else if (regionLower.contains("china")) {
      this.locationFactor = LOC_CHINA;
    } else if (regionLower.contains("australia")) {
      this.locationFactor = LOC_AUSTRALIA;
    } else if (regionLower.contains("brazil")) {
      this.locationFactor = LOC_BRAZIL;
    } else if (regionLower.contains("west africa") || regionLower.contains("nigeria")
        || regionLower.contains("angola")) {
      this.locationFactor = LOC_WEST_AFRICA;
    } else {
      this.locationFactor = LOC_US_GULF_COAST; // Default
    }
  }

  /**
   * Get all available location factors.
   *
   * @return map of region names to location factors
   */
  public static Map<String, Double> getAvailableLocationFactors() {
    Map<String, Double> factors = new LinkedHashMap<String, Double>();
    factors.put("US Gulf Coast", LOC_US_GULF_COAST);
    factors.put("US Midwest", LOC_US_MIDWEST);
    factors.put("US West Coast", LOC_US_WEST_COAST);
    factors.put("Western Europe", LOC_WESTERN_EUROPE);
    factors.put("North Sea / Norway", LOC_NORTH_SEA);
    factors.put("Middle East", LOC_MIDDLE_EAST);
    factors.put("Southeast Asia", LOC_SOUTHEAST_ASIA);
    factors.put("China", LOC_CHINA);
    factors.put("Australia", LOC_AUSTRALIA);
    factors.put("Brazil", LOC_BRAZIL);
    factors.put("West Africa", LOC_WEST_AFRICA);
    return factors;
  }

  /**
   * Get default exchange rates for available currencies.
   *
   * @return map of currency codes to exchange rates (from USD)
   */
  public static Map<String, Double> getDefaultExchangeRates() {
    Map<String, Double> rates = new LinkedHashMap<String, Double>();
    rates.put(CURRENCY_USD, 1.0);
    rates.put(CURRENCY_EUR, 0.92);
    rates.put(CURRENCY_NOK, 11.0);
    rates.put(CURRENCY_GBP, 0.79);
    rates.put(CURRENCY_CNY, 7.25);
    rates.put(CURRENCY_JPY, 155.0);
    return rates;
  }

  /**
   * Format cost value with currency symbol.
   *
   * @param cost cost value in current currency
   * @return formatted string with currency symbol
   */
  public String formatCost(double cost) {
    String symbol;
    if (CURRENCY_EUR.equalsIgnoreCase(currencyCode)) {
      symbol = "\u20AC"; // Euro symbol
    } else if (CURRENCY_GBP.equalsIgnoreCase(currencyCode)) {
      symbol = "\u00A3"; // Pound symbol
    } else if (CURRENCY_NOK.equalsIgnoreCase(currencyCode)) {
      symbol = "kr";
    } else if (CURRENCY_CNY.equalsIgnoreCase(currencyCode)) {
      symbol = "\u00A5"; // Yuan symbol
    } else if (CURRENCY_JPY.equalsIgnoreCase(currencyCode)) {
      symbol = "\u00A5"; // Yen symbol
    } else {
      symbol = "$";
    }

    if (cost >= 1e9) {
      return String.format("%s%.2fB", symbol, cost / 1e9);
    } else if (cost >= 1e6) {
      return String.format("%s%.2fM", symbol, cost / 1e6);
    } else if (cost >= 1e3) {
      return String.format("%s%.1fK", symbol, cost / 1e3);
    } else {
      return String.format("%s%.2f", symbol, cost);
    }
  }
}
