package neqsim.process.equipment.pipeline.evaporation;

import java.util.ArrayList;
import java.util.List;
import neqsim.fluidmechanics.flownode.DispersedPhaseSlipCalculator;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow.InterphaseDropletFlow;
import neqsim.fluidmechanics.flownode.twophasenode.TwoPhaseFlowNode;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.AnnularFlow;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.BubbleFlowNode;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.DropletFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Estimates the distance required to evaporate an injected hydrocarbon liquid in a gas pipeline.
 *
 * <p>
 * The axial marching calculation uses NeqSim's Krishna standard film boundary. The boundary solves fugacity equality at
 * the interface and a full multicomponent Maxwell-Stefan resistance matrix in both phases. Finite-flux and
 * thermodynamic-factor corrections are enabled. Ranz-Marshall/Kronig-Brink coefficients are used for droplets and the
 * annular-flow correlations are used for a wall film.
 * </p>
 *
 * <p>
 * Component flow inventories are advanced conservatively. The two phase energy equations use the boundary's coupled
 * interphase heat fluxes, including the enthalpy carried by mass transfer, and optional wall heat. An adaptive axial
 * step limits donor depletion and temperature changes. Completion is based on a tracked fraction of the initially
 * injected liquid, never on hydrodynamic liquid holdup.
 * </p>
 *
 * <p>
 * The supplied system must contain gas as phase 0 and hydrocarbon liquid as phase 1. Its phase mole inventories are
 * interpreted as molar flow rates in mol/s, consistent with NeqSim flow-node calculations. No equilibrium flash is
 * performed between axial steps because doing so would replace the finite-rate model with instantaneous equilibrium.
 * </p>
 */
public class PipelineEvaporationStudy {
  private static final double MINIMUM_INVENTORY = 1.0e-20;
  private final SystemInterface inletSystem;
  private final PipelineEvaporationConfig config;
  private final boolean gasDissolution;

  /**
   * Constructor.
   *
   * @param inletSystem explicit two-phase inlet system, gas in phase 0 and injected liquid in phase 1
   * @param config geometry and numerical settings
   */
  public PipelineEvaporationStudy(SystemInterface inletSystem, PipelineEvaporationConfig config) {
    this(inletSystem, config, false);
  }

  /** Constructor used by the gas-dissolution specialization. */
  protected PipelineEvaporationStudy(SystemInterface inletSystem, PipelineEvaporationConfig config,
      boolean gasDissolution) {
    if (inletSystem == null) {
      throw new IllegalArgumentException("inlet system cannot be null");
    }
    if (config == null) {
      throw new IllegalArgumentException("configuration cannot be null");
    }
    this.inletSystem = inletSystem.clone();
    this.config = config;
    this.gasDissolution = gasDissolution;
  }

