package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessModelOperatingAction.ActionParameterBinding;
import neqsim.process.util.optimizer.ProcessModelOperatingAction.ActionState;
import neqsim.process.util.optimizer.ProcessModelOperatingAction.ApplicationResult;
import neqsim.process.util.optimizer.ProcessModelOperatingAction.CapabilityAssessment;
import neqsim.process.util.optimizer.ProcessModelOperatingAction.ValueSemantics;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests for {@link ProcessModelOperatingAction}. */
class ProcessModelOperatingActionTest {
  /** Fixture containing a named feed and multi-area model. */
  private static final class ModelFixture {
    /** Writable feed stream. */
    private final Stream feed;

    /** Process model. */
    private final ProcessModel model;

    /** Creates a fixture. */
    private ModelFixture(Stream feed, ProcessModel model) {
      this.feed = feed;
      this.model = model;
    }
  }

  /** Creates a deterministic small process model. */
  private ModelFixture createModelFixture() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(1000.0, "kg/hr");

    Stream feed = new Stream("feed", fluid);
    ThrottlingValve choke = new ThrottlingValve("choke", feed);
    choke.setOutletPressure(30.0, "bara");
    ProcessSystem wells = new ProcessSystem("wells");
    wells.add(feed);
    wells.add(choke);
    ProcessModel model = new ProcessModel();
    model.add("wells", wells);
    wells.run();
    return new ModelFixture(feed, model);
  }

  /** Verifies strict continuous application, immutable capture, and deterministic restoration. */
  @Test
  void continuousActionAppliesAndRestoresWithoutRunningModel() throws Exception {
    ModelFixture fixture = createModelFixture();
    ProcessModelOperatingAction action = ProcessModelOperatingAction.continuous("field-feed", "Field feed target",
        "wells::feed.flowRate", 500.0, 1500.0, "kg/hr", "synthetic installed operating envelope");

    CapabilityAssessment capability = action.inspectCapability(fixture.model);
    assertTrue(capability.isAvailable());
    assertTrue(capability.isCurrentValueWithinDomain());
    assertEquals(1000.0, capability.getCurrentValue(), 1.0e-8);
    assertThrows(UnsupportedOperationException.class, () -> capability.getDiagnostics().clear());

    ActionState baseline = action.capture(fixture.model);
    ApplicationResult applied = action.apply(fixture.model, 1250.0);
    assertTrue(applied.isApplied());
    assertEquals(1000.0, applied.getPriorState().getValue(), 1.0e-8);
    assertEquals(1250.0, fixture.feed.getFlowRate("kg/hr"), 1.0e-8);

    ApplicationResult restored = action.restore(fixture.model, baseline);
    assertTrue(restored.isApplied());
    assertEquals(1000.0, fixture.feed.getFlowRate("kg/hr"), 1.0e-8);
    assertTrue(action.apply(fixture.model, 1100.0).isApplied());
    assertTrue(action.restore(fixture.model, baseline).isApplied());
    assertEquals(1000.0, fixture.feed.getFlowRate("kg/hr"), 1.0e-8);
    ApplicationResult rejected = action.apply(fixture.model, 1600.0);
    assertFalse(rejected.isApplied());
    assertEquals(1000.0, fixture.feed.getFlowRate("kg/hr"), 1.0e-8,
        "an out-of-domain candidate must not modify the model");

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(action);
    output.writeObject(baseline);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    ProcessModelOperatingAction restoredAction = (ProcessModelOperatingAction) input.readObject();
    ActionState restoredState = (ActionState) input.readObject();
    input.close();
    assertEquals("field-feed", restoredAction.getId());
    assertEquals("synthetic installed operating envelope", restoredAction.getProvenance());
    assertTrue(restoredAction.restore(fixture.model, restoredState).isApplied());
  }

  /** Verifies discrete actions reject interpolation while preserving an off-domain baseline. */
  @Test
  void discreteActionEnumeratesCandidatesAndRestoresExistingBaseline() {
    ModelFixture fixture = createModelFixture();
    ProcessModelOperatingAction action = ProcessModelOperatingAction.discrete("feed-lineup", "Feed line-up",
        "wells::feed.flowRate", new double[] { 800.0, 1200.0 }, "kg/hr", "synthetic line-up table");

    CapabilityAssessment capability = action.inspectCapability(fixture.model);
    assertTrue(capability.isAvailable());
    assertFalse(capability.isCurrentValueWithinDomain(),
        "a brownfield baseline may be restorable without being a candidate");
    ActionState baseline = action.capture(fixture.model);
    assertEquals(ValueSemantics.DISCRETE, action.getValueSemantics());
    assertArrayEquals(new double[] { 800.0, 1200.0 }, action.getAllowedValues(), 0.0);

    assertFalse(action.apply(fixture.model, 1000.0).isApplied(),
        "interpolation between discrete line-ups must fail closed");
    assertEquals(1000.0, fixture.feed.getFlowRate("kg/hr"), 1.0e-8);
    assertTrue(action.apply(fixture.model, 1200.0).isApplied());
    assertEquals(1200.0, fixture.feed.getFlowRate("kg/hr"), 1.0e-8);
    assertTrue(action.restore(fixture.model, baseline).isApplied());
    assertEquals(1000.0, fixture.feed.getFlowRate("kg/hr"), 1.0e-8);
    ProcessModelSimulationEvaluator invalidInitialEvaluator = new ProcessModelSimulationEvaluator(fixture.model);
    assertThrows(IllegalStateException.class, () -> action.registerWith(invalidInitialEvaluator));

    double[] defensiveValues = action.getAllowedValues();
    defensiveValues[0] = 999.0;
    assertArrayEquals(new double[] { 800.0, 1200.0 }, action.getAllowedValues(), 0.0);
  }

  /** Verifies unavailable targets and foreign state tokens fail with explicit diagnostics. */
  @Test
  void unavailableOrForeignActionsFailClosed() {
    ModelFixture fixture = createModelFixture();
    ProcessModelOperatingAction missing = ProcessModelOperatingAction.continuous("missing", "Missing target",
        "wells::does-not-exist.value", 0.0, 1.0, "fraction", "synthetic test");
    CapabilityAssessment unavailable = missing.inspectCapability(fixture.model);
    assertFalse(unavailable.isAvailable());
    assertFalse(missing.apply(fixture.model, 0.5).isApplied());
    assertThrows(IllegalStateException.class, () -> missing.capture(fixture.model));

    ProcessModelOperatingAction first = ProcessModelOperatingAction.continuous("feed-a", "Feed A",
        "wells::feed.flowRate", 500.0, 1500.0, "kg/hr", "basis A");
    ProcessModelOperatingAction second = ProcessModelOperatingAction.continuous("feed-b", "Feed B",
        "wells::feed.flowRate", 500.0, 1500.0, "kg/hr", "basis B");
    ActionState firstState = first.capture(fixture.model);
    assertThrows(IllegalArgumentException.class, () -> second.restore(fixture.model, firstState));
    assertEquals(1000.0, fixture.feed.getFlowRate("kg/hr"), 1.0e-8);

    assertThrows(IllegalArgumentException.class,
        () -> ProcessModelOperatingAction.continuous(" ", "name", "wells::feed.flowRate", 0.0, 1.0, "kg/hr", "basis"));
    assertThrows(IllegalArgumentException.class,
        () -> ProcessModelOperatingAction.continuous("id", "name", "wells::feed.flowRate", 2.0, 1.0, "kg/hr", "basis"));
    assertThrows(IllegalArgumentException.class, () -> ProcessModelOperatingAction.discrete("id", "name",
        "wells::feed.flowRate", new double[] { 1.0, 1.0 }, "kg/hr", "basis"));
  }

  /** Verifies optimization-facing registration and exact discrete failure behavior. */
  @Test
  void actionRegistersWithProcessModelEvaluator() throws Exception {
    ModelFixture continuousFixture = createModelFixture();
    ProcessModelSimulationEvaluator continuousEvaluator = new ProcessModelSimulationEvaluator(continuousFixture.model);
    ProcessModelOperatingAction continuous = ProcessModelOperatingAction.continuous("feed", "Field feed",
        "wells::feed.flowRate", 500.0, 1500.0, "kg/hr", "synthetic operating envelope");
    ActionParameterBinding continuousBinding = continuous.registerWith(continuousEvaluator);
    continuousEvaluator.addObjective("feed", model -> continuousFixture.feed.getFlowRate("kg/hr"));

    assertEquals(0, continuousBinding.getParameterIndex());
    assertEquals(1000.0, continuousBinding.getInitialValue(), 1.0e-8);
    assertEquals("wells::feed.flowRate", continuousEvaluator.getParameters().get(0).getAddress());
    assertFalse(continuousEvaluator.getParameters().get(0).isClampToBounds());
    assertTrue(continuousEvaluator.evaluate(new double[] { 1300.0 }).isSimulationConverged());
    assertEquals(1300.0, continuousFixture.feed.getFlowRate("kg/hr"), 1.0e-8);
    assertFalse(continuousEvaluator.evaluate(new double[] { 1600.0 }).isSimulationConverged());
    assertEquals(1300.0, continuousFixture.feed.getFlowRate("kg/hr"), 1.0e-8,
        "a strict continuous action must reject rather than clamp an out-of-bounds candidate");
    ProcessModelSimulationEvaluator.SensitivityQualityResult quality = continuousEvaluator
        .estimateSensitivitiesWithQuality(new double[] { 1000.0 });
    assertFalse(quality.getParameterSnapshots().get(0).isClampToBounds());
    assertEquals(1000.0, quality.getParameterSnapshots().get(0).getBaseValue(), 1.0e-8);

    ModelFixture discreteFixture = createModelFixture();
    ProcessModelSimulationEvaluator discreteEvaluator = new ProcessModelSimulationEvaluator(discreteFixture.model);
    ProcessModelOperatingAction discrete = ProcessModelOperatingAction.discrete("line-up", "Feed line-up",
        "wells::feed.flowRate", new double[] { 1000.0, 1200.0 }, "kg/hr", "synthetic line-up table");
    ActionParameterBinding discreteBinding = discrete.registerWith(discreteEvaluator);
    discreteEvaluator.addObjective("feed", model -> discreteFixture.feed.getFlowRate("kg/hr"));
    assertArrayEquals(new double[] { 1000.0, 1200.0 }, discreteBinding.getAllowedValues(), 0.0);
    assertTrue(discreteEvaluator.evaluate(new double[] { 1200.0 }).isSimulationConverged());
    assertEquals(1200.0, discreteFixture.feed.getFlowRate("kg/hr"), 1.0e-8);

    ProcessModelSimulationEvaluator.EvaluationResult rejected = discreteEvaluator.evaluate(new double[] { 1100.0 });
    assertFalse(rejected.isSimulationConverged());
    assertNotNull(rejected.getErrorMessage());
    assertEquals(1200.0, discreteFixture.feed.getFlowRate("kg/hr"), 1.0e-8,
        "a rejected discrete candidate must leave the previous verified value unchanged");
    assertFalse(discreteEvaluator.evaluate(new double[] { 700.0 }).isSimulationConverged());
    assertEquals(1200.0, discreteFixture.feed.getFlowRate("kg/hr"), 1.0e-8,
        "an out-of-envelope discrete candidate must reject rather than clamp to a line-up");

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(discreteBinding);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    ActionParameterBinding restoredBinding = (ActionParameterBinding) input.readObject();
    input.close();
    assertEquals("line-up", restoredBinding.getAction().getId());
    assertArrayEquals(new double[] { 1000.0, 1200.0 }, restoredBinding.getAllowedValues(), 0.0);
  }
}
