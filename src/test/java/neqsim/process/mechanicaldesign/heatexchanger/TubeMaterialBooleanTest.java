package neqsim.process.mechanicaldesign.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Preserve bundled true and false NACE material flags through the calculator.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TubeMaterialBooleanTest {
  /** A shell forging must use its named row instead of the previous carbon-steel stress. */
  @Test
  void shellForgingLoadsNamedProperties() {
    ShellAndTubeDesignCalculator calculator = new ShellAndTubeDesignCalculator();
    calculator.setShellMaterialGrade("SA-182-F316");
    calculator.calculate();
    Map<?, ?> shell = (Map<?, ?>) calculator.toMap().get("shell");
    assertEquals(115.0, ((Number) shell.get("allowableStress_MPa")).doubleValue(), 1.0e-12);
    assertTrue(shell.get("material").toString().contains("SA-182-F316"));
  }

  /** Missing material data must invalidate a previous calculation and permit explicit recovery. */
  @Test
  void unknownGradeCannotReusePreviousStress() {
    ShellAndTubeDesignCalculator calculator = new ShellAndTubeDesignCalculator();
    calculator.calculate();
    calculator.setShellMaterialGrade("unknown-grade");
    assertThrows(IllegalArgumentException.class, () -> calculator.calculate());
    Map<?, ?> shell = (Map<?, ?>) calculator.toMap().get("shell");
    assertTrue(Double.isNaN(((Number) shell.get("allowableStress_MPa")).doubleValue()));
    assertTrue(Double.isNaN(calculator.getMawpShellSide()));
    assertTrue(Double.isNaN(calculator.getHydroTestPressureShell()));
    calculator.setShellMaterialGrade("SA-516-70");
    calculator.calculate();
    shell = (Map<?, ?>) calculator.toMap().get("shell");
    assertEquals(138.0, ((Number) shell.get("allowableStress_MPa")).doubleValue(), 1.0e-12);
  }

  /** A failed tube reload must not export the old pressure rating, weight or cost. */
  @Test
  void unknownTubeGradeInvalidatesAllDerivedResults() {
    ShellAndTubeDesignCalculator calculator = new ShellAndTubeDesignCalculator();
    calculator.calculate();
    assertTrue(calculator.getMawpTubeSide() > 0.0);
    calculator.setTubeMaterialGrade("unknown-tube");
    assertTrue(Double.isNaN(calculator.getMawpTubeSide()));
    assertThrows(IllegalArgumentException.class, () -> calculator.calculate());
    assertTrue(Double.isNaN(calculator.getHydroTestPressureTube()));
    assertTrue(Double.isNaN(calculator.getTotalDryWeight()));
    assertTrue(Double.isNaN(calculator.getTotalCost()));
    calculator.setTubeMaterialGrade("SA-179");
    calculator.calculate();
    assertTrue(calculator.getMawpTubeSide() > 0.0);
    assertTrue(calculator.getTotalDryWeight() > 0.0);
  }

  /** Reloading materials must preserve both boolean values for shell and tube. */
  @Test
  void readsBooleanComplianceFlags() {
    ShellAndTubeDesignCalculator calculator = new ShellAndTubeDesignCalculator();
    calculator.setShellMaterialGrade("SA-213-TP316L");
    calculator.setTubeMaterialGrade("SA-213-TP316L");
    calculator.calculate();
    assertTrue(calculator.isShellNACECompliant());
    assertTrue(calculator.isTubeNACECompliant());
    calculator.setShellMaterialGrade("SA-179");
    calculator.setTubeMaterialGrade("SA-179");
    calculator.calculate();
    assertFalse(calculator.isShellNACECompliant());
    assertFalse(calculator.isTubeNACECompliant());
  }
}
