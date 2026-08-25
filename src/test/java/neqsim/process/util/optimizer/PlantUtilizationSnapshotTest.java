package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.network.NetworkDecisionVariable;

/** Regression tests for complete immutable plant utilization snapshots. */
class PlantUtilizationSnapshotTest {

  @Test
  void completeSnapshotRetainsEveryRowAndBuildsDeterministicLadder() {
    PlantConstraintDefinition separator = definition("gas-load",
        PlantConstraintScope.equipment("Plant", "Separation", "V-1"), ConstraintSeverity.HARD, true);
    PlantConstraintDefinition power = definition("total-power",
        PlantConstraintScope.sharedResource("Plant", "electricity"), ConstraintSeverity.HARD, true);
    PlantConstraintDefinition disabled = definition("flare-screen",
        PlantConstraintScope.sharedResource("Plant", "flare"), ConstraintSeverity.ADVISORY, false);
    PlantConstraintRegistry registry = new PlantConstraintRegistry().register(separator).register(disabled)
        .register(power);

    PlantUtilizationSnapshot snapshot = PlantUtilizationSnapshot.builder(registry, "calc-1")
        .sample(available(power, "calc-1", 105.0, 100.0, 1.05, -5.0))
        .sample(available(separator, "calc-1", 95.0, 100.0, 0.95, 5.0)).build();

    assertEquals(3, snapshot.getEvidence().size());
    assertTrue(snapshot.isComplete());
    assertFalse(snapshot.isFeasible());
    assertEquals(power.getQualifiedId(), snapshot.getBottleneck().getQualifiedConstraintId());
    assertEquals(separator.getQualifiedId(), snapshot.getBottleneckLadder().get(1).getQualifiedConstraintId());
    assertEquals(PlantConstraintEvidence.CoverageStatus.DISABLED,
        snapshot.getEvidence(disabled.getQualifiedId()).getCoverageStatus());
    assertEquals(registry.getIdentityDigest(), snapshot.getRegistryIdentityDigest());
  }

  @Test
  void softViolationRemainsVisibleWithoutInventingHardInfeasibility() {
    PlantConstraintDefinition hard = definition("driver-power",
        PlantConstraintScope.coupledGroup("Plant", "Compression", "shaft-1"), ConstraintSeverity.HARD, true);
    PlantConstraintDefinition soft = definition("cooling-target",
        PlantConstraintScope.sharedResource("Plant", "cooling"), ConstraintSeverity.SOFT, true);
    PlantConstraintRegistry registry = new PlantConstraintRegistry().register(hard).register(soft);

    PlantUtilizationSnapshot snapshot = PlantUtilizationSnapshot.builder(registry, "calc-soft")
        .sample(available(hard, "calc-soft", 80.0, 100.0, 0.8, 20.0))
        .sample(available(soft, "calc-soft", 110.0, 100.0, 1.1, -10.0)).build();

    assertTrue(snapshot.isComplete());
    assertTrue(snapshot.isFeasible());
    assertEquals(PlantConstraintEvidence.OperatingStatus.VIOLATED,
        snapshot.getEvidence(soft.getQualifiedId()).getOperatingStatus());
    assertFalse(snapshot.getEvidence(soft.getQualifiedId()).isHardConstraint());
    assertTrue(snapshot.getFeasibilityDiagnostics().isEmpty());
  }

