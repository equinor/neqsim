package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Tests the phase-resolved face-flux view used by transient temperature transport. */
class TwoFluidThermalFaceFluxTest {
  @Test
  void closedExternalFacesAreZeroWhileInternalConvectionRemainsActive() {
    TwoFluidSection[] sections = new TwoFluidSection[] { createGasSection(0.0, 0.0), createGasSection(10.0, 2.0),
        createGasSection(20.0, 0.0) };
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();

    equations.calcRHS(sections, 10.0);
    double[][] faceFluxes = equations.getLastPhaseMassFaceFluxes();

    for (int phase = 0; phase < 3; phase++) {
      assertEquals(0.0, faceFluxes[0][phase], 0.0);
      assertEquals(0.0, faceFluxes[faceFluxes.length - 1][phase], 0.0);
    }
    double internalThroughput = 0.0;
    for (int face = 1; face < faceFluxes.length - 1; face++) {
      for (int phase = 0; phase < 3; phase++) {
        internalThroughput += Math.abs(faceFluxes[face][phase]);
      }
    }
    assertTrue(internalThroughput > 0.0, "Closing the external faces must not disable internal convection");

    double retainedInternalGasFlow = faceFluxes[1][0];
    double[][] weightedFluxes = new double[faceFluxes.length][3];
    equations.accumulateLastPhaseMassFaceFluxes(weightedFluxes, 0.25);
    equations.accumulateLastPhaseMassFaceFluxes(weightedFluxes, 0.75);
    for (int face = 0; face < faceFluxes.length; face++) {
      for (int phase = 0; phase < 3; phase++) {
        assertEquals(faceFluxes[face][phase], weightedFluxes[face][phase], 1.0e-12,
            "Stage accumulation must preserve the weighted conservative face flow");
      }
    }
    faceFluxes[0][0] = 1.0;
    assertEquals(0.0, equations.getLastPhaseMassFaceFluxes()[0][0], 0.0,
        "Callers must not be able to mutate the retained integration-stage fluxes");

    sections[1].setGasVelocity(4.0);
    sections[1].updateConservativeVariables();
    sections[1].updateDerivedQuantities();
    equations.calcRHS(sections, 10.0);
    double[][] updatedFaceFluxes = equations.getLastPhaseMassFaceFluxes();

    assertNotEquals(retainedInternalGasFlow, updatedFaceFluxes[1][0],
        "A reused retained buffer must be overwritten by the next RHS evaluation");
    assertEquals(retainedInternalGasFlow, faceFluxes[1][0], 0.0,
        "A previously returned defensive snapshot must not alias the reused internal buffer");
  }

  @Test
  void singleSectionReturnsClosedBoundaryFacesWithoutAnInternalInterface() {
    TwoFluidSection[] sections = new TwoFluidSection[] { createGasSection(0.0, 0.0) };
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();

    double[][] faceFluxes = equations.calcPhaseMassFaceFluxes(sections, 10.0);

    assertEquals(2, faceFluxes.length);
    for (int face = 0; face < faceFluxes.length; face++) {
      for (int phase = 0; phase < 3; phase++) {
        assertEquals(0.0, faceFluxes[face][phase], 0.0);
      }
    }
  }

  private TwoFluidSection createGasSection(double position, double gasVelocity) {
    TwoFluidSection section = new TwoFluidSection(position, 10.0, 0.20, 0.0);
    section.setPressure(60.0e5);
    section.setTemperature(300.0);
    section.setGasDensity(40.0);
    section.setLiquidDensity(700.0);
    section.setOilDensity(700.0);
    section.setWaterDensity(1000.0);
    section.setGasViscosity(1.2e-5);
    section.setLiquidViscosity(1.0e-3);
    section.setOilViscosity(1.0e-3);
    section.setWaterViscosity(1.0e-3);
    section.setGasSoundSpeed(300.0);
    section.setLiquidSoundSpeed(1200.0);
    section.setGasEnthalpy(1.0e5);
    section.setLiquidEnthalpy(5.0e4);
    section.setSurfaceTension(0.02);
    section.setGasHoldup(1.0);
    section.setLiquidHoldup(0.0);
    section.setOilHoldup(0.0);
    section.setWaterHoldup(0.0);
    section.setWaterCut(0.0);
    section.setGasVelocity(gasVelocity);
    section.setLiquidVelocity(0.0);
    section.setOilVelocity(0.0);
    section.setWaterVelocity(0.0);
    section.updateConservativeVariables();
    section.updateDerivedQuantities();
    return section;
  }
}
