package neqsim.thermo.util.readwrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemInterface;

/**
 * Independent E300 fixtures for metric and legacy critical-volume import through both reader paths.
 *
 * @author NeqSim
 * @version 1.0
 */
class EclipseCriticalVolumeUnitsTest {
  /**
   * Import known critical volumes without deriving the input from the NeqSim writer.
   *
   * @param legacy whether the fixture uses legacy NeqSim cm3/mol values
   * @param directory temporary fixture directory
   * @throws IOException if the fixture cannot be written
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void readsCriticalVolumeInBothReaderPaths(boolean legacy, @TempDir Path directory) throws IOException {
    String volumes = legacy ? "98.6\n350.0\n" : "0.0986\n0.3500\n";
    String contents = "METRIC\nEOS\nPR /\nNCOMPS\n2 /\nCNAMES\nC1\nC7\n/\n"
        + "TCRIT\n190.564\n540.0\n/\nPCRIT\n45.99\n29.0\n/\nACF\n0.011\n0.3\n/\n"
        + "MW\n16.043\n92.0\n/\nTBOIL\n111.66\n365.0\n/\nVCRIT\n" + volumes
        + "/\nPARACHOR\n77.0\n260.0\n/\nSSHIFT\n0.0\n0.0\n/\nZI\n0.9\n0.1\n/\nBIC\n0.02\n/\n";
    Path input = directory.resolve("critical-volume.e300");
    Files.write(input, contents.getBytes(StandardCharsets.UTF_8));

    SystemInterface imported = EclipseFluidReadWrite.read(input.toString());
    assertCriticalVolumes(imported, 1);

    SystemInterface replicated = EclipseFluidReadWrite.read(input.toString(), new String[] {"north", "south"});
    assertCriticalVolumes(replicated, 2);
  }

  /**
   * Check the pure and pseudo-component volumes in every phase and replicated fluid.
   *
   * @param fluid imported fluid
   * @param copies number of component copies
   */
  private void assertCriticalVolumes(SystemInterface fluid, int copies) {
    assertEquals(2 * copies, fluid.getNumberOfComponents());
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      for (int copy = 0; copy < copies; copy++) {
        assertEquals(98.6, fluid.getPhase(phase).getComponent(2 * copy).getCriticalVolume(), 1.0e-9,
            "Methane critical volume must be stored in cm3/mol");
        assertEquals(350.0, fluid.getPhase(phase).getComponent(2 * copy + 1).getCriticalVolume(), 1.0e-9,
            "Pseudo-component critical volume must be stored in cm3/mol");
      }
    }
  }
}
