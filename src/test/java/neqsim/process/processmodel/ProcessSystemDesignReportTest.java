package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/** Tests robust process-level design reporting across equipment initialization states. */
class ProcessSystemDesignReportTest {

  @Test
  void unrunAutoSizeableEquipmentDoesNotAbortCompleteDesignReport() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    Stream feed = new Stream("feed", fluid);
    Compressor compressor = new Compressor("unrun compressor", feed);
    compressor.setOutletPressure(30.0, "bara");
    ProcessSystem process = new ProcessSystem("unrun process");
    process.add(feed);
    process.add(compressor);

    String report = assertDoesNotThrow(process::getDesignReportJson);

    assertTrue(report.contains("unrun compressor"));
    assertTrue(report.contains("\"sizingDataAvailable\": false"));
  }
}
