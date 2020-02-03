package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;


import org.apache.logging.log4j.*;

/*
 * TPflash.java
 *
 * Created on 27. september 2001, 09:43
 */

 /*
 *
 * @author esol @version
 */
public class TPflashGR {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TPflashGR.class);

    /**
     * Creates new TPflash
     */
    public TPflashGR() {
    }
    
    public void testrun() {
    	 String[] components = new String[]{"water", "nitrogen", "CO2", "methane", "ethane", "propane", "i-butane","n-butane","i-pentane", "n-pentane", "CHCmp_1", "CHCmp_2", "CHCmp_3", "CHCmp_4" ,"CHCmp_5","CHCmp_6","CHCmp_7","CHCmp_8","CHCmp_9","CHCmp_10","CHCmp_11","CHCmp_12","CHCmp_13"};
    	 double[] fractions1 = new double[] {0.0304006958007813, 4.73001127829775E-07, 0.000380391739308834, 0.00102935172617435, 0.00350199580192566, 0.0149815678596497, 0.00698469817638397 ,  0.0226067280769348, 0.0143046414852142, 0.0203909373283386, 0.0352155113220215, 0.0705802822113037,0.0850765609741211,0.0605201292037964, 0.1793018150329590, 0.1033354282379150, 0.0706664896011353,  0.0626348257064819,0.0488108015060425,0.0484040451049805,0.0417061710357666,0.0425787830352783,0.0365876793861389 };
    	 double[] molarmass = new double[] {0.0386243104934692, 1.08263303991407E-05, 0.00019008457660675, 0.00305547803640366, 0.00200786963105202, 0.00389420658349991,0.00179276615381241 ,  0.00255768150091171, 0.00205287128686905, 0.00117853358387947 , 0.0854749984741211,0.0890039978027344,0.1021979980468750,0.1156969985961910,0.1513029937744140,0.2105240020751950,0.2728500061035160,0.3172810058593750,0.3585450134277340,0.4076000061035160,0.4698110046386720,0.5629600219726560, 0.7858560180664060 };
    	 double[] density = new double[] {0.0386243104934692, 1.08263303991407E-05, 0.00019008457660675, 0.00305547803640366, 0.00200786963105202, 0.00389420658349991,0.00179276615381241 ,  0.00255768150091171, 0.00205287128686905, 0.00117853358387947,  0.664700031280518,0.757499992847443,0.778400003910065,0.792500019073486,0.82480001449585,0.869700014591217,0.881599962711334,0.89300000667572,0.90200001001358,0.911700010299683, 0.923400044441223,0.939900040626526,0.979299962520599};
    	
    	 double[] P_bar = new double[] { 1, 1, 1, 1, 10, 10, 10, 10, 100, 100, 100, 100};
    	 double[] T_C = new double[] {0, 15, 30, 100, 0, 15, 30, 100,0, 15, 30, 100 };

    	 double[] enthalpy = new double[P_bar.length];
    	 double[] entropy = new double[P_bar.length];

    	 double[] errH = new double[P_bar.length];
    	 double[] errS = new double[P_bar.length];
    	SystemInterface fluid1 = new SystemSrkEos(298.0, 10.0);
		for (int i = 0; i < components.length; i++) {
			if(components[i].startsWith("CH")) {
		        fluid1.addTBPfraction(components[i],fractions1[i], molarmass[i], density[i]);
			}
		        else {
		        fluid1.addComponent(components[i],fractions1[i]);
		        }
		}
		
		fluid1.createDatabase(true);
		fluid1.setMixingRule(2);
		fluid1.setMultiPhaseCheck(true);
		ThermodynamicOperations thermoOps = new ThermodynamicOperations(fluid1);
		for (int i = 0; i < P_bar.length; i++) {
			fluid1.setTemperature(T_C[i] + 273.15);
			fluid1.setPressure(P_bar[i]);
			thermoOps.TPflash();
			fluid1.init(2);
			fluid1.initPhysicalProperties();
			enthalpy[i] = fluid1.getEnthalpy();
			entropy[i] = fluid1.getEntropy();
			System.out.println("etnhalpy " + enthalpy[i]);
		}
		
		for (int i = 0; i < P_bar.length; i++) {
			fluid1.setPressure(P_bar[i]);
			thermoOps.PHflash(enthalpy[i]);
			errH[i] = fluid1.getTemperature() - T_C[i] - 273.15;
			System.out.println("err " + errH[i]);
			//assertTrue(Math.abs(errH[i]) < 1e-2);
		}
    }

    public static void main(String[] args) {
    	TPflashGR test = new TPflashGR();
    	test.testrun();
    	
		
    }
}
