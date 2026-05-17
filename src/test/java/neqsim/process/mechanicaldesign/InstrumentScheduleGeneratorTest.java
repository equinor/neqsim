package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.InstrumentScheduleGenerator.InstrumentEntry;
import neqsim.process.mechanicaldesign.InstrumentScheduleGenerator.MeasuredVariable;
import neqsim.process.mechanicaldesign.InstrumentScheduleGenerator.SilRating;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link InstrumentScheduleGenerator} and its integration with
 * {@link EngineeringDeliverablesPackage} and the process simulation instrument framework.
 */
class InstrumentScheduleGeneratorTest extends neqsim.NeqSimTest {

  private static ProcessSystem buildSeparatorProcess() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 60.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-heptane", 0.15);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");

    Separator sep = new Separator("HP Separator", feed);

    ThrottlingValve gasValve = new ThrottlingValve("Gas CV", sep.getGasOutStream());
    gasValve.setOutletPressure(20.0);

    Compressor comp = new Compressor("Export Compressor", gasValve.getOutletStream());
    comp.setOutletPressure(100.0);

    Cooler cooler = new Cooler("After Cooler", comp.getOutletStream());
    cooler.setOutTemperature(273.15 + 30.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.add(gasValve);
    process.add(comp);
    process.add(cooler);
    process.run();

    return process;
  }

  @Nested
  class GeneratorBasicTests {

    @Test
    void testNullProcessSystemThrows() {
      assertThrows(IllegalArgumentException.class, () -> new InstrumentScheduleGenerator(null));
    }

    @Test
    void testNotGeneratedInitially() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      assertFalse(gen.isGenerated());
      assertEquals(0, gen.getInstrumentCount());
    }

    @Test
    void testGenerateCreatesInstruments() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      assertTrue(gen.isGenerated());
      assertTrue(gen.getInstrumentCount() > 0, "Should create instruments for equipment");
    }

    @Test
    void testISATagNumbering() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      // Check that PT tags start at 100, TT at 200, etc.
      boolean hasPT = false;
      boolean hasTT = false;
      boolean hasLT = false;
      boolean hasFT = false;

      for (InstrumentEntry entry : gen.getEntries()) {
        String tag = entry.getTagNumber();
        if (tag.startsWith("PT-1")) {
          hasPT = true;
        }
        if (tag.startsWith("TT-2")) {
          hasTT = true;
        }
        if (tag.startsWith("LT-3")) {
          hasLT = true;
        }
        if (tag.startsWith("FT-4")) {
          hasFT = true;
        }
      }

      assertTrue(hasPT, "Should have PT-1xx pressure tag");
      assertTrue(hasTT, "Should have TT-2xx temperature tag");
      assertTrue(hasLT, "Should have LT-3xx level tag");
      assertTrue(hasFT, "Should have FT-4xx flow tag");
    }
  }

  @Nested
  class SeparatorInstrumentationTests {

    @Test
    void testSeparatorHasAllExpectedInstruments() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      List<InstrumentEntry> sepEntries = gen.getEntriesForEquipment("HP Separator");
      // Expect PT, TT, LT, FT = 4 instruments
      assertTrue(sepEntries.size() >= 4,
          "Separator should have at least 4 instruments, got: " + sepEntries.size());

      boolean hasPT = false;
      boolean hasTT = false;
      boolean hasLT = false;
      boolean hasFT = false;
      for (InstrumentEntry e : sepEntries) {
        if (e.getMeasuredVariable() == MeasuredVariable.PRESSURE) {
          hasPT = true;
        }
        if (e.getMeasuredVariable() == MeasuredVariable.TEMPERATURE) {
          hasTT = true;
        }
        if (e.getMeasuredVariable() == MeasuredVariable.LEVEL) {
          hasLT = true;
        }
        if (e.getMeasuredVariable() == MeasuredVariable.FLOW) {
          hasFT = true;
        }
      }
      assertTrue(hasPT, "Separator should have pressure transmitter");
      assertTrue(hasTT, "Separator should have temperature transmitter");
      assertTrue(hasLT, "Separator should have level transmitter");
      assertTrue(hasFT, "Separator should have flow transmitter");
    }

    @Test
    void testSeparatorPressureAlarms() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      List<InstrumentEntry> sepEntries = gen.getEntriesForEquipment("HP Separator");
      InstrumentEntry pt = null;
      for (InstrumentEntry e : sepEntries) {
        if (e.getMeasuredVariable() == MeasuredVariable.PRESSURE) {
          pt = e;
          break;
        }
      }
      assertNotNull(pt, "Should have pressure entry");
      assertTrue(pt.getAlarmLow() < pt.getNormalValue(), "Low alarm should be below normal");
      assertTrue(pt.getAlarmHigh() > pt.getNormalValue(), "High alarm should be above normal");
      assertTrue(pt.getTripLow() < pt.getAlarmLow(), "Trip low should be below alarm low");
      assertTrue(pt.getTripHigh() > pt.getAlarmHigh(), "Trip high should be above alarm high");
    }

    @Test
    void testSeparatorLevelSILRating() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      List<InstrumentEntry> sepEntries = gen.getEntriesForEquipment("HP Separator");
      InstrumentEntry lt = null;
      for (InstrumentEntry e : sepEntries) {
        if (e.getMeasuredVariable() == MeasuredVariable.LEVEL) {
          lt = e;
          break;
        }
      }
      assertNotNull(lt, "Should have level entry");
      assertEquals(SilRating.SIL_2, lt.getSilRating(), "Separator level should be SIL 2");
    }
  }

  @Nested
  class CompressorInstrumentationTests {

    @Test
    void testCompressorHasSuctionAndDischarge() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      List<InstrumentEntry> compEntries = gen.getEntriesForEquipment("Export Compressor");
      assertTrue(compEntries.size() >= 3,
          "Compressor should have at least 3 instruments (suction PT, discharge PT, discharge TT)");

      int ptCount = 0;
      int ttCount = 0;
      for (InstrumentEntry e : compEntries) {
        if (e.getMeasuredVariable() == MeasuredVariable.PRESSURE) {
          ptCount++;
        }
        if (e.getMeasuredVariable() == MeasuredVariable.TEMPERATURE) {
          ttCount++;
        }
      }
      assertEquals(2, ptCount, "Compressor should have 2 pressure transmitters");
      assertEquals(1, ttCount, "Compressor should have 1 temperature transmitter");
    }
  }

  @Nested
  class LiveDeviceTests {

    @Test
    void testDevicesCreatedWithTags() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      for (InstrumentEntry entry : gen.getEntries()) {
        MeasurementDeviceInterface device = entry.getDevice();
        assertNotNull(device, "Entry " + entry.getTagNumber() + " should have a live device");
        assertEquals(entry.getTagNumber(), device.getTag(),
            "Device tag should match entry tag number");
      }
    }

    @Test
    void testDevicesRetrievableByTag() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      // Get the first PT tag
      String firstPTTag = null;
      for (InstrumentEntry e : gen.getEntries()) {
        if (e.getMeasuredVariable() == MeasuredVariable.PRESSURE) {
          firstPTTag = e.getTagNumber();
          break;
        }
      }
      assertNotNull(firstPTTag, "Should have at least one PT");
      MeasurementDeviceInterface device = gen.getDevice(firstPTTag);
      assertNotNull(device, "Should retrieve device by tag");
    }

    @Test
    void testDevicesHaveAlarmConfig() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      // Check separator pressure device has alarm config
      List<InstrumentEntry> sepEntries = gen.getEntriesForEquipment("HP Separator");
      for (InstrumentEntry e : sepEntries) {
        if (e.getMeasuredVariable() == MeasuredVariable.PRESSURE) {
          MeasurementDeviceInterface device = e.getDevice();
          assertNotNull(device.getAlarmConfig(), "Pressure device should have alarm config");
          assertNotNull(device.getAlarmConfig().getHighLimit(), "Should have high limit set");
          assertNotNull(device.getAlarmConfig().getHighHighLimit(),
              "Should have high-high limit set");
          assertNotNull(device.getAlarmConfig().getLowLimit(), "Should have low limit set");
          break;
        }
      }
    }

    @Test
    void testRegisterOnProcessAddsDevices() {
      ProcessSystem process = buildSeparatorProcess();
      int beforeCount = process.getMeasurementDevices().size();

      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.setRegisterOnProcess(true);
      assertTrue(gen.isRegisterOnProcess());
      gen.generate();

      int afterCount = process.getMeasurementDevices().size();
      assertTrue(afterCount > beforeCount, "Registering on process should add devices: before="
          + beforeCount + " after=" + afterCount);

      // Devices should be findable by tag on the process
      String firstTag = gen.getEntries().get(0).getTagNumber();
      MeasurementDeviceInterface found = process.getMeasurementDeviceByTag(firstTag);
      assertNotNull(found, "Device " + firstTag + " should be findable on ProcessSystem by tag");
    }

    @Test
    void testNotRegisterDoesNotAddDevices() {
      ProcessSystem process = buildSeparatorProcess();
      int beforeCount = process.getMeasurementDevices().size();

      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.setRegisterOnProcess(false);
      gen.generate();

      int afterCount = process.getMeasurementDevices().size();
      assertEquals(beforeCount, afterCount, "Not registering should not add devices to process");
    }
  }

  @Nested
  class FilterAndQueryTests {

    @Test
    void testFilterByType() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      List<InstrumentEntry> pressureEntries = gen.getEntriesByType(MeasuredVariable.PRESSURE);
      assertTrue(pressureEntries.size() > 0, "Should have pressure entries");
      for (InstrumentEntry e : pressureEntries) {
        assertEquals(MeasuredVariable.PRESSURE, e.getMeasuredVariable());
      }

      int ptCount = gen.getCountByType(MeasuredVariable.PRESSURE);
      assertEquals(pressureEntries.size(), ptCount);
    }

    @Test
    void testFilterByEquipment() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      List<InstrumentEntry> feedEntries = gen.getEntriesForEquipment("Feed");
      assertTrue(feedEntries.size() >= 3, "Feed stream should have PT, TT, FT");
    }

    @Test
    void testGetCreatedDevicesList() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      List<MeasurementDeviceInterface> devices = gen.getCreatedDevices();
      assertEquals(gen.getInstrumentCount(), devices.size(),
          "Created devices should match instrument count");
    }
  }

  @Nested
  class JsonOutputTests {

    @Test
    void testJsonContainsSchedule() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      String json = gen.toJson();
      assertNotNull(json);
      assertTrue(json.contains("instrumentSchedule"), "JSON should have instrumentSchedule");
      assertTrue(json.contains("totalInstruments"), "JSON should have totalInstruments");
      assertTrue(json.contains("summary"), "JSON should have summary");
      assertTrue(json.contains("PT-"), "JSON should contain PT tags");
      assertTrue(json.contains("TT-"), "JSON should contain TT tags");
      assertTrue(json.contains("silRating"), "JSON should have SIL ratings");
      assertTrue(json.contains("ioCount"), "JSON should have I/O count");
    }

    @Test
    void testJsonIOCounts() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      String json = gen.toJson();
      // All standard transmitters are AI type
      assertTrue(json.contains("\"AI\""), "Should report AI count");
    }
  }

  @Nested
  class EnumTests {

    @Test
    void testMeasuredVariableProperties() {
      assertEquals("PT", MeasuredVariable.PRESSURE.getTagPrefix());
      assertEquals("Pressure Transmitter", MeasuredVariable.PRESSURE.getInstrumentType());
      assertEquals("AI", MeasuredVariable.PRESSURE.getIoType());

      assertEquals("TT", MeasuredVariable.TEMPERATURE.getTagPrefix());
      assertEquals("LT", MeasuredVariable.LEVEL.getTagPrefix());
      assertEquals("FT", MeasuredVariable.FLOW.getTagPrefix());
    }

    @Test
    void testSilRatingDisplayName() {
      assertEquals("None", SilRating.NONE.getDisplayName());
      assertEquals("SIL 1", SilRating.SIL_1.getDisplayName());
      assertEquals("SIL 2", SilRating.SIL_2.getDisplayName());
      assertEquals("SIL 3", SilRating.SIL_3.getDisplayName());
    }
  }

  @Nested
  class DeliverableIntegrationTests {

    @Test
    void testInstrumentScheduleInStudyClassA() {
      assertTrue(StudyClass.CLASS_A.requires(StudyClass.DeliverableType.INSTRUMENT_SCHEDULE),
          "Class A should require instrument schedule");
    }

    @Test
    void testInstrumentScheduleInStudyClassB() {
      assertTrue(StudyClass.CLASS_B.requires(StudyClass.DeliverableType.INSTRUMENT_SCHEDULE),
          "Class B should require instrument schedule");
    }

    @Test
    void testInstrumentScheduleNotInStudyClassC() {
      assertFalse(StudyClass.CLASS_C.requires(StudyClass.DeliverableType.INSTRUMENT_SCHEDULE),
          "Class C should not require instrument schedule");
    }

    @Test
    void testPackageGeneratesInstrumentSchedule() {
      ProcessSystem process = buildSeparatorProcess();
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
      pkg.generate();

      assertNotNull(pkg.getInstrumentSchedule(),
          "Class A package should generate instrument schedule");
      assertTrue(pkg.getInstrumentSchedule().isGenerated());
      assertTrue(pkg.getInstrumentSchedule().getInstrumentCount() > 0);
    }

    @Test
    void testPackageJsonIncludesInstrumentSchedule() {
      ProcessSystem process = buildSeparatorProcess();
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
      pkg.generate();

      String json = pkg.toJson();
      assertTrue(json.contains("instrumentSchedule"),
          "Package JSON should include instrument schedule section");
      assertTrue(json.contains("PT-"),
          "Package JSON should contain PT tags from instrument schedule");
    }

    @Test
    void testPackageClassBIncludesInstrumentSchedule() {
      ProcessSystem process = buildSeparatorProcess();
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_B);
      pkg.generate();

      assertNotNull(pkg.getInstrumentSchedule(),
          "Class B should also generate instrument schedule");
    }

    @Test
    void testPackageClassCNoInstrumentSchedule() {
      ProcessSystem process = buildSeparatorProcess();
      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_C);
      pkg.generate();

      assertTrue(pkg.getInstrumentSchedule() == null,
          "Class C should not generate instrument schedule");
    }

    @Test
    void testInstrumentsRegisteredOnProcessViaPackage() {
      ProcessSystem process = buildSeparatorProcess();
      int beforeCount = process.getMeasurementDevices().size();

      EngineeringDeliverablesPackage pkg =
          new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
      pkg.generate();

      int afterCount = process.getMeasurementDevices().size();
      assertTrue(afterCount > beforeCount,
          "Package generation should register instruments on process");
    }
  }

  @Nested
  class ThreePhaseSeparatorTests {

    @Test
    void testThreePhaseSepHasWaterLevel() {
      SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 60.0);
      fluid.addComponent("methane", 0.70);
      fluid.addComponent("n-heptane", 0.20);
      fluid.addComponent("water", 0.10);
      fluid.setMixingRule("classic");
      fluid.setMultiPhaseCheck(true);

      Stream feed = new Stream("Feed-3ph", fluid);
      feed.setFlowRate(5000.0, "kg/hr");

      ThreePhaseSeparator sep3 = new ThreePhaseSeparator("3-Phase Sep", feed);

      ProcessSystem process = new ProcessSystem();
      process.add(feed);
      process.add(sep3);
      process.run();

      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      List<InstrumentEntry> sepEntries = gen.getEntriesForEquipment("3-Phase Sep");
      int levelCount = 0;
      for (InstrumentEntry e : sepEntries) {
        if (e.getMeasuredVariable() == MeasuredVariable.LEVEL) {
          levelCount++;
        }
      }
      assertTrue(levelCount >= 2,
          "Three-phase separator should have at least 2 level transmitters (oil + water)");
    }
  }

  @Nested
  class HeaterCoolerTests {

    @Test
    void testCoolerHasTemperatureInstrument() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      List<InstrumentEntry> coolerEntries = gen.getEntriesForEquipment("After Cooler");
      assertTrue(coolerEntries.size() >= 1, "Cooler should have at least 1 instrument");
      assertEquals(MeasuredVariable.TEMPERATURE, coolerEntries.get(0).getMeasuredVariable(),
          "Cooler instrument should be temperature");
    }
  }

  @Nested
  class ValveTests {

    @Test
    void testValveHasDownstreamPressure() {
      ProcessSystem process = buildSeparatorProcess();
      InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
      gen.generate();

      List<InstrumentEntry> valveEntries = gen.getEntriesForEquipment("Gas CV");
      assertTrue(valveEntries.size() >= 1, "Valve should have at least 1 instrument");
      assertEquals(MeasuredVariable.PRESSURE, valveEntries.get(0).getMeasuredVariable(),
          "Valve instrument should be pressure");
    }
  }
}
