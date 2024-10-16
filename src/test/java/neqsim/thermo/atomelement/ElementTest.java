package neqsim.thermo.atomelement;

import java.util.ArrayList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ElementTest {
  @Test
  public void testExistingElement() {
    ArrayList<String> names = Element.getAllElementComponentNames();

    Assertions.assertTrue(names.size() > 0);

    Element a = new Element(names.get(0));
    Assertions.assertEquals(a.getName(), names.get(0));
  }

  @Test
  public void testNonExistingElement() {
    ArrayList<String> names = Element.getAllElementComponentNames();

    String nonExistingElement = "DUMMY_ELEMENT";

    Assertions.assertFalse(names.contains(nonExistingElement));

    Element e = new Element(nonExistingElement);
    Assertions.assertNull(e.getElementNames());
    Assertions.assertNull(e.getElementCoefs());
  }
}
