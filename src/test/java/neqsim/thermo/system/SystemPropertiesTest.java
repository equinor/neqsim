package neqsim.thermo.system;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SystemPropertiesTest extends neqsim.NeqSimTest{
    @Test
    void testGetProperties() {
        String[] name = SystemProperties.getPropertyNames();
        Assertions.assertEquals(70, name.length);
    }
}
