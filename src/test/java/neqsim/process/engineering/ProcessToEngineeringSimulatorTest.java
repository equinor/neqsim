package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** End-to-end test of the inlet-separation, compression, and export engineering slice. */
class ProcessToEngineeringSimulatorTest {

  @Test
  void designsACompleteInletCompressionExportSlice() {
    ProcessSystem process = process();
    double originalLineDiameter = ((AdiabaticPipe) process.getUnit("EXPORT-LINE")).getDiameter();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Process-to-engineering test", process)
        .projectId("pte-test").build();
    project.addDesignCase(flowCase("normal", 8000.0, 10));
    project.addDesignCase(flowCase("maximum", 12000.0, 20));
    ProcessToEngineeringDesignBuilder.on(project).exportLineLimits(25.0, 5.0)
        .compressorDrivers(0.10, 500.0, 1000.0, 2000.0, 3000.0, 5000.0, 7500.0, 10000.0)
        .addInletCompressionExportSlice("INLET-SEP", "EXPORT-COMP", "EXPORT-LINE", "", "PIT-100");

    EngineeringSimulationResult result = ProcessToEngineeringSimulator.run(project, 2);

    assertNotNull(result.getEngineeringDesignLoopResult());
    assertTrue(result.getEngineeringDesignLoopResult().isConverged());
    assertTrue(result.getEngineeringDesignLoopResult().getState().contains("INLET-SEP.insideDiameter"));
    assertTrue(result.getEngineeringDesignLoopResult().getState().contains("EXPORT-COMP.driverRatedPower"));
    assertTrue(result.getEngineeringDesignLoopResult().getState().contains("EXPORT-LINE.insideDiameter"));
    assertTrue(result.getEngineeringDesignLoopResult().getState().contains("PIT-100.upperRangeValue"));
    assertEquals(1.01325,
        result.getEngineeringDesignLoopResult().getState().requireValue("INLET-SEP.designPressure")
            - result.getEngineeringDesignLoopResult().getState().requireValue("INLET-SEP.proposedPsvSetPressure"),
        1.0e-9);
    assertTrue(result.toJson().contains("preliminaryMaterialClass"));
    assertEquals(originalLineDiameter, ((AdiabaticPipe) process.getUnit("EXPORT-LINE")).getDiameter(), 1.0e-12);
  }

  private EngineeringDesignCase flowCase(String id, final double flowKgHr, int priority) {
    return new EngineeringDesignCase(id, id, EngineeringDesignCase.Type.CUSTOM,
        new EngineeringDesignCase.Configurator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void configure(ProcessSystem process) {
            ((Stream) process.getUnit("FEED")).setFlowRate(flowKgHr, "kg/hr");
          }
        }).setPriority(priority).addInput(new EngineeringDesignCase.Input("feedFlow", flowKgHr, "kg/hr", "DB-1"));
  }

  private ProcessSystem process() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.92);
    fluid.addComponent("ethane", 0.04);
    fluid.addComponent("n-heptane", 0.04);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(8000.0, "kg/hr");
    Separator separator = new Separator("INLET-SEP", feed);
    Compressor compressor = new Compressor("EXPORT-COMP", separator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");
    compressor.setPolytropicEfficiency(0.78);
    AdiabaticPipe exportLine = new AdiabaticPipe("EXPORT-LINE", compressor.getOutletStream());
    exportLine.setLength(1000.0);
    exportLine.setDiameter(0.2027);
    exportLine.setPipeWallRoughness(4.6e-5);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(exportLine);
    process.run();
    return process;
  }
}
