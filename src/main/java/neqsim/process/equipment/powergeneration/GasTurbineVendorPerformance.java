package neqsim.process.equipment.powergeneration;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.standards.gasquality.Standard_ISO6976;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Vendor-performance gas turbine driver / generator model.
 *
 * <p>
 * Unlike the simplified {@link GasTurbine} thermodynamic simple-cycle model (whose net power and efficiency are only
 * indicative), this class computes fuel consumption, thermal efficiency and CO2 emissions from OEM (vendor) performance
 * data: an ISO base-load power rating, a base lower-heating-value (LHV) thermal efficiency, a linear
 * ambient-temperature power-derating slope and a part-load heat-rate penalty. Given a load demand (shaft or electric)
 * and the site ambient temperature it derates the available power, computes the load fraction and part-load efficiency,
 * and sizes the fuel-gas draw from the connected fuel stream using the ISO 6976 net (inferior) calorific value. CO2 is
 * computed from complete combustion of all fuel carbon (including any CO2 already present in the fuel gas, which is
 * also emitted at the stack).
 * </p>
 *
 * <p>
 * The outlet stream returned by {@link #getOutletStream()} is the fuel gas actually consumed (sized to the computed
 * fuel demand), so a splitter/mixer can account for the fuel draw in a flowsheet fuel-gas balance. It is NOT the
 * flue-gas stream.
 * </p>
 *
 * <p>
 * Standards basis: ISO 3977 (gas turbine procurement and site ratings), ISO 6976 (fuel calorific value). Vendor
 * performance numbers should be taken from the driver OEM datasheet.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class GasTurbineVendorPerformance extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(GasTurbineVendorPerformance.class);

  /** ISO base-load power rating in Watts. */
  private double isoRatedPowerW = 0.0;
  /** Base lower-heating-value thermal efficiency (0-1) at ISO base load. */
  private double baseThermalEfficiency = 0.35;
  /** Design (ISO / rating) ambient temperature in degC. */
  private double designAmbientTemperatureC = 15.0;
  /** Site ambient temperature in degC. */
  private double siteAmbientTemperatureC = 15.0;
  /** Fractional power loss per degC above the design ambient temperature (1/degC). */
  private double powerDerationPerDegC = 0.007;
  /** Part-load heat-rate rise coefficient (dimensionless). */
  private double partLoadHeatRateCoefficient = 0.15;
  /** Load demand (shaft or electric) in Watts. */
  private double loadDemandW = 0.0;

  /** Computed site (derated) rated power in Watts. */
  private double siteRatedPowerW = 0.0;
  /** Computed load fraction (demand / site rating). */
  private double loadFraction = 0.0;
  /** Computed part-load thermal efficiency (0-1). */
  private double thermalEfficiency = 0.0;
  /** Computed fuel heat input in Watts. */
  private double fuelHeatW = 0.0;
  /** Computed fuel mass rate in kg/s. */
  private double fuelMassRate = 0.0;
  /** Computed CO2 mass rate in kg/s. */
  private double co2MassRate = 0.0;
  /** Whether the load demand exceeds the site-derated rating. */
  private boolean overloaded = false;

  /**
   * Constructor for GasTurbineVendorPerformance.
   *
   * @param name a {@link java.lang.String} object
   */
  public GasTurbineVendorPerformance(String name) {
    super(name);
  }

  /**
   * Constructor for GasTurbineVendorPerformance.
   *
   * @param name a {@link java.lang.String} object
   * @param fuelStream the fuel-gas inlet stream
   */
  public GasTurbineVendorPerformance(String name, StreamInterface fuelStream) {
    super(name);
    setInletStream(fuelStream);
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface inletStream) {
    this.inStream = inletStream;
    try {
      this.outStream = inletStream.clone(this.getName() + " fuel consumed");
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * Set the vendor ISO base-load rating and base thermal efficiency.
   *
   * @param isoRatedPower ISO base-load power rating value
   * @param unit power unit ("W", "kW", "MW", "hp")
   * @param baseLhvEfficiency base LHV thermal efficiency at ISO base load (0-1)
   */
  public void setVendorRating(double isoRatedPower, String unit, double baseLhvEfficiency) {
    this.isoRatedPowerW = powerToWatt(isoRatedPower, unit);
    this.baseThermalEfficiency = baseLhvEfficiency;
  }

  /**
   * Set the ambient-temperature power-derating basis.
   *
   * @param designAmbientC design (rating) ambient temperature in degC
   * @param powerLossFractionPerDegC fractional power loss per degC above the design ambient (1/degC)
   */
  public void setAmbientDerating(double designAmbientC, double powerLossFractionPerDegC) {
    this.designAmbientTemperatureC = designAmbientC;
    this.powerDerationPerDegC = powerLossFractionPerDegC;
  }

  /**
   * Set the site ambient temperature.
   *
   * @param ambientC site ambient temperature in degC
   */
  public void setSiteAmbientTemperature(double ambientC) {
    this.siteAmbientTemperatureC = ambientC;
  }

  /**
   * Set the part-load heat-rate rise coefficient. A value of 0 gives constant efficiency; typical aeroderivative values
   * are 0.1-0.2.
   *
   * @param coeff part-load heat-rate rise coefficient (dimensionless, &ge; 0)
   */
  public void setPartLoadHeatRateCoefficient(double coeff) {
    this.partLoadHeatRateCoefficient = coeff;
  }

  /**
   * Set the load demand delivered by the turbine (shaft or electric).
   *
   * @param load load demand value
   * @param unit power unit ("W", "kW", "MW", "hp")
   */
  public void setLoadDemand(double load, String unit) {
    this.loadDemandW = powerToWatt(load, unit);
  }

  /**
   * Convert a power value in a named unit to Watts.
   *
   * @param v power value
   * @param unit power unit ("W", "kW", "MW", "hp")
   * @return power in Watts
   */
  private static double powerToWatt(double v, String unit) {
    if ("kW".equals(unit)) {
      return v * 1.0e3;
    }
    if ("MW".equals(unit)) {
      return v * 1.0e6;
    }
    if ("hp".equals(unit)) {
      return v * 745.7;
    }
    return v;
  }

  /**
   * Convert a power value in Watts to a named unit.
   *
   * @param w power in Watts
   * @param unit power unit ("W", "kW", "MW", "hp")
   * @return power in the requested unit
   */
  private static double wattTo(double w, String unit) {
    if ("kW".equals(unit)) {
      return w / 1.0e3;
    }
    if ("MW".equals(unit)) {
      return w / 1.0e6;
    }
    if ("hp".equals(unit)) {
      return w / 745.7;
    }
    return w;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // 1. Site power derating (linear ISO 3977-style ambient derating).
    double dT = siteAmbientTemperatureC - designAmbientTemperatureC;
    siteRatedPowerW = isoRatedPowerW * Math.max(0.0, 1.0 - powerDerationPerDegC * dT);

    // 2. Load fraction and part-load efficiency (heat rate rises at part load).
    loadFraction = siteRatedPowerW > 0.0 ? loadDemandW / siteRatedPowerW : 0.0;
    overloaded = loadFraction > 1.0;
    double pl = Math.max(0.05, Math.min(1.0, loadFraction));
    thermalEfficiency = baseThermalEfficiency / (1.0 + partLoadHeatRateCoefficient * (1.0 / pl - 1.0));

    // 3. Fuel heat and fuel mass from the ISO 6976 net calorific value of the fuel gas.
    fuelHeatW = thermalEfficiency > 0.0 ? loadDemandW / thermalEfficiency : 0.0;
    double lcvJperKg = fuelLcvJperKg();
    fuelMassRate = lcvJperKg > 0.0 ? fuelHeatW / lcvJperKg : 0.0;

    // 4. CO2 from complete combustion of all fuel carbon.
    co2MassRate = fuelMassRate * stoichiometricCo2PerKg();

    // 5. Outlet stream = fuel gas actually consumed (sized to the fuel demand).
    if (inStream != null) {
      try {
        outStream = inStream.clone(this.getName() + " fuel consumed");
        outStream.setFlowRate(fuelMassRate * 3600.0, "kg/hr");
        outStream.run(id);
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
    setCalculationIdentifier(id);
  }

  /**
   * Compute the fuel-gas net (inferior) calorific value on a mass basis using ISO 6976.
   *
   * @return net calorific value in J/kg, or 0 if no fuel stream is connected
   */
  private double fuelLcvJperKg() {
    if (inStream == null) {
      return 0.0;
    }
    SystemInterface fluid = inStream.getThermoSystem().clone();
    fluid.setPressure(ThermodynamicConstantsInterface.referencePressure);
    fluid.setTemperature(288.15);
    new ThermodynamicOperations(fluid).TPflash();
    Standard_ISO6976 std = new Standard_ISO6976(fluid, 0, 15.55, "mass");
    std.setReferenceState("real");
    std.setReferenceType("mass");
    std.calculate();
    return std.getValue("InferiorCalorificValue", "kJ/kg") * 1000.0;
  }

  /**
   * Compute the stoichiometric CO2 produced per kg of fuel burned (all fuel carbon to CO2).
   *
   * @return kg CO2 per kg fuel, or 0 if no fuel stream is connected
   */
  private double stoichiometricCo2PerKg() {
    if (inStream == null) {
      return 0.0;
    }
    SystemInterface fluid = inStream.getThermoSystem();
    double cMol = 0.0;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      double c = carbonAtoms(fluid.getComponent(i));
      cMol += fluid.getComponent(i).getz() * c;
    }
    double mmKgPerMol = fluid.getMolarMass();
    return mmKgPerMol > 0.0 ? cMol * 0.04401 / mmKgPerMol : 0.0;
  }

  /**
   * Number of carbon atoms in a component, robust to components missing from the element database.
   *
   * <p>
   * First tries the NeqSim element database; if the component is a pseudo/plus fraction or is otherwise not registered,
   * falls back to a name-based lookup for common hydrocarbons and finally to a "Cn" pattern parse. Non-carbon
   * components (water, nitrogen, oxygen) return zero.
   * </p>
   *
   * @param comp the component to inspect
   * @return number of carbon atoms per molecule (0 if none/unknown)
   */
  private static double carbonAtoms(neqsim.thermo.component.ComponentInterface comp) {
    try {
      return comp.getElements().getNumberOfElements("C");
    } catch (Exception ex) {
      // fall through to name-based estimate
    }
    String name = comp.getComponentName() == null ? "" : comp.getComponentName().toLowerCase().trim();
    if (name.contains("water") || name.equals("nitrogen") || name.equals("oxygen") || name.equals("h2s")
        || name.equals("hydrogen sulfide") || name.equals("hydrogen") || name.equals("argon")
        || name.equals("helium")) {
      return 0.0;
    }
    if (name.equals("co2") || name.equals("carbon dioxide") || name.equals("co") || name.equals("carbon monoxide")) {
      return 1.0;
    }
    if (name.equals("methane")) {
      return 1.0;
    }
    if (name.equals("ethane")) {
      return 2.0;
    }
    if (name.equals("propane")) {
      return 3.0;
    }
    if (name.contains("butane")) {
      return 4.0;
    }
    if (name.contains("pentane") || name.equals("22-dim-c3")) {
      return 5.0;
    }
    if (name.contains("hexane") || name.equals("benzene")) {
      return 6.0;
    }
    if (name.contains("heptane") || name.equals("toluene")) {
      return 7.0;
    }
    if (name.contains("octane")) {
      return 8.0;
    }
    if (name.contains("nonane")) {
      return 9.0;
    }
    if (name.contains("decane")) {
      return 10.0;
    }
    // Parse a leading/embedded "C<n>" carbon-number label (pseudo/plus fractions such as "C7", "C20").
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("c\\s*(\\d+)").matcher(name.replace("-", ""));
    if (m.find()) {
      try {
        return Double.parseDouble(m.group(1));
      } catch (NumberFormatException nfe) {
        return 0.0;
      }
    }
    return 0.0;
  }

  /**
   * Get the site (ambient-derated) rated power.
   *
   * @param unit power unit ("W", "kW", "MW", "hp")
   * @return site-derated rated power in the requested unit
   */
  public double getSiteRatedPower(String unit) {
    return wattTo(siteRatedPowerW, unit);
  }

  /**
   * Get the load fraction (demand / site-derated rating).
   *
   * @return load fraction (dimensionless)
   */
  public double getLoadFraction() {
    return loadFraction;
  }

  /**
   * Whether the load demand exceeds the site-derated rating.
   *
   * @return true if overloaded
   */
  public boolean isOverloaded() {
    return overloaded;
  }

  /**
   * Get the part-load thermal efficiency.
   *
   * @return thermal efficiency (0-1)
   */
  public double getThermalEfficiency() {
    return thermalEfficiency;
  }

  /**
   * Get the delivered power (load demand).
   *
   * @param unit power unit ("W", "kW", "MW", "hp")
   * @return delivered power in the requested unit
   */
  public double getPower(String unit) {
    return wattTo(loadDemandW, unit);
  }

  /**
   * Get the fuel heat input.
   *
   * @param unit power unit ("W", "kW", "MW", "hp")
   * @return fuel heat input in the requested unit
   */
  public double getFuelHeat(String unit) {
    return wattTo(fuelHeatW, unit);
  }

  /**
   * Get the fuel-gas consumption.
   *
   * @param unit mass-flow unit ("kg/sec", "kg/hr", "tonne/day")
   * @return fuel consumption in the requested unit
   */
  public double getFuelFlowRate(String unit) {
    if ("kg/hr".equals(unit)) {
      return fuelMassRate * 3600.0;
    }
    if ("tonne/day".equals(unit)) {
      return fuelMassRate * 86.4;
    }
    return fuelMassRate;
  }

  /**
   * Get the CO2 emission rate.
   *
   * @param unit mass-flow unit ("kg/sec", "kg/hr", "tonne/day")
   * @return CO2 emission rate in the requested unit
   */
  public double getCO2EmissionRate(String unit) {
    if ("kg/hr".equals(unit)) {
      return co2MassRate * 3600.0;
    }
    if ("tonne/day".equals(unit)) {
      return co2MassRate * 86.4;
    }
    return co2MassRate;
  }
}
