package neqsim.api.ioc;

import neqsim.api.ioc.exceptions.NeqSimUnsupportedFluid;
import neqsim.thermodynamicOperations.ThermodynamicOperations;


/**
 * 
 * @author jo.lyshoel
 */
public class FluidProperties {

    protected static int nCols = 70;

    public CalculationResult doCalculation(CalcRequest req)
            throws NeqSimException, NeqSimUnsupportedFluid {

        ThermodynamicOperations ops = new ThermodynamicOperations(req.fluid);
        if (req.isStaticFractions()) {
            return ops.propertyFlash(req.Sp1, req.Sp2, req.FlashMode, null, null);
        } else {
            return ops.propertyFlash(req.Sp1, req.Sp2, req.FlashMode, req.components,
                    req.onlineFractions);
        }
    }
}
