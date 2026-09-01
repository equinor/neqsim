package neqsim.process.equipment.reactor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Wall-reaction source that converts historical iron corrosion products into an S8-bearing process stream.
 *
 * <p>
 * The model deliberately separates wall history from fluid equilibrium. It can form FeS from bare-steel corrosion,
 * dissolved Fe2+, siderite sulfidation, and reactive iron oxide. It can then oxidize the available FeS using oxygen
 * transferred from the gas. Elemental sulfur is introduced into the outlet as {@code S8}; the existing NeqSim TP-solid
 * flash, sulfur filter, and compressor deposit models can therefore be used downstream without a separate sulfur
 * property model.
 * </p>
 *
 * <p>
 * Reaction rates and product selectivity depend strongly on mineralogy, scale morphology, wetting, and mass transfer.
 * Default kinetic rate constants are zero so the model never creates a hidden wall source. Users must supply measured,
 * fitted, or scenario values. When kinetics are not calibrated, the result includes explicit low/base/high
 * sulfur-source estimates.
 * </p>
 *
 * <h2>Screening reactions</h2>
 *
 * <pre>
 * Fe + H2S -&gt; FeS + H2
 * FeCO3 + H2S -&gt; FeS + CO2 + H2O
 * Fe2O3 + 3 H2S -&gt; 2 FeS + S0 + 3 H2O
 * 4 FeS + 3 O2 -&gt; 2 Fe2O3 + 4 S0  (default oxidation basis)
 * </pre>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class IronSulfideOxidationSource extends TwoPortEquipment {
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(IronSulfideOxidationSource.class);

  private static final double FE_MOLAR_MASS_KG_PER_MOL = 0.055845;
  private static final double H2S_MOLAR_MASS_KG_PER_MOL = 0.034081;
  private static final double O2_MOLAR_MASS_KG_PER_MOL = 0.031998;
  private static final double H2_MOLAR_MASS_KG_PER_MOL = 0.002016;
  private static final double CO2_MOLAR_MASS_KG_PER_MOL = 0.044010;
  private static final double WATER_MOLAR_MASS_KG_PER_MOL = 0.018015;
  private static final double STEEL_DENSITY_KG_PER_M3 = 7850.0;
  private static final double HOURS_PER_YEAR = 8760.0;

  private final IronSulfideWallInventory wallInventory;

  // Formation configuration. All kinetic defaults are deliberately zero.
  private double bareSteelCorrosionRateMmPerYear = 0.0;
  private double dissolvedIronRateKgPerHour = 0.0;
  private double feCO3SulfidationFractionPerHour = 0.0;
  private double ironOxideSulfidationFractionPerHour = 0.0;
  private double h2sWallContactFraction = 1.0;
  private boolean requireWetWallForSulfidation = true;

  // Oxidation and mass-transfer configuration.
  private double feSOxidationFractionPerHour = 0.0;
  private double oxygenMolesPerMoleFeS = 0.75;
  private double oxygenTransferFraction = 1.0;
  private double oxygenMassTransferCoefficientMPerS = Double.NaN;
  private double maximumOxygenTransferRateKgPerHour = Double.POSITIVE_INFINITY;
  private double elementalSulfurYieldFraction = 0.50;
  private double lowerElementalSulfurYieldFraction = 0.10;
  private double upperElementalSulfurYieldFraction = 1.00;
  private double kineticUncertaintyFactor = 3.0;
  private boolean kineticsCalibrated = false;
  private double oxidationHeatKJPerMoleFeS = 300.0;

  private boolean solidFlashEnabled = true;
  private double steadyStateInventoryHorizonHours = 1.0;

  // Last calculation results.
  private double feSFormedFromBareSteelKgPerHour;
  private double feSFormedFromDissolvedIronKgPerHour;
  private double feSFormedFromFeCO3KgPerHour;
  private double feSFormedFromIronOxideKgPerHour;
  private double feCO3ConsumedKgPerHour;
  private double ironOxideConsumedKgPerHour;
  private double feSOxidizedKgPerHour;
  private double oxygenAvailableAtWallKgPerHour;
  private double oxygenConsumedKgPerHour;
  private double h2sConsumedKgPerHour;
  private double elementalSulfurRateKgPerHour;
  private double elementalSulfurLowRateKgPerHour;
  private double elementalSulfurHighRateKgPerHour;
  private double oxidizedSulfurEquivalentRateKgPerHour;
  private double ironOxideEquivalentFormedKgPerHour;
  private double hydrogenProducedKgPerHour;
  private double carbonDioxideProducedKgPerHour;
  private double waterProducedKgPerHour;
  private double reactionHeatKW;
  private boolean solidSulfurPresent;
  private String oxidationLimitingFactor = "none";

  /**
   * Create a wall source without an inlet stream.
   *
   * @param name equipment name
   * @param inventory wall corrosion-product inventory
   */
  public IronSulfideOxidationSource(String name, IronSulfideWallInventory inventory) {
    super(name);
    if (inventory == null) {
      throw new IllegalArgumentException("wall inventory must not be null");
    }
    wallInventory = inventory;
  }

  /**
   * Create a wall source connected to a process stream.
   *
   * @param name equipment name
   * @param inletStream gas or multiphase stream contacting the wall
   * @param inventory wall corrosion-product inventory
   */
  public IronSulfideOxidationSource(String name, StreamInterface inletStream, IronSulfideWallInventory inventory) {
    super(name, inletStream);
    if (inventory == null) {
      throw new IllegalArgumentException("wall inventory must not be null");
    }
    wallInventory = inventory;
  }

  /** @return attached wall inventory */
  public IronSulfideWallInventory getWallInventory() {
    return wallInventory;
  }

  /**
   * Evaluate a steady-state source rate without changing the historical wall inventory.
   *
   * @param id calculation identifier
   */
  @Override
  public void run(UUID id) {
    execute(steadyStateInventoryHorizonHours, false, id);
  }

  /**
   * Advance wall formation and oxidation over a transient time step.
   *
   * @param dt time step in seconds
   * @param id calculation identifier
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (!Double.isFinite(dt) || dt < 0.0) {
      throw new IllegalArgumentException("time step must be finite and non-negative");
    }
    double hours = dt > 0.0 ? dt / 3600.0 : steadyStateInventoryHorizonHours;
    execute(hours, dt > 0.0, id);
    increaseTime(dt);
  }

  /**
   * Record and simulate a historical exposure using the current inlet-stream composition.
   *
   * <p>
   * The event controls duration and wall wetting. Gas composition should be set on the inlet stream before this method
   * is called, allowing seawater, sour-service, nitrogen-purge, and restart periods to be represented by a sequence of
   * auditable events.
   * </p>
   *
   * @param event historical exposure event
   * @param id calculation identifier
   */
  public void runExposure(IronSulfideWallInventory.ExposureEvent event, UUID id) {
    if (event == null) {
      throw new IllegalArgumentException("exposure event must not be null");
    }
    wallInventory.recordExposure(event);
    wallInventory.setWettedFraction(event.getWettedFraction());
    execute(Math.max(event.getDurationHours(), 1.0e-12), event.getDurationHours() > 0.0, id);
    increaseTime(event.getDurationHours() * 3600.0);
  }

  private void execute(double inventoryHorizonHours, boolean updateInventory, UUID id) {
    if (getInletStream() == null || getInletStream().getThermoSystem() == null) {
      throw new IllegalStateException("iron sulfide wall source requires an inlet stream");
    }
    resetResults();
    double horizon = Math.max(inventoryHorizonHours, 1.0e-12);
    SystemInterface inletSystem = getInletStream().getThermoSystem();

    calculateFormationRates(inletSystem, horizon);
    calculateOxidationRates(inletSystem, horizon);

    if (updateInventory) {
      updateWallInventory(horizon);
    }

    SystemInterface outletSystem = inletSystem.clone();
    ensureOutletComponents(outletSystem);
    applyWallSourceToStream(outletSystem);
    flashOutlet(outletSystem);
    getOutletStream().setThermoSystem(outletSystem);
    getOutletStream().run(id);
    setCalculationIdentifier(id);
  }

  private void calculateFormationRates(SystemInterface system, double horizonHours) {
    boolean wetEnough = !requireWetWallForSulfidation || wallInventory.getWettedFraction() > 0.0;
    if (!wetEnough) {
      return;
    }

    double effectiveWettedArea = wallInventory.getEffectiveWettedAreaM2();
    double bareSteelLossKgPerHour = bareSteelCorrosionRateMmPerYear / 1000.0 / HOURS_PER_YEAR * effectiveWettedArea
        * STEEL_DENSITY_KG_PER_M3;
    double bareFeSMolesPerHour = bareSteelLossKgPerHour / FE_MOLAR_MASS_KG_PER_MOL;
    double dissolvedFeSMolesPerHour = dissolvedIronRateKgPerHour / FE_MOLAR_MASS_KG_PER_MOL;

    double feCO3MolesAvailable = wallInventory.getFeCO3MassKg() / IronSulfideWallInventory.FECO3_MOLAR_MASS_KG_PER_MOL;
    double feCO3MolesPerHour = Math.min(feCO3MolesAvailable / horizonHours,
        feCO3MolesAvailable * feCO3SulfidationFractionPerHour);

    double ironOxideMolesAvailable = wallInventory.getIronOxideEquivalentMassKg()
        / IronSulfideWallInventory.FE2O3_MOLAR_MASS_KG_PER_MOL;
    double ironOxideMolesPerHour = Math.min(ironOxideMolesAvailable / horizonHours,
        ironOxideMolesAvailable * ironOxideSulfidationFractionPerHour);

    double h2sDemandMolesPerHour = bareFeSMolesPerHour + dissolvedFeSMolesPerHour + feCO3MolesPerHour
        + 3.0 * ironOxideMolesPerHour;
    double h2sAvailableMolesPerHour = getComponentFlowKgPerHour(system, "H2S") / H2S_MOLAR_MASS_KG_PER_MOL
        * h2sWallContactFraction;
    double h2sScale = h2sDemandMolesPerHour > 0.0 ? Math.min(1.0, h2sAvailableMolesPerHour / h2sDemandMolesPerHour)
        : 0.0;

    bareFeSMolesPerHour *= h2sScale;
    dissolvedFeSMolesPerHour *= h2sScale;
    feCO3MolesPerHour *= h2sScale;
    ironOxideMolesPerHour *= h2sScale;

    feSFormedFromBareSteelKgPerHour = bareFeSMolesPerHour * IronSulfideWallInventory.FES_MOLAR_MASS_KG_PER_MOL;
    feSFormedFromDissolvedIronKgPerHour = dissolvedFeSMolesPerHour * IronSulfideWallInventory.FES_MOLAR_MASS_KG_PER_MOL;
    feSFormedFromFeCO3KgPerHour = feCO3MolesPerHour * IronSulfideWallInventory.FES_MOLAR_MASS_KG_PER_MOL;
    feSFormedFromIronOxideKgPerHour = 2.0 * ironOxideMolesPerHour * IronSulfideWallInventory.FES_MOLAR_MASS_KG_PER_MOL;
    feCO3ConsumedKgPerHour = feCO3MolesPerHour * IronSulfideWallInventory.FECO3_MOLAR_MASS_KG_PER_MOL;
    ironOxideConsumedKgPerHour = ironOxideMolesPerHour * IronSulfideWallInventory.FE2O3_MOLAR_MASS_KG_PER_MOL;
    h2sConsumedKgPerHour = (bareFeSMolesPerHour + dissolvedFeSMolesPerHour + feCO3MolesPerHour
        + 3.0 * ironOxideMolesPerHour) * H2S_MOLAR_MASS_KG_PER_MOL;
    hydrogenProducedKgPerHour = bareFeSMolesPerHour * H2_MOLAR_MASS_KG_PER_MOL;
    carbonDioxideProducedKgPerHour = feCO3MolesPerHour * CO2_MOLAR_MASS_KG_PER_MOL;
    waterProducedKgPerHour = (feCO3MolesPerHour + 3.0 * ironOxideMolesPerHour) * WATER_MOLAR_MASS_KG_PER_MOL;

    // The iron-sponge sulfidation route liberates one sulfur atom per Fe2O3.
    elementalSulfurRateKgPerHour = ironOxideMolesPerHour * IronSulfideWallInventory.SULFUR_MOLAR_MASS_KG_PER_MOL;
    elementalSulfurLowRateKgPerHour = elementalSulfurRateKgPerHour;
    elementalSulfurHighRateKgPerHour = elementalSulfurRateKgPerHour;
  }

  private void calculateOxidationRates(SystemInterface system, double horizonHours) {
    double formedFeSKgPerHour = feSFormedFromBareSteelKgPerHour + feSFormedFromDissolvedIronKgPerHour
        + feSFormedFromFeCO3KgPerHour + feSFormedFromIronOxideKgPerHour;
    double virtualFeSMassKg = wallInventory.getFeSMassKg() + formedFeSKgPerHour * horizonHours;
    double availableFeSMolesPerHour = virtualFeSMassKg / IronSulfideWallInventory.FES_MOLAR_MASS_KG_PER_MOL
        / horizonHours;
    double kineticCandidateMolesPerHour = virtualFeSMassKg / IronSulfideWallInventory.FES_MOLAR_MASS_KG_PER_MOL
        * feSOxidationFractionPerHour * wallInventory.getFeSPhase().getRelativeOxidationReactivity();
    kineticCandidateMolesPerHour = Math.min(kineticCandidateMolesPerHour, availableFeSMolesPerHour);

    oxygenAvailableAtWallKgPerHour = calculateOxygenAvailableAtWall(system);
    double oxygenLimitedFeSMolesPerHour = oxygenMolesPerMoleFeS > 0.0
        ? oxygenAvailableAtWallKgPerHour / O2_MOLAR_MASS_KG_PER_MOL / oxygenMolesPerMoleFeS
        : 0.0;
    double oxidizedMolesPerHour = Math.min(kineticCandidateMolesPerHour, oxygenLimitedFeSMolesPerHour);

    if (oxidizedMolesPerHour <= 0.0) {
      oxidationLimitingFactor = virtualFeSMassKg <= 0.0 ? "FeS inventory"
          : oxygenAvailableAtWallKgPerHour <= 0.0 ? "oxygen" : "kinetics";
      return;
    }
    if (oxygenLimitedFeSMolesPerHour + 1.0e-15 < kineticCandidateMolesPerHour) {
      oxidationLimitingFactor = "oxygen transfer";
    } else if (kineticCandidateMolesPerHour + 1.0e-15 < availableFeSMolesPerHour) {
      oxidationLimitingFactor = "kinetics";
    } else {
      oxidationLimitingFactor = "FeS inventory";
    }

    feSOxidizedKgPerHour = oxidizedMolesPerHour * IronSulfideWallInventory.FES_MOLAR_MASS_KG_PER_MOL;
    oxygenConsumedKgPerHour = oxidizedMolesPerHour * oxygenMolesPerMoleFeS * O2_MOLAR_MASS_KG_PER_MOL;
    double sulfurEquivalentKgPerHour = oxidizedMolesPerHour * IronSulfideWallInventory.SULFUR_MOLAR_MASS_KG_PER_MOL;
    elementalSulfurRateKgPerHour += sulfurEquivalentKgPerHour * elementalSulfurYieldFraction;
    oxidizedSulfurEquivalentRateKgPerHour = sulfurEquivalentKgPerHour * (1.0 - elementalSulfurYieldFraction);
    ironOxideEquivalentFormedKgPerHour = oxidizedMolesPerHour * 0.5
        * IronSulfideWallInventory.FE2O3_MOLAR_MASS_KG_PER_MOL;
    reactionHeatKW = oxidizedMolesPerHour * oxidationHeatKJPerMoleFeS / 3600.0;

    double lowerMoles = Math.min(oxygenLimitedFeSMolesPerHour, kineticCandidateMolesPerHour / kineticUncertaintyFactor);
    double upperMoles = Math.min(availableFeSMolesPerHour,
        Math.min(oxygenLimitedFeSMolesPerHour, kineticCandidateMolesPerHour * kineticUncertaintyFactor));
    double directSulfur = elementalSulfurLowRateKgPerHour;
    if (kineticsCalibrated) {
      elementalSulfurLowRateKgPerHour = elementalSulfurRateKgPerHour;
      elementalSulfurHighRateKgPerHour = elementalSulfurRateKgPerHour;
    } else {
      elementalSulfurLowRateKgPerHour = directSulfur
          + lowerMoles * IronSulfideWallInventory.SULFUR_MOLAR_MASS_KG_PER_MOL * lowerElementalSulfurYieldFraction;
      elementalSulfurHighRateKgPerHour = directSulfur
          + upperMoles * IronSulfideWallInventory.SULFUR_MOLAR_MASS_KG_PER_MOL * upperElementalSulfurYieldFraction;
    }
  }

  private double calculateOxygenAvailableAtWall(SystemInterface system) {
    double streamOxygenKgPerHour = getComponentFlowKgPerHour(system, "oxygen");
    double available = streamOxygenKgPerHour * oxygenTransferFraction;
    if (Double.isFinite(oxygenMassTransferCoefficientMPerS)) {
      try {
        if (system.hasPhaseType("gas") && system.getPhaseOfType("gas").hasComponent("oxygen")) {
          double oxygenConcentrationKgPerM3 = system.getPhaseOfType("gas").getDensity("kg/m3")
              * system.getPhaseOfType("gas").getWtFrac("oxygen");
          double reactiveArea = wallInventory.getPipeSurfaceAreaM2() * wallInventory.getSurfaceAreaMultiplier();
          double coefficientLimit = oxygenMassTransferCoefficientMPerS * reactiveArea * oxygenConcentrationKgPerM3
              * 3600.0;
          available = Math.min(available, coefficientLimit);
        }
      } catch (Exception ex) {
        logger.warn("Could not evaluate oxygen mass-transfer coefficient limit: {}", ex.getMessage());
      }
    }
    return Math.max(0.0, Math.min(available, maximumOxygenTransferRateKgPerHour));
  }

  private void updateWallInventory(double hours) {
    wallInventory.addFeCO3MassKg(-feCO3ConsumedKgPerHour * hours);
    wallInventory.addIronOxideEquivalentMassKg(-ironOxideConsumedKgPerHour * hours);
    double totalFeSFormationKgPerHour = feSFormedFromBareSteelKgPerHour + feSFormedFromDissolvedIronKgPerHour
        + feSFormedFromFeCO3KgPerHour + feSFormedFromIronOxideKgPerHour;
    wallInventory.addFeSMassKg((totalFeSFormationKgPerHour - feSOxidizedKgPerHour) * hours);
    wallInventory.addIronOxideEquivalentMassKg(ironOxideEquivalentFormedKgPerHour * hours);
  }

  private void applyWallSourceToStream(SystemInterface system) {
    if (h2sConsumedKgPerHour > 0.0) {
      system.addComponent("H2S", -h2sConsumedKgPerHour, "kg/hr");
    }
    if (oxygenConsumedKgPerHour > 0.0) {
      system.addComponent("oxygen", -oxygenConsumedKgPerHour, "kg/hr");
    }
    if (elementalSulfurRateKgPerHour > 0.0) {
      system.addComponent("S8", elementalSulfurRateKgPerHour, "kg/hr");
    }
    if (hydrogenProducedKgPerHour > 0.0) {
      system.addComponent("hydrogen", hydrogenProducedKgPerHour, "kg/hr");
    }
    if (carbonDioxideProducedKgPerHour > 0.0) {
      system.addComponent("CO2", carbonDioxideProducedKgPerHour, "kg/hr");
    }
    if (waterProducedKgPerHour > 0.0) {
      system.addComponent("water", waterProducedKgPerHour, "kg/hr");
    }
  }

  private void ensureOutletComponents(SystemInterface system) {
    String[] components = { "H2S", "oxygen", "S8", "hydrogen", "CO2", "water" };
    for (String component : components) {
      HydrogenProductionUtils.ensureComponent(system, component);
    }
    system.createDatabase(true);
    system.init(0);
  }

  private void flashOutlet(SystemInterface system) {
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    try {
      if (solidFlashEnabled && elementalSulfurRateKgPerHour > 0.0) {
        system.setSolidPhaseCheck("S8");
        operations.TPSolidflash();
      } else {
        operations.TPflash();
      }
      system.initProperties();
      solidSulfurPresent = system.hasPhaseType("solid");
    } catch (Exception ex) {
      logger.warn("Wall-source solid flash failed; falling back to TP flash: {}", ex.getMessage());
      operations.TPflash();
      system.initProperties();
      solidSulfurPresent = false;
    }
  }

  private static double getComponentFlowKgPerHour(SystemInterface system, String name) {
    try {
      if (system.hasComponent(name)) {
        return Math.max(0.0, system.getComponent(name).getFlowRate("kg/hr"));
      }
    } catch (Exception ex) {
      return 0.0;
    }
    return 0.0;
  }

  private void resetResults() {
    feSFormedFromBareSteelKgPerHour = 0.0;
    feSFormedFromDissolvedIronKgPerHour = 0.0;
    feSFormedFromFeCO3KgPerHour = 0.0;
    feSFormedFromIronOxideKgPerHour = 0.0;
    feCO3ConsumedKgPerHour = 0.0;
    ironOxideConsumedKgPerHour = 0.0;
    feSOxidizedKgPerHour = 0.0;
    oxygenAvailableAtWallKgPerHour = 0.0;
    oxygenConsumedKgPerHour = 0.0;
    h2sConsumedKgPerHour = 0.0;
    elementalSulfurRateKgPerHour = 0.0;
    elementalSulfurLowRateKgPerHour = 0.0;
    elementalSulfurHighRateKgPerHour = 0.0;
    oxidizedSulfurEquivalentRateKgPerHour = 0.0;
    ironOxideEquivalentFormedKgPerHour = 0.0;
    hydrogenProducedKgPerHour = 0.0;
    carbonDioxideProducedKgPerHour = 0.0;
    waterProducedKgPerHour = 0.0;
    reactionHeatKW = 0.0;
    solidSulfurPresent = false;
    oxidationLimitingFactor = "none";
  }

  /** @return result values and uncertainty bounds from the last calculation */
  public Map<String, Object> getResults() {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("FeSFormedFromBareSteel_kgPerHour", feSFormedFromBareSteelKgPerHour);
    values.put("FeSFormedFromDissolvedIron_kgPerHour", feSFormedFromDissolvedIronKgPerHour);
    values.put("FeSFormedFromFeCO3_kgPerHour", feSFormedFromFeCO3KgPerHour);
    values.put("FeSFormedFromIronOxide_kgPerHour", feSFormedFromIronOxideKgPerHour);
    values.put("FeCO3Consumed_kgPerHour", feCO3ConsumedKgPerHour);
    values.put("ironOxideConsumed_kgPerHour", ironOxideConsumedKgPerHour);
    values.put("FeSOxidized_kgPerHour", feSOxidizedKgPerHour);
    values.put("oxygenAvailableAtWall_kgPerHour", oxygenAvailableAtWallKgPerHour);
    values.put("oxygenConsumed_kgPerHour", oxygenConsumedKgPerHour);
    values.put("H2SConsumed_kgPerHour", h2sConsumedKgPerHour);
    values.put("elementalSulfur_kgPerHour", elementalSulfurRateKgPerHour);
    values.put("elementalSulfurLow_kgPerHour", elementalSulfurLowRateKgPerHour);
    values.put("elementalSulfurHigh_kgPerHour", elementalSulfurHighRateKgPerHour);
    values.put("oxidizedSulfurEquivalent_kgPerHour", oxidizedSulfurEquivalentRateKgPerHour);
    values.put("ironOxideEquivalentFormed_kgPerHour", ironOxideEquivalentFormedKgPerHour);
    values.put("hydrogenProduced_kgPerHour", hydrogenProducedKgPerHour);
    values.put("carbonDioxideProduced_kgPerHour", carbonDioxideProducedKgPerHour);
    values.put("waterProduced_kgPerHour", waterProducedKgPerHour);
    values.put("reactionHeat_kW", reactionHeatKW);
    values.put("oxidationLimitingFactor", oxidationLimitingFactor);
    values.put("solidSulfurPresent", solidSulfurPresent);
    values.put("kineticsCalibrated", kineticsCalibrated);
    values.put("wallInventory", wallInventory.toMap());
    return values;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(getResults());
  }

  /** @return FeS oxidation rate in kg/hr */
  public double getFeSOxidizedKgPerHour() {
    return feSOxidizedKgPerHour;
  }

  /** @return oxygen consumption in kg/hr */
  public double getOxygenConsumedKgPerHour() {
    return oxygenConsumedKgPerHour;
  }

  /** @return H2S consumption by wall-formation routes in kg/hr */
  public double getH2SConsumedKgPerHour() {
    return h2sConsumedKgPerHour;
  }

  /** @return base elemental sulfur source rate introduced as S8 in kg/hr */
  public double getElementalSulfurRateKgPerHour() {
    return elementalSulfurRateKgPerHour;
  }

  /** @return low elemental sulfur source estimate in kg/hr */
  public double getElementalSulfurLowRateKgPerHour() {
    return elementalSulfurLowRateKgPerHour;
  }

  /** @return high elemental sulfur source estimate in kg/hr */
  public double getElementalSulfurHighRateKgPerHour() {
    return elementalSulfurHighRateKgPerHour;
  }

  /** @return reaction heat release in kW, reported as a positive value */
  public double getReactionHeatKW() {
    return reactionHeatKW;
  }

  /** @return true if S8 solid was present after the outlet flash */
  public boolean isSolidSulfurPresent() {
    return solidSulfurPresent;
  }

  /** @return limiting factor for FeS oxidation */
  public String getOxidationLimitingFactor() {
    return oxidationLimitingFactor;
  }

  /** @param rateMmPerYear bare-steel penetration rate in mm/year */
  public void setBareSteelCorrosionRateMmPerYear(double rateMmPerYear) {
    bareSteelCorrosionRateMmPerYear = requireNonNegative(rateMmPerYear, "bare steel corrosion rate");
  }

  /** @param rateKgPerHour dissolved Fe2+ delivery rate to the wall in kg/hr */
  public void setDissolvedIronRateKgPerHour(double rateKgPerHour) {
    dissolvedIronRateKgPerHour = requireNonNegative(rateKgPerHour, "dissolved iron rate");
  }

  /** @param fractionPerHour first-order FeCO3 sulfidation fraction per hour */
  public void setFeCO3SulfidationFractionPerHour(double fractionPerHour) {
    feCO3SulfidationFractionPerHour = requireNonNegative(fractionPerHour, "FeCO3 sulfidation fraction");
  }

  /** @param fractionPerHour first-order iron-oxide sulfidation fraction per hour */
  public void setIronOxideSulfidationFractionPerHour(double fractionPerHour) {
    ironOxideSulfidationFractionPerHour = requireNonNegative(fractionPerHour, "iron oxide sulfidation fraction");
  }

  /** @param fraction fraction of stream H2S available to wall reactions */
  public void setH2SWallContactFraction(double fraction) {
    h2sWallContactFraction = requireFraction(fraction, "H2S wall contact fraction");
  }

  /** @param required require an aqueous wall film for FeS formation routes */
  public void setRequireWetWallForSulfidation(boolean required) {
    requireWetWallForSulfidation = required;
  }

  /** @param fractionPerHour first-order FeS oxidation fraction per hour */
  public void setFeSOxidationFractionPerHour(double fractionPerHour) {
    feSOxidationFractionPerHour = requireNonNegative(fractionPerHour, "FeS oxidation fraction");
  }

  /**
   * Set oxygen stoichiometry for the selected screening branch. Fe2O3 + S0 uses 0.75 mol O2/mol FeS; Fe3O4 + S0 uses
   * 2/3.
   *
   * @param value oxygen moles consumed per mole FeS
   */
  public void setOxygenMolesPerMoleFeS(double value) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException("oxygen stoichiometry must be finite and positive");
    }
    oxygenMolesPerMoleFeS = value;
  }

  /** @param fraction fraction of inlet oxygen able to reach the wall */
  public void setOxygenTransferFraction(double fraction) {
    oxygenTransferFraction = requireFraction(fraction, "oxygen transfer fraction");
  }

  /**
   * Set a gas-film oxygen mass-transfer coefficient. Set {@code Double.NaN} to disable the coefficient limit and use
   * only the oxygen-transfer fraction.
   *
   * @param coefficientMPerS mass-transfer coefficient in m/s or NaN
   */
  public void setOxygenMassTransferCoefficientMPerS(double coefficientMPerS) {
    if (!Double.isNaN(coefficientMPerS) && (!Double.isFinite(coefficientMPerS) || coefficientMPerS < 0.0)) {
      throw new IllegalArgumentException("oxygen mass-transfer coefficient must be non-negative or NaN");
    }
    oxygenMassTransferCoefficientMPerS = coefficientMPerS;
  }

  /** @param rateKgPerHour upper oxygen-transfer rate in kg/hr */
  public void setMaximumOxygenTransferRateKgPerHour(double rateKgPerHour) {
    if (Double.isNaN(rateKgPerHour) || rateKgPerHour < 0.0) {
      throw new IllegalArgumentException("maximum oxygen transfer rate must be non-negative");
    }
    maximumOxygenTransferRateKgPerHour = rateKgPerHour;
  }

  /** @param fraction base fraction of FeS sulfur reported as elemental sulfur */
  public void setElementalSulfurYieldFraction(double fraction) {
    elementalSulfurYieldFraction = requireFraction(fraction, "elemental sulfur yield");
  }

  /**
   * Set low and high elemental-sulfur selectivity bounds.
   *
   * @param lower lower yield fraction
   * @param upper upper yield fraction
   */
  public void setElementalSulfurYieldBounds(double lower, double upper) {
    lowerElementalSulfurYieldFraction = requireFraction(lower, "lower sulfur yield");
    upperElementalSulfurYieldFraction = requireFraction(upper, "upper sulfur yield");
    if (upper < lower) {
      throw new IllegalArgumentException("upper sulfur yield must not be below lower yield");
    }
  }

  /** @param factor multiplicative kinetic uncertainty factor, at least 1 */
  public void setKineticUncertaintyFactor(double factor) {
    if (!Double.isFinite(factor) || factor < 1.0) {
      throw new IllegalArgumentException("kinetic uncertainty factor must be finite and at least one");
    }
    kineticUncertaintyFactor = factor;
  }

  /** @param calibrated true when rates and yields have been fitted to relevant data */
  public void setKineticsCalibrated(boolean calibrated) {
    kineticsCalibrated = calibrated;
  }

  /** @param heatKJPerMoleFeS positive heat release in kJ per mol FeS oxidized */
  public void setOxidationHeatKJPerMoleFeS(double heatKJPerMoleFeS) {
    oxidationHeatKJPerMoleFeS = requireNonNegative(heatKJPerMoleFeS, "oxidation heat");
  }

  /** @param enabled run a TP-solid flash after S8 is introduced */
  public void setSolidFlashEnabled(boolean enabled) {
    solidFlashEnabled = enabled;
  }

  /** @param hours inventory-depletion horizon used by non-mutating steady-state runs */
  public void setSteadyStateInventoryHorizonHours(double hours) {
    if (!Double.isFinite(hours) || hours <= 0.0) {
      throw new IllegalArgumentException("steady-state inventory horizon must be finite and positive");
    }
    steadyStateInventoryHorizonHours = hours;
  }

  private static double requireNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
    return value;
  }

  private static double requireFraction(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(name + " must be finite and between zero and one");
    }
    return value;
  }
}
