package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.process.processmodel.ProcessSystem;

class CoupledProcessEnergySolverConfigurationTest {

  @Test
  void testMinimumIterationsCannotExceedMaximumIterations() {
    CoupledProcessEnergySolver solver = new CoupledProcessEnergySolver(new ProcessSystem());
    solver.setMaximumIterations(1);
    solver.setMinimumIterations(2);

    assertThrows(IllegalStateException.class, solver::solve);
  }
}
