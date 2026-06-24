package neqsim.pvtsimulation.flowassurance;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * SURF (Subsea Umbilicals, Risers, Flowlines) cooldown and no-touch-time analyzer.
 *
 * <p>
 * This class bridges a live NeqSim fluid to the lumped-parameter {@link PipelineCooldownCalculator}
 * thermal engine and produces the single most important flow-assurance metric for subsea operation
 * support and field-development design: the <b>no-touch time</b> &mdash; the time available after
 * an unplanned shutdown before the stagnant fluid cools to the hydrate formation temperature (plus
 * an operating margin).
 * </p>
 *
 * <p>
 * Unlike {@link PipelineCooldownCalculator}, which requires the fluid density and specific heat to
 * be supplied manually, this analyzer extracts those properties directly from a NeqSim
 * {@link neqsim.thermo.system.SystemInterface} and computes the hydrate equilibrium temperature
 * from the same fluid, so the no-touch time is consistent with the actual produced composition and
 * operating point.
 * </p>
 *
 * <p>
 * The analyzer supports two complementary use cases:
 * </p>
 * <ul>
 * <li><b>Operation support</b> &mdash; given the current operating temperature, flowline geometry
 * and insulation, report the no-touch time and a screening verdict so operators know how long they
 * have to restart, depressurise, or inject inhibitor.</li>
 * <li><b>Field development</b> &mdash; given a target no-touch time, compare candidate insulation
 * U-values (via {@link #setOverallUValue(double)} or layer properties) to select a flowline thermal
 * design.</li>
 * </ul>
 *
 * <p>
 * The cooldown physics is a lumped radial heat-transfer model (nested cylindrical shells: fluid,
 * steel wall, insulation, coating) with an overall heat transfer coefficient referenced to the
 * outer diameter. The model is screening-level and does not resolve axial temperature gradients,
 * multiphase holdup redistribution, or transient wall conduction; for detailed design use a
 * dedicated transient thermal-hydraulic tool.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * SystemInterface fluid = new SystemSrkEos(273.15 + 65.0, 120.0);
 * fluid.addComponent("methane", 0.80);
 * fluid.addComponent("ethane", 0.07);
 * fluid.addComponent("propane", 0.04);
 * fluid.addComponent("water", 0.09);
 * fluid.setMixingRule("classic");
 *
 * SurfCooldownAnalyzer analyzer = new SurfCooldownAnalyzer(fluid);
 * analyzer.setInternalDiameter(0.254); // 10 inch
 * analyzer.setWallThickness(0.0159);
 * analyzer.setInsulationThickness(0.060);
 * analyzer.setOverallUValue(2.5); // W/m2K
 * analyzer.setSeabedTemperature(4.0); // degC
 * analyzer.setHydrateMargin(3.0); // K above hydrate Teq
 * analyzer.setRequiredNoTouchTimeHours(8.0);
 * analyzer.calculate();
 *
 * double noTouch = analyzer.getNoTouchTimeHours();
 * String verdict = analyzer.getVerdict();
 * String json = analyzer.toJson();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class SurfCooldownAnalyzer implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Class logger. */
  private static final Logger logger = LogManager.getLogger(SurfCooldownAnalyzer.class);

  /**
   * Verdict returned when the no-touch time comfortably meets the requirement or absolute target.
   */
  public static final String VERDICT_OK = "OK";

  /** Verdict returned when the no-touch time is within the marginal band. */
  public static final String VERDICT_MARGINAL = "MARGINAL";

  /** Verdict returned when the no-touch time is below the acceptable threshold. */
  public static final String VERDICT_CRITICAL = "CRITICAL";

  /** Verdict returned when the fluid never forms hydrates above seabed temperature. */
  public static final String VERDICT_NO_HYDRATE_RISK = "NO_HYDRATE_RISK";

  // --- Inputs ---

  /** The produced fluid, used for property and hydrate evaluation. */
  private final SystemInterface fluid;

  /** Internal diameter in metres. */
  private double internalDiameter = 0.254;

  /** Steel wall thickness in metres. */
  private double wallThickness = 0.0159;

  /** Insulation thickness in metres. */
  private double insulationThickness = 0.050;

  /** External coating thickness in metres. */
  private double coatingThickness = 0.006;

  /** Insulation thermal conductivity in W/mK. */
  private double insulationConductivity = 0.17;

  /**
   * Overall heat transfer coefficient in W/m2K referenced to outer diameter, or NaN to use layers.
   */
  private double overallUValue = Double.NaN;

  /** External convection coefficient in W/m2K, used when the overall U-value is not set. */
  private double externalHTC = 300.0;

  /** Seabed (ambient) temperature in Kelvin. */
  private double seabedTemperatureK = 273.15 + 4.0;

  /**
   * Operating (initial) fluid temperature override in Kelvin, or NaN to use the fluid temperature.
   */
  private double operatingTemperatureK = Double.NaN;

  /** Hydrate safety margin in Kelvin added above the hydrate equilibrium temperature. */
  private double hydrateMarginK = 3.0;

  /**
   * Required no-touch time in hours for verdict evaluation, or NaN for absolute-band
   * classification.
   */
  private double requiredNoTouchTimeHours = Double.NaN;

  /** Total simulation time in hours. */
  private double totalTimeHours = 72.0;

  /** Time step in minutes. */
  private double timeStepMinutes = 5.0;

  // --- Results ---

  /** Underlying cooldown engine. */
  private PipelineCooldownCalculator cooldown;

  /** Fluid density at the operating point in kg/m3. */
  private double fluidDensity = Double.NaN;

  /** Fluid specific heat at the operating point in J/kgK. */
  private double fluidSpecificHeat = Double.NaN;

  /** Hydrate equilibrium temperature at the operating pressure in Kelvin. */
  private double hydrateEquilibriumTemperatureK = Double.NaN;

  /** Initial (operating) fluid temperature used for the cooldown in Kelvin. */
  private double initialFluidTemperatureK = Double.NaN;

  /** Computed no-touch time in hours (time to reach hydrate Teq plus margin). */
  private double noTouchTimeHours = Double.NaN;

  /** Screening verdict. */
  private String verdict = null;

  /** Whether the analysis has been run. */
  private boolean calculated = false;

  /**
   * Creates a new analyzer for the supplied produced fluid.
   *
   * @param fluid the produced fluid; its current temperature and pressure define the operating
   *        point unless {@link #setOperatingTemperature(double)} is used. Must not be null.
   * @throws IllegalArgumentException if {@code fluid} is null
   */
  public SurfCooldownAnalyzer(SystemInterface fluid) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }
    this.fluid = fluid;
  }

  /**
   * Sets the internal diameter.
   *
   * @param diameter internal diameter in metres (must be greater than 0)
   */
  public void setInternalDiameter(double diameter) {
    this.internalDiameter = diameter;
  }

  /**
   * Sets the steel wall thickness.
   *
   * @param thickness wall thickness in metres
   */
  public void setWallThickness(double thickness) {
    this.wallThickness = thickness;
  }

  /**
   * Sets the insulation thickness.
   *
   * @param thickness insulation thickness in metres (0 for a bare pipe)
   */
  public void setInsulationThickness(double thickness) {
    this.insulationThickness = thickness;
  }

  /**
   * Sets the external coating thickness.
   *
   * @param thickness coating thickness in metres
   */
  public void setCoatingThickness(double thickness) {
    this.coatingThickness = thickness;
  }

  /**
   * Sets the insulation thermal conductivity, used only when the overall U-value is not set.
   *
   * @param conductivity insulation conductivity in W/mK
   */
  public void setInsulationConductivity(double conductivity) {
    this.insulationConductivity = conductivity;
  }

  /**
   * Sets the overall heat transfer coefficient directly, bypassing the layer calculation.
   *
   * @param uValue overall heat transfer coefficient in W/m2K referenced to the outer diameter
   */
  public void setOverallUValue(double uValue) {
    this.overallUValue = uValue;
  }

  /**
   * Sets the external convection coefficient, used when the overall U-value is not set.
   *
   * @param htc external heat transfer coefficient in W/m2K
   */
  public void setExternalHTC(double htc) {
    this.externalHTC = htc;
  }

  /**
   * Sets the seabed (ambient) temperature.
   *
   * @param temperatureCelsius seabed temperature in degrees Celsius
   */
  public void setSeabedTemperature(double temperatureCelsius) {
    this.seabedTemperatureK = temperatureCelsius + 273.15;
  }

  /**
   * Overrides the operating (initial) fluid temperature for the cooldown.
   *
   * @param temperatureCelsius operating temperature in degrees Celsius
   */
  public void setOperatingTemperature(double temperatureCelsius) {
    this.operatingTemperatureK = temperatureCelsius + 273.15;
  }

  /**
   * Sets the hydrate safety margin added above the hydrate equilibrium temperature when defining
   * the no-touch target temperature.
   *
   * @param marginK margin in Kelvin (typically 2 to 4 K)
   */
  public void setHydrateMargin(double marginK) {
    this.hydrateMarginK = marginK;
  }

  /**
   * Sets the required no-touch time used for the verdict. When set, the verdict compares the
   * computed no-touch time against this requirement; when left unset (NaN), an absolute-band
   * classification is used instead.
   *
   * @param hours required no-touch time in hours
   */
  public void setRequiredNoTouchTimeHours(double hours) {
    this.requiredNoTouchTimeHours = hours;
  }

  /**
   * Sets the total simulation horizon.
   *
   * @param hours total time in hours
   */
  public void setTotalTimeHours(double hours) {
    this.totalTimeHours = hours;
  }

  /**
   * Sets the integration time step.
   *
   * @param minutes time step in minutes
   */
  public void setTimeStepMinutes(double minutes) {
    this.timeStepMinutes = minutes;
  }

  /**
   * Runs the cooldown analysis.
   *
   * <p>
   * The method (1) extracts the fluid density and specific heat at the operating point, (2)
   * computes the hydrate equilibrium temperature at the operating pressure, (3) configures and runs
   * the {@link PipelineCooldownCalculator}, and (4) evaluates the no-touch time and screening
   * verdict.
   * </p>
   */
  public void calculate() {
    extractFluidProperties();
    computeHydrateEquilibriumTemperature();

    cooldown = new PipelineCooldownCalculator();
    cooldown.setInternalDiameter(internalDiameter);
    cooldown.setWallThickness(wallThickness);
    cooldown.setInsulationThickness(insulationThickness);
    cooldown.setCoatingThickness(coatingThickness);
    cooldown.setInsulationConductivity(insulationConductivity);
    if (Double.isNaN(overallUValue)) {
      cooldown.useLayerCalculation();
      cooldown.setExternalHTC(externalHTC);
    } else {
      cooldown.setOverallUValue(overallUValue);
    }
    cooldown.setInitialFluidTemperature(initialFluidTemperatureK);
    cooldown.setAmbientTemperature(seabedTemperatureK);
    cooldown.setFluidDensity(fluidDensity);
    cooldown.setFluidSpecificHeat(fluidSpecificHeat);
    cooldown.setTimeStepMinutes(timeStepMinutes);
    cooldown.setTotalTimeHours(totalTimeHours);
    cooldown.calculate();

    evaluateNoTouchTime();
    calculated = true;
  }

  /**
   * Extracts fluid density, specific heat and operating temperature from a flashed clone of the
   * fluid.
   */
  private void extractFluidProperties() {
    SystemInterface work = fluid.clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(work);
    ops.TPflash();
    work.initProperties();
    fluidDensity = work.getDensity("kg/m3");
    // getCp("J/kgK") returns the mass-specific heat capacity directly.
    fluidSpecificHeat = work.getCp("J/kgK");
    initialFluidTemperatureK =
        Double.isNaN(operatingTemperatureK) ? work.getTemperature() : operatingTemperatureK;
  }

  /**
   * Computes the hydrate equilibrium temperature at the operating pressure from a fluid clone.
   */
  private void computeHydrateEquilibriumTemperature() {
    try {
      SystemInterface hyd = fluid.clone();
      hyd.setHydrateCheck(true);
      ThermodynamicOperations ops = new ThermodynamicOperations(hyd);
      ops.hydrateFormationTemperature();
      hydrateEquilibriumTemperatureK = hyd.getTemperature();
    } catch (Exception ex) {
      logger.warn("Hydrate equilibrium temperature calculation failed: {}", ex.getMessage());
      hydrateEquilibriumTemperatureK = Double.NaN;
    }
  }

  /**
   * Evaluates the no-touch time and screening verdict from the cooldown profile.
   */
  private void evaluateNoTouchTime() {
    if (Double.isNaN(hydrateEquilibriumTemperatureK)
        || hydrateEquilibriumTemperatureK <= seabedTemperatureK) {
      noTouchTimeHours = Double.POSITIVE_INFINITY;
      verdict = VERDICT_NO_HYDRATE_RISK;
      return;
    }
    double targetTemperatureK = hydrateEquilibriumTemperatureK + hydrateMarginK;
    if (initialFluidTemperatureK <= targetTemperatureK) {
      noTouchTimeHours = 0.0;
    } else {
      double t = cooldown.getTimeToReachTemperature(targetTemperatureK);
      noTouchTimeHours = t < 0.0 ? Double.POSITIVE_INFINITY : t;
    }
    verdict = classifyVerdict();
  }

  /**
   * Classifies the screening verdict from the computed no-touch time.
   *
   * @return one of {@link #VERDICT_OK}, {@link #VERDICT_MARGINAL} or {@link #VERDICT_CRITICAL}
   */
  private String classifyVerdict() {
    if (Double.isInfinite(noTouchTimeHours)) {
      return VERDICT_OK;
    }
    if (!Double.isNaN(requiredNoTouchTimeHours) && requiredNoTouchTimeHours > 0.0) {
      if (noTouchTimeHours >= requiredNoTouchTimeHours) {
        return VERDICT_OK;
      }
      if (noTouchTimeHours >= 0.75 * requiredNoTouchTimeHours) {
        return VERDICT_MARGINAL;
      }
      return VERDICT_CRITICAL;
    }
    // Absolute-band classification when no requirement is supplied.
    if (noTouchTimeHours >= 12.0) {
      return VERDICT_OK;
    }
    if (noTouchTimeHours >= 6.0) {
      return VERDICT_MARGINAL;
    }
    return VERDICT_CRITICAL;
  }

  /**
   * Returns the computed no-touch time.
   *
   * @return no-touch time in hours, or positive infinity if the hydrate target is never reached
   */
  public double getNoTouchTimeHours() {
    if (!calculated) {
      calculate();
    }
    return noTouchTimeHours;
  }

  /**
   * Returns the screening verdict.
   *
   * @return the verdict string
   */
  public String getVerdict() {
    if (!calculated) {
      calculate();
    }
    return verdict;
  }

  /**
   * Returns the hydrate equilibrium temperature at the operating pressure.
   *
   * @return hydrate equilibrium temperature in Kelvin, or NaN if the calculation failed
   */
  public double getHydrateEquilibriumTemperatureK() {
    if (!calculated) {
      calculate();
    }
    return hydrateEquilibriumTemperatureK;
  }

  /**
   * Returns the initial (operating) fluid temperature used for the cooldown.
   *
   * @return initial fluid temperature in Kelvin
   */
  public double getInitialFluidTemperatureK() {
    if (!calculated) {
      calculate();
    }
    return initialFluidTemperatureK;
  }

  /**
   * Returns the fluid density used in the cooldown model.
   *
   * @return fluid density in kg/m3
   */
  public double getFluidDensity() {
    if (!calculated) {
      calculate();
    }
    return fluidDensity;
  }

  /**
   * Returns the fluid specific heat used in the cooldown model.
   *
   * @return specific heat in J/kgK
   */
  public double getFluidSpecificHeat() {
    if (!calculated) {
      calculate();
    }
    return fluidSpecificHeat;
  }

  /**
   * Returns the lumped thermal time constant of the configured flowline.
   *
   * @return time constant in hours
   */
  public double getTimeConstantHours() {
    if (!calculated) {
      calculate();
    }
    return cooldown.getTimeConstantHours();
  }

  /**
   * Returns the underlying cooldown calculator for access to the full temperature profile.
   *
   * @return the cooldown calculator, or null if {@link #calculate()} has not been run
   */
  public PipelineCooldownCalculator getCooldownCalculator() {
    if (!calculated) {
      calculate();
    }
    return cooldown;
  }

  /**
   * Returns a JSON representation of the no-touch-time analysis.
   *
   * @return JSON string
   */
  public String toJson() {
    if (!calculated) {
      calculate();
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("verdict", verdict);
    result.put("noTouchTime_hours", round2(noTouchTimeHours));
    result.put("requiredNoTouchTime_hours",
        Double.isNaN(requiredNoTouchTimeHours) ? null : round2(requiredNoTouchTimeHours));
    result.put("initialFluidTemperature_C", round2(initialFluidTemperatureK - 273.15));
    result.put("hydrateEquilibriumTemperature_C",
        Double.isNaN(hydrateEquilibriumTemperatureK) ? null
            : round2(hydrateEquilibriumTemperatureK - 273.15));
    result.put("hydrateMargin_K", round2(hydrateMarginK));
    result.put("seabedTemperature_C", round2(seabedTemperatureK - 273.15));
    result.put("overallUValue_Wm2K",
        Double.isNaN(overallUValue) ? round2(cooldown.getCalculatedUValue())
            : round2(overallUValue));
    result.put("timeConstant_hours", round2(cooldown.getTimeConstantHours()));
    result.put("fluidDensity_kgm3", round2(fluidDensity));
    result.put("fluidSpecificHeat_JkgK", round2(fluidSpecificHeat));
    result.put("internalDiameter_m", internalDiameter);
    result.put("insulationThickness_mm", round2(insulationThickness * 1000.0));

    Gson gson =
        new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(result);
  }

  /**
   * Rounds a value to two decimal places, preserving non-finite values.
   *
   * @param value the value to round
   * @return the rounded value, or the original value if non-finite
   */
  private static double round2(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return value;
    }
    return Math.round(value * 100.0) / 100.0;
  }
}
