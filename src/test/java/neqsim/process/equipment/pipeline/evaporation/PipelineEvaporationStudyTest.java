package neqsim.process.equipment.pipeline.evaporation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.fluidmechanics.flownode.DispersedPhaseSlipCalculator;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/** Tests for the conservative axial pipeline evaporation model. */
public class PipelineEvaporationStudyTest {
  @Test
  void testDropletAreaPerLengthUsesSauterMeanDiameter() {
    double area = PipelineEvaporationStudy.dropletAreaPerLength(2.0e-5, 0.5, 200.0e-6);
    assertEquals(1.2, area, 1.0e-12);
    assertEquals(0.0, PipelineEvaporationStudy.dropletAreaPerLength(0.0, 0.5, 200.0e-6), 0.0);
  }

  @Test
  void testFilmAreaPerLengthUsesInterfaceDiameter() {
    double area = PipelineEvaporationStudy.filmAreaPerLength(0.20, 0.50e-3, 0.75);
    assertEquals(0.75 * Math.PI * 0.199, area, 1.0e-12);
  }

  @Test
  void testBubbleAreaAndTerminalSlipClosures() {
    assertEquals(1.2, PipelineEvaporationStudy.bubbleAreaPerLength(2.0e-4, 5.0, 200.0e-6), 1.0e-12);
    double smallBubble = DispersedPhaseSlipCalculator.terminalVelocityMagnitude(0.50e-3, 800.0, 30.0, 5.0e-3);
    double largeBubble = DispersedPhaseSlipCalculator.terminalVelocityMagnitude(1.00e-3, 800.0, 30.0, 5.0e-3);
    assertTrue(smallBubble > 0.0);
    assertTrue(largeBubble > smallBubble);
  }

