package neqsim.mcp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ValueWithUnit}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ValueWithUnitTest {

  @Test
  void testConstructorAndGetters() {
    ValueWithUnit v = new ValueWithUnit(25.0, "C");
    assertEquals(25.0, v.getValue(), 1e-9);
    assertEquals("C", v.getUnit());
  }

  @Test
  void testFromJson_bareNumber() {
    JsonElement el = new JsonPrimitive(300.0);
    ValueWithUnit v = ValueWithUnit.fromJson(el, "K");

    assertNotNull(v);
    assertEquals(300.0, v.getValue(), 1e-9);
    assertEquals("K", v.getUnit());
  }

  @Test
  void testFromJson_objectWithUnit() {
    JsonObject obj = new JsonObject();
    obj.addProperty("value", 25.0);
    obj.addProperty("unit", "C");

    ValueWithUnit v = ValueWithUnit.fromJson(obj, "K");

    assertNotNull(v);
    assertEquals(25.0, v.getValue(), 1e-9);
    assertEquals("C", v.getUnit());
  }

  @Test
  void testFromJson_objectWithoutUnit() {
    JsonObject obj = new JsonObject();
    obj.addProperty("value", 50.0);

    ValueWithUnit v = ValueWithUnit.fromJson(obj, "bara");

    assertNotNull(v);
    assertEquals(50.0, v.getValue(), 1e-9);
    assertEquals("bara", v.getUnit());
  }

  @Test
  void testFromJson_null() {
    ValueWithUnit v = ValueWithUnit.fromJson(null, "K");
    assertNull(v);
  }

  @Test
  void testToJson() {
    ValueWithUnit v = new ValueWithUnit(100.0, "bara");
    JsonElement json = v.toJson();

    JsonObject obj = json.getAsJsonObject();
    assertEquals(100.0, obj.get("value").getAsDouble(), 1e-9);
    assertEquals("bara", obj.get("unit").getAsString());
  }

  @Test
  void testToString() {
    ValueWithUnit v = new ValueWithUnit(25.0, "C");
    assertEquals("25.0 C", v.toString());
  }
}
