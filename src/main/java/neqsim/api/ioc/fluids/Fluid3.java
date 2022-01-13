package neqsim.api.ioc.fluids;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author jo.lyshoel
 */
public class Fluid3 extends NeqSimAbstractFluid {

    @Override
    public String[] getComponentNames() {
        return new String[] {"water"};
    }

    @Override
    public void addComponents(SystemInterface fluid) {
        // Fluid water
        fluid.addComponent("water", 1.0);
    }

    @Override
    public int getComponentCount() {
        return 1;
    }
}
