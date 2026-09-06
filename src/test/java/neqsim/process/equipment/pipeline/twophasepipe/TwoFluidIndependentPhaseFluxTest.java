package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Physical transport contracts for independent liquid velocities. */
class TwoFluidIndependentPhaseFluxTest {
  @Test
  void oppositeOilWaterVelocitiesCarryMassInOppositeDirections() {
    TwoFluidSection left = section(0.5, 2.0, -1.0);
    TwoFluidSection right = left.clone();
    right.setPosition(1.5);
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setEnableWaterOilSlip(true);
    equations.setAllowOutletPhaseBackflow(true);
    double[][] flux = equations.calcPhaseMassFaceFluxes(new TwoFluidSection[] { left, right }, 1.0);
    assertTrue(flux[1][1] > 0.0, "Oil advects downstream");
    assertTrue(flux[1][2] < 0.0, "Water advects upstream independently of bulk liquid flow");
    assertEquals(left.getOilMassPerLength() * 2.0, flux[1][1], 1e-10);
    assertEquals(left.getWaterMassPerLength() * -1.0, flux[1][2], 1e-10);
    for (int phase = 0; phase < 3; phase++) {
      assertEquals(flux[0][phase], flux[1][phase], 1e-10, "Uniform flow must match inlet");
      assertEquals(flux[1][phase], flux[2][phase], 1e-10, "Uniform flow must match outlet");
    }
  }

  @Test
  void materialContactAtRestDoesNotCreatePhaseMomentum() {
    TwoFluidSection left = section(0.1, 0.0, 0.0);
    TwoFluidSection right = section(0.9, 0.0, 0.0);
    right.setPosition(1.5);
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setEnableWaterOilSlip(true);
    equations.setEnableInterfacialPressure(true);
    double[][] rhs = equations.calcRHS(new TwoFluidSection[] { left, right }, 1.0);
    for (double[] cell : rhs) {
      for (int phase = 0; phase < 3; phase++) {
        assertEquals(0.0, cell[phase], 1e-10, "Resting phase mass");
        assertEquals(0.0, cell[phase + 3], 1e-8, "Constant pressure cannot accelerate a material contact");
      }
    }
  }

  @Test
  void uniformLowDensityGasCarriesItsActualMassAtBothBoundaries() {
    TwoFluidSection left = section(1.0, 0.0, 0.0);
    left.setGasDensity(0.02);
    left.setGasVelocity(3.0);
    left.updateConservativeVariables();
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    double expected = left.getGasMassPerLength() * 3.0;
    double[][] flux = equations.calcPhaseMassFaceFluxes(new TwoFluidSection[] { left, left.clone() }, 1.0);
    for (int face = 0; face < 3; face++) {
      assertEquals(expected, flux[face][0], 1e-12, "Positive density must not be replaced at face " + face);
    }
  }

  private static TwoFluidSection section(double waterCut, double oilVelocity, double waterVelocity) {
    TwoFluidSection section = new TwoFluidSection(0.5, 1.0, 0.1, 0.0);
    section.setGasDensity(10.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setGasHoldup(0.4);
    section.setLiquidHoldup(0.6);
    section.setWaterCut(waterCut);
    section.setOilHoldup(0.6 * (1.0 - waterCut));
    section.setWaterHoldup(0.6 * waterCut);
    section.setLiquidDensity(800.0 * (1.0 - waterCut) + 1000.0 * waterCut);
    section.setGasVelocity(0.0);
    section.setOilVelocity(oilVelocity);
    section.setWaterVelocity(waterVelocity);
    section.setPressure(2e5);
    section.setGasSoundSpeed(300.0);
    section.setLiquidSoundSpeed(1200.0);
    section.setGasEnthalpy(1e5);
    section.setLiquidEnthalpy(1e5);
    section.updateConservativeVariables();
    section.extractPrimitiveVariables();
    return section;
  }
}
