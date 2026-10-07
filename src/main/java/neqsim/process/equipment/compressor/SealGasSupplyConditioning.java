package neqsim.process.equipment.compressor;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Supply-side conditioning check for dry gas seal gas (API 692 style): is the seal gas still superheated above its
 * hydrocarbon and water dew points after the isenthalpic pressure reduction across the seal gas supply valve or
 * orifice, and what supply temperature is needed to keep the required margin.
 *
 * <p>
 * Complements {@link DryGasSealAnalyzer}, which models condensation on the leakage side (primary vent). This class
 * models the supply side: the seal gas is taken at a source pressure and temperature (impeller take-off, discharge or
 * an external conditioned supply), optionally heated, and expanded to the buffer (supply) pressure at the seal. The
 * Joule-Thomson cooling of a dense gas can remove 15-25 K, so a take-off at an impeller exit or a supply that is only
 * slightly above the scrubber temperature can condense water or heavy hydrocarbons inside the seal.
 * </p>
 *
 * <p>
 * Also provides two small helpers for evidence from filter sumps: the liquid load per million standard cubic metres
 * implied by a sump filling rate, and the corresponding filter life.
 * </p>
 *
 * @author neqsim
 * @version 1.0
 * @see DryGasSealAnalyzer
 */
public class SealGasSupplyConditioning {

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(SealGasSupplyConditioning.class);

  /** Molar volume of an ideal gas at 15 C and 1.01325 bara in Sm3 per mol. */
  private static final double SM3_PER_MOL = 0.0236443;

  /** Standard atmosphere in bar, used for barg conversion. */
  private static final double ATM_BAR = 1.01325;

  private final String name;
  private SystemInterface sealGas;
  private double sourcePressureBara = 90.0;
  private double sourceTemperatureK = 273.15 + 45.0;
  private double supplyPressureBara = 50.0;
  private double heaterTemperatureK = Double.NaN;
  private double requiredMarginK = 20.0;

  private boolean analysed = false;
  private double supplyTemperatureK = Double.NaN;
  private double liquidMassFraction = 0.0;
  private double hydrocarbonDewPointC = Double.NaN;
  private double waterDewPointC = Double.NaN;

  /**
   * Creates the check.
   *
   * @param name tag of the compressor seal gas system
   */
  public SealGasSupplyConditioning(String name) {
    this.name = name;
  }

  /**
   * Sets the seal gas composition (cloned for every calculation). Water, if present, is used for the water dew point.
   *
   * @param sealGas fluid with composition and mixing rule set
   */
  public void setSealGas(SystemInterface sealGas) {
    this.sealGas = sealGas;
    analysed = false;
  }

  /**
   * Sets the source of the seal gas.
   *
   * @param pressure source pressure
   * @param pressureUnit "bara" or "barg"
   * @param temperature source temperature
   * @param temperatureUnit "C" or "K"
   */
  public void setSource(double pressure, String pressureUnit, double temperature, String temperatureUnit) {
    this.sourcePressureBara = toBara(pressure, pressureUnit);
    this.sourceTemperatureK = toKelvin(temperature, temperatureUnit);
    analysed = false;
  }

  /**
   * Sets the pressure after the supply valve or orifice (buffer gas pressure at the seal).
   *
   * @param pressure supply pressure
   * @param pressureUnit "bara" or "barg"
   */
  public void setSupplyPressure(double pressure, String pressureUnit) {
    this.supplyPressureBara = toBara(pressure, pressureUnit);
    analysed = false;
  }

  /**
   * Sets a heater between the source and the supply valve. The gas is heated to this temperature when it is colder.
   *
   * @param temperature heater outlet temperature
   * @param temperatureUnit "C" or "K"
   */
  public void setHeaterOutletTemperature(double temperature, String temperatureUnit) {
    this.heaterTemperatureK = toKelvin(temperature, temperatureUnit);
    analysed = false;
  }

  /** Removes the heater, so the gas enters the supply valve at the source temperature. */
  public void clearHeater() {
    this.heaterTemperatureK = Double.NaN;
    analysed = false;
  }

  /**
   * Sets the required superheat above the dew point (API 692 practice: 20 K).
   *
   * @param marginK required margin in K
   */
  public void setRequiredSuperheatK(double marginK) {
    this.requiredMarginK = marginK;
    analysed = false;
  }

