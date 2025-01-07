package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TPflashGR class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TPflashGR {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPflashGR.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // String[] components = new String[]{"water", "nitrogen", "CO2", "methane",
    // "ethane", "propane", "i-butane","n-butane","i-pentane", "n-pentane",
    // "CHCmp_1", "CHCmp_2", "CHCmp_3", "CHCmp_4"
    // ,"CHCmp_5","CHCmp_6","CHCmp_7","CHCmp_8","CHCmp_9","CHCmp_10","CHCmp_11","CHCmp_12","CHCmp_13"};
    // double[] fractions1 = new double[] {0.0304006958007813, 4.73001127829775E-07,
    // 0.000380391739308834, 0.00102935172617435, 0.00350199580192566,
    // 0.0149815678596497, 0.00698469817638397 , 0.0226067280769348,
    // 0.0143046414852142, 0.0203909373283386, 0.0352155113220215,
    // 0.0705802822113037,0.0850765609741211,0.0605201292037964, 0.1793018150329590,
    // 0.1033354282379150, 0.0706664896011353,
    // 0.0626348257064819,0.0488108015060425,0.0484040451049805,0.0417061710357666,0.0425787830352783,0.0365876793861389
    // };
    // double[] molarmass = new double[] {0.0386243104934692, 1.08263303991407E-05,
    // 0.00019008457660675, 0.00305547803640366, 0.00200786963105202,
    // 0.00389420658349991,0.00179276615381241 , 0.00255768150091171,
    // 0.00205287128686905, 0.00117853358387947 ,
    // 0.0854749984741211,0.0890039978027344,0.1021979980468750,0.1156969985961910,0.1513029937744140,0.2105240020751950,0.2728500061035160,0.3172810058593750,0.3585450134277340,0.4076000061035160,0.4698110046386720,0.5629600219726560,
    // 0.7858560180664060 };
    // double[] density = new double[] {0.0386243104934692, 1.08263303991407E-05,
    // 0.00019008457660675, 0.00305547803640366, 0.00200786963105202,
    // 0.00389420658349991,0.00179276615381241 , 0.00255768150091171,
    // 0.00205287128686905, 0.00117853358387947,
    // 0.664700031280518,0.757499992847443,0.778400003910065,0.792500019073486,0.82480001449585,0.869700014591217,0.881599962711334,0.89300000667572,0.90200001001358,0.911700010299683,
    // 0.923400044441223,0.939900040626526,0.979299962520599};

    String[] components = new String[] {"water", "nitrogen", "CO2", "methane", "ethane", "propane",
        "i-butane", "n-butane", "i-pentane", "n-pentane", "C6", "C7", "C8", "C9", "C10"}; // ,"CHCmp_6","CHCmp_7","CHCmp_8"};
                                                                                          // //,"CHCmp_9","CHCmp_10","CHCmp_11","CHCmp_12","CHCmp_13"};
    double[] fractions1 = new double[] {0.691986417509639, 0.001518413245518, 0.004876419074493,
        0.177034951950947, 0.016102566295901, 0.008162056735947, 0.002489557955828,
        0.008657117478144, 0.006116331881632, 0.007300146475110, 0.008772462741648,
        0.012794973584387, 0.012834050157103, 0.007273111871068,
        0.014261799565032 + 0.010199184799741 + 0.005722681071876 + 0.003897757605989};
    double[] molarmass = new double[] {18.0153, 28.0135, 44.0098, 16.0429, 30.0698, 44.0968,
        58.1239, 58.1239, 72.1510, 72.1510, 86.1776 / 1000.0, 90.1140 / 1000.0, 102.0386 / 1000.0,
        117.4548 / 1000.0, 205.5306 / 1000.0};
    double[] density = new double[] {1.0, -1.0000e+19, -1.0000e+19, -1.0000e+19, -1.0000e+19,
        -1.0000e+19, -1.0000e+19, -1.0000e+19, -1.0000e+19, -1.0000e+19, 667.4991 / 1000.0,
        746.5672 / 1000.0, 787.1960 / 1000.0, 776.5150 / 1000.0, 849.9863 / 1000.0,};

    double[] P_bar = new double[] {43.991, 1, 1, 1, 10, 10, 10, 10, 100, 100, 100, 100};
    double[] T_C = new double[] {330.54 - 273.15, 0, 15, 30, 100, 0, 15, 30, 100, 0, 15, 30, 100};

    double[] enthalpy = new double[P_bar.length];
    double[] entropy = new double[P_bar.length];

    double[] errH = new double[P_bar.length];
    double[] errS = new double[P_bar.length];
    SystemInterface fluid1 = new SystemSrkEos(298.0, 10.0);
    fluid1.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(8);
    for (int i = 0; i < components.length; i++) {
      if (components[i].startsWith("C") && !components[i].startsWith("CO2")) {
        fluid1.addTBPfraction(components[i], fractions1[i], molarmass[i], density[i]);
      } else {
        fluid1.addComponent(components[i], fractions1[i]);
      }
    }
    fluid1.setHeavyTBPfractionAsPlusFraction();
    fluid1.getCharacterization().characterisePlusFraction();

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
      logger.debug("enthalpy " + enthalpy[i]);
      fluid1.display();
    }
    /*
     * for (int i = 0; i < P_bar.length; i++) { fluid1.setPressure(P_bar[i]);
     * thermoOps.PHflash(enthalpy[i]); errH[i] = fluid1.getTemperature() - T_C[i] - 273.15;
     * System.out.println("err " + errH[i]); //assertTrue(Math.abs(errH[i]) < 1e-2); }
     */
  }
}
