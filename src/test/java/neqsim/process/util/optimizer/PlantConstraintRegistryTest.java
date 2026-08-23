package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;

/** Regression tests for deterministic plant-wide constraint registration. */
class PlantConstraintRegistryTest {

  @Test
  void registryIdentityIsOrderAndSerializationIndependent() throws Exception {
    PlantConstraintDefinition pipe = directDefinition("pressure",
        PlantConstraintScope.stream("Plant", "Export", "P-1"));
    PlantConstraintDefinition power = PlantConstraintDefinition
        .builder("total-power", PlantConstraintScope.sharedResource("Plant", "electricity"))
        .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.SHARED_BUDGET).unit("MW")
        .basis("instantaneous electrical load").provenance("power study")
        .participant(PlantConstraintParticipant.direct("compressor-a", "MW", "instantaneous electrical load"))
        .participant(PlantConstraintParticipant.direct("compressor-b", "MW", "instantaneous electrical load")).build();

    PlantConstraintRegistry first = new PlantConstraintRegistry().register(pipe).register(power);
    PlantConstraintRegistry second = new PlantConstraintRegistry().register(power).register(pipe);
    PlantConstraintRegistry restored = roundTrip(first);

    assertEquals(first.getIdentityDigest(), second.getIdentityDigest());
    assertEquals(first.getIdentityDigest(), restored.getIdentityDigest());
    assertEquals(first.getDefinitions().get(0).getQualifiedId(), restored.getDefinitions().get(0).getQualifiedId());
    assertEquals(2, restored.size());
  }

  @Test
  void everyScopeHasUniqueEscapedStableIdentity() {
    PlantConstraintScope equipment = PlantConstraintScope.equipment("Plant:1", "Compression/A", "K#1|main");
    PlantConstraintScope stream = PlantConstraintScope.stream("Plant:1", "Compression/A", "K#1");
    PlantConstraintScope area = PlantConstraintScope.area("Plant:1", "Compression/A");
    PlantConstraintScope model = PlantConstraintScope.model("Plant:1");
    PlantConstraintScope resource = PlantConstraintScope.sharedResource("Plant:1", "total/power");
    PlantConstraintScope group = PlantConstraintScope.coupledGroup("Plant:1", "Compression/A", "shaft#1");

    assertNotEquals(equipment.getStableId(), stream.getStableId());
    assertNotEquals(area.getStableId(), model.getStableId());
    assertNotEquals(resource.getStableId(), group.getStableId());
    assertEquals("equipment:Plant%3A1/Compression%2FA/K%231%7Cmain", equipment.getStableId());
    assertEquals("shared_resource:Plant%3A1/total%2Fpower", resource.getStableId());
  }

  @Test
  void aggregationRejectsImplicitUnitOrBasisConversion() {
    PlantConstraintDefinition.Builder builder = PlantConstraintDefinition
        .builder("total-power", PlantConstraintScope.sharedResource("Plant", "power"))
        .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.SUM).unit("MW").basis("electrical load")
        .participant(PlantConstraintParticipant.direct("gas-turbine", "kW", "shaft power"));

    assertThrows(IllegalArgumentException.class, builder::build);
    assertThrows(IllegalArgumentException.class,
        () -> PlantConstraintDefinition.builder("incomplete", PlantConstraintScope.model("Plant"))
            .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.SUM)
            .participant(PlantConstraintParticipant.direct("source", "kg/h", "standard rate")).build());
  }

  @Test
  void aggregationAcceptsExplicitFiniteConversion() {
    PlantConstraintParticipant participant = PlantConstraintParticipant.converted("compressor", "kW", "shaft power",
        0.001, 0.0);
    PlantConstraintDefinition definition = PlantConstraintDefinition
        .builder("driver-power", PlantConstraintScope.coupledGroup("Plant", "Compression", "shaft-1"))
        .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.RATE_BASIS_CONVERSION).unit("MW")
        .basis("common-shaft driver load").provenance("driver data sheet").participant(participant).build();

    assertEquals(2.5, participant.convertToTarget(2500.0), 0.0);
    assertEquals(PlantConstraintDefinition.RegistrationStatus.REGISTERED, definition.getRegistrationStatus());
    assertThrows(IllegalArgumentException.class, () -> participant.convertToTarget(Double.NaN));
  }

  @Test
  void disabledAndIncompleteDefinitionsRemainVisible() {
    PlantConstraintDefinition definition = PlantConstraintDefinition
        .builder("separator-oil-residence", PlantConstraintScope.equipment("Plant", "Separation", "V-1")).enabled(false)
        .build();
    PlantConstraintRegistry registry = new PlantConstraintRegistry().register(definition);

    assertEquals(PlantConstraintDefinition.RegistrationStatus.DISABLED_INCOMPLETE_BASIS,
        registry.getDefinitions().get(0).getRegistrationStatus());
    assertFalse(registry.getDefinitions().get(0).isEnabled());
  }

  @Test
  void equipmentAdapterCopiesEvidenceWithoutSamplingSupplier() {
    AtomicInteger calls = new AtomicInteger();
    CapacityConstraint source = new CapacityConstraint("speed", "rpm", ConstraintType.HARD)
        .setSeverity(ConstraintSeverity.CRITICAL).setDescription("installed overspeed limit")
        .setDataSource("vendor map rev 4").setConfidence(0.93).setValidityRange(7000.0, 11000.0).setEnabled(false)
        .setValueSupplier(() -> {
          calls.incrementAndGet();
          return 9000.0;
        });
    PlantConstraintRegistry registry = new PlantConstraintRegistry();

    PlantConstraintDefinition copied = registry.registerEquipmentConstraint("Plant", "Compression", "K-1", "speed",
        "installed shaft speed", PlantConstraintDefinition.Category.DESIGN, "rotating equipment", "K-1 data sheet",
        source);

    assertEquals(0, calls.get());
    assertEquals(ConstraintSeverity.CRITICAL, copied.getSeverity());
    assertEquals("rpm", copied.getUnit());
    assertEquals("vendor map rev 4", copied.getProvenance());
    assertEquals(0.93, copied.getConfidence(), 0.0);
    assertEquals(7000.0, copied.getValidityMinimum(), 0.0);
    assertFalse(copied.isEnabled());
  }

  @Test
  void duplicateQualifiedIdentityIsRejected() {
    PlantConstraintDefinition definition = directDefinition("pressure", PlantConstraintScope.model("Plant"));
    PlantConstraintRegistry registry = new PlantConstraintRegistry().register(definition);

    assertThrows(IllegalArgumentException.class, () -> registry.register(definition));
    assertTrue(registry.contains(definition.getQualifiedId()));
  }

  private static PlantConstraintDefinition directDefinition(String id, PlantConstraintScope scope) {
    return PlantConstraintDefinition.builder(id, scope).unit("bara").basis("absolute pressure")
        .provenance("operating envelope").build();
  }

  private static PlantConstraintRegistry roundTrip(PlantConstraintRegistry registry) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(registry);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    PlantConstraintRegistry restored = (PlantConstraintRegistry) input.readObject();
    input.close();
    return restored;
  }
}
