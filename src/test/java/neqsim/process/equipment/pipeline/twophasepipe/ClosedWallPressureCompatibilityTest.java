package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/**
 * Mechanical closed-wall pressure compatibility, not two-phase pool qualification.
 *
 * @author NeqSim
 * @version 1.0
 */
class ClosedWallPressureCompatibilityTest {
  /** Affine hydrostatic pressure balances both closed ends on every prescribed mesh. */
  @Test
  void affineHydrostaticBalance() {
    for (int count : new int[] {1, 12, 24, 48}) {
      for (int phase = 0; phase < 3; phase++) {
        for (double slope : new double[] {-0.011, 0.011}) {
          double dx = 5000.0 / count;
          TwoFluidSection[] cells = new TwoFluidSection[count];
          double rho = phase == 0 ? 10.0 : phase == 1 ? 800.0 : 1000.0;
          for (int i = 0; i < count; i++) {
            cells[i] = cell(i, dx, phase, Math.asin(slope));
            cells[i].setPressure(1.5e7 - rho * 9.81 * slope * (i + 0.5) * dx);
          }
          double[][] rates = equations().calcRHS(cells, dx);
          for (int i = 0; i < count; i++) {
            assertEquals(0.0, rates[i][3 + phase] / (rho * cells[i].getArea()), 1e-10);
            for (int phaseIndex = 0; phaseIndex < 3; phaseIndex++) {
              assertEquals(0.0, rates[i][phaseIndex], 0.0);
              if (phaseIndex != phase) {
                assertEquals(0.0, rates[i][3 + phaseIndex], 0.0);
              }
            }
            assertEquals(0.0, rates[i][6], 0.0);
          }
        }
      }
    }
  }

  /** A horizontal acoustic cosine retains the zero-normal-gradient wall pressure. */
  @Test
  void horizontalAcousticReflection() {
    int n = 24;
    double dx = 10.0;
    TwoFluidSection[] cells = new TwoFluidSection[n];
    for (int i = 0; i < n; i++) {
      cells[i] = cell(i, dx, 0, 0.0);
      cells[i].setPressure(2.0e5 + 1.0e3 * Math.cos(Math.PI * (i + 0.5) / n));
    }
    double[][] rates = equations().calcRHS(cells, dx);
    double expected = -(0.5 * (cells[1].getPressure() - cells[0].getPressure())) / dx;
    assertEquals(expected, rates[0][3] / cells[0].getArea(), 1e-10);
    assertEquals(expected, rates[n - 1][3] / cells[n - 1].getArea(), 1e-10);
    for (double[] rate : rates) {
      assertEquals(0.0, rate[0], 0.0);
      assertEquals(0.0, rate[6], 0.0);
    }
  }

  /** Closed phases have no boundary transport even with nonzero interior momentum. */
  @Test
  void wallDoesNotLeakMassOrEnthalpy() {
    TwoFluidSection cell = cell(0, 10.0, 0, 0.0);
    cell.setGasVelocity(2.0);
    cell.updateConservativeVariables();
    double[][] rates = equations().calcRHS(new TwoFluidSection[] {cell}, 10.0);
    assertEquals(0.0, rates[0][0], 0.0);
    assertEquals(0.0, rates[0][6], 0.0);
  }

  /** A closed inlet cannot borrow pressure, geometry or phases from an external donor. */
  @Test
  void closedInletIgnoresExternalDonor() {
    for (boolean consistent : new boolean[] {false, true}) {
      TwoFluidSection cell = cell(0, 10.0, 0, 0.0);
      TwoFluidSection donor = new TwoFluidSection(0.0, 1.0, 0.5, 0.0);
      donor.setPressure(1.0e5);
      TwoFluidConservationEquations equations = equations();
      equations.setConsistentPhasePressureEnabled(consistent);
      double[][] reference = equations.calcRHS(new TwoFluidSection[] {cell}, 10.0);
      equations.setInletBoundaryState(donor);
      double[][] supplied = equations.calcRHS(new TwoFluidSection[] {cell}, 10.0);
      for (int entry = 0; entry < reference[0].length; entry++) {
        assertEquals(reference[0][entry], supplied[0][entry], 0.0);
      }
    }
  }

