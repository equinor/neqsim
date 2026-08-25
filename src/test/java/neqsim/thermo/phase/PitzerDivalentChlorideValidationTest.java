package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentGePitzer;
import neqsim.thermo.system.SystemPitzer;

/**
 * Independent validation and default-selection regressions for the PHREEQC BaCl2 and SrCl2
 * binary families.
 */
class PitzerDivalentChlorideValidationTest extends neqsim.NeqSimTest {
  private static final double REFERENCE_TEMPERATURE = 298.15;
  private static final double MAXIMUM_SRCL2_RELATIVE_ACTIVITY_RESIDUAL = 0.032;

  @Test
  void bariumAndStrontiumChlorideUseCatalogByDefault() {
    PhasePitzer barium = createBinaryPhase("Ba++", 0.5, REFERENCE_TEMPERATURE);
    triggerDefaultCatalogSelection(barium);
    assertEquals(PitzerParameterDatasets.PHREEQC_PITZER_CATALOG_ID, barium.getParameterDatasetId());
    assertEquals(0.5268, barium.getBeta0ij(index(barium, "Ba++"), index(barium, "Cl-"), REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.687, barium.getBeta1ij(index(barium, "Ba++"), index(barium, "Cl-"), REFERENCE_TEMPERATURE), 0.0);
    assertEquals(-0.143, barium.getCphiij(index(barium, "Ba++"), index(barium, "Cl-"), REFERENCE_TEMPERATURE), 0.0);
    assertBinaryState(barium, "Ba++");

    PhasePitzer strontium = createBinaryPhase("Sr++", 0.5, REFERENCE_TEMPERATURE);
    triggerDefaultCatalogSelection(strontium);
    assertEquals(PitzerParameterDatasets.PHREEQC_PITZER_CATALOG_ID, strontium.getParameterDatasetId());
    assertEquals(0.2858,
        strontium.getBeta0ij(index(strontium, "Sr++"), index(strontium, "Cl-"), REFERENCE_TEMPERATURE), 0.0);
    assertEquals(1.667,
        strontium.getBeta1ij(index(strontium, "Sr++"), index(strontium, "Cl-"), REFERENCE_TEMPERATURE), 0.0);
    assertEquals(-0.0013,
        strontium.getCphiij(index(strontium, "Sr++"), index(strontium, "Cl-"), REFERENCE_TEMPERATURE), 0.0);
    assertBinaryState(strontium, "Sr++");
  }

  @Test
  void strontiumChlorideMatchesCompleteNistThermoMlMatrix() throws Exception {
    List<ReferenceState> states = readReferenceStates();
    assertEquals(58, states.size());

    double maximumRelativeResidual = 0.0;
    for (ReferenceState state : states) {
      PhasePitzer phase = createBinaryPhase("Sr++", state.molality, state.temperature);
      triggerDefaultCatalogSelection(phase);
      double actual = meanIonicActivityCoefficient(phase, "Sr++");
      double relativeResidual = Math.abs(actual / state.meanActivityCoefficient - 1.0);
      maximumRelativeResidual = Math.max(maximumRelativeResidual, relativeResidual);
      assertTrue(relativeResidual <= MAXIMUM_SRCL2_RELATIVE_ACTIVITY_RESIDUAL,
          "SrCl2 relative mean-activity residual at " + state.temperature + " K and " + state.molality
              + " mol/kg: " + relativeResidual + " (archive 95% expanded uncertainty "
              + state.expandedUncertainty + ")");
      assertBinaryState(phase, "Sr++");
    }
    assertTrue(maximumRelativeResidual > 0.03,
        "Keep the declared engineering gate separate from the source point uncertainty: " + maximumRelativeResidual);
  }

  @Test
  void strontiumValidationEnvelopeIsExplicit() {
    assertTrue(PitzerParameterDatasets.isWithinStrontiumChlorideValidationRange(283.15, 0.01));
    assertTrue(PitzerParameterDatasets.isWithinStrontiumChlorideValidationRange(333.15, 3.52));
    assertFalse(PitzerParameterDatasets.isWithinStrontiumChlorideValidationRange(283.14, 0.01));
    assertFalse(PitzerParameterDatasets.isWithinStrontiumChlorideValidationRange(298.15, 3.53));
    assertFalse(PitzerParameterDatasets.isWithinStrontiumChlorideValidationRange(Double.NaN, 0.1));

    PitzerParameterQualification qualification =
        PitzerParameterDatasets.getQualification(PitzerParameterDatasets.PHREEQC_PITZER_CATALOG_ID);
    assertTrue(qualification.getValidatedSystems().toString().contains("SrCl2"));
    assertTrue(qualification.getLimitations().toString().contains("BaCl2"));
  }

  @Test
  void binaryCatalogStateIsDeterministicAcrossCloneSerializationAndChangedState() throws Exception {
    SystemPitzer system = createBinarySystem("Sr++", 0.5, REFERENCE_TEMPERATURE);
    PhasePitzer original = (PhasePitzer) system.getPhase(1);
    triggerDefaultCatalogSelection(original);
    double checksum = binaryChecksum(original, "Sr++");
    assertEquals(checksum, binaryChecksum(original, "Sr++"), 0.0);

    SystemPitzer clonedSystem = system.clone();
    PhasePitzer clone = (PhasePitzer) clonedSystem.getPhase(1);
    assertEquals(checksum, binaryChecksum(clone, "Sr++"), 1.0e-12);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(system);
    }
    SystemPitzer restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (SystemPitzer) input.readObject();
    }
    assertEquals(checksum, binaryChecksum((PhasePitzer) restored.getPhase(1), "Sr++"), 1.0e-12);

