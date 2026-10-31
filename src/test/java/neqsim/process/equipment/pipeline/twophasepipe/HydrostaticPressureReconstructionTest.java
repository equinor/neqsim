package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Well-balanced interior pressure, gravity, and signed pressure-work qualification.
 *
 * @author NeqSim
 * @version 1.0
 */
class HydrostaticPressureReconstructionTest {
  private static final double GRAVITY = 9.81;
  private static final double DIAMETER = 1.0148;
  private static final double PRESSURE_WORK_ROUNDOFF_W = 5.0e-8;

  /** Piecewise-constant hydrostatic states remain fixed across terrain, density, phase, and mesh controls. */
  @Test
  void hydrostaticTerrainAndDensityMatrix() {
    int cases = 0;
    for (String terrain : new String[] {"flat", "affine", "original"}) {
      for (String densityMode : new String[] {"constant", "smooth", "alternating"}) {
        for (int count : new int[] {12, 24, 48, 96, 192}) {
          for (int phase = 0; phase < 2; phase++) {
            TwoFluidSection[] cells = hydrostaticCells(terrain, densityMode, count, phase);
            TwoFluidConservationEquations equations = equations();
            double[][] rates = equations.calcRHS(cells, 5000.0 / count);
            double maximumAcceleration = 0.0;
            for (int cell = 0; cell < count; cell++) {
              double density = phase == 0 ? cells[cell].getGasDensity() : cells[cell].getOilDensity();
              maximumAcceleration = Math.max(maximumAcceleration,
                  Math.abs(rates[cell][TwoFluidConservationEquations.IDX_GAS_MOMENTUM + phase]
                      / (density * cells[cell].getArea())));
              assertEquals(0.0, rates[cell][TwoFluidConservationEquations.IDX_GAS_MASS + phase], 0.0);
              assertEquals(0.0, rates[cell][TwoFluidConservationEquations.IDX_ENERGY], 0.0);
            }
            assertTrue(maximumAcceleration < 1.0e-10,
                terrain + "/" + densityMode + "/" + count + "/" + phase + ": " + maximumAcceleration);
            assertEquals(count - 1, equations.getLastHydrostaticFacePressureCorrections().length);
            cases++;
          }
        }
      }
    }
    assertEquals(90, cases);
  }

  /** A separately declared aqueous extension applies the same hydrostatic gates without changing the 90-case basis. */
  @Test
  void hydrostaticWaterExtension() {
    int cases = 0;
    for (String terrain : new String[] {"flat", "affine", "original"}) {
      for (String densityMode : new String[] {"constant", "smooth", "alternating"}) {
        for (int count : new int[] {12, 24, 48, 96, 192}) {
          TwoFluidSection[] cells = hydrostaticCells(terrain, densityMode, count, 2);
          TwoFluidConservationEquations equations = equations();
          double[][] rates = equations.calcRHS(cells, 5000.0 / count);
          double maximumAcceleration = 0.0;
          for (int cell = 0; cell < count; cell++) {
            maximumAcceleration = Math.max(maximumAcceleration,
                Math.abs(rates[cell][TwoFluidConservationEquations.IDX_WATER_MOMENTUM]
                    / (cells[cell].getWaterDensity() * cells[cell].getArea())));
            assertEquals(0.0, rates[cell][TwoFluidConservationEquations.IDX_WATER_MASS], 0.0);
            assertEquals(0.0, rates[cell][TwoFluidConservationEquations.IDX_ENERGY], 0.0);
          }
          assertTrue(maximumAcceleration < 1.0e-10,
              terrain + "/" + densityMode + "/" + count + "/water: " + maximumAcceleration);
          cases++;
        }
      }
    }
    assertEquals(45, cases);
  }

  /** The face correction carries signed pressure work with the same thermodynamic donor as mass. */
  @Test
  void signedPressureWorkUsesMassDonor() {
    for (double velocity : new double[] {-2.0, 2.0}) {
      TwoFluidSection left = gasCell(0, 10.0, 10.0, 0.0, velocity);
      TwoFluidSection right = gasCell(1, 10.0, 20.0, 0.1, velocity);
      TwoFluidConservationEquations equations = equations();
      equations.calcRHS(new TwoFluidSection[] {left, right}, 10.0);

      double correction = 0.25 * GRAVITY * right.getLength() * Math.sin(right.getInclination()) * right.getGasDensity();
      double donorDensity = velocity > 0.0 ? left.getGasDensity() : right.getGasDensity();
      double massFlow = donorDensity * velocity * left.getArea();
      double donorTotalEnthalpy = 100000.0 + 0.5 * velocity * velocity;
      double expected = massFlow * donorTotalEnthalpy + massFlow * correction / donorDensity;

      assertEquals(correction, equations.getLastHydrostaticFacePressureCorrections()[0], 1.0e-12);
      assertEquals(expected, equations.getLastEnergyFaceFluxes()[1], PRESSURE_WORK_ROUNDOFF_W);
      assertEquals(massFlow / donorDensity * correction,
          equations.getLastEnergyFaceFluxes()[1] - massFlow * donorTotalEnthalpy, PRESSURE_WORK_ROUNDOFF_W);
    }
  }

  /** Horizontal geometry retains the established face flux exactly. */
  @Test
  void horizontalCorrectionIsExactZero() {
    TwoFluidSection left = gasCell(0, 10.0, 8.0, 0.0, 1.0);
    TwoFluidSection right = gasCell(1, 10.0, 20.0, 0.0, -1.0);
    right.setPressure(1.4e7);
    TwoFluidConservationEquations equations = equations();
    equations.calcRHS(new TwoFluidSection[] {left, right}, 10.0);
    assertEquals(0.0, equations.getLastHydrostaticFacePressureCorrections()[0], 0.0);
  }

