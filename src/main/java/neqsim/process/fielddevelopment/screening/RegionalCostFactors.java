package neqsim.process.fielddevelopment.screening;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Regional cost adjustment factors for field development economics.
 *
 * <p>
 * This class provides location-specific multipliers to adjust base cost estimates for different
 * regions worldwide. The base costs in {@link EconomicsEstimator} are calibrated for the Norwegian
 * Continental Shelf (NCS). Use these factors to adjust estimates for other regions.
 * </p>
 *
 * <h2>Factor Categories</h2>
 * <ul>
 * <li><b>CAPEX Factor</b>: Multiplier for capital costs (facilities, equipment, wells)</li>
 * <li><b>OPEX Factor</b>: Multiplier for operating costs</li>
 * <li><b>Well Cost Factor</b>: Specific adjustment for drilling and completion</li>
 * <li><b>Labor Factor</b>: Adjustment for local labor costs</li>
 * </ul>
 *
 * <h2>Cost Variation Drivers</h2>
 * <p>
 * Regional cost differences are driven by:
 * </p>
 * <ul>
 * <li>Labor costs and productivity</li>
 * <li>Material and equipment logistics</li>
 * <li>Regulatory requirements and compliance</li>
 * <li>Infrastructure availability</li>
 * <li>Weather/environmental conditions</li>
 * <li>Local content requirements</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Get factors for Brazil
 * RegionalCostFactors brazilFactors = RegionalCostFactors.forRegion("BR");
 * 
 * // Apply to NCS-based estimate
 * double ncsCAPEX = 800; // MUSD
 * double brazilCAPEX = ncsCAPEX * brazilFactors.getCapexFactor();
 * 
 * // Use with EconomicsEstimator
 * EconomicsEstimator estimator = new EconomicsEstimator();
 * EconomicsReport ncsReport = estimator.estimate(concept, facility);
 * double adjustedCAPEX = ncsReport.getTotalCapexMUSD() * brazilFactors.getCapexFactor();
 * }</pre>
 *
 * <h2>Data Sources</h2>
 * <p>
 * Cost factors are based on industry benchmarks and public data. They represent typical ratios
 * relative to NCS costs and should be validated against project-specific data for detailed
 * estimates.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see EconomicsEstimator
 */
public final class RegionalCostFactors implements Serializable {
  private static final long serialVersionUID = 1000L;

  // ============================================================================
  // PREDEFINED REGIONS
  // ============================================================================

  private static final Map<String, RegionalCostFactors> REGISTRY =
      new LinkedHashMap<String, RegionalCostFactors>();

