package neqsim.process.equipment.tank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link Tank}, including the fixed-total-volume dynamic (pressure-building) mode used to represent a bounded
 * vessel such as an underground storage cavern: liquid (with dissolved gas) flows continuously into a vessel of fixed
 * total volume, and the vessel pressure is a RESULT of the material/energy balance (via a fixed-volume flash), not a
 * boundary condition.
 *
 * @author NeqSim
 */
class TankTest {
  private Stream createFeedStream(double flowKgPerHr) {
    SystemInterface fluid = new SystemSrkEos(288.15, 1.3);
    fluid.addComponent("methane", 0.02);
    fluid.addComponent("ethane", 0.03);
    fluid.addComponent("n-heptane", 0.95);
    fluid.setMixingRule(2);

    Stream feed = new Stream("cavern feed", fluid);
    feed.setFlowRate(flowKgPerHr, "kg/hr");
    feed.setTemperature(15.0, "C");
    feed.setPressure(1.3, "bara");
    feed.run();
    return feed;
  }

  @Test
  void testSetTotalVolumeConfiguresGeometryUsedByRun() {
    Stream feed = createFeedStream(1000.0);
    Tank tank = new Tank("cavern", feed);

    double totalVolume = 200000.0; // m3, representative rock-cavern order of magnitude
    tank.setTotalVolume(totalVolume, 20.0);
    assertEquals(20.0, tank.getSeparatorDiameter(), 1e-9);

    UUID id = UUID.randomUUID();
    tank.run(id);

    assertEquals(totalVolume,
        tank.getLiquidLevel() * Math.PI / 4.0 * tank.getSeparatorDiameter() * tank.getSeparatorDiameter()
            * tank.getSeparatorLength()
            + (1.0 - tank.getLiquidLevel()) * Math.PI / 4.0 * tank.getSeparatorDiameter() * tank.getSeparatorDiameter()
                * tank.getSeparatorLength(),
        totalVolume * 1e-6, "liquidVolume + gasVolume should equal the configured total volume");
  }

  @Test
  void testFixedVolumeDynamicallyBuildsPressureWhenAccumulating() {
    // Represents a bounded vessel (e.g. an underground storage cavern) that receives continuous
    // liquid feed containing light ends, with both outlets shut (a downstream stop, as during a
    // Sture-type SCUP outage): the fixed total volume forces the accumulating fluid onto a
    // rising-pressure path, since pressure here is a RESULT of the fixed-volume flash, not a
    // boundary condition.
    Stream feed = createFeedStream(6000.0);
    Tank cavern = new Tank("cavern", feed);
    cavern.run(UUID.randomUUID());
    cavern.setCalculateSteadyState(false);

    // Shut both outlets: nothing leaves the vessel, so every mole of feed accumulates.
    cavern.getGasOutStream().setFlowRate(1.0e-6, "kg/hr");
    cavern.getGasOutStream().run();
    cavern.getLiquidOutStream().setFlowRate(1.0e-6, "kg/hr");
    cavern.getLiquidOutStream().run();

    double dt = 0.02; // hours
    double startPressure = cavern.getPressure("bara");
    double startMoles = cavern.getThermoSystem().getTotalNumberOfMoles();
    double pressure = startPressure;
    double moles = startMoles;
    for (int i = 0; i < 15; i++) {
      cavern.runTransient(dt, UUID.randomUUID());
      pressure = cavern.getPressure("bara");
      moles = cavern.getThermoSystem().getTotalNumberOfMoles();
    }

    assertTrue(moles > startMoles, "Total moles in the closed vessel should increase as feed keeps arriving");
    assertTrue(pressure > startPressure,
        "Vessel pressure should rise as more fluid is packed into the fixed total volume " + "(start=" + startPressure
            + " bara, end=" + pressure + " bara)");
  }
}
