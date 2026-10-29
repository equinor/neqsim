package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.empiric.BukacekWaterInGas;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Screening model of an extractive moisture-analyser sample system (probe, heat-traced tube, pressure regulator,
 * analyser cell).
 *
 * <p>
 * The class answers the questions that decide whether a water dew point analyser reading is a process value or a
 * sample-system artefact after a shutdown and restart:
 * </p>
 * <ul>
 * <li>does water, hydrocarbon or glycol condense in the sample tube or cabinet at a given tube temperature;</li>
 * <li>how long does the sample take to travel from the process tap to the cell (transport lag);</li>
 * <li>how long does a liquid water film take to evaporate into the dry sample flow (dry-out time);</li>
 * <li>how long does a glycol (TEG) film take to evaporate (persistence of a glycol film on tubing or sensor).</li>
 * </ul>
 *
 * <p>
 * Water content and water dew point use the Bukacek correlation (same as {@link WaterDewPointAnalyser} default). The
 * hydrocarbon dew point uses a NeqSim dew point temperature flash on the water- and glycol-free gas. The glycol vapour
 * content uses the component vapour pressure (ideal Raoult, enhancement factor neglected), which makes the glycol
 * dry-out time an upper bound at high pressure and close to exact at the near-atmospheric analyser cell. Flows are in
 * normal litres per hour (0 C, 1.01325 bara). The film evaporation model assumes the sample gas leaving the film
 * location is saturated at the film temperature (maximum evaporation capacity), so the dry-out times are lower bounds
 * for a given film mass.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class AnalyserSampleSystem implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Molar volume of an ideal gas at 0 C and 1.01325 bara in litres per mole. */
  private static final double NORMAL_MOLAR_VOLUME_L = 22.414;

  /** Molar mass of water in kg/mol. */
  private static final double MOLAR_MASS_WATER = 0.018015;

  /** Sample gas at process conditions (must contain water; glycol optional). */
  private final SystemInterface sampleGas;

  /** Process (tap) pressure in bara. */
  private double processPressureBara;

  /** Sample tube / cabinet temperature in C. */
  private double tubeTemperatureC = 30.0;

  /** Pressure downstream of the sample pressure regulator in bara. */
  private double regulatorOutletPressureBara = 3.5;

  /** Analyser cell (sensor) temperature in C. */
  private double sensorTemperatureC = 60.0;

  /** Analyser flow in normal litres per hour. */
  private double analyserFlowNlPerHour = 15.0;

  /** Bypass (fast-loop) flow in normal litres per hour. */
  private double bypassFlowNlPerHour = 90.0;

  /** Sample tube inner diameter in m. */
  private double tubeInnerDiameterM = 1.4e-3;

  /** Sample tube length in m. */
  private double tubeLengthM = 9.0;

  /** Name of the glycol component used for glycol film calculations. */
  private String glycolComponentName = "TEG";

  /**
   * Create a sample-system model.
   *
   * @param sampleGas gas at the process tap (pressure and composition are taken from this system)
   */
  public AnalyserSampleSystem(SystemInterface sampleGas) {
    if (sampleGas == null) {
      throw new IllegalArgumentException("sampleGas must not be null");
    }
    if (!sampleGas.hasComponent("water")) {
      throw new IllegalArgumentException("sampleGas must contain the component 'water'");
    }
    this.sampleGas = sampleGas;
    this.processPressureBara = sampleGas.getPressure("bara");
  }

  /**
   * Water mole fraction of the sample gas (overall composition).
   *
   * @return water mole fraction (-)
   */
  public double getWaterMoleFraction() {
    double total = sampleGas.getTotalNumberOfMoles();
    return total > 0.0 ? sampleGas.getComponent("water").getNumberOfmoles() / total : 0.0;
  }

  /**
   * Water mole fraction of a gas saturated with water at the given temperature and pressure (Bukacek).
   *
   * @param temperatureC temperature in C
   * @param pressureBara pressure in bara
   * @return saturated water mole fraction (-)
   */
  public static double getSaturatedWaterMoleFraction(double temperatureC, double pressureBara) {
    return BukacekWaterInGas.getWaterInGas(temperatureC + 273.15, pressureBara);
  }

  /**
   * Water dew point of the sample gas at a given pressure (Bukacek, constant water content).
   *
   * @param pressureBara pressure in bara
   * @return water dew point in C
   */
  public double getWaterDewPointC(double pressureBara) {
    double x = getWaterMoleFraction();
    if (x <= 0.0) {
      return Double.NaN;
    }
    // bisection on the Bukacek water content (robust for wet and dry gas and for low pressures)
    double lo = 173.15;
    double hi = 473.15;
    for (int i = 0; i < 100; i++) {
      double mid = 0.5 * (lo + hi);
      if (BukacekWaterInGas.getWaterInGas(mid, pressureBara) > x) {
        hi = mid;
      } else {
        lo = mid;
      }
    }
    return 0.5 * (lo + hi) - 273.15;
  }

  /**
   * Water dew point at the process (tap) pressure.
   *
   * @return water dew point in C
   */
  public double getWaterDewPointAtTapC() {
    return getWaterDewPointC(processPressureBara);
  }

  /**
   * Water dew point downstream of the sample pressure regulator.
   *
   * @return water dew point in C
   */
  public double getWaterDewPointAtRegulatorOutletC() {
    return getWaterDewPointC(regulatorOutletPressureBara);
  }

  /**
   * Water condensation margin in the high-pressure sample tube (tube temperature minus water dew point at tap
   * pressure). A negative value means liquid water condenses in the tube.
   *
   * @return margin in K
   */
  public double getWaterCondensationMarginK() {
    return tubeTemperatureC - getWaterDewPointAtTapC();
  }

  /**
   * Whether liquid water condenses in the high-pressure sample tube.
   *
   * @return true if the tube is colder than the water dew point
   */
  public boolean isWaterCondensingInTube() {
    return getWaterCondensationMarginK() < 0.0;
  }

  /**
   * Hydrocarbon dew point of the water- and glycol-free sample gas at a given pressure.
   *
   * @param pressureBara pressure in bara
   * @return hydrocarbon dew point in C, or NaN if the flash does not converge or there is no dew point
   */
  public double getHydrocarbonDewPointC(double pressureBara) {
    SystemInterface hc = sampleGas.clone();
    String[] polar = {"water", "TEG", "MEG", "DEG", "methanol", glycolComponentName};
    for (String name : polar) {
      if (hc.hasComponent(name) && hc.getNumberOfComponents() > 1) {
        hc.removeComponent(name);
      }
    }
    hc.setPressure(pressureBara, "bara");
    hc.setTemperature(273.15 + 20.0);
    try {
      hc.init(0);
      ThermodynamicOperations ops = new ThermodynamicOperations(hc);
      ops.dewPointTemperatureFlash();
      double t = hc.getTemperature() - 273.15;
      return Double.isFinite(t) ? t : Double.NaN;
    } catch (Exception ex) {
      return Double.NaN;
    }
  }

  /**
   * Hydrocarbon condensation margin in the sample tube (tube temperature minus hydrocarbon dew point at tap pressure).
   * Negative means hydrocarbon liquid condenses in the tube.
   *
   * @return margin in K, or NaN if the hydrocarbon dew point could not be calculated
   */
  public double getHydrocarbonCondensationMarginK() {
    return tubeTemperatureC - getHydrocarbonDewPointC(processPressureBara);
  }

  /**
   * Total sample flow through the high-pressure tube (analyser plus bypass) in normal litres per hour.
   *
   * @return flow in Nl/h
   */
  public double getTotalSampleFlowNlPerHour() {
    return analyserFlowNlPerHour + bypassFlowNlPerHour;
  }

  /**
   * Transport lag from the process tap to the regulator through the high-pressure tube (ideal-gas volume correction
   * with the compressibility of the sample gas at tube conditions).
   *
   * @return lag in seconds
   */
  public double getTransportLagSeconds() {
    double volumeL = Math.PI / 4.0 * tubeInnerDiameterM * tubeInnerDiameterM * tubeLengthM * 1000.0;
    double z = compressibilityAtTube();
    double tubeT = tubeTemperatureC + 273.15;
    double actualFlowLPerS = getTotalSampleFlowNlPerHour() / 3600.0 * (1.01325 / processPressureBara) * (tubeT / 273.15)
        * z;
    if (actualFlowLPerS <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    return volumeL / actualFlowLPerS;
  }

  /**
   * Compressibility factor of the sample gas at tube temperature and tap pressure (1.0 if the flash fails).
   *
   * @return compressibility factor (-)
   */
  private double compressibilityAtTube() {
    try {
      SystemInterface s = sampleGas.clone();
      s.setPressure(processPressureBara, "bara");
      s.setTemperature(tubeTemperatureC + 273.15);
      new ThermodynamicOperations(s).TPflash();
      s.initProperties();
      double z = s.getPhase(0).getZ();
      return (z > 0.1 && z < 2.0) ? z : 1.0;
    } catch (Exception ex) {
      return 1.0;
    }
  }

  /**
   * Time to evaporate a liquid water film into the sample flow. The gas passing the film is assumed to leave saturated
   * at the film temperature and pressure (maximum evaporation capacity), so the result is a lower bound.
   *
   * @param filmMassKg mass of liquid water in the film in kg
   * @param filmTemperatureC film temperature in C
   * @param filmPressureBara pressure at the film in bara
   * @param flowNlPerHour sample flow passing the film in Nl/h
   * @return dry-out time in hours (infinite if the gas is already saturated)
   */
  public double getWaterFilmDryOutTimeHours(double filmMassKg, double filmTemperatureC, double filmPressureBara,
      double flowNlPerHour) {
    double ySat = getSaturatedWaterMoleFraction(filmTemperatureC, filmPressureBara);
    double driving = ySat - getWaterMoleFraction();
    if (driving <= 0.0 || flowNlPerHour <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    double molPerHour = flowNlPerHour / NORMAL_MOLAR_VOLUME_L;
    double kgPerHour = molPerHour * driving * MOLAR_MASS_WATER;
    return filmMassKg / kgPerHour;
  }

  /**
   * Glycol vapour mole fraction of a gas in equilibrium with liquid glycol at the given temperature and pressure.
   *
   * <p>
   * Calculated with a two-phase TP flash of the dry sample gas with an excess of glycol, using the equation of state of
   * the sample gas (use a CPA system for glycols). The component vapour-pressure correlation is only used as a fallback
   * if the flash does not give a liquid glycol phase, because tabulated glycol vapour-pressure coefficients can be
   * unreliable far below the normal boiling point.
   * </p>
   *
   * @param temperatureC temperature in C
   * @param pressureBara pressure in bara
   * @return glycol vapour mole fraction (-), NaN if it cannot be calculated
   */
  public double getSaturatedGlycolMoleFraction(double temperatureC, double pressureBara) {
    try {
      SystemInterface s = sampleGas.clone();
      double total = s.getTotalNumberOfMoles();
      String[] remove = {"water", "MEG", "DEG", "methanol"};
      for (String name : remove) {
        if (s.hasComponent(name) && !name.equals(glycolComponentName)) {
          s.addComponent(name, -s.getComponent(name).getNumberOfmoles() * (1.0 - 1.0e-12));
        }
      }
      if (!s.hasComponent(glycolComponentName)) {
        return Double.NaN;
      }
      s.addComponent(glycolComponentName, 0.5 * total);
      s.setTemperature(temperatureC + 273.15);
      s.setPressure(pressureBara, "bara");
      s.setMultiPhaseCheck(true);
      new ThermodynamicOperations(s).TPflash();
      if (s.getNumberOfPhases() >= 2) {
        double y = s.getPhase(0).getComponent(glycolComponentName).getx();
        if (Double.isFinite(y) && y > 0.0 && y < 0.5) {
          return y;
        }
      }
    } catch (Exception ex) {
      // fall through to the vapour-pressure fallback
    }
    SystemInterface s = sampleGas.clone();
    if (!s.hasComponent(glycolComponentName)) {
      return Double.NaN;
    }
    double psat = s.getPhase(0).getComponent(glycolComponentName).getAntoineVaporPressure(temperatureC + 273.15);
    if (!Double.isFinite(psat) || psat <= 0.0) {
      return Double.NaN;
    }
    return Math.min(1.0, psat / pressureBara);
  }

  /**
   * Time to evaporate a liquid glycol film into the sample flow (ideal Raoult, saturated outlet gas, lower bound at low
   * pressure, upper bound at high pressure where the enhancement factor is neglected).
   *
   * @param filmMassKg glycol mass in the film in kg
   * @param filmTemperatureC film temperature in C
   * @param filmPressureBara pressure at the film in bara
   * @param flowNlPerHour sample flow passing the film in Nl/h
   * @return dry-out time in hours
   */
  public double getGlycolFilmDryOutTimeHours(double filmMassKg, double filmTemperatureC, double filmPressureBara,
      double flowNlPerHour) {
    double y = getSaturatedGlycolMoleFraction(filmTemperatureC, filmPressureBara);
    if (!Double.isFinite(y) || y <= 0.0 || flowNlPerHour <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    SystemInterface s = sampleGas.clone();
    if (!s.hasComponent(glycolComponentName)) {
      s.addComponent(glycolComponentName, 1.0e-10);
    }
    double molarMass = s.getPhase(0).getComponent(glycolComponentName).getMolarMass();
    double kgPerHour = flowNlPerHour / NORMAL_MOLAR_VOLUME_L * y * molarMass;
    return filmMassKg / kgPerHour;
  }

  /**
   * Summary of condensation margins, lag and the dry-out times of a reference film (1 g water and 1 g glycol at the
   * analyser cell).
   *
   * @return map of results (temperatures in C, margins in K, times in s or h)
   */
  public Map<String, Object> getResults() {
    Map<String, Object> r = new LinkedHashMap<String, Object>();
    r.put("processPressure_bara", processPressureBara);
    r.put("tubeTemperature_C", tubeTemperatureC);
    r.put("waterMoleFraction_ppm", getWaterMoleFraction() * 1.0e6);
    r.put("waterDewPointAtTap_C", getWaterDewPointAtTapC());
    r.put("waterDewPointAtRegulatorOutlet_C", getWaterDewPointAtRegulatorOutletC());
    r.put("waterCondensationMargin_K", getWaterCondensationMarginK());
    double hcdp = getHydrocarbonDewPointC(processPressureBara);
    r.put("hydrocarbonDewPointAtTap_C", hcdp);
    r.put("hydrocarbonCondensationMargin_K", tubeTemperatureC - hcdp);
    r.put("transportLag_s", getTransportLagSeconds());
    r.put("waterFilm1g_dryOutAtCell_h",
        getWaterFilmDryOutTimeHours(1.0e-3, sensorTemperatureC, regulatorOutletPressureBara, analyserFlowNlPerHour));
    r.put("glycolFilm1g_dryOutAtCell_h",
        getGlycolFilmDryOutTimeHours(1.0e-3, sensorTemperatureC, regulatorOutletPressureBara, analyserFlowNlPerHour));
    return r;
  }

  /**
   * Results as JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(getResults());
  }

  /**
   * Set the process (tap) pressure.
   *
   * @param pressureBara pressure in bara
   */
  public void setProcessPressureBara(double pressureBara) {
    this.processPressureBara = pressureBara;
  }

  /**
   * Get the process (tap) pressure.
   *
   * @return pressure in bara
   */
  public double getProcessPressureBara() {
    return processPressureBara;
  }

  /**
   * Set the sample tube / cabinet temperature.
   *
   * @param temperatureC temperature in C
   */
  public void setTubeTemperatureC(double temperatureC) {
    this.tubeTemperatureC = temperatureC;
  }

  /**
   * Get the sample tube / cabinet temperature.
   *
   * @return temperature in C
   */
  public double getTubeTemperatureC() {
    return tubeTemperatureC;
  }

  /**
   * Set the pressure downstream of the sample pressure regulator.
   *
   * @param pressureBara pressure in bara
   */
  public void setRegulatorOutletPressureBara(double pressureBara) {
    this.regulatorOutletPressureBara = pressureBara;
  }

  /**
   * Set the analyser cell temperature.
   *
   * @param temperatureC temperature in C
   */
  public void setSensorTemperatureC(double temperatureC) {
    this.sensorTemperatureC = temperatureC;
  }

  /**
   * Set analyser and bypass flows.
   *
   * @param analyserNlPerHour analyser flow in Nl/h
   * @param bypassNlPerHour bypass flow in Nl/h
   */
  public void setFlowsNlPerHour(double analyserNlPerHour, double bypassNlPerHour) {
    this.analyserFlowNlPerHour = analyserNlPerHour;
    this.bypassFlowNlPerHour = bypassNlPerHour;
  }

  /**
   * Set the high-pressure sample tube geometry.
   *
   * @param innerDiameterM inner diameter in m
   * @param lengthM length in m
   */
  public void setTubeGeometry(double innerDiameterM, double lengthM) {
    this.tubeInnerDiameterM = innerDiameterM;
    this.tubeLengthM = lengthM;
  }

  /**
   * Set the glycol component name used for glycol film calculations (default TEG).
   *
   * @param name component name
   */
  public void setGlycolComponentName(String name) {
    this.glycolComponentName = name;
  }
}
