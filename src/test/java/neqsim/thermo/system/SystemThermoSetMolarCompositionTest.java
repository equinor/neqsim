package neqsim.thermo.system;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SystemThermoSetMolarCompositionTest {
    SystemInterface sys;

    @BeforeEach
    void setup() {
        sys = new SystemSrkEos(298.0, 300.0);
        sys.addComponent("nitrogen", 0.64);
        sys.addTBPfraction("C7", 1.06, 92.2 / 1000.0, 0.7324);
        sys.addPlusFraction("C20", 10.62, 381.0 / 1000.0, 0.88);
    }

    @Test
    void testSetMolarComposition() {
        sys.setMolarComposition(new double[] { 1, 1.5, 1.75 });
        double[] molarComposition = sys.getMolarComposition();
        Assertions.assertEquals(0.23529411764705882, molarComposition[0], 0.0000001);
        Assertions.assertEquals(0.35294117647058826, molarComposition[1], 0.0000001);
        Assertions.assertEquals(0.411764705882353, molarComposition[2], 0.0000001);

        System.out.println(sys);
    }

    @Test
    void setMolarCompositionPlus() {
        sys.getCharacterization().characterisePlusFraction();
        sys.setMolarCompositionPlus(new double[] { 1, 1.5, 1.75 });
        double[] molarComposition = sys.getMolarComposition();

        Assertions.assertEquals(0.3999998435802131, molarComposition[0], 0.0000001);
        Assertions.assertEquals(0.17255098326920942, molarComposition[1], 0.0000001);
        Assertions.assertEquals(0.1310217963111427, molarComposition[2], 0.0000001);
        System.out.println(sys);
    }

    @Test
    void testSetMolarCompositionOfPlusFluid() {
        sys.setMolarCompositionOfPlusFluid(new double[] { 1, 1.5, 1.75 });
        double[] molarComposition = sys.getMolarComposition();

        Assertions.assertEquals(0.39999999999999997, molarComposition[0], 0.0000001);
        Assertions.assertEquals(0.6, molarComposition[1], 0.0000001);
        Assertions.assertEquals(0.0, molarComposition[2], 0.0000001);
        System.out.println(sys);
    }

    @Test
    void testNoFlow() {
        sys.reset();
        RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
                () -> {
                    sys.setMolarComposition(new double[] { 1, 1.5, 1.75 });
                });
        Assertions.assertEquals(
                "neqsim.util.exception.InvalidInputException: Total flow can not be 0 when setting molar composition",
                thrown.getMessage());
    }

    @Test
    void testNoFlowPlus() {
        sys.reset();
        RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
                () -> {
                    sys.setMolarCompositionPlus(new double[] { 1, 1.5, 1.75 });
                });
        Assertions.assertEquals(
                "neqsim.util.exception.InvalidInputException: Total flow can not be 0 when setting molar composition",
                thrown.getMessage());
    }

    @Test
    void testNoFlowPlusFluid() {
        sys.reset();
        RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
                () -> {
                    sys.setMolarCompositionOfPlusFluid(new double[] { 1, 1.5, 1.75 });
                });
        Assertions.assertEquals(
                "neqsim.util.exception.InvalidInputException: Total flow can not be 0 when setting molar composition",
                thrown.getMessage());
    }
}
