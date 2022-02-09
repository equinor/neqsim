package neqsim.thermo.system;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SystemThermoAddComponentTest {
    SystemInterface sys;

    @BeforeEach
    void setup() {
        sys = new SystemSrkEos(298.0, 300.0);
        /*
         * sys.addComponent("nitrogen", 0.64);
         * sys.addTBPfraction("C7", 1.06, 92.2 / 1000.0, 0.7324);
         * sys.addPlusFraction("C20", 10.62, 381.0 / 1000.0, 0.88);
         * sys.getCharacterization().characterisePlusFraction();
         * sys.createDatabase(true);
         * sys.setMixingRule(2);
         */
    }


    @Test
    void testAddComponent() {
        // Assure that System contains no components
        Assertions.assertEquals(0, sys.getNumberOfComponents());

        // Add a component with no moles,
        // assure number of system components is 1 and that number of moles is 0
        sys.addComponent("nitrogen");
        Assertions.assertEquals(1, sys.getNumberOfComponents());
        Assertions.assertEquals(0, sys.getTotalNumberOfMoles());

        // Add a component with moles,
        // assure number of components is 1 and that number of moles is equal to input
        double moles = 0.64;
        sys.addComponent("nitrogen", moles);
        Assertions.assertEquals(1, sys.getNumberOfComponents());
        Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());

        // Add same component with no moles,
        // assure no change in number of components nor number of moles
        sys.addComponent("nitrogen");
        Assertions.assertEquals(1, sys.getNumberOfComponents());
        Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());

        // Add same component with no moles,
        // assure no change in number of components nor number of moles
        sys.addComponent("nitrogen", 0);
        Assertions.assertEquals(1, sys.getNumberOfComponents());
        Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());

        // Add same component with moles, assure no change in number of components
        // assure number of moles is doubled
        sys.addComponent("nitrogen", moles);
        Assertions.assertEquals(1, sys.getNumberOfComponents());
        Assertions.assertEquals(2 * moles, sys.getTotalNumberOfMoles());
    }

    @Test
    void testAddPlusFraction() {
        // Assure that System contains no components
        Assertions.assertEquals(0, sys.getNumberOfComponents());

        // Add a component with no moles,
        // assure number of components is 1 and that number of moles is 0
        sys.addPlusFraction("C20", 0, 381.0 / 1000.0, 0.88);
        Assertions.assertEquals(1, sys.getNumberOfComponents());
        Assertions.assertEquals(0, sys.getTotalNumberOfMoles());

        // Add a component with moles,
        // assure number of components is 1 and that number of moles is equal to input
        double moles = 10.62;
        sys.addPlusFraction("C20", moles, 381.0 / 1000.0, 0.88);
        Assertions.assertEquals(1, sys.getNumberOfComponents());
        Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());

        // Add same component with no moles,
        // assure no change in number of components nor number of moles
        sys.addPlusFraction("C20", 0, 381.0 / 1000.0, 0.88);
        Assertions.assertEquals(1, sys.getNumberOfComponents());
        Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());

        // Add same component with moles,
        // assure no change in number of components, assure number of moles is doubled
        sys.addPlusFraction("C20", moles, 381.0 / 1000.0, 0.88);
        Assertions.assertEquals(1, sys.getNumberOfComponents());
        Assertions.assertEquals(2 * moles, sys.getTotalNumberOfMoles());
    }

    @Test
    void testAddTBPFraction() {
        // Assure that System contains no components
        Assertions.assertEquals(0, sys.getNumberOfComponents());

        // Add a component with no moles,
        // assure number of components is 1 and that number of moles is 0
        sys.addTBPfraction("C7", 0, 92.2 / 1000.0, 0.7324);
        Assertions.assertEquals(1, sys.getNumberOfComponents());
        Assertions.assertEquals(0, sys.getTotalNumberOfMoles());

        // Add a component with moles,
        // assure number of components is 1 and that number of moles is equal to input
        double moles = 1.06;
        sys.addTBPfraction("C7", moles, 92.2 / 1000.0, 0.7324);
        Assertions.assertEquals(1, sys.getNumberOfComponents());
        Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());

        // Add same component with no moles,
        // assure no change in number of components nor number of moles
        sys.addTBPfraction("C7", 0, 92.2 / 1000.0, 0.7324);
        Assertions.assertEquals(1, sys.getNumberOfComponents());
        Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());

        // Add same component with moles,
        // assure no change in number of components, assure number of moles is doubled
        sys.addTBPfraction("C7", moles, 92.2 / 1000.0, 0.7324);
        Assertions.assertEquals(1, sys.getNumberOfComponents());
        Assertions.assertEquals(2 * moles, sys.getTotalNumberOfMoles());
    }

    @Test
    void testGetTotalNumberOfMoles() {
        sys.addComponent("nitrogen");
        Assertions.assertEquals(0, sys.getTotalNumberOfMoles());

        double moles = 1;
        sys.setTotalNumberOfMoles(moles);
        Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());
    }
}
