package neqsim.process.processmodel.biorefinery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.reactor.BiomassGasifier;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModule;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.characterization.BiomassCharacterization;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Pre-built biorefinery module for biomass gasification and Fischer-Tropsch synthesis.
 *
 * <p>
 * Composes a {@link BiomassGasifier}, syngas cleanup (tar removal, cooling), and a simplified
 * Fischer-Tropsch (FT) reactor into a complete biomass-to-liquids process. The module takes a
 * biomass feed stream and produces a synthetic liquid product and tail gas.
 * </p>
 *
 * <p>
 * The internal process is:
 * </p>
 * <ol>
 * <li>Biomass gasification to produce syngas (CO, H2, CO2)</li>
 * <li>Syngas cooling and tar/particulate removal</li>
 * <li>Fischer-Tropsch synthesis: CO + 2H2 &rarr; -CH2- + H2O (simplified stoichiometry)</li>
 * <li>Product separation (liquid hydrocarbons + tail gas)</li>
 * </ol>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class GasificationSynthesisModule extends ProcessModule {
  private static final long serialVersionUID = 1005L;
  private static final Logger logger = LogManager.getLogger(GasificationSynthesisModule.class);

  // ── Configuration ──
  /** Gasifier type. */
  private BiomassGasifier.GasifierType gasifierType = BiomassGasifier.GasifierType.FLUIDIZED_BED;
  /** Gasifier temperature in Celsius. */
  private double gasifierTemperatureC = 850.0;
  /** Equivalence ratio for gasifier. */
  private double equivalenceRatio = 0.25;
  /** Steam-to-biomass ratio. */
  private double steamToBiomassRatio = 0.0;
  /** Syngas cooling target temperature in Celsius. */
  private double syngasCoolingTemperatureC = 200.0;
  /** FT reactor temperature in Celsius. */
  private double ftReactorTemperatureC = 230.0;
  /** FT reactor pressure in bara. */
  private double ftReactorPressureBara = 25.0;
  /** CO single-pass conversion in FT reactor (fraction, 0-1). */
  private double ftConversion = 0.40;
  /** Alpha chain-growth probability for FT product distribution. */
  private double ftAlpha = 0.85;

  // ── Internal equipment ──
  private transient BiomassGasifier gasifier;
  private transient BiomassCharacterization biomass;
  private transient StreamInterface ftLiquidStream;
  private transient StreamInterface tailGasStream;

  /** Biomass feed rate in kg/hr (dry basis). */
  private double biomassFeedRateKgPerHr = 1000.0;

  // ── Results ──
  private double syngasFlowKgPerHr = 0.0;
  private double ftLiquidFlowKgPerHr = 0.0;
  private double tailGasFlowKgPerHr = 0.0;
  private double syngasH2COmolRatio = 0.0;
  private boolean hasRun = false;

  /**
   * Creates a gasification-synthesis module with the given name.
   *
   * @param name module name
   */
  public GasificationSynthesisModule(String name) {
    super(name);
  }

  /**
   * Sets the biomass feedstock and feed rate.
   *
   * @param biomassChar the biomass characterization
   * @param feedRateKgPerHr dry biomass feed rate in kg/hr
   */
  public void setBiomass(BiomassCharacterization biomassChar, double feedRateKgPerHr) {
    this.biomass = biomassChar;
    this.biomassFeedRateKgPerHr = feedRateKgPerHr;
  }

  /**
   * Sets the biomass feed rate.
   *
   * @param feedRateKgPerHr dry biomass feed rate in kg/hr
   */
  public void setBiomassFeedRateKgPerHr(double feedRateKgPerHr) {
    this.biomassFeedRateKgPerHr = feedRateKgPerHr;
  }

  /**
   * Sets the gasifier type.
   *
   * @param type gasifier type
   */
  public void setGasifierType(BiomassGasifier.GasifierType type) {
    this.gasifierType = type;
  }

  /**
   * Sets the gasifier temperature.
   *
   * @param temperatureC gasifier temperature in Celsius
   */
  public void setGasifierTemperatureC(double temperatureC) {
    this.gasifierTemperatureC = temperatureC;
  }

  /**
   * Sets the equivalence ratio.
   *
   * @param ratio equivalence ratio (0-1)
   */
  public void setEquivalenceRatio(double ratio) {
    this.equivalenceRatio = ratio;
  }

  /**
   * Sets the steam-to-biomass ratio.
   *
   * @param ratio steam to biomass mass ratio
   */
  public void setSteamToBiomassRatio(double ratio) {
    this.steamToBiomassRatio = ratio;
  }

  /**
   * Sets the FT reactor temperature.
   *
   * @param temperatureC FT reactor temperature in Celsius
   */
  public void setFtReactorTemperatureC(double temperatureC) {
    this.ftReactorTemperatureC = temperatureC;
  }

  /**
   * Sets the FT reactor pressure.
   *
   * @param pressureBara FT reactor pressure in bara
   */
  public void setFtReactorPressureBara(double pressureBara) {
    this.ftReactorPressureBara = pressureBara;
  }

  /**
   * Sets the FT CO conversion.
   *
   * @param conversion CO single-pass conversion (0-1)
   */
  public void setFtConversion(double conversion) {
    this.ftConversion = Math.max(0.0, Math.min(1.0, conversion));
  }

  /**
   * Sets the FT alpha chain-growth probability.
   *
   * @param alpha alpha parameter (0-1)
   */
  public void setFtAlpha(double alpha) {
    this.ftAlpha = Math.max(0.0, Math.min(1.0, alpha));
  }

  /**
   * Returns the FT liquid product stream.
   *
   * @return liquid product stream
   */
  public StreamInterface getFtLiquidStream() {
    return ftLiquidStream;
  }

  /**
   * Returns the tail gas stream.
   *
   * @return tail gas stream
   */
  public StreamInterface getTailGasStream() {
    return tailGasStream;
  }

  /**
   * Returns the syngas H2/CO ratio.
   *
   * @return H2/CO molar ratio
   */
  public double getSyngasH2COmolRatio() {
    return syngasH2COmolRatio;
  }

  /**
   * Returns the FT liquid flow rate.
   *
   * @return FT liquid flow in kg/hr
   */
  public double getFtLiquidFlowKgPerHr() {
    return ftLiquidFlowKgPerHr;
  }

  /**
   * Builds and runs the gasification + FT synthesis process.
   *
   * @param id calculation identifier
   */
  @Override
  public void run(UUID id) {
    logger.info("Running GasificationSynthesisModule: " + getName());

    // Create default biomass if not set
    if (biomass == null) {
      biomass = BiomassCharacterization.library("wood_chips");
    }

    // ── Step 1: Biomass Gasification ──
    gasifier = new BiomassGasifier(getName() + "_gasifier");
    gasifier.setBiomass(biomass, biomassFeedRateKgPerHr);
    gasifier.setGasifierType(gasifierType);
    gasifier.setGasificationTemperature(gasifierTemperatureC + 273.15);
    gasifier.setEquivalenceRatio(equivalenceRatio);
    if (steamToBiomassRatio > 0) {
      gasifier.setSteamToBiomassRatio(steamToBiomassRatio);
    }

    ProcessSystem gasificationSystem = new ProcessSystem();
    gasificationSystem.add(gasifier);
    gasificationSystem.run(id);

    StreamInterface syngasStream = gasifier.getSyngasOutStream();
    try {
      syngasFlowKgPerHr = syngasStream.getFlowRate("kg/hr");
    } catch (Exception e) {
      syngasFlowKgPerHr = 0.0;
    }

    // ── Step 2: Syngas Cooling ──
    Cooler syngasCooler = new Cooler(getName() + "_syngasCooler", syngasStream);
    syngasCooler.setOutTemperature(273.15 + syngasCoolingTemperatureC);

    ProcessSystem coolingSystem = new ProcessSystem();
    coolingSystem.add(syngasCooler);
    coolingSystem.run(id);

    StreamInterface cooledSyngas = syngasCooler.getOutletStream();

    // ── Step 3: Compute H2/CO ratio from syngas ──
    SystemInterface syngasFluid = cooledSyngas.getFluid();
    double h2Moles = 0.0;
    double coMoles = 0.0;
    try {
      h2Moles = syngasFluid.getPhase(0).getComponent("hydrogen").getNumberOfMolesInPhase();
    } catch (Exception e) {
      // no hydrogen
    }
    try {
      coMoles = syngasFluid.getPhase(0).getComponent("CO").getNumberOfMolesInPhase();
    } catch (Exception e) {
      // no CO
    }
    syngasH2COmolRatio = coMoles > 0 ? h2Moles / coMoles : 0.0;

    // ── Step 4: Simplified FT Synthesis ──
    // Model FT as: CO + 2H2 -> -CH2- + H2O
    // Split syngas into 'liquid product' and 'tail gas' based on conversion
    // FT liquid approximated by nC10 (decane), tail gas is unconverted syngas + CO2

    double h2Converted = 0.0;
    double coConverted = 0.0;
    if (coMoles > 0 && h2Moles > 0) {
      coConverted = coMoles * ftConversion;
      double h2Required = coConverted * 2.0;
      if (h2Required > h2Moles) {
        // H2-limited
        h2Converted = h2Moles;
        coConverted = h2Moles / 2.0;
      } else {
        h2Converted = h2Required;
      }
    }

    // Product: n-decane (C10H22) as representative FT wax/liquid
    double molesC10 = coConverted / 10.0; // 10C per decane molecule
    double waterMoles = coConverted; // 1 H2O per CO reacted
    double ftLiquidKg = molesC10 * 142.28 / 1000.0; // MW n-C10 = 142.28 g/mol
    ftLiquidFlowKgPerHr = ftLiquidKg;

    // Build FT liquid output stream
    SystemSrkEos ftLiquidFluid =
        new SystemSrkEos(273.15 + ftReactorTemperatureC, ftReactorPressureBara);
    ftLiquidFluid.addComponent("nC10", molesC10 > 0 ? molesC10 : 1.0e-10);
    ftLiquidFluid.addComponent("water", waterMoles > 0 ? waterMoles * 0.01 : 1.0e-10);
    ftLiquidFluid.setMixingRule("classic");
    ftLiquidFluid.init(0);
    ftLiquidFluid.init(3);
    Stream ftLiquidOut = new Stream(getName() + "_FTliquid", ftLiquidFluid);
    ftLiquidOut.run(id);
    ftLiquidStream = ftLiquidOut;

    // Build tail gas stream (unconverted syngas + CO2)
    double remainH2 = h2Moles - h2Converted;
    double remainCO = coMoles - coConverted;
    double co2Moles = 0.0;
    try {
      co2Moles = syngasFluid.getPhase(0).getComponent("CO2").getNumberOfMolesInPhase();
    } catch (Exception e) {
      // no CO2
    }
    double ch4Moles = 0.0;
    try {
      ch4Moles = syngasFluid.getPhase(0).getComponent("methane").getNumberOfMolesInPhase();
    } catch (Exception e) {
      // no CH4
    }

    SystemSrkEos tailGasFluid =
        new SystemSrkEos(273.15 + ftReactorTemperatureC, ftReactorPressureBara);
    tailGasFluid.addComponent("hydrogen", remainH2 > 0 ? remainH2 : 1.0e-10);
    tailGasFluid.addComponent("CO", remainCO > 0 ? remainCO : 1.0e-10);
    tailGasFluid.addComponent("CO2", co2Moles > 0 ? co2Moles : 1.0e-10);
    tailGasFluid.addComponent("methane", ch4Moles > 0 ? ch4Moles : 1.0e-10);
    tailGasFluid.addComponent("water", waterMoles > 0 ? waterMoles * 0.99 : 1.0e-10);
    tailGasFluid.setMixingRule("classic");
    tailGasFluid.init(0);
    tailGasFluid.init(3);
    Stream tailGasOut = new Stream(getName() + "_tailGas", tailGasFluid);
    tailGasOut.run(id);
    tailGasStream = tailGasOut;
    try {
      tailGasFlowKgPerHr = tailGasStream.getFlowRate("kg/hr");
    } catch (Exception e) {
      tailGasFlowKgPerHr = 0.0;
    }

    hasRun = true;
    setCalculationIdentifier(id);
    logger.info("GasificationSynthesisModule completed: " + getName());
  }

  /**
   * Returns a results map.
   *
   * @return map of result names to values
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("moduleName", getName());
    results.put("processType", "Gasification + Fischer-Tropsch");
    results.put("gasifierType", gasifierType.name());
    results.put("gasifierTemperature_C", gasifierTemperatureC);
    results.put("equivalenceRatio", equivalenceRatio);
    results.put("ftConversion", ftConversion);
    results.put("ftAlpha", ftAlpha);
    results.put("ftReactorTemperature_C", ftReactorTemperatureC);
    results.put("ftReactorPressure_bara", ftReactorPressureBara);
    results.put("syngasFlow_kg_per_hr", syngasFlowKgPerHr);
    results.put("syngasH2COratio", syngasH2COmolRatio);
    results.put("ftLiquidFlow_kg_per_hr", ftLiquidFlowKgPerHr);
    results.put("tailGasFlow_kg_per_hr", tailGasFlowKgPerHr);
    results.put("hasRun", hasRun);
    return results;
  }

  /**
   * Returns a JSON string of results.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getResults());
  }
}
