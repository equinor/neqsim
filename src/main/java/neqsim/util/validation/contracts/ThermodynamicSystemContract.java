package neqsim.util.validation.contracts;

import neqsim.thermo.system.SystemInterface;
import neqsim.util.validation.ValidationResult;

/**
 * Contract for thermodynamic systems.
 * 
 * <p>
 * Defines requirements and guarantees for {@link SystemInterface} implementations. AI agents can
 * use this contract to validate fluid setup before running simulations.
 * </p>
 * 
 * <h2>Preconditions (what the system needs):</h2>
 * <ul>
 * <li>At least one component defined</li>
 * <li>Temperature &gt; 0 K</li>
 * <li>Pressure &gt; 0 bar</li>
 * <li>Mixing rule set for multi-component systems</li>
 * <li>Total moles &gt; 0</li>
 * </ul>
 * 
 * <h2>Postconditions (what init() provides):</h2>
 * <ul>
 * <li>Valid phase fractions (sum to 1.0)</li>
 * <li>Finite compressibility factor</li>
 * <li>Finite enthalpy and entropy</li>
 * </ul>
 * 
 * @author NeqSim
 * @version 1.0
 */
public class ThermodynamicSystemContract implements ModuleContract<SystemInterface> {
  /** Singleton instance. */
  private static final ThermodynamicSystemContract INSTANCE = new ThermodynamicSystemContract();

  /** Minimum valid temperature in Kelvin. */
  private static final double MIN_TEMPERATURE_K = 1.0;

  /** Minimum valid pressure in bar. */
  private static final double MIN_PRESSURE_BAR = 1e-10;

  private ThermodynamicSystemContract() {}

  /**
   * Get the singleton instance.
   * 
   * @return contract instance
   */
  public static ThermodynamicSystemContract getInstance() {
    return INSTANCE;
  }

  @Override
  public String getContractName() {
    return "ThermodynamicSystemContract";
  }

  @Override
  public ValidationResult checkPreconditions(SystemInterface system) {
    ValidationResult result = new ValidationResult("ThermodynamicSystem:" + system.getFluidName());

    // Check: Has components
    if (system.getNumberOfComponents() == 0) {
      result.addError("thermo.components", "No components defined in the system",
          "Add components using system.addComponent(\"methane\", 1.0)");
    }

    // Check: Valid temperature
    if (system.getTemperature() < MIN_TEMPERATURE_K) {
      result
          .addError(
              "thermo.temperature", "Temperature too low: " + system.getTemperature() + " K (min: "
                  + MIN_TEMPERATURE_K + " K)",
              "Set temperature: system.setTemperature(298.15, \"K\")");
    }

    // Check: Valid pressure
    if (system.getPressure() <= MIN_PRESSURE_BAR) {
      result.addError("thermo.pressure",
          "Pressure must be positive: " + system.getPressure() + " bar",
          "Set pressure: system.setPressure(1.0, \"bar\")");
    }

    // Check: Mixing rule for multi-component
    if (system.getNumberOfComponents() > 1) {
      String mixingRule = system.getMixingRuleName();
      if (mixingRule == null || mixingRule.isEmpty()) {
        result.addWarning("thermo.mixingRule", "No mixing rule set for multi-component system",
            "Set mixing rule: system.setMixingRule(\"classic\") or system.setMixingRule(2)");
      }
    }

    // Check: Total moles
    if (system.getTotalNumberOfMoles() <= 0) {
      result.addError("thermo.moles",
          "Total moles must be positive: " + system.getTotalNumberOfMoles(),
          "Ensure addComponent() has positive mole values");
    }

    return result;
  }

  @Override
  public ValidationResult checkPostconditions(SystemInterface system) {
    ValidationResult result =
        new ValidationResult("ThermodynamicSystem:" + system.getFluidName() + " (post-run)");

    // Check: Number of phases
    if (system.getNumberOfPhases() < 1) {
      result.addError("thermo.phases", "No phases detected after calculation",
          "Run TPflash() or check input conditions");
    }

    // Check: Phase fractions sum to ~1.0
    double phaseSum = 0;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      phaseSum += system.getBeta(i);
    }
    if (Math.abs(phaseSum - 1.0) > 0.01) {
      result.addWarning("thermo.phaseFraction",
          "Phase fractions sum to " + phaseSum + " (expected 1.0)",
          "Check flash calculation convergence");
    }

    // Check: Finite compressibility
    try {
      double z = system.getZ();
      if (Double.isNaN(z) || Double.isInfinite(z)) {
        result.addError("thermo.compressibility", "Compressibility factor is NaN/Infinite",
            "Check equation of state parameters or input conditions");
      }
    } catch (Exception e) {
      result.addWarning("thermo.compressibility",
          "Could not retrieve compressibility: " + e.getMessage(), "Ensure init() was called");
    }

    return result;
  }

  @Override
  public String getRequirementsDescription() {
    return "ThermodynamicSystem Requirements:\n" + "- At least 1 component (addComponent)\n"
        + "- Temperature > 0 K\n" + "- Pressure > 0 bar\n"
        + "- Mixing rule set for multi-component systems\n" + "- Total moles > 0";
  }

  @Override
  public String getProvidesDescription() {
    return "ThermodynamicSystem Provides (after init/TPflash):\n" + "- Phase fractions (getBeta)\n"
        + "- Compressibility factor (getZ)\n" + "- Density, enthalpy, entropy\n"
        + "- Fugacity coefficients\n" + "- Component properties in each phase";
  }
}
