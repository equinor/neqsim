package neqsim.process.equipment.splitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

class SplitterTest {
  neqsim.thermo.system.SystemInterface testSystem;
  Stream inletStream;

  @BeforeEach
  void setUp() {
    testSystem = new SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("methane", 80.0);
    testSystem.addComponent("ethane", 12.0);
    testSystem.addComponent("propane", 8.0);
    testSystem.setMixingRule("classic");

    inletStream = new Stream("inlet", testSystem);
    inletStream.setPressure(50.0, "bara");
    inletStream.setTemperature(25.0, "C");
    inletStream.setFlowRate(10.0, "MSm3/day");
    inletStream.run();
  }

  @Test
  void testTwoWayEqualSplit() {
    Splitter splitter = new Splitter("splitter", inletStream, 2);
    splitter.setSplitFactors(new double[] { 0.5, 0.5 });
    splitter.run();

    double inletMoles = inletStream.getThermoSystem().getTotalNumberOfMoles();
    double split0Moles = splitter.getSplitStream(0).getThermoSystem().getTotalNumberOfMoles();
    double split1Moles = splitter.getSplitStream(1).getThermoSystem().getTotalNumberOfMoles();

    // Each split stream should have half the moles
    assertEquals(inletMoles / 2.0, split0Moles, inletMoles * 1e-4);
    assertEquals(inletMoles / 2.0, split1Moles, inletMoles * 1e-4);
  }

  @Test
  void testUnequalSplit() {
    Splitter splitter = new Splitter("splitter", inletStream, 3);
    splitter.setSplitFactors(new double[] { 0.6, 0.3, 0.1 });
    splitter.run();

    double inletMoles = inletStream.getThermoSystem().getTotalNumberOfMoles();
    double split0Moles = splitter.getSplitStream(0).getThermoSystem().getTotalNumberOfMoles();
    double split1Moles = splitter.getSplitStream(1).getThermoSystem().getTotalNumberOfMoles();
    double split2Moles = splitter.getSplitStream(2).getThermoSystem().getTotalNumberOfMoles();

    assertEquals(inletMoles * 0.6, split0Moles, inletMoles * 1e-4);
    assertEquals(inletMoles * 0.3, split1Moles, inletMoles * 1e-4);
    assertEquals(inletMoles * 0.1, split2Moles, inletMoles * 1e-4);
  }

  @Test
  void testSplitFactorsAreNormalized() {
    // Factors that don't sum to 1.0 should be normalized
    Splitter splitter = new Splitter("splitter", inletStream, 2);
    splitter.setSplitFactors(new double[] { 2.0, 3.0 });
    splitter.run();

    // After normalization: 2/5=0.4, 3/5=0.6
    assertEquals(0.4, splitter.getSplitFactor(0), 1e-10);
    assertEquals(0.6, splitter.getSplitFactor(1), 1e-10);
  }

  @Test
  void testNegativeSplitFactorsClampedToZero() {
    Splitter splitter = new Splitter("splitter", inletStream, 2);
    splitter.setSplitFactors(new double[] { -0.5, 1.5 });
    // Negative factor is clamped to 0, so effective split: 0/1.5=0, 1.5/1.5=1.0
    assertEquals(0.0, splitter.getSplitFactor(0), 1e-10);
    assertEquals(1.0, splitter.getSplitFactor(1), 1e-10);
  }

  @Test
  void testMassBalance() {
    Splitter splitter = new Splitter("splitter", inletStream, 3);
    splitter.setSplitFactors(new double[] { 0.5, 0.3, 0.2 });
    splitter.run();

    double massBalance = splitter.getMassBalance("kg/hr");
    assertEquals(0.0, massBalance, 1.0);
  }

  @Test
  void testCompositionPreserved() {
    Splitter splitter = new Splitter("splitter", inletStream, 2);
    splitter.setSplitFactors(new double[] { 0.7, 0.3 });
    splitter.run();

    // Composition (Z-factor) should be the same in all split streams
    double inletZ = inletStream.getThermoSystem().getPhase(0).getZ();
    double split0Z = splitter.getSplitStream(0).getThermoSystem().getPhase(0).getZ();
    double split1Z = splitter.getSplitStream(1).getThermoSystem().getPhase(0).getZ();

    assertEquals(inletZ, split0Z, 0.01);
    assertEquals(inletZ, split1Z, 0.01);
  }

  @Test
  void testSetFlowRates() {
    Splitter splitter = new Splitter("splitter", inletStream, 3);
    splitter.setFlowRates(new double[] { 5.0, 3.0, 2.0 }, "MSm3/day");
    splitter.run();

    // Verify split factors are calculated from flow rates
    assertEquals(0.5, splitter.getSplitFactor(0), 0.01);
    assertEquals(0.3, splitter.getSplitFactor(1), 0.01);
    assertEquals(0.2, splitter.getSplitFactor(2), 0.01);
  }

  @Test
  void testFixedFlowExceedingInletIsClamped() {
    // A fixed split flow larger than the inlet (e.g. an anti-surge recycle in deep
    // turndown) must not produce a negative remainder ("-1") stream. The fixed flow
    // is clamped to the inlet and the remainder stream gets zero flow, so the split
    // still conserves mass.
    double inletKgHr = inletStream.getThermoSystem().getFlowRate("kg/hr");

    Splitter splitter = new Splitter("splitter", inletStream, 2);
    // element 0 is the remainder ("-1"), element 1 demands 3x the inlet mass flow.
    splitter.setFlowRates(new double[] { -1.0, 3.0 * inletKgHr }, "kg/hr");
    splitter.run();

    // Remainder stream should be (near) zero and the fixed stream should take the
    // whole inlet — no negative split factors.
    assertTrue(splitter.getSplitFactor(0) >= -1e-9, "remainder split factor must not be negative");
    assertEquals(0.0, splitter.getSplitFactor(0), 1e-6);
    assertEquals(1.0, splitter.getSplitFactor(1), 1e-6);

    double out0 = splitter.getSplitStream(0).getThermoSystem().getFlowRate("kg/hr");
    double out1 = splitter.getSplitStream(1).getThermoSystem().getFlowRate("kg/hr");
    assertEquals(inletKgHr, out0 + out1, inletKgHr * 1e-4);
    assertEquals(0.0, out0, inletKgHr * 1e-4);
    assertEquals(inletKgHr, out1, inletKgHr * 1e-4);
  }

