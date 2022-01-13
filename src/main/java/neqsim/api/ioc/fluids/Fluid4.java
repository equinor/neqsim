package neqsim.api.ioc.fluids;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author jo.lyshoel
 */
public class Fluid4 extends NeqSimAbstractFluid {

    @Override
    public String[] getComponentNames() {
        return new String[] {"water", "helium", "hydrogen", "nitrogen", "argon", "oxygen", "CO2",
                "H2S", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
                "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10", "nC11"};
    }

    @Override
    public void addComponents(SystemInterface fluid) {
        // Fluid extended gas
        fluid.addComponent("water", 0.0320);
        fluid.addComponent("helium", 0.0100);
        fluid.addComponent("hydrogen", 0.0100);
        fluid.addComponent("nitrogen", 0.0400);
        fluid.addComponent("argon", 0.0100);
        fluid.addComponent("oxygen", 0.0100);
        fluid.addComponent("CO2", 0.0500);
        fluid.addComponent("H2S", 0.0200);
        fluid.addComponent("methane", 0.6000);
        fluid.addComponent("ethane", 0.1000);
        fluid.addComponent("propane", 0.0500);
        fluid.addComponent("i-butane", 0.0300);
        fluid.addComponent("n-butane", 0.0150);
        fluid.addComponent("i-pentane", 0.0100);
        fluid.addComponent("n-pentane", 0.0050);
        fluid.addComponent("n-hexane", 0.0030);
        fluid.addComponent("n-heptane", 0.0020);
        fluid.addComponent("n-octane", 0.0010);
        fluid.addComponent("n-nonane", 0.0010);
        fluid.addComponent("nC10", 0.0005);
        fluid.addComponent("nC11", 0.0005);
    }

    @Override
    public int getComponentCount() {
        return 21;
    }

}
