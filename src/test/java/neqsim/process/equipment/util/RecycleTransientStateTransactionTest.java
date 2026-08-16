package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.dynamics.TransientTransactionCoverage;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.DesignConditions;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Quantitative transaction and restart evidence for recycle-equipment state. */
class RecycleTransientStateTransactionTest extends neqsim.NeqSimTest {
  private static final double TOLERANCE = 1.0e-12;

  @Test
  void processTransactionRestoresRecycleStreamsSolverAndBaseStateInPlace() {
    Fixture fixture = fixture();
    Recycle recycle = fixture.recycle;
    recycle.applyAutoMinimumFlow(2.0);
    recycle.setPriority(25);
    recycle.setAdaptiveAcceleration(true);
    recycle.setAbsoluteFlowTolerance(3.0);
    recycle.setTime(12.0);
    recycle.setCalculationIdentifier(UUID.randomUUID());
    recycle.properties.put("mode", "captured");
    recycle.getReferenceDesignation().setProductDesignation("RC-101");
    BroydenAccelerator accelerator = recycle.getBroydenAccelerator();
    accelerator.setDelayIterations(0);
    accelerateThreeTimes(accelerator);

    StreamInterface capturedInput = recycle.getStream(0);
    StreamInterface capturedMixed = recycle.getOutStream();
    StreamInterface capturedLastIteration = recycle.lastIterationStream;
    StreamInterface capturedOutlet = recycle.getOutletStream();
    UUID capturedCalculationIdentifier = recycle.getCalculationIdentifier();
    int capturedBroydenIterations = accelerator.getIterationCount();
    double capturedInputFlow = capturedInput.getFlowRate("kg/hr");
    double capturedMixedTemperature = capturedMixed.getTemperature("K");
    double capturedLastPressure = capturedLastIteration.getPressure("bara");
    double capturedOutletFlow = capturedOutlet.getFlowRate("kg/hr");

    TransientTransactionCoverage coverage = fixture.process.getTransientTransactionCoverage();
    assertTrue(coverage.isComplete());
    assertEquals(1, coverage.getProcessElementCount());
    assertEquals(1, coverage.getParticipantCount());

    TransientStepTransaction transaction = fixture.process.beginTransientStepTransaction();
    Stream replacement = new Stream("replacement", fixture.fluid.clone());
    replacement.setFlowRate(900.0, "kg/hr");
    recycle.replaceStream(0, replacement);
    recycle.setOutletStream(replacement);
    recycle.setPriority(999);
    recycle.setAdaptiveAcceleration(false);
    recycle.setAbsoluteFlowTolerance(33.0);
    recycle.setMinimumFlow(9.0);
    recycle.setTime(99.0);
    recycle.setCalculationIdentifier(UUID.randomUUID());
    recycle.properties.put("mode", "trial");
    recycle.getReferenceDesignation().setProductDesignation("TRIAL");
    capturedInput.setFlowRate(777.0, "kg/hr");
    capturedMixed.setTemperature(350.0, "K");
    capturedLastIteration.setPressure(5.0, "bara");
    capturedOutlet.setFlowRate(888.0, "kg/hr");
    accelerator.setRelaxationFactor(0.25);
    accelerator.accelerate(new double[] { 1.4, 2.3 }, new double[] { 1.6, 2.5 });

    transaction.rollback();

    assertEquals(TransientStepTransaction.Status.ROLLED_BACK, transaction.getStatus());
    assertSame(capturedInput, recycle.getStream(0));
    assertSame(capturedMixed, recycle.getOutStream());
    assertSame(capturedLastIteration, recycle.lastIterationStream);
    assertSame(capturedOutlet, recycle.getOutletStream());
    assertEquals(capturedInputFlow, capturedInput.getFlowRate("kg/hr"), TOLERANCE);
    assertEquals(capturedMixedTemperature, capturedMixed.getTemperature("K"), TOLERANCE);
    assertEquals(capturedLastPressure, capturedLastIteration.getPressure("bara"), TOLERANCE);
    assertEquals(capturedOutletFlow, capturedOutlet.getFlowRate("kg/hr"), TOLERANCE);
    assertEquals(25, recycle.getPriority());
    assertTrue(recycle.isAdaptiveAcceleration());
    assertEquals(3.0, recycle.getAbsoluteFlowTolerance(), TOLERANCE);
    assertEquals(2.0, recycle.getMinimumFlow(), TOLERANCE);
    assertTrue(recycle.isMinimumFlowAutoManaged());
    assertFalse(recycle.isMinimumFlowExplicitlyConfigured());
    assertEquals(12.0, recycle.getTime(), TOLERANCE);
    assertEquals(capturedCalculationIdentifier, recycle.getCalculationIdentifier());
    assertEquals("captured", recycle.properties.get("mode"));
    assertEquals("RC-101", recycle.getReferenceDesignation().getProductDesignation());
    assertSame(accelerator, recycle.getBroydenAccelerator());
    assertEquals(capturedBroydenIterations, accelerator.getIterationCount());
    assertEquals(1.0, accelerator.getRelaxationFactor(), TOLERANCE);
  }

