package neqsim.processSimulation.measurementDevice.simpleFlowRegime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.stream.Stream;

public class SevereSlugTest {
  @Test
  void testCheckFlowRegime1() {
    // neqsim.thermo.system.SystemInterface testSystem =
    // new neqsim.thermo.system.SystemSrkEos((273.15 + 15.0), 10);
    // testSystem.addComponent("methane", 0.015, "MSm^3/day");
    // testSystem.addComponent("n-heptane", 0.0055, "MSm^3/day");
    // testSystem.setMixingRule(2);
    // testSystem.init(0);

    // //Test 1
    // FluidSevereSlug myFluid = new FluidSevereSlug(testSystem);
    // Pipe myPipe = new Pipe("SevereSlugPipe", 0.05, 167, 7.7, 2);
    // SevereSlugAnalyser mySevereSlug = new SevereSlugAnalyser(testSystem, myPipe, 100000.0, 20.0,
    // 200.0, 200000);
    // String flowPattern = mySevereSlug.getPredictedFlowRegime(myFluid,myPipe,mySevereSlug);
    // double slugValue = mySevereSlug.getMeasuredValue(myFluid,myPipe,mySevereSlug);
    // assertEquals(flowPattern, "Stratified Flow", "");
    // //assertEquals(slugValue, 1.8880452756775412E-12, "");

    // //Test 2
    // FluidSevereSlug myFluid2 = new FluidSevereSlug(1000.0, 0.001, 0.029);
    // Pipe myPipe2 = new Pipe("SevereSlugPipe", 0.05, 167, 7.7, 2);
    // SevereSlugAnalyser mySevereSlug2= new SevereSlugAnalyser(0.5, 2, 100000, 20.0, 200, 200000);
    // String flowPattern2 = mySevereSlug2.getPredictedFlowRegime(myFluid2,myPipe2,mySevereSlug2);
    // double slugValue2 = mySevereSlug2.getMeasuredValue(myFluid2,myPipe2,mySevereSlug2);
    // assertEquals(flowPattern2, "Severe Slug", "");
    // // assertEquals(slugValue2, 0.15768123476467366, "");

    // //Test 3
    // FluidSevereSlug myFluid3 = new FluidSevereSlug(1000.0, 0.001, 0.029);
    // Pipe myPipe3 = new Pipe("SevereSlugPipe", 0.05, 167, 7.7, 2);
    // SevereSlugAnalyser mySevereSlug3= new SevereSlugAnalyser(0.5, 5, 100000, 20.0, 200, 200000);
    // String flowPattern3 = mySevereSlug.getPredictedFlowRegime(myFluid3,myPipe3,mySevereSlug3);
    // double slugValue3 = mySevereSlug.getMeasuredValue(myFluid3,myPipe3,mySevereSlug3);
    // assertEquals(flowPattern3, "Slug Flow", "");
    // //assertEquals(slugValue3, 3.959782115536825E-7, "");

    // //Test 4
    // Stream inputStream = new Stream(testSystem);
    // SevereSlugAnalyser mySevereSlug4= new SevereSlugAnalyser (inputStream, 0.05, 167, 7.7,
    // 2,100000.0,20.0, 200.0,20000);
    // assertEquals(mySevereSlug4.getPredictedFlowRegime(), "Stratified Flow", "");
    // //assertEquals(mySevereSlug4.getMeasuredValue(), 5.020564803390748E-4, "");

    // //Test 5
    // inputStream.setFlowRate(0.00001, "MSm^3/day");
    // mySevereSlug4.getPredictedFlowRegime();
    // assertEquals(mySevereSlug4.getPredictedFlowRegime(), "Severe Slug", "");
    // //assertEquals(mySevereSlug4.getMeasuredValue(), 0.11813274762542325, "");

    // Test 6
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 15.0), 10);
    testSystem2.addComponent("methane", 0.00015, "MSm^3/day");
    testSystem2.addComponent("n-heptane", 0.0015, "MSm^3/day");
    testSystem2.addComponent("propane", 0.00015, "MSm^3/day");
    testSystem2.addComponent("water", 0.00015, "MSm^3/day");
    testSystem2.setMixingRule(2);
    testSystem2.setMultiPhaseCheck(true);;
    testSystem2.init(0);

    // Stream inputStream2 = new Stream(testSystem2);
    // SevereSlugAnalyser mySevereSlug5= new SevereSlugAnalyser (inputStream2, 0.05, 167, 7.7,
    // 2,100000.0,20.0, 200.0,20000);
    // assertEquals(mySevereSlug5.getPredictedFlowRegime(), "Severe Slug", "");
    // //assertEquals(mySevereSlug5.getMeasuredValue(), 0.23534014090391553, "");

    Stream inputStream3 = new Stream(testSystem2);
    SevereSlugAnalyser mySevereSlug6 = new SevereSlugAnalyser(inputStream3, 0.05, 167, 7.7, 0.1);
    assertEquals(mySevereSlug6.getPredictedFlowRegime(), "Severe Slug", "");
    assertEquals(0.19085996383839476, mySevereSlug6.getMeasuredValue(), "");
  }

}

