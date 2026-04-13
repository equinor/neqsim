package neqsim.process.equipment.lng;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.standards.gasquality.Standard_ISO6578;
import neqsim.standards.gasquality.Standard_ISO6976;
import neqsim.standards.gasquality.Standard_ISO6976_2016;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Multi-layer LNG tank model with stratification support.
 *
 * <p>
 * This model extends the single-layer approach of {@code LNGship.solveTransient()} to support
 * multiple horizontal liquid layers. Each layer has its own composition, temperature, and density.
 * The model tracks how preferential boil-off of lighter components (N2, methane) enriches the
 * residual liquid in heavier components over time.
 * </p>
 *
 * <p>
 * Physics modeled:
 * </p>
 * <ul>
 * <li><b>Heat ingress:</b> Q = U * A * (T_ambient - T_LNG) distributed to layers by wall contact
 * area fraction</li>
 * <li><b>Boil-off:</b> Bubble-point flash at tank pressure determines vapor composition; moles
 * removed proportional to gas-phase mole fractions (following LNGship pattern)</li>
 * <li><b>Quality tracking:</b> ISO 6578 density, ISO 6976 WI/GCV at each time step</li>
 * <li><b>Stratification:</b> Multiple layers with independent compositions and temperatures; layers
 * can be merged when density difference drops below threshold</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LNGTankLayeredModel implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1006L;

  /** Logger object. */
  private static final Logger logger = LogManager.getLogger(LNGTankLayeredModel.class);

  /** List of liquid layers from bottom (index 0) to top. */
  private List<LNGTankLayer> layers;

  /** Tank total volume (m3). */
  private double totalTankVolume = 140000.0;

  /** Tank pressure (bara). */
  private double tankPressure = 1.013;

  /** Overall heat transfer coefficient (W/m2/K). */
  private double overallHeatTransferCoeff = 0.04;

  /** Tank outer surface area (m2). */
  private double tankSurfaceArea = 12000.0;

  /** Ambient temperature (K). */
  private double ambientTemperature = 273.15 + 25.0;

  /** Minimum density difference to maintain separate layers (kg/m3). */
  private double layerMergeDensityThreshold = 0.5;

  /** Reference thermo system (used as template for flash calculations). */
  private transient SystemInterface referenceSystem;

  /** ISO 6976 standard for quality calculations. */
  private transient Standard_ISO6976 standardISO6976;

  /** Current vapor composition from last flash. */
  private Map<String, Double> currentVaporComposition;

  /** Current bulk liquid temperature (K). */
  private double bulkTemperature;

  /** Current bulk liquid density (kg/m3). */
  private double bulkDensity;

  /** Current total liquid moles. */
  private double totalLiquidMoles;

  /** Sloshing mixing enhancement factor (1.0 = no sloshing, &gt;1.0 = enhanced mixing). */
  private double sloshingMixingFactor = 1.0;

  /**
   * Constructor for LNGTankLayeredModel.
   *
   * @param referenceSystem template thermodynamic system (defines EOS and components)
   */
  public LNGTankLayeredModel(SystemInterface referenceSystem) {
    this.referenceSystem = referenceSystem;
    this.layers = new ArrayList<LNGTankLayer>();
    this.currentVaporComposition = new LinkedHashMap<String, Double>();
  }

  /**
   * Initialise the tank with a single well-mixed liquid layer.
   *
   * <p>
   * Performs a bubble-point temperature flash at tank pressure to determine equilibrium conditions,
   * then creates a single layer with the full liquid inventory. This follows the same
   * initialisation pattern as {@code LNGship.createSystem()}.
   * </p>
   *
   * @param totalLiquidVolume initial liquid volume in tank (m3)
   */
  public void initialise(double totalLiquidVolume) {
    SystemInterface initSystem = referenceSystem.clone();
    initSystem.setPressure(tankPressure);

    ThermodynamicOperations ops = new ThermodynamicOperations(initSystem);
    try {
      ops.bubblePointTemperatureFlash();
    } catch (Exception ex) {
      logger.error("Failed bubble-point flash during initialisation", ex);
      return;
    }
    initSystem.init(0);

    // Calculate initial density using ISO 6578
    Standard_ISO6578 densityStd = new Standard_ISO6578(initSystem);
    densityStd.calculate();
    double initialDensity = densityStd.getValue("density");

    // Calculate initial moles to fill the requested volume
    // Following LNGship pattern: moles = volume * density / molarMass
    double molarMass = initSystem.getPhase(1).getMolarMass();
    double initialMoles = totalLiquidVolume * initialDensity / molarMass;

    // Scale thermo system to correct number of moles
    double currentMoles = initSystem.getTotalNumberOfMoles();
    for (int i = 0; i < initSystem.getPhase(0).getNumberOfComponents(); i++) {
      String name = initSystem.getPhase(0).getComponent(i).getComponentName();
      double zi = initSystem.getPhase(0).getComponent(i).getz();
      initSystem.addComponent(name, (initialMoles - currentMoles) * zi);
    }

    // Re-flash with correct composition and moles
    initSystem.init(0);
    ops = new ThermodynamicOperations(initSystem);
    try {
      ops.bubblePointTemperatureFlash();
    } catch (Exception ex) {
      logger.error("Failed re-flash after scaling", ex);
    }
    initSystem.init(0);

    // Create single layer
    LNGTankLayer layer =
        new LNGTankLayer(0, initialMoles, initSystem.getTemperature(), tankPressure);
    layer.setThermoSystem(initSystem);

    // Extract liquid composition
    Map<String, Double> liqComp = new LinkedHashMap<String, Double>();
    for (int i = 0; i < initSystem.getPhase(1).getNumberOfComponents(); i++) {
      String name = initSystem.getPhase(1).getComponent(i).getComponentName();
      double x = initSystem.getPhase(1).getComponent(i).getx();
      liqComp.put(name, x);
    }
    layer.setComposition(liqComp);
    layer.setDensity(initialDensity);
    layer.setMolarMass(molarMass);
    layer.setVolume(totalLiquidVolume);

    layers.clear();
    layers.add(layer);

    this.bulkTemperature = initSystem.getTemperature();
    this.bulkDensity = initialDensity;
    this.totalLiquidMoles = initialMoles;

    // Initialise ISO 6976 standard
    standardISO6976 = new Standard_ISO6976_2016(initSystem, 0, 25, "volume");

    logger.info(String.format("Tank initialised: V=%.0f m3, T=%.2f K, rho=%.1f kg/m3, n=%.2e mol",
        totalLiquidVolume, bulkTemperature, initialDensity, initialMoles));
  }

  /**
   * Add a new liquid layer on top of existing layers (e.g., heel mixing, new cargo parcel).
   *
   * @param layer the new layer to add on top
   */
  public void addLayerOnTop(LNGTankLayer layer) {
    layer.setLayerIndex(layers.size());
    layers.add(layer);
  }

  /**
   * Advance the tank model by one time step.
   *
   * <p>
   * This method:
   * </p>
   * <ol>
   * <li>Calculates heat ingress from ambient</li>
   * <li>Distributes heat to layers proportional to wall contact area</li>
   * <li>Performs bubble-point flash on the top layer to determine vapor composition</li>
   * <li>Removes boil-off moles from the top layer (following LNGship pattern)</li>
   * <li>Updates density, volume, and quality KPIs</li>
   * <li>Checks for layer merging</li>
   * </ol>
   *
   * @param timeStepHours time step size (hours)
   * @param currentAmbientTemp current ambient temperature (K)
   * @return ageing result snapshot for this time step
   */
  public LNGAgeingResult step(double timeStepHours, double currentAmbientTemp) {
    this.ambientTemperature = currentAmbientTemp;

    if (layers.isEmpty()) {
      logger.warn("No layers in tank, cannot step");
      return new LNGAgeingResult();
    }

    // 1. Calculate heat ingress: Q = U * A * (T_amb - T_LNG)
    double heatIngress =
        overallHeatTransferCoeff * tankSurfaceArea * (ambientTemperature - bulkTemperature);
    if (heatIngress < 0) {
      heatIngress = 0.0;
    }
    double heatIngressJoules = heatIngress * timeStepHours * 3600.0; // W * s = J

    // 2. Perform VLE flash on top layer to get equilibrium vapor composition
    LNGTankLayer topLayer = layers.get(layers.size() - 1);
    SystemInterface flashSystem = buildFlashSystem(topLayer);

    ThermodynamicOperations flashOps = new ThermodynamicOperations(flashSystem);
    try {
      flashOps.bubblePointTemperatureFlash();
    } catch (Exception ex) {
      logger.error("Bubble-point flash failed at step", ex);
    }
    flashSystem.init(0);

    // Extract vapor composition (following LNGship pattern)
    currentVaporComposition.clear();
    double[] xgas = new double[flashSystem.getPhase(0).getNumberOfComponents()];
    for (int i = 0; i < flashSystem.getPhase(0).getNumberOfComponents(); i++) {
      String name = flashSystem.getPhase(0).getComponent(i).getComponentName();
      double yi = flashSystem.getPhase(0).getComponent(i).getx();
      currentVaporComposition.put(name, yi);
      xgas[i] = yi;
    }

    // 3. Calculate molar boil-off: BOG = Q / latentHeat (in moles per time step)
    double latentHeat = estimateLatentHeat(flashSystem);
    double molarMass = flashSystem.getPhase(1).getMolarMass();
    double bogMassRate = 0.0;
    double bogMolesThisStep = 0.0;

    if (latentHeat > 0 && molarMass > 0) {
      double bogMassKgPerStep = heatIngressJoules / latentHeat;
      bogMolesThisStep = bogMassKgPerStep / molarMass;
      bogMassRate = bogMassKgPerStep / timeStepHours; // kg/hr

      // Sloshing can enhance surface renewal and thus BOG
      bogMolesThisStep *= sloshingMixingFactor;
    }

    // 4. Remove boil-off from top layer (LNGship pattern: remove gas-composition-weighted moles)
    if (bogMolesThisStep > 0 && bogMolesThisStep < topLayer.getTotalMoles()) {
      topLayer.removeVapor(bogMolesThisStep, currentVaporComposition);
    }

    // 5. Distribute heat to layers (simple: all to bottom in single-layer case)
    if (layers.size() == 1) {
      double cpEstimate = estimateCp(flashSystem);
      if (cpEstimate > 0) {
        topLayer.addHeat(heatIngressJoules, cpEstimate);
      }
    } else {
      distributeHeatToLayers(heatIngressJoules, flashSystem);
    }

    // 6. Update bulk properties
    updateBulkProperties();

    // 7. Check for layer merging
    mergeSimilarLayers();

    // 8. Build result snapshot
    LNGAgeingResult result = buildResult(heatIngress, bogMassRate, bogMolesThisStep);
    return result;
  }

  /**
   * Build a thermo system for flash calculation from a layer's composition.
   *
   * @param layer the tank layer
   * @return configured thermo system ready for flash
   */
  private SystemInterface buildFlashSystem(LNGTankLayer layer) {
    SystemInterface system = referenceSystem.clone();
    system.setPressure(tankPressure);
    system.setTemperature(layer.getTemperature());

    // Set composition from layer
    system.init(0);
    double currentMoles = system.getTotalNumberOfMoles();
    Map<String, Double> comp = layer.getComposition();

    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      String name = system.getPhase(0).getComponent(i).getComponentName();
      double targetFrac = comp.containsKey(name) ? comp.get(name) : 0.0;
      double currentFrac = system.getPhase(0).getComponent(i).getz();
      double molesNeeded = (targetFrac - currentFrac) * layer.getTotalMoles();
      if (Math.abs(molesNeeded) > 1e-20) {
        system.addComponent(name, molesNeeded);
      }
    }
    return system;
  }

  /**
   * Estimate latent heat of vaporisation from a flashed system (J/kg).
   *
   * @param flashSystem flashed thermo system with gas and liquid phases
   * @return latent heat (J/kg), or default 510000 if calculation fails
   */
  private double estimateLatentHeat(SystemInterface flashSystem) {
    try {
      flashSystem.initProperties();
      if (flashSystem.getNumberOfPhases() > 1 && flashSystem.hasPhaseType("gas")
          && flashSystem.hasPhaseType("oil")) {
        double hGas = flashSystem.getPhase("gas").getEnthalpy()
            / flashSystem.getPhase("gas").getNumberOfMolesInPhase()
            / flashSystem.getPhase("gas").getMolarMass("kg/mol");
        double hLiq = flashSystem.getPhase("oil").getEnthalpy()
            / flashSystem.getPhase("oil").getNumberOfMolesInPhase()
            / flashSystem.getPhase("oil").getMolarMass("kg/mol");
        double calc = Math.abs(hGas - hLiq);
        if (calc > 100.0) {
          return calc;
        }
      }
    } catch (Exception ex) {
      logger.debug("Latent heat estimation failed, using default", ex);
    }
    return 510000.0; // default LNG latent heat J/kg
  }

  /**
   * Estimate molar heat capacity of the liquid phase (J/(mol*K)).
   *
   * @param flashSystem flashed thermo system
   * @return molar Cp (J/(mol*K)), or default 55.0 if estimation fails
   */
  private double estimateCp(SystemInterface flashSystem) {
    try {
      if (flashSystem.hasPhaseType("oil")) {
        // Cp in J/(mol*K)
        double cpMass = flashSystem.getPhase("oil").getCp("J/kgK");
        double molarMass = flashSystem.getPhase("oil").getMolarMass("kg/mol");
        return cpMass * molarMass;
      }
    } catch (Exception ex) {
      logger.debug("Cp estimation failed, using default", ex);
    }
    return 55.0; // approximate molar Cp for LNG in J/(mol*K)
  }

  /**
   * Distribute heat ingress across multiple layers based on wall contact fraction.
   *
   * @param totalHeatJ total heat ingress (J)
   * @param flashSystem thermo system for Cp estimation
   */
  private void distributeHeatToLayers(double totalHeatJ, SystemInterface flashSystem) {
    double totalVolume = 0;
    for (LNGTankLayer layer : layers) {
      totalVolume += layer.getVolume();
    }

    double cpEstimate = estimateCp(flashSystem);
    for (LNGTankLayer layer : layers) {
      double fraction = (totalVolume > 0) ? layer.getVolume() / totalVolume : 1.0 / layers.size();
      layer.addHeat(totalHeatJ * fraction, cpEstimate);
    }
  }

  /**
   * Update bulk properties from all layers.
   */
  private void updateBulkProperties() {
    double totalMoles = 0;
    double totalMass = 0;
    double totalVolume = 0;
    double weightedTemp = 0;

    for (LNGTankLayer layer : layers) {
      totalMoles += layer.getTotalMoles();
      double mass = layer.getMass();
      totalMass += mass;
      totalVolume += layer.getVolume();
      weightedTemp += layer.getTemperature() * mass;
    }

    this.totalLiquidMoles = totalMoles;
    if (totalMass > 0) {
      this.bulkTemperature = weightedTemp / totalMass;
    }
    if (totalVolume > 0) {
      this.bulkDensity = totalMass / totalVolume;
    }
  }

  /**
   * Merge layers whose density difference is below threshold.
   */
  private void mergeSimilarLayers() {
    if (layers.size() < 2) {
      return;
    }

    // Apply sloshing mixing: if wave height causes significant sloshing, reduce threshold
    double effectiveThreshold = layerMergeDensityThreshold / sloshingMixingFactor;

    List<LNGTankLayer> merged = new ArrayList<LNGTankLayer>();
    LNGTankLayer current = layers.get(0);

    for (int i = 1; i < layers.size(); i++) {
      LNGTankLayer next = layers.get(i);
      if (current.getDensityDifference(next) < effectiveThreshold) {
        current = mergeTwoLayers(current, next);
      } else {
        merged.add(current);
        current = next;
      }
    }
    merged.add(current);

    // Re-index layers
    for (int i = 0; i < merged.size(); i++) {
      merged.get(i).setLayerIndex(i);
    }
    this.layers = merged;
  }

  /**
   * Merge two layers into one (mass-weighted mixing).
   *
   * @param lower lower layer
   * @param upper upper layer
   * @return merged layer
   */
  private LNGTankLayer mergeTwoLayers(LNGTankLayer lower, LNGTankLayer upper) {
    double totalMoles = lower.getTotalMoles() + upper.getTotalMoles();
    double massLow = lower.getMass();
    double massUp = upper.getMass();
    double totalMass = massLow + massUp;

    // Mass-weighted temperature
    double mergedTemp = (totalMass > 0)
        ? (lower.getTemperature() * massLow + upper.getTemperature() * massUp) / totalMass
        : lower.getTemperature();

    // Mole-averaged composition
    Map<String, Double> mergedComp = new LinkedHashMap<String, Double>();
    for (Map.Entry<String, Double> entry : lower.getComposition().entrySet()) {
      String comp = entry.getKey();
      double xLow = entry.getValue() * lower.getTotalMoles();
      double xUp = upper.getComposition().containsKey(comp)
          ? upper.getComposition().get(comp) * upper.getTotalMoles()
          : 0.0;
      mergedComp.put(comp, (xLow + xUp) / totalMoles);
    }
    // Add any components only in upper
    for (Map.Entry<String, Double> entry : upper.getComposition().entrySet()) {
      if (!mergedComp.containsKey(entry.getKey())) {
        mergedComp.put(entry.getKey(), entry.getValue() * upper.getTotalMoles() / totalMoles);
      }
    }

    LNGTankLayer merged =
        new LNGTankLayer(lower.getLayerIndex(), totalMoles, mergedTemp, lower.getPressure());
    merged.setComposition(mergedComp);
    merged.setVolume(lower.getVolume() + upper.getVolume());
    merged.setMolarMass((totalMass > 0) ? totalMass / totalMoles : lower.getMolarMass());
    merged
        .setDensity((merged.getVolume() > 0) ? totalMass / merged.getVolume() : lower.getDensity());
    return merged;
  }

  /**
   * Build an ageing result snapshot from current state.
   *
   * @param heatIngressW heat ingress (W)
   * @param bogMassRateKgHr BOG mass flow rate (kg/hr)
   * @param bogMolesStep BOG moles removed this step
   * @return result snapshot
   */
  private LNGAgeingResult buildResult(double heatIngressW, double bogMassRateKgHr,
      double bogMolesStep) {
    LNGAgeingResult result = new LNGAgeingResult();

    result.setTemperature(bulkTemperature);
    result.setPressure(tankPressure);
    result.setDensity(bulkDensity);
    result.setHeatIngressKW(heatIngressW / 1000.0);
    result.setBogMassFlowRate(bogMassRateKgHr);
    result.setAmbientTemperature(ambientTemperature);
    result.setNumberOfLayers(layers.size());

    // Calculate total liquid volume and mass
    double totalVol = 0;
    double totalMass = 0;
    for (LNGTankLayer layer : layers) {
      totalVol += layer.getVolume();
      totalMass += layer.getMass();
    }
    result.setLiquidVolume(totalVol);
    result.setLiquidMass(totalMass);
    result.setLiquidMoles(totalLiquidMoles);

    // BOR %/day
    if (totalMass > 0) {
      result.setBoilOffRatePctPerDay(bogMassRateKgHr * 24.0 / totalMass * 100.0);
    }

    // Max density difference between layers
    if (layers.size() > 1) {
      double maxDiff = 0;
      for (int i = 0; i < layers.size() - 1; i++) {
        double diff = layers.get(i).getDensityDifference(layers.get(i + 1));
        if (diff > maxDiff) {
          maxDiff = diff;
        }
      }
      result.setMaxLayerDensityDifference(maxDiff);
    }

    // Get bulk liquid and vapor composition
    result.setLiquidComposition(getBulkLiquidComposition());
    result.setVaporComposition(new LinkedHashMap<String, Double>(currentVaporComposition));

    // Calculate quality KPIs using ISO 6976 if available
    calculateQualityKPIs(result);

    return result;
  }

  /**
   * Calculate ISO 6976 quality KPIs (WI, GCV) from current state.
   *
   * @param result ageing result to populate with quality data
   */
  private void calculateQualityKPIs(LNGAgeingResult result) {
    if (standardISO6976 == null || layers.isEmpty()) {
      return;
    }

    try {
      LNGTankLayer topLayer = layers.get(layers.size() - 1);
      SystemInterface qualitySystem = buildFlashSystem(topLayer);
      qualitySystem.init(0);
      ThermodynamicOperations ops = new ThermodynamicOperations(qualitySystem);
      ops.bubblePointTemperatureFlash();
      qualitySystem.init(0);

      // ISO 6578 density
      Standard_ISO6578 densityStd = new Standard_ISO6578(qualitySystem);
      densityStd.calculate();
      result.setDensity(densityStd.getValue("density"));

      // ISO 6976 quality
      standardISO6976.setThermoSystem(qualitySystem);
      standardISO6976.calculate();
      result.setWobbeIndex(standardISO6976.getValue("SuperiorWobbeIndex") / 1000.0);
      result.setGcvVolumetric(standardISO6976.getValue("SuperiorCalorificValue") / 1000.0);

      standardISO6976.setReferenceType("mass");
      result.setGcvMass(standardISO6976.getValue("SuperiorCalorificValue") / 1000.0);
      standardISO6976.setReferenceType("volume");

      // Methane Number estimate (simplified GPA 2145 correlation)
      double xC1 = 0;
      double xC2 = 0;
      double xC3 = 0;
      Map<String, Double> comp = topLayer.getComposition();
      if (comp.containsKey("methane")) {
        xC1 = comp.get("methane");
      }
      if (comp.containsKey("ethane")) {
        xC2 = comp.get("ethane");
      }
      if (comp.containsKey("propane")) {
        xC3 = comp.get("propane");
      }
      // Simplified MN correlation: MN ~ 137.78 * xC1 - 29.948 * xC2 - 18.193 * xC3 - 167.06 *
      // xN2
      double xN2 = comp.containsKey("nitrogen") ? comp.get("nitrogen") : 0.0;
      double mn = 137.78 * xC1 - 29.948 * xC2 - 18.193 * xC3 - 167.06 * xN2;
      if (mn < 0) {
        mn = 0;
      }
      result.setMethaneNumber(mn);

    } catch (Exception ex) {
      logger.warn("Failed to calculate quality KPIs", ex);
    }
  }

  /**
   * Get the mole-averaged bulk liquid composition across all layers.
   *
   * @return map of component name to bulk mole fraction
   */
  public Map<String, Double> getBulkLiquidComposition() {
    Map<String, Double> bulk = new LinkedHashMap<String, Double>();
    double totalMoles = 0;

    for (LNGTankLayer layer : layers) {
      totalMoles += layer.getTotalMoles();
    }

    if (totalMoles <= 0) {
      return bulk;
    }

    for (LNGTankLayer layer : layers) {
      for (Map.Entry<String, Double> entry : layer.getComposition().entrySet()) {
        String comp = entry.getKey();
        double contribution = entry.getValue() * layer.getTotalMoles() / totalMoles;
        if (bulk.containsKey(comp)) {
          bulk.put(comp, bulk.get(comp) + contribution);
        } else {
          bulk.put(comp, contribution);
        }
      }
    }
    return bulk;
  }

  /**
   * Get the list of liquid layers.
   *
   * @return list of layers (bottom to top)
   */
  public List<LNGTankLayer> getLayers() {
    return layers;
  }

  /**
   * Get total tank volume.
   *
   * @return tank volume (m3)
   */
  public double getTotalTankVolume() {
    return totalTankVolume;
  }

  /**
   * Set total tank volume.
   *
   * @param totalTankVolume tank volume (m3)
   */
  public void setTotalTankVolume(double totalTankVolume) {
    this.totalTankVolume = totalTankVolume;
  }

  /**
   * Get tank pressure.
   *
   * @return pressure (bara)
   */
  public double getTankPressure() {
    return tankPressure;
  }

  /**
   * Set tank pressure.
   *
   * @param tankPressure pressure (bara)
   */
  public void setTankPressure(double tankPressure) {
    this.tankPressure = tankPressure;
  }

  /**
   * Get overall heat transfer coefficient.
   *
   * @return U (W/m2/K)
   */
  public double getOverallHeatTransferCoeff() {
    return overallHeatTransferCoeff;
  }

  /**
   * Set overall heat transfer coefficient.
   *
   * @param u heat transfer coefficient (W/m2/K)
   */
  public void setOverallHeatTransferCoeff(double u) {
    this.overallHeatTransferCoeff = u;
  }

  /**
   * Get tank surface area.
   *
   * @return surface area (m2)
   */
  public double getTankSurfaceArea() {
    return tankSurfaceArea;
  }

  /**
   * Set tank surface area.
   *
   * @param tankSurfaceArea surface area (m2)
   */
  public void setTankSurfaceArea(double tankSurfaceArea) {
    this.tankSurfaceArea = tankSurfaceArea;
  }

  /**
   * Get current bulk temperature.
   *
   * @return bulk temperature (K)
   */
  public double getBulkTemperature() {
    return bulkTemperature;
  }

  /**
   * Get current bulk density.
   *
   * @return bulk density (kg/m3)
   */
  public double getBulkDensity() {
    return bulkDensity;
  }

  /**
   * Get total liquid moles.
   *
   * @return total moles
   */
  public double getTotalLiquidMoles() {
    return totalLiquidMoles;
  }

  /**
   * Get current vapor composition.
   *
   * @return map of component name to vapor mole fraction
   */
  public Map<String, Double> getCurrentVaporComposition() {
    return currentVaporComposition;
  }

  /**
   * Get layer merge density threshold.
   *
   * @return density threshold (kg/m3)
   */
  public double getLayerMergeDensityThreshold() {
    return layerMergeDensityThreshold;
  }

  /**
   * Set layer merge density threshold.
   *
   * @param threshold density threshold (kg/m3)
   */
  public void setLayerMergeDensityThreshold(double threshold) {
    this.layerMergeDensityThreshold = threshold;
  }

  /**
   * Get the sloshing mixing enhancement factor.
   *
   * @return mixing factor (1.0 = none)
   */
  public double getSloshingMixingFactor() {
    return sloshingMixingFactor;
  }

  /**
   * Set the sloshing mixing enhancement factor.
   *
   * <p>
   * Sloshing from wave action enhances mixing between layers and surface renewal at the
   * liquid-vapor interface. Factor of 1.0 means no enhancement (quiescent storage), values greater
   * than 1.0 represent enhanced mixing due to ship motion.
   * </p>
   *
   * @param factor mixing factor (1.0 = no sloshing effect)
   */
  public void setSloshingMixingFactor(double factor) {
    this.sloshingMixingFactor = Math.max(1.0, factor);
  }

  /**
   * Get the reference thermodynamic system.
   *
   * @return reference system
   */
  public SystemInterface getReferenceSystem() {
    return referenceSystem;
  }
}
