package neqsim.process.util.combustion;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * State-of-the-art combustion / flue-gas calculator for gas turbines, fired heaters, burners and other continuous
 * combustion processes.
 *
 * <p>
 * The calculation deliberately separates two physically different families of exhaust species, because a single
 * equilibrium (Gibbs) reactor is the wrong tool for the whole problem:
 * </p>
 *
 * <ul>
 * <li><b>Major species and SO2 &mdash; stoichiometric.</b> N2, O2, CO2, H2O, Ar and SO2 are combustion-complete (not
 * equilibrium-limited at the exhaust), so they are obtained by an exact element balance of full combustion with a
 * specified excess air. All fuel carbon &rarr; CO2, all fuel hydrogen &rarr; H2O, all fuel sulphur (e.g. H2S) &rarr;
 * SO2.</li>
 * <li><b>NOx and CO &mdash; kinetically frozen.</b> Thermal (Zeldovich) NO and CO are controlled by kinetics and quench
 * in the exhaust. A Gibbs reactor over-predicts NO at flame temperature and returns essentially zero at stack
 * temperature, so it must NOT be used for NOx. They are therefore estimated from public EMEP/EEA-style emission factors
 * (mass per unit fuel lower-heating-value energy), which the caller can replace with a vendor guarantee or
 * CEMS-measured value.</li>
 * </ul>
 *
 * <p>
 * The calculator also returns the air/fuel ratio, the excess-air (lambda) basis, the fuel lower heating value, and
 * &mdash; using a rigorous NeqSim energy balance on the product mixture &mdash; the adiabatic flame temperature.
 * </p>
 *
 * <p>
 * <b>Physics basis and alignment with combustion simulators.</b> The split above is the standard practice in process
 * combustion tools (heat-and-material-balance simulators, gas-turbine performance codes, and stack-emission
 * calculators): major species from a stoichiometric element balance with excess air, and NOx/CO from emission factors
 * or vendor correlations rather than exhaust equilibrium. The methodology follows widely used public references:
 * </p>
 *
 * <ul>
 * <li><b>Stoichiometry &amp; excess air (lambda).</b> Standard combustion stoichiometry; all C&rarr;CO2, H&rarr;H2O,
 * S&rarr;SO2 (Turns, <i>An Introduction to Combustion</i>; GPSA Engineering Data Book; API 560/537).</li>
 * <li><b>Adiabatic flame temperature.</b> First-law energy balance (product enthalpy = reactant enthalpy + LHV) solved
 * by a NeqSim PH-flash. Note this neglects high-temperature <i>dissociation</i> (CO2&rarr;CO+&frac12;O2,
 * H2O&rarr;OH+&frac12;H2, etc.), so like a no-dissociation H&amp;MB simulator it slightly <b>over-predicts</b> the peak
 * flame temperature near stoichiometric (by order 100&ndash;150 K for methane/air); the error shrinks with excess air.
 * Use the reference-flame-temperature calibration of the thermal-NOx/CO scaling so this systematic offset cancels.</li>
 * <li><b>NOx &mdash; emission factor with optional thermal scaling and route breakdown.</b> Baseline factors are
 * EMEP/EEA and US EPA AP-42 style values on fuel LHV energy. The optional thermal scaling is the extended-Zeldovich
 * temperature dependence {@code exp(-Ta/Tflame)} with an activation temperature Ta&asymp;38000 K (from the
 * O+N2&rarr;NO+N step, Ea&asymp;319 kJ/mol; Turns; Lefebvre, <i>Gas Turbine Combustion</i>). The additive <i>prompt</i>
 * (Fenimore) and <i>fuel-bound-N</i> routes are available as separate factors
 * ({@link #setPromptNoxFactorGPerGJ(double)} / {@link #setFuelNoxFactorGPerGJ(double)}) and the result carries the
 * thermal/prompt/fuel breakdown; the <i>N2O-intermediate</i> route is not resolved but N2O itself is reported as a
 * separate emission ({@link #setN2oFactorGPerGJ(double)}).</li>
 * <li><b>CO &mdash; emission factor with optional (screening) thermal scaling.</b> Real CO versus excess air is
 * U-shaped: high near stoichiometric (dissociation + O2 starvation), a minimum at moderate excess air, then rising
 * again at high excess air as the flame is quenched (Lefebvre). The optional inverse-Arrhenius scaling only captures
 * the lean/cold-quench branch and gives the wrong sign on a pure excess-air sweep &mdash; field-calibrate CO against
 * measured CO-versus-O2 data for load/lambda studies.</li>
 * <li><b>SO2 / SO3 &amp; acid dew point.</b> Fuel sulphur is emitted as SO2, with an optional SO3 fraction
 * ({@link #setSo3FractionOfSox(double)}, typically 1&ndash;5 %). When SO3 is present the result reports the
 * sulfuric-acid dew point (Verhoff-Banchero) and the flue-gas water dew point, which drive cold-end / stack
 * corrosion.</li>
 * <li><b>Other pollutants.</b> Particulate matter (PM), unburned methane (CH4 slip), non-methane VOC and N2O are
 * available as optional emission factors for liquid/dual-fuel, lean-premix and reciprocating-engine service (all 0 by
 * default, negligible for clean gas firing).</li>
 * <li><b>Reference-O2, dry basis, mg/Nm3 and stack conditions.</b> Concentrations are reported wet and dry, in ppmv and
 * mg/Nm3, and corrected to a reference dry-O2 basis with {@code C_ref = C_meas*(20.9-O2_ref)/(20.9-O2_meas)} on dry
 * values (EPA Method 19 / EN 14792 convention). Normalized flue-gas flow (Nm3/hr) and, when a stack-gas temperature and
 * diameter are supplied, the actual (stack-condition) volumetric flow (Am3/hr) and stack-exit velocity are also
 * returned for dispersion / plume screening.</li>
 * </ul>
 *
 * <p>
 * The one genuine physics limit that remains is high-temperature <i>dissociation</i> in the adiabatic flame temperature
 * (see above). For rigorous flame chemistry (dissociation, detailed NO formation, ignition/extinction) a
 * chemical-kinetics solver such as Cantera or CHEMKIN with a validated mechanism (e.g. GRI-Mech 3.0) is the appropriate
 * tool; this class is a fast, auditable engineering-screening model consistent with how emissions are estimated in
 * process simulators.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class CombustionCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Logger. */
  private static final Logger logger = LogManager.getLogger(CombustionCalculator.class);

  /** Dry-air molar composition (O2, N2, Ar, CO2). */
  private static final double AIR_O2 = 0.2095;
  private static final double AIR_N2 = 0.7808;
  private static final double AIR_AR = 0.0093;
  private static final double AIR_CO2 = 0.0004;

  /** Molar masses [g/mol] of flue-gas species. */
  private static final Map<String, Double> MW = new LinkedHashMap<>();
  /** Carbon / hydrogen / sulphur / oxygen atom counts per combustible component. */
  private static final Map<String, int[]> ATOMS = new LinkedHashMap<>();
  /** Net (lower) molar heating value [kJ/mol] per combustible component. */
  private static final Map<String, Double> LHV_MOL = new LinkedHashMap<>();

  static {
    MW.put("N2", 28.0134);
    MW.put("O2", 31.9988);
    MW.put("CO2", 44.0095);
    MW.put("H2O", 18.0153);
    MW.put("Ar", 39.948);
    MW.put("SO2", 64.066);
    MW.put("SO3", 80.0642);
    MW.put("NO2", 46.0055);
    MW.put("CO", 28.010);
    MW.put("CH4", 16.0425);
    MW.put("N2O", 44.0128);

    // ATOMS: {C, H, S, O}
    ATOMS.put("methane", new int[] { 1, 4, 0, 0 });
    ATOMS.put("ethane", new int[] { 2, 6, 0, 0 });
    ATOMS.put("propane", new int[] { 3, 8, 0, 0 });
    ATOMS.put("i-butane", new int[] { 4, 10, 0, 0 });
    ATOMS.put("n-butane", new int[] { 4, 10, 0, 0 });
    ATOMS.put("iso-butane", new int[] { 4, 10, 0, 0 });
    ATOMS.put("i-pentane", new int[] { 5, 12, 0, 0 });
    ATOMS.put("n-pentane", new int[] { 5, 12, 0, 0 });
    ATOMS.put("22-dim-C3", new int[] { 5, 12, 0, 0 });
    ATOMS.put("n-hexane", new int[] { 6, 14, 0, 0 });
    ATOMS.put("benzene", new int[] { 6, 6, 0, 0 });
    ATOMS.put("hydrogen", new int[] { 0, 2, 0, 0 });
    ATOMS.put("CO", new int[] { 1, 0, 0, 1 });
    ATOMS.put("H2S", new int[] { 0, 2, 1, 0 });

    // Net (lower) heating values [kJ/mol]
    LHV_MOL.put("methane", 802.3);
    LHV_MOL.put("ethane", 1428.6);
    LHV_MOL.put("propane", 2043.1);
    LHV_MOL.put("i-butane", 2649.0);
    LHV_MOL.put("n-butane", 2657.3);
    LHV_MOL.put("iso-butane", 2649.0);
    LHV_MOL.put("i-pentane", 3264.0);
    LHV_MOL.put("n-pentane", 3272.0);
    LHV_MOL.put("22-dim-C3", 3264.0);
    LHV_MOL.put("n-hexane", 3887.0);
    LHV_MOL.put("benzene", 3170.0);
    LHV_MOL.put("hydrogen", 241.8);
    LHV_MOL.put("CO", 283.0);
    LHV_MOL.put("H2S", 518.0);
  }

  /**
   * Selectable burner / combustion-technology database. Each entry carries typical, screening-level EMEP/EEA-style NOx
   * (as NO2) and CO emission factors on fuel lower-heating-value energy. These are indicative technology defaults, not
   * a substitute for a vendor guarantee or CEMS measurement; a low-NOx technology trades lower NOx for somewhat higher
   * CO. Natural-gas basis &mdash; scale up for hydrogen-rich or preheated-air firing.
   */
  public enum BurnerType {
    /** Conventional / standard diffusion burner (older fired heaters and boilers). */
    CONVENTIONAL(130.0, 30.0, "Conventional diffusion burner"),
    /** Low-NOx burner (staged air/fuel, internal flue-gas recirculation). */
    LOW_NOX(60.0, 40.0, "Low-NOx burner (air/fuel staging)"),
    /** Ultra-low-NOx burner (staging + external flue-gas recirculation). */
    ULTRA_LOW_NOX(30.0, 50.0, "Ultra-low-NOx burner (staging + FGR)"),
    /** Gas-turbine conventional (diffusion) combustor. */
    GAS_TURBINE_CONVENTIONAL(300.0, 40.0, "Gas turbine, conventional combustor"),
    /** Gas-turbine dry-low-emission / dry-low-NOx (lean-premix) combustor. */
    GAS_TURBINE_DLE(90.0, 30.0, "Gas turbine, dry-low-emission (lean premix)"),
    /** Gas-turbine with water or steam injection for NOx control. */
    GAS_TURBINE_WET(130.0, 60.0, "Gas turbine, water/steam injection");

    private final double noxGPerGJ;
    private final double coGPerGJ;
    private final String description;

    BurnerType(double noxGPerGJ, double coGPerGJ, String description) {
      this.noxGPerGJ = noxGPerGJ;
      this.coGPerGJ = coGPerGJ;
      this.description = description;
    }

    /**
     * Typical NOx emission factor (as NO2) for this burner technology.
     *
     * @return NOx factor [g NO2 per GJ fuel LHV]
     */
    public double getNoxGPerGJ() {
      return noxGPerGJ;
    }

    /**
     * Typical CO emission factor for this burner technology.
     *
     * @return CO factor [g CO per GJ fuel LHV]
     */
    public double getCoGPerGJ() {
      return coGPerGJ;
    }

    /**
     * Human-readable description of the burner technology.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }
  }

  /** Fuel fluid (composition source). */
  private final SystemInterface fuel;
  /** Fuel mass flow [kg/hr]. */
  private double fuelFlowKgPerHr = 1000.0;
  /** Excess-air ratio lambda (actual air / stoichiometric air). GT ~3-3.5. */
  private double excessAirRatio = 1.15;
  /**
   * Fixed combustion-air mass flow [kg/hr]. When set (not NaN and &gt; 0) it puts the calculator in air-driven mode:
   * the excess-air ratio is derived from this air flow, the fuel rate and the fuel composition instead of being
   * imposed, so lambda floats down (leaner) as the fuel rate is reduced and shifts with fuel heaviness. NaN = classic
   * lambda-driven mode.
   */
  private double airFlowKgPerHr = Double.NaN;
  /** NOx emission factor (as NO2) [g per GJ fuel LHV]. */
  private double noxFactorGPerGJ = 130.0;
  /** CO emission factor [g per GJ fuel LHV]. */
  private double coFactorGPerGJ = 30.0;
  /** Assumed fuel-gas H2S [ppmv] used only when the fuel carries no sulphur. */
  private double assumedFuelH2sPpmv = 0.0;
  /** Selected burner technology (null = caller-set factors only). */
  private BurnerType burnerType = null;
  /** Multiplicative field-calibration on the NOx emission factor (1.0 = uncalibrated). */
  private double noxCalibrationFactor = 1.0;
  /** Multiplicative field-calibration on the CO emission factor (1.0 = uncalibrated). */
  private double coCalibrationFactor = 1.0;
  /** Reference dry-O2 basis [vol%] for reporting corrected pollutant ppmv (NaN = not reported). */
  private double referenceO2VolPercentDry = Double.NaN;
  /** Normal (reference) temperature [degC] for mg/Nm3 and Nm3/hr stack-emission reporting (0 = EU Normal, 273.15 K). */
  private double normalTemperatureC = 0.0;
  /** Annual operating hours used for the tonnes/year emission roll-up (default 8760 = continuous). */
  private double annualOperatingHours = 8760.0;
  /** Fraction of total fuel-sulphur oxides that leaves as SO3 rather than SO2 (0 = all SO2; typical 0.01-0.05). */
  private double so3FractionOfSox = 0.0;
  /** Particulate-matter (PM) emission factor [g per GJ fuel LHV] (0 = clean gas, no PM reported). */
  private double pmFactorGPerGJ = 0.0;
  /** Unburned-methane (CH4 slip) emission factor [g per GJ fuel LHV] (0 = none reported). */
  private double ch4SlipFactorGPerGJ = 0.0;
  /** Non-methane VOC (unburned hydrocarbon) emission factor [g per GJ fuel LHV] (0 = none reported). */
  private double vocFactorGPerGJ = 0.0;
  /** Nitrous-oxide (N2O) emission factor [g per GJ fuel LHV] (0 = none reported). */
  private double n2oFactorGPerGJ = 0.0;
  /** Additive prompt-NOx (Fenimore) emission factor [g NO2 per GJ fuel LHV] (0 = not added). */
  private double promptNoxFactorGPerGJ = 0.0;
  /** Additive fuel-bound-nitrogen NOx emission factor [g NO2 per GJ fuel LHV] (0 = not added). */
  private double fuelNoxFactorGPerGJ = 0.0;
  /** Actual flue-gas temperature at the stack exit [degC] (NaN = not modelled; adiabatic flame T is NOT stack T). */
  private double stackGasTemperatureC = Double.NaN;
  /** Stack (flue-gas) pressure [bara] used for actual-condition and dew-point calculations. */
  private double stackPressureBara = 1.01325;
  /** Internal stack diameter [m] for the stack-exit velocity (NaN = velocity not reported). */
  private double stackDiameterM = Double.NaN;
  /** Enable Zeldovich thermal-NOx scaling of the NOx factor with adiabatic flame temperature. */
  private boolean thermalNoxScaling = false;
  /** Reference adiabatic flame temperature [K] at which the NOx factor was defined/calibrated. */
  private double thermalNoxReferenceFlameTempK = Double.NaN;
  /** Zeldovich activation temperature [K] controlling thermal-NOx sensitivity (default 38000 K). */
  private double thermalNoxActivationTempK = 38000.0;
  /** Enable thermal-CO scaling of the CO factor with adiabatic flame temperature (inverse of NOx). */
  private boolean thermalCoScaling = false;
  /** Reference adiabatic flame temperature [K] at which the CO factor was defined/calibrated. */
  private double thermalCoReferenceFlameTempK = Double.NaN;
  /** Activation temperature [K] controlling thermal-CO sensitivity (default 12000 K). */
  private double thermalCoActivationTempK = 12000.0;

  /**
   * Constructor.
   *
   * @param fuel the fuel-gas fluid whose composition drives the combustion stoichiometry (must be non-null and contain
   * at least one combustible component)
   */
  public CombustionCalculator(SystemInterface fuel) {
    if (fuel == null) {
      throw new IllegalArgumentException("fuel must not be null");
    }
    this.fuel = fuel;
  }

  /**
   * Set the fuel mass flow.
   *
   * @param kgPerHr fuel mass flow [kg/hr], must be &gt; 0
   * @return this calculator for chaining
   */
  public CombustionCalculator setFuelFlowRate(double kgPerHr) {
    this.fuelFlowKgPerHr = kgPerHr;
    return this;
  }

  /**
   * Set the excess-air ratio lambda (actual air / stoichiometric air). Gas turbines run rich in air (lambda ~3-3.5)
   * giving ~14-15 vol% exhaust O2; a burner near-stoichiometric uses lambda ~1.05-1.2.
   *
   * @param lambda excess-air ratio, must be &ge; 1.0
   * @return this calculator for chaining
   */
  public CombustionCalculator setExcessAirRatio(double lambda) {
    this.excessAirRatio = lambda;
    return this;
  }

  /**
   * Put the calculator in air-driven mode by fixing the combustion-air mass flow. In this mode the excess-air ratio
   * lambda is no longer imposed but computed from this air flow, the fuel mass flow and the fuel composition, so lambda
   * floats leaner as the fuel rate is reduced and shifts as the fuel gets heavier (a heavier gas needs more air per
   * kg). This is the mode to use for a minimum-fuel-at-fixed-air turndown study within CO/NOx limits. Pass
   * {@link Double#NaN} (or a non-positive value) to return to classic lambda-driven mode. When the resulting lambda is
   * below 1.0 the point is air-starved (sub-stoichiometric); the complete-combustion balance no longer holds there and
   * the result flags it via {@code subStoichiometric}.
   *
   * @param kgPerHr fixed combustion-air mass flow [kg/hr], or NaN/non-positive to disable air-driven mode
   * @return this calculator for chaining
   */
  public CombustionCalculator setAirFlowRate(double kgPerHr) {
    this.airFlowKgPerHr = kgPerHr;
    return this;
  }

  /**
   * Set the NOx emission factor (expressed as NO2) on fuel lower-heating-value energy input.
   *
   * @param gPerGJ NOx factor [g NO2 per GJ fuel LHV]
   * @return this calculator for chaining
   */
  public CombustionCalculator setNoxFactorGPerGJ(double gPerGJ) {
    this.noxFactorGPerGJ = gPerGJ;
    return this;
  }

  /**
   * Set the CO emission factor on fuel lower-heating-value energy input.
   *
   * @param gPerGJ CO factor [g CO per GJ fuel LHV]
   * @return this calculator for chaining
   */
  public CombustionCalculator setCoFactorGPerGJ(double gPerGJ) {
    this.coFactorGPerGJ = gPerGJ;
    return this;
  }

  /**
   * Select a burner technology from the built-in database, which sets the NOx and CO emission factors to that
   * technology's typical values. Call this before {@link #setNoxFactorGPerGJ(double)} /
   * {@link #setCoFactorGPerGJ(double)} if you want to override one of them with a vendor guarantee or CEMS value
   * afterwards.
   *
   * @param type burner technology (e.g. {@link BurnerType#LOW_NOX})
   * @return this calculator for chaining
   */
  public CombustionCalculator setBurnerType(BurnerType type) {
    this.burnerType = type;
    if (type != null) {
      this.noxFactorGPerGJ = type.getNoxGPerGJ();
      this.coFactorGPerGJ = type.getCoGPerGJ();
    }
    return this;
  }

  /**
   * Get the selected burner technology.
   *
   * @return burner type, or null if none selected
   */
  public BurnerType getBurnerType() {
    return burnerType;
  }

  /**
   * Correct a dry-basis pollutant concentration measured at one exhaust O2 level to a reference O2 level, using the
   * standard air-dilution correction {@code C_ref = C_meas * (20.9 - O2_ref) / (20.9 - O2_meas)}. This lets a measured
   * value (usually reported at a reference O2 on the datasheet) be compared with a prediction at a different O2.
   *
   * @param valueAtMeasuredO2 concentration at the measured O2 (e.g. ppmv, dry)
   * @param measuredO2VolPercent measured dry exhaust O2 [vol%], must be &lt; 20.9
   * @param referenceO2VolPercent reference dry O2 [vol%] (3 for fired heaters, 15 for gas turbines)
   * @return concentration corrected to the reference O2 basis
   */
  public static double correctToReferenceO2(double valueAtMeasuredO2, double measuredO2VolPercent,
      double referenceO2VolPercent) {
    double denom = 20.9 - measuredO2VolPercent;
    if (denom <= 0.0) {
      return valueAtMeasuredO2;
    }
    return valueAtMeasuredO2 * (20.9 - referenceO2VolPercent) / denom;
  }

  /**
   * Set the reference dry-O2 basis for reporting O2-corrected pollutant ppmv in the result
   * ({@code pollutantPpmvAtReferenceO2}). Use 3 vol% for fired heaters/boilers, 15 vol% for gas turbines.
   *
   * @param o2VolPercent reference dry O2 [vol%]
   * @return this calculator for chaining
   */
  public CombustionCalculator setReferenceO2VolPercent(double o2VolPercent) {
    this.referenceO2VolPercentDry = o2VolPercent;
    return this;
  }

  /**
   * Set the normal (reference) temperature for stack-emission reporting of mg/Nm3 concentrations and normalized
   * volumetric flue-gas flow (Nm3/hr). The reference pressure is always 101.325 kPa. Use 0 degC for the EU "Normal"
   * basis (273.15 K, the basis for IED / EN 14792 / EN 15058 / EN 14791 limits in mg/Nm3) or 15/25 degC for other
   * "standard" bases. Default 0 degC.
   *
   * @param temperatureC normal reference temperature [degC]
   * @return this calculator for chaining
   */
  public CombustionCalculator setNormalTemperatureC(double temperatureC) {
    this.normalTemperatureC = temperatureC;
    return this;
  }

  /**
   * Set the annual operating hours used to roll the pollutant mass rates up to tonnes/year (permit / annual-report
   * basis). Default 8760 h (continuous).
   *
   * @param hours annual operating hours [h/yr]
   * @return this calculator for chaining
   */
  public CombustionCalculator setAnnualOperatingHours(double hours) {
    this.annualOperatingHours = hours;
    return this;
  }

  /**
   * Set the fraction of the fuel-sulphur oxides that leaves the flame as SO3 rather than SO2 (typically 1-5 % for
   * gas/oil firing). When &gt; 0 the result reports SO3, the SO2/SO3 split, and the sulfuric-acid dew point (via the
   * Verhoff-Banchero correlation) in addition to the water dew point, which drives cold-end / stack corrosion. Default
   * 0 (all sulphur as SO2).
   *
   * @param fraction SO3 fraction of total sulphur oxides (0-1)
   * @return this calculator for chaining
   */
  public CombustionCalculator setSo3FractionOfSox(double fraction) {
    this.so3FractionOfSox = fraction;
    return this;
  }

  /**
   * Set the particulate-matter (PM) emission factor on fuel LHV energy. Negligible for clean gas firing but relevant
   * for liquid / dual-fuel or lean-blowout; default 0 (no PM reported).
   *
   * @param gPerGJ PM factor [g per GJ fuel LHV]
   * @return this calculator for chaining
   */
  public CombustionCalculator setPmFactorGPerGJ(double gPerGJ) {
    this.pmFactorGPerGJ = gPerGJ;
    return this;
  }

  /**
   * Set the unburned-methane (CH4 slip) emission factor on fuel LHV energy. Relevant for lean-premix gas turbines and
   * reciprocating gas engines; default 0 (no CH4 slip reported).
   *
   * @param gPerGJ CH4 slip factor [g CH4 per GJ fuel LHV]
   * @return this calculator for chaining
   */
  public CombustionCalculator setCh4SlipFactorGPerGJ(double gPerGJ) {
    this.ch4SlipFactorGPerGJ = gPerGJ;
    return this;
  }

  /**
   * Set the non-methane VOC (unburned hydrocarbon) emission factor on fuel LHV energy. Default 0 (no VOC reported).
   *
   * @param gPerGJ VOC factor [g per GJ fuel LHV]
   * @return this calculator for chaining
   */
  public CombustionCalculator setVocFactorGPerGJ(double gPerGJ) {
    this.vocFactorGPerGJ = gPerGJ;
    return this;
  }

  /**
   * Set the nitrous-oxide (N2O) emission factor on fuel LHV energy. N2O is a strong greenhouse gas favoured by lean,
   * low-temperature, high-pressure premix combustion; default 0 (no N2O reported).
   *
   * @param gPerGJ N2O factor [g N2O per GJ fuel LHV]
   * @return this calculator for chaining
   */
  public CombustionCalculator setN2oFactorGPerGJ(double gPerGJ) {
    this.n2oFactorGPerGJ = gPerGJ;
    return this;
  }

  /**
   * Set an additive prompt-NOx (Fenimore) emission factor on fuel LHV energy. Prompt NOx forms in the fuel-rich flame
   * front from CH radicals and matters most at low flame temperature where the thermal route is weak; default 0.
   *
   * @param gPerGJ prompt-NOx factor [g NO2 per GJ fuel LHV]
   * @return this calculator for chaining
   */
  public CombustionCalculator setPromptNoxFactorGPerGJ(double gPerGJ) {
    this.promptNoxFactorGPerGJ = gPerGJ;
    return this;
  }

  /**
   * Set an additive fuel-bound-nitrogen NOx emission factor on fuel LHV energy. Fuel NOx forms from nitrogen chemically
   * bound in the fuel (e.g. NH3, organic-N) and is important for liquid/solid fuels and N-bearing gases; negligible for
   * clean natural gas; default 0.
   *
   * @param gPerGJ fuel-NOx factor [g NO2 per GJ fuel LHV]
   * @return this calculator for chaining
   */
  public CombustionCalculator setFuelNoxFactorGPerGJ(double gPerGJ) {
    this.fuelNoxFactorGPerGJ = gPerGJ;
    return this;
  }

  /**
   * Set the actual flue-gas temperature at the stack exit. This is NOT the adiabatic flame temperature (which ignores
   * heat recovery and stack heat loss); supply the measured or process-model stack-gas temperature. When set, the
   * result reports the actual (stack-condition) volumetric flow and, if {@link #setStackDiameterM(double)} is also set,
   * the stack-exit velocity for dispersion / plume screening.
   *
   * @param temperatureC stack-exit flue-gas temperature [degC]
   * @return this calculator for chaining
   */
  public CombustionCalculator setStackGasTemperatureC(double temperatureC) {
    this.stackGasTemperatureC = temperatureC;
    return this;
  }

  /**
   * Set the stack (flue-gas) pressure used for actual-condition volumetric flow and for the acid / water dew-point
   * partial pressures. Default 1.01325 bara.
   *
   * @param bara stack pressure [bara]
   * @return this calculator for chaining
   */
  public CombustionCalculator setStackPressureBara(double bara) {
    this.stackPressureBara = bara;
    return this;
  }

  /**
   * Set the internal stack diameter used to convert the actual volumetric flow into a stack-exit velocity. Requires
   * {@link #setStackGasTemperatureC(double)} to be set; default NaN (velocity not reported).
   *
   * @param diameterM internal stack diameter [m]
   * @return this calculator for chaining
   */
  public CombustionCalculator setStackDiameterM(double diameterM) {
    this.stackDiameterM = diameterM;
    return this;
  }

  /**
   * Set a multiplicative field-calibration factor on the NOx emission factor (1.0 = model default). A value from a
   * measurement removes the model's systematic bias so predictions at other loads scale correctly on an energy basis.
   *
   * @param factor NOx calibration multiplier (&gt; 0)
   * @return this calculator for chaining
   */
  public CombustionCalculator setNoxCalibrationFactor(double factor) {
    this.noxCalibrationFactor = factor;
    return this;
  }

  /**
   * Set a multiplicative field-calibration factor on the CO emission factor (1.0 = model default).
   *
   * @param factor CO calibration multiplier (&gt; 0)
   * @return this calculator for chaining
   */
  public CombustionCalculator setCoCalibrationFactor(double factor) {
    this.coCalibrationFactor = factor;
    return this;
  }

  /**
   * Calibrate the CO emission factor from a single field measurement (CEMS / stack test): the measured CO ppmv at the
   * measured exhaust O2 is compared with the current uncalibrated prediction (corrected to the same O2), and a
   * multiplicative calibration factor is stored so subsequent {@link #calculate()} calls reproduce the measurement and
   * scale it to other loads on a fuel-energy basis.
   *
   * @param measuredCoPpmv measured CO [ppmv, dry]
   * @param measuredExhaustO2VolPercent measured dry exhaust O2 [vol%]
   * @return this calculator for chaining
   */
  public CombustionCalculator calibrateCoFromMeasuredPpmv(double measuredCoPpmv, double measuredExhaustO2VolPercent) {
    this.coCalibrationFactor = 1.0;
    CombustionResult base = calculate();
    double predAtMeasO2 = correctToReferenceO2(base.pollutantPpmv.get("CO"), base.exhaustO2VolPercent,
        measuredExhaustO2VolPercent);
    if (predAtMeasO2 > 0.0) {
      this.coCalibrationFactor = measuredCoPpmv / predAtMeasO2;
    }
    return this;
  }

  /**
   * Calibrate the NOx emission factor from a single field measurement, analogous to
   * {@link #calibrateCoFromMeasuredPpmv(double, double)}.
   *
   * @param measuredNoxPpmv measured NOx (as NO2) [ppmv, dry]
   * @param measuredExhaustO2VolPercent measured dry exhaust O2 [vol%]
   * @return this calculator for chaining
   */
  public CombustionCalculator calibrateNoxFromMeasuredPpmv(double measuredNoxPpmv, double measuredExhaustO2VolPercent) {
    this.noxCalibrationFactor = 1.0;
    CombustionResult base = calculate();
    double predAtMeasO2 = correctToReferenceO2(base.pollutantPpmv.get("NOx"), base.exhaustO2VolPercent,
        measuredExhaustO2VolPercent);
    if (predAtMeasO2 > 0.0) {
      this.noxCalibrationFactor = measuredNoxPpmv / predAtMeasO2;
    }
    return this;
  }

  /**
   * Get the current CO field-calibration factor.
   *
   * @return CO calibration multiplier
   */
  public double getCoCalibrationFactor() {
    return coCalibrationFactor;
  }

  /**
   * Get the current NOx field-calibration factor.
   *
   * @return NOx calibration multiplier
   */
  public double getNoxCalibrationFactor() {
    return noxCalibrationFactor;
  }

  /**
   * Enable Zeldovich thermal-NOx scaling so that the NOx emission factor rises and falls with the computed adiabatic
   * flame temperature. This makes NOx respond to fuel composition (a heavier or more hydrogen-rich gas burns hotter),
   * combustion-air preheat, and excess air, not just to the fuel energy. The scaling is a screening Arrhenius form
   * {@code exp(-Ta/Tflame) / exp(-Ta/Tref)} with a tunable activation temperature Ta; at the reference flame
   * temperature the factor is unchanged, so a burner-type or field-calibrated factor is preserved at the design point.
   * Set the reference to the adiabatic flame temperature computed at your design / calibration condition. Disabled by
   * default; it is a screening sensitivity, not a validated NOx kinetics model.
   *
   * @param referenceFlameTempK adiabatic flame temperature [K] at the design/calibration condition
   * @return this calculator for chaining
   */
  public CombustionCalculator enableThermalNoxScaling(double referenceFlameTempK) {
    this.thermalNoxScaling = true;
    this.thermalNoxReferenceFlameTempK = referenceFlameTempK;
    return this;
  }

  /**
   * Set the Zeldovich activation temperature that controls how strongly thermal NOx scaling responds to flame
   * temperature (higher = more sensitive). Only used when thermal-NOx scaling is enabled. Default 38000 K.
   *
   * @param activationTempK activation temperature [K]
   * @return this calculator for chaining
   */
  public CombustionCalculator setThermalNoxActivationTempK(double activationTempK) {
    this.thermalNoxActivationTempK = activationTempK;
    return this;
  }

  /**
   * Enable thermal-CO scaling so that the CO emission factor responds to the computed adiabatic flame temperature with
   * the opposite trend to NOx: a hotter, more complete flame (a heavier or more hydrogen-rich fuel, air preheat, or
   * tighter excess air) oxidises CO more fully and lowers it, while a cooler flame raises it. The scaling is a
   * screening inverse-Arrhenius form {@code exp(Ta/Tflame) / exp(Ta/Tref)}; at the reference flame temperature the
   * factor is unchanged, so a burner-type or field-calibrated CO factor is preserved at the design point.
   *
   * <p>
   * <b>WARNING — do NOT use this for an excess-air (lambda) sweep.</b> The real driver of CO versus excess air is
   * oxygen availability / combustion completeness, which follows a U-shaped curve (CO high near stoichiometric, a
   * minimum at moderate excess air, then rising again at very high excess air as the flame is quenched). This
   * flame-temperature scaling captures neither the minimum nor the correct sign: adding excess air lowers the flame
   * temperature and this method would then predict CO <i>increasing</i>, which is the wrong direction over the normal
   * operating range. It is only intended as a fuel-composition / air-preheat sensitivity at roughly constant lambda.
   * For a CO limit (e.g. 200 ppm) study across excess air, use measured / vendor CO-versus-O2 data (field calibration
   * per operating point via {@link #calibrateCoFromMeasuredPpmv(double, double)}) and compare against the limit on the
   * reference-O2 basis via {@link #setReferenceO2VolPercent(double)} — not this scaling.
   * </p>
   *
   * @param referenceFlameTempK adiabatic flame temperature [K] at the design/calibration condition
   * @return this calculator for chaining
   */
  public CombustionCalculator enableThermalCoScaling(double referenceFlameTempK) {
    this.thermalCoScaling = true;
    this.thermalCoReferenceFlameTempK = referenceFlameTempK;
    return this;
  }

  /**
   * Set the activation temperature that controls how strongly thermal-CO scaling responds to flame temperature (higher
   * = more sensitive). Only used when thermal-CO scaling is enabled. Default 12000 K.
   *
   * @param activationTempK activation temperature [K]
   * @return this calculator for chaining
   */
  public CombustionCalculator setThermalCoActivationTempK(double activationTempK) {
    this.thermalCoActivationTempK = activationTempK;
    return this;
  }

  /**
   * Set an assumed fuel-gas H2S content, used only when the fuel fluid carries no sulphur species, so a screening SO2
   * rate can still be reported.
   *
   * @param ppmv assumed fuel H2S [ppmv]
   * @return this calculator for chaining
   */
  public CombustionCalculator setAssumedFuelH2sPpmv(double ppmv) {
    this.assumedFuelH2sPpmv = ppmv;
    return this;
  }

  /**
   * Atom counts {C, H, S, O} for a fuel component, using the internal table first, then the NeqSim element database,
   * then a paraffin (CnH(2n+2)) fallback from the carbon count.
   *
   * @param name NeqSim component name
   * @param comp the component (for element-database lookup)
   * @return an int array {C, H, S, O}
   */
  private static int[] atomsOf(String name, neqsim.thermo.component.ComponentInterface comp) {
    int[] a = ATOMS.get(name);
    if (a != null) {
      return a;
    }
    int c = 0;
    int h = 0;
    int s = 0;
    int o = 0;
    try {
      c = (int) Math.round(comp.getElements().getNumberOfElements("C"));
      h = (int) Math.round(comp.getElements().getNumberOfElements("H"));
      s = (int) Math.round(comp.getElements().getNumberOfElements("S"));
      o = (int) Math.round(comp.getElements().getNumberOfElements("O"));
    } catch (Exception ex) {
      logger.debug("element lookup failed for {}: {}", name, ex.getMessage());
    }
    if (c > 0 && h == 0) {
      h = 2 * c + 2; // paraffin fallback
    }
    return new int[] { c, h, s, o };
  }

  /**
   * Run the combustion calculation and return a full flue-gas result.
   *
   * @return a {@link CombustionResult} with exhaust composition, air/fuel ratio, pollutant rates and adiabatic flame
   * temperature
   */
  public CombustionResult calculate() {
    SystemInterface f = fuel.clone();
    f.init(0);
    double mmFuelKgPerMol = f.getMolarMass();

    // per-mol-of-fuel balances
    double o2Demand = 0.0;
    double co2P = 0.0;
    double h2oP = 0.0;
    double so2P = 0.0;
    double n2Fuel = 0.0;
    double arFuel = 0.0;
    double o2Fuel = 0.0;
    double lhvPerMolFuel = 0.0;

    for (int i = 0; i < f.getNumberOfComponents(); i++) {
      neqsim.thermo.component.ComponentInterface comp = f.getComponent(i);
      String nm = comp.getComponentName();
      double z = comp.getz();
      if ("CO2".equals(nm)) {
        co2P += z;
        continue;
      }
      if ("nitrogen".equals(nm)) {
        n2Fuel += z;
        continue;
      }
      if ("argon".equals(nm)) {
        arFuel += z;
        continue;
      }
      if ("water".equals(nm)) {
        h2oP += z;
        continue;
      }
      if ("helium".equals(nm)) {
        continue;
      }
      if ("oxygen".equals(nm)) {
        o2Fuel += z;
        continue;
      }
      int[] a = atomsOf(nm, comp);
      int c = a[0];
      int h = a[1];
      int s = a[2];
      int o = a[3];
      o2Demand += z * (c + h / 4.0 + s - o / 2.0);
      co2P += z * c;
      h2oP += z * (h / 2.0);
      so2P += z * s;
      Double lhv = LHV_MOL.get(nm);
      if (lhv != null) {
        lhvPerMolFuel += z * lhv;
      }
    }

    String h2sBasis = "from fuel composition";
    if (so2P <= 0.0 && assumedFuelH2sPpmv > 0.0) {
      double zs = assumedFuelH2sPpmv * 1.0e-6;
      o2Demand += zs * 1.5;
      h2oP += zs;
      so2P += zs;
      lhvPerMolFuel += zs * LHV_MOL.get("H2S");
      h2sBasis = "assumed " + assumedFuelH2sPpmv + " ppmv (no sulphur in fuel)";
    }
    o2Demand = Math.max(o2Demand - o2Fuel, 1.0e-12);

    // Effective excess-air ratio: either the user-set lambda, or (air-driven mode) derived from a fixed combustion-air
    // mass flow so lambda floats with fuel rate and fuel composition.
    double airMwGPerMol = AIR_O2 * MW.get("O2") + AIR_N2 * MW.get("N2") + AIR_AR * MW.get("Ar")
        + AIR_CO2 * MW.get("CO2");
    double effectiveLambda = excessAirRatio;
    boolean airDriven = !Double.isNaN(airFlowKgPerHr) && airFlowKgPerHr > 0.0;
    if (airDriven) {
      double nFuelHrForAir = fuelFlowKgPerHr / mmFuelKgPerMol;
      double stoichAirMolPerHr = (o2Demand / AIR_O2) * nFuelHrForAir;
      double airMolPerHr = airFlowKgPerHr / (airMwGPerMol / 1000.0);
      if (stoichAirMolPerHr > 0.0) {
        effectiveLambda = airMolPerHr / stoichAirMolPerHr;
      }
    }

    // combustion air and flue (per mol fuel)
    double airO2 = effectiveLambda * o2Demand;
    double airTot = airO2 / AIR_O2;
    double o2Flue = airO2 - o2Demand;
    double n2Flue = n2Fuel + airTot * AIR_N2;
    double arFlue = arFuel + airTot * AIR_AR;
    double co2Flue = co2P + airTot * AIR_CO2;
    double h2oFlue = h2oP;
    // Split total sulphur oxides into SO2 and SO3 (SO3 drives the acid dew point / cold-end corrosion).
    double so3Flue = so2P * so3FractionOfSox;
    double so2Flue = so2P - so3Flue;
    double tot = o2Flue + n2Flue + arFlue + co2Flue + h2oFlue + so2Flue + so3Flue;

    // scale to actual combusted fuel molar rate
    double nFuelHr = fuelFlowKgPerHr / mmFuelKgPerMol; // mol/hr
    double totMolHr = tot * nFuelHr;

    // NOx / CO from emission factors on fuel LHV energy
    // LHV_MOL is kJ/mol and the molar mass is kg/mol, so kJ/mol / kg/mol = kJ/kg.
    double lhvKJperKg = lhvPerMolFuel / mmFuelKgPerMol;
    double gjPerHr = fuelFlowKgPerHr * lhvKJperKg / 1.0e6;

    // Adiabatic flame temperature (also used for optional thermal-NOx scaling below).
    double flameTempK = adiabaticFlameTemperature(n2Flue, o2Flue, co2Flue, h2oFlue, arFlue, lhvPerMolFuel);
    double thermalNoxFactor = 1.0;
    if (thermalNoxScaling && !Double.isNaN(thermalNoxReferenceFlameTempK) && !Double.isNaN(flameTempK)
        && flameTempK > 0.0 && thermalNoxReferenceFlameTempK > 0.0) {
      thermalNoxFactor = Math.exp(-thermalNoxActivationTempK / flameTempK)
          / Math.exp(-thermalNoxActivationTempK / thermalNoxReferenceFlameTempK);
    }
    double thermalCoFactor = 1.0;
    if (thermalCoScaling && !Double.isNaN(thermalCoReferenceFlameTempK) && !Double.isNaN(flameTempK) && flameTempK > 0.0
        && thermalCoReferenceFlameTempK > 0.0) {
      thermalCoFactor = Math.exp(thermalCoActivationTempK / flameTempK)
          / Math.exp(thermalCoActivationTempK / thermalCoReferenceFlameTempK);
    }

    double noxKgHr = noxFactorGPerGJ * noxCalibrationFactor * thermalNoxFactor * gjPerHr / 1000.0;
    double coKgHr = coFactorGPerGJ * coCalibrationFactor * thermalCoFactor * gjPerHr / 1000.0;
    // NOx route breakdown: base factor = thermal (Zeldovich, optionally scaled); prompt and fuel-N are additive.
    double noxThermalKgHr = noxKgHr;
    double noxPromptKgHr = promptNoxFactorGPerGJ * gjPerHr / 1000.0;
    double noxFuelKgHr = fuelNoxFactorGPerGJ * gjPerHr / 1000.0;
    noxKgHr = noxThermalKgHr + noxPromptKgHr + noxFuelKgHr;
    double noxMolHr = noxKgHr * 1000.0 / MW.get("NO2");
    double coMolHr = coKgHr * 1000.0 / MW.get("CO");
    // Extra pollutant mass rates from emission factors (kg/hr); 0 when the factor is unset.
    double pmKgHr = pmFactorGPerGJ * gjPerHr / 1000.0;
    double ch4KgHr = ch4SlipFactorGPerGJ * gjPerHr / 1000.0;
    double vocKgHr = vocFactorGPerGJ * gjPerHr / 1000.0;
    double n2oKgHr = n2oFactorGPerGJ * gjPerHr / 1000.0;
    double ch4MolHr = ch4KgHr * 1000.0 / MW.get("CH4");
    double n2oMolHr = n2oKgHr * 1000.0 / MW.get("N2O");

    CombustionResult r = new CombustionResult();
    r.excessAirRatio = effectiveLambda;
    r.airDriven = airDriven;
    r.airFlowKgPerHr = airDriven ? airFlowKgPerHr : Double.NaN;
    r.subStoichiometric = effectiveLambda < 1.0;
    r.fuelFlowKgPerHr = fuelFlowKgPerHr;
    r.fuelH2sBasis = h2sBasis;
    r.fuelLhvKJperKg = lhvKJperKg;
    r.fuelEnergyGJperHr = gjPerHr;

    // air/fuel mass ratios
    double stoichAirMolPerMolFuel = o2Demand / AIR_O2;
    r.stoichAirFuelMassRatio = stoichAirMolPerMolFuel * airMwGPerMol / (mmFuelKgPerMol * 1000.0);
    r.airFuelMassRatio = airTot * airMwGPerMol / (mmFuelKgPerMol * 1000.0);

    r.exhaustO2VolPercent = 100.0 * o2Flue / tot;
    double totDry = Math.max(tot - h2oFlue, 1.0e-12);
    r.exhaustO2VolPercentDry = 100.0 * o2Flue / totDry;
    r.waterVolPercent = 100.0 * h2oFlue / tot;
    r.flueMoleFraction = new LinkedHashMap<>();
    r.flueMoleFraction.put("N2", n2Flue / tot);
    r.flueMoleFraction.put("O2", o2Flue / tot);
    r.flueMoleFraction.put("CO2", co2Flue / tot);
    r.flueMoleFraction.put("H2O", h2oFlue / tot);
    r.flueMoleFraction.put("Ar", arFlue / tot);
    r.flueMoleFraction.put("SO2", so2Flue / tot);
    if (so3Flue > 0.0) {
      r.flueMoleFraction.put("SO3", so3Flue / tot);
    }

    // Wet-basis ppmv (as in the actual flue gas, water included).
    r.pollutantPpmv = new LinkedHashMap<>();
    r.pollutantPpmv.put("SO2", 1.0e6 * so2Flue / tot);
    r.pollutantPpmv.put("NOx", 1.0e6 * noxMolHr / totMolHr);
    r.pollutantPpmv.put("CO", 1.0e6 * coMolHr / totMolHr);
    if (so3Flue > 0.0) {
      r.pollutantPpmv.put("SO3", 1.0e6 * so3Flue / tot);
    }
    if (ch4KgHr > 0.0) {
      r.pollutantPpmv.put("CH4", 1.0e6 * ch4MolHr / totMolHr);
    }
    if (n2oKgHr > 0.0) {
      r.pollutantPpmv.put("N2O", 1.0e6 * n2oMolHr / totMolHr);
    }

    // Dry-basis ppmv (water removed). This is the basis on which stack CEMS report and on which
    // emission limits are written, so it is the correct basis for a compliance check.
    double wetToDry = tot / totDry;
    r.pollutantPpmvDry = new LinkedHashMap<>();
    for (Map.Entry<String, Double> e : r.pollutantPpmv.entrySet()) {
      r.pollutantPpmvDry.put(e.getKey(), e.getValue() * wetToDry);
    }

    if (!Double.isNaN(referenceO2VolPercentDry)) {
      r.referenceO2VolPercent = referenceO2VolPercentDry;
      r.pollutantPpmvAtReferenceO2 = new LinkedHashMap<>();
      // Correct the DRY concentration at the DRY exhaust O2 to the reference O2 (all dry) via the standard
      // air-dilution formula. The 20.9 % basis in that formula is dry-air O2, so both the concentration and the
      // measured O2 must be on a dry basis for the correction to be physically consistent.
      for (Map.Entry<String, Double> e : r.pollutantPpmvDry.entrySet()) {
        r.pollutantPpmvAtReferenceO2.put(e.getKey(),
            correctToReferenceO2(e.getValue(), r.exhaustO2VolPercentDry, referenceO2VolPercentDry));
      }
    }
    r.noxCalibrationFactor = noxCalibrationFactor;
    r.coCalibrationFactor = coCalibrationFactor;

    r.massRateKgPerHr = new LinkedHashMap<>();
    r.massRateKgPerHr.put("N2", n2Flue * nFuelHr * MW.get("N2") / 1000.0);
    r.massRateKgPerHr.put("O2", o2Flue * nFuelHr * MW.get("O2") / 1000.0);
    r.massRateKgPerHr.put("CO2", co2Flue * nFuelHr * MW.get("CO2") / 1000.0);
    r.massRateKgPerHr.put("H2O", h2oFlue * nFuelHr * MW.get("H2O") / 1000.0);
    r.massRateKgPerHr.put("Ar", arFlue * nFuelHr * MW.get("Ar") / 1000.0);
    r.massRateKgPerHr.put("SO2", so2Flue * nFuelHr * MW.get("SO2") / 1000.0);
    r.massRateKgPerHr.put("NOx_as_NO2", noxKgHr);
    r.massRateKgPerHr.put("CO", coKgHr);
    if (so3Flue > 0.0) {
      r.massRateKgPerHr.put("SO3", so3Flue * nFuelHr * MW.get("SO3") / 1000.0);
    }
    if (pmKgHr > 0.0) {
      r.massRateKgPerHr.put("PM", pmKgHr);
    }
    if (ch4KgHr > 0.0) {
      r.massRateKgPerHr.put("CH4", ch4KgHr);
    }
    if (vocKgHr > 0.0) {
      r.massRateKgPerHr.put("VOC", vocKgHr);
    }
    if (n2oKgHr > 0.0) {
      r.massRateKgPerHr.put("N2O", n2oKgHr);
    }
    // NOx route breakdown (all expressed as NO2).
    r.noxThermalKgPerHr = noxThermalKgHr;
    r.noxPromptKgPerHr = noxPromptKgHr;
    r.noxFuelKgPerHr = noxFuelKgHr;

    // ---- Stack-emission reporting outputs (EU IED / EN 14792 / EN 15058 / EN 14791 style) ----
    // Normalized molar volume at the reporting reference temperature and 101.325 kPa.
    double normalMolarVolumeM3PerMol = 8.314462618 * (273.15 + normalTemperatureC) / 101325.0;
    double dryMolHr = totDry * nFuelHr;
    r.normalTemperatureC = normalTemperatureC;
    r.flueGasNm3PerHrWet = totMolHr * normalMolarVolumeM3PerMol;
    r.flueGasNm3PerHrDry = dryMolHr * normalMolarVolumeM3PerMol;

    // mg/Nm3 (dry) and mg/Nm3 at the reference O2 (dry). NOx is expressed as NO2. The conversion is
    // mg/Nm3 = ppmv * MW / Vm * 1e-3, e.g. 1 ppmv NOx-as-NO2 ~ 2.05 mg/Nm3 at 0 degC.
    Map<String, Double> pollutantMw = new LinkedHashMap<>();
    pollutantMw.put("SO2", MW.get("SO2"));
    pollutantMw.put("NOx", MW.get("NO2"));
    pollutantMw.put("CO", MW.get("CO"));
    pollutantMw.put("SO3", MW.get("SO3"));
    pollutantMw.put("CH4", MW.get("CH4"));
    pollutantMw.put("N2O", MW.get("N2O"));
    r.pollutantMgPerNm3Dry = new LinkedHashMap<>();
    for (Map.Entry<String, Double> e : r.pollutantPpmvDry.entrySet()) {
      r.pollutantMgPerNm3Dry.put(e.getKey(),
          e.getValue() * pollutantMw.get(e.getKey()) / normalMolarVolumeM3PerMol * 1.0e-3);
    }
    // Mass-only species (PM, VOC) have no single molar mass: mg/Nm3(dry) = mass rate / dry normalized flow.
    double dryNm3Hr = Math.max(r.flueGasNm3PerHrDry, 1.0e-12);
    if (pmKgHr > 0.0) {
      r.pollutantMgPerNm3Dry.put("PM", pmKgHr * 1.0e6 / dryNm3Hr);
    }
    if (vocKgHr > 0.0) {
      r.pollutantMgPerNm3Dry.put("VOC", vocKgHr * 1.0e6 / dryNm3Hr);
    }
    if (r.pollutantPpmvAtReferenceO2 != null) {
      r.pollutantMgPerNm3AtReferenceO2 = new LinkedHashMap<>();
      for (Map.Entry<String, Double> e : r.pollutantPpmvAtReferenceO2.entrySet()) {
        r.pollutantMgPerNm3AtReferenceO2.put(e.getKey(),
            e.getValue() * pollutantMw.get(e.getKey()) / normalMolarVolumeM3PerMol * 1.0e-3);
      }
      // PM / VOC corrected to reference O2 on the mass-based dry mg/Nm3.
      if (pmKgHr > 0.0) {
        r.pollutantMgPerNm3AtReferenceO2.put("PM",
            correctToReferenceO2(r.pollutantMgPerNm3Dry.get("PM"), r.exhaustO2VolPercentDry, referenceO2VolPercentDry));
      }
      if (vocKgHr > 0.0) {
        r.pollutantMgPerNm3AtReferenceO2.put("VOC", correctToReferenceO2(r.pollutantMgPerNm3Dry.get("VOC"),
            r.exhaustO2VolPercentDry, referenceO2VolPercentDry));
      }
    }

    // Annual mass emissions [tonnes/yr] from the mass rates and the operating hours.
    r.annualOperatingHours = annualOperatingHours;
    r.massRateTonnesPerYear = new LinkedHashMap<>();
    for (Map.Entry<String, Double> e : r.massRateKgPerHr.entrySet()) {
      r.massRateTonnesPerYear.put(e.getKey(), e.getValue() * annualOperatingHours / 1000.0);
    }

    // ---- Actual stack-exit conditions (for dispersion / plume), when a stack-gas temperature is supplied. ----
    // The adiabatic flame temperature is NOT the stack temperature; the caller supplies the measured or
    // heat-recovery-outlet stack-gas temperature.
    r.stackGasTemperatureC = stackGasTemperatureC;
    r.stackPressureBara = stackPressureBara;
    if (!Double.isNaN(stackGasTemperatureC)) {
      double actualMolarVolumeM3PerMol = 8.314462618 * (273.15 + stackGasTemperatureC) / (stackPressureBara * 1.0e5);
      r.stackActualM3PerHr = totMolHr * actualMolarVolumeM3PerMol;
      if (!Double.isNaN(stackDiameterM) && stackDiameterM > 0.0) {
        double area = Math.PI / 4.0 * stackDiameterM * stackDiameterM;
        r.stackVelocityMPerS = r.stackActualM3PerHr / 3600.0 / area;
      }
    }

    // ---- Dew points (cold-end corrosion): water dew point always, sulfuric-acid dew point when SO3 is present. ----
    double pTotalMmHg = stackPressureBara * 760.0 / 1.01325;
    double pH2OmmHg = (h2oFlue / tot) * pTotalMmHg;
    r.waterDewPointC = waterDewPointC(pH2OmmHg);
    if (so3Flue > 0.0) {
      double pSO3mmHg = (so3Flue / tot) * pTotalMmHg;
      r.acidDewPointC = sulfuricAcidDewPointC(pH2OmmHg, pSO3mmHg);
    }

    double totalFlueKgHr = 0.0;
    for (String k : new String[] { "N2", "O2", "CO2", "H2O", "Ar", "SO2", "SO3" }) {
      Double m = r.massRateKgPerHr.get(k);
      if (m != null) {
        totalFlueKgHr += m;
      }
    }
    r.totalFlueKgPerHr = totalFlueKgHr;

    r.adiabaticFlameTemperatureK = adiabaticFlameTemperature(n2Flue, o2Flue, co2Flue, h2oFlue, arFlue, lhvPerMolFuel);
    return r;
  }

  /**
   * Rigorous adiabatic flame temperature by a NeqSim energy balance: the product mixture starts at 298.15 K and
   * receives all of the fuel lower-heating-value energy, then a PH-flash gives the flame temperature. Excess air lowers
   * the temperature because it is carried as inert dilution in the product mixture.
   *
   * @param n2 N2 mol per mol fuel
   * @param o2 O2 mol per mol fuel
   * @param co2 CO2 mol per mol fuel
   * @param h2o H2O mol per mol fuel
   * @param ar Ar mol per mol fuel
   * @param lhvPerMolFuel fuel net heating value [kJ/mol fuel]
   * @return adiabatic flame temperature [K], or {@link Double#NaN} if the flash does not converge
   */
  private double adiabaticFlameTemperature(double n2, double o2, double co2, double h2o, double ar,
      double lhvPerMolFuel) {
    try {
      SystemInterface prod = new SystemSrkEos(298.15, 1.01325);
      if (n2 > 0) {
        prod.addComponent("nitrogen", n2);
      }
      if (o2 > 0) {
        prod.addComponent("oxygen", o2);
      }
      if (co2 > 0) {
        prod.addComponent("CO2", co2);
      }
      if (h2o > 0) {
        prod.addComponent("water", h2o);
      }
      if (ar > 0) {
        prod.addComponent("argon", ar);
      }
      prod.setMixingRule("classic");
      ThermodynamicOperations ops = new ThermodynamicOperations(prod);
      prod.setTemperature(298.15);
      ops.TPflash();
      double h0 = prod.getEnthalpy(); // J for the per-mol-fuel product batch at 298.15 K
      double target = h0 + lhvPerMolFuel * 1000.0; // add the released heat (J per mol fuel)
      // bisection: enthalpy increases monotonically with temperature
      double lo = 300.0;
      double hi = 2800.0;
      for (int it = 0; it < 60; it++) {
        double mid = 0.5 * (lo + hi);
        prod.setTemperature(mid);
        ops.TPflash();
        if (prod.getEnthalpy() < target) {
          lo = mid;
        } else {
          hi = mid;
        }
      }
      return 0.5 * (lo + hi);
    } catch (Exception ex) {
      logger.debug("adiabatic flame temperature flash failed: {}", ex.getMessage());
      return Double.NaN;
    }
  }

  /**
   * Water dew point of the flue gas from the water partial pressure, via the Antoine equation for water. Below this
   * temperature liquid water condenses on cold-end surfaces.
   *
   * @param pH2OmmHg water partial pressure [mmHg]
   * @return water dew point [degC], or {@link Double#NaN} if the water partial pressure is not positive
   */
  private static double waterDewPointC(double pH2OmmHg) {
    if (pH2OmmHg <= 0.0) {
      return Double.NaN;
    }
    // Antoine (water, mmHg, degC): log10(P) = 8.07131 - 1730.63 / (T + 233.426).
    return 1730.63 / (8.07131 - Math.log10(pH2OmmHg)) - 233.426;
  }

  /**
   * Sulfuric-acid dew point via the Verhoff-Banchero (1974) correlation, the standard screening method for the cold-end
   * acid dew point in sulphur-bearing flue gas. Below this temperature H2SO4 condenses and drives cold-end corrosion;
   * it is markedly higher than the water dew point even at low SO3.
   *
   * @param pH2OmmHg water partial pressure [mmHg], must be &gt; 0
   * @param pSO3mmHg SO3 partial pressure [mmHg], must be &gt; 0
   * @return sulfuric-acid dew point [degC], or {@link Double#NaN} if either partial pressure is not positive
   */
  private static double sulfuricAcidDewPointC(double pH2OmmHg, double pSO3mmHg) {
    if (pH2OmmHg <= 0.0 || pSO3mmHg <= 0.0) {
      return Double.NaN;
    }
    double lnH2O = Math.log(pH2OmmHg);
    double lnSO3 = Math.log(pSO3mmHg);
    double invT = 2.276e-3 - 0.0294e-3 * lnH2O - 0.0858e-3 * lnSO3 + 0.0062e-3 * lnH2O * lnSO3;
    if (invT <= 0.0) {
      return Double.NaN;
    }
    return 1.0 / invT - 273.15;
  }

  /**
   * Result of a combustion / flue-gas calculation.
   */
  public static class CombustionResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Excess-air ratio lambda (derived from the fixed air flow when in air-driven mode). */
    public double excessAirRatio;
    /** True when the excess-air ratio was derived from a fixed combustion-air flow (air-driven mode). */
    public boolean airDriven;
    /** Fixed combustion-air mass flow [kg/hr] in air-driven mode ({@link Double#NaN} otherwise). */
    public double airFlowKgPerHr = Double.NaN;
    /**
     * True when the derived lambda is below 1.0 (air-starved / sub-stoichiometric): the complete-combustion balance no
     * longer holds and real CO would rise sharply, so the point is outside the valid screening range.
     */
    public boolean subStoichiometric;
    /** Fuel mass flow [kg/hr]. */
    public double fuelFlowKgPerHr;
    /** Basis note for the fuel sulphur (measured vs assumed). */
    public String fuelH2sBasis;
    /** Fuel lower heating value [kJ/kg]. */
    public double fuelLhvKJperKg;
    /** Fuel energy input [GJ/hr]. */
    public double fuelEnergyGJperHr;
    /** Actual air/fuel mass ratio [kg air / kg fuel]. */
    public double airFuelMassRatio;
    /** Stoichiometric air/fuel mass ratio [kg air / kg fuel]. */
    public double stoichAirFuelMassRatio;
    /** Exhaust O2 [vol%] on a wet basis (water included). */
    public double exhaustO2VolPercent;
    /** Exhaust O2 [vol%] on a dry basis (water removed) — the basis used for reference-O2 emission corrections. */
    public double exhaustO2VolPercentDry;
    /** Flue-gas water content [vol%] (wet basis). */
    public double waterVolPercent;
    /** Total flue-gas mass rate [kg/hr]. */
    public double totalFlueKgPerHr;
    /** Adiabatic flame temperature [K] (NaN if the flash did not converge). */
    public double adiabaticFlameTemperatureK;
    /** Exhaust major-species mole fractions. */
    public Map<String, Double> flueMoleFraction;
    /** Pollutant concentrations on a WET basis [ppmv]: SO2 (stoichiometric), NOx and CO (emission factor). */
    public Map<String, Double> pollutantPpmv;
    /**
     * Pollutant concentrations on a DRY basis [ppmv] (water removed). This is the basis on which stack CEMS report and
     * on which emission limits are written, so use these (or {@link #pollutantPpmvAtReferenceO2}) for a compliance
     * check, not the wet-basis {@link #pollutantPpmv}.
     */
    public Map<String, Double> pollutantPpmvDry;
    /**
     * Pollutant concentrations corrected to the reference dry-O2 basis [ppmv] (null if no reference set). Computed from
     * the dry-basis concentration at the dry exhaust O2, which is the physically consistent chain for the standard
     * air-dilution correction.
     */
    public Map<String, Double> pollutantPpmvAtReferenceO2;
    /** Reference dry-O2 basis [vol%] used for {@link #pollutantPpmvAtReferenceO2} (NaN if not set). */
    public double referenceO2VolPercent = Double.NaN;
    /**
     * Pollutant concentrations on a DRY basis in mg/Nm3 at the normal reference temperature (NOx as NO2). This is the
     * EU IED / EN 14792 (NOx) / EN 15058 (CO) / EN 14791 (SO2) reporting unit; use
     * {@link #pollutantMgPerNm3AtReferenceO2} for the limit-comparison value at the reference O2.
     */
    public Map<String, Double> pollutantMgPerNm3Dry;
    /**
     * Pollutant concentrations in mg/Nm3 (dry) corrected to the reference O2 (NOx as NO2), null if no reference O2 set.
     * This is the value directly comparable with a permit / IED emission limit in mg/Nm3.
     */
    public Map<String, Double> pollutantMgPerNm3AtReferenceO2;
    /** Normal reference temperature [degC] used for the mg/Nm3 and Nm3/hr outputs (reference pressure 101.325 kPa). */
    public double normalTemperatureC;
    /** Normalized wet flue-gas volumetric flow [Nm3/hr] at the normal reference conditions. */
    public double flueGasNm3PerHrWet;
    /** Normalized dry flue-gas volumetric flow [Nm3/hr] at the normal reference conditions. */
    public double flueGasNm3PerHrDry;
    /** Annual operating hours used for {@link #massRateTonnesPerYear}. */
    public double annualOperatingHours;
    /** Annual species mass emissions [tonnes/yr] (NOx key is {@code NOx_as_NO2}). */
    public Map<String, Double> massRateTonnesPerYear;
    /** Thermal (Zeldovich) NOx mass rate [kg/hr as NO2]. */
    public double noxThermalKgPerHr;
    /** Additive prompt (Fenimore) NOx mass rate [kg/hr as NO2]. */
    public double noxPromptKgPerHr;
    /** Additive fuel-bound-nitrogen NOx mass rate [kg/hr as NO2]. */
    public double noxFuelKgPerHr;
    /** Actual stack-exit flue-gas temperature [degC] (NaN if not supplied). */
    public double stackGasTemperatureC = Double.NaN;
    /** Stack (flue-gas) pressure [bara] used for the actual-condition and dew-point calculations. */
    public double stackPressureBara = Double.NaN;
    /** Actual (stack-condition) volumetric flue-gas flow [Am3/hr] (NaN if no stack temperature supplied). */
    public double stackActualM3PerHr = Double.NaN;
    /** Stack-exit velocity [m/s] (NaN if no stack diameter/temperature supplied). */
    public double stackVelocityMPerS = Double.NaN;
    /** Flue-gas water dew point [degC] (below which water condenses on cold-end surfaces). */
    public double waterDewPointC = Double.NaN;
    /** Sulfuric-acid (H2SO4) dew point [degC] via Verhoff-Banchero (NaN if no SO3 modelled). */
    public double acidDewPointC = Double.NaN;
    /** Applied NOx field-calibration factor (1.0 = uncalibrated). */
    public double noxCalibrationFactor = 1.0;
    /** Applied CO field-calibration factor (1.0 = uncalibrated). */
    public double coCalibrationFactor = 1.0;
    /** Species mass rates [kg/hr]. */
    public Map<String, Double> massRateKgPerHr;

    /**
     * Get a flue-gas mole fraction.
     *
     * @param species species name (N2, O2, CO2, H2O, Ar, SO2)
     * @return the mole fraction, or 0 if absent
     */
    public double getFlueMoleFraction(String species) {
      Double v = flueMoleFraction.get(species);
      return v == null ? 0.0 : v;
    }

    /**
     * Get a species mass rate.
     *
     * @param species species name
     * @return the mass rate [kg/hr], or 0 if absent
     */
    public double getMassRateKgPerHr(String species) {
      Double v = massRateKgPerHr.get(species);
      return v == null ? 0.0 : v;
    }

    /**
     * Serialize the result to a compact JSON string.
     *
     * @return a JSON representation of the result
     */
    public String toJson() {
      StringBuilder sb = new StringBuilder();
      sb.append("{\"excessAirRatio\":").append(excessAirRatio);
      sb.append(",\"airDriven\":").append(airDriven);
      sb.append(",\"subStoichiometric\":").append(subStoichiometric);
      sb.append(",\"fuelFlowKgPerHr\":").append(fuelFlowKgPerHr);
      sb.append(",\"fuelLhvKJperKg\":").append(round(fuelLhvKJperKg, 1));
      sb.append(",\"fuelEnergyGJperHr\":").append(round(fuelEnergyGJperHr, 4));
      sb.append(",\"airFuelMassRatio\":").append(round(airFuelMassRatio, 3));
      sb.append(",\"stoichAirFuelMassRatio\":").append(round(stoichAirFuelMassRatio, 3));
      sb.append(",\"exhaustO2VolPercent\":").append(round(exhaustO2VolPercent, 3));
      sb.append(",\"exhaustO2VolPercentDry\":").append(round(exhaustO2VolPercentDry, 3));
      sb.append(",\"adiabaticFlameTemperatureK\":").append(round(adiabaticFlameTemperatureK, 1));
      sb.append(",\"totalFlueKgPerHr\":").append(round(totalFlueKgPerHr, 1));
      sb.append(",\"normalTemperatureC\":").append(round(normalTemperatureC, 2));
      sb.append(",\"flueGasNm3PerHrWet\":").append(round(flueGasNm3PerHrWet, 1));
      sb.append(",\"flueGasNm3PerHrDry\":").append(round(flueGasNm3PerHrDry, 1));
      sb.append(",\"waterDewPointC\":").append(round(waterDewPointC, 1));
      sb.append(",\"acidDewPointC\":").append(round(acidDewPointC, 1));
      sb.append(",\"stackGasTemperatureC\":").append(round(stackGasTemperatureC, 1));
      sb.append(",\"stackActualM3PerHr\":").append(round(stackActualM3PerHr, 1));
      sb.append(",\"stackVelocityMPerS\":").append(round(stackVelocityMPerS, 2));
      sb.append(",\"noxThermalKgPerHr\":").append(round(noxThermalKgPerHr, 4));
      sb.append(",\"noxPromptKgPerHr\":").append(round(noxPromptKgPerHr, 4));
      sb.append(",\"noxFuelKgPerHr\":").append(round(noxFuelKgPerHr, 4));
      appendMap(sb, ",\"flueMoleFraction\":", flueMoleFraction, 6);
      appendMap(sb, ",\"pollutantPpmv\":", pollutantPpmv, 3);
      appendMap(sb, ",\"pollutantPpmvDry\":", pollutantPpmvDry, 3);
      if (pollutantMgPerNm3Dry != null) {
        appendMap(sb, ",\"pollutantMgPerNm3Dry\":", pollutantMgPerNm3Dry, 3);
      }
      if (pollutantMgPerNm3AtReferenceO2 != null) {
        appendMap(sb, ",\"pollutantMgPerNm3AtReferenceO2\":", pollutantMgPerNm3AtReferenceO2, 3);
      }
      appendMap(sb, ",\"massRateKgPerHr\":", massRateKgPerHr, 4);
      if (massRateTonnesPerYear != null) {
        appendMap(sb, ",\"massRateTonnesPerYear\":", massRateTonnesPerYear, 4);
      }
      sb.append("}");
      return sb.toString();
    }

    /**
     * Append a string-keyed numeric map to a JSON builder.
     *
     * @param sb the target builder
     * @param key the JSON key (including the leading comma and colon)
     * @param m the map to serialize
     * @param decimals rounding decimals
     */
    private static void appendMap(StringBuilder sb, String key, Map<String, Double> m, int decimals) {
      sb.append(key).append("{");
      boolean first = true;
      for (Map.Entry<String, Double> e : m.entrySet()) {
        if (!first) {
          sb.append(",");
        }
        sb.append("\"").append(e.getKey()).append("\":").append(round(e.getValue(), decimals));
        first = false;
      }
      sb.append("}");
    }

    /**
     * Round a value to a fixed number of decimals.
     *
     * @param v the value
     * @param decimals number of decimals
     * @return the rounded value
     */
    private static double round(double v, int decimals) {
      if (Double.isNaN(v) || Double.isInfinite(v)) {
        return v;
      }
      double f = Math.pow(10.0, decimals);
      return Math.round(v * f) / f;
    }
  }
}
