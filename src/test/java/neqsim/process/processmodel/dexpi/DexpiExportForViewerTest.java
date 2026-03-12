package neqsim.process.processmodel.dexpi;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Generates DEXPI XML files for testing in external DEXPI viewers. Run this test then open the
 * generated files from target/dexpi-viewer/.
 *
 * @author esol
 * @version 1.0
 */
public class DexpiExportForViewerTest extends NeqSimTest {

  /**
   * Generates a gas processing flowsheet with separator, compressor, cooler, and valve. Writes to
   * target/dexpi-viewer/gas_processing.xml.
   *
   * @throws Exception if export fails
   */
  @Test
  public void generateGasProcessingDexpi() throws Exception {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("nC5", 0.04);
    fluid.addComponent("nC10", 0.03);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed-001", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setPressure(50.0, "bara");
    feed.setTemperature(30.0, "C");

    ThreePhaseSeparator inletSep = new ThreePhaseSeparator("V-100 Inlet Separator", feed);

    Compressor gasComp = new Compressor("K-101 Gas Compressor", inletSep.getGasOutStream());
    gasComp.setOutletPressure(120.0);

    Cooler aftercooler = new Cooler("E-101 Aftercooler", gasComp.getOutletStream());
    aftercooler.setOutTemperature(273.15 + 40.0);

    Separator scrubber = new Separator("V-102 Scrubber", aftercooler.getOutletStream());

    ThrottlingValve liqValve =
        new ThrottlingValve("XV-103 Liquid Valve", inletSep.getLiquidOutStream());
    liqValve.setOutletPressure(10.0);

    Heater oilHeater = new Heater("E-102 Oil Heater", liqValve.getOutletStream());
    oilHeater.setOutTemperature(273.15 + 60.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(inletSep);
    process.add(gasComp);
    process.add(aftercooler);
    process.add(scrubber);
    process.add(liqValve);
    process.add(oilHeater);
    process.run();

    File outFile = new File("target/dexpi-viewer/gas_processing.xml");
    DexpiXmlWriter.write(process, outFile);
    System.out.println("DEXPI XML written to: " + outFile.getAbsolutePath());
  }

  /**
   * Generates a simple 2-stage compression flowsheet. Writes to
   * target/dexpi-viewer/two_stage_compression.xml.
   *
   * @throws Exception if export fails
   */
  @Test
  public void generateTwoStageCompressionDexpi() throws Exception {
    SystemInterface gas = new SystemSrkEos(273.15 + 25.0, 5.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.06);
    gas.addComponent("propane", 0.03);
    gas.addComponent("CO2", 0.01);
    gas.setMixingRule("classic");

    Stream feed = new Stream("Feed Gas", gas);
    feed.setFlowRate(100000.0, "kg/hr");

    Compressor stage1 = new Compressor("K-201 1st Stage", feed);
    stage1.setOutletPressure(20.0);

    Cooler ic1 = new Cooler("E-201 Intercooler", stage1.getOutletStream());
    ic1.setOutTemperature(273.15 + 35.0);

    Separator knockOut1 = new Separator("V-201 KO Drum", ic1.getOutletStream());

    Compressor stage2 = new Compressor("K-202 2nd Stage", knockOut1.getGasOutStream());
    stage2.setOutletPressure(80.0);

    Cooler ac = new Cooler("E-202 Aftercooler", stage2.getOutletStream());
    ac.setOutTemperature(273.15 + 40.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(stage1);
    process.add(ic1);
    process.add(knockOut1);
    process.add(stage2);
    process.add(ac);
    process.run();

    File outFile = new File("target/dexpi-viewer/two_stage_compression.xml");
    DexpiXmlWriter.write(process, outFile);
    System.out.println("DEXPI XML written to: " + outFile.getAbsolutePath());
  }

  /**
   * Copies the official DEXPI example to target/ for easy access. Writes to
   * target/dexpi-viewer/official_dexpi_example.xml.
   *
   * @throws Exception if copy fails
   */
  @Test
  public void copyOfficialDexpiExample() throws Exception {
    File source = new File("src/test/resources/dexpi/C01V04-VER.EX01.xml");
    File dest = new File("target/dexpi-viewer/official_dexpi_example.xml");
    dest.getParentFile().mkdirs();
    java.nio.file.Files.copy(source.toPath(), dest.toPath(),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    System.out.println("Official DEXPI example copied to: " + dest.getAbsolutePath());
  }

  /**
   * Generates a gas processing flowsheet with instruments (PT, TT) on key streams. Writes to
   * target/dexpi-viewer/instruments_test.xml.
   *
   * @throws Exception if export fails
   */
  @Test
  public void generateDexpiWithInstruments() throws Exception {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("nC5", 0.03);
    fluid.addComponent("nC10", 0.02);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed-001", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setPressure(50.0, "bara");
    feed.setTemperature(30.0, "C");

    Separator inletSep = new Separator("V-100 Inlet Separator", feed);

    Compressor compressor = new Compressor("K-101 Compressor", inletSep.getGasOutStream());
    compressor.setOutletPressure(120.0);

    Cooler aftercooler = new Cooler("E-101 Aftercooler", compressor.getOutletStream());
    aftercooler.setOutTemperature(273.15 + 40.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(inletSep);
    process.add(compressor);
    process.add(aftercooler);
    process.run();

    // Create instrument transmitters on equipment outlet streams
    Map<String, MeasurementDeviceInterface> transmitters = new LinkedHashMap<>();

    PressureTransmitter ptSep = new PressureTransmitter("PT-1001", inletSep.getGasOutStream());
    transmitters.put("PT-1001", ptSep);

    TemperatureTransmitter ttSep =
        new TemperatureTransmitter("TT-1002", inletSep.getGasOutStream());
    transmitters.put("TT-1002", ttSep);

    PressureTransmitter ptComp = new PressureTransmitter("PT-1003", compressor.getOutletStream());
    transmitters.put("PT-1003", ptComp);

    TemperatureTransmitter ttCooler =
        new TemperatureTransmitter("TT-1004", aftercooler.getOutletStream());
    transmitters.put("TT-1004", ttCooler);

    File outFile = new File("target/dexpi-viewer/instruments_test.xml");
    DexpiXmlWriter.write(process, outFile, transmitters, null);
    System.out.println("DEXPI XML with instruments written to: " + outFile.getAbsolutePath());
  }
}
