package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests strict exact-calculation piping evidence. */
class PlantPipelineEvidenceTest {
  private static final class Fixture {
    private final ProcessSystem process;
    private final PipeBeggsAndBrills pipeline;
    private final String calculationId;
    private final double maximumVelocity;

    private Fixture(ProcessSystem process, PipeBeggsAndBrills pipeline, String calculationId, double maximumVelocity) {
      this.process = process;
      this.pipeline = pipeline;
      this.calculationId = calculationId;
      this.maximumVelocity = maximumVelocity;
    }
  }

  @Test
  void capturesCompletePhysicalProfilesAndLocations() throws Exception {
    Fixture fixture = solvedFixture();
    PlantPipelineEvidence evidence = evidence(fixture, fixture.calculationId, true, true,
        fixture.maximumVelocity * 1.10);

    assertTrue(fixture.process.solved());
    assertTrue(evidence.isComplete(), evidence.getDiagnostics().toString());
    assertTrue(evidence.isFeasible());
    assertEquals(6, evidence.getDefinitions().size());
    assertEquals(6, evidence.getSamples().size());
    assertEquals(6, evidence.getLocations().size());
    assertEquals(fixture.pipeline.getLength(),
        location(evidence, PlantPipelineEvidence.Metric.PRESSURE_DROP).getDistanceMetres(), 1.0e-12);
    assertEquals(fixture.pipeline.getLength(),
        location(evidence, PlantPipelineEvidence.Metric.RECEIVING_PRESSURE).getDistanceMetres(), 1.0e-12);

    String json = evidence.toJson();
    assertFalse(json.contains("NaN"));
    assertFalse(json.contains("Infinity"));
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    assertEquals("AVAILABLE", root.get("status").getAsString());
    assertEquals(6, root.getAsJsonArray("evidence").size());
    assertTrue(root.getAsJsonArray("evidence").get(0).getAsJsonObject().has("distanceMetres"));

    PlantPipelineEvidence restored = serialize(evidence);
    assertEquals(json, restored.toJson());
    assertEquals(evidence.getLocations().size(), restored.getLocations().size());
  }

  @Test
  void changedInstalledLimitIsImmutableAndRestoresExactly() {
    Fixture fixture = solvedFixture();
    PlantPipelineEvidence initial = evidence(fixture, fixture.calculationId, true, true,
        fixture.maximumVelocity * 1.10);
    String initialJson = initial.toJson();

    PlantPipelineEvidence restricted = evidence(fixture, fixture.calculationId, true, true,
        fixture.maximumVelocity * 0.90);
    assertTrue(restricted.isComplete(), restricted.getDiagnostics().toString());
    assertFalse(restricted.isFeasible());
    PlantConstraintSample velocity = sample(restricted, PlantPipelineEvidence.Metric.MIXTURE_VELOCITY);
    assertTrue(velocity.getNormalizedUtilization() > 1.0);
    assertTrue(velocity.getRequiredRelief() > 0.0);
    assertEquals(initialJson, initial.toJson());

    PlantPipelineEvidence restored = evidence(fixture, fixture.calculationId, true, true,
        fixture.maximumVelocity * 1.10);
    assertEquals(initialJson, restored.toJson());
  }

  @Test
  void staleAndUnconvergedCandidatePathsFailClosed() {
    Fixture fixture = solvedFixture();
    PlantPipelineEvidence stale = evidence(fixture, UUID.randomUUID().toString(), true, true,
        fixture.maximumVelocity * 1.10);
    PlantPipelineEvidence unconverged = evidence(fixture, fixture.calculationId, false, true,
        fixture.maximumVelocity * 1.10);

    assertFalse(stale.isComplete());
    assertFalse(stale.isFeasible());
    assertTrue(stale.getDiagnostics().contains("STALE_CALCULATION_IDENTITY"));
    assertTrue(
        stale.getSamples().stream().allMatch(sample -> sample.getStatus() == PlantConstraintSample.SampleStatus.STALE));
    assertFalse(unconverged.isComplete());
    assertFalse(unconverged.isFeasible());
    assertTrue(unconverged.getDiagnostics().contains("INCOMPLETE_CONVERGENCE"));
    assertTrue(unconverged.getSamples().stream()
        .allMatch(sample -> sample.getStatus() == PlantConstraintSample.SampleStatus.INCOMPLETE_CONVERGENCE));
    assertFalse(stale.toJson().contains("NaN"));
    assertFalse(unconverged.toJson().contains("Infinity"));
  }

  @Test
  void missingGeometryProvenanceOrLimitSetRemainsUnavailable() {
    Fixture fixture = solvedFixture();
    PlantPipelineEvidence unverified = evidence(fixture, fixture.calculationId, true, false,
        fixture.maximumVelocity * 1.10);
    PlantPipelineEvidence missingLimit = PlantPipelineEvidence
        .builder("L", "Gathering", fixture.calculationId, fixture.pipeline,
            "synthetic line list and solved operating case")
        .geometryVerified(true).convergenceComplete(true).maximumPressureBara(100.0).maximumPressureDropBar(10.0)
        .minimumReceivingPressureBara(30.0).maximumMixtureVelocityMetresPerSecond(fixture.maximumVelocity * 1.10)
        .minimumTemperatureCelsius(-20.0).build();
    PlantPipelineEvidence nonFiniteLimit = evidence(fixture, fixture.calculationId, true, true,
        Double.POSITIVE_INFINITY);

    assertFalse(unverified.isComplete());
    assertTrue(unverified.getDiagnostics().contains("GEOMETRY_PROVENANCE_NOT_VERIFIED"));
    assertTrue(unverified.getLocations().isEmpty());
    assertFalse(missingLimit.isComplete());
    assertTrue(missingLimit.getDiagnostics().contains("INSTALLED_LIMIT_SET_INCOMPLETE"));
    assertFalse(nonFiniteLimit.isComplete());
    assertTrue(nonFiniteLimit.getDiagnostics().contains("INSTALLED_LIMIT_SET_INCOMPLETE"));
    JsonObject json = JsonParser.parseString(missingLimit.toJson()).getAsJsonObject();
    assertEquals("INCOMPLETE", json.get("status").getAsString());
    assertTrue(json.getAsJsonArray("evidence").get(0).getAsJsonObject().get("sampledValue").isJsonNull());
  }

