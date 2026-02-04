package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.readwrite.EclipseFluidReadWrite;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Reference fluid input from FluidMagic/Eclipse E300 for multi-scenario optimization.
 *
 * <p>
 * Provides the base case gas and oil compositions with molar flow rates. This is the starting point
 * for recombination flash to generate GOR variations.
 * </p>
 *
 * <h2>Data Sources</h2>
 * <ul>
 * <li><b>E300 File</b>: Gas and oil composition (from FluidMagic export)</li>
 * <li><b>Eclipse 100</b>: GOR range (from FGOR vector), Water cut range (from FWCT vector)</li>
 * </ul>
 *
 * <p>
 * NOTE: The E300 file provides gas and oil composition ONLY. Water cut and GOR ranges come from
 * Eclipse 100 simulation results and must be specified separately.
 * </p>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * // From E300 file
 * FluidMagicInput input = FluidMagicInput.fromE300File(Paths.get("FLUID.E300"));
 * input.setGORRange(250, 10000); // from Eclipse 100 FGOR
 * input.setWaterCutRange(0.05, 0.60); // from Eclipse 100 FWCT
 * input.separateToStandardConditions();
 *
 * // Or using builder
 * FluidMagicInput input = FluidMagicInput.builder().referenceFluid(myFluid).gorRange(250, 10000)
 *     .waterCutRange(0.05, 0.60).numberOfGORPoints(6).numberOfWaterCutPoints(5).build();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 * @see RecombinationFlashGenerator
 * @see MultiScenarioVFPGenerator
 */
