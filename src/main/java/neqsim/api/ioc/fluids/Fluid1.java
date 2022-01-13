package neqsim.api.ioc.fluids;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author jo.lyshoel
 */
public class Fluid1 extends NeqSimAbstractFluid {

    @Override
    public String[] getComponentNames() {
        return new String[] {"water", "nitrogen", "CO2", "methane", "ethane", "propane", "i-butane",
                "n-butane", "i-pentane", "n-pentane", "n-hexane"};
    }

    @Override
    public void addComponents(SystemInterface fluid) {
        // Fluid gas
        fluid.addComponent("water", 0.01);
        fluid.addComponent("nitrogen", 0.02);
        fluid.addComponent("CO2", 0.03);
        fluid.addComponent("methane", 0.81);
        fluid.addComponent("ethane", 0.04);
        fluid.addComponent("propane", 0.03);
        fluid.addComponent("i-butane", 0.02);
        fluid.addComponent("n-butane", 0.01);
        fluid.addComponent("i-pentane", 0.01);
        fluid.addComponent("n-pentane", 0.01);
        fluid.addComponent("n-hexane", 0.01);
    }

    @Override
    public int getComponentCount() {
        return 11;
    }

}
