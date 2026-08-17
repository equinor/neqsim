package neqsim.process.equipment.absorber;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Rigorous counter-current tray absorber based on the {@link DistillationColumn} MESH solver.
 *
 * <p>
 * The column has no condenser or reboiler. Gas enters tray zero and solvent enters the highest numbered tray. Each tray
 * is an equilibrium stage by default. Overall, per-tray, per-component, and per-tray/per-component Murphree vapor
 * efficiencies can be configured. Component-specific corrections preserve the flashed vapor flow and complement the
 * corrected vapor inventory in the liquid phase, so every component remains conserved on the tray.
 * </p>
 *
 * <p>
 * Tray numbers are zero based from bottom to top. Component-specific efficiencies override the inherited overall or
 * per-tray efficiency for that component. Because independently specified component efficiencies do not generally
 * produce mole fractions summing to one, the corrected vapor composition is normalized and limited by the component
 * inventory available on the tray.
 * </p>
 *
 * @author esolbraa
 * @version $Id: $Id
 */
public class AbsorptionColumn extends DistillationColumn {
  private static final long serialVersionUID = 1000L;
  private static final double MOLE_TOLERANCE = 1.0e-15;

  private StreamInterface gasInStream;
  private StreamInterface solventInStream;
  private final Map<String, Double> componentMurphreeEfficiencies = new HashMap<>();
  private final Map<Integer, Map<String, Double>> trayComponentMurphreeEfficiencies = new HashMap<>();

  /**
   * Create a counter-current tray absorber without a condenser or reboiler.
   *
   * @param name equipment name
   * @param numberOfTrays number of actual trays, excluding no terminal equilibrium stages
   */
  public AbsorptionColumn(String name, int numberOfTrays) {
    super(name, requirePositiveTrayCount(numberOfTrays), false, false);
  }

  /**
   * Add the gas feed to the bottom tray.
   *
   * @param stream gas feed
   */
  public void addGasInStream(StreamInterface stream) {
    Objects.requireNonNull(stream, "gas inlet stream");
    if (gasInStream != null && gasInStream != stream) {
      throw new IllegalStateException("The gas inlet has already been assigned");
    }
    if (gasInStream == null) {
      gasInStream = stream;
      addFeedStream(stream, 0);
    }
  }

  /**
   * Add the solvent feed to the top tray.
   *
   * @param stream liquid solvent feed
   */
  public void addSolventInStream(StreamInterface stream) {
    Objects.requireNonNull(stream, "solvent inlet stream");
    if (solventInStream != null && solventInStream != stream) {
      throw new IllegalStateException("The solvent inlet has already been assigned");
    }
    if (solventInStream == null) {
      solventInStream = stream;
      addFeedStream(stream, getNumberOfTrays() - 1);
    }
  }

  /**
   * Get the gas feed.
   *
   * @return gas inlet stream, or {@code null} before assignment
   */
  public StreamInterface getGasInStream() {
    return gasInStream;
  }

  /**
   * Get the solvent feed.
   *
   * @return solvent inlet stream, or {@code null} before assignment
   */
  public StreamInterface getSolventInStream() {
    return solventInStream;
  }

  /**
   * Set a component Murphree efficiency used on trays without a component-specific override.
   *
   * @param componentName component name
   * @param efficiency efficiency from 0.0 to 1.0
   */
  public void setComponentMurphreeEfficiency(String componentName, double efficiency) {
    componentMurphreeEfficiencies.put(normalizeComponentName(componentName), validateEfficiency(efficiency));
    setDoInitializion(true);
  }

  /**
   * Set a component Murphree efficiency for one tray.
   *
   * @param trayNumber zero-based tray number from bottom to top
   * @param componentName component name
   * @param efficiency efficiency from 0.0 to 1.0
   */
  public void setComponentMurphreeEfficiency(int trayNumber, String componentName, double efficiency) {
    validateTrayNumber(trayNumber);
    trayComponentMurphreeEfficiencies.computeIfAbsent(trayNumber, key -> new HashMap<>())
        .put(normalizeComponentName(componentName), validateEfficiency(efficiency));
    setDoInitializion(true);
  }

