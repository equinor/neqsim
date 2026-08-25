package neqsim.thermo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseSolidHelmholtzEos;
import neqsim.thermo.system.SystemArgonSolidHelmholtzEos;
import neqsim.thermo.system.SystemLeachmanEos;
import neqsim.thermo.util.solid.SolidHelmholtzState;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.saturationops.FreezingPointResult;

/** Executes the examples in docs/thermo/solid_helmholtz_models.md. */
class SolidHelmholtzDocumentationTest {

  /** Execute the documented calibrated solid-argon state workflow. */
  @Test
  void argonDirectStateExample() {
    SystemArgonSolidHelmholtzEos solid = new SystemArgonSolidHelmholtzEos(70.0, 10.0);
    solid.init(3);

    PhaseSolidHelmholtzEos phase = (PhaseSolidHelmholtzEos) solid.getPhase(0);
    SolidHelmholtzState state = phase.getSolidState();

    double molarVolume = state.getMolarVolume();
    double heatCapacityCp = state.getHeatCapacityCp();

    assertEquals(2.39546e-5, molarVolume, 1.0e-10);
    assertEquals(30.2861, heatCapacityCp, 1.0e-3);
    assertTrue(state.getHeatCapacityCp() > state.getHeatCapacityCv());
  }

  /** Execute the documented structured para-hydrogen freezing-point workflow. */
  @Test
  void paraHydrogenFreezingPointExample() {
    SystemLeachmanEos hydrogen = new SystemLeachmanEos(13.6, 0.07042, "para-hydrogen", true);
    hydrogen.setSolidPhaseCheck("para-hydrogen");

    ThermodynamicOperations operations = new ThermodynamicOperations(hydrogen);
    FreezingPointResult result = operations.freezingPointTemperatureFlashResult();

    if (!result.isConverged()) {
      throw new IllegalStateException(result.getFailureReason());
    }
    double freezingTemperatureK = result.getTemperature("K");
    double equilibriumResidual = result.getResidual();

    assertEquals(13.8033, freezingTemperatureK, 1.0e-4);
    assertTrue(Math.abs(equilibriumResidual) < 1.0e-10);
    assertEquals(freezingTemperatureK, hydrogen.getTemperature(), 0.0);
    assertEquals("para-hydrogen", result.getComponentName());
  }
}
