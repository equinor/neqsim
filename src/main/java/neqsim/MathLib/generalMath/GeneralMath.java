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
/*
 * newtonRhapson.java
 *
 * Created on 15. juli 2000, 17:43
 */

package neqsim.MathLib.generalMath;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class GeneralMath extends Object implements java.io.Serializable {

    private static final long serialVersionUID = 1000;

    /** Creates new newtonRhapson */
    public GeneralMath() {
    }

    public static double log10(double var) {
        return Math.log(var) / Math.log(10);
    }

}
