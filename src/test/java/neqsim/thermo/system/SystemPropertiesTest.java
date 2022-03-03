package neqsim.thermo.system;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SystemPropertiesTest {
    @Test
    void testGetProperties() {
        String[] name = SystemProperties.getPropertyNames();
        Assertions.assertEquals(70, name.length);
    }
}
