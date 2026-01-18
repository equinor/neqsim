package neqsim.process.fielddevelopment.evaluation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * Production allocation and metering calculations.
 *
 * <p>
 * Handles production allocation when multiple wells or sources are commingled, accounting for
 * measurement uncertainty. Key tool for fiscal metering, production reporting, and revenue
 * allocation.
 * </p>
 *
 * <h2>Metering Technologies Supported</h2>
 * <ul>
 * <li><b>Ultrasonic</b> - ±0.5% (fiscal gas)</li>
 * <li><b>Coriolis</b> - ±0.1% (fiscal liquid)</li>
 * <li><b>Differential Pressure</b> - ±1.0% (process)</li>
 * <li><b>Multiphase</b> - ±2-5% (wellhead)</li>
 * <li><b>Venturi/Orifice</b> - ±1.5% (gas)</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * {@code
 * ProductionAllocator allocator = new ProductionAllocator();
 * allocator.addSource("Well-A", wellAStream, MeteringType.MULTIPHASE);
 * allocator.addSource("Well-B", wellBStream, MeteringType.MULTIPHASE);
 * allocator.setExportMeter("Export", exportStream, MeteringType.ULTRASONIC);
 * 
 * Map<String, Double> allocation = allocator.allocateByOil();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProductionAllocator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Production sources (wells, manifolds). */
  private final List<ProductionSource> sources = new ArrayList<>();

  /** Export meter reference. */
  private ProductionSource exportMeter;

  // ============================================================================
  // METERING TYPES
  // ============================================================================

  /**
   * Metering technology types with typical uncertainties.
   */
  public enum MeteringType {
    /** Ultrasonic - high accuracy gas metering. */
    ULTRASONIC(0.005, "Ultrasonic"),

    /** Coriolis - high accuracy liquid metering. */
    CORIOLIS(0.001, "Coriolis"),

    /** Differential pressure (orifice, venturi). */
    DIFFERENTIAL_PRESSURE(0.010, "DP"),

    /** Vortex shedding meter. */
    VORTEX(0.015, "Vortex"),

    /** Turbine meter. */
    TURBINE(0.005, "Turbine"),

    /** Multiphase meter. */
    MULTIPHASE(0.03, "Multiphase"),

    /** Test separator allocation. */
    TEST_SEPARATOR(0.02, "Test Sep"),

    /** Estimated/calculated. */
    ESTIMATED(0.10, "Estimated");

    private final double uncertainty;
    private final String displayName;

    MeteringType(double uncertainty, String displayName) {
      this.uncertainty = uncertainty;
      this.displayName = displayName;
    }

    /**
     * Gets typical measurement uncertainty.
     *
     * @return uncertainty as fraction (e.g., 0.005 = 0.5%)
     */
    public double getUncertainty() {
      return uncertainty;
    }

    public String getDisplayName() {
      return displayName;
    }
  }

  // ============================================================================
  // CONFIGURATION
  // ============================================================================

  /**
   * Adds a production source for allocation.
   *
   * @param name source identifier (e.g., well name)
   * @param stream production stream
   * @param meterType metering technology
   * @return this for chaining
   */
  public ProductionAllocator addSource(String name, StreamInterface stream,
      MeteringType meterType) {
    sources.add(new ProductionSource(name, stream, meterType));
    return this;
  }

  /**
   * Adds a production source with multiphase meter (default).
   *
   * @param name source name
   * @param stream production stream
   * @return this for chaining
   */
  public ProductionAllocator addSource(String name, StreamInterface stream) {
    return addSource(name, stream, MeteringType.MULTIPHASE);
  }

  /**
   * Sets the export/sales meter reference.
   *
   * @param name meter name
   * @param stream export stream
   * @param meterType metering technology
   * @return this for chaining
   */
  public ProductionAllocator setExportMeter(String name, StreamInterface stream,
      MeteringType meterType) {
    this.exportMeter = new ProductionSource(name, stream, meterType);
    return this;
  }

  // ============================================================================
  // ALLOCATION METHODS
  // ============================================================================

  /**
   * Allocates production by oil contribution.
   *
   * <p>
   * Oil-based allocation is common for commingled oil production. Each source is allocated a
   * fraction of the export based on its oil flow rate contribution.
   * </p>
   *
   * @return map of source name to allocation fraction
   */
  public Map<String, Double> allocateByOil() {
    Map<String, Double> allocation = new HashMap<>();

    double totalOil = 0;
    for (ProductionSource source : sources) {
      totalOil += getOilFlowRate(source.stream);
    }

    if (totalOil <= 0) {
      // Equal allocation if no oil
      double equalShare = sources.isEmpty() ? 0 : 1.0 / sources.size();
      for (ProductionSource source : sources) {
        allocation.put(source.name, equalShare);
      }
      return allocation;
    }

    for (ProductionSource source : sources) {
      double fraction = getOilFlowRate(source.stream) / totalOil;
      allocation.put(source.name, fraction);
    }

    return allocation;
  }

  /**
   * Allocates production by gas contribution.
   *
   * @return map of source name to allocation fraction
   */
  public Map<String, Double> allocateByGas() {
    Map<String, Double> allocation = new HashMap<>();

    double totalGas = 0;
    for (ProductionSource source : sources) {
      totalGas += getGasFlowRate(source.stream);
    }

    if (totalGas <= 0) {
      double equalShare = sources.isEmpty() ? 0 : 1.0 / sources.size();
      for (ProductionSource source : sources) {
        allocation.put(source.name, equalShare);
      }
      return allocation;
    }

    for (ProductionSource source : sources) {
      double fraction = getGasFlowRate(source.stream) / totalGas;
      allocation.put(source.name, fraction);
    }

    return allocation;
  }

  /**
   * Allocates production by total mass.
   *
   * @return map of source name to allocation fraction
   */
  public Map<String, Double> allocateByMass() {
    Map<String, Double> allocation = new HashMap<>();

    double totalMass = 0;
    for (ProductionSource source : sources) {
      totalMass += source.stream.getFlowRate("kg/hr");
    }

    if (totalMass <= 0) {
      double equalShare = sources.isEmpty() ? 0 : 1.0 / sources.size();
      for (ProductionSource source : sources) {
        allocation.put(source.name, equalShare);
      }
      return allocation;
    }

    for (ProductionSource source : sources) {
      double fraction = source.stream.getFlowRate("kg/hr") / totalMass;
      allocation.put(source.name, fraction);
    }

    return allocation;
  }

  /**
   * Allocates production by energy content (hydrocarbon heating value).
   *
   * @return map of source name to allocation fraction
   */
  public Map<String, Double> allocateByEnergy() {
    Map<String, Double> allocation = new HashMap<>();

    double totalEnergy = 0;
    for (ProductionSource source : sources) {
      totalEnergy += getEnergyRate(source.stream);
    }

    if (totalEnergy <= 0) {
      double equalShare = sources.isEmpty() ? 0 : 1.0 / sources.size();
      for (ProductionSource source : sources) {
        allocation.put(source.name, equalShare);
      }
      return allocation;
    }

    for (ProductionSource source : sources) {
      double fraction = getEnergyRate(source.stream) / totalEnergy;
      allocation.put(source.name, fraction);
    }

    return allocation;
  }

  // ============================================================================
  // ALLOCATED QUANTITIES
  // ============================================================================

  /**
   * Gets allocated oil volume for each source.
   *
   * @param exportOilSm3d export oil volume in Sm³/d
   * @return map of source name to allocated volume
   */
  public Map<String, Double> getAllocatedOilVolumes(double exportOilSm3d) {
    Map<String, Double> allocation = allocateByOil();
    Map<String, Double> volumes = new HashMap<>();
    for (Map.Entry<String, Double> entry : allocation.entrySet()) {
      volumes.put(entry.getKey(), entry.getValue() * exportOilSm3d);
    }
    return volumes;
  }

  /**
   * Gets allocated gas volume for each source.
   *
   * @param exportGasSm3d export gas volume in Sm³/d
   * @return map of source name to allocated volume
   */
  public Map<String, Double> getAllocatedGasVolumes(double exportGasSm3d) {
    Map<String, Double> allocation = allocateByGas();
    Map<String, Double> volumes = new HashMap<>();
    for (Map.Entry<String, Double> entry : allocation.entrySet()) {
      volumes.put(entry.getKey(), entry.getValue() * exportGasSm3d);
    }
    return volumes;
  }

  // ============================================================================
  // UNCERTAINTY ANALYSIS
  // ============================================================================

  /**
   * Calculates overall allocation uncertainty.
   *
   * <p>
   * Uses root-sum-square method to combine uncertainties from individual meters.
   * </p>
   *
   * @return overall uncertainty as fraction
   */
  public double getOverallUncertainty() {
    if (sources.isEmpty()) {
      return 0.0;
    }

    double sumSquares = 0;
    for (ProductionSource source : sources) {
      double u = source.meterType.getUncertainty();
      sumSquares += u * u;
    }

    // Add export meter uncertainty if defined
    if (exportMeter != null) {
      double u = exportMeter.meterType.getUncertainty();
      sumSquares += u * u;
    }

    return Math.sqrt(sumSquares);
  }

  /**
   * Gets uncertainty for a specific source's allocation.
   *
   * @param sourceName source name
   * @return uncertainty as fraction
   */
  public double getSourceUncertainty(String sourceName) {
    for (ProductionSource source : sources) {
      if (source.name.equals(sourceName)) {
        return source.meterType.getUncertainty();
      }
    }
    return 0.0;
  }

  /**
   * Gets allocated volume with uncertainty bounds.
   *
   * @param sourceName source name
   * @param exportVolume total export volume
   * @return array [allocated, low, high]
   */
  public double[] getAllocatedWithUncertainty(String sourceName, double exportVolume) {
    Map<String, Double> allocation = allocateByOil();
    Double fraction = allocation.get(sourceName);
    if (fraction == null) {
      return new double[] {0, 0, 0};
    }

    double allocated = fraction * exportVolume;
    double uncertainty = getSourceUncertainty(sourceName);
    double range = allocated * uncertainty;

    return new double[] {allocated, allocated - range, allocated + range};
  }

  // ============================================================================
  // IMBALANCE DETECTION
  // ============================================================================

  /**
   * Calculates mass balance imbalance.
   *
   * @return imbalance as fraction of export
   */
  public double getMassImbalance() {
    if (exportMeter == null) {
      return 0.0;
    }

    double totalSourceMass = 0;
    for (ProductionSource source : sources) {
      totalSourceMass += source.stream.getFlowRate("kg/hr");
    }

    double exportMass = exportMeter.stream.getFlowRate("kg/hr");

    if (exportMass <= 0) {
      return 0.0;
    }

    return (totalSourceMass - exportMass) / exportMass;
  }

  /**
   * Checks if imbalance exceeds acceptable tolerance.
   *
   * @param toleranceFraction acceptable tolerance (e.g., 0.02 for 2%)
   * @return true if imbalance is acceptable
   */
  public boolean isBalanceAcceptable(double toleranceFraction) {
    return Math.abs(getMassImbalance()) <= toleranceFraction;
  }

  // ============================================================================
  // REPORT GENERATION
  // ============================================================================

  /**
   * Generates an allocation report.
   *
   * @return markdown formatted report
   */
  public String generateReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("# Production Allocation Report\n\n");

    // Summary
    sb.append("## Sources\n\n");
    sb.append("| Source | Meter Type | Uncertainty | Oil (m³/h) | Gas (m³/h) |\n");
    sb.append("|--------|------------|-------------|------------|------------|\n");
    for (ProductionSource source : sources) {
      sb.append(String.format("| %s | %s | ±%.1f%% | %.1f | %.1f |\n", source.name,
          source.meterType.getDisplayName(), source.meterType.getUncertainty() * 100,
          getOilFlowRate(source.stream), getGasFlowRate(source.stream)));
    }

    sb.append("\n## Allocation Fractions\n\n");
    Map<String, Double> oilAlloc = allocateByOil();
    Map<String, Double> gasAlloc = allocateByGas();

    sb.append("| Source | Oil Allocation | Gas Allocation |\n");
    sb.append("|--------|----------------|----------------|\n");
    for (ProductionSource source : sources) {
      Double oilFrac = oilAlloc.get(source.name);
      Double gasFrac = gasAlloc.get(source.name);
      sb.append(String.format("| %s | %.1f%% | %.1f%% |\n", source.name,
          (oilFrac != null ? oilFrac : 0) * 100, (gasFrac != null ? gasFrac : 0) * 100));
    }

    // Uncertainty
    sb.append(String.format("\n**Overall Uncertainty:** ±%.2f%%\n", getOverallUncertainty() * 100));

    // Imbalance
    if (exportMeter != null) {
      sb.append(String.format("**Mass Imbalance:** %.2f%%\n", getMassImbalance() * 100));
      sb.append(String.format("**Balance Status:** %s\n",
          isBalanceAcceptable(0.02) ? "✓ OK" : "⚠️ Exceeds 2%"));
    }

    return sb.toString();
  }

  // ============================================================================
  // HELPERS
  // ============================================================================

  /**
   * Gets oil flow rate from a stream.
   *
   * @param stream the stream to get oil flow rate from
   * @return oil flow rate in m3/hr, or 0.0 if no oil phase
   */
  private double getOilFlowRate(StreamInterface stream) {
    if (stream.getFluid().hasPhaseType(PhaseType.OIL)) {
      return stream.getFluid().getPhase(PhaseType.OIL).getFlowRate("m3/hr");
    }
    return 0.0;
  }

  /**
   * Gets gas flow rate from a stream.
   *
   * @param stream the stream to get gas flow rate from
   * @return gas flow rate in m3/hr, or 0.0 if no gas phase
   */
  private double getGasFlowRate(StreamInterface stream) {
    if (stream.getFluid().hasPhaseType(PhaseType.GAS)) {
      return stream.getFluid().getPhase(PhaseType.GAS).getFlowRate("m3/hr");
    }
    return 0.0;
  }

  /**
   * Gets energy rate (heating value * flow).
   *
   * @param stream the stream to calculate energy rate from
   * @return energy rate in MJ/hr
   */
  private double getEnergyRate(StreamInterface stream) {
    // Simplified: use mass flow * typical energy content
    double massFlow = stream.getFlowRate("kg/hr");
    // Typical hydrocarbon: ~45 MJ/kg
    return massFlow * 45.0;
  }

  // ============================================================================
  // INNER CLASSES
  // ============================================================================

  /**
   * Production source with metering information.
   */
  private static class ProductionSource implements Serializable {
    private static final long serialVersionUID = 1000L;

    final String name;
    final StreamInterface stream;
    final MeteringType meterType;

    ProductionSource(String name, StreamInterface stream, MeteringType meterType) {
      this.name = name;
      this.stream = stream;
      this.meterType = meterType;
    }
  }
}
