package neqsim.process.controllerdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.TransientStepIdentifier;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.dynamics.TransientTransactionCoverage;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Quantitative identity, rollback, replay, and restart evidence for native control blocks. */
class ControlBlockTransientStateTransactionTest extends neqsim.NeqSimTest {
  private static final double TOLERANCE = 0.0;

  @Test
  void multiAreaRollbackRestoresBindingsConfigurationStateAndExactReplay() {
    PressureTransmitter transmitter = createTransmitter(10.0);
    TransferFunctionBlock transfer = createTransferBlock(transmitter);
    LogicBlock logic = createLogicBlock(transmitter);
    ProcessSystem controlArea = new ProcessSystem("control area");
    controlArea.add(transfer);
    ProcessSystem safeguardingArea = new ProcessSystem("safeguarding area");
    safeguardingArea.add(logic);
    ProcessModel model = new ProcessModel();
    model.add("control", controlArea);
    model.add("safeguarding", safeguardingArea);

    UUID initialId = TransientStepIdentifier.deterministicPhysicalStep("control-block-replay", 0L);
    transfer.runTransient(0.0, 1.0, initialId);
    logic.runTransient(0.0, 1.0, initialId);
    double initialTransferOutput = transfer.getOutput();
    double initialLogicOutput = logic.getOutput();
    String transferIdentity = transfer.getTransientStateIdentity();
    String logicIdentity = logic.getTransientStateIdentity();

    TransientTransactionCoverage coverage = model.getTransientTransactionCoverage();
    assertEquals(2, coverage.getProcessElementCount());
    assertEquals(2, coverage.getParticipantCount());
    assertTrue(coverage.isComplete());

    UUID[] stepIds = new UUID[] { TransientStepIdentifier.deterministicPhysicalStep("control-block-replay", 1L),
        TransientStepIdentifier.deterministicPhysicalStep("control-block-replay", 2L),
        TransientStepIdentifier.deterministicPhysicalStep("control-block-replay", 3L) };
    double[] pressures = new double[] { 20.0, 30.0, 15.0 };
    double[] trialOutputs = new double[pressures.length];

    TransientStepTransaction transaction = model.beginTransientStepTransaction();
    for (int i = 0; i < pressures.length; i++) {
      transmitter.getStream().setPressure(pressures[i], "bara");
      transfer.runTransient(0.0, 1.0, stepIds[i]);
      trialOutputs[i] = transfer.getOutput();
    }
    logic.runTransient(0.0, 1.0, stepIds[0]);
    double trialLogicOutput = logic.getOutput();

    transfer.setName("trial transfer");
    transfer.setGain(9.0);
    transfer.setLagTime(12.0);
    transfer.setLagTime2(13.0);
    transfer.setLeadTime(7.0);
    transfer.setDeadTime(8.0);
    transfer.setInputBias(5.0);
    transfer.setOutputBias(6.0);
    transfer.setTransmitter(createTransmitter(99.0));
    transfer.setUnit("trial");
    transfer.setActive(false);
    logic.setName("trial logic");
    logic.addFixedInput(false);
    logic.setEqualityTolerance(0.25);
    logic.setUnit("trial");
    logic.setActive(false);
    transaction.rollback();

    assertEquals(transferIdentity, transfer.getTransientStateIdentity());
    assertEquals(logicIdentity, logic.getTransientStateIdentity());
    assertEquals("TF-100", transfer.getName());
    assertEquals(2.0, transfer.getGain(), TOLERANCE);
    assertEquals(4.0, transfer.getLagTime(), TOLERANCE);
    assertEquals(6.0, transfer.getLagTime2(), TOLERANCE);
    assertEquals(1.0, transfer.getLeadTime(), TOLERANCE);
    assertEquals(2.0, transfer.getDeadTime(), TOLERANCE);
    assertEquals(1.0, transfer.getInputBias(), TOLERANCE);
    assertEquals(3.0, transfer.getOutputBias(), TOLERANCE);
    assertSame(transmitter, transfer.getTransmitter());
    assertEquals("bara", transfer.getUnit());
    assertTrue(transfer.isActive());
    assertEquals(initialTransferOutput, transfer.getOutput(), TOLERANCE);
    assertEquals("LS-100", logic.getName());
    assertEquals(1, logic.getInputs().size());
    assertSame(transmitter, logic.getInputs().get(0).getDevice());
    assertEquals(1.0e-6, logic.getEqualityTolerance(), TOLERANCE);
    assertEquals("[bool]", logic.getUnit());
    assertTrue(logic.isActive());
    assertEquals(initialLogicOutput, logic.getOutput(), TOLERANCE);
    assertFalse(transfer.hasRunTransient(stepIds[0]));
    assertFalse(logic.hasRunTransient(stepIds[0]));

    for (int i = 0; i < pressures.length; i++) {
      transmitter.getStream().setPressure(pressures[i], "bara");
      transfer.runTransient(0.0, 1.0, stepIds[i]);
      assertEquals(trialOutputs[i], transfer.getOutput(), TOLERANCE,
          "rollback must restore the dead-time buffer and both lag states exactly");
    }
    logic.runTransient(0.0, 1.0, stepIds[0]);
    assertEquals(trialLogicOutput, logic.getOutput(), TOLERANCE);
  }