  @Test
  void directSnapshotRejectsForeignAndNullState() {
    Recycle first = fixture().recycle;
    Recycle second = fixture().recycle;
    Recycle.TransientState snapshot = first.captureTransientState();

    assertThrows(IllegalArgumentException.class, () -> second.restoreTransientState(snapshot));
    assertThrows(NullPointerException.class, () -> first.restoreTransientState(null));
  }

  @Test
  void javaSerializationPreservesIdentityAndBroydenContinuation() throws Exception {
    Recycle original = fixture().recycle;
    original.getBroydenAccelerator().setDelayIterations(0);
    accelerateThreeTimes(original.getBroydenAccelerator());
    Recycle restarted = roundTrip(original);

    assertEquals(original.getTransientStateIdentity(), restarted.getTransientStateIdentity());
    assertEquals(original.getBroydenAccelerator().getIterationCount(),
        restarted.getBroydenAccelerator().getIterationCount());
    assertEquals(original.getBroydenAccelerator().getResidualNorm(),
        restarted.getBroydenAccelerator().getResidualNorm(), TOLERANCE);

    Recycle.TransientState checkpoint = restarted.captureTransientState();
    double capturedFlow = restarted.getStream(0).getFlowRate("kg/hr");
    restarted.getStream(0).setFlowRate(capturedFlow + 50.0, "kg/hr");
    restarted.getBroydenAccelerator().accelerate(new double[] { 1.4, 2.3 }, new double[] { 1.6, 2.5 });
    restarted.restoreTransientState(checkpoint);

    assertEquals(capturedFlow, restarted.getStream(0).getFlowRate("kg/hr"), TOLERANCE);
    assertEquals(original.getBroydenAccelerator().getIterationCount(),
        restarted.getBroydenAccelerator().getIterationCount());
  }

  @Test
  void unsupportedBaseAttachmentsRemainFailClosed() {
    Recycle recycle = fixture().recycle;
    recycle.setDesignConditions(new DesignConditions());

    String issue = recycle.getTransientStateCoverageIssue();
    assertNotNull(issue);
    assertTrue(issue.contains("design-condition"));
    assertThrows(IllegalStateException.class, recycle::captureTransientState);
  }

  private static Fixture fixture() {
    SystemInterface fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("recycle inlet", fluid.clone());
    inlet.setFlowRate(100.0, "kg/hr");
    inlet.run();
    Stream outlet = new Stream("recycle outlet", fluid.clone());
    outlet.setFlowRate(100.0, "kg/hr");
    outlet.run();

    Recycle recycle = new Recycle("recycle");
    recycle.addStream(inlet);
    recycle.setOutletStream(outlet);
    ProcessSystem process = new ProcessSystem("recycle transaction");
    process.add(recycle);
    return new Fixture(process, recycle, fluid);
  }

  private static void accelerateThreeTimes(BroydenAccelerator accelerator) {
    accelerator.accelerate(new double[] { 1.0, 2.0 }, new double[] { 1.2, 2.2 });
    accelerator.accelerate(new double[] { 1.2, 2.2 }, new double[] { 1.3, 2.25 });
    accelerator.accelerate(new double[] { 1.3, 2.25 }, new double[] { 1.35, 2.28 });
  }

  @SuppressWarnings("unchecked")
  private static <T> T roundTrip(T value) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(value);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (T) input.readObject();
    }
  }

  private static final class Fixture {
    private final ProcessSystem process;
    private final Recycle recycle;
    private final SystemInterface fluid;

    private Fixture(ProcessSystem process, Recycle recycle, SystemInterface fluid) {
      this.process = process;
      this.recycle = recycle;
      this.fluid = fluid;
    }
  }
}
