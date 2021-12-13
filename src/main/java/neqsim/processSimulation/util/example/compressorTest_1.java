package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.stream.Stream;

public class compressorTest_1 {
    /**
     * This method is just meant to test the thermo package.
     */
    public static void main(String args[]) {

                comp_1.setPolytropicEfficiency(0.74629255);

                neqsim.processSimulation.processSystem.ProcessSystem operations =
                                new neqsim.processSimulation.processSystem.ProcessSystem();
                operations.add(stream_1);
                operations.add(comp_1);

                operations.run();

                // comp_1.solvePolytropicEfficiency(380.0);
                // operations.displayResult();
                System.out.println("power " + comp_1.getTotalWork());

                System.out.println("speed of sound " + comp_1.getOutStream().getThermoSystem()
                                .getPhase(0).getSoundSpeed());
                System.out.println("out temperature"
                                + comp_1.getOutStream().getThermoSystem().getTemperature());
                System.out.println("Cp "
                                + comp_1.getOutStream().getThermoSystem().getPhase(0).getCp());
                System.out.println("Cv "
                                + comp_1.getOutStream().getThermoSystem().getPhase(0).getCv());
                System.out.println("molarmass " + comp_1.getOutStream().getThermoSystem()
                                .getPhase(0).getMolarMass());
                System.out.println("molarmass " + comp_1.getOutStream().getThermoSystem()
                                .getPhase(0).getMolarMass());

                double outTemp = 500.1; // temperature in Kelvin
                double efficiency = comp_1.solveEfficiency(outTemp);
                System.out.println("compressor polytropic efficiency " + efficiency);
                System.out.println("compressor out temperature "
                                + comp_1.getOutStream().getTemperature());
                System.out.println("compressor power " + comp_1.getPower() + " J/sec");
                System.out.println("compressor head "
                                + comp_1.getPower()
                                                / comp_1.getThermoSystem().getTotalNumberOfMoles()
                                + " J/mol");
        }
}
