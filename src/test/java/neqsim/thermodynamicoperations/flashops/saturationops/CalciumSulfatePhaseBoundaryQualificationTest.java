package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    CalciumSulfatePhaseBoundaryQualification highPressure = new ThermodynamicOperations(new SystemSrkEos(313.15, 500.0))
        .qualifyCalciumSulfatePhaseBoundary();
    assertFalse(highPressure.isReferencePressureEnvelopePass());
    assertFalse(highPressure.isPublicationReady());
  }
}
