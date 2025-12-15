package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.EntrainmentDeposition.EntrainmentResult;
import neqsim.process.equipment.pipeline.twophasepipe.EntrainmentDeposition.EntrainmentModel;
import neqsim.process.equipment.pipeline.twophasepipe.EntrainmentDeposition.DepositionModel;

/**
 * Unit tests for EntrainmentDeposition class.
 */
class EntrainmentDepositionTest {

  private EntrainmentDeposition entrainment;

  // Typical properties for gas-liquid flow
  private static final double GAS_VELOCITY = 15.0; // m/s (high speed for entrainment)
  private static final double LIQUID_VELOCITY = 0.5; // m/s
  private static final double GAS_DENSITY = 50.0; // kg/m³
  private static final double LIQUID_DENSITY = 700.0; // kg/m³
  private static final double GAS_VISCOSITY = 1.5e-5; // Pa·s
  private static final double LIQUID_VISCOSITY = 5e-4; // Pa·s
  private static final double SURFACE_TENSION = 0.02; // N/m
  private static final double DIAMETER = 0.1; // m
  private static final double LIQUID_HOLDUP = 0.1; // fraction

  @BeforeEach
  void setUp() {
    entrainment = new EntrainmentDeposition();
  }

  @Test
  void testNoEntrainmentInStratifiedFlow() {
    EntrainmentResult result = entrainment.calculate(FlowRegime.STRATIFIED_SMOOTH, GAS_VELOCITY,
        LIQUID_VELOCITY, GAS_DENSITY, LIQUID_DENSITY, GAS_VISCOSITY, LIQUID_VISCOSITY,
        SURFACE_TENSION, DIAMETER, LIQUID_HOLDUP);

    assertEquals(0.0, result.entrainmentRate, 1e-10, "No entrainment in stratified flow");
    assertEquals(0.0, result.depositionRate, 1e-10, "No deposition in stratified flow");
    assertFalse(result.isEntraining, "Should not be entraining");
  }

  @Test
  void testEntrainmentInAnnularFlow() {
    EntrainmentResult result = entrainment.calculate(FlowRegime.ANNULAR, GAS_VELOCITY,
        LIQUID_VELOCITY, GAS_DENSITY, LIQUID_DENSITY, GAS_VISCOSITY, LIQUID_VISCOSITY,
        SURFACE_TENSION, DIAMETER, LIQUID_HOLDUP);

    // At high gas velocity, entrainment should occur
    assertTrue(result.entrainmentRate >= 0.0, "Entrainment rate should be non-negative");
  }

  @Test
  void testEntrainmentInMistFlow() {
    EntrainmentResult result = entrainment.calculate(FlowRegime.MIST, GAS_VELOCITY * 2,
        LIQUID_VELOCITY, GAS_DENSITY, LIQUID_DENSITY, GAS_VISCOSITY, LIQUID_VISCOSITY,
        SURFACE_TENSION, DIAMETER, LIQUID_HOLDUP);

    assertTrue(result.entrainmentRate >= 0.0,
        "Entrainment rate should be non-negative in mist flow");
  }

  @Test
  void testDropletDiameterIsPositive() {
    EntrainmentResult result = entrainment.calculate(FlowRegime.ANNULAR, GAS_VELOCITY,
        LIQUID_VELOCITY, GAS_DENSITY, LIQUID_DENSITY, GAS_VISCOSITY, LIQUID_VISCOSITY,
        SURFACE_TENSION, DIAMETER, LIQUID_HOLDUP);

    assertTrue(result.dropletDiameter > 0, "Droplet diameter should be positive");
    assertTrue(result.dropletDiameter < DIAMETER,
        "Droplet diameter should be smaller than pipe diameter");
  }

  @Test
  void testEntrainmentFractionInRange() {
    EntrainmentResult result = entrainment.calculate(FlowRegime.ANNULAR, GAS_VELOCITY,
        LIQUID_VELOCITY, GAS_DENSITY, LIQUID_DENSITY, GAS_VISCOSITY, LIQUID_VISCOSITY,
        SURFACE_TENSION, DIAMETER, LIQUID_HOLDUP);

    assertTrue(result.entrainmentFraction >= 0.0, "Entrainment fraction should be >= 0");
    assertTrue(result.entrainmentFraction <= 1.0, "Entrainment fraction should be <= 1");
  }

  @Test
  void testNetTransferRateIsConsistent() {
    EntrainmentResult result = entrainment.calculate(FlowRegime.ANNULAR, GAS_VELOCITY,
        LIQUID_VELOCITY, GAS_DENSITY, LIQUID_DENSITY, GAS_VISCOSITY, LIQUID_VISCOSITY,
        SURFACE_TENSION, DIAMETER, LIQUID_HOLDUP);

    double expectedNet = result.entrainmentRate - result.depositionRate;
    assertEquals(expectedNet, result.netTransferRate, 1e-10,
        "Net transfer rate should equal entrainment - deposition");
  }