  /**
   * Run the adaptive axial calculation.
   *
   * @return evaporation result and profile
   */
  public PipelineEvaporationResult run() {
    config.validate();
    SystemInterface state = inletSystem.clone();
    validateSystem(state);
    PhaseType configuredLiquidPhaseType = state.getPhase(1).getType();
    state.init(3);
    setLiquidPhaseType(state, configuredLiquidPhaseType);
    state.initPhysicalProperties();

    int componentCount = state.getPhase(0).getNumberOfComponents();
    double[] initialComponentTotals = componentTotals(state, componentCount);
    int trackedPhase = gasDissolution ? 0 : 1;
    double[][] trackedMolesByPhase = new double[2][componentCount];
    for (int i = 0; i < componentCount; i++) {
      trackedMolesByPhase[trackedPhase][i] = state.getPhase(trackedPhase).getComponent(i).getNumberOfMolesInPhase();
    }

    double initialTrackedMass = trackedMass(state, trackedMolesByPhase[trackedPhase], trackedPhase);
    if (!(initialTrackedMass > 0.0)) {
      throw new IllegalArgumentException(gasDissolution ? "phase 0 must contain a positive injected-gas flow"
          : "phase 1 must contain a positive injected-liquid flow");
    }
    double initialDispersedDensity = state.getPhase(trackedPhase).getPhysicalProperties().getDensity();
    if (!Double.isFinite(initialDispersedDensity) || initialDispersedDensity <= 0.0) {
      throw new IllegalArgumentException("dispersed-phase density must be finite and positive");
    }
    double initialDispersedVolumeFlow = initialTrackedMass / initialDispersedDensity;
    double initialEnthalpy = totalEnthalpy(state);

    List<EvaporationProfilePoint> profile = new ArrayList<EvaporationProfilePoint>();
    List<String> warnings = new ArrayList<String>();
    double distance = 0.0;
    double stepLength = config.getInitialStepLength();
    double cumulativeWallHeat = 0.0;
    double remainingFraction = 1.0;
    double completionDistance = Double.NaN;
    boolean complete = false;

    LocalHydrodynamics initialHydrodynamics = calculateLocalHydrodynamics(state, characteristicSize(1.0));
    profile.add(new EvaporationProfilePoint(0.0, 1.0, characteristicSize(1.0), state.getPhase(0).getTemperature(),
        state.getPhase(1).getTemperature(),
        interfacialAreaPerLength(1.0, initialDispersedVolumeFlow,
            initialHydrodynamics.dispersedPhaseVelocity(gasDissolution)),
        0.0, new double[componentCount], initialHydrodynamics.gasVelocity, initialHydrodynamics.liquidVelocity,
        initialHydrodynamics.relativeVelocity));

    int acceptedSteps = 0;
    while (distance < config.getPipeLength() && remainingFraction > config.getCompletionFraction()
        && acceptedSteps < config.getMaximumNumberOfSteps()) {
      state.init(3);
      setLiquidPhaseType(state, configuredLiquidPhaseType);
      state.initPhysicalProperties();
      double dispersedVolumeRatio = phaseVolumeFlow(state, trackedPhase) / initialDispersedVolumeFlow;
      double characteristicSize = characteristicSize(dispersedVolumeRatio);
      LocalHydrodynamics hydrodynamics = calculateLocalHydrodynamics(state, characteristicSize);
      LocalClosure closure = calculateLocalClosure(state, characteristicSize, trackedMolesByPhase[1], hydrodynamics);
      validateClosure(closure);
      if (closure.usedHeatFluxFallback) {
        addWarningOnce(warnings, "The coupled boundary returned a non-finite heat flux near phase depletion; "
            + "a bounded two-film enthalpy closure was used for that axial state.");
      }

      double areaPerLength = interfacialAreaPerLength(dispersedVolumeRatio, initialDispersedVolumeFlow,
          hydrodynamics.dispersedPhaseVelocity(gasDissolution));
      double candidateStep = Math.min(stepLength, config.getPipeLength() - distance);
      StepEstimate estimate = estimateStep(state, closure, areaPerLength, candidateStep);

      while ((estimate.maximumDonorFraction > config.getMaximumDonorFractionPerStep()
          || estimate.maximumTemperatureChange > config.getMaximumTemperatureChangePerStep())
          && candidateStep > config.getMinimumStepLength()) {
        candidateStep = Math.max(config.getMinimumStepLength(), 0.5 * candidateStep);
        estimate = estimateStep(state, closure, areaPerLength, candidateStep);
      }

      double effectiveArea = areaPerLength * candidateStep;
      double previousFraction = remainingFraction;
      double enthalpyBefore = totalEnthalpy(state);
      double[] transfer = new double[componentCount];
      for (int i = 0; i < componentCount; i++) {
        transfer[i] = closure.componentMolarFluxes[i] * effectiveArea;
        int donorPhase = transfer[i] >= 0.0 ? 0 : 1;
        int receiverPhase = 1 - donorPhase;
        double donorInventory = state.getPhase(donorPhase).getComponent(i).getNumberOfMolesInPhase();
        double maximumTransfer = 0.999 * Math.max(0.0, donorInventory);
        if (Math.abs(transfer[i]) > maximumTransfer) {
          transfer[i] = Math.copySign(maximumTransfer, transfer[i]);
          addWarningOnce(warnings, "A closure flux pointed out of a depleted component inventory; "
              + "a conservative component limiter was applied.");
        }
        if (donorInventory > MINIMUM_INVENTORY) {
          double trackedShare = Math.min(1.0, trackedMolesByPhase[donorPhase][i] / donorInventory);
          double trackedTransfer = Math.abs(transfer[i]) * trackedShare;
          trackedMolesByPhase[donorPhase][i] = Math.max(0.0, trackedMolesByPhase[donorPhase][i] - trackedTransfer);
          trackedMolesByPhase[receiverPhase][i] += trackedTransfer;
        }
        state.getPhase(0).addMoles(i, -transfer[i]);
        state.getPhase(1).addMoles(i, transfer[i]);
      }

      double gasTemperature = state.getPhase(0).getTemperature();
      double liquidTemperature = state.getPhase(1).getTemperature();
      state.initBeta();
      state.init_x_y();
      state.init(3);
      setLiquidPhaseType(state, configuredLiquidPhaseType);

      double wallHeat = wallHeat(candidateStep, gasTemperature, liquidTemperature);
      double gasWallHeat = wallHeatReceivingPhase() == 0 ? wallHeat : 0.0;
      double liquidWallHeat = wallHeatReceivingPhase() == 1 ? wallHeat : 0.0;
      double gasTargetEnthalpy = state.getPhase(0).getEnthalpy("J") + closure.interphaseHeatFluxes[0] * effectiveArea
          + gasWallHeat;
      double liquidTargetEnthalpy = state.getPhase(1).getEnthalpy("J") + closure.interphaseHeatFluxes[1] * effectiveArea
          + liquidWallHeat;

      solvePhaseTemperature(state, 0, gasTargetEnthalpy, gasTemperature);
      solvePhaseTemperature(state, 1, liquidTargetEnthalpy, liquidTemperature);
      solveTotalEnergy(state, enthalpyBefore + wallHeat);
      state.init(3);
      setLiquidPhaseType(state, configuredLiquidPhaseType);
      state.initPhysicalProperties();

      double stepEnergyResidual = totalEnthalpy(state) - enthalpyBefore - wallHeat;
      double stepEnergyScale = Math.max(1.0, Math.abs(enthalpyBefore) + Math.abs(wallHeat));
      if (Math.abs(stepEnergyResidual) / stepEnergyScale > 1.0e-3) {
        addWarningOnce(warnings,
            "The local energy residual exceeded 0.1%; reduce the maximum temperature change or axial step.");
      }

      distance += candidateStep;
      cumulativeWallHeat += wallHeat;
      acceptedSteps++;
      remainingFraction = trackedMass(state, trackedMolesByPhase[trackedPhase], trackedPhase) / initialTrackedMass;
      remainingFraction = Math.max(0.0, Math.min(1.0, remainingFraction));

      double totalFlux = 0.0;
      double[] appliedFluxes = new double[componentCount];
      for (int i = 0; i < componentCount; i++) {
        appliedFluxes[i] = transfer[i] / effectiveArea;
        totalFlux += appliedFluxes[i];
      }
      double outletDispersedVolumeRatio = phaseVolumeFlow(state, trackedPhase) / initialDispersedVolumeFlow;
      double outletCharacteristicSize = characteristicSize(outletDispersedVolumeRatio);
      LocalHydrodynamics outletHydrodynamics = calculateLocalHydrodynamics(state, outletCharacteristicSize);
      profile.add(new EvaporationProfilePoint(distance, remainingFraction,
          characteristicSize(outletDispersedVolumeRatio), state.getPhase(0).getTemperature(),
          state.getPhase(1).getTemperature(),
          interfacialAreaPerLength(outletDispersedVolumeRatio, initialDispersedVolumeFlow,
              outletHydrodynamics.dispersedPhaseVelocity(gasDissolution)),
          totalFlux, appliedFluxes, outletHydrodynamics.gasVelocity, outletHydrodynamics.liquidVelocity,
          outletHydrodynamics.relativeVelocity));

      if (remainingFraction <= config.getCompletionFraction()) {
        double denominator = previousFraction - remainingFraction;
        double completedStepFraction = denominator > 0.0
            ? (previousFraction - config.getCompletionFraction()) / denominator
            : 1.0;
        completionDistance = distance - candidateStep + candidateStep * completedStepFraction;
        complete = true;
      }

      if (estimate.maximumDonorFraction < 0.25 * config.getMaximumDonorFractionPerStep()
          && estimate.maximumTemperatureChange < 0.25 * config.getMaximumTemperatureChangePerStep()) {
        stepLength = Math.min(config.getMaximumStepLength(), 1.5 * candidateStep);
      } else {
        stepLength = candidateStep;
      }
    }

    if (acceptedSteps >= config.getMaximumNumberOfSteps() && !complete) {
      addWarningOnce(warnings, "The maximum number of axial steps was reached before completion.");
    }
    if (!complete && remainingFraction > config.getCompletionFraction()) {
      addWarningOnce(warnings,
          gasDissolution ? "The injected gas did not reach the dissolution criterion within the pipe length."
              : "The injected liquid did not reach the evaporation criterion within the pipe length.");
    }

    double maxComponentBalanceError = maximumComponentBalanceError(state, initialComponentTotals);
    setLiquidPhaseType(state, configuredLiquidPhaseType);
    double finalEnthalpy = totalEnthalpy(state);
    double energyScale = Math.max(1.0, Math.abs(initialEnthalpy) + Math.abs(cumulativeWallHeat));
    double relativeEnergyError = Math.abs(finalEnthalpy - initialEnthalpy - cumulativeWallHeat) / energyScale;
    return new PipelineEvaporationResult(profile, complete, completionDistance, maxComponentBalanceError,
        relativeEnergyError, warnings, state, gasDissolution);
  }

