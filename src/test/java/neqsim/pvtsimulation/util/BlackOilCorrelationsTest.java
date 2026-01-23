package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for BlackOilCorrelations.
 */
class BlackOilCorrelationsTest {
  @Test
  void testSpecificGravityToAPI() {
    // Water (SG = 1.0) should give API = 10
    double api = BlackOilCorrelations.apiFromSpecificGravity(1.0);
    assertEquals(10.0, api, 0.01);

    // Light oil (SG = 0.85) should give API ≈ 35
    api = BlackOilCorrelations.apiFromSpecificGravity(0.85);
    assertEquals(34.97, api, 0.1);

    // Heavy oil (SG = 0.95) should give API ≈ 17
    api = BlackOilCorrelations.apiFromSpecificGravity(0.95);
    assertEquals(17.45, api, 0.1);
  }

  @Test
  void testAPIToSpecificGravity() {
    // Round trip conversion
    double sg = 0.85;
    double api = BlackOilCorrelations.apiFromSpecificGravity(sg);
    double sgBack = BlackOilCorrelations.specificGravityFromAPI(api);
    assertEquals(sg, sgBack, 0.001);
  }

  @Test
  void testBubblePointStanding() {
    // Example: Rs = 500 scf/STB, γg = 0.8, API = 35, T = 200°F
    double Rs = 500;
    double gammaG = 0.8;
    double api = 35;
    double T = 200;

    double Pb = BlackOilCorrelations.bubblePointStanding(Rs, gammaG, api, T, true);

    // Should be reasonable value (1000-3000 psia for this case)
    assertTrue(Pb > 1000 && Pb < 4000, "Bubble point should be between 1000-4000 psia, got " + Pb);
  }

  @Test
  void testBubblePointVasquezBeggs() {
    double Rs = 500;
    double gammaG = 0.8;
    double api = 35;
    double T = 200;

    double Pb = BlackOilCorrelations.bubblePointVasquezBeggs(Rs, gammaG, api, T);

    assertTrue(Pb > 1000 && Pb < 4000, "Bubble point should be between 1000-4000 psia, got " + Pb);
  }

  @Test
  void testSolutionGORStanding() {
    double p = 2000; // psia
    double gammaG = 0.8;
    double api = 35;
    double T = 200;

    double Rs = BlackOilCorrelations.solutionGORStanding(p, gammaG, api, T);

    // Should be reasonable (300-800 scf/STB)
    assertTrue(Rs > 200 && Rs < 1000, "Solution GOR should be between 200-1000 scf/STB, got " + Rs);
  }

  @Test
  void testOilFVFStanding() {
    double Rs = 500;
    double gammaG = 0.8;
    double gammaO = 0.85;
    double T = 200;

    double Bo = BlackOilCorrelations.oilFVFStanding(Rs, gammaG, gammaO, T);

    // Bo typically 1.1 - 1.8 for this range
    assertTrue(Bo > 1.0 && Bo < 2.0, "Bo should be between 1.0-2.0, got " + Bo);
  }

  @Test
  void testDeadOilViscosityBeggsRobinson() {
    double api = 35;
    double T = 200;

    double muOD = BlackOilCorrelations.deadOilViscosityBeggsRobinson(api, T);

    // Dead oil viscosity typically 1-10 cP for light oil at reservoir temp
    assertTrue(muOD > 0.1 && muOD < 50,
        "Dead oil viscosity should be between 0.1-50 cP, got " + muOD);
  }

  @Test
  void testSaturatedOilViscosityBeggsRobinson() {
    double muOD = 5.0; // cP
    double Rs = 500;

    double muO = BlackOilCorrelations.saturatedOilViscosityBeggsRobinson(muOD, Rs);

    // Saturated oil viscosity should be less than dead oil
    assertTrue(muO < muOD,
        "Saturated viscosity should be less than dead oil, got " + muO + " vs " + muOD);
    assertTrue(muO > 0.1, "Saturated viscosity should be positive, got " + muO);
  }

  @Test
  void testGasViscosityLeeGonzalezEakin() {
    double T = 660; // °R (200°F)
    double rhoG = 5.0; // lb/ft3
    double Mg = 20.0; // g/mol

    double muG = BlackOilCorrelations.gasViscosityLeeGonzalezEakin(T, rhoG, Mg);

    // Gas viscosity typically 0.01-0.03 cP
    assertTrue(muG > 0.005 && muG < 0.1,
        "Gas viscosity should be between 0.005-0.1 cP, got " + muG);
  }

  @Test
  void testUnitConversions() {
    // Temperature
    assertEquals(212.0, BlackOilCorrelations.celsiusToFahrenheit(100.0), 0.01);
    assertEquals(100.0, BlackOilCorrelations.fahrenheitToCelsius(212.0), 0.01);

    // Pressure
    assertEquals(145.038, BlackOilCorrelations.baraToPsia(10.0), 0.01);
    assertEquals(10.0, BlackOilCorrelations.psiaToBara(145.038), 0.01);

    // GOR
    double gorSm3 = 100; // Sm3/Sm3
    double gorScf = BlackOilCorrelations.gorSm3ToScfPerStb(gorSm3);
    double gorBack = BlackOilCorrelations.gorScfToSm3(gorScf);
    assertEquals(gorSm3, gorBack, 0.1);
  }

