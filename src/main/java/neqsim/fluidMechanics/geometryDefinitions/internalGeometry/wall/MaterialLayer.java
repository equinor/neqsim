package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.wall;

/**
 * <p>
 * MaterialLayer class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class MaterialLayer {
  /**
   * <p>
   * Getter for the field <code>density</code>.
   * </p>
   *
   * @return the density
   */
  public double getDensity() {
    return density;
  }

  /**
   * <p>
   * Setter for the field <code>density</code>.
   * </p>
   *
   * @param density the density to set
   */
  public void setDensity(double density) {
    this.density = density;
  }

  /**
   * <p>
   * Getter for the field <code>thickness</code>.
   * </p>
   *
   * @return the thickness
   */
  public double getThickness() {
    return thickness;
  }

  /**
   * <p>
   * Setter for the field <code>thickness</code>.
   * </p>
   *
   * @param thickness the thickness to set
   */
  public void setThickness(double thickness) {
    this.thickness = thickness;
  }

  /**
   * <p>
   * Getter for the field <code>conductivity</code>.
   * </p>
   *
   * @return the conductivity
   */
  public double getConductivity() {
    return conductivity;
  }

  /**
   * <p>
   * Setter for the field <code>conductivity</code>.
   * </p>
   *
   * @param conductivity the conductivity to set
   */
  public void setConductivity(double conductivity) {
    this.conductivity = conductivity;
  }

  /**
   * <p>
   * getHeatTransferCoefficient.
   * </p>
   *
   * @return a double
   */
  public double getHeatTransferCoefficient() {
    return conductivity / thickness;
  }

  /**
   * <p>
   * getCv.
   * </p>
   *
   * @return the Cv
   */
  public double getCv() {
    return Cv;
  }

  /**
   * <p>
   * setCv.
   * </p>
   *
   * @param Cv the Cv to set
   */
  public void setCv(double Cv) {
    this.Cv = Cv;
  }

  private double thickness = 0.01;
  private double conductivity = 1.0;
  private double Cv = 10.0;
  private double density = 2000.0;
  private double insideTemperature = 298.15;
  private double outsideTemperature = 298.15;

  String material = null;

  /**
   * <p>
   * Constructor for MaterialLayer.
   * </p>
   *
   * @param material a {@link java.lang.String} object
   * @param thickness a double
   */
  public MaterialLayer(String material, double thickness) {
    this.thickness = thickness;
    this.material = material;
  }

  /**
   * <p>
   * Getter for the field <code>insideTemperature</code>.
   * </p>
   *
   * @return the insideTemperature
   */
  public double getInsideTemperature() {
    return insideTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>insideTemperature</code>.
   * </p>
   *
   * @param insideTemperature the insideTemperature to set
   */
  public void setInsideTemperature(double insideTemperature) {
    this.insideTemperature = insideTemperature;
  }

  /**
   * <p>
   * Getter for the field <code>outsideTemperature</code>.
   * </p>
   *
   * @return the outsideTemperature
   */
  public double getOutsideTemperature() {
    return outsideTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>outsideTemperature</code>.
   * </p>
   *
   * @param outsideTemperature the outsideTemperature to set
   */
  public void setOutsideTemperature(double outsideTemperature) {
    this.outsideTemperature = outsideTemperature;
  }
}
