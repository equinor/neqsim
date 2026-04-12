package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.BiomassCharacterization;

/**
 * Tests for {@link PyrolysisReactor}.
 */
class PyrolysisReactorTest {

  @Test
  void testFastPyrolysisWoodChips() {
    BiomassCharacterization wood = BiomassCharacterization.library("wood_chips");

    PyrolysisReactor reactor = new PyrolysisReactor("FastPyro");
    reactor.setBiomass(wood, 1000.0);
    reactor.setPyrolysisMode(PyrolysisReactor.PyrolysisMode.FAST);
    reactor.setPyrolysisTemperature(500.0, "C");
    reactor.run();

    assertNotNull(reactor.getGasOutStream(), "Gas stream should not be null");
    assertNotNull(reactor.getBioOilOutStream(), "Bio-oil stream should not be null");
    assertNotNull(reactor.getBiocharOutStream(), "Biochar stream should not be null");

    // Fast pyrolysis should maximise bio-oil
    assertTrue(reactor.getActualBioOilYield() > reactor.getActualCharYield(),
        "Bio-oil yield should exceed char yield in fast mode");
    assertTrue(reactor.getActualBioOilYield() > reactor.getActualGasYield(),
        "Bio-oil yield should exceed gas yield in fast mode");

    // Yields should sum to approximately 1.0
    double sum =
        reactor.getActualCharYield() + reactor.getActualBioOilYield() + reactor.getActualGasYield();
    assertEquals(1.0, sum, 0.01, "Yields should sum to 1.0");
  }

  @Test
  void testSlowPyrolysis() {
    BiomassCharacterization straw = BiomassCharacterization.library("straw");

    PyrolysisReactor reactor = new PyrolysisReactor("SlowPyro");
    reactor.setBiomass(straw, 500.0);
    reactor.setPyrolysisMode(PyrolysisReactor.PyrolysisMode.SLOW);
    reactor.setPyrolysisTemperature(400.0, "C");
    reactor.run();

    assertNotNull(reactor.getGasOutStream());
    // Slow pyrolysis should have higher char yield than fast
    assertTrue(reactor.getActualCharYield() >= 0.25,
        "Slow pyrolysis char yield should be at least 25%");
  }

  @Test
  void testFlashPyrolysis() {
    BiomassCharacterization bagasse = BiomassCharacterization.library("bagasse");

    PyrolysisReactor reactor = new PyrolysisReactor("FlashPyro");
    reactor.setBiomass(bagasse, 800.0);
    reactor.setPyrolysisMode(PyrolysisReactor.PyrolysisMode.FLASH);
    reactor.setPyrolysisTemperature(600.0, "C");
    reactor.run();

    // Flash should have very low char yield
    assertTrue(reactor.getActualCharYield() <= 0.20, "Flash pyrolysis char yield should be low");
    assertTrue(reactor.getGasLHVMjPerNm3() > 0.0, "Gas LHV should be positive");
  }

  @Test
  void testUserDefinedYields() {
    BiomassCharacterization wood = BiomassCharacterization.library("wood_chips");

    PyrolysisReactor reactor = new PyrolysisReactor("Custom");
    reactor.setBiomass(wood, 1000.0);
    reactor.setProductYields(0.30, 0.45, 0.25);
    reactor.setPyrolysisTemperature(500.0, "C");
    reactor.run();

    assertEquals(0.30, reactor.getActualCharYield(), 1e-6);
    assertEquals(0.45, reactor.getActualBioOilYield(), 1e-6);
    assertEquals(0.25, reactor.getActualGasYield(), 1e-6);
  }

  @Test
  void testHighTemperatureShiftsYieldsToGas() {
    BiomassCharacterization wood = BiomassCharacterization.library("wood_chips");

    PyrolysisReactor lowT = new PyrolysisReactor("LowT");
    lowT.setBiomass(wood, 1000.0);
    lowT.setPyrolysisMode(PyrolysisReactor.PyrolysisMode.FAST);
    lowT.setPyrolysisTemperature(500.0, "C");
    lowT.run();

    PyrolysisReactor highT = new PyrolysisReactor("HighT");
    highT.setBiomass(wood, 1000.0);
    highT.setPyrolysisMode(PyrolysisReactor.PyrolysisMode.FAST);
    highT.setPyrolysisTemperature(700.0, "C");
    highT.run();

    assertTrue(highT.getActualGasYield() > lowT.getActualGasYield(),
        "Higher T should increase gas yield");
    assertTrue(highT.getActualCharYield() < lowT.getActualCharYield(),
        "Higher T should decrease char yield");
  }

  @Test
  void testEnergyYieldIsReasonable() {
    BiomassCharacterization wood = BiomassCharacterization.library("wood_chips");

    PyrolysisReactor reactor = new PyrolysisReactor("Energy");
    reactor.setBiomass(wood, 1000.0);
    reactor.setPyrolysisTemperature(500.0, "C");
    reactor.run();

    assertTrue(reactor.getEnergyYield() > 0.0, "Energy yield should be positive");
  }

  @Test
  void testGetResults() {
    BiomassCharacterization rice = BiomassCharacterization.library("rice_husk");

    PyrolysisReactor reactor = new PyrolysisReactor("Results");
    reactor.setBiomass(rice, 1000.0);
    reactor.setPyrolysisTemperature(500.0, "C");
    reactor.run();

    Map<String, Object> results = reactor.getResults();
    assertNotNull(results);
    assertTrue(results.containsKey("pyrolysisMode"));
    assertTrue(results.containsKey("charYield"));
    assertTrue(results.containsKey("bioOilYield"));
    assertTrue(results.containsKey("gasYield"));
    assertTrue(results.containsKey("gasComposition_molFrac"));
  }

  @Test
  void testToJson() {
    BiomassCharacterization wood = BiomassCharacterization.library("wood_chips");

    PyrolysisReactor reactor = new PyrolysisReactor("JSON");
    reactor.setBiomass(wood, 500.0);
    reactor.setPyrolysisTemperature(500.0, "C");
    reactor.run();

    String json = reactor.toJson();
    assertNotNull(json);
    assertTrue(json.contains("charYield"));
    assertTrue(json.contains("bioOilYield"));
  }

  @Test
  void testToStringBeforeAndAfterRun() {
    BiomassCharacterization wood = BiomassCharacterization.library("wood_chips");
    PyrolysisReactor reactor = new PyrolysisReactor("Str");

    String beforeRun = reactor.toString();
    assertTrue(beforeRun.contains("not yet run"));

    reactor.setBiomass(wood, 1000.0);
    reactor.setPyrolysisTemperature(500.0, "C");
    reactor.run();

    String afterRun = reactor.toString();
    assertTrue(afterRun.contains("Yields"));
    assertTrue(afterRun.contains("Gas LHV"));
  }

  @Test
  void testOutletStreams() {
    BiomassCharacterization wood = BiomassCharacterization.library("wood_chips");

    PyrolysisReactor reactor = new PyrolysisReactor("Outlets");
    reactor.setBiomass(wood, 1000.0);
    reactor.setPyrolysisTemperature(500.0, "C");
    reactor.run();

    assertEquals(3, reactor.getOutletStreams().size(), "Should have 3 outlet streams");
  }
}
