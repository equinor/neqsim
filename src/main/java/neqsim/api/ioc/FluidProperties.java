
package neqsim.api.ioc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemProperties;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;



/**
 * 
 * @author jo.lyshoel
 */
public class FluidProperties {

    private static final Logger LOGGER = LogManager.getLogger(FluidProperties.class.getName());
    protected static int nCols = 70;

    public CalculationResult doCalculation(CalcRequest req) throws NeqSimException {
        Double[][] fluidProperties = new Double[req.Sp1.size()][nCols]; // 70 cols
        String[] calculationError = new String[req.Sp1.size()];

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

        ThermodynamicOperations fluidOps = new ThermodynamicOperations(fluid);

        for (int t = 0; t < req.Sp1.size(); t++) {
            try {
                Double Sp1 = req.Sp1.get(t);
                Double Sp2 = req.Sp2.get(t);

                if (Sp1 == null || Sp2 == null || Double.isNaN(Sp1) || Double.isNaN(Sp2)) {
                    calculationError[t] = "Sp1 or Sp2 is NaN";
                    LOGGER.info("Sp1 or Sp2 is NULL for datapoint {}", t);
                    continue;
                }

                if (req.isOnlineFractions()) {
                    req.validateOnlineFractions(t);

                    fluid.setMolarComposition(NeqSimFluidManager.getPreparedFractions(req.fn,
                            req.components != null ? req.components.toArray(new String[0]) : null,
                            req.getOnlineFractionsAsArray(t), true));
                }

                double pressureInPa = Sp1 / 1e5;
                fluid.setPressure(pressureInPa);

                if (req.FlashMode == 1) {
                    fluid.setTemperature(Sp2);
                    fluidOps.TPflash();
                    fluid.init(2);
                    fluid.initPhysicalProperties();
                } else if (req.FlashMode == 2) {
                    fluidOps.PHflash(Sp2, "J/mol");
                    fluid.init(2);
                    fluid.initPhysicalProperties();
                } else if (req.FlashMode == 3) {
                    fluidOps.PSflash(Sp2, "J/molK");
                    fluid.init(2);
                    fluid.initPhysicalProperties();
                }

                int numberOfMole = Math.round((float) fluid.getNumberOfMoles());

                if (numberOfMole != 1) {
                    calculationError[t] = "Number of moles is " + fluid.getNumberOfMoles()
                            + " and not 1. Check input fragments.";
                    LOGGER.info("Number of moles is " + fluid.getNumberOfMoles()
                            + " and not 1. Check input fragments.", t);
                    continue;
                }
                SystemProperties a = fluid.getProperties();

                fluidProperties[t] = a.values;
            } catch (Exception ex) {
                calculationError[t] = ex.getMessage();
                LOGGER.warn("Single calculation failed", ex);
            }
        }

        return new CalculationResult(fluidProperties, calculationError);
    }
}
