package neqsim.thermo.util.readwrite;

import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.MultiStageSeparatorTest;
import neqsim.pvtsimulation.simulation.SaturationPressure;
import neqsim.pvtsimulation.simulation.ViscositySim;
import neqsim.pvtsimulation.util.PVTReportGenerator;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Unit tests for WhitsonPVTReader.
 *
 * @author ESOL
 */
public class WhitsonPVTReaderTest {

  @TempDir
  Path tempDir;

  /**
   * Create sample file content for testing.
   */
  private String createSampleFileContent() {
    StringBuilder sb = new StringBuilder();
    sb.append("Parameter\tValue\n");
    sb.append("Name\tPredictive_EOS_Parameters\n");
    sb.append("EOS Type\tPR\n");
    sb.append("First C6+\tNA\n");
    sb.append("First C7+\tNA\n");
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
   * Create minimal file content for specific tests.
   */
  private String createMinimalFileContent(String eosType, boolean includeLBC, boolean includeGamma,
      String componentLine) {
    StringBuilder sb = new StringBuilder();
    sb.append("Parameter\tValue\n");
    sb.append("EOS Type\t").append(eosType).append("\n");
    if (includeLBC) {
      sb.append("LBC P0\t0.1023\n");
      sb.append("LBC P1\t0.023364\n");
      sb.append("LBC P2\t0.058533\n");
      sb.append("LBC P3\t-0.037734\n");
      sb.append("LBC P4\t0.00839916\n");
    }
    if (includeGamma) {
      sb.append("C7+ Gamma Shape\t0.677652\n");
      sb.append("C7+ Gamma Bound\t94.9981\n");
    }
    sb.append("\n\n");
    sb.append(
        "Component\tMW\tPc\tTc\tAF, ω\tVolume Shift, s\tZcVisc\tVcVisc\tVc\tZc\tPchor\tSG\tTb\tLMW\n");
    sb.append("-\t-\tbara\tC\t-\t-\t-\tm3/kmol\tm3/kmol\t-\t-\t-\tC\t-\n");
    sb.append(componentLine).append("\n");
    return sb.toString();
  }

  @Test
  void testReadSimpleFile() throws IOException {
    // Create temp file
    File tempFile = tempDir.resolve("test_pvt.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(createSampleFileContent());
    }

    // Read file
    SystemInterface fluid = WhitsonPVTReader.read(tempFile.getAbsolutePath());

    // Verify fluid was created
    assertNotNull(fluid, "Fluid should be created");

    // Verify number of components
    assertEquals(7, fluid.getNumberOfComponents(), "Should have 7 components");

    // Verify EOS type (PR should create SystemPrEos)
    assertTrue(fluid.getClass().getName().contains("Pr"), "Should be PR EOS");
  }

  @Test
  void testReadWithComposition() throws IOException {
    File tempFile = tempDir.resolve("test_pvt2.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(createSampleFileContent());
    }

    // Specify molar composition
    double[] composition = {0.02, 0.01, 0.70, 0.10, 0.05, 0.08, 0.04};

    SystemInterface fluid = WhitsonPVTReader.read(tempFile.getAbsolutePath(), composition);

    assertNotNull(fluid, "Fluid should be created");
    assertEquals(7, fluid.getNumberOfComponents());

    // Verify total moles approximately sums to 1
    double totalMoles = 0;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      totalMoles += fluid.getPhase(0).getComponent(i).getNumberOfMolesInPhase();
    }
    assertEquals(1.0, totalMoles, 0.01, "Total moles should be approximately 1");
  }

  @Test
  void testComponentProperties() throws IOException {
    File tempFile = tempDir.resolve("test_pvt3.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(createSampleFileContent());
    }

    SystemInterface fluid = WhitsonPVTReader.read(tempFile.getAbsolutePath());

    // Find CO2 component
    var co2 = fluid.getPhase(0).getComponent("CO2");
    assertNotNull(co2, "Should have CO2 component");

    // Verify CO2 properties
    assertEquals(44.01 / 1000.0, co2.getMolarMass(), 0.001, "CO2 molar mass");
    assertEquals(73.74, co2.getPC(), 0.01, "CO2 Pc");
    assertEquals(30.97 + 273.15, co2.getTC(), 0.1, "CO2 Tc");
    assertEquals(0.225, co2.getAcentricFactor(), 0.001, "CO2 acentric factor");
  }

