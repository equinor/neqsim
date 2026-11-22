package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Analytical unequal-density force and signed-work limits of the common-pressure reconstruction.
 *
 * <p>
 * These instantaneous inviscid controls are not a stationary pool or an evaporation-rate benchmark.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
class MultiphaseHydrostaticForceQualificationTest {
  private static final double GRAVITY = 9.81;
  private static final double DIAMETER = 1.0148;
  private static final double ACCELERATION_TOLERANCE = 1.0e-10;
  private static final double MOMENTUM_TOLERANCE = 1.0e-8;
  private static final double[] ZERO_VELOCITY = {0.0, 0.0, 0.0};

  /** A common hydrostatic pressure balances mixture force while permitting phase buoyancy. */
  @Test
  void unequalDensityForcesAndExactAbsentPhaseLimit() {
    assertEquals(720, qualifyForces(null));
  }

  /** Countercurrent phases retain their own density donor in signed pressure work. */
  @Test
  void signedMultiphaseWorkAndAbsentPhaseLimits() {
    assertEquals(48, qualifyWork(null));
  }

  /**
   * Execute the same assertions and retain all analytical case results as CSV.
   *
   * @param args output directory
   * @throws IOException if output cannot be written
   */
  public static void main(String[] args) throws IOException {
    Path output = Paths.get(args[0]);
    Files.createDirectories(output);
    try (
        PrintWriter forces = new PrintWriter(
            Files.newBufferedWriter(output.resolve("phase-forces.csv"), StandardCharsets.UTF_8));
        PrintWriter work = new PrintWriter(
            Files.newBufferedWriter(output.resolve("phase-work.csv"), StandardCharsets.UTF_8))) {
      forces.println("terrain,density,cells,gas_holdup,water_cut,max_acceleration_error_m_s2,"
          + "max_mixture_force_N_m,max_gas_acceleration_m_s2,max_oil_acceleration_m_s2,max_water_acceleration_m_s2,"
          + "first_gas_acceleration_m_s2,first_oil_acceleration_m_s2,first_water_acceleration_m_s2");
      work.println("holdup_case,velocity_case,inclination_rad,correction_Pa,expected_work_W,observed_work_W,error_W");
      assertEquals(720, qualifyForces(forces));
      assertEquals(48, qualifyWork(work));
    }
  }

  private static int qualifyForces(PrintWriter output) {
    int cases = 0;
    for (String terrain : new String[] {"flat", "rise", "fall", "original"}) {
      for (boolean smooth : new boolean[] {false, true}) {
        for (int count : new int[] {12, 24, 48, 96, 192}) {
          for (double gasHoldup : new double[] {0.0, 1.0e-12, 0.01, 0.35, 0.99, 1.0}) {
            for (double waterCut : new double[] {0.0, 0.25, 1.0}) {
              TwoFluidSection[] cells = hydrostaticCells(terrain, smooth, count, gasHoldup, waterCut);
              double[][] rates = equations().calcRHS(cells, 5000.0 / count);
              double maximumError = 0.0;
              double maximumMixtureForce = 0.0;
              double[] maximumAcceleration = new double[3];
              double[] firstAcceleration = new double[3];
              for (int cell = 0; cell < count; cell++) {
                TwoFluidSection section = cells[cell];
                double[] mass = {section.getGasMassPerLength(), section.getOilMassPerLength(),
                    section.getWaterMassPerLength()};
                double[] density = {section.getGasDensity(), section.getOilDensity(), section.getWaterDensity()};
                double mixtureDensity = (mass[0] + mass[1] + mass[2]) / section.getArea();
                double mixtureForce = 0.0;
                for (int phase = 0; phase < 3; phase++) {
                  double force = rates[cell][TwoFluidConservationEquations.IDX_GAS_MOMENTUM + phase];
                  mixtureForce += force;
                  assertEquals(0.0, rates[cell][phase], 0.0, "stationary local phase mass");
                  if (mass[phase] == 0.0) {
                    assertEquals(0.0, force, 0.0, "exact absent-phase momentum");
                  } else {
                    double expected = (mixtureDensity / density[phase] - 1.0) * GRAVITY
                        * Math.sin(section.getInclination());
                    double acceleration = force / mass[phase];
                    if (cell == 0) {
                      firstAcceleration[phase] = acceleration;
                    }
                    maximumError = Math.max(maximumError, Math.abs(acceleration - expected));
                    maximumAcceleration[phase] = Math.max(maximumAcceleration[phase], Math.abs(acceleration));
                  }
                }
                maximumMixtureForce = Math.max(maximumMixtureForce, Math.abs(mixtureForce));
                assertEquals(0.0, rates[cell][TwoFluidConservationEquations.IDX_ENERGY], 0.0,
                    "stationary local total energy");
              }
              String context = terrain + "/" + smooth + "/" + count + "/" + gasHoldup + "/" + waterCut;
              assertTrue(maximumError <= ACCELERATION_TOLERANCE, context + ": acceleration error " + maximumError);
              assertTrue(maximumMixtureForce <= MOMENTUM_TOLERANCE, context + ": mixture force " + maximumMixtureForce);
              if (output != null) {
                output.printf(Locale.ROOT, "%s,%s,%d,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g%n",
                    terrain, smooth ? "smooth" : "constant", count, gasHoldup, waterCut, maximumError,
                    maximumMixtureForce, maximumAcceleration[0], maximumAcceleration[1], maximumAcceleration[2],
                    firstAcceleration[0], firstAcceleration[1], firstAcceleration[2]);
              }
              cases++;
            }
          }
        }
      }
    }
    return cases;
  }

