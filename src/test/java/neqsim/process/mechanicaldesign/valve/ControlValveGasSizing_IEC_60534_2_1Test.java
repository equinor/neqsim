package neqsim.process.mechanicaldesign.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ControlValveGasSizing_IEC_60534_2_1}.
 */
public class ControlValveGasSizing_IEC_60534_2_1Test {
  /**
   * A moderate pressure drop should give non-choked subcritical flow with Y near unity.
   */
  @Test
  void testSubcriticalSizing() {
    ControlValveGasSizing_IEC_60534_2_1 calc = new ControlValveGasSizing_IEC_60534_2_1();
    calc.setFlowConditions(1000.0, 10.0, 9.0, 8.0);
    calc.setValveCoefficients(1.30, 0.70, 1.0);
    calc.calcSizing();

    assertEquals(0.1, calc.getPressureDropRatio(), 1e-9, "x = (10-9)/10 = 0.1");
    assertFalse(calc.isChoked(), "Small pressure drop should not be choked");
    assertTrue(calc.getExpansionFactor() > 0.9, "Y should be near unity for low x");
    assertTrue(calc.getRequiredKv() > 0.0, "Kv should be positive");
    assertNotNull(calc.toJson());
  }

  /**
   * A large pressure drop should be choked and clamp the expansion factor to 2/3.
   */
  @Test
  void testChokedFlowClampsExpansionFactor() {
    ControlValveGasSizing_IEC_60534_2_1 calc = new ControlValveGasSizing_IEC_60534_2_1();
    calc.setFlowConditions(1000.0, 10.0, 2.0, 8.0);
    calc.setValveCoefficients(1.30, 0.70, 1.0);
    calc.calcSizing();

    assertTrue(calc.isChoked(), "Large pressure drop should be choked");
    assertEquals(2.0 / 3.0, calc.getExpansionFactor(), 1e-9, "Y should clamp to 2/3 when choked");
  }

  /**
   * The required Cv should equal Kv / 0.865 and exceed Kv.
   */
  @Test
  void testCvFromKvConversion() {
    ControlValveGasSizing_IEC_60534_2_1 calc = new ControlValveGasSizing_IEC_60534_2_1();
    calc.setFlowConditions(2000.0, 20.0, 15.0, 15.0);
    calc.setValveCoefficients(1.27, 0.72, 0.95);
    calc.calcSizing();

    assertEquals(calc.getRequiredKv() / 0.865, calc.getRequiredCv(), 1e-9, "Cv = Kv / 0.865");
    assertTrue(calc.getRequiredCv() > calc.getRequiredKv(), "Cv should exceed Kv");
  }

  /**
   * Higher mass flow should require a larger flow coefficient.
   */
  @Test
  void testKvScalesWithFlow() {
    ControlValveGasSizing_IEC_60534_2_1 low = new ControlValveGasSizing_IEC_60534_2_1();
    low.setFlowConditions(1000.0, 10.0, 8.0, 8.0);
    low.setValveCoefficients(1.30, 0.70, 1.0);
    low.calcSizing();

    ControlValveGasSizing_IEC_60534_2_1 high = new ControlValveGasSizing_IEC_60534_2_1();
    high.setFlowConditions(3000.0, 10.0, 8.0, 8.0);
    high.setValveCoefficients(1.30, 0.70, 1.0);
    high.calcSizing();

    assertTrue(high.getRequiredKv() > low.getRequiredKv(), "Higher flow needs larger Kv");
  }

  /**
   * The {@code fromValve} bridge should populate flow, pressures and density from a run throttling
   * valve.
   */
  @Test
  void testFromProcessValve() {
    neqsim.thermo.system.SystemSrkEos fluid = new neqsim.thermo.system.SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    neqsim.process.equipment.stream.Stream feed =
        new neqsim.process.equipment.stream.Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(10.0, "bara");
    neqsim.process.equipment.valve.ThrottlingValve valve =
        new neqsim.process.equipment.valve.ThrottlingValve("valve", feed);
    valve.setOutletPressure(5.0, "bara");
    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(feed);
    process.add(valve);
    process.run();
    feed.getFluid().initProperties();

    ControlValveGasSizing_IEC_60534_2_1 calc = new ControlValveGasSizing_IEC_60534_2_1();
    calc.fromValve(valve);
    calc.setValveCoefficients(1.30, 0.70, 1.0);
    calc.calcSizing();

    assertTrue(calc.getRequiredKv() > 0.0, "Kv should be positive");
    assertTrue(calc.getRequiredCv() > 0.0, "Cv should be positive");
    assertTrue(calc.getPressureDropRatio() > 0.0,
        "Pressure drop ratio should be derived from the valve");
    assertNotNull(calc.toJson());
  }
}
