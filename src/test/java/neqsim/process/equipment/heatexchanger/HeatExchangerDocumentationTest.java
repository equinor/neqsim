package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Executable regression coverage for {@code docs/process/equipment/heat_exchangers.md}. */
public class HeatExchangerDocumentationTest extends neqsim.NeqSimTest {

  /** Verifies the complete two-stream quick start and its result semantics. */
  @Test
  public void testTwoStreamQuickStart() {
    SystemInterface gas = new SystemSrkEos(303.15, 30.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");

    Stream hot = new Stream("hot feed", gas);
    hot.setTemperature(100.0, "C");
    hot.setFlowRate(10000.0, "kg/hr");
    hot.run();

    Stream cold = new Stream("cold feed", gas.clone());
    cold.setTemperature(20.0, "C");
    cold.setFlowRate(8000.0, "kg/hr");
    cold.run();

    HeatExchanger exchanger = new HeatExchanger("E-100", hot, cold);
    exchanger.setUAvalue(5000.0);
    exchanger.setGuessOutTemperature(70.0, "C");
    exchanger.run();

    double hotOutletC = exchanger.getOutStream(0).getTemperature("C");
    double coldOutletC = exchanger.getOutStream(1).getTemperature("C");
    double dutyKW = Math.abs(exchanger.getDuty()) / 1000.0;
    double effectiveness = exchanger.getThermalEffectiveness();
    double minimumApproachK = exchanger.getApproachTemperature();

    assertTrue(hotOutletC < 100.0);
    assertTrue(coldOutletC > 20.0);
    assertTrue(dutyKW > 0.0);
    assertTrue(Double.isFinite(effectiveness));
    assertTrue(effectiveness > 0.0 && effectiveness <= 1.0);
    assertTrue(Double.isFinite(minimumApproachK));
    assertTrue(minimumApproachK > 0.0);
  }

  /** Verifies unit-bearing heater/cooler temperature specifications and duty reporting. */
  @Test
  public void testHeaterAndCoolerTemperatureSpecifications() {
    SystemInterface gas = new SystemSrkEos(298.15, 20.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");

    Stream feed = new Stream("heater feed", gas);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    Heater heater = new Heater("E-200", feed);
    heater.setOutletTemperature(80.0, "C");
    heater.run();
    assertEquals(80.0, heater.getOutletStream().getTemperature("C"), 1.0e-6);
    assertTrue(heater.getDuty("kW") > 0.0);

    Cooler cooler = new Cooler("E-201", heater.getOutletStream());
    cooler.setOutletTemperature(30.0, "C");
    cooler.run();
    assertEquals(30.0, cooler.getOutletStream().getTemperature("C"), 1.0e-6);
    assertTrue(cooler.getDuty("kW") < 0.0);

    heater.autoSize(1.2);
    assertTrue(heater.isAutoSized());
    assertFalse(heater.getSizingReportJson().isEmpty());
  }

  /** Verifies the fixed-outlet-temperature mode and side ownership. */
  @Test
  public void testFixedOutletTemperatureMode() {
    SystemInterface gas = new SystemSrkEos(303.15, 30.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");

    Stream hot = new Stream("specified hot", gas);
    hot.setTemperature(100.0, "C");
    hot.setFlowRate(1000.0, "kg/hr");
    hot.run();

    Stream cold = new Stream("balanced cold", gas.clone());
    cold.setTemperature(20.0, "C");
    cold.setFlowRate(1000.0, "kg/hr");
    cold.run();

    HeatExchanger exchanger = new HeatExchanger("E-300", hot, cold);
    exchanger.setOutStreamSpecificationNumber(0);
    exchanger.setOutTemperature(55.0, "C");
    exchanger.run();

    assertEquals(55.0, exchanger.getOutStream(0).getTemperature("C"), 1.0e-3);
    assertTrue(exchanger.getOutStream(1).getTemperature("C") > 20.0);
    assertEquals(hot.getPressure("bara"), exchanger.getOutStream(0).getPressure("bara"), 1.0e-8);
  }
}
