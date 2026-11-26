package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Path;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.refrigeration.RefrigerationLoad;
import neqsim.process.equipment.refrigeration.RefrigerationSystem;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.lng.LNGProcessBuilder;
import neqsim.process.lng.LNGProcessCycle;
import neqsim.process.lng.LNGProcessModel;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.processmodel.ProcessSystem;

/** Exercises native XML, graphical P&amp;ID and transient cooling on the same equipment. */
class RefrigerationPlantProjectionTest {
  @TempDir
  Path directory;

  /** Qualifies all three passes and proves that a drawn controller can respond to actual loss of duty. */
  @Test
  void threeLevelC3mrPreservesPortsAndLiveTemperatureLoop() throws Exception {
    LNGProcessModel model = new LNGProcessBuilder().setName("Train").setCycle(LNGProcessCycle.C3MR)
        .setPropanePrecoolingLevels(3).setFeedFlowRate(20000.0).setNumberOfZones(12).build();
    ProcessSystem process = model.getProcessSystem();
    model.run();
    Cooler chiller = (Cooler) process.getUnit("Train C3 level 1 feed");
    StreamInterface inlet = chiller.getInletStream();
    double target = chiller.getOutletStream().getTemperature("C");
    chiller.configureDynamicTemperatureControl(inlet.getFlowRate("kg/hr"), inlet.getTemperature("C"), target,
        target - 3.0, "C");
    chiller.setDynamicTimeConstants(2.0, 10.0);
    chiller.setCalculateSteadyState(false);
    TemperatureTransmitter tt = new TemperatureTransmitter("TT-101", chiller.getOutletStream());
    tt.setUnit("C");
    tt.setMinimumValue(-50.0);
    tt.setMaximumValue(100.0);
    tt.setTransientSamplingEnabled(true);
    ControllerDeviceBaseClass tic = new ControllerDeviceBaseClass("TIC-101");
    tic.setTransmitter(tt);
    tic.setControllerSetPoint(target, "C");
    tic.setControllerParameters(4.0, 30.0, 0.0);
    tic.setOutputLimits(0.0, 100.0);
    chiller.setController(tic);
    process.add(tt);
    process.add(tic);

    Document nativeXml = nativePlant(process);
    assertEquals(3, count(nativeXml, "//Object[@type='Plant/ProcessEquipment.Compressor']"));
    assertEquals(3, count(nativeXml, "//Object[Data/String='Train main cryogenic exchanger']"
        + "/Components[@property='Nozzles']/Object[starts-with(@id,'Equipment') and contains(@id,'Inlet')]"));
    Path graphical = directory.resolve("train.proteus.xml");
    DexpiXmlWriter.writeForPyDexpi(process, graphical.toFile());
    Document drawing = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(graphical.toFile());
    assertEquals(6,
        count(drawing, "//Equipment[@ID='Train-main-cryogenic-exchanger' and @ComponentClass='HeatExchanger']/Nozzle"));
    assertEquals(4,
        count(drawing, "//Equipment[@ID='Train-C3-level-1-feed' and @ComponentClass='HeatExchanger']/Nozzle"));
    assertEquals(0, count(drawing, "//Equipment[@ID='Train-MR-recycle']"));
    assertTrue(DexpiXmlSvgRenderer.render(graphical.toFile()).contains("<svg"));
    DexpiVisualQualityAssessment.Report visual = DexpiVisualQualityAssessment.assess(graphical.toFile());
    assertFalse(visual.hasErrors(), visual.toJson());
    assertEquals(1, visual.getMetrics().get("closedControlLoops").intValue());
    assertSame(chiller, process.getUnit(chiller.getName()));

    ProcessSystem loop = new ProcessSystem("Isolated control test");
    loop.add(inlet);
    loop.add(chiller);
    loop.add(tt);
    loop.add(tic);
    inlet.setTemperature(35.0, "C");
    inlet.run();
    for (int i = 0; i < 180; i++) {
      loop.runTransient(1.0, UUID.randomUUID());
    }
    assertEquals(target, chiller.getOutletStream().getTemperature("C"), 0.5);
    assertTrue(chiller.getCoolingValveOpening() > 52.0);
    chiller.setDynamicCoolingUtilityAvailable(false);
    for (int i = 0; i < 60; i++) {
      loop.runTransient(1.0, UUID.randomUUID());
    }
    assertEquals(35.0, chiller.getOutletStream().getTemperature("C"), 0.2);
    assertTrue(chiller.getCoolingValveOpening() > 99.0);
    chiller.setCalculateSteadyState(true);
    chiller.run();
    assertEquals(target, chiller.getOutletStream().getTemperature("C"), 1.0e-6);
  }

  /** Direct expansion and a cascade share the physical condenser without introducing a feed or product. */
  @Test
  void cascadeSharesCondenserAndDirectExpansionOmitsFlashDrum() throws Exception {
    RefrigerationSystem cold = new RefrigerationSystem("Cold");
    cold.setRefrigerant("propane");
    cold.setEconomizerType(RefrigerationSystem.EconomizerType.NONE);
    cold.addLevel("LP", -30, "C").addFixedDuty("process", 100, "kW");
    RefrigerationSystem pump = new RefrigerationSystem("Heat pump");
    pump.setRefrigerant("ammonia");
    pump.addLevel("source", 5, "C").addLoad(RefrigerationLoad.cascadeCondenser(cold));
    ProcessSystem process = new ProcessSystem("Cascade");
    process.add(cold);
    process.add(pump);
    Document plant = nativePlant(process);
    assertEquals(1, count(plant, "//Object[@type='Plant/ProcessEquipment.Separator']"));
    assertEquals(2, count(plant, "//Object[@type='Plant/Piping.OperatedValve']"));
    assertEquals(4, count(plant, "//Object[Data/String='Cold condenser']/Components[@property='Nozzles']/Object"));
    assertEquals(0, count(plant, "//Object[contains(@type,'OffPageConnector')]"));
  }

  /** Writes and assesses the actual file against the pinned schema and reviewed native profile. */
  private Document nativePlant(ProcessSystem process) throws Exception {
    Path nativeFile = directory.resolve("native.xml");
    Dexpi20PlantExportMetadata metadata = Dexpi20PlantExportMetadata
        .builder("2026-11-26T00:00:00Z", "NeqSim", "Equinor", "test")
        .plantProperty(Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_NAME, process.getName()).build();
    Dexpi20ConformanceAssessment.Report report = Dexpi201XmlWriter.writeAndAssess(process, nativeFile.toFile(),
        metadata);
    assertTrue(report.isSchemaAndProfileConformant(), report.toJson());
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(nativeFile.toFile());
  }

  /** Counts objects in the serialized XML, including their owned connections. */
  private static int count(Document document, String expression) throws Exception {
    return ((Number) XPathFactory.newInstance().newXPath().evaluate("count(" + expression + ")", document,
        XPathConstants.NUMBER)).intValue();
  }
}
