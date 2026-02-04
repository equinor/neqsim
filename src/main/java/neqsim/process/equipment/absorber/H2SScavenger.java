package neqsim.process.equipment.absorber;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * H2S Scavenger unit operation for removing hydrogen sulfide from gas streams.
 *
 * <p>
 * This unit operation models H2S removal using chemical scavengers without rigorous chemical
 * reaction calculations. Instead, it uses empirical correlations based on scavenger type, injection
 * rate, and operating conditions.
 * </p>
 *
 * <p>
 * Supported scavenger types and their characteristics:
 * </p>
 *
 * <table>
 * <caption>H2S Scavenger Types and Properties</caption>
 * <tr>
 * <th>Type</th>
 * <th>Active Component</th>
 * <th>Stoichiometry (lb scavenger/lb H2S)</th>
 * <th>Typical Efficiency</th>
 * </tr>
 * <tr>
 * <td>TRIAZINE</td>
 * <td>MEA-triazine (1,3,5-tri(2-hydroxyethyl)-hexahydro-s-triazine)</td>
 * <td>3.5-6.0</td>
 * <td>85-99%</td>
 * </tr>
 * <tr>
 * <td>GLYOXAL</td>
 * <td>Glyoxal-based</td>
 * <td>4.0-7.0</td>
 * <td>80-95%</td>
 * </tr>
 * <tr>
 * <td>IRON_SPONGE</td>
 * <td>Iron oxide (Fe2O3) on wood chips</td>
 * <td>N/A (solid bed)</td>
 * <td>95-99%</td>
 * </tr>
 * <tr>
 * <td>CAUSTIC</td>
 * <td>Sodium hydroxide (NaOH)</td>
 * <td>2.4 (stoichiometric)</td>
 * <td>90-99%</td>
 * </tr>
 * <tr>
 * <td>LIQUID_REDOX</td>
 * <td>Iron chelate solution (LO-CAT, SulFerox)</td>
 * <td>N/A (catalytic)</td>
 * <td>99+%</td>
 * </tr>
 * </table>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>GPSA Engineering Data Book, 14th Edition, Section 21 - Hydrocarbon Treating</li>
 * <li>Kohl, A.L. and Nielsen, R.B., "Gas Purification", 5th Edition, Gulf Publishing</li>
 * <li>SPE-141434-MS: "H2S Scavenger Performance in Bakken Crude"</li>
 * <li>Arnold, K. and Stewart, M., "Surface Production Operations", Vol. 2</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class H2SScavenger extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Enum representing types of H2S scavengers available.
   */
  public enum ScavengerType {
    /**
     * MEA-triazine based scavenger. Most common for oilfield applications. Reaction: 3H2S +
     * C6H15N3O3 → products (dithiazine + thiadiazine). Typical stoichiometry: 3.5-6.0 lb scavenger
     * per lb H2S.
     */
    TRIAZINE("MEA-Triazine", 4.5, 0.95, 1.07),

    /**
     * Glyoxal-based scavenger. Good for high CO2 systems. Typical stoichiometry: 4.0-7.0 lb
     * scavenger per lb H2S.
     */
    GLYOXAL("Glyoxal", 5.5, 0.90, 1.10),

    /**
     * Iron sponge (iron oxide on wood chips). Used for dry gas treating. Regenerable with air.
     * Capacity: 0.4-0.6 lb H2S per lb iron oxide.
     */
    IRON_SPONGE("Iron Sponge", 2.5, 0.98, 0.0),

    /**
     * Caustic (NaOH) scrubbing. Very effective but produces liquid waste. Stoichiometry: 2.4 lb
     * NaOH per lb H2S (based on 2NaOH + H2S → Na2S + 2H2O).
     */
    CAUSTIC("Sodium Hydroxide", 2.4, 0.95, 1.05),

    /**
     * Liquid redox processes (LO-CAT, SulFerox). Catalytic conversion to elemental sulfur. Very
     * high efficiency for large scale.
     */
    LIQUID_REDOX("Liquid Redox", 0.0, 0.995, 1.02);

    private final String displayName;
    private final double baseStoichiometry; // lb scavenger per lb H2S (theoretical)
    private final double baseEfficiency; // Base removal efficiency at optimal conditions
    private final double density; // Specific gravity (water = 1.0), 0 for solids

    /**
     * Constructor for ScavengerType enum.
     *
     * @param displayName human-readable name
     * @param baseStoichiometry theoretical lb scavenger per lb H2S
     * @param baseEfficiency base removal efficiency (0-1)
     * @param density specific gravity of liquid scavenger
     */
    ScavengerType(String displayName, double baseStoichiometry, double baseEfficiency,
        double density) {
      this.displayName = displayName;
      this.baseStoichiometry = baseStoichiometry;
      this.baseEfficiency = baseEfficiency;
      this.density = density;
    }

    /**
     * Get the display name.
     *
     * @return display name string
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Get base stoichiometry (lb scavenger per lb H2S).
     *
     * @return stoichiometry ratio
     */
    public double getBaseStoichiometry() {
      return baseStoichiometry;
    }

    /**
     * Get base removal efficiency.
     *
     * @return efficiency as fraction (0-1)
     */
    public double getBaseEfficiency() {
      return baseEfficiency;
    }

    /**
     * Get scavenger density (specific gravity).
     *
     * @return specific gravity relative to water
     */
    public double getDensity() {
      return density;
    }
  }

  // Configuration
  private ScavengerType scavengerType = ScavengerType.TRIAZINE;
  private double scavengerInjectionRate = 0.0; // l/hr or kg/hr depending on type
  private String injectionRateUnit = "l/hr";
  private double scavengerConcentration = 1.0; // Active ingredient concentration (fraction)
  private double targetH2SConcentration = 4.0; // ppm target in outlet

  // Operating conditions
  private double contactTime = 30.0; // seconds - residence time for gas-liquid contact
  private double mixingEfficiency = 0.85; // Contactor mixing efficiency (0-1)

  // Calculated results
  private double h2sRemovalEfficiency = 0.0;
  private double h2sRemoved = 0.0; // kg/hr
  private double inletH2SConcentration = 0.0; // ppm
  private double outletH2SConcentration = 0.0; // ppm
  private double actualScavengerConsumption = 0.0; // kg/hr
  private double scavengerExcess = 0.0; // fraction excess over stoichiometric

  // Molecular weights
  private static final double MW_H2S = 34.08; // g/mol

  /**
   * Constructor for H2SScavenger.
   *
   * @param name equipment name
   */
  public H2SScavenger(String name) {
    super(name);
  }

  /**
   * Constructor for H2SScavenger with inlet stream.
   *
   * @param name equipment name
   * @param inStream inlet gas stream
   */
  public H2SScavenger(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * Set the scavenger type.
   *
   * @param type ScavengerType enum value
   */
  public void setScavengerType(ScavengerType type) {
    this.scavengerType = type;
  }

  /**
   * Get the scavenger type.
   *
   * @return current ScavengerType
   */
  public ScavengerType getScavengerType() {
    return scavengerType;
  }

  /**
   * Set scavenger injection rate.
   *
   * @param rate injection rate value
   * @param unit unit string ("l/hr", "gal/hr", "kg/hr", "lb/hr")
   */
  public void setScavengerInjectionRate(double rate, String unit) {
    this.scavengerInjectionRate = rate;
    this.injectionRateUnit = unit;
  }

  /**
   * Get scavenger injection rate in specified unit.
   *
   * @param unit desired unit ("l/hr", "gal/hr", "kg/hr", "lb/hr")
   * @return injection rate in specified unit
   */
  public double getScavengerInjectionRate(String unit) {
    double rateKgHr = getScavengerInjectionRateKgPerHour();
    if (unit.equals("kg/hr")) {
      return rateKgHr;
    } else if (unit.equals("lb/hr")) {
      return rateKgHr * 2.20462;
    } else if (unit.equals("l/hr")) {
      return rateKgHr / (scavengerType.getDensity() * 1000.0 / 1000.0); // density in kg/l
    } else if (unit.equals("gal/hr")) {
      return rateKgHr / (scavengerType.getDensity() * 1000.0 / 1000.0) / 3.78541;
    }
    return scavengerInjectionRate;
  }

  /**
   * Convert injection rate to kg/hr.
   *
   * @return injection rate in kg/hr
   */
  private double getScavengerInjectionRateKgPerHour() {
    double density = scavengerType.getDensity();
    if (density <= 0) {
      // Solid scavenger - assume rate is already in kg/hr
      return scavengerInjectionRate;
    }

    double densityKgPerL = density * 1.0; // 1 kg/L = SG of 1.0
    if (injectionRateUnit.equals("l/hr")) {
      return scavengerInjectionRate * densityKgPerL;
    } else if (injectionRateUnit.equals("gal/hr")) {
      return scavengerInjectionRate * 3.78541 * densityKgPerL;
    } else if (injectionRateUnit.equals("kg/hr")) {
      return scavengerInjectionRate;
    } else if (injectionRateUnit.equals("lb/hr")) {
      return scavengerInjectionRate / 2.20462;
    }
    return scavengerInjectionRate;
  }

  /**
   * Set the active ingredient concentration in the scavenger product.
   *
   * @param concentration concentration as mass fraction (0-1), e.g., 0.5 for 50% active
   */
  public void setScavengerConcentration(double concentration) {
    this.scavengerConcentration = Math.max(0.0, Math.min(1.0, concentration));
  }

  /**
   * Get scavenger concentration.
   *
   * @return concentration as mass fraction
   */
  public double getScavengerConcentration() {
    return scavengerConcentration;
  }

  /**
   * Set target H2S outlet concentration.
   *
   * @param ppm target H2S in ppm (molar)
   */
  public void setTargetH2SConcentration(double ppm) {
    this.targetH2SConcentration = ppm;
  }

  /**
   * Get target H2S outlet concentration.
   *
   * @return target in ppm
   */
  public double getTargetH2SConcentration() {
    return targetH2SConcentration;
  }

  /**
   * Set contact time (residence time).
   *
   * @param seconds contact time in seconds
   */
  public void setContactTime(double seconds) {
    this.contactTime = seconds;
  }

  /**
   * Get contact time.
   *
   * @return contact time in seconds
   */
  public double getContactTime() {
    return contactTime;
  }

  /**
   * Set mixing efficiency of the contactor.
   *
   * @param efficiency efficiency as fraction (0-1)
   */
  public void setMixingEfficiency(double efficiency) {
    this.mixingEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
  }

  /**
   * Get mixing efficiency.
   *
   * @return mixing efficiency as fraction
   */
  public double getMixingEfficiency() {
    return mixingEfficiency;
  }

  /**
   * Calculate H2S removal efficiency based on scavenger type, injection rate, and conditions.
   *
   * <p>
   * The efficiency correlation is based on empirical data from literature:
   * </p>
   * <ul>
   * <li>Base efficiency depends on scavenger type</li>
   * <li>Efficiency increases with excess scavenger (diminishing returns)</li>
   * <li>Contact time affects mass transfer</li>
   * <li>Temperature affects reaction kinetics</li>
   * </ul>
   *
   * @param inletH2SMassFlow H2S mass flow in inlet stream (kg/hr)
   * @param temperature operating temperature (K)
   * @param pressure operating pressure (bara)
   * @return removal efficiency as fraction (0-1)
   */
  private double calculateRemovalEfficiency(double inletH2SMassFlow, double temperature,
      double pressure) {
    if (inletH2SMassFlow <= 0 || scavengerInjectionRate <= 0) {
      return 0.0;
    }

    // Calculate stoichiometric requirement
    double stoichRatio = scavengerType.getBaseStoichiometry();
    double activeScavengerRate = getScavengerInjectionRateKgPerHour() * scavengerConcentration;
    double stoichRequirement = inletH2SMassFlow * stoichRatio;

    // Calculate excess ratio (actual/stoichiometric)
    double excessRatio = activeScavengerRate / stoichRequirement;
    this.scavengerExcess = excessRatio - 1.0;

    // Base efficiency from scavenger type
    double baseEff = scavengerType.getBaseEfficiency();

    // Efficiency correction for scavenger excess
    // Based on empirical correlation: η = η_base * (1 - exp(-k * excess))
    // where k depends on scavenger type
    double k = getKineticConstant();
    double excessCorrection;
    if (excessRatio >= 1.0) {
      // Excess scavenger - efficiency approaches base efficiency asymptotically
      excessCorrection = 1.0 - Math.exp(-k * (excessRatio - 0.5));
    } else {
      // Under-dosed - linear reduction
      excessCorrection = excessRatio * 0.9;
    }

    // Contact time correction
    // Minimum contact time ~10s for good mixing, optimal ~30s
    double contactCorrection = 1.0 - Math.exp(-contactTime / 15.0);

    // Temperature correction
    // Reactions are faster at higher temperature, but equilibrium may be less favorable
    // Optimal range 20-60°C for triazine
    double tempC = temperature - 273.15;
    double tempCorrection;
    if (tempC < 10) {
      tempCorrection = 0.7 + 0.03 * tempC; // Reduced at low temp
    } else if (tempC > 80) {
      tempCorrection = 1.0 - 0.005 * (tempC - 80); // Slight reduction at high temp
    } else {
      tempCorrection = 1.0;
    }

    // Mixing efficiency correction
    double mixCorrection = 0.5 + 0.5 * mixingEfficiency;

    // Combined efficiency
    double efficiency =
        baseEff * excessCorrection * contactCorrection * tempCorrection * mixCorrection;

    return Math.max(0.0, Math.min(0.999, efficiency));
  }

  /**
   * Get kinetic constant for efficiency correlation based on scavenger type.
   *
   * @return kinetic constant k
   */
  private double getKineticConstant() {
    switch (scavengerType) {
      case TRIAZINE:
        return 1.8; // Fast reaction
      case GLYOXAL:
        return 1.5;
      case CAUSTIC:
        return 2.5; // Very fast
      case LIQUID_REDOX:
        return 3.0; // Catalytic, very efficient
      case IRON_SPONGE:
        return 1.2; // Solid bed, slower
      default:
        return 1.5;
    }
  }

  /**
   * Calculate required scavenger injection rate to achieve target H2S outlet.
   *
   * @return required injection rate in current unit
   */
  public double calculateRequiredInjectionRate() {
    if (inStream == null) {
      return 0.0;
    }

    SystemInterface system = inStream.getThermoSystem();
    if (!system.hasComponent("H2S")) {
      return 0.0;
    }

    // Get H2S in inlet
    double h2sMolFrac = system.getPhase(0).getComponent("H2S").getx();
    double totalMolarFlow = system.getTotalNumberOfMoles() * 3600; // mol/hr
    double h2sMolFlow = h2sMolFrac * totalMolarFlow; // mol H2S/hr
    double h2sMassFlow = h2sMolFlow * MW_H2S / 1000.0; // kg H2S/hr

    // Calculate inlet ppm
    inletH2SConcentration = h2sMolFrac * 1e6;

    if (inletH2SConcentration <= targetH2SConcentration) {
      return 0.0; // Already meets spec
    }

    // Required removal fraction
    double requiredRemoval = 1.0 - (targetH2SConcentration / inletH2SConcentration);

    // Solve for required injection rate iteratively
    // Start with stoichiometric amount
    double stoichRate = h2sMassFlow * scavengerType.getBaseStoichiometry() / scavengerConcentration;

    // Iterate to find rate that gives required efficiency
    double rate = stoichRate;
    for (int i = 0; i < 20; i++) {
      double testRateKgHr = rate;
      // Temporarily set rate to calculate efficiency
      double tempRate = scavengerInjectionRate;
      String tempUnit = injectionRateUnit;
      scavengerInjectionRate = testRateKgHr;
      injectionRateUnit = "kg/hr";

      double efficiency =
          calculateRemovalEfficiency(h2sMassFlow, system.getTemperature(), system.getPressure());

      scavengerInjectionRate = tempRate;
      injectionRateUnit = tempUnit;

      if (efficiency >= requiredRemoval) {
        break;
      }
      rate *= 1.2; // Increase by 20%
    }

    // Convert to current unit
    double density = scavengerType.getDensity();
    if (density <= 0) {
      return rate; // kg/hr for solids
    }

    if (injectionRateUnit.equals("l/hr")) {
      return rate / density;
    } else if (injectionRateUnit.equals("gal/hr")) {
      return rate / density / 3.78541;
    } else if (injectionRateUnit.equals("lb/hr")) {
      return rate * 2.20462;
    }
    return rate;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface inletSystem = inStream.getThermoSystem();

    // Clone inlet system for outlet
    SystemInterface outletSystem = inletSystem.clone();

    // Check if H2S is present
    if (!outletSystem.hasComponent("H2S")) {
      outStream.setThermoSystem(outletSystem);
      h2sRemovalEfficiency = 0.0;
      h2sRemoved = 0.0;
      inletH2SConcentration = 0.0;
      outletH2SConcentration = 0.0;
      setCalculationIdentifier(id);
      return;
    }

    // Get inlet H2S data
    int gasPhaseIndex = 0;
    if (outletSystem.hasPhaseType("gas")) {
      gasPhaseIndex = outletSystem.getPhaseIndex("gas");
    }

    double h2sMolFrac = outletSystem.getPhase(gasPhaseIndex).getComponent("H2S").getx();
    double totalMolarFlow = outletSystem.getTotalNumberOfMoles() * 3600; // mol/hr
    double h2sMolFlow = h2sMolFrac * totalMolarFlow; // mol H2S/hr
    double h2sMassFlow = h2sMolFlow * MW_H2S / 1000.0; // kg H2S/hr

    inletH2SConcentration = h2sMolFrac * 1e6; // ppm

    // Calculate removal efficiency
    h2sRemovalEfficiency = calculateRemovalEfficiency(h2sMassFlow, outletSystem.getTemperature(),
        outletSystem.getPressure());

    // Calculate H2S removed
    h2sRemoved = h2sMassFlow * h2sRemovalEfficiency; // kg/hr

    // Calculate actual scavenger consumption (stoichiometric amount for H2S removed)
    actualScavengerConsumption = h2sRemoved * scavengerType.getBaseStoichiometry();

    // Update outlet composition
    double outletH2SMolFrac = h2sMolFrac * (1.0 - h2sRemovalEfficiency);
    outletH2SConcentration = outletH2SMolFrac * 1e6; // ppm

    // Remove H2S from system
    double h2sMolesRemoved = h2sRemoved * 1000.0 / MW_H2S / 3600.0; // mol/s
    double currentH2SMoles = outletSystem.getComponent("H2S").getNumberOfmoles();
    double newH2SMoles = currentH2SMoles * (1.0 - h2sRemovalEfficiency);

    if (newH2SMoles < 1e-20) {
      newH2SMoles = 1e-20; // Avoid zero
    }

    // Adjust composition - remove H2S moles
    outletSystem.addComponent("H2S", -(currentH2SMoles - newH2SMoles));

    // Re-flash to get new phase equilibrium
    outletSystem.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(outletSystem);
    ops.TPflash();
    outletSystem.initProperties();

    outStream.setThermoSystem(outletSystem);
    setCalculationIdentifier(id);
  }

  /**
   * Get the calculated H2S removal efficiency.
   *
   * @return removal efficiency as fraction (0-1)
   */
  public double getH2SRemovalEfficiency() {
    return h2sRemovalEfficiency;
  }

  /**
   * Get the H2S removal efficiency as percentage.
   *
   * @return removal efficiency as percentage
   */
  public double getH2SRemovalEfficiencyPercent() {
    return h2sRemovalEfficiency * 100.0;
  }

  /**
   * Get the mass of H2S removed.
   *
   * @param unit mass flow unit ("kg/hr", "lb/hr", "kg/day")
   * @return H2S removed in specified unit
   */
  public double getH2SRemoved(String unit) {
    if (unit.equals("kg/hr")) {
      return h2sRemoved;
    } else if (unit.equals("lb/hr")) {
      return h2sRemoved * 2.20462;
    } else if (unit.equals("kg/day")) {
      return h2sRemoved * 24.0;
    } else if (unit.equals("lb/day")) {
      return h2sRemoved * 2.20462 * 24.0;
    }
    return h2sRemoved;
  }

  /**
   * Get inlet H2S concentration.
   *
   * @return inlet H2S in ppm (molar)
   */
  public double getInletH2SConcentration() {
    return inletH2SConcentration;
  }

  /**
   * Get outlet H2S concentration.
   *
   * @return outlet H2S in ppm (molar)
   */
  public double getOutletH2SConcentration() {
    return outletH2SConcentration;
  }

  /**
   * Get actual scavenger consumption rate.
   *
   * @param unit mass flow unit ("kg/hr", "lb/hr")
   * @return consumption rate in specified unit
   */
  public double getActualScavengerConsumption(String unit) {
    if (unit.equals("lb/hr")) {
      return actualScavengerConsumption * 2.20462;
    }
    return actualScavengerConsumption;
  }

  /**
   * Get scavenger excess over stoichiometric requirement.
   *
   * @return excess as fraction (0 = stoichiometric, 1 = 100% excess)
   */
  public double getScavengerExcess() {
    return scavengerExcess;
  }

  /**
   * Calculate scavenger cost.
   *
   * @param costPerUnit cost per unit of scavenger
   * @param unit cost unit basis ("$/L", "$/gal", "$/kg", "$/lb")
   * @return hourly cost in same currency
   */
  public double calculateHourlyCost(double costPerUnit, String unit) {
    double rateKgHr = getScavengerInjectionRateKgPerHour();
    double density = scavengerType.getDensity();

    if (unit.equals("$/kg")) {
      return costPerUnit * rateKgHr;
    } else if (unit.equals("$/lb")) {
      return costPerUnit * rateKgHr * 2.20462;
    } else if (unit.equals("$/L") && density > 0) {
      return costPerUnit * rateKgHr / density;
    } else if (unit.equals("$/gal") && density > 0) {
      return costPerUnit * rateKgHr / density / 3.78541;
    }
    return 0.0;
  }

  /**
   * Get summary report of scavenger performance.
   *
   * @return formatted string with performance summary
   */
  public String getPerformanceSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== H2S Scavenger Performance Summary ===\n");
    sb.append(String.format("Scavenger Type: %s\n", scavengerType.getDisplayName()));
    sb.append(
        String.format("Injection Rate: %.2f %s\n", scavengerInjectionRate, injectionRateUnit));
    sb.append(String.format("Active Concentration: %.1f%%\n", scavengerConcentration * 100));
    sb.append("\n--- Results ---\n");
    sb.append(String.format("Inlet H2S: %.1f ppm\n", inletH2SConcentration));
    sb.append(String.format("Outlet H2S: %.1f ppm\n", outletH2SConcentration));
    sb.append(String.format("Removal Efficiency: %.1f%%\n", h2sRemovalEfficiency * 100));
    sb.append(String.format("H2S Removed: %.3f kg/hr\n", h2sRemoved));
    sb.append(String.format("Scavenger Consumption: %.3f kg/hr\n", actualScavengerConsumption));
    sb.append(String.format("Scavenger Excess: %.1f%%\n", scavengerExcess * 100));
    return sb.toString();
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new H2SScavengerResponse(this));
  }

  /**
   * Response class for JSON serialization.
   */
  public static class H2SScavengerResponse {
    /** Equipment name. */
    public String name;
    /** Scavenger type. */
    public String scavengerType;
    /** Injection rate. */
    public double injectionRate;
    /** Injection rate unit. */
    public String injectionRateUnit;
    /** Active concentration. */
    public double concentration;
    /** Inlet H2S ppm. */
    public double inletH2Sppm;
    /** Outlet H2S ppm. */
    public double outletH2Sppm;
    /** Removal efficiency percent. */
    public double removalEfficiencyPercent;
    /** H2S removed kg/hr. */
    public double h2sRemovedKgPerHr;
    /** Scavenger consumption kg/hr. */
    public double scavengerConsumptionKgPerHr;
    /** Scavenger excess percent. */
    public double scavengerExcessPercent;

    /**
     * Constructor from H2SScavenger.
     *
     * @param scavenger the H2S scavenger unit
     */
    public H2SScavengerResponse(H2SScavenger scavenger) {
      this.name = scavenger.getName();
      this.scavengerType = scavenger.getScavengerType().getDisplayName();
      this.injectionRate = scavenger.scavengerInjectionRate;
      this.injectionRateUnit = scavenger.injectionRateUnit;
      this.concentration = scavenger.scavengerConcentration;
      this.inletH2Sppm = scavenger.inletH2SConcentration;
      this.outletH2Sppm = scavenger.outletH2SConcentration;
      this.removalEfficiencyPercent = scavenger.h2sRemovalEfficiency * 100;
      this.h2sRemovedKgPerHr = scavenger.h2sRemoved;
      this.scavengerConsumptionKgPerHr = scavenger.actualScavengerConsumption;
      this.scavengerExcessPercent = scavenger.scavengerExcess * 100;
    }
  }
}
