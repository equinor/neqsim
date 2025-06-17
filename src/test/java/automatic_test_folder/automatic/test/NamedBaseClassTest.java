package automatic.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.util.NamedBaseClass;

class NamedBaseClassTest {
  static class TestObject extends NamedBaseClass {
    TestObject(String name) {
      super(name);
    }
  }

  @Test
  void testNameAndTagHandling() {
    TestObject obj = new TestObject("initial");
    assertEquals("initial", obj.getName());
    obj.setName("changed");
    assertEquals("changed", obj.getName());

    obj.setTagName("tag1");
    assertEquals("tag1", obj.getTagName());
    assertThrows(IllegalArgumentException.class, () -> obj.setTagName(null));
  }
}
