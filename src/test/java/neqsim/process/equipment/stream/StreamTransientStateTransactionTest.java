package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import neqsim.process.equipment.util.Recycle;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Quantitative transaction and restart evidence for local stream-source equipment. */
class StreamTransientStateTransactionTest extends neqsim.NeqSimTest {
  private static final double TOLERANCE = 1.0e-12;

  @Test
  void streamRollbackRestoresThermodynamicsConfigurationAndBaseState() {
    Stream stream = stream("feed");
    stream.setGasQuality(0.3);
    stream.setPropertyInitLevel(Stream.PropertyInitLevel.DENSITY_ONLY);
    stream.setSpecification("TP");
    stream.run();
    stream.setTime(4.0);
    UUID capturedIdentifier = stream.getCalculationIdentifier();
    ProcessSystem process = new ProcessSystem("stream transaction");
    process.add(stream);

    TransientTransactionCoverage coverage = process.getTransientTransactionCoverage();
    assertTrue(coverage.isComplete());
    assertEquals(1, coverage.getProcessElementCount());
    assertEquals(1, coverage.getParticipantCount());
    assertFalse(stream.needRecalculation());

    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    stream.setTemperature(340.0, "K");
    stream.setPressure(8.0, "bara");
    stream.setFlowRate(250.0, "kg/hr");
    stream.setGasQuality(0.9);
    stream.setPropertyInitLevel(Stream.PropertyInitLevel.FULL);
    stream.setSpecification("PH");
    process.runTransient(0.5, UUID.randomUUID());
    transaction.rollback();

    assertSame(stream, process.getUnitOperations().get(0));
    assertEquals(298.15, stream.getTemperature("K"), TOLERANCE);
    assertEquals(30.0, stream.getPressure("bara"), TOLERANCE);
    assertEquals(100.0, stream.getFlowRate("kg/hr"), 1.0e-9);
    assertEquals(0.3, stream.getGasQuality(), TOLERANCE);
    assertEquals(Stream.PropertyInitLevel.DENSITY_ONLY, stream.getPropertyInitLevel());
    assertEquals("TP", stream.getSpecification());
    assertEquals(4.0, stream.getTime(), TOLERANCE);
    assertEquals(0.0, process.getTime(), TOLERANCE);
    assertEquals(capturedIdentifier, stream.getCalculationIdentifier());
    assertFalse(stream.needRecalculation());
  }

  @Test
  void registeredStreamAndRecycleRollbackTogetherWithCompleteCoverage() {
    Stream feed = stream("registered feed");
    Stream recycleOutlet = stream("recycle outlet");
    Recycle recycle = new Recycle("recycle");
    recycle.addStream(feed);
    recycle.setOutletStream(recycleOutlet);
    ProcessSystem process = new ProcessSystem("stream recycle transaction");
    process.add(feed);
    process.add(recycle);

    TransientTransactionCoverage coverage = process.getTransientTransactionCoverage();
    assertTrue(coverage.isComplete());
    assertEquals(2, coverage.getProcessElementCount());
    assertEquals(2, coverage.getParticipantCount());
    StreamInterface capturedRecycleInput = recycle.getStream(0);
    StreamInterface capturedRecycleOutput = recycle.getOutletStream();
    double capturedFeedFlow = feed.getFlowRate("kg/hr");
    double capturedRecycleFlow = recycleOutlet.getFlowRate("kg/hr");

    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    feed.setFlowRate(180.0, "kg/hr");
    process.runTransient(0.25, UUID.randomUUID());
    transaction.rollback();

    assertSame(feed, process.getUnitOperations().get(0));
    assertSame(capturedRecycleInput, recycle.getStream(0));
    assertSame(capturedRecycleOutput, recycle.getOutletStream());
    assertEquals(capturedFeedFlow, feed.getFlowRate("kg/hr"), 1.0e-9);
    assertEquals(capturedRecycleFlow, recycleOutlet.getFlowRate("kg/hr"), 1.0e-9);
    assertEquals(0.0, process.getTime(), TOLERANCE);
    assertEquals(0.0, feed.getTime(), TOLERANCE);
    assertEquals(0.0, recycle.getTime(), TOLERANCE);
  }

  @Test
  void serializationForeignSnapshotsAndUnsupportedOwnershipFailClosed() throws Exception {
    Stream original = stream("original");
    String identity = original.getTransientStateIdentity();
    Stream restarted = roundTrip(original);
    assertEquals(identity, restarted.getTransientStateIdentity());
    assertNotEquals(identity, original.clone("clone").getTransientStateIdentity());

    Stream.TransientState checkpoint = restarted.captureTransientState();
    restarted.setTemperature(330.0, "K");
    restarted.restoreTransientState(checkpoint);
    assertEquals(298.15, restarted.getTemperature("K"), TOLERANCE);
    assertThrows(IllegalArgumentException.class, () -> original.restoreTransientState(checkpoint));
    assertThrows(NullPointerException.class, () -> original.restoreTransientState(null));

    Stream wrapper = new Stream("wrapper", original);
    assertTrue(wrapper.getTransientStateCoverageIssue().contains("wrapper"));
    assertThrows(IllegalStateException.class, wrapper::captureTransientState);

    DerivedStream derived = new DerivedStream("derived", original.getFluid().clone());
    assertTrue(derived.getTransientStateCoverageIssue().contains("subclass"));
    assertThrows(IllegalStateException.class, derived::captureTransientState);

    VirtualStream virtual = new VirtualStream("virtual", original);
    ProcessSystem virtualProcess = new ProcessSystem("unsupported virtual stream");
    virtualProcess.add(virtual);
    TransientTransactionCoverage virtualCoverage = virtualProcess.getTransientTransactionCoverage();
    assertEquals(1, virtualCoverage.getProcessElementCount());
    assertEquals(0, virtualCoverage.getParticipantCount());
    assertTrue(virtualCoverage.getBlockingIssues().get(0).contains("does not implement"));
    assertThrows(IllegalStateException.class, virtualProcess::beginTransientStepTransaction);
  }

  private static Stream stream(String name) {
    SystemInterface fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.2);
    fluid.setMixingRule("classic");
    Stream stream = new Stream(name, fluid);
    stream.setFlowRate(100.0, "kg/hr");
    return stream;
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

  private static final class DerivedStream extends Stream {
    private static final long serialVersionUID = 1000L;

    private DerivedStream(String name, SystemInterface fluid) {
      super(name, fluid);
    }
  }
}
