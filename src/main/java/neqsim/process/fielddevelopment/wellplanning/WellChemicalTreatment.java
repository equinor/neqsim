package neqsim.process.fielddevelopment.wellplanning;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Hydrate-inhibitor (MEG or methanol) requirement along a well using rigorous CPA hydrate equilibrium, phase
 * partitioning of the inhibitor, and injection-rate and pump-duty basis.
 *
 * <p>
 * For each pressure and temperature station (for example the flowing wellhead, mid-well and tree conditions and the
 * shut-in cold tree) the model finds, by bisection, the inhibitor mass fraction w on the basis of inhibitor plus water
 * such that the NeqSim hydrate formation temperature of the whole fluid (including inhibitor partitioning into gas and
 * hydrocarbon liquid) is at least the subcooling margin below the station temperature. The governing station gives the
 * lean-solution injection rate m_lean = w m_water / (x_lean - w). A TP flash at the station reports the inhibitor split
 * between gas, hydrocarbon liquid and aqueous phase and the aqueous inhibitor concentration.
 * </p>
 * <p>
 * The fluid must be a CPA system (for example SystemSrkCPAstatoil with mixing rule 10) containing water. The generic
 * {@link #chemicalInjectionRateLitresPerHour(double, double, double, double)} gives injection rates for scale,
 * corrosion, scavenger and demulsifier doses that are sized with the existing chemistry performance classes.
 * </p>
 * <p>
 * Limitations: screening; hydrate thermodynamics only (no kinetic inhibitors, no salt-in-MEG solubility or regeneration
 * model); inhibitor injected at the station is assumed mixed with all produced water; no downhole injection-mandrel
 * hydraulics. Validation compares the CPA inhibition effect with the Hammerschmidt correlation and checks mass
 * balances; it is not field-calibrated.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class WellChemicalTreatment implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Thermodynamic hydrate inhibitor. */
  public enum HydrateInhibitor {
    /** Mono-ethylene glycol. */
    MEG("MEG", 0.06207, 1113.0),
    /** Methanol. */
    METHANOL("methanol", 0.03204, 792.0);

    private final String componentName;
    private final double molarMassKgPerMol;
    private final double densityKgPerM3;

    /**
     * Creates an inhibitor constant.
     *
     * @param componentName NeqSim component name
     * @param molarMassKgPerMol molar mass in kg/mol
     * @param densityKgPerM3 neat liquid density in kg/m3
     */
    HydrateInhibitor(String componentName, double molarMassKgPerMol, double densityKgPerM3) {
      this.componentName = componentName;
      this.molarMassKgPerMol = molarMassKgPerMol;
      this.densityKgPerM3 = densityKgPerM3;
    }

    /**
     * Gets the NeqSim component name.
     *
     * @return component name
     */
    public String getComponentName() {
      return componentName;
    }

    /**
     * Gets the molar mass.
     *
     * @return molar mass in kg/mol
     */
    public double getMolarMassKgPerMol() {
      return molarMassKgPerMol;
    }
  }

  private final SystemInterface base;
  private final double waterMolesInBase;
  private HydrateInhibitor inhibitor = HydrateInhibitor.MEG;
  private double leanWeightFraction = 0.9;
  private double marginK = 3.0;
  private double waterRateKgPerH = 1000.0;
  private final List<double[]> stations = new ArrayList<double[]>();
  private final List<String> stationNames = new ArrayList<String>();

  /**
   * Creates the treatment model.
   *
   * @param cpaFluid CPA fluid with water and hydrocarbons, mixing rule 10 set; it is cloned
   * @throws IllegalArgumentException if the fluid has no water
   */
  public WellChemicalTreatment(SystemInterface cpaFluid) {
    this.base = cpaFluid.clone();
    if (!base.hasComponent("water")) {
      throw new IllegalArgumentException("fluid must contain water");
    }
    this.waterMolesInBase = base.getComponent("water").getNumberOfmoles();
  }

  /**
   * Selects the inhibitor and the lean-solution strength.
   *
   * @param inhibitor MEG or methanol
   * @param leanWeightFraction inhibitor mass fraction of the injected lean solution (for example 0.9 for lean MEG)
   * @return this model
   */
  public WellChemicalTreatment setInhibitor(HydrateInhibitor inhibitor, double leanWeightFraction) {
    this.inhibitor = inhibitor;
    this.leanWeightFraction = leanWeightFraction;
    if (!base.hasComponent(inhibitor.componentName)) {
      base.addComponent(inhibitor.componentName, 1.0e-12 * base.getTotalNumberOfMoles());
      base.setMixingRule(10);
    }
    return this;
  }

  /**
   * Sets the required subcooling margin below the station temperature.
   *
   * @param marginK margin in K
   * @return this model
   */
  public WellChemicalTreatment setSubcoolingMargin(double marginK) {
    this.marginK = marginK;
    return this;
  }

  /**
   * Sets the produced-water mass rate.
   *
   * @param kgPerHour water rate in kg/h
   * @return this model
   */
  public WellChemicalTreatment setWaterRate(double kgPerHour) {
    this.waterRateKgPerH = kgPerHour;
    return this;
  }

  /**
   * Adds a pressure and temperature station.
   *
   * @param name station name
   * @param pressureBara pressure in bara
   * @param temperatureC temperature in degrees Celsius
   * @return this model
   */
  public WellChemicalTreatment addStation(String name, double pressureBara, double temperatureC) {
    stations.add(new double[] {pressureBara, temperatureC});
    stationNames.add(name);
    return this;
  }

  /**
   * Computes the hydrate formation temperature for an inhibitor mass fraction.
   *
   * @param pressureBara pressure in bara
   * @param inhibitorWeightFraction inhibitor mass fraction on an inhibitor plus water basis (0 to below 1)
   * @return hydrate formation temperature in degrees Celsius
   */
  public double hydrateTemperatureC(double pressureBara, double inhibitorWeightFraction) {
    if (!base.hasComponent(inhibitor.componentName)) {
      setInhibitor(inhibitor, leanWeightFraction);
    }
    SystemInterface f = base.clone();
    double waterMassKg = waterMolesInBase * 0.018015;
    double nInh = inhibitorWeightFraction / (1.0 - inhibitorWeightFraction) * waterMassKg / inhibitor.molarMassKgPerMol;
    if (nInh > 0.0) {
      f.addComponent(inhibitor.componentName, nInh);
    }
    f.setPressure(pressureBara);
    f.setTemperature(283.15);
    f.setMultiPhaseCheck(true);
    f.setHydrateCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(f);
    try {
      ops.hydrateFormationTemperature();
    } catch (Exception e) {
      return Double.NaN;
    }
    return f.getTemperature() - 273.15;
  }

  /**
   * Finds the inhibitor mass fraction that keeps the hydrate temperature at the target.
   *
   * @param pressureBara pressure in bara
   * @param targetTemperatureC maximum allowed hydrate formation temperature in degrees Celsius
   * @return inhibitor mass fraction (zero if not needed, NaN if above 0.75 would be required)
   */
  public double requiredWeightFraction(double pressureBara, double targetTemperatureC) {
    double t0 = hydrateTemperatureC(pressureBara, 0.0);
    if (Double.isNaN(t0) || t0 <= targetTemperatureC) {
      return Double.isNaN(t0) ? Double.NaN : 0.0;
    }
    double lo = 0.0;
    double hi = 0.75;
    double tHi = hydrateTemperatureC(pressureBara, hi);
    if (Double.isNaN(tHi) || tHi > targetTemperatureC) {
      return Double.NaN;
    }
    while (hi - lo > 0.002) {
      double mid = 0.5 * (lo + hi);
      double t = hydrateTemperatureC(pressureBara, mid);
      if (!Double.isNaN(t) && t > targetTemperatureC) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return hi;
  }

  /**
   * Evaluates all stations and the governing injection requirement.
   *
   * @return result
   * @throws IllegalStateException if no station has been added
   */
  public Result evaluate() {
    if (stations.isEmpty()) {
      throw new IllegalStateException("add at least one station with addStation()");
    }
    Result r = new Result();
    r.inhibitor = inhibitor;
    r.leanWeightFraction = leanWeightFraction;
    double governing = -1.0;
    for (int i = 0; i < stations.size(); i++) {
      double p = stations.get(i)[0];
      double t = stations.get(i)[1];
      StationResult s = new StationResult();
      s.name = stationNames.get(i);
      s.pressureBara = p;
      s.temperatureC = t;
      s.uninhibitedHydrateTemperatureC = hydrateTemperatureC(p, 0.0);
      s.requiredWeightFraction = requiredWeightFraction(p, t - marginK);
      if (!Double.isNaN(s.requiredWeightFraction)) {
        s.inhibitedHydrateTemperatureC = hydrateTemperatureC(p, s.requiredWeightFraction);
        partition(s, p, t, s.requiredWeightFraction);
        if (s.requiredWeightFraction > governing) {
          governing = s.requiredWeightFraction;
          r.governingStation = s.name;
        }
      } else {
        r.warnings.add("Station " + s.name + ": hydrate not controllable below 75 wt% inhibitor or calculation failed");
      }
      r.stations.add(s);
    }
    r.governingWeightFraction = governing;
    if (governing >= 0.0) {
      double w = governing;
      r.leanSolutionKgPerH = w <= 0.0 ? 0.0 : w * waterRateKgPerH / (leanWeightFraction - w);
      r.activeInhibitorKgPerH = r.leanSolutionKgPerH * leanWeightFraction;
      double x = leanWeightFraction;
      r.leanDensityKgPerM3 = 1.0 / (x / inhibitor.densityKgPerM3 + (1.0 - x) / 1000.0);
      r.leanSolutionM3PerH = r.leanSolutionKgPerH / r.leanDensityKgPerM3;
    }
    if (leanWeightFraction <= governing) {
      r.warnings.add("Lean solution strength is not above the required aqueous concentration");
    }
    return r;
  }

  /**
   * Flashes the dosed fluid at a station and records the inhibitor partitioning.
   *
   * @param s station result to fill
   * @param pBara pressure in bara
   * @param tC temperature in degrees Celsius
   * @param w inhibitor mass fraction on an inhibitor plus water basis
   */
  private void partition(StationResult s, double pBara, double tC, double w) {
    SystemInterface f = base.clone();
    double waterMassKg = waterMolesInBase * 0.018015;
    double nInh = w / (1.0 - w) * waterMassKg / inhibitor.molarMassKgPerMol;
    if (nInh > 0.0) {
      f.addComponent(inhibitor.componentName, nInh);
    }
    f.setPressure(pBara);
    f.setTemperature(tC + 273.15);
    f.setMultiPhaseCheck(true);
    new ThermodynamicOperations(f).TPflash();
    f.initProperties();
    double total = 0.0;
    double gas = 0.0;
    double liquidHc = 0.0;
    double aq = 0.0;
    double aqWt = 0.0;
    for (int i = 0; i < f.getNumberOfPhases(); i++) {
      PhaseInterface ph = f.getPhase(i);
      double n = ph.getComponent(inhibitor.componentName).getNumberOfMolesInPhase();
      total += n;
      if (ph.getType() == PhaseType.GAS) {
        gas += n;
      } else if (ph.getType() == PhaseType.AQUEOUS) {
        aq += n;
        aqWt = ph.getWtFrac(inhibitor.componentName);
      } else {
        liquidHc += n;
      }
    }
    if (total > 0.0) {
      s.fractionInGas = gas / total;
      s.fractionInHydrocarbonLiquid = liquidHc / total;
      s.fractionInAqueous = aq / total;
    }
    s.aqueousInhibitorWeightFraction = aqWt;
  }

  /**
   * Computes the injection rate of a chemical dosed on produced water.
   *
   * @param doseMgPerLitreWater dose in mg of product active per litre of water
   * @param waterRateM3PerHour produced water rate in m3/h
   * @param activeWeightFraction active mass fraction of the product (0 to 1)
   * @param productDensityKgPerL product density in kg/L
   * @return product injection rate in L/h
   */
  public static double chemicalInjectionRateLitresPerHour(double doseMgPerLitreWater, double waterRateM3PerHour,
      double activeWeightFraction, double productDensityKgPerL) {
    double activeKgPerHour = doseMgPerLitreWater * waterRateM3PerHour * 1000.0 * 1.0e-6;
    return activeKgPerHour / (activeWeightFraction * productDensityKgPerL);
  }

  /**
   * Computes the hydraulic and shaft power of an injection pump.
   *
   * @param volumeRateM3PerHour injected volume rate in m3/h
   * @param dischargePressureBara required discharge pressure in bara (injection point pressure plus line loss)
   * @param suctionPressureBara suction pressure in bara
   * @param efficiency pump efficiency (0 to 1)
   * @return shaft power in kW
   */
  public static double pumpShaftPowerKw(double volumeRateM3PerHour, double dischargePressureBara,
      double suctionPressureBara, double efficiency) {
    double hydraulicW = volumeRateM3PerHour / 3600.0 * (dischargePressureBara - suctionPressureBara) * 1.0e5;
    return hydraulicW / efficiency / 1000.0;
  }

  /**
   * Computes the pressure drop in a chemical injection line (laminar Hagen-Poiseuille or turbulent Blasius).
   *
   * @param volumeRateM3PerHour volume rate in m3/h
   * @param lengthM line length in m
   * @param innerDiameterM line inner diameter in m
   * @param viscosityPaS dynamic viscosity in Pa s
   * @param densityKgPerM3 density in kg/m3
   * @return friction pressure drop in bar
   */
  public static double injectionLineFrictionBar(double volumeRateM3PerHour, double lengthM, double innerDiameterM,
      double viscosityPaS, double densityKgPerM3) {
    double q = volumeRateM3PerHour / 3600.0;
    double area = Math.PI * innerDiameterM * innerDiameterM / 4.0;
    double v = q / area;
    double re = densityKgPerM3 * v * innerDiameterM / viscosityPaS;
    double f = re < 2300.0 ? 64.0 / Math.max(re, 1.0e-9) : 0.3164 / Math.pow(re, 0.25);
    return f * lengthM / innerDiameterM * densityKgPerM3 * v * v / 2.0 / 1.0e5;
  }

  /** Result at one station. */
  public static final class StationResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private String name;
    private double pressureBara;
    private double temperatureC;
    private double uninhibitedHydrateTemperatureC;
    private double requiredWeightFraction = Double.NaN;
    private double inhibitedHydrateTemperatureC = Double.NaN;
    private double fractionInGas;
    private double fractionInHydrocarbonLiquid;
    private double fractionInAqueous;
    private double aqueousInhibitorWeightFraction;

    /**
     * Gets the station name.
     *
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the station pressure.
     *
     * @return pressure in bara
     */
    public double getPressureBara() {
      return pressureBara;
    }

    /**
     * Gets the station temperature.
     *
     * @return temperature in degrees Celsius
     */
    public double getTemperatureC() {
      return temperatureC;
    }

    /**
     * Gets the hydrate formation temperature without inhibitor.
     *
     * @return temperature in degrees Celsius
     */
    public double getUninhibitedHydrateTemperatureC() {
      return uninhibitedHydrateTemperatureC;
    }

    /**
     * Gets the required inhibitor mass fraction on an inhibitor plus water basis.
     *
     * @return mass fraction, zero if no inhibitor is needed, NaN if not controllable
     */
    public double getRequiredWeightFraction() {
      return requiredWeightFraction;
    }

    /**
     * Gets the hydrate formation temperature at the required dose.
     *
     * @return temperature in degrees Celsius
     */
    public double getInhibitedHydrateTemperatureC() {
      return inhibitedHydrateTemperatureC;
    }

    /**
     * Gets the inhibitor mole fraction of the total inhibitor that sits in the gas phase.
     *
     * @return fraction of the inhibitor moles
     */
    public double getFractionInGas() {
      return fractionInGas;
    }

    /**
     * Gets the fraction of the inhibitor in hydrocarbon liquid.
     *
     * @return fraction of the inhibitor moles
     */
    public double getFractionInHydrocarbonLiquid() {
      return fractionInHydrocarbonLiquid;
    }

    /**
     * Gets the fraction of the inhibitor in the aqueous phase.
     *
     * @return fraction of the inhibitor moles
     */
    public double getFractionInAqueous() {
      return fractionInAqueous;
    }

    /**
     * Gets the inhibitor mass fraction in the aqueous phase after the station flash.
     *
     * @return mass fraction of the aqueous phase
     */
    public double getAqueousInhibitorWeightFraction() {
      return aqueousInhibitorWeightFraction;
    }
  }

  /** Result of a well treatment evaluation. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private HydrateInhibitor inhibitor;
    private double leanWeightFraction;
    private final List<StationResult> stations = new ArrayList<StationResult>();
    private final List<String> warnings = new ArrayList<String>();
    private String governingStation;
    private double governingWeightFraction = Double.NaN;
    private double leanSolutionKgPerH;
    private double activeInhibitorKgPerH;
    private double leanDensityKgPerM3;
    private double leanSolutionM3PerH;

    /**
     * Gets the station results.
     *
     * @return list of station results
     */
    public List<StationResult> getStations() {
      return new ArrayList<StationResult>(stations);
    }

    /**
     * Gets the governing station name.
     *
     * @return name, or null if none could be solved
     */
    public String getGoverningStation() {
      return governingStation;
    }

    /**
     * Gets the governing required inhibitor mass fraction.
     *
     * @return mass fraction on an inhibitor plus water basis
     */
    public double getGoverningWeightFraction() {
      return governingWeightFraction;
    }

    /**
     * Gets the lean-solution injection rate.
     *
     * @return rate in kg/h
     */
    public double getLeanSolutionKgPerHour() {
      return leanSolutionKgPerH;
    }

    /**
     * Gets the active inhibitor rate.
     *
     * @return rate in kg/h
     */
    public double getActiveInhibitorKgPerHour() {
      return activeInhibitorKgPerH;
    }

    /**
     * Gets the lean-solution density (ideal volume mixing).
     *
     * @return density in kg/m3
     */
    public double getLeanDensityKgPerM3() {
      return leanDensityKgPerM3;
    }

    /**
     * Gets the lean-solution volume rate.
     *
     * @return rate in m3/h
     */
    public double getLeanSolutionM3PerHour() {
      return leanSolutionM3PerH;
    }

    /**
     * Gets the warnings.
     *
     * @return list of warnings
     */
    public List<String> getWarnings() {
      return new ArrayList<String>(warnings);
    }

    /**
     * Returns the key results as a map.
     *
     * @return ordered map of result names to values
     */
    public Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<String, Object>();
      m.put("inhibitor", inhibitor.name());
      m.put("leanWeightFraction", leanWeightFraction);
      m.put("governingStation", governingStation);
      m.put("governingWeightFraction", governingWeightFraction);
      m.put("leanSolutionKgPerHour", leanSolutionKgPerH);
      m.put("leanSolutionM3PerHour", leanSolutionM3PerH);
      return m;
    }
  }
}