  @Test
  void testOilCompressibility() {
    double Rs = 500;
    double gammaG = 0.8;
    double api = 35;
    double T = 200;
    double p = 3000;

    double co = BlackOilCorrelations.oilCompressibilityVasquezBeggs(Rs, gammaG, api, T, p);

    // Oil compressibility typically 1e-5 to 3e-5 1/psi
    assertTrue(co > 1e-6 && co < 1e-4,
        "Oil compressibility should be between 1e-6 and 1e-4, got " + co);
  }

  @Test
  void testUndersaturatedOilFVF() {
    double Bob = 1.35; // at bubble point
    double co = 1.5e-5; // 1/psi
    double p = 4000; // psia
    double pb = 2500; // psia

    double Bo = BlackOilCorrelations.oilFVFUndersaturated(Bob, co, p, pb);

    // Bo should decrease above bubble point
    assertTrue(Bo < Bob, "Undersaturated Bo should be less than Bob, got " + Bo + " vs " + Bob);
    assertTrue(Bo > 1.0, "Bo should be greater than 1.0, got " + Bo);
  }

  @Test
  void testGasFVF() {
    double p = 2000; // psia
    double T = 660; // °R
    double z = 0.85;

    double Bg = BlackOilCorrelations.gasFVF(p, T, z);

    // Bg typically 0.005-0.02 rcf/scf at reservoir conditions
    assertTrue(Bg > 0.001 && Bg < 0.05, "Gas FVF should be between 0.001-0.05, got " + Bg);
  }

  // ==================== UNIT-AWARE METHOD TESTS ====================

  @Test
  void testBubblePointStandingWithUnits() {
    // Field units test values
    double Rs_field = 500; // scf/STB
    double gammaG = 0.8;
    double api = 35;
    double T_field = 200; // °F

    // Calculate in field units
    double Pb_field =
        BlackOilCorrelations.bubblePointStanding(Rs_field, gammaG, api, T_field, true);

    // Convert inputs to SI
    double Rs_si = Rs_field * 0.178108; // Sm³/Sm³
    double T_si = (T_field - 32.0) * 5.0 / 9.0; // °C

    // Calculate in SI units
    double Pb_si = BlackOilCorrelations.bubblePointStandingSI(Rs_si, gammaG, api, T_si);

    // Convert field result to bara for comparison
    double Pb_field_to_bara = Pb_field / 14.5038;

    assertEquals(Pb_field_to_bara, Pb_si, Pb_field_to_bara * 0.001,
        "SI and field results should match after conversion");
  }

  @Test
  void testBubblePointWithBlackOilUnitsEnum() {
    double Rs = 89.054; // Sm³/Sm³ (≈500 scf/STB)
    double gammaG = 0.8;
    double api = 35;
    double T = 93.33; // °C (≈200°F)

    // Using SI enum
    double Pb_si = BlackOilCorrelations.bubblePointStanding(Rs, gammaG, api, T, BlackOilUnits.SI);

    // Using NEQSIM enum (Kelvin input)
    double T_kelvin = T + 273.15;
    double Pb_neqsim =
        BlackOilCorrelations.bubblePointStanding(Rs, gammaG, api, T_kelvin, BlackOilUnits.NEQSIM);

    assertEquals(Pb_si, Pb_neqsim, Pb_si * 0.001,
        "SI and NEQSIM results should be identical (both return bara)");
  }

  @Test
  void testSolutionGORWithUnits() {
    // Field units
    double p_field = 2000; // psia
    double gammaG = 0.8;
    double api = 35;
    double T_field = 200; // °F

    double Rs_field = BlackOilCorrelations.solutionGORStanding(p_field, gammaG, api, T_field);

    // SI units
    double p_si = p_field / 14.5038; // bara
    double T_si = (T_field - 32.0) * 5.0 / 9.0; // °C

    double Rs_si = BlackOilCorrelations.solutionGORStandingSI(p_si, gammaG, api, T_si);

    // Convert field result to Sm³/Sm³
    double Rs_field_to_sm3 = Rs_field * 0.178108;

    assertEquals(Rs_field_to_sm3, Rs_si, Rs_field_to_sm3 * 0.001,
        "SI and field GOR results should match");
  }

  @Test
  void testOilFVFWithUnits() {
    double Rs_field = 500;
    double gammaG = 0.8;
    double gammaO = 0.85;
    double T_field = 200;

    double Bo_field = BlackOilCorrelations.oilFVFStanding(Rs_field, gammaG, gammaO, T_field);

    // SI inputs
    double Rs_si = Rs_field * 0.178108;
    double T_si = (T_field - 32.0) * 5.0 / 9.0;

    double Bo_si = BlackOilCorrelations.oilFVFStandingSI(Rs_si, gammaG, gammaO, T_si);

    // Bo is dimensionless ratio - should be identical
    assertEquals(Bo_field, Bo_si, Bo_field * 0.001, "Bo should be the same in all unit systems");
  }