  private void validateSystem(SystemInterface system) {
    if (system.getNumberOfPhases() < 2) {
      throw new IllegalArgumentException("an explicit gas and liquid phase are required");
    }
    if (system.getPhase(0).getType() != PhaseType.GAS) {
      throw new IllegalArgumentException("phase 0 must be gas");
    }
    PhaseType liquidType = system.getPhase(1).getType();
    if (liquidType != PhaseType.OIL && liquidType != PhaseType.LIQUID && liquidType != PhaseType.AQUEOUS) {
      throw new IllegalArgumentException("phase 1 must be an oil, liquid, or aqueous phase");
    }
    if (system.getPhase(0).getNumberOfComponents() < 2) {
      throw new IllegalArgumentException("Maxwell-Stefan transfer requires at least two components");
    }
  }

  private LocalClosure calculateLocalClosure(SystemInterface state, double characteristicSize,
      double[] trackedLiquidMoles, LocalHydrodynamics hydrodynamics) {
    PipeData pipe = new PipeData(config.getPipeDiameter(), config.getPipeRoughness());
    SystemInterface localSystem = state.clone();
    TwoPhaseFlowNode node;
    if (gasDissolution) {
      BubbleFlowNode bubbleNode = new BubbleFlowNode(localSystem, pipe);
      bubbleNode.setAverageBubbleDiameter(characteristicSize);
      bubbleNode.setSpecifiedDispersedPhaseRelativeVelocity(hydrodynamics.relativeVelocity);
      node = bubbleNode;
    } else if (config.getLiquidDistribution() == LiquidDistribution.DROPLETS) {
      DropletFlowNode dropletNode = new DropletFlowNode(localSystem, pipe);
      dropletNode.setAverageDropletDiameter(characteristicSize);
      dropletNode.setSpecifiedDispersedPhaseRelativeVelocity(hydrodynamics.relativeVelocity);
      node = dropletNode;
    } else {
      node = new AnnularFlow(localSystem, pipe);
    }

    setLocalHydrodynamics(node, localSystem, pipe, hydrodynamics);
    node.setLengthOfNode(1.0);
    node.getFluidBoundary().setMassTransferCalc(true);
    node.getFluidBoundary().setHeatTransferCalc(true);
    node.getFluidBoundary().useFiniteFluxCorrection(true);
    node.getFluidBoundary().useThermodynamicCorrections(true);
    node.init();
    node.calcFluxes();

    if (node instanceof DropletFlowNode && config.isUseAbramzonSirignano()) {
      InterphaseDropletFlow transferModel = (InterphaseDropletFlow) node.getInterphaseTransportCoefficient();
      double spaldingNumber = calculateSpaldingMassTransferNumber(node, trackedLiquidMoles);
      transferModel.setSpaldingMassTransferNumber(spaldingNumber);
      transferModel.setUseAbramzonSirignano(true);
      node.calcFluxes();
    }

    int componentCount = state.getPhase(0).getNumberOfComponents();
    double[] fluxes = new double[componentCount];
    for (int i = 0; i < componentCount; i++) {
      fluxes[i] = node.getFluidBoundary().getInterphaseMolarFlux(i);
    }
    double[] heatFluxes = new double[] { node.getFluidBoundary().getInterphaseHeatFlux(0),
        node.getFluidBoundary().getInterphaseHeatFlux(1) };
    boolean usedHeatFluxFallback = !allFinite(heatFluxes);
    if (usedHeatFluxFallback) {
      heatFluxes = calculateFallbackHeatFluxes(node, fluxes);
    }
    return new LocalClosure(fluxes, heatFluxes, usedHeatFluxFallback);
  }

