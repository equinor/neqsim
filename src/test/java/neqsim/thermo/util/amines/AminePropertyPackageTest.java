package neqsim.thermo.util.amines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;

/**
 * Tests for the improved amine property package.
 *
 * <p>
 * Validates heat of absorption calculations and the AmineSystem convenience wrapper for MEA, DEA,
 * MDEA, and aMDEA systems. Heat values are negative (exothermic convention), so tests compare
 * absolute values.
 * </p>
 */
class AminePropertyPackageTest {

  @Test
  void testMEAHeatOfAbsorption() {
    AmineHeatOfAbsorption calc = new AmineHeatOfAbsorption();
    calc.setAmineType(AmineHeatOfAbsorption.AmineType.MEA);
    calc.setTemperature(273.15 + 40.0);
    calc.setAmineConcentration(0.30);
    calc.setCO2Loading(0.3);
    double absHeat = Math.abs(calc.calcHeatOfAbsorptionCO2());
    assertTrue(absHeat > 60.0 && absHeat < 100.0,
        "MEA |heat| should be 60-100 kJ/mol, got " + absHeat);
  }

  @Test
  void testDEAHeatOfAbsorption() {
    AmineHeatOfAbsorption calc = new AmineHeatOfAbsorption();
    calc.setAmineType(AmineHeatOfAbsorption.AmineType.DEA);
    calc.setTemperature(273.15 + 40.0);
    calc.setAmineConcentration(0.30);
    calc.setCO2Loading(0.3);
    double absHeat = Math.abs(calc.calcHeatOfAbsorptionCO2());
    assertTrue(absHeat > 50.0 && absHeat < 90.0,
        "DEA |heat| should be 50-90 kJ/mol, got " + absHeat);
  }

  @Test
  void testMDEAHeatOfAbsorption() {
    AmineHeatOfAbsorption calc = new AmineHeatOfAbsorption();
    calc.setAmineType(AmineHeatOfAbsorption.AmineType.MDEA);
    calc.setTemperature(273.15 + 40.0);
    calc.setAmineConcentration(0.50);
    calc.setCO2Loading(0.3);
    double absHeat = Math.abs(calc.calcHeatOfAbsorptionCO2());
    assertTrue(absHeat > 30.0 && absHeat < 70.0,
        "MDEA |heat| should be 30-70 kJ/mol, got " + absHeat);
  }

  @Test
  void testH2SHeatOfAbsorption() {
    AmineHeatOfAbsorption calc = new AmineHeatOfAbsorption();
    calc.setAmineType(AmineHeatOfAbsorption.AmineType.MEA);
    calc.setTemperature(273.15 + 40.0);
    double absHeat = Math.abs(calc.calcHeatOfAbsorptionH2S());
    assertTrue(absHeat > 20.0 && absHeat < 40.0,
        "H2S |heat| should be 20-40 kJ/mol, got " + absHeat);
  }

  @Test
  void testTotalHeatReleased() {
    AmineHeatOfAbsorption calc = new AmineHeatOfAbsorption();
    calc.setAmineType(AmineHeatOfAbsorption.AmineType.MEA);
    calc.setTemperature(273.15 + 40.0);
    calc.setAmineConcentration(0.30);
    calc.setCO2Loading(0.4);
    double totalHeat = calc.calcTotalHeatReleased();
    assertTrue(Math.abs(totalHeat) > 0, "Total heat should be nonzero, got " + totalHeat);
  }

  @Test
  void testAbsorptionPropertiesMap() {
    AmineHeatOfAbsorption calc = new AmineHeatOfAbsorption();
    calc.setAmineType(AmineHeatOfAbsorption.AmineType.MEA);
    calc.setTemperature(273.15 + 40.0);
    calc.setAmineConcentration(0.30);
    calc.setCO2Loading(0.3);
    Map<String, Double> props = calc.getAbsorptionProperties();
    assertNotNull(props);
    assertTrue(props.containsKey("heatOfAbsorptionCO2_kJmol"));
    assertTrue(props.containsKey("heatOfAbsorptionH2S_kJmol"));
    assertTrue(props.containsKey("totalHeatReleased_kJmolAmine"));
  }

  @Test
  void testAmineSystemMEACreation() {
    AmineSystem amineSys = new AmineSystem(AmineSystem.AmineType.MEA, 273.15 + 40.0, 2.0);
    amineSys.setAmineConcentration(0.30);
    amineSys.setCO2Loading(0.1);
    SystemInterface system = amineSys.createSystem();
    assertNotNull(system);
    assertTrue(system.getNumberOfComponents() > 5);
  }

  @Test
  void testAmineSystemMDEACreation() {
    AmineSystem amineSys = new AmineSystem(AmineSystem.AmineType.MDEA, 273.15 + 40.0, 2.0);
    amineSys.setAmineConcentration(0.50);
    amineSys.setCO2Loading(0.1);
    SystemInterface system = amineSys.createSystem();
    assertNotNull(system);
    assertTrue(system.getNumberOfComponents() > 4);
  }

