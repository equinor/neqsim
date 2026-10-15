package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Donor momentum must remain conservative when opposing component fluxes have zero net mass.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class NonEquilibriumFilmMomentumTest {
  /** Counter-transfer exchanges momentum despite zero net gas/aqueous mass transfer. */
  @Test
  void zeroNetMassStillTransfersDonorMomentum() {
    TwoFluidSection section = new TwoFluidSection(0.0, 10.0, 0.2, 0.0);
    section.setGasHoldup(0.8);
    section.setLiquidHoldup(0.2);
    section.setWaterCut(1.0);
    section.setGasDensity(30.0);
    section.setWaterDensity(1100.0);
    section.setLiquidDensity(1100.0);
    section.setGasVelocity(3.0);
    section.setLiquidVelocity(1.0);
    section.setWaterVelocity(1.0);
    section.updateConservativeVariables();
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeMassTransfer(true);
    double[] baseline = equations.calcSourceTerms(new TwoFluidSection[] {section})[0];
    equations.setFiniteRateComponentSources(new double[][][] {{{0.01, -0.01}, {0.0, 0.0}, {-0.01, 0.01}}});
    double[] source = equations.calcSourceTerms(new TwoFluidSection[] {section})[0];
    assertEquals(0.0, source[0], 0.0);
    assertEquals(0.0, source[2], 0.0);
    assertEquals(-0.02, source[3] - baseline[3], 1.0e-12);
    assertEquals(0.02, source[5] - baseline[5], 1.0e-12);
  }
}
