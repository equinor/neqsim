package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.dynamics.TransientStepIdentifier;
import neqsim.process.equipment.energy.EnergyNetworkSolver;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for physical-step versus refinement calculation identity. */
public class TransientPhysicalStepIdentityTest extends neqsim.NeqSimTest {

  /** Repeated evaluations inside one physical step share an ID without advancing local time twice. */
  @Test
  public void refinementReusesPhysicalStepIdentifierWithoutDoubleClockAdvance() {
    Recycle recycle = createRecycle();
    UUID physicalStepA = TransientStepIdentifier.deterministicPhysicalStep("recycle-refinement", 0L);
    UUID physicalStepB = TransientStepIdentifier.deterministicPhysicalStep("recycle-refinement", 1L);

    recycle.runTransient(2.0, physicalStepA);
    recycle.runTransient(2.0, physicalStepA);
    recycle.runTransient(2.0, physicalStepB);

    assertEquals(3, recycle.getIterations());
    assertEquals(4.0, recycle.getTime(), 0.0);
    assertEquals(physicalStepB, recycle.getCalculationIdentifier());
  }

  /** Algebraic energy-network refinement recalculates but advances its local clock once per physical step. */
  @Test
  public void energyNetworkRefinementAdvancesClockOncePerPhysicalStep() {
    EnergyNetworkSolver energyNetwork = new EnergyNetworkSolver("energy network");
    UUID physicalStepA = TransientStepIdentifier.deterministicPhysicalStep("energy-refinement", 0L);
    UUID physicalStepB = TransientStepIdentifier.deterministicPhysicalStep("energy-refinement", 1L);

    energyNetwork.runTransient(2.0, physicalStepA);
    energyNetwork.runTransient(2.0, physicalStepA);
    energyNetwork.runTransient(2.0, physicalStepB);

    assertEquals(4.0, energyNetwork.getTime(), 0.0);
    assertEquals(physicalStepB, energyNetwork.getCalculationIdentifier());
  }

  /** Consecutive physical steps use distinct IDs so a mutable controller advances exactly once per step. */
  @Test
  public void consecutiveProcessSystemStepsAdvanceControllerOnceEach() {
    ProcessSystem process = createControlledProcess("process");
    ControllerDeviceBaseClass controller = (ControllerDeviceBaseClass) process.getControllerDevices().get(0);
    UUID physicalStepA = TransientStepIdentifier.deterministicPhysicalStep("controller-loop", 0L);
    UUID physicalStepB = TransientStepIdentifier.deterministicPhysicalStep("controller-loop", 1L);

    process.runTransient(1.0, physicalStepA);
    process.runTransient(1.0, physicalStepB);

    assertNotEquals(physicalStepA, physicalStepB);
    assertEquals(2.0, process.getTime(), 0.0);
    assertEquals(2, controller.getEventLog().size());
    assertTrue(controller.hasRunTransient(physicalStepB));
  }

  /** One model-level physical-step ID is shared across areas, while the following step receives a new ID. */
  @Test
  public void processModelSharesOnePhysicalStepIdAcrossAreasAndChangesItNextStep() {
    ProcessSystem areaA = createControlledProcess("area A");
    ProcessSystem areaB = createControlledProcess("area B");
    ProcessModel model = new ProcessModel();
    model.add("A", areaA);
    model.add("B", areaB);

    UUID physicalStepA = TransientStepIdentifier.deterministicPhysicalStep("multi-area", 0L);
    UUID physicalStepB = TransientStepIdentifier.deterministicPhysicalStep("multi-area", 1L);

    model.runTransient(0.5, physicalStepA);
    assertEquals(physicalStepA, areaA.getCalculationIdentifier());
    assertEquals(physicalStepA, areaB.getCalculationIdentifier());
    assertEquals(0.5, areaA.getTime(), 0.0);
    assertEquals(areaA.getTime(), areaB.getTime(), 0.0);

    model.runTransient(0.5, physicalStepB);
    assertEquals(physicalStepB, areaA.getCalculationIdentifier());
    assertEquals(physicalStepB, areaB.getCalculationIdentifier());
    assertEquals(1.0, areaA.getTime(), 0.0);
    assertEquals(areaA.getTime(), areaB.getTime(), 0.0);
    assertEquals(2, ((ControllerDeviceBaseClass) areaA.getControllerDevices().get(0)).getEventLog().size());
    assertEquals(2, ((ControllerDeviceBaseClass) areaB.getControllerDevices().get(0)).getEventLog().size());
  }

  /** Refinement identities are diagnostic metadata and remain separate from the physical-step identity. */
  @Test
  public void evaluationIdentifiersAreDistinctFromPhysicalStepIdentifier() {
    UUID physicalStep = TransientStepIdentifier.deterministicPhysicalStep("newton-step", 7L);
    UUID evaluation0 = TransientStepIdentifier.deterministicEvaluation(physicalStep, 0L);
    UUID evaluation1 = TransientStepIdentifier.deterministicEvaluation(physicalStep, 1L);

    assertNotEquals(physicalStep, evaluation0);
    assertNotEquals(physicalStep, evaluation1);
    assertNotEquals(evaluation0, evaluation1);
    assertEquals(physicalStep, TransientStepIdentifier.deterministicPhysicalStep("newton-step", 7L));
    assertEquals(evaluation0, TransientStepIdentifier.deterministicEvaluation(physicalStep, 0L));
  }

  private static ProcessSystem createControlledProcess(String name) {
    Stream feed = createFeed(name + " feed");
    PressureTransmitter transmitter = new PressureTransmitter(name + " PT", feed);
    transmitter.setUnit("bara");

    ControllerDeviceBaseClass controller = new ControllerDeviceBaseClass(name + " PC");
    controller.setTransmitter(transmitter);
    controller.setUnit("bara");
    controller.setControllerSetPoint(50.0);
    controller.setControllerParameters(1.0, 20.0, 0.0);

    ProcessSystem process = new ProcessSystem(name);
    process.add(feed);
    process.add(transmitter);
    process.add(controller);
    process.run();
    return process;
  }

  private static Stream createFeed(String name) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setPressure(50.0, "bara");
    feed.setTemperature(25.0, "C");
    return feed;
  }

  private static Recycle createRecycle() {
    Stream inlet = createFeed("recycle inlet");
    Stream outlet = inlet.clone("recycle outlet");
    Recycle recycle = new Recycle("recycle");
    recycle.addStream(inlet);
    recycle.setOutletStream(outlet);
    return recycle;
  }
}
