/*
 * LNGfilling.java
 *
 * Created on 6. september 2006, 14:46
 *
 * To change this template, choose Tools | Template Manager and open the template in the editor.
 */

package neqsim.processSimulation.util.example;

import neqsim.processSimulation.measurementDevice.PressureTransmitter;
import neqsim.processSimulation.measurementDevice.TemperatureTransmitter;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.tank.Tank;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processEquipment.valve.ValveInterface;

/**
 *
 * @author ESOL
 */
public class LNGtankfilling {
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos((273.15 + 0.0), 1.02);
        double total1 = 9.5;
        testSystem.addComponent("nitrogen", total1 * 429.9 / (16.0 / 1000.0) / 3600.0);
        testSystem.addComponent("methane",
                total1 / 1.0e5 * 0.970934 * 429.9 / (16.0 / 1000.0) / 3600.0);
        testSystem.addComponent("ethane",
                total1 / 1.0e5 * 0.02432 * 429.9 / (16.0 / 1000.0) / 3600.0);
        testSystem.addComponent("propane",
                total1 / 1.0e5 * 0.003646 * 429.9 / (16.0 / 1000.0) / 3600.0);
        testSystem.addComponent("i-butane",
                total1 / 1.0e5 * 0.000641 * 429.9 / (16.0 / 1000.0) / 3600.0);
        testSystem.addComponent("n-butane",
                total1 / 1.0e5 * 0.000406 * 429.9 / (16.0 / 1000.0) / 3600.0);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        neqsim.thermo.system.SystemInterface testSystem2 =
                new neqsim.thermo.system.SystemSrkEos((273.15 - 165.0), 7.2);
        total1 = 9.5;
        testSystem2.addComponent("nitrogen", total1 * 0.000122 * 429.9 / (16.0 / 1000.0) / 3600.0);
        testSystem2.addComponent("methane", total1 * 0.970934 * 429.9 / (16.0 / 1000.0) / 3600.0);
        testSystem2.addComponent("ethane", total1 * 0.02432 * 429.9 / (16.0 / 1000.0) / 3600.0);
        testSystem2.addComponent("propane", total1 * 0.003646 * 429.9 / (16.0 / 1000.0) / 3600.0);
        testSystem2.addComponent("i-butane", total1 * 0.000641 * 429.9 / (16.0 / 1000.0) / 3600.0);
        testSystem2.addComponent("n-butane", total1 * 0.000406 * 429.9 / (16.0 / 1000.0) / 3600.0);

        testSystem2.createDatabase(true);
        testSystem2.setMixingRule(2);

        neqsim.thermodynamicOperations.ThermodynamicOperations ops =
                new neqsim.thermodynamicOperations.ThermodynamicOperations(testSystem);
        ops.TPflash();
        testSystem.display();

        Stream stream_1 = new Stream("Methane Stream", testSystem);

        ValveInterface valve = new ThrottlingValve(stream_1);
        valve.setOutletPressure(1.01325 + 0.110);

        Tank tank = new neqsim.processSimulation.processEquipment.tank.Tank();
        tank.addStream(valve.getOutStream());
        // tank.addStream(stream_2);

        ValveInterface valve2 = new ThrottlingValve(tank.getGasOutStream());
        valve2.setOutletPressure(0.9);

        PressureTransmitter tankPressureTransmitter =
                new PressureTransmitter(tank.getGasOutStream());
        tankPressureTransmitter.setUnit("bar");
        tankPressureTransmitter.setMaximumValue(1.2);
        tankPressureTransmitter.setMinimumValue(0.9);

        TemperatureTransmitter tankTemperatureTransmitter =
                new TemperatureTransmitter(tank.getGasOutStream());
        tankTemperatureTransmitter.setUnit("K");
        tankTemperatureTransmitter.setMaximumValue(0.0);
        tankTemperatureTransmitter.setMinimumValue(400.0);

        // ControllerDeviceInterface pressureController = new ControllerDeviceBaseClass();
        // pressureController.setTransmitter(tankPressureTransmitter);
        // pressureController.setReverseActing(false);
        // pressureController.setControllerSetPoint(1.01325+0.110);
        // pressureController.setControllerParameters(2.0,400,0);

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(valve);
        operations.add(tank);
        operations.add(valve2);

        operations.add(tankPressureTransmitter);
        // valve2.setController(pressureController);

        operations.add(tankTemperatureTransmitter);

        operations.run();
        // operations.displayResult();

        valve2.setCv(100.0);

        operations.setTimeStep(10);

        stream_1.setThermoSystem(testSystem2);
        tank.getLiquidOutStream().getThermoSystem().setTotalNumberOfMoles(1.0e-10);
        valve2.setPercentValveOpening(0);

        for (int i = 0; i < 100; i++) {
            operations.runTransient();
            // operations.displayResult();
            if (i % 6 == 0) {
                tank.displayResult();
            }
        }

        operations.printLogFile("c:/temp3.txt");
        operations.setTimeStep(10);

        for (int i = 0; i < 100; i++) {
            operations.runTransient();
            // operations.displayResult();
            if (i % 6 == 0) {
                tank.displayResult();
            }
        }

        operations.printLogFile("c:/temp4.txt");

        operations.setTimeStep(10);

        for (int i = 0; i < 100; i++) {
            operations.runTransient();
            // operations.displayResult();
            if (i % 6 == 0) {
                tank.displayResult();
            }
        }

        operations.printLogFile("c:/temp5.txt");

        operations.setTimeStep(10);

        for (int i = 0; i < 100; i++) {
            operations.runTransient();
            // operations.displayResult();
            if (i % 6 == 0) {
                tank.displayResult();
            }
        }

        operations.printLogFile("c:/temp6.txt");

        for (int i = 0; i < 100; i++) {
            operations.runTransient();
            // operations.displayResult();
            if (i % 6 == 0) {
                tank.displayResult();
            }
        }

        operations.printLogFile("c:/temp7.txt");

        for (int i = 0; i < 100; i++) {
            operations.runTransient();
            // operations.displayResult();
            if (i % 6 == 0) {
                tank.displayResult();
            }
        }

        operations.printLogFile("c:/temp8.txt");

        for (int i = 0; i < 100; i++) {
            operations.runTransient();
            // operations.displayResult();
            if (i % 6 == 0) {
                tank.displayResult();
            }
        }

        operations.printLogFile("c:/temp9.txt");
    }
}