  @Test
  void testNegativeFixedFlowClampedToZero() {
    // A negative fixed outlet flow (e.g. a faulty sensor reading passed through as a
    // setpoint) must NOT be mistaken for the "-1" remainder marker. Historically any
    // value < -0.1 was treated as remainder, so a negative fixed flow on outlet 0
    // collided with the remainder marker on outlet 1 and silently drove the real
    // remainder outlet (outlet 1) to zero. It must instead be clamped to zero, leaving
    // the remainder outlet to take the whole inlet.
    double inletKgHr = inletStream.getThermoSystem().getFlowRate("kg/hr");

    Splitter splitter = new Splitter("splitter", inletStream, 2);
    // outlet 0: invalid negative fixed flow; outlet 1: remainder ("-1").
    splitter.setFlowRates(new double[] { -3.0 * inletKgHr, -1.0 }, "kg/hr");
    splitter.run();

    assertEquals(0.0, splitter.getSplitFactor(0), 1e-6);
    assertEquals(1.0, splitter.getSplitFactor(1), 1e-6);

    double out0 = splitter.getSplitStream(0).getThermoSystem().getFlowRate("kg/hr");
    double out1 = splitter.getSplitStream(1).getThermoSystem().getFlowRate("kg/hr");
    assertEquals(inletKgHr, out0 + out1, inletKgHr * 1e-4);
    assertEquals(0.0, out0, inletKgHr * 1e-4);
    assertEquals(inletKgHr, out1, inletKgHr * 1e-4);
  }

  @Test
  void testRemainderMarkerFirstWithFixedSecond() {
    // The "-1" remainder marker on outlet 0 must still take the flow left over after
    // the fixed flow on outlet 1 (regression guard for the sentinel handling).
    double inletKgHr = inletStream.getThermoSystem().getFlowRate("kg/hr");

    Splitter splitter = new Splitter("splitter", inletStream, 2);
    splitter.setFlowRates(new double[] { -1.0, 0.25 * inletKgHr }, "kg/hr");
    splitter.run();

    assertEquals(0.75, splitter.getSplitFactor(0), 1e-4);
    assertEquals(0.25, splitter.getSplitFactor(1), 1e-4);

    double out0 = splitter.getSplitStream(0).getThermoSystem().getFlowRate("kg/hr");
    double out1 = splitter.getSplitStream(1).getThermoSystem().getFlowRate("kg/hr");
    assertEquals(inletKgHr, out0 + out1, inletKgHr * 1e-4);
    assertEquals(0.75 * inletKgHr, out0, inletKgHr * 1e-4);
    assertEquals(0.25 * inletKgHr, out1, inletKgHr * 1e-4);
  }

  @Test
  void testGetSplitNumber() {
    Splitter splitter = new Splitter("splitter", inletStream, 4);
    assertEquals(4, splitter.getSplitNumber());
  }

  @Test
  void testToJsonNotNull() {
    Splitter splitter = new Splitter("splitter", inletStream, 2);
    splitter.setSplitFactors(new double[] { 0.5, 0.5 });
    splitter.run();

    String json = splitter.toJson();
    assertNotNull(json);
    assertFalse(json.isEmpty());
  }

  @Test
  void testNeedRecalculation() {
    Splitter splitter = new Splitter("splitter", inletStream, 2);
    splitter.setSplitFactors(new double[] { 0.5, 0.5 });
    splitter.run();

    // After run with same conditions, should not need recalculation
    assertFalse(splitter.needRecalculation());

    // After changing split factors, should need recalculation
    splitter.setSplitFactors(new double[] { 0.7, 0.3 });
    assertTrue(splitter.needRecalculation());
  }

  @Test
  void testInProcessSystem() {
    ProcessSystem process = new ProcessSystem();
    process.add(inletStream);

    Splitter splitter = new Splitter("splitter", inletStream, 2);
    splitter.setSplitFactors(new double[] { 0.6, 0.4 });
    process.add(splitter);

    Stream out0 = new Stream("out0", splitter.getSplitStream(0));
    Stream out1 = new Stream("out1", splitter.getSplitStream(1));
    process.add(out0);
    process.add(out1);

    process.run();

    double totalOut = out0.getThermoSystem().getFlowRate("kg/hr") + out1.getThermoSystem().getFlowRate("kg/hr");
    double inlet = inletStream.getThermoSystem().getFlowRate("kg/hr");
    assertEquals(inlet, totalOut, inlet * 1e-4);
  }

  @Test
  void testValidateSetup() {
    Splitter splitter = new Splitter("splitter", inletStream, 2);
    splitter.setSplitFactors(new double[] { 0.5, 0.5 });

    neqsim.util.validation.ValidationResult result = splitter.validateSetup();
    assertTrue(result.isValid());
  }

  @Test
  void testValidateSetupNoInletStream() {
    Splitter splitter = new Splitter("no-inlet-splitter");
    neqsim.util.validation.ValidationResult result = splitter.validateSetup();
    assertFalse(result.isValid());
  }
}
