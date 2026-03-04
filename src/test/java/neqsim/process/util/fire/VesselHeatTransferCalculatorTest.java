package neqsim.process.util.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for VesselHeatTransferCalculator class.
 *
 * @author ESOL
 */
public class VesselHeatTransferCalculatorTest {
  @Test
  void testGrashofNumber() {
    // Test Grashof number calculation for vertical vessel wall
    // Typical values: L = 2m, Twall = 300K, Tfluid = 350K, beta ~ 1/T, rho = 10 kg/m³
    // nu = mu/rho ~ 1e-5 m²/s for gas

    double L = 2.0; // m
    double Tfluid = 350.0; // K
    double Twall = 300.0; // K
    double beta = 1.0 / Tfluid; // 1/K for ideal gas
    double nu = 1.5e-5; // m²/s

    double Gr = VesselHeatTransferCalculator.calculateGrashofNumber(L, Tfluid, Twall, beta, nu);

    // Gr = g * beta * dT * L^3 / nu^2
    // = 9.81 * (1/350) * 50 * 8 / (1.5e-5)^2
    // ~ 5e10
    assertTrue(Gr > 1e9, "Grashof number should be large for natural convection");
    assertTrue(Gr < 1e12, "Grashof number should be reasonable");
  }

  @Test
  void testPrandtlNumber() {
    // Test Prandtl number for typical gas
    double Cp = 1000.0; // J/(kg*K)
    double mu = 1.8e-5; // Pa*s
    double k = 0.025; // W/(m*K)

    double Pr = VesselHeatTransferCalculator.calculatePrandtlNumber(Cp, mu, k);

    // Pr = Cp * mu / k = 1000 * 1.8e-5 / 0.025 = 0.72
    assertEquals(0.72, Pr, 0.01, "Prandtl number for air should be ~0.72");
  }

  @Test
  void testRayleighNumber() {
    double Gr = 1e10;
    double Pr = 0.72;

    double Ra = VesselHeatTransferCalculator.calculateRayleighNumber(Gr, Pr);

    assertEquals(7.2e9, Ra, 1e8, "Rayleigh = Grashof * Prandtl");
  }

  @Test
  void testNusseltVerticalSurface() {
    // Test Churchill-Chu correlation for vertical surface
    double Ra = 1e9; // Turbulent regime
    double Pr = 0.72;

    double Nu = VesselHeatTransferCalculator.calculateNusseltVerticalSurface(Ra, Pr);

    // For Ra = 1e9, Nu should be in range 100-200
    assertTrue(Nu > 50, "Nusselt number should indicate convective enhancement");
    assertTrue(Nu < 300, "Nusselt number should be reasonable");
  }

  @Test
  void testNusseltHorizontalCylinder() {
    double Ra = 1e8;
    double Pr = 0.72;

    double Nu = VesselHeatTransferCalculator.calculateNusseltHorizontalCylinder(Ra, Pr);

    assertTrue(Nu > 10, "Horizontal cylinder should have convective enhancement");
    assertTrue(Nu < 200, "Nusselt should be reasonable for this Ra");
  }

  @Test
  void testInternalFilmCoefficient() {
    // Test the full internal film coefficient calculation
    double L = 2.0; // characteristic length [m]
    double Twall = 350.0; // K
    double Tfluid = 300.0; // K
    double k = 0.025; // thermal conductivity [W/(m*K)]
    double Cp = 1000.0; // heat capacity [J/(kg*K)]
    double mu = 1.8e-5; // dynamic viscosity [Pa*s]
    double rho = 1.2; // density [kg/m³]
    boolean isVertical = true;

    double h = VesselHeatTransferCalculator.calculateInternalFilmCoefficient(L, Twall, Tfluid, k,
        Cp, mu, rho, isVertical);

    assertTrue(h > 0, "Film coefficient should be positive");
    assertTrue(h < 50, "Film coefficient should be reasonable for natural convection");
  }

