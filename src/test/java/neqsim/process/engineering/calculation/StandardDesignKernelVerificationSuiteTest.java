package neqsim.process.engineering.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import neqsim.process.engineering.production.EngineeringBenchmarkSuite;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import org.junit.jupiter.api.Test;

/** Regression, unit-equivalence, registry-integrity, and serialization checks for standard kernels. */
class StandardDesignKernelVerificationSuiteTest {
  @Test
  void executesEveryRegisteredKernelWithoutClaimingIndependentQualification() throws Exception {
    EngineeringBenchmarkSuite.Report report = StandardDesignKernelVerificationSuite.evaluateRegression();

    assertEquals(5, report.getBenchmarks().size());
    assertTrue(report.areAllBenchmarksPassed(), report.toMap().toString());
    assertTrue(report.getFailedBenchmarkIds().isEmpty());
    assertFalse(report.isPassed(), "Regression baselines must not qualify the methods");
    assertEquals(5, report.getMissingQualifyingMethods().size());
    assertEquals("engineering_benchmark_suite.v1", report.toMap().get("schemaVersion"));
    EngineeringBenchmarkSuite.Report restored = roundTrip(report);
    assertTrue(restored.areAllBenchmarksPassed());
    assertEquals(report.getRequiredMethods(), restored.getRequiredMethods());
  }

  @Test
  void registrySnapshotIsDeterministicImmutableAndInternallyConsistent() throws Exception {
    Set<StandardType> expected = EnumSet.of(StandardType.API_12J, StandardType.API_521, StandardType.API_526,
        StandardType.API_610, StandardType.API_617);

    assertEquals(expected, EquipmentDesignKernelRegistry.getRegisteredStandards());
    assertThrows(UnsupportedOperationException.class,
        () -> EquipmentDesignKernelRegistry.getRegisteredStandards().clear());

    Set<String> methodKeys = new HashSet<String>();
    for (StandardType standard : EquipmentDesignKernelRegistry.getRegisteredStandards()) {
      EquipmentDesignKernelRegistry.Lookup lookup = EquipmentDesignKernelRegistry.lookup(standard);
      EquipmentDesignKernel<?, ?> kernel = lookup.requireKernel();
      assertTrue(lookup.isImplemented());
      assertEquals(standard, kernel.standard());
      assertTrue(kernel.maturity() != StandardSupportLevel.CATALOGUED);
      assertTrue(kernel.supports(StandardEdition.defaultEdition(standard)));
      assertTrue(methodKeys.add(kernel.getMethod() + "@" + kernel.getMethodVersion()), "Duplicate method key");
      assertNotNull(roundTrip(kernel));
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T roundTrip(T value) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(value);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    T copy = (T) input.readObject();
    input.close();
    return copy;
  }
}
