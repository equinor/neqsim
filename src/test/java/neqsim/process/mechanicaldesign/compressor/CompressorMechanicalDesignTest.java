package neqsim.process.mechanicaldesign.compressor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Tests for compressor mechanical design calculations. */
public class CompressorMechanicalDesignTest {
  @Test
  void testCalcDesign() {
    SystemInterface gas = new SystemSrkEos(300.0, 10.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();

    Stream inlet = new Stream("inlet", gas);
    inlet.setFlowRate(10.0, "kg/hr");

    Compressor comp = new Compressor("comp", inlet);
    comp.setOutletPressure(20.0);

    ProcessSystem ps = new ProcessSystem();
    ps.add(inlet);
    ps.add(comp);
    ps.run();

    comp.initMechanicalDesign();
    comp.getMechanicalDesign().calcDesign();
    assertTrue(comp.getMechanicalDesign().getWeightTotal() > 0.0);
  }
}
