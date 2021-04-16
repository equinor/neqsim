/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.costEstimation.separator;

import neqsim.processSimulation.costEstimation.UnitCostEstimateBaseClass;
import neqsim.processSimulation.mechanicalDesign.separator.SeparatorMechanicalDesign;

/**
 *
 * @author ESOL
 */
public class SeparatorCostEstimate extends UnitCostEstimateBaseClass {

    private static final long serialVersionUID = 1000;

    public SeparatorCostEstimate(SeparatorMechanicalDesign mechanicalEquipment) {
        super(mechanicalEquipment);
    }

    public double getTotaltCost() {
        SeparatorMechanicalDesign sepMecDesign = (SeparatorMechanicalDesign) mechanicalEquipment;

        sepMecDesign.getWeightTotal();
        sepMecDesign.getVolumeTotal();

        return this.mechanicalEquipment.getWeightTotal();
    }
}
