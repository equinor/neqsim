package neqsim.process.equipment.refrigeration;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import neqsim.thermo.system.SystemAmmoniaEos;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrMathiasCopeman;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Property layer for a single-component refrigerant used by {@link RefrigerationSystem}.
 *
 * <p>
 * Wraps a one-component NeqSim fluid and exposes the saturation line and pressure-enthalpy / pressure-entropy state
 * solves that a vapour-compression cycle needs. Two-phase states are resolved with the lever rule between the saturated
 * liquid and vapour, so the result does not depend on how a flash algorithm labels a pure fluid at its saturation
 * pressure.
 * </p>
 *
 * <p>
 * {@link #of(String)} selects the equation of state measured to reproduce the saturation pressure and latent heat of
 * each refrigerant best: the dedicated reference equation for ammonia, GERG-2008 for carbon dioxide and Peng-Robinson
 * with the Mathias-Copeman temperature function for hydrocarbons and halocarbons. Components without Mathias-Copeman
 * parameters in the database fall back to the standard Peng-Robinson temperature function. ASHRAE designations such as
 * {@code R717} or {@code R290} are accepted. Use {@link #of(String, SystemInterface)} to supply another model.
 * </p>
 *
 * <p>
 * Instances cache saturation states and reuse one working fluid, so they are not thread safe.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class RefrigerantFluid implements Serializable {
  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Relative pressure offset used to step off the saturation line into a single phase. */
  private static final double SATURATION_PRESSURE_OFFSET = 5.0e-4;

  /** Temperature band next to the saturation line that is interpolated instead of flashed, in K. */
  private static final double SATURATION_TEMPERATURE_BAND = 0.05;

  /** Maximum number of Newton iterations for the single-phase state solves. */
  private static final int MAX_ITERATIONS = 60;

  /** ASHRAE designations mapped to NeqSim component names. */
  private static final Map<String, String> ASHRAE_NAMES = createAshraeNames();

  /** Refrigerant display name. */
  private final String name;

  /** NeqSim component name. */
  private final String componentName;

  /** Name of the thermodynamic model. */
  private final String modelName;

  /** Template fluid that is cloned to create working fluids. */
  private final SystemInterface template;

  /** Critical temperature in K. */
  private final double criticalTemperature;

  /** Critical pressure in bara. */
  private final double criticalPressure;

  /** Acentric factor. */
  private final double acentricFactor;

  /** Working fluid reused by the state solves. */
  private transient SystemInterface work;

  /** Heat capacity of the last flashed state in J/(kg K). */
  private transient double lastHeatCapacity = Double.NaN;

  /** Cache of saturation temperatures keyed by pressure. */
  private transient Map<Long, Double> saturationTemperatureCache;

  /** Cache of saturation states keyed by temperature. */
  private transient Map<Long, RefrigerantState[]> saturationCache;

  /**
   * Creates a refrigerant from a prepared single-component fluid.
   *
   * @param name display name
   * @param fluid single-component fluid; it is cloned
   */
  private RefrigerantFluid(String name, SystemInterface fluid) {
    if (fluid == null) {
      throw new IllegalArgumentException("refrigerant fluid must not be null");
    }
    if (fluid.getPhase(0).getNumberOfComponents() != 1) {
      throw new IllegalArgumentException("RefrigerantFluid supports single-component refrigerants only, got "
          + fluid.getPhase(0).getNumberOfComponents() + " components. Use the dominant component, or "
          + "neqsim.process.lng.LNGProcessBuilder for mixed-refrigerant cycles.");
    }
    this.name = name;
    this.template = fluid.clone();
    this.componentName = template.getPhase(0).getComponent(0).getComponentName();
    this.modelName = template.getModelName();
    this.criticalTemperature = template.getPhase(0).getComponent(0).getTC();
    this.criticalPressure = template.getPhase(0).getComponent(0).getPC();
    this.acentricFactor = template.getPhase(0).getComponent(0).getAcentricFactor();
  }

  /**
   * Creates the ASHRAE designation table.
   *
   * @return map from lower-case designation to NeqSim component name
   */
  private static Map<String, String> createAshraeNames() {
    Map<String, String> names = new HashMap<String, String>();
    names.put("r717", "ammonia");
    names.put("nh3", "ammonia");
    names.put("r744", "CO2");
    names.put("co2", "CO2");
    names.put("carbon dioxide", "CO2");
    names.put("r290", "propane");
    names.put("r1270", "propene");
    names.put("propylene", "propene");
    names.put("r1150", "ethylene");
    names.put("ethene", "ethylene");
    names.put("r170", "ethane");
    names.put("r50", "methane");
    names.put("r600", "n-butane");
    names.put("r600a", "i-butane");
    names.put("isobutane", "i-butane");
    names.put("r728", "nitrogen");
    names.put("r134a", "R134a");
    names.put("r12", "R12");
    return names;
  }

  /**
   * Creates a refrigerant with the recommended equation of state.
   *
   * @param refrigerant NeqSim component name or ASHRAE designation, for example {@code "propane"}, {@code "R717"} or
   * {@code "CO2"}
   * @return refrigerant property layer
   */
  public static RefrigerantFluid of(String refrigerant) {
    if (refrigerant == null || refrigerant.trim().isEmpty()) {
      throw new IllegalArgumentException("refrigerant name must not be empty");
    }
    String key = refrigerant.trim().toLowerCase(Locale.ROOT);
    String component = ASHRAE_NAMES.containsKey(key) ? ASHRAE_NAMES.get(key) : refrigerant.trim();
    SystemInterface fluid;
    if ("ammonia".equals(component)) {
      fluid = new SystemAmmoniaEos(273.15, 1.0);
      fluid.addComponent(component, 1.0);
    } else if ("CO2".equals(component)) {
      fluid = new SystemGERG2008Eos(273.15, 10.0);
      fluid.addComponent(component, 1.0);
      fluid.createDatabase(true);
      fluid.setMixingRule(2);
    } else {
      fluid = new SystemPrMathiasCopeman(273.15, 1.0);
      fluid.addComponent(component, 1.0);
      fluid.createDatabase(true);
      fluid.setMixingRule(2);
    }
    return new RefrigerantFluid(component, fluid);
  }

  /**
   * Creates a refrigerant from a user-supplied single-component fluid.
   *
   * @param name display name
   * @param fluid single-component fluid with its mixing rule set; it is cloned
   * @return refrigerant property layer
   */
  public static RefrigerantFluid of(String name, SystemInterface fluid) {
    return new RefrigerantFluid(name, fluid);
  }

  /**
   * Gets the refrigerant display name.
   *
   * @return name
   */
  public String getName() {
    return name;
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
   * Gets the name of the thermodynamic model.
   *
   * @return model name
   */
  public String getModelName() {
    return modelName;
  }

  /**
   * Gets the critical temperature.
   *
   * @return critical temperature in K
   */
  public double getCriticalTemperature() {
    return criticalTemperature;
  }

  /**
   * Gets the critical pressure.
   *
   * @return critical pressure in bara
   */
  public double getCriticalPressure() {
    return criticalPressure;
  }

  /**
   * Gets the saturation pressure at a temperature.
   *
   * @param temperature temperature in K, below the critical temperature
   * @return saturation pressure in bara
   */
  public double getSaturationPressure(double temperature) {
    return saturation(temperature)[0].getPressure();
  }

  /**
   * Gets the saturation temperature at a pressure.
   *
   * @param pressure pressure in bara, below the critical pressure
   * @return saturation temperature in K
   */
  public double getSaturationTemperature(double pressure) {
    if (!(pressure > 0.0) || pressure >= criticalPressure) {
      throw new IllegalArgumentException("pressure " + pressure + " bara is outside the two-phase range of " + name
          + " (critical pressure " + criticalPressure + " bara)");
    }
    if (saturationTemperatureCache == null) {
      saturationTemperatureCache = new HashMap<Long, Double>();
    }
    Long key = Long.valueOf(Math.round(pressure * 1.0e9));
    Double cached = saturationTemperatureCache.get(key);
    if (cached != null) {
      return cached.doubleValue();
    }
    double slope = 5.373 * (1.0 + acentricFactor);
    double temperature = criticalTemperature / (1.0 - Math.log(pressure / criticalPressure) / slope);
    // Secant iteration on ln(p) against 1/T, where the saturation line is nearly straight.
    double dLnPdInvT = -slope * criticalTemperature;
    double previousInvT = Double.NaN;
    double previousError = Double.NaN;
    for (int i = 0; i < MAX_ITERATIONS; i++) {
      temperature = Math.max(0.3 * criticalTemperature, Math.min(temperature, 0.9995 * criticalTemperature));
      double invT = 1.0 / temperature;
      double error = Math.log(saturationPressureUncached(temperature) / pressure);
      if (Math.abs(error) < 1.0e-9) {
        saturationTemperatureCache.put(key, Double.valueOf(temperature));
        return temperature;
      }
      if (!Double.isNaN(previousInvT) && Math.abs(error - previousError) > 1.0e-14) {
        double secant = (error - previousError) / (invT - previousInvT);
        if (secant < 0.0) {
          dLnPdInvT = secant;
        }
      }
      previousInvT = invT;
      previousError = error;
      temperature = 1.0 / (invT - error / dLnPdInvT);
    }
    throw new IllegalStateException("saturation temperature of " + name + " did not converge at " + pressure + " bara");
  }

  /**
   * Gets the saturated liquid state at a temperature.
   *
   * @param temperature temperature in K, below the critical temperature
   * @return saturated liquid state
   */
  public RefrigerantState saturatedLiquid(double temperature) {
    return saturation(temperature)[0];
  }

  /**
   * Gets the saturated vapour state at a temperature.
   *
   * @param temperature temperature in K, below the critical temperature
   * @return saturated vapour state
   */
  public RefrigerantState saturatedVapor(double temperature) {
    return saturation(temperature)[1];
  }

  /**
   * Gets the latent heat of vaporisation at a temperature.
   *
   * @param temperature temperature in K, below the critical temperature
   * @return latent heat in J/kg
   */
  public double getLatentHeat(double temperature) {
    RefrigerantState[] states = saturation(temperature);
    return states[1].getEnthalpy() - states[0].getEnthalpy();
  }

  /**
   * Gets the state of subcooled liquid at a pressure and temperature.
   *
   * @param pressure pressure in bara
   * @param temperature temperature in K, at or below the saturation temperature
   * @return liquid state
   */
  public RefrigerantState liquidAt(double pressure, double temperature) {
    double saturationTemperature = getSaturationTemperature(pressure);
    RefrigerantState saturated = saturation(saturationTemperature)[0];
    if (temperature > saturationTemperature + 1.0e-6) {
      throw new IllegalArgumentException("temperature " + temperature + " K is above the saturation temperature "
          + saturationTemperature + " K of " + name + " at " + pressure + " bara; the state is not liquid");
    }
    double edge = saturationTemperature - SATURATION_TEMPERATURE_BAND;
    if (temperature >= edge) {
      RefrigerantState atEdge = flash(pressure, edge, 0.0);
      return interpolate(saturated, atEdge, (saturationTemperature - temperature) / SATURATION_TEMPERATURE_BAND);
    }
    return flash(pressure, temperature, 0.0);
  }

  /**
   * Gets the state of superheated vapour at a pressure and temperature.
   *
   * @param pressure pressure in bara
   * @param temperature temperature in K, at or above the saturation temperature
   * @return vapour state
   */
  public RefrigerantState vaporAt(double pressure, double temperature) {
    if (pressure >= criticalPressure) {
      return flash(pressure, temperature, 1.0);
    }
    double saturationTemperature = getSaturationTemperature(pressure);
    RefrigerantState saturated = saturation(saturationTemperature)[1];
    if (temperature < saturationTemperature - 1.0e-6) {
      throw new IllegalArgumentException("temperature " + temperature + " K is below the saturation temperature "
          + saturationTemperature + " K of " + name + " at " + pressure + " bara; the state is not vapour");
    }
    double edge = saturationTemperature + SATURATION_TEMPERATURE_BAND;
    if (temperature <= edge) {
      RefrigerantState atEdge = flash(pressure, edge, 1.0);
      return interpolate(saturated, atEdge, (temperature - saturationTemperature) / SATURATION_TEMPERATURE_BAND);
    }
    return flash(pressure, temperature, 1.0);
  }

  /**
   * Gets the state at a pressure and specific enthalpy.
   *
   * @param pressure pressure in bara
   * @param enthalpy specific enthalpy in J/kg on this fluid's reference state
   * @return liquid, two-phase or vapour state
   */
  public RefrigerantState stateAtPH(double pressure, double enthalpy) {
    return solve(pressure, enthalpy, true);
  }

  /**
   * Gets the state at a pressure and specific entropy.
   *
   * @param pressure pressure in bara
   * @param entropy specific entropy in J/(kg K) on this fluid's reference state
   * @return liquid, two-phase or vapour state
   */
  public RefrigerantState stateAtPS(double pressure, double entropy) {
    return solve(pressure, entropy, false);
  }

  /**
   * Creates a NeqSim fluid at a refrigerant state, for use in a {@code Stream} or a detailed equipment model.
   *
   * <p>
   * A state on the saturation line is moved 0.05 K into the single-phase region so that a later flash of the returned
   * fluid cannot pick the other phase.
   * </p>
   *
   * @param state refrigerant state; two-phase states are returned at the saturation temperature
   * @param massFlowKgPerHr mass flow in kg/hr
   * @return new fluid at the state pressure and temperature with the flow set
   */
  public SystemInterface createFluid(RefrigerantState state, double massFlowKgPerHr) {
    SystemInterface fluid = template.clone();
    double temperature = state.getTemperature();
    if (state.getPressure() < criticalPressure) {
      double saturationTemperature = getSaturationTemperature(state.getPressure());
      if (state.getVaporFraction() >= 1.0) {
        temperature = Math.max(temperature, saturationTemperature + SATURATION_TEMPERATURE_BAND);
      } else if (state.getVaporFraction() <= 0.0) {
        temperature = Math.min(temperature, saturationTemperature - SATURATION_TEMPERATURE_BAND);
      }
    }
    fluid.setTemperature(temperature);
    fluid.setPressure(state.getPressure());
    fluid.setTotalFlowRate(Math.max(massFlowKgPerHr, 1.0e-12), "kg/hr");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }

  /**
   * Solves a single-phase or two-phase state at a pressure for a target enthalpy or entropy.
   *
   * @param pressure pressure in bara
   * @param target target specific enthalpy in J/kg or entropy in J/(kg K)
   * @param enthalpyTarget true when the target is enthalpy, false when it is entropy
   * @return state matching the target
   */
  private RefrigerantState solve(double pressure, double target, boolean enthalpyTarget) {
    if (pressure >= criticalPressure) {
      return newton(pressure, target, enthalpyTarget, criticalTemperature * 1.1, Double.NaN, 1.0);
    }
    double saturationTemperature = getSaturationTemperature(pressure);
    RefrigerantState[] saturated = saturation(saturationTemperature);
    double liquidValue = value(saturated[0], enthalpyTarget);
    double vaporValue = value(saturated[1], enthalpyTarget);
    if (target >= liquidValue && target <= vaporValue) {
      double quality = (target - liquidValue) / (vaporValue - liquidValue);
      // A state built from saturation properties lands on the line only to round-off; snap it.
      if (quality > 1.0 - 1.0e-6) {
        return saturated[1];
      } else if (quality < 1.0e-6) {
        return saturated[0];
      }
      double specificVolume = (1.0 - quality) / saturated[0].getDensity() + quality / saturated[1].getDensity();
      return new RefrigerantState(pressure, saturationTemperature,
          saturated[0].getEnthalpy() + quality * (saturated[1].getEnthalpy() - saturated[0].getEnthalpy()),
          saturated[0].getEntropy() + quality * (saturated[1].getEntropy() - saturated[0].getEntropy()),
          1.0 / specificVolume, quality);
    }
    boolean vapor = target > vaporValue;
    double edge = saturationTemperature + (vapor ? SATURATION_TEMPERATURE_BAND : -SATURATION_TEMPERATURE_BAND);
    RefrigerantState atEdge = flash(pressure, edge, vapor ? 1.0 : 0.0);
    double edgeValue = value(atEdge, enthalpyTarget);
    RefrigerantState onLine = saturated[vapor ? 1 : 0];
    double lineValue = vapor ? vaporValue : liquidValue;
    if ((vapor && target <= edgeValue) || (!vapor && target >= edgeValue)) {
      return interpolate(onLine, atEdge, (target - lineValue) / (edgeValue - lineValue));
    }
    return newton(pressure, target, enthalpyTarget, edge, edge, vapor ? 1.0 : 0.0);
  }

  /**
   * Runs a Newton iteration on temperature at constant pressure in a single phase.
   *
   * @param pressure pressure in bara
   * @param target target specific enthalpy in J/kg or entropy in J/(kg K)
   * @param enthalpyTarget true when the target is enthalpy, false when it is entropy
   * @param startTemperature initial temperature in K
   * @param boundTemperature temperature in K the iteration must not cross towards the two-phase region, or NaN
   * @param vaporFraction vapour fraction assigned to the result, 0 or 1
   * @return converged state
   */
  private RefrigerantState newton(double pressure, double target, boolean enthalpyTarget, double startTemperature,
      double boundTemperature, double vaporFraction) {
    double temperature = startTemperature;
    boolean vapor = vaporFraction >= 1.0;
    RefrigerantState state = null;
    for (int i = 0; i < MAX_ITERATIONS; i++) {
      state = flash(pressure, temperature, vaporFraction);
      double heatCapacity = lastHeatCapacity;
      if (!(heatCapacity > 0.0) || Double.isInfinite(heatCapacity)) {
        throw new IllegalStateException("non-physical heat capacity for " + name + " at " + pressure + " bara and "
            + temperature + " K; check the ideal-gas heat capacity coefficients of the component");
      }
      double error = target - value(state, enthalpyTarget);
      double tolerance = enthalpyTarget ? 1.0e-2 : 1.0e-4;
      if (Math.abs(error) < tolerance) {
        return state;
      }
      double next = enthalpyTarget ? temperature + error / heatCapacity
          : temperature * Math.exp(Math.max(-0.5, Math.min(0.5, error / heatCapacity)));
      next = Math.max(0.5 * temperature, Math.min(1.5 * temperature, next));
      if (!Double.isNaN(boundTemperature)) {
        next = vapor ? Math.max(next, boundTemperature) : Math.min(next, boundTemperature);
      }
      if (Math.abs(next - temperature) < 1.0e-7) {
        return state;
      }
      temperature = next;
    }
    throw new IllegalStateException("state solve for " + name + " did not converge at " + pressure + " bara");
  }

  /**
   * Selects the enthalpy or entropy of a state.
   *
   * @param state state to read
   * @param enthalpy true for enthalpy, false for entropy
   * @return specific enthalpy in J/kg or entropy in J/(kg K)
   */
  private static double value(RefrigerantState state, boolean enthalpy) {
    return enthalpy ? state.getEnthalpy() : state.getEntropy();
  }

  /**
   * Interpolates linearly between two states at the same pressure.
   *
   * @param from state at fraction 0
   * @param to state at fraction 1
   * @param fraction interpolation fraction
   * @return interpolated state
   */
  private static RefrigerantState interpolate(RefrigerantState from, RefrigerantState to, double fraction) {
    double f = Math.max(0.0, Math.min(1.0, fraction));
    return new RefrigerantState(from.getPressure(),
        from.getTemperature() + f * (to.getTemperature() - from.getTemperature()),
        from.getEnthalpy() + f * (to.getEnthalpy() - from.getEnthalpy()),
        from.getEntropy() + f * (to.getEntropy() - from.getEntropy()),
        from.getDensity() + f * (to.getDensity() - from.getDensity()), from.getVaporFraction());
  }

  /**
   * Gets the saturated liquid and vapour states at a temperature, using the cache.
   *
   * @param temperature temperature in K
   * @return array with the saturated liquid at index 0 and the saturated vapour at index 1
   */
  private RefrigerantState[] saturation(double temperature) {
    if (!(temperature > 0.0) || temperature >= criticalTemperature) {
      throw new IllegalArgumentException("temperature " + temperature + " K is outside the two-phase range of " + name
          + " (critical temperature " + criticalTemperature + " K)");
    }
    if (saturationCache == null) {
      saturationCache = new HashMap<Long, RefrigerantState[]>();
    }
    Long key = Long.valueOf(Math.round(temperature * 1.0e7));
    RefrigerantState[] cached = saturationCache.get(key);
    if (cached != null) {
      return cached;
    }
    double pressure = saturationPressureUncached(temperature);
    RefrigerantState liquid = flashRaw(pressure * (1.0 + SATURATION_PRESSURE_OFFSET), temperature, 0.0);
    RefrigerantState vapor = flashRaw(pressure * (1.0 - SATURATION_PRESSURE_OFFSET), temperature, 1.0);
    // Two flashes that land in the same phase differ only by the 0.1 % pressure step, far less than 0.5 % in density.
    if (!(vapor.getEnthalpy() > liquid.getEnthalpy()) || !(vapor.getDensity() < 0.995 * liquid.getDensity())) {
      throw new IllegalStateException("the " + modelName + " model does not resolve separate liquid and vapour phases"
          + " for " + name + " at " + temperature + " K; choose another equation of state");
    }
    // Liquid and vapour must have the same Gibbs energy at saturation. The two states sit 0.05 % in pressure on each
    // side of the line, which accounts for about 0.001 in reduced units; a larger difference means that the
    // bubble-point flash of this model returned a pressure that is not the saturation pressure.
    double gibbsDifference = (vapor.getEnthalpy() - temperature * vapor.getEntropy())
        - (liquid.getEnthalpy() - temperature * liquid.getEntropy());
    double reducedDifference = gibbsDifference * vapor.getDensity() / (pressure * 1.0e5);
    if (Math.abs(reducedDifference) > 0.01) {
      throw new IllegalStateException("the " + modelName + " model returned " + pressure + " bara as the saturation "
          + "pressure of " + name + " at " + temperature + " K, but liquid and vapour are not in equilibrium there"
          + " (reduced Gibbs energy difference " + reducedDifference + "); choose another equation of state");
    }
    RefrigerantState[] states = new RefrigerantState[] {
        new RefrigerantState(pressure, temperature, liquid.getEnthalpy(), liquid.getEntropy(), liquid.getDensity(),
            0.0),
        new RefrigerantState(pressure, temperature, vapor.getEnthalpy(), vapor.getEntropy(), vapor.getDensity(), 1.0)};
    saturationCache.put(key, states);
    return states;
  }

  /**
   * Calculates the saturation pressure with a bubble-point flash.
   *
   * @param temperature temperature in K
   * @return saturation pressure in bara
   */
  private double saturationPressureUncached(double temperature) {
    SystemInterface fluid = working();
    double guess = criticalPressure
        * Math.exp(5.373 * (1.0 + acentricFactor) * (1.0 - criticalTemperature / temperature));
    fluid.setTemperature(temperature);
    fluid.setPressure(Math.max(guess, 1.0e-4));
    try {
      new ThermodynamicOperations(fluid).bubblePointPressureFlash(false);
    } catch (Exception ex) {
      throw new IllegalStateException(
          "saturation pressure of " + name + " could not be calculated at " + temperature + " K", ex);
    }
    double pressure = fluid.getPressure();
    if (!(pressure > 0.0) || Double.isNaN(pressure)) {
      throw new IllegalStateException(
          "saturation pressure of " + name + " could not be calculated at " + temperature + " K");
    }
    return pressure;
  }

  /**
   * Flashes the working fluid in a single phase and checks that the expected phase was found.
   *
   * @param pressure pressure in bara
   * @param temperature temperature in K
   * @param vaporFraction expected vapour fraction, 0 or 1
   * @return single-phase state
   */
  private RefrigerantState flash(double pressure, double temperature, double vaporFraction) {
    // Look the saturation states up first: they use the working fluid that the flash below leaves in its final state.
    double split = Double.NaN;
    if (pressure < criticalPressure) {
      RefrigerantState[] saturated = saturation(getSaturationTemperature(pressure));
      split = 0.5 * (saturated[0].getDensity() + saturated[1].getDensity());
    }
    RefrigerantState state = flashRaw(pressure, temperature, vaporFraction);
    if (!Double.isNaN(split)) {
      boolean vapor = vaporFraction >= 1.0;
      if ((vapor && state.getDensity() > split) || (!vapor && state.getDensity() < split)) {
        throw new IllegalStateException(
            "flash of " + name + " at " + pressure + " bara and " + temperature + " K returned the "
                + (vapor ? "liquid" : "vapour") + " phase where " + (vapor ? "vapour" : "liquid") + " was expected");
      }
    }
    return state;
  }

  /**
   * Flashes the working fluid at pressure and temperature and reads its properties.
   *
   * @param pressure pressure in bara
   * @param temperature temperature in K
   * @param vaporFraction vapour fraction assigned to the result
   * @return state of the stable phase
   */
  private RefrigerantState flashRaw(double pressure, double temperature, double vaporFraction) {
    SystemInterface fluid = working();
    fluid.setTemperature(temperature);
    fluid.setPressure(pressure);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    lastHeatCapacity = fluid.getCp("J/kgK");
    return new RefrigerantState(pressure, temperature, fluid.getEnthalpy("J/kg"), fluid.getEntropy("J/kgK"),
        fluid.getDensity("kg/m3"), vaporFraction);
  }

  /**
   * Gets the working fluid, creating it on first use.
   *
   * @return working fluid
   */
  private SystemInterface working() {
    if (work == null) {
      work = template.clone();
      work.setTotalFlowRate(1.0, "kg/sec");
    }
    return work;
  }
}
