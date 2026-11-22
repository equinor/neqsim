package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Generates feed fluids at different GOR by recombining gas and oil phases.
 *
 * <p>
 * This is a simple, physically-based approach:
 * <ol>
 * <li>Start with separated gas and oil phases from reference fluid</li>
 * <li>Mix at different ratios to achieve target GOR</li>
 * <li>Add water based on water cut</li>
 * </ol>
 *
 * <p>
 * The target GOR and water cut are supplied scenario inputs. This class does not calculate reservoir depletion,
 * drawdown-dependent relative permeability, or gas-cap coning.
 *
 * <h2>Performance Optimization</h2>
 * <p>
 * The generator includes a fluid cache keyed by (GOR, WC) to avoid regenerating the same fluid composition multiple
 * times during parallel VFP table generation.
 * </p>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>
 * FluidMagicInput input = FluidMagicInput.fromE300File("FLUID.E300");
 * input.setGORRange(250, 10000);
 * input.setWaterCutRange(0.05, 0.60);
 * input.separateToStandardConditions();
 *
 * RecombinationFlashGenerator generator = new RecombinationFlashGenerator(input);
 *
 * // Generate fluid at GOR=1000, WC=20%
 * SystemInterface fluid = generator.generateFluid(1000.0, 0.20, 10000.0, 353.15, 50.0);
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 * @see FluidMagicInput
 * @see MultiScenarioVFPGenerator
 */