  private static double toBara(double pressure, String unit) {
    return "barg".equalsIgnoreCase(unit) ? pressure + ATM_BAR : pressure;
  }

  private static double toKelvin(double temperature, String unit) {
    return "K".equalsIgnoreCase(unit) ? temperature : temperature + 273.15;
  }

  /** Runs the isenthalpic expansion and the dew point calculations. */
  public void run() {
    if (sealGas == null) {
      throw new IllegalStateException("Seal gas fluid is not set for " + name);
    }
    double inletK = Double.isNaN(heaterTemperatureK) ? sourceTemperatureK
        : Math.max(sourceTemperatureK, heaterTemperatureK);
    double[] expanded = expand(inletK);
    supplyTemperatureK = expanded[0];
    liquidMassFraction = expanded[1];
    hydrocarbonDewPointC = dewPointC(false);
    waterDewPointC = sealGas.hasComponent("water") ? dewPointC(true) : Double.NaN;
    analysed = true;
  }

  /**
   * Isenthalpic expansion from the source pressure to the supply pressure.
   *
   * @param inletK gas temperature entering the supply valve in K
   * @return array with outlet temperature in K and liquid mass fraction
   */
  private double[] expand(double inletK) {
    SystemInterface f = sealGas.clone();
    f.setTemperature(inletK);
    f.setPressure(sourcePressureBara);
    f.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(f);
    ops.TPflash();
    double enthalpy = f.getEnthalpy("J/mol");
    f.setPressure(supplyPressureBara);
    ops.PHflash(enthalpy, "J/mol");
    f.initProperties();
    double liquid = 0.0;
    if (f.getNumberOfPhases() > 1) {
      double total = 0.0;
      for (int i = 0; i < f.getNumberOfPhases(); i++) {
        double mass = f.getPhase(i).getFlowRate("kg/hr");
        total += mass;
        if (!"gas".equals(f.getPhase(i).getType().getDesc())) {
          liquid += mass;
        }
      }
      liquid = total > 0.0 ? liquid / total : 0.0;
    }
    return new double[] {f.getTemperature(), liquid};
  }

