package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.instrumentdesign.InstrumentDesign;
import neqsim.process.instrumentdesign.InstrumentSpecification;
import neqsim.process.instrumentdesign.system.SystemInstrumentDesign;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Integer boundary oracles for the instrument-sizing guide and class Javadoc.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class InstrumentSizingDocumentationTest {
  /** Verify zero inventory preserves the documented minimum cabinets. */
  @Test
  void emptyInventoryRetainsDcsAndMarshallingMinimums() {
    assertSizing(0, 0, 0, 1, 0, 0, 1);
  }

  /** Verify DCS card and cabinet boundaries without safety channels. */
  @Test
  void dcsSizingRoundsUpAtCardAndCabinetBoundaries() {
    assertSizing(15, 0, 1, 1, 0, 0, 1);
    assertSizing(16, 0, 1, 1, 0, 0, 1);
    assertSizing(17, 0, 2, 1, 0, 0, 1);
    assertSizing(255, 0, 16, 1, 0, 0, 2);
    assertSizing(256, 0, 16, 1, 0, 0, 2);
    assertSizing(257, 0, 17, 2, 0, 0, 2);
  }

  /** Verify SIS card and cabinet boundaries without process channels. */
  @Test
  void sisSizingRoundsUpAtCardAndCabinetBoundaries() {
    assertSizing(0, 7, 0, 1, 1, 1, 1);
    assertSizing(0, 8, 0, 1, 1, 1, 1);
    assertSizing(0, 9, 0, 1, 2, 1, 1);
    assertSizing(0, 63, 0, 1, 8, 1, 1);
    assertSizing(0, 64, 0, 1, 8, 1, 1);
    assertSizing(0, 65, 0, 1, 9, 2, 1);
  }

  /** Verify marshalling uses the combined inventory, including safety channels. */
  @Test
  void marshallingUsesTotalIoAcrossEquipment() {
    assertSizing(135, 64, 9, 1, 8, 1, 1);
    assertSizing(136, 64, 9, 1, 8, 1, 1);
    assertSizing(137, 64, 9, 1, 8, 1, 2);
  }

  /**
   * Compare actual aggregation with explicit integer boundary expectations, including a repeated calculation.
   *
   * @param dcsIo non-safety channel count
   * @param sisIo safety AI/DI/DO channel count
   * @param dcsCards expected DCS cards
   * @param dcsCabinets expected DCS cabinets
   * @param sisCards expected SIS cards
   * @param sisCabinets expected SIS cabinets
   * @param marshalling expected marshalling cabinets
   */
  private void assertSizing(int dcsIo, int sisIo, int dcsCards, int dcsCabinets, int sisCards, int sisCabinets,
      int marshalling) {
    ProcessSystem process = new ProcessSystem("Synthetic I/O boundary");
    process.add(new InventoryStream("Process inventory", dcsIo, false));
    process.add(new InventoryStream("Safety inventory", sisIo, true));
    SystemInstrumentDesign design = process.getSystemInstrumentDesign();
    for (int pass = 0; pass < 2; pass++) {
      assertEquals(dcsIo + sisIo, design.getTotalIO());
      assertEquals(dcsIo + sisIo, design.getTotalInstruments());
      assertEquals(sisIo, design.getTotalSafetyIO());
      assertEquals(dcsCabinets, design.getDcsCabinets());
      assertEquals(sisCabinets, design.getSisCabinets());
      assertEquals(marshalling, design.getMarshallingCabinets());
      JsonObject sizing = JsonParser.parseString(design.toJson()).getAsJsonObject()
          .getAsJsonObject("controlSystemSizing");
      assertEquals(dcsCards, sizing.get("dcsIOCards").getAsInt());
      assertEquals(sisCards, sizing.get("sisIOCards").getAsInt());
      design.calcDesign();
    }
  }

  /**
   * Deterministic instrument inventory without thermodynamic calculation or equipment-specific auto-generation.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  private static class InventoryStream extends Stream {
    private static final long serialVersionUID = 1L;
    private final InstrumentDesign inventory;

    /**
     * Construct a fixed inventory cycling through the supported signal types.
     *
     * @param name unique equipment name
     * @param count channel count
     * @param safety whether channels are safety-related
     */
    InventoryStream(String name, int count, boolean safety) {
      super(name);
      inventory = new InstrumentDesign(this);
      String[] ioTypes = safety ? new String[] {"AI", "DI", "DO"} : new String[] {"AI", "AO", "DI", "DO"};
      for (int i = 0; i < count; i++) {
        InstrumentSpecification spec = new InstrumentSpecification("PT", "Synthetic channel", 0.0, 1.0, "dimensionless",
            ioTypes[i % ioTypes.length]);
        spec.setSafetyRelated(safety);
        inventory.getInstrumentList().add(spec);
      }
    }

    /**
     * Return the stable inventory consumed by system aggregation.
     *
     * @return fixed synthetic instrument design
     */
    @Override
    public InstrumentDesign getInstrumentDesign() {
      return inventory;
    }
  }
}
