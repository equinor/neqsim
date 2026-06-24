package neqsim.process.mechanicaldesign.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StidWellBarrierDataSource}.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class StidWellBarrierDataSourceTest {

  /**
   * A complete producer schematic parses and validates as passed.
   */
  @Test
  void testProducerSchematicParses() {
    String json = "{" + "\"wellId\":\"WELL-A1\"," + "\"wellType\":\"OIL_PRODUCER\"," + "\"installationCode\":\"AAA\","
        + "\"primaryEnvelope\":{\"elements\":["
        + "{\"type\":\"TUBING\",\"name\":\"Tubing\",\"status\":\"INTACT\",\"verified\":true},"
        + "{\"type\":\"DHSV\",\"name\":\"SCSSV\",\"status\":\"INTACT\",\"verified\":true},"
        + "{\"type\":\"XMAS_TREE\",\"name\":\"Tree\",\"status\":\"INTACT\",\"verified\":true}]},"
        + "\"secondaryEnvelope\":{\"elements\":["
        + "{\"type\":\"CASING\",\"name\":\"Casing\",\"status\":\"INTACT\",\"verified\":true},"
        + "{\"type\":\"CEMENT\",\"name\":\"Cement\",\"status\":\"INTACT\",\"verified\":true},"
        + "{\"type\":\"WELLHEAD\",\"name\":\"Wellhead\",\"status\":\"INTACT\",\"verified\":true}]}}";
    StidWellBarrierDataSource source = new StidWellBarrierDataSource(json);
    assertEquals("WELL-A1", source.getWellId());
    assertEquals("AAA", source.getInstallationCode());
    WellBarrierSchematic schematic = source.read();
    assertEquals("OIL_PRODUCER", schematic.getWellType());
    assertEquals(3, schematic.getPrimaryEnvelope().getElementCount());
    assertEquals(3, schematic.getSecondaryEnvelope().getElementCount());
    assertTrue(schematic.getPrimaryEnvelope().hasElementType(BarrierElement.ElementType.DHSV));
    assertTrue(schematic.validate());
  }

  /**
   * An unrecognized element type defaults to CASING and a degraded status is parsed.
   */
  @Test
  void testUnknownTypeAndStatusDefaults() {
    String json = "{\"wellId\":\"WELL-B2\",\"primaryEnvelope\":{\"elements\":["
        + "{\"type\":\"NOT_A_TYPE\",\"name\":\"X\",\"status\":\"DEGRADED\"}]}}";
    StidWellBarrierDataSource source = new StidWellBarrierDataSource(json);
    WellBarrierSchematic schematic = source.read();
    BarrierElement element = schematic.getPrimaryEnvelope().getElements().get(0);
    assertEquals(BarrierElement.ElementType.CASING, element.getType());
    assertEquals(BarrierElement.Status.DEGRADED, element.getStatus());
  }

  /**
   * An empty source yields an empty, non-null schematic.
   */
  @Test
  void testEmptySource() {
    StidWellBarrierDataSource source = new StidWellBarrierDataSource("{}");
    WellBarrierSchematic schematic = source.read();
    assertEquals(0, schematic.getPrimaryEnvelope().getElementCount());
    assertEquals(0, schematic.getSecondaryEnvelope().getElementCount());
  }
}
