package neqsim.thermodynamicoperations.flashops.saturationops;

import java.util.ArrayList;
import java.util.List;
import neqsim.thermo.characterization.AsphalteneComponents;
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
      if (!isPrecipitatePhase(phase)) {
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
   * Tests whether a component name denotes an asphaltene.
   *
   * @param name component name
   * @return true for asphaltene pseudo-components
   */
  static boolean isAsphaltene(String name) {
    return AsphalteneComponents.isAsphaltene(name);
  }

  /**
   * Tests whether a phase holds precipitated asphaltene (an asphaltene, liquid-asphaltene or solid phase, or a phase
   * that is asphaltene-rich by mass).
   *
   * @param phase the phase
   * @return true if the phase is a precipitate phase
   */
  static boolean isPrecipitatePhase(PhaseInterface phase) {
    return phase.getType() == PhaseType.ASPHALTENE || phase.getType() == PhaseType.LIQUID_ASPHALTENE
        || phase.getType() == PhaseType.SOLID || phase.isAsphalteneRich();
  }

  /**
   * Returns the fraction of the total asphaltene inventory (summed over all asphaltene components) that sits in
   * precipitate phases.
   *
   * @param system flashed fluid
   * @return precipitated fraction between 0 and 1
   */
  static double precipitatedFraction(SystemInterface system) {
    double total = 0.0;
    double precipitated = 0.0;
    for (int c = 0; c < system.getPhase(0).getNumberOfComponents(); c++) {
      if (!isAsphaltene(system.getPhase(0).getComponent(c).getComponentName())) {
        continue;
      }
      total += system.getPhase(0).getComponent(c).getz() * system.getTotalNumberOfMoles();
      for (int p = 0; p < system.getNumberOfPhases(); p++) {
        PhaseInterface phase = system.getPhase(p);
        if (isPrecipitatePhase(phase)) {
          precipitated += phase.getComponent(c).getNumberOfMolesInPhase();
        }
      }
    }
    return total > 1e-20 ? Math.min(1.0, precipitated / total) : 0.0;
  }
}
