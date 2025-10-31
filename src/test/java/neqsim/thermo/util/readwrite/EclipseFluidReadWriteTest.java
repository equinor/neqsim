package neqsim.thermo.util.readwrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.AntiSurge;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChart;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.system.SystemInterface;
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
  void testGOW34() throws IOException {
    testSystem = EclipseFluidReadWrite.read(gow);
    testSystem.setMultiPhaseCheck(true);


    double[] moleFractions = {0.00540071, 0.0127938, 0.724541, 0.0972789, 0.0746364, 0.0124884,
        0.0296084, 0.00701328, 0.00872348, 0.00703798, 0.00641995, 0.00266744, 0.00123526,
        0.00015286, 4.30823E-08, 7.49295E-16, 0.0100024};


    testSystem.setMolarComposition(moleFractions);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(4., "bara");
    testSystem.setTemperature(20.0, "C");
    testOps.TPflash();

    Assertions.assertEquals(3, testSystem.getNumberOfPhases());
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

    Assertions.assertTrue(gasFraction > 0.1);
  }

  @Test
  void testGOW4() throws IOException {
    testSystem = EclipseFluidReadWrite.read(gow);
    testSystem.setMultiPhaseCheck(true);

    double[] molcompLowWater = new double[] {4.9107930618050546e-05, 0.0012546826725008057,
        0.026343316936642148, 0.015782513912485484, 0.021507966038800383, 0.005719038644454519,
        0.014456587791398226, 0.010316784218293597, 0.01494526854482507, 0.02174974260520435,
        0.046008119779292554, 0.055040972592013085, 0.02538455516611449, 0.0906953549264313,
        0.057955349835511455, 0.02339737645058884, 0.5693932619548259};

    testSystem = EclipseFluidReadWrite.read(gow);
    testSystem.setMultiPhaseCheck(true);
    testSystem.setMolarComposition(molcompLowWater);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(5.1, "bara");
    testSystem.setTemperature(52.519057394367, "C");
    testOps.TPflash();
    Assertions.assertEquals(3, testSystem.getNumberOfPhases());

    double gasFraction = testSystem.getPhase("gas").getPhaseFraction();
    System.out.println("gasFraction=" + gasFraction);
    for (int phaseIdx = 0; phaseIdx < testSystem.getNumberOfPhases(); phaseIdx++) {
      System.out.println("phase" + phaseIdx + " type=" + testSystem.getPhase(phaseIdx).getType()
          + " beta=" + testSystem.getPhase(phaseIdx).getBeta());
    }
    // testSystem.display();
    for (int phaseIdx = 0; phaseIdx < testSystem.getNumberOfPhases(); phaseIdx++) {
      System.out.println("phase" + phaseIdx + " type=" + testSystem.getPhase(phaseIdx).getType()
          + " methane x=" + testSystem.getPhase(phaseIdx).getComponent("methane").getx() + " Z="
          + testSystem.getPhase(phaseIdx).getZ() + " rho="
          + testSystem.getPhase(phaseIdx).getPhysicalProperties().getDensity());
    }

    Assertions.assertTrue(gasFraction > 0.00001);
  }

  @Test
  void antiSurgeTest() throws IOException {

    double SECOND_STAGE_PRESSURE_BARA = 7.0;
    double THIRD_STAGE_PRESSURE_BARA = 20.0;
    double SECOND_STAGE_COOLER_OUTLET_TEMP_C = 28.0;
    double COMPRESSOR_SPEED_RPM = 10250.0;
    double POLYTROPIC_EFFICIENCY = 0.80;
    String HEAD_UNIT = "kJ/kg";
    double[] CHART_REFERENCE = new double[] {17.3, 298.15, 1.01325, 0.92};
    double[] SURGE_FLOW_KGH = new double[] {5607.45, 6007.91, 6480.26, 7111.75, 7799.81, 8179.81,
        8508.5, 8749.97, 9006.93, 9248.64, 9397.9, 9578.11, 9758.49};
    double[] SURGE_HEAD_KJKG = new double[] {150.0, 149.54, 148.83, 148.05, 146.14, 144.76, 142.98,
        140.73, 137.29, 132.13, 127.56, 121.13, 112.65};
    double SURGE_CONTROL_FACTOR = 0.15;
    double LP_GAS_SPLIT_FACTOR = 0.25;

    testSystem = EclipseFluidReadWrite.read(gow);
    testSystem.setMultiPhaseCheck(true);

    double[] molcompLowWater = new double[] {4.9107930618050546e-05, 0.0012546826725008057,
        0.26343316936642148, 0.015782513912485484, 0.021507966038800383, 0.005719038644454519,
        0.00014456587791398226, 0.00010316784218293597, 0.001494526854482507, 0.0002174974260520435,
        0.00046008119779292554, 0.00055040972592013085, 0.0002538455516611449, 0.000906953549264313,
        0.000057955349835511455, 0.0002339737645058884, 0.00005693932619548259};

    testSystem.setMolarComposition(molcompLowWater);
    SystemInterface suctionGas = testSystem;

    ProcessSystem separationProcess = new ProcessSystem("2nd stage recompressor anti surge");

    Stream firstStageDischarge = new Stream("first stage discharge", suctionGas.clone());
    firstStageDischarge.setPressure(SECOND_STAGE_PRESSURE_BARA, "bara");
    firstStageDischarge.setTemperature(SECOND_STAGE_COOLER_OUTLET_TEMP_C, "C");
    firstStageDischarge.setFlowRate(1800.0, "kg/hr");
    separationProcess.add(firstStageDischarge);
    firstStageDischarge.run();

    System.out.println("flow inlet " + firstStageDischarge.getFluid().getFlowRate("m3/hr"));

    Stream lpGasFeed = new Stream("LP flash gas", suctionGas.clone());
    lpGasFeed.setPressure(SECOND_STAGE_PRESSURE_BARA, "bara");
    lpGasFeed.setTemperature(30.0, "C");
    lpGasFeed.setFlowRate(firstStageDischarge.getFlowRate("kg/hr") * LP_GAS_SPLIT_FACTOR, "kg/hr");
    separationProcess.add(lpGasFeed);
    lpGasFeed.run();

    Stream recycleGasStream2 = new Stream("recycle 2nd stage", suctionGas.clone());
    recycleGasStream2.setPressure(SECOND_STAGE_PRESSURE_BARA, "bara");
    recycleGasStream2.setTemperature(25.0, "C");
    recycleGasStream2.setFlowRate(1.0, "kg/hr");
    separationProcess.add(recycleGasStream2);
    recycleGasStream2.run();

    Mixer firstStageGasMixer = new Mixer("first stage mixer");
    firstStageGasMixer.addStream(firstStageDischarge);
    firstStageGasMixer.addStream(lpGasFeed);
    firstStageGasMixer.addStream(recycleGasStream2);
    separationProcess.add(firstStageGasMixer);
    firstStageGasMixer.run();

    Cooler firstStageCooler2 =
        new Cooler("1st stage cooler2", firstStageGasMixer.getOutletStream());
    firstStageCooler2.setOutTemperature(SECOND_STAGE_COOLER_OUTLET_TEMP_C, "C");
    separationProcess.add(firstStageCooler2);
    firstStageCooler2.run();

    Separator firstStageScrubber2 =
        new Separator("1st stage scrubber2", firstStageCooler2.getOutletStream());
    separationProcess.add(firstStageScrubber2);
    firstStageScrubber2.run();

    System.out.println("flow inlet feed compressor start"
        + firstStageScrubber2.getGasOutStream().getFluid().getFlowRate("m3/hr"));

    Compressor secondStageCompressor =
        new Compressor("2nd stage compressor", firstStageScrubber2.getGasOutStream());
    secondStageCompressor.setCompressorChartType("interpolate and extrapolate");
    secondStageCompressor.setUsePolytropicCalc(true);
    secondStageCompressor.setPolytropicEfficiency(POLYTROPIC_EFFICIENCY);
    secondStageCompressor.setOutletPressure(THIRD_STAGE_PRESSURE_BARA, "bara");
    secondStageCompressor.setSpeed(COMPRESSOR_SPEED_RPM);

    CompressorChart chart = (CompressorChart) secondStageCompressor.getCompressorChart();
    chart.setHeadUnit(HEAD_UNIT);
    chart.setReferenceConditions(CHART_REFERENCE[0], CHART_REFERENCE[1], CHART_REFERENCE[2],
        CHART_REFERENCE[3]);
    chart.getSurgeCurve().setCurve(CHART_REFERENCE, SURGE_FLOW_KGH, SURGE_HEAD_KJKG);
    secondStageCompressor.run();
    separationProcess.add(secondStageCompressor);

    Splitter antiSurgeSplitter =
        new Splitter("2nd stage anti surge splitter", secondStageCompressor.getOutletStream(), 2);
    antiSurgeSplitter.setFlowRates(new double[] {-1.0, 1.0}, "kg/hr");
    separationProcess.add(antiSurgeSplitter);
    antiSurgeSplitter.run();

    Calculator antiSurgeCalculator2 = new Calculator("anti surge calculator 2");
    antiSurgeCalculator2.addInputVariable(secondStageCompressor);
    antiSurgeCalculator2.setOutputVariable(antiSurgeSplitter);
    separationProcess.add(antiSurgeCalculator2);
    antiSurgeCalculator2.run();

    ThrottlingValve antiSurgeValve =
        new ThrottlingValve("anti surge valve 2", antiSurgeSplitter.getSplitStream(1));
    antiSurgeValve.setOutletPressure(SECOND_STAGE_PRESSURE_BARA, "bara");
    separationProcess.add(antiSurgeValve);
    antiSurgeValve.run();

    Recycle recycle2 = new Recycle("recycle anti surge 2nd stage compressor");
    recycle2.addStream(antiSurgeValve.getOutletStream());
    recycle2.setOutletStream(recycleGasStream2);
    recycle2.setTolerance(1e-6);
    separationProcess.add(recycle2);
    recycle2.run();

    separationProcess.run();
    // separationProcess.run();
    // separationProcess.run();

    // assertFalse(secondStageCompressor.isSurge(), "compressor is surge");
    // assertFalse(secondStageCompressor.isStoneWall(), "compressor is stone wall limited");

    System.out
        .println("compressor poytropic head end " + secondStageCompressor.getPolytropicFluidHead());

    System.out.println("flow inlet feed compressor end "
        + firstStageScrubber2.getGasOutStream().getFluid().getFlowRate("m3/hr"));

    System.out.println(recycle2.toJson());

  }


}

