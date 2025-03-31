package neqsim.process.util.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class SeparatorResponseTest {
  @Test
  void testWrite() {
    SystemSrkEos testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("n-heptane", 100.0);
    testSystem.addComponent("water", 100.0);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);

    ProcessSystem processOps = new ProcessSystem();

    Stream inletStream = new Stream("feed stream", testSystem);
    inletStream.setPressure(10.0, "bara");
    inletStream.setTemperature(20.0, "C");
    inletStream.setFlowRate(290.0, "kg/hr");

    Separator separator = new Separator("two phase separator", inletStream);
    separator.setInternalDiameter(0.05);

    ThreePhaseSeparator separator3phase =
        new ThreePhaseSeparator("three phase separator", inletStream);
    separator3phase.setInternalDiameter(0.05);

    HeatExchanger hx1 = new HeatExchanger("E-100", separator3phase.getGasOutStream());
    hx1.setGuessOutTemperature(273.15 + 35.0);
    hx1.setUAvalue(444000.2);
    hx1.setFeedStream(1, separator3phase.getOilOutStream());

    Pump pump1 = new Pump("pump", separator3phase.getOilOutStream());
    pump1.setTagName("20PA-001");
    pump1.setOutletPressure(100.0);

    processOps.add(inletStream);
    processOps.add(separator);
    processOps.add(separator3phase);
    // processOps.add(hx1);
    processOps.add(pump1);
    processOps.run();

    String sepjson = separator.toJson();
    String sep3json = separator3phase.toJson();
    String hxjson = hx1.toJson();
    String pumpjson = pump1.toJson();
    JsonObject jsonObject = JsonParser.parseString(sep3json).getAsJsonObject();
    Double reldens = jsonObject.getAsJsonObject("feed").getAsJsonObject("properties")
        .getAsJsonObject("oil").getAsJsonObject("relative density").get("value").getAsDouble();
    assertEquals(0.688292615281, reldens, 0.01);

  }
}
