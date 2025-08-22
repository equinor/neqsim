package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.valve.SafetyValveMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Test API 520 gas sizing for safety valves. */
public class SafetyValveMechanicalDesignTest {
  @Test
  void testApi520GasSizing() {
    SystemInterface gas = new SystemSrkEos(300.0, 50.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    gas.setTotalFlowRate(10.0, "kg/sec");
    StreamInterface inlet = new Stream("gas", gas);

    SafetyValve valve = new SafetyValve("PSV", inlet);
    valve.setPressureSpec(50.0);

    SafetyValveMechanicalDesign design =
        (SafetyValveMechanicalDesign) valve.getMechanicalDesign();
    design.calcDesign();
    double area = design.getOrificeArea();

    double k = gas.getGamma();
    double z = gas.getZ();
    double mw = gas.getMolarMass();
    double R = 8.314;
    double kd = 0.975;
    double kb = 1.0;
    double kw = 1.0;
    double relievingPressure = 50.0 * 1e5;
    double relievingTemperature = 300.0;
    double C = Math.sqrt(k) * Math.pow(2.0 / (k + 1.0), (k + 1.0) / (2.0 * (k - 1.0)));
    double expected = 10.0 * Math.sqrt(z * R * relievingTemperature / mw)
        / (kd * kb * kw * relievingPressure * C);

    assertEquals(expected, area, 1e-8);
  }
}
