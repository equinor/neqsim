package neqsim.process.dynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.processmodules.SeparationTrainModule;
import neqsim.thermo.system.SystemSrkEos;

/** Regression coverage for composite process-module dynamic capability semantics. */
public class DynamicCapabilityModuleContainerTest extends neqsim.NeqSimTest {
  /**
   * A process module delegates transient execution to its child ProcessSystem and must not be treated as an independent
   * unaudited dynamic state owner. Child equipment remains recursively audited.
   */
  @Test
  public void moduleContainerIsAlgebraicWhileChildrenRemainAudited() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("module feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setPressure(50.0, "bara");
    feed.setTemperature(25.0, "C");

    SeparationTrainModule module = new SeparationTrainModule("separation train");
    module.addInputStream("feed stream", feed);

    ProcessSystem process = new ProcessSystem("module process");
    process.add(module);

    assertEquals(DynamicCapability.ALGEBRAIC, module.getDynamicCapability());

    DynamicCapabilityReport report = DynamicCapabilityReport.from(process);
    assertTrue(report.getEntries().size() > 10);
    assertTrue(report.getReviewItems().isEmpty());
    assertTrue(report.isFullyAudited());
    assertTrue(report.isStrictPreflightReady());
  }
}
