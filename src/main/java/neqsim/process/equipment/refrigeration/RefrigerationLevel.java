package neqsim.process.equipment.refrigeration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.util.unit.PowerUnit;

/**
 * One evaporating temperature level of a {@link RefrigerationSystem}.
 *
 * <p>
 * A level owns the loads it serves, the superheat of the vapour leaving its evaporators and the pressure drop in its
 * suction line. A level without loads acts as a pure economizer stage. The evaporating temperature is either fixed or
 * follows the coldest bound process outlet temperature minus an approach.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class RefrigerationLevel implements Serializable {
  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Level name. */
  private final String name;

  /** Evaporating temperature in K. */
  private double temperature;

  /** Useful superheat of the vapour leaving the evaporators in K. */
  private double superheat = 0.0;

  /** Suction line pressure drop in bar. */
  private double suctionPressureDrop = 0.0;

  /** Isentropic efficiency of the compressor stage taking suction from this level, or NaN for the system default. */
  private double isentropicEfficiency = Double.NaN;

  /** Approach in K used when the temperature follows the loads, or NaN when the temperature is fixed. */
  private double approachToProcess = Double.NaN;

  /** Loads served by this level. */
  private final List<RefrigerationLoad> loads = new ArrayList<RefrigerationLoad>();

  // Results
  private double pressure = Double.NaN;
  private double duty = 0.0;
  private double evaporatorFlow = 0.0;
  private double flashGasFlow = 0.0;
  private double feedFlow = 0.0;
  private double feedVaporFraction = 0.0;

  /**
   * Creates a level.
   *
   * @param name level name
   * @param temperatureK evaporating temperature in K
   */
  RefrigerationLevel(String name, double temperatureK) {
    this.name = name;
    this.temperature = temperatureK;
  }

  /**
   * Adds a load to this level.
   *
   * @param load load to serve
   * @return this level
   */
  public RefrigerationLevel addLoad(RefrigerationLoad load) {
    if (load == null) {
      throw new IllegalArgumentException("load must not be null");
    }
    loads.add(load);
    return this;
  }

  /**
   * Binds a process cooler to this level; its duty is read every time the refrigeration system runs.
   *
   * @param cooler process cooler
   * @return this level
   */
  public RefrigerationLevel addCooler(Heater cooler) {
    return addLoad(RefrigerationLoad.of(cooler));
  }

  /**
   * Adds a fixed duty to this level.
   *
   * @param loadName load name
   * @param value duty, must not be negative
   * @param unit power unit, for example {@code "kW"}, {@code "MW"} or {@code "W"}
   * @return this level
   */
  public RefrigerationLevel addFixedDuty(String loadName, double value, String unit) {
    return addLoad(RefrigerationLoad.fixed(loadName, new PowerUnit(value, unit).getValue("W")));
  }

  /**
   * Makes the evaporating temperature follow the bound loads.
   *
   * <p>
   * On every run the evaporating temperature is set to the coldest process outlet temperature reported by the loads
   * minus the approach. Loads without a process temperature are ignored; if none reports one the last temperature is
   * kept.
   * </p>
   *
   * @param approachK temperature approach between process outlet and evaporating refrigerant in K, must be positive
   * @return this level
   */
  public RefrigerationLevel setTemperatureFromLoads(double approachK) {
    if (!(approachK > 0.0)) {
      throw new IllegalArgumentException("approach must be positive, got " + approachK);
    }
    this.approachToProcess = approachK;
    return this;
  }

  /**
   * Fixes the evaporating temperature.
   *
   * @param temperatureK evaporating temperature in K
   * @return this level
   */
  public RefrigerationLevel setTemperature(double temperatureK) {
    this.temperature = temperatureK;
    this.approachToProcess = Double.NaN;
    return this;
  }

  /**
   * Sets the useful superheat of the vapour leaving the evaporators.
   *
   * @param superheatK superheat in K, must not be negative
   * @return this level
   */
  public RefrigerationLevel setSuperheat(double superheatK) {
    if (superheatK < 0.0) {
      throw new IllegalArgumentException("superheat must be non-negative, got " + superheatK);
    }
    this.superheat = superheatK;
    return this;
  }

  /**
   * Sets the suction line pressure drop between this level and its compressor stage.
   *
   * @param pressureDropBar pressure drop in bar, must not be negative
   * @return this level
   */
  public RefrigerationLevel setSuctionPressureDrop(double pressureDropBar) {
    if (pressureDropBar < 0.0) {
      throw new IllegalArgumentException("suction pressure drop must be non-negative, got " + pressureDropBar);
    }
    this.suctionPressureDrop = pressureDropBar;
    return this;
  }

  /**
   * Sets the isentropic efficiency of the compressor stage taking suction from this level.
   *
   * @param efficiency isentropic efficiency in the range (0, 1]
   * @return this level
   */
  public RefrigerationLevel setIsentropicEfficiency(double efficiency) {
    if (!(efficiency > 0.0) || efficiency > 1.0) {
      throw new IllegalArgumentException("isentropic efficiency must be in (0, 1], got " + efficiency);
    }
    this.isentropicEfficiency = efficiency;
    return this;
  }

  /**
   * Reads the loads and updates the duty and, when it follows the loads, the evaporating temperature.
   */
  void refresh() {
    double total = 0.0;
    double coldest = Double.NaN;
    for (RefrigerationLoad load : loads) {
      double loadDuty = load.getDuty();
      if (loadDuty > 0.0) {
        total += loadDuty;
      }
      double processTemperature = load.getProcessOutletTemperature();
      if (!Double.isNaN(processTemperature) && (Double.isNaN(coldest) || processTemperature < coldest)) {
        coldest = processTemperature;
      }
    }
    duty = total;
    if (!Double.isNaN(approachToProcess) && !Double.isNaN(coldest)) {
      temperature = coldest - approachToProcess;
    }
  }

  /**
   * Stores the solved flows of this level.
   *
   * @param pressureBara evaporating pressure in bara
   * @param evaporatorFlowKgPerSec refrigerant evaporated by the loads in kg/s
   * @param flashGasFlowKgPerSec vapour leaving the flash economizer in kg/s
   * @param feedFlowKgPerSec refrigerant throttled into this level in kg/s
   * @param feedVaporFraction vapour mass fraction of the throttled feed
   */
  void setResults(double pressureBara, double evaporatorFlowKgPerSec, double flashGasFlowKgPerSec,
      double feedFlowKgPerSec, double feedVaporFraction) {
    this.pressure = pressureBara;
    this.evaporatorFlow = evaporatorFlowKgPerSec;
    this.flashGasFlow = flashGasFlowKgPerSec;
    this.feedFlow = feedFlowKgPerSec;
    this.feedVaporFraction = feedVaporFraction;
  }

  /**
   * Gets the level name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the evaporating temperature.
   *
   * @return temperature in K
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * Gets the superheat of the vapour leaving the evaporators.
   *
   * @return superheat in K
   */
  public double getSuperheat() {
    return superheat;
  }

  /**
   * Gets the suction line pressure drop.
   *
   * @return pressure drop in bar
   */
  public double getSuctionPressureDrop() {
    return suctionPressureDrop;
  }

  /**
   * Gets the isentropic efficiency set for this level's compressor stage.
   *
   * @return isentropic efficiency, or NaN when the system default applies
   */
  public double getIsentropicEfficiency() {
    return isentropicEfficiency;
  }

  /**
   * Gets the loads served by this level.
   *
   * @return unmodifiable list of loads
   */
  public List<RefrigerationLoad> getLoads() {
    return Collections.unmodifiableList(loads);
  }

  /**
   * Gets the evaporating pressure from the last run.
   *
   * @return pressure in bara, or NaN before the first run
   */
  public double getPressure() {
    return pressure;
  }

  /**
   * Gets the total duty of this level from the last run.
   *
   * @return duty in W
   */
  public double getDuty() {
    return duty;
  }

  /**
   * Gets the total duty of this level from the last run.
   *
   * @param unit power unit, for example {@code "kW"}
   * @return duty in the requested unit
   */
  public double getDuty(String unit) {
    return new PowerUnit(duty, "W").getValue(unit);
  }

  /**
   * Gets the refrigerant evaporated by the loads of this level.
   *
   * @return flow in kg/s
   */
  public double getEvaporatorFlow() {
    return evaporatorFlow;
  }

  /**
   * Gets the vapour leaving the flash economizer of this level.
   *
   * @return flow in kg/s, zero without a flash economizer
   */
  public double getFlashGasFlow() {
    return flashGasFlow;
  }

  /**
   * Gets the refrigerant throttled into this level.
   *
   * @return flow in kg/s
   */
  public double getFeedFlow() {
    return feedFlow;
  }

  /**
   * Gets the vapour mass fraction of the refrigerant throttled into this level.
   *
   * @return vapour mass fraction
   */
  public double getFeedVaporFraction() {
    return feedVaporFraction;
  }

  /**
   * Builds an ordered map of the level for reporting.
   *
   * @return results map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("name", name);
    map.put("evaporatingTemperature_C", temperature - 273.15);
    map.put("evaporatingPressure_bara", pressure);
    map.put("superheat_K", superheat);
    map.put("duty_kW", duty / 1000.0);
    map.put("evaporatorFlow_kg_per_hr", evaporatorFlow * 3600.0);
    map.put("flashGasFlow_kg_per_hr", flashGasFlow * 3600.0);
    map.put("feedFlow_kg_per_hr", feedFlow * 3600.0);
    map.put("feedVaporFraction", feedVaporFraction);
    List<Map<String, Object>> loadMaps = new ArrayList<Map<String, Object>>();
    for (RefrigerationLoad load : loads) {
      Map<String, Object> loadMap = new LinkedHashMap<String, Object>();
      loadMap.put("name", load.getName());
      loadMap.put("duty_kW", Math.max(load.getDuty(), 0.0) / 1000.0);
      double processTemperature = load.getProcessOutletTemperature();
      if (!Double.isNaN(processTemperature)) {
        loadMap.put("processOutletTemperature_C", processTemperature - 273.15);
      }
      loadMaps.add(loadMap);
    }
    map.put("loads", loadMaps);
    return map;
  }
}
