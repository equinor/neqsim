package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Exact integrated energy-transfer and rejection checks, independent of the EOS thermal recovery.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ConservativeEnergyTransportTest {
  /** Unequal cell lengths exchange one energy amount without modifying mass or momentum. */
  @Test
  void internalTransfersUseIndependentUpwindPhaseEnthalpiesAndConserveTotalEnergy() {
    double[][] state = {{1.0, 2.0, 3.0, 4.0, 5.0, 6.0, -100.0}, {7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 200.0}};
    double[][] original = {state[0].clone(), state[1].clone()};
    double[][] transfers = {{0.0, 0.0, 0.0}, {0.25, -0.5, 0.125}, {0.0, 0.0, 0.0}};
    double[][] enthalpies = {{1000.0, 2000.0, -3000.0}, {4000.0, 5000.0, 6000.0}};
    double[] boundary = ConservativeEnergyTransport.applyFaceTransfers(state, transfers, enthalpies,
        new double[] {Double.NaN, Double.NaN, Double.NaN}, new double[] {2.0, 5.0});
    double transferred = 0.25 * 1000.0 - 0.5 * 5000.0 - 0.125 * 3000.0;
    assertEquals(original[0][6] - transferred / 2.0, state[0][6], 0.0);
    assertEquals(original[1][6] + transferred / 5.0, state[1][6], 0.0);
    assertEquals(original[0][6] * 2.0 + original[1][6] * 5.0, state[0][6] * 2.0 + state[1][6] * 5.0, 0.0);
    assertArrayEquals(new double[2], boundary, 0.0);
    for (int cell = 0; cell < state.length; cell++) {
      for (int variable = 0; variable < 6; variable++) {
        assertEquals(original[cell][variable], state[cell][variable], 0.0);
      }
    }
  }

  /** Feed inflow, inlet withdrawal and outlet withdrawal use signed conservative first-law terms. */
  @Test
  void boundaryTransfersCloseEnergyIncludingNegativeCaloricReferences() {
    for (double inletMass : new double[] {0.3, -0.2}) {
      double[][] state = {{1.0, 0.0, 0.0, 0.0, 0.0, 0.0, -100.0}};
      double[] boundary = ConservativeEnergyTransport.applyFaceTransfers(state,
          new double[][] {{inletMass, 0.0, 0.0}, {0.1, 0.0, 0.0}}, new double[][] {{-1000.0, Double.NaN, Double.NaN}},
          new double[] {-500.0, Double.NaN, Double.NaN}, new double[] {2.0});
      double inletEnergy = inletMass * (inletMass > 0.0 ? -500.0 : -1000.0);
      assertArrayEquals(new double[] {inletEnergy, -100.0}, boundary, 0.0);
      assertEquals(-200.0 + boundary[0] - boundary[1], state[0][6] * 2.0, 0.0);
    }
  }

  /** Unsupported reverse outlet flow cannot partially apply an earlier valid internal transfer. */
  @Test
  void outletBackflowRejectsWithoutChangingAnyCellEnergy() {
    double[][] state = {{1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 100.0}, {1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 200.0}};
    double[][] original = {state[0].clone(), state[1].clone()};
    assertThrows(IllegalArgumentException.class,
        () -> ConservativeEnergyTransport.applyFaceTransfers(state,
            new double[][] {{0.0, 0.0, 0.0}, {0.1, 0.0, 0.0}, {-0.01, 0.0, 0.0}},
            new double[][] {{1000.0, 0.0, 0.0}, {2000.0, 0.0, 0.0}}, new double[3], new double[] {1.0, 2.0}));
    assertArrayEquals(original[0], state[0], 0.0);
    assertArrayEquals(original[1], state[1], 0.0);
  }

  /** Invalid properties and geometry are rejected before publishing any partial update. */
  @Test
  void invalidDonorAndLengthRejectTransactionally() {
    for (double length : new double[] {1.0, 0.0, Double.NaN}) {
      double[][] state = {{1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 100.0}};
      double[] original = state[0].clone();
      assertThrows(IllegalArgumentException.class,
          () -> ConservativeEnergyTransport.applyFaceTransfers(state, new double[][] {{0.0, 0.0, 0.0}, {0.1, 0.0, 0.0}},
              new double[][] {{Double.NaN, 0.0, 0.0}}, new double[3], new double[] {length}));
      assertArrayEquals(original, state[0], 0.0);
    }
  }
}
