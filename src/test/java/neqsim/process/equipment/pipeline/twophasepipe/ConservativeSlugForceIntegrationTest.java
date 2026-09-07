package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker.SlugBubbleUnit;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.SlugForceBalance;
import neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;

/** Physical and conservative contracts for opt-in subcell mechanical forces. */
class ConservativeSlugForceIntegrationTest {
  @Test
  void fallingFilmUsesItsOwnWallVelocityAndConservesInternalMomentumExchange() {
    TwoFluidSection cell = cell();
    SlugBubbleUnit slug = new SlugBubbleUnit();
    slug.tailPosition = 0.0;
    slug.frontPosition = 1.0;
    slug.slugHoldup = 0.9;
    slug.usesConservativeFilmCoupling = true;
    slug.hasConservativeVelocity = true;
    slug.slugLiquidVelocity = 3.0;
    double[] before = cell.getStateVector();
    SlugFilmCoupling.Reconstruction split = SlugFilmCoupling.reconstruct(cell, Collections.singletonList(slug));
    assertTrue(split.isActive());
    TwoFluidSection body = split.getBodyState();
    TwoFluidSection film = split.getFilmState();
    assertTrue(body.getLiquidVelocity() > 0.0);
    assertTrue(film.getLiquidVelocity() < 0.0, "inventory and momentum budgets must admit film fallback");
    body.setRegimeWeights(null);
    body.setFlowRegime(FlowRegime.SLUG);
    SlugForceBalance.Forces fb = new SlugForceBalance().evaluate(body);
    WallFriction.WallFrictionResult wf = new WallFriction().calculate(FlowRegime.ANNULAR, film.getGasVelocity(),
        film.getLiquidVelocity(), film.getGasDensity(), film.getLiquidDensity(), film.getGasViscosity(),
        film.getLiquidViscosity(), film.getLiquidHoldup(), film.getDiameter(), film.getRoughness());
    assertTrue(wf.liquidWallForcePerLength < 0.0, "wall resistance reverses with the falling film");
    InterfacialFriction.InterfacialFrictionResult df = new InterfacialFriction().calculate(FlowRegime.ANNULAR,
        film.getGasVelocity(), film.getLiquidVelocity(), film.getGasDensity(), film.getLiquidDensity(),
        film.getGasViscosity(), film.getLiquidViscosity(), film.getLiquidHoldup(), film.getDiameter(),
        film.getSurfaceTension());
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setSharedSlugForceBalanceEnabled(true);
    equations.setConservativeSlugs(Collections.singletonList(slug));
    assertNull(equations.conservativeSlugForces(cell));
    equations.setConservativeSlugForceIntegrationEnabled(true);
    double[] force = equations.conservativeSlugForces(cell);
    double weight = split.getBodyFraction();
    assertEquals(-weight * fb.gasWall - (1.0 - weight) * wf.gasWallForcePerLength, force[0], 1e-12);
    assertEquals(-weight * fb.liquidWall - (1.0 - weight) * wf.liquidWallForcePerLength, force[1], 1e-12);
    assertEquals(-weight * fb.interfaceForce - (1.0 - weight) * df.interfacialShear * df.interfacialAreaPerLength,
        force[2], 1e-12);
    assertEquals(0.0, force[2] + force[3], 0.0);
    assertArrayEquals(before, cell.getStateVector(), 0.0);
    double[][] advanced = equations.applyConservativeSlugFriction(new double[][] { before },
        new TwoFluidSection[] { cell }, 10.0);
    for (int phase = 0; phase < 3; phase++) {
      assertEquals(before[phase], advanced[0][phase], 0.0);
    }
    assertEquals(before[6], advanced[0][6], 0.0);
    assertArrayEquals(before, cell.getStateVector(), 0.0);
    double initialKinetic = 0.0;
    double finalKinetic = 0.0;
    for (int phase = 0; phase < 3; phase++) {
      if (before[phase] > 0.0) {
        initialKinetic += before[phase + 3] * before[phase + 3] / (2.0 * before[phase]);
        finalKinetic += advanced[0][phase + 3] * advanced[0][phase + 3] / (2.0 * before[phase]);
      }
    }
    assertTrue(finalKinetic <= initialKinetic);
    equations.setConservativeSlugs(Collections.emptyList());
    assertNull(equations.conservativeSlugForces(cell));
    double[][] unchanged = equations.applyConservativeSlugFriction(new double[][] { before },
        new TwoFluidSection[] { cell }, 10.0);
    assertArrayEquals(before, unchanged[0], 0.0);
  }

  private TwoFluidSection cell() {
    TwoFluidSection section = new TwoFluidSection(5.0, 10.0, 0.1, 0.5);
    section.setGasDensity(50.0);
    section.setLiquidDensity(800.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setGasHoldup(0.8);
    section.setLiquidHoldup(0.2);
    section.setWaterCut(0.0);
    section.setGasVelocity(3.0);
    section.setLiquidVelocity(1.0);
    section.setOilVelocity(1.0);
    section.setWaterVelocity(0.0);
    section.setGasViscosity(1e-5);
    section.setLiquidViscosity(1e-3);
    section.setSurfaceTension(0.02);
    section.setPressure(1e5);
    section.setTemperature(300.0);
    section.setMixtureHeatCapacity(2000.0);
    section.setGasEnthalpy(5e5);
    section.setLiquidEnthalpy(2e5);
    section.updateDerivedQuantities();
    section.updateConservativeVariables();
    return section;
  }
}
