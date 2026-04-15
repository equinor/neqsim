package neqsim.process.equipment.lng;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of LNG ageing KPIs at a single time step.
 *
 * <p>
 * Captures all quality metrics, thermodynamic state, and operational parameters for the LNG cargo
 * at one point in time. A list of these snapshots forms the complete ageing trajectory.
 * </p>
 *
 * <p>
 * Quality metrics tracked:
 * </p>
 * <ul>
 * <li>Wobbe Index (WI) — combustion interchangeability, MJ/Sm3</li>
 * <li>Gross Calorific Value (GCV) — energy content, MJ/Sm3 and MJ/kg</li>
 * <li>Methane Number (MN) — knock resistance for gas engines</li>
 * <li>Density — LNG liquid density per ISO 6578, kg/m3</li>
 * <li>Boil-off rate — daily mass loss percentage</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LNGAgeingResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Time elapsed since start of scenario (hours). */
  private double timeHours;

  /** LNG bulk temperature (K). */
  private double temperature;

  /** Tank pressure (bara). */
  private double pressure;

  /** LNG liquid volume remaining (m3). */
  private double liquidVolume;

  /** LNG liquid mass remaining (kg). */
  private double liquidMass;

  /** Moles of LNG remaining (mol). */
  private double liquidMoles;

  /** LNG liquid density per ISO 6578 (kg/m3). */
  private double density;

  /** Wobbe Index of the boil-off gas (MJ/Sm3). */
  private double wobbeIndex;

  /** Gross Calorific Value — volumetric (MJ/Sm3). */
  private double gcvVolumetric;

  /** Gross Calorific Value — mass basis (MJ/kg). */
  private double gcvMass;

  /** Methane Number of the BOG. */
  private double methaneNumber;

  /** Boil-off rate (%/day of LNG mass). */
  private double boilOffRatePctPerDay;

  /** BOG mass flow rate (kg/hr). */
  private double bogMassFlowRate;

  /** Heat ingress into the tank (kW). */
  private double heatIngressKW;

  /** Liquid composition — mole fractions keyed by component name. */
  private Map<String, Double> liquidComposition;

  /** Vapor composition — mole fractions keyed by component name. */
  private Map<String, Double> vaporComposition;

  /** Number of liquid layers (1 = well-mixed, &gt;1 = stratified). */
  private int numberOfLayers;

  /** Maximum density difference between layers (kg/m3). Zero if single layer. */
  private double maxLayerDensityDifference;

  /** Rollover risk flag. */
  private boolean rolloverRisk;

  /** Ambient temperature at this time step (K). */
  private double ambientTemperature;

  /** Operational mode description (e.g., "LOADED_VOYAGE", "UNLOADING"). */
  private String operationalMode;

  /**
   * Default constructor.
   */
  public LNGAgeingResult() {
    this.liquidComposition = new LinkedHashMap<String, Double>();
    this.vaporComposition = new LinkedHashMap<String, Double>();
    this.operationalMode = "LOADED_VOYAGE";
  }

  /**
   * Constructor with time stamp.
   *
   * @param timeHours time elapsed since start (hours)
   */
  public LNGAgeingResult(double timeHours) {
    this();
    this.timeHours = timeHours;
  }

  /**
   * Get the elapsed time.
   *
   * @return time since scenario start (hours)
   */
  public double getTimeHours() {
    return timeHours;
  }

  /**
   * Set the elapsed time.
   *
   * @param timeHours time since scenario start (hours)
   */
  public void setTimeHours(double timeHours) {
    this.timeHours = timeHours;
  }

  /**
   * Get the LNG bulk temperature.
   *
   * @return temperature (K)
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * Set the LNG bulk temperature.
   *
   * @param temperature temperature (K)
   */
  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }

  /**
   * Get the tank pressure.
   *
   * @return pressure (bara)
   */
  public double getPressure() {
    return pressure;
  }

  /**
   * Set the tank pressure.
   *
   * @param pressure pressure (bara)
   */
  public void setPressure(double pressure) {
    this.pressure = pressure;
  }

  /**
   * Get the LNG liquid volume.
   *
   * @return liquid volume (m3)
   */
  public double getLiquidVolume() {
    return liquidVolume;
  }

  /**
   * Set the LNG liquid volume.
   *
   * @param liquidVolume liquid volume (m3)
   */
  public void setLiquidVolume(double liquidVolume) {
    this.liquidVolume = liquidVolume;
  }

  /**
   * Get the LNG liquid mass.
   *
   * @return mass (kg)
   */
  public double getLiquidMass() {
    return liquidMass;
  }

  /**
   * Set the LNG liquid mass.
   *
   * @param liquidMass mass (kg)
   */
  public void setLiquidMass(double liquidMass) {
    this.liquidMass = liquidMass;
  }

  /**
   * Get the moles of LNG remaining.
   *
   * @return moles (mol)
   */
  public double getLiquidMoles() {
    return liquidMoles;
  }

  /**
   * Set the moles of LNG remaining.
   *
   * @param liquidMoles moles (mol)
   */
  public void setLiquidMoles(double liquidMoles) {
    this.liquidMoles = liquidMoles;
  }

  /**
   * Get the LNG density.
   *
   * @return density (kg/m3)
   */
  public double getDensity() {
    return density;
  }

  /**
   * Set the LNG density.
   *
   * @param density density (kg/m3)
   */
  public void setDensity(double density) {
    this.density = density;
  }

  /**
   * Get the Wobbe Index of the boil-off gas.
   *
   * @return WI (MJ/Sm3)
   */
  public double getWobbeIndex() {
    return wobbeIndex;
  }

  /**
   * Set the Wobbe Index.
   *
   * @param wobbeIndex WI (MJ/Sm3)
   */
  public void setWobbeIndex(double wobbeIndex) {
    this.wobbeIndex = wobbeIndex;
  }

  /**
   * Get the gross calorific value (volumetric basis).
   *
   * @return GCV (MJ/Sm3)
   */
  public double getGcvVolumetric() {
    return gcvVolumetric;
  }

  /**
   * Set the gross calorific value (volumetric).
   *
   * @param gcvVolumetric GCV (MJ/Sm3)
   */
  public void setGcvVolumetric(double gcvVolumetric) {
    this.gcvVolumetric = gcvVolumetric;
  }

  /**
   * Get the gross calorific value (mass basis).
   *
   * @return GCV (MJ/kg)
   */
  public double getGcvMass() {
    return gcvMass;
  }

  /**
   * Set the gross calorific value (mass basis).
   *
   * @param gcvMass GCV (MJ/kg)
   */
  public void setGcvMass(double gcvMass) {
    this.gcvMass = gcvMass;
  }

  /**
   * Get the methane number.
   *
   * @return methane number (dimensionless)
   */
  public double getMethaneNumber() {
    return methaneNumber;
  }

  /**
   * Set the methane number.
   *
   * @param methaneNumber methane number (dimensionless)
   */
  public void setMethaneNumber(double methaneNumber) {
    this.methaneNumber = methaneNumber;
  }

  /**
   * Get the boil-off rate.
   *
   * @return boil-off rate (%/day)
   */
  public double getBoilOffRatePctPerDay() {
    return boilOffRatePctPerDay;
  }

  /**
   * Set the boil-off rate.
   *
   * @param boilOffRatePctPerDay boil-off rate (%/day)
   */
  public void setBoilOffRatePctPerDay(double boilOffRatePctPerDay) {
    this.boilOffRatePctPerDay = boilOffRatePctPerDay;
  }

  /**
   * Get the BOG mass flow rate.
   *
   * @return BOG flow rate (kg/hr)
   */
  public double getBogMassFlowRate() {
    return bogMassFlowRate;
  }

  /**
   * Set the BOG mass flow rate.
   *
   * @param bogMassFlowRate BOG flow rate (kg/hr)
   */
  public void setBogMassFlowRate(double bogMassFlowRate) {
    this.bogMassFlowRate = bogMassFlowRate;
  }

  /**
   * Get the heat ingress.
   *
   * @return heat ingress (kW)
   */
  public double getHeatIngressKW() {
    return heatIngressKW;
  }

  /**
   * Set the heat ingress.
   *
   * @param heatIngressKW heat ingress (kW)
   */
  public void setHeatIngressKW(double heatIngressKW) {
    this.heatIngressKW = heatIngressKW;
  }

  /**
   * Get the liquid composition.
   *
   * @return map of component name to mole fraction
   */
  public Map<String, Double> getLiquidComposition() {
    return liquidComposition;
  }

  /**
   * Set the liquid composition.
   *
   * @param liquidComposition map of component name to mole fraction
   */
  public void setLiquidComposition(Map<String, Double> liquidComposition) {
    this.liquidComposition = liquidComposition;
  }

  /**
   * Get the vapor composition.
   *
   * @return map of component name to mole fraction
   */
  public Map<String, Double> getVaporComposition() {
    return vaporComposition;
  }

  /**
   * Set the vapor composition.
   *
   * @param vaporComposition map of component name to mole fraction
   */
  public void setVaporComposition(Map<String, Double> vaporComposition) {
    this.vaporComposition = vaporComposition;
  }

  /**
   * Get the number of liquid layers.
   *
   * @return number of layers (1 = well-mixed)
   */
  public int getNumberOfLayers() {
    return numberOfLayers;
  }

  /**
   * Set the number of liquid layers.
   *
   * @param numberOfLayers number of layers
   */
  public void setNumberOfLayers(int numberOfLayers) {
    this.numberOfLayers = numberOfLayers;
  }

  /**
   * Get the maximum density difference between layers.
   *
   * @return density difference (kg/m3)
   */
  public double getMaxLayerDensityDifference() {
    return maxLayerDensityDifference;
  }

  /**
   * Set the maximum density difference between layers.
   *
   * @param maxLayerDensityDifference density difference (kg/m3)
   */
  public void setMaxLayerDensityDifference(double maxLayerDensityDifference) {
    this.maxLayerDensityDifference = maxLayerDensityDifference;
  }

  /**
   * Check if rollover risk is present.
   *
   * @return true if rollover risk detected
   */
  public boolean isRolloverRisk() {
    return rolloverRisk;
  }

  /**
   * Set the rollover risk flag.
   *
   * @param rolloverRisk true if rollover risk detected
   */
  public void setRolloverRisk(boolean rolloverRisk) {
    this.rolloverRisk = rolloverRisk;
  }

  /**
   * Get the ambient temperature.
   *
   * @return ambient temperature (K)
   */
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /**
   * Set the ambient temperature.
   *
   * @param ambientTemperature ambient temperature (K)
   */
  public void setAmbientTemperature(double ambientTemperature) {
    this.ambientTemperature = ambientTemperature;
  }

  /**
   * Get the operational mode.
   *
   * @return operational mode description
   */
  public String getOperationalMode() {
    return operationalMode;
  }

  /**
   * Set the operational mode.
   *
   * @param operationalMode operational mode description
   */
  public void setOperationalMode(String operationalMode) {
    this.operationalMode = operationalMode;
  }

  /**
   * Get the elapsed time in days.
   *
   * @return time since start (days)
   */
  public double getTimeDays() {
    return timeHours / 24.0;
  }

  /**
   * Get the temperature in Celsius.
   *
   * @return temperature (C)
   */
  public double getTemperatureCelsius() {
    return temperature - 273.15;
  }

  /**
   * Convert this result to a map for JSON serialization.
   *
   * @return map of all result fields
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("timeHours", timeHours);
    map.put("timeDays", getTimeDays());
    map.put("temperature_K", temperature);
    map.put("temperature_C", getTemperatureCelsius());
    map.put("pressure_bara", pressure);
    map.put("liquidVolume_m3", liquidVolume);
    map.put("liquidMass_kg", liquidMass);
    map.put("density_kgm3", density);
    map.put("wobbeIndex_MJSm3", wobbeIndex);
    map.put("gcvVolumetric_MJSm3", gcvVolumetric);
    map.put("gcvMass_MJkg", gcvMass);
    map.put("methaneNumber", methaneNumber);
    map.put("boilOffRate_pctPerDay", boilOffRatePctPerDay);
    map.put("bogMassFlowRate_kghr", bogMassFlowRate);
    map.put("heatIngress_kW", heatIngressKW);
    map.put("numberOfLayers", numberOfLayers);
    map.put("maxLayerDensityDifference_kgm3", maxLayerDensityDifference);
    map.put("rolloverRisk", rolloverRisk);
    map.put("ambientTemperature_K", ambientTemperature);
    map.put("operationalMode", operationalMode);
    map.put("liquidComposition", liquidComposition);
    map.put("vaporComposition", vaporComposition);
    return map;
  }

  /**
   * Create a summary string for logging.
   *
   * @return formatted summary string
   */
  public String toSummaryString() {
    return String.format(
        "t=%.1fh (%.1fd): T=%.2fK (%.2fC), P=%.3f bara, V=%.0f m3, rho=%.1f kg/m3, "
            + "WI=%.2f MJ/Sm3, BOR=%.4f %%/d, layers=%d, rollover=%b",
        timeHours, getTimeDays(), temperature, getTemperatureCelsius(), pressure, liquidVolume,
        density, wobbeIndex, boilOffRatePctPerDay, numberOfLayers, rolloverRisk);
  }

  /**
   * Utility to collect multiple results into a tabular map.
   *
   * @param results list of ageing results over time
   * @return map of arrays keyed by property name for plotting
   */
  public static Map<String, double[]> toTimeSeries(List<LNGAgeingResult> results) {
    int n = results.size();
    Map<String, double[]> series = new LinkedHashMap<String, double[]>();
    double[] tArr = new double[n];
    double[] tempArr = new double[n];
    double[] pArr = new double[n];
    double[] volArr = new double[n];
    double[] rhoArr = new double[n];
    double[] wiArr = new double[n];
    double[] gcvArr = new double[n];
    double[] borArr = new double[n];
    double[] bogArr = new double[n];
    double[] qArr = new double[n];

    for (int i = 0; i < n; i++) {
      LNGAgeingResult r = results.get(i);
      tArr[i] = r.timeHours;
      tempArr[i] = r.temperature;
      pArr[i] = r.pressure;
      volArr[i] = r.liquidVolume;
      rhoArr[i] = r.density;
      wiArr[i] = r.wobbeIndex;
      gcvArr[i] = r.gcvVolumetric;
      borArr[i] = r.boilOffRatePctPerDay;
      bogArr[i] = r.bogMassFlowRate;
      qArr[i] = r.heatIngressKW;
    }

    series.put("timeHours", tArr);
    series.put("temperature_K", tempArr);
    series.put("pressure_bara", pArr);
    series.put("liquidVolume_m3", volArr);
    series.put("density_kgm3", rhoArr);
    series.put("wobbeIndex_MJSm3", wiArr);
    series.put("gcvVolumetric_MJSm3", gcvArr);
    series.put("boilOffRate_pctPerDay", borArr);
    series.put("bogMassFlowRate_kghr", bogArr);
    series.put("heatIngress_kW", qArr);
    return series;
  }
}
