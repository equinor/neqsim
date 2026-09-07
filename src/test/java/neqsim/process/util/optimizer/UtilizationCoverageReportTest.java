package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.util.optimizer.EquipmentCapacityConstraintResolver.ResolvedConstraint;
import neqsim.process.util.optimizer.InstalledEquipmentCapacityEvidence.ConstraintOrigin;
import neqsim.process.util.optimizer.UtilizationCoverageReport.Row;
import neqsim.process.util.optimizer.UtilizationCoverageReport.Status;

/** Regressions for expected capacity coverage, frozen evidence, and incomplete installed ratings. */
class UtilizationCoverageReportTest {
  @Test
  void directOverrideKeepsUnrelatedStrategyConstraintsAndDisabledOverride() {
    Map<String, CapacityConstraint> direct = new LinkedHashMap<String, CapacityConstraint>();
    Map<String, CapacityConstraint> strategy = new LinkedHashMap<String, CapacityConstraint>();
    CapacityConstraint speed = rated("speed").setEnabled(false);
    CapacityConstraint power = rated("power");
    direct.put("speed", speed);
    strategy.put("speed", rated("speed").setDesignValue(200.0));
    strategy.put("power", power);

    Map<String, ResolvedConstraint> resolved = EquipmentCapacityConstraintResolver.resolve(direct, strategy);

    assertEquals(Arrays.asList("power", "speed"), Arrays.asList(resolved.keySet().toArray()));
    assertSame(speed, resolved.get("speed").getConstraint());
    assertSame(power, resolved.get("power").getConstraint());
    assertEquals(ConstraintOrigin.DIRECT, resolved.get("speed").getOrigin());
    assertEquals(ConstraintOrigin.STRATEGY, resolved.get("power").getOrigin());
    assertFalse(resolved.get("speed").getConstraint().isEnabled());
    assertThrows(UnsupportedOperationException.class, resolved::clear);
  }

  @Test
  void emptyRegistryCannotHideUndiscoveredEquipmentOrConstraints() {
    Stream equipment = new Stream("K-1");
    equipment.addCapacityConstraint(rated("power").setCurrentValue(60.0));
    UtilizationCoverageReport report = UtilizationCoverageReport.builder("Plant")
        .expectConstraint("Compression", "K-2", "power").expectConstraint("Compression", "K-1", "speed")
        .equipment("Compression", equipment).registry(new PlantConstraintRegistry()).build();

    assertFalse(report.isComplete());
    assertTrue(report.getDiagnostics().contains("equipment:Plant/Compression/K-2=MISSING_EQUIPMENT"));
    assertTrue(report.getDiagnostics().contains("equipment:Plant/Compression/K-1#speed=MISSING_CONSTRAINT"));
    assertTrue(report.getDiagnostics().contains("equipment:Plant/Compression/K-1#power=MISSING_REGISTRATION"));
    assertEquals(3, report.getRequiredConstraintIds().size());
    assertFalse(UtilizationCoverageReport.builder("Plant").build().isComplete());
  }

  @Test
  void unsetCurrentValueAndLimitNeverBecomeZeroUtilization() {
    Stream equipment = new Stream("unrated");
    equipment.addCapacityConstraint(new CapacityConstraint("power"));
    UtilizationCoverageReport report = UtilizationCoverageReport.builder("Plant").equipment("Area", equipment).build();
    Row row = report.getRows().get(0);

    assertFalse(report.isComplete());
    assertTrue(row.getStatuses().containsAll(Arrays.asList(Status.MISSING_CURRENT_VALUE, Status.MISSING_LIMIT,
        Status.MISSING_UNIT, Status.MISSING_BASIS, Status.MISSING_PROVENANCE, Status.SCREENING_ONLY)));
    assertTrue(Double.isNaN(row.getCurrentValue()));
    assertTrue(Double.isNaN(row.getApplicableLimit()));
    assertTrue(Double.isNaN(row.getNormalizedUtilization()));
    JsonObject jsonRow = JsonParser.parseString(report.toJson()).getAsJsonObject().getAsJsonArray("rows").get(0)
        .getAsJsonObject();
    assertTrue(jsonRow.get("currentValue").isJsonNull());
    assertTrue(jsonRow.get("applicableLimit").isJsonNull());
    assertTrue(jsonRow.get("normalizedUtilization").isJsonNull());
  }

  @Test
  void explicitZeroHasCompleteEvidenceWithoutClaimingConvergence() {
    Stream equipment = new Stream("K-1");
    equipment.addCapacityConstraint(rated("power").setCurrentValue(0.0));
    UtilizationCoverageReport report = UtilizationCoverageReport.builder("Plant").equipment("Area", equipment)
        .basis("Area", "K-1", "power", "shaft power").build();

    assertTrue(report.isComplete());
    assertEquals(0.0, report.getRows().get(0).getCurrentValue());
    assertEquals(0.0, report.getRows().get(0).getNormalizedUtilization());
    assertEquals("DECLARED_EQUIPMENT_AND_CONSTRAINTS", report.getCoverageScope());
    assertEquals("", report.getRegistryIdentityDigest());
  }

