package neqsim.process.util.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;

public class MultiStreamHeatExchanger2ResponseTest {
  @Test
  void testJsonOutput() {
    neqsim.thermo.system.SystemInterface system = new neqsim.thermo.Fluid().create("dry gas");
    system.setPressure(10.0, "bara");
    system.setTemperature(273.15 + 60.0, "K");
    system.setMixingRule(2);

    Stream hot = new Stream("hot", system.clone());
    hot.setTemperature(100.0, "C");
    hot.setFlowRate(5000.0, "kg/hr");

    Stream cold = new Stream("cold", system.clone());
    cold.setTemperature(20.0, "C");
    cold.setFlowRate(5000.0, "kg/hr");

    MultiStreamHeatExchanger2 hx = new MultiStreamHeatExchanger2("HX");
    hx.addInStreamMSHE(hot, "hot", null);
    hx.addInStreamMSHE(cold, "cold", null);
    hx.setTemperatureApproach(5.0);

    ProcessSystem process = new ProcessSystem();
    process.add(hot);
    process.add(cold);
    process.add(hx);
    process.run();

    String json = hx.toJson();
    JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
    double approach = jsonObject.get("temperatureApproach").getAsDouble();
    assertEquals(5.0, approach, 1e-2);
  }
}
