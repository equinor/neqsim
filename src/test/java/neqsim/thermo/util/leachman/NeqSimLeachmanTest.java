package neqsim.thermo.util.leachman;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * @author victorigi99
 */

public class NeqSimLeachmanTest {
    @Test
    void testGetDensityHydrogen() {
      SystemInterface testSystem = new SystemSrkEos(298.0, 10.0);
      SystemInterface testSystem2 = new SystemSrkEos(298.0, 10.0);

      testSystem.addComponent("hydrogen", 100.0);
      testSystem2.addComponent("para-hydrogen", 100.0);

      testSystem.init(0);
      double density = testSystem.getPhase(0).getDensity_Leachman();
      double density2 = testSystem2.getPhase(0).getDensity_Leachman("normal");
      //System.out.println("Density (Para-Hydrogen): " + density);
      assertEquals(density, density2, 1e-8);
      }

    @Test
    void testGetDensityParaHydrogen() {
        SystemInterface testSystem = new SystemSrkEos(298.0, 10.0);
        SystemInterface testSystem2 = new SystemSrkEos(298.0, 10.0);

        testSystem.addComponent("hydrogen", 100.0);
        testSystem2.addComponent("para-hydrogen", 100.0);

        testSystem.init(0);
        double density = testSystem.getPhase(0).getDensity_Leachman("para");
        double density2 = testSystem2.getPhase(0).getDensity_Leachman();
        //System.out.println("Density (Para-Hydrogen): " + density);
        assertEquals(density, density2, 1e-8);
    }

    @Test
    void testGetDensityOrthoHydrogen() {
      SystemInterface testSystem = new SystemSrkEos(298.0, 10.0);
      SystemInterface testSystem2 = new SystemSrkEos(298.0, 10.0);

      testSystem.addComponent("hydrogen", 100.0);
      testSystem2.addComponent("ortho-hydrogen", 100.0);

      testSystem.init(0);
      double density = testSystem.getPhase(0).getDensity_Leachman("ortho");
      double density2 = testSystem2.getPhase(0).getDensity_Leachman();
      //System.out.println("Density (Para-Hydrogen): " + density);
      assertEquals(density, density2, 1e-8);
    }
        @Test
    void testGetPropertiesLeachmanHydrogen() {
        SystemInterface testSystem = new SystemSrkEos(298.0, 10.0);
        SystemInterface testSystem2 = new SystemSrkEos(298.0, 10.0);

        testSystem.addComponent("hydrogen", 100.0);
        testSystem2.addComponent("para-hydrogen", 100.0);

        testSystem.init(0);
        double[] properties = testSystem.getPhase(0).getProperties_Leachman();
        double[] properties2 = testSystem2.getPhase(0).getProperties_Leachman("normal");
        //System.out.println("Properties (Hydrogen): " + java.util.Arrays.toString(properties));
        assertArrayEquals(properties, properties2, 1e-8);
    }

    @Test
    void testGetPropertiesLeachmanParaHydrogen() {
        SystemInterface testSystem = new SystemSrkEos(298.0, 10.0);
        SystemInterface testSystem2 = new SystemSrkEos(298.0, 10.0);

        testSystem.addComponent("hydrogen", 100.0);
        testSystem2.addComponent("para-hydrogen", 100.0);

        testSystem.init(0);
        double[] properties = testSystem.getPhase(0).getProperties_Leachman("para");
        double[] properties2 = testSystem2.getPhase(0).getProperties_Leachman();
        //System.out.println("Properties (Para-Hydrogen): " + java.util.Arrays.toString(properties));
        assertArrayEquals(properties, properties2, 1e-8);
    }

    @Test
    void testGetPropertiesLeachmanOrthoHydrogen() {
        SystemInterface testSystem = new SystemSrkEos(298.0, 10.0);
        SystemInterface testSystem2 = new SystemSrkEos(298.0, 10.0);

        testSystem.addComponent("hydrogen", 100.0);
        testSystem2.addComponent("ortho-hydrogen", 100.0);

        testSystem.init(0);
        double[] properties = testSystem.getPhase(0).getProperties_Leachman("ortho");
        double[] properties2 = testSystem2.getPhase(0).getProperties_Leachman();
        //System.out.println("Properties (Ortho-Hydrogen): " + java.util.Arrays.toString(properties));
        assertArrayEquals(properties, properties2, 1e-8);
    }
}
