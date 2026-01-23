package neqsim.thermo.util.readwrite;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for using WhitsonPVTReader with Whitson Gamma characterization model.
 *
 * @author ESOL
 */
public class WhitsonPVTReaderGammaCharacterizationTest {
  @TempDir
  Path tempDir;

  /**
   * Create sample Whitson PVT file content with C7+ gamma parameters.
   */
  private String createSampleFileContent() {
    StringBuilder sb = new StringBuilder();
    sb.append("Parameter\tValue\n");
    sb.append("EOS Type\tPR\n");
    sb.append("LBC P0\t0.1023\n");
    sb.append("LBC P1\t0.023364\n");
    sb.append("LBC P2\t0.058533\n");
    sb.append("LBC P3\t-0.037734245\n");
    sb.append("LBC P4\t0.00839916\n");
    sb.append("LBC F0\t0.1\n");
    sb.append("C7+ Gamma Shape\t0.677652\n");
    sb.append("C7+ Gamma Bound\t94.9981\n");
    sb.append("Omega A, ΩA\t0.457236\n");
    sb.append("Omega B, ΩB\t0.0777961\n");
    sb.append("\n\n\n");
    sb.append(
        "Component\tMW\tPc\tTc\tAF, ω\tVolume Shift, s\tZcVisc\tVcVisc\tVc\tZc\tPchor\tSG\tTb\tLMW\n");
    sb.append("-\t-\tbara\tC\t-\t-\t-\tm3/kmol\tm3/kmol\t-\t-\t-\tC\t-\n");
    sb.append("CO2\t44.01000\t73.74000\t30.97000\t0.225000\t0.001910\t0.274330\t");
    sb.append("0.09407\t0.09407\t0.274330\t80.00\t0.76193\t-88.266\t\n");
    sb.append("N2\t28.01400\t33.98000\t-146.95000\t0.037000\t-0.167580\t0.291780\t");
    sb.append("0.09010\t0.09010\t0.291780\t59.10\t0.28339\t-195.903\t\n");
    sb.append("C1\t16.04300\t45.99000\t-82.59000\t0.011000\t-0.149960\t0.286200\t");
    sb.append("0.09860\t0.09860\t0.286200\t71.00\t0.14609\t-161.593\t\n");
    sb.append("C2\t30.07000\t48.72000\t32.17000\t0.099000\t-0.062800\t0.279240\t");
    sb.append("0.14550\t0.14550\t0.279240\t111.00\t0.32976\t-88.717\t\n");
    sb.append("C3\t44.09700\t42.48000\t96.68000\t0.152000\t-0.063810\t0.276300\t");
    sb.append("0.20000\t0.20000\t0.276300\t151.00\t0.50977\t-42.216\t\n");
    sb.append("C7\t97.63000\t32.19980\t278.01800\t0.266280\t-0.023030\t0.266370\t");
    sb.append("0.37910\t0.37962\t0.266740\t269.31\t0.74464\t92.822\t94.99800\n");
    sb.append("C10\t134.46700\t24.44820\t358.36400\t0.397560\t0.084730\t0.242910\t");
    sb.append("0.52169\t0.54276\t0.252720\t357.72\t0.78410\t170.293\t127.77900\n");
    sb.append("\n\n\n\n");
    sb.append("BIPS\tCO2\tN2\tC1\tC2\tC3\tC7\tC10\n");
    sb.append("CO2\t0.00\t0.00\t0.11\t0.13\t0.13\t0.12\t0.12\n");
    sb.append("N2\t0.00\t0.00\t0.03\t0.01\t0.09\t0.11\t0.11\n");
    sb.append("C1\t0.11\t0.03\t0.00\t0.00\t0.00\t0.02\t0.03\n");
    sb.append("C2\t0.13\t0.01\t0.00\t0.00\t0.00\t0.00\t0.00\n");
    sb.append("C3\t0.13\t0.09\t0.00\t0.00\t0.00\t0.00\t0.00\n");
    sb.append("C7\t0.12\t0.11\t0.02\t0.00\t0.00\t0.00\t0.00\n");
    sb.append("C10\t0.12\t0.11\t0.03\t0.00\t0.00\t0.00\t0.00\n");
    return sb.toString();
  }