  /** Transactional trials restore the accepted pressure-reconstruction diagnostic. */
  @Test
  void transactionalEvaluationRestoresCorrectionLedger() {
    TwoFluidConservationEquations equations = equations();
    TwoFluidSection[] accepted = {gasCell(0, 10.0, 10.0, 0.0, 0.0), gasCell(1, 10.0, 20.0, 0.1, 0.0)};
    equations.calcRHS(accepted, 10.0);
    double[] correction = equations.getLastHydrostaticFacePressureCorrections();

    TwoFluidSection[] trial = {gasCell(0, 10.0, 30.0, -0.2, 0.0), gasCell(1, 10.0, 5.0, 0.2, 0.0)};
    equations.calcRHSTransactional(trial, 10.0);
    assertArrayEquals(correction, equations.getLastHydrostaticFacePressureCorrections(), 0.0);

    TwoFluidSection invalidLeft = gasCell(0, 1.0e6, 1000.0, Math.PI / 2.0, 0.0);
    TwoFluidSection invalidRight = gasCell(1, 10.0, 10.0, 0.0, 0.0);
    invalidLeft.setPressure(1.0e5);
    invalidRight.setPressure(1.0e5);
    IllegalStateException rejected = assertThrows(IllegalStateException.class,
        () -> equations.calcRHSTransactional(new TwoFluidSection[] {invalidLeft, invalidRight}, 10.0));
    assertTrue(rejected.getMessage().contains("positive finite pressure"));
    assertArrayEquals(correction, equations.getLastHydrostaticFacePressureCorrections(), 0.0);
  }

  /**
   * Construct one exact piecewise-constant hydrostatic control.
   *
   * @param terrain flat, affine, or original public terrain
   * @param densityMode constant, smooth, or alternating cell density
   * @param count number of cells
   * @param phase gas=0, oil=1, or water=2
   * @return initialized hydrostatic cells
   */
  private static TwoFluidSection[] hydrostaticCells(String terrain, String densityMode, int count, int phase) {
    double length = 5000.0 / count;
    double[] elevation = new double[count + 1];
    double[] facePressure = new double[count + 1];
    TwoFluidSection[] cells = new TwoFluidSection[count];
    for (int face = 0; face <= count; face++) {
      double fraction = (double) face / count;
      elevation[face] = "flat".equals(terrain) ? -300.0
          : "affine".equals(terrain) ? -300.0 - 55.0 * fraction
              : -300.0 - 55.0 * Math.pow(Math.sin(Math.PI * fraction), 2)
                  - 12.0 * Math.pow(Math.sin(3.0 * Math.PI * fraction), 2);
    }
    facePressure[0] = 1.5e7;
    for (int cell = 0; cell < count; cell++) {
      double factor = "constant".equals(densityMode) ? 1.0
          : "smooth".equals(densityMode) ? 1.0 + 0.25 * Math.sin(2.0 * Math.PI * (cell + 0.5) / count)
              : cell % 2 == 0 ? 0.75 : 1.25;
      double density = (phase == 0 ? 10.0 : phase == 1 ? 800.0 : 1000.0) * factor;
      double elevationChange = elevation[cell + 1] - elevation[cell];
      facePressure[cell + 1] = facePressure[cell] - density * GRAVITY * elevationChange;
      TwoFluidSection section = new TwoFluidSection((cell + 0.5) * length, length, DIAMETER,
          Math.asin(elevationChange / length));
      section.setPressure(0.5 * (facePressure[cell] + facePressure[cell + 1]));
      section.setGasHoldup(phase == 0 ? 1.0 : 0.0);
      section.setLiquidHoldup(phase == 0 ? 0.0 : 1.0);
      section.setWaterCut(phase == 2 ? 1.0 : 0.0);
      section.setGasDensity(density);
      section.setOilDensity(density);
      section.setLiquidDensity(density);
      section.setWaterDensity(phase == 2 ? density : 1000.0);
      section.setGasVelocity(0.0);
      section.setLiquidVelocity(0.0);
      section.setOilVelocity(0.0);
      section.setWaterVelocity(0.0);
      section.updateConservativeVariables();
      cells[cell] = section;
    }
    return cells;
  }

  /**
   * Construct one gas cell for pressure-work controls.
   *
   * @param index cell index
   * @param length cell length in m
   * @param density gas density in kg/m3
   * @param inclination inclination in radians
   * @param velocity gas velocity in m/s
   * @return initialized gas cell
   */
  private static TwoFluidSection gasCell(int index, double length, double density, double inclination,
      double velocity) {
    TwoFluidSection section = new TwoFluidSection((index + 0.5) * length, length, DIAMETER, inclination);
    section.setPressure(1.5e7);
    section.setGasHoldup(1.0);
    section.setLiquidHoldup(0.0);
    section.setWaterCut(0.0);
    section.setGasDensity(density);
    section.setGasVelocity(velocity);
    section.setGasSoundSpeed(340.0);
    section.setGasEnthalpy(100000.0);
    section.setOilDensity(800.0);
    section.setLiquidDensity(800.0);
    section.setWaterDensity(1000.0);
    section.updateConservativeVariables();
    return section;
  }

  /** @return conservative equations with impermeable external boundaries */
  private static TwoFluidConservationEquations equations() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(true);
    equations.setIncludeMassTransfer(false);
    equations.setConsistentPhasePressureEnabled(true);
    equations.setClosedBoundaries(true, true);
    return equations;
  }
}