  @Test
  void physicalStepIdentityPreventsDuplicateControlStateAdvance() {
    PressureTransmitter transmitter = createTransmitter(10.0);
    TransferFunctionBlock transfer = createTransferBlock(transmitter);
    LogicBlock logic = createLogicBlock(transmitter);
    UUID initialId = TransientStepIdentifier.deterministicPhysicalStep("control-block-id", 0L);
    transfer.runTransient(0.0, 1.0, initialId);
    logic.runTransient(0.0, 1.0, initialId);

    transmitter.getStream().setPressure(20.0, "bara");
    UUID physicalStepId = TransientStepIdentifier.deterministicPhysicalStep("control-block-id", 1L);
    transfer.runTransient(0.0, 1.0, physicalStepId);
    logic.runTransient(0.0, 1.0, physicalStepId);
    double transferAfterFirstEvaluation = transfer.getOutput();
    double logicAfterFirstEvaluation = logic.getOutput();

    transmitter.getStream().setPressure(50.0, "bara");
    transfer.runTransient(0.0, 1.0, physicalStepId);
    logic.runTransient(0.0, 1.0, physicalStepId);

    assertEquals(transferAfterFirstEvaluation, transfer.getOutput(), TOLERANCE);
    assertEquals(logicAfterFirstEvaluation, logic.getOutput(), TOLERANCE);
    assertTrue(transfer.hasRunTransient(physicalStepId));
    assertTrue(logic.hasRunTransient(physicalStepId));

    transfer.reset();
    assertFalse(transfer.hasRunTransient(physicalStepId), "reset must reopen deterministic replay from initial state");
    transfer.runTransient(0.0, 1.0, physicalStepId);
    assertTrue(transfer.hasRunTransient(physicalStepId));
  }

  @Test
  void processSystemTransactionalStepCommitsBothControlBlocksAndClock() {
    PressureTransmitter transmitter = createTransmitter(20.0);
    TransferFunctionBlock transfer = createTransferBlock(transmitter);
    LogicBlock logic = createLogicBlock(transmitter);
    ProcessSystem process = new ProcessSystem("control-block commit");
    process.add(transfer);
    process.add(logic);
    UUID stepId = TransientStepIdentifier.deterministicPhysicalStep("control-block-commit", 0L);

    process.runTransientTransactional(0.5, stepId);

    assertEquals(0.5, process.getTime(), TOLERANCE);
    assertEquals(stepId, process.getCalculationIdentifier());
    assertTrue(transfer.hasRunTransient(stepId));
    assertTrue(logic.hasRunTransient(stepId));
    assertEquals(1.0, logic.getOutput(), TOLERANCE);
  }

  @Test
  void serializedSnapshotsPreserveStableIdentitiesSharedBindingsAndContinuation() throws Exception {
    PressureTransmitter transmitter = createTransmitter(10.0);
    TransferFunctionBlock transfer = createTransferBlock(transmitter);
    LogicBlock logic = createLogicBlock(transmitter);
    UUID initialId = TransientStepIdentifier.deterministicPhysicalStep("control-block-restart", 0L);
    transfer.runTransient(0.0, 1.0, initialId);
    logic.runTransient(0.0, 1.0, initialId);
    TransferFunctionBlock.TransferFunctionState transferSnapshot = transfer.captureTransientState();
    LogicBlock.LogicBlockState logicSnapshot = logic.captureTransientState();
    String transferIdentity = transfer.getTransientStateIdentity();
    String logicIdentity = logic.getTransientStateIdentity();

    byte[] serialized;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(transfer);
      output.writeObject(logic);
      output.writeObject(transferSnapshot);
      output.writeObject(logicSnapshot);
      serialized = bytes.toByteArray();
    }

