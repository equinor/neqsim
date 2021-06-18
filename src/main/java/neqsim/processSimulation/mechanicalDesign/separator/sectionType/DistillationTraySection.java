/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package neqsim.processSimulation.mechanicalDesign.separator.sectionType;

import neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection;

/**
 *
 * @author esol
 */
public class DistillationTraySection extends SepDesignSection {

    private static final long serialVersionUID = 1000;

    public DistillationTraySection(SeparatorSection separatorSection) {
        super(separatorSection);
    }

    @Override
	public void calcDesign() {

        double vesselDiameter = separatorSection.getSeparator().getMechanicalDesign().getOuterDiameter() * 1e3;
        if (vesselDiameter <= 616) {
            totalWeight = 32.0;
        } else if (vesselDiameter <= 770) {
            totalWeight = 48.0;
        } else if (vesselDiameter <= 925) {
            totalWeight = 73.0;
        } else if (vesselDiameter <= 1078) {
            totalWeight = 95.0;
        } else if (vesselDiameter <= 1232) {
            totalWeight = 127.0;
        } else if (vesselDiameter <= 1386) {
            totalWeight = 159.0;
        } else if (vesselDiameter <= 1540) {
            totalWeight = 200.0;
        } else if (vesselDiameter <= 1694) {
            totalWeight = 236.0;
        } else if (vesselDiameter <= 1848) {
            totalWeight = 284.0;
        } else if (vesselDiameter <= 2002) {
            totalWeight = 331.0;
        } else if (vesselDiameter <= 2156) {
            totalWeight = 386.0;
        } else if (vesselDiameter <= 2310) {
            totalWeight = 440.0;
        } else if (vesselDiameter <= 2464) {
            totalWeight = 504.0;
        } else if (vesselDiameter <= 2618) {
            totalWeight = 563.0;
        } else if (vesselDiameter <= 2772) {
            totalWeight = 635.0;
        } else if (vesselDiameter <= 2926) {
            totalWeight = 703.0;
        } else if (vesselDiameter <= 3080) {
            totalWeight = 794.0;
        } else if (vesselDiameter <= 3234) {
            totalWeight = 862.0;
        } else {
            totalWeight = 900.0;
        }

        totalHeight = 0.6;
    }
}
