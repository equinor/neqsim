/*
 * TestSlugcatcher.java
 *
 * Created on 30. juli 2007, 18:49
 */

package neqsim.processSimulation.util.example;

import neqsim.processSimulation.measurementDevice.VolumeFlowTransmitter;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

/**
 * <p>
 * TestSlugcatcher class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestSlugcatcher {
  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String args[]) {
    double reservoirTemperatureSnohvit = 273.15 + 10.0; // K
    double reservoirPressureSnohvit = 5.0; // bar

    neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkCPAstatoil(
        reservoirTemperatureSnohvit, reservoirPressureSnohvit);

    testSystem.addComponent("nitrogen", 40);
    testSystem.addComponent("methane", 10);
    testSystem.addComponent("CO2", 0.5);
    testSystem.addComponent("water", 70);
    testSystem.addComponent("MEG", 30);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(7);

    Stream stream_1 = new Stream("Stream1", testSystem);
    ThreePhaseSeparator separator = new ThreePhaseSeparator("Separator 1", stream_1);

    ThrottlingValve valve1 = new ThrottlingValve("snohvit valve", separator.getWaterOutStream());
    valve1.setOutletPressure(1.4);

    ThreePhaseSeparator separator2 =
        new ThreePhaseSeparator("Separator 1", valve1.getOutletStream());
    Stream stream_2 = new Stream("stream_2", separator2.getGasOutStream());

    VolumeFlowTransmitter volumeTransmitter3 =
        new VolumeFlowTransmitter(separator2.getGasOutStream());
    volumeTransmitter3.setMeasuredPhaseNumber(0);
    volumeTransmitter3.setName("Gas Volume FLow From Slug Catcher");

    VolumeFlowTransmitter volumeTransmitter4 =
        new VolumeFlowTransmitter(separator2.getWaterOutStream());
    volumeTransmitter4.setMeasuredPhaseNumber(0);
    volumeTransmitter4.setName("Water Volume FLow From Slug Catcher");

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(separator);
    operations.add(valve1);
    operations.add(separator2);
    operations.add(stream_2);
    operations.add(volumeTransmitter3);
    operations.add(volumeTransmitter4);

    operations.run();
    operations.displayResult();
    operations.reportMeasuredValues();
  }
}
