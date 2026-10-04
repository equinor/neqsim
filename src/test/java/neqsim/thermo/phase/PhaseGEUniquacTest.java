package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class PhaseGEUniquacTest {
  @Test
  void rejectsUnsupportedStandaloneModel() {
    assertThrows(UnsupportedOperationException.class, () -> new PhaseGEUniquac());
  }
}
