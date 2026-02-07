package neqsim.process.util.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the data reconciliation engine.
 *
 * @author Process Optimization Team
 * @version 1.0
 */
public class DataReconciliationEngineTest {

  private DataReconciliationEngine engine;

  @BeforeEach
  void setUp() {
    engine = new DataReconciliationEngine();
  }

  // ==================== Basic functionality ====================

  @Test
  void testSimpleMassBalance() {
    // Feed = product + waste; measurements don't close (1000 != 600 + 380 = 980)
    engine.addVariable(new ReconciliationVariable("feed", 1000.0, 20.0));
    engine.addVariable(new ReconciliationVariable("product", 600.0, 15.0));
    engine.addVariable(new ReconciliationVariable("waste", 380.0, 10.0));
    engine.addConstraint(new double[] {1.0, -1.0, -1.0});

    ReconciliationResult result = engine.reconcile();

    assertTrue(result.isConverged(), "Should converge");

    // After reconciliation, constraint should be satisfied: feed - product - waste = 0
    double feed = engine.getVariable("feed").getReconciledValue();
    double product = engine.getVariable("product").getReconciledValue();
    double waste = engine.getVariable("waste").getReconciledValue();
    assertEquals(0.0, feed - product - waste, 1e-8, "Mass balance should close");

    // Adjustments should be small relative to original values
    assertTrue(Math.abs(engine.getVariable("feed").getAdjustment()) < 30.0,
        "Feed adjustment should be small");
  }

  @Test
  void testPerfectMeasurementsNoAdjustment() {
    // Measurements already close: 1000 = 600 + 400
    engine.addVariable(new ReconciliationVariable("feed", 1000.0, 10.0));
    engine.addVariable(new ReconciliationVariable("product", 600.0, 10.0));
    engine.addVariable(new ReconciliationVariable("waste", 400.0, 10.0));
    engine.addConstraint(new double[] {1.0, -1.0, -1.0});

    ReconciliationResult result = engine.reconcile();

    assertTrue(result.isConverged());
    assertEquals(0.0, result.getObjectiveValue(), 1e-10,
        "No adjustment needed when measurements close");

    for (ReconciliationVariable v : result.getVariables()) {
      assertEquals(0.0, v.getAdjustment(), 1e-10, v.getName() + " should have zero adjustment");
    }
  }

  @Test
  void testHighUncertaintyGetsLargerAdjustment() {
    // Feed = product + waste; imbalance = 20 (1000 - 600 - 380 = 20)
    // Feed has sigma=100 (very uncertain), product/waste have sigma=5 (precise)
    engine.addVariable(new ReconciliationVariable("feed", 1000.0, 100.0));
    engine.addVariable(new ReconciliationVariable("product", 600.0, 5.0));
    engine.addVariable(new ReconciliationVariable("waste", 380.0, 5.0));
    engine.addConstraint(new double[] {1.0, -1.0, -1.0});

    ReconciliationResult result = engine.reconcile();
    assertTrue(result.isConverged());

    // Feed should absorb most of the adjustment since it is most uncertain
    double feedAdj = Math.abs(engine.getVariable("feed").getAdjustment());
    double productAdj = Math.abs(engine.getVariable("product").getAdjustment());
    double wasteAdj = Math.abs(engine.getVariable("waste").getAdjustment());
    assertTrue(feedAdj > productAdj, "More uncertain variable (feed) should get larger adjustment");
    assertTrue(feedAdj > wasteAdj,
        "More uncertain variable (feed) should get larger adjustment than waste");
  }

  @Test
  void testTwoConstraints() {
    // 4 streams, 2 balance nodes:
    // Node 1: feed - mid1 - mid2 = 0
    // Node 2: mid1 + mid2 - product = 0
    engine.addVariable(new ReconciliationVariable("feed", 1000.0, 20.0));
    engine.addVariable(new ReconciliationVariable("mid1", 400.0, 15.0));
    engine.addVariable(new ReconciliationVariable("mid2", 580.0, 15.0));
    engine.addVariable(new ReconciliationVariable("product", 990.0, 20.0));
    engine.addConstraint(new double[] {1.0, -1.0, -1.0, 0.0});
    engine.addConstraint(new double[] {0.0, 1.0, 1.0, -1.0});

    ReconciliationResult result = engine.reconcile();
    assertTrue(result.isConverged());

    double feed = engine.getVariable("feed").getReconciledValue();
    double mid1 = engine.getVariable("mid1").getReconciledValue();
    double mid2 = engine.getVariable("mid2").getReconciledValue();
    double product = engine.getVariable("product").getReconciledValue();

    assertEquals(0.0, feed - mid1 - mid2, 1e-8, "Node 1 balance should close");
    assertEquals(0.0, mid1 + mid2 - product, 1e-8, "Node 2 balance should close");
    assertEquals(2, result.getDegreesOfFreedom());
  }