  @Test
  void testDeadOilViscosityWithUnits() {
    double api = 35;
    double T_field = 200; // °F

    double mu_field = BlackOilCorrelations.deadOilViscosityBeggsRobinson(api, T_field);

    // SI
    double T_si = (T_field - 32.0) * 5.0 / 9.0; // °C
    double mu_si = BlackOilCorrelations.deadOilViscosityBeggsRobinsonSI(api, T_si);

    // Convert field cP to SI Pa·s
    double mu_field_to_pas = mu_field * 0.001;

    assertEquals(mu_field_to_pas, mu_si, mu_field_to_pas * 0.01,
        "Viscosity conversion should be accurate");
  }

  @Test
  void testGasViscosityWithUnits() {
    double T_rankine = 660; // °R
    double rho_field = 5.0; // lb/ft³
    double Mg = 20.0;

    double mu_field = BlackOilCorrelations.gasViscosityLeeGonzalezEakin(T_rankine, rho_field, Mg);

    // Convert to SI inputs
    double T_celsius = (T_rankine - 459.67 - 32.0) * 5.0 / 9.0;
    double rho_si = rho_field * 16.01846; // kg/m³

    double mu_si = BlackOilCorrelations.gasViscosityLeeGonzalezEakinSI(T_celsius, rho_si, Mg);

    // Convert field cP to SI Pa·s
    double mu_field_to_pas = mu_field * 0.001;

    assertEquals(mu_field_to_pas, mu_si, mu_field_to_pas * 0.01,
        "Gas viscosity SI conversion should be accurate");
  }

  @Test
  void testBlackOilUnitsConversions() {
    // Test pressure conversion
    assertEquals(145.038, BlackOilUnits.toPsia(10.0, BlackOilUnits.SI), 0.01);
    assertEquals(10.0, BlackOilUnits.fromPsia(145.038, BlackOilUnits.SI), 0.01);

    // Test temperature conversion
    assertEquals(212.0, BlackOilUnits.toFahrenheit(100.0, BlackOilUnits.SI), 0.1);
    assertEquals(100.0, BlackOilUnits.fromFahrenheit(212.0, BlackOilUnits.SI), 0.1);

    // Test Kelvin conversion
    assertEquals(212.0, BlackOilUnits.toFahrenheit(373.15, BlackOilUnits.NEQSIM), 0.1);

    // Test GOR conversion
    double gor_sm3 = 100.0;
    double gor_scf = BlackOilUnits.toScfPerStb(gor_sm3, BlackOilUnits.SI);
    double gor_back = BlackOilUnits.fromScfPerStb(gor_scf, BlackOilUnits.SI);
    assertEquals(gor_sm3, gor_back, 0.01, "GOR round-trip should be accurate");

    // Test viscosity conversion (1 Pa·s = 1000 cP, so 0.001 Pa·s = 1 cP)
    double mu_pas = 0.001; // 1 cP expressed in Pa·s
    double mu_cp = BlackOilUnits.toCentipoise(mu_pas, BlackOilUnits.SI);
    assertEquals(1.0, mu_cp, 0.001, "0.001 Pa·s should equal 1 cP");

    // Test density conversion
    double rho_kgm3 = 800.0;
    double rho_lbft3 = BlackOilUnits.toLbPerFt3(rho_kgm3, BlackOilUnits.SI);
    double rho_back = BlackOilUnits.fromLbPerFt3(rho_lbft3, BlackOilUnits.SI);
    assertEquals(rho_kgm3, rho_back, 0.1, "Density round-trip should be accurate");
  }

  @Test
  void testDefaultUnitsAccessors() {
    // Store original
    BlackOilUnits original = BlackOilCorrelations.getDefaultUnits();

    // Test setter
    BlackOilCorrelations.setDefaultUnits(BlackOilUnits.SI);
    assertEquals(BlackOilUnits.SI, BlackOilCorrelations.getDefaultUnits());

    BlackOilCorrelations.setDefaultUnits(BlackOilUnits.NEQSIM);
    assertEquals(BlackOilUnits.NEQSIM, BlackOilCorrelations.getDefaultUnits());

    // Restore original
    BlackOilCorrelations.setDefaultUnits(original);
  }

  @Test
  void testOilCompressibilityWithUnits() {
    double Rs_field = 500;
    double gammaG = 0.8;
    double api = 35;
    double T_field = 200; // °F
    double p_field = 3000; // psia

    double co_field = BlackOilCorrelations.oilCompressibilityVasquezBeggs(Rs_field, gammaG, api,
        T_field, p_field);

    // SI inputs
    double Rs_si = Rs_field * 0.178108;
    double T_si = (T_field - 32.0) * 5.0 / 9.0;
    double p_si = p_field / 14.5038;

    double co_si =
        BlackOilCorrelations.oilCompressibilityVasquesBeggsS(Rs_si, gammaG, api, T_si, p_si);

    // Convert field 1/psi to SI 1/bara
    double co_field_to_si = co_field * 14.5038;

    assertEquals(co_field_to_si, co_si, co_field_to_si * 0.01,
        "Compressibility conversion should be accurate");
  }
}
