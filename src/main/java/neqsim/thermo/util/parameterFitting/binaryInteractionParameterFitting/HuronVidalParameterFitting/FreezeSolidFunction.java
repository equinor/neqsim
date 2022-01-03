package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * FreezeSolidFunction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class FreezeSolidFunction extends HuronVidalFunction {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(FreezeSolidFunction.class);

    /**
     * <p>
     * Constructor for FreezeSolidFunction.
     * </p>
     */
    public FreezeSolidFunction() {}

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
        system.init(0);
        try {
            thermoOps.freezingPointTemperatureFlash();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        return system.getTemperature();
    }
}
