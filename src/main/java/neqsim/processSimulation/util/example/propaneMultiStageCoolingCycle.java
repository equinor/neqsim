/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

/**
 *
 * @author esol
 */
public class propaneMultiStageCoolingCycle {

    private static final long serialVersionUID = 1000;

    public static void main(String args[]) {

        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemPrEos((273.15 + 30.0), 15.00);
        testSystem.addComponent("propane", 4759.0, "kg/hr");
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        Stream stream_1 = new Stream("Stream1", testSystem);
        stream_1.setSpecification("bubT");
        stream_1.run();

        ThrottlingValve JTvalve1 = new ThrottlingValve(stream_1);
        JTvalve1.setOutletPressure(3.0);

        Cooler cooler = new Cooler(JTvalve1.getOutStream());
        cooler.setSpecification("out stream");

        Stream stream_2 = new Stream(cooler.getOutStream());
        stream_2.setSpecification("gas quality");
        stream_2.run();
        cooler.setOutStream(stream_2);

        ThrottlingValve JTvalve2 = new ThrottlingValve(stream_2);
        JTvalve2.setOutletPressure(1.1);
        JTvalve2.run();

        Cooler cooler2 = new Cooler(JTvalve2.getOutStream());
        cooler2.setSpecification("out stream");

        Stream stream_3 = new Stream(cooler2.getOutStream());
        stream_3.setSpecification("dewt");
        stream_3.run();
        cooler2.setOutStream(stream_3);

        Compressor compressor1 = new Compressor(stream_3);
        compressor1.setOutletPressure(stream_1.getPressure());

        Cooler cooler3 = new Cooler(compressor1.getOutStream());
        cooler3.setSpecification("out stream");
        cooler3.setOutStream(stream_1);

        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(JTvalve1);
        operations.add(cooler);
        operations.add(stream_2);
        operations.add(JTvalve2);
        operations.add(cooler2);
        operations.add(stream_3);
        operations.add(compressor1);
        operations.add(cooler3);
        //     operations.add(compressor1);
        //    operations.add(heater);

        operations.run();
        stream_1.displayResult();
        stream_2.displayResult();
        stream_3.displayResult();
        compressor1.displayResult();
        cooler3.displayResult();
        //   JTvalve.displayResult();
        //  compressor1.displayResult();
        //  stream_2.displayResult();
        // operations.displayResult();
        System.out.println("compressor work" + compressor1.getEnergy());
        //   System.out.println("compressor isentropic ef " + compressor1.getIsentropicEfficiency());
        System.out.println("cooler duty " + cooler.getEnergyInput());
        System.out.println("cooler2 duty " + cooler2.getEnergyInput());
        System.out.println("cooler3 duty " + cooler3.getEnergyInput());
        //  System.out.println("heater duty " + heater.getEnergyInput());

    }
}
