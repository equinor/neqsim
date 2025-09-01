package neqsim.process.mechanicaldesign.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for HeatExchanger mechanical design calculations.
 */
public class HeatExchangerMechanicalDesignTest {
  @Test
  void testCalcDesign() {
    SystemInterface system1 = new SystemSrkEos(273.15 + 60.0, 20.0);
    system1.addComponent("methane", 120.0);
    system1.addComponent("ethane", 120.0);
    system1.addComponent("n-heptane", 3.0);
    system1.createDatabase(true);
    system1.setMixingRule(2);
    ThermodynamicOperations ops1 = new ThermodynamicOperations(system1);
    ops1.TPflash();

    Stream hot = new Stream("hot", system1);
    hot.setTemperature(100.0, "C");
    hot.setFlowRate(1000.0, "kg/hr");

    Stream cold = new Stream("cold", system1.clone());
    cold.setTemperature(20.0, "C");
    cold.setFlowRate(310.0, "kg/hr");

    HeatExchanger hx = new HeatExchanger("hx", hot, cold);
    hx.setUAvalue(1000.0);

    ProcessSystem ps = new ProcessSystem();
    ps.add(hot);
    ps.add(cold);
    ps.add(hx);
    ps.run();

    hx.initMechanicalDesign();
    hx.getMechanicalDesign().calcDesign();
    assertTrue(hx.getMechanicalDesign().getWeightTotal() > 0.0);
  }
}
