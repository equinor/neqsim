package neqsim.process.safety.hazid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StidHazopDataSource}, which builds HAZOP nodes from normalized STID/P&amp;ID JSON.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class StidHazopDataSourceTest {

  private static final String SAMPLE_JSON = "{" + "\"projectName\": \"Example\"," + "\"installationCode\": \"AAA\","
      + "\"nodes\": [" + "  {" + "    \"nodeId\": \"Node-01: Inlet separator\"," + "    \"tag\": \"VA-2001\","
      + "    \"designIntent\": \"Separate inlet gas and liquid at 60 bara\"," + "    \"deviations\": [" + "      {"
      + "        \"guideWord\": \"MORE\"," + "        \"parameter\": \"LEVEL\","
      + "        \"cause\": \"Liquid outlet valve fails closed\","
      + "        \"consequence\": \"Liquid carry-over to compressor\","
      + "        \"safeguard\": \"LAHH-2001 trips inlet valve\","
      + "        \"recommendation\": \"Verify trip set-point\"" + "      }" + "    ]" + "  }," + "  {"
      + "    \"tag\": \"PA-3001\"," + "    \"description\": \"Export compressor\"," + "    \"deviations\": ["
      + "      {\"guideWord\": \"NO\", \"parameter\": \"FLOW\"}" + "    ]" + "  }" + "]" + "}";

  @Test
  void readsProjectMetadata() {
    StidHazopDataSource source = new StidHazopDataSource(SAMPLE_JSON);
    assertEquals("Example", source.getProjectName());
    assertEquals("AAA", source.getInstallationCode());
  }

  @Test
  void buildsOneNodePerRecord() {
    List<HAZOPTemplate> nodes = new StidHazopDataSource(SAMPLE_JSON).read();
    assertEquals(2, nodes.size());
  }

  @Test
  void usesExplicitNodeIdAndDesignIntent() {
    List<HAZOPTemplate> nodes = new StidHazopDataSource(SAMPLE_JSON).read();
    HAZOPTemplate first = nodes.get(0);
    assertEquals("Node-01: Inlet separator", first.getNodeId());
    assertEquals("Separate inlet gas and liquid at 60 bara", first.getDesignIntent());
  }

  @Test
  void synthesizesNodeIdFromTagWhenMissing() {
    List<HAZOPTemplate> nodes = new StidHazopDataSource(SAMPLE_JSON).read();
    HAZOPTemplate second = nodes.get(1);
    assertTrue(second.getNodeId().startsWith("Node-02: "));
    assertTrue(second.getNodeId().contains("PA-3001"));
    assertEquals("Export compressor", second.getDesignIntent());
  }

  @Test
  void mapsDeviationFieldsWithRecommendation() {
    List<HAZOPTemplate> nodes = new StidHazopDataSource(SAMPLE_JSON).read();
    List<HAZOPTemplate.HAZOPDeviation> deviations = nodes.get(0).getDeviations();
    assertEquals(1, deviations.size());
    HAZOPTemplate.HAZOPDeviation deviation = deviations.get(0);
    assertEquals(HAZOPTemplate.GuideWord.MORE, deviation.guideWord);
    assertEquals(HAZOPTemplate.Parameter.LEVEL, deviation.parameter);
    assertEquals("Liquid outlet valve fails closed", deviation.cause);
    assertEquals("Verify trip set-point", deviation.recommendation);
  }

  @Test
  void defaultsMissingDeviationTextToTbd() {
    List<HAZOPTemplate> nodes = new StidHazopDataSource(SAMPLE_JSON).read();
    HAZOPTemplate.HAZOPDeviation deviation = nodes.get(1).getDeviations().get(0);
    assertEquals(HAZOPTemplate.GuideWord.NO, deviation.guideWord);
    assertEquals(HAZOPTemplate.Parameter.FLOW, deviation.parameter);
    assertEquals("TBD", deviation.cause);
    assertEquals("TBD", deviation.consequence);
    assertEquals("TBD", deviation.safeguard);
  }

  @Test
  void skipsUnrecognizedDeviation() {
    String json = "{\"nodes\": [{\"nodeId\": \"N1\", \"designIntent\": \"x\","
	+ "\"deviations\": [{\"guideWord\": \"BOGUS\", \"parameter\": \"FLOW\"}]}]}";
    List<HAZOPTemplate> nodes = new StidHazopDataSource(json).read();
    assertEquals(1, nodes.size());
    assertTrue(nodes.get(0).getDeviations().isEmpty());
  }

  @Test
  void emptySourceProducesNoNodes() {
    assertNotNull(new StidHazopDataSource("{}").read());
    assertTrue(new StidHazopDataSource("{}").read().isEmpty());
    assertFalse(new StidHazopDataSource(SAMPLE_JSON).read().isEmpty());
  }
}
