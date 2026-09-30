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
    assertEquals(35, points.size());
    Set<String> casNumbers = new HashSet<>();
    int plyasunovCount = 0;
    int brockbankCount = 0;
    int mackayShiuCount = 0;
    for (HenryWaterReferencePoint point : points) {
      assertTrue(casNumbers.add(point.getCasNumber()), point.getCasNumber());
      ComponentSrk component = new ComponentSrk(point.getComponentName(), 1.0, 1.0, 0);
      assertEquals(point.getCasNumber(), component.getCASnumber(), point.getComponentName());
      assertEquals("reference_point_only", point.getStatus());
      assertEquals("water", point.getSolvent());
      assertEquals("CC BY 4.0", point.getCompilationLicense());
      assertFalse(point.getOriginalReferenceUrl().isEmpty());
      assertFalse(point.getOriginalReferenceRights().isEmpty());
      assertFalse(point.getIdentityBasis().isEmpty());
      if ("3673".equals(point.getReferenceId())) {
        plyasunovCount++;
        assertEquals("10.1016/S0016-7037(99)00330-0", point.getOriginalReferenceDoi());
      } else if ("3518".equals(point.getReferenceId())) {
        brockbankCount++;
        assertEquals("", point.getOriginalReferenceDoi());
        assertEquals("https://scholarsarchive.byu.edu/etd/3691/", point.getOriginalReferenceUrl());
        assertFalse(point.getSourceInchiKey().isEmpty());
      } else {
        mackayShiuCount++;
        assertEquals("479", point.getReferenceId());
        assertEquals("10.1063/1.555654", point.getOriginalReferenceDoi());
        assertEquals("https://doi.org/10.1063/1.555654", point.getOriginalReferenceUrl());
      }
    }
    assertEquals(28, plyasunovCount);
    assertEquals(6, brockbankCount);
    assertEquals(1, mackayShiuCount);
    assertThrows(UnsupportedOperationException.class, () -> points.add(points.get(0)));
  }

  /** The Mackay-Shiu point retains its exact identity and unknown pressure basis. */
  @Test
  void ethyltoluenePointRetainsSourceLimitations() {
    HenryWaterReferencePoint point = HenryWaterReferencePointCatalog.findByComponentName("4-ethyltoluene")
        .orElseThrow(AssertionError::new);
    assertEquals("622-96-8", point.getCasNumber());
    assertEquals("JRLPEMVDPFPYPJ-UHFFFAOYSA-N", point.getSourceInchiKey());
    assertEquals(2.0e-1, point.getSolubilityMolalityPerAtm(), 0.0);
    assertEquals(298.15, point.getReferenceTemperatureK(), 0.0);
    assertTrue(Double.isNaN(point.getReferencePressureMPa()));
    assertEquals(1.01325 / 2.0e-1, point.getMolalityVolatilityBarKgPerMol(298.15), 0.0);
    assertTrue(Double.isNaN(point.getMolalityVolatilityBarKgPerMol(298.1500000001)));
    assertTrue(Double.isNaN(point.getMolalityVolatilityTemperatureDerivative()));
  }

  /** Brockbank rows retain their exact source values and per-row provenance. */
  @Test
  void brockbankRowsRetainPerRowSourceEvidence() {
    String[] names = {"4-methylheptane", "cis-2-pentene", "cis-2-heptene", "nC7-Benzene", "nC8-Benzene", "nC9-Benzene"};
    double[] values = {2.7e-4, 4.5e-3, 2.4e-3, 2.7e-2, 1.9e-2, 1.5e-2};
    for (int index = 0; index < names.length; index++) {
      HenryWaterReferencePoint point = HenryWaterReferencePointCatalog.findByComponentName(names[index])
          .orElseThrow(AssertionError::new);
      assertEquals(values[index], point.getSolubilityMolalityPerAtm(), 0.0);
      assertEquals("3518", point.getReferenceId());
      assertEquals(298.15, point.getReferenceTemperatureK(), 0.0);
      assertEquals(0.1, point.getReferencePressureMPa(), 0.0);
      assertTrue(Double.isNaN(point.getMolalityVolatilityBarKgPerMol(308.15)));
    }
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
    assertTrue(Double.isNaN(point.getMolalityVolatilityTemperatureDerivative()));
    assertFalse(HenryWaterReferencePointCatalog.findByCasNumber("74-82-8").isPresent());
  }

  /** Immutable points retain their complete contract through cloning and Java serialization. */
  @Test
  void referencePointIsCloneAndSerializationStable() throws Exception {
    HenryWaterReferencePoint original = HenryWaterReferencePointCatalog.findByComponentName("nC8-Benzene")
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
    assertEquals(original.getOriginalReferenceUrl(), restored.getOriginalReferenceUrl());
    assertEquals(original.getOriginalReferenceRights(), restored.getOriginalReferenceRights());
    assertEquals(original.getSourceInchiKey(), restored.getSourceInchiKey());
    assertEquals(original.getIdentityBasis(), restored.getIdentityBasis());
  }
}
