package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

class HeatExchangerMassBalanceTest {
  @Test
  void maintainsMassBalanceDuringRun() {
    Stream hot = createStream("hot", 1200.0, 90.0);
    Stream cold = createStream("cold", 800.0, 20.0);

    HeatExchanger hx = new HeatExchanger("hx", hot, cold);
    hx.setUAvalue(750.0);
    hx.setThermalEffectiveness(0.7);

    ProcessSystem system = new ProcessSystem();
    system.add(hot);
    system.add(cold);
    system.add(hx);
    system.run();

    assertEquals(hot.getFlowRate("kg/hr"), hx.getOutStream(0).getFlowRate("kg/hr"), 1e-10);
    assertEquals(cold.getFlowRate("kg/hr"), hx.getOutStream(1).getFlowRate("kg/hr"), 1e-10);
  }

  @Test
  void maintainsMassBalanceWithDeltaTSpecification() {
    Stream hot = createStream("hot", 950.0, 110.0);
    Stream cold = createStream("cold", 500.0, 30.0);

    HeatExchanger hx = new HeatExchanger("hxDeltaT", hot, cold);
    hx.setDeltaT(8.0);

    ProcessSystem system = new ProcessSystem();
    system.add(hot);
    system.add(cold);
    system.add(hx);
    system.run();

    assertEquals(hot.getFlowRate("kg/hr"), hx.getOutStream(0).getFlowRate("kg/hr"), 1e-10);
    assertEquals(cold.getFlowRate("kg/hr"), hx.getOutStream(1).getFlowRate("kg/hr"), 1e-10);
  }

  private Stream createStream(String name, double flowRate, double temperatureC) {
    SystemInterface system = new SystemSrkEos(273.15 + temperatureC, 30.0);
    system.addComponent("methane", 80.0);
    system.addComponent("ethane", 20.0);
    system.createDatabase(true);
    system.setMixingRule(2);

    Stream stream = new Stream(name, system);
    stream.setTemperature(temperatureC, "C");
    stream.setPressure(40.0, "bara");
    stream.setFlowRate(flowRate, "kg/hr");
    return stream;
  }
}
