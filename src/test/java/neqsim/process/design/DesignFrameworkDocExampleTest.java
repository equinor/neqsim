package neqsim.process.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.designstandards.DesignStandard;
import neqsim.process.mechanicaldesign.designstandards.StandardRegistry;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Compilation-and-run guard for the copy-paste examples in {@code docs/process/DESIGN_FRAMEWORK.md}.
 *
 * <p>
 * The code in these tests is intentionally kept identical (apart from assertions) to the runnable snippets in the
 * documentation. If the public design API changes, this test fails and the documentation must be updated in lock-step.
 * Only classes that actually exist in the source tree are used here — {@code DesignOptimizer}, {@code DesignResult},
 * {@code StandardRegistry}, and {@code StandardType}.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class DesignFrameworkDocExampleTest {

  /**
   * Build a simple single-separator process used by the documentation snippets.
   *
   * @return a run {@link ProcessSystem} containing one feed stream and one separator named {@code "HP-Separator"}
   */
  private static ProcessSystem buildProcess() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-pentane", 0.03);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    Separator separator = new Separator("HP-Separator", feed);
    separator.setDesignGasLoadFactor(0.08);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.run();
    return process;
  }

  /**
   * Mirrors the "Auto-size and read the design result" doc snippet. Verifies the fluent {@link DesignOptimizer}
   * workflow returns a populated {@link DesignResult}.
   */
  @Test
  void docExampleAutoSizeAndReadResult() {
    ProcessSystem process = buildProcess();

    DesignResult result = DesignOptimizer.forProcess(process).autoSizeEquipment(1.2).applyDefaultConstraints()
        .configureFeedRateOptimization("Feed", 5000.0, 15000.0, "kg/hr")
        .setObjective(DesignOptimizer.ObjectiveType.MAXIMIZE_PRODUCTION).optimize();

    // Explicit bounds cause optimize() to run a converged search rather than autosizing only.
    assertEquals(DesignResult.ExecutionStatus.OPTIMIZED, result.getExecutionStatus());
    assertTrue(result.isConverged());
    // The selected objective is a finite feed rate inside the configured search interval.
    assertTrue(Double.isFinite(result.getObjectiveValue()));
    assertTrue(result.getObjectiveValue() >= 5000.0);
    assertTrue(result.getObjectiveValue() <= 15000.0);
    // Equipment sizes were recorded for the separator.
    assertNotNull(result.getEquipmentSizes("HP-Separator"));
    assertTrue(result.getEquipmentSizes("HP-Separator").containsKey("diameter"));
    // Constraint-status map is available and iterable (as shown in the doc snippet).
    assertNotNull(result.getConstraintStatus());
    for (DesignResult.ConstraintStatus cs : result.getConstraintStatus().values()) {
      assertNotNull(cs.getName());
      // Utilisation is a finite fraction; satisfied flag is readable.
      assertTrue(cs.getUtilization() >= 0.0);
      cs.isSatisfied();
    }
    // Violations list is always non-null (may be empty).
    assertNotNull(result.getViolations());
    // A human-readable summary is available.
    assertNotNull(result.getSummary());
  }

  /**
   * Mirrors the "Validate an existing design" doc snippet.
   */
  @Test
  void docExampleValidateOnly() {
    ProcessSystem process = buildProcess();

    DesignResult result = DesignOptimizer.forProcess(process).validate();

    assertNotNull(result);
    // A clean single-separator screen should not report hard violations.
    assertFalse(result.hasViolations());
  }

  /**
   * Mirrors the "Select a mechanical-design standard" doc snippet. Verifies the real {@link StandardRegistry} /
   * {@link StandardType} selection API (NOT the not-yet-implemented {@code StandardSelection.strict(...)} shown
   * elsewhere).
   */
  @Test
  void docExampleStandardSelection() {
    ProcessSystem process = buildProcess();
    Separator sep = (Separator) process.getUnit("HP-Separator");

    // Discover the standards that apply to a separator.
    List<StandardType> applicable = StandardRegistry.getApplicableStandards("Separator");
    assertFalse(applicable.isEmpty());

    // Create a concrete design standard bound to the separator's mechanical-design context.
    DesignStandard vesselStandard = StandardRegistry.createStandard(StandardType.ASME_VIII_DIV1,
        sep.getMechanicalDesign());
    assertNotNull(vesselStandard);
    assertTrue(vesselStandard.getStandardName().startsWith("ASME-VIII-Div1"));
  }
}