  @Test
  void testConfigurationRejectsUnresolvedStepRange() {
    PipelineEvaporationConfig config = new PipelineEvaporationConfig();
    config.setMinimumStepLength(1.0);
    config.setInitialStepLength(0.5);
    assertThrows(IllegalArgumentException.class, config::validate);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void testShortDropletMarchConservesComponents() {
    SystemInterface inlet = createTwoPhaseHydrocarbonSystem();
    double initialLiquidHeptane = inlet.getPhase(1).getComponent("n-heptane").getNumberOfMolesInPhase();

    PipelineEvaporationConfig config = new PipelineEvaporationConfig();
    config.setPipeLength(1.0e-3);
    config.setMinimumStepLength(1.0e-3);
    config.setInitialStepLength(1.0e-3);
    config.setMaximumStepLength(1.0e-3);
    config.setPipeDiameter(0.10);
    config.setGasVelocity(5.0);
    config.setLiquidVelocity(0.5);
    config.setInitialDropletDiameter(200.0e-6);
    config.setMaximumDonorFractionPerStep(0.5);
    config.setMaximumTemperatureChangePerStep(20.0);
    config.setUseAbramzonSirignano(false);

    PipelineEvaporationResult result = new PipelineEvaporationStudy(inlet, config).run();

    List<EvaporationProfilePoint> profile = result.getProfile();
    assertEquals(2, profile.size());
    assertEquals(1.0e-3, profile.get(1).getDistance(), 1.0e-15);
    assertTrue(profile.get(1).getRemainingInjectedLiquidFraction() >= 0.0);
    assertTrue(profile.get(1).getRemainingInjectedLiquidFraction() <= 1.0);
    assertTrue(Double.isFinite(profile.get(1).getGasTemperature()));
    assertTrue(Double.isFinite(profile.get(1).getLiquidTemperature()));
    assertTrue(result.getMaximumComponentMolarBalanceError() < 1.0e-10);
    assertTrue(result.getRelativeEnergyBalanceError() < 1.0e-6);
    assertFalse(result.isCompleteEvaporation());

    assertEquals(initialLiquidHeptane, inlet.getPhase(1).getComponent("n-heptane").getNumberOfMolesInPhase(), 0.0,
        "the study must not mutate its inlet system");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void testShortWallFilmMarchConservesComponents() {
    SystemInterface inlet = createTwoPhaseHydrocarbonSystem();
    PipelineEvaporationConfig config = new PipelineEvaporationConfig();
    config.setLiquidDistribution(LiquidDistribution.WALL_FILM);
    config.setPipeLength(1.0e-3);
    config.setMinimumStepLength(1.0e-3);
    config.setInitialStepLength(1.0e-3);
    config.setMaximumStepLength(1.0e-3);
    config.setPipeDiameter(0.10);
    config.setGasVelocity(5.0);
    config.setLiquidVelocity(0.5);
    config.setInitialFilmThickness(0.50e-3);
    config.setWettedPerimeterFraction(0.75);
    config.setMaximumDonorFractionPerStep(0.5);
    config.setMaximumTemperatureChangePerStep(20.0);

    PipelineEvaporationResult result = new PipelineEvaporationStudy(inlet, config).run();
    EvaporationProfilePoint outlet = result.getProfile().get(result.getProfile().size() - 1);

    assertEquals(2, result.getProfile().size());
    assertTrue(outlet.getRemainingInjectedLiquidFraction() >= 0.0);
    assertTrue(outlet.getRemainingInjectedLiquidFraction() <= 1.0);
    assertTrue(outlet.getInterfacialAreaPerLength() > 0.0);
    assertTrue(result.getMaximumComponentMolarBalanceError() < 1.0e-10);
    assertTrue(result.getRelativeEnergyBalanceError() < 1.0e-6);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void testShortBubbleDissolutionIntoOilTracksInjectedGasAndSlip() {
    SystemInterface inlet = createGasBubbleInOilSystem();
    double initialGasMethane = inlet.getPhase(0).getComponent("methane").getNumberOfMolesInPhase();

    PipelineEvaporationConfig config = oneStepDissolutionConfig();
    config.setSlipModel(DispersedPhaseSlipModel.TERMINAL_VELOCITY);
    config.setPipeInclinationAngle(Math.PI / 6.0);

    PipelineDissolutionResult result = new PipelineDissolutionStudy(inlet, config).run();
    EvaporationProfilePoint outlet = result.getProfile().get(1);

    assertEquals(2, result.getProfile().size());
    assertTrue(result.isGasDissolutionStudy());
    assertFalse(result.isCompleteDissolution());
    assertTrue(Double.isNaN(result.getCompleteDissolutionDistance()));
    assertTrue(outlet.getRemainingTrackedPhaseFraction() >= 0.0);
    assertTrue(outlet.getRemainingTrackedPhaseFraction() <= 1.0);
    assertTrue(outlet.getGasVelocity() > outlet.getLiquidVelocity(), "bubbles should drift forward in an uphill pipe");
    assertTrue(outlet.getDispersedPhaseRelativeVelocity() > 0.0);
    assertTrue(result.getMaximumComponentMolarBalanceError() < 1.0e-10);
    assertTrue(result.getRelativeEnergyBalanceError() < 1.0e-6);
    assertEquals(config.getPipeLength(), outlet.getDistance(), 1.0e-15);
    assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("did not reach")));
    assertEquals(initialGasMethane, inlet.getPhase(0).getComponent("methane").getNumberOfMolesInPhase(), 0.0,
        "the dissolution study must not mutate its inlet system");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void testShortBubbleDissolutionAcceptsAqueousContinuousPhase() {
    SystemInterface inlet = createGasBubbleInWaterSystem();
    PipelineEvaporationConfig config = oneStepDissolutionConfig();
    config.setInitialBubbleDiameter(0.50e-3);

    PipelineDissolutionResult result = new PipelineDissolutionStudy(inlet, config).run();
    EvaporationProfilePoint outlet = result.getProfile().get(1);

    assertEquals(PhaseType.AQUEOUS, result.getOutletSystem().getPhase(1).getType());
    assertTrue(outlet.getRemainingTrackedPhaseFraction() >= 0.0);
    assertTrue(outlet.getRemainingTrackedPhaseFraction() < 1.0);
    assertTrue(result.getMaximumComponentMolarBalanceError() < 1.0e-10);
    assertTrue(result.getRelativeEnergyBalanceError() < 1.0e-6);
  }

  @Test
  @Tag("slow")
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  void testGasBubbleCaseReachesMassBasedDissolutionCriterion() {
    SystemInterface inlet = createGasBubbleInOilSystem();
    PipelineEvaporationConfig config = oneStepDissolutionConfig();
    config.setPipeLength(0.10);
    config.setMinimumStepLength(1.0e-8);
    config.setMaximumStepLength(0.01);
    config.setMaximumDonorFractionPerStep(0.20);
    config.setCompletionFraction(0.01);
    config.setSlipModel(DispersedPhaseSlipModel.TERMINAL_VELOCITY);
    config.setPipeInclinationAngle(Math.PI / 6.0);

    PipelineDissolutionResult result = new PipelineDissolutionStudy(inlet, config).run();
    EvaporationProfilePoint outlet = result.getProfile().get(result.getProfile().size() - 1);

    assertTrue(result.isCompleteDissolution());
    assertTrue(result.getCompleteDissolutionDistance() > 0.0);
    assertTrue(result.getCompleteDissolutionDistance() < config.getPipeLength());
    assertTrue(outlet.getRemainingTrackedPhaseFraction() <= config.getCompletionFraction());
    assertTrue(result.getMaximumComponentMolarBalanceError() < 1.0e-10);
    assertTrue(result.getRelativeEnergyBalanceError() < 1.0e-5);
  }

  @Test
  @Tag("slow")
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  void testHotDropletCaseReachesMassBasedCompletion() {
    SystemInterface inlet = createTwoPhaseHydrocarbonSystem();
    inlet.setPressure(1.5);
    inlet.getPhase(0).setTemperature(400.15);
    inlet.getPhase(1).setTemperature(330.15);
    inlet.init(3);
    inlet.initPhysicalProperties();

    PipelineEvaporationConfig config = new PipelineEvaporationConfig();
    config.setPipeLength(1.0);
    config.setPipeDiameter(0.10);
    config.setGasVelocity(5.0);
    config.setLiquidVelocity(0.5);
    config.setMinimumStepLength(1.0e-8);
    config.setInitialStepLength(1.0e-3);
    config.setMaximumStepLength(0.01);
    config.setMaximumDonorFractionPerStep(0.2);
    config.setMaximumTemperatureChangePerStep(20.0);
    config.setCompletionFraction(0.01);
    config.setAmbientTemperature(400.15);
    config.setOverallWallHeatTransferCoefficient(100.0);

    PipelineEvaporationResult result = new PipelineEvaporationStudy(inlet, config).run();
    EvaporationProfilePoint outlet = result.getProfile().get(result.getProfile().size() - 1);

    assertTrue(result.isCompleteEvaporation());
    assertTrue(result.getCompleteEvaporationDistance() > 0.0);
    assertTrue(result.getCompleteEvaporationDistance() < config.getPipeLength());
    assertTrue(outlet.getRemainingInjectedLiquidFraction() <= config.getCompletionFraction());
    assertTrue(result.getMaximumComponentMolarBalanceError() < 1.0e-10);
    assertTrue(result.getRelativeEnergyBalanceError() < 1.0e-5);
  }

  private static SystemInterface createTwoPhaseHydrocarbonSystem() {
    SystemInterface system = new SystemSrkEos(335.15, 10.0);
    system.addComponent("methane", 5.0, 0);
    system.addComponent("ethane", 0.5, 0);
    system.addComponent("n-heptane", 0.01, 1);
    system.addComponent("nC10", 0.01, 1);
    system.createDatabase(true);
    system.setMixingRule(2);
    system.setPhaseType(0, PhaseType.GAS);
    system.setPhaseType(1, PhaseType.OIL);
    system.getPhase(0).setTemperature(335.15);
    system.getPhase(1).setTemperature(295.15);
    system.initBeta();
    system.init_x_y();
    system.init(3);
    system.initPhysicalProperties();
    return system;
  }

  private static PipelineEvaporationConfig oneStepDissolutionConfig() {
    PipelineEvaporationConfig config = new PipelineEvaporationConfig();
    config.setPipeLength(1.0e-3);
    config.setMinimumStepLength(1.0e-3);
    config.setInitialStepLength(1.0e-3);
    config.setMaximumStepLength(1.0e-3);
    config.setPipeDiameter(0.10);
    config.setGasVelocity(0.50);
    config.setLiquidVelocity(0.30);
    config.setInitialBubbleDiameter(1.0e-3);
    config.setMaximumDonorFractionPerStep(0.5);
    config.setMaximumTemperatureChangePerStep(20.0);
    return config;
  }

  private static SystemInterface createGasBubbleInOilSystem() {
    SystemInterface system = new SystemSrkEos(305.15, 120.0);
    system.addComponent("methane", 0.01, 0);
    system.addComponent("nC10", 5.0, 1);
    system.createDatabase(true);
    system.setMixingRule(2);
    system.setPhaseType(0, PhaseType.GAS);
    system.setPhaseType(1, PhaseType.OIL);
    system.initBeta();
    system.init_x_y();
    system.init(3);
    system.initPhysicalProperties();
    return system;
  }

  private static SystemInterface createGasBubbleInWaterSystem() {
    SystemInterface system = new SystemSrkCPAstatoil(295.15, 50.0);
    system.addComponent("CO2", 0.10, 0);
    system.addComponent("water", 20.0, 1);
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setPhaseType(0, PhaseType.GAS);
    system.setPhaseType(1, PhaseType.AQUEOUS);
    system.initBeta();
    system.init_x_y();
    system.init(3);
    system.initPhysicalProperties();
    system.setPhaseType(1, PhaseType.AQUEOUS);
    system.getPhase(1).setType(PhaseType.AQUEOUS);
    return system;
  }
}
