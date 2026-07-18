package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Verifies that HAZOP preparation consumes the governed P&amp;ID model and fails on missing nodes. */
public class PidHazopStudyRunnerTest extends NeqSimTest {
  @Test
  void completePidProducesTraceableHazopNodesAndSafeguards() {
    EngineeringProject project = project();
    PidDesignModel model = PidDesignSynthesizer.synthesize(project,
        new PidDesignBasis("NORSOK-COMPLETE-PID-PROPOSALS", "20"),
        NorsokPidRuleCatalog.completeProposals());

    PidHazopStudyReport report = PidHazopStudyRunner.run(project, model);

    assertTrue(report.isReadyForHazopWorkshop());
    assertFalse(report.getNodes().isEmpty());
    assertTrue(report.toJson().contains("creditedSafeguardTags"));
    assertTrue(report.toJson().contains("IEC 61882"));
    assertTrue(report.toJson().contains("workshopDecision"));
  }

  @Test
  void emptyPidFailsHazopReadiness() {
    EngineeringProject project = project();
    PidHazopStudyReport report =
        PidHazopStudyRunner.run(project, new PidDesignModel(project.getProjectId(), "EMPTY"));
    assertFalse(report.isReadyForHazopWorkshop());
  }

  private static EngineeringProject project() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("n-heptane", 0.05);
    Stream feed = new Stream("20-FEED-001", fluid);
    Separator separator = new Separator("20-VG-001", feed);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    return NorsokOffshoreEngineeringBuilder.from("HAZOP-ready P&ID", process)
        .projectId("PID-HAZOP-TEST").build();
  }
}
