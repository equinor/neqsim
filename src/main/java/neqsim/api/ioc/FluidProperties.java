package neqsim.api.ioc;

import neqsim.api.ioc.exceptions.NeqSimUnsupportedFluid;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;


/**
 * 
 * @author jo.lyshoel
 */
public class FluidProperties {

    protected static int nCols = 70;

    public CalculationResult doCalculation(CalcRequest req)
            throws NeqSimException, NeqSimUnsupportedFluid {
        SystemInterface fluid;

        if (req.fluid == null) {
            fluid = new SystemSrkEos();
            NeqSimFluidManager.addComponents(req.fn, fluid);
            if (req.isStaticFractions() && req.fractions != null && !req.fractions.isEmpty()) {
                fluid.setMolarComposition(NeqSimFluidManager.getPreparedFractions(req.fn,
                        req.components != null ? req.components.toArray(new String[0]) : null,
                        req.getFractionsAsArray(), false));
            }
        } else {
            fluid = req.fluid;
        }

        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        if (req.isStaticFractions()) {
            return ops.propertyFlash(req.Sp1, req.Sp2, fluid, req.FlashMode, null, null);
        } else {
            return ops.propertyFlash(req.Sp1, req.Sp2, fluid, req.FlashMode, req.components,
                    req.onlineFractions);
        }
    }
}
