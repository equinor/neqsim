package neqsim.api.ioc.fluids;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author jo.lyshoel
 */
public class Fluid5 extends NeqSimAbstractFluid {

    @Override
    public String[] getComponentNames() {
        return new String[] {"water", "nitrogen", "CO2", "methane", "ethane", "propane", "i-butane",
                "n-butane", "i-pentane", "n-pentane", "CHCmp_1", "CHCmp_2", "CHCmp_3", "CHCmp_4",
                "CHCmp_5", "CHCmp_6", "CHCmp_7", "CHCmp_8", "CHCmp_9", "CHCmp_10", "CHCmp_11",
                "CHCmp_12", "CHCmp_13"};
    }

    @Override
    public void addComponents(SystemInterface fluid) {
        // ?sgard B export oil
        fluid.addComponent("water", 1.63588003488258E-06);
        fluid.addComponent("nitrogen", 9.69199026590317E-14);
        fluid.addComponent("CO2", 4.56441011920106E-07);
        fluid.addComponent("methane", 2.34691992773151E-08);
        fluid.addComponent("ethane", 0.000086150337010622);
        fluid.addComponent("propane", 0.0186985325813293);
        fluid.addComponent("i-butane", 0.0239006042480469);
        fluid.addComponent("n-butane", 0.0813478374481201);
        fluid.addComponent("i-pentane", 0.0550192785263062);
        fluid.addComponent("n-pentane", 0.0722700452804565);
        fluid.addTBPfraction("CHCmp_1", 0.11058749198913600, 0.0861780014038086, 0.662663996219635);
        fluid.addTBPfraction("CHCmp_2", 0.17813117980957000, 0.0909560012817383, 0.740698456764221);
        fluid.addTBPfraction("CHCmp_3", 0.14764604568481400, 0.1034290008544920, 0.769004046916962);
        fluid.addTBPfraction("CHCmp_4", 0.10463774681091300, 0.1171869964599610, 0.789065659046173);
        fluid.addTBPfraction("CHCmp_5", 0.08433451652526860, 0.1458090057373050, 0.80481481552124);
        fluid.addTBPfraction("CHCmp_6", 0.03788370132446290, 0.1813300018310550, 0.825066685676575);
        fluid.addTBPfraction("CHCmp_7", 0.02444351673126220, 0.2122779998779300, 0.837704122066498);
        fluid.addTBPfraction("CHCmp_8", 0.01481210947036740, 0.2481419982910160, 0.849904119968414);
        fluid.addTBPfraction("CHCmp_9", 0.01158336877822880, 0.2892170104980470, 0.863837122917175);
        fluid.addTBPfraction("CHCmp_10", 0.01286722421646120, 0.3303389892578130,
                0.875513017177582);
        fluid.addTBPfraction("CHCmp_11", 0.00838199377059937, 0.3846969909667970,
                0.888606309890747);
        fluid.addTBPfraction("CHCmp_12", 0.00746552944183350, 0.4711579895019530,
                0.906100511550903);
        fluid.addTBPfraction("CHCmp_13", 0.00590102910995483, 0.6624600219726560,
                0.936200380325317);
    }

    @Override
    public int getComponentCount() {
        return 23;
    }

}
