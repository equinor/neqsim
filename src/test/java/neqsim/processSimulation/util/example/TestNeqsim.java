package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * <p>TestNeqsim class.</p>
 *
 * @author Administrator
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestNeqsim {
    /**
     * <p>main.</p>
     *
     * @param args the command line arguments
     */
    @SuppressWarnings("unused")
    public static void main(String[] args) {
        SystemInterface testFluid = new SystemSrkEos(298.15, 10.0);// SystemSrkCPAstatoil(298.1,
                                                                   // 10.9); //298.15 K

        testFluid.addComponent("methane", 8.8316);
        testFluid.addComponent("nitrogen", 0.0296682);
        testFluid.addComponent("CO2", 0.0209667);
        testFluid.addComponent("ethane", 0.28915);
        testFluid.addComponent("propane", 0.116637);
        testFluid.addComponent("i-butane", 0.0222488);
        testFluid.addComponent("n-butane", 0.0315726);
        testFluid.addComponent("i-pentane", 0.0097763);
        testFluid.addComponent("n-pentane", 0.0104928);

        // testFluid.addTBPfraction("C7", 0.0137641, 86.18 / 1000.0, 0.664);
        // testFluid.addTBPfraction("C8", 0.025341, 96.46 / 1000.0, 0.7217);
        // testFluid.addTBPfraction("C9", 0.023069, 124.66 / 1000.0, 0.7604);
        // testFluid.addTBPfraction("C10", 0.0031771, 178.2 / 1000.0, 0.8021);
        // testFluid.addTBPfraction("C11", 0.0000188549, 263.77 / 1000.0, 0.8416);

        testFluid.addComponent("water", 0.39944);
        // testFluid.addComponent("MEG", 0.173083);

        testFluid.createDatabase(true);
        testFluid.setMixingRule(2); // use 10 for CPA and 2 for SRK/PR
        testFluid.setMultiPhaseCheck(true);
        testFluid.initPhysicalProperties();
        double[] temperature = new double[10];
        double[] work = new double[10];
        double[] Cp_Vapour = new double[10];
        double[] Cp_liquid = new double[10];
        double[] Cp = new double[10];
        double[] Density_Vapour = new double[10];
        double[] Density_liquid = new double[10];
        double[] Density = new double[10];

        // for(int i=0;i<10;i++){
        Stream stream1 = new Stream("stream1", testFluid);
        Compressor compressor1 = new Compressor("compressor1", stream1);
        compressor1.setOutletPressure(26.590909);// (20+5*i)
        compressor1.setUsePolytropicCalc(true);
        compressor1.setPolytropicEfficiency(0.64951);
        Stream stream2 = new Stream("stream2", compressor1.getOutStream());

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream1);
        operations.add(compressor1);
        operations.add(stream2);
        operations.run();
        // compressor1.getOutStream().getThermoSystem().display();

        double CpVapour = stream1.getThermoSystem().getPhase(0).getCp();
        double Cpliquid = stream1.getThermoSystem().getPhase(2).getCp();
        double CpOil = stream1.getThermoSystem().getPhase(1).getCp();
        double Cpone = stream1.getThermoSystem().getPhase(0).getBeta() * CpVapour
                + stream1.getThermoSystem().getPhase(1).getBeta() * CpOil
                + stream1.getThermoSystem().getPhase(2).getBeta() * Cpliquid;

        double DensityVapour = stream1.getThermoSystem().getPhase(0).getDensity();
        double Densityliquid = stream1.getThermoSystem().getPhase(2).getDensity();
        double DensityOil = stream1.getThermoSystem().getPhase(1).getDensity();
        double Density1 = stream1.getThermoSystem().getWtFraction(0) * DensityVapour
                + stream1.getThermoSystem().getWtFraction(1) * DensityOil
                + stream1.getThermoSystem().getWtFraction(2) * Densityliquid;
        // operations.run();
        System.out.println("work " + compressor1.getTotalWork() + " density " + Density1 + " Cp "
                + Cpone + " SPEED OF SOUND "
                + stream1.getThermoSystem().getPhase(0).getSoundSpeed());

        compressor1.solveEfficiency(390.15);
        compressor1.getOutStream().displayResult();
        System.out.println("Hvap " + stream1.getThermoSystem().getHeatOfVaporization() + " POLI "
                + compressor1.getPolytropicEfficiency() + " dentity "
                + stream1.getThermoSystem().getDensity() + " cp "
                + stream1.getThermoSystem().getCp());
        stream1.getThermoSystem().display();

        double massFlowGas = stream1.getThermoSystem().getPhase(0).getBeta()
                * stream1.getThermoSystem().getPhase(0).getMolarMass();
        double massFlowLiq = stream1.getThermoSystem().getPhase(2).getBeta()
                * stream1.getThermoSystem().getPhase(2).getMolarMass();
        double massFlowOil = stream1.getThermoSystem().getPhase(1).getBeta()
                * stream1.getThermoSystem().getPhase(1).getMolarMass();

        double volFlowGas = massFlowGas / stream1.getThermoSystem().getPhase(0).getDensity();
        double volFlowLiq = massFlowLiq / stream1.getThermoSystem().getPhase(2).getDensity();
        double volFlowOil = massFlowOil / stream1.getThermoSystem().getPhase(1).getDensity();

        double GMF = massFlowGas / (massFlowGas + massFlowOil + massFlowLiq);
        double GVF = volFlowGas / (volFlowGas + volFlowOil + volFlowLiq);
        System.out.println("inlet stream -  GMF " + GMF + "  GVF " + GVF + " Z IN "
                + stream1.getThermoSystem().getZ() + " Z OUT "
                + compressor1.getOutStream().getThermoSystem().getZ());
        /*
         * temperature[i] = compressor1.getOutStream().getTemperature(); work [i] =
         * compressor1.getTotalWork();
         * 
         * Cp_Vapour [i] = compressor1.getOutStream().getThermoSystem().getPhase(0).getCp();
         * Cp_liquid [i] = compressor1.getOutStream().getThermoSystem().getPhase(1).getCp(); Cp [i]
         * = compressor1.getOutStream().getThermoSystem().getPhase(0).getBeta() * Cp_Vapour [i] +
         * compressor1.getOutStream().getThermoSystem().getPhase(1).getBeta()* Cp_liquid [i];
         * 
         * Density_Vapour [i] =
         * compressor1.getOutStream().getThermoSystem().getPhase(0).getDensity(); Density_liquid [i]
         * = compressor1.getOutStream().getThermoSystem().getPhase(1).getDensity(); Density [i] =
         * compressor1.getOutStream().getThermoSystem().getWtFraction(0) * Density_Vapour [i] +
         * compressor1.getOutStream().getThermoSystem().getWtFraction(1)* Density_liquid [i];
         */
        // }

        // System.out.println("poli");
        // stream2.displayResult();
        // operations.displayResult();
        // ThermodynamicOperations flash = new ThermodynamicOperations(testFluid);
        // try{
        // flash.TPflash();
    }
    // catch(Exception e){
    /*
     * System.out.println( "P_out" ); for (int i=0;i<10;i++ ) { System.out.println(20 + 5*i);
     * 
     * }
     * 
     * System.out.println( "Temperature" ); for (int i=0;i<10;i++ ) { System.out.println(
     * temperature[i] );
     * 
     * }
     * 
     * System.out.println( "Work" ); for (int i=0;i<10;i++ ) { System.out.println( work [i] );
     * 
     * } System.out.println( "Cp" ); for (int i=0;i<10;i++ ) { System.out.println( Cp [i] );
     * 
     * }
     * 
     * System.out.println( "Density" ); for (int i=0;i<10;i++ ) { System.out.println( Density [i] );
     */
}
// }
// testFluid.display();
// }
