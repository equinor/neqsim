package neqsim.thermo.util.readwrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies that binary interaction parameters survive an E300 export without loss of precision.
 *
 * @author esol
 * @version $Id: $Id
 */
public class EclipseFluidReadWriteBicPrecisionTest {
  /**
   * Builds a three-component fluid with small, non-round binary interaction parameters.
   *
   * @return fluid with the mixing rule set and the parameters applied to every phase
   */
  private SystemInterface createFluid() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.15);
    fluid.addComponent("CO2", 0.05);
    fluid.setMixingRule("classic");
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      PhaseEosInterface eos = (PhaseEosInterface) fluid.getPhase(phase);
      eos.getEosMixingRule().setBinaryInteractionParameter(0, 1, 4.7251e-4);
      eos.getEosMixingRule().setBinaryInteractionParameter(1, 0, 4.7251e-4);
      eos.getEosMixingRule().setBinaryInteractionParameter(0, 2, 0.1234567891234);
      eos.getEosMixingRule().setBinaryInteractionParameter(2, 0, 0.1234567891234);
    }
    return fluid;
  }

  /**
   * The exported BIC text must hold the full value, not a three-digit rounding.
   */
  @Test
  void testBicTextKeepsFullPrecision() {
    String e300 = EclipseFluidReadWrite.toE300String(createFluid());
    assertTrue(e300.contains("0.00047251"), "small kij must keep all digits");
    assertTrue(e300.contains("0.1234567891234"), "large kij must keep all digits");
  }

  /**
   * Writing and reading back must return the same kij values.
   */
  @Test
  void testBicRoundTripIsExact() {
    SystemInterface fluid = createFluid();
    String e300 = EclipseFluidReadWrite.toE300String(fluid);
    java.nio.file.Path file;
    try {
      file = java.nio.file.Files.createTempFile("bic_precision", ".e300");
      java.nio.file.Files.write(file, e300.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
    SystemInterface back = EclipseFluidReadWrite.read(file.toString());
    java.io.File f = file.toFile();
    assertTrue(f.delete() || !f.exists());
    PhaseEosInterface eos = (PhaseEosInterface) back.getPhase(0);
    int methane = back.getPhase(0).getComponent("methane").getComponentNumber();
    int ethane = back.getPhase(0).getComponent("ethane").getComponentNumber();
    int co2 = back.getPhase(0).getComponent("CO2").getComponentNumber();
    assertEquals(4.7251e-4, eos.getEosMixingRule().getBinaryInteractionParameter(methane, ethane), 1.0e-15);
    assertEquals(0.1234567891234, eos.getEosMixingRule().getBinaryInteractionParameter(methane, co2), 1.0e-15);
  }
}
