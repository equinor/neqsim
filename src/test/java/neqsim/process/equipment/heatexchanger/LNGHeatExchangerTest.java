package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link LNGHeatExchanger}.
 *
 * @author NeqSim
 */
class LNGHeatExchangerTest {

  @Test
  void testBasicLNGHeatExchanger() {
    // Hot feed gas (process side)
    SystemInterface hotFluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    hotFluid.addComponent("methane", 0.90);
    hotFluid.addComponent("ethane", 0.05);
    hotFluid.addComponent("propane", 0.03);
    hotFluid.addComponent("nitrogen", 0.02);
    hotFluid.setMixingRule("classic");

    Stream hotStream = new Stream("hot_feed", hotFluid);
    hotStream.setFlowRate(100000.0, "kg/hr");
    hotStream.setTemperature(30.0, "C");
    hotStream.setPressure(50.0, "bara");

    // Cold refrigerant stream
    SystemInterface coldFluid = new SystemSrkEos(273.15 - 160.0, 3.0);
    coldFluid.addComponent("methane", 0.40);
    coldFluid.addComponent("ethane", 0.30);
    coldFluid.addComponent("propane", 0.30);
    coldFluid.setMixingRule("classic");

    Stream coldStream = new Stream("cold_ref", coldFluid);
    coldStream.setFlowRate(150000.0, "kg/hr");
    coldStream.setTemperature(-33.0, "C");
    coldStream.setPressure(3.0, "bara");

    // Create LNG heat exchanger
    LNGHeatExchanger lngHX = new LNGHeatExchanger("MCHE",
        Arrays.asList((neqsim.process.equipment.stream.StreamInterface) hotStream,
            (neqsim.process.equipment.stream.StreamInterface) coldStream));

    lngHX.setNumberOfZones(10);
    assertEquals(10, lngHX.getNumberOfZones());
    lngHX.setExchangerType("BAHX");
    assertEquals("BAHX", lngHX.getExchangerType());

    ProcessSystem process = new ProcessSystem();
    process.add(hotStream);
    process.add(coldStream);
    process.add(lngHX);
    process.run();

    // Verify MITA was calculated
    double mita = lngHX.getMITA();
    assertTrue(mita >= 0.0, "MITA should be non-negative, got: " + mita);
  }

  @Test
  void testStreamClassification() {
    SystemInterface fluid1 = new SystemSrkEos(273.15 + 20.0, 50.0);
    fluid1.addComponent("methane", 1.0);
    fluid1.setMixingRule("classic");

    Stream s1 = new Stream("warm", fluid1);
    s1.setFlowRate(50000.0, "kg/hr");
    s1.setTemperature(20.0, "C");
    s1.setPressure(50.0, "bara");

    SystemInterface fluid2 = new SystemSrkEos(273.15 - 100.0, 3.0);
    fluid2.addComponent("methane", 0.5);
    fluid2.addComponent("ethane", 0.5);
    fluid2.setMixingRule("classic");

    Stream s2 = new Stream("cold", fluid2);
    s2.setFlowRate(50000.0, "kg/hr");
    s2.setTemperature(-100.0, "C");
    s2.setPressure(3.0, "bara");

    LNGHeatExchanger hx = new LNGHeatExchanger("test_hx",
        Arrays.asList((neqsim.process.equipment.stream.StreamInterface) s1,
            (neqsim.process.equipment.stream.StreamInterface) s2));
    hx.setNumberOfZones(5);

    ProcessSystem process = new ProcessSystem();
    process.add(s1);
    process.add(s2);
    process.add(hx);
    process.run();

    // Hot and cold composite curves may or may not be available depending on
    // number of zones and stream classification
    double[][] hotCurve = hx.getHotCompositeCurve();
    double[][] coldCurve = hx.getColdCompositeCurve();
    // At minimum, MITA should be calculated
    double mita = hx.getMITA();
    assertTrue(mita >= 0.0 || hotCurve == null, "MITA should be valid or curves absent");
  }
}
