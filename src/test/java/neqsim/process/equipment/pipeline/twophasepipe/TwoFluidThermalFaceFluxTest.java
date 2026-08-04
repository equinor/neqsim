package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Tests the phase-resolved face-flux view used by transient temperature transport. */
class TwoFluidThermalFaceFluxTest {
  @Test
  void closedExternalFacesAreZeroWhileInternalConvectionRemainsActive() {
    TwoFluidSection[] sections = new TwoFluidSection[] { createGasSection(0.0, 0.0), createGasSection(10.0, 2.0),
        createGasSection(20.0, 0.0) };
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();

    double[][] faceFluxes = equations.calcPhaseMassFaceFluxes(sections, 10.0);

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
