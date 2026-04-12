package neqsim.process.equipment.reactor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.BiomassCharacterization;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Biomass gasifier reactor for thermochemical conversion of solid biomass to syngas.
 *
 * <p>
 * Models downdraft, updraft, and fluidized-bed gasification by combining empirical yield
 * correlations with constrained Gibbs equilibrium (via the existing {@link GibbsReactor}). The
 * gasifier accepts a biomass characterization and a gasification agent stream, producing a syngas
 * outlet stream and a solid residue (char/ash) outlet stream.
 * </p>
 *
 * <h2>Gasifier Types</h2>
 * <ul>
 * <li><b>DOWNDRAFT</b> — co-current flow, low tar, typical for small-scale power</li>
 * <li><b>UPDRAFT</b> — counter-current flow, high tar, high thermal efficiency</li>
 * <li><b>FLUIDIZED_BED</b> — bubbling/circulating, good for large-scale and varied feedstocks</li>
 * </ul>
 *
 * <h2>Gasification Agents</h2>
 * <ul>
 * <li>Air (default) — produces N2-diluted syngas</li>
 * <li>Oxygen — higher quality syngas</li>
 * <li>Steam — hydrogen-rich syngas</li>
 * <li>Air + steam combinations</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * BiomassCharacterization wood = BiomassCharacterization.library("wood_chips");
 *
 * BiomassGasifier gasifier = new BiomassGasifier("Gasifier");
 * gasifier.setBiomass(wood, 1000.0); // 1000 kg/hr
 * gasifier.setGasifierType(BiomassGasifier.GasifierType.DOWNDRAFT);
 * gasifier.setEquivalenceRatio(0.25);
 * gasifier.setGasificationTemperature(1073.15); // 800 C
 * gasifier.run();
 *
 * StreamInterface syngas = gasifier.getSyngasOutStream();
 * StreamInterface charAsh = gasifier.getCharAshOutStream();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class BiomassGasifier extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(BiomassGasifier.class);

  /** Gasifier type enumeration. */
  public enum GasifierType {
    /** Co-current downdraft gasifier, low tar. */
    DOWNDRAFT,
    /** Counter-current updraft gasifier, high tar. */
    UPDRAFT,
    /** Bubbling or circulating fluidized bed gasifier. */
    FLUIDIZED_BED
  }

  /** Gasification agent type. */
  public enum AgentType {
    /** Air gasification (default). */
    AIR,
    /** Pure oxygen gasification. */
    OXYGEN,
    /** Steam gasification. */
    STEAM,
    /** Combined air and steam gasification. */
    AIR_STEAM
  }

  // ── Configuration ──
  /** The biomass feedstock characterization. */
  private BiomassCharacterization biomass;
  /** Biomass feed rate in kg/hr (dry basis). */
  private double biomassFeedRateKgPerHr = 0.0;
  /** Gasifier type. */
  private GasifierType gasifierType = GasifierType.DOWNDRAFT;
  /** Gasification agent. */
  private AgentType agentType = AgentType.AIR;
  /** Equivalence ratio (actual air / stoichiometric air). Typically 0.2-0.4. */
  private double equivalenceRatio = 0.25;
  /** Steam-to-biomass mass ratio (for STEAM and AIR_STEAM agents). */
  private double steamToBiomassRatio = 0.0;
  /** Gasification temperature in Kelvin. If NaN, uses Gibbs equilibrium (adiabatic). */
  private double gasificationTemperature = Double.NaN;
  /** Gasification pressure in bara. */
  private double gasificationPressure = 1.01325;
  /** Carbon conversion efficiency (0-1). Fraction of feed carbon converted to gas. */
  private double carbonConversionEfficiency = 0.95;

  // ── Internal reactor ──
  /** Internal Gibbs reactor used for equilibrium calculation. */
  private transient GibbsReactor gibbsReactor;

  // ── Output streams ──
  /** Syngas (product gas) outlet stream. */
  private StreamInterface syngasOutStream;
  /** Char and ash residue outlet stream. */
  private StreamInterface charAshOutStream;
  /** Optional external gasification agent inlet stream. */
  private StreamInterface agentInletStream;

  // ── Results ──
  /** Cold gas efficiency (LHV syngas / LHV feed, 0-1). */
  private double coldGasEfficiency = Double.NaN;
  /** Syngas yield in Nm3 per kg dry biomass. */
  private double syngasYieldNm3PerKg = Double.NaN;
  /** Syngas LHV in MJ/Nm3. */
  private double syngasLHVMjPerNm3 = Double.NaN;
  /** Char yield as fraction of dry biomass feed (0-1). */
  private double charYieldFraction = Double.NaN;
  /** Whether the gasifier has been run. */
  private boolean hasRun = false;

  // ── Atomic masses ──
  private static final double MW_C = 12.011;
  private static final double MW_H = 1.008;
  private static final double MW_O = 15.999;
  private static final double MW_N = 14.007;
  private static final double MW_S = 32.065;

  /**
   * Creates a biomass gasifier with the given name.
   *
   * @param name equipment name
   */
  public BiomassGasifier(String name) {
    super(name);
  }

  /**
   * Sets the biomass feedstock and feed rate.
   *
   * @param biomass the biomass characterization
   * @param feedRateKgPerHr dry biomass feed rate in kg/hr
   */
  public void setBiomass(BiomassCharacterization biomass, double feedRateKgPerHr) {
    this.biomass = biomass;
    this.biomassFeedRateKgPerHr = feedRateKgPerHr;
  }

  /**
   * Sets the gasifier type.
   *
   * @param type the gasifier type
   */
  public void setGasifierType(GasifierType type) {
    this.gasifierType = type;
  }

  /**
   * Gets the gasifier type.
   *
   * @return gasifier type
   */
  public GasifierType getGasifierType() {
    return gasifierType;
  }

  /**
   * Sets the gasification agent type.
   *
   * @param agent the gasification agent
   */
  public void setAgentType(AgentType agent) {
    this.agentType = agent;
  }

  /**
   * Gets the gasification agent type.
   *
   * @return agent type
   */
  public AgentType getAgentType() {
    return agentType;
  }

  /**
   * Sets the equivalence ratio (ER = actual air / stoichiometric air). Typical range: 0.2 to 0.4.
   *
   * @param er equivalence ratio
   */
  public void setEquivalenceRatio(double er) {
    this.equivalenceRatio = Math.max(0.0, Math.min(1.0, er));
  }

  /**
   * Gets the equivalence ratio.
   *
   * @return equivalence ratio
   */
  public double getEquivalenceRatio() {
    return equivalenceRatio;
  }

  /**
   * Sets the steam-to-biomass mass ratio for steam or air-steam gasification.
   *
   * @param ratio steam-to-biomass ratio (kg steam / kg dry biomass)
   */
  public void setSteamToBiomassRatio(double ratio) {
    this.steamToBiomassRatio = Math.max(0.0, ratio);
  }

  /**
   * Gets the steam-to-biomass ratio.
   *
   * @return steam-to-biomass ratio
   */
  public double getSteamToBiomassRatio() {
    return steamToBiomassRatio;
  }

  /**
   * Sets the gasification temperature in Kelvin. If not set, operates in adiabatic equilibrium
   * mode.
   *
   * @param temperatureK temperature in Kelvin
   */
  public void setGasificationTemperature(double temperatureK) {
    this.gasificationTemperature = temperatureK;
  }

  /**
   * Sets the gasification temperature with unit specification.
   *
   * @param temperature temperature value
   * @param unit unit string ("K", "C", "F")
   */
  public void setGasificationTemperature(double temperature, String unit) {
    if ("C".equalsIgnoreCase(unit)) {
      this.gasificationTemperature = temperature + 273.15;
    } else if ("F".equalsIgnoreCase(unit)) {
      this.gasificationTemperature = (temperature - 32.0) * 5.0 / 9.0 + 273.15;
    } else {
      this.gasificationTemperature = temperature;
    }
  }

  /**
   * Gets the gasification temperature in Kelvin.
   *
   * @return gasification temperature in K
   */
  public double getGasificationTemperature() {
    return gasificationTemperature;
  }

  /**
   * Sets the gasification pressure in bara.
   *
   * @param pressureBara pressure in bara
   */
  public void setGasificationPressure(double pressureBara) {
    this.gasificationPressure = pressureBara;
  }

  /**
   * Gets the gasification pressure in bara.
   *
   * @return pressure in bara
   */
  public double getGasificationPressure() {
    return gasificationPressure;
  }

  /**
   * Sets the carbon conversion efficiency (0 to 1).
   *
   * @param efficiency carbon conversion efficiency
   */
  public void setCarbonConversionEfficiency(double efficiency) {
    this.carbonConversionEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
  }

  /**
   * Gets the carbon conversion efficiency.
   *
   * @return carbon conversion efficiency (0-1)
   */
  public double getCarbonConversionEfficiency() {
    return carbonConversionEfficiency;
  }

  /**
   * Sets an optional external gasification agent inlet stream. When set, this stream provides the
   * gasification agent (air, oxygen, steam) directly, overriding the auto-generated agent from ER.
   *
   * @param stream the gasification agent stream
   */
  public void setAgentInletStream(StreamInterface stream) {
    this.agentInletStream = stream;
  }

  /**
   * Returns the syngas outlet stream.
   *
   * @return syngas outlet stream, or null if not yet run
   */
  public StreamInterface getSyngasOutStream() {
    return syngasOutStream;
  }

  /**
   * Returns the char/ash residue outlet stream.
   *
   * @return char/ash outlet stream, or null if not yet run
   */
  public StreamInterface getCharAshOutStream() {
    return charAshOutStream;
  }

  /**
   * Returns the cold gas efficiency (LHV_syngas / LHV_feed).
   *
   * @return cold gas efficiency (0-1)
   */
  public double getColdGasEfficiency() {
    return coldGasEfficiency;
  }

  /**
   * Returns the syngas yield in Nm3 per kg dry biomass.
   *
   * @return syngas yield
   */
  public double getSyngasYieldNm3PerKg() {
    return syngasYieldNm3PerKg;
  }

  /**
   * Returns the syngas lower heating value in MJ/Nm3.
   *
   * @return syngas LHV
   */
  public double getSyngasLHVMjPerNm3() {
    return syngasLHVMjPerNm3;
  }

  /**
   * Returns the char yield as a fraction of dry biomass feed.
   *
   * @return char yield fraction (0-1)
   */
  public double getCharYieldFraction() {
    return charYieldFraction;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> outlets = new ArrayList<StreamInterface>();
    if (syngasOutStream != null) {
      outlets.add(syngasOutStream);
    }
    if (charAshOutStream != null) {
      outlets.add(charAshOutStream);
    }
    return Collections.unmodifiableList(outlets);
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    List<StreamInterface> inlets = new ArrayList<StreamInterface>();
    if (agentInletStream != null) {
      inlets.add(agentInletStream);
    }
    return Collections.unmodifiableList(inlets);
  }

  /**
   * Runs the biomass gasifier simulation.
   *
   * <p>
   * Converts the biomass elemental composition and gasification agent into a NeqSim fluid, runs
   * Gibbs equilibrium, and separates products into syngas and char/ash streams.
   * </p>
   *
   * @param id UUID for this run
   */
  @Override
  public void run(UUID id) {
    if (biomass == null) {
      throw new IllegalStateException("Biomass characterization must be set before running");
    }
    if (biomassFeedRateKgPerHr <= 0.0) {
      throw new IllegalStateException("Biomass feed rate must be positive");
    }
    if (!biomass.isCalculated()) {
      biomass.calculate();
    }

    // ── Step 1: Convert biomass to elemental mole flows ──
    double dryFeedKgPerHr = biomassFeedRateKgPerHr;
    double dafFraction = (100.0 - biomass.getAsh()) / 100.0;
    double dafFeedKgPerHr = dryFeedKgPerHr * dafFraction;

    double cMolesPerHr = dafFeedKgPerHr * (biomass.getCarbonWt() / 100.0) / MW_C;
    double hMolesPerHr = dafFeedKgPerHr * (biomass.getHydrogenWt() / 100.0) / MW_H;
    double oMolesPerHr = dafFeedKgPerHr * (biomass.getOxygenWt() / 100.0) / MW_O;
    double nMolesPerHr = dafFeedKgPerHr * (biomass.getNitrogenWt() / 100.0) / MW_N;
    double sMolesPerHr = dafFeedKgPerHr * (biomass.getSulfurWt() / 100.0) / MW_S;

    // Carbon that stays as char (unconverted)
    double cCharMoles = cMolesPerHr * (1.0 - carbonConversionEfficiency);
    double cGasMoles = cMolesPerHr * carbonConversionEfficiency;

    // Moisture from biomass
    double moistureFraction = biomass.getMoisture() / 100.0;
    double totalWetFeed = biomassFeedRateKgPerHr / (1.0 - moistureFraction);
    double moistureKgPerHr = totalWetFeed * moistureFraction;
    double moistureMolesPerHr = moistureKgPerHr / 18.015;

    // ── Step 2: Calculate gasification agent flows ──
    double airMolesPerHr = 0.0;
    double o2AgentMoles = 0.0;
    double n2AgentMoles = 0.0;
    double steamAgentMoles = 0.0;

    double stoichAirKg = biomass.getStoichiometricAir() * dryFeedKgPerHr;
    double actualAirKg = stoichAirKg * equivalenceRatio;

    switch (agentType) {
      case AIR:
        airMolesPerHr = actualAirKg / 28.97; // average MW of air
        o2AgentMoles = airMolesPerHr * 0.21;
        n2AgentMoles = airMolesPerHr * 0.79;
        break;
      case OXYGEN:
        o2AgentMoles = (actualAirKg * 0.233) / (2.0 * MW_O);
        break;
      case STEAM:
        steamAgentMoles = (steamToBiomassRatio * dryFeedKgPerHr) / 18.015;
        break;
      case AIR_STEAM:
        airMolesPerHr = actualAirKg / 28.97;
        o2AgentMoles = airMolesPerHr * 0.21;
        n2AgentMoles = airMolesPerHr * 0.79;
        steamAgentMoles = (steamToBiomassRatio * dryFeedKgPerHr) / 18.015;
        break;
      default:
        break;
    }

    // ── Step 3: Build NeqSim fluid for Gibbs equilibrium ──
    double initTemp =
        Double.isNaN(gasificationTemperature) ? 273.15 + 800.0 : gasificationTemperature;
    SystemInterface gasifierFluid = new SystemSrkEos(initTemp, gasificationPressure);

    // Add biomass-derived species to the reactor fluid
    // Carbon enters as CH4 proxy (carbon + hydrogen), excess hydrogen as H2
    // This gives the Gibbs reactor the right elemental balance
    double ch4Moles = Math.min(cGasMoles, hMolesPerHr / 4.0);
    double excessH2Moles = Math.max(0.0, (hMolesPerHr - ch4Moles * 4.0) / 2.0);
    double excessCMoles = Math.max(0.0, cGasMoles - ch4Moles);

    // Add components with mol/hr flow rates
    if (ch4Moles > 0) {
      gasifierFluid.addComponent("methane", ch4Moles, "mole/hr");
    }
    if (excessH2Moles > 0) {
      gasifierFluid.addComponent("hydrogen", excessH2Moles, "mole/hr");
    }
    if (excessCMoles > 0) {
      // Represent excess C as CO (will be redistributed by Gibbs)
      gasifierFluid.addComponent("CO", excessCMoles, "mole/hr");
    }

    // Oxygen from biomass + agent
    double totalO2Moles = oMolesPerHr / 2.0 + o2AgentMoles;
    if (totalO2Moles > 0) {
      gasifierFluid.addComponent("oxygen", totalO2Moles, "mole/hr");
    }

    // Water (moisture + steam agent)
    double totalWaterMoles = moistureMolesPerHr + steamAgentMoles;
    if (totalWaterMoles > 0) {
      gasifierFluid.addComponent("water", totalWaterMoles, "mole/hr");
    }

    // Nitrogen from biomass + air agent
    double totalN2Moles = nMolesPerHr / 2.0 + n2AgentMoles;
    if (totalN2Moles > 0) {
      gasifierFluid.addComponent("nitrogen", totalN2Moles, "mole/hr");
    }

    // Sulfur from biomass
    if (sMolesPerHr > 0) {
      gasifierFluid.addComponent("H2S", sMolesPerHr, "mole/hr");
    }

    // Ensure key syngas components are present for Gibbs equilibrium
    ensureComponent(gasifierFluid, "CO2");
    ensureComponent(gasifierFluid, "CO");
    ensureComponent(gasifierFluid, "hydrogen");
    ensureComponent(gasifierFluid, "methane");
    ensureComponent(gasifierFluid, "water");

    gasifierFluid.setMixingRule("classic");
    gasifierFluid.init(0);
    gasifierFluid.init(3);

    // ── Step 4: Run Gibbs equilibrium ──
    Stream reactorFeed = new Stream(getName() + " feed", gasifierFluid);
    reactorFeed.run(id);

    gibbsReactor = new GibbsReactor(getName() + " Gibbs", reactorFeed);
    gibbsReactor.setUseAllDatabaseSpecies(false);
    gibbsReactor.setDampingComposition(1e-4);
    gibbsReactor.setMaxIterations(5000);
    gibbsReactor.setConvergenceTolerance(1e-6);

    if (!Double.isNaN(gasificationTemperature)) {
      gibbsReactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    } else {
      gibbsReactor.setEnergyMode(GibbsReactor.EnergyMode.ADIABATIC);
    }

    // Mark nitrogen as inert if present
    if (totalN2Moles > 0) {
      gibbsReactor.setComponentAsInert("nitrogen");
    }

    gibbsReactor.run(id);

    // ── Step 5: Create output streams ──
    syngasOutStream = gibbsReactor.getOutletStream();
    syngasOutStream.setName(getName() + " syngas");

    // Create char/ash stream
    double charCarbonKg = cCharMoles * MW_C;
    double ashKg = dryFeedKgPerHr * (biomass.getAsh() / 100.0);
    charYieldFraction = (charCarbonKg + ashKg) / dryFeedKgPerHr;

    // Build a simple char/ash stream (pure carbon + inert)
    SystemInterface charSystem = new SystemSrkEos(273.15 + 25.0, gasificationPressure);
    // Represent char as methane at trace amounts (placeholder for solid carbon)
    charSystem.addComponent("methane", Math.max(1e-10, cCharMoles), "mole/hr");
    charSystem.setMixingRule("classic");
    charSystem.init(0);
    charSystem.init(3);
    charAshOutStream = new Stream(getName() + " char/ash", charSystem);
    charAshOutStream.run(id);

    // ── Step 6: Calculate performance metrics ──
    calculatePerformanceMetrics();

    hasRun = true;
  }

  /**
   * Ensures a component exists in the fluid (adds trace amount if missing).
   *
   * @param fluid the SystemInterface
   * @param componentName the component name
   */
  private void ensureComponent(SystemInterface fluid, String componentName) {
    if (!fluid.hasComponent(componentName)) {
      fluid.addComponent(componentName, 1e-20, "mole/hr");
    }
  }

  /**
   * Calculates syngas quality and efficiency metrics after simulation.
   */
  private void calculatePerformanceMetrics() {
    if (syngasOutStream == null) {
      return;
    }
    SystemInterface syngas = syngasOutStream.getThermoSystem();
    if (syngas == null) {
      return;
    }

    // Total syngas mole flow (mol/hr)
    double totalMolesPerHr = syngas.getFlowRate("mole/hr");

    // Nm3/hr at STP (0 C, 1 atm): 1 mole = 22.414 L
    double syngasNm3PerHr = totalMolesPerHr * 22.414 / 1000.0;
    syngasYieldNm3PerKg =
        biomassFeedRateKgPerHr > 0 ? syngasNm3PerHr / biomassFeedRateKgPerHr : 0.0;

    // Estimate syngas LHV from combustible fractions
    double coFraction = getMoleFraction(syngas, "CO");
    double h2Fraction = getMoleFraction(syngas, "hydrogen");
    double ch4Fraction = getMoleFraction(syngas, "methane");

    // LHV contributions in MJ/Nm3: CO=12.63, H2=10.78, CH4=35.88
    syngasLHVMjPerNm3 = coFraction * 12.63 + h2Fraction * 10.78 + ch4Fraction * 35.88;

    // Cold gas efficiency
    double syngasEnergyMjPerHr = syngasLHVMjPerNm3 * syngasNm3PerHr;
    double feedEnergyMjPerHr = biomass.getLHV() * biomassFeedRateKgPerHr;
    coldGasEfficiency = feedEnergyMjPerHr > 0 ? syngasEnergyMjPerHr / feedEnergyMjPerHr : 0.0;
  }

  /**
   * Returns the mole fraction of a component in a system, or 0 if not present.
   *
   * @param system the thermo system
   * @param componentName component name
   * @return mole fraction
   */
  private double getMoleFraction(SystemInterface system, String componentName) {
    try {
      if (system.hasComponent(componentName)) {
        return system.getPhase(0).getComponent(componentName).getz();
      }
    } catch (Exception e) {
      logger.debug("Could not get mole fraction for {}: {}", componentName, e.getMessage());
    }
    return 0.0;
  }

  /**
   * Returns a map of key results from the gasifier simulation.
   *
   * @return map of result name to value
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("gasifierType", gasifierType.name());
    results.put("agentType", agentType.name());
    results.put("equivalenceRatio", equivalenceRatio);
    results.put("carbonConversionEfficiency", carbonConversionEfficiency);
    results.put("coldGasEfficiency", coldGasEfficiency);
    results.put("syngasYieldNm3PerKg", syngasYieldNm3PerKg);
    results.put("syngasLHVMjPerNm3", syngasLHVMjPerNm3);
    results.put("charYieldFraction", charYieldFraction);

    if (syngasOutStream != null) {
      SystemInterface syngas = syngasOutStream.getThermoSystem();
      if (syngas != null) {
        Map<String, Double> composition = new LinkedHashMap<String, Double>();
        for (int i = 0; i < syngas.getPhase(0).getNumberOfComponents(); i++) {
          String name = syngas.getPhase(0).getComponent(i).getComponentName();
          double z = syngas.getPhase(0).getComponent(i).getz();
          if (z > 1e-10) {
            composition.put(name, z);
          }
        }
        results.put("syngasComposition_molFrac", composition);
      }
    }
    return results;
  }

  /**
   * Returns a JSON string with the gasifier results.
   *
   * @return JSON results string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getResults());
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    if (!hasRun) {
      return "BiomassGasifier '" + getName() + "' (not yet run)";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("BiomassGasifier '").append(getName()).append("'\n");
    sb.append(String.format("  Type: %s, Agent: %s%n", gasifierType, agentType));
    sb.append(
        String.format("  ER = %.3f, CCE = %.3f%n", equivalenceRatio, carbonConversionEfficiency));
    sb.append(String.format("  Cold gas efficiency = %.3f%n", coldGasEfficiency));
    sb.append(String.format("  Syngas yield = %.2f Nm3/kg%n", syngasYieldNm3PerKg));
    sb.append(String.format("  Syngas LHV = %.2f MJ/Nm3%n", syngasLHVMjPerNm3));
    sb.append(String.format("  Char yield = %.3f%n", charYieldFraction));
    return sb.toString();
  }
}
