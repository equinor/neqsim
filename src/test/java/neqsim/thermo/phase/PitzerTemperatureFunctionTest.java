package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;

/** Tests PHREEQC six-term temperature semantics and Pitzer-family placement. */
class PitzerTemperatureFunctionTest extends neqsim.NeqSimTest {
  private static final double REFERENCE_TEMPERATURE = 298.15;
  private static final double[] COEFFICIENTS =
      {0.12, 250.0, -0.035, 4.2e-4, -3.1e-7, 18000.0};

  @Test
  void matchesPublicDomainPhreeqcTemperatureFunction() {
    PitzerTemperatureFunction function =
        new PitzerTemperatureFunction(REFERENCE_TEMPERATURE, COEFFICIENTS);

    assertEquals(0.12, function.valueAt(REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.12, function.valueAt(REFERENCE_TEMPERATURE + 5.0e-4), 0.0);
    assertEquals(-0.11371073586719137, function.valueAt(373.15), 2.0e-15);
    assertEquals(REFERENCE_TEMPERATURE, function.getReferenceTemperature(), 0.0);
  }

  @Test
  void rejectsInvalidInputsAndProtectsCoefficientState() {
    assertThrows(IllegalArgumentException.class,
        () -> new PitzerTemperatureFunction(0.0, COEFFICIENTS));
    assertThrows(IllegalArgumentException.class,
        () -> new PitzerTemperatureFunction(REFERENCE_TEMPERATURE, new double[5]));
    assertThrows(IllegalArgumentException.class,
        () -> new PitzerTemperatureFunction(REFERENCE_TEMPERATURE,
            new double[] {0.0, 0.0, Double.NaN, 0.0, 0.0, 0.0}));

    PitzerTemperatureFunction function =
        new PitzerTemperatureFunction(REFERENCE_TEMPERATURE, COEFFICIENTS);
    double[] copy = function.getCoefficients();
    copy[0] = 99.0;
    assertArrayEquals(COEFFICIENTS, function.getCoefficients(), 0.0);
    assertEquals(0.12, function.valueAt(REFERENCE_TEMPERATURE), 0.0);
    assertThrows(IllegalArgumentException.class, () -> function.valueAt(-1.0));
  }

  @Test
  void appliesTemperatureFunctionsToEveryImplementedPitzerFamily() {
    PhasePitzer phase = createPhase();
    int sodium = phase.getComponent("Na+").getComponentNumber();
    int potassium = phase.getComponent("K+").getComponentNumber();
    int chloride = phase.getComponent("Cl-").getComponentNumber();

    phase.setPhreeqcBinaryTemperatureCoefficients(sodium, chloride, REFERENCE_TEMPERATURE,
        COEFFICIENTS, COEFFICIENTS, COEFFICIENTS);
    phase.setBeta2TemperatureCoefficients(sodium, chloride, REFERENCE_TEMPERATURE,
        COEFFICIENTS);
    phase.setThetaTemperatureCoefficients(sodium, potassium, REFERENCE_TEMPERATURE,
        COEFFICIENTS);
    phase.setPsiTemperatureCoefficients(sodium, potassium, chloride,
        REFERENCE_TEMPERATURE, COEFFICIENTS);

    double expected = -0.11371073586719137;
    assertEquals(expected, phase.getBeta0ij(sodium, chloride, 373.15), 2.0e-15);
    assertEquals(expected, phase.getBeta1ij(sodium, chloride, 373.15), 2.0e-15);
    assertEquals(expected, phase.getCphiij(sodium, chloride, 373.15), 2.0e-15);
    assertEquals(expected, phase.getBeta2ij(sodium, chloride, 373.15), 2.0e-15);
    assertEquals(expected, phase.getThetaij(potassium, sodium, 373.15), 2.0e-15);
    assertEquals(expected, phase.getPsiijk(chloride, potassium, sodium, 373.15),
        2.0e-15);

    PhasePitzer clone = phase.clone();
    clone.setThetaTemperatureCoefficients(sodium, potassium, REFERENCE_TEMPERATURE,
        new double[] {0.25, 0.0, 0.0, 0.0, 0.0, 0.0});
    assertEquals(expected, phase.getThetaij(sodium, potassium, 373.15), 2.0e-15);
    assertEquals(0.25, clone.getThetaij(sodium, potassium, 373.15), 0.0);
  }

  @Test
  void mapsPhreeqcC0DirectlyToCphiForDifferentChargeProducts() {
    PhasePitzer phase = createPhase();
    int sodium = phase.getComponent("Na+").getComponentNumber();
    int calcium = phase.getComponent("Ca++").getComponentNumber();
    int chloride = phase.getComponent("Cl-").getComponentNumber();
    double[] zeros = new double[6];

    phase.setPhreeqcBinaryTemperatureCoefficients(sodium, chloride, REFERENCE_TEMPERATURE,
        zeros, zeros, COEFFICIENTS);
    phase.setPhreeqcBinaryTemperatureCoefficients(calcium, chloride, REFERENCE_TEMPERATURE,
        zeros, zeros, COEFFICIENTS);

    double expectedCphi = -0.11371073586719137;
    assertEquals(expectedCphi, phase.getCphiij(sodium, chloride, 373.15), 2.0e-15);
    assertEquals(expectedCphi, phase.getCphiij(calcium, chloride, 373.15), 2.0e-15);
    assertEquals(expectedCphi / 2.0,
        phase.getCphiij(sodium, chloride, 373.15) / 2.0, 2.0e-15);
    assertEquals(expectedCphi / (2.0 * Math.sqrt(2.0)),
        phase.getCphiij(calcium, chloride, 373.15) / (2.0 * Math.sqrt(2.0)),
        2.0e-15);
  }

  @Test
  void serializationPreservesSparseTemperatureFunctions() throws Exception {
    PhasePitzer phase = createPhase();
    int sodium = phase.getComponent("Na+").getComponentNumber();
    int potassium = phase.getComponent("K+").getComponentNumber();
    phase.setThetaTemperatureCoefficients(sodium, potassium, REFERENCE_TEMPERATURE,
        COEFFICIENTS);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(phase);
    }
    PhasePitzer restored;
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (PhasePitzer) input.readObject();
    }

    assertEquals(-0.11371073586719137,
        restored.getThetaij(sodium, potassium, 373.15), 2.0e-15);
  }

  @Test
  void preservesLegacyThreeCoefficientTemperatureBehavior() {
    PhasePitzer phase = createPhase();
    int sodium = phase.getComponent("Na+").getComponentNumber();
    int chloride = phase.getComponent("Cl-").getComponentNumber();
    phase.setBinaryParameters(sodium, chloride, 0.08, 0.2, 0.001);
    phase.setBeta0T(sodium, chloride, 125.0, -0.03);

    double temperature = 333.15;
    double expected = 0.08 + 125.0 * (1.0 / temperature - 1.0 / REFERENCE_TEMPERATURE)
        - 0.03 * Math.log(temperature / REFERENCE_TEMPERATURE);
    assertEquals(expected, phase.getBeta0ij(sodium, chloride, temperature), 1.0e-15);
    assertEquals(0.2, phase.getBeta1ij(sodium, chloride, temperature), 0.0);
  }

  private static PhasePitzer createPhase() {
    SystemInterface system = new SystemPitzer(REFERENCE_TEMPERATURE, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 1.0);
    system.addComponent("K+", 0.1);
    system.addComponent("Cl-", 1.3);
    system.addComponent("Ca++", 0.1);
    return (PhasePitzer) system.getPhase(1);
  }
}
