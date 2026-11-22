package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.physicalproperties.system.gasphysicalproperties.GasPhysicalProperties;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Compatibility of PFCT correction factors with saved models predating the field.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class PFCTLegacySerializationTest {
  /**
   * Missing serialized factors recover the historical calculation before cloning or evaluation.
   *
   * @param model concrete PFCT variant
   * @throws Exception if serialization or field access fails
   */
  @ParameterizedTest
  @ValueSource(strings = {"original", "mod86", "heavy-oil"})
  void missingFactorsRecoverHistoricalViscosity(String model) throws Exception {
    PhysicalProperties original = properties(model);
    double expected = original.getViscosityModel().calcViscosity();
    Field factors = original.getViscosityModel().getClass().getDeclaredField("cspViscosityCorrectionFactors");
    factors.setAccessible(true);
    // An absent object field in an older Java stream is read as null.
    factors.set(original.getViscosityModel(), null);
    PhysicalProperties restored = roundTrip(original);
    assertArrayEquals(new double[] {1.0, 1.0, 1.0, 1.0}, restored.getCspViscosityCorrectionFactors(), 0.0);
    assertEquals(expected, restored.getViscosityModel().calcViscosity(), Math.abs(expected) * 1e-12);
    PhysicalProperties copy = restored.clone();
    assertNotSame(restored.getViscosityModel(), copy.getViscosityModel());
    copy.setCspViscosityCorrectionFactor(0, 0.7);
    assertArrayEquals(new double[] {1.0, 1.0, 1.0, 1.0}, restored.getCspViscosityCorrectionFactors(), 0.0);
  }

  /**
   * Recovery leaves existing tuning intact and copies retain independent correction vectors.
   *
   * @param model concrete PFCT variant
   * @throws Exception if serialization fails
   */
  @ParameterizedTest
  @ValueSource(strings = {"original", "mod86", "heavy-oil"})
  void tunedFactorsSurviveSerializationAndClone(String model) throws Exception {
    PhysicalProperties original = properties(model);
    double[] tuned = {0.6232, 1.1507, 1.0, 1.0};
    original.setCspViscosityCorrectionFactors(tuned);
    double expected = original.getViscosityModel().calcViscosity();
    PhysicalProperties restored = roundTrip(original);
    assertArrayEquals(tuned, restored.getCspViscosityCorrectionFactors(), 0.0);
    assertEquals(expected, restored.getViscosityModel().calcViscosity(), Math.abs(expected) * 1e-12);
    PhysicalProperties copy = restored.clone();
    copy.setCspViscosityCorrectionFactor(0, 0.8);
    assertArrayEquals(tuned, restored.getCspViscosityCorrectionFactors(), 0.0);
    restored.setCspViscosityCorrectionFactor(1, 1.2);
    assertArrayEquals(tuned, original.getCspViscosityCorrectionFactors(), 0.0);
  }

  /**
   * Configures a methane/ethane phase and the requested concrete calculator.
   *
   * @param model concrete PFCT variant
   * @return initialized physical properties
   */
  private PhysicalProperties properties(String model) {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 35.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    fluid.init(1);
    return new PfctProperties(fluid.getPhase(0), model);
  }

  /**
   * Serializes the complete property graph with the native Java format.
   *
   * @param properties property graph to save
   * @return independently loaded graph
   * @throws Exception if serialization fails
   */
  private PhysicalProperties roundTrip(PhysicalProperties properties) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(properties);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (PhysicalProperties) input.readObject();
    }
  }

  /**
   * Installs all three concrete PFCT variants through the existing protected calculator field.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static class PfctProperties extends GasPhysicalProperties {
    private static final long serialVersionUID = 1L;

    /**
     * Installs one concrete PFCT model.
     *
     * @param phase configured gas phase
     * @param model concrete PFCT variant
     */
    private PfctProperties(PhaseInterface phase, String model) {
      super(phase, 0, 0);
      if ("original".equals(model)) {
        viscosityCalc = new PFCTViscosityMethod(this);
      } else if ("mod86".equals(model)) {
        viscosityCalc = new PFCTViscosityMethodMod86(this);
      } else {
        viscosityCalc = new PFCTViscosityMethodHeavyOil(this);
      }
    }
  }
}