    system.addComponent("Sr++", -0.2);
    system.addComponent("Cl-", -0.4);
    system.init(0);
    PhasePitzer changed = (PhasePitzer) system.getPhase(1);
    PhasePitzer fresh = createBinaryPhase("Sr++", 0.3, REFERENCE_TEMPERATURE);
    triggerDefaultCatalogSelection(fresh);
    assertEquals(binaryChecksum(fresh, "Sr++"), binaryChecksum(changed, "Sr++"), 1.0e-12);
  }

  @Test
  void incompleteMixedBariumStrontiumTopologyFailsClosed() {
    SystemPitzer system = new SystemPitzer(REFERENCE_TEMPERATURE, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Ba++", 0.2);
    system.addComponent("Sr++", 0.3);
    system.addComponent("Cl-", 1.0);
    system.setMixingRule("classic");
    system.init(0);
    PhasePitzer phase = (PhasePitzer) system.getPhase(1);

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> PitzerParameterDatasets.applyCompletePhreeqcPitzerCatalog(phase));
    assertTrue(error.getMessage().contains("no explicit"));
    assertTrue(error.getMessage().contains("Ba++") || error.getMessage().contains("Sr++"));
  }

  private static void assertBinaryState(PhasePitzer phase, String cation) {
    phase.requireCompletePitzerParameterCoverage();
    double compositionSum = 0.0;
    double chargeResidual = 0.0;
    for (int component = 0; component < phase.getNumberOfComponents(); component++) {
      double moleFraction = phase.getComponent(component).getx();
      assertTrue(Double.isFinite(moleFraction) && moleFraction >= 0.0);
      compositionSum += moleFraction;
      chargeResidual += phase.getComponent(component).getMolality(phase)
          * phase.getComponent(component).getIonicCharge();
    }
    assertEquals(1.0, compositionSum, 1.0e-12);
    assertEquals(0.0, chargeResidual, 1.0e-12);
    assertTrue(Double.isFinite(meanIonicActivityCoefficient(phase, cation)));
    assertTrue(phase.getOsmoticCoefficientOfWater() > 0.0);
    assertTrue(waterActivity(phase) > 0.0 && waterActivity(phase) <= 1.0);
  }

  private static void triggerDefaultCatalogSelection(PhasePitzer phase) {
    phase.getExcessGibbsEnergy(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure(),
        phase.getType());
  }

  private static double meanIonicActivityCoefficient(PhasePitzer phase, String cation) {
    double cationLogGamma = componentLogGamma(phase, cation);
    double chlorideLogGamma = componentLogGamma(phase, "Cl-");
    return Math.exp((cationLogGamma + 2.0 * chlorideLogGamma) / 3.0);
  }

  private static double componentLogGamma(PhasePitzer phase, String componentName) {
    ComponentGePitzer component = (ComponentGePitzer) phase.getComponent(componentName);
    return Math.log(component.getGamma(phase, phase.getNumberOfComponents(), phase.getTemperature(),
        phase.getPressure(), phase.getType()));
  }

  private static double waterActivity(PhasePitzer phase) {
    ComponentGePitzer water = (ComponentGePitzer) phase.getComponent("water");
    return water.getGamma(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure(),
        phase.getType()) * water.getx();
  }

  private static double binaryChecksum(PhasePitzer phase, String cation) {
    return componentLogGamma(phase, cation) + componentLogGamma(phase, "Cl-") + phase.getOsmoticCoefficientOfWater()
        + waterActivity(phase);
  }

  private static PhasePitzer createBinaryPhase(String cation, double molality, double temperature) {
    return (PhasePitzer) createBinarySystem(cation, molality, temperature).getPhase(1);
  }

  private static SystemPitzer createBinarySystem(String cation, double molality, double temperature) {
    SystemPitzer system = new SystemPitzer(temperature, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent(cation, molality);
    system.addComponent("Cl-", 2.0 * molality);
    system.setMixingRule("classic");
    system.init(0);
    return system;
  }

  private static List<ReferenceState> readReferenceStates() throws Exception {
    String resource = "/neqsim/thermo/phase/phreeqc-srcl2-activity-reference.csv";
    InputStream stream = PitzerDivalentChlorideValidationTest.class.getResourceAsStream(resource);
    if (stream == null) {
      throw new IllegalStateException("Missing SrCl2 validation resource " + resource);
    }
    List<ReferenceState> states = new ArrayList<ReferenceState>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isEmpty() || line.charAt(0) == '#' || line.startsWith("temperature_K")) {
          continue;
        }
        states.add(new ReferenceState(line.split(",")));
      }
    }
    return states;
  }

  private static int index(PhasePitzer phase, String componentName) {
    return phase.getComponent(componentName).getComponentNumber();
  }

  private static final class ReferenceState {
    private final double temperature;
    private final double molality;
    private final double meanActivityCoefficient;
    private final double expandedUncertainty;

    private ReferenceState(String[] fields) {
      if (fields.length != 4) {
        throw new IllegalArgumentException("Unexpected SrCl2 validation row length: " + fields.length);
      }
      temperature = Double.parseDouble(fields[0]);
      molality = Double.parseDouble(fields[1]);
      meanActivityCoefficient = Double.parseDouble(fields[2]);
      expandedUncertainty = Double.parseDouble(fields[3]);
    }
  }
}
