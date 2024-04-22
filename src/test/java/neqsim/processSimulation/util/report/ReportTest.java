package neqsim.processSimulation.util.report;

import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processSystem.ProcessSystem;
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

    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.setName("feed stream");
    inletStream.setPressure(10.0, "bara");
    inletStream.setTemperature(20.0, "C");
    inletStream.setFlowRate(100.0, "kg/hr");

    Separator separator = new Separator("two phase separator", inletStream);

    Compressor compressor = new Compressor("gas compressor", separator.getGasOutStream());
    compressor.setOutletPressure(20.0, "bara");

    ThrottlingValve valve = new ThrottlingValve("valve 1", separator.getLiquidOutStream());
    valve.setOutletPressure(1.0, "bara");

    processOps.add(inletStream);
    processOps.add(separator);
    processOps.add(compressor);
    processOps.add(valve);
    processOps.run();

    neqsim.util.unit.Units units = new neqsim.util.unit.Units();

    Report report = new Report(processOps);
    String obj = report.json();
    // System.out.println(obj);
  }
}
