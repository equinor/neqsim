package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;

/**
 * Regression tests for the boiling-point based TBP fraction entry points.
 *
 * <p>
 * These cover the failure mode where the specific gravity was obtained by bisecting the selected TBP model's own
 * <code>calcTB</code> against density. For the Pedersen models <code>calcTB</code> carries no density dependence below
 * 540 g/mol, so the inverse problem had no solution and the solver silently returned a constant 1.0 g/cm3 for every
 * boiling point, making {@code addTBPfraction2} insensitive to its boiling point argument.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class TBPBoilingPointInversionTest {
  /** Molar mass of a C10 cut in kg/mol. */
  private static final double M_C10 = 0.1293;

  private SystemInterface testSystem;

  /** Create a fresh system with the default TBP model before each test. */
  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemSrkEos(298.15, 10.0);
    testSystem.addComponent("methane", 0.9);
  }

  /** The specific gravity must increase monotonically with boiling point at fixed molar mass. */
  @Test
  void testDensityRespondsMonotonicallyToBoilingPoint() {
    double[] boilingPoints = new double[] { 400.0, 420.0, 447.0, 470.0 };
    double previous = 0.0;
    for (int i = 0; i < boilingPoints.length; i++) {
      double density = testSystem.calculateDensityFromBoilingPoint(M_C10, boilingPoints[i]);
      assertTrue(density > previous, "Specific gravity must increase with boiling point, but " + density + " followed "
          + previous + " at Tb = " + boilingPoints[i] + " K");
      previous = density;
    }
    assertTrue(previous - testSystem.calculateDensityFromBoilingPoint(M_C10, 400.0) > 0.2,
        "Specific gravity must vary appreciably over a 70 K boiling point range");
  }

  /** A boiling point that cannot correspond to the given molar mass must be rejected, not silently accepted. */
  @Test
  void testNonPhysicalBoilingPointThrows() {
    assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        testSystem.calculateDensityFromBoilingPoint(M_C10, 10.0);
      }
    }, "A 10 K boiling point for a C10 cut must be rejected");

    assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        testSystem.calculateDensityFromBoilingPoint(M_C10, 1000.0);
      }
    }, "A 1000 K boiling point for a C10 cut must be rejected");
  }

  /** Non-positive arguments must be rejected. */
  @Test
  void testNonPositiveArgumentsThrow() {
    assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        testSystem.calculateDensityFromBoilingPoint(-1.0, 447.0);
      }
    });
    assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        testSystem.calculateDensityFromBoilingPoint(M_C10, -1.0);
      }
    });
  }

  /**
   * The Watson K route must reproduce the specific gravity of pure components to better than 1 %, which is the reason
   * it is preferred when the PNA character of the cut is known.
   */
  @Test
  void testWatsonKRouteMatchesPureComponents() {
    // name, boiling point [K], Watson K [-], reference specific gravity [g/cm3]
    double[][] cases = new double[][] { { 371.58, 12.70, 0.6882 }, { 447.30, 12.66, 0.7342 }, { 353.25, 9.74, 0.8844 },
        { 383.78, 10.13, 0.8719 } };
    for (int i = 0; i < cases.length; i++) {
      double density = testSystem.calculateDensityFromBoilingPointAndWatsonK(cases[i][0], cases[i][1]);
      assertEquals(cases[i][2], density, 0.01 * cases[i][2],
          "Watson K route must be within 1 % for boiling point " + cases[i][0] + " K");
    }
  }

  /** addTBPfraction2 must produce different critical properties for different boiling points. */
  @Test
  void testAddTBPfraction2IsSensitiveToBoilingPoint() {
    SystemInterface cold = new neqsim.thermo.system.SystemSrkEos(298.15, 10.0);
    cold.addComponent("methane", 0.9);
    cold.addTBPfraction2("C10", 0.1, M_C10, 410.0);
    cold.setMixingRule("classic");

    SystemInterface hot = new neqsim.thermo.system.SystemSrkEos(298.15, 10.0);
    hot.addComponent("methane", 0.9);
    hot.addTBPfraction2("C10", 0.1, M_C10, 470.0);
    hot.setMixingRule("classic");

    double tcCold = cold.getComponent("C10_PC").getTC();
    double tcHot = hot.getComponent("C10_PC").getTC();
    assertTrue(tcHot - tcCold > 5.0,
        "Critical temperature must respond to boiling point, but got " + tcCold + " K and " + tcHot + " K");
  }

  /** addTBPfraction4 must not leave its boiling point on the shared TBP model. */
  @Test
  void testAddTBPfraction4DoesNotLeakBoilingPoint() {
    testSystem.addTBPfraction4("X", 0.1, M_C10, 0.734, 560.0);
    assertEquals(0.0, testSystem.getCharacterization().getTBPModel().getBoilingPoint(), 1e-12,
        "Boiling point must be cleared after the call so later fractions are unaffected");

    double correlated = testSystem.getCharacterization().getTBPModel().calcTB(200.0, 0.80);
    assertTrue(Math.abs(correlated - 560.0) > 1.0,
        "calcTB must return the correlated value once the pinned boiling point is cleared, got " + correlated);
  }

  /** addTBPfraction3 must reject a boiling point that the selected model cannot reach. */
  @Test
  void testUnattainableBoilingPointForMolarMassSolveThrows() {
    assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        testSystem.calculateMolarMassFromDensityAndBoilingPoint(0.734, 120.0);
      }
    }, "A 120 K boiling point is not attainable and must be rejected");
  }

  /** The molar mass solve must still reproduce a known petroleum fraction. */
  @Test
  void testMolarMassSolveRemainsAccurate() {
    testSystem.getCharacterization().setTBPModel("PedersenPR2");
    double molarMass = testSystem.calculateMolarMassFromDensityAndBoilingPoint(0.73, 447.15);
    assertTrue(molarMass > 0.13 && molarMass < 0.15,
        "Molar mass for an nC10 cut should be near 0.142 kg/mol, got " + molarMass);
  }

  /**
   * Verifies the examples printed in docs/thermo/fluid_creation_guide.md section 10.1.1, so the documentation cannot
   * drift away from the API.
   */
  @Test
  void testFluidCreationGuideBoilingPointExamples() {
    SystemInterface oil = new neqsim.thermo.system.SystemSrkEos(350.0, 100.0);
    oil.addComponent("methane", 0.9);

    oil.addTBPfraction2("C10a", 0.02, 0.142, 447.3);
    oil.addTBPfraction3("C10b", 0.02, 0.734, 447.3);

    double sg = oil.calculateDensityFromBoilingPointAndWatsonK(447.3, 12.66);
    assertEquals(0.7342, sg, 0.01, "Watson K route should reproduce the nC10 specific gravity");
    oil.addTBPfraction("C10c", 0.02, 0.142, sg);
    oil.setMixingRule("classic");

    assertTrue(oil.getComponent("C10a_PC").getTC() > 0.0);
    assertTrue(oil.getComponent("C10b_PC").getTC() > 0.0);
    assertTrue(oil.getComponent("C10c_PC").getTC() > 0.0);

    assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        testSystem.addTBPfraction2("bad", 0.02, 0.129, 560.0);
      }
    }, "a 129 g/mol cut cannot boil at 560 K and must be rejected");
  }

  /** Verifies the corrected TBP example values used in the characterization and workflow guides. */
  @Test
  void testCharacterizationGuideExamples() {
    SystemInterface oil = new neqsim.thermo.system.SystemSrkEos(323.15, 150.0);
    oil.addComponent("nitrogen", 0.01);
    oil.addComponent("methane", 0.60);
    oil.addTBPfraction("C7", 0.08, 0.096, 0.738);
    oil.addTBPfraction("C10", 0.10, 0.134, 0.792);
    oil.addPlusFraction("C20", 0.21, 0.275, 0.870);
    oil.setMixingRule(2);

    assertEquals(0.096, oil.getComponent("C7_PC").getMolarMass(), 1e-9);
    assertEquals(0.134, oil.getComponent("C10_PC").getMolarMass(), 1e-9);
    assertTrue(oil.getComponent("C20_PC").getTC() > oil.getComponent("C7_PC").getTC(),
        "the heavier cut must have the higher critical temperature");
  }
}
