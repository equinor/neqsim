package neqsim.api.ioc.fluids;


import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author jo.lyshoel
 */
public class Fluid7 extends NeqSimAbstractFluid {

    @Override
    public String[] getComponentNames() {
        return new String[] {"water", "nitrogen", "CO2", "methane", "ethane", "propane", "i-butane",
                "n-butane", "i-pentane", "n-pentane", "CHCmp_1", "CHCmp_2", "CHCmp_3", "CHCmp_4",
                "CHCmp_5", "CHCmp_6", "CHCmp_7", "CHCmp_8", "CHCmp_9", "CHCmp_10", "CHCmp_11",
                "CHCmp_12", "CHCmp_13"};
    }

    @Override
    public void addComponents(SystemInterface fluid) {
        // Gina Krog export oil
        fluid.addComponent("water", 0.0304006958007813);
        fluid.addComponent("nitrogen", 4.73001127829775E-07);
        fluid.addComponent("CO2", 0.000380391739308834);
        fluid.addComponent("methane", 0.00102935172617435);
        fluid.addComponent("ethane", 0.00350199580192566);
        fluid.addComponent("propane", 0.0149815678596497);
        fluid.addComponent("i-butane", 0.00698469817638397);
        fluid.addComponent("n-butane", 0.0226067280769348);
        fluid.addComponent("i-pentane", 0.0143046414852142);
        fluid.addComponent("n-pentane", 0.0203909373283386);
        fluid.addTBPfraction("CHCmp_1", 0.0352155113220215, 0.0854749984741211, 0.664700031280518);
        fluid.addTBPfraction("CHCmp_2", 0.0705802822113037, 0.0890039978027344, 0.757499992847443);
        fluid.addTBPfraction("CHCmp_3", 0.0850765609741211, 0.1021979980468750, 0.778400003910065);
        fluid.addTBPfraction("CHCmp_4", 0.0605201292037964, 0.1156969985961910, 0.792500019073486);
        fluid.addTBPfraction("CHCmp_5", 0.1793018150329590, 0.1513029937744140, 0.82480001449585);
        fluid.addTBPfraction("CHCmp_6", 0.1033354282379150, 0.2105240020751950, 0.869700014591217);
        fluid.addTBPfraction("CHCmp_7", 0.0706664896011353, 0.2728500061035160, 0.881599962711334);
        fluid.addTBPfraction("CHCmp_8", 0.0626348257064819, 0.3172810058593750, 0.89300000667572);
        fluid.addTBPfraction("CHCmp_9", 0.0488108015060425, 0.3585450134277340, 0.90200001001358);
        fluid.addTBPfraction("CHCmp_10", 0.0484040451049805, 0.4076000061035160, 0.911700010299683);
        fluid.addTBPfraction("CHCmp_11", 0.0417061710357666, 0.4698110046386720, 0.923400044441223);
        fluid.addTBPfraction("CHCmp_12", 0.0425787830352783, 0.5629600219726560, 0.939900040626526);
        fluid.addTBPfraction("CHCmp_13", 0.0365876793861389, 0.7858560180664060, 0.979299962520599);
    }

    @Override
    public int getComponentCount() {
        return 23;
    }

}
