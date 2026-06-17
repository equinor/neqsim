package neqsim.process.equipment.tank;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link LNGTank}.
 *
 * @author NeqSim
 */
class LNGTankTest {

  @Test
  void testBasicBOGCalculation() {
    // Create LNG fluid at storage conditions
    SystemInterface lngFluid = new SystemSrkEos(273.15 - 162.0, 1.1);
    lngFluid.addComponent("methane", 0.92);
    lngFluid.addComponent("ethane", 0.05);
    lngFluid.addComponent("propane", 0.02);
    lngFluid.addComponent("nitrogen", 0.01);
    lngFluid.setMixingRule("classic");

    Stream lngFeed = new Stream("LNG_feed", lngFluid);
    lngFeed.setFlowRate(100000.0, "kg/hr");
    lngFeed.setTemperature(-162.0, "C");
    lngFeed.setPressure(1.1, "bara");

    LNGTank tank = new LNGTank("LNG_tank_1", lngFeed);
    tank.setInsulationType(LNGTank.InsulationType.MEMBRANE);
    tank.setAmbientTemperature(25.0, "C");
    tank.setTankSurfaceArea(12000.0); // m2
    tank.setLNGInventory(60000000.0); // 60 ktonnes
    tank.setStoragePressure(1.1);

    ProcessSystem process = new ProcessSystem();
    process.add(lngFeed);
    process.add(tank);
    process.run();

    // Heat ingress should be positive
    double qi = tank.getHeatIngress();
    assertTrue(qi > 0, "Heat ingress should be positive, got: " + qi);

    // BOG rate should be in typical range 0.03 - 0.20 %/day
    double bogRate = tank.getBoilOffRatePctPerDay();
    assertTrue(bogRate > 0.0, "BOG rate should be positive, got: " + bogRate);
    assertTrue(bogRate < 1.0, "BOG rate should be < 1%/day, got: " + bogRate);

    // BOG mass flow should be positive
    double bogFlow = tank.getBOGMassFlowRate();
    assertTrue(bogFlow > 0, "BOG mass flow should be positive, got: " + bogFlow);

    // BOG stream should exist
    StreamInterface bogStream = tank.getBOGStream();
    assertNotNull(bogStream, "BOG stream should not be null");
  }

  @Test
  void testInsulationTypes() {
    LNGTank tank = new LNGTank("test_tank");

    tank.setInsulationType(LNGTank.InsulationType.MEMBRANE);
    assertEquals(LNGTank.InsulationType.MEMBRANE, tank.getInsulationType());
    assertEquals(0.04, tank.getOverallHeatTransferCoefficient(), 0.001);

    tank.setInsulationType(LNGTank.InsulationType.MOSS);
    assertEquals(LNGTank.InsulationType.MOSS, tank.getInsulationType());
    assertEquals(0.05, tank.getOverallHeatTransferCoefficient(), 0.001);

    tank.setInsulationType(LNGTank.InsulationType.PRISMATIC);
    assertEquals(LNGTank.InsulationType.PRISMATIC, tank.getInsulationType());
    assertEquals(0.045, tank.getOverallHeatTransferCoefficient(), 0.001);
  }

  @Test
  void testAmbientTemperature() {
    LNGTank tank = new LNGTank("test_tank2");
    tank.setAmbientTemperature(35.0, "C");
    assertEquals(273.15 + 35.0, tank.getAmbientTemperature(), 0.01);

    tank.setAmbientTemperature(310.0, "K");
    assertEquals(310.0, tank.getAmbientTemperature(), 0.01);
  }
}
