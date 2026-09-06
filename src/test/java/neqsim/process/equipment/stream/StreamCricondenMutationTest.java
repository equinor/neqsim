package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression coverage for direct EOS tuning after a stream envelope has been cached. */
class StreamCricondenMutationTest extends neqsim.NeqSimTest {
  /** Builds a synthetic hydrocarbon gas with a resolved two-phase envelope. */
  private SystemInterface createFluid() {
    SystemInterface fluid = new SystemSrkEos(300.0, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.1);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return fluid;
  }

  /** Compares a warmed accessor with a new stream tracing the identical mutated EOS. */
  private void assertMutationRecalculates(SystemInterface fluid, Consumer<SystemInterface> mutation) {
    Stream cached = new Stream("cached", fluid);
    double originalTemperature = cached.CCT("K");
    double originalPressure = cached.CCB("bara");
    mutation.accept(fluid);
    Stream fresh = new Stream("fresh", fluid.clone());
    double expectedTemperature = fresh.CCT("K");
    double expectedPressure = fresh.CCB("bara");
    assertTrue(Double.isFinite(expectedTemperature) && Double.isFinite(expectedPressure));
    assertTrue(
        Math.abs(expectedTemperature - originalTemperature) > 1.0e-4
            || Math.abs(expectedPressure - originalPressure) > 1.0e-4,
        "The EOS mutation must measurably change the independently traced envelope");
    assertEquals(expectedTemperature, cached.CCT("K"), 1.0e-6,
        "CCT must match a fresh envelope after direct EOS tuning");
    assertEquals(expectedPressure, cached.CCB("bara"), 1.0e-6,
        "CCB must match a fresh envelope after direct EOS tuning");
  }

  @Test
  void changingAttractiveTermInvalidatesEnvelope() {
    assertMutationRecalculates(createFluid(), fluid -> fluid.setAttractiveTerm(5));
  }

  @Test
  void changingAttractiveParametersInvalidatesEnvelope() {
    SystemInterface fluid = createFluid();
    fluid.setAttractiveTerm(4);
    assertMutationRecalculates(fluid, system -> {
      for (int phase = 0; phase < system.getMaxNumberOfPhases(); phase++) {
        if (system.getPhase(phase) != null) {
          system.getPhase(phase).getComponent(2).getAttractiveTerm().setParameters(1, 0.5);
        }
      }
    });
  }

  @Test
  void changingCovolumeMixingRuleInvalidatesEnvelope() {
    assertMutationRecalculates(createFluid(), system -> {
      for (int phase = 0; phase < system.getMaxNumberOfPhases(); phase++) {
        if (system.getPhase(phase) instanceof PhaseEosInterface) {
          ((PhaseEosInterface) system.getPhase(phase)).getEosMixingRule().setBmixType(1);
        }
      }
    });
  }

  @Test
  void changingFifthAttractiveCoefficientInvalidatesEnvelope() {
    SystemInterface fluid = createFluid();
    fluid.setAttractiveTerm(19);
    assertMutationRecalculates(fluid, system -> {
      for (int phase = 0; phase < system.getMaxNumberOfPhases(); phase++) {
        if (system.getPhase(phase) != null) {
          assertEquals(5, system.getPhase(phase).getComponent(2).getAttractiveTerm().getNumberOfParameters());
          system.getPhase(phase).getComponent(2).getAttractiveTerm().setParameters(4, 5.0);
        }
      }
    });
  }

  @Test
  void changingLiquidPhaseInteractionInvalidatesEnvelope() {
    assertMutationRecalculates(createFluid(), system -> {
      ((PhaseEosInterface) system.getPhase(1)).getEosMixingRule().setBinaryInteractionParameter(0, 2, 0.05);
    });
  }
}
