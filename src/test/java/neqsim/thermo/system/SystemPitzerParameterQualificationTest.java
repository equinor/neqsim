package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PitzerParameterDatasets;
import neqsim.thermo.phase.PitzerParameterQualification;

/** Tests the explicit SystemPitzer scientific-qualification publication gate. */
class SystemPitzerParameterQualificationTest {
  @Test
  void completeNamedSubsetPassesAfterCoverageAudit() {
    SystemPitzer system = createSodiumPotassiumChlorideSystem(298.15);
    system.applyPhreeqcSodiumPotassiumChlorideParameters();

    PitzerParameterQualification qualification = system.requireCompletePitzerDatasetQualification();

    assertEquals(PitzerParameterDatasets.PHREEQC_NA_K_CL_ID, qualification.getDatasetId());
    assertEquals(PitzerParameterQualification.Level.VALIDATED_WITHIN_DECLARED_ENVELOPE,
        qualification.getLevel());
    assertTrue(qualification.isValidatedWithinDeclaredEnvelope());
  }

  @Test
  void automaticBroadCatalogFailsCompleteQualificationEvenWhenCoverageIsComplete() {
    SystemPitzer system = createSodiumChlorideSystem(298.15);

    PitzerParameterQualification qualification = system.getPitzerParameterQualification();

    assertEquals(PitzerParameterDatasets.PHREEQC_PITZER_CATALOG_ID, qualification.getDatasetId());
    assertEquals(PitzerParameterQualification.Level.PARTIALLY_EXPERIMENTALLY_VALIDATED,
        qualification.getLevel());
    IllegalStateException failure =
        assertThrows(IllegalStateException.class, system::requireCompletePitzerDatasetQualification);
    assertTrue(failure.getMessage().contains(PitzerParameterDatasets.PHREEQC_PITZER_CATALOG_ID));
    assertTrue(failure.getMessage().contains("PARTIALLY_EXPERIMENTALLY_VALIDATED"));
    assertTrue(failure.getMessage().contains("Quaternary Ca-Mg-Cl-SO4"));
  }

  @Test
  void legacyDatasetFailsClosedWithDeterministicEvidence() {
    SystemPitzer system = createSodiumChlorideSystem(298.15);
    system = createLegacySodiumChlorideSystem();

    PitzerParameterQualification qualification = system.getPitzerParameterQualification();

    assertEquals(PitzerParameterQualification.Level.UNQUALIFIED, qualification.getLevel());
    IllegalStateException failure =
        assertThrows(IllegalStateException.class, system::requireCompletePitzerDatasetQualification);
    assertEquals(qualification.formatDiagnostic(), failure.getMessage());
    assertTrue(failure.getMessage().contains("No reviewed qualification record"));
  }

  @Test
  void datasetGateDoesNotSilentlyClaimCurrentStateIsInsideTheEvidenceRange() {
    SystemPitzer system = createSodiumPotassiumChlorideSystem(450.0);
    system.applyPhreeqcSodiumPotassiumChlorideParameters();

    assertTrue(system.requireCompletePitzerDatasetQualification().isValidatedWithinDeclaredEnvelope());
    assertFalse(PitzerParameterDatasets.isWithinSodiumPotassiumChlorideValidationRange(450.0, 0.5, 0.5, 1.0));
  }

  @Test
  void qualificationSurvivesCloneSerializationAndConcurrentReads() throws Exception {
    SystemPitzer system = createSodiumPotassiumChlorideSystem(298.15);
    system.applyPhreeqcSodiumPotassiumChlorideParameters();
    String expected = system.requireCompletePitzerDatasetQualification().formatDiagnostic();

    SystemPitzer cloned = system.clone();
    assertEquals(expected, cloned.requireCompletePitzerDatasetQualification().formatDiagnostic());

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(system);
    }
    SystemPitzer restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (SystemPitzer) input.readObject();
    }
    assertEquals(expected, restored.requireCompletePitzerDatasetQualification().formatDiagnostic());

    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Callable<String>> tasks = new ArrayList<Callable<String>>();
      for (int task = 0; task < 16; task++) {
        tasks.add(() -> system.getPitzerParameterQualification().formatDiagnostic());
      }
      for (Future<String> result : executor.invokeAll(tasks)) {
        assertEquals(expected, result.get());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static SystemPitzer createSodiumChlorideSystem(double temperature) {
    SystemPitzer system = new SystemPitzer(temperature, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.init(0);
    return system;
  }

  private static SystemPitzer createLegacySodiumChlorideSystem() {
    SystemPitzer system = new SystemPitzer(298.15, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.useLegacyPitzerParameters();
    system.init(0);
    return system;
  }

  private static SystemPitzer createSodiumPotassiumChlorideSystem(double temperature) {
    SystemPitzer system = new SystemPitzer(temperature, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 0.5);
    system.addComponent("K+", 0.5);
    system.addComponent("Cl-", 1.0);
    system.init(0);
    return system;
  }
}
