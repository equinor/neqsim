package neqsim.process.equipment.pipeline.evaporation;

/** One accepted axial state from a pipeline evaporation or gas-dissolution calculation. */
public class EvaporationProfilePoint {
  private final double distance;
  private final double remainingInjectedLiquidFraction;
  private final double characteristicLiquidSize;
  private final double gasTemperature;
  private final double liquidTemperature;
  private final double interfacialAreaPerLength;
  private final double totalMolarFlux;
  private final double[] componentMolarFluxes;
  private final double gasVelocity;
  private final double liquidVelocity;
  private final double dispersedPhaseRelativeVelocity;

  /**
   * Constructor.
   *
   * @param distance axial distance in m
   * @param remainingInjectedLiquidFraction remaining injected-liquid mass fraction
   * @param characteristicLiquidSize droplet diameter or film thickness in m
   * @param gasTemperature gas temperature in K
   * @param liquidTemperature liquid temperature in K
   * @param interfacialAreaPerLength interfacial area per axial length in m2/m
   * @param totalMolarFlux total gas-to-liquid molar flux in mol/(m2 s)
   * @param componentMolarFluxes gas-to-liquid component fluxes in mol/(m2 s)
   */
  public EvaporationProfilePoint(double distance, double remainingInjectedLiquidFraction,
      double characteristicLiquidSize, double gasTemperature, double liquidTemperature, double interfacialAreaPerLength,
      double totalMolarFlux, double[] componentMolarFluxes) {
    this(distance, remainingInjectedLiquidFraction, characteristicLiquidSize, gasTemperature, liquidTemperature,
        interfacialAreaPerLength, totalMolarFlux, componentMolarFluxes, Double.NaN, Double.NaN, Double.NaN);
  }

  /** Constructor including the local slip state. */
  public EvaporationProfilePoint(double distance, double remainingInjectedLiquidFraction,
      double characteristicLiquidSize, double gasTemperature, double liquidTemperature, double interfacialAreaPerLength,
      double totalMolarFlux, double[] componentMolarFluxes, double gasVelocity, double liquidVelocity,
      double dispersedPhaseRelativeVelocity) {
    this.distance = distance;
    this.remainingInjectedLiquidFraction = remainingInjectedLiquidFraction;
    this.characteristicLiquidSize = characteristicLiquidSize;
    this.gasTemperature = gasTemperature;
    this.liquidTemperature = liquidTemperature;
    this.interfacialAreaPerLength = interfacialAreaPerLength;
    this.totalMolarFlux = totalMolarFlux;
    this.componentMolarFluxes = componentMolarFluxes.clone();
    this.gasVelocity = gasVelocity;
    this.liquidVelocity = liquidVelocity;
    this.dispersedPhaseRelativeVelocity = dispersedPhaseRelativeVelocity;
  }

  /** @return axial distance in m */
  public double getDistance() {
    return distance;
  }

  /** @return remaining fraction of the initially injected liquid mass */
  public double getRemainingInjectedLiquidFraction() {
    return remainingInjectedLiquidFraction;
  }

  /**
   * Return the remaining fraction of the initially tracked dispersed-phase mass.
   *
   * <p>
   * This is injected liquid for evaporation and injected gas for dissolution.
   * </p>
   *
   * @return remaining tracked mass fraction
   */
  public double getRemainingTrackedPhaseFraction() {
    return remainingInjectedLiquidFraction;
  }

  /** @return droplet diameter or film thickness in m */
  public double getCharacteristicLiquidSize() {
    return characteristicLiquidSize;
  }

  /** @return droplet diameter, bubble diameter, or film thickness in m */
  public double getCharacteristicDispersedPhaseSize() {
    return characteristicLiquidSize;
  }

  /** @return gas temperature in K */
  public double getGasTemperature() {
    return gasTemperature;
  }

  /** @return liquid temperature in K */
  public double getLiquidTemperature() {
    return liquidTemperature;
  }

  /** @return interfacial area per axial length in m2/m */
  public double getInterfacialAreaPerLength() {
    return interfacialAreaPerLength;
  }

  /** @return total molar flux, positive from gas to liquid, in mol/(m2 s) */
  public double getTotalMolarFlux() {
    return totalMolarFlux;
  }

  /** @return component molar fluxes, positive from gas to liquid, in mol/(m2 s) */
  public double[] getComponentMolarFluxes() {
    return componentMolarFluxes.clone();
  }

  /** @return local actual gas velocity in m/s */
  public double getGasVelocity() {
    return gasVelocity;
  }

  /** @return local actual liquid velocity in m/s */
  public double getLiquidVelocity() {
    return liquidVelocity;
  }

  /** @return total particle-relative speed used in transfer correlations in m/s */
  public double getDispersedPhaseRelativeVelocity() {
    return dispersedPhaseRelativeVelocity;
  }
}