  private static double[] calculateFallbackHeatFluxes(TwoPhaseFlowNode node, double[] fluxes) {
    double[] heatTransferCoefficients = new double[2];
    for (int phase = 0; phase < 2; phase++) {
      double moles = Math.max(MINIMUM_INVENTORY, node.getBulkSystem().getPhase(phase).getNumberOfMolesInPhase());
      double molarMass = Math.max(MINIMUM_INVENTORY, node.getBulkSystem().getPhase(phase).getMolarMass());
      double conductivity = Math.max(MINIMUM_INVENTORY,
          node.getBulkSystem().getPhase(phase).getPhysicalProperties().getConductivity());
      double prandtlNumber = node.getBulkSystem().getPhase(phase).getCp() / moles / molarMass
          * node.getBulkSystem().getPhase(phase).getPhysicalProperties().getViscosity() / conductivity;
      heatTransferCoefficients[phase] = node.getInterphaseTransportCoefficient()
          .calcInterphaseHeatTransferCoefficient(phase, prandtlNumber, node);
      if (!Double.isFinite(heatTransferCoefficients[phase]) || heatTransferCoefficients[phase] <= 0.0) {
        double safeConductivity = Double.isFinite(conductivity) && conductivity > 1.0e-12 ? conductivity
            : (phase == 0 ? 0.02 : 0.10);
        double characteristicLength = node instanceof DropletFlowNode
            ? ((DropletFlowNode) node).getAverageDropletDiameter()
            : node instanceof BubbleFlowNode ? ((BubbleFlowNode) node).getAverageBubbleDiameter()
                : 0.01 * node.getGeometry().getDiameter();
        double stagnantNusseltNumber = node instanceof DropletFlowNode && phase == 1 ? 17.66 : 2.0;
        heatTransferCoefficients[phase] = stagnantNusseltNumber * safeConductivity
            / Math.max(1.0e-8, characteristicLength);
      }
    }

    double enthalpyFlux = 0.0;
    double interfaceTemperature = 0.5
        * (node.getBulkSystem().getPhase(0).getTemperature() + node.getBulkSystem().getPhase(1).getTemperature());
    for (int i = 0; i < fluxes.length; i++) {
      double evaluationTemperature = Math.min(interfaceTemperature,
          0.999 * node.getBulkSystem().getPhase(0).getComponent(i).getTC());
      double vaporizationEnthalpy = node.getBulkSystem().getPhase(0).getComponent(i)
          .getPureComponentHeatOfVaporization(evaluationTemperature) * 1000.0;
      if (Double.isFinite(vaporizationEnthalpy) && vaporizationEnthalpy > 0.0) {
        enthalpyFlux += fluxes[i] * vaporizationEnthalpy;
      }
    }

    double coefficientSum = heatTransferCoefficients[0] + heatTransferCoefficients[1];
    if (!Double.isFinite(coefficientSum) || coefficientSum <= 0.0) {
      throw new IllegalStateException("unable to calculate a finite fallback interphase heat flux");
    }
    interfaceTemperature = (enthalpyFlux
        + heatTransferCoefficients[0] * node.getBulkSystem().getPhase(0).getTemperature()
        + heatTransferCoefficients[1] * node.getBulkSystem().getPhase(1).getTemperature()) / coefficientSum;
    interfaceTemperature = Math.max(1.0, Math.min(
        Math.max(node.getBulkSystem().getPhase(0).getTemperature(), node.getBulkSystem().getPhase(1).getTemperature())
            + 100.0,
        interfaceTemperature));
    return new double[] {
        -heatTransferCoefficients[0] * (node.getBulkSystem().getPhase(0).getTemperature() - interfaceTemperature),
        -heatTransferCoefficients[1] * (node.getBulkSystem().getPhase(1).getTemperature() - interfaceTemperature) };
  }