  @Test
  void incompleteStaleAndMismatchedEvidenceFailsClosed() {
    PlantConstraintDefinition constraint = definition("export-pressure",
        PlantConstraintScope.stream("Plant", "Export", "sales gas"), ConstraintSeverity.HARD, true);
    PlantConstraintRegistry registry = new PlantConstraintRegistry().register(constraint);

    PlantUtilizationSnapshot missing = PlantUtilizationSnapshot.builder(registry, "calc-2").build();
    assertFalse(missing.isComplete());
    assertFalse(missing.isFeasible());
    assertEquals(PlantConstraintEvidence.CoverageStatus.MISSING_SAMPLE,
        missing.getEvidence(constraint.getQualifiedId()).getCoverageStatus());

    PlantConstraintSample stale = PlantConstraintSample.builder(constraint.getQualifiedId(), "calc-2")
        .status(PlantConstraintSample.SampleStatus.STALE).unit("kW").basis("rated duty").build();
    assertEquals(PlantConstraintEvidence.CoverageStatus.STALE, PlantUtilizationSnapshot.builder(registry, "calc-2")
        .sample(stale).build().getEvidence(constraint.getQualifiedId()).getCoverageStatus());

    PlantConstraintSample wrongCalculation = available(constraint, "old-calc", 90.0, 100.0, 0.9, 10.0);
    assertEquals(PlantConstraintEvidence.CoverageStatus.CALCULATION_ID_MISMATCH,
        PlantUtilizationSnapshot.builder(registry, "calc-2").sample(wrongCalculation).build()
            .getEvidence(constraint.getQualifiedId()).getCoverageStatus());

    PlantConstraintSample wrongUnit = PlantConstraintSample.builder(constraint.getQualifiedId(), "calc-2")
        .values(90.0, 100.0).normalized(0.9, -0.1).physical(10.0, 0.0).unit("bara").basis(constraint.getBasis())
        .provenance("meter").build();
    assertEquals(PlantConstraintEvidence.CoverageStatus.METADATA_MISMATCH,
        PlantUtilizationSnapshot.builder(registry, "calc-2").sample(wrongUnit).build()
            .getEvidence(constraint.getQualifiedId()).getCoverageStatus());

    PlantConstraintSample nonFinite = PlantConstraintSample.builder(constraint.getQualifiedId(), "calc-2")
        .status(PlantConstraintSample.SampleStatus.NON_FINITE_VALUE).unit(constraint.getUnit())
        .basis(constraint.getBasis()).build();
    assertEquals(PlantConstraintEvidence.CoverageStatus.NON_FINITE_VALUE,
        PlantUtilizationSnapshot.builder(registry, "calc-2").sample(nonFinite).build()
            .getEvidence(constraint.getQualifiedId()).getCoverageStatus());

    assertEquals(PlantConstraintEvidence.CoverageStatus.INCOMPLETE_CONVERGENCE,
        PlantUtilizationSnapshot.builder(registry, "calc-2")
            .sample(available(constraint, "calc-2", 90.0, 100.0, 0.9, 10.0)).convergenceComplete(false).build()
            .getEvidence(constraint.getQualifiedId()).getCoverageStatus());
    assertThrows(IllegalArgumentException.class, () -> PlantUtilizationSnapshot.builder(registry, "calc-2")
        .sample(PlantConstraintSample.builder("unknown#constraint", "calc-2").build()));
    assertThrows(IllegalArgumentException.class,
        () -> PlantUtilizationSnapshot.builder(registry, "calc-2")
            .sample(available(constraint, "calc-2", 90.0, 100.0, 0.9, 10.0))
            .sample(available(constraint, "calc-2", 90.0, 100.0, 0.9, 10.0)));
  }