public class RecombinationFlashGenerator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1002L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(RecombinationFlashGenerator.class);

  /** Molecular weight of water in kg/kmol. */
  private static final double MW_WATER = 18.015;

  /** Standard temperature in Kelvin (15°C). */
  private static final double STD_TEMPERATURE_K = 288.15;

  /** Standard pressure in bara (1 atm). */
  private static final double STD_PRESSURE_BARA = 1.01325;

  // Phases from FluidMagicInput
  private SystemInterface gasPhase;
  private SystemInterface oilPhase;
  private SystemInterface waterPhase;

  // Std condition volumes for scaling
  private double gasStdVolumePerMole;
  private double oilStdVolumePerMole;
  private double waterStdVolumePerMole;

  // Fluid cache for performance (keyed by GOR_WC string)
  private transient Map<String, SystemInterface> fluidCache;
  private boolean enableCaching = true;
  private int cacheHits = 0;
  private int cacheMisses = 0;

  /**
   * Create generator from FluidMagicInput.
   *
   * @param input FluidMagicInput with separated phases
   */
  public RecombinationFlashGenerator(FluidMagicInput input) {
    if (!input.isReady()) {
      throw new IllegalArgumentException(
          "FluidMagicInput must have phases separated. Call separateToStandardConditions() first.");
    }

    this.gasPhase = input.getGasPhase();
    this.oilPhase = input.getOilPhase();
    this.waterPhase = input.getWaterPhase();

    // Calculate molar volumes at standard conditions
    calculateMolarVolumes();

    this.fluidCache = new ConcurrentHashMap<>();

    logger.info("RecombinationFlashGenerator initialized");
    logger.info("  Gas molar volume: {} Sm3/mol", gasStdVolumePerMole);
    logger.info("  Oil molar volume: {} Sm3/mol", oilStdVolumePerMole);
    logger.info("  Water molar volume: {} Sm3/mol", waterStdVolumePerMole);
  }

  /**
   * Calculate standard molar volumes for each phase.
   */
  private void calculateMolarVolumes() {
    // Gas molar volume at std conditions
    if (gasPhase != null) {
      double totalMoles = gasPhase.getTotalNumberOfMoles();
      double volume = gasPhase.getVolume("m3");
      gasStdVolumePerMole = (totalMoles > 0) ? volume / totalMoles : 0.02369; // Ideal gas fallback, m3/mol
    }

    // Oil molar volume at std conditions
    if (oilPhase != null) {
      double totalMoles = oilPhase.getTotalNumberOfMoles();
      double volume = oilPhase.getVolume("m3");
      oilStdVolumePerMole = (totalMoles > 0) ? volume / totalMoles : 0.0001; // Default
    }

    // Water molar volume at std conditions (~18 cm3/mol)
    if (waterPhase != null) {
      waterStdVolumePerMole = MW_WATER / 1.0e6; // ~0.000018 m3/mol
    }
  }

  /**
   * Generate feed fluid at specified GOR and water cut.
   *
   * <p>
   * Uses recombination flash: mix gas and oil at ratio to achieve target GOR, then add water based on water cut.
   * </p>
   *
   * @param targetGOR target GOR in Sm3/Sm3
   * @param waterCut water cut as fraction (0-1)
   * @param totalLiquidRate total liquid rate (oil + water) in Sm3/hr
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return recombined fluid at specified conditions
   */
  public SystemInterface generateFluid(double targetGOR, double waterCut, double totalLiquidRate, double temperature,
      double pressure) {

    // Check cache first
    String cacheKey = getCacheKey(targetGOR, waterCut);
    if (enableCaching && fluidCache != null && fluidCache.containsKey(cacheKey)) {
      cacheHits++;
      SystemInterface cachedFluid = fluidCache.get(cacheKey).clone();
      // Scale to desired rate and set conditions
      scaleFluidToRate(cachedFluid, totalLiquidRate);
      cachedFluid.setTemperature(temperature);
      cachedFluid.setPressure(pressure);
      ThermodynamicOperations ops = new ThermodynamicOperations(cachedFluid);
      ops.TPflash();
      return cachedFluid;
    }
    cacheMisses++;

    // Calculate oil and water rates from water cut
    // Water cut = water / (oil + water)
    // NeqSim component amounts represent molar flow in mol/s. Convert the requested
    // hourly standard liquid volumes before scaling the separated reference phases.
    double oilRate = totalLiquidRate * (1.0 - waterCut) / 3600.0; // Sm3/s oil
    double waterRate = totalLiquidRate * waterCut / 3600.0; // Sm3/s water

    // Calculate gas rate from GOR (gas per oil volume)
    double gasRate = oilRate * targetGOR; // Sm3/s gas

    logger.debug("Generating fluid: GOR={}, WC={}%, Oil={}, Gas={}, Water={}", targetGOR, waterCut * 100, oilRate,
        gasRate, waterRate);

    // Create recombined fluid starting from gas phase composition
    SystemInterface recombined = createBaseFluid();

    // Add gas phase components (scaled to gas rate)
    if (gasPhase != null && gasRate > 0) {
      double gasVolumeFactor = gasRate / getStandardVolume(gasPhase);
      addScaledPhase(recombined, gasPhase, gasVolumeFactor);
    }

    // Add oil phase components (scaled to oil rate)
    if (oilPhase != null && oilRate > 0) {
      double oilVolumeFactor = oilRate / getStandardVolume(oilPhase);
      addScaledPhase(recombined, oilPhase, oilVolumeFactor);
    }

    // Add water if water cut > 0
    if (waterCut > 0 && waterPhase != null && waterRate > 0) {
      double waterVolumeFactor = waterRate / getStandardVolume(waterPhase);
      addScaledPhase(recombined, waterPhase, waterVolumeFactor);
    }

    recombined.setMixingRule("classic");
    recombined.setMultiPhaseCheck(true);

    // Use the same equilibrium liquid-rate normalization on cache hits and misses.
    scaleFluidToRate(recombined, totalLiquidRate);
    recombined.setTemperature(temperature);
    recombined.setPressure(pressure);

    ThermodynamicOperations ops = new ThermodynamicOperations(recombined);
    ops.TPflash();

    // Cache an independent fluid; cache hits rescale its equilibrium liquid rate.
    if (enableCaching && fluidCache != null) {
      fluidCache.put(cacheKey, recombined.clone());
    }

    return recombined;
  }

  /**
   * Generate fluid at specified GOR and water cut with default rates.
   *
   * @param targetGOR target GOR in Sm3/Sm3
   * @param waterCut water cut as fraction (0-1)
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return recombined fluid
   */
  public SystemInterface generateFluid(double targetGOR, double waterCut, double temperature, double pressure) {
    return generateFluid(targetGOR, waterCut, 1000.0, temperature, pressure);
  }

  /**
   * Create base fluid system for recombination.
   *
   * @return empty fluid system
   */
  private SystemInterface createBaseFluid() {
    // Keep the reference EOS and characterized component definitions. Constructing an
    // empty SRK system and adding fractions by name loses TBP/E300 petroleum properties.
    // Phase.clone shares the mixing-rule matrices. Adding water and reconfiguring
    // those matrices needs an independent copy for parallel composition generation.
    SystemInterface fluid = SerializationUtils.clone(gasPhase);
    fluid.setEmptyFluid();
    fluid.setTemperature(STD_TEMPERATURE_K);
    fluid.setPressure(STD_PRESSURE_BARA);
    return fluid;
  }

  /**
   * Get standard volume of a phase.
   *
   * @param phase the phase
   * @return volume at standard conditions in m3
   */
  private double getStandardVolume(SystemInterface phase) {
    if (phase == null) {
      return 1.0;
    }
    double vol = phase.getVolume("m3");
    return (vol > 0) ? vol : 1.0;
  }

  /**
   * Add scaled phase components to target fluid.
   *
   * @param target target fluid to add components to
   * @param source source phase with components
   * @param factor scaling factor for moles
   */
  private void addScaledPhase(SystemInterface target, SystemInterface source, double factor) {
    if (source == null || factor <= 0) {
      return;
    }

    for (int i = 0; i < source.getNumberOfComponents(); i++) {
      String compName = source.getComponent(i).getName();
      double molesToAdd = source.getComponent(i).getNumberOfmoles() * factor;

      if (molesToAdd > 0) {
        // addComponent will handle both existing and new components
        target.addComponent(compName, molesToAdd);
      }
    }
  }

  /**
   * Scale fluid to desired total liquid rate.
   *
   * @param fluid the fluid to scale
   * @param totalLiquidRate desired total liquid rate in Sm3/hr
   */
  private void scaleFluidToRate(SystemInterface fluid, double totalLiquidRate) {
    // Calculate current liquid volume at std conditions
    fluid.setTemperature(STD_TEMPERATURE_K);
    fluid.setPressure(STD_PRESSURE_BARA);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initPhysicalProperties();

    double currentLiquidVolume = 0.0;
    if (fluid.hasPhaseType("oil")) {
      currentLiquidVolume += fluid.getPhase("oil").getVolume("m3");
    }
    if (fluid.hasPhaseType("aqueous")) {
      currentLiquidVolume += fluid.getPhase("aqueous").getVolume("m3");
    }

    if (currentLiquidVolume > 0) {
      double scaleFactor = totalLiquidRate / 3600.0 / currentLiquidVolume;
      fluid.setTotalNumberOfMoles(fluid.getTotalNumberOfMoles() * scaleFactor);
      fluid.init(1);
    }
  }

  /**
   * Get cache key for GOR/WC combination.
   *
   * @param gor GOR value
   * @param wc water cut value
   * @return cache key string
   */
  private String getCacheKey(double gor, double wc) {
    return Double.toHexString(gor) + "_" + Double.toHexString(wc);
  }

  /**
   * Clear the fluid cache.
   */
  public void clearCache() {
    if (fluidCache != null) {
      fluidCache.clear();
    }
    cacheHits = 0;
    cacheMisses = 0;
  }

  /**
   * Get cache statistics.
   *
   * @return string with cache hit/miss statistics
   */
  public String getCacheStatistics() {
    int total = cacheHits + cacheMisses;
    double hitRate = (total > 0) ? (100.0 * cacheHits / total) : 0.0;
    return String.format("Cache: %d hits, %d misses (%.1f%% hit rate)", cacheHits, cacheMisses, hitRate);
  }

  /**
   * Enable or disable fluid caching.
   *
   * @param enable true to enable caching
   */
  public void setEnableCaching(boolean enable) {
    this.enableCaching = enable;
  }

  /**
   * Check if caching is enabled.
   *
   * @return true if caching is enabled
   */
  public boolean isEnableCaching() {
    return enableCaching;
  }

  /**
   * Validate that recombination produces expected GOR.
   *
   * <p>
   * This method is useful for testing and verification.
   * </p>
   *
   * @param targetGOR target GOR in Sm3/Sm3
   * @param waterCut water cut fraction
   * @param tolerance acceptable relative error (e.g., 0.05 for 5%)
   * @return true if actual GOR is within tolerance of target
   */
  public boolean validateGOR(double targetGOR, double waterCut, double tolerance) {
    SystemInterface fluid = generateFluid(targetGOR, waterCut, 1000.0, STD_TEMPERATURE_K, STD_PRESSURE_BARA);

    // Calculate actual GOR at std conditions
    fluid.setTemperature(STD_TEMPERATURE_K);
    fluid.setPressure(STD_PRESSURE_BARA);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    double gasVolume = 0.0;
    double oilVolume = 0.0;

    if (fluid.hasPhaseType("gas")) {
      gasVolume = fluid.getPhase("gas").getVolume("m3");
    }
    if (fluid.hasPhaseType("oil")) {
      oilVolume = fluid.getPhase("oil").getVolume("m3");
    }

    double actualGOR = (oilVolume > 0) ? gasVolume / oilVolume : 0.0;
    double relativeError = Math.abs(actualGOR - targetGOR) / targetGOR;

    logger.debug("GOR validation: target={}, actual={}, error={}%", targetGOR, actualGOR, relativeError * 100);

    return relativeError <= tolerance;
  }

  /**
   * Get the gas phase used for recombination.
   *
   * @return gas phase
   */
  public SystemInterface getGasPhase() {
    return gasPhase;
  }

  /**
   * Get the oil phase used for recombination.
   *
   * @return oil phase
   */
  public SystemInterface getOilPhase() {
    return oilPhase;
  }

  /**
   * Get the water phase used for recombination.
   *
   * @return water phase
   */
  public SystemInterface getWaterPhase() {
    return waterPhase;
  }
}
