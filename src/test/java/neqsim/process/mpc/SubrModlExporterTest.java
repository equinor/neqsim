package neqsim.process.mpc;

import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for SubrModlExporter.
 */
class SubrModlExporterTest {

  private ProcessSystem processSystem;
  private ProcessLinkedMPC mpc;

  @TempDir
  File tempDir;

  @BeforeEach
  void setUp() {
    // Create a simple process system
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    ThrottlingValve valve = new ThrottlingValve("valve", feed);
    valve.setOutletPressure(30.0);

    Separator separator = new Separator("separator", valve.getOutletStream());

    processSystem = new ProcessSystem();
    processSystem.add(feed);
    processSystem.add(valve);
    processSystem.add(separator);
    processSystem.run();

    // Create MPC
    mpc = new ProcessLinkedMPC("testController", processSystem);
    mpc.addMV("valve", "opening", 0.0, 1.0);
    mpc.addCV("separator", "pressure", 30.0);
    mpc.addDV("feed", "pressure");
  }

  @Test
  void testBasicExporterCreation() {
    SubrModlExporter exporter = new SubrModlExporter(processSystem);
    assertNotNull(exporter);
    exporter.setModelName("TestModel");
    exporter.setApplicationName("TestApp");
    assertEquals(1, exporter.getIndexTable().size()); // Only Nsecs initially
    assertEquals("Nsecs", exporter.getIndexTable().get(0));
  }

  @Test
  void testAddParameters() {
    SubrModlExporter exporter = new SubrModlExporter(processSystem);
    exporter.addParameter("Volume", 100.0, "m3");
    exporter.addParameter("Height", 2000.0, "m", "Well height");

    assertEquals(2, exporter.getParameters().size());
    assertEquals("Volume", exporter.getParameters().get(0).getName());
    assertEquals(100.0, exporter.getParameters().get(0).getValue());
    assertEquals("m3", exporter.getParameters().get(0).getUnit());
  }

  @Test
  void testAddSubrXvr() {
    SubrModlExporter exporter = new SubrModlExporter(processSystem);
    exporter.addSubrXvr("Pdownhole", "pdh", "Downhole pressure", 147.7);
    exporter.addSubrXvr("Pwellhead", "pwh", "Wellhead pressure", 10.4);

    assertEquals(2, exporter.getSubrXvrs().size());
    assertEquals("Pdownhole", exporter.getSubrXvrs().get(0).getName());
    assertEquals("pdh", exporter.getSubrXvrs().get(0).getDtaIx());
    assertEquals(147.7, exporter.getSubrXvrs().get(0).getInit());

    // Index table should include Nsecs + 2 xvrs
    assertEquals(3, exporter.getIndexTable().size());
    assertTrue(exporter.getIndexTable().contains("pdh"));
    assertTrue(exporter.getIndexTable().contains("pwh"));
  }

  @Test
  void testAddStateVariable() {
    SubrModlExporter exporter = new SubrModlExporter(processSystem);
    exporter.addStateVariable("FlowIn", "qin", "Inlet flow rate", 50.0);
    exporter.addStateVariable("FlowOut", "qout", "Outlet flow rate", 50.0);

    assertEquals(2, exporter.getStateVariables().size());
    assertEquals("FlowIn", exporter.getStateVariables().get(0).getName());
    assertEquals("qin", exporter.getStateVariables().get(0).getDtaIx());
  }

  @Test
  void testPopulateFromMPC() {
    SubrModlExporter exporter = new SubrModlExporter(processSystem);
    exporter.populateFromMPC(mpc);

    // Should have MV, CV, and DV as SubrXvrs
    assertEquals(3, exporter.getSubrXvrs().size());
  }

  @Test
  void testExportConfiguration() throws IOException {
    SubrModlExporter exporter = new SubrModlExporter(processSystem);
    exporter.setModelName("WellModel");
    exporter.setApplicationName("NonlinearSQP");
    exporter.addParameter("Volume", 100.0, "m3");
    exporter.addParameter("Height", 2000.0, "m");
    exporter.addSubrXvr("Pdownhole", "pdh", "Downhole pressure", 147.7);
    exporter.addSubrXvr("Pwellhead", "pwh", "Wellhead pressure", 10.4);

    File configFile = new File(tempDir, "well_config.txt");
    exporter.exportConfiguration(configFile.getAbsolutePath());

    assertTrue(configFile.exists());
    String content = new String(Files.readAllBytes(configFile.toPath()));
    assertTrue(content.contains("WellModel"));
    assertTrue(content.contains("Volume="));
    assertTrue(content.contains("Height="));
    assertTrue(content.contains("SubrXvr:"));
    assertTrue(content.contains("DtaIx=  \"pdh\""));
  }

