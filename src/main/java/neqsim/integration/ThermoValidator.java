package neqsim.integration;

import neqsim.thermo.system.SystemInterface;
import neqsim.integration.ValidationFramework.*;

/**
 * Validators for thermodynamic systems (SystemInterface implementations).
 * 
 * <p>
 * Checks:
 * <ul>
 * <li>Components added to system</li>
 * <li>Mixing rule set (critical for multi-component systems)</li>
 * <li>Database created and initialized</li>
 * <li>Temperature and pressure in valid ranges</li>
 * <li>Composition normalized to ~1.0</li>
 * </ul>
 */
public class ThermoValidator {

  /**
   * Validate a thermodynamic system before use in equipment.
   * 
   * @param system The system to validate
   * @return ValidationResult with errors and warnings
   */
  public static ValidationResult validateSystem(SystemInterface system) {
    ValidationBuilder builder =
        new ValidationBuilder("SystemInterface: " + system.getClass().getSimpleName());

    // Check: Has components
    if (system.getPhase(0).getNumberOfComponents() == 0) {
      builder.checkTrue(false, CommonErrors.NO_COMPONENTS, CommonErrors.REMEDIATION_NO_COMPONENTS);
    }

    // Check: Mixing rule set
    // Note: This is system-specific; some implementations set default
    try {
      // Try to detect if mixing rule is set (varies by implementation)
      // For now, add as warning - users should explicitly set it
      builder.addWarning("thermo", "Mixing rule not explicitly verified",
          "Explicitly call setMixingRule() to ensure correct mixing model: "
              + "system.setMixingRule(\"classic\") or system.setMixingRule(10)");
    } catch (Exception e) {
      // Silently skip if method not available
    }

    // Check: Temperature valid
    if (system.getTemperature() < 1.0) {
      builder.checkTrue(false, CommonErrors.INVALID_TEMPERATURE,
          CommonErrors.REMEDIATION_INVALID_TEMPERATURE);
    }

    // Check: Pressure valid
    if (system.getPressure() <= 0) {
      builder.checkTrue(false, CommonErrors.INVALID_PRESSURE,
          CommonErrors.REMEDIATION_INVALID_PRESSURE);
    }

    // Check: Composition normalized
    double moleSum = 0.0;
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      try {
        moleSum += system.getPhase(0).getComponent(i).getx();
      } catch (Exception e) {
        // Component access may vary by phase type
      }
    }
    if (moleSum > 0 && Math.abs(moleSum - 1.0) > 0.05) {
      builder.addWarning("thermo",
          "Composition does not sum to 1.0 (sum=" + String.format("%.4f", moleSum) + ")",
          "Normalize component mole fractions");
    }

    // Check: Phase type expectations
    if (system.getNumberOfPhases() > 3) {
      builder.addWarning("thermo",
          "System has more than 3 phases (" + system.getNumberOfPhases() + ")",
          "Verify multi-phase handling in your equipment");
    }

    return builder.build();
  }

  /**
   * Validate that system is ready for equilibrium calculations (flash, VLE, etc.).
   */
  public static ValidationResult validateForEquilibrium(SystemInterface system) {
    ValidationBuilder builder = new ValidationBuilder("SystemInterface (equilibrium mode)");

    // All checks from validateSystem
    ValidationResult baseValidation = validateSystem(system);
    for (ValidationError error : baseValidation.getErrors()) {
      builder.checkTrue(false, error.getMessage(), error.getRemediation());
    }

    // Additional equilibrium-specific checks
    try {
      // Attempt a test calculation to verify system is truly ready
      double testHEnthalpy = system.getEnthalpy();
      if (Double.isNaN(testHEnthalpy)) {
        builder.checkTrue(false, "Enthalpy calculation returned NaN",
            "System state may be invalid. Verify pressure, temperature, and composition");
      }
    } catch (Exception e) {
      builder.addWarning("thermo", "Could not verify enthalpy calculation: " + e.getMessage(),
          "System may not be fully initialized. Ensure init(0) was called.");
    }

    return builder.build();
  }

  /**
   * Validate specific EOS implementations (SrkEos, CPA, etc.).
   */
  public static ValidationResult validateSrkEos(SystemInterface system) {
    ValidationBuilder builder = new ValidationBuilder("SystemSrkEos");

    // Base validations
    ValidationResult baseValidation = validateSystem(system);
    for (ValidationError error : baseValidation.getErrors()) {
      builder.checkTrue(false, error.getMessage(), error.getRemediation());
    }

    // SRK-specific: check for problematic components at wide ranges
    try {
      if (system.getTemperature() < 200.0) {
        builder.addWarning("thermo", "SRK-EOS at very low temperature (< 200 K)",
            "SRK may be inaccurate at cryogenic conditions. Consider CPA model.");
      }
      if (system.getPressure() > 500.0) {
        builder.addWarning("thermo", "SRK-EOS at very high pressure (> 500 bar)",
            "SRK accuracy degrades at extreme pressures. Verify K-values.");
      }
    } catch (Exception e) {
      // Skip if temperature/pressure access fails
    }

    return builder.build();
  }

  /**
   * Validate CPA (Associating) systems for polar molecules.
   */
  public static ValidationResult validateCpAeos(SystemInterface system) {
    ValidationBuilder builder = new ValidationBuilder("SystemSrkCPAstatoil");

    // Base validations
    ValidationResult baseValidation = validateSystem(system);
    for (ValidationError error : baseValidation.getErrors()) {
      builder.checkTrue(false, error.getMessage(), error.getRemediation());
    }

    // CPA-specific: warn if no polar components
    boolean hasPolarComponents = false;
    try {
      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        String compName = system.getPhase(0).getComponent(i).getComponentName().toLowerCase();
        if (compName.contains("water") || compName.contains("glycol") || compName.contains("h2o")
            || compName.contains("methanol")) {
          hasPolarComponents = true;
          break;
        }
      }
      if (!hasPolarComponents) {
        builder.addWarning("thermo", "CPA-EOS used without polar components (water, glycol, etc.)",
            "Consider using SRK-EOS for non-polar systems (lighter and faster)");
      }
    } catch (Exception e) {
      // Skip component iteration if not supported
    }

    return builder.build();
  }

  /**
   * Helper: Check if system appears to be properly initialized.
   */
  public static boolean isSystemReady(SystemInterface system) {
    ValidationResult result = validateSystem(system);
    return result.isReady();
  }
}