  @Test
  void testAmineSystemAMDEACreation() {
    AmineSystem amineSys = new AmineSystem(AmineSystem.AmineType.AMDEA, 273.15 + 40.0, 2.0);
    amineSys.setAmineConcentration(0.45);
    amineSys.setPiperazineConcentration(0.05);
    amineSys.setCO2Loading(0.1);
    SystemInterface system = amineSys.createSystem();
    assertNotNull(system);
    assertTrue(system.getNumberOfComponents() > 6);
  }

  @Test
  void testHeatMagnitudeDecreasesWithLoading() {
    AmineHeatOfAbsorption calc = new AmineHeatOfAbsorption();
    calc.setAmineType(AmineHeatOfAbsorption.AmineType.MEA);
    calc.setTemperature(273.15 + 40.0);
    calc.setAmineConcentration(0.30);
    calc.setCO2Loading(0.1);
    double absLow = Math.abs(calc.calcHeatOfAbsorptionCO2());
    calc.setCO2Loading(0.45);
    double absHigh = Math.abs(calc.calcHeatOfAbsorptionCO2());
    assertTrue(absLow > absHigh,
        "|Heat| at 0.1 (" + absLow + ") should exceed |heat| at 0.45 (" + absHigh + ")");
  }

  @Test
  void testMDEAHeatMagnitudeLowerThanMEA() {
    AmineHeatOfAbsorption calc = new AmineHeatOfAbsorption();
    calc.setTemperature(273.15 + 40.0);
    calc.setAmineConcentration(0.30);
    calc.setCO2Loading(0.2);
    calc.setAmineType(AmineHeatOfAbsorption.AmineType.MEA);
    double absMea = Math.abs(calc.calcHeatOfAbsorptionCO2());
    calc.setAmineType(AmineHeatOfAbsorption.AmineType.MDEA);
    double absMdea = Math.abs(calc.calcHeatOfAbsorptionCO2());
    assertTrue(absMdea < absMea,
        "MDEA |heat| (" + absMdea + ") should be < MEA |heat| (" + absMea + ")");
  }

  @Test
  void testAmineSystemToString() {
    AmineSystem amineSys = new AmineSystem(AmineSystem.AmineType.MEA, 273.15 + 40.0, 2.0);
    amineSys.setAmineConcentration(0.30);
    amineSys.setCO2Loading(0.3);
    String result = amineSys.toString();
    assertTrue(result.contains("MEA"));
    assertTrue(result.contains("40.0"));
  }

  @Test
  void testAmineSystemHeatAccessors() {
    AmineSystem amineSys = new AmineSystem(AmineSystem.AmineType.MEA, 273.15 + 40.0, 2.0);
    amineSys.setAmineConcentration(0.30);
    amineSys.setCO2Loading(0.3);
    double absHeatCO2 = Math.abs(amineSys.getHeatOfAbsorptionCO2());
    double absHeatH2S = Math.abs(amineSys.getHeatOfAbsorptionH2S());
    double absTotalHeat = Math.abs(amineSys.getTotalHeatReleased());
    assertTrue(absHeatCO2 > 50.0, "|CO2 heat| > 50, got " + absHeatCO2);
    assertTrue(absHeatH2S > 20.0, "|H2S heat| > 20, got " + absHeatH2S);
    assertTrue(absTotalHeat > 0, "total heat nonzero");
  }

  @Test
  void testDEASystemWithH2S() {
    AmineSystem amineSys = new AmineSystem(AmineSystem.AmineType.DEA, 273.15 + 40.0, 5.0);
    amineSys.setAmineConcentration(0.30);
    amineSys.setCO2Loading(0.1);
    amineSys.setH2SLoading(0.05);
    SystemInterface system = amineSys.createSystem();
    assertNotNull(system);
    assertTrue(system.getNumberOfComponents() > 6);
  }

  @Test
  void testMDEATPFlash() {
    AmineSystem amineSys = new AmineSystem(AmineSystem.AmineType.MDEA, 273.15 + 40.0, 2.0);
    amineSys.setAmineConcentration(0.50);
    amineSys.setCO2Loading(0.1);
    SystemInterface system = amineSys.createSystem();
    assertNotNull(system, "System should exist before flash");
    SystemInterface result = amineSys.runTPflash();
    assertNotNull(result, "Flash result should not be null");
    assertTrue(result.getNumberOfPhases() >= 1);
  }

  @Test
  void testDefaultConstructor() {
    AmineSystem amineSys = new AmineSystem(AmineSystem.AmineType.MDEA);
    amineSys.setAmineConcentration(0.50);
    amineSys.setCO2Loading(0.1);
    SystemInterface system = amineSys.createSystem();
    assertNotNull(system);
    assertEquals(1.01325, system.getPressure(), 0.01);
  }
}
