package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

class ProcessSystemDocumentationTest {
  @Test
  void processOverviewQuickStartExecutesSupportedDiagnostics() {
    SystemInterface fluid = new SystemSrkEos(300.0, 80.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.02);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");

    ThrottlingValve valve = new ThrottlingValve("inlet valve", feed);
    valve.setOutletPressure(40.0, "bara");

    Separator separator = new Separator("HP separator", valve.getOutletStream());

    ProcessSystem process = new ProcessSystem("gas processing plant");
    process.add(feed);
    process.add(valve);
    process.add(separator);
    process.run();

    double inletMassFlowKgPerHr = feed.getFlowRate("kg/hr");
    double gasMassFlowKgPerHr = separator.getGasOutStream().getFlowRate("kg/hr");
    double liquidMassFlowKgPerHr =
        separator.getLiquidOutStream().getFlowRate("kg/hr");
    double relativeMassBalanceError =
        Math.abs(
                inletMassFlowKgPerHr
                    - gasMassFlowKgPerHr
                    - liquidMassFlowKgPerHr)
            / inletMassFlowKgPerHr;

    assertTrue(gasMassFlowKgPerHr > 0.0);
    assertTrue(relativeMassBalanceError < 1.0e-6);
    assertSame(separator, process.getUnit("HP separator"));
    assertFalse(process.hasRecycleLoops());
    assertFalse(process.getExecutionPartitionInfo().isEmpty());
    assertFalse(process.getReport_json().isEmpty());
    assertFalse(process.getStreamSummaryTable().isEmpty());
  }

  @Test
  void equipmentIndexDocumentsStreamTopologyOwnership()
      throws NoSuchMethodException {
    assertTrue(
        ProcessEquipmentInterface.class.isAssignableFrom(
            ProcessEquipmentBaseClass.class));
    assertTrue(
        ProcessEquipmentBaseClass.class.isAssignableFrom(
            TwoPortEquipment.class));
    assertTrue(TwoPortEquipment.class.isAssignableFrom(ThrottlingValve.class));
    assertFalse(TwoPortEquipment.class.isAssignableFrom(Separator.class));

    assertSame(
        StreamInterface.class,
        TwoPortEquipment.class
            .getMethod("getInletStream")
            .getReturnType());
    assertSame(
        StreamInterface.class,
        TwoPortEquipment.class
            .getMethod("getOutletStream")
            .getReturnType());
    assertSame(
        StreamInterface.class,
        Separator.class.getMethod("getGasOutStream").getReturnType());
    assertSame(
        StreamInterface.class,
        Separator.class.getMethod("getLiquidOutStream").getReturnType());
  }
}
