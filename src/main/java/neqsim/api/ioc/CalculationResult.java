package neqsim.api.ioc;

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
}