  @Test
  void establishedInstalledAndBoundaryEvidenceAdaptersPreservePhysicalBasis() {
    CapacityConstraint capacity = new CapacityConstraint("power", "kW", ConstraintType.HARD).setDesignValue(100.0)
        .setSeverity(ConstraintSeverity.HARD).setDataSource("vendor data sheet");
    InstalledEquipmentCapacityEvidence installed = new InstalledEquipmentCapacityEvidence("Compression", "K-1",
        "neqsim.process.equipment.compressor.Compressor", "K-1", "power",
        InstalledEquipmentCapacityEvidence.ConstraintOrigin.DIRECT, capacity, 90.0);
    PlantConstraintDefinition installedDefinition = PlantConstraintDefinition
        .builder("power", PlantConstraintScope.equipment("Plant", "Compression", "K-1")).unit("kW")
        .basis("installed shaft power").provenance("vendor data sheet").build();

    PlantConstraintSample installedSample = PlantConstraintSample.fromInstalledEquipmentEvidence(installedDefinition,
        "calc-adapter", installed);
    assertEquals(PlantConstraintSample.SampleStatus.AVAILABLE, installedSample.getStatus());
    assertEquals(0.9, installedSample.getNormalizedUtilization(), 0.0);
    assertEquals(10.0, installedSample.getPhysicalMargin(), 0.0);

    ProcessBoundaryConstraintEvidence.Metadata metadata = new ProcessBoundaryConstraintEvidence.Metadata("export",
        "Export", "sales meter", ProcessBoundaryConstraintEvidence.Kind.EXPORT_CAPACITY,
        ProcessBoundaryConstraintEvidence.FlowDirection.OUT_OF_PROCESS, NetworkDecisionVariable.RateBasis.MASS,
        "sales agreement", 0.95, null, null, ProcessBoundaryConstraintEvidence.ApplicabilityStatus.APPLICABLE,
        "mass rate", "metered", null, -1);
    ProcessModelSimulationEvaluator.ConstraintDefinition boundaryDefinition = new ProcessModelSimulationEvaluator.ConstraintDefinition();
    boundaryDefinition.setName("maximum export");
    boundaryDefinition.setType(ProcessModelSimulationEvaluator.ConstraintDefinition.Type.UPPER_BOUND);
    boundaryDefinition.setUpperBound(10000.0);
    boundaryDefinition.setUnit("kg/hr");
    ProcessBoundaryConstraintEvidence boundary = new ProcessBoundaryConstraintEvidence(metadata, boundaryDefinition,
        ProcessBoundaryConstraintEvidence.Sample.available(10500.0), 1000.0);
    PlantConstraintDefinition plantBoundary = PlantConstraintDefinition
        .builder("maximum export", PlantConstraintScope.stream("Plant", "Export", "sales meter"))
        .limitDirection(PlantConstraintDefinition.LimitDirection.MAXIMUM).unit("kg/hr").basis("mass rate")
        .provenance("sales agreement").build();

    PlantConstraintSample boundarySample = PlantConstraintSample.fromProcessBoundaryEvidence(plantBoundary,
        "calc-adapter", boundary);
    assertEquals(PlantConstraintSample.SampleStatus.AVAILABLE, boundarySample.getStatus());
    assertEquals(1.5, boundarySample.getNormalizedUtilization(), 0.0);
    assertEquals(-500.0, boundarySample.getPhysicalMargin(), 0.0);
    assertEquals(500.0, boundarySample.getRequiredRelief(), 0.0);
  }

  @Test
  void snapshotRoundTripsAndAccessorsRemainImmutable() throws Exception {
    PlantConstraintDefinition definition = definition("flow",
        PlantConstraintScope.stream("Plant", "Gathering", "inlet"), ConstraintSeverity.HARD, true);
    PlantConstraintRegistry registry = new PlantConstraintRegistry().register(definition);
    PlantUtilizationSnapshot original = PlantUtilizationSnapshot.builder(registry, "calc-serial")
        .sample(available(definition, "calc-serial", 75.0, 100.0, 0.75, 25.0)).build();

    PlantUtilizationSnapshot restored = roundTrip(original);

    assertEquals(original.getCalculationId(), restored.getCalculationId());
    assertEquals(original.getRegistryIdentityDigest(), restored.getRegistryIdentityDigest());
    assertEquals(0.75, restored.getBottleneck().getNormalizedUtilization(), 0.0);
    assertNotNull(restored.getEvidence(definition.getQualifiedId()));
    assertThrows(UnsupportedOperationException.class, () -> restored.getEvidence().clear());
    assertThrows(UnsupportedOperationException.class, () -> restored.getBottleneckLadder().clear());
    assertThrows(UnsupportedOperationException.class, () -> restored.getCoverageDiagnostics().clear());
  }

  private static PlantConstraintDefinition definition(String id, PlantConstraintScope scope,
      ConstraintSeverity severity, boolean enabled) {
    return PlantConstraintDefinition.builder(id, scope).severity(severity).unit("kW").basis("rated duty")
        .provenance("engineering basis").enabled(enabled).build();
  }

  private static PlantConstraintSample available(PlantConstraintDefinition definition, String calculationId,
      double value, double limit, double utilization, double margin) {
    return PlantConstraintSample.builder(definition.getQualifiedId(), calculationId).values(value, limit)
        .normalized(utilization, utilization - 1.0).physical(margin, Math.max(0.0, -margin)).unit(definition.getUnit())
        .basis(definition.getBasis()).provenance("runtime evidence").build();
  }

  private static PlantUtilizationSnapshot roundTrip(PlantUtilizationSnapshot snapshot) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(snapshot);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    PlantUtilizationSnapshot restored = (PlantUtilizationSnapshot) input.readObject();
    input.close();
    return restored;
  }
}
