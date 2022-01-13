package neqsim.api.ioc.fluids;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author jo.lyshoel
 */
public class Fluid2 extends NeqSimAbstractFluid {

    @Override
    public String[] getComponentNames() {
        return new String[]{ "nitrogen", "oxygen" };
    }

    @Override
    public void addComponents(SystemInterface fluid) {
        // Fluid air
        fluid.addComponent("nitrogen", 0.79);
        fluid.addComponent("oxygen", 0.21);
    }

    @Override
    public int getComponentCount() {
        return 2;
    }

}