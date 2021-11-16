/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

/*
 * ReferencePotComperator.java
 *
 * Created on 11. april 2001, 20:21
 */

package neqsim.chemicalReactions.chemicalEquilibriaum;

import java.util.Comparator;
import neqsim.thermo.component.ComponentInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ReferencePotComparator implements Comparator, java.io.Serializable {
    private static final long serialVersionUID = 1000;

    @Override
    public int compare(Object o1, Object o2) {
        double v1 = ((ComponentInterface) o1).getReferencePotential();
        double v2 = ((ComponentInterface) o2).getReferencePotential();

        int ans = v1 >= v2 ? 1 : 0;

        return ans;
    }
}
