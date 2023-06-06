package neqsim.PVTsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * @author ESOL
 *
 */
class SaturationTemperatureTest extends neqsim.NeqSimTest {
    /**
     * @throws java.lang.Exception
     */
    @BeforeAll
    static void setUpBeforeClass() throws Exception {}

    /**
     * Test method for
     * {@link neqsim.PVTsimulation.simulation.SaturationTemperature#calcSaturationTemperature()}.
     */
    @Test
    void testCalcSaturationTemperature() {
        SystemInterface tempSystem = new SystemSrkEos(273.15 + 220, 60.0);
        tempSystem.addComponent("nitrogen", 0.34);
        tempSystem.addComponent("CO2", 3.59);
        tempSystem.addComponent("methane", 67.42);
        tempSystem.addComponent("ethane", 9.02);
        tempSystem.addComponent("propane", 4.31);
        tempSystem.addComponent("i-butane", 0.93);
        tempSystem.addComponent("n-butane", 1.71);
        tempSystem.addComponent("i-pentane", 0.74);
        tempSystem.addComponent("n-pentane", 0.85);
        tempSystem.addComponent("n-hexane", 0.38);
        tempSystem.addTBPfraction("C7", 0.5, 109.00 / 1000.0, 0.6912);
        tempSystem.addTBPfraction("C8", 0.69, 120.20 / 1000.0, 0.7255);
        tempSystem.addTBPfraction("C9", 0.14, 129.5 / 1000.0, 0.7454);
        tempSystem.addTBPfraction("C10", 0.08, 135.3 / 1000.0, 0.7864);
        // tempSystem.createDatabase(true);
        tempSystem.setMixingRule(2); // "HV", "UNIFAC_UMRPRU");
        tempSystem.init(0);
        tempSystem.init(1);
        // tempSystem.saveFluid(928);

        SimulationInterface satPresSim = new SaturationTemperature(tempSystem);
        satPresSim.run();
        assertEquals(tempSystem.getTemperature(), 380.3071922, 0.1);
    }
}
