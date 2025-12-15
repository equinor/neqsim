package neqsim.thermo.util.derivatives;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Container for gradients of a scalar thermodynamic property with respect to state variables.
 *
 * <p>
 * Stores derivatives of a property (e.g., density, enthalpy, entropy) with respect to:
 * </p>
 * <ul>
 * <li>Temperature (T)</li>
 * <li>Pressure (P)</li>
 * <li>Composition (mole fractions or moles)</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * PropertyGradient densityGrad = flash.computePropertyGradient("density");
 * double dRho_dT = densityGrad.getDerivativeWrtTemperature();
 * double dRho_dP = densityGrad.getDerivativeWrtPressure();
 * double[] dRho_dz = densityGrad.getDerivativeWrtComposition();
 * }
 * </pre>
 *
 * @author ESOL
 * @since 3.0
 */
public class PropertyGradient implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Name of the property. */
  private final String propertyName;

  /** Unit of the property. */
  private final String unit;

  /** Current value of the property. */
  private final double value;

  /** Derivative with respect to temperature [property_unit/K]. */
  private final double dT;

  /** Derivative with respect to pressure [property_unit/bar]. */
  private final double dP;

  /** Derivatives with respect to composition (mole fractions) [property_unit/mol_frac]. */
  private final double[] dz;

  /** Component names corresponding to composition derivatives. */
  private final String[] componentNames;

  /**
   * Constructor for PropertyGradient.
   *
   * @param propertyName name of the property
   * @param unit unit of the property
   * @param value current value of the property
   * @param dT derivative with respect to temperature
   * @param dP derivative with respect to pressure
   * @param dz derivatives with respect to composition
   * @param componentNames names of components
   */
  public PropertyGradient(String propertyName, String unit, double value, double dT, double dP,
      double[] dz, String[] componentNames) {
    this.propertyName = propertyName;
    this.unit = unit;
    this.value = value;
    this.dT = dT;
    this.dP = dP;
    this.dz = dz != null ? Arrays.copyOf(dz, dz.length) : new double[0];
    this.componentNames =
        componentNames != null ? Arrays.copyOf(componentNames, componentNames.length)
            : new String[0];
  }

  /**
   * Simplified constructor without component names.
   *
   * @param propertyName name of the property
   * @param unit unit of the property
   * @param value current value of the property
   * @param dT derivative with respect to temperature
   * @param dP derivative with respect to pressure
   * @param dz derivatives with respect to composition
   */
  public PropertyGradient(String propertyName, String unit, double value, double dT, double dP,
      double[] dz) {
    this(propertyName, unit, value, dT, dP, dz, null);
  }

  /**
   * Get the property name.
   *
   * @return property name
   */
  public String getPropertyName() {
    return propertyName;
  }

  /**
   * Get the property unit.
   *
   * @return property unit
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Get the current value of the property.
   *
   * @return property value
   */
  public double getValue() {
    return value;
  }

  /**
   * Get the derivative with respect to temperature.
   *
   * @return dProperty/dT [property_unit/K]
   */
  public double getDerivativeWrtTemperature() {
    return dT;
  }

  /**
   * Get the derivative with respect to pressure.
   *
   * @return dProperty/dP [property_unit/bar]
   */
  public double getDerivativeWrtPressure() {
    return dP;
  }

  /**
   * Get derivatives with respect to composition.
   *
   * @return array of dProperty/dz_i
   */
  public double[] getDerivativeWrtComposition() {
    return Arrays.copyOf(dz, dz.length);
  }

  /**
   * Get derivative with respect to a specific component.
   *
   * @param componentIndex index of the component
   * @return dProperty/dz_i
   */
  public double getDerivativeWrtComponent(int componentIndex) {
    if (componentIndex < 0 || componentIndex >= dz.length) {
      throw new IndexOutOfBoundsException(
          "Component index " + componentIndex + " out of bounds [0, " + dz.length + ")");
    }
    return dz[componentIndex];
  }

  /**
   * Get component names.
   *
   * @return array of component names
   */
  public String[] getComponentNames() {
    return Arrays.copyOf(componentNames, componentNames.length);
  }

  /**
   * Get the number of components.
   *
   * @return number of components
   */
  public int getNumberOfComponents() {
    return dz.length;
  }

  /**
   * Compute directional derivative along a perturbation vector.
   *
   * @param deltaT temperature perturbation
   * @param deltaP pressure perturbation
   * @param deltaZ composition perturbations
   * @return directional derivative
   */
  public double directionalDerivative(double deltaT, double deltaP, double[] deltaZ) {
    double result = dT * deltaT + dP * deltaP;
    if (deltaZ != null) {
      for (int i = 0; i < Math.min(dz.length, deltaZ.length); i++) {
        result += dz[i] * deltaZ[i];
      }
    }
    return result;
  }

  /**
   * Get gradient as a flat array [dT, dP, dz_0, dz_1, ...].
   *
   * @return gradient vector
   */
  public double[] toArray() {
    double[] result = new double[2 + dz.length];
    result[0] = dT;
    result[1] = dP;
    System.arraycopy(dz, 0, result, 2, dz.length);
    return result;
  }

  /**
   * Create a zero gradient for a given property.
   *
   * @param propertyName name of the property
   * @param unit unit of the property
   * @param value current value
   * @param numberOfComponents number of components
   * @return zero gradient
   */
  public static PropertyGradient zero(String propertyName, String unit, double value,
      int numberOfComponents) {
    return new PropertyGradient(propertyName, unit, value, 0.0, 0.0,
        new double[numberOfComponents]);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("PropertyGradient[").append(propertyName).append(" = ").append(value);
    sb.append(" ").append(unit).append("]\n");
    sb.append("  d/dT = ").append(dT).append(" ").append(unit).append("/K\n");
    sb.append("  d/dP = ").append(dP).append(" ").append(unit).append("/bar\n");
    sb.append("  d/dz = [");
    for (int i = 0; i < dz.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      if (componentNames.length > i) {
        sb.append(componentNames[i]).append(":");
      }
      sb.append(String.format("%.6e", dz[i]));
    }
    sb.append("]");
    return sb.toString();
  }
}