  @Test
  void unsolvedPipelineDoesNotInventZeroUtilization() {
    SystemInterface fluid = fluid();
    Stream feed = new Stream("unrun feed", fluid);
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("unrun pipe", feed);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.30);
    pipe.setPipeWallRoughness(15.0e-6);
    PlantPipelineEvidence evidence = PlantPipelineEvidence
        .builder("L", "Gathering", UUID.randomUUID().toString(), pipe, "synthetic line list").geometryVerified(true)
        .convergenceComplete(true).maximumPressureBara(100.0).maximumPressureDropBar(10.0)
        .minimumReceivingPressureBara(30.0).maximumMixtureVelocityMetresPerSecond(20.0).minimumTemperatureCelsius(-20.0)
        .maximumTemperatureCelsius(80.0).build();

    assertFalse(evidence.isComplete());
    assertTrue(evidence.getDiagnostics().contains("STALE_CALCULATION_IDENTITY"));
    assertTrue(evidence.getSamples().stream()
        .noneMatch(sample -> sample.getStatus() == PlantConstraintSample.SampleStatus.AVAILABLE));
    JsonObject json = JsonParser.parseString(evidence.toJson()).getAsJsonObject();
    for (int index = 0; index < json.getAsJsonArray("evidence").size(); index++) {
      assertTrue(json.getAsJsonArray("evidence").get(index).getAsJsonObject().get("sampledValue").isJsonNull());
    }
  }

  private Fixture solvedFixture() {
    Stream feed = new Stream("pipeline feed", fluid());
    feed.setFlowRate(25000.0, "kg/hr");
    feed.setPressure(75.0, "bara");
    feed.setTemperature(35.0, "C");
    PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("gathering line", feed);
    pipeline.setLength(1200.0);
    pipeline.setDiameter(0.30);
    pipeline.setPipeWallRoughness(15.0e-6);
    pipeline.setElevation(-20.0);
    pipeline.setNumberOfIncrements(6);
    ProcessSystem process = new ProcessSystem("pipeline evidence fixture");
    process.add(feed);
    process.add(pipeline);
    UUID calculationId = UUID.randomUUID();
    process.run(calculationId);
    double maximumVelocity = maximum(pipeline.getMixtureSuperficialVelocityProfile());
    assertTrue(Double.isFinite(maximumVelocity) && maximumVelocity > 0.0);
    return new Fixture(process, pipeline, calculationId.toString(), maximumVelocity);
  }

  private SystemInterface fluid() {
    SystemInterface fluid = new SystemSrkEos(308.15, 75.0);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("CO2", 0.02);
    fluid.addComponent("methane", 0.82);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private PlantPipelineEvidence evidence(Fixture fixture, String calculationId, boolean converged,
      boolean geometryVerified, double maximumVelocity) {
    return PlantPipelineEvidence
        .builder("L", "Gathering", calculationId, fixture.pipeline, "synthetic line list and solved operating case")
        .geometryVerified(geometryVerified).convergenceComplete(converged).maximumPressureBara(100.0)
        .maximumPressureDropBar(10.0).minimumReceivingPressureBara(30.0)
        .maximumMixtureVelocityMetresPerSecond(maximumVelocity).minimumTemperatureCelsius(-20.0)
        .maximumTemperatureCelsius(80.0).build();
  }

  private PlantPipelineEvidence.ExtremumLocation location(PlantPipelineEvidence evidence,
      PlantPipelineEvidence.Metric metric) {
    for (PlantPipelineEvidence.ExtremumLocation location : evidence.getLocations()) {
      if (location.getMetric() == metric) {
        return location;
      }
    }
    return null;
  }

  private PlantConstraintSample sample(PlantPipelineEvidence evidence, PlantPipelineEvidence.Metric metric) {
    String qualifiedId = null;
    for (PlantConstraintDefinition definition : evidence.getDefinitions()) {
      if (definition.getId().equals(metricId(metric))) {
        qualifiedId = definition.getQualifiedId();
        break;
      }
    }
    for (PlantConstraintSample sample : evidence.getSamples()) {
      if (sample.getQualifiedConstraintId().equals(qualifiedId)) {
        return sample;
      }
    }
    return null;
  }

  private String metricId(PlantPipelineEvidence.Metric metric) {
    switch (metric) {
    case MAXIMUM_PRESSURE:
      return "maximum-pressure";
    case PRESSURE_DROP:
      return "pressure-drop";
    case RECEIVING_PRESSURE:
      return "receiving-pressure";
    case MIXTURE_VELOCITY:
      return "mixture-velocity";
    case MINIMUM_TEMPERATURE:
      return "minimum-temperature";
    case MAXIMUM_TEMPERATURE:
      return "maximum-temperature";
    default:
      return "";
    }
  }

  private double maximum(List<Double> values) {
    double maximum = Double.NEGATIVE_INFINITY;
    for (Double value : values) {
      maximum = Math.max(maximum, value);
    }
    return maximum;
  }

  private PlantPipelineEvidence serialize(PlantPipelineEvidence evidence) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(evidence);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    PlantPipelineEvidence restored = (PlantPipelineEvidence) input.readObject();
    input.close();
    assertNotNull(restored);
    return restored;
  }
}
