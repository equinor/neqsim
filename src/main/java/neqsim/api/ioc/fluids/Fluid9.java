package neqsim.api.ioc.fluids;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author jo.lyshoel
 */
public class Fluid9 extends NeqSimAbstractFluid {

    @Override
    public String[] getComponentNames() {
        return new String[] {"seawater"};
    }

    @Override
    public void addComponents(SystemInterface fluid) {
        fluid.addComponent("seawater", 1.0);
    }

    @Override
    public int getComponentCount() {
        return 1;
    }
}
