package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.stream.EnergyStream;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

class CompressorTest extends neqsim.NeqSimTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CompressorTest.class);

  static neqsim.thermo.system.SystemInterface testSystem = null;

  double pressure_inlet = 85.0;
  double temperature_inlet = 35.0;
  double gasFlowRate = 5.0;
  double pressure_Out = 150.0;
  double polytropicEff = 0.77;
  ProcessSystem processOps = null;
  neqsim.process.equipment.compressor.Compressor compressor1 = null;

  String unitName = "Compressor1";

  /**
   * <p>
   * setUp.
   * </p>
   *
   * @throws java.lang.Exception if any.
   */
  @BeforeEach
  public void setUp() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    compressor1 = new Compressor(unitName, inletStream);
    compressor1.setOutletPressure(pressure_Out);
    processOps.add(inletStream);
    processOps.add(compressor1);
  }

  /**
   * @throws IOException
   * @throws ClassNotFoundException
   */
  @Test
  public void testSerialize() throws IOException, ClassNotFoundException {
    Compressor cmp = (Compressor) processOps.getUnit(unitName);

    String tmp = "test_compressor_saved.ser";

    try (
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(tmp))) {
      objectOutputStream.writeObject(cmp);
      objectOutputStream.flush();
      objectOutputStream.close();

      ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(tmp));
      Compressor cmp2 = (Compressor) objectInputStream.readObject();
      objectInputStream.close();

      Assertions.assertTrue(cmp.getName().compareTo(cmp2.getName()) == 0);

      Assertions.assertTrue(cmp.equals(cmp2));
    } catch (Exception ex) {
      logger.warn("Exceptions happen!", ex);
    } finally {
      File file = new File(tmp);
      file.delete();
    }
  }

  /**
   * <p>
   * testCompressorSchultzMethod.
   * </p>
   */
  @Test
  public void testCompressorSchultzMethod() {
    compressor1.setPolytropicEfficiency(polytropicEff);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setPolytropicMethod("schultz");
    processOps.run();
    // logger.info("schultz compressor power " + compressor1.getPower() / 1e6
    // + " MW");
    assertEquals(compressor1.getPower() / 1e6, 4.668373797540108, 0.01);
    // // "Test case for compressor Schultz method polytropic calculation should return
    // approximate
    // 4.67 MW");
  }

  /**
   * <p>
   * testCompressorRigorousMethod.
   * </p>
   */
  @Test
  public void testCompressorRigorousMethod() {
    compressor1.setPolytropicEfficiency(polytropicEff);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setPolytropicMethod("detailed");
    processOps.run();
    // logger.info("rigorous compressor power " + compressor1.getPower() /
    // 1e6 + " MW");
    assertEquals(compressor1.getPower() / 1e6, 4.655081035416562, 0.01);
    // "Test case for rigorous polytropic compressor calculation should return approximate 4.66
    // MW");
  }

  /**
   * <p>
   * testIsentropicCalcMethod.
   * </p>
   */
  @Test
  public void testIsentropicCalcMethod() {
    compressor1.setIsentropicEfficiency(polytropicEff);
    compressor1.setUsePolytropicCalc(false);
    processOps.run();
    // logger.info("compressor power " + compressor1.getPower() / 1e6 + "
    // MW");
    assertEquals(compressor1.getPower() / 1e6, 4.5621157449685, 0.01);
  }

  /**
   * <p>
   * testCompressorWithSrk.
   * </p>
   */
  @Test
  public void testCompressorWithSrk() {
    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    inletStream.run();
    neqsim.process.equipment.compressor.Compressor compressor1 =
        new neqsim.process.equipment.compressor.Compressor("Compressor1", inletStream);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setOutletPressure(pressure_Out);
    compressor1.setOutTemperature(358.0);
    compressor1.run();
    // logger.info("srk polytropic efficiency " +
    // compressor1.getPolytropicEfficiency()*100 + " poly eff");
    // logger.info("srk fluid head " + compressor1.getPolytropicFluidHead() +
    // " kJ/kg");
    // logger.info("srk power " + compressor1.getPower() + " W");
    assertEquals(compressor1.getPolytropicEfficiency() * 100, 88.94871563458828, 0.1);
    // "Test case for rigorous polytropic efficiency with SRK calculation should return approximate
    // 88.948715 ");
  }

  /**
   * <p>
   * testCompressorWithGERG2008.
   * </p>
   */
  @Test
  public void testCompressorWithGERG2008() {
    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    inletStream.run();
    neqsim.process.equipment.compressor.Compressor compressor1 =
        new neqsim.process.equipment.compressor.Compressor("Compressor1", inletStream);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setOutletPressure(pressure_Out);
    compressor1.setOutTemperature(358.0);
    compressor1.setUseGERG2008(true);
    // compressor1.setUseRigorousPolytropicMethod(true);
    compressor1.run();
    // logger.info("gerg polytropic efficiency " +
    // compressor1.getPolytropicEfficiency()*100 + " poly eff");
    // logger.info("gerg fluid head " + compressor1.getPolytropicFluidHead()
    // + " kJ/kg");
    // logger.info("gerg power " + compressor1.getPower() + " W");
    assertEquals(compressor1.getPolytropicEfficiency() * 100, 89.99367027631443, 0.01);
    // "Test case for rigorous polytropic efficiency with GER2008 calculation should return
    // approximate 89.992296751");
  }

  /**
   * <p>
   * testCompressorWithGERG2008-2.
   * </p>
   */
  @Test
  public void testCompressorWithGERG2008_2() {
    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    inletStream.run();
    neqsim.process.equipment.compressor.Compressor compressor1 =
        new neqsim.process.equipment.compressor.Compressor("Compressor1", inletStream);
    compressor1.setOutletPressure(pressure_Out);
    compressor1.setPolytropicEfficiency(0.56);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setUseGERG2008(true);
    compressor1.run();
    // double head = compressor1.getPolytropicHead("kJ/kg");
    // logger.info("gerg power " + compressor1.getPower() + " W");
    // assertEquals(compressor1.getPolytropicHead("kJ/kg"), 89.464626, 0.01);
    compressor1.setUseGERG2008(false);
    compressor1.run();
    // double head2 = compressor1.getPolytropicHead("kJ/kg");
    // logger.info("gerg power " + compressor1.getPower() + " W");
    assertEquals(compressor1.getPolytropicHead("kJ/kg"), 94.32923841459161, 0.01);
  }

  /**
   * <p>
   * test Multi Phase Compression.
   * </p>
   */
  @Test
  public void testMultiPhaseCompression() {
    SystemSrkEos testSystem = new SystemSrkEos(315.0, 10.0);
    testSystem.addComponent("nitrogen", 2.0);
    testSystem.addComponent("methane", 50.0);
    testSystem.addComponent("nC10", 100.0);
    testSystem.addComponent("water", 100.0);
    testSystem.setMixingRule(2);
    testSystem.setMultiPhaseCheck(true);

    processOps = new ProcessSystem();
    Stream inletStream = new Stream("feed stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    compressor1 = new Compressor(unitName, inletStream);
    compressor1.setOutletPressure(pressure_Out);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setPolytropicEfficiency(0.5);
    processOps.add(inletStream);
    processOps.add(compressor1);
    processOps.run();
    assertEquals(compressor1.getPower(), 3712607.597542703, 1110.01);
  }

  /**
   * <p>
   * test Multi Phase Compression.
   * </p>
   */
  @Test
  public void testPowerEffSpec() {
    SystemSrkEos testSystem = new SystemSrkEos(315.0, 10.0);
    testSystem.addComponent("nitrogen", 2.0);
    testSystem.addComponent("methane", 50.0);
    testSystem.setMixingRule(2);

    processOps = new ProcessSystem();
    Stream inletStream = new Stream("feed stream", testSystem);
    inletStream.setPressure(10, "bara");
    inletStream.setTemperature(30, "C");
    inletStream.setFlowRate(1, "MSm3/day");
    compressor1 = new Compressor(unitName, inletStream);
    compressor1.setPower(2000000);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setPolytropicEfficiency(0.8);
    compressor1.setCalcPressureOut(true);
    processOps.add(inletStream);
    processOps.add(compressor1);
    processOps.run();
    assertEquals(30.610386, compressor1.getOutletPressure(), 0.01);
    // compressor1.getOutletStream().getFluid().prettyPrint();

    processOps = new ProcessSystem();
    compressor1 = new Compressor(unitName, inletStream);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setPolytropicEfficiency(0.8);
    compressor1.setOutletPressure(30.61);
    processOps.add(inletStream);
    processOps.add(compressor1);
    processOps.run();
    assertEquals(139.7216108, compressor1.getOutletStream().getTemperature("C"), 0.01);
    // compressor1.getOutletStream().getFluid().prettyPrint();
  }

  /**
   * <p>
   * test run with energy stream input.
   * </p>
   */
  @Test
  public void testRunWithEnergyStreamInput() {
    SystemSrkEos testSystem = new SystemSrkEos(315.0, 10.0);
    testSystem.addComponent("nitrogen", 2.0);
    testSystem.addComponent("methane", 50.0);
    testSystem.setMixingRule(2);

    Stream inletStream = new Stream("feed stream", testSystem);
    inletStream.setPressure(100, "bara");
    inletStream.setTemperature(30, "C");
    inletStream.setFlowRate(1, "MSm3/day");
    inletStream.run();

    EnergyStream estream1 = new EnergyStream("e_stream");

    Expander expander1 = new Expander("expander 1", inletStream);
    expander1.setPolytropicEfficiency(0.8);
    expander1.setUsePolytropicCalc(true);
    // expander1.setIsentropicEfficiency(1.0);
    expander1.setOutletPressure(55.0, "bara");
    expander1.setEnergyStream(estream1);
    expander1.run();

    assertEquals(481376.2230301, estream1.getDuty(), 0.01);

    compressor1 = new Compressor("compressor 1", expander1.getOutletStream());
    compressor1.setUsePolytropicCalc(true);
    compressor1.setPolytropicEfficiency(0.8);
    // compressor1.setIsentropicEfficiency(1.0);
    compressor1.setEnergyStream(estream1);
    compressor1.setCalcPressureOut(true);
    compressor1.run();

    assertEquals(481376.2230301, compressor1.getPower(), 0.01);

    processOps = new ProcessSystem();
    processOps.add(inletStream);
    processOps.add(expander1);
    processOps.add(compressor1);
    processOps.run();

    assertEquals(81.61462472, compressor1.getOutletPressure(), 0.01);
  }

  /**
   * <p>
   * testCompressorWithLeachman.
   * </p>
   */
  @Test
  public void testCompressorWithLeachman() {
    // Initialize the test system with hydrogen
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("hydrogen", 100.0); // Add hydrogen component

    // Create and configure the inlet stream
    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    inletStream.run();

    // Create and configure the compressor
    neqsim.process.equipment.compressor.Compressor compressor1 =
        new neqsim.process.equipment.compressor.Compressor("Compressor1", inletStream);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setOutletPressure(pressure_Out);
    compressor1.setOutTemperature(400.0);
    compressor1.setUseLeachman(true); // Enable the use of Leachman method

    // Run the compressor
    compressor1.run();

    // Assertions to verify the expected outcomes
    assertEquals(compressor1.getPolytropicEfficiency() * 100, 63.29499883086769, 0.01);
    // assertEquals(compressor1.getPower("MW"), 6.820449828263508, 0.0001);
  }

  /**
   * <p>
   * testCompressorWithLeachman.
   * </p>
   */
  @Test
  public void testCompressorWithLeachman_2() {
    // Initialize the test system with hydrogen
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("hydrogen", 100.0); // Add hydrogen component

    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    inletStream.run();
    neqsim.process.equipment.compressor.Compressor compressor1 =
        new neqsim.process.equipment.compressor.Compressor("Compressor1", inletStream);
    compressor1.setOutletPressure(pressure_Out);
    compressor1.setPolytropicEfficiency(0.56);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setUseLeachman(true);
    compressor1.run();
    assertEquals(compressor1.getPolytropicHead("kJ/kg"), 888.7512012528256, 0.01);
    compressor1.setUseLeachman(false);
    compressor1.setUseGERG2008(true);
    compressor1.run();
    assertEquals(compressor1.getPolytropicHead("kJ/kg"), 888.7059204667879, 0.01);
  }


  /**
   * <p>
   * testCompressorWithVega.
   * </p>
   */
  @Test
  public void testCompressorWithVega() {
    // Initialize the test system with helium
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("helium", 100.0); // Add helium component

    // Create and configure the inlet stream
    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    inletStream.run();

    // Create and configure the compressor
    neqsim.process.equipment.compressor.Compressor compressor1 =
        new neqsim.process.equipment.compressor.Compressor("Compressor1", inletStream);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setOutletPressure(pressure_Out);
    compressor1.setOutTemperature(400.0);
    compressor1.setUseVega(true); // Enable the use of Vega method

    // Run the compressor
    compressor1.run();

    // Assertions to verify the expected outcomes
    assertEquals(compressor1.getPolytropicEfficiency() * 100, 87.18264587172833, 0.01);
    // assertEquals(compressor1.getPower("MW"), 6.820449828263508, 0.0001);
  }

  /**
   * <p>
   * testCompressorWithVega_2.
   * </p>
   */
  @Test
  public void testCompressorWithVega_2() {
    // Initialize the test system with helium
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("helium", 100.0); // Add helium component

    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    inletStream.run();
    neqsim.process.equipment.compressor.Compressor compressor1 =
        new neqsim.process.equipment.compressor.Compressor("Compressor1", inletStream);
    compressor1.setOutletPressure(pressure_Out);
    compressor1.setPolytropicEfficiency(0.56);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setUseVega(true);
    compressor1.run();
    assertEquals(compressor1.getPolytropicHead("kJ/kg"), 466.97515072687054, 0.01);
    compressor1.setUseVega(false);
    compressor1.setUseGERG2008(true);
    compressor1.run();
    assertEquals(compressor1.getPolytropicHead("kJ/kg"), 466.82945407210633, 0.01);
  }
}
