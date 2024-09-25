package neqsim.api.ioc;

import java.util.Arrays;

/**
 * <p>
 * CalculationResult class.
 * </p>
 *
 * @author jo.lyshoel
 * @version $Id: $Id
 */
public class CalculationResult {
  public Double[][] fluidProperties;
  public String[] calculationError;

  /**
   * <p>
   * Constructor for CalculationResult.
   * </p>
   *
   * @param fluidProperties an array of {@link java.lang.Double} objects
   * @param calculationError an array of {@link java.lang.String} objects
   */
  public CalculationResult(Double[][] fluidProperties, String[] calculationError) {
    this.fluidProperties = fluidProperties;
    this.calculationError = calculationError;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    CalculationResult other = (CalculationResult) obj;
    return Arrays.equals(calculationError, other.calculationError)
        && Arrays.deepEquals(fluidProperties, other.fluidProperties);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(calculationError);
    result = prime * result + Arrays.deepHashCode(fluidProperties);
    return result;
  }
}
