/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.costEstimation;

import java.io.Serializable;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 *
 * @author esol
 */
public class UnitCostEstimateBaseClass implements Serializable {

    private static final long serialVersionUID = 1000;

    private double costPerWeightUnit = 1000.0;
    public MechanicalDesign mechanicalEquipment = null;

    public UnitCostEstimateBaseClass() {

    }

    public UnitCostEstimateBaseClass(MechanicalDesign mechanicalEquipment) {
        this.mechanicalEquipment = mechanicalEquipment;
    }

    /**
     * @return the totaltCost
     */
    public double getTotaltCost() {
        return this.mechanicalEquipment.getWeightTotal() * costPerWeightUnit;
    }

}
