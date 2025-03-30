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



  @Test
  void testReadBrd() throws IOException {
    testSystem = EclipseFluidReadWrite.read(file_brd);
    double[] molcomp = new double[] {0.000793504, 0.002185115, 0.970279547, 0.020714159,
        0.002100576, 0.002181042, 0.000447426, 0.000480092, 0.000119605, 0.000279524, 0.000226219,
        0.000154766, 3.65936E-05, 1.82958E-06, 1.10E-09, 8.06E-13, 2.62E-16, 1.08E-25};
    molcomp = new double[] {0.001139104, 0.002173947, 0.969756121, 0.02078901, 0.002092749,
        0.002280241, 0.000446227, 0.000499483, 0.000120059, 0.000288996, 0.000226389, 0.000151364,
        3.43368E-05, 1.97238E-06, 1.15E-09, 8.71E-13, 3.03E-16, 1.57E-25};

    testSystem.setMolarComposition(molcomp);

    Stream stream1 = new Stream("Stream1", testSystem);
    stream1.run();
    assertEquals(-3.84116452828, stream1.CCT("C"), 1e-3);
  }


  /**
   * Test method for
   * {@link neqsim.thermo.util.readwrite.EclipseFluidReadWrite#read(java.lang.String)}.
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
}