    TransferFunctionBlock restoredTransfer;
    LogicBlock restoredLogic;
    TransferFunctionBlock.TransferFunctionState restoredTransferSnapshot;
    LogicBlock.LogicBlockState restoredLogicSnapshot;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      restoredTransfer = (TransferFunctionBlock) input.readObject();
      restoredLogic = (LogicBlock) input.readObject();
      restoredTransferSnapshot = (TransferFunctionBlock.TransferFunctionState) input.readObject();
      restoredLogicSnapshot = (LogicBlock.LogicBlockState) input.readObject();
    }

    restoredTransfer.restoreTransientState(restoredTransferSnapshot);
    restoredLogic.restoreTransientState(restoredLogicSnapshot);
    assertEquals(transferIdentity, restoredTransfer.getTransientStateIdentity());
    assertEquals(logicIdentity, restoredLogic.getTransientStateIdentity());
    assertSame(restoredTransfer.getTransmitter(), restoredLogic.getInputs().get(0).getDevice());

    PressureTransmitter restoredTransmitter = (PressureTransmitter) restoredTransfer.getTransmitter();
    restoredTransmitter.getStream().setPressure(25.0, "bara");
    transmitter.getStream().setPressure(25.0, "bara");
    UUID nextId = TransientStepIdentifier.deterministicPhysicalStep("control-block-restart", 1L);
    restoredTransfer.runTransient(0.0, 1.0, nextId);
    restoredLogic.runTransient(0.0, 1.0, nextId);
    transfer.runTransient(0.0, 1.0, nextId);
    logic.runTransient(0.0, 1.0, nextId);
    assertEquals(transfer.getOutput(), restoredTransfer.getOutput(), TOLERANCE);
    assertEquals(logic.getOutput(), restoredLogic.getOutput(), TOLERANCE);
  }

  @Test
  void foreignSnapshotsAndUnqualifiedSubclassesFailBeforeMutation() {
    TransferFunctionBlock firstTransfer = createTransferBlock(createTransmitter(10.0));
    TransferFunctionBlock secondTransfer = createTransferBlock(createTransmitter(20.0));
    secondTransfer.setGain(7.0);
    assertThrows(IllegalArgumentException.class,
        () -> secondTransfer.restoreTransientState(firstTransfer.captureTransientState()));
    assertEquals(7.0, secondTransfer.getGain(), TOLERANCE);

    LogicBlock firstLogic = createLogicBlock(createTransmitter(10.0));
    LogicBlock secondLogic = createLogicBlock(createTransmitter(20.0));
    secondLogic.addFixedInput(true);
    assertThrows(IllegalArgumentException.class,
        () -> secondLogic.restoreTransientState(firstLogic.captureTransientState()));
    assertEquals(2, secondLogic.getInputs().size());

    ProcessSystem process = new ProcessSystem("control-block subclass coverage");
    process.add(new StatefulTransferSubclass("custom transfer"));
    process.add(new StatefulLogicSubclass("custom logic"));
    TransientTransactionCoverage coverage = process.getTransientTransactionCoverage();
    assertEquals(2, coverage.getProcessElementCount());
    assertEquals(2, coverage.getParticipantCount());
    assertFalse(coverage.isComplete());
    assertTrue(coverage.getBlockingIssues().get(0).contains("subclass-owned mutable state"));
    assertTrue(coverage.getBlockingIssues().get(1).contains("subclass-owned mutable state"));
    assertThrows(IllegalStateException.class, process::beginTransientStepTransaction);
  }

  private static TransferFunctionBlock createTransferBlock(PressureTransmitter transmitter) {
    TransferFunctionBlock block = new TransferFunctionBlock("TF-100", TransferFunctionBlock.Type.SECOND_ORDER);
    block.setGain(2.0);
    block.setLagTime(4.0);
    block.setLagTime2(6.0);
    block.setLeadTime(1.0);
    block.setDeadTime(2.0);
    block.setInputBias(1.0);
    block.setOutputBias(3.0);
    block.setUnit("bara");
    block.setTransmitter(transmitter);
    return block;
  }

  private static LogicBlock createLogicBlock(PressureTransmitter transmitter) {
    LogicBlock block = new LogicBlock("LS-100", LogicBlock.Operator.AND);
    block.addInput(transmitter, 15.0, LogicBlock.Comparator.GREATER_EQUAL);
    return block;
  }

  private static PressureTransmitter createTransmitter(double pressureBara) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, pressureBara);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream stream = new Stream("feed", fluid);
    stream.setPressure(pressureBara, "bara");
    return new PressureTransmitter("PT-100", stream);
  }

  private static final class StatefulTransferSubclass extends TransferFunctionBlock {
    private static final long serialVersionUID = 1000L;
    @SuppressWarnings("unused")
    private double customState;

    private StatefulTransferSubclass(String name) {
      super(name, Type.FIRST_ORDER_LAG);
    }
  }

  private static final class StatefulLogicSubclass extends LogicBlock {
    private static final long serialVersionUID = 1000L;
    @SuppressWarnings("unused")
    private double customState;

    private StatefulLogicSubclass(String name) {
      super(name, Operator.AND);
    }
  }
}