  static {
    // Norwegian Continental Shelf (baseline)
    register(new RegionalCostFactors("NO", "Norwegian Continental Shelf", 1.0, 1.0, 1.0, 1.0,
        "High-cost mature basin with excellent infrastructure"));

    // United Kingdom Continental Shelf
    register(new RegionalCostFactors("UK", "UK Continental Shelf", 0.95, 0.90, 0.90, 0.95,
        "Similar to NCS but slightly lower costs"));

    // United States - Gulf of Mexico
    register(new RegionalCostFactors("US-GOM", "Gulf of Mexico", 0.85, 0.80, 0.75, 0.85,
        "Mature basin with extensive infrastructure and competitive contractor market"));

    // United States - Permian Basin
    register(new RegionalCostFactors("US-PERMIAN", "Permian Basin", 0.60, 0.55, 0.50, 0.70,
        "Onshore shale with highly competitive drilling market"));

    // Brazil - Offshore
    register(new RegionalCostFactors("BR", "Brazil Offshore", 1.10, 1.05, 1.15, 0.80,
        "Deep water pre-salt with local content requirements"));

    // Brazil - Pre-Salt
    register(new RegionalCostFactors("BR-PS", "Brazil Pre-Salt", 1.20, 1.10, 1.25, 0.80,
        "Ultra-deep water with technical complexity"));

    // Angola - Offshore
    register(new RegionalCostFactors("AO", "Angola Offshore", 1.15, 1.10, 1.20, 0.60,
        "Deep water with logistics challenges"));

    // Nigeria - Offshore
    register(new RegionalCostFactors("NG", "Nigeria Offshore", 1.10, 1.15, 1.10, 0.55,
        "Security and logistics challenges offset lower labor"));

    // Australia - Offshore
    register(new RegionalCostFactors("AU", "Australia Offshore", 1.15, 1.10, 1.10, 1.10,
        "Remote location with high labor costs"));

    // Australia - Browse/Carnarvon
    register(new RegionalCostFactors("AU-NW", "Australia NW Shelf", 1.20, 1.15, 1.15, 1.15,
        "Remote location with cyclone exposure"));

    // Malaysia
    register(new RegionalCostFactors("MY", "Malaysia Offshore", 0.70, 0.65, 0.70, 0.50,
        "Established basin with lower labor costs"));

    // Indonesia
    register(new RegionalCostFactors("ID", "Indonesia Offshore", 0.65, 0.60, 0.65, 0.45,
        "Lower costs but regulatory complexity"));

    // United Arab Emirates
    register(new RegionalCostFactors("AE", "UAE Offshore", 0.80, 0.75, 0.85, 0.70,
        "Shallow water with good infrastructure"));

    // Saudi Arabia
    register(new RegionalCostFactors("SA", "Saudi Arabia", 0.70, 0.65, 0.75, 0.65,
        "State-supported infrastructure, lower costs"));

    // Qatar
    register(new RegionalCostFactors("QA", "Qatar Offshore", 0.75, 0.70, 0.80, 0.70,
        "Mature gas infrastructure"));

    // Canada - Atlantic
    register(new RegionalCostFactors("CA-ATL", "Canada Atlantic", 1.10, 1.05, 1.05, 1.00,
        "Harsh environment, mature infrastructure"));

    // Canada - Alberta
    register(new RegionalCostFactors("CA-AB", "Canada Alberta", 0.75, 0.70, 0.65, 0.90,
        "Onshore with competitive market"));

    // Guyana
    register(new RegionalCostFactors("GY", "Guyana Offshore", 0.95, 0.90, 1.00, 0.50,
        "Emerging basin with limited infrastructure"));

    // Egypt
    register(new RegionalCostFactors("EG", "Egypt Offshore", 0.75, 0.70, 0.80, 0.50,
        "Established infrastructure, lower labor costs"));

    // Kazakhstan - Caspian
    register(new RegionalCostFactors("KZ", "Kazakhstan Caspian", 0.85, 0.80, 0.90, 0.55,
        "Challenging logistics, established operators"));

    // Mozambique
    register(new RegionalCostFactors("MZ", "Mozambique Offshore", 1.25, 1.20, 1.30, 0.50,
        "Emerging basin with limited infrastructure"));

    // Trinidad and Tobago
    register(new RegionalCostFactors("TT", "Trinidad & Tobago", 0.80, 0.75, 0.85, 0.65,
        "Mature gas basin with good infrastructure"));

    // Mexico - Gulf
    register(new RegionalCostFactors("MX", "Mexico Gulf", 0.80, 0.75, 0.80, 0.60,
        "Emerging competitive market post-reform"));
  }

  // ============================================================================
  // INSTANCE VARIABLES
  // ============================================================================

  private final String regionCode;
  private final String regionName;
  private final double capexFactor;
  private final double opexFactor;
  private final double wellCostFactor;
  private final double laborFactor;
  private final String notes;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates regional cost factors.
   *
   * @param regionCode region code (e.g., "BR", "US-GOM")
   * @param regionName full region name
   * @param capexFactor CAPEX multiplier relative to NCS (1.0 = NCS baseline)
   * @param opexFactor OPEX multiplier relative to NCS
   * @param wellCostFactor well cost multiplier relative to NCS
   * @param laborFactor labor cost multiplier relative to NCS
   * @param notes additional notes about the region
   */
  public RegionalCostFactors(String regionCode, String regionName, double capexFactor,
      double opexFactor, double wellCostFactor, double laborFactor, String notes) {
    this.regionCode = regionCode;
    this.regionName = regionName;
    this.capexFactor = capexFactor;
    this.opexFactor = opexFactor;
    this.wellCostFactor = wellCostFactor;
    this.laborFactor = laborFactor;
    this.notes = notes;
  }

  /**
   * Creates regional cost factors with a single overall factor.
   *
   * @param regionCode region code
   * @param regionName full region name
   * @param overallFactor single factor applied to all cost categories
   */
  public RegionalCostFactors(String regionCode, String regionName, double overallFactor) {
    this(regionCode, regionName, overallFactor, overallFactor, overallFactor, overallFactor, "");
  }

  // ============================================================================
  // STATIC FACTORY METHODS
  // ============================================================================

  /**
   * Gets cost factors for a predefined region.
   *
   * @param regionCode region code (e.g., "BR", "US-GOM", "NO")
   * @return cost factors, or null if not found
   */
  public static RegionalCostFactors forRegion(String regionCode) {
    if (regionCode == null) {
      return null;
    }
    return REGISTRY.get(regionCode.toUpperCase());
  }

  /**
   * Gets cost factors for a region, with fallback to baseline.
   *
   * @param regionCode region code
   * @return cost factors (NCS baseline if region not found)
   */
  public static RegionalCostFactors forRegionOrDefault(String regionCode) {
    RegionalCostFactors factors = forRegion(regionCode);
    return factors != null ? factors : REGISTRY.get("NO");
  }