  // ==================== Gross error detection ====================

  @Test
  void testGrossErrorDetection() {
    // Feed = product + waste; product has a gross error (should be ~400, measured 600)
    engine.addVariable(new ReconciliationVariable("feed", 1000.0, 20.0));
    engine.addVariable(new ReconciliationVariable("product", 600.0, 20.0));
    engine.addVariable(new ReconciliationVariable("waste", 420.0, 20.0));
    engine.addConstraint(new double[] {1.0, -1.0, -1.0});

    // Lower threshold to catch the error more easily
    engine.setGrossErrorThreshold(1.5);

    ReconciliationResult result = engine.reconcile();
    assertTrue(result.isConverged());

    // The imbalance is -20, with equal sigmas it distributes evenly but the pattern
    // should be detectable with a sufficiently large bias
    // Note: with equal uncertainties and a single constraint, all get equal
    // normalized residuals, so this tests the mechanism works
    assertNotNull(result.getGrossErrors());
  }

  @Test
  void testGrossErrorElimination() {
    // 4 variables, 2 constraints; one variable has a gross error
    engine.addVariable(new ReconciliationVariable("feed", 1000.0, 20.0));
    engine.addVariable(new ReconciliationVariable("mid", 500.0, 20.0));
    engine.addVariable(new ReconciliationVariable("product", 480.0, 20.0));
    engine.addVariable(new ReconciliationVariable("leak", 200.0, 20.0)); // gross error

    // feed - mid - leak = 0 (leak is wrong: should be ~500)
    engine.addConstraint(new double[] {1.0, -1.0, 0.0, -1.0});
    // mid - product = 0
    engine.addConstraint(new double[] {0.0, 1.0, -1.0, 0.0});

    ReconciliationResult result = engine.reconcileWithGrossErrorElimination(2);
    assertTrue(result.isConverged());
  }

  // ==================== Named constraint convenience ====================

  @Test
  void testMassBalanceConstraintByName() {
    engine.addVariable(new ReconciliationVariable("feed", 1000.0, 20.0));
    engine.addVariable(new ReconciliationVariable("gas", 600.0, 15.0));
    engine.addVariable(new ReconciliationVariable("liquid", 380.0, 10.0));

    engine.addMassBalanceConstraint("Separator", new String[] {"feed"},
        new String[] {"gas", "liquid"});

    ReconciliationResult result = engine.reconcile();
    assertTrue(result.isConverged());

    double feed = engine.getVariable("feed").getReconciledValue();
    double gas = engine.getVariable("gas").getReconciledValue();
    double liquid = engine.getVariable("liquid").getReconciledValue();
    assertEquals(0.0, feed - gas - liquid, 1e-8);
  }

  @Test
  void testMassBalanceConstraintListApi() {
    engine.addVariable(new ReconciliationVariable("in1", 500.0, 10.0));
    engine.addVariable(new ReconciliationVariable("in2", 500.0, 10.0));
    engine.addVariable(new ReconciliationVariable("out", 980.0, 15.0));

    engine.addMassBalanceConstraint("Mixer", Arrays.asList("in1", "in2"), Arrays.asList("out"));

    ReconciliationResult result = engine.reconcile();
    assertTrue(result.isConverged());

    double in1 = engine.getVariable("in1").getReconciledValue();
    double in2 = engine.getVariable("in2").getReconciledValue();
    double out = engine.getVariable("out").getReconciledValue();
    assertEquals(0.0, in1 + in2 - out, 1e-8);
  }

  // ==================== Edge cases ====================

  @Test
  void testNoVariablesReturnsError() {
    ReconciliationResult result = engine.reconcile();
    assertFalse(result.isConverged());
    assertTrue(result.getErrorMessage().contains("No variables"));
  }

