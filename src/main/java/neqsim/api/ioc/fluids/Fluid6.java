package neqsim.api.ioc.fluids;


import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author jo.lyshoel
 */
public class Fluid6 extends NeqSimAbstractFluid {

    @Override
    public String[] getComponentNames() {
        return new String[] {"water", "nitrogen", "CO2", "methane", "ethane", "propane", "i-butane",
                "n-butane", "i-pentane", "n-pentane", "CHCmp_1", "CHCmp_2", "CHCmp_3", "CHCmp_4",
                "CHCmp_5", "CHCmp_6", "CHCmp_7"};
    }

    @Override
    public void addComponents(SystemInterface fluid) {
        // Grane export oil
        fluid.addComponent("water", 0.0386243104934692);
        fluid.addComponent("nitrogen", 1.08263303991407E-05);
        fluid.addComponent("CO2", 0.00019008457660675);
        fluid.addComponent("methane", 0.00305547803640366);
        fluid.addComponent("ethane", 0.00200786963105202);
        fluid.addComponent("propane", 0.00389420658349991);
        fluid.addComponent("i-butane", 0.00179276615381241);
        fluid.addComponent("n-butane", 0.00255768150091171);
        fluid.addComponent("i-pentane", 0.00205287128686905);
        fluid.addComponent("n-pentane", 0.00117853358387947);
        fluid.addTBPfraction("CHCmp_1", 0.000867870151996613, 0.0810000000000000, 0.72122997045517);
        fluid.addTBPfraction("CHCmp_2", 0.048198757171630900, 0.0987799987792969,
                0.754330039024353);
        fluid.addTBPfraction("CHCmp_3", 0.097208471298217800, 0.1412200012207030, 0.81659996509552);
        fluid.addTBPfraction("CHCmp_4", 0.165174083709717000, 0.1857899932861330,
                0.861050009727478);
        fluid.addTBPfraction("CHCmp_5", 0.279571933746338000, 0.2410899963378910,
                0.902539968490601);
        fluid.addTBPfraction("CHCmp_6", 0.240494251251221000, 0.4045100097656250,
                0.955269992351531);
        fluid.addTBPfraction("CHCmp_7", 0.113120021820068000, 0.9069699707031250, 1.0074599981308);
    }

    @Override
    public int getComponentCount() {
        return 17;
    }

}
