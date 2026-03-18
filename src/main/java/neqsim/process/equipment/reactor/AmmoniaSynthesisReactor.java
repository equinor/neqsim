package neqsim.process.equipment.reactor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Ammonia synthesis reactor modeling the Haber-Bosch process.
 *
 * <p>
 * Simulates the catalytic synthesis of ammonia from nitrogen and hydrogen:
 * </p>
 *
 * <pre>
 * N2 + 3 H2 &lt;-&gt; 2 NH3    (delta H = -92.4 kJ/mol at 25 C)
 * </pre>
 *
 * <p>
 * The reactor models the equilibrium and kinetic-limited conversion of the Haber-Bosch process
 * operating at 150-300 bar and 400-500 C over an iron or ruthenium catalyst.
 * </p>
 *
 * <h2>Features</h2>
 * <ul>
 * <li>Equilibrium conversion calculation using temperature and pressure</li>
 * <li>Per-pass conversion factor (typically 10-20% due to kinetic limitations)</li>
 * <li>Adiabatic or isothermal operation modes</li>
 * <li>Heat of reaction tracking</li>
 * <li>Excess hydrogen or nitrogen recycle support</li>
 * </ul>
 *
 * <h2>Typical Operating Conditions</h2>
 *
 * <table>
 * <caption>Typical Haber-Bosch operating conditions</caption>
 * <tr><th>Parameter</th><th>Range</th></tr>
 * <tr><td>Pressure</td><td>150-300 bar</td></tr>
 * <tr><td>Temperature</td><td>400-500 C</td></tr>
 * <tr><td>H2/N2 ratio</td><td>3:1 (stoichiometric)</td></tr>
 * <tr><td>Per-pass conversion</td><td>10-20%</td></tr>
 * <tr><td>Overall conversion (with recycle)</td><td>95-98%</td></tr>
 * <tr><td>Catalyst</td><td>Magnetite (Fe3O4) or Ruthenium</td></tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create synthesis gas feed (3:1 H2:N2)
 * SystemInterface synthGas = new SystemSrkEos(273.15 + 450.0, 200.0);
 * synthGas.addComponent("hydrogen", 0.75);
 * synthGas.addComponent("nitrogen", 0.25);
 * synthGas.setMixingRule("classic");
 *
 * Stream feed = new Stream("Synth Gas Feed", synthGas);
 * feed.setFlowRate(1000.0, "kg/hr");
 * feed.run();
 *
 * AmmoniaSynthesisReactor reactor = new AmmoniaSynthesisReactor("HB Reactor", feed);
 * reactor.setPerPassConversion(0.15);
 * reactor.run();
 *
 * double nh3Production = reactor.getAmmoniaProductionRate("kg/hr");
 * }</pre>
 *
 * @author esol
 * @version 1.0
 */
