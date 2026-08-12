package neqsim.thermo.util.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.util.benchmark.ThermodynamicBenchmark.Dataset;
import neqsim.thermo.util.benchmark.ThermodynamicBenchmark.Point;
import neqsim.thermo.util.benchmark.ThermodynamicBenchmark.Property;
import neqsim.thermo.util.benchmark.ThermodynamicBenchmark.Report;

/** Tests for reusable thermodynamic benchmark infrastructure and published H2-CO2 data. */
class ThermodynamicBenchmarkTest {
  @Test
  void loadsPublishedH2CO2DatasetWithProvenance() throws Exception {
    Dataset dataset = H2CO2PhaseEquilibriumData.load();

    assertEquals(24, dataset.getPoints().size());
    assertEquals("10.1063/5.0288386", dataset.getDoi());
    assertTrue(dataset.getCitation().contains("Zhang"));

    Point firstPoint = dataset.getPoints().get(0);
    assertEquals(Property.BUBBLE_POINT_PRESSURE, firstPoint.getProperty());
    assertEquals(243.15, firstPoint.getTemperatureK(), 1.0e-12);
    assertEquals(14.5, firstPoint.getExperimentalValue(), 1.0e-12);
    assertEquals(0.96, firstPoint.getComposition().get("CO2"), 1.0e-12);
    assertEquals(0.04, firstPoint.getComposition().get("hydrogen"), 1.0e-12);

    Point finalPoint = dataset.getPoints().get(23);
    assertEquals(Property.DEW_POINT_PRESSURE, finalPoint.getProperty());
    assertEquals(80.9, finalPoint.getExperimentalValue(), 1.0e-12);
    assertEquals(0.02, finalPoint.getComposition().get("nitrogen"), 1.0e-12);
  }

  @Test
  void reportsMalformedNumericCsvRowsAsIoFailures() {
    IOException exception = assertThrows(IOException.class,
        () -> H2CO2PhaseEquilibriumData.parseCsvRow("bad,-30,bubble,1.45,0.96,0.04,0.0,bara", 12));

    assertTrue(exception.getMessage().contains("line 12"));
    assertTrue(exception.getCause() instanceof NumberFormatException);
  }

  @Test
  void calculatesAardBiasRmsAndMaximumError() throws Exception {
    Dataset dataset = H2CO2PhaseEquilibriumData.load();
    Report report = ThermodynamicBenchmark.run("synthetic +2 percent", dataset,
        point -> point.getExperimentalValue() * 1.02);

    assertEquals(2.0, report.getAverageAbsoluteRelativeDeviationPercent(), 1.0e-12);
    assertEquals(2.0, report.getBiasPercent(), 1.0e-12);
    assertEquals(2.0, report.getRootMeanSquareRelativeErrorPercent(), 1.0e-12);
    assertEquals(2.0, report.getMaximumAbsoluteRelativeErrorPercent(), 1.0e-12);
    assertEquals(24, report.getRows().size());
  }

  @Test
  void calculatesUncertaintyNormalizedResidualWhenAvailable() throws Exception {
    Map<String, Double> composition = new LinkedHashMap<String, Double>();
    composition.put("CO2", 0.96);
    composition.put("hydrogen", 0.04);
    Point point = new Point(Property.BUBBLE_POINT_PRESSURE, 273.15, 36.5, 36.5, 0.5, "bara", composition);
    Dataset dataset = new Dataset("uncertainty test", "test citation", "10.0000/test", "test data",
        java.util.Collections.singletonList(point));

    Report report = ThermodynamicBenchmark.run("test model", dataset, pointValue -> 37.5);

    assertEquals(2.0, report.getRows().get(0).getUncertaintyNormalizedResidual(), 1.0e-12);
  }

  @Test
  void rejectsCompositionThatDoesNotSumToOne() {
    Map<String, Double> composition = new LinkedHashMap<String, Double>();
    composition.put("CO2", 0.90);
    composition.put("hydrogen", 0.04);

    assertThrows(IllegalArgumentException.class,
        () -> new Point(Property.BUBBLE_POINT_PRESSURE, 273.15, 36.5, 36.5, Double.NaN, "bara", composition));
  }

  @Test
  void rejectsInvalidStateValueAndUncertainty() {
    Map<String, Double> composition = new LinkedHashMap<String, Double>();
    composition.put("CO2", 0.96);
    composition.put("hydrogen", 0.04);

    assertThrows(IllegalArgumentException.class,
        () -> new Point(Property.BUBBLE_POINT_PRESSURE, 273.15, 0.0, 36.5, Double.NaN, "bara", composition));
    assertThrows(IllegalArgumentException.class,
        () -> new Point(Property.BUBBLE_POINT_PRESSURE, 273.15, 36.5, 0.0, Double.NaN, "bara", composition));
    assertThrows(IllegalArgumentException.class,
        () -> new Point(Property.BUBBLE_POINT_PRESSURE, 273.15, 36.5, 36.5, -0.1, "bara", composition));
  }

  @Test
  void exposesConfiguredNeqSimModel() {
    NeqSimPhaseEquilibriumPrediction prediction = new NeqSimPhaseEquilibriumPrediction(
        NeqSimPhaseEquilibriumPrediction.Model.GERG_2008_H2);

    assertEquals(NeqSimPhaseEquilibriumPrediction.Model.GERG_2008_H2, prediction.getModel());
  }

  @Test
  void returnsPredictionInPointPressureUnit() throws Exception {
    Point source = H2CO2PhaseEquilibriumData.load().getPoints().get(0);
    Point pressureInMegapascal = new Point(source.getProperty(), source.getTemperatureK(),
        source.getPressureBara(), source.getExperimentalValue() / 10.0, source.getStandardUncertainty(),
        "MPa", source.getComposition());
    NeqSimPhaseEquilibriumPrediction prediction =
        new NeqSimPhaseEquilibriumPrediction(NeqSimPhaseEquilibriumPrediction.Model.SRK);

    double predictionBara = prediction.predict(source);
    double predictionMegapascal = prediction.predict(pressureInMegapascal);

    assertTrue(Double.isFinite(predictionMegapascal));
    assertEquals(predictionBara, predictionMegapascal * 10.0, 1.0e-8);
  }

  @Test
  void fitsScalarObjectiveWithinBounds() throws Exception {
    Dataset dataset = H2CO2PhaseEquilibriumData.load();
    BinaryInteractionParameterFitter fitter = new BinaryInteractionParameterFitter(dataset,
        parameter -> point -> point.getExperimentalValue() * (1.0 + parameter - 0.125));

    BinaryInteractionParameterFitter.Result result = fitter.fit(-0.2, 0.3, 1.0e-8, 50);

    assertEquals(0.125, result.getBinaryInteractionParameter(), 1.0e-6);
    assertTrue(result.getRootMeanSquareRelativeErrorPercent() < 1.0e-5);
  }

  @Test
  void rejectsCustomKijForNonCubicModel() {
    assertThrows(IllegalArgumentException.class,
        () -> new NeqSimPhaseEquilibriumPrediction(NeqSimPhaseEquilibriumPrediction.Model.GERG_2008_H2, 0.1));
  }

  @Test
  void exposesCustomKijForCubicModel() {
    NeqSimPhaseEquilibriumPrediction prediction = new NeqSimPhaseEquilibriumPrediction(
        NeqSimPhaseEquilibriumPrediction.Model.PR, -0.1);

    assertEquals(-0.1, prediction.getHydrogenCarbonDioxideKij(), 1.0e-12);
  }
}
