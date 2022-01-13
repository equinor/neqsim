package neqsim.api.ioc.fluids;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author jo.lyshoel
 */
public class Fluid8 extends NeqSimAbstractFluid {

    @Override
    public String[] getComponentNames() {
        return new String[] {"water", "MEG"};
    }

    @Override
    public void addComponents(SystemInterface fluid) {
        // Gina Krog export oil
        fluid.addComponent("water", 0.8);
        fluid.addComponent("MEG", 0.2);
    }

    @Override
    public int getComponentCount() {
        return 2;
    }
}