  /**
   * Get the effective Murphree efficiency for a component on a tray.
   *
   * @param trayNumber zero-based tray number from bottom to top
   * @param componentName component name
   * @return effective component efficiency
   */
  public double getComponentMurphreeEfficiency(int trayNumber, String componentName) {
    validateTrayNumber(trayNumber);
    String normalizedName = normalizeComponentName(componentName);
    Map<String, Double> trayEfficiencies = trayComponentMurphreeEfficiencies.get(trayNumber);
    if (trayEfficiencies != null && trayEfficiencies.containsKey(normalizedName)) {
      return trayEfficiencies.get(normalizedName);
    }
    if (componentMurphreeEfficiencies.containsKey(normalizedName)) {
      return componentMurphreeEfficiencies.get(normalizedName);
    }
    return getMurphreeEfficiency(trayNumber);
  }

  /** Clear all component-specific Murphree efficiencies. */
  public void clearComponentMurphreeEfficiencies() {
    componentMurphreeEfficiencies.clear();
    trayComponentMurphreeEfficiencies.clear();
    setDoInitializion(true);
  }

  /** {@inheritDoc} */
  @Override
  protected void applyMurphreeCorrection(int trayIndex) {
    if (trayIndex < 0 || trayIndex >= getNumberOfTrays()) {
      return;
    }

    SystemInterface equilibriumFluid = getTray(trayIndex).getThermoSystem();
    if (equilibriumFluid == null || equilibriumFluid.getNumberOfPhases() < 2) {
      return;
    }

    SystemInterface inletVaporFluid;
    if (trayIndex == 0) {
      if (gasInStream == null || gasInStream.getThermoSystem() == null) {
        return;
      }
      inletVaporFluid = gasInStream.getThermoSystem();
    } else {
      inletVaporFluid = getTray(trayIndex - 1).getThermoSystem();
    }
    if (inletVaporFluid == null || inletVaporFluid.getNumberOfPhases() < 1) {
      return;
    }

    int numberOfComponents = equilibriumFluid.getPhase(0).getNumberOfComponents();
    double[] correctedMoleFraction = new double[numberOfComponents];
    double[] totalComponentMoles = new double[numberOfComponents];
    boolean correctionRequired = false;
    double correctedMoleFractionSum = 0.0;

    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      String componentName = equilibriumFluid.getPhase(0).getComponent(componentIndex).getName();
      double efficiency = getComponentMurphreeEfficiency(trayIndex, componentName);
      correctionRequired |= efficiency < 1.0 - 1.0e-10;
      double equilibriumMoleFraction = equilibriumFluid.getPhase(0).getComponent(componentIndex).getx();
      ComponentInterface inletComponent = inletVaporFluid.getPhase(0).getComponent(componentName);
      double inletMoleFraction = inletComponent == null ? 0.0 : inletComponent.getx();
      correctedMoleFraction[componentIndex] = Math.max(0.0,
          inletMoleFraction + efficiency * (equilibriumMoleFraction - inletMoleFraction));
      correctedMoleFractionSum += correctedMoleFraction[componentIndex];
      totalComponentMoles[componentIndex] = Math.max(0.0,
          equilibriumFluid.getPhase(0).getComponent(componentIndex).getNumberOfMolesInPhase()
              + equilibriumFluid.getPhase(1).getComponent(componentName).getNumberOfMolesInPhase());
    }
    if (!correctionRequired || correctedMoleFractionSum <= MOLE_TOLERANCE) {
      return;
    }

    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      correctedMoleFraction[componentIndex] /= correctedMoleFractionSum;
    }

    double vaporMoles = equilibriumFluid.getPhase(0).getNumberOfMolesInPhase();
    double liquidMoles = equilibriumFluid.getPhase(1).getNumberOfMolesInPhase();
    double[] vaporComponentMoles = allocateVaporMoles(correctedMoleFraction, totalComponentMoles, vaporMoles);

    SystemInterface gasSystem = equilibriumFluid.phaseToSystem(0);
    SystemInterface liquidSystem = equilibriumFluid.phaseToSystem(1);
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      double gasMoles = vaporComponentMoles[componentIndex];
      double liquidComponentMoles = Math.max(0.0, totalComponentMoles[componentIndex] - gasMoles);
      setComponentInventory(gasSystem, componentIndex, gasMoles, vaporMoles);
      setComponentInventory(liquidSystem, componentIndex, liquidComponentMoles, liquidMoles);
    }
    gasSystem.setTotalNumberOfMoles(vaporMoles);
    liquidSystem.setTotalNumberOfMoles(liquidMoles);
    gasSystem.init(0);
    gasSystem.init(1);
    liquidSystem.init(0);
    liquidSystem.init(1);

    getTray(trayIndex).setCachedGasOutStream(new Stream(getName() + " tray " + trayIndex + " gas", gasSystem));
    getTray(trayIndex).setCachedLiquidOutStream(new Stream(getName() + " tray " + trayIndex + " liquid", liquidSystem));
  }

  private static double[] allocateVaporMoles(double[] moleFraction, double[] availableMoles, double vaporMoles) {
    double[] allocatedMoles = new double[moleFraction.length];
    boolean[] fixed = new boolean[moleFraction.length];
    double remainingMoles = vaporMoles;

    for (int iteration = 0; iteration < moleFraction.length && remainingMoles > MOLE_TOLERANCE; iteration++) {
      double remainingWeight = 0.0;
      for (int componentIndex = 0; componentIndex < moleFraction.length; componentIndex++) {
        if (!fixed[componentIndex]) {
          remainingWeight += moleFraction[componentIndex];
        }
      }

      boolean limitedComponentFound = false;
      for (int componentIndex = 0; componentIndex < moleFraction.length; componentIndex++) {
        if (fixed[componentIndex]) {
          continue;
        }
        double trialMoles = remainingWeight > MOLE_TOLERANCE
            ? remainingMoles * moleFraction[componentIndex] / remainingWeight
            : remainingMoles * availableMoles[componentIndex] / sumAvailableMoles(availableMoles, fixed);
        if (trialMoles > availableMoles[componentIndex] + MOLE_TOLERANCE) {
          allocatedMoles[componentIndex] = availableMoles[componentIndex];
          remainingMoles -= allocatedMoles[componentIndex];
          fixed[componentIndex] = true;
          limitedComponentFound = true;
        }
      }

      if (!limitedComponentFound) {
        for (int componentIndex = 0; componentIndex < moleFraction.length; componentIndex++) {
          if (!fixed[componentIndex]) {
            allocatedMoles[componentIndex] = remainingWeight > MOLE_TOLERANCE
                ? remainingMoles * moleFraction[componentIndex] / remainingWeight
                : remainingMoles * availableMoles[componentIndex] / sumAvailableMoles(availableMoles, fixed);
          }
        }
        remainingMoles = 0.0;
      }
    }
    return allocatedMoles;
  }

  private static double sumAvailableMoles(double[] availableMoles, boolean[] fixed) {
    double sum = 0.0;
    for (int componentIndex = 0; componentIndex < availableMoles.length; componentIndex++) {
      if (!fixed[componentIndex]) {
        sum += availableMoles[componentIndex];
      }
    }
    return Math.max(sum, MOLE_TOLERANCE);
  }

  private static void setComponentInventory(SystemInterface system, int componentIndex, double componentMoles,
      double phaseMoles) {
    system.getPhase(0).getComponent(componentIndex)
        .setx(phaseMoles > MOLE_TOLERANCE ? componentMoles / phaseMoles : 0.0);
    system.getPhase(0).getComponent(componentIndex).setNumberOfMolesInPhase(componentMoles);
    system.getPhase(0).getComponent(componentIndex).setNumberOfmoles(componentMoles);
  }

  private void validateTrayNumber(int trayNumber) {
    if (trayNumber < 0 || trayNumber >= getNumberOfTrays()) {
      throw new IndexOutOfBoundsException("tray index " + trayNumber + " out of range [0, " + getNumberOfTrays() + ")");
    }
  }

  private static int requirePositiveTrayCount(int numberOfTrays) {
    if (numberOfTrays < 1) {
      throw new IllegalArgumentException("An absorption column requires at least one tray");
    }
    return numberOfTrays;
  }

  private static double validateEfficiency(double efficiency) {
    if (!Double.isFinite(efficiency) || efficiency < 0.0 || efficiency > 1.0) {
      throw new IllegalArgumentException("Murphree efficiency must be finite and in the range 0.0 to 1.0");
    }
    return efficiency;
  }

  private static String normalizeComponentName(String componentName) {
    Objects.requireNonNull(componentName, "component name");
    String normalizedName = componentName.trim().toLowerCase(Locale.ROOT);
    if (normalizedName.isEmpty()) {
      throw new IllegalArgumentException("component name cannot be empty");
    }
    return normalizedName;
  }
}
