package neqsim.process.dynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.diffpressure.Orifice;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.processmodel.ProcessSystem;

/** Regression coverage for audited quasi-steady pressure-flow relations. */
public class PressureFlowDynamicCapabilityTest extends neqsim.NeqSimTest {
  /** Orifice and well IPR relations own no integrated physical inventory of their own. */
  @Test
  public void auditedPressureFlowRelationsAreAlgebraicTransientParticipants() {
    Orifice orifice = new Orifice("orifice");
    WellFlow wellFlow = new WellFlow("well flow");

    assertEquals(DynamicCapability.ALGEBRAIC, orifice.getDynamicCapability());
    assertEquals(DynamicCapability.ALGEBRAIC, wellFlow.getDynamicCapability());

    ProcessSystem process = new ProcessSystem("pressure-flow relations");
    process.add(orifice);
    process.add(wellFlow);

    DynamicCapabilityReport report = DynamicCapabilityReport.from(process);
    assertTrue(report.isStrictPreflightReady());
    assertTrue(report.getReviewItems().isEmpty());
    assertEquals(2, report.getCapabilityCounts().get(DynamicCapability.ALGEBRAIC).intValue());
  }
}