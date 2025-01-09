package neqsim.thermodynamicoperations;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import neqsim.api.ioc.CalculationResult;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemProperties;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations.FlashType;

public class ThermodynamicOperationsTest extends neqsim.NeqSimTest {
  @Test
  void testFlash() {
    SystemInterface thermoSystem = new neqsim.thermo.system.SystemSrkEos(280.0, 10.0);
    thermoSystem.addComponent("methane", 0.7);
    thermoSystem.addComponent("ethane", 0.3);
    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);

    double P = 10;
    double T = 20;
    String unitP = "bara";
    String unitT = "C";

    ops.flash(FlashType.PT, P, T, unitP, unitT);
    ops.getSystem().init(2);
    ops.getSystem().initPhysicalProperties();
    Double[] PTfluidProperties = ops.getSystem().getProperties().getValues();

    // Test that the operations are stable, i.e., do the same flash on the same
    // system with the same
    // components and at the same conditions and assert that the result is the same.
    ops.getSystem().init(0);
    ops.flash(FlashType.TP, T, P, unitT, unitP);
    ops.getSystem().init(2);
    ops.getSystem().initPhysicalProperties();
    Double[] TPfluidProperties = ops.getSystem().getProperties().getValues();

    for (int k = 0; k < PTfluidProperties.length; k++) {
      Assertions.assertEquals(PTfluidProperties[k], TPfluidProperties[k]);
    }
  }

  @Test
  void testFluidDefined() {
    double[] fractions = new double[] {98.0, 2.0};
    List<Double> Sp1 =
        Arrays.asList(new Double[] {22.1, 23.2, 24.23, 25.98, 25.23, 26.1, 27.3, 28.7, 23.5, 22.7});
    List<Double> Sp2 = Arrays.asList(
        new Double[] {288.1, 290.1, 295.1, 301.2, 299.3, 310.2, 315.3, 310.0, 305.2, 312.7});
    List<String> components = Arrays.asList(new String[] {"O2", "N2"});
    List<List<Double>> onlineFractions = new ArrayList<List<Double>>();

    for (double d : fractions) {
      ArrayList<Double> l = new ArrayList<Double>();
      for (int i = 0; i < Sp1.size(); i++) {
        l.add(d);
      }
      onlineFractions.add(l);
    }

    SystemInterface fluid_static = new SystemSrkEos(273.15 + 45.0, 22.0);
    fluid_static.addComponent("N2", fractions[0]);
    fluid_static.addComponent("O2", fractions[1]);
    fluid_static.setMixingRule(2);
    fluid_static.useVolumeCorrection(true);
    fluid_static.setMultiPhaseCheck(true);
    // fluid_static.init(0);

    ThermodynamicOperations fluidOps_static = new ThermodynamicOperations(fluid_static);
    CalculationResult res_static = fluidOps_static.propertyFlash(Sp1, Sp2, 1, null, null);

    for (String err : res_static.calculationError) {
      Assertions.assertEquals(err,
          "Sum of fractions must be approximately to 1 or 100, currently (0.0). Have you called init(0)?");
    }
    // fluid_static.setTotalNumberOfMoles(1);
    fluid_static.init(0);
    res_static = fluidOps_static.propertyFlash(Sp1, Sp2, 1, null, null);
    for (String err : res_static.calculationError) {
      Assertions.assertEquals(err, null);
    }

    SystemInterface fluid = new SystemSrkEos(273.15 + 45.0, 22.0);
    fluid.addComponent("nitrogen", 0.79);
    fluid.addComponent("oxygen", 0.21);
    fluid.setMixingRule(2);
    fluid.useVolumeCorrection(true);
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations fluidOps = new ThermodynamicOperations(fluid);
    CalculationResult res = fluidOps.propertyFlash(Sp1, Sp2, 1, components, onlineFractions);
    Assertions.assertEquals(Sp1.size(), res.calculationError.length);
    for (String err : res.calculationError) {
      Assertions.assertNull(err);
    }

    fluid = new SystemSrkEos(273.15 + 45.0, 22.0);
    fluid.addComponent("N2", 0.79);
    fluid.addComponent("O2", 0.21);
    fluid.setMixingRule(2);
    fluid.useVolumeCorrection(true);
    fluid.setMultiPhaseCheck(true);

    fluidOps = new ThermodynamicOperations(fluid);
    CalculationResult res2 = fluidOps.propertyFlash(Sp1, Sp2, 1, components, onlineFractions);
    Assertions.assertEquals(Sp1.size(), res2.calculationError.length);
    for (String err : res2.calculationError) {
      Assertions.assertNull(err);
    }

    Assertions.assertEquals(res, res2);
    // todo: why does below not work?

    // Assertions.assertArrayEquals(res_static.fluidProperties[0],
    // res.fluidProperties[0]);
  }

  @Test
  void testNeqSimPython() {
    SystemInterface thermoSystem = new neqsim.thermo.system.SystemSrkEos(280.0, 10.0);
    thermoSystem.addComponent("methane", 0.7);
    thermoSystem.addComponent("ethane", 0.3);
    thermoSystem.init(0);

    ThermodynamicOperations thermoOps =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(thermoSystem);
    List<Double> jP = Arrays.asList(new Double[] {10.0});
    List<Double> jT = Arrays.asList(new Double[] {280.0});
    CalculationResult res = thermoOps.propertyFlash(jP, jT, 1, null, null);

    // Verify some basic properties
    Assertions.assertEquals(1.0, res.fluidProperties[0][0], "Number of phases mismatch");
    Assertions.assertEquals(thermoSystem.getPressure("Pa"), res.fluidProperties[0][1],
        "Pressure mismatch");
    Assertions.assertEquals(thermoSystem.getTemperature("K"), res.fluidProperties[0][2],
        "Temperature mismatch");

    CalculationResult res2 = thermoOps.propertyFlash(jP, jT, 0, null, null);
    Assertions.assertEquals(res2.calculationError[0],
        "neqsim.util.exception.InvalidInputException: ThermodynamicOperations:propertyFlash - Input FlashMode must be 1, 2 or 3");

    Assertions.assertFalse(res2 == null);
    Assertions.assertEquals(res2, res2);
    Assertions.assertNotEquals(res2, null);
    Assertions.assertNotEquals(res2, new Object());
    Assertions.assertEquals(res2.hashCode(), res2.hashCode());
    Assertions.assertFalse(res2 == res);

    CalculationResult res2_copy =
        new CalculationResult(res2.fluidProperties, res2.calculationError);
    Assertions.assertEquals(res2, res2_copy);
  }

  @Test
  void testNeqSimPython2() {
    String[] components =
        new String[] {"H2O", "N2", "CO2", "C1", "C2", "C3", "iC4", "nC4", "iC5", "nC5", "C6"};
    double[] fractions = new double[] {0.0003, 1.299, 0.419, 94.990, 2.399, 0.355, 0.172, 0.088,
        0.076, 0.036, 0.1656};

    double[] fractions2 = new double[] {0.0003, 2.299, 0.419, 93.990, 2.399, 0.355, 0.172, 0.088,
        0.076, 0.036, 0.1656};

    SystemInterface thermoSystem = new neqsim.thermo.system.SystemSrkEos(100 + 273.15, 60.0);
    thermoSystem.addComponents(components, fractions);
    thermoSystem.init(0);
    ThermodynamicOperations thermoOps =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(thermoSystem);

    double temp = 373.15;
    double press = 60.0 + ThermodynamicConstantsInterface.referencePressure;

    List<Double> jP = Arrays.asList(new Double[] {press});
    List<Double> jT = Arrays.asList(new Double[] {temp});
    CalculationResult res = thermoOps.propertyFlash(jP, jT, 1, null, null);
    // Assert no calculation failed
    for (String errorMessage : res.calculationError) {
      Assertions.assertNull(errorMessage, "Calculation returned: " + errorMessage);
    }

    String[] propNames = SystemProperties.getPropertyNames();
    Assertions.assertEquals(res.fluidProperties[0].length, propNames.length);

    // Redo propertyFlash with online fractions, but still only one data point
    List<List<Double>> onlineFractions = createDummyRequest(thermoSystem.getMolarComposition(), 1);
    CalculationResult res1 = thermoOps.propertyFlash(jP, jT, 1, null, onlineFractions);
    // Assert no calculation failed
    for (String errorMessage : res1.calculationError) {
      Assertions.assertNull(errorMessage, "Calculation returned: " + errorMessage);
    }

    // Assert all properties are the same with online fraction and without
    for (int i = 0; i < res.fluidProperties[0].length; i++) {
      Assertions.assertEquals(res.fluidProperties[0][i], res1.fluidProperties[0][i],
          "Property " + i + " : " + SystemProperties.getPropertyNames()[i]);
    }

    Assertions.assertArrayEquals(res.fluidProperties[0], res1.fluidProperties[0]);

    int numFrac = 3;
    List<List<Double>> onlineFractions2 =
        createDummyRequest(thermoSystem.getMolarComposition(), numFrac);

    List<Double> jP2 = Arrays.asList(new Double[] {press, press});
    List<Double> jT2 = Arrays.asList(new Double[] {temp, temp});
    SystemInterface thermoSystem2 = new neqsim.thermo.system.SystemSrkEos(273.15, 0.0);
    thermoSystem2.addComponents(components, fractions2);
    ThermodynamicOperations thermoOps2 =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(thermoSystem2);
    CalculationResult res2 = thermoOps2.propertyFlash(jP2, jT2, 1, null, onlineFractions2);
    // Assert no calculation failed
    for (String errorMessage : res.calculationError) {
      Assertions.assertNull(errorMessage, "Calculation returned: " + errorMessage);
    }

    // Assert all properties are the same with online fraction and without
    for (int i = 0; i < res.fluidProperties[0].length; i++) {
      Assertions.assertEquals(res.fluidProperties[0][i], res2.fluidProperties[0][i],
          "Property " + i + " : " + SystemProperties.getPropertyNames()[i]);
    }

    // Verify stability
    Assertions.assertArrayEquals(res2.fluidProperties[0], res2.fluidProperties[1]);
  }

  @Test
  void testComponentNames() {
    SystemInterface system = new neqsim.thermo.system.SystemSrkEos();
    system.addComponent("H2O");
    system.addComponent("N2");
    system.addComponent("CO2");
    system.addComponent("C1");
    system.addComponent("C2");
    system.addComponent("C3");
    system.addComponent("iC4");
    system.addComponent("nC4");
    system.addComponent("iC5");
    system.addComponent("nC5");
    system.addComponent("C6");
    system.addComponent("nC10");

    system.removeComponent("H2O");
    system.removeComponent("N2");
    system.removeComponent("CO2");
    system.removeComponent("C1");
    system.removeComponent("C2");
    system.removeComponent("C3");
    system.removeComponent("iC4");
    system.removeComponent("nC4");
    system.removeComponent("iC5");
    system.removeComponent("nC5");
    system.removeComponent("C6");
    system.removeComponent("nC10");
  }

  @Test
  void testPropertyFlash() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 45.0, 22.0);

    List<Double> Sp1 = Arrays.asList(new Double[] {100000.0, 1000000.0, 10000000.0, 20000000.0,
        100000.0, 1000000.0, 10000000.0, 20000000.0, 100000.0, 1000000.0, 10000000.0, 20000000.0});
    List<Double> Sp2 = Arrays.asList(new Double[] {288.15, 288.15, 288.15, 288.15, 303.15, 303.15,
        303.15, 303.15, 423.15, 423.15, 423.15, 423.15});

    fluid.addComponent("water", 0.01);
    fluid.addComponent("nitrogen", 0.02);
    fluid.addComponent("CO2", 0.03);
    fluid.addComponent("methane", 0.81);
    fluid.addComponent("ethane", 0.04);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("i-butane", 0.02);
    fluid.addComponent("n-butane", 0.01);
    fluid.addComponent("i-pentane", 0.01);
    fluid.addComponent("n-pentane", 0.01);
    fluid.addComponent("n-hexane", 0.01);
    fluid.setMolarComposition(
        new double[] {0.054, 0.454, 1.514, 89.92, 5.324, 1.535, 0.232, 0.329, 0.094, 0.107, 0.437});

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    CalculationResult s = ops.propertyFlash(Sp1, Sp2, 1, null, null);

    // Mix mole count and mix molecular weights shall be same for all passes
    for (int i = 1; i < s.fluidProperties.length; i++) {
      Assertions.assertEquals(Double.valueOf(100), s.fluidProperties[i][4],
          "Mix mole count didn't return expected result");
      Assertions.assertEquals(s.fluidProperties[0][9], s.fluidProperties[i][9],
          "Mix molecular weight not correct");
    }
  }

  @Test
  void testpropertyFlashOnline() {
    String[] components = {"nitrogen", "oxygen"};
    double[] fractions = {0.79, 0.21};
    int len = 10;
    List<List<Double>> onlineFractions = createDummyRequest(fractions, len);

    SystemInterface fluid = new SystemSrkEos(298, 1.0);
    fluid.addComponents(components); // , fractions);
    // fluid.setMixingRule("classic");
    // fluid.setTotalFlowRate(1, "mole/sec");
    // fluid.init(0);

    Double[] pressure = {1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 4.0, 3.5, 3.0, 2.5};
    Double[] temperature = {301.0, 301.5, 302.0, 302.5, 303.0, 304.0, 304.0, 303.5, 303.0, 302.5};

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    CalculationResult s = ops.propertyFlash(Arrays.asList(pressure), Arrays.asList(temperature), 1,
        Arrays.asList(components), onlineFractions);
    Assertions.assertEquals(len, s.fluidProperties.length);
  }

  @Test
  void testpropertyFlashOnlineSingle() {
    String[] components = {"nitrogen"};
    double[] fractions = {0.98};
    int len = 10;
    List<List<Double>> onlineFractions = createDummyRequest(fractions, len);

    SystemInterface fluid = new SystemSrkEos(298, 1.0);
    fluid.addComponents(components);
    fluid.addComponent("oxygen");

    Double[] pressure =
        new Double[] {22.1, 23.2, 24.23, 25.98, 25.23, 26.1, 27.3, 28.7, 23.5, 22.7};
    Double[] temperature =
        new Double[] {288.1, 290.1, 295.1, 301.2, 299.3, 310.2, 315.3, 310.0, 305.2, 312.7};

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    CalculationResult s = ops.propertyFlash(Arrays.asList(pressure), Arrays.asList(temperature), 1,
        Arrays.asList(components), onlineFractions);
    Assertions.assertEquals(len, s.fluidProperties.length);
  }

  @Test
  void testpropertyFlashOnlineTooFewInputComponents() {
    String[] components = {"nitrogen", "oxygen"};
    double[] fractions = {0.79, 0.21};
    int len = 10;
    List<List<Double>> onlineFractions = createDummyRequest(fractions, len);

    Double[] pressure = {1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 4.0, 3.5, 3.0, 2.5};
    Double[] temperature = {301.0, 301.5, 302.0, 302.5, 303.0, 304.0, 304.0, 303.5, 303.0, 302.5};

    SystemInterface fluid = new SystemSrkEos(298, 1.0);
    // Add extra component C1
    fluid.addComponent("C1");
    fluid.addComponents(components);
    // Add extra component iC4
    fluid.addComponent("iC4");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    CalculationResult s = ops.propertyFlash(Arrays.asList(pressure), Arrays.asList(temperature), 1,
        Arrays.asList(components), onlineFractions);
    Assertions.assertEquals(len, s.fluidProperties.length);
    Assertions.assertNull(s.calculationError[0]);
  }

  @Test
  void testPropertyFlashTooManyInputComponents() {
    int len = 10;
    String[] components_too_many = {"nitrogen", "oxygen", "water"};
    double[] fractions_to_many = {0.79, 0.21, 0.01};

    Double[] pressure = {1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 4.0, 3.5, 3.0, 2.5};
    Double[] temperature = {301.0, 301.5, 302.0, 302.5, 303.0, 304.0, 304.0, 303.5, 303.0, 302.5};

    List<List<Double>> onlineFractions_too_many = createDummyRequest(fractions_to_many, len);
    SystemInterface fluid = new SystemSrkEos(298, 1.0);

    // Add only two components to fluid
    String[] components = {"nitrogen", "oxygen"};
    fluid.addComponents(components);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    CalculationResult s = ops.propertyFlash(Arrays.asList(pressure), Arrays.asList(temperature), 1,
        Arrays.asList(components_too_many), onlineFractions_too_many);
    Assertions.assertEquals(len, s.fluidProperties.length);
    Assertions.assertEquals("Input component list does not match fluid component list.",
        s.calculationError[0]);
  }

  @Disabled
  @Test
  @SuppressWarnings("unchecked")
  void testpropertyFlashRegressions() throws IOException {
    // TODO: make these tests work
    // make output log of differences per failing test and see check if it is
    // related to change in
    // component input data
    Collection<TestData> testData = getTestData();

    for (TestData test : testData) {
      HashMap<String, Object> inputData = test.getInput();

      SystemInterface fluid = new SystemSrkEos(273.15 + 45.0, 22.0);

      ArrayList<String> compNames = (ArrayList<String>) inputData.get("components");
      ArrayList<Double> fractions = (ArrayList<Double>) inputData.get("fractions");

      if (compNames == null) {
        // System.out.println("Skips test " + test.toString());
        /*
         * for (int k = 0; k < fractions.size(); k++) { fluid.addComponent(k, fractions.get(k)); }
         */
        continue;
      } else {
        for (int k = 0; k < compNames.size(); k++) {
          fluid.addComponent(compNames.get(k), fractions.get(k));
        }
      }

      fluid.init(0);

      ArrayList<Double> sp1 = (ArrayList<Double>) inputData.get("Sp1");
      ArrayList<Double> sp2 = (ArrayList<Double>) inputData.get("Sp2");

      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      int flashMode = (int) inputData.get("FlashMode");
      CalculationResult s = ops.propertyFlash(sp1, sp2, flashMode, compNames, null);
      for (String errorMessage : s.calculationError) {
        Assertions.assertNull(errorMessage, "Calculation returned: " + errorMessage);
      }

      CalculationResult expected = test.getOutput();

      for (int nSamp = 0; nSamp < s.fluidProperties.length; nSamp++) {
        for (int nProp = 0; nProp < s.fluidProperties[nSamp].length; nProp++) {
          if (Double.isNaN(expected.fluidProperties[nSamp][nProp])) {
            Assertions.assertEquals(expected.fluidProperties[nSamp][nProp],
                s.fluidProperties[nSamp][nProp],
                "Test " + (nSamp + 1) + " Property " + SystemProperties.getPropertyNames()[nProp]);
          } else {
            Assertions.assertEquals(expected.fluidProperties[nSamp][nProp],
                s.fluidProperties[nSamp][nProp], 1e-5,
                "Test " + (nSamp + 1) + " Property " + SystemProperties.getPropertyNames()[nProp]);
          }
        }
      }
      // Assertions.assertEquals(expected, s);
    }
  }

  @Test
  @Disabled
  void testDisplay() {
    ThermodynamicOperations ops = new ThermodynamicOperations();
    ops.display();
  }

  private List<List<Double>> createDummyRequest(double[] fractions, int len) {
    List<List<Double>> onlineFractions = new ArrayList<List<Double>>();

    for (double d : fractions) {
      List<Double> l = new ArrayList<Double>();
      for (int i = 0; i < len; i++) {
        l.add(d);
      }
      onlineFractions.add(l);
    }

    return onlineFractions;
  }

  private Collection<TestData> getTestData() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    File folder =
        new File(classLoader.getResource("neqsim/thermodynamicoperations/testcases").getFile());

    HashMap<String, TestData> testData = new HashMap<>();

    File[] directoryListing = folder.listFiles();
    for (File child : directoryListing) {
      String[] n = child.getName().split("_");

      if (testData.get(n[0]) == null) {
        testData.put(n[0], new TestData(n[0]));
      }

      if ("I.json".equals(n[1])) {
        testData.get(n[0]).setInputFile(child.getAbsolutePath());
      } else if ("O.json".equals(n[1])) {
        testData.get(n[0]).setOutputFile(child.getAbsolutePath());
      }
    }

    return testData.values();
  }

  private class TestData {
    private final String name;
    private String inputFile;
    private String outputFile;
    private HashMap<String, Object> input;

    public TestData(String name) {
      this.name = name;
    }

    public void setInputFile(String inputFile) throws IOException {
      this.setInput(inputFile);
      this.inputFile = inputFile;
    }

    public String getOutputFile() {
      return outputFile;
    }

    public void setOutputFile(String outputFile) {
      this.outputFile = outputFile;
    }

    @SuppressWarnings("unchecked")
    private void setInput(String inputFile) throws IOException {
      String fileContents = new String(Files.readAllBytes(Paths.get(inputFile)));
      Gson gson = new Gson();
      Type type = new TypeToken<HashMap<String, Object>>() {}.getType();

      input = gson.fromJson(fileContents, type);

      input.replace("fn", Math.toIntExact(Math.round((double) input.get("fn"))));
      input.replace("FlashMode", Math.toIntExact(Math.round((double) input.get("FlashMode"))));

      ArrayList<Double> sp_in_pa = (ArrayList<Double>) input.get("Sp1");
      ArrayList<Double> sp1 = new ArrayList<Double>();

      for (int k = 0; k < sp_in_pa.size(); k++) {
        sp1.add(sp_in_pa.get(k) / 1e5);
      }
      input.replace("Sp1", sp1);
    }

    private HashMap<String, Object> getInput() {
      return input;
    }

    @SuppressWarnings("unchecked")
    public CalculationResult getOutput() throws IOException {
      String fileContents = new String(Files.readAllBytes(Paths.get(this.getOutputFile())));
      Gson gson = new Gson();
      Type type = new TypeToken<HashMap<String, Object>>() {}.getType();

      HashMap<String, Object> outputData = gson.fromJson(fileContents, type);
      ArrayList<ArrayList<Double>> calcresult =
          (ArrayList<ArrayList<Double>>) outputData.get("calcresult");

      Double[][] calcResult = new Double[calcresult.size()][];
      for (int kSample = 0; kSample < calcresult.size(); kSample++) {
        calcResult[kSample] = new Double[calcresult.get(kSample).size()];
        for (int kProp = 0; kProp < calcresult.get(kSample).size(); kProp++) {
          try {
            calcResult[kSample][kProp] = calcresult.get(kSample).get(kProp);
          } catch (Exception e) {
            calcResult[kSample][kProp] = Double.NaN;
          }
        }
      }

      ArrayList<String> calcerrors = (ArrayList<String>) outputData.get("calcerrors");

      return new CalculationResult(calcResult, calcerrors.toArray(new String[0]));
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      return "TestData{" + "name=" + name + ", input=" + inputFile + ", output=" + outputFile + '}';
    }
  }
}