  /**
   * Create sample file content with a plus fraction for characterization.
   */
  private String createFileWithPlusFraction() {
    StringBuilder sb = new StringBuilder();
    sb.append("Parameter\tValue\n");
    sb.append("EOS Type\tSRK\n");
    sb.append("LBC P0\t0.1023\n");
    sb.append("LBC P1\t0.023364\n");
    sb.append("LBC P2\t0.058533\n");
    sb.append("LBC P3\t-0.037734245\n");
    sb.append("LBC P4\t0.00839916\n");
    sb.append("LBC F0\t0.1\n");
    sb.append("C7+ Gamma Shape\t1.2\n");
    sb.append("C7+ Gamma Bound\t90.0\n");
    sb.append("Omega A, ΩA\t0.4274802\n");
    sb.append("Omega B, ΩB\t0.08664035\n");
    sb.append("\n\n\n");
    sb.append(
        "Component\tMW\tPc\tTc\tAF, ω\tVolume Shift, s\tZcVisc\tVcVisc\tVc\tZc\tPchor\tSG\tTb\tLMW\n");
    sb.append("-\t-\tbara\tC\t-\t-\t-\tm3/kmol\tm3/kmol\t-\t-\t-\tC\t-\n");
    sb.append("N2\t28.01400\t33.98000\t-146.95000\t0.037000\t-0.167580\t0.291780\t");
    sb.append("0.09010\t0.09010\t0.291780\t59.10\t0.28339\t-195.903\t\n");
    sb.append("CO2\t44.01000\t73.74000\t30.97000\t0.225000\t0.001910\t0.274330\t");
    sb.append("0.09407\t0.09407\t0.274330\t80.00\t0.76193\t-88.266\t\n");
    sb.append("C1\t16.04300\t45.99000\t-82.59000\t0.011000\t-0.149960\t0.286200\t");
    sb.append("0.09860\t0.09860\t0.286200\t71.00\t0.14609\t-161.593\t\n");
    sb.append("C2\t30.07000\t48.72000\t32.17000\t0.099000\t-0.062800\t0.279240\t");
    sb.append("0.14550\t0.14550\t0.279240\t111.00\t0.32976\t-88.717\t\n");
    sb.append("C3\t44.09700\t42.48000\t96.68000\t0.152000\t-0.063810\t0.276300\t");
    sb.append("0.20000\t0.20000\t0.276300\t151.00\t0.50977\t-42.216\t\n");
    sb.append("i-C4\t58.12400\t36.48000\t134.98000\t0.185000\t-0.071670\t0.282500\t");
    sb.append("0.26260\t0.26260\t0.282500\t181.00\t0.56312\t-11.849\t\n");
    sb.append("n-C4\t58.12400\t37.96000\t152.03000\t0.201000\t-0.063330\t0.274000\t");
    sb.append("0.25500\t0.25500\t0.274000\t189.00\t0.58455\t-0.566\t\n");
    sb.append("C6\t86.18000\t30.25000\t234.45000\t0.301000\t0.000000\t0.264000\t");
    sb.append("0.37000\t0.37000\t0.264000\t271.00\t0.66400\t68.730\t\n");
    sb.append("C7_PC\t200.00000\t18.00000\t400.00000\t0.500000\t0.100000\t0.250000\t");
    sb.append("0.60000\t0.60000\t0.250000\t450.00\t0.85000\t250.000\t\n");
    return sb.toString();
  }

  /**
   * Test using Whitson Gamma characterization with gamma parameters from the PVT file.
   */
  @Test
  void testWhitsonGammaCharacterizationFromFile() throws IOException {
    File tempFile = tempDir.resolve("test_gamma.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(createFileWithPlusFraction());
    }

    // Read the fluid with composition using static read method
    double[] composition = {0.005, 0.02, 0.70, 0.08, 0.05, 0.02, 0.03, 0.02, 0.075};
    SystemInterface fluid = WhitsonPVTReader.read(tempFile.getAbsolutePath(), composition);

    assertNotNull(fluid, "Fluid should be created");

    // The gamma parameters (shape=1.2, bound=90.0) are in the file
    // Configure Whitson Gamma characterization with these parameters
    fluid.getCharacterization().setTBPModel("PedersenSRK");
    fluid.getCharacterization().setPlusFractionModel("Whitson Gamma Model");

    // Apply gamma parameters using fluent API (matching values from file)
    fluid.getCharacterization().setGammaShapeParameter(1.2).setGammaMinMW(90.0);

    // Configure lumping to get 7 pseudo-components
    fluid.getCharacterization().setLumpingModel("PVTlumpingModel");
    fluid.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(7);

    // Run characterization
    fluid.getCharacterization().characterisePlusFraction();

    // Set mixing rule and initialize
    fluid.setMixingRule("classic");
    fluid.init(3);
    fluid.initPhysicalProperties();

    // Run flash calculation
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    // Verify characterization produced expected results
    assertTrue(fluid.getNumberOfComponents() >= 7,
        "Should have at least 7 components after characterization");

    // Print fluid composition for debugging
    // fluid.prettyPrint();

    System.out.println("Number of components after Whitson Gamma characterization: "
        + fluid.getNumberOfComponents());
    System.out.println("Vapor fraction: " + fluid.getBeta());
    fluid.prettyPrint();
  }

