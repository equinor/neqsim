package neqsim.process.safety.api14c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for the API RP 14C SAFE chart classes.
 *
 * @author ESOL
 * @version 1.0
 */
class Api14cSafeChartBuilderTest {

  @Test
  void pressureVesselRequiresStandardSet() {
    EnumSet<Api14cDeviceType> req = EnumSet
	.copyOf(Api14cSafetyAnalysisTable.getRequiredDevices(Api14cEquipmentCategory.PRESSURE_VESSEL));
    assertTrue(req.contains(Api14cDeviceType.PSH));
    assertTrue(req.contains(Api14cDeviceType.PSL));
    assertTrue(req.contains(Api14cDeviceType.LSH));
    assertTrue(req.contains(Api14cDeviceType.LSL));
    assertTrue(req.contains(Api14cDeviceType.PSV));
  }

  @Test
  void chartItemDetectsMissingDevices() {
    EnumSet<Api14cDeviceType> required = EnumSet.of(Api14cDeviceType.PSH, Api14cDeviceType.PSV);
    EnumSet<Api14cDeviceType> present = EnumSet.of(Api14cDeviceType.PSH);
    Api14cSafeChartItem it = new Api14cSafeChartItem("V-100", Api14cEquipmentCategory.PRESSURE_VESSEL, required,
	present);
    assertFalse(it.isComplete());
    assertEquals(1, it.getMissing().size());
    assertTrue(it.getMissing().contains(Api14cDeviceType.PSV));
  }

  @Test
  void completeChartItem() {
    EnumSet<Api14cDeviceType> set = EnumSet.of(Api14cDeviceType.PSH, Api14cDeviceType.PSV);
    Api14cSafeChartItem it = new Api14cSafeChartItem("V-100", Api14cEquipmentCategory.PRESSURE_VESSEL, set, set);
    assertTrue(it.isComplete());
  }

  @Test
  void buildAssumingCompleteEnumeratesEquipment() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator sep = new Separator("HP separator", feed);
    ProcessSystem p = new ProcessSystem();
    p.add(feed);
    p.add(sep);
    p.run();

    Api14cSafeChartBuilder b = new Api14cSafeChartBuilder().buildAssumingComplete(p);
    assertTrue(b.isComplete());
    assertTrue(b.getItems().size() >= 1);
    boolean found = false;
    for (Api14cSafeChartItem it : b.getItems()) {
      if ("HP separator".equals(it.getEquipmentName())) {
	assertEquals(Api14cEquipmentCategory.PRESSURE_VESSEL, it.getCategory());
	found = true;
      }
    }
    assertTrue(found, "HP separator should be present in SAFE chart");
  }

  @Test
  void buildDetectsMissingDevicesOnRealSeparator() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator sep = new Separator("HP separator", feed);
    ProcessSystem p = new ProcessSystem();
    p.add(feed);
    p.add(sep);
    p.run();

    Api14cSafeChartBuilder b = new Api14cSafeChartBuilder()
	.declarePresent("HP separator", EnumSet.of(Api14cDeviceType.PSH, Api14cDeviceType.PSV)).build(p);
    assertFalse(b.isComplete());
    assertFalse(b.getGaps().isEmpty());
  }

  @Test
  void markdownAndJsonExport() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator sep = new Separator("HP separator", feed);
    ProcessSystem p = new ProcessSystem();
    p.add(feed);
    p.add(sep);
    p.run();

    Api14cSafeChartBuilder b = new Api14cSafeChartBuilder().buildAssumingComplete(p);
    String md = b.toMarkdown();
    assertTrue(md.contains("HP separator"));
    String json = b.toJson();
    assertTrue(json.contains("HP separator"));
  }
}
