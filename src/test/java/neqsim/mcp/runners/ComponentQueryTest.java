package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link ComponentQuery}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ComponentQueryTest {

  @Test
  void testIsValidKnownComponents() {
    assertTrue(ComponentQuery.isValid("methane"));
    assertTrue(ComponentQuery.isValid("ethane"));
    assertTrue(ComponentQuery.isValid("propane"));
    assertTrue(ComponentQuery.isValid("CO2"));
    assertTrue(ComponentQuery.isValid("water"));
    assertTrue(ComponentQuery.isValid("H2S"));
    assertTrue(ComponentQuery.isValid("nitrogen"));
    assertTrue(ComponentQuery.isValid("hydrogen"));
  }

  @Test
  void testIsValidCaseInsensitive() {
    assertTrue(ComponentQuery.isValid("Methane"));
    assertTrue(ComponentQuery.isValid("METHANE"));
    assertTrue(ComponentQuery.isValid("co2"));
    assertTrue(ComponentQuery.isValid("Co2"));
  }

  @Test
  void testIsValidUnknownComponents() {
    assertFalse(ComponentQuery.isValid("unobtainium"));
    assertFalse(ComponentQuery.isValid("mthane"));
    assertFalse(ComponentQuery.isValid(""));
    assertFalse(ComponentQuery.isValid(null));
  }

  @Test
  void testGetAllNamesNotEmpty() {
    List<String> names = ComponentQuery.getAllNames();
    assertNotNull(names);
    assertFalse(names.isEmpty());
    assertTrue(names.size() > 50, "Should have many components, got: " + names.size());
  }

  @Test
  void testGetAllNamesContainsKnownComponents() {
    List<String> names = ComponentQuery.getAllNames();
    assertTrue(names.contains("methane"));
    assertTrue(names.contains("ethane"));
    assertTrue(names.contains("water"));
  }

  @Test
  void testSearchBySubstring() {
    String resultJson = ComponentQuery.search("meth");
    assertNotNull(resultJson);

    JsonObject result = JsonParser.parseString(resultJson).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString());
    assertTrue(result.get("matchCount").getAsInt() >= 2, "Should match methane, methanol, etc.");

    // Check that methane is in the results
    boolean foundMethane = false;
    for (int i = 0; i < result.getAsJsonArray("components").size(); i++) {
      if ("methane".equals(result.getAsJsonArray("components").get(i).getAsString())) {
        foundMethane = true;
        break;
      }
    }
    assertTrue(foundMethane, "Search for 'meth' should include methane");
  }

  @Test
  void testSearchEmptyQuery() {
    String resultJson = ComponentQuery.search("");
    JsonObject result = JsonParser.parseString(resultJson).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString());
    assertTrue(result.get("matchCount").getAsInt() > 50,
        "Empty query should return all components");
  }

  @Test
  void testSearchNullQuery() {
    String resultJson = ComponentQuery.search(null);
    JsonObject result = JsonParser.parseString(resultJson).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString());
    assertTrue(result.get("matchCount").getAsInt() > 50);
  }

  @Test
  void testSearchNoMatch() {
    String resultJson = ComponentQuery.search("unobtainium");
    JsonObject result = JsonParser.parseString(resultJson).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString());
    assertEquals(0, result.get("matchCount").getAsInt());
  }

  @Test
  void testClosestMatchTypo() {
    // "metane" should suggest "methane"
    String match = ComponentQuery.closestMatch("metane");
    assertNotNull(match, "Should find a close match for 'metane'");
    assertEquals("methane", match);
  }

  @Test
  void testClosestMatchExact() {
    // Exact match returns the properly-cased name
    String match = ComponentQuery.closestMatch("methane");
    assertNotNull(match);
    assertEquals("methane", match);
  }

  @Test
  void testClosestMatchNoReasonableMatch() {
    // Something totally unrelated should return null
    String match = ComponentQuery.closestMatch("xyzzyplugh");
    assertNull(match, "Should not suggest a match for something very different");
  }

  @Test
  void testClosestMatchNull() {
    assertNull(ComponentQuery.closestMatch(null));
    assertNull(ComponentQuery.closestMatch(""));
  }

  @Test
  void testLevenshteinDistance() {
    assertEquals(0, ComponentQuery.levenshteinDistance("abc", "abc"));
    assertEquals(1, ComponentQuery.levenshteinDistance("abc", "ab"));
    assertEquals(1, ComponentQuery.levenshteinDistance("abc", "axc"));
    assertEquals(3, ComponentQuery.levenshteinDistance("abc", "xyz"));
    assertEquals(1, ComponentQuery.levenshteinDistance("metane", "methane"));
  }

  @Test
  void testGetInfoKnownComponent() {
    String resultJson = ComponentQuery.getInfo("methane");
    assertNotNull(resultJson);

    JsonObject result = JsonParser.parseString(resultJson).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString());
    assertTrue(result.has("component"));

    JsonObject comp = result.getAsJsonObject("component");
    assertEquals("methane", comp.get("name").getAsString());
    assertTrue(comp.get("molarMass_g_mol").getAsDouble() > 15.0);
    assertTrue(comp.get("molarMass_g_mol").getAsDouble() < 17.0);
    assertTrue(comp.get("criticalPressure_bara").getAsDouble() > 40.0);
  }

  @Test
  void testGetInfoUnknownComponent() {
    String resultJson = ComponentQuery.getInfo("unobtainium");
    assertNotNull(resultJson);

    JsonObject result = JsonParser.parseString(resultJson).getAsJsonObject();
    assertEquals("error", result.get("status").getAsString());
    assertTrue(result.has("errors"));
    assertEquals("UNKNOWN_COMPONENT",
        result.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  @Test
  void testGetInfoNull() {
    String resultJson = ComponentQuery.getInfo(null);
    JsonObject result = JsonParser.parseString(resultJson).getAsJsonObject();
    assertEquals("error", result.get("status").getAsString());
  }

  @Test
  void testGetInfoWithSuggestion() {
    // Misspelled "metane" should produce error with suggestion
    String resultJson = ComponentQuery.getInfo("metane");
    JsonObject result = JsonParser.parseString(resultJson).getAsJsonObject();
    assertEquals("error", result.get("status").getAsString());

    String remediation =
        result.getAsJsonArray("errors").get(0).getAsJsonObject().get("remediation").getAsString();
    assertTrue(remediation.contains("methane"),
        "Should suggest 'methane' for 'metane', got: " + remediation);
  }
}