  /**
   * Checks if a region is registered.
   *
   * @param regionCode region code
   * @return true if registered
   */
  public static boolean isRegistered(String regionCode) {
    return regionCode != null && REGISTRY.containsKey(regionCode.toUpperCase());
  }

  /**
   * Gets all registered regions.
   *
   * @return unmodifiable map of all regions
   */
  public static Map<String, RegionalCostFactors> getAllRegions() {
    return Collections.unmodifiableMap(REGISTRY);
  }

  /**
   * Registers a custom region.
   *
   * @param factors cost factors to register
   */
  public static void register(RegionalCostFactors factors) {
    REGISTRY.put(factors.getRegionCode().toUpperCase(), factors);
  }

  // ============================================================================
  // GETTERS
  // ============================================================================

  /**
   * Gets the region code.
   *
   * @return region code
   */
  public String getRegionCode() {
    return regionCode;
  }

  /**
   * Gets the region name.
   *
   * @return region name
   */
  public String getRegionName() {
    return regionName;
  }

  /**
   * Gets the CAPEX adjustment factor.
   *
   * <p>
   * Multiply NCS-based CAPEX by this factor to get regional estimate.
   * </p>
   *
   * @return CAPEX factor (1.0 = NCS baseline)
   */
  public double getCapexFactor() {
    return capexFactor;
  }

  /**
   * Gets the OPEX adjustment factor.
   *
   * @return OPEX factor
   */
  public double getOpexFactor() {
    return opexFactor;
  }

  /**
   * Gets the well cost adjustment factor.
   *
   * @return well cost factor
   */
  public double getWellCostFactor() {
    return wellCostFactor;
  }

  /**
   * Gets the labor cost adjustment factor.
   *
   * @return labor factor
   */
  public double getLaborFactor() {
    return laborFactor;
  }

  /**
   * Gets notes about the region.
   *
   * @return notes
   */
  public String getNotes() {
    return notes;
  }

  // ============================================================================
  // COST ADJUSTMENT METHODS
  // ============================================================================

  /**
   * Adjusts a CAPEX value from NCS baseline to this region.
   *
   * @param ncsCAPEX CAPEX in NCS terms
   * @return adjusted CAPEX for this region
   */
  public double adjustCapex(double ncsCAPEX) {
    return ncsCAPEX * capexFactor;
  }

  /**
   * Adjusts an OPEX value from NCS baseline to this region.
   *
   * @param ncsOPEX OPEX in NCS terms
   * @return adjusted OPEX for this region
   */
  public double adjustOpex(double ncsOPEX) {
    return ncsOPEX * opexFactor;
  }

  /**
   * Adjusts a well cost from NCS baseline to this region.
   *
   * @param ncsWellCost well cost in NCS terms
   * @return adjusted well cost for this region
   */
  public double adjustWellCost(double ncsWellCost) {
    return ncsWellCost * wellCostFactor;
  }

  /**
   * Gets the overall weighted cost factor.
   *
   * <p>
   * Calculated as weighted average of CAPEX (60%), OPEX (25%), and well costs (15%).
   * </p>
   *
   * @return weighted overall factor
   */
  public double getOverallFactor() {
    return 0.60 * capexFactor + 0.25 * opexFactor + 0.15 * wellCostFactor;
  }

  // ============================================================================
  // UTILITY METHODS
  // ============================================================================

  /**
   * Gets a summary table of all registered regions.
   *
   * @return formatted table string
   */
  public static String getSummaryTable() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("%-10s %-25s %8s %8s %8s %8s%n", "Code", "Region", "CAPEX", "OPEX",
        "Well", "Labor"));
    sb.append(repeatChar('-', 75)).append("\n");

    for (RegionalCostFactors f : REGISTRY.values()) {
      sb.append(String.format("%-10s %-25s %8.2f %8.2f %8.2f %8.2f%n", f.getRegionCode(),
          truncate(f.getRegionName(), 25), f.getCapexFactor(), f.getOpexFactor(),
          f.getWellCostFactor(), f.getLaborFactor()));
    }

    return sb.toString();
  }

  private static String repeatChar(char c, int n) {
    StringBuilder sb = new StringBuilder(n);
    for (int i = 0; i < n; i++) {
      sb.append(c);
    }
    return sb.toString();
  }

  private static String truncate(String s, int maxLen) {
    if (s == null || s.length() <= maxLen) {
      return s;
    }
    return s.substring(0, maxLen - 2) + "..";
  }

  @Override
  public String toString() {
    return String.format("RegionalCostFactors[%s: CAPEX=%.2f, OPEX=%.2f, Wells=%.2f]", regionCode,
        capexFactor, opexFactor, wellCostFactor);
  }
}