  @Test
  void testExportMPCConfiguration() throws IOException {
    SubrModlExporter exporter = new SubrModlExporter(processSystem);
    exporter.setApplicationName("TestMPC");

    // Export nonlinear config
    File configFile = new File(tempDir, "mpc_config.txt");
    exporter.exportMPCConfiguration(configFile.getAbsolutePath(), true);

    assertTrue(configFile.exists());
    String content = new String(Files.readAllBytes(configFile.toPath()));
    assertTrue(content.contains("SmpcAppl:"));
    assertTrue(content.contains("SteadySolver=  SQP"));
    assertTrue(content.contains("IterOpt=  ON"));
    assertTrue(content.contains("MajItLim="));
  }

  @Test
  void testExportLinearMPCConfiguration() throws IOException {
    SubrModlExporter exporter = new SubrModlExporter(processSystem);
    exporter.setApplicationName("TestMPC");

    // Export linear config
    File configFile = new File(tempDir, "mpc_linear.txt");
    exporter.exportMPCConfiguration(configFile.getAbsolutePath(), false);

    assertTrue(configFile.exists());
    String content = new String(Files.readAllBytes(configFile.toPath()));
    assertTrue(content.contains("SteadySolver=  QP"));
    assertTrue(content.contains("IterOpt=  OFF"));
  }

  @Test
  void testExportIndexTable() throws IOException {
    SubrModlExporter exporter = new SubrModlExporter(processSystem);
    exporter.setModelName("WellModel");
    exporter.addSubrXvr("Pdownhole", "pdh", "Downhole pressure", 147.7);
    exporter.addSubrXvr("Pwellhead", "pwh", "Wellhead pressure", 10.4);
    exporter.addIndexEntry("qin");
    exporter.addIndexEntry("qout");

    File indexFile = new File(tempDir, "well_ixid.cpp");
    exporter.exportIndexTable(indexFile.getAbsolutePath());

    assertTrue(indexFile.exists());
    String content = new String(Files.readAllBytes(indexFile.toPath()));
    assertTrue(content.contains("char* WellModel::IxId[]"));
    assertTrue(content.contains("\"Nsecs\""));
    assertTrue(content.contains("\"pdh\""));
    assertTrue(content.contains("\"pwh\""));
  }

  @Test
  void testExportJSON() throws IOException {
    SubrModlExporter exporter = new SubrModlExporter(processSystem);
    exporter.setModelName("WellModel");
    exporter.setSampleTime(1.0);
    exporter.addParameter("Volume", 100.0, "m3");
    exporter.addSubrXvr("Pdownhole", "pdh", "Downhole pressure", 147.7);
    exporter.addStateVariable("FlowIn", "qin", "Inlet flow rate", 50.0);

    File jsonFile = new File(tempDir, "model.json");
    exporter.exportJSON(jsonFile.getAbsolutePath());

    assertTrue(jsonFile.exists());
    String content = new String(Files.readAllBytes(jsonFile.toPath()));
    assertTrue(content.contains("\"modelName\": \"WellModel\""));
    assertTrue(content.contains("\"sampleTime\": 1.0"));
    assertTrue(content.contains("\"parameters\""));
    assertTrue(content.contains("\"subrXvrs\""));
    assertTrue(content.contains("\"stateVariables\""));
  }

  @Test
  void testCreateFromMPC() {
    mpc.addSVR("separator", "liquidLevel", "lvl");

    SubrModlExporter exporter = mpc.createSubrModlExporter();
    assertNotNull(exporter);

    // Should have MVs, CVs, DVs from MPC
    assertEquals(3, exporter.getSubrXvrs().size());

    // Should have SVR
    assertEquals(1, exporter.getStateVariables().size());
  }
}
