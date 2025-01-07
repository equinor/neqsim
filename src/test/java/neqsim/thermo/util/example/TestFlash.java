package neqsim.thermo.util.example;

import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.api.ioc.CalculationResult;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestFlash class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestFlash {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestFlash.class);

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
    int phaseNumber;

    Double[][] fluidProperties;
    int fluidNumber = 1;
    int flashMode = 1;
    Double[] spec1 = {1.0, 23.2, 24.23, 25.98, 25.23, 26.1, 27.3, 28.7, 23.5, 1.0};
    Double[] spec2 = {288.15, 290.1, 295.1, 301.2, 299.3, 310.2, 315.3, 310.0, 305.2, 312.7}; // Temperatures
    // double[]
    // spec2={-470.0,-480.0,-475.0,-471.0,-474.0,-450.0,-480.0,-473.0,-471.0,-477.0};
    // // Enthalpies
    // double[] spec2={-18.0,-19.0,-18.5,-18.0,-15.0,-19.5,-22.0,-21.0,-18.7,-18.0};
    // // Entropies

    // Fractions for use with fluid number 1
    double[] fractions = {0.01, 0.02, 0.03, 0.01, 0.80, 0.04, 0.03, 0.02, 0.01, 0.01, 0.01, 0.01, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    // double[] fractions={0.01, 0.02, +.03, 0.01, 0.70, 0.14, 0.03, 0.02, 0.01,
    // 0.01, 0.01, 0.01};
    // Normalize fractions sum fractions = 1

    SystemInterface fluid = new SystemSrkEos(273.15 + 45.0, 22.0);
    ThermodynamicOperations fluidOps = new ThermodynamicOperations(fluid);

    if (fluidNumber == 1) {
      // Fluid gas
      fluid.addComponent("water", 0.01);
      fluid.addComponent("nitrogen", 0.02);
      fluid.addComponent("CO2", 0.03);
      fluid.addComponent("H2S", 0.01);
      fluid.addComponent("methane", 0.80);
      fluid.addComponent("ethane", 0.04);
      fluid.addComponent("propane", 0.03);
      fluid.addComponent("i-butane", 0.02);
      fluid.addComponent("n-butane", 0.01);
      fluid.addComponent("i-pentane", 0.01);
      fluid.addComponent("n-pentane", 0.01);
      fluid.addComponent("n-hexane", 0.01);
      fluid.createDatabase(true);
      fluid.setMixingRule(2);
      fluid.useVolumeCorrection(true);
      fluid.setMultiPhaseCheck(true);
    } else if (fluidNumber == 2) { // example to show property calc for pure phase
                                   // (without
                                   // flash)
      // Fluid air
      fluid.addComponent("nitrogen", 0.79);
      fluid.addComponent("oxygen", 0.21);
      fluid.createDatabase(true);
      fluid.setMixingRule(2);
      fluid.useVolumeCorrection(true);
      fluid.init(0); // careful: this method will reset forced phase types
      fluid.setMaxNumberOfPhases(1);
      fluid.setForcePhaseTypes(true);
      fluid.setPhaseType(0, PhaseType.GAS);
    } else if (fluidNumber == 3) {
      // Fluid water
      fluid.addComponent("water", 1.0);
      fluid.createDatabase(true);
      fluid.setMixingRule(2);
      fluid.useVolumeCorrection(true);
      fluid.setMultiPhaseCheck(true);
    } else if (fluidNumber == 4) {
      // Fluid extended gas
      fluid.addComponent("water", 0.01);
      fluid.addComponent("helium", 0.01);
      fluid.addComponent("hydrogen", 0.01);
      fluid.addComponent("nitrogen", 0.01);
      fluid.addComponent("argon", 0.01);
      fluid.addComponent("oxygen", 0.01);
      fluid.addComponent("CO2", 0.01);
      fluid.addComponent("H2S", 0.01);
      fluid.addComponent("methane", 0.80);
      fluid.addComponent("ethane", 0.04);
      fluid.addComponent("propane", 0.03);
      fluid.addComponent("i-butane", 0.02);
      fluid.addComponent("n-butane", 0.01);
      fluid.addComponent("i-pentane", 0.01);
      fluid.addComponent("n-pentane", 0.01);
      fluid.addComponent("n-hexane", 0.01);
      fluid.addComponent("n-heptane", 0.01);
      fluid.addComponent("n-octane", 0.01);
      fluid.addComponent("n-nonane", 0.01);
      fluid.addComponent("nC10", 0.01);
      fluid.addComponent("nC11", 1);
      fluid.createDatabase(true);
      fluid.setMixingRule(2);
      fluid.useVolumeCorrection(true);
      fluid.setMultiPhaseCheck(true);
    } else if (fluidNumber == 5) {
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
      fluid.addTBPfraction("CHCmp_10", 0.01286722421646120, 0.3303389892578130, 0.875513017177582);
      fluid.addTBPfraction("CHCmp_11", 0.00838199377059937, 0.3846969909667970, 0.888606309890747);
      fluid.addTBPfraction("CHCmp_12", 0.00746552944183350, 0.4711579895019530, 0.906100511550903);
      fluid.addTBPfraction("CHCmp_13", 0.00590102910995483, 0.6624600219726560, 0.936200380325317);
      fluid.createDatabase(true);
      fluid.setMixingRule(2);
      fluid.useVolumeCorrection(true);
      fluid.setMultiPhaseCheck(true);
    } else if (fluidNumber == 6) {
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
      fluid.addTBPfraction("CHCmp_2", 0.048198757171630900, 0.0987799987792969, 0.754330039024353);
      fluid.addTBPfraction("CHCmp_3", 0.097208471298217800, 0.1412200012207030, 0.81659996509552);
      fluid.addTBPfraction("CHCmp_4", 0.165174083709717000, 0.1857899932861330, 0.861050009727478);
      fluid.addTBPfraction("CHCmp_5", 0.279571933746338000, 0.2410899963378910, 0.902539968490601);
      fluid.addTBPfraction("CHCmp_6", 0.240494251251221000, 0.4045100097656250, 0.955269992351531);
      fluid.addTBPfraction("CHCmp_7", 0.113120021820068000, 0.9069699707031250, 1.0074599981308);
      fluid.createDatabase(true);
      fluid.setMixingRule(2);
      fluid.useVolumeCorrection(true);
      fluid.setMultiPhaseCheck(true);
    } else if (fluidNumber == 7) {
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
      fluid.createDatabase(true);
      fluid.setMixingRule(2);
      fluid.useVolumeCorrection(true);
      fluid.setMultiPhaseCheck(true);
    }

    // Set fractions for gas
    // fluid.setMolarComposition(fractions);

    long time = System.currentTimeMillis();

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(fluid);

    CalculationResult s =
        thermoOps.propertyFlash(Arrays.asList(spec1), Arrays.asList(spec2), 3, null, null);
    fluidProperties = s.fluidProperties;

    logger.info("Time taken for 10 flash calcs [ms] = " + (System.currentTimeMillis() - time));

    int t = 0;
    int k = 0;
    logger.info("Mix Number of Phases                            " + fluidProperties[t][k++]);
    logger.info("Mix Pressure [Pa]                         " + fluidProperties[t][k++]);
    logger.info("Mix Temperature [K]                       " + fluidProperties[t][k++]);
    logger.info("Mix Mole Percent                          " + fluidProperties[t][k++]);
    logger.info("Mix Weight Percent                        " + fluidProperties[t][k++]);
    logger.info("Mix Molar Volume               [m3/mol]   " + fluidProperties[t][k++]);
    logger.info("Mix Volume Percent                        " + fluidProperties[t][k++]);
    logger.info("Mix Density                    [kg/m3]    " + fluidProperties[t][k++]);
    logger.info("Mix Z Factor                              " + fluidProperties[t][k++]);
    logger.info("Mix Molecular Weight           [g/mol]    " + fluidProperties[t][k++]);
    logger.info("Mix Enthalpy                   [J/mol]    " + fluidProperties[t][k++]);
    logger.info("Mix Entropy                    [J/molK]   " + fluidProperties[t][k++]);
    logger.info("Mix Heat Capacity-Cp           [J/molK]   " + fluidProperties[t][k++]);
    logger.info("Mix Heat Capacity-Cv           [J/molK]   " + fluidProperties[t][k++]);
    logger.info("Mix Mix Kappa (Cp/Cv)                     " + fluidProperties[t][k++]);
    logger.info("Mix JT Coefficient             [K/Pa]     " + fluidProperties[t][k++]);
    logger.info("Mix Velocity of Sound          [m/s]      " + fluidProperties[t][k++]);
    logger.info("Mix Viscosity [Pa s] eller     [kg/(m*s)] " + fluidProperties[t][k++]);
    logger.info("Mix Thermal Conductivity        W/mK]     " + fluidProperties[t][k++]);
    // logger.info("\n");
    logger.info("Gas Mole Percent                          " + fluidProperties[t][k++]);
    logger.info("Gas Weight Percent                        " + fluidProperties[t][k++]);
    logger.info("Gas Molar Volume               [m3/mol]   " + fluidProperties[t][k++]);
    logger.info("Gas Volume Percent                        " + fluidProperties[t][k++]);
    logger.info("Gas Density                    [kg/m3]    " + fluidProperties[t][k++]);
    logger.info("Gas Z Factor                              " + fluidProperties[t][k++]);
    logger.info("Gas Molecular Weight           [g/mol]    " + fluidProperties[t][k++]);
    logger.info("Gas Enthalpy                   [J/mol]    " + fluidProperties[t][k++]);
    logger.info("Gas Entropy                    [J/molK]   " + fluidProperties[t][k++]);
    logger.info("Gas Heat Capacity-Cp           [J/molK]   " + fluidProperties[t][k++]);
    logger.info("Gas Heat Capacity-Cv           [J/molK]   " + fluidProperties[t][k++]);
    logger.info("Gas Gas Kappa (Cp/Cv)                     " + fluidProperties[t][k++]);
    logger.info("Gas JT Coefficient             [K/Pa]     " + fluidProperties[t][k++]);
    logger.info("Gas Velocity of Sound          [m/s]      " + fluidProperties[t][k++]);
    logger.info("Gas Viscosity [Pa s] eller     [kg/(m*s)] " + fluidProperties[t][k++]);
    logger.info("Gas Thermal Conductivity       [W/mK]     " + fluidProperties[t][k++]);
    // logger.info("\n");
    logger.info("Liquid Mole Percent                       " + fluidProperties[t][k++]);
    logger.info("Liquid Weight Percent                     " + fluidProperties[t][k++]);
    logger.info("Liquid Molar Volume            [m3/mol]   " + fluidProperties[t][k++]);
    logger.info("Liquid Volume Percent                     " + fluidProperties[t][k++]);
    logger.info("Liquid Density                 [kg/m3]    " + fluidProperties[t][k++]);
    logger.info("Liquid Z Factor                           " + fluidProperties[t][k++]);
    logger.info("Liquid Molecular Weight        [g/mol]    " + fluidProperties[t][k++]);
    logger.info("Liquid Enthalpy                [J/mol]    " + fluidProperties[t][k++]);
    logger.info("Liquid Entropy                 [J/molK]   " + fluidProperties[t][k++]);
    logger.info("Liquid Heat Capacity-Cp        [J/molK]   " + fluidProperties[t][k++]);
    logger.info("Liquid Heat Capacity-Cv        [J/molK]   " + fluidProperties[t][k++]);
    logger.info("Liquid Liquid Kappa (Cp/Cv)               " + fluidProperties[t][k++]);
    logger.info("Liquid JT Coefficient          [K/Pa]     " + fluidProperties[t][k++]);
    logger.info("Liquid Velocity of Sound       [m/s]      " + fluidProperties[t][k++]);
    logger.info("Liquid Viscosity [Pa s] eller  [kg/(m*s)] " + fluidProperties[t][k++]);
    logger.info("Liquid Thermal Conductivity    [W/mK]     " + fluidProperties[t][k++]);
    // logger.info("\n");
    logger.info("Aqueous Mole Percent                      " + fluidProperties[t][k++]);
    logger.info("Aqueous Weight Percent                    " + fluidProperties[t][k++]);
    logger.info("Aqueous Molar Volume           [m3/mol]   " + fluidProperties[t][k++]);
    logger.info("Aqueous Volume Percent                    " + fluidProperties[t][k++]);
    logger.info("Aqueous Density                [kg/m3]    " + fluidProperties[t][k++]);
    logger.info("Aqueous Z Factor                          " + fluidProperties[t][k++]);
    logger.info("Aqueous Molecular Weight       [g/mol]    " + fluidProperties[t][k++]);
    logger.info("Aqueous Enthalpy               [J/mol]    " + fluidProperties[t][k++]);
    logger.info("Aqueous Entropy                [J/molK]   " + fluidProperties[t][k++]);
    logger.info("Aqueous Heat Capacity-Cp       [J/molK]   " + fluidProperties[t][k++]);
    logger.info("Aqueous Heat Capacity-Cv       [J/molK]   " + fluidProperties[t][k++]);
    logger.info("Aqueous Aqueous Kappa (Cp/Cv)             " + fluidProperties[t][k++]);
    logger.info("Aqueous JT Coefficient         [K/Pa]     " + fluidProperties[t][k++]);
    logger.info("Aqueous Velocity of Sound      [m/s]      " + fluidProperties[t][k++]);
    logger.info("Aqueous Viscosity [Pa s] eller [kg/(m*s)] " + fluidProperties[t][k++]);
    logger.info("Aqueous Thermal Conductivity   [W/mK]     " + fluidProperties[t][k++]);

    double interfacialtensiongasoil = Double.NaN;
    double interfacialtensiongasaqueous = Double.NaN;
    double interfacialtensionoilaqueous = Double.NaN;
    if (fluid.hasPhaseType("gas") && fluid.hasPhaseType("oil")) {
      interfacialtensiongasoil = fluid.getInterfacialTension("gas", "oil");
    }

    if (fluid.hasPhaseType("gas") && fluid.hasPhaseType("aqueous")) {
      interfacialtensiongasaqueous = fluid.getInterfacialTension("gas", "aqueous");
    }

    if (fluid.hasPhaseType("oil") && fluid.hasPhaseType("aqueous")) {
      interfacialtensionoilaqueous = fluid.getInterfacialTension("oil", "aqueous");
    }

    logger.info("Interfacial Tension Gas-Oil      [N/m]      " + interfacialtensiongasoil);
    logger.info("Interfacial Tension Gas-Aqueous      [N/m]      " + interfacialtensiongasaqueous);
    logger.info("Interfacial Tension Oil-Aqueous      [N/m]      " + interfacialtensionoilaqueous);

    fluid.display();
  }
}
