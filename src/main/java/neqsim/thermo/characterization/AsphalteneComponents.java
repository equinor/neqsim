package neqsim.thermo.characterization;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Shared helpers for recognising asphaltene pseudo-components and asphaltene-rich phases.
 *
 * <p>
 * All asphaltene code in NeqSim (onset flashes, Pedersen and SARA characterization, the solid model, parameter
 * fitting and the phase-type checks) uses these helpers so that a component or phase that one class treats as
 * asphaltene is treated the same way everywhere.
 * </p>
 *
 * <p>
 * A component is an asphaltene when its name (case-insensitive) contains {@code "asphalten"} or starts with
 * {@code "asph_"} (the prefix used by older versions of
 * {@link PedersenAsphalteneCharacterization#addDistributedAsphaltene}). A phase is asphaltene-rich when asphaltene
 * components make up more than half of its <b>mass</b>. A mole-fraction test is not used because asphaltene
 * pseudo-components are 5-20 times heavier than the average oil molecule: a dense asphaltene-rich liquid can be
 * mostly asphaltene by mass but well under half by moles.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public final class AsphalteneComponents {
  /** Mass-fraction limit above which a phase counts as asphaltene-rich. */
  public static final double ASPHALTENE_RICH_MASS_FRACTION = 0.5;

  private AsphalteneComponents() {}

  /**
   * Tests whether a component name denotes an asphaltene pseudo-component.
   *
   * @param name component name, may be null
   * @return true if the name contains "asphalten" or starts with "asph_" (case-insensitive)
   */
  public static boolean isAsphaltene(String name) {
    if (name == null) {
      return false;
    }
    String lower = name.toLowerCase(Locale.ROOT);
    return lower.contains("asphalten") || lower.startsWith("asph_");
  }

  /**
   * Tests whether a component is an asphaltene pseudo-component.
   *
   * @param component the component, may be null
   * @return true if the component name denotes an asphaltene
   */
  public static boolean isAsphaltene(ComponentInterface component) {
    return component != null && isAsphaltene(component.getComponentName());
  }

  /**
   * Returns the component indices of all asphaltene components in a fluid.
   *
   * @param system the fluid
   * @return list of component indices (may be empty)
   */
  public static List<Integer> indices(SystemInterface system) {
    List<Integer> result = new ArrayList<Integer>();
    PhaseInterface phase = system.getPhase(0);
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      if (isAsphaltene(phase.getComponent(i))) {
        result.add(i);
      }
    }
    return result;
  }

  /**
   * Returns the index of the first asphaltene component, or -1 if there is none.
   *
   * @param system the fluid
   * @return component index or -1
   */
  public static int firstIndex(SystemInterface system) {
    List<Integer> found = indices(system);
    return found.isEmpty() ? -1 : found.get(0);
  }

  /**
   * Returns every allocated phase object of a fluid, including phases that are not present after the latest flash.
   *
   * <p>
   * Component parameters (molar mass, association energy, interaction parameters) must be set on all of these, not
   * only on the first {@code getNumberOfPhases()} phases, otherwise a later flash that creates a new phase uses stale
   * database values.
   * </p>
   *
   * @param system the fluid
   * @return list of non-null phase objects
   */
  public static List<PhaseInterface> allPhases(SystemInterface system) {
    List<PhaseInterface> result = new ArrayList<PhaseInterface>();
    PhaseInterface[] phases = system.getPhases();
    if (phases != null) {
      for (PhaseInterface phase : phases) {
        if (phase != null && !containsSameInstance(result, phase)) {
          result.add(phase);
        }
      }
    }
    return result;
  }

  /**
   * Identity-based membership test (phases override equals to compare contents).
   *
   * @param list list of phases
   * @param phase phase to look for
   * @return true if the same object is already in the list
   */
  private static boolean containsSameInstance(List<PhaseInterface> list, PhaseInterface phase) {
    for (PhaseInterface existing : list) {
      if (existing == phase) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the mass fraction of asphaltene components in a phase.
   *
   * @param phase the phase
   * @return asphaltene mass fraction between 0 and 1
   */
  public static double massFraction(PhaseInterface phase) {
    double total = 0.0;
    double asphaltene = 0.0;
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      ComponentInterface component = phase.getComponent(i);
      double mass = component.getx() * component.getMolarMass();
      if (!(mass > 0.0) || !Double.isFinite(mass)) {
        continue;
      }
      total += mass;
      if (isAsphaltene(component)) {
        asphaltene += mass;
      }
    }
    return total > 0.0 ? asphaltene / total : 0.0;
  }

  /**
   * Tests whether asphaltene components make up more than half of the phase mass.
   *
   * @param phase the phase
   * @return true if the asphaltene mass fraction exceeds {@link #ASPHALTENE_RICH_MASS_FRACTION}
   */
  public static boolean isAsphalteneRichByMass(PhaseInterface phase) {
    return massFraction(phase) > ASPHALTENE_RICH_MASS_FRACTION;
  }
}
