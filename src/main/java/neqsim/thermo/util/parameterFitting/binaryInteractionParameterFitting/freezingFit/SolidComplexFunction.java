/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.freezingFit;

import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermodynamicOperations.flashOps.saturationOps.SolidComplexTemperatureCalc;
import org.apache.logging.log4j.*;

/**
 *
 * @author ESOL
 */
public class SolidComplexFunction extends LevenbergMarquardtFunction {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(SolidComplexFunction.class);

    public SolidComplexFunction() {
    }

    public double calcValue(double[] dependentValues) {
        try {
            thermoOps.calcSolidComlexTemperature("TEG", "water");
        } catch (Exception e) {
            logger.error("error", e);
        }
        //System.out.println("x " + system.getPhases()[1].getComponents()[0].getx());
        return system.getTemperature();  // for lucia data
        //return system.getPhases()[0].getComponents()[1].getx(); // for MEG
    }

    public void setFittingParams(int i, double value) {
        params[i] = value;
        if (i == 1) {
            SolidComplexTemperatureCalc.HrefComplex = value;
        }
        if (i == 0) {
            SolidComplexTemperatureCalc.Kcomplex = value;
        }
        if (i == 2) {
            SolidComplexTemperatureCalc.TrefComplex = value*100.0;
        }
    }
}