  private void setLocalHydrodynamics(TwoPhaseFlowNode node, SystemInterface system, PipeData pipe,
      LocalHydrodynamics hydrodynamics) {
    double gasVolumeFlow = phaseVolumeFlow(system, 0);
    double liquidVolumeFlow = phaseVolumeFlow(system, 1);
    double gasAreaDemand = gasVolumeFlow / hydrodynamics.gasVelocity;
    double liquidAreaDemand = liquidVolumeFlow / hydrodynamics.liquidVelocity;
    double totalAreaDemand = gasAreaDemand + liquidAreaDemand;
    double gasFraction = totalAreaDemand > 0.0 ? gasAreaDemand / totalAreaDemand : 0.999;
    gasFraction = Math.max(1.0e-6, Math.min(1.0 - 1.0e-6, gasFraction));
    node.setPhaseFraction(0, gasFraction);
    node.setPhaseFraction(1, 1.0 - gasFraction);
    node.setVelocity(0, hydrodynamics.gasVelocity);
    node.setVelocity(1, hydrodynamics.liquidVelocity);
    pipe.setNodeLength(1.0);
  }

  private LocalHydrodynamics calculateLocalHydrodynamics(SystemInterface system, double characteristicSize) {
    if (config.getSlipModel() == DispersedPhaseSlipModel.USER_SPECIFIED
        || config.getLiquidDistribution() == LiquidDistribution.WALL_FILM && !gasDissolution) {
      return new LocalHydrodynamics(config.getGasVelocity(), config.getLiquidVelocity(),
          Math.abs(config.getGasVelocity() - config.getLiquidVelocity()));
    }

    int dispersedPhase = gasDissolution ? 0 : 1;
    int continuousPhase = 1 - dispersedPhase;
    double continuousDensity = system.getPhase(continuousPhase).getPhysicalProperties().getDensity();
    double dispersedDensity = system.getPhase(dispersedPhase).getPhysicalProperties().getDensity();
    double continuousViscosity = system.getPhase(continuousPhase).getPhysicalProperties().getViscosity();
    double relativeVelocity = DispersedPhaseSlipCalculator.terminalVelocityMagnitude(characteristicSize,
        continuousDensity, dispersedDensity, continuousViscosity);
    double axialSlip = -Math.signum(dispersedDensity - continuousDensity) * relativeVelocity
        * Math.sin(config.getPipeInclinationAngle());
    double continuousVelocity = gasDissolution ? config.getLiquidVelocity() : config.getGasVelocity();
    double dispersedVelocity = Math.max(1.0e-6, continuousVelocity + axialSlip);
    return gasDissolution ? new LocalHydrodynamics(dispersedVelocity, continuousVelocity, relativeVelocity)
        : new LocalHydrodynamics(continuousVelocity, dispersedVelocity, relativeVelocity);
  }

