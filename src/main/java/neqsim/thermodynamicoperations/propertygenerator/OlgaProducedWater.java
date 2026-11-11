package neqsim.thermodynamicoperations.propertygenerator;

import java.util.Locale;
import java.util.function.ToDoubleFunction;

import neqsim.thermo.mixingrule.MixingRuleTypeInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Adds produced water to a water-free feed before an OLGA table is generated.
 *
 * <p>
 * Supported specifications: {@code watercut} (volume % water of the total standard liquid), {@code mol/mol} (mol
 * produced water per mol feed), {@code mol%} (mol % of feed plus water) and {@code wt%} (weight % of feed plus water).
 * Produced water is the total water, including water dissolved in the gas and oil phases.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
final class OlgaProducedWater {
  /** Relative tolerance of the water-cut iteration. */
  private static final double WATER_CUT_TOLERANCE = 1.0e-7;

  /** Prevents instantiation. */
  private OlgaProducedWater() {
  }

  /**
   * Normalizes and validates a produced-water specification.
   *
   * @param specification specification name
   * @param value specification value
   * @return normalized specification name
   * @throws IllegalArgumentException if the specification or value is invalid
   */
  static String validate(String specification, double value) {
    if (specification == null) {
      throw new IllegalArgumentException("Produced-water specification must not be null");
    }
    String spec = specification.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    if ("watercut".equals(spec) || "wc".equals(spec) || "watercut%".equals(spec)) {
      spec = "watercut";
    }
    boolean percentage = "watercut".equals(spec) || "mol%".equals(spec) || "wt%".equals(spec);
    if (!percentage && !"mol/mol".equals(spec)) {
      throw new IllegalArgumentException(
          "Produced-water specification must be watercut, mol/mol, mol% or wt%, got " + specification);
    }
    if (!Double.isFinite(value) || value < 0.0 || (percentage && value >= 100.0)) {
      throw new IllegalArgumentException("Produced-water value must be finite, non-negative"
          + (percentage ? " and below 100 %" : "") + ", got " + value);
    }
    return spec;
  }

  /**
   * Returns a copy of the feed with the produced water added.
   *
   * @param source water-free feed, not modified
   * @param specification normalized specification from {@link #validate(String, double)}
   * @param value specification value
   * @param standardTemperature standard temperature for the water cut [K]
   * @param standardPressure standard pressure for the water cut [bara]
   * @param waterDensity standard-condition density model of the aqueous phase [kg/m3], consistent with the table
   * @return feed with produced water
   * @throws IllegalArgumentException if the feed already contains water, or a water cut is requested for a feed without
   * standard-condition oil
   * @throws IllegalStateException if the water component cannot be added or the water cut does not converge
   */
  static SystemInterface apply(SystemInterface source, String specification, double value, double standardTemperature,
      double standardPressure, ToDoubleFunction<PhaseInterface> waterDensity) {
    SystemInterface fluid = source.clone();
    fluid.init(0);
    if (fluid.hasComponent("water")) {
      double existing = fluid.getPhase(0).getComponent("water").getNumberOfmoles();
      if (existing > 1.0e-12 * fluid.getTotalNumberOfMoles()) {
        throw new IllegalArgumentException(
            "The feed already contains water; produced water can only be specified for a water-free feed");
      }
    } else {
      MixingRuleTypeInterface mixingRule = fluid.getMixingRule();
      if (mixingRule == null) {
        throw new IllegalStateException(
            "Set a mixing rule on the feed, or add water with zero moles, before" + " specifying produced water");
      }
      fluid.addComponent("water", 0.0);
      fluid.setMixingRule(mixingRule);
    }
    double feedMoles = fluid.getTotalNumberOfMoles();
    double feedMass = feedMoles * fluid.getMolarMass();
    double waterMolarMass = fluid.getPhase(0).getComponent("water").getMolarMass();
    double waterMoles;
    if ("mol/mol".equals(specification)) {
      waterMoles = value * feedMoles;
    } else if ("mol%".equals(specification)) {
      waterMoles = value / (100.0 - value) * feedMoles;
    } else if ("wt%".equals(specification)) {
      waterMoles = value / (100.0 - value) * feedMass / waterMolarMass;
    } else {
      waterMoles = waterCutMoles(fluid, value / 100.0, waterMolarMass, standardTemperature, standardPressure,
          waterDensity);
    }
    if (waterMoles > 0.0) {
      fluid.addComponent("water", waterMoles);
    }
    fluid.init(0);
    return fluid;
  }

