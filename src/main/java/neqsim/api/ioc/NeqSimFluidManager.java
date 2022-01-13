/*
 * To change this license header, choose License Headers in Project Properties. To change this
 * template file, choose Tools | Templates and open the template in the editor.
 */
package neqsim.api.ioc;

import neqsim.api.ioc.exceptions.NeqSimUnsupportedFluid;
import neqsim.api.ioc.fluids.Fluid1;
import neqsim.api.ioc.fluids.Fluid2;
import neqsim.api.ioc.fluids.Fluid3;
import neqsim.api.ioc.fluids.Fluid4;
import neqsim.api.ioc.fluids.Fluid5;
import neqsim.api.ioc.fluids.Fluid6;
import neqsim.api.ioc.fluids.Fluid7;
import neqsim.api.ioc.fluids.Fluid8;
import neqsim.api.ioc.fluids.Fluid9;
import neqsim.api.ioc.fluids.NeqSimAbstractFluid;
import neqsim.thermo.system.SystemInterface;


/**
 *
 * @author jo.lyshoel
 */
public class NeqSimFluidManager {

    public static double[] getPreparedFractions(int fn, String[] components, double[] fractions,
            boolean ignoreMissingComponents) throws NeqSimException {
        NeqSimAbstractFluid fluid = getFluid(fn);

        int fluidCount = fluid.getComponentNames().length;
        int count = fractions.length;
        if (count == fluidCount || ignoreMissingComponents) {
            if (components != null) {
                return fluid.parseFractions(components, fractions);
            } else {
                return fractions;
            }
        } else {
            throw new NeqSimException("Calculation error, wrong component count. Got " + count
                    + " expected " + fluidCount + " for fluid " + fn);
        }
    }

    public static NeqSimAbstractFluid getFluid(int fn) throws NeqSimUnsupportedFluid {
        switch (fn) {
            case 1:
                return new Fluid1();
            case 2:
                return new Fluid2();
            case 3:
                return new Fluid3();
            case 4:
                return new Fluid4();
            case 5:
                return new Fluid5();
            case 6:
                return new Fluid6();
            case 7:
                return new Fluid7();
            case 8:
                return new Fluid8();
            case 9:
                return new Fluid9();
        }

        throw new NeqSimUnsupportedFluid("Fluid " + fn + " is not supported");
    }

    public static void addComponents(int fn, SystemInterface fluid) throws NeqSimUnsupportedFluid {
        getFluid(fn).addComponents(fluid);

        if (fn == 2) {
            // Fluid air
            fluid.setMixingRule(2);
            fluid.useVolumeCorrection(true);
            fluid.init(0); // careful: this method will reset forced phase types
            fluid.setMaxNumberOfPhases(1);
            fluid.setForcePhaseTypes(true);
            fluid.setPhaseType(0, "gas");
        } else {
            fluid.setMixingRule(2);
            fluid.useVolumeCorrection(true);
            fluid.setMultiPhaseCheck(true);
        }
    }

}
