package neqsim.processSimulation.processSystem;

import java.util.ArrayList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.separator.Separator;

public class ProcessSystemTest {
    ProcessSystem p;
    String _name = "TestProcess";


    @BeforeEach
    public void setUp() {
        p = new ProcessSystem();
        p.setName(_name);
    }

    @Test
    void testGetName() {
        Assertions.assertEquals(_name, p.getName());
    }

    @Test
    public void testSetTimeStep() {
        double timeStep = p.getTimeStep() * 2;
        Assertions.assertEquals(timeStep / 2, p.getTimeStep());
        Assertions.assertNotEquals(timeStep, p.getTimeStep());

        p.setTimeStep(timeStep);
        Assertions.assertEquals(timeStep, p.getTimeStep());
        Assertions.assertNotEquals(timeStep / 2, p.getTimeStep());
    }

    @Test
    public void testHasUnitName() {
        String sepName = "TestSep";
        Assertions.assertFalse(p.hasUnitName(sepName));
        p.add(new Separator(sepName));
        Assertions.assertTrue(p.hasUnitName(sepName));
    }

    @Test
    void testAdd() {
        String sepName = "TestSep";
        Separator sep = new Separator();
        sep.setName(sepName);
        p.add(sep);

        ArrayList<ProcessEquipmentInterface> list = p.getUnitOperations();

        Assertions.assertTrue(sep == p.getUnit(sepName));

        Assertions.assertEquals(1, list.size());
        Assertions.assertEquals(1, p.size());

        Assertions.assertTrue((Separator) list.get(0) == sep);

        p.removeUnit(sepName);
        Assertions.assertNull(p.getUnit(sepName));
        Assertions.assertEquals(0, p.size());

        list = p.getUnitOperations();

        Assertions.assertEquals(0, list.size());

        p.add(sep);
        Assertions.assertEquals(1, p.size());

        p.clear();
        Assertions.assertEquals(0, p.size());

        p.add(sep);
        Assertions.assertEquals(1, p.size());

        p.clearAll();
        Assertions.assertEquals(0, p.size());
    }

    @Test
    public void testAddUnitTwice() {
        Separator sep = new Separator();
        p.add(sep);
        p.add(sep); // Won't add the copy
        Assertions.assertEquals(1, p.size());
    }

    @Test
    public void testRemoveUnit() {
        Separator sep = new Separator();
        p.add(sep);
        Assertions.assertEquals(1, p.size());
        p.removeUnit("");
        Assertions.assertEquals(0, p.size());
    }


    @Test
    public void testAddUnitsWithNoName() {
        Separator sep = new Separator();
        p.add(sep);
        sep = new Separator();
        p.add(sep);
        Assertions.assertEquals(2, p.size());
        p.removeUnit("Separator2");
        Assertions.assertEquals(1, p.size());
        p.removeUnit("");
        Assertions.assertEquals(0, p.size());
    }

    @Test
    public void testGetUnitNumber() {
        Separator sep = new Separator();
        p.add(sep);
        Separator sep2 = new Separator();
        p.add(sep2);

        Assertions.assertEquals(0, p.getUnitNumber(""));
        Assertions.assertEquals(1, p.getUnitNumber("Separator2"));

        p.removeUnit("");
        p.add(sep);

        Assertions.assertEquals(0, p.getUnitNumber("Separator2"));
        Assertions.assertEquals(1, p.getUnitNumber(""));
    }

    @Test
    public void testSetSurroundingTemperature() {
        double temp = 200;
        p.setSurroundingTemperature(temp);
        Assertions.assertEquals(temp, p.getSurroundingTemperature());
    }

    @Test
    void testClear() {
        p.clear();
    }

    @Test
    void testClearAll() {
        p.clearAll();
    }

    @Test
    void testCopy() {
        ProcessSystem sys2 = p.copy();
        Assertions.assertTrue(p.equals(sys2));
        Assertions.assertEquals(p, sys2);
    }

    @Test
    void testDisplayResult() {}

    @Test
    void testGetAllUnitNames() {

    }

    @Test
    void testGetConditionMonitor() {

    }

    @Test
    void testGetCoolerDuty() {

    }

    @Test
    void testGetCostEstimator() {

    }

    @Test
    void testGetEntropyProduction() {

    }

    @Test
    void testGetExergyChange() {

    }

    @Test
    void testGetHeaterDuty() {

    }

    @Test
    void testGetMeasurementDevice() {

    }

    @Test
    void testGetMechanicalWeight() {

    }



    @Test
    void testGetPower() {

    }

    @Test
    void testGetSurroundingTemperature() {

    }

    @Test
    void testGetSystemMechanicalDesign() {

    }

    @Test
    void testGetUnit() {

    }


    @Test
    void testGetUnitOperations() {

    }


    @Test
    void testOpen() {

    }

    @Test
    void testPrintLogFile() {

    }


    @Test
    void testReplaceObject() {

    }

    @Test
    void testReportMeasuredValues() {

    }

    @Test
    void testReportResults() {

    }

    @Test
    void testRun() {

    }

    @Test
    void testRunAsThread() {

    }

    @Test
    void testSave() {

    }

    @Test
    void testSetFluid() {

    }

    @Test
    void testSetName() {}

    @Test
    void testSetSystemMechanicalDesign() {

    }

    @Test
    void testSize() {

    }

    @Test
    void testView() {}
}