  private double calculateSpaldingMassTransferNumber(TwoPhaseFlowNode node, double[] trackedLiquidMoles) {
    double surfaceNumerator = 0.0;
    double surfaceDenominator = 0.0;
    double bulkNumerator = 0.0;
    double bulkDenominator = 0.0;
    double trackedMass = 0.0;
    int componentCount = trackedLiquidMoles.length;
    for (int i = 0; i < componentCount; i++) {
      trackedMass += trackedLiquidMoles[i] * node.getBulkSystem().getPhase(0).getComponent(i).getMolarMass();
    }
    for (int i = 0; i < componentCount; i++) {
      double molarMass = node.getBulkSystem().getPhase(0).getComponent(i).getMolarMass();
      double surfaceMass = node.getInterphaseSystem().getPhase(0).getComponent(i).getx() * molarMass;
      double bulkMass = node.getBulkSystem().getPhase(0).getComponent(i).getx() * molarMass;
      surfaceDenominator += surfaceMass;
      bulkDenominator += bulkMass;
      if (trackedLiquidMoles[i] * molarMass > 1.0e-12 * trackedMass) {
        surfaceNumerator += surfaceMass;
        bulkNumerator += bulkMass;
      }
    }
    if (surfaceDenominator <= 0.0 || bulkDenominator <= 0.0) {
      return 0.0;
    }
    double surfaceMassFraction = surfaceNumerator / surfaceDenominator;
    double bulkMassFraction = bulkNumerator / bulkDenominator;
    if (surfaceMassFraction <= bulkMassFraction || surfaceMassFraction >= 1.0 - 1.0e-12) {
      return 0.0;
    }
    return (surfaceMassFraction - bulkMassFraction) / (1.0 - surfaceMassFraction);
  }

  private StepEstimate estimateStep(SystemInterface state, LocalClosure closure, double areaPerLength,
      double stepLength) {
    double area = areaPerLength * stepLength;
    double maximumDonorFraction = 0.0;
    for (int i = 0; i < closure.componentMolarFluxes.length; i++) {
      double transfer = closure.componentMolarFluxes[i] * area;
      int donorPhase = transfer >= 0.0 ? 0 : 1;
      double donor = state.getPhase(donorPhase).getComponent(i).getNumberOfMolesInPhase();
      if (donor > MINIMUM_INVENTORY) {
        maximumDonorFraction = Math.max(maximumDonorFraction, Math.abs(transfer) / donor);
      }
    }

    double wallHeat = wallHeat(stepLength, state.getPhase(0).getTemperature(), state.getPhase(1).getTemperature());
    double gasWallHeat = wallHeatReceivingPhase() == 0 ? wallHeat : 0.0;
    double liquidWallHeat = wallHeatReceivingPhase() == 1 ? wallHeat : 0.0;
    double gasCp = safeHeatCapacity(state.getPhase(0).getCp());
    double liquidCp = safeHeatCapacity(state.getPhase(1).getCp());
    double gasChange = Math.abs(closure.interphaseHeatFluxes[0] * area + gasWallHeat) / gasCp;
    double liquidChange = Math.abs(closure.interphaseHeatFluxes[1] * area + liquidWallHeat) / liquidCp;
    return new StepEstimate(maximumDonorFraction, Math.max(gasChange, liquidChange));
  }

  private double interfacialAreaPerLength(double liquidVolumeRatio, double initialLiquidVolumeFlow) {
    return interfacialAreaPerLength(liquidVolumeRatio, initialLiquidVolumeFlow,
        gasDissolution ? config.getGasVelocity() : config.getLiquidVelocity());
  }

  private double interfacialAreaPerLength(double liquidVolumeRatio, double initialLiquidVolumeFlow,
      double dispersedPhaseVelocity) {
    if (gasDissolution) {
      double diameter = characteristicSize(liquidVolumeRatio);
      double currentVolumeFlow = initialLiquidVolumeFlow * Math.max(0.0, liquidVolumeRatio);
      return bubbleAreaPerLength(currentVolumeFlow, dispersedPhaseVelocity, diameter);
    }
    if (config.getLiquidDistribution() == LiquidDistribution.DROPLETS) {
      double diameter = characteristicSize(liquidVolumeRatio);
      double currentVolumeFlow = initialLiquidVolumeFlow * Math.max(0.0, liquidVolumeRatio);
      return dropletAreaPerLength(currentVolumeFlow, dispersedPhaseVelocity, diameter);
    }
    double filmThickness = characteristicSize(liquidVolumeRatio);
    return filmAreaPerLength(config.getPipeDiameter(), filmThickness, config.getWettedPerimeterFraction());
  }

  /**
   * Calculate the surface area of a spherical-droplet population per axial length.
   *
   * @param liquidVolumeFlow dispersed liquid volume flow in m3/s
   * @param liquidVelocity droplet velocity in m/s
   * @param sauterMeanDiameter Sauter mean diameter in m
   * @return interfacial area per length in m2/m
   */
  static double dropletAreaPerLength(double liquidVolumeFlow, double liquidVelocity, double sauterMeanDiameter) {
    if (liquidVolumeFlow <= 0.0 || liquidVelocity <= 0.0 || sauterMeanDiameter <= 0.0) {
      return 0.0;
    }
    return 6.0 * liquidVolumeFlow / liquidVelocity / sauterMeanDiameter;
  }

