package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Independent-evidence tests for the COMPSALT gypsum/anhydrite phase boundary. */
class CalciumSulfatePhaseBoundaryQualificationTest extends neqsim.NeqSimTest {

  @Test
  void currentCorrelationsFailClosedAgainstIndependentAtmosphericEvidence() {
    SystemInterface system = new SystemSrkEos(298.15, 1.01325);
    double originalTemperature = system.getTemperature();
    double originalPressure = system.getPressure();

    CalciumSulfatePhaseBoundaryQualification qualification = new ThermodynamicOperations(system)
        .qualifyCalciumSulfatePhaseBoundary();

    assertEquals(60.445190, qualification.getPredictedPureWaterTransitionCelsius(), 1.0e-6);
    assertEquals(60.445190, qualification.getPredictedPureWaterTransitionAtEvaluatedPressureCelsius(), 1.0e-6);
    assertEquals(-52.4, qualification.getAnhydriteLumpedReactionVolumeCm3PerMol(), 0.0);
    assertEquals(-33.0, qualification.getGypsumLumpedReactionVolumeCm3PerMol(), 0.0);
    assertEquals(0.0, qualification.getAnhydriteLogKspPressureCorrection(), 0.0);
    assertEquals(0.0, qualification.getGypsumLogKspPressureCorrection(), 0.0);
    assertFalse(qualification.isAqueousSpeciesVolumeResolved());
    assertFalse(qualification.isHighPressureQualified());
    assertEquals("10.2475/ajs.261.1.61", qualification.getHighPressureLineageDoi());
    assertEquals(1.01325, CalciumSulfatePhaseBoundaryQualification.COMPSALT_PRESSURE_CORRECTION_REFERENCE_BARA, 0.0);
    assertEquals(0.7736299, qualification.getRequiredWaterActivityAt25Celsius(), 1.0e-7);
    assertEquals(0.8437837, qualification.getRequiredWaterActivityAt40Celsius(), 1.0e-7);
    assertFalse(qualification.isPureWaterEnvelopePass());
    assertFalse(qualification.isSodiumChloride25CEnvelopePass());
    assertFalse(qualification.isSodiumChloride40CEnvelopePass());
    assertFalse(qualification.isPublicationReady());
    assertEquals("REJECTED", qualification.getDecision());
    assertEquals("10.3389/fnuen.2023.1208582", qualification.getEvidenceDoi());
    assertEquals("10.1139/v61-228", qualification.getPrimaryLineageDoi());
    assertEquals("CC BY 4.0", qualification.getEvidenceLicense());
    assertEquals(1.0, CalciumSulfatePhaseBoundaryQualification.REFERENCE_PRESSURE_BARA, 0.0);
    assertTrue(qualification.isReferencePressureEnvelopePass());
    assertEquals(originalTemperature, system.getTemperature(), 0.0);
    assertEquals(originalPressure, system.getPressure(), 0.0);
  }

  @Test
  void evidenceObjectIsDeterministicSerializableAndPressureScoped() throws Exception {
    CalciumSulfatePhaseBoundaryQualification first = new ThermodynamicOperations(new SystemSrkEos(313.15, 1.01325))
        .qualifyCalciumSulfatePhaseBoundary();
    CalciumSulfatePhaseBoundaryQualification repeated = new ThermodynamicOperations(new SystemSrkEos(313.15, 1.01325))
        .qualifyCalciumSulfatePhaseBoundary();
    assertEquals(first.formatDiagnostic(), repeated.formatDiagnostic());
    assertFalse(first.getLimitations().isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> first.getLimitations().add("unexpected"));

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
      output.writeObject(first);
    }
    CalciumSulfatePhaseBoundaryQualification restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
      restored = (CalciumSulfatePhaseBoundaryQualification) input.readObject();
    }
    assertEquals(first.formatDiagnostic(), restored.formatDiagnostic());
    assertTrue(first.isReferencePressureEnvelopePass());

    CalciumSulfatePhaseBoundaryQualification oneBar = new ThermodynamicOperations(new SystemSrkEos(313.15, 1.0))
        .qualifyCalciumSulfatePhaseBoundary();
    assertTrue(oneBar.isReferencePressureEnvelopePass());

    CalciumSulfatePhaseBoundaryQualification outsideAtmosphericEnvelope = new ThermodynamicOperations(
        new SystemSrkEos(313.15, 1.03)).qualifyCalciumSulfatePhaseBoundary();
    assertFalse(outsideAtmosphericEnvelope.isReferencePressureEnvelopePass());

    CalciumSulfatePhaseBoundaryQualification highPressure = new ThermodynamicOperations(new SystemSrkEos(313.15, 500.0))
        .qualifyCalciumSulfatePhaseBoundary();
    assertFalse(highPressure.isReferencePressureEnvelopePass());
    assertFalse(highPressure.isPublicationReady());
    assertEquals(75.92, highPressure.getPredictedPureWaterTransitionAtEvaluatedPressureCelsius(), 0.01);
    assertEquals(52.4 * (500.0 - 1.01325) / (83.1446 * 313.15), highPressure.getAnhydriteLogKspPressureCorrection(),
        1.0e-12);
    assertEquals(33.0 * (500.0 - 1.01325) / (83.1446 * 313.15), highPressure.getGypsumLogKspPressureCorrection(),
        1.0e-12);
    assertTrue(highPressure.formatDiagnostic().contains("highPressureQualified=false"));
  }
}