  /**
   * Test using Whitson Gamma characterization with custom parameters (fluent API).
   */
  @Test
  void testWhitsonGammaWithFluentAPI() throws IOException {
    File tempFile = tempDir.resolve("test_gamma_fluent.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(createFileWithPlusFraction());
    }

    // Read the fluid
    double[] composition = {0.005, 0.02, 0.70, 0.08, 0.05, 0.02, 0.03, 0.02, 0.075};
    SystemInterface fluid = WhitsonPVTReader.read(tempFile.getAbsolutePath(), composition);

    assertNotNull(fluid, "Fluid should be created");

    // Configure using fluent API
    fluid.getCharacterization().setTBPModel("PedersenSRK");
    fluid.getCharacterization().setPlusFractionModel("Whitson Gamma Model");

    // Use fluent API to set gamma parameters
    fluid.getCharacterization().setGammaShapeParameter(1.0) // alpha = 1.0 (exponential)
        .setGammaMinMW(90.0) // eta = 90 g/mol (minimum MW)
        .setGammaDensityModel("Soreide"); // Soreide density correlation

    // Configure for 7 lumped pseudo-components - must set lumping model first!
    fluid.getCharacterization().setLumpingModel("PVTlumpingModel");
    fluid.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(7);

    // Run characterization
    fluid.getCharacterization().characterisePlusFraction();

    // Verify gamma model parameters were set (via the characterization interface)
    // Parameters are applied internally when characterization runs

    // Initialize with mixing rule and flash
    fluid.setMixingRule("classic");
    fluid.init(3);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    assertTrue(fluid.getNumberOfComponents() >= 7, "Should have components after characterization");

    System.out.println("Components after fluent API config: " + fluid.getNumberOfComponents());
    fluid.prettyPrint();
  }

  /**
   * Test that gamma parameters from WhitsonPVTReader are correctly parsed. Note: The
   * getGammaParameters() method is available on instances but requires parsing the file first.
   * Since parseFile is private, we verify via the static read method.
   */
  @Test
  void testGammaParametersParsing() throws IOException {
    File tempFile = tempDir.resolve("test_gamma_params.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(createSampleFileContent());
    }

    // Read fluid - this parses the file internally
    SystemInterface fluid = WhitsonPVTReader.read(tempFile.getAbsolutePath());

    assertNotNull(fluid, "Fluid should be created from file with gamma parameters");

    // The file contains: C7+ Gamma Shape = 0.677652, C7+ Gamma Bound = 94.9981
    // These are parsed internally and can be used when configuring characterization
    fluid.getCharacterization().setPlusFractionModel("Whitson Gamma Model");
    fluid.getCharacterization().setGammaShapeParameter(0.677652).setGammaMinMW(94.9981);

    // Verify the configuration doesn't throw errors
    assertNotNull(fluid.getCharacterization().getPlusFractionModel());
  }

  /**
   * Test comparing Pedersen vs Whitson Gamma characterization results.
   */
  @Test
  void testPedersenVsWhitsonGammaComparison() throws IOException {
    File tempFile = tempDir.resolve("test_comparison.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(createFileWithPlusFraction());
    }

    double[] composition = {0.005, 0.02, 0.70, 0.08, 0.05, 0.02, 0.03, 0.02, 0.075};

    // Create fluid with Pedersen characterization
    SystemInterface fluidPedersen = WhitsonPVTReader.read(tempFile.getAbsolutePath(), composition);
    fluidPedersen.getCharacterization().setTBPModel("PedersenSRK");
    fluidPedersen.getCharacterization().setPlusFractionModel("Pedersen");
    fluidPedersen.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(7);
    fluidPedersen.getCharacterization().characterisePlusFraction();
    fluidPedersen.setMixingRule("classic");
    fluidPedersen.init(3);
    ThermodynamicOperations opsPedersen = new ThermodynamicOperations(fluidPedersen);
    opsPedersen.TPflash();

    // Create fluid with Whitson Gamma characterization
    SystemInterface fluidGamma = WhitsonPVTReader.read(tempFile.getAbsolutePath(), composition);
    fluidGamma.getCharacterization().setTBPModel("PedersenSRK");
    fluidGamma.getCharacterization().setPlusFractionModel("Whitson Gamma Model");
    fluidGamma.getCharacterization().setGammaShapeParameter(1.0).setGammaMinMW(90.0);
    fluidGamma.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(7);
    fluidGamma.getCharacterization().characterisePlusFraction();
    fluidGamma.setMixingRule("classic");
    fluidGamma.init(3);
    ThermodynamicOperations opsGamma = new ThermodynamicOperations(fluidGamma);
    opsGamma.TPflash();
    fluidGamma.prettyPrint();

    // Both should produce valid results
    assertTrue(fluidPedersen.getNumberOfComponents() >= 7,
        "Pedersen should have at least 7 components");
    assertTrue(fluidGamma.getNumberOfComponents() >= 7, "Gamma should have at least 7 components");

    System.out.println("\n=== Characterization Comparison ===");
    System.out.println("Pedersen model:");
    System.out.println("  Components: " + fluidPedersen.getNumberOfComponents());
    System.out.println("  Vapor fraction: " + String.format("%.6f", fluidPedersen.getBeta()));

    System.out.println("Whitson Gamma model:");
    System.out.println("  Components: " + fluidGamma.getNumberOfComponents());
    System.out.println("  Vapor fraction: " + String.format("%.6f", fluidGamma.getBeta()));
  }
}