  @Test
  void testMixedConvectionCoefficient() {
    // Test mixed convection (natural + forced)
    double L = 2.0;
    double Twall = 350.0;
    double Tfluid = 300.0;
    double k = 0.025;
    double Cp = 1000.0;
    double mu = 1.8e-5;
    double rho = 1.2;
    double massFlowRate = 0.1; // kg/s
    double inletDiameter = 0.05; // m
    double vesselDiameter = 0.5; // m
    boolean isVertical = true;

    double hMixed = VesselHeatTransferCalculator.calculateMixedConvectionCoefficient(L, Twall,
        Tfluid, massFlowRate, inletDiameter, vesselDiameter, k, Cp, mu, rho, isVertical);

    assertTrue(hMixed > 0, "Mixed convection coefficient should be positive");

    // Also get pure natural convection for comparison
    double hNatural = VesselHeatTransferCalculator.calculateInternalFilmCoefficient(L, Twall,
        Tfluid, k, Cp, mu, rho, isVertical);

    // Mixed should generally be higher, but with low flow rate it might be similar
    assertTrue(hMixed >= hNatural * 0.5, "Mixed should be comparable to or better than natural");
  }

  @Test
  void testReynoldsNumber() {
    double velocity = 5.0; // m/s
    double L = 0.1; // m
    double rho = 1.2; // kg/m³
    double mu = 1.8e-5; // Pa*s

    double Re = VesselHeatTransferCalculator.calculateReynoldsNumber(velocity, L, rho, mu);

    // Re = rho * v * L / mu = 1.2 * 5 * 0.1 / 1.8e-5 = 33333
    assertEquals(33333.0, Re, 100.0, "Reynolds number calculation");
  }

  @Test
  void testNusseltForcedConvection() {
    double Re = 10000; // Turbulent
    double Pr = 0.72;

    double Nu = VesselHeatTransferCalculator.calculateNusseltForcedConvection(Re, Pr);

    assertTrue(Nu > 20, "Should have convective enhancement in turbulent flow");
    assertTrue(Nu < 200, "Should be reasonable");
  }

  @Test
  void testCompleteHeatTransfer() {
    // Test the complete heat transfer calculation
    double L = 1.5;
    double Twall = 400.0;
    double Tfluid = 300.0;
    double k = 0.03;
    double Cp = 1050.0;
    double mu = 2.0e-5;
    double rho = 1.5;
    boolean isVertical = true;

    VesselHeatTransferCalculator.HeatTransferResult result = VesselHeatTransferCalculator
        .calculateCompleteHeatTransfer(L, Twall, Tfluid, k, Cp, mu, rho, isVertical);

    assertTrue(result.getGrashofNumber() > 0, "Gr should be positive");
    assertTrue(result.getPrandtlNumber() > 0, "Pr should be positive");
    assertTrue(result.getRayleighNumber() > 0, "Ra should be positive");
    assertTrue(result.getNusseltNumber() > 0, "Nu should be positive");
    assertTrue(result.getFilmCoefficient() > 0, "h should be positive");
    assertTrue(result.getHeatFlux() != 0, "q should be non-zero");
  }

  @Test
  void testInternalFilmCoefficientWithRealGasBeta() {
    // At high pressure the real-gas beta is larger than 1/T,
    // so the film coefficient should increase.
    double L = 2.0;
    double Twall = 350.0;
    double Tfluid = 300.0;
    double k = 0.025;
    double Cp = 1000.0;
    double mu = 1.8e-5;
    double rho = 1.2;
    boolean isVertical = true;

    double hIdeal = VesselHeatTransferCalculator.calculateInternalFilmCoefficient(L, Twall, Tfluid,
        k, Cp, mu, rho, isVertical);

    // Simulate real-gas beta = 2x ideal-gas value
    double filmT = (Twall + Tfluid) / 2.0;
    double betaReal = 2.0 / filmT;
    double hReal = VesselHeatTransferCalculator.calculateInternalFilmCoefficient(L, Twall, Tfluid,
        k, Cp, mu, rho, isVertical, betaReal);

    assertTrue(hReal > hIdeal, "Higher beta should give higher film coefficient");
  }

