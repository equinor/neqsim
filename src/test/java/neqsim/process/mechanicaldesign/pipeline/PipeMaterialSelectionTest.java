package neqsim.process.mechanicaldesign.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for rejected pipeline material selections and topside compatibility.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PipeMaterialSelectionTest extends neqsim.NeqSimTest {
  /** Rejected material identities block strength access, sizing and result publication until a supported selection. */
  @Test
  void unknownGradeBlocksSizingUntilSupportedGradeSelected() {
    PipeMechanicalDesignCalculator calc = new PipeMechanicalDesignCalculator();
    calc.setMaterialGrade("L360");
    calc.setDesignPressure(10.0);
    double originalThickness = calc.calculateMinimumWallThickness();
    assertThrows(IllegalArgumentException.class, () -> calc.setMaterialGrade("X52-typo"));
    assertEquals("X52-TYPO", calc.getMaterialGrade());
    assertThrows(IllegalArgumentException.class, calc::getSmys);
    assertThrows(IllegalArgumentException.class, calc::getSmts);
    assertThrows(IllegalArgumentException.class, calc::calculateMinimumWallThickness);
    assertThrows(IllegalArgumentException.class, calc::calculateMAOP);
    assertThrows(IllegalArgumentException.class, calc::getMinimumWallThickness);
    assertThrows(IllegalArgumentException.class, calc::getMaop);
    assertThrows(IllegalArgumentException.class, calc::isDesignSafe);
    assertThrows(IllegalArgumentException.class, calc::calculateSafetyMargin);
    assertThrows(IllegalArgumentException.class, () -> calc.calculateExpansionLoopLength(50, "U-loop"));
    assertThrows(IllegalArgumentException.class, calc::calculateCollapsePressure);
    assertThrows(IllegalArgumentException.class, calc::calculatePropagationBucklingPressure);
    assertThrows(IllegalArgumentException.class, calc::toJson);
    assertThrows(IllegalArgumentException.class, calc::generateDesignReport);
    calc.setMaterialGrade(" x52 ");
    assertEquals(359.0, calc.getSmys(), 1e-12);
    assertEquals(455.0, calc.getSmts(), 1e-12);
    assertEquals(originalThickness, calc.calculateMinimumWallThickness(), 1e-12);
  }

  /** Empty and unsupported base-calculator identities cannot be revived by strength setters. */
  @Test
  void invalidNamesRequireAnExplicitSupportedReselection() {
    PipeMechanicalDesignCalculator calc = new PipeMechanicalDesignCalculator();
    for (String grade : new String[] {null, "  ", "A106-B", "X999"}) {
      assertThrows(IllegalArgumentException.class, () -> calc.setMaterialGrade(grade));
      calc.setSmys(359.0);
      calc.setSmts(455.0);
      assertThrows(IllegalArgumentException.class, calc::getSmys);
      assertThrows(IllegalArgumentException.class, calc::toMap);
      calc.setMaterialGrade("X52");
      assertEquals(359.0, calc.getSmys(), 1e-12);
    }
  }

  /** A missing database row must report rejection rather than silently succeeding. */
  @Test
  void databaseFallbackRejectsUnknownGrade() {
    PipeMechanicalDesignCalculator calc = new PipeMechanicalDesignCalculator();
    assertThrows(IllegalArgumentException.class,
        () -> new PipelineMechanicalDesignDataSource().loadIntoCalculator("unknown-grade", "default", calc));
    assertThrows(IllegalArgumentException.class, calc::calculateMinimumWallThickness);
  }

  /** The deprecated loader must not bypass material selection validation. */
  @Test
  @SuppressWarnings("deprecation")
  void deprecatedDatabaseLoaderRejectsUnknownGrade() {
    PipeMechanicalDesignCalculator calc = new PipeMechanicalDesignCalculator();
    assertThrows(IllegalArgumentException.class, () -> calc.loadMaterialFromDatabase("unknown-grade"));
    assertThrows(IllegalArgumentException.class, calc::calculateMinimumWallThickness);
    calc.loadMaterialFromDatabase("l360");
    assertEquals("X52", calc.getMaterialGrade());
    assertEquals(52000.0 * 0.00689476, calc.getSmys(), 1e-12);
  }

  /** Known topside table selections still dispatch to their own allowable-stress rows. */
  @Test
  void topsideGradesRemainSupportedWhileUnknownGradesAreRejected() {
    TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();
    calc.setDesignTemperature(20.0);
    String[] grades = {"A106-B", "A106-C", "A333-6", "A312-TP304", "A312-TP316", "A312-TP316L", "A312-TP321",
        "A312-TP347", "A790-S31803", "A790-S32750"};
    double[] expected = {138.0, 159.0, 138.0, 138.0, 138.0, 115.0, 138.0, 138.0, 207.0, 241.0};
    for (int i = 0; i < grades.length; i++) {
      calc.setMaterialGrade(grades[i].toLowerCase(java.util.Locale.ROOT));
      assertEquals(grades[i], calc.getMaterialGrade());
      assertEquals(expected[i], calc.calculateAllowableStress(), 1e-12);
    }
    assertThrows(IllegalArgumentException.class, () -> calc.setMaterialGrade("A106-typo"));
    assertThrows(IllegalArgumentException.class, calc::calculateAllowableStress);
    assertThrows(IllegalArgumentException.class, calc::getAllowableStress);
    assertThrows(IllegalArgumentException.class, calc::isStressCheckPassed);
    assertThrows(IllegalArgumentException.class, () -> calc.calculateSustainedStress(2.0));
    assertThrows(IllegalArgumentException.class, () -> calc.calculateThermalExpansionStress(10.0));
    assertThrows(IllegalArgumentException.class, () -> calc.calculateOccasionalStress(100.0, 2.0));
    assertThrows(IllegalArgumentException.class, calc::toMap);
    calc.setMaterialGrade("A106-C");
    assertEquals(159.0, calc.calculateAllowableStress(), 1e-12);
  }
}
