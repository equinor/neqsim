package neqsim.thermo.util.readwrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * @author ESOL
 */
class EclipseFluidReadWriteTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;

  File file = new File("src/test/java/neqsim/thermo/util/readwrite");
  String fileFluid1 = file.getAbsolutePath() + "/fluid1.e300";
  String fileA1 = file.getAbsolutePath() + "/A-1.E300";
  String fileA13 = file.getAbsolutePath() + "/A-13.E300";
  String fileA17 = file.getAbsolutePath() + "/A-17.E300";
  String fileA19 = file.getAbsolutePath() + "/A-19.E300";
  String filer = file.getAbsolutePath() + "/fluid-r.E300";
  String fluid_water = file.getAbsolutePath() + "/fluid_water.E300";
  String file_brd = file.getAbsolutePath() + "/Brd.e300";
  String delete = file.getAbsolutePath() + "/deleteme.e300";
  String example = file.getAbsolutePath() + "/example.e300";
  String gow = file.getAbsolutePath() + "/gas_oil_water.e300";

  /**
   * 
   * @Test void testReadBrd() throws IOException { testSystem =
   *       EclipseFluidReadWrite.read(file_brd); double[] molcomp = new double[] {0.000793504,
   *       0.002185115, 0.970279547, 0.020714159, 0.002100576, 0.002181042, 0.000447426,
   *       0.000480092, 0.000119605, 0.000279524, 0.000226219, 0.000154766, 3.65936E-05,
   *       1.82958E-06, 1.10E-09, 8.06E-13, 2.62E-16, 1.08E-25}; molcomp = new double[]
   *       {0.001139104, 0.002173947, 0.969756121, 0.02078901, 0.002092749, 0.002280241,
   *       0.000446227, 0.000499483, 0.000120059, 0.000288996, 0.000226389, 0.000151364,
   *       3.43368E-05, 1.97238E-06, 1.15E-09, 8.71E-13, 3.03E-16, 1.57E-25};
   * 
   *       testSystem.setMolarComposition(molcomp);
   * 
   *       Stream stream1 = new Stream("Stream1", testSystem); stream1.run(); assertEquals(-4.0,
   *       stream1.CCT("C"), 0.2); }
   * 
   *       /** Test method for
   *       {@link neqsim.thermo.util.readwrite.EclipseFluidReadWrite#read(java.lang.String)}.
   *
   * @throws IOException
   */
  @Test
  void testRead() throws IOException {
    EclipseFluidReadWrite.pseudoName = "_A13";
    testSystem = EclipseFluidReadWrite.read(fileA13);
  }

  @Test
  void testReadAndAddFluids() throws IOException {
    EclipseFluidReadWrite.pseudoName = "_A19";
    testSystem = EclipseFluidReadWrite.read(fileA19);

    EclipseFluidReadWrite.pseudoName = "_A17";
    neqsim.thermo.system.SystemInterface testSystem2 = EclipseFluidReadWrite.read(fileA17);
    testSystem.addFluid(testSystem2);

    EclipseFluidReadWrite.pseudoName = "_A13";
    neqsim.thermo.system.SystemInterface testSystem3 = EclipseFluidReadWrite.read(fileA13);
    testSystem.addFluid(testSystem3);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  @Test
  void testReadAndAddFluids2() throws IOException {
    EclipseFluidReadWrite.pseudoName = "_A1";
    testSystem = EclipseFluidReadWrite.read(fileA1);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(100.0, "bara");
    testSystem.setTemperature(25.0, "C");
    testOps.TPflash();

    // neqsim.thermo.util.readwrite.TablePrinter.printTable((((PhaseEos
    // )testSystem.getPhase(0)).getMixingRule().getBinaryInteractionParameters()));
  }

  @Test
  void testSetComposition() throws IOException {
    EclipseFluidReadWrite.pseudoName = "_A19";
    testSystem = EclipseFluidReadWrite.read(fileA19);

    EclipseFluidReadWrite.pseudoName = "_A17";
    neqsim.thermo.system.SystemInterface testSystem2 = EclipseFluidReadWrite.read(fileA17);
    testSystem.addFluid(testSystem2);

    EclipseFluidReadWrite.pseudoName = "_A13";
    neqsim.thermo.system.SystemInterface testSystem3 = EclipseFluidReadWrite.read(fileA13);
    testSystem.addFluid(testSystem3);

    EclipseFluidReadWrite.pseudoName = "_A13";
    EclipseFluidReadWrite.setComposition(testSystem, fileA13);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  @Test
  void testSetAllocationComposition() throws IOException {
    String[] fluids = new String[] {"A19", "A13"};
    testSystem = EclipseFluidReadWrite.read(fileA19, fluids);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();

    double x1 = testSystem.getPhase(1).getComponent("methane_A19").getx();

    fluids = new String[] {"A19"};
    testSystem = EclipseFluidReadWrite.read(fileA19, fluids);
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();

    double kij2 =
        ((PhaseEos) testSystem.getPhase(0)).getMixingRule().getBinaryInteractionParameter(0, 1);
    double x2 = testSystem.getPhase(1).getComponent("methane_A19").getx();

    assertEquals(-0.0170, kij2, 1e-9);
    assertEquals(x2, x1 * 2.0, 1e-5);

    double[] molcomp = testSystem.getMolarComposition();

    fluids = new String[] {"A19", "A13", "A20"};
    testSystem = EclipseFluidReadWrite.read(fileA19, fluids);

    testSystem.setMolarCompositionOfNamedComponents("A19", molcomp);
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();

    double x_methane_A13 = testSystem.getPhase(1).getComponent("methane_A13").getx();

    double x_methane_A19 = testSystem.getPhase(1).getComponent("methane_A19").getx();
    assertEquals(0.0, x_methane_A13, 1e-9);
    assertEquals(0.0046202437, x_methane_A19, 1e-9);

    // double[][] interactionParams =
    // ((PhaseEos)
    // testSystem.getPhase(0)).getMixingRule().getBinaryInteractionParameters();

    // System.out.println(Arrays.deepToString(interactionParams));
  }

  @Test
  void testReadFluid1() throws IOException {
    testSystem = EclipseFluidReadWrite.read(fileFluid1);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(50.0, "bara");

    // neqsim.thermo.util.readwrite.TablePrinter.printTable(
    // (((PhaseEos)
    // testSystem.getPhase(0)).getMixingRule().getBinaryInteractionParameters()));

    // for (int i = 0; i < testSystem.getNumberOfComponents(); i++) {
    // System.out.println(testSystem.getComponent(i).getName() + " TC "
    // + (testSystem.getComponent(i).getVolumeCorrectionConst()));
    // }
  }

  @Test
  void testReadFlui() throws IOException {
    testSystem = EclipseFluidReadWrite.read(delete);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);
    testSystem.setPressure(30.97, "bara");
    testSystem.setTemperature(15.0, "C");
    testSystem.setTotalFlowRate(10.0, "kg/hr");
    testOps.TPflash();
    Assertions.assertEquals(0.9780559630, testSystem.getBeta(0), 1e-6);
    // testSystem.prettyPrint();
    // String fileName = "OLGAneqsim.tab";
    // testOps.OLGApropTable(273.15 + 20, 273.15 + 100.0, 20, 1.0, 100.0, 20, fileName, 1);
  }

  @Test
  void testReadFluidR() throws IOException {
    testSystem = EclipseFluidReadWrite.read(filer);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(530.97, "bara");
    testSystem.setTemperature(105.0, "C");
    testOps.TPflash();
    // testSystem.prettyPrint();
    // Assertions.assertEquals(0.9270363530255, testSystem.getBeta(0), 1e-6);

    testSystem = EclipseFluidReadWrite.read(filer);
    testSystem.setMultiPhaseCheck(true);
    testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(520.0, "bara");
    testSystem.setTemperature(105.0, "C");
    testOps.TPflash();
    // testSystem.prettyPrint();
    // Assertions.assertEquals(0.9270363530, testSystem.getBeta(0), 1e-6);
  }

  @Test
  void testFluidWater() throws IOException {
    testSystem = EclipseFluidReadWrite.read(fluid_water);
    testSystem.setMultiPhaseCheck(true);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(60.0, "bara");
    testSystem.setTemperature(65.0, "C");
    testOps.TPflash();

    // testSystem.prettyPrint();

    // neqsim.thermo.util.readwrite.TablePrinter.printTable(
    // (((PhaseEos) testSystem.getPhase(0)).getMixingRule().getBinaryInteractionParameters()));
    double[][] paramsPhase0 =
        ((PhaseEos) testSystem.getPhase(0)).getMixingRule().getBinaryInteractionParameters();
    double[][] paramsPhase1 =
        ((PhaseEos) testSystem.getPhase(2)).getMixingRule().getBinaryInteractionParameters();

    // Check that both 2D arrays have the same dimensions first.
    Assertions.assertEquals(paramsPhase0.length, paramsPhase1.length);

    // CHeck that kij are equal of phase gas and water
    for (int i = 0; i < paramsPhase0.length; i++) {
      for (int j = 0; j < paramsPhase0.length; j++) {
        Assertions.assertEquals(paramsPhase0[i][j], paramsPhase1[i][j]);
      }
    }
  }

  @Test
  void testFluidWater2() throws IOException {
    testSystem = EclipseFluidReadWrite.read(fluid_water);
    testSystem.setMultiPhaseCheck(true);

    // testSystem.init(0);

    double molcomp[] = new double[] {.005823483446243756, 0.011911670685498591, 0.7510528953701038,
        0.06432302170169239, 0.027440959875481938, 0.003176445516132957, 0.0068822986182880755,
        0.0015882227580664785, 0.002029395746418279, 0.0018529265510775586, 0.0021176303440886383,
        0.001235284367385039, 0.001235284367385039, 0.0008823459767035993, 0.00044117298835179964,
        0.00017646919534071987, 0.00405500399809711783049249174136};

    testSystem.setMolarComposition(molcomp);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(60.0, "bara");
    testSystem.setTemperature(65.0, "C");
    testOps.TPflash();

    Assertions.assertEquals(3, testSystem.getNumberOfPhases());
  }

  @Test
  void testFluidWater3() throws IOException {
    testSystem = EclipseFluidReadWrite.read(fluid_water);
    testSystem.setMultiPhaseCheck(true);

    // testSystem.init(0);

    double molcomp[] = new double[] {0.0017264947889263874, 0.007402331403631907,
        0.36802882183084096, 0.05169506737490504, 0.03388449522583478, 0.006558258623105914,
        0.014887151553907825, 0.009077822719104057, 0.012438044055263933, 0.019147621313098974,
        0.030786253313698466, 0.031557200635259686, 0.021582189815237608, 0.06076824533807442,
        0.04616741968199379, 0.020877439498187718, 0.26341514282892897};

    testSystem.setMolarComposition(molcomp);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(20.2, "bara");
    testSystem.setTemperature(331.25521590092563, "K");
    testOps.TPflash();

    Stream stream1 = new Stream("Stream1", testSystem);
    stream1.setFlowRate(100.0, "kg/hr");
    stream1.setTemperature(331.25521590092563, "K");
    stream1.setPressure(20.2, "bara");
    stream1.run();

    ThreePhaseSeparator separator = new ThreePhaseSeparator("threePhaseSeparator", stream1);
    separator.setEntrainment(0.01, "product", "volume", "aqueous", "oil");
    separator.run();

    double ent = separator.getOilOutStream().getFluid().getEnthalpy();
    separator.getOilOutStream().run();
    // separator.getOilOutStream().getFluid().prettyPrint();
    Assertions.assertEquals(-4680.63031, ent, 1e-3);

    Assertions.assertEquals(2, separator.getOilOutStream().getFluid().getNumberOfPhases());

    ThrottlingValve throttlingValve =
        new ThrottlingValve("throttlingValve", separator.getOilOutStream());
    throttlingValve.setOutletPressure(3.0, "bara");
    throttlingValve.run();

    // throttlingValve.getOutletStream().getFluid().prettyPrint();
    Assertions.assertEquals(3, throttlingValve.getOutletStream().getFluid().getNumberOfPhases());
    Assertions.assertEquals(54.5362976,
        throttlingValve.getOutletStream().getFluid().getTemperature("C"), 1e-3);
  }


  @Test
  void testSetAllocationComposition2() throws IOException {
    String[] fluids = new String[] {"A", "B"};
    testSystem = EclipseFluidReadWrite.read(example, fluids);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }


  @Test
  void testGOW() throws IOException {
    testSystem = EclipseFluidReadWrite.read(gow);
    testSystem.setMultiPhaseCheck(true);

    double molcompHighWater[] = new double[] {0.006412157523628081, 0.010284556325774607,
        0.5948031004752822, 0.05547161269670469, 0.02244981369291901, 0.002427305705540153,
        0.0051926405125144355, 0.001019338910149834, 0.0013514578158311205, 0.0015380316247187251,
        0.002930764400688029, 0.0027918580247969812, 0.0009663155244097777, 0.0037156646050503844,
        0.0024900059190517826, 0.0008201394784577446, 0.32500853352367648164};

    testSystem.setMolarComposition(molcompHighWater);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(65.2, "bara");
    testSystem.setTemperature(60, "C");
    testOps.TPflash();

    Assertions.assertEquals(3, testSystem.getNumberOfPhases());
    testSystem.prettyPrint();
  }

  @Test
  void testGOW3() throws IOException {
    double molcompLowWater[] = new double[] {0.006412157523628081, 0.010284556325774607,
        0.5948031004752822, 0.05547161269670469, 0.02244981369291901, 0.002427305705540153,
        0.0051926405125144355, 0.001019338910149834, 0.0013514578158311205, 0.0015380316247187251,
        0.002930764400688029, 0.0027918580247969812, 0.0009663155244097777, 0.0037156646050503844,
        0.0024900059190517826, 0.0008201394784577446, 0.0002500853352367648164};

    testSystem = EclipseFluidReadWrite.read(gow);
    testSystem.setMultiPhaseCheck(true);
    testSystem.setMolarComposition(molcompLowWater);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(65.2, "bara");
    testSystem.setTemperature(60, "C");
    testOps.TPflash();

    Assertions.assertEquals(2, testSystem.getNumberOfPhases());

  }


  @Test
  void testGOW2() throws IOException {
    testSystem = EclipseFluidReadWrite.read(gow);
    testSystem.setMultiPhaseCheck(true);

    double[] molcompLowWater = new double[] {0.01206177683974074, 0.027345937050178615,
        0.671140783011007, 0.06351261548338824, 0.023585809555430968, 0.003243942277664318,
        0.00531903453294321, 0.0029453595855580728, 0.003041059592933105, 0.0021510810982620153,
        0.0031940205365572558, 0.00323662007843437, 0.0016271200382793763, 0.0029353835821057748,
        0.0010067063083738046, 5.49033694408053e-05, 0.17359784705970216};

    testSystem = EclipseFluidReadWrite.read(gow);
    testSystem.setMultiPhaseCheck(true);
    testSystem.setMolarComposition(molcompLowWater);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(29.11, "bara");
    testSystem.setTemperature(27.44261416, "C");
    testOps.TPflash();

    Assertions.assertEquals(3, testSystem.getNumberOfPhases());

    double gasFraction = testSystem.getPhase("gas").getPhaseFraction();
    System.out.println("gasFraction=" + gasFraction);
    for (int phaseIdx = 0; phaseIdx < testSystem.getNumberOfPhases(); phaseIdx++) {
      System.out.println("phase" + phaseIdx + " type=" + testSystem.getPhase(phaseIdx).getType()
          + " beta=" + testSystem.getPhase(phaseIdx).getBeta());
    }
    testSystem.display();
    for (int phaseIdx = 0; phaseIdx < testSystem.getNumberOfPhases(); phaseIdx++) {
      System.out.println("phase" + phaseIdx + " type=" + testSystem.getPhase(phaseIdx).getType()
          + " methane x=" + testSystem.getPhase(phaseIdx).getComponent("methane").getx() + " Z="
          + testSystem.getPhase(phaseIdx).getZ() + " rho="
          + testSystem.getPhase(phaseIdx).getPhysicalProperties().getDensity());
    }

    Assertions.assertTrue(gasFraction > 0.1);
  }

}