  /**
   * Dew point temperature at the supply pressure by bisection on a TP flash.
   *
   * @param aqueous true for the water dew point, false for the hydrocarbon dew point (water removed)
   * @return dew point in C; NaN if no liquid forms above -30 C
   */
  private double dewPointC(boolean aqueous) {
    double lo = -30.0;
    double hi = 150.0;
    if (!liquidPresent(lo + 273.15, aqueous)) {
      return Double.NaN;
    }
    if (liquidPresent(hi + 273.15, aqueous)) {
      return hi;
    }
    for (int i = 0; i < 30; i++) {
      double mid = 0.5 * (lo + hi);
      if (liquidPresent(mid + 273.15, aqueous)) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return 0.5 * (lo + hi);
  }

  private boolean liquidPresent(double temperatureK, boolean aqueous) {
    SystemInterface f = sealGas.clone();
    if (!aqueous && f.hasComponent("water")) {
      f.removeComponent("water");
    }
    f.setTemperature(temperatureK);
    f.setPressure(supplyPressureBara);
    f.setMultiPhaseCheck(true);
    try {
      new ThermodynamicOperations(f).TPflash();
    } catch (Exception ex) {
      logger.warn("TP flash failed in {} at {} K: {}", name, temperatureK, ex.getMessage());
      return false;
    }
    return aqueous ? f.hasPhaseType("aqueous") : f.getNumberOfPhases() > 1;
  }

  private void ensureRun() {
    if (!analysed) {
      run();
    }
  }

  /**
   * Gas temperature after the supply valve.
   *
   * @return temperature in C
   */
  public double getSupplyTemperatureC() {
    ensureRun();
    return supplyTemperatureK - 273.15;
  }

  /**
   * Liquid mass fraction formed in the expansion itself.
   *
   * @return liquid mass fraction (0 for dry gas)
   */
  public double getLiquidMassFraction() {
    ensureRun();
    return liquidMassFraction;
  }

  /**
   * Hydrocarbon dew point at the supply pressure.
   *
   * @return dew point in C, NaN when none
   */
  public double getHydrocarbonDewPointC() {
    ensureRun();
    return hydrocarbonDewPointC;
  }

  /**
   * Water dew point at the supply pressure.
   *
   * @return dew point in C, NaN when the gas holds no water
   */
  public double getWaterDewPointC() {
    ensureRun();
    return waterDewPointC;
  }

  /**
   * Smallest margin between the supply temperature and the hydrocarbon and water dew points.
   *
   * @return margin in K; positive infinity when no dew point exists
   */
  public double getMinimumMarginK() {
    ensureRun();
    double margin = Double.POSITIVE_INFINITY;
    double t = supplyTemperatureK - 273.15;
    if (!Double.isNaN(hydrocarbonDewPointC)) {
      margin = Math.min(margin, t - hydrocarbonDewPointC);
    }
    if (!Double.isNaN(waterDewPointC)) {
      margin = Math.min(margin, t - waterDewPointC);
    }
    return margin;
  }

  /**
   * Whether the required superheat is kept and no liquid forms in the expansion.
   *
   * @return true when the margin is met
   */
  public boolean isMarginMet() {
    ensureRun();
    return liquidMassFraction <= 0.0 && getMinimumMarginK() >= requiredMarginK;
  }

  /**
   * Heater outlet (supply valve inlet) temperature that restores the required margin, found by bisection.
   *
   * @param maxTemperatureC upper search limit in C
   * @return temperature in C, the source temperature when no heating is needed, NaN if not reachable
   */
  public double calculateRequiredInletTemperatureC(double maxTemperatureC) {
    double saved = heaterTemperatureK;
    try {
      clearHeater();
      run();
      if (isMarginMet()) {
        return sourceTemperatureK - 273.15;
      }
      double lo = sourceTemperatureK - 273.15;
      double hi = maxTemperatureC;
      setHeaterOutletTemperature(hi, "C");
      run();
      if (!isMarginMet()) {
        return Double.NaN;
      }
      for (int i = 0; i < 25; i++) {
        double mid = 0.5 * (lo + hi);
        setHeaterOutletTemperature(mid, "C");
        run();
        if (isMarginMet()) {
          hi = mid;
        } else {
          lo = mid;
        }
      }
      return hi;
    } finally {
      heaterTemperatureK = saved;
      analysed = false;
    }
  }

  /**
   * Results as a map for reporting.
   *
   * @return ordered map of the main results
   */
  public Map<String, Object> getResults() {
    ensureRun();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("source_pressure_bara", sourcePressureBara);
    m.put("source_temperature_C", sourceTemperatureK - 273.15);
    m.put("supply_pressure_bara", supplyPressureBara);
    m.put("supply_temperature_C", supplyTemperatureK - 273.15);
    m.put("hydrocarbon_dew_point_C", hydrocarbonDewPointC);
    m.put("water_dew_point_C", waterDewPointC);
    m.put("minimum_margin_K", getMinimumMarginK());
    m.put("required_margin_K", requiredMarginK);
    m.put("liquid_mass_fraction", liquidMassFraction);
    m.put("margin_met", isMarginMet());
    return m;
  }

  /**
   * Liquid load carried by the gas, implied by a filter sump filling rate.
   *
   * @param liquidRateLitresPerDay liquid collected per day in litres
   * @param liquidDensityKgM3 density of the collected liquid in kg/m3
   * @param gasRateKgHr gas mass flow through the filter in kg/h
   * @param gasMolarMassKgMol gas molar mass in kg/mol
   * @return liquid load in kg per million standard cubic metres of gas
   */
  public static double impliedLiquidLoadKgPerMSm3(double liquidRateLitresPerDay, double liquidDensityKgM3,
      double gasRateKgHr, double gasMolarMassKgMol) {
    double liquidKgPerDay = liquidRateLitresPerDay * liquidDensityKgM3 / 1000.0;
    double gasMSm3PerDay = gasRateKgHr * 24.0 / gasMolarMassKgMol * SM3_PER_MOL / 1.0e6;
    return gasMSm3PerDay > 0.0 ? liquidKgPerDay / gasMSm3PerDay : Double.NaN;
  }

  /**
   * Days until a filter sump of the given liquid capacity is full.
   *
   * @param capacityLitres usable sump capacity in litres
   * @param liquidRateLitresPerDay liquid collected per day in litres
   * @return filter sump life in days; positive infinity when nothing is collected
   */
  public static double sumpFillTimeDays(double capacityLitres, double liquidRateLitresPerDay) {
    return liquidRateLitresPerDay > 0.0 ? capacityLitres / liquidRateLitresPerDay : Double.POSITIVE_INFINITY;
  }
}
