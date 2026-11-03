package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Verifies that HAZOP preparation consumes the governed P&amp;ID model and fails on missing nodes. */
public class PidHazopStudyRunnerTest extends NeqSimTest {
  @Test
  void completePidProducesTraceableHazopNodesAndSafeguards() {
    EngineeringProject project = project();
    PidDesignModel model = PidDesignSynthesizer.synthesize(project,
        new PidDesignBasis("NORSOK-COMPLETE-PID-PROPOSALS", "20"), NorsokPidRuleCatalog.completeProposals());

    PidHazopStudyReport report = PidHazopStudyRunner.run(project, model);

    assertTrue(report.isReadyForHazopWorkshop());
    assertFalse(report.getNodes().isEmpty());
    assertTrue(report.toJson().contains("creditedSafeguardTags"));
    assertTrue(report.toJson().contains("IEC 61882"));
    assertTrue(report.toJson().contains("workshopDecision"));
    assertTrue(report.toJson().contains("Facilitator confirmation required"));
  }

  @Test
  void emptyPidFailsHazopReadiness() {
    EngineeringProject project = project();
    PidHazopStudyReport report = PidHazopStudyRunner.run(project, new PidDesignModel(project.getProjectId(), "EMPTY"));
    assertFalse(report.isReadyForHazopWorkshop());
  }

  @Test
  void completePidIncludesPassiveProcessTopologyAsHazopNodes() {
    EngineeringProject project = projectWithPassiveValve();
    PidDesignModel model = PidDesignSynthesizer.synthesize(project,
        new PidDesignBasis("NORSOK-COMPLETE-PID-PROPOSALS", "20"), NorsokPidRuleCatalog.completeProposals());

    PidHazopStudyReport report = PidHazopStudyRunner.run(project, model);

    assertTrue(report.isReadyForHazopWorkshop());
    assertTrue(model.getElementsForEquipment("20-PCV-001").isEmpty());
    assertTrue(report.toJson().contains("20-PCV-001"));
    assertTrue(report.toJson().contains("20-FEED-002"));
    assertTrue(report.toJson().contains("processInletStreamTags"));
  }

  /** Verifies that numerical helpers neither require safeguards nor become physical HAZOP nodes. */
  @Test
  void numericalHelpersDoNotBlockPhysicalEquipmentHazopReadiness() {
    EngineeringProject project = project();
    ProcessSystem process = project.getProcessSystem();
    process.add(new Recycle("LP oil recycle"));
    process.add(new Calculator("Material balance calculator"));
    process.add(new Adjuster("Pressure target adjuster"));
    PidDesignModel model = PidDesignSynthesizer.synthesize(project,
        new PidDesignBasis("NORSOK-COMPLETE-PID-PROPOSALS", "20"), NorsokPidRuleCatalog.completeProposals());

    PidHazopStudyReport report = PidHazopStudyRunner.run(project, model);

    assertTrue(report.isReadyForHazopWorkshop(), report.toJson());
    assertEquals(1, report.getNodes().size());
    assertEquals("20-VG-001", report.getNodes().get(0).get("equipmentTag"));
    assertFalse(PidHazopStudyRunner.run(project, new PidDesignModel(project.getProjectId(), "EMPTY"))
        .isReadyForHazopWorkshop());
  }

  /** Verifies that a solver-only model cannot qualify as a workshop-ready physical process. */
  @Test
  void numericalHelpersAloneDoNotEstablishHazopReadiness() {
    ProcessSystem process = new ProcessSystem();
    process.add(new Recycle("Recycle solver"));
    process.add(new Calculator("Calculator"));
    process.add(new Adjuster("Adjuster"));
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Numerical helpers", process)
        .projectId("PID-HELPERS-TEST").build();
    PidDesignModel model = PidDesignSynthesizer.synthesize(project,
        new PidDesignBasis("NORSOK-COMPLETE-PID-PROPOSALS", "20"), NorsokPidRuleCatalog.completeProposals());

    PidHazopStudyReport report = PidHazopStudyRunner.run(project, model);

    assertFalse(report.isReadyForHazopWorkshop());
    assertTrue(report.getNodes().isEmpty());
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
    return NorsokOffshoreEngineeringBuilder.from("HAZOP-ready P&ID", process).projectId("PID-HAZOP-TEST").build();
  }

  private static EngineeringProject projectWithPassiveValve() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 1.0);
    Stream feed = new Stream("20-FEED-002", fluid);
    ThrottlingValve valve = new ThrottlingValve("20-PCV-001", feed);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(valve);
    return NorsokOffshoreEngineeringBuilder.from("Topology-complete P&ID", process).projectId("PID-TOPOLOGY-TEST")
        .build();
  }
}
