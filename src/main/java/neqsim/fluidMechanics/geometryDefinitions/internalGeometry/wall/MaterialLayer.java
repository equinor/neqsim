/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.wall;

/**
 *
 * @author ESOL
 */
public class MaterialLayer {

    private static final long serialVersionUID = 1000;

 

    /**
     * @return the density
     */
    public double getDensity() {
        return density;
    }

    /**
     * @param density the density to set
     */
    public void setDensity(double density) {
        this.density = density;
    }

    /**
     * @return the thickness
     */
    public double getThickness() {
        return thickness;
    }

    /**
     * @param thickness the thickness to set
     */
    public void setThickness(double thickness) {
        this.thickness = thickness;
    }

    /**
     * @return the conductivity
     */
    public double getConductivity() {
        return conductivity;
    }

    /**
     * @param conductivity the conductivity to set
     */
    public void setConductivity(double conductivity) {
        this.conductivity = conductivity;
    }

    public double getHeatTransferCoefficient() {
        return conductivity / thickness;
    }

    /**
     * @return the Cv
     */
    public double getCv() {
        return Cv;
    }

    /**
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

    public MaterialLayer(String material, double thickness) {
        this.thickness = thickness;
        this.material = material;
    }

    /**
     * @return the insideTemperature
     */
    public double getInsideTemperature() {
        return insideTemperature;
    }

    /**
     * @param insideTemperature the insideTemperature to set
     */
    public void setInsideTemperature(double insideTemperature) {
        this.insideTemperature = insideTemperature;
    }

    /**
     * @return the outsideTemperature
     */
    public double getOutsideTemperature() {
        return outsideTemperature;
    }

    /**
     * @param outsideTemperature the outsideTemperature to set
     */
    public void setOutsideTemperature(double outsideTemperature) {
        this.outsideTemperature = outsideTemperature;
    }

}
