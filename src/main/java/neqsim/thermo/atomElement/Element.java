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
 * Element.java
 *
 * Created on 4. februar 2001, 22:11
 */

package neqsim.thermo.atomElement;

import java.util.*;
import neqsim.thermo.ThermodynamicConstantsInterface;
import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class Element extends Object implements ThermodynamicConstantsInterface, java.io.Serializable {
	private static final long serialVersionUID = 1000;
	String[] nameArray;
	double[] coefArray;
	static Logger logger = LogManager.getLogger(Element.class);

	/** Creates new Element */
	public Element() {
	}

	public Element(String name) {

		ArrayList<String> names = new ArrayList<String>();
		ArrayList<String> stocCoef = new ArrayList<String>();
		neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
		try {
			java.sql.ResultSet dataSet = database
					.getResultSet(("SELECT * FROM element WHERE componentname='" + name + "'"));
			dataSet.next();
			// System.out.println("comp name " + dataSet.getString("componentname"));
			do {
				names.add(dataSet.getString("atomelement").trim());
				// System.out.println("name " + dataSet.getString("atomelement"));
				stocCoef.add(dataSet.getString("number"));
			} while (dataSet.next());

			nameArray = new String[names.size()];
			coefArray = new double[nameArray.length];
			for (int i = 0; i < nameArray.length; i++) {
				coefArray[i] = Double.parseDouble((String) stocCoef.get(i));
				nameArray[i] = (String) names.get(i);
			}
			dataSet.close();
			database.getConnection().close();
		} catch (Exception e) {
			try {
				database.getConnection().close();
			} catch (Exception ex) {
				logger.error(ex);
			}
			String err = e.toString();
			logger.error(err);
			// System.out.println(err);
		}
	}

	public String[] getElementNames() {
		return nameArray;
	}

	public double[] getElementCoefs() {
		return coefArray;
	}

}