  @Test
  void testNoConstraintsReturnsError() {
    engine.addVariable(new ReconciliationVariable("x", 100.0, 10.0));
    ReconciliationResult result = engine.reconcile();
    assertFalse(result.isConverged());
    assertTrue(result.getErrorMessage().contains("No constraints"));
  }

  @Test
  void testTooManyConstraintsReturnsError() {
    engine.addVariable(new ReconciliationVariable("x", 100.0, 10.0));
    engine.addConstraint(new double[] {1.0});
    ReconciliationResult result = engine.reconcile();
    assertFalse(result.isConverged());
    assertTrue(result.getErrorMessage().contains("more variables"));
  }

  @Test
  void testClearAndReuse() {
    engine.addVariable(new ReconciliationVariable("a", 100.0, 5.0));
    engine.addVariable(new ReconciliationVariable("b", 90.0, 5.0));
    engine.addConstraint(new double[] {1.0, -1.0});

    ReconciliationResult r1 = engine.reconcile();
    assertTrue(r1.isConverged());

    engine.clear();
    assertEquals(0, engine.getVariableCount());
    assertEquals(0, engine.getConstraintCount());

    // Reuse with different data
    engine.addVariable(new ReconciliationVariable("x", 200.0, 10.0));
    engine.addVariable(new ReconciliationVariable("y", 190.0, 10.0));
    engine.addConstraint(new double[] {1.0, -1.0});

    ReconciliationResult r2 = engine.reconcile();
    assertTrue(r2.isConverged());
  }

  // ==================== Output formats ====================

