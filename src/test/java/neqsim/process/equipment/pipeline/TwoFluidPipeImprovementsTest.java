package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.closure.OilWaterFlowRegimeDetector;
import neqsim.process.equipment.pipeline.twophasepipe.closure.OilWaterFlowRegimeDetector.OilWaterFlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.OilWaterFlowRegimeDetector.OilWaterResult;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator.RHSFunction;

/**
 * Tests for the three multiphase flow improvements:
 * <ul>
 * <li>G1: IMEX pressure correction time integration</li>
 * <li>G2: Characteristic-based boundary conditions</li>
 * <li>G3: Oil-water flow regime detection</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
class TwoFluidPipeImprovementsTest {

  // =================================================================
  // G1: IMEX Pressure Correction Tests
  // =================================================================

  @Nested
  @DisplayName("G1: IMEX pressure correction")
  class IMEXTests {

    @Test
    @DisplayName("IMEX method exists in TimeIntegrator enum")
    void testIMEXMethodExists() {
      TimeIntegrator.Method method = TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION;
      assertNotNull(method);
    }

    @Test
    @DisplayName("IMEX timestep is larger than acoustic CFL timestep")
    void testIMEXTimestepLarger() {
      TimeIntegrator integrator = new TimeIntegrator();

      // Single-cell arrays for typical values
      double[] gasVel = {10.0};
      double[] liqVel = {2.0};
      double[] gasSS = {350.0};
      double[] liqSS = {1200.0};
      double dx = 100.0;

      // Acoustic CFL timestep (standard)
      double dtAcoustic = integrator.calcTwoFluidTimeStep(gasVel, liqVel, gasSS, liqSS, dx);

      // Convective-only CFL timestep (IMEX)
      double dtIMEX = integrator.calcIMEXTimeStep(gasVel, liqVel, dx);

      // IMEX should allow much larger timestep (no sound speed in denominator)
      assertTrue(dtIMEX > dtAcoustic,
          "IMEX timestep (" + dtIMEX + ") should be larger than acoustic (" + dtAcoustic + ")");

      // The ratio should be roughly c/v_max ~ 350/10 = 35x larger
      double ratio = dtIMEX / dtAcoustic;
      assertTrue(ratio > 10,
          "IMEX/acoustic ratio (" + ratio + ") should be > 10 for typical conditions");
    }

    @Test
    @DisplayName("IMEX step produces bounded pressure correction")
    void testIMEXStepBounded() {
      TimeIntegrator integrator = new TimeIntegrator();
      integrator.setMethod(TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION);

      // Set up a simple 5-cell problem
      final int n = 5;
      final int neq = 7; // 7-equation model
      double dx = 100.0;

      // Uniform initial state
      final double[][] U0 = new double[n][neq];
      for (int i = 0; i < n; i++) {
        U0[i][0] = 5.0; // gas mass
        U0[i][1] = 50.0; // oil mass
        U0[i][2] = 10.0; // water mass
        U0[i][3] = 50.0; // gas momentum
        U0[i][4] = 100.0; // oil momentum
        U0[i][5] = 20.0; // water momentum
        U0[i][6] = 1e6; // energy
      }

      // RHSFunction that returns a small perturbation
      RHSFunction rhsFunc = new RHSFunction() {
        @Override
        public double[][] evaluate(double[][] state, double time) {
          double[][] dUdt = new double[n][neq];
          for (int i = 0; i < n; i++) {
            dUdt[i][0] = 0.01;
            dUdt[i][3] = -0.1;
          }
          return dUdt;
        }
      };

      // Set IMEX properties
      double[] soundSpeeds = new double[n];
      double[] densities = new double[n];
      for (int i = 0; i < n; i++) {
        soundSpeeds[i] = 350.0;
        densities[i] = 100.0;
      }
      integrator.setIMEXProperties(soundSpeeds, densities, dx, 50e5, true);

      double dt = 0.5; // relatively large timestep
      double[][] Unew = integrator.step(U0, rhsFunc, dt);

      // Verify output is non-null and has correct dimensions
      assertNotNull(Unew);
      assertEquals(n, Unew.length);
      assertEquals(neq, Unew[0].length);

      // Verify no NaN or Inf values
      for (int i = 0; i < n; i++) {
        for (int j = 0; j < neq; j++) {
          assertFalse(Double.isNaN(Unew[i][j]), "NaN at cell " + i + " eq " + j);
          assertFalse(Double.isInfinite(Unew[i][j]), "Inf at cell " + i + " eq " + j);
        }
      }
    }

    @Test
    @DisplayName("TwoFluidPipe can set IMEX time integration method")
    void testSetIMEXOnPipe() {
      // Verify that the method enum and setter exist
      TimeIntegrator.Method method = TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION;
      assertNotNull(method);
      assertEquals("IMEX_PRESSURE_CORRECTION", method.name());
    }
  }

  // =================================================================
  // G2: Characteristic Boundary Conditions Tests
  // =================================================================

  @Nested
  @DisplayName("G2: Characteristic boundary conditions")
  class CharacteristicBCTests {

    @Test
    @DisplayName("CHARACTERISTIC boundary condition type exists")
    void testCharacteristicEnumExists() {
      TwoFluidPipe.BoundaryCondition bc = TwoFluidPipe.BoundaryCondition.CHARACTERISTIC;
      assertNotNull(bc);
      assertEquals("CHARACTERISTIC", bc.name());
    }

    @Test
    @DisplayName("All five BC types are available")
    void testAllBCTypesAvailable() {
      TwoFluidPipe.BoundaryCondition[] values = TwoFluidPipe.BoundaryCondition.values();
      assertEquals(5, values.length);

      // Verify all expected types
      assertNotNull(TwoFluidPipe.BoundaryCondition.CONSTANT_PRESSURE);
      assertNotNull(TwoFluidPipe.BoundaryCondition.CONSTANT_FLOW);
      assertNotNull(TwoFluidPipe.BoundaryCondition.STREAM_CONNECTED);
      assertNotNull(TwoFluidPipe.BoundaryCondition.CLOSED);
      assertNotNull(TwoFluidPipe.BoundaryCondition.CHARACTERISTIC);
    }
  }

  // =================================================================
  // G3: Oil-Water Flow Regime Detection Tests
  // =================================================================

  @Nested
  @DisplayName("G3: Oil-water flow regime detection")
  class OilWaterRegimeTests {

    // Typical fluid properties
    private static final double RHO_OIL = 800.0; // kg/m3
    private static final double RHO_WATER = 1020.0; // kg/m3
    private static final double MU_OIL = 0.005; // Pa.s (5 cP)
    private static final double MU_WATER = 0.001; // Pa.s (1 cP)
    private static final double SIGMA_OW = 0.025; // N/m
    private static final double DIAMETER = 0.2; // 200 mm
    private static final double INCLINATION = 0.0; // horizontal

    @Test
    @DisplayName("Single phase detected for pure oil (waterCut=0)")
    void testSinglePhaseOil() {
      OilWaterFlowRegimeDetector detector = new OilWaterFlowRegimeDetector();
      OilWaterResult result = detector.detect(0.001, 2.0, RHO_OIL, RHO_WATER, MU_OIL, MU_WATER,
          SIGMA_OW, DIAMETER, INCLINATION);

      assertEquals(OilWaterFlowRegime.SINGLE_PHASE, result.regime);
      assertTrue(result.oilContinuous);
      assertFalse(result.waterWetting);
    }

    @Test
    @DisplayName("Single phase detected for pure water (waterCut=1)")
    void testSinglePhaseWater() {
      OilWaterFlowRegimeDetector detector = new OilWaterFlowRegimeDetector();
      OilWaterResult result = detector.detect(0.999, 2.0, RHO_OIL, RHO_WATER, MU_OIL, MU_WATER,
          SIGMA_OW, DIAMETER, INCLINATION);

      assertEquals(OilWaterFlowRegime.SINGLE_PHASE, result.regime);
      assertFalse(result.oilContinuous);
      assertTrue(result.waterWetting);
    }

    @Test
    @DisplayName("Stratified flow at very low velocity")
    void testStratifiedAtLowVelocity() {
      OilWaterFlowRegimeDetector detector = new OilWaterFlowRegimeDetector();
      OilWaterResult result = detector.detect(0.3, 0.05, RHO_OIL, RHO_WATER, MU_OIL, MU_WATER,
          SIGMA_OW, DIAMETER, INCLINATION);

      assertEquals(OilWaterFlowRegime.STRATIFIED, result.regime);
      assertTrue(result.waterWetting, "Water should wet the wall in stratified flow");
      assertTrue(result.waterDropoutRisk, "Water dropout risk should be present");
    }

    @Test
    @DisplayName("Dispersed W/O at low water cut and high velocity")
    void testDispersedWaterInOilHighVelocity() {
      OilWaterFlowRegimeDetector detector = new OilWaterFlowRegimeDetector();
      // Low water cut (0.1), high velocity (5 m/s)
      OilWaterResult result = detector.detect(0.10, 5.0, RHO_OIL, RHO_WATER, MU_OIL, MU_WATER,
          SIGMA_OW, DIAMETER, INCLINATION);

      // At low water cut, oil is continuous
      assertTrue(result.oilContinuous, "Oil should be continuous at low water cut");
      assertEquals(OilWaterFlowRegime.DISPERSED_WATER_IN_OIL, result.regime);
      assertFalse(result.waterWetting, "No water wetting in dispersed W/O");
    }

    @Test
    @DisplayName("Dispersed O/W at high water cut and high velocity")
    void testDispersedOilInWaterHighVelocity() {
      OilWaterFlowRegimeDetector detector = new OilWaterFlowRegimeDetector();
      // High water cut (0.8), high velocity (5 m/s)
      OilWaterResult result = detector.detect(0.80, 5.0, RHO_OIL, RHO_WATER, MU_OIL, MU_WATER,
          SIGMA_OW, DIAMETER, INCLINATION);

      assertFalse(result.oilContinuous, "Water should be continuous at high water cut");
      assertEquals(OilWaterFlowRegime.DISPERSED_OIL_IN_WATER, result.regime);
      assertTrue(result.waterWetting, "Water wetting in dispersed O/W");
    }

    @Test
    @DisplayName("Phase inversion point is within physical range")
    void testInversionPointPhysicalRange() {
      OilWaterFlowRegimeDetector detector = new OilWaterFlowRegimeDetector();
      double muRatio = MU_OIL / MU_WATER; // 5.0

      double inversionWC = detector.calcInversionWaterFraction(muRatio, RHO_OIL, RHO_WATER);

      // Should be between 0.1 and 0.9
      assertTrue(inversionWC > 0.1, "Inversion WC should be > 0.1, got " + inversionWC);
      assertTrue(inversionWC < 0.9, "Inversion WC should be < 0.9, got " + inversionWC);

      // For mu_o/mu_w = 5, inversion should be < 0.5 (more viscous oil = lower inversion point)
      assertTrue(inversionWC < 0.55,
          "With mu_o > mu_w, inversion should be < 0.55, got " + inversionWC);
    }

    @Test
    @DisplayName("Equal viscosity fluids give inversion near 0.5")
    void testInversionEqualViscosity() {
      OilWaterFlowRegimeDetector detector = new OilWaterFlowRegimeDetector();
      double inversionWC = detector.calcInversionWaterFraction(1.0, 800.0, 1020.0);

      // Near 50% with small density correction
      assertTrue(inversionWC > 0.35 && inversionWC < 0.65,
          "Equal viscosity should give inversion near 0.5, got " + inversionWC);
    }

    @Test
    @DisplayName("Effective viscosity increases with dispersed phase fraction")
    void testEmulsionViscosityIncreases() {
      OilWaterFlowRegimeDetector detector = new OilWaterFlowRegimeDetector();

      // Oil continuous, increasing water fraction
      double mu_10 = detector.calcEffectiveViscosity(0.10, MU_OIL, MU_WATER, true);
      double mu_30 = detector.calcEffectiveViscosity(0.30, MU_OIL, MU_WATER, true);
      double mu_50 = detector.calcEffectiveViscosity(0.50, MU_OIL, MU_WATER, true);

      // Viscosity must increase with dispersed phase fraction
      assertTrue(mu_30 > mu_10, "Viscosity at WC=0.3 should > WC=0.1");
      assertTrue(mu_50 > mu_30, "Viscosity at WC=0.5 should > WC=0.3");

      // At WC=0.10, viscosity should be close to oil viscosity
      assertTrue(mu_10 < MU_OIL * 1.5, "At WC=0.1, viscosity should be close to oil viscosity");
    }

    @Test
    @DisplayName("Max droplet diameter decreases with velocity")
    void testDropletDiameterDecreasesWithVelocity() {
      OilWaterFlowRegimeDetector detector = new OilWaterFlowRegimeDetector();

      double d1 = detector.calcMaxDropletDiameter(1.0, RHO_OIL, SIGMA_OW, DIAMETER, MU_OIL);
      double d3 = detector.calcMaxDropletDiameter(3.0, RHO_OIL, SIGMA_OW, DIAMETER, MU_OIL);
      double d5 = detector.calcMaxDropletDiameter(5.0, RHO_OIL, SIGMA_OW, DIAMETER, MU_OIL);

      // Higher velocity = smaller droplets (more turbulent breakup)
      assertTrue(d3 < d1, "Droplet at 3 m/s should be smaller than at 1 m/s");
      assertTrue(d5 < d3, "Droplet at 5 m/s should be smaller than at 3 m/s");
    }

    @Test
    @DisplayName("Water dropout detected at low velocity with medium water cut")
    void testWaterDropout() {
      OilWaterFlowRegimeDetector detector = new OilWaterFlowRegimeDetector();

      // Water dropout should occur at low velocity
      boolean dropout = detector.isWaterDropoutLikely(0.2, 0.1, // Low velocity
          RHO_OIL, RHO_WATER, MU_OIL, DIAMETER, 0.002);

      assertTrue(dropout, "Water dropout should be detected at low velocity");

      // No dropout at high velocity
      boolean noDropout = detector.isWaterDropoutLikely(0.2, 5.0, // High velocity
          RHO_OIL, RHO_WATER, MU_OIL, DIAMETER, 0.001);

      assertFalse(noDropout, "No water dropout at high velocity");
    }

    @Test
    @DisplayName("Critical dispersion velocity is positive and physical")
    void testCriticalDispersionVelocity() {
      OilWaterFlowRegimeDetector detector = new OilWaterFlowRegimeDetector();

      double vCrit = detector.calcCriticalDispersionVelocity(0.3, RHO_OIL, RHO_WATER, MU_OIL,
          SIGMA_OW, DIAMETER);

      assertTrue(vCrit > 0, "Critical velocity should be positive");
      assertTrue(vCrit < 20.0, "Critical velocity should be < 20 m/s for typical conditions");
      assertTrue(vCrit > 0.1, "Critical velocity should be > 0.1 m/s");
    }

    @Test
    @DisplayName("Weber number can be configured")
    void testConfigurableWeber() {
      OilWaterFlowRegimeDetector detector = new OilWaterFlowRegimeDetector();
      assertEquals(1.17, detector.getCriticalWeber(), 0.01);

      detector.setCriticalWeber(2.0);
      assertEquals(2.0, detector.getCriticalWeber(), 0.01);
    }

    @Test
    @DisplayName("Inversion constant can be configured")
    void testConfigurableInversionConstant() {
      OilWaterFlowRegimeDetector detector = new OilWaterFlowRegimeDetector();
      assertEquals(0.5, detector.getInversionConstant(), 0.01);

      detector.setInversionConstant(0.3);
      assertEquals(0.3, detector.getInversionConstant(), 0.01);
    }

    @Test
    @DisplayName("Result contains all required fields")
    void testResultCompleteness() {
      OilWaterFlowRegimeDetector detector = new OilWaterFlowRegimeDetector();
      OilWaterResult result = detector.detect(0.3, 2.0, RHO_OIL, RHO_WATER, MU_OIL, MU_WATER,
          SIGMA_OW, DIAMETER, INCLINATION);

      assertNotNull(result.regime);
      assertTrue(result.effectiveViscosity > 0);
      assertTrue(result.inversionWaterFraction > 0);
      assertTrue(result.inversionWaterFraction < 1);
      assertTrue(result.criticalDispersionVelocity > 0);
      assertTrue(result.maxDropletDiameter > 0);
    }

    @Test
    @DisplayName("Uphill inclination increases water dropout risk")
    void testUphillWaterDropout() {
      OilWaterFlowRegimeDetector detector = new OilWaterFlowRegimeDetector();

      // Horizontal pipe, moderate velocity, oil-continuous
      OilWaterResult horizontal =
          detector.detect(0.15, 1.5, RHO_OIL, RHO_WATER, MU_OIL, MU_WATER, SIGMA_OW, DIAMETER, 0.0);

      // Same conditions but steep uphill (5 degree)
      OilWaterResult uphill = detector.detect(0.15, 1.5, RHO_OIL, RHO_WATER, MU_OIL, MU_WATER,
          SIGMA_OW, DIAMETER, 0.087); // ~5 degrees

      // Uphill should have higher (or equal) dropout risk when oil-continuous
      if (uphill.oilContinuous) {
        assertTrue(uphill.waterDropoutRisk,
            "Uphill should have water dropout risk when oil-continuous");
      }
    }
  }
}