  @Test
  void supplierIsSampledOnceAndSerializationRetainsFrozenEvidence() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    CapacityConstraint power = rated("power").setValueSupplier(() -> 50.0 + calls.incrementAndGet());
    Stream equipment = new Stream("K-1");
    equipment.addCapacityConstraint(power);
    PlantConstraintRegistry registry = registry(equipment, power);
    UtilizationCoverageReport report = UtilizationCoverageReport.builder("Plant").equipment("Area", equipment)
        .registry(registry).build();
    String json = report.toJson();
    String digest = report.getRegistryIdentityDigest();

    power.setDesignValue(1000.0).setDataSource("changed").setCurrentValue(999.0);
    registry.register(PlantConstraintDefinition.builder("new", PlantConstraintScope.model("Plant")).build());
    UtilizationCoverageReport restored = roundTrip(report);

    assertEquals(1, calls.get());
    assertTrue(report.isComplete());
    assertEquals(51.0, report.getRows().get(0).getCurrentValue());
    assertEquals(100.0, report.getRows().get(0).getApplicableLimit());
    assertEquals(0.51, report.getRows().get(0).getNormalizedUtilization());
    assertEquals(digest, restored.getRegistryIdentityDigest());
    assertEquals(json, restored.toJson());
    assertThrows(UnsupportedOperationException.class, () -> restored.getRows().clear());
  }

  @Test
  void exceptionsAndNonFiniteSamplesRemainExplicit() {
    Stream equipment = new Stream("K-1");
    equipment.addCapacityConstraint(rated("throwing").setValueSupplier(() -> {
      throw new IllegalStateException("source unavailable");
    }));
    equipment.addCapacityConstraint(rated("infinite").setCurrentValue(Double.POSITIVE_INFINITY));
    equipment.addCapacityConstraint(rated("invalid-limit").setDesignValue(-1.0).setCurrentValue(5.0));
    UtilizationCoverageReport report = UtilizationCoverageReport.builder("Plant").equipment("Area", equipment).build();

    assertFalse(report.isComplete());
    assertTrue(report.getDiagnostics().contains("equipment:Plant/Area/K-1#throwing=SAMPLE_FAILED"));
    assertTrue(report.getDiagnostics().contains("equipment:Plant/Area/K-1#infinite=NON_FINITE_CURRENT_VALUE"));
    assertTrue(report.getDiagnostics().contains("equipment:Plant/Area/K-1#invalid-limit=INVALID_LIMIT"));
    assertFalse(report.toJson().contains("Infinity"));
  }

  @Test
  void metadataAndScreeningLimitsCannotQualifyAsRatedEvidence() {
    Stream equipment = new Stream("K-1");
    CapacityConstraint power = rated("power").setCurrentValue(80.0).setValidityRange(0.0, 70.0);
    equipment.addCapacityConstraint(power);
    PlantConstraintRegistry registry = registry(equipment, power);
    power.setUnit("MW").setDataSource("default").setSeverity(ConstraintSeverity.ADVISORY);
    UtilizationCoverageReport report = UtilizationCoverageReport.builder("Plant").equipment("Area", equipment)
        .registry(registry).build();

    assertFalse(report.isComplete());
    assertTrue(report.getRows().get(0).getStatuses()
        .containsAll(Arrays.asList(Status.METADATA_MISMATCH, Status.SCREENING_ONLY, Status.OUTSIDE_VALIDITY_RANGE)));
  }

  @Test
  void sourceRevisionChangeRequiresUpdatedRegistration() {
    Stream equipment = new Stream("K-1");
    CapacityConstraint power = rated("power").setCurrentValue(80.0);
    equipment.addCapacityConstraint(power);
    PlantConstraintRegistry registry = registry(equipment, power);
    assertTrue(UtilizationCoverageReport.builder("Plant").equipment("Area", equipment).registry(registry).build()
        .isComplete());

    power.setDataSource("vendor datasheet revision 4");
    UtilizationCoverageReport changed = UtilizationCoverageReport.builder("Plant").equipment("Area", equipment)
        .registry(registry).build();

    assertFalse(changed.isComplete());
    assertEquals(Arrays.asList(Status.METADATA_MISMATCH), changed.getRows().get(0).getStatuses());
  }

  @Test
  void disabledConstraintIsRetainedWithoutSampling() {
    AtomicInteger calls = new AtomicInteger();
    Stream equipment = new Stream("K-1");
    equipment.addCapacityConstraint(rated("power").setEnabled(false).setValueSupplier(() -> {
      calls.incrementAndGet();
      return 80.0;
    }));
    UtilizationCoverageReport report = UtilizationCoverageReport.builder("Plant").equipment("Area", equipment).build();

    assertTrue(report.isComplete());
    assertEquals(Arrays.asList(Status.DISABLED), report.getRows().get(0).getStatuses());
    assertEquals(0, calls.get());
    assertTrue(Double.isNaN(report.getRows().get(0).getNormalizedUtilization()));
    assertEquals(1, report.getRequiredConstraintIds().size());
  }

  @Test
  void minimumZeroPreservesViolationAndRegistryRangeIsEnforced() {
    Stream equipment = new Stream("V-1");
    CapacityConstraint residence = new CapacityConstraint("residence", "s", ConstraintType.HARD).setMinValue(60.0)
        .setCurrentValue(0.0).setDataSource("design study");
    equipment.addCapacityConstraint(residence);
    UtilizationCoverageReport report = UtilizationCoverageReport.builder("Plant").equipment("Area", equipment)
        .basis("Area", "V-1", "residence", "liquid residence time").build();

    assertTrue(report.isComplete());
    assertTrue(report.getRows().get(0).isMinimumConstraint());
    assertEquals(60.0, report.getRows().get(0).getApplicableLimit());
    assertTrue(report.getRows().get(0).getNormalizedUtilization() > 1.0);

    PlantConstraintRegistry registry = new PlantConstraintRegistry()
        .register(PlantConstraintDefinition.builder("residence", PlantConstraintScope.equipment("Plant", "Area", "V-1"))
            .limitDirection(PlantConstraintDefinition.LimitDirection.MINIMUM).unit("s").basis("liquid residence time")
            .provenance("design study").validityRange(30.0, 120.0).build());
    UtilizationCoverageReport outside = UtilizationCoverageReport.builder("Plant").equipment("Area", equipment)
        .registry(registry).build();
    assertFalse(outside.isComplete());
    assertTrue(outside.getRows().get(0).getStatuses().contains(Status.OUTSIDE_VALIDITY_RANGE));
  }

  @Test
  void scalarEquipmentCannotSatisfyRangeRegistrationAndNonFiniteDerivedEvidence() {
    Stream equipment = new Stream("K-1");
    equipment.addCapacityConstraint(rated("power").setCurrentValue(60.0));
    PlantConstraintRegistry registry = new PlantConstraintRegistry()
        .register(PlantConstraintDefinition.builder("power", PlantConstraintScope.equipment("Plant", "Area", "K-1"))
            .limitDirection(PlantConstraintDefinition.LimitDirection.RANGE).unit("kW").basis("shaft power")
            .provenance("vendor sheet").build());
    UtilizationCoverageReport report = UtilizationCoverageReport.builder("Plant").equipment("Area", equipment)
        .registry(registry).build();
    assertFalse(report.isComplete());
    assertTrue(report.getRows().get(0).getStatuses().contains(Status.METADATA_MISMATCH));

    CapacityConstraint invalid = new CapacityConstraint("invalid", "kW", ConstraintType.HARD) {
      private static final long serialVersionUID = 1L;

      @Override
      public double getUtilization(double value) {
        return Double.NaN;
      }
    };
    invalid.setDesignValue(100.0).setCurrentValue(50.0).setDataSource("vendor sheet");
    equipment.addCapacityConstraint(invalid);
    UtilizationCoverageReport derived = UtilizationCoverageReport.builder("Plant").equipment("Area", equipment)
        .basis("Area", "K-1", "power", "shaft power").basis("Area", "K-1", "invalid", "shaft power").build();
    assertFalse(derived.isComplete());
    assertTrue(derived.getDiagnostics().contains("equipment:Plant/Area/K-1#invalid=NON_FINITE_UTILIZATION"));
  }

  @Test
  void documentedRegistryBoundCoverageExample() {
    Stream equipment = new Stream("K-101");
    CapacityConstraint power = new CapacityConstraint("power", "kW", ConstraintType.HARD).setDesignValue(1000.0)
        .setCurrentValue(750.0).setDataSource("vendor datasheet revision 3");
    equipment.addCapacityConstraint(power);
    PlantConstraintRegistry registry = new PlantConstraintRegistry();
    registry.registerEquipmentConstraint("NorthPlant", "Compression", "K-101", "power", "shaft power",
        PlantConstraintDefinition.Category.DESIGN, "rotating equipment", "K-101 datasheet", power);

    UtilizationCoverageReport coverage = UtilizationCoverageReport.builder("NorthPlant")
        .expectConstraint("Compression", "K-101", "power").equipment("Compression", equipment).registry(registry)
        .build();

    assertTrue(coverage.isComplete());
    assertEquals(0.75, coverage.getRows().get(0).getNormalizedUtilization());
    assertEquals(registry.getIdentityDigest(), coverage.getRegistryIdentityDigest());
    assertTrue(coverage.toJson().contains("\"currentValue\":750.0"));
  }

  private static CapacityConstraint rated(String name) {
    return new CapacityConstraint(name, "kW", ConstraintType.HARD).setDesignValue(100.0)
        .setDataSource("vendor datasheet revision 3");
  }

  private static PlantConstraintRegistry registry(Stream equipment, CapacityConstraint constraint) {
    PlantConstraintRegistry registry = new PlantConstraintRegistry();
    registry.registerEquipmentConstraint("Plant", "Area", equipment.getName(), constraint.getName(), "shaft power",
        PlantConstraintDefinition.Category.DESIGN, "rotating equipment", "vendor sheet", constraint);
    return registry;
  }

  private static UtilizationCoverageReport roundTrip(UtilizationCoverageReport report) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(report);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (UtilizationCoverageReport) input.readObject();
    }
  }
}
