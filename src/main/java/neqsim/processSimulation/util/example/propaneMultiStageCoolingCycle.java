/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger;

/**
 *
 * @author esol
 */
public class propaneMultiStageCoolingCycle {

    private static final long serialVersionUID = 1000;

    public static void main(String args[]) {

        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemPrEos((273.15 + 30.0), 15.00);
        testSystem.addComponent("propane", 261759.0, "kg/hr");
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        neqsim.thermo.system.SystemInterface testSystemEthane = new neqsim.thermo.system.SystemPrEos((273.15 - 40.0),
                15.00);
        testSystemEthane.addComponent("ethane", 130759.0, "kg/hr");
        testSystemEthane.createDatabase(true);
        testSystemEthane.setMixingRule(2);

        Stream stream_Ethane = new Stream("Stream1", testSystemEthane);
        // stream_Ethane.setSpecification("bubT");

        // ThrottlingValve JTvalve1_et = new ThrottlingValve(stream_Ethane);
        // JTvalve1_et.setOutletPressure(5.0);

        neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemPrEos((273.15 + 30.0), 60.00);
        testSystem2.addComponent("methane", 0.9);
        testSystem2.addComponent("ethane", 0.08);
        testSystem2.addComponent("propane", 0.1);
        testSystem2.addComponent("n-butane", 0.01);
        testSystem2.addComponent("n-hexane", 0.006);
        testSystem2.createDatabase(true);
        testSystem2.setMixingRule(2);
        testSystem2.setTotalFlowRate(8.0, "MSm3/day");

        StreamInterface naturalGasInletStream = new Stream("NG stream", testSystem2);

        Stream stream_1 = new Stream("Stream1", testSystem);
        stream_1.setSpecification("bubT");

        ThrottlingValve JTvalve1 = new ThrottlingValve(stream_1);
        JTvalve1.setOutletPressure(5.0);

        HeatExchanger heatEx1_et = new HeatExchanger(stream_Ethane);
        heatEx1_et.setFeedStream(1, JTvalve1.getOutStream());
        heatEx1_et.setSpecification("out stream");

        Stream stream_Ethane_out = new Stream(heatEx1_et.getOutStream(0));
        stream_Ethane.setSpecification("bubT");
        heatEx1_et.setOutStream(0, stream_Ethane_out);

        ThrottlingValve JTvalve1_et = new ThrottlingValve(stream_Ethane_out);
        JTvalve1_et.setOutletPressure(2.5);

        HeatExchanger heatEx1 = new HeatExchanger(naturalGasInletStream);
        heatEx1.setGuessOutTemperature(273.0 + 10.0);
        heatEx1.setUAvalue(10000.0);
        heatEx1.setFeedStream(1, heatEx1_et.getOutStream(1));

        Stream coldMidGasFromPropaneCooler = new Stream(heatEx1.getOutStream(0));

        StreamInterface heatExPropaneOut = heatEx1.getOutStream(1);

        Separator sep1 = new Separator(heatExPropaneOut);

        ThrottlingValve JTvalve2 = new ThrottlingValve(sep1.getLiquidOutStream());
        JTvalve2.setOutletPressure(1.5);

        HeatExchanger heatEx2 = new HeatExchanger(heatEx1.getOutStream(0));
        heatEx2.setFeedStream(1, JTvalve2.getOutStream());
        heatEx2.setSpecification("out stream");

        // Cooler cooler = new Cooler(heatEx2.getOutStream(1));
        // cooler.setSpecification("out stream");

        Stream stream_2 = new Stream(heatEx2.getOutStream(1));
        stream_2.setSpecification("dewP");
        stream_2.run();
        heatEx2.setOutStream(1, stream_2);

        Stream coldGasFromPropaneCooler = new Stream(heatEx2.getOutStream(0));

        HeatExchanger heatEx22 = new HeatExchanger(heatEx2.getOutStream(0));
        heatEx22.setFeedStream(1, JTvalve1_et.getOutStream());
        heatEx22.setSpecification("out stream");

        Stream stream_22 = new Stream(heatEx22.getOutStream(1));
        stream_22.setSpecification("dewP");
        heatEx22.setOutStream(1, stream_22);

        Stream heatEx22stream = new Stream(heatEx22.getOutStream(0));

        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();

        operations.add(stream_Ethane);
        operations.add(stream_1);
        operations.add(naturalGasInletStream);
        operations.add(JTvalve1);
        operations.add(heatEx1_et);
        operations.add(stream_Ethane_out);
        operations.add(JTvalve1_et);
        operations.add(heatEx1);
        operations.add(coldMidGasFromPropaneCooler);
        operations.add(heatExPropaneOut);
        operations.add(sep1);
        operations.add(JTvalve2);
        operations.add(heatEx2);
        // operations.add(cooler);
        operations.add(stream_2);
        operations.add(coldGasFromPropaneCooler);
        operations.add(heatEx22);
        operations.add(stream_22);
        operations.add(heatEx22stream);

        operations.run();
        operations.run();

        // heatEx1.run();

        coldGasFromPropaneCooler.displayResult();
        heatEx22stream.getFluid().display();
        JTvalve2.getOutStream().getFluid().display();
        stream_22.getFluid().display();

        // JTvalve1.displayResult();
        // heatExPropaneOut.displayResult();

        /*
         * heatEx1.getOutStream(0).displayResult();
         * heatEx1.getInStream(1).displayResult();
         * heatEx1.getOutStream(1).displayResult(); System.out.println("heatex duty " +
         * heatEx1.getDuty()); heatEx2.run(); heatEx2.getOutStream(0).displayResult();
         * heatEx2.getInStream(1).displayResult();
         * heatEx2.getOutStream(1).displayResult(); System.out.println("heatex duty " +
         * heatEx2.getDuty());
         */
        // stream_3.displayResult();
        // compressor1.displayResult();
        // cooler3.displayResult();
        // JTvalve.displayResult();
        // compressor1.displayResult();
        // stream_2.displayResult();
        // operations.displayResult();
        // System.out.println("compressor work" + compressor1.getEnergy()/1.0e3 + "
        // kW");
        // System.out.println("compressor isentropic ef " +
        // compressor1.getIsentropicEfficiency());
        // System.out.println("cooler duty " + cooler.getEnergyInput()/1.0e3 + " kW");
        // System.out.println("cooler2 duty " + cooler2.getEnergyInput()/1.0e3 + " kW");
        // System.out.println("cooler3 duty " + cooler3.getEnergyInput()/1.0e3 + " kW");
        // System.out.println("heater duty " + heater.getEnergyInput());

    }
}
