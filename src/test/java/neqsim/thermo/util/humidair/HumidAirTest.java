package neqsim.thermo.util.humidair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HumidAir}.
 *
 * <p>All quantities are in SI units unless otherwise noted.</p>
 */
class HumidAirTest {

    /**
     * Validate humidity ratio calculation.
     *
     * <p>Inputs: temperature in Kelvin, pressure in Pascal and relative humidity
     * as a fraction. The expected humidity ratio is expressed in kg water per kg
     * dry air.</p>
     */
    @Test
    void testHumidityRatioFromRH() {
        double T = 303.15; // 30 °C
        double p = 101325.0; // Pa
        double rh = 0.5; // 50 % RH

        double W = HumidAir.humidityRatioFromRH(T, p, rh);

        assertEquals(0.0133, W, 1e-4); // kg/kg dry air
    }

    /**
     * Validate dew point temperature calculation.
     *
     * <p>Inputs: humidity ratio in kg/kg dry air and pressure in Pascal. The
     * expected output is the dew point temperature in Kelvin.</p>
     */
    @Test
    void testDewPoint() {
        double p = 101325.0; // Pa
        double W = 0.010; // kg/kg dry air

        double td = HumidAir.dewPointTemperature(W, p);

        assertEquals(287.2, td, 0.5); // K
    }

    /**
     * Validate saturated humid-air specific heat correlation.
     *
     * <p>Input temperature is given in Kelvin and the specific heat is returned
     * in kJ∕(kg·K).</p>
     */
    @Test
    void testCairSat() {
        double cp = HumidAir.cairSat(298.15); // K

        assertEquals(4.17, cp, 0.05); // kJ/(kg*K)
    }
}
