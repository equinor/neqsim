package neqsim.processSimulation.measurementDevice.simpleFlowRegime;
import java.util.Arrays;
import java.util.Collections;
import neqsim.processSimulation.measurementDevice.MeasurementDeviceBaseClass;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class SevereSlugAnalyser extends MeasurementDeviceBaseClass {
    FluidSevereSlug fluidSevereS;
    Pipe pipe;
    SevereSlugAnalyser severeSlug;
    Stream streamS;


    final double gravAcc = 9.81;
    
    //Severe slug problem
    private double simulationTime = 200;
    private double usl = 3.0;
    private double usg = 0.5;
    private double outletPressure = 100000.0;
    private double temperature = 20.0;
    private int numberOfTimeSteps = 20000;
    private double internalDiameter = 0.0;
    private double leftLength = 0.0;
    private double rightLength = 0.0;
    private double angle = 0.0;
    
    // These variables should not be changed by the user I guess. 
    // But this can be done if the user has advanced knowledge about the problem. 
    double alfaRiser = 0.0; // gas fraction in the riser 
    double z = 0.0001; // some initial value to start the calculation
    double lambdaStagnant = 0.0;
    double uLevel = 0.0;// some initial value to start the calculation
    double valveConstant = 0.0;
    double normalPressure = 100000.0;
    final double pi = 3.1415926; 

    double[] resPres;
    double[] resTime;
    double[] resLiqHoldUpRiser;
    double[] resLiqHeight;
    double[] resMixVelocity; 
    double[] usgMap;
    double[] uslMap;
    double slugValue;
    
    //Simulation variables (calculated variables)
    double deltaT;
    double driftVel;
    double flowDistCoeff;
    double mixDensity;
    double pressure;
    double slugLength;
    double transVel;
    double Um; 
    double UmOld;
    double UsgL;
    double UslL;
    double UsgR;
    double UslR;
    double U;
    double Re;
    double lambda;
    double friction;
    double frictionStagnant;
    double frictionValve;
    double frictionTot;
    double gravL;
    double gravR;
    double gravity;
    double alfaRiserOld;
    double zOld;
    double Lg;
    double pressureOld;
    double alfaLeft;
    double gasDensity;
    double n;
    double gamma1;
    double gamma2;
    double gamma;
    double holdUp1;
    double holdUp2;
    double holdUp;
    double function2;
    double function1;

    
    double iter;

    String flowPattern;

    // This constructor is used for the "default" values
    SevereSlugAnalyser(){
      this.setSuperficialGasVelocity(usl);
      this.setSuperficialGasVelocity(usg);
      this.setOutletPressure(outletPressure);
      this.setTemperature(temperature);
      this.setSimulationTime(simulationTime);
      this.setNumberOfTimeSteps(numberOfTimeSteps);
            
    }
    
    // This constructor is used for the user input of superficial liquid and gas velocities,
    // and the rest will be the default values
    SevereSlugAnalyser(double usl, double usg){
      this.setSuperficialLiquidVelocity(usl);
      this.setSuperficialGasVelocity(usg);
      this.setOutletPressure(outletPressure);
      this.setTemperature(temperature);
      this.setSimulationTime(simulationTime);
      this.setNumberOfTimeSteps(numberOfTimeSteps);      
    }
    
    // This constructor is used for the user input of superficial liquid and gas velocities, outletPressure,
    // temperature, simulationTime, numberOfTimeSteps
    // and the rest will be the default values
    SevereSlugAnalyser(double usl, double usg, double outletPressure, double temperature, double simulationTime, int numberOfTimeSteps){
      this.setSuperficialLiquidVelocity(usl);
      this.setSuperficialGasVelocity(usg);
      this.setOutletPressure(outletPressure);
      this.setTemperature(temperature);
      this.setSimulationTime(simulationTime);
      this.setNumberOfTimeSteps(numberOfTimeSteps);      
    }

    SevereSlugAnalyser(SystemInterface fluid, Pipe pipe, double outletPressure, double temperature, double simulationTime, int numberOfTimeSteps){
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.TPflash();
      fluid.initProperties();
      if (fluid.getNumberOfPhases() == 2){
        usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea();
      }
      else{
        usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea() + fluid.getPhase(2).getFlowRate("m3/sec") / pipe.getArea();
      }
      this.setSuperficialLiquidVelocity(usl);
      usg = fluid.getPhase(0).getFlowRate("m3/sec") / pipe.getArea();
      this.setSuperficialGasVelocity(usg);
      this.setOutletPressure(outletPressure);
      this.setTemperature(temperature);
      this.setSimulationTime(simulationTime);
      this.setNumberOfTimeSteps(numberOfTimeSteps);      
    }

    SevereSlugAnalyser(Stream stream, double internalDiameter, double leftLength, double rightLength,  double angle, double outletPressure, double temperature, double simulationTime, int numberOfTimeSteps){
      pipe = new Pipe(internalDiameter, leftLength, rightLength, angle);
      streamS = stream;
      SystemInterface fluid = stream.getThermoSystem();
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.TPflash();
      fluid.initProperties();
      if (fluid.getNumberOfPhases() == 2){
        usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea();
      }
      else{
        usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea() + fluid.getPhase(2).getFlowRate("m3/sec") / pipe.getArea();
      }
      fluidSevereS = new FluidSevereSlug(fluid);
      usg = fluid.getPhase(0).getFlowRate("m3/sec") / pipe.getArea();
      severeSlug = new SevereSlugAnalyser(usl, usg,outletPressure, temperature, simulationTime, numberOfTimeSteps);
    }

    SevereSlugAnalyser(double outletPressure, double temperature, double simulationTime, int numberOfTimeSteps){
      this.setSuperficialLiquidVelocity(usl);
      this.setSuperficialGasVelocity(usg);
      this.setOutletPressure(outletPressure);
      this.setTemperature(temperature);
      this.setSimulationTime(simulationTime);
      this.setNumberOfTimeSteps(numberOfTimeSteps);      
    }


    // Encapsulation
    // 1. Superficial Liquid Velocity Encapsulation
    public void setSuperficialLiquidVelocity(double usl) {
      this.usl = usl;
    }
    
    public double getSuperficialLiquidVelocity() {
      return usl;
    }
    
    // 2. Superficial Gas Velocity Encapsulation
    public void setSuperficialGasVelocity(double usg) {
      this.usg = usg;
    }
    
    public double getSuperficialGasVelocity() {
      return usg;
    }
    
    public String getFlowPattern() {
      return flowPattern;
    }

    public double getSlugValue() {
      return slugValue;
    }

    // 3. Pipe Outlet Pressure Encapsulation
    public void setOutletPressure(double outletPressure) {
      this.outletPressure = outletPressure;
    }
    
    public double getOutletPressure() {
      return outletPressure;
    }
    
    // 4. Temperature Encapsulation
    public void setTemperature(double temperature) {
      this.temperature = temperature;
    }
    
    public double getTemperature() {
      return temperature;
    }
    
    // 5. Number of Time Steps Encapsulation
    public void setNumberOfTimeSteps(int numberOfTimeSteps) {
      this.numberOfTimeSteps = numberOfTimeSteps;
    }
    
    public int getNumberOfTimeSteps() {
      return numberOfTimeSteps;
    }
    
    // 6. Simulation Time Encapsulation
    public void setSimulationTime(double simulationTime) {
      this.simulationTime = simulationTime;
    }
    
    public double getSimulationTime() {
      return simulationTime;
    }
    
    // Method 1: Calculating the universal gas constant
     public double gasConst(FluidSevereSlug fluid) {    
        return 8.314 / fluid.getMolecularWeight() * (273.15 + temperature);    
      }
    
    //Declare the variables for resuts after creating an object Severe slug with required number of steps.
    
    public double slugHoldUp (Pipe pipe, SevereSlugAnalyser severeSlug){
      double Udrift;
      double C0 = 1.2;
      double Umix;

      Umix = severeSlug.getSuperficialGasVelocity() + severeSlug.getSuperficialLiquidVelocity();
      Udrift = Math.sqrt(gravAcc*pipe.getInternalDiameter());
      holdUp = 1 - severeSlug.getSuperficialGasVelocity()/(C0 * Umix + Udrift);
      return holdUp;
  }  

  public double stratifiedHoldUp (FluidSevereSlug fluid, Pipe pipe, SevereSlugAnalyser severeSlug){
    Re = fluid.getLiqDensity()*severeSlug.getSuperficialLiquidVelocity()*pipe.getInternalDiameter()/(fluid.getliqVisc());
    lambda = Math.max(0.34 * Math.pow(Re, -0.25), 64 / Re);
    if (0.34 * Math.pow(Re, -0.25) > 64 / Re){
      n = 0.25;
    } 
    else{
     n = 1;
    }
    friction=0.5*lambda*Math.pow(severeSlug.getSuperficialLiquidVelocity(),2)/(gravAcc*Math.sin(pipe.getAngle("Radian"))*pipe.getInternalDiameter());

    gamma1 = 0.1;
    gamma2 = 2.2;
    iter = 0;
    while(Math.abs(gamma2 - gamma1) > 1e-5 && iter < 200){

      holdUp2 = (gamma2 - 0.5*Math.sin(2*gamma2))/(pi);
      function2 = Math.pow(holdUp2,3)*Math.pow((pi/gamma2),(n+1))-friction;

      holdUp1 = (gamma1 - 0.5*Math.sin(2*gamma1))/(pi);
      function1 = Math.pow(holdUp1,3)*Math.pow((pi/gamma1),(n+1))-friction;

      gamma = gamma2 - function2*(gamma2 - gamma1)/(function2 - function1);
      if (gamma < 0){
        if (gamma2 != 0.1)
        {
          gamma = 0.1;
        }
        else{
          gamma = 0.2;
        }
      }
      if (gamma > 3.00){
        if (gamma2 != 2.99)
        {
          gamma = 2.99;
        }
        else{
          gamma = 2.97;
        }
      }
      
      gamma1 = gamma2;
      gamma2 = gamma;
      iter = iter + 1;
    }

    if (iter == 199){
      System.out.println("Could not find solution for stratified flow holdup");
    }
    else{
      holdUp = (gamma - 0.5*Math.sin(2*gamma))/(pi);

    }
    return holdUp;
}
    
    // Passing 3 objects as input parameters (fluid, pipe, severeSlug)
    public void runSevereSlug(FluidSevereSlug fluid, Pipe pipe, SevereSlugAnalyser severeSlug){

      resPres = new double[severeSlug.getNumberOfTimeSteps()]; 
      resTime = new double[severeSlug.getNumberOfTimeSteps()];
      resLiqHoldUpRiser = new double[severeSlug.getNumberOfTimeSteps()];
      resLiqHeight = new double[severeSlug.getNumberOfTimeSteps()];
      resMixVelocity = new double[severeSlug.getNumberOfTimeSteps()]; 

      deltaT = 0.001; //severeSlug.getSimulationTime() / severeSlug.getNumberOfTimeSteps();
      mixDensity = fluid.getLiqDensity();
      pressure = severeSlug.getOutletPressure() + mixDensity * severeSlug.gravAcc * pipe.getRightLength(); // Initial condition
      Um = severeSlug.getSuperficialGasVelocity();
      holdUp = severeSlug.stratifiedHoldUp(fluid, pipe, severeSlug);
      driftVel = 0.35 * Math.sqrt(gravAcc * pipe.getInternalDiameter()); // Drift velocity for the vertical flows
      alfaLeft = 1 - holdUp;

      for(int i = 0; i < severeSlug.numberOfTimeSteps; i++)
      
       {  
          slugLength = -z  + pipe.getRightLength() * (1 - alfaRiser); //Slug Length          
          Re = fluid.getLiqDensity() * Math.abs(Um) * pipe.getInternalDiameter() / fluid.getliqVisc(); // Reynolds number
          lambda = Math.max(0.34 * Math.pow(Re, -0.25), 64 / Re); // friction factor
          friction = 0.5 * lambda * fluid.getLiqDensity()* Um * Math.abs(Um) * slugLength / pipe.getInternalDiameter(); // frictional pressure loss
          //Oscillation Friction
          frictionStagnant = 0.5 * lambdaStagnant * fluid.getLiqDensity() * uLevel * Math.abs(uLevel) * slugLength  / pipe.getInternalDiameter();
          //Valve Friction
          frictionValve = valveConstant * fluid.getLiqDensity() * Um * Math.abs(Um);
          //Total Friction
          friction = friction + frictionStagnant + frictionValve;
          // Gravity
          gravL = -fluid.getLiqDensity() *  Math.abs(z) * gravAcc * Math.sin((pipe.getAngle("Radian")));
          gravR = mixDensity * gravAcc * pipe.getRightLength();
          gravity = gravL + gravR;

          // Momentum Balance
          UmOld = Um;
          Um = UmOld + deltaT * ((pressure - severeSlug.outletPressure) - friction - gravity)
                    / (-z * fluid.getLiqDensity() + pipe.getRightLength() * mixDensity);

          // Slip Relation: Calculate translational velocity
          if (Re < 2300) {
            flowDistCoeff = 2;
          } else {
            flowDistCoeff = 1.2;
          } 

          transVel = flowDistCoeff * Um + driftVel;
          // State Equation
          // All cases, Case 1: Open Bend; Case 2: Blocked Bend; Case 3: Backflow
          UsgL = (Um - severeSlug.getSuperficialLiquidVelocity()) *  ((Um > 0) ? 1 : 0) * ((z > 0) ? 1 : 0);

          UslL =  ( severeSlug.getSuperficialLiquidVelocity() * ((z > 0) ? 1 : 0) + Um * ((z < 0) ? 1 : 0) ) *  ((Um > 0) ? 1 : 0) + 
                           Um* ((Um < 0) ? 1 : 0);

          UsgR = (alfaRiser * transVel * ((z > 0) ? 1 : 0) + Um * ((z < 0) ? 1 : 0)) * ((Um > 0) ? 1 : 0) + Um * ((Um < 0) ? 1 : 0);
          UslR = (Um - alfaRiser * transVel) * ((Um > 0) ? 1 : 0) * ((z > 0) ? 1 : 0);

          U = UsgL - UsgR - UslL + UslR; 
          
          // Riser vapour fraction
          alfaRiserOld = alfaRiser;
          alfaRiser = alfaRiserOld + 0.5 * deltaT * U / (pipe.getRightLength());
          alfaRiser = Math.max(0.0, alfaRiser);
          alfaRiser = Math.min(1.0, alfaRiser);

          // Level
          uLevel = (-severeSlug.getSuperficialLiquidVelocity() + Um); 
          zOld = z;
          z = zOld + deltaT * uLevel;
          z = ((z < 0) ? 1 : 0) * z + ((z > 0) ? 1 : 0) * 0.0001;
          uLevel = uLevel * ((z < 0) ? 1 : 0);

          Lg = pipe.getLeftLength() + z;
          pressureOld = pressure;
          pressure = pressureOld + deltaT * (severeSlug.getSuperficialGasVelocity() * normalPressure - UsgL * pressureOld) / (Lg*alfaLeft) - deltaT *(pressureOld * uLevel) / (Lg*alfaLeft);

          gasDensity = pressure / (fluid.getGasConstant()*(273.15 + severeSlug.getTemperature()));
          mixDensity = alfaRiser * gasDensity + (1 - alfaRiser) * fluid.getLiqDensity();

          resPres[i] = pressure/100000; 
          resTime[i] = i*deltaT; 
          resLiqHoldUpRiser[i] = (1-alfaRiser);
          resLiqHeight[i] = z;
          resMixVelocity[i] = Um; 
          
        }
    }

    public String checkFlowRegime(FluidSevereSlug fluid, Pipe pipe, SevereSlugAnalyser severeSlug){
      Double[] halfRes =new Double[severeSlug.getNumberOfTimeSteps()/2];
      severeSlug.runSevereSlug(fluid, pipe, severeSlug);
      double sum = 0;
      for (int i = severeSlug.numberOfTimeSteps / 2; i < severeSlug.numberOfTimeSteps; i++) {
        sum = sum + severeSlug.resPres[i];
        halfRes[i - severeSlug.numberOfTimeSteps / 2] = severeSlug.resPres[i];
      }
      double meanValue =  sum / ((double) numberOfTimeSteps/2);
      double max = Collections.max(Arrays.asList(halfRes));
      slugValue = (max/meanValue) - 1;
      double stratifiedHoldUp = stratifiedHoldUp (fluid, pipe, severeSlug);
      System.out.println(stratifiedHoldUp);

      double slugHoldUp = slugHoldUp(pipe,severeSlug);
      System.out.println(slugHoldUp);
      System.out.println("The severe slug value is " + slugValue);
      if (slugValue > 0.1 && slugHoldUp > stratifiedHoldUp){
        flowPattern = "Severe Slug";
      }
      else if(slugValue > 0.05 && slugHoldUp > stratifiedHoldUp){
        flowPattern = "Severe Slug 2. Small pressure variations";
      }
      else{
        if (slugHoldUp<stratifiedHoldUp){
          flowPattern = "Slug Flow";
        }
        else{
          if(stratifiedHoldUp < 0.1)
          {
            flowPattern = "Liquid droplets flow";
          }
          if(stratifiedHoldUp > 0.9)
          {
            flowPattern = "Gas droplets flow";
          }
          if(stratifiedHoldUp > 0.1 && stratifiedHoldUp < 0.9) {
            flowPattern = "Stratified Flow";
          }
        }
      }
      System.out.println("Simulated flow regime is then: " + flowPattern);
      return flowPattern;
    }

    public double getMeasuredValue() {
      SystemInterface fluid = streamS.getThermoSystem();
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.TPflash();
      fluid.initProperties();
      if (fluid.getNumberOfPhases() == 2){
        usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea();
      }
      else{
        usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea() + fluid.getPhase(2).getFlowRate("m3/sec") / pipe.getArea();
      }
      fluidSevereS = new FluidSevereSlug(fluid);
      usg = fluid.getPhase(0).getFlowRate("m3/sec") / pipe.getArea();
      severeSlug = new SevereSlugAnalyser(usl, usg,outletPressure, temperature, simulationTime, numberOfTimeSteps);
      checkFlowRegime(fluidSevereS, pipe, severeSlug);
      return slugValue;
    }

    public String getPredictedFlowRegime() {
      SystemInterface fluid = streamS.getThermoSystem();
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.TPflash();
      fluid.initProperties();
      if (fluid.getNumberOfPhases() == 2){
        usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea();
      }
      else{
        usl = fluid.getPhase(1).getFlowRate("m3/sec") / pipe.getArea() + fluid.getPhase(2).getFlowRate("m3/sec") / pipe.getArea();
      }
      fluidSevereS = new FluidSevereSlug(fluid);
      usg = fluid.getPhase(0).getFlowRate("m3/sec") / pipe.getArea();
      severeSlug = new SevereSlugAnalyser(usl, usg,outletPressure, temperature, simulationTime, numberOfTimeSteps);
      checkFlowRegime(fluidSevereS, pipe, severeSlug);
      return flowPattern;
    }

    public double getMeasuredValue(FluidSevereSlug fluid, Pipe pipe, SevereSlugAnalyser severeSlug) {
      checkFlowRegime(fluid, pipe, severeSlug);
      return slugValue;
    }

    public String getPredictedFlowRegime(FluidSevereSlug fluid, Pipe pipe, SevereSlugAnalyser severeSlug) {
      checkFlowRegime(fluid, pipe, severeSlug);
      return flowPattern;
    }

    public static void main(String args[]){
      neqsim.thermo.system.SystemInterface testSystem =
      new neqsim.thermo.system.SystemSrkEos((273.15 + 15.0), 10);
      testSystem.addComponent("methane", 0.015, "MSm^3/day");
      testSystem.addComponent("n-heptane", 0.0055, "MSm^3/day");
      testSystem.setMixingRule(2);
      testSystem.init(0);
      Stream inputStream = new Stream(testSystem);
      SevereSlugAnalyser mySevereSlug4= new SevereSlugAnalyser (inputStream, 0.05, 167, 7.7, 2,100000.0,20.0, 200.0,20000);
      System.out.println(inputStream.getFlowRate("kg/sec"));
      mySevereSlug4.getPredictedFlowRegime();
      // inputStream.setFlowRate(0.00001, "MSm^3/day");
      // System.out.println(inputStream.getFlowRate("kg/sec"));
      // mySevereSlug4.getPredictedFlowRegime();
      
    }


// To be implemented
    // public void buildFlowMap(double ugmax, double ulmax, int numberOfStepsMap, FluidSevereSlug fluid, Pipe pipe, SevereSlug severeSlug){
    //   String stability1;
    //   String stability2;
    //   for (double usl = 0.01; usl < ulmax; usl = usl + ulmax/numberOfStepsMap) { 
    //     severeSlug.setSuperficialLiquidVelocity(usl);
    //     double usg1 = 0.01;
    //     double usg2 = ugmax;
    //     double usg_sol;
    //     iter = 0;


    //     while(Math.abs(usg1 - usg2) > 1e-5 && iter < 200){

    //       severeSlug.setSuperficialGasVelocity(usg1);
    //       severeSlug.checkFlowRegime(fluid, pipe, severeSlug);
    //       function1 = severeSlug.slugValue - 0.05;

    //       severeSlug.setSuperficialGasVelocity(usg2);
    //       checkFlowRegime(fluid, pipe, severeSlug);
    //       function2 = severeSlug.slugValue - 0.05;
    
    //       usg_sol = usg2 - function2*(usg2 - usg1)/(function2 - function1);

    //       if (usg_sol < 0){
    //         if (usg2 != 0.01)
    //         {
    //           usg2 = 0.01;
    //         }
    //         else{
    //           usg2 = ugmax/2;
    //         }
    //       }
    //       if (usg_sol > ugmax){
    //         if (usg_sol != ugmax-0.01)
    //         {
    //           usg2 = ugmax-0.01;
    //         }
    //         else{
    //           usg2 = 0.02;
    //         }
    //       }
          
    //       usg1 = usg2;
    //       usg2 = usg;
    //       iter = iter + 1;
    //     }
    
    //     if (iter == 199){
    //       System.out.println("Could not find the border");
    //     }
    //     else{
          
    //     }

    //     severeSlug.setSuperficialLiquidVelocity(usl);
    //     severeSlug.setSuperficialGasVelocity(0.01);
    //     String flowPattern1 = severeSlug.checkFlowRegime(fluid, pipe, severeSlug);
    //     severeSlug.setSuperficialGasVelocity(ugmax);
    //     String flowPattern2 = severeSlug.checkFlowRegime(fluid, pipe, severeSlug);
    //     if (flowPattern1 == "Severe Slug" || flowPattern1 == "Severe Slug 2. Small pressure variations"){
    //       stability1 = "Not stable";
    //     }
    //     else{
    //       stability1 = "Stable";
    //     }
    //     if (flowPattern2 == "Severe Slug" || flowPattern2 == "Severe Slug 2. Small pressure variations"){
    //       stability2 = "Not stable";
    //     }
    //     else{
    //       stability2 = "Stable";
    //     }
    //     if (flowPattern1 != flowPattern2){
          
    //     }

    //}

    //}


  }
