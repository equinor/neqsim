/*
 * TestTransientFlow.java
 *
 * Created on 8. oktober 2006, 13:13
 */

package neqsim.processsimulation.util.example;

import neqsim.processsimulation.controllerdevice.ControllerDeviceBaseClass;
import neqsim.processsimulation.controllerdevice.ControllerDeviceInterface;
import neqsim.processsimulation.measurementdevice.LevelTransmitter;
import neqsim.processsimulation.measurementdevice.PressureTransmitter;
import neqsim.processsimulation.processequipment.separator.Separator;
import neqsim.processsimulation.processequipment.stream.Stream;
import neqsim.processsimulation.processequipment.valve.ThrottlingValve;

/**
 * <p>
 * TestTransientFlow class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestTransientFlow {
  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String args[]) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem.addComponent("methane", 0.900);
    testSystem.addComponent("ethane", 0.100);
    testSystem.addComponent("n-heptane", 1.00);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.1);
    testSystem2.addComponent("ethane", 0.10001);
    testSystem2.addComponent("n-heptane", 0.001);
    testSystem2.setMixingRule(2);

    Stream purgeStream = new Stream("Purge Stream", testSystem2);
    ThrottlingValve purgeValve = new ThrottlingValve("purgeValve", purgeStream);
    purgeValve.setOutletPressure(7.0);
    purgeValve.setPercentValveOpening(50.0);

    Stream stream_1 = new Stream("Stream1", testSystem);
    ThrottlingValve valve_1 = new ThrottlingValve("valve_1", stream_1);
    valve_1.setOutletPressure(7.0);
    valve_1.setPercentValveOpening(50);

    Separator separator_1 = new Separator("separator_1");
    separator_1.addStream(valve_1.getOutletStream());
    separator_1.addStream(purgeValve.getOutletStream());

    ThrottlingValve valve_2 = new ThrottlingValve("valve_2", separator_1.getLiquidOutStream());
    valve_2.setOutletPressure(5.0);
    valve_2.setPercentValveOpening(50);
    // valve_2.setCv(10.0);

    ThrottlingValve valve_3 = new ThrottlingValve("valve_3", separator_1.getGasOutStream());
    valve_3.setOutletPressure(5.0);
    valve_3.setPercentValveOpening(50);
    // valve_3.setCv(10.0);

    LevelTransmitter separatorLevelTransmitter =
        new LevelTransmitter("separatorLevelTransmitter1", separator_1);
    separatorLevelTransmitter.setMaximumValue(1.0);
    separatorLevelTransmitter.setMinimumValue(0.0);

    ControllerDeviceInterface separatorLevelController = new ControllerDeviceBaseClass();
    separatorLevelController.setReverseActing(false);
    separatorLevelController.setTransmitter(separatorLevelTransmitter);
    separatorLevelController.setControllerSetPoint(0.3);
    separatorLevelController.setControllerParameters(1.0, 300.0, 10.0);

    PressureTransmitter separatorPressureTransmitter =
        new PressureTransmitter(separator_1.getGasOutStream());
    separatorPressureTransmitter.setUnit("bar");
    separatorPressureTransmitter.setMaximumValue(10.0);
    separatorPressureTransmitter.setMinimumValue(1.0);

    ControllerDeviceInterface separatorPressureController = new ControllerDeviceBaseClass();
    separatorPressureController.setTransmitter(separatorPressureTransmitter);
    separatorPressureController.setReverseActing(false);
    separatorPressureController.setControllerSetPoint(7.0);
    separatorPressureController.setControllerParameters(1.0, 300.0, 10.0);

    neqsim.processsimulation.processsystem.ProcessSystem operations =
        new neqsim.processsimulation.processsystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(valve_1);

    operations.add(purgeStream);
    operations.add(purgeValve);
    operations.add(separator_1);
    operations.add(valve_2);
    operations.add(valve_3);

    // add transmitters and controllers
    operations.add(separatorLevelTransmitter);
    valve_2.setController(separatorLevelController);

    operations.add(separatorPressureTransmitter);
    valve_3.setController(separatorPressureController);

    operations.run();
    operations.displayResult();
    operations.setTimeStep(0.001);
    operations.runTransient();
    operations.runTransient();

    /*
     * // transient behaviour operations.setTimeStep(1.1); for(int i=0;i<50;i++){
     * operations.runTransient(); System.out.println("liquid level " + separator_1.getLiquidLevel()+
     * " PRESSURE " + separator_1.getGasOutStream().getPressure()); }
     *
     * operations.setTimeStep(30.0); for(int i=0;i<2000;i++){ operations.runTransient();
     * System.out.println("liquid level " + separator_1.getLiquidLevel()+ " PRESSURE " +
     * separator_1.getGasOutStream().getPressure()); } operations.displayResult();
     *
     * operations.displayResult();
     */
  }
}
