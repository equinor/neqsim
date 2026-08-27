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
  private static final double DEFAULT_MAX_ALLOWABLE_FS_FACTOR = 3.0;
  private static final double DEFAULT_MAX_ALLOWABLE_GAS_LOAD_FACTOR = 0.15;
  private static final double DEFAULT_LIQUID_DENSITY = 1000.0;
  private static final double MIN_LIQUID_GAS_DENSITY_DIFFERENCE = 10.0;

  private StreamInterface gasInStream;
  private StreamInterface solventInStream;
  private final Map<String, Double> componentMurphreeEfficiencies = new HashMap<>();
  private final Map<Integer, Map<String, Double>> trayComponentMurphreeEfficiencies = new HashMap<>();
  private double maxAllowableGasLoadFactor = DEFAULT_MAX_ALLOWABLE_GAS_LOAD_FACTOR;

  /**
   * Create a counter-current tray absorber without a condenser or reboiler.
   *
   * @param name equipment name
   * @param numberOfTrays number of actual trays, excluding no terminal equilibrium stages
   */
  public AbsorptionColumn(String name, int numberOfTrays) {
    super(name, requirePositiveTrayCount(numberOfTrays), false, false);
    setMaxAllowableFsFactor(DEFAULT_MAX_ALLOWABLE_FS_FACTOR);
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
   * Calculates the superficial gas velocity based on the gas outlet stream and the column internal diameter.
   *
   * @return superficial gas velocity in m/s, or 0 if the gas outlet stream or internal diameter are not available
   */
  public double getGasSuperficialVelocity() {
    if (getGasOutStream() == null || getGasOutStream().getThermoSystem() == null) {
      return 0.0;
    }
    double intArea = Math.PI * getInternalDiameter() * getInternalDiameter() / 4.0;
    if (intArea <= 0.0) {
      return 0.0;
    }
    return getGasOutStream().getThermoSystem().getFlowRate("m3/sec") / intArea;
  }

  /**
   * Calculates the Souders-Brown gas load factor (K-factor) for the contactor.
   *
   * <p>
   * The gas load factor is defined as {@code Ks = Vs * sqrt(rho_gas / (rho_liquid - rho_gas))} where {@code Vs} is the
   * superficial gas velocity (m/s), {@code rho_gas} is the gas outlet density (kg/m3) and {@code rho_liquid} is the
   * liquid outlet density (kg/m3). This is the standard entrainment/flooding capacity indicator used across NeqSim
   * separators and scrubbers.
   * </p>
   *
   * @return gas load factor in m/s, or 0 if the gas or liquid outlet streams are not initialized
   */
  public double getGasLoadFactor() {
    double vs = getGasSuperficialVelocity();
    if (vs <= 0.0 || getLiquidOutStream() == null || getLiquidOutStream().getThermoSystem() == null) {
      return 0.0;
    }
    getGasOutStream().getThermoSystem().initPhysicalProperties();
    getLiquidOutStream().getThermoSystem().initPhysicalProperties();
    double gasDensity = getGasOutStream().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
    double liquidDensity = getLiquidOutStream().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
    if (liquidDensity - gasDensity < MIN_LIQUID_GAS_DENSITY_DIFFERENCE) {
      liquidDensity = DEFAULT_LIQUID_DENSITY;
    }
    return vs * Math.sqrt(gasDensity / (liquidDensity - gasDensity));
  }

  /**
   * Gets the maximum allowable gas load factor (Souders-Brown Ks) used as the design basis for the gas-load-factor
   * capacity constraint.
   *
   * @return maximum allowable gas load factor in m/s
   */
  public double getMaxAllowableGasLoadFactor() {
    return maxAllowableGasLoadFactor;
  }

  /**
   * Sets the maximum allowable gas load factor (Souders-Brown Ks) used as the design basis for the gas-load-factor
   * capacity constraint.
   *
   * <p>
   * Re-initializes the gas-load-factor capacity constraint so the new design value takes effect immediately.
   * </p>
   *
   * @param maxAllowableGasLoadFactor maximum allowable gas load factor in m/s; must be positive
   * @throws IllegalArgumentException if the value is not positive and finite
   */
  public void setMaxAllowableGasLoadFactor(double maxAllowableGasLoadFactor) {
    if (!Double.isFinite(maxAllowableGasLoadFactor) || maxAllowableGasLoadFactor <= 0.0) {
      throw new IllegalArgumentException("maxAllowableGasLoadFactor must be positive and finite");
    }
    this.maxAllowableGasLoadFactor = maxAllowableGasLoadFactor;
    reinitializeGasLoadFactorConstraint();
  }

  /**
   * Calculates the gas load factor utilization as a fraction of the maximum allowable gas load factor.
   *
   * @return utilization ratio (0.0-1.0+); values above 1.0 indicate the design limit is exceeded
   */
  public double getGasLoadFactorUtilization() {
    double maxKs = getMaxAllowableGasLoadFactor();
    if (maxKs <= 0.0) {
      return 0.0;
    }
    return getGasLoadFactor() / maxKs;
  }

  /**
   * Checks whether the current gas load factor is within the design limit.
   *
   * @return true if the gas load factor is within the maximum allowable limit
   */
  public boolean isGasLoadFactorWithinDesignLimit() {
    return getGasLoadFactor() <= getMaxAllowableGasLoadFactor();
  }

  /**
   * Calculates the minimum vessel internal diameter required to keep the gas load factor at or below the maximum
   * allowable value for the current gas and liquid outlet conditions.
   *
   * <p>
   * From {@code Ks = Vs * sqrt(rho_gas / (rho_liquid - rho_gas))} and {@code Vs = Q / A}, the minimum diameter is
   * {@code D_min = sqrt(4 * Q / (pi * Ks_max * sqrt((rho_liquid - rho_gas) / rho_gas)))}.
   * </p>
   *
   * @return minimum internal diameter in metres, or 0 if the gas or liquid outlet streams are not initialized
   */
  public double getMinimumDiameterForGasLoadLimit() {
    if (getGasOutStream() == null || getGasOutStream().getThermoSystem() == null || getLiquidOutStream() == null
        || getLiquidOutStream().getThermoSystem() == null) {
      return 0.0;
    }
    double maxKs = getMaxAllowableGasLoadFactor();
    if (maxKs <= 0.0) {
      return 0.0;
    }
    getGasOutStream().getThermoSystem().initPhysicalProperties();
    getLiquidOutStream().getThermoSystem().initPhysicalProperties();
    double gasFlowM3s = getGasOutStream().getThermoSystem().getFlowRate("m3/sec");
    double gasDensity = getGasOutStream().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
    double liquidDensity = getLiquidOutStream().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
    if (liquidDensity - gasDensity < MIN_LIQUID_GAS_DENSITY_DIFFERENCE) {
      liquidDensity = DEFAULT_LIQUID_DENSITY;
    }
    double vsMax = maxKs * Math.sqrt((liquidDensity - gasDensity) / gasDensity);
    if (vsMax <= 0.0) {
      return 0.0;
    }
    return Math.sqrt(4.0 * gasFlowM3s / (Math.PI * vsMax));
  }

  /**
   * Calculates the liquid wetting rate on the column cross-section.
   *
   * @return wetting rate in m3/hr per m2, or 0 if the liquid outlet stream is not initialized
   */
  public double getWettingRate() {
    if (getLiquidOutStream() == null || getLiquidOutStream().getThermoSystem() == null) {
      return 0.0;
    }
    double intArea = Math.PI * getInternalDiameter() * getInternalDiameter() / 4.0;
    if (intArea <= 0.0) {
      return 0.0;
    }
    return getLiquidOutStream().getThermoSystem().getFlowRate("m3/hr") / intArea;
  }

  /**
   * Sets up the default capacity constraints for the absorption column.
   *
   * <p>
   * Adds the inherited Fs-factor constraint (see {@link DistillationColumn#initializeDefaultConstraints()}) plus a
   * gas-load-factor (Souders-Brown Ks) constraint based on {@link #getGasLoadFactor()} and
   * {@link #getMaxAllowableGasLoadFactor()}.
   * </p>
   */
  @Override
  protected void initializeDefaultConstraints() {
    super.initializeDefaultConstraints();
    neqsim.process.equipment.capacity.CapacityConstraint gasLoadConstraint = new neqsim.process.equipment.capacity.CapacityConstraint(
        "gasLoadFactor", "m/s", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT);
    gasLoadConstraint.setDesignValue(maxAllowableGasLoadFactor);
    gasLoadConstraint.setMaxValue(maxAllowableGasLoadFactor);
    gasLoadConstraint.setSeverity(neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity.SOFT);
    gasLoadConstraint.setDescription("Column gas load factor (Souders-Brown Ks) vs maximum allowable");
    gasLoadConstraint.setDataSource("equipment");
    gasLoadConstraint.setValueSupplier(this::getGasLoadFactor);
    addCapacityConstraint(gasLoadConstraint);
  }

  /**
   * Rebuilds the gas-load-factor capacity constraint so an updated {@link #maxAllowableGasLoadFactor} design value
   * takes effect.
   */
  private void reinitializeGasLoadFactorConstraint() {
    neqsim.process.equipment.capacity.CapacityConstraint existing = getCapacityConstraints().get("gasLoadFactor");
    if (existing != null) {
      existing.setDesignValue(maxAllowableGasLoadFactor);
      existing.setMaxValue(maxAllowableGasLoadFactor);
    } else {
      initializeDefaultConstraints();
    }
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
