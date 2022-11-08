package neqsim.api.ioc;

import java.util.Arrays;

/**
 *
 * @author jo.lyshoel
 */
public class CalculationResult {
  public Double[][] fluidProperties;
  public String[] calculationError;

  public CalculationResult() {}

  public CalculationResult(Double[][] fluidProperties, String[] calculationError) {
    this.fluidProperties = fluidProperties;
    this.calculationError = calculationError;
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
}
