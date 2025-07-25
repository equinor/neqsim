package neqsim.fluidmechanics.flownode.twophasenode;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flownode.FlowNode;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Abstract TwoPhaseFlowNode class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class TwoPhaseFlowNode extends FlowNode {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TwoPhaseFlowNode.class);

  // public double[] molarMassTransferFlux;
  // public double[] molarMassTransfer;

  /**
   * <p>
   * Constructor for TwoPhaseFlowNode.
   * </p>
   */
  public TwoPhaseFlowNode() {}

  /**
   * <p>
   * Constructor for TwoPhaseFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public TwoPhaseFlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
    super(system, pipe);

    // gasPrandtlNumber = new
    // double[getBulkSystem().getPhases()[0].getNumberOfComponents()][getBulkSystem().getPhases()[0].getNumberOfComponents()];
    // liquidPrandtlNumber = new
    // double[getBulkSystem().getPhases()[0].getNumberOfComponents()][getBulkSystem().getPhases()[0].getNumberOfComponents()];
    // molarMassTransferFlux = new
    // double[getBulkSystem().getPhases()[0].getNumberOfComponents()];
    // molarMassTransfer = new
    // double[getBulkSystem().getPhases()[0].getNumberOfComponents()];
    getBulkSystem().initBeta();
    getBulkSystem().init_x_y();
  }

  /** {@inheritDoc} */
  @Override
  public TwoPhaseFlowNode clone() {
    TwoPhaseFlowNode clonedSystem = null;
    try {
      clonedSystem = (TwoPhaseFlowNode) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }

    // clonedSystem.molarMassTransferFlux = (double[])
    // clonedSystem.molarMassTransferFlux.clone();
    // clonedSystem.molarMassTransfer = (double[])
    // clonedSystem.molarMassTransferFlux.clone();
    clonedSystem.fluidBoundary = fluidBoundary.clone();
    return clonedSystem;
  }

  /**
   * <p>
   * initVelocity.
   * </p>
   *
   * @return a double
   */
  public double initVelocity() {
    getBulkSystem().init(1);
    getBulkSystem().initPhysicalProperties();
    molarFlowRate[1] = getBulkSystem().getPhases()[1].getNumberOfMolesInPhase();
    molarFlowRate[0] = getBulkSystem().getPhases()[0].getNumberOfMolesInPhase();
    massFlowRate[1] = molarFlowRate[1] * getBulkSystem().getPhases()[1].getMolarMass();
    massFlowRate[0] = molarFlowRate[0] * getBulkSystem().getPhases()[0].getMolarMass();
    volumetricFlowRate[0] =
        massFlowRate[0] / getBulkSystem().getPhases()[0].getPhysicalProperties().getDensity();
    volumetricFlowRate[1] =
        massFlowRate[1] / getBulkSystem().getPhases()[1].getPhysicalProperties().getDensity();
    velocity[0] = volumetricFlowRate[0] / (phaseFraction[0] * pipe.getArea());
    velocity[1] = volumetricFlowRate[1] / (phaseFraction[1] * pipe.getArea());
    // System.out.println("velocity " + velocity[0] + " velocity " +velocity[1]);
    return velocity[1];
  }

  /** {@inheritDoc} */
  @Override
  public void initFlowCalc() {
    initVelocity();
    init();

    phaseFraction[0] = getBulkSystem().getBeta();
    phaseFraction[1] = 1.0 - phaseFraction[0];
    double f = 0;

    double fOld = 0;
    double betaOld = 0;
    // double df = 0
    int iterations = 0;
    double step = 100.0;

    do {
      iterations++;
      fOld = f;

      initVelocity();
      init();

      double Fg = 0.5 * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
          * wallFrictionFactor[0] * Math.pow(velocity[0], 2.0) * wallContactLength[0]
          / (pipe.getArea() * 4.0);
      double Fl = 0.5 * bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
          * wallFrictionFactor[1] * Math.pow(velocity[1], 2.0) * wallContactLength[1]
          / (pipe.getArea() * 4.0);
      double Fi = 0.5 * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
          * interphaseFrictionFactor[0] * Math.pow(velocity[0] - velocity[1], 2.0)
          * interphaseContactLength[0] / (pipe.getArea() * 4.0);

      f = -phaseFraction[0] * Fl + (1 - phaseFraction[0]) * Fg + Fi
          + (1.0 - phaseFraction[0]) * phaseFraction[0]
              * (bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
                  - bulkSystem.getPhases()[0].getPhysicalProperties().getDensity())
              * gravity * inclination;

      /*
       * df = -Fl - Fg + (bulkSystem.getPhases()[1].getPhysicalProperties().getDensity() -
       * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()) * gravity * inclination -
       * Math.pow(phaseFraction[0], 2.0)
       * (bulkSystem.getPhases()[1].getPhysicalProperties().getDensity() -
       * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()) gravity * inclination;
       */

      if (f > 0) {
        phaseFraction[0] += (betaOld - phaseFraction[0]);
        if (fOld < 0) {
          step *= 5.0;
        }
      } else {
        betaOld = phaseFraction[0];
        phaseFraction[0] -= phaseFraction[0] / step;
      }
      phaseFraction[1] = 1.0 - phaseFraction[0];
      // System.out.println("f " + f + " iterations " + iterations + " beta " + phaseFraction[0]);
    }
    // while (Math.abs(f) > 1e-6 && iterations < 100);
    while (Math.abs((f - fOld) / f) > 1e-8 && iterations < 100);

    if (iterations == 10000) {
      System.out.println("error in void init calc");
    }
    // System.out.println("f " + f + " iterations " + iterations + " beta " +
    // phaseFraction[0]);
    this.init();
  }

  /**
   * <p>
   * calcHydraulicDiameter.
   * </p>
   *
   * @return a double
   */
  public double calcHydraulicDiameter() {
    hydraulicDiameter[0] = 4.0 * phaseFraction[0] * pipe.getArea()
        / (wallContactLength[0] + interphaseContactLength[0]);
    hydraulicDiameter[1] = 4.0 * phaseFraction[1] * pipe.getArea() / wallContactLength[1];
    return hydraulicDiameter[0];
  }

  /**
   * <p>
   * calcReynoldNumber.
   * </p>
   *
   * @return a double
   */
  public double calcReynoldNumber() {
    reynoldsNumber[1] = velocity[1] * hydraulicDiameter[1]
        * bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
        / bulkSystem.getPhases()[1].getPhysicalProperties().getViscosity();
    reynoldsNumber[0] = velocity[0] * hydraulicDiameter[0]
        * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
        / bulkSystem.getPhases()[0].getPhysicalProperties().getViscosity();
    return reynoldsNumber[1];
  }

  /**
   * <p>
   * calcWallFrictionFactor.
   * </p>
   *
   * @return a double
   */
  public double calcWallFrictionFactor() {
    for (int i = 0; i < 2; i++) {
      wallFrictionFactor[i] = Math.pow(
          (1.0 / (-1.8 * Math.log10(6.9 / getReynoldsNumber(i)
              * Math.pow(pipe.getRelativeRoughnes(this.getHydraulicDiameter(i)) / 3.7, 1.11)))),
          2.0);
    }
    return wallFrictionFactor[0];
  }

  /** {@inheritDoc} */
  @Override
  public void setFluxes(double[] dn) {
    for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
      getBulkSystem().getPhases()[0].addMoles(i, -dn[i]);
      getBulkSystem().getPhases()[1].addMoles(i, +dn[i]);

      getInterphaseSystem().getPhases()[0].addMoles(i, -dn[i]);
      getInterphaseSystem().getPhases()[1].addMoles(i, +dn[i]);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void updateMolarFlow() {
    for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
      for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
        if (molarFlowRate[phaseNum] > 1e-100) {
          getBulkSystem().getPhase(phaseNum).addMoles(i,
              (getBulkSystem().getPhase(phaseNum).getComponent(i).getx() * (molarFlowRate[phaseNum]
                  - getBulkSystem().getPhase(phaseNum).getNumberOfMolesInPhase())));
        }
      }
    }
    getBulkSystem().initBeta();
    getBulkSystem().init_x_y();
    getBulkSystem().init(1);
  }

  /**
   * <p>
   * calcContactLength.
   * </p>
   *
   * @return a double
   */
  public double calcContactLength() {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    super.init();
    massFlowRate[0] =
        velocity[0] * getBulkSystem().getPhases()[0].getPhysicalProperties().getDensity()
            * pipe.getArea() * phaseFraction[0];
    massFlowRate[1] =
        velocity[1] * getBulkSystem().getPhases()[1].getPhysicalProperties().getDensity()
            * pipe.getArea() * phaseFraction[1];
    molarFlowRate[0] = massFlowRate[0] / getBulkSystem().getPhases()[0].getMolarMass();
    molarFlowRate[1] = massFlowRate[1] / getBulkSystem().getPhases()[1].getMolarMass();
    superficialVelocity[0] = velocity[0] * phaseFraction[0];
    superficialVelocity[1] = velocity[1] * phaseFraction[1];
    volumetricFlowRate[0] = superficialVelocity[0] * pipe.getArea();
    volumetricFlowRate[1] = superficialVelocity[1] * pipe.getArea();
    this.updateMolarFlow();

    this.calcHydraulicDiameter();
    this.calcReynoldNumber();
    interphaseContactArea = this.calcGasLiquidContactArea();

    wallFrictionFactor[0] = interphaseTransportCoefficient.calcWallFrictionFactor(0, this);
    wallFrictionFactor[1] = interphaseTransportCoefficient.calcWallFrictionFactor(1, this);

    interphaseFrictionFactor[0] =
        interphaseTransportCoefficient.calcInterPhaseFrictionFactor(0, this);
    interphaseFrictionFactor[1] =
        interphaseTransportCoefficient.calcInterPhaseFrictionFactor(0, this);
  }

  /**
   * <p>
   * calcGasLiquidContactArea.
   * </p>
   *
   * @return a double
   */
  public double calcGasLiquidContactArea() {
    interphaseContactArea = pipe.getNodeLength() * interphaseContactLength[0];
    return interphaseContactArea;
  }

  /** {@inheritDoc} */
  @Override
  public void calcFluxes() {
    if (bulkSystem.isChemicalSystem()) {
      // getBulkSystem().getChemicalReactionOperations().setSystem(getBulkSystem());
      // getOperations().chemicalEquilibrium();
    }
    fluidBoundary.solve();
    fluidBoundary.calcFluxes();
  }

  /** {@inheritDoc} */
  @Override
  public void update() {
    // System.out.println("reac heat " +
    // getBulkSystem().getChemicalReactionOperations().getDeltaReactionHeat());
    double heatFluxGas = getFluidBoundary().getInterphaseHeatFlux(0);
    // getInterphaseTransportCoefficient().calcInterphaseHeatTransferCoefficient(0,
    // getPrandtlNumber(0),
    // this) *
    // (getInterphaseSystem().getPhase(0).getTemperature()
    // -
    // getBulkSystem().getPhase(0).getTemperature())
    // *
    // getInterphaseContactArea();

    double heatFluxLiquid = getFluidBoundary().getInterphaseHeatFlux(1);
    // getInterphaseTransportCoefficient().calcInterphaseHeatTransferCoefficient(1,
    // getPrandtlNumber(1),
    // this) *
    // (getInterphaseSystem().getPhase(1).getTemperature()
    // -
    // getBulkSystem().getPhase(1).getTemperature())
    // *
    // getInterphaseContactArea();
    // System.out.println("heat flux local " + heatFluxLiquid);
    // double liquid_dT =
    // -this.flowDirection[1]*heatFlux/1000.0/getBulkSystem().getPhase(1).getCp();

    double liquid_dT = this.flowDirection[1] * heatFluxLiquid * getGeometry().getNodeLength()
        / getVelocity(1) / getBulkSystem().getPhase(1).getCp();
    double gas_dT = this.flowDirection[0] * heatFluxGas * getGeometry().getNodeLength()
        / getVelocity(0) / getBulkSystem().getPhase(0).getCp();
    liquid_dT -= 0 * getInterphaseTransportCoefficient().calcWallHeatTransferCoefficient(1, this)
        * (getBulkSystem().getPhase(1).getTemperature() - pipe.getInnerWallTemperature())
        * getWallContactLength(1) * getGeometry().getNodeLength()
        / getBulkSystem().getPhase(1).getCp();
    gas_dT -= 0 * getInterphaseTransportCoefficient().calcWallHeatTransferCoefficient(0, this)
        * (getBulkSystem().getPhase(0).getTemperature() - pipe.getInnerWallTemperature())
        * getWallContactLength(0) * getGeometry().getNodeLength()
        / getBulkSystem().getPhase(0).getCp();
    // liquid_dT +=
    // getInterphaseTransportCoefficient().calcWallHeatTransferCoefficient(0, this)*
    // (getBulkSystem().getPhase(0).getTemperature()-pipe.getOuterTemperature())*
    // getWallContactLength(0) *
    // getGeometry().getNodeLength()/getBulkSystem().getPhase(0).getCp();
    // liquid_dT +=
    // 10*this.flowDirection[1]*getBulkSystem().getChemicalReactionOperations().getDeltaReactionHeat()/getBulkSystem().getPhase(1).getCp();
    // System.out.println("Cp " + getBulkSystem().getPhase(1).getCp());
    // System.out.println("liq dT " + liquid_dT);
    // System.out.println("gas dT " + gas_dT);
    double fluxwallinternal =
        getInterphaseTransportCoefficient().calcWallHeatTransferCoefficient(1, this)
            * (getBulkSystem().getPhase(1).getTemperature() - pipe.getInnerWallTemperature())
            * getWallContactLength(1) * getGeometry().getNodeLength()
            + getInterphaseTransportCoefficient().calcWallHeatTransferCoefficient(0, this)
                * (getBulkSystem().getPhase(0).getTemperature() - pipe.getInnerWallTemperature())
                * getWallContactLength(0) * getGeometry().getNodeLength();

    double JolprK = 3.14 * 0.2032 * 0.0094 * getGeometry().getNodeLength() * 7500 * 500;
    double fluxOut = -50.0 * 3.14 * (0.2032 + 0.01) * getGeometry().getNodeLength()
        * (pipe.getInnerWallTemperature() - pipe.getSurroundingEnvironment().getTemperature());
    double dTwall = 0 * (fluxOut + fluxwallinternal) / JolprK;
    pipe.setInnerWallTemperature(pipe.getInnerWallTemperature() + dTwall);

    getBulkSystem().getPhase(1)
        .setTemperature(getBulkSystem().getPhase(1).getTemperature() + liquid_dT);
    getBulkSystem().getPhase(0)
        .setTemperature(getBulkSystem().getPhase(0).getTemperature() + gas_dT);

    // System.out.println("pipe wall temperature " + pipe.getTemperature());
    // System.out.println("liquid velocity " + getSuperficialVelocity(1));
    // System.out.println("gas velocity " + getSuperficialVelocity(0));

    for (int componentNumber = 0; componentNumber < getBulkSystem().getPhases()[0]
        .getNumberOfComponents(); componentNumber++) {
      double liquidMolarRate =
          getFluidBoundary().getInterphaseMolarFlux(componentNumber) * getInterphaseContactArea(); // getInterphaseContactLength(0)*getGeometry().getNodeLength();

      double gasMolarRate =
          -getFluidBoundary().getInterphaseMolarFlux(componentNumber) * getInterphaseContactArea(); // getInterphaseContactLength(0)*getGeometry().getNodeLength();

      // System.out.println("liquidMolarRate" + liquidMolarRate);
      getBulkSystem().getPhase(0).addMoles(componentNumber, this.flowDirection[0] * gasMolarRate);
      getBulkSystem().getPhase(1).addMoles(componentNumber,
          this.flowDirection[1] * liquidMolarRate);
    }

    getBulkSystem().initBeta();
    getBulkSystem().init_x_y();
    getBulkSystem().init(3);

    if (bulkSystem.isChemicalSystem()) {
      getBulkSystem().getChemicalReactionOperations().setSystem(getBulkSystem());
      getOperations().chemicalEquilibrium();
    }
  }

  /**
   * <p>
   * update.
   * </p>
   *
   * @param deltaTime a double
   */
  public void update(double deltaTime) {
    for (int componentNumber = 0; componentNumber < getBulkSystem().getPhases()[0]
        .getNumberOfComponents(); componentNumber++) {
      double liquidMolarRate =
          getFluidBoundary().getInterphaseMolarFlux(componentNumber) * getInterphaseContactArea(); // getInterphaseContactLength(0)*getGeometry().getNodeLength();

      double gasMolarRate =
          -getFluidBoundary().getInterphaseMolarFlux(componentNumber) * getInterphaseContactArea(); // getInterphaseContactLength(0)*getGeometry().getNodeLength();

      getBulkSystem().getPhase(0).addMoles(componentNumber,
          this.flowDirection[0] * gasMolarRate * deltaTime);
      getBulkSystem().getPhase(1).addMoles(componentNumber,
          this.flowDirection[1] * liquidMolarRate * deltaTime);
    }

    getBulkSystem().initBeta();
    getBulkSystem().init_x_y();
    getBulkSystem().initProperties();
  }
}
