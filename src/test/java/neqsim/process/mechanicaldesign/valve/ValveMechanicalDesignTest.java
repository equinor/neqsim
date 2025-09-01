package neqsim.process.mechanicaldesign.valve;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests for valve mechanical design calculations. */
public class ValveMechanicalDesignTest {
  @Test
  void testCalcDesign() {
    SystemInterface gas = new SystemSrkEos(300.0, 10.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();

    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(10.0, "kg/hr");

    ThrottlingValve valve = new ThrottlingValve("valve", inlet);
    valve.setOutletPressure(5.0);

    ProcessSystem ps = new ProcessSystem();
    ps.add(inlet);
    ps.add(valve);
    ps.run();

    valve.initMechanicalDesign();
    valve.getMechanicalDesign().calcDesign();
    assertTrue(valve.getMechanicalDesign().getWeightTotal() > 0.0);
  }
}