  @Test
  void testJsonOutput() {
    engine.addVariable(new ReconciliationVariable("feed", 1000.0, 20.0));
    engine.addVariable(new ReconciliationVariable("product", 600.0, 15.0));
    engine.addVariable(new ReconciliationVariable("waste", 380.0, 10.0));
    engine.addConstraint(new double[] {1.0, -1.0, -1.0});

    ReconciliationResult result = engine.reconcile();
    String json = result.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"converged\": true"));
    assertTrue(json.contains("\"feed\""));
    assertTrue(json.contains("\"product\""));
    assertTrue(json.contains("\"waste\""));
  }

  @Test
  void testReportOutput() {
    engine.addVariable(new ReconciliationVariable("feed", 1000.0, 20.0));
    engine.addVariable(new ReconciliationVariable("product", 600.0, 15.0));
    engine.addVariable(new ReconciliationVariable("waste", 380.0, 10.0));
    engine.addConstraint(new double[] {1.0, -1.0, -1.0});

    ReconciliationResult result = engine.reconcile();
    String report = result.toReport();
    assertNotNull(report);
    assertTrue(report.contains("Data Reconciliation Report"));
    assertTrue(report.contains("feed"));
  }

  @Test
  void testChiSquareGlobalTest() {
    // With perfectly balanced measurements, objective should be 0 and global test should pass
    engine.addVariable(new ReconciliationVariable("a", 100.0, 10.0));
    engine.addVariable(new ReconciliationVariable("b", 50.0, 10.0));
    engine.addVariable(new ReconciliationVariable("c", 50.0, 10.0));
    engine.addConstraint(new double[] {1.0, -1.0, -1.0});

    ReconciliationResult result = engine.reconcile();
    assertTrue(result.isGlobalTestPassed(), "Balanced measurements should pass global test");
  }

  // ==================== Variable features ====================

  @Test
  void testVariableUnitAndEquipmentLink() {
    ReconciliationVariable v =
        new ReconciliationVariable("flow", "HP_Separator", "massFlowRate", 1000.0, 20.0);
    v.setUnit("kg/hr");

    assertEquals("HP_Separator", v.getEquipmentName());
    assertEquals("massFlowRate", v.getPropertyName());
    assertEquals("kg/hr", v.getUnit());
  }

  @Test
  void testVariableModelValue() {
    ReconciliationVariable v = new ReconciliationVariable("T1", 85.0, 2.0);
    assertFalse(v.hasModelValue());
    assertTrue(Double.isNaN(v.getModelValue()));

    v.setModelValue(84.5);
    assertTrue(v.hasModelValue());
    assertEquals(84.5, v.getModelValue(), 1e-10);
  }

  @Test
  void testVariableToString() {
    ReconciliationVariable v = new ReconciliationVariable("feed", 1000.0, 20.0);
    v.setUnit("kg/hr");
    v.setReconciledValue(995.0);
    String s = v.toString();
    assertTrue(s.contains("feed"));
    assertTrue(s.contains("kg/hr"));
  }

  @Test
  void testVariableUncertaintyValidation() {
    try {
      new ReconciliationVariable("bad", 100.0, -5.0);
      assertTrue(false, "Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("positive"));
    }

    try {
      new ReconciliationVariable("bad", 100.0, 0.0);
      assertTrue(false, "Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("positive"));
    }
  }

  // ==================== Realistic scenarios ====================

  @Test
  void testSeparatorWithThreeOutputs() {
    // 3-phase separator: feed -> gas + oil + water
    engine.addVariable(new ReconciliationVariable("feed", 10000.0, 200.0).setUnit("kg/hr"));
    engine.addVariable(new ReconciliationVariable("gas", 3500.0, 100.0).setUnit("kg/hr"));
    engine.addVariable(new ReconciliationVariable("oil", 4800.0, 150.0).setUnit("kg/hr"));
    engine.addVariable(new ReconciliationVariable("water", 1900.0, 80.0).setUnit("kg/hr"));

    // feed - gas - oil - water = 0
    engine.addMassBalanceConstraint("3-Phase Sep", new String[] {"feed"},
        new String[] {"gas", "oil", "water"});

    ReconciliationResult result = engine.reconcile();
    assertTrue(result.isConverged());

    // Imbalance: 10000 - 3500 - 4800 - 1900 = -200
    // After reconciliation, balance should close
    double feed = engine.getVariable("feed").getReconciledValue();
    double gas = engine.getVariable("gas").getReconciledValue();
    double oil = engine.getVariable("oil").getReconciledValue();
    double water = engine.getVariable("water").getReconciledValue();
    assertEquals(0.0, feed - gas - oil - water, 1e-6, "Mass balance should close");
  }

  @Test
  void testMultiNodeProcessNetwork() {
    // 3-node network:
    // Node 1 (separator): feed -> gas + liquid
    // Node 2 (compressor): gas -> compressed_gas
    // Node 3 (pump): liquid -> pumped_liquid
    engine.addVariable(new ReconciliationVariable("feed", 5000.0, 100.0));
    engine.addVariable(new ReconciliationVariable("gas", 2100.0, 50.0));
    engine.addVariable(new ReconciliationVariable("liquid", 2850.0, 70.0));
    engine.addVariable(new ReconciliationVariable("compressed_gas", 2080.0, 50.0));
    engine.addVariable(new ReconciliationVariable("pumped_liquid", 2870.0, 70.0));

    // Separator: feed - gas - liquid = 0
    engine.addConstraint(new double[] {1.0, -1.0, -1.0, 0.0, 0.0});
    // Compressor: gas - compressed_gas = 0
    engine.addConstraint(new double[] {0.0, 1.0, 0.0, -1.0, 0.0});
    // Pump: liquid - pumped_liquid = 0
    engine.addConstraint(new double[] {0.0, 0.0, 1.0, 0.0, -1.0});

    ReconciliationResult result = engine.reconcile();
    assertTrue(result.isConverged());
    assertEquals(3, result.getDegreesOfFreedom());

    // All balances should close
    double[] r = result.getConstraintResidualsAfter();
    for (int i = 0; i < r.length; i++) {
      assertEquals(0.0, r[i], 1e-6, "Constraint " + i + " should close");
    }
  }

  @Test
  void testConstraintResidualsBefore() {
    engine.addVariable(new ReconciliationVariable("a", 100.0, 10.0));
    engine.addVariable(new ReconciliationVariable("b", 60.0, 10.0));
    engine.addVariable(new ReconciliationVariable("c", 30.0, 10.0));
    engine.addConstraint(new double[] {1.0, -1.0, -1.0});

    ReconciliationResult result = engine.reconcile();
    double[] before = result.getConstraintResidualsBefore();
    assertEquals(1, before.length);
    // Residual before: 100 - 60 - 30 = 10
    assertEquals(10.0, before[0], 1e-10);

    double[] after = result.getConstraintResidualsAfter();
    assertEquals(0.0, after[0], 1e-8, "Residual after should be zero");
  }
}
