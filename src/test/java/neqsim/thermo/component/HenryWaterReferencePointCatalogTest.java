package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests qualified reference-point-only Henry data and its fail-closed temperature contract. */
class HenryWaterReferencePointCatalogTest {
  /** Every row must be a unique, neutral, exact-CAS NeqSim identity. */
  @Test
  void catalogContainsQualifiedExactCasIdentities() {
    List<HenryWaterReferencePoint> points = HenryWaterReferencePointCatalog.getAll();
    assertEquals(28, points.size());
    Set<String> casNumbers = new HashSet<>();
    for (HenryWaterReferencePoint point : points) {
      assertTrue(casNumbers.add(point.getCasNumber()), point.getCasNumber());
      ComponentSrk component = new ComponentSrk(point.getComponentName(), 1.0, 1.0, 0);
      assertEquals(point.getCasNumber(), component.getCASnumber(), point.getComponentName());
      assertEquals("reference_point_only", point.getStatus());
      assertEquals("3673", point.getReferenceId());
      assertEquals("10.1016/S0016-7037(99)00330-0", point.getOriginalReferenceDoi());
      assertEquals("CC BY 4.0", point.getCompilationLicense());
    }
    assertThrows(UnsupportedOperationException.class, () -> points.add(points.get(0)));
  }

  /** A point evaluates at 298.15 K only; it cannot masquerade as a zero-slope correlation. */
  @Test
  void referencePointDoesNotExtrapolateOrInventDerivative() {
    HenryWaterReferencePoint point = HenryWaterReferencePointCatalog.findByCasNumber("124-18-5")
        .orElseThrow(AssertionError::new);
    assertEquals("nC10", point.getComponentName());
    assertSame(point, HenryWaterReferencePointCatalog.findByComponentName("nC10").orElseThrow(AssertionError::new));
    assertEquals(1.01325 / 1.1e-4, point.getMolalityVolatilityBarKgPerMol(), 1.0e-10);
    assertEquals(point.getMolalityVolatilityBarKgPerMol(), point.getMolalityVolatilityBarKgPerMol(298.15), 0.0);
    assertTrue(Double.isNaN(point.getMolalityVolatilityBarKgPerMol(298.1500000001)));
    assertTrue(Double.isNaN(point.getMolalityVolatilityBarKgPerMol(288.15)));
    assertTrue(Double.isNaN(point.getMolalityVolatilityTemperatureDerivative(298.15)));
    assertFalse(HenryWaterReferencePointCatalog.findByCasNumber("74-82-8").isPresent());
  }

  /** Immutable points retain their complete contract through cloning and Java serialization. */
  @Test
  void referencePointIsCloneAndSerializationStable() throws Exception {
    HenryWaterReferencePoint original = HenryWaterReferencePointCatalog.findByComponentName("o-E-toluene")
        .orElseThrow(AssertionError::new);
    assertSame(original, original.clone());
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
      output.writeObject(original);
    }
    HenryWaterReferencePoint restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
      restored = (HenryWaterReferencePoint) input.readObject();
    }
    assertNotSame(original, restored);
    assertEquals(original.getComponentName(), restored.getComponentName());
    assertEquals(original.getCasNumber(), restored.getCasNumber());
    assertEquals(original.getSolubilityMolalityPerAtm(), restored.getSolubilityMolalityPerAtm(), 0.0);
    assertEquals(original.getTemperatureScope(), restored.getTemperatureScope());
    assertEquals(original.getUncertainty(), restored.getUncertainty());
  }
}