  /** A mixed cell balances total body force without claiming static individual phases. */
  @Test
  void mixtureTotalForceUsesConservativeInertia() {
    TwoFluidSection cell = cell(0, 10.0, 0, 0.1);
    cell.setGasHoldup(0.6);
    cell.setLiquidHoldup(0.4);
    cell.setWaterCut(0.25);
    cell.updateConservativeVariables();
    double[][] rates = equations().calcRHS(new TwoFluidSection[] {cell}, 10.0);
    assertEquals(0.0, rates[0][3] + rates[0][4] + rates[0][5], 1e-8);
    assertEquals(0.0, rates[0][6], 0.0);
  }

  /** Rejection at the wall restores the existing transactional operator diagnostics. */
  @Test
  void wallRejectionRestoresPublishedLedger() {
    TwoFluidConservationEquations equations = equations();
    TwoFluidSection accepted = cell(0, 10.0, 0, 0.0);
    double[] state = accepted.getStateVector();
    equations.calcRHS(new TwoFluidSection[] {accepted.clone()}, 10.0);
    TwoFluidConservationEquations.MassBalanceRate balance = equations.getLastMassBalanceRate();
    double[] energyFaces = equations.getLastEnergyFaceFluxes();
    TwoFluidSection trial = cell(0, 100.0, 1, 0.2);
    trial.setPressure(1000.0);
    assertThrows(IllegalStateException.class,
        () -> equations.evaluateTransactional(new TwoFluidSection[] {trial}, 100.0));
    assertSame(balance, equations.getLastMassBalanceRate());
    assertArrayEquals(energyFaces, equations.getLastEnergyFaceFluxes(), 0.0);
    assertArrayEquals(state, accepted.getStateVector(), 0.0);
  }

  /** A nonphysical half-cell head fails without imposing a numerical pressure floor. */
  @Test
  void inadmissibleWallPressureRejected() {
    TwoFluidSection cell = cell(0, 100.0, 1, 0.2);
    cell.setPressure(1000.0);
    assertThrows(IllegalStateException.class, () -> equations().calcRHS(new TwoFluidSection[] {cell}, 100.0));
  }

  /**
   * Construct a pure phase with initialized conservative inertia.
   *
   * @param index cell index
   * @param length arclength in m
   * @param phase gas=0, oil=1, water=2
   * @param angle inclination in radians
   * @return initialized section
   */
  private static TwoFluidSection cell(int index, double length, int phase, double angle) {
    TwoFluidSection s = new TwoFluidSection((index + 0.5) * length, length, 1.0148, angle);
    s.setPressure(1.5e7);
    s.setGasHoldup(phase == 0 ? 1.0 : 0.0);
    s.setLiquidHoldup(phase == 0 ? 0.0 : 1.0);
    s.setWaterCut(phase == 2 ? 1.0 : 0.0);
    s.setGasDensity(10.0);
    s.setOilDensity(800.0);
    s.setWaterDensity(1000.0);
    s.setLiquidDensity(phase == 2 ? 1000.0 : 800.0);
    s.setGasVelocity(0.0);
    s.setLiquidVelocity(0.0);
    s.setOilVelocity(0.0);
    s.setWaterVelocity(0.0);
    s.updateConservativeVariables();
    return s;
  }

  /**
   * Configure existing conservative pressure ownership and impermeable ends.
   *
   * @return equations with no phase transfer
   */
  private static TwoFluidConservationEquations equations() {
    TwoFluidConservationEquations e = new TwoFluidConservationEquations();
    e.setIncludeEnergyEquation(true);
    e.setIncludeMassTransfer(false);
    e.setConsistentPhasePressureEnabled(true);
    e.setClosedBoundaries(true, true);
    return e;
  }
}
