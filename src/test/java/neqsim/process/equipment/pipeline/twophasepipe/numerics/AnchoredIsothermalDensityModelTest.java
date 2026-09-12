package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AnchoredIsothermalDensityModelTest extends neqsim.NeqSimTest {
  @ParameterizedTest
  @CsvSource({ "SRK, 295.0, 48.0", "SRK, 305.0, 52.0", "PR, 295.0, 48.0", "PR, 305.0, 52.0" })
  void anchoredVolumeResponseAndDerivativeAgreeWithIndependentSystemEvaluation(String eos, double temperature,
      double pressureBar) {
    SystemInterface fluid = gas(eos, temperature, pressureBar, 0.75);
    TwoFluidSection section = section(fluid);
    section.setGasDensity(1.03 * fluid.getPhase(0).getDensity());
    AnchoredIsothermalDensityModel model = model(section, fluid);
    double referencePressure = section.getPressure();
    double[] state = section.getStateVector();
    assertEquals(section.getGasDensity(), model.calculate(0, state, referencePressure, 5.0)[0], 0.0);

    for (double multiplier : new double[] { 0.98, 1.02 }) {
      double pressure = multiplier * referencePressure;
      double expected = anchoredSystemDensity(fluid, PhaseType.GAS, section, pressure);
      assertEquals(expected, model.calculate(0, state, pressure, 5.0)[0], 1.0e-9 * expected);
    }

    double deltaPressure = 100.0;
    double densityPlus = model.calculate(0, state, referencePressure + deltaPressure, 5.0)[0];
    double densityMinus = model.calculate(0, state, referencePressure - deltaPressure, 5.0)[0];
    double volumeDerivative = (1.0 / densityPlus - 1.0 / densityMinus) / (2.0 * deltaPressure);
    PhaseInterface reference = fluid.getPhase(0);
    double eosVolumeDerivative = -reference.getIsothermalCompressibility() / (1.0e5 * reference.getDensity());
    assertEquals(eosVolumeDerivative, volumeDerivative, Math.abs(eosVolumeDerivative) * 1.0e-6);
    double anchoredCompressibility = (densityPlus - densityMinus) / (2.0 * deltaPressure * section.getGasDensity());
    assertEquals(model.getReferenceIsothermalCompressibility(0, 0), anchoredCompressibility,
        anchoredCompressibility * 1.0e-6);
    assertTrue(densityPlus > section.getGasDensity());
    assertTrue(densityMinus < section.getGasDensity());
  }

  @Test
  void diluteMethaneApproachesTheAnalyticalIsothermalIdealGasLimit() {
    SystemInterface fluid = gas("SRK", 300.0, 0.01, 1.0);
    TwoFluidSection section = section(fluid);
    AnchoredIsothermalDensityModel model = model(section, fluid);
    double pressure = 2.0e3;
    double idealDensity = pressure * fluid.getPhase(0).getMolarMass()
        / (ThermodynamicConstantsInterface.R * section.getTemperature());
    double density = model.calculate(0, section.getStateVector(), pressure, 0.0)[0];
    assertEquals(idealDensity, density, idealDensity * 5.0e-5);
    assertEquals(1.0 / section.getPressure(), model.getReferenceIsothermalCompressibility(0, 0), 5.0e-8);
  }

  @Test
  void gasOilAndWaterRetainTheirDistinctFrozenCompositions() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("n-heptane", 0.25);
    fluid.addComponent("water", 0.15);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    assertEquals(3, fluid.getNumberOfPhases());
    PhaseType[] types = { PhaseType.GAS, PhaseType.OIL, PhaseType.AQUEOUS };
    TwoFluidSection section = section(fluid);
    section.setGasDensity(fluid.getPhase(PhaseType.GAS).getDensity("kg/m3"));
    section.setOilDensity(fluid.getPhase(PhaseType.OIL).getDensity("kg/m3"));
    section.setWaterDensity(fluid.getPhase(PhaseType.AQUEOUS).getDensity("kg/m3"));
    section.setStateVector(new double[] { 0.2, 0.4, 0.6, 0.0, 0.0, 0.0, 0.0 });
    double[] sourceMoles = new double[3];
    for (int phase = 0; phase < types.length; phase++) {
      sourceMoles[phase] = fluid.getPhase(types[phase]).getNumberOfMolesInPhase();
    }
    AnchoredIsothermalDensityModel model = model(section, fluid);
    double pressure = 5.1e6;
    double[] actual = model.calculate(0, section.getStateVector(), pressure, 2.0);
    for (int phase = 0; phase < types.length; phase++) {
      assertTrue(model.hasPhase(0, phase));
      double expected = anchoredSystemDensity(fluid, types[phase], section, pressure);
      assertEquals(expected, actual[phase], expected * 1.0e-8);
      assertEquals(sourceMoles[phase], fluid.getPhase(types[phase]).getNumberOfMolesInPhase(), 0.0);
      assertEquals(types[phase], fluid.getPhase(types[phase]).getType());
    }
    assertTrue(actual[0] < actual[1]);
    assertTrue(actual[1] < actual[2]);
  }

  @Test
  void eachCellUsesItsOwnAcceptedTemperatureAndPhaseComposition() {
    SystemInterface firstFluid = gas("SRK", 295.0, 50.0, 0.95);
    SystemInterface secondFluid = gas("SRK", 305.0, 50.0, 0.60);
    TwoFluidSection first = section(firstFluid);
    TwoFluidSection second = section(secondFluid);
    first.setGasDensity(40.0);
    second.setGasDensity(40.0);
    second.setTemperature(310.0);
    AnchoredIsothermalDensityModel model = new AnchoredIsothermalDensityModel(new TwoFluidSection[] { first, second },
        new SystemInterface[] { firstFluid, secondFluid });
    double pressure = 5.2e6;
    double firstDensity = model.calculate(0, first.getStateVector(), pressure, 1.0)[0];
    double secondDensity = model.calculate(1, second.getStateVector(), pressure, 1.0)[0];
    assertEquals(2, model.getNumberOfCells());
    assertEquals(anchoredSystemDensity(firstFluid, PhaseType.GAS, first, pressure), firstDensity, 1.0e-8);
    assertEquals(anchoredSystemDensity(secondFluid, PhaseType.GAS, second, pressure), secondDensity, 1.0e-8);
    assertNotEquals(firstDensity, secondDensity, 1.0e-4);
    assertEquals(295.0, firstFluid.getTemperature(), 0.0);
    assertEquals(305.0, secondFluid.getTemperature(), 0.0);
  }

  @Test
  void absentPhasesUseOnlyExplicitResidualDensitiesAndAllowJacobianProbes() {
    SystemInterface fluid = gas("SRK", 300.0, 50.0, 0.9);
    TwoFluidSection section = section(fluid);
    AnchoredIsothermalDensityModel model = model(section, fluid);
    double[] probe = section.getStateVector();
    probe[1] = 1.0e-8;
    probe[2] = 1.0e-8;
    double[] density = model.calculate(0, probe, 5.1e6, 0.0);
    assertTrue(model.hasPhase(0, 0));
    assertFalse(model.hasPhase(0, 1));
    assertFalse(model.hasPhase(0, 2));
    assertEquals(777.0, density[1], 0.0);
    assertEquals(1025.0, density[2], 0.0);
    assertEquals(0.0, model.getReferenceIsothermalCompressibility(0, 1), 0.0);
    assertEquals(0.0, model.getReferenceIsothermalCompressibility(0, 2), 0.0);
    section.setStateVector(probe);
    assertThrows(IllegalArgumentException.class, () -> model(section, fluid));
    section.setStateVector(new double[7]);
    section.setOilDensity(0.0);
    assertThrows(IllegalArgumentException.class, () -> model(section, fluid));
  }

  @Test
  void repeatedProbesSnapshotsClonesAndSerializationRemainIndependent() throws Exception {
    SystemInterface fluid = gas("SRK", 300.0, 50.0, 0.75);
    TwoFluidSection section = section(fluid);
    TwoFluidSection[] sections = { section };
    SystemInterface[] fluids = { fluid };
    AnchoredIsothermalDensityModel model = new AnchoredIsothermalDensityModel(sections, fluids);
    AnchoredIsothermalDensityModel cloned = model.clone();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(model);
    }
    AnchoredIsothermalDensityModel restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (AnchoredIsothermalDensityModel) input.readObject();
    }
    double[] state = section.getStateVector();
    double[] savedState = state.clone();
    double[] expected = model.calculate(0, state, 5.2e6, 0.5);
    assertArrayEquals(savedState, state, 0.0);
    assertEquals(300.0, fluid.getTemperature(), 0.0);
    assertEquals(50.0, fluid.getPressure(), 0.0);
    assertEquals(0.75, fluid.getPhase(0).getComponent(0).getx(), 1.0e-15);

    section.setGasDensity(999.0);
    section.setTemperature(350.0);
    fluid.setTemperature(360.0);
    fluid.getPhase(0).getComponent(0).setx(0.50);
    fluid.getPhase(0).getComponent(1).setx(0.50);
    ((EosMixingRulesInterface) fluid.getPhase(0).getMixingRule()).setBinaryInteractionParameter(0, 1, 0.3);
    sections[0] = null;
    fluids[0] = null;
    for (int repetition = 0; repetition < 5; repetition++) {
      model.calculate(0, state, 4.8e6, 0.5);
      double[] actual = model.calculate(0, state, 5.2e6, 10.0);
      assertArrayEquals(expected, actual, 0.0);
      assertArrayEquals(expected, cloned.calculate(0, state, 5.2e6, 10.0), 0.0);
      assertArrayEquals(expected, restored.calculate(0, state, 5.2e6, 10.0), 0.0);
      actual[0] = -1.0;
    }
  }

  @Test
  void microscopicSourceInventoryDoesNotSubstituteAnIdealGasDensity() {
    SystemInterface fluid = gas("PR", 300.0, 50.0, 0.70);
    TwoFluidSection section = section(fluid);
    AnchoredIsothermalDensityModel ordinary = model(section, fluid);
    fluid.setTotalNumberOfMoles(1.0e-14);
    fluid.init(1);
    assertTrue(fluid.getPhase(0).getNumberOfMolesInPhase() < 1.0e-10);
    AnchoredIsothermalDensityModel microscopic = model(section, fluid);
    assertArrayEquals(ordinary.calculate(0, section.getStateVector(), 5.1e6, 0.0),
        microscopic.calculate(0, section.getStateVector(), 5.1e6, 0.0), 1.0e-9);
  }

  @Test
  void unsupportedAssociationEosAndSolidPhasesAreRejected() {
    SystemInterface unsupported = new SystemSrkCPAstatoil(300.0, 50.0);
    unsupported.addComponent("methane", 1.0);
    unsupported.setMixingRule(10);
    unsupported.init(0);
    TwoFluidSection section = new TwoFluidSection(0.5, 1.0, 0.1, 0.0);
    section.setPressure(5.0e6);
    section.setTemperature(300.0);
    section.setGasDensity(40.0);
    section.setOilDensity(777.0);
    section.setWaterDensity(1025.0);
    assertThrows(IllegalArgumentException.class, () -> model(section, unsupported));
    SystemInterface solid = gas("SRK", 300.0, 50.0, 0.9);
    solid.getPhase(0).setType(PhaseType.SOLID);
    assertThrows(IllegalArgumentException.class, () -> model(section, solid));
  }

  @Test
  void invalidTranslatedVolumesAndInvalidProbeInputsFailWithoutChangingTheModel() {
    SystemInterface fluid = gas("SRK", 300.0, 50.0, 0.9);
    TwoFluidSection section = section(fluid);
    section.setGasDensity(10000.0);
    AnchoredIsothermalDensityModel model = model(section, fluid);
    double[] state = section.getStateVector();
    assertThrows(IllegalArgumentException.class, () -> model.calculate(0, state, 6.0e6, 0.0));
    assertThrows(IllegalArgumentException.class, () -> model.calculate(0, state, 0.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> model.calculate(0, state, Double.NaN, 0.0));
    assertThrows(IllegalArgumentException.class, () -> model.calculate(0, state, 5.0e6, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> model.calculate(-1, state, 5.0e6, 0.0));
    assertThrows(IllegalArgumentException.class, () -> model.calculate(0, new double[6], 5.0e6, 0.0));
    assertThrows(IllegalArgumentException.class, () -> model.hasPhase(0, 3));
    assertThrows(IllegalArgumentException.class, () -> model.getReferenceIsothermalCompressibility(1, 0));
    assertEquals(10000.0, model.calculate(0, state, 5.0e6, 0.0)[0], 0.0);
  }

  private static AnchoredIsothermalDensityModel model(TwoFluidSection section, SystemInterface fluid) {
    return new AnchoredIsothermalDensityModel(new TwoFluidSection[] { section }, new SystemInterface[] { fluid });
  }

  private static SystemInterface gas(String eos, double temperature, double pressureBar, double methaneFraction) {
    SystemInterface fluid = "PR".equals(eos) ? new SystemPrEos(temperature, pressureBar)
        : new SystemSrkEos(temperature, pressureBar);
    fluid.addComponent("methane", methaneFraction);
    fluid.addComponent("ethane", 1.0 - methaneFraction);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    assertEquals(1, fluid.getNumberOfPhases());
    assertEquals(PhaseType.GAS, fluid.getPhase(0).getType());
    return fluid;
  }

  private static TwoFluidSection section(SystemInterface fluid) {
    TwoFluidSection section = new TwoFluidSection(0.5, 1.0, 0.1, 0.0);
    section.setPressure(fluid.getPressure() * 1.0e5);
    section.setTemperature(fluid.getTemperature());
    section.setGasDensity(fluid.getPhase(PhaseType.GAS).getDensity());
    section.setOilDensity(777.0);
    section.setWaterDensity(1025.0);
    section.setStateVector(new double[] { 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 });
    return section;
  }

  private static double anchoredSystemDensity(SystemInterface fluid, PhaseType type, TwoFluidSection section,
      double pressure) {
    int index = fluid.getPhaseNumberOfPhase(type);
    SystemInterface reference = fluid.phaseToSystem(index);
    reference.setTemperature(section.getTemperature());
    reference.setPressure(section.getPressure() / 1.0e5);
    reference.init(1);
    double referenceEosDensity = reference.getPhase(0).getDensity();
    double acceptedDensity = type == PhaseType.GAS ? section.getGasDensity()
        : type == PhaseType.AQUEOUS ? section.getWaterDensity() : section.getOilDensity();
    double offset = 1.0 / acceptedDensity - 1.0 / referenceEosDensity;
    reference.setPressure(pressure / 1.0e5);
    reference.init(1);
    return 1.0 / (1.0 / reference.getPhase(0).getDensity() + offset);
  }
}