  @Test
  void testFilmReynoldsNumberIsPositive() {
    EntrainmentResult result = entrainment.calculate(FlowRegime.ANNULAR, GAS_VELOCITY,
        LIQUID_VELOCITY, GAS_DENSITY, LIQUID_DENSITY, GAS_VISCOSITY, LIQUID_VISCOSITY,
        SURFACE_TENSION, DIAMETER, LIQUID_HOLDUP);

    assertTrue(result.filmReynoldsNumber > 0, "Film Reynolds number should be positive");
  }

  @Test
  void testDifferentEntrainmentModels() {
    // Test all entrainment models
    for (EntrainmentModel model : EntrainmentModel.values()) {
      entrainment.setEntrainmentModel(model);

      EntrainmentResult result = entrainment.calculate(FlowRegime.ANNULAR, GAS_VELOCITY,
          LIQUID_VELOCITY, GAS_DENSITY, LIQUID_DENSITY, GAS_VISCOSITY, LIQUID_VISCOSITY,
          SURFACE_TENSION, DIAMETER, LIQUID_HOLDUP);

      assertTrue(result.entrainmentRate >= 0.0,
          "Entrainment rate should be non-negative for model " + model);
    }
  }

  @Test
  void testDifferentDepositionModels() {
    // Test all deposition models
    for (DepositionModel model : DepositionModel.values()) {
      entrainment.setDepositionModel(model);

      EntrainmentResult result = entrainment.calculate(FlowRegime.ANNULAR, GAS_VELOCITY,
          LIQUID_VELOCITY, GAS_DENSITY, LIQUID_DENSITY, GAS_VISCOSITY, LIQUID_VISCOSITY,
          SURFACE_TENSION, DIAMETER, LIQUID_HOLDUP);

      assertTrue(result.depositionRate >= 0.0,
          "Deposition rate should be non-negative for model " + model);
    }
  }

  @Test
  void testEntrainmentIncreasesWithGasVelocity() {
    entrainment.setCriticalWeber(5.0); // Lower threshold for testing
    entrainment.setCriticalReFilm(50.0);

    EntrainmentResult resultLow = entrainment.calculate(FlowRegime.ANNULAR, 10.0, LIQUID_VELOCITY,
        GAS_DENSITY, LIQUID_DENSITY, GAS_VISCOSITY, LIQUID_VISCOSITY, SURFACE_TENSION, DIAMETER,
        LIQUID_HOLDUP);

    EntrainmentResult resultHigh = entrainment.calculate(FlowRegime.ANNULAR, 30.0, LIQUID_VELOCITY,
        GAS_DENSITY, LIQUID_DENSITY, GAS_VISCOSITY, LIQUID_VISCOSITY, SURFACE_TENSION, DIAMETER,
        LIQUID_HOLDUP);

    // Higher gas velocity should lead to more entrainment
    if (resultHigh.isEntraining && resultLow.isEntraining) {
      assertTrue(resultHigh.entrainmentRate >= resultLow.entrainmentRate,
          "Entrainment should increase with gas velocity");
    }
  }

  @Test
  void testCriticalWeberGetterSetter() {
    double newWeber = 20.0;
    entrainment.setCriticalWeber(newWeber);
    assertEquals(newWeber, entrainment.getCriticalWeber(), 1e-10);
  }

  @Test
  void testCriticalReFilmGetterSetter() {
    double newReFilm = 200.0;
    entrainment.setCriticalReFilm(newReFilm);
    assertEquals(newReFilm, entrainment.getCriticalReFilm(), 1e-10);
  }

  @Test
  void testNoEntrainmentInSlugFlow() {
    EntrainmentResult result = entrainment.calculate(FlowRegime.SLUG, GAS_VELOCITY, LIQUID_VELOCITY,
        GAS_DENSITY, LIQUID_DENSITY, GAS_VISCOSITY, LIQUID_VISCOSITY, SURFACE_TENSION, DIAMETER,
        LIQUID_HOLDUP);

    assertEquals(0.0, result.entrainmentRate, 1e-10,
        "No entrainment expected in slug flow (not annular/mist)");
  }

  @Test
  void testDropletConcentrationIsNonNegative() {
    EntrainmentResult result = entrainment.calculate(FlowRegime.ANNULAR, GAS_VELOCITY,
        LIQUID_VELOCITY, GAS_DENSITY, LIQUID_DENSITY, GAS_VISCOSITY, LIQUID_VISCOSITY,
        SURFACE_TENSION, DIAMETER, LIQUID_HOLDUP);

    assertTrue(result.dropletConcentration >= 0.0, "Droplet concentration should be non-negative");
  }
}