public class AmmoniaSynthesisReactor extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(AmmoniaSynthesisReactor.class);

  /** Heat of reaction for N2 + 3H2 -&gt; 2NH3 [J/mol N2 reacted]. */
  private static final double HEAT_OF_REACTION = -92400.0;

  /** Molar mass of NH3 [kg/mol]. */
  private static final double MW_NH3 = 0.01703;

  /** Molar mass of H2 [kg/mol]. */
  private static final double MW_H2 = 0.002016;

  /** Molar mass of N2 [kg/mol]. */
  private static final double MW_N2 = 0.02802;

  /** Per-pass conversion of nitrogen [0-1]. Typical 0.10 to 0.20. */
  private double perPassConversion = 0.15;

  /** Whether to use equilibrium conversion (true) or fixed per-pass (false). */
  private boolean useEquilibriumConversion = false;

  /** Isothermal mode (true) or adiabatic (false). */
  private boolean isothermal = false;

  /** Reactor outlet temperature [K] (set after run). */
  private double outletTemperature = 0.0;

  /** Heat duty [W] (negative = exothermic heat released). */
  private double heatDuty = 0.0;

  /** Ammonia production rate [mol/s]. */
  private double ammoniaProductionMolPerSec = 0.0;

  /** N2 converted [mol/s]. */
  private double nitrogenConvertedMolPerSec = 0.0;

  /**
   * Constructor for AmmoniaSynthesisReactor.
   *
   * @param name name of the reactor
   */
  public AmmoniaSynthesisReactor(String name) {
    super(name);
  }

  /**
   * Constructor for AmmoniaSynthesisReactor with inlet stream.
   *
   * @param name name of the reactor
   * @param inletStream the synthesis gas feed stream
   */
  public AmmoniaSynthesisReactor(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface outSystem = inStream.getThermoSystem().clone();

    // Ensure ammonia component exists in the system before reaction
    if (!outSystem.hasComponent("ammonia")) {
      outSystem.addComponent("ammonia", 1.0e-20);
      outSystem.createDatabase(true);
      outSystem.init(0);
    }

    double n2Moles = 0.0;
    double h2Moles = 0.0;

    if (outSystem.hasComponent("nitrogen")) {
      n2Moles = outSystem.getComponent("nitrogen").getNumberOfmoles();
    }
    if (outSystem.hasComponent("hydrogen")) {
      h2Moles = outSystem.getComponent("hydrogen").getNumberOfmoles();
    }

    if (n2Moles <= 0.0 || h2Moles <= 0.0) {
      logger.warn("Insufficient reactants: N2={} mol, H2={} mol", n2Moles, h2Moles);
      outStream.setThermoSystem(outSystem);
      outStream.run(id);
      setCalculationIdentifier(id);
      return;
    }

    // Determine conversion
    double conversion = perPassConversion;
    if (useEquilibriumConversion) {
      double tempK = outSystem.getTemperature();
      double pressureBar = outSystem.getPressure();
      conversion = calculateEquilibriumConversion(tempK, pressureBar, n2Moles, h2Moles);
    }

    // Stoichiometry: N2 + 3H2 -> 2NH3
    // Limit by both N2 and H2 availability
    double n2Reacted = n2Moles * conversion;
    double h2Required = n2Reacted * 3.0;
    if (h2Required > h2Moles * 0.99) {
      // H2 limited
      n2Reacted = h2Moles * 0.99 / 3.0;
      h2Required = n2Reacted * 3.0;
    }

    double nh3Produced = n2Reacted * 2.0;

    // Update compositions
    outSystem.addComponent("nitrogen", -n2Reacted);
    outSystem.addComponent("hydrogen", -h2Required);
    outSystem.addComponent("ammonia", nh3Produced);

    // Calculate heat of reaction
    heatDuty = HEAT_OF_REACTION * n2Reacted; // Negative = exothermic

    if (isothermal) {
      // Keep temperature, remove heat
      ThermodynamicOperations ops = new ThermodynamicOperations(outSystem);
      try {
        ops.TPflash();
      } catch (Exception ex) {
        logger.error("Isothermal TP flash failed: {}", ex.getMessage());
      }
      outSystem.init(3);
    } else {
      // Adiabatic: add heat to system
      ThermodynamicOperations ops = new ThermodynamicOperations(outSystem);
      double inletEnthalpy = inStream.getThermoSystem().getEnthalpy();
      double newEnthalpy = inletEnthalpy - heatDuty; // Subtract negative = add
      try {
        ops.PHflash(newEnthalpy);
      } catch (Exception ex) {
        logger.error("Adiabatic PH flash failed: {}", ex.getMessage());
        outSystem.init(3);
      }
    }

    // Store results
    nitrogenConvertedMolPerSec = n2Reacted;
    ammoniaProductionMolPerSec = nh3Produced;
    outletTemperature = outSystem.getTemperature();

    outStream.setThermoSystem(outSystem);
    outStream.run(id);

    getEnergyStream().setDuty(heatDuty);
    setCalculationIdentifier(id);
  }

  /**
   * Calculate equilibrium conversion using simplified Kp correlation.
   *
   * <p>
   * Uses the Gillespie-Beattie correlation for the equilibrium constant:
   * log10(Kp) = -2.691122 * log10(T) - 5.519265e-5 * T + 1.848863e-7 * T^2 + 2001.6 / T + 2.6899
   * </p>
   *
   * @param tempK temperature [K]
   * @param pressureBar pressure [bar]
   * @param n2Moles moles of N2
   * @param h2Moles moles of H2
   * @return equilibrium conversion of N2 [0-1]
   */
  private double calculateEquilibriumConversion(double tempK, double pressureBar,
      double n2Moles, double h2Moles) {
    // Simplified Kp using Gillespie-Beattie
    double logKp = -2.691122 * Math.log10(tempK) - 5.519265e-5 * tempK
        + 1.848863e-7 * tempK * tempK + 2001.6 / tempK + 2.6899;
    double kp = Math.pow(10.0, logKp);

    // Pressure effect (higher pressure favors products)
    // Kp in terms of pressure: Kp * P^2 (since delta_n = -2)
    double kpAdjusted = kp * pressureBar * pressureBar;

    // Simplified equilibrium conversion estimate
    // For 3:1 H2:N2, alpha ~ sqrt(Kp * P^2) / (1 + sqrt(Kp * P^2))
    double sqrtKpP2 = Math.sqrt(Math.max(0.0, kpAdjusted));
    double alphaEq = sqrtKpP2 / (1.0 + sqrtKpP2);

    // Limit to reasonable range
    return Math.min(0.50, Math.max(0.01, alphaEq));
  }

  /**
   * Get ammonia production rate.
   *
   * @param unit "mol/s", "kg/hr", "tonne/day"
   * @return production rate in specified unit
   */
  public double getAmmoniaProductionRate(String unit) {
    if ("kg/hr".equals(unit)) {
      return ammoniaProductionMolPerSec * MW_NH3 * 3600.0;
    } else if ("tonne/day".equals(unit)) {
      return ammoniaProductionMolPerSec * MW_NH3 * 3600.0 * 24.0 / 1000.0;
    }
    return ammoniaProductionMolPerSec;
  }

  /**
   * Get heat duty [W].
   *
   * @return heat duty (negative = exothermic)
   */
  public double getHeatDuty() {
    return heatDuty;
  }

  /**
   * Get heat duty in specified unit.
   *
   * @param unit "W", "kW", "MW"
   * @return heat duty in unit
   */
  public double getHeatDuty(String unit) {
    if ("kW".equals(unit)) {
      return heatDuty / 1.0e3;
    } else if ("MW".equals(unit)) {
      return heatDuty / 1.0e6;
    }
    return heatDuty;
  }

  /**
   * Get outlet temperature [K].
   *
   * @return outlet temperature [K]
   */
  public double getOutletTemperature() {
    return outletTemperature;
  }

  /**
   * Get N2 conversion achieved.
   *
   * @return conversion [0-1]
   */
  public double getConversion() {
    double n2In = 0.0;
    if (inStream.getThermoSystem().hasComponent("nitrogen")) {
      n2In = inStream.getThermoSystem().getComponent("nitrogen").getNumberOfmoles();
    }
    if (n2In <= 0.0) {
      return 0.0;
    }
    return nitrogenConvertedMolPerSec / n2In;
  }

  /**
   * Set per-pass conversion of nitrogen [0-1].
   *
   * @param conversion per-pass conversion (typical 0.10-0.20)
   */
  public void setPerPassConversion(double conversion) {
    this.perPassConversion = conversion;
  }

  /**
   * Get per-pass conversion.
   *
   * @return per-pass conversion [0-1]
   */
  public double getPerPassConversion() {
    return perPassConversion;
  }

  /**
   * Set whether to use equilibrium conversion calculation.
   *
   * @param useEquilibrium true to use equilibrium, false for fixed per-pass
   */
  public void setUseEquilibriumConversion(boolean useEquilibrium) {
    this.useEquilibriumConversion = useEquilibrium;
  }

  /**
   * Set isothermal operation mode.
   *
   * @param isothermal true for isothermal, false for adiabatic
   */
  public void setIsothermal(boolean isothermal) {
    this.isothermal = isothermal;
  }

  /**
   * Check if reactor is in isothermal mode.
   *
   * @return true if isothermal
   */
  public boolean isIsothermal() {
    return isothermal;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    List<StreamInterface> streams = new ArrayList<StreamInterface>();
    if (inStream != null) {
      streams.add(inStream);
    }
    return streams;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> streams = new ArrayList<StreamInterface>();
    if (outStream != null) {
      streams.add(outStream);
    }
    return streams;
  }
}
