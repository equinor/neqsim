package neqsim.pvtsimulation.util.parameterfitting;

import neqsim.pvtsimulation.flowassurance.AsphalteneSolidModel;
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
      // params[0] = epsilon/R (association energy in K)
      // params[1] = kappa (association volume, dimensionless)
      // Apply to all phases for consistency
      for (int phaseNum = 0; phaseNum < system.getNumberOfPhases(); phaseNum++) {
        system.getPhase(phaseNum).getComponent(asphalteneIndex).setAssociationEnergy(params[0]);
        system.getPhase(phaseNum).getComponent(asphalteneIndex).setAssociationVolume(params[1]);
      }
      break;

    case BINARY_INTERACTION:
      applyScaledKij(asphalteneIndex, params[0]);
      break;

    case MOLAR_MASS:
      // params[0] = molar mass of asphaltene (g/mol)
      for (int phaseNum = 0; phaseNum < system.getNumberOfPhases(); phaseNum++) {
        system.getPhase(phaseNum).getComponent(asphalteneIndex).setMolarMass(params[0] / 1000.0);
      }
      break;

    case COMBINED:
      requireCpa();
      // params[0] = epsilon/R, params[1] = asphaltene-methane kij (size-scaled to the others)
      applyScaledKij(asphalteneIndex, params[1]);
      for (int phaseNum = 0; phaseNum < system.getNumberOfPhases(); phaseNum++) {
        system.getPhase(phaseNum).getComponent(asphalteneIndex).setAssociationEnergy(params[0]);
        system.getPhase(phaseNum).getComponent(asphalteneIndex).setAssociationVolume(0.005); // Fixed
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
      String name = system.getPhase(0).getComponent(i).getComponentName();
      if (name.equalsIgnoreCase(asphalteneComponentName) || name.toLowerCase().contains("asphaltene")) {
        return i;
      }
    }
    return -1;
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
    flash.run();
    return flash.isOnsetFound() ? flash.getOnsetPressure() : Double.NaN;
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
