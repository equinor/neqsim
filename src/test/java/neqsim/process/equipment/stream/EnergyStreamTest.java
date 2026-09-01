package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.ProcessElementInterface;

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

    // Identity equality: duty is mutable, so two distinct streams are never equal even when they
    // currently carry the same duty. Compare getDuty() when a value comparison is intended.
    assertFalse(a.equals(b));
    assertTrue(a.equals(a));
    assertEquals(a.getDuty(), b.getDuty(), 1e-12);
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

    // The hash must be stable across a duty change: an EnergyStream held as a HashMap key or
    // HashSet element has to stay reachable after the flowsheet runs.
    int before = a.hashCode();
    a.setDuty(250.0);
    assertEquals(before, a.hashCode());

    java.util.Set<EnergyStream> registry = new java.util.HashSet<EnergyStream>();
    registry.add(a);
    a.setDuty(-40.0);
    assertTrue(registry.contains(a));
  }

  @Test
  void testUnitAwareEnergyFlowAliases() {
    EnergyStream stream = new EnergyStream("motor-power", EnergyType.ELECTRICAL);
    stream.setEnergyFlow(1.5, "MW");

    assertEquals(1.5e6, stream.getDuty(), 1e-10);
    assertEquals(1500.0, stream.getPower("kW"), 1e-10);
    assertEquals(1.5, stream.getEnergyFlow("MW"), 1e-10);
  }

  @Test
  void testEnergyStreamIsProcessElement() {
    ProcessElementInterface element = new EnergyStream("shaft-work", EnergyType.SHAFT_WORK);

    assertEquals("shaft-work", element.getName());
    assertEquals(EnergyType.SHAFT_WORK, ((EnergyStream) element).getEnergyType());
  }

}