  /**
   * Solves for the water moles that give a standard-condition water cut.
   *
   * @param fluid feed with a zero-mole water component
   * @param waterCut target water volume fraction of the total standard liquid
   * @param waterMolarMass water molar mass [kg/mol]
   * @param temperature standard temperature [K]
   * @param pressure standard pressure [bara]
   * @param waterDensity standard-condition aqueous density model [kg/m3]
   * @return water moles
   */
  private static double waterCutMoles(SystemInterface fluid, double waterCut, double waterMolarMass, double temperature,
      double pressure, ToDoubleFunction<PhaseInterface> waterDensity) {
    if (waterCut == 0.0) {
      return 0.0;
    }
    double[] dry = standardLiquidVolumes(fluid, 0.0, temperature, pressure, waterDensity);
    if (dry[0] <= 0.0) {
      throw new IllegalArgumentException("The feed forms no oil at standard conditions, so a water cut is undefined;"
          + " specify produced water as mol/mol, mol% or wt%");
    }
    double targetRatio = waterCut / (1.0 - waterCut);
    double moles = targetRatio * dry[0] * 999.0 / waterMolarMass;
    for (int iteration = 0; iteration < 50; iteration++) {
      double[] volumes = standardLiquidVolumes(fluid, moles, temperature, pressure, waterDensity);
      if (volumes[1] <= 0.0 || volumes[0] <= 0.0) {
        moles *= 2.0;
        continue;
      }
      double ratio = volumes[1] / volumes[0];
      if (Math.abs(ratio / targetRatio - 1.0) < WATER_CUT_TOLERANCE) {
        return moles;
      }
      moles *= targetRatio / ratio;
    }
    throw new IllegalStateException("Water cut " + waterCut + " did not converge at standard conditions");
  }

  /**
   * Flashes a copy of the feed with added water at standard conditions.
   *
   * @param fluid feed with a water component
   * @param waterMoles water moles to add
   * @param temperature standard temperature [K]
   * @param pressure standard pressure [bara]
   * @param waterDensity standard-condition aqueous density model [kg/m3]
   * @return oil and aqueous phase volumes [m3]
   */
  private static double[] standardLiquidVolumes(SystemInterface fluid, double waterMoles, double temperature,
      double pressure, ToDoubleFunction<PhaseInterface> waterDensity) {
    SystemInterface copy = fluid.clone();
    if (waterMoles > 0.0) {
      copy.addComponent("water", waterMoles);
    }
    copy.setTemperature(temperature);
    copy.setPressure(pressure);
    copy.setMultiPhaseCheck(true);
    new ThermodynamicOperations(copy).TPflash();
    copy.initPhysicalProperties();
    double water = 0.0;
    if (copy.hasPhaseType("aqueous")) {
      PhaseInterface aqueous = copy.getPhaseOfType("aqueous");
      water = aqueous.getMass() / waterDensity.applyAsDouble(aqueous);
    }
    return new double[] {volume(copy, "oil"), water};
  }

  /**
   * Returns a phase volume from mass and density, or zero when the phase is absent.
   *
   * @param system flashed system
   * @param type phase type name
   * @return volume [m3]
   */
  private static double volume(SystemInterface system, String type) {
    if (!system.hasPhaseType(type)) {
      return 0.0;
    }
    PhaseInterface phase = system.getPhaseOfType(type);
    return phase.getMass() / phase.getPhysicalProperties().getDensity();
  }
}
