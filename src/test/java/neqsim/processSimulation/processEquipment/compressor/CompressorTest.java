package neqsim.processSimulation.processEquipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

class CompressorTest {
    static Logger logger = LogManager.getLogger(CompressorTest.class);

    static neqsim.thermo.system.SystemInterface testSystem = null;

    double pressure_inlet = 85.0;
    double temperature_inlet = 35.0;
    double gasFlowRate = 5.0;
    double pressure_Out = 150.0;
    double polytropicEff = 0.77;
    ProcessSystem processOps = null;
    neqsim.processSimulation.processEquipment.compressor.Compressor compressor1 = null;

    String unitName = "Compressor1";

    /**
     * <p>
     * setUp.
     * </p>
     *
     * @throws java.lang.Exception if any.
     */
    @BeforeEach
    public void setUp() throws Exception {
        testSystem = new SystemSrkEos(298.0, 10.0);
        testSystem.addComponent("methane", 100.0);
        processOps = new ProcessSystem();
        Stream inletStream = new Stream(testSystem);
        inletStream.setPressure(pressure_inlet, "bara");
        inletStream.setTemperature(temperature_inlet, "C");
        inletStream.setFlowRate(gasFlowRate, "MSm3/day");
        compressor1 = new Compressor(unitName, inletStream);
        compressor1.setOutletPressure(pressure_Out);
        processOps.add(inletStream);
        processOps.add(compressor1);
    }

    /**
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @Test
    public void testSerialize() throws IOException, ClassNotFoundException {
        Compressor cmp = (Compressor) processOps.getUnit(unitName);

        String tmp = "test_compressor_saved.ser";

        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(tmp))) {
            objectOutputStream.writeObject(cmp);
            objectOutputStream.flush();
            objectOutputStream.close();

            ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(tmp));
            Compressor cmp2 = (Compressor) objectInputStream.readObject();
            objectInputStream.close();

            Assertions.assertTrue(cmp.getName().compareTo(cmp2.getName()) == 0);

            Assertions.assertTrue(cmp.equals(cmp2));
        } catch (Exception e) {
            logger.warn("Exceptions happen!", e);
        } finally {
            File file = new File(tmp);
            file.delete();
        }
    }

    /**
     * <p>
     * testCompressorSchultzMethod.
     * </p>
     */
    @Test
    public void testCompressorSchultzMethod() {
        compressor1.setPolytropicEfficiency(polytropicEff);
        compressor1.setUsePolytropicCalc(true);
        compressor1.setPolytropicMethod("schultz");
        processOps.run();
        // System.out.println("schultz compressor power " + compressor1.getPower() / 1e6
        // + " MW");
        assertEquals(compressor1.getPower() / 1e6, 4.668373797540108,
                "Test case for compressor Schultz method polytropic calculation should return approximate 4.67 MW");
    }

    /**
     * <p>
     * testCompressorRigorousMethod.
     * </p>
     */
    @Test
    public void testCompressorRigorousMethod() {
        compressor1.setPolytropicEfficiency(polytropicEff);
        compressor1.setUsePolytropicCalc(true);
        compressor1.setPolytropicMethod("detailed");
        processOps.run();
        // System.out.println("rigorous compressor power " + compressor1.getPower() /
        // 1e6 + " MW");
        assertEquals(compressor1.getPower() / 1e6, 4.655081035416562,
                "Test case for rigorous polytropic compressor calculation should return approximate 4.66 MW");
    }

    /**
     * <p>
     * testIsentropicCalcMethod.
     * </p>
     */
    @Test
    public void testIsentropicCalcMethod() {
        compressor1.setIsentropicEfficiency(polytropicEff);
        compressor1.setUsePolytropicCalc(false);
        processOps.run();
        // System.out.println("compressor power " + compressor1.getPower() / 1e6 + "
        // MW");
        assertEquals(compressor1.getPower() / 1e6, 4.5621157449685);
    }

    /**
     * <p>
     * testCompressorWithSrk.
     * </p>
     */
    @Test
    public void testCompressorWithSrk() {
        Stream inletStream = new Stream(testSystem);
        inletStream.setPressure(pressure_inlet, "bara");
        inletStream.setTemperature(temperature_inlet, "C");
        inletStream.setFlowRate(gasFlowRate, "MSm3/day");
        inletStream.run();
        neqsim.processSimulation.processEquipment.compressor.Compressor compressor1 = new neqsim.processSimulation.processEquipment.compressor.Compressor(
                "Compressor1",
                inletStream);
        compressor1.setUsePolytropicCalc(true);
        compressor1.setOutletPressure(pressure_Out);
        compressor1.setOutTemperature(358.0);
        compressor1.run();
        // System.out.println("srk polytropic efficiency " +
        // compressor1.getPolytropicEfficiency()*100 + " poly eff");
        // System.out.println("srk fluid head " + compressor1.getPolytropicFluidHead() +
        // " kJ/kg");
        // System.out.println("srk power " + compressor1.getPower() + " W");
        assertEquals(compressor1.getPolytropicEfficiency() * 100, 88.94871563458828,
                "Test case for rigorous polytropic efficiency with SRK calculation should return approximate 88.948715 ");
    }

    /**
     * <p>
     * testCompressorWithGERG2008.
     * </p>
     */
    @Test
    public void testCompressorWithGERG2008() {
        Stream inletStream = new Stream(testSystem);
        inletStream.setPressure(pressure_inlet, "bara");
        inletStream.setTemperature(temperature_inlet, "C");
        inletStream.setFlowRate(gasFlowRate, "MSm3/day");
        inletStream.run();
        neqsim.processSimulation.processEquipment.compressor.Compressor compressor1 = new neqsim.processSimulation.processEquipment.compressor.Compressor(
                "Compressor1",
                inletStream);
        compressor1.setUsePolytropicCalc(true);
        compressor1.setOutletPressure(pressure_Out);
        compressor1.setOutTemperature(358.0);
        compressor1.setUseGERG2008(true);
        // compressor1.setUseRigorousPolytropicMethod(true);
        compressor1.run();
        // System.out.println("gerg polytropic efficiency " +
        // compressor1.getPolytropicEfficiency()*100 + " poly eff");
        // System.out.println("gerg fluid head " + compressor1.getPolytropicFluidHead()
        // + " kJ/kg");
        // System.out.println("gerg power " + compressor1.getPower() + " W");
        assertEquals(compressor1.getPolytropicEfficiency() * 100, 89.99367027631443,
                "Test case for rigorous polytropic efficiency with GER2008 calculation should return approximate 89.992296751");
    }
}