  @Test
  void testNusseltImpingingJet() {
    double Re = 50000;
    double Pr = 0.72;
    double hOverD = 6.0;
    double rOverD = 4.0;

    double Nu = VesselHeatTransferCalculator.calculateNusseltImpingingJet(Re, Pr, hOverD, rOverD);

    assertTrue(Nu > 50, "Impinging jet should give significant Nu");
    assertTrue(Nu < 2000, "Nu should be reasonable");
  }

  @Test
  void testNusseltImpingingJetLowReFallback() {
    // Below Re 2000 should fall back to Gnielinski
    double Re = 1000;
    double Pr = 0.72;
    double hOverD = 6.0;
    double rOverD = 4.0;

    double NuJet =
        VesselHeatTransferCalculator.calculateNusseltImpingingJet(Re, Pr, hOverD, rOverD);
    double NuGnielinski = VesselHeatTransferCalculator.calculateNusseltForcedConvection(Re, Pr);

    assertEquals(NuGnielinski, NuJet, 0.01, "Low-Re impinging jet should fall back to Gnielinski");
  }

  @Test
  void testDischargeConvectionCoefficient() {
    double L = 2.0;
    double Twall = 320.0;
    double Tfluid = 280.0;
    double massFlowRate = 0.5;
    double orificeDiameter = 0.01;
    double vesselDiameter = 0.5;
    double k = 0.025;
    double Cp = 1000.0;
    double mu = 1.8e-5;
    double rho = 20.0;
    boolean isVertical = true;

    double hDischarge = VesselHeatTransferCalculator.calculateDischargeConvectionCoefficient(L,
        Twall, Tfluid, massFlowRate, orificeDiameter, vesselDiameter, k, Cp, mu, rho, isVertical);

    double hNatural = VesselHeatTransferCalculator.calculateInternalFilmCoefficient(L, Twall,
        Tfluid, k, Cp, mu, rho, isVertical);

    assertTrue(hDischarge > 0, "Discharge HTC should be positive");
    assertTrue(hDischarge >= hNatural * 0.99,
        "Discharge HTC should be >= natural convection (mixed convection blend)");
  }

  @Test
  void testMixedConvectionWithRealGasBeta() {
    double L = 2.0;
    double Twall = 350.0;
    double Tfluid = 300.0;
    double k = 0.025;
    double Cp = 1000.0;
    double mu = 1.8e-5;
    double rho = 1.2;
    double massFlowRate = 0.1;
    double inletDiameter = 0.05;
    double vesselDiameter = 0.5;
    boolean isVertical = true;

    double hIdeal = VesselHeatTransferCalculator.calculateMixedConvectionCoefficient(L, Twall,
        Tfluid, massFlowRate, inletDiameter, vesselDiameter, k, Cp, mu, rho, isVertical);

    double filmT = (Twall + Tfluid) / 2.0;
    double betaReal = 3.0 / filmT;
    double hReal = VesselHeatTransferCalculator.calculateMixedConvectionCoefficient(L, Twall,
        Tfluid, massFlowRate, inletDiameter, vesselDiameter, k, Cp, mu, rho, isVertical, betaReal);

    assertTrue(hReal > hIdeal, "Higher beta should increase mixed convection coefficient");
  }

  @Test
  void testWettedWallUsesRohsenow() {
    // With wall significantly above saturation, boiling should give h > pure convection
    double Twall = 400.0;
    double Tfluid = 370.0;
    double Tsat = 373.0; // water saturation
    double L = 1.5;
    double k = 0.68; // liquid water
    double Cp = 4186.0;
    double mu = 2.8e-4;
    double rho = 960.0;
    boolean isVertical = true;

    double hWetted = VesselHeatTransferCalculator.calculateWettedWallFilmCoefficient(Twall, Tfluid,
        Tsat, L, k, Cp, mu, rho, isVertical);

    assertTrue(hWetted > 100, "Boiling should give high heat transfer coefficient");
    assertTrue(hWetted <= 5000, "Should be capped at 5000 W/(m2*K)");
  }
}
