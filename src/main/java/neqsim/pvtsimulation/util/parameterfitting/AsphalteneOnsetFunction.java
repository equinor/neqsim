package neqsim.pvtsimulation.util.parameterfitting;

import neqsim.pvtsimulation.flowassurance.AsphalteneSolidModel;
import neqsim.thermo.characterization.AsphalteneComponents;
import neqsim.thermo.characterization.PedersenAsphalteneCharacterization;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseCPAInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermodynamicoperations.flashops.saturationops.AsphalteneOnsetPressureFlash;

/**
 * Fitting function for asphaltene onset pressure/temperature.
 *
 * <p>
 * This function calculates the asphaltene onset pressure at a given temperature by adjusting CPA association parameters
 * (association energy epsilon/R and association volume kappa) to match experimental onset data.
 * </p>
 *
 * <p>
 * The fitting parameters are:
 * </p>
 * <ul>
 * <li>ASSOCIATION_PARAMETERS (CPA systems only): params[0] association energy epsilon/R (K), typically 2500-4000 K;
 * params[1] association volume kappa, typically 0.001-0.05</li>
 * <li>BINARY_INTERACTION: params[0] the asphaltene-methane interaction parameter; every other component gets a value
 * scaled by its size asymmetry with the asphaltene (see {@link AsphalteneSolidModel#sizeAsymmetryKij}), so light
 * components get large and heavy fractions near-zero values</li>
 * <li>MOLAR_MASS: params[0] asphaltene molar mass (g/mol)</li>
 * <li>COMBINED (CPA systems only): params[0] epsilon/R (K), params[1] the asphaltene-methane interaction parameter as
 * for BINARY_INTERACTION; kappa is fixed at 0.005</li>
 * </ul>
 *
 * <p>
 * The onset is found with {@link AsphalteneOnsetPressureFlash}. Flash failures and a fluid that is already unstable at
 * the start pressure raise exceptions instead of being reported as an onset.
 * </p>
 *
 * <p>
 * The dependent values are:
 * </p>
 * <ul>
 * <li>dependentValues[0]: Temperature (K) at which to calculate onset pressure</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Li, Z., Firoozabadi, A. (2010). Energy Fuels, 24, 1106-1113</li>
 * <li>Vargas, F.M., et al. (2009). Energy Fuels, 23, 1140-1146</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class AsphalteneOnsetFunction extends LevenbergMarquardtFunction {

  /** Name of the asphaltene component in the system. */
  private String asphalteneComponentName = "asphaltene";

  /** Starting pressure for onset search (bara). */
  private double startPressure = 500.0;

  /** Minimum pressure to search to (bara). */
  private double minPressure = 10.0;

  /** Pressure step for search (bara). */
  private double pressureStep = 10.0;

  /** Convergence tolerance for pressure (bara). */
  private double pressureTolerance = 0.5;

  /** Type of parameter being fitted. */
  private FittingParameterType parameterType = FittingParameterType.ASSOCIATION_PARAMETERS;

  /** Asphaltene mass held constant during MOLAR_MASS fitting (kg), NaN until first used. */
  private double referenceAsphalteneMass = Double.NaN;

  /** Asphaltene density used to recharacterize during MOLAR_MASS fitting (g/cm3). */
  private double referenceAsphalteneDensity = 1.1;

  /**
   * Enum for different fitting parameter types.
   */
  public enum FittingParameterType {
    /** Fit association energy (epsilon/R) and volume (kappa). */
    ASSOCIATION_PARAMETERS,
    /** Fit binary interaction parameter (kij) with specific component. */
    BINARY_INTERACTION,
    /** Fit molar mass of asphaltene pseudo-component. */
    MOLAR_MASS,
    /** Fit both association energy and binary interaction parameter. */
    COMBINED
  }

  /**
   * Constructor for AsphalteneOnsetFunction. Initializes with 2 parameters: association energy and volume.
   */
  public AsphalteneOnsetFunction() {
    params = new double[2];
  }

  /**
   * Constructor with specified number of parameters.
   *
   * @param numberOfParams number of fitting parameters
   */
  public AsphalteneOnsetFunction(int numberOfParams) {
    params = new double[numberOfParams];
  }

  /**
   * Calculates the asphaltene onset pressure at the specified temperature.
   *
   * <p>
   * The result is always finite so that the Levenberg-Marquardt Jacobian stays defined: when no onset is found down to
   * the minimum search pressure the minimum pressure is returned, and when asphaltene is already precipitated at the
   * start pressure the start pressure is returned.
   * </p>
   *
   * @param dependentValues array containing [temperature_K]
   * @return calculated onset pressure in bara
   */
  @Override
  public double calcValue(double[] dependentValues) {
    double temperature = dependentValues[0];

    // Apply the current parameter values to the system
    applyParameters();

    // Set temperature for this calculation
    system.setTemperature(temperature);
    system.init(0);
    system.init(1);

    // Calculate onset pressure
    double onsetPressure = calculateOnsetPressure();

    return onsetPressure;
  }

  /**
   * Applies the current fitting parameters to the thermodynamic system.
   */
  private void applyParameters() {
    int asphalteneIndex = findAsphalteneIndex();
    if (asphalteneIndex < 0) {
      return;
    }

    switch (parameterType) {
    case ASSOCIATION_PARAMETERS:
      requireCpa();
      // params[0] = association energy (J/mol, NeqSim CPA unit)
      // params[1] = kappa (association volume, dimensionless)
      // Apply to all phases for consistency
      for (PhaseInterface phase : AsphalteneComponents.allPhases(system)) {
        phase.getComponent(asphalteneIndex).setAssociationEnergy(params[0]);
        phase.getComponent(asphalteneIndex).setAssociationVolume(params[1]);
      }
      break;

    case BINARY_INTERACTION:
      applyScaledKij(asphalteneIndex, params[0]);
      break;

    case MOLAR_MASS:
      // params[0] = molar mass of asphaltene (g/mol); see applyMolarMass
      applyMolarMass(asphalteneIndex, params[0]);
      break;

    case COMBINED:
      requireCpa();
      // params[0] = association energy (J/mol), params[1] = asphaltene-methane kij (size-scaled to the others)
      applyScaledKij(asphalteneIndex, params[1]);
      for (PhaseInterface phase : AsphalteneComponents.allPhases(system)) {
        phase.getComponent(asphalteneIndex).setAssociationEnergy(params[0]);
        phase.getComponent(asphalteneIndex).setAssociationVolume(0.005); // Fixed
        // kappa
      }
      break;
    }
  }

  /**
   * Finds the index of the asphaltene component in the system.
   *
   * @return component index, or -1 if not found
   */
  private int findAsphalteneIndex() {
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).getComponentName().equalsIgnoreCase(asphalteneComponentName)) {
        return i;
      }
    }
    return AsphalteneComponents.firstIndex(system);
  }

  /**
   * Applies a new asphaltene molar mass consistently.
   *
   * <p>
   * The asphaltene mass is held at its value when fitting started, so the number of moles changes as 1/M. For a TBP
   * pseudo-component (for example from {@link PedersenAsphalteneCharacterization}) the critical properties and acentric
   * factor are also recalculated with the Pedersen asphaltene correlations at the component's density. A database
   * asphaltene keeps its critical and solid properties, which its solid-phase reference is tied to. Changing only the
   * molar mass (as earlier versions did) left a mole-based flash, and hence the onset, unchanged. CPA systems are not
   * supported; their asphaltene takes a and b from the database.
   * </p>
   *
   * @param asphalteneIndex component index
   * @param molarMassGPerMol new molar mass (g/mol)
   */
  private void applyMolarMass(int asphalteneIndex, double molarMassGPerMol) {
    if (system.getPhase(0) instanceof PhaseCPAInterface) {
      throw new IllegalStateException("MOLAR_MASS fitting needs a cubic asphaltene pseudo-component; a CPA asphaltene "
          + "takes a and b from the database, so its molar mass does not change the onset");
    }
    if (!(molarMassGPerMol > 0.0) || !Double.isFinite(molarMassGPerMol)) {
      throw new IllegalArgumentException("Asphaltene molar mass must be positive");
    }
    double currentMoles = system.getPhase(0).getComponent(asphalteneIndex).getNumberOfmoles();
    double currentMolarMass = system.getPhase(0).getComponent(asphalteneIndex).getMolarMass();
    if (Double.isNaN(referenceAsphalteneMass)) {
      referenceAsphalteneMass = currentMoles * currentMolarMass;
      double density = system.getPhase(0).getComponent(asphalteneIndex).getNormalLiquidDensity();
      referenceAsphalteneDensity = density >= 0.9 && density <= 1.4 ? density : 1.1;
    }
    PedersenAsphalteneCharacterization characterization = new PedersenAsphalteneCharacterization(molarMassGPerMol,
        referenceAsphalteneDensity, 0.0);
    characterization.characterize();
    // The characterization clamps the molar mass to its valid range; use the clamped value throughout.
    molarMassGPerMol = characterization.getAsphalteneMW();
    double newMoles = referenceAsphalteneMass / (molarMassGPerMol / 1000.0);
    boolean pseudoComponent = system.getPhase(0).getComponent(asphalteneIndex).isIsTBPfraction();
    system.addComponent(asphalteneIndex, newMoles - currentMoles);
    for (PhaseInterface phase : AsphalteneComponents.allPhases(system)) {
      phase.getComponent(asphalteneIndex).setMolarMass(molarMassGPerMol / 1000.0);
      if (pseudoComponent) {
        phase.getComponent(asphalteneIndex).setTC(characterization.getCriticalTemperature());
        phase.getComponent(asphalteneIndex).setPC(characterization.getCriticalPressure());
        phase.getComponent(asphalteneIndex).setAcentricFactor(characterization.getAcentricFactor());
      }
    }
  }

  /**
   * Throw if the system is not a CPA system (association parameters have no effect otherwise).
   */
  private void requireCpa() {
    if (!(system.getPhase(0) instanceof PhaseCPAInterface)) {
      throw new IllegalStateException(
          "Association parameters need a CPA system; use BINARY_INTERACTION or MOLAR_MASS for cubic equations of state");
    }
  }

  /**
   * Set asphaltene interaction parameters: the given value for methane, scaled by size asymmetry for the others.
   *
   * @param asphalteneIndex asphaltene component index
   * @param methaneKij asphaltene-methane interaction parameter
   */
  private void applyScaledKij(int asphalteneIndex, double methaneKij) {
    PhaseInterface phase0 = system.getPhase(0);
    double asphSize = phase0.getComponent(asphalteneIndex).getTC() / phase0.getComponent(asphalteneIndex).getPC();
    double methaneSize = phase0.hasComponent("methane")
        ? phase0.getComponent("methane").getTC() / phase0.getComponent("methane").getPC()
        : 190.564 / 45.99;
    double methaneShape = AsphalteneSolidModel.sizeAsymmetryKij(asphSize, methaneSize, 1.0);
    int nc = phase0.getNumberOfComponents();
    for (int p = 0; p < system.getMaxNumberOfPhases(); p++) {
      PhaseInterface phase = system.getPhase(p);
      if (!(phase instanceof PhaseEosInterface) || ((PhaseEosInterface) phase).getEosMixingRule() == null) {
        continue;
      }
      EosMixingRulesInterface rule = ((PhaseEosInterface) phase).getEosMixingRule();
      for (int k = 0; k < nc; k++) {
        if (k == asphalteneIndex || "water".equalsIgnoreCase(phase.getComponent(k).getComponentName())) {
          continue;
        }
        double size = phase0.getComponent(k).getTC() / phase0.getComponent(k).getPC();
        double kij = methaneShape > 0.0
            ? methaneKij * AsphalteneSolidModel.sizeAsymmetryKij(asphSize, size, 1.0) / methaneShape
            : methaneKij;
        rule.setBinaryInteractionParameter(asphalteneIndex, k, kij);
        rule.setBinaryInteractionParameter(k, asphalteneIndex, kij);
      }
    }
  }

  /**
   * Calculates the asphaltene onset pressure with the shared onset flash.
   *
   * @return onset pressure in bara, or NaN if no onset is found in the range
   */
  private double calculateOnsetPressure() {
    AsphalteneOnsetPressureFlash flash = new AsphalteneOnsetPressureFlash(system, startPressure, minPressure);
    flash.setPressureStep(pressureStep);
    flash.setPressureTolerance(pressureTolerance);
    flash.setSearchLowerOnset(false);
    try {
      flash.run();
    } catch (IllegalStateException alreadyPrecipitated) {
      // Unstable at the start pressure: the onset is at or above it.
      return startPressure;
    }
    return flash.isOnsetFound() ? flash.getOnsetPressure() : minPressure;
  }

  /** {@inheritDoc} */
  @Override
  public void setFittingParams(int i, double value) {
    params[i] = value;
  }

  /**
   * Sets the name of the asphaltene component.
   *
   * @param name component name
   */
  public void setAsphalteneComponentName(String name) {
    this.asphalteneComponentName = name;
  }

  /**
   * Sets the pressure search range.
   *
   * @param start starting pressure (bara)
   * @param min minimum pressure (bara)
   * @param step pressure step (bara)
   */
  public void setPressureRange(double start, double min, double step) {
    this.startPressure = start;
    this.minPressure = min;
    this.pressureStep = step;
  }

  /**
   * Sets the type of parameters to fit.
   *
   * @param type the fitting parameter type
   */
  public void setParameterType(FittingParameterType type) {
    this.parameterType = type;
    switch (type) {
    case ASSOCIATION_PARAMETERS:
      params = new double[2];
      break;
    case BINARY_INTERACTION:
    case MOLAR_MASS:
      params = new double[1];
      break;
    case COMBINED:
      params = new double[2];
      break;
    }
  }

  /**
   * Sets the pressure tolerance for convergence.
   *
   * @param tolerance pressure tolerance in bara
   */
  public void setPressureTolerance(double tolerance) {
    this.pressureTolerance = tolerance;
  }
}
