package neqsim.pvtsimulation.util.parameterfitting;

import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Fitting function for asphaltene onset pressure/temperature.
 *
 * <p>
 * This function calculates the asphaltene onset pressure at a given temperature by adjusting CPA
 * association parameters (association energy epsilon/R and association volume kappa) to match
 * experimental onset data.
 * </p>
 *
 * <p>
 * The fitting parameters are:
 * </p>
 * <ul>
 * <li>params[0]: Association energy epsilon/R (K) - typically 2500-4000 K for asphaltenes</li>
 * <li>params[1]: Association volume kappa (dimensionless) - typically 0.001-0.05</li>
 * </ul>
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
   * Constructor for AsphalteneOnsetFunction. Initializes with 2 parameters: association energy and
   * volume.
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
        // params[0] = epsilon/R (association energy in K)
        // params[1] = kappa (association volume, dimensionless)
        // Apply to all phases for consistency
        for (int phaseNum = 0; phaseNum < system.getNumberOfPhases(); phaseNum++) {
          system.getPhase(phaseNum).getComponent(asphalteneIndex).setAssociationEnergy(params[0]);
          system.getPhase(phaseNum).getComponent(asphalteneIndex).setAssociationVolume(params[1]);
        }
        break;

      case BINARY_INTERACTION:
        // params[0] = kij with n-heptane or specified component
        // Binary interaction parameters would be set here
        break;

      case MOLAR_MASS:
        // params[0] = molar mass of asphaltene (g/mol)
        for (int phaseNum = 0; phaseNum < system.getNumberOfPhases(); phaseNum++) {
          system.getPhase(phaseNum).getComponent(asphalteneIndex).setMolarMass(params[0] / 1000.0);
        }
        break;

      case COMBINED:
        // params[0] = epsilon/R
        // params[1] = kij (not implemented yet - uses fixed kappa)
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
      if (name.equalsIgnoreCase(asphalteneComponentName)
          || name.toLowerCase().contains("asphaltene")) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Calculates the asphaltene onset pressure using phase stability analysis.
   *
   * @return onset pressure in bara, or NaN if not found
   */
  private double calculateOnsetPressure() {
    ThermodynamicOperations ops = new ThermodynamicOperations(system);

    // Enable solid phase checking
    system.setSolidPhaseCheck(asphalteneComponentName);

    double currentPressure = startPressure;
    double previousPressure = startPressure;
    boolean wasStable = true;

    // Coarse search
    while (currentPressure > minPressure) {
      system.setPressure(currentPressure);

      try {
        ops.TPflash();
      } catch (Exception e) {
        currentPressure -= pressureStep;
        continue;
      }

      boolean hasAsphaltene = checkForAsphaltenePrecipitation();

      if (hasAsphaltene && wasStable) {
        // Found transition - refine with bisection
        return refinePressure(previousPressure, currentPressure, ops);
      }

      wasStable = !hasAsphaltene;
      previousPressure = currentPressure;
      currentPressure -= pressureStep;
    }

    return Double.NaN;
  }

  /**
   * Refines the onset pressure using bisection.
   */
  private double refinePressure(double high, double low, ThermodynamicOperations ops) {
    double mid;

    while ((high - low) > pressureTolerance) {
      mid = (high + low) / 2.0;
      system.setPressure(mid);

      try {
        ops.TPflash();
      } catch (Exception e) {
        high = mid;
        continue;
      }

      if (checkForAsphaltenePrecipitation()) {
        low = mid;
      } else {
        high = mid;
      }
    }

    return (high + low) / 2.0;
  }

  /**
   * Checks if asphaltene precipitation has occurred.
   */
  private boolean checkForAsphaltenePrecipitation() {
    if (system.hasPhaseType("solid")) {
      try {
        if (system.getPhaseOfType("solid").getNumberOfMolesInPhase() > 1e-10) {
          return true;
        }
      } catch (Exception e) {
        // Phase check failed
      }
    }
    return false;
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