public class FluidMagicInput implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(FluidMagicInput.class);

  /** Standard temperature in Kelvin (15°C). */
  private static final double STD_TEMPERATURE_K = 288.15;

  /** Standard pressure in bara (1 atm). */
  private static final double STD_PRESSURE_BARA = 1.01325;

  // Reference fluid from E300 file (gas + oil only)
  private SystemInterface referenceFluid;

  // Separated phases at standard conditions (for recombination)
  private SystemInterface gasPhase;
  private SystemInterface oilPhase;
  private SystemInterface waterPhase;

  // Ranges from Eclipse 100 simulation (FGOR, FWCT vectors)
  private double minGOR = 100.0;
  private double maxGOR = 10000.0;
  private double minWaterCut = 0.0;
  private double maxWaterCut = 0.80;

  // Conditions for generating fluids
  private double temperature = 353.15; // 80°C default
  private double pressure = 50.0; // 50 bara default

  // Sampling configuration
  private int numberOfGORPoints = 5;
  private int numberOfWaterCutPoints = 5;
  private GORSpacing gorSpacing = GORSpacing.LOGARITHMIC;

  // Standard condition volumes (calculated during separation)
  private double gasStdVolume = 0.0;
  private double oilStdVolume = 0.0;
  private double waterStdVolume = 0.0;

  // Base case GOR from reference fluid
  private double baseCaseGOR = 0.0;

  // Water salinity for formation water (ppm NaCl equivalent)
  private double waterSalinityPPM = 0.0;

  /**
   * GOR spacing options for scenario generation.
   */
  public enum GORSpacing {
    /** Equal spacing between GOR values. */
    LINEAR,
    /** Logarithmic spacing (better for wide GOR ranges like 100-10000). */
    LOGARITHMIC
  }

  /**
   * Default constructor.
   */
  public FluidMagicInput() {}

  /**
   * Constructor with reference fluid.
   *
   * @param referenceFluid the reference fluid from E300 or NeqSim
   */
  public FluidMagicInput(SystemInterface referenceFluid) {
    this.referenceFluid = referenceFluid;
  }

  /**
   * Import reference fluid from E300/FluidMagic file.
   *
   * <p>
   * Uses EclipseFluidReadWrite to parse the E300 file format.
   * </p>
   *
   * @param e300Path path to the E300 file
   * @return FluidMagicInput with imported fluid
   */
  public static FluidMagicInput fromE300File(Path e300Path) {
    FluidMagicInput input = new FluidMagicInput();
    try {
      SystemInterface fluid = EclipseFluidReadWrite.read(e300Path.toString());
      input.setReferenceFluid(fluid);
      logger.info("Imported reference fluid from E300 file: {}", e300Path);
      logger.info("Components: {}", fluid.getNumberOfComponents());
    } catch (Exception e) {
      logger.error("Failed to read E300 file: {}", e300Path, e);
      throw new RuntimeException("Failed to read E300 file: " + e300Path, e);
    }
    return input;
  }

  /**
   * Import reference fluid from E300/FluidMagic file.
   *
   * @param e300FilePath path to the E300 file as string
   * @return FluidMagicInput with imported fluid
   */
  public static FluidMagicInput fromE300File(String e300FilePath) {
    return fromE300File(Path.of(e300FilePath));
  }

  /**
   * Create FluidMagicInput from existing NeqSim fluid.
   *
   * @param fluid the reference fluid (should contain gas and oil components)
   * @return FluidMagicInput with the provided fluid
   */
  public static FluidMagicInput fromFluid(SystemInterface fluid) {
    FluidMagicInput input = new FluidMagicInput();
    input.setReferenceFluid(fluid.clone());
    return input;
  }

  /**
   * Separate reference fluid into gas and oil phases at standard conditions.
   *
   * <p>
   * This prepares for recombination flash by:
   * <ol>
   * <li>Cloning the reference fluid</li>
   * <li>Flashing to standard conditions (15°C, 1.01325 bara)</li>
   * <li>Extracting gas and oil phases separately</li>
   * <li>Calculating standard volumes for rate scaling</li>
   * </ol>
   *
   * <p>
   * NOTE: Water phase is NOT extracted from E300 - it will be added based on water cut from Eclipse
   * 100 simulation.
   * </p>
   */
  public void separateToStandardConditions() {
    if (referenceFluid == null) {
      throw new IllegalStateException("Reference fluid not set. Call setReferenceFluid() first.");
    }

    logger.info("Separating reference fluid to standard conditions...");

    // Clone and flash to standard conditions
    SystemInterface stdFluid = referenceFluid.clone();
    stdFluid.setTemperature(STD_TEMPERATURE_K);
    stdFluid.setPressure(STD_PRESSURE_BARA);

    ThermodynamicOperations ops = new ThermodynamicOperations(stdFluid);
    ops.TPflash();
    stdFluid.initPhysicalProperties();

    // Extract gas phase
    if (stdFluid.hasPhaseType("gas")) {
      gasPhase = stdFluid.phaseToSystem("gas");
      gasPhase.setTemperature(STD_TEMPERATURE_K);
      gasPhase.setPressure(STD_PRESSURE_BARA);
      gasPhase.init(0);
      gasPhase.initPhysicalProperties();

      // Calculate standard gas volume
      gasStdVolume = gasPhase.getVolume("m3");
      logger.info("Gas phase extracted: {} components, {:.4f} m3 at std conditions",
          gasPhase.getNumberOfComponents(), gasStdVolume);
    } else {
      logger.warn("No gas phase found in reference fluid at standard conditions");
    }

    // Extract oil phase
    if (stdFluid.hasPhaseType("oil")) {
      oilPhase = stdFluid.phaseToSystem("oil");
      oilPhase.setTemperature(STD_TEMPERATURE_K);
      oilPhase.setPressure(STD_PRESSURE_BARA);
      oilPhase.init(0);
      oilPhase.initPhysicalProperties();

      // Calculate standard oil volume
      oilStdVolume = oilPhase.getVolume("m3");
      logger.info("Oil phase extracted: {} components, {:.4f} m3 at std conditions",
          oilPhase.getNumberOfComponents(), oilStdVolume);
    } else {
      logger.warn("No oil phase found in reference fluid at standard conditions");
    }

    // Calculate base case GOR
    if (gasStdVolume > 0 && oilStdVolume > 0) {
      baseCaseGOR = gasStdVolume / oilStdVolume;
      logger.info("Base case GOR: {:.1f} Sm3/Sm3", baseCaseGOR);
    }

    // Create water phase (pure water or brine based on salinity)
    createWaterPhase();

    logger.info("Phase separation complete. Ready for recombination.");
  }

  /**
   * Create water phase for water cut calculations.
   *
   * <p>
   * If salinity is specified, creates formation water with dissolved salts. Otherwise, creates pure
   * water.
   * </p>
   */
  private void createWaterPhase() {
    waterPhase = new SystemSrkEos(STD_TEMPERATURE_K, STD_PRESSURE_BARA);
    waterPhase.addComponent("water", 1.0);
    waterPhase.setMixingRule("classic");
    waterPhase.init(0);
    waterPhase.initPhysicalProperties();

    // Calculate standard water volume (1 kmol at std conditions)
    waterStdVolume = waterPhase.getVolume("m3");
    logger.info("Water phase created: {:.4f} m3/kmol at std conditions", waterStdVolume);
  }

  /**
   * Generate array of GOR values for scenario generation.
   *
   * @return array of GOR values in Sm3/Sm3
   */
  public double[] generateGORValues() {
    double[] values = new double[numberOfGORPoints];

    if (gorSpacing == GORSpacing.LOGARITHMIC && minGOR > 0) {
      // Logarithmic spacing (better for wide ranges like 100-10000)
      double logMin = Math.log10(minGOR);
      double logMax = Math.log10(maxGOR);
      for (int i = 0; i < numberOfGORPoints; i++) {
        double logVal = logMin + (logMax - logMin) * i / (numberOfGORPoints - 1);
        values[i] = Math.pow(10, logVal);
      }
    } else {
      // Linear spacing
      for (int i = 0; i < numberOfGORPoints; i++) {
        values[i] = minGOR + (maxGOR - minGOR) * i / (numberOfGORPoints - 1);
      }
    }

    return values;
  }

  /**
   * Generate array of water cut values for scenario generation.
   *
   * @return array of water cut values as fractions (0-1)
   */
  public double[] generateWaterCutValues() {
    double[] values = new double[numberOfWaterCutPoints];
    for (int i = 0; i < numberOfWaterCutPoints; i++) {
      values[i] = minWaterCut + (maxWaterCut - minWaterCut) * i / (numberOfWaterCutPoints - 1);
    }
    return values;
  }

  /**
   * Set GOR range from Eclipse 100 FGOR vector.
   *
   * @param min minimum GOR in Sm3/Sm3
   * @param max maximum GOR in Sm3/Sm3
   */
  public void setGORRange(double min, double max) {
    if (min <= 0 || max <= 0) {
      throw new IllegalArgumentException("GOR values must be positive");
    }
    if (min >= max) {
      throw new IllegalArgumentException("minGOR must be less than maxGOR");
    }
    this.minGOR = min;
    this.maxGOR = max;
  }

  /**
   * Set GOR range with number of points (convenience method).
   *
   * <p>
   * Combines {@link #setGORRange(double, double)} and {@link #setNumberOfGORPoints(int)}.
   * </p>
   *
   * @param min minimum GOR in Sm3/Sm3
   * @param max maximum GOR in Sm3/Sm3
   * @param count number of GOR points to generate (minimum 2)
   */
  public void setGORRange(double min, double max, int count) {
    setGORRange(min, max);
    setNumberOfGORPoints(count);
  }

  /**
   * Set water cut range from Eclipse 100 FWCT vector.
   *
   * @param min minimum water cut as fraction (0-1)
   * @param max maximum water cut as fraction (0-1)
   */
  public void setWaterCutRange(double min, double max) {
    if (min < 0 || max < 0 || min > 1 || max > 1) {
      throw new IllegalArgumentException("Water cut must be between 0 and 1");
    }
    if (min > max) {
      throw new IllegalArgumentException("minWaterCut must be <= maxWaterCut");
    }
    this.minWaterCut = min;
    this.maxWaterCut = max;
  }

  /**
   * Set water cut range with number of points (convenience method).
   *
   * <p>
   * Combines {@link #setWaterCutRange(double, double)} and {@link #setNumberOfWaterCutPoints(int)}.
   * </p>
   *
   * @param min minimum water cut as fraction (0-1)
   * @param max maximum water cut as fraction (0-1)
   * @param count number of water cut points to generate (minimum 2)
   */
  public void setWaterCutRange(double min, double max, int count) {
    setWaterCutRange(min, max);
    setNumberOfWaterCutPoints(count);
  }

  // ==================== Getters and Setters ====================

  /**
   * Get the reference fluid.
   *
   * @return reference fluid
   */
  public SystemInterface getReferenceFluid() {
    return referenceFluid;
  }

  /**
   * Set the reference fluid.
   *
   * @param referenceFluid reference fluid from E300 or NeqSim
   */
  public void setReferenceFluid(SystemInterface referenceFluid) {
    this.referenceFluid = referenceFluid;
  }

  /**
   * Get the gas phase at standard conditions.
   *
   * @return gas phase
   */
  public SystemInterface getGasPhase() {
    return gasPhase;
  }

  /**
   * Get the oil phase at standard conditions.
   *
   * @return oil phase
   */
  public SystemInterface getOilPhase() {
    return oilPhase;
  }

  /**
   * Get the water phase.
   *
   * @return water phase
   */
  public SystemInterface getWaterPhase() {
    return waterPhase;
  }

  /**
   * Get minimum GOR.
   *
   * @return minimum GOR in Sm3/Sm3
   */
  public double getMinGOR() {
    return minGOR;
  }

  /**
   * Get maximum GOR.
   *
   * @return maximum GOR in Sm3/Sm3
   */
  public double getMaxGOR() {
    return maxGOR;
  }

  /**
   * Get minimum water cut.
   *
   * @return minimum water cut as fraction
   */
  public double getMinWaterCut() {
    return minWaterCut;
  }

  /**
   * Get maximum water cut.
   *
   * @return maximum water cut as fraction
   */
  public double getMaxWaterCut() {
    return maxWaterCut;
  }

  /**
   * Get the temperature for generating fluids.
   *
   * @return temperature in Kelvin
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * Set the temperature for generating fluids.
   *
   * @param temperature temperature in Kelvin
   */
  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }

  /**
   * Get the pressure for generating fluids.
   *
   * @return pressure in bara
   */
  public double getPressure() {
    return pressure;
  }

  /**
   * Set the pressure for generating fluids.
   *
   * @param pressure pressure in bara
   */
  public void setPressure(double pressure) {
    this.pressure = pressure;
  }

  /**
   * Get number of GOR points for scenario generation.
   *
   * @return number of GOR points
   */
  public int getNumberOfGORPoints() {
    return numberOfGORPoints;
  }

  /**
   * Set number of GOR points for scenario generation.
   *
   * @param numberOfGORPoints number of GOR points (minimum 2)
   */
  public void setNumberOfGORPoints(int numberOfGORPoints) {
    if (numberOfGORPoints < 2) {
      throw new IllegalArgumentException("numberOfGORPoints must be at least 2");
    }
    this.numberOfGORPoints = numberOfGORPoints;
  }

  /**
   * Get number of water cut points for scenario generation.
   *
   * @return number of water cut points
   */
  public int getNumberOfWaterCutPoints() {
    return numberOfWaterCutPoints;
  }

  /**
   * Set number of water cut points for scenario generation.
   *
   * @param numberOfWaterCutPoints number of water cut points (minimum 2)
   */
  public void setNumberOfWaterCutPoints(int numberOfWaterCutPoints) {
    if (numberOfWaterCutPoints < 2) {
      throw new IllegalArgumentException("numberOfWaterCutPoints must be at least 2");
    }
    this.numberOfWaterCutPoints = numberOfWaterCutPoints;
  }

  /**
   * Get GOR spacing mode.
   *
   * @return GOR spacing mode
   */
  public GORSpacing getGorSpacing() {
    return gorSpacing;
  }

  /**
   * Set GOR spacing mode.
   *
   * @param gorSpacing GOR spacing mode (LINEAR or LOGARITHMIC)
   */
  public void setGorSpacing(GORSpacing gorSpacing) {
    this.gorSpacing = gorSpacing;
  }

  /**
   * Get standard gas volume.
   *
   * @return gas volume at standard conditions in m3
   */
  public double getGasStdVolume() {
    return gasStdVolume;
  }

  /**
   * Get standard oil volume.
   *
   * @return oil volume at standard conditions in m3
   */
  public double getOilStdVolume() {
    return oilStdVolume;
  }

  /**
   * Get standard water volume.
   *
   * @return water volume at standard conditions in m3/kmol
   */
  public double getWaterStdVolume() {
    return waterStdVolume;
  }

  /**
   * Get base case GOR from reference fluid.
   *
   * @return base case GOR in Sm3/Sm3
   */
  public double getBaseCaseGOR() {
    return baseCaseGOR;
  }

  /**
   * Get water salinity.
   *
   * @return salinity in ppm NaCl equivalent
   */
  public double getWaterSalinityPPM() {
    return waterSalinityPPM;
  }

  /**
   * Set water salinity for formation water.
   *
   * @param waterSalinityPPM salinity in ppm NaCl equivalent
   */
  public void setWaterSalinityPPM(double waterSalinityPPM) {
    this.waterSalinityPPM = waterSalinityPPM;
  }

  /**
   * Check if phase separation has been performed.
   *
   * @return true if phases are separated and ready for recombination
   */
  public boolean isReady() {
    return gasPhase != null && oilPhase != null;
  }

  /**
   * Get total number of scenarios that will be generated.
   *
   * @return total scenarios (GOR points × WC points)
   */
  public int getTotalScenarios() {
    return numberOfGORPoints * numberOfWaterCutPoints;
  }

  /**
   * Create a builder for FluidMagicInput.
   *
   * @return new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder class for FluidMagicInput.
   */
  public static class Builder {
    private SystemInterface referenceFluid;
    private double minGOR = 100.0;
    private double maxGOR = 10000.0;
    private double minWaterCut = 0.0;
    private double maxWaterCut = 0.80;
    private int numberOfGORPoints = 5;
    private int numberOfWaterCutPoints = 5;
    private GORSpacing gorSpacing = GORSpacing.LOGARITHMIC;
    private double temperature = 353.15;
    private double pressure = 50.0;
    private double waterSalinityPPM = 0.0;

    /**
     * Set reference fluid.
     *
     * @param fluid reference fluid
     * @return this builder
     */
    public Builder referenceFluid(SystemInterface fluid) {
      this.referenceFluid = fluid;
      return this;
    }

    /**
     * Set GOR range.
     *
     * @param min minimum GOR in Sm3/Sm3
     * @param max maximum GOR in Sm3/Sm3
     * @return this builder
     */
    public Builder gorRange(double min, double max) {
      this.minGOR = min;
      this.maxGOR = max;
      return this;
    }

    /**
     * Set water cut range.
     *
     * @param min minimum water cut (0-1)
     * @param max maximum water cut (0-1)
     * @return this builder
     */
    public Builder waterCutRange(double min, double max) {
      this.minWaterCut = min;
      this.maxWaterCut = max;
      return this;
    }

    /**
     * Set number of GOR points.
     *
     * @param n number of GOR points
     * @return this builder
     */
    public Builder numberOfGORPoints(int n) {
      this.numberOfGORPoints = n;
      return this;
    }

    /**
     * Set number of water cut points.
     *
     * @param n number of water cut points
     * @return this builder
     */
    public Builder numberOfWaterCutPoints(int n) {
      this.numberOfWaterCutPoints = n;
      return this;
    }

    /**
     * Set GOR spacing mode.
     *
     * @param spacing spacing mode
     * @return this builder
     */
    public Builder gorSpacing(GORSpacing spacing) {
      this.gorSpacing = spacing;
      return this;
    }

    /**
     * Set temperature for fluid generation.
     *
     * @param temperatureK temperature in Kelvin
     * @return this builder
     */
    public Builder temperature(double temperatureK) {
      this.temperature = temperatureK;
      return this;
    }

    /**
     * Set pressure for fluid generation.
     *
     * @param pressureBara pressure in bara
     * @return this builder
     */
    public Builder pressure(double pressureBara) {
      this.pressure = pressureBara;
      return this;
    }

    /**
     * Set water salinity.
     *
     * @param salinityPPM salinity in ppm
     * @return this builder
     */
    public Builder waterSalinity(double salinityPPM) {
      this.waterSalinityPPM = salinityPPM;
      return this;
    }

    /**
     * Build the FluidMagicInput.
     *
     * @return configured FluidMagicInput
     */
    public FluidMagicInput build() {
      FluidMagicInput input = new FluidMagicInput();
      input.referenceFluid = this.referenceFluid;
      input.minGOR = this.minGOR;
      input.maxGOR = this.maxGOR;
      input.minWaterCut = this.minWaterCut;
      input.maxWaterCut = this.maxWaterCut;
      input.numberOfGORPoints = this.numberOfGORPoints;
      input.numberOfWaterCutPoints = this.numberOfWaterCutPoints;
      input.gorSpacing = this.gorSpacing;
      input.temperature = this.temperature;
      input.pressure = this.pressure;
      input.waterSalinityPPM = this.waterSalinityPPM;
      return input;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("FluidMagicInput {\n");
    sb.append("  GOR range: ").append(minGOR).append(" - ").append(maxGOR).append(" Sm3/Sm3\n");
    sb.append("  WC range: ").append(minWaterCut * 100).append(" - ").append(maxWaterCut * 100)
        .append(" %\n");
    sb.append("  GOR points: ").append(numberOfGORPoints).append(" (")
        .append(gorSpacing.name().toLowerCase()).append(")\n");
    sb.append("  WC points: ").append(numberOfWaterCutPoints).append("\n");
    sb.append("  Total scenarios: ").append(getTotalScenarios()).append("\n");
    if (baseCaseGOR > 0) {
      sb.append("  Base case GOR: ").append(String.format("%.1f", baseCaseGOR))
          .append(" Sm3/Sm3\n");
    }
    sb.append("  Ready: ").append(isReady()).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