  /**
   * Calculate the surface area of a spherical-bubble population per axial length.
   *
   * @param gasVolumeFlow dispersed gas volume flow in m3/s
   * @param gasVelocity bubble velocity in m/s
   * @param sauterMeanDiameter Sauter mean diameter in m
   * @return interfacial area per length in m2/m
   */
  static double bubbleAreaPerLength(double gasVolumeFlow, double gasVelocity, double sauterMeanDiameter) {
    return dropletAreaPerLength(gasVolumeFlow, gasVelocity, sauterMeanDiameter);
  }

  /**
   * Calculate wall-film interfacial area per axial length.
   *
   * @param pipeDiameter pipe inside diameter in m
   * @param filmThickness film thickness in m
   * @param wettedFraction wetted perimeter fraction
   * @return interfacial area per length in m2/m
   */
  static double filmAreaPerLength(double pipeDiameter, double filmThickness, double wettedFraction) {
    double interfaceDiameter = Math.max(1.0e-12, pipeDiameter - 2.0 * filmThickness);
    return wettedFraction * Math.PI * interfaceDiameter;
  }

  private double characteristicSize(double liquidVolumeRatio) {
    double boundedRatio = Math.max(0.0, liquidVolumeRatio);
    if (gasDissolution) {
      return config.getInitialBubbleDiameter() * Math.cbrt(boundedRatio);
    }
    if (config.getLiquidDistribution() == LiquidDistribution.DROPLETS) {
      return config.getInitialDropletDiameter() * Math.cbrt(boundedRatio);
    }
    return config.getInitialFilmThickness() * boundedRatio;
  }

  private double wallHeat(double stepLength, double gasTemperature, double liquidTemperature) {
    double receivingTemperature = wallHeatReceivingPhase() == 0 ? gasTemperature : liquidTemperature;
    return config.getOverallWallHeatTransferCoefficient() * Math.PI * config.getPipeDiameter() * stepLength
        * (config.getAmbientTemperature() - receivingTemperature);
  }

  private int wallHeatReceivingPhase() {
    return !gasDissolution && config.getLiquidDistribution() == LiquidDistribution.DROPLETS ? 0 : 1;
  }

  private static double phaseVolumeFlow(SystemInterface system, int phase) {
    double massFlow = system.getPhase(phase).getNumberOfMolesInPhase() * system.getPhase(phase).getMolarMass();
    double density = system.getPhase(phase).getPhysicalProperties().getDensity();
    return density > 0.0 ? massFlow / density : 0.0;
  }

  private static void setLiquidPhaseType(SystemInterface system, PhaseType phaseType) {
    system.setPhaseType(1, phaseType);
    system.getPhase(1).setType(phaseType);
  }

  private static void solvePhaseTemperature(SystemInterface state, int phaseNumber, double targetEnthalpy,
      double initialTemperature) {
    double temperature = initialTemperature;
    for (int iteration = 0; iteration < 12; iteration++) {
      state.getPhase(phaseNumber).setTemperature(temperature);
      state.init(3);
      double error = targetEnthalpy - state.getPhase(phaseNumber).getEnthalpy("J");
      double heatCapacity = state.getPhase(phaseNumber).getCp();
      if (!Double.isFinite(error) || !Double.isFinite(heatCapacity) || Math.abs(heatCapacity) < 1.0e-12) {
        return;
      }
      if (Math.abs(error) <= 1.0e-8 * Math.max(1.0, Math.abs(targetEnthalpy))) {
        return;
      }
      double change = error / heatCapacity;
      change = Math.max(-20.0, Math.min(20.0, change));
      temperature = Math.max(1.0, temperature + change);
    }
    state.getPhase(phaseNumber).setTemperature(temperature);
    state.init(3);
  }

