package neqsim.fluidmechanics.flowsolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem;

/** Tests the typed public API for conservative species advection. */
class SpeciesAdvectionSchemeTest {
  @Test
  void pipeFlowSystemKeepsFirstOrderCompatibilityDefault() {
    PipeFlowSystem pipe = new PipeFlowSystem();

    assertEquals(SpeciesAdvectionScheme.FIRST_ORDER_IMPLICIT, pipe.getSpeciesAdvectionScheme());
    assertFalse(pipe.getSpeciesAdvectionScheme().isHighResolution());
    assertEquals(1, pipe.getSpeciesAdvectionScheme().getOrder());
  }

  @Test
  void pipeFlowSystemAcceptsBoundedHighResolutionScheme() {
    PipeFlowSystem pipe = new PipeFlowSystem();
    pipe.setSpeciesAdvectionScheme(SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2);

    assertEquals(SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2, pipe.getSpeciesAdvectionScheme());
    assertTrue(pipe.getSpeciesAdvectionScheme().isHighResolution());
    assertEquals(2, pipe.getSpeciesAdvectionScheme().getOrder());
    assertEquals(0.45, pipe.getSpeciesAdvectionScheme().getMaximumCourantNumber(), 0.0);
    assertThrows(IllegalArgumentException.class, () -> pipe.setSpeciesAdvectionScheme(null));
  }
}
