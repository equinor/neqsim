package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class BinaryHVParameterFittingToSolubilityData extends HuronVidalFunction {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(BinaryHVParameterFittingToSolubilityData.class);

    int phase = 1;
    int type = 1;


    public BinaryHVParameterFittingToSolubilityData() {}

    public BinaryHVParameterFittingToSolubilityData(int phase, int type) {
        this.phase = phase;
        this.type = type;
    }

    @Override
    public double calcValue(double[] dependentValues) {

        if (type == 1) {
            thermoOps.TPflash();
            // system.display();
            // System.out.println("x " + system.getPhases()[1].getComponents()[0].getx());
            return system.getPhases()[phase].getComponents()[0].getx();
        } else if (type == 10) {
            try {
                thermoOps.bubblePointPressureFlash(true);
            } catch (Exception e) {
                logger.error("error", e);
                return system.getPressure() * system.getPhase(0).getComponents()[0].getx();
            }
            return system.getPressure() * system.getPhase(0).getComponents()[0].getx();
        } else {
            thermoOps.TPflash();
            return system.getPhases()[phase].getComponents()[1].getx();
        }
    }

    @Override
    public double calcTrueValue(double val) {
        return val;
    }
}