  private static void solveTotalEnergy(SystemInterface state, double targetEnthalpy) {
    for (int iteration = 0; iteration < 50; iteration++) {
      state.init(3);
      double error = targetEnthalpy - totalEnthalpy(state);
      if (!Double.isFinite(error)) {
        throw new IllegalStateException("unable to evaluate overall energy balance");
      }
      if (Math.abs(error) <= 1.0e-9 * Math.max(1.0, Math.abs(targetEnthalpy))) {
        return;
      }
      double[] heatCapacities = new double[] { state.getPhase(0).getCp(), state.getPhase(1).getCp() };
      int correctionPhase = -1;
      double correctionHeatCapacity = 0.0;
      for (int phase = 0; phase < 2; phase++) {
        if (Double.isFinite(heatCapacities[phase]) && heatCapacities[phase] > correctionHeatCapacity) {
          correctionPhase = phase;
          correctionHeatCapacity = heatCapacities[phase];
        }
      }
      if (correctionPhase < 0 || correctionHeatCapacity < 1.0e-12) {
        throw new IllegalStateException("unable to close overall energy balance");
      }
      double temperatureChange = error / correctionHeatCapacity;
      temperatureChange = Math.max(-100.0, Math.min(100.0, temperatureChange));
      state.getPhase(correctionPhase)
          .setTemperature(Math.max(1.0, state.getPhase(correctionPhase).getTemperature() + temperatureChange));
    }
    state.init(3);
    double finalError = targetEnthalpy - totalEnthalpy(state);
    if (!Double.isFinite(finalError) || Math.abs(finalError) > 1.0e-6 * Math.max(1.0, Math.abs(targetEnthalpy))) {
      throw new IllegalStateException("overall energy balance did not converge");
    }
  }

  private static double[] componentTotals(SystemInterface system, int componentCount) {
    double[] totals = new double[componentCount];
    for (int i = 0; i < componentCount; i++) {
      totals[i] = system.getPhase(0).getComponent(i).getNumberOfMolesInPhase()
          + system.getPhase(1).getComponent(i).getNumberOfMolesInPhase();
    }
    return totals;
  }

  private static double maximumComponentBalanceError(SystemInterface system, double[] initialTotals) {
    double maximumError = 0.0;
    for (int i = 0; i < initialTotals.length; i++) {
      double finalTotal = system.getPhase(0).getComponent(i).getNumberOfMolesInPhase()
          + system.getPhase(1).getComponent(i).getNumberOfMolesInPhase();
      maximumError = Math.max(maximumError, Math.abs(finalTotal - initialTotals[i]));
    }
    return maximumError;
  }

  private static double trackedMass(SystemInterface system, double[] trackedMoles, int trackedPhase) {
    double mass = 0.0;
    for (int i = 0; i < trackedMoles.length; i++) {
      mass += trackedMoles[i] * system.getPhase(trackedPhase).getComponent(i).getMolarMass();
    }
    return mass;
  }

  private static double totalEnthalpy(SystemInterface system) {
    return system.getPhase(0).getEnthalpy("J") + system.getPhase(1).getEnthalpy("J");
  }

  private static double safeHeatCapacity(double heatCapacity) {
    return Double.isFinite(heatCapacity) && Math.abs(heatCapacity) > 1.0e-12 ? Math.abs(heatCapacity) : 1.0e-12;
  }

  private static void validateClosure(LocalClosure closure) {
    for (int i = 0; i < closure.componentMolarFluxes.length; i++) {
      if (!Double.isFinite(closure.componentMolarFluxes[i])) {
        throw new IllegalStateException("non-finite Maxwell-Stefan component flux at index " + i);
      }
    }
    for (int i = 0; i < closure.interphaseHeatFluxes.length; i++) {
      if (!Double.isFinite(closure.interphaseHeatFluxes[i])) {
        throw new IllegalStateException("non-finite interphase heat flux for phase " + i);
      }
    }
  }

  private static boolean allFinite(double[] values) {
    for (int i = 0; i < values.length; i++) {
      if (!Double.isFinite(values[i])) {
        return false;
      }
    }
    return true;
  }

  private static void addWarningOnce(List<String> warnings, String warning) {
    if (!warnings.contains(warning)) {
      warnings.add(warning);
    }
  }

  private static final class LocalClosure {
    private final double[] componentMolarFluxes;
    private final double[] interphaseHeatFluxes;
    private final boolean usedHeatFluxFallback;

    private LocalClosure(double[] componentMolarFluxes, double[] interphaseHeatFluxes, boolean usedHeatFluxFallback) {
      this.componentMolarFluxes = componentMolarFluxes;
      this.interphaseHeatFluxes = interphaseHeatFluxes;
      this.usedHeatFluxFallback = usedHeatFluxFallback;
    }
  }

  private static final class LocalHydrodynamics {
    private final double gasVelocity;
    private final double liquidVelocity;
    private final double relativeVelocity;

    private LocalHydrodynamics(double gasVelocity, double liquidVelocity, double relativeVelocity) {
      this.gasVelocity = gasVelocity;
      this.liquidVelocity = liquidVelocity;
      this.relativeVelocity = relativeVelocity;
    }

    private double dispersedPhaseVelocity(boolean gasIsDispersed) {
      return gasIsDispersed ? gasVelocity : liquidVelocity;
    }
  }

  private static final class StepEstimate {
    private final double maximumDonorFraction;
    private final double maximumTemperatureChange;

    private StepEstimate(double maximumDonorFraction, double maximumTemperatureChange) {
      this.maximumDonorFraction = maximumDonorFraction;
      this.maximumTemperatureChange = maximumTemperatureChange;
    }
  }
}
