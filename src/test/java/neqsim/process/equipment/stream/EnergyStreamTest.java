package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class EnergyStreamTest {

  @Test
  void testDefaultConstructor() {
    EnergyStream stream = new EnergyStream();
    assertEquals(0.0, stream.getDuty(), 1e-10);
    assertEquals("", stream.getName());
  }

  @Test
  void testNameConstructor() {
    EnergyStream stream = new EnergyStream("heater-energy");
    assertEquals("heater-energy", stream.getName());
    assertEquals(0.0, stream.getDuty(), 1e-10);
  }

  @Test
  void testSetGetDuty() {
    EnergyStream stream = new EnergyStream("energy");
    stream.setDuty(1500.0);
    assertEquals(1500.0, stream.getDuty(), 1e-10);
  }

  @Test
  void testSetGetDutyNegative() {
    EnergyStream stream = new EnergyStream("cooling");
    stream.setDuty(-3000.0);
    assertEquals(-3000.0, stream.getDuty(), 1e-10);
  }

  @Test
  void testSetGetName() {
    EnergyStream stream = new EnergyStream();
    stream.setName("compressor-work");
    assertEquals("compressor-work", stream.getName());
  }

  @Test
  void testClone() {
    EnergyStream original = new EnergyStream("original");
    original.setDuty(2500.0);

    EnergyStream cloned = original.clone();
    assertNotNull(cloned);
    assertNotSame(original, cloned);
    assertEquals(original.getDuty(), cloned.getDuty(), 1e-10);
    assertEquals(original.getName(), cloned.getName());
  }

  @Test
  void testCloneIndependence() {
    EnergyStream original = new EnergyStream("original");
    original.setDuty(2500.0);

    EnergyStream cloned = original.clone();
    cloned.setDuty(5000.0);

    // Original should not be affected
    assertEquals(2500.0, original.getDuty(), 1e-10);
    assertEquals(5000.0, cloned.getDuty(), 1e-10);
  }

  @Test
  void testEqualsSameObject() {
    EnergyStream stream = new EnergyStream("a");
    stream.setDuty(100.0);
    assertTrue(stream.equals(stream));
  }

  @Test
  void testEqualsEqualDuty() {
    EnergyStream a = new EnergyStream("a");
    a.setDuty(100.0);

    EnergyStream b = new EnergyStream("b");
    b.setDuty(100.0);

    // equals is based on duty value only
    assertTrue(a.equals(b));
  }

  @Test
  void testEqualsDifferentDuty() {
    EnergyStream a = new EnergyStream("a");
    a.setDuty(100.0);

    EnergyStream b = new EnergyStream("b");
    b.setDuty(200.0);

    assertFalse(a.equals(b));
  }

  @Test
  void testEqualsNull() {
    EnergyStream stream = new EnergyStream("a");
    assertFalse(stream.equals(null));
  }

  @Test
  void testEqualsDifferentType() {
    EnergyStream stream = new EnergyStream("a");
    assertFalse(stream.equals("not an energy stream"));
  }

  @Test
  void testHashCodeConsistency() {
    EnergyStream a = new EnergyStream("a");
    a.setDuty(100.0);

    EnergyStream b = new EnergyStream("b");
    b.setDuty(100.0);

    assertEquals(a.hashCode(), b.hashCode());
  }
}
