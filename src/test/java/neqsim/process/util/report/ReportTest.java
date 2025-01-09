package neqsim.process.util.report;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class ReportTest {
  @Test
  void testWrite() {
    SystemSrkEos testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("n-heptane", 100.0);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);

    ProcessSystem processOps = new ProcessSystem();

    Stream inletStream = new Stream("feed stream", testSystem);
    inletStream.setPressure(10.0, "bara");
    inletStream.setTemperature(20.0, "C");
    inletStream.setFlowRate(100.0, "kg/hr");

    Separator separator = new Separator("two phase separator", inletStream);

    Compressor compressor = new Compressor("gas compressor", separator.getGasOutStream());
    compressor.setOutletPressure(20.0, "bara");

    ThrottlingValve valve = new ThrottlingValve("valve 1", separator.getLiquidOutStream());
    valve.setOutletPressure(1.0, "bara");

    Mixer mixer1 = new Mixer("mixer 1");
    mixer1.addStream(valve.getOutletStream());

    processOps.add(inletStream);
    processOps.add(separator);
    processOps.add(compressor);
    processOps.add(valve);
    processOps.add(mixer1);
    processOps.run();

    Report report = new Report(processOps);
    String obj = report.generateJsonReport();
    neqsim.util.unit.Units.activateFieldUnits();
    String obj2 = report.generateJsonReport();
    // System.out.println(obj2);
    neqsim.util.unit.Units.activateSIUnits();
    // reporting from process Object
    String processreportasjson = processOps.getReport_json();
    // System.out.println(processreportasjson);
    // report stream
    String streamreportasjson = inletStream.getReport_json();
  }
}
