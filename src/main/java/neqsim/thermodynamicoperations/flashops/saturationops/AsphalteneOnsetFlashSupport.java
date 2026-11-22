package neqsim.thermodynamicoperations.flashops.saturationops;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Shared input and phase-evidence checks for the existing asphaltene onset searches.
 *
 * @author Even Solbraa
 * @version 1.0
 */
final class AsphalteneOnsetFlashSupport {
  /** Prevent construction of this stateless helper. */
  private AsphalteneOnsetFlashSupport() {
  }

  /**
   * Validate a finite positive search coordinate or step.
   *
   * @param value supplied value
   * @param name quantity name for the diagnostic
   */
  static void requirePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  /**
   * Require actual characterized inventory and enable its exact component names, including TBP aliases.
   *
   * @param system fluid supplied by the caller
   */
  static void prepare(SystemInterface system) {
    requirePositive(system.getTemperature(), "Temperature");
    requirePositive(system.getPressure(), "Pressure");
    List<String> names = new ArrayList<String>();
    for (int c = 0; c < system.getNumberOfComponents(); c++) {
      String name = system.getPhase(0).getComponent(c).getComponentName();
      double moles = system.getPhase(0).getComponent(c).getNumberOfmoles();
      if (isAsphaltene(name) && Double.isFinite(moles) && moles > 0.0) {
        names.add(name);
      }
    }
    if (names.isEmpty()) {
      throw new IllegalArgumentException("Asphaltene onset requires a characterized asphaltene component "
          + "with positive inventory; a generic heavy TBP fraction is insufficient");
    }
    for (String name : names) {
      system.setSolidPhaseCheck(name);
    }
  }

  /**
   * Run the existing TP flash without converting a failed evaluation into stability evidence.
   *
   * @param system current trial fluid
   * @param operations operations on that fluid
   */
  static void flash(SystemInterface system, ThermodynamicOperations operations) {
    try {
      operations.TPflash();
    } catch (RuntimeException failure) {
      throw new IllegalStateException(
          "Asphaltene onset TP flash failed at " + system.getTemperature() + " K and " + system.getPressure() + " bara",
          failure);
    }
  }

  /**
   * Check both solid and existing liquid-asphaltene routes using actual phase inventory.
   *
   * @param system evaluated fluid
   * @return true when a material asphaltene-rich precipitate exists
   */
  static boolean hasPrecipitate(SystemInterface system) {
    for (int p = 0; p < system.getNumberOfPhases(); p++) {
      PhaseInterface phase = system.getPhase(p);
      if (phase.getType() != PhaseType.ASPHALTENE && phase.getType() != PhaseType.LIQUID_ASPHALTENE
          && phase.getType() != PhaseType.SOLID && !phase.isAsphalteneRich()) {
        continue;
      }
      for (int c = 0; c < phase.getNumberOfComponents(); c++) {
        if (isAsphaltene(phase.getComponent(c).getComponentName())
            && phase.getComponent(c).getNumberOfMolesInPhase() > 1e-10) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Retain the established component-name convention independently of the machine locale.
   *
   * @param name component name
   * @return true when the name identifies an asphaltene fraction
   */
  private static boolean isAsphaltene(String name) {
    return name.toLowerCase(Locale.ROOT).contains("asphalten");
  }
}