  @Test
  void testPseudoComponentNaming() throws IOException {
    File tempFile = tempDir.resolve("test_pvt4.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(createSampleFileContent());
    }

    SystemInterface fluid = WhitsonPVTReader.read(tempFile.getAbsolutePath());

    // C7+ components should be added as pseudo-components with _PC suffix
    boolean hasC7 = false;
    boolean hasC10 = false;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      String name = fluid.getPhase(0).getComponent(i).getComponentName();
      if (name.contains("C7")) {
        hasC7 = true;
      }
      if (name.contains("C10")) {
        hasC10 = true;
      }
    }
    assertTrue(hasC7, "Should have C7 component");
    assertTrue(hasC10, "Should have C10 component");
  }

  @Test
  void testFluidInitialization() throws IOException {
    File tempFile = tempDir.resolve("test_pvt5.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(createSampleFileContent());
    }

    double[] composition = {0.02, 0.01, 0.70, 0.10, 0.05, 0.08, 0.04};
    SystemInterface fluid = WhitsonPVTReader.read(tempFile.getAbsolutePath(), composition);

    // Set conditions and initialize
    fluid.setTemperature(373.15); // 100C
    fluid.setPressure(100.0); // 100 bar

    // Create thermoOps and run flash calculation for proper initialization
    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
    ops.TPflash();

    // Verify we can get properties after flash calculation
    double molarMass = fluid.getMolarMass();
    assertTrue(molarMass > 0, "Molar mass should be positive");

    // Verify component count is still correct
    assertEquals(7, fluid.getNumberOfComponents(), "Should still have 7 components");

    // Verify pressure and temperature are set
    assertEquals(100.0, fluid.getPressure(), 0.01, "Pressure should be 100 bar");
    assertEquals(373.15, fluid.getTemperature(), 0.1, "Temperature should be 373.15 K");
  }

  @Test
  void testLBCParametersParsing() throws IOException {
    // Create a minimal file with just parameters
    String componentLine = "C1\t16.04300\t45.99000\t-82.59000\t0.011000\t-0.149960\t"
        + "0.286200\t0.09860\t0.09860\t0.286200\t71.00\t0.14609\t-161.593\t";

    File tempFile = tempDir.resolve("test_lbc.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(createMinimalFileContent("PR", true, false, componentLine));
    }

    SystemInterface fluid = WhitsonPVTReader.read(tempFile.getAbsolutePath());
    assertNotNull(fluid);
    assertEquals(1, fluid.getNumberOfComponents());
  }

  @Test
  void testVolumeCorrection() throws IOException {
    File tempFile = tempDir.resolve("test_volcorr.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(createSampleFileContent());
    }

    SystemInterface fluid = WhitsonPVTReader.read(tempFile.getAbsolutePath());

    // Verify volume correction is enabled
    // Note: The actual volume correction values are set on components
    var co2 = fluid.getPhase(0).getComponent("CO2");
    assertEquals(0.00191, co2.getVolumeCorrectionConst(), 0.0001, "CO2 volume shift should be set");
  }

  @Test
  void testSRKEosType() throws IOException {
    String componentLine = "C1\t16.04300\t45.99000\t-82.59000\t0.011000\t-0.149960\t"
        + "0.286200\t0.09860\t0.09860\t0.286200\t71.00\t0.14609\t-161.593\t";

    File tempFile = tempDir.resolve("test_srk.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(createMinimalFileContent("SRK", true, false, componentLine));
    }

    SystemInterface fluid = WhitsonPVTReader.read(tempFile.getAbsolutePath());

    // Verify SRK EOS type
    assertTrue(fluid.getClass().getName().contains("Srk"), "Should be SRK EOS");
  }

  @Test
  void testGammaParameters() throws IOException {
    String componentLine = "C7\t97.63000\t32.19980\t278.01800\t0.266280\t-0.023030\t"
        + "0.266370\t0.37910\t0.37962\t0.266740\t269.31\t0.74464\t92.822\t94.99800";

    File tempFile = tempDir.resolve("test_gamma.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(createMinimalFileContent("PR", false, true, componentLine));
    }

    SystemInterface fluid = WhitsonPVTReader.read(tempFile.getAbsolutePath());
    assertNotNull(fluid);
    // Gamma parameters are parsed but currently stored in the reader, not applied to fluid
    // This test verifies the parsing doesn't fail
  }

  @Test
  void testBinaryInteractionParameters() throws IOException {
    File tempFile = tempDir.resolve("test_bip.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(createSampleFileContent());
    }

    SystemInterface fluid = WhitsonPVTReader.read(tempFile.getAbsolutePath());

    // Initialize the fluid (don't call setMixingRule again as it would reset BIPs)
    fluid.init(0);
    fluid.init(1);

    // Verify BIPs were set - check CO2-C1 interaction (should be 0.11)
    // Access through the mixing rule
    neqsim.thermo.phase.PhaseEosInterface phase =
        (neqsim.thermo.phase.PhaseEosInterface) fluid.getPhase(0);

    // Get component indices - CO2 should be at index 0, methane should be at index 2
    // (ordering is CO2, N2, C1, C2, C3, C7, C10)
    int co2Index = 0; // CO2 is first component
    int c1Index = 2; // C1/methane is third component

    double bip = phase.getEosMixingRule().getBinaryInteractionParameter(co2Index, c1Index);
    assertEquals(0.11, bip, 0.001, "CO2-C1 BIP should be 0.11");
  }

  @Test
  void testLargerComponentSystem() throws IOException {
    // Create a file with more components to test scalability
    StringBuilder sb = new StringBuilder();
    sb.append("Parameter\tValue\n");
    sb.append("EOS Type\tPR\n");
    sb.append("LBC P0\t0.1023\n");
    sb.append("LBC P1\t0.023364\n");
    sb.append("LBC P2\t0.058533\n");
    sb.append("LBC P3\t-0.037734245\n");
    sb.append("LBC P4\t0.00839916\n");
    sb.append("\n\n\n");
    sb.append("Component\tMW\tPc\tTc\tAF, ω\tVolume Shift, s\t");
    sb.append("ZcVisc\tVcVisc\tVc\tZc\tPchor\tSG\tTb\tLMW\n");
    sb.append("-\t-\tbara\tC\t-\t-\t-\tm3/kmol\tm3/kmol\t-\t-\t-\tC\t-\n");
    sb.append("CO2\t44.01000\t73.74000\t30.97000\t0.225000\t0.001910\t");
    sb.append("0.274330\t0.09407\t0.09407\t0.274330\t80.00\t0.76193\t-88.266\t\n");
    sb.append("H2S\t34.08200\t89.37000\t99.95000\t0.094000\t-0.024790\t");
    sb.append("0.283810\t0.09750\t0.09750\t0.283810\t80.10\t0.79650\t-60.560\t\n");
    sb.append("N2\t28.01400\t33.98000\t-146.95000\t0.037000\t-0.167580\t");
    sb.append("0.291780\t0.09010\t0.09010\t0.291780\t59.10\t0.28339\t-195.903\t\n");
    sb.append("C1\t16.04300\t45.99000\t-82.59000\t0.011000\t-0.149960\t");
    sb.append("0.286200\t0.09860\t0.09860\t0.286200\t71.00\t0.14609\t-161.593\t\n");
    sb.append("C2\t30.07000\t48.72000\t32.17000\t0.099000\t-0.062800\t");
    sb.append("0.279240\t0.14550\t0.14550\t0.279240\t111.00\t0.32976\t-88.717\t\n");
    sb.append("C3\t44.09700\t42.48000\t96.68000\t0.152000\t-0.063810\t");
    sb.append("0.276300\t0.20000\t0.20000\t0.276300\t151.00\t0.50977\t-42.216\t\n");
    sb.append("i-C4\t58.12400\t36.48000\t134.65000\t0.185000\t-0.087000\t");
    sb.append("0.282630\t0.26290\t0.26290\t0.282630\t181.50\t0.55780\t-11.716\t\n");
    sb.append("n-C4\t58.12400\t37.96000\t151.97000\t0.201000\t-0.070090\t");
    sb.append("0.274070\t0.25500\t0.25500\t0.274070\t189.90\t0.57780\t-0.616\t\n");
    sb.append("i-C5\t72.15100\t33.80000\t187.20000\t0.227000\t-0.054770\t");
    sb.append("0.272900\t0.30600\t0.30600\t0.272900\t225.00\t0.62020\t27.734\t\n");
    sb.append("n-C5\t72.15100\t33.70000\t196.45000\t0.251000\t-0.044720\t");
    sb.append("0.268000\t0.30400\t0.30400\t0.268000\t231.50\t0.62630\t36.084\t\n");
    sb.append("\n\n\n\n");
    // Add BIPs for 10 components
    sb.append("BIPS\tCO2\tH2S\tN2\tC1\tC2\tC3\ti-C4\tn-C4\ti-C5\tn-C5\n");
    sb.append("CO2\t0.00\t0.10\t0.00\t0.11\t0.13\t0.13\t0.12\t0.12\t0.12\t0.12\n");
    sb.append("H2S\t0.10\t0.00\t0.15\t0.08\t0.07\t0.07\t0.06\t0.06\t0.06\t0.06\n");
    sb.append("N2\t0.00\t0.15\t0.00\t0.03\t0.01\t0.09\t0.11\t0.11\t0.11\t0.11\n");
    sb.append("C1\t0.11\t0.08\t0.03\t0.00\t0.00\t0.00\t0.00\t0.00\t0.01\t0.01\n");
    sb.append("C2\t0.13\t0.07\t0.01\t0.00\t0.00\t0.00\t0.00\t0.00\t0.00\t0.00\n");
    sb.append("C3\t0.13\t0.07\t0.09\t0.00\t0.00\t0.00\t0.00\t0.00\t0.00\t0.00\n");
    sb.append("i-C4\t0.12\t0.06\t0.11\t0.00\t0.00\t0.00\t0.00\t0.00\t0.00\t0.00\n");
    sb.append("n-C4\t0.12\t0.06\t0.11\t0.00\t0.00\t0.00\t0.00\t0.00\t0.00\t0.00\n");
    sb.append("i-C5\t0.12\t0.06\t0.11\t0.01\t0.00\t0.00\t0.00\t0.00\t0.00\t0.00\n");
    sb.append("n-C5\t0.12\t0.06\t0.11\t0.01\t0.00\t0.00\t0.00\t0.00\t0.00\t0.00\n");

    File tempFile = tempDir.resolve("test_large.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(sb.toString());
    }

    SystemInterface fluid = WhitsonPVTReader.read(tempFile.getAbsolutePath());

    // Verify all 10 components were added
    assertEquals(10, fluid.getNumberOfComponents(), "Should have 10 components");

    // Verify some component properties
    assertNotNull(fluid.getPhase(0).getComponent("H2S"), "Should have H2S component");
    assertNotNull(fluid.getPhase(0).getComponent("i-butane"), "Should have i-C4 component");
    assertNotNull(fluid.getPhase(0).getComponent("n-pentane"), "Should have n-C5 component");
  }

  @Test
  void testPVTReportGeneration() throws IOException {
    // Create a realistic gas condensate composition
    File tempFile = tempDir.resolve("test_pvt_report.txt").toFile();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(createSampleFileContent());
    }

    // Composition: typical gas condensate (mole fractions)
    double[] composition = {0.02, 0.01, 0.70, 0.10, 0.05, 0.08, 0.04};

    SystemInterface fluid = WhitsonPVTReader.read(tempFile.getAbsolutePath(), composition);

    // Set reservoir temperature (100C = 373.15 K)
    double reservoirTemperatureC = 100.0;
    fluid.setTemperature(reservoirTemperatureC + 273.15);
    fluid.setPressure(300.0); // Start at high pressure

    // Initialize fluid with physical properties
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    // Calculate saturation pressure
    SaturationPressure satPres = new SaturationPressure(fluid);
    satPres.setTemperature(reservoirTemperatureC, "C");
    satPres.run();
    double psat = satPres.getSaturationPressure();

    // Verify saturation pressure is reasonable (should be > 0 and < 400 bar for this fluid)
    assertTrue(psat > 50, "Saturation pressure should be > 50 bar, got: " + psat);
    assertTrue(psat < 400, "Saturation pressure should be < 400 bar, got: " + psat);
    System.out.println("Saturation pressure: " + psat + " bar");

    // Run CCE
    ConstantMassExpansion cce = new ConstantMassExpansion(fluid);
    cce.setTemperature(reservoirTemperatureC, "C");
    double[] ccePressures = {psat * 1.2, psat * 1.1, psat, psat * 0.9, psat * 0.8, psat * 0.7};
    cce.setPressures(ccePressures);
    cce.runCalc();

    // Verify CCE results
    double[] relVol = cce.getRelativeVolume();
    assertNotNull(relVol, "CCE relative volume should not be null");
    assertTrue(relVol.length > 0, "CCE should have results");
    assertTrue(relVol[0] > 0, "Relative volume should be positive");
    System.out.println("CCE relative volume at Psat: " + relVol[2]);

    // Run Viscosity simulation at various pressures
    ViscositySim viscSim = new ViscositySim(fluid);
    double[] viscPressures = {300.0, 250.0, 200.0, 150.0, 100.0};
    double[] viscTemps = new double[viscPressures.length];
    for (int i = 0; i < viscTemps.length; i++) {
      viscTemps[i] = reservoirTemperatureC + 273.15;
    }
    viscSim.setTemperaturesAndPressures(viscTemps, viscPressures);
    viscSim.runCalc();
    double[] gasViscosity = viscSim.getGasViscosity();
    assertNotNull(gasViscosity, "Gas viscosity should not be null");
    assertTrue(gasViscosity[0] > 0, "Gas viscosity should be positive");

    // Calculate gas density at various pressures (manually since DensitySim has a bug)
    double[] gasDensity = new double[viscPressures.length];
    for (int i = 0; i < viscPressures.length; i++) {
      fluid.setPressure(viscPressures[i]);
      fluid.setTemperature(viscTemps[i]);
      ops.TPflash();
      fluid.initPhysicalProperties();
      if (fluid.hasPhaseType("gas")) {
        gasDensity[i] = fluid.getPhase("gas").getDensity("kg/m3");
      }
    }
    assertTrue(gasDensity[0] > 0, "Gas density should be positive");

    // Run multi-stage separator test
    // Note: MultiStageSeparatorTest is designed for oil systems.
    // For gas condensates, GOR may not be meaningful - use CGR (condensate-gas ratio) instead.
    MultiStageSeparatorTest sepTest = new MultiStageSeparatorTest(fluid);
    sepTest.setReservoirConditions(300.0, reservoirTemperatureC);
    sepTest.addSeparatorStage(50.0, 40.0, "HP Separator");
    sepTest.addSeparatorStage(10.0, 30.0, "LP Separator");
    sepTest.addSeparatorStage(1.01325, 15.0, "Stock Tank");
    sepTest.run();
    double totalGOR = sepTest.getTotalGOR();
    double Bo = sepTest.getBo();
    // For gas condensate, reported GOR may be inverted or unreliable
    // CGR (condensate-gas ratio) = 1/GOR is more meaningful for gas condensates
    double CGR = totalGOR > 0 ? 1.0 / totalGOR : 0.0; // Sm3 condensate / Sm3 gas
    double CGR_bblperMMscf = CGR * 6289.81; // Convert to bbl/MMscf

    // Generate PVT report with all simulations
    PVTReportGenerator report = new PVTReportGenerator(fluid);
    report.setProjectInfo("Whitson PVT Reader Test", "Test Gas Condensate")
        .setReservoirConditions(300.0, reservoirTemperatureC).setSaturationPressure(psat, false) // Dew
                                                                                                 // point
                                                                                                 // for
                                                                                                 // gas
                                                                                                 // condensate
        .addCCE(cce).addSeparatorTest(sepTest);

    String markdown = report.generateMarkdownReport();

    // Verify report contains expected sections
    assertNotNull(markdown, "Report should not be null");
    assertTrue(markdown.contains("PVT Study Report"), "Report should have title");
    assertTrue(markdown.contains("Fluid Composition"), "Report should have composition");
    assertTrue(markdown.contains("CCE") || markdown.contains("Constant Composition"),
        "Report should have CCE section");
    assertTrue(markdown.contains("Separator") || markdown.contains("separator"),
        "Report should have separator test section");

    // Also test fluid's built-in JSON report
    String fluidJson = fluid.toJson();
    assertNotNull(fluidJson, "Fluid JSON should not be null");
    assertTrue(fluidJson.contains("CO2"), "JSON should contain CO2");
    assertTrue(fluidJson.contains("methane"), "JSON should contain methane");

    // Print comprehensive summary for visual verification
    System.out.println("\n========================================");
    System.out.println("       PVT REPORT SUMMARY");
    System.out.println("========================================");
    System.out.println("Fluid: Test Gas Condensate (7 components)");
    System.out.println("Reservoir T: " + reservoirTemperatureC + " C");
    System.out.println("Reservoir P: 300.0 bar");
    System.out.println("Dew Point Pressure: " + String.format("%.2f", psat) + " bar");
    System.out
        .println("Molar Mass: " + String.format("%.2f", fluid.getMolarMass() * 1000) + " g/mol");

    System.out.println("\n--- CCE Results ---");
    for (int i = 0; i < ccePressures.length; i++) {
      System.out.println("  P=" + String.format("%.1f", ccePressures[i]) + " bar, Vrel="
          + String.format("%.4f", relVol[i]));
    }

    System.out.println("\n--- Gas Viscosity ---");
    for (int i = 0; i < viscPressures.length; i++) {
      // ViscositySim returns Pa·s, multiply by 1000 to convert to cP (mPa·s)
      System.out.println("  P=" + String.format("%.1f", viscPressures[i]) + " bar, mu="
          + String.format("%.4f", gasViscosity[i] * 1000) + " cP");
    }

    System.out.println("\n--- Gas Density ---");
    for (int i = 0; i < viscPressures.length; i++) {
      System.out.println("  P=" + String.format("%.1f", viscPressures[i]) + " bar, rho="
          + String.format("%.2f", gasDensity[i]) + " kg/m3");
    }

    System.out.println("\n--- Separator Test Results ---");
    System.out.println("  (Note: For gas condensates, CGR is more meaningful than GOR)");
    System.out.println("  Reported GOR: " + String.format("%.1f", totalGOR) + " Sm3/Sm3");
    System.out.println("  CGR: " + String.format("%.3f", CGR) + " Sm3 condensate/Sm3 gas");
    System.out.println("  CGR: " + String.format("%.1f", CGR_bblperMMscf) + " bbl/MMscf");
    System.out.println("  Bo: " + String.format("%.4f", Bo) + " m3/Sm3");
    System.out
        .println("  Stock Tank API: " + String.format("%.1f", sepTest.getStockTankAPIGravity()));
    System.out.println("  Stock Tank Oil Density: "
        + String.format("%.1f", sepTest.getStockTankOilDensity()) + " kg/m3");

    System.out.println("\n========================================");
    System.out.println("       GENERATED PVT REPORT");
    System.out.println("========================================\n");
    System.out.println(markdown);

    // Print additional PVT data not in the standard report
    System.out.println("\n## Additional PVT Data\n");
    System.out.println("### Gas Viscosity (LBC Model)\n");
    System.out.println("| Pressure (bara) | Viscosity (cP) |");
    System.out.println("|-----------------|----------------|");
    for (int i = 0; i < viscPressures.length; i++) {
      System.out
          .println(String.format("| %.1f | %.4f |", viscPressures[i], gasViscosity[i] * 1000));
    }

    System.out.println("\n### Gas Density\n");
    System.out.println("| Pressure (bara) | Density (kg/m³) |");
    System.out.println("|-----------------|-----------------|");
    for (int i = 0; i < viscPressures.length; i++) {
      System.out.println(String.format("| %.1f | %.2f |", viscPressures[i], gasDensity[i]));
    }

    System.out.println("\n### Separator Stage Oil Properties\n");
    System.out.println("| Stage | P (bara) | T (°C) | Oil Density (kg/m³) | Oil Viscosity (cP) |");
    System.out.println("|-------|----------|--------|---------------------|---------------------|");
    for (neqsim.pvtsimulation.simulation.MultiStageSeparatorTest.SeparatorStageResult stage : sepTest
        .getStageResults()) {
      System.out.println(String.format("| %s | %.1f | %.1f | %.2f | %.4f |", stage.getStageName(),
          stage.getPressure(), stage.getTemperature(), stage.getOilDensity(),
          stage.getOilViscosity()));
    }

    // Get oil viscosity from ViscositySim (if oil phase exists)
    double[] oilViscosity = viscSim.getOilViscosity();
    boolean hasOilViscosity = false;
    for (double v : oilViscosity) {
      if (v > 0) {
        hasOilViscosity = true;
        break;
      }
    }

    if (hasOilViscosity) {
      System.out.println("\n### Oil Viscosity vs Pressure\n");
      System.out.println("| Pressure (bara) | Viscosity (cP) |");
      System.out.println("|-----------------|----------------|");
      for (int i = 0; i < viscPressures.length; i++) {
        if (oilViscosity[i] > 0) {
          System.out
              .println(String.format("| %.1f | %.4f |", viscPressures[i], oilViscosity[i] * 1000));
        }
      }
    }

    // Get oil density at various pressures
    double[] oilDensity = new double[viscPressures.length];
    for (int i = 0; i < viscPressures.length; i++) {
      fluid.setPressure(viscPressures[i]);
      fluid.setTemperature(viscTemps[i]);
      ops.TPflash();
      fluid.initPhysicalProperties();
      if (fluid.hasPhaseType("oil")) {
        oilDensity[i] = fluid.getPhase("oil").getDensity("kg/m3");
      }
    }
    boolean hasOilDensity = false;
    for (double d : oilDensity) {
      if (d > 0) {
        hasOilDensity = true;
        break;
      }
    }

    if (hasOilDensity) {
      System.out.println("\n### Oil Density vs Pressure\n");
      System.out.println("| Pressure (bara) | Density (kg/m³) |");
      System.out.println("|-----------------|-----------------|");
      for (int i = 0; i < viscPressures.length; i++) {
        if (oilDensity[i] > 0) {
          System.out.println(String.format("| %.1f | %.2f |", viscPressures[i], oilDensity[i]));
        }
      }
    }

    System.out.println("\n### Gas Condensate Metrics\n");
    System.out.println("| Property | Value | Unit |");
    System.out.println("|----------|-------|------|");
    System.out.println(String.format("| Dew Point Pressure | %.2f | bara |", psat));
    System.out.println(String.format("| GOR | %.1f | Sm³/Sm³ |", totalGOR));
    System.out.println(String.format("| CGR | %.1f | bbl/MMscf |", CGR_bblperMMscf));
    System.out.println(String.format("| Bo | %.4f | m³/Sm³ |", Bo));
    System.out.println(
        String.format("| Stock Tank API | %.1f | °API |", sepTest.getStockTankAPIGravity()));
    System.out.println(
        String.format("| Stock Tank Density | %.1f | kg/m³ |", sepTest.getStockTankOilDensity()));
    System.out.println(String.format("| Molar Mass | %.2f | g/mol |", fluid.getMolarMass() * 1000));

    System.out.println("\n========================================\n");
  }
}
