package neqsim.processSimulation.measurementDevice.simpleFlowRegime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class SevereSlugTest {
  @Test
  void testCheckFlowRegime1() {

      // FluidSevereSlug myFluid = new FluidSevereSlug(1000.0, 0.001, 0.029); 
      // Pipe myPipe = new Pipe("SevereSlugPipe", 0.05, 167, 7.7, 2);
      // SevereSlug mySevereSlug = new SevereSlug(0.5, 2, 100000, 20.0, 200, 200000); 
      // mySevereSlug.checkFlowRegime(myFluid, myPipe, mySevereSlug);

      neqsim.thermo.system.SystemInterface testSystem =
      new neqsim.thermo.system.SystemSrkEos((273.15 + 15.0), 10);
      testSystem.addComponent("methane", 0.015, "MSm^3/day");
      testSystem.addComponent("n-heptane", 0.0055, "MSm^3/day");
      testSystem.setMixingRule(2);
      testSystem.init(0);

      FluidSevereSlug myFluid = new FluidSevereSlug(testSystem); 
      Pipe myPipe = new Pipe("SevereSlugPipe", 0.05, 167, 7.7, 2);
      SevereSlug mySevereSlug = new SevereSlug(testSystem, myPipe, 100000.0, 20.0, 200.0, 200000); 
      String flowPattern = mySevereSlug.checkFlowRegime(myFluid, myPipe, mySevereSlug);
      double slugValue = mySevereSlug.slugValue;
      assertEquals(flowPattern, "Severe Slug", "");
      assertEquals(slugValue, 0.27098219114911415, "");

      FluidSevereSlug myFluid2 = new FluidSevereSlug(1000.0, 0.001, 0.029); 
      Pipe myPipe2 = new Pipe("SevereSlugPipe", 0.05, 167, 7.7, 2);
      SevereSlug mySevereSlug2= new SevereSlug(0.5, 2, 100000, 20.0, 200, 200000); 
      String flowPattern2 = mySevereSlug.checkFlowRegime(myFluid2, myPipe2, mySevereSlug2);
      double slugValue2 = mySevereSlug.slugValue;
      assertEquals(flowPattern2, "Severe Slug", "");
      assertEquals(slugValue2, 0.15287553937573484, "");

      FluidSevereSlug myFluid3 = new FluidSevereSlug(1000.0, 0.001, 0.029); 
      Pipe myPipe3 = new Pipe("SevereSlugPipe", 0.05, 167, 7.7, 2);
      SevereSlug mySevereSlug3= new SevereSlug(0.5, 5, 100000, 20.0, 200, 200000); 
      String flowPattern3 = mySevereSlug.checkFlowRegime(myFluid3, myPipe3, mySevereSlug3);
      double slugValue3 = mySevereSlug.slugValue;
      assertEquals(flowPattern3, "Slug Flow", "");
      assertEquals(slugValue3, 3.940225590248758E-7, "");


  }
    
}

