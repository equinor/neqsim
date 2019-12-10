package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.log4j.Logger;

public class WaxFlash {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(WaxFlash.class);

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkEos(273.0 + 92, 4.0);
        //SystemInterface testSystem = new SystemPCSAFT(239.0, 223.0);
        //SystemInterface testSystem = new SystemGERG2004Eos(121.0, 1.61);
        //     testSystem.getWaxCharacterisation().setModelName("Pedersen");
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("methane", 21.0);
        testSystem.addComponent("ethane", 4.5);
        //testSystem.addComponent("nC10", 80.5);
        //   testSystem.addComponent("n-heptane", 1.0);
        //   testSystem.addComponent("nC14", 10.0);
        //   testSystem.addComponent("nC16", 10.0);
        //testSystem.addComponent("nC10", 0.8);
           testSystem.addTBPfraction("C7", 10.0, 93.30 / 1000.0, 0.73);
          testSystem.addTBPfraction("C8", 10.0, 106.60 / 1000.0, 0.7533);
        //   testSystem.addTBPfraction("C9", 10.0, 119.60 / 1000.0, 0.7653);
     //   testSystem.addTBPfraction("C15", 21, 132.60 / 1000.0, 0.79);
     //   testSystem.addTBPfraction("C20", 52.00, 343 / 1000.0, 0.85);
        testSystem.addPlusFraction("C20", 10.62, 481.0 / 1000.0, 0.882888);

        testSystem.getCharacterization().characterisePlusFraction();
        // testSystem.addComponent("MEG", 8.8);
              testSystem.addComponent("water", 10.8);

        testSystem.getWaxModel().addTBPWax();
        testSystem.createDatabase(true);
      //  testSystem.setMixingRule(2);
        testSystem.addSolidComplexPhase("wax");
        //  testSystem.setSolidPhaseCheck("nC14");
        testSystem.setMultiphaseWaxCheck(true);
      //  testSystem.autoSelectMixingRule();
       // testSystem.setMixingRule(2);
        //testSystem.setMultiPhaseCheck(true);
        //  //    testSystem.getPhase(5).setPressure(20.0);
//testSystem.setTemperature(232);
        //  testSystem.setSolidPhaseCheck("n-hexane");
        testSystem.init(0);
        testSystem.init(1);
        try {
            //   testOps.TPflash();
            //        System.out.println("wax in oil " + testSystem.getPhase(0).getWtFractionOfWaxFormingComponents());
            //  testSystem.setPressure(5.0);
         //       testOps.calcWAT();
            //  testSystem.display();
            //   testSystem.setPressure(100.0);
            //   testOps.calcWAT();
            // testOps.hydrateFormationTemperature();
            //testOps.freezingPointTemperatureFlash();
            // testOps.TPflash();
            testOps.TPflash();
        //    testSystem.getPhase("oil").getPhysicalProperties().calcEffectiveDiffusionCoefficients();
          //  testSystem.getPhase("oil").getPhysicalProperties().getEffectiveDiffusionCoefficient(12);
            testSystem.display();
            //       testSystem.init(0);
            //   testSystem.display();
            //testOps.waterDewPointTemperatureFlash();
            //testOps.bubblePointTemperatureFlash();
            //   testSystem.display();
            //  testSystem.setMolarComposition(new double[]{0.1, 0.1, 0.1, 0.0001, 0.0000000001});

            //          testOps.TPflash();
            //         testSystem.display();
            //        testSystem.getWaxModel().removeWax();
            //         testSystem.getWaxModel().addTBPWax();
            //        testOps.TPflash();
            //         testSystem.display();
            // testSystem.init(0);
            //    testSystem.getWaxModel().setWaxParameters(new double[] {testSystem.getWaxModel().getWaxParameters()[0]*1.1,testSystem.getWaxModel().getWaxParameters()[1]*1.1, testSystem.getWaxModel().getWaxParameters()[2]*1.0});
            //testSystem.getWaxModel().addTBPWax();
//
            //  testOps.TPflash();
            //  testSystem.display();
            //  testOps.dewPointTemperatureFlash();
            //  testSystem.display();
            //testOps.TPflash();
        } catch (Exception e) {
            logger.error("error", e);
        }
        double waxVOlumeFrac = 0;
        if (testSystem.hasPhaseType("wax")) {
            waxVOlumeFrac = testSystem.getWtFraction(testSystem.getPhaseIndexOfPhase("wax"));
        }
        //    testSystem.getPhase("oil").getPhysicalProperties().getViscosityOfWaxyOil(waxVOlumeFrac, 1000.0);
        //   System.out.println("viscosity wax-oil suspesion " + testSystem.getPhase("oil").getPhysicalProperties().getViscosityOfWaxyOil(waxVOlumeFrac, 1000.0));
    }
}