  private static int qualifyWork(PrintWriter output) {
    double[][] holdups = {{1.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 1.0}, {0.35, 0.65, 0.0}, {0.35, 0.0, 0.65},
        {0.35, 0.45, 0.20}};
    double[][] velocities = {{2.0, 2.0, 2.0}, {-2.0, -2.0, -2.0}, {2.0, -0.3, 0.2}, {-2.0, 0.3, -0.2}};
    int cases = 0;
    for (int holdupCase = 0; holdupCase < holdups.length; holdupCase++) {
      double[] holdup = holdups[holdupCase];
      double waterCut = holdup[1] + holdup[2] > 0.0 ? holdup[2] / (holdup[1] + holdup[2]) : 0.0;
      for (int velocityCase = 0; velocityCase < velocities.length; velocityCase++) {
        double[] velocity = velocities[velocityCase];
        for (double inclination : new double[] {-0.1, 0.1}) {
          TwoFluidSection[] flat = {cell(0, 10.0, 0.0, holdup[0], waterCut, 1.0, velocity),
              cell(1, 10.0, 0.0, holdup[0], waterCut, 1.2, velocity)};
          TwoFluidSection[] inclined = {cell(0, 10.0, 0.0, holdup[0], waterCut, 1.0, velocity),
              cell(1, 10.0, inclination, holdup[0], waterCut, 1.2, velocity)};
          TwoFluidConservationEquations flatEquations = equations();
          TwoFluidConservationEquations inclinedEquations = equations();
          flatEquations.calcRHS(flat, 10.0);
          inclinedEquations.calcRHS(inclined, 10.0);
          double correction = inclinedEquations.getLastHydrostaticFacePressureCorrections()[0];
          double expected = 0.0;
          double[][] flatMass = flatEquations.getLastPhaseMassFaceFluxes();
          double[][] inclinedMass = inclinedEquations.getLastPhaseMassFaceFluxes();
          for (int phase = 0; phase < 3; phase++) {
            assertEquals(flatMass[1][phase], inclinedMass[1][phase], 0.0, "pressure reconstruction preserves mass");
            expected += holdup[phase] * velocity[phase] * flat[0].getArea() * correction;
          }
          double flatEnergy = flatEquations.getLastEnergyFaceFluxes()[1];
          double observed = inclinedEquations.getLastEnergyFaceFluxes()[1] - flatEnergy;
          assertEquals(expected, observed, 1.0e-7 + 1.0e-12 * Math.abs(flatEnergy), "signed phase volume work");
          if (output != null) {
            output.printf(Locale.ROOT, "%d,%d,%.12g,%.12g,%.12g,%.12g,%.12g%n", holdupCase, velocityCase, inclination,
                correction, expected, observed, Math.abs(expected - observed));
          }
          cases++;
        }
      }
    }
    return cases;
  }

  static TwoFluidSection[] hydrostaticCells(String terrain, boolean smooth, int count, double gasHoldup,
      double waterCut) {
    double length = 5000.0 / count;
    TwoFluidSection[] cells = new TwoFluidSection[count];
    double facePressure = 1.5e7;
    for (int index = 0; index < count; index++) {
      double fraction = (index + 0.5) / count;
      double factor = smooth ? 1.0 + 0.25 * Math.sin(2.0 * Math.PI * fraction) : 1.0;
      double dz = elevation(terrain, (index + 1.0) / count) - elevation(terrain, (double) index / count);
      TwoFluidSection section = cell(index, length, Math.asin(dz / length), gasHoldup, waterCut, factor, ZERO_VELOCITY);
      double density = (section.getGasMassPerLength() + section.getOilMassPerLength() + section.getWaterMassPerLength())
          / section.getArea();
      double nextPressure = facePressure - density * GRAVITY * dz;
      section.setPressure(0.5 * (facePressure + nextPressure));
      section.updateConservativeVariables();
      cells[index] = section;
      facePressure = nextPressure;
    }
    return cells;
  }

  private static double elevation(String terrain, double fraction) {
    if ("flat".equals(terrain)) {
      return -300.0;
    }
    if ("rise".equals(terrain) || "fall".equals(terrain)) {
      return -300.0 + ("rise".equals(terrain) ? 55.0 : -55.0) * fraction;
    }
    return -300.0 - 55.0 * Math.pow(Math.sin(Math.PI * fraction), 2)
        - 12.0 * Math.pow(Math.sin(3.0 * Math.PI * fraction), 2);
  }

  private static TwoFluidSection cell(int index, double length, double inclination, double gasHoldup, double waterCut,
      double densityFactor, double[] velocity) {
    TwoFluidSection section = new TwoFluidSection((index + 0.5) * length, length, DIAMETER, inclination);
    section.setPressure(1.5e7);
    section.setGasHoldup(gasHoldup);
    section.setLiquidHoldup(1.0 - gasHoldup);
    section.setWaterCut(waterCut);
    section.setGasDensity(10.0 * densityFactor);
    section.setOilDensity(800.0 * densityFactor);
    section.setWaterDensity(1000.0 * densityFactor);
    section.setLiquidDensity((800.0 * (1.0 - waterCut) + 1000.0 * waterCut) * densityFactor);
    section.setGasSoundSpeed(340.0);
    section.setLiquidSoundSpeed(1000.0);
    section.setGasEnthalpy(100000.0);
    section.setLiquidEnthalpy(200000.0);
    section.setGasVelocity(velocity[0]);
    section.setLiquidVelocity(0.0);
    section.setOilVelocity(velocity[1]);
    section.setWaterVelocity(velocity[2]);
    section.updateConservativeVariables();
    return section;
  }

  static TwoFluidConservationEquations equations() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(true);
    equations.setIncludeMassTransfer(false);
    equations.setEnableWaterOilSlip(true);
    equations.setConsistentPhasePressureEnabled(true);
    equations.setClosedBoundaries(true, true);
    return equations;
  }
}
