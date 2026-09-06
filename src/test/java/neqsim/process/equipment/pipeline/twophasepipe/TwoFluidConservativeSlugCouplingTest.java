package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker.SlugBubbleUnit;

/** Conservation and pressure-equilibrium gates for subcell geometry in the actual finite-volume flux. */
class TwoFluidConservativeSlugCouplingTest {
  @Test
  void reconstructedFilmChangesSharedFluxWithoutChangingCellInventory() {
    TwoFluidSection[] cells = cells(0.4, 2.0);
    SlugBubbleUnit slug = slug(0.2, 0.6);
    double[][] original = new TwoFluidConservationEquations().extractState(cells);
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    double[][] uncoupled = equations.calcPhaseMassFaceFluxes(cells, 1.0);
    equations.setConservativeSlugs(Collections.singletonList(slug));
    double[][] coupled = equations.calcPhaseMassFaceFluxes(cells, 1.0);
    assertNotEquals(uncoupled[1][1], coupled[1][1], "Film reconstruction must actually affect oil transport");
    assertNotEquals(uncoupled[1][2], coupled[1][2], "Film reconstruction must actually affect water transport");
    for (int cell = 0; cell < cells.length; cell++) {
      assertArrayEquals(original[cell], cells[cell].getStateVector(), 0.0, "Reconstruction cannot deposit inventory");
    }
    equations.setConservativeSlugs(null);
    double[][] disabled = equations.calcPhaseMassFaceFluxes(cells, 1.0);
    for (int face = 0; face < coupled.length; face++) {
      assertArrayEquals(uncoupled[face], disabled[face], 0.0);
    }
  }

  @Test
  void internalTransfersCancelForEveryPhaseOnANonuniformMesh() {
    for (double waterCut : new double[] { 0.0, 0.4, 1.0 }) {
      TwoFluidSection[] cells = cells(waterCut, 2.0);
      cells[1].setLength(0.7);
      cells[1].setPosition(1.35);
      cells[2].setPosition(2.2);
      TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
      equations.setConservativeSlugs(Collections.singletonList(slug(0.2, 1.5)));
      equations.setIncludeMassTransfer(false);
      double[][] rates = equations.calcRHS(cells, 1.0);
      TwoFluidConservationEquations.MassBalanceRate boundary = equations.getLastMassBalanceRate();
      double[][] fluxes = equations.calcPhaseMassFaceFluxes(cells, 1.0);
      assertTrue(boundary != null);
      for (int phase = 0; phase < 3; phase++) {
        double change = 0.0;
        for (int cell = 0; cell < cells.length; cell++) {
          change += rates[cell][phase] * cells[cell].getLength();
        }
        assertEquals(fluxes[0][phase] - fluxes[cells.length][phase], change, 1e-10);
      }
    }
  }

  @Test
  void restingSubcellInterfacesRemainInPressureEquilibrium() {
    TwoFluidSection[] cells = cells(0.4, 0.0);
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setEnableInterfacialPressure(true);
    equations.setConservativeSlugs(Collections.singletonList(slug(0.2, 1.4)));
    double[][] rates = equations.calcRHS(cells, 1.0);
    for (double[] rate : rates) {
      for (int variable = 0; variable < 6; variable++) {
        assertEquals(0.0, rate[variable], 1e-8, "Resting reconstructed contact cannot generate flow");
      }
    }
  }

  @Test
  void gasFreeRestingLiquidHasNoArtificialPressurePulse() {
    for (double waterCut : new double[] { 0.0, 0.4, 1.0 }) {
      TwoFluidSection[] cells = cells(waterCut, 0.0);
      for (TwoFluidSection cell : cells) {
        cell.setGasHoldup(0.0);
        cell.setLiquidHoldup(1.0);
        cell.setOilHoldup(1.0 - waterCut);
        cell.setWaterHoldup(waterCut);
        cell.updateConservativeVariables();
      }
      TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
      equations.setEnableInterfacialPressure(true);
      for (double[] rate : equations.calcRHS(cells, 1.0)) {
        for (int variable = 0; variable < 6; variable++) {
          assertEquals(0.0, rate[variable], 1e-8);
        }
      }
    }
  }

  @Test
  void closedFaceCannotLeakReconstructedSlugMomentum() {
    TwoFluidSection[] cells = cells(0.4, 0.0);
    SlugBubbleUnit slug = slug(2.6, 3.2);
    slug.hasConservativeVelocity = true;
    slug.slugLiquidVelocity = 2.0;
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setConservativeSlugs(Collections.singletonList(slug));
    equations.setClosedBoundaries(true, true);
    double[][] fluxes = equations.calcPhaseMassFaceFluxes(cells, 1.0);
    assertArrayEquals(new double[3], fluxes[0], 0.0);
    assertArrayEquals(new double[3], fluxes[3], 0.0);
  }

  @Test
  void oneCellRestingFeedContactCancelsBoundaryPressureTerms() {
    TwoFluidSection cell = cells(0.4, 0.0)[0];
    TwoFluidSection feed = cell.clone();
    feed.setWaterCut(0.8);
    feed.setOilHoldup(0.08);
    feed.setWaterHoldup(0.32);
    feed.updateConservativeVariables();
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setEnableInterfacialPressure(true);
    equations.setInletBoundaryState(feed);
    double[][] rhs = equations.calcRHS(new TwoFluidSection[] { cell }, 1.0);
    for (int variable = 0; variable < 6; variable++) {
      assertEquals(0.0, rhs[0][variable], 1e-8);
    }
  }

  private static SlugBubbleUnit slug(double tail, double front) {
    SlugBubbleUnit slug = new SlugBubbleUnit();
    slug.id = 1;
    slug.frontPosition = front;
    slug.tailPosition = tail;
    slug.slugLength = front - tail;
    slug.slugHoldup = 0.9;
    return slug;
  }

  private static TwoFluidSection[] cells(double waterCut, double velocity) {
    TwoFluidSection[] cells = new TwoFluidSection[3];
    for (int index = 0; index < cells.length; index++) {
      TwoFluidSection cell = new TwoFluidSection(index + 0.5, 1.0, 0.1, 0.0);
      cell.setGasDensity(5.0);
      cell.setOilDensity(800.0);
      cell.setWaterDensity(1000.0);
      cell.setLiquidDensity(800.0 * (1.0 - waterCut) + 1000.0 * waterCut);
      cell.setGasHoldup(0.6);
      cell.setLiquidHoldup(0.4);
      cell.setOilHoldup(0.4 * (1.0 - waterCut));
      cell.setWaterHoldup(0.4 * waterCut);
      cell.setWaterCut(waterCut);
      cell.setGasVelocity(velocity);
      cell.setLiquidVelocity(velocity);
      cell.setOilVelocity(velocity);
      cell.setWaterVelocity(velocity);
      cell.setPressure(2e5);
      cell.setGasSoundSpeed(300.0);
      cell.setLiquidSoundSpeed(1200.0);
      cell.setGasEnthalpy(1e5);
      cell.setLiquidEnthalpy(1e5);
      cell.updateConservativeVariables();
      cells[index] = cell;
    }
    return cells;
  }
}
