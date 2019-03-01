NeqSim
NeqSim is an abbreviation for Non-equilibrium Simulator. It is a tool for thermodynamic and fluid-mechanic simulations. 
NeqSim enables simulation of the most common unit operations you find in the petroleum industry. 
The basis for NeqSim is a library of fundamental mathematical models related to phase behavior and physical properties of oil and gas.  NeqSim is easilly extended with new methods.
NeqSim is built upon six base modules:
1. Thermodynamic Routines
2. Physical Properties Routines
3. Fluid Mechanic Routines
4. Unit Operations
5. Chemical Reactions Routines
6. Parameter Fitting Routines
7. Process simulation routines


File System and examples:
neqsim/: main library with all modules 

neqsim/thermo/: Main path for thermodynamic routines
neqsim/thermo/util/examples/: examples of use of Thermodynamic Models and Routines

neqsim/thermodynamicOperation: Main path for flash routines (TPflash, phase envelopes, etc.)
neqsim/thermodynamicOperation/util/example/: examples of use of thermodynamic operations (eg. flash calculations etc.)

neqsim/physicalProperties: Main path for Physical Property methods
neqsim/physicalProperties/util/examples/: Examples of use of physical properties calculations

neqsim/physicalProperties: Main path for Physical Property methods
neqsim/physicalProperties/util/examples/: Examples of use of physical properties calculations

neqsim/processSimulation: Main path for Process Simulation Calculations
neqsim/processSimulation/util/examples/: Examples of use of Process Simulation calculations

changelog.txt : History of what changed between each version.
license.txt: license document


Third party open source projects used by NeqSim
EJML - for liner algebra calculations (http://ejml.org)
JAMA - for linear algebra calculations (https://math.nist.gov/javanumerics/jama/)
Commons Math - for various mathematical operations (http://commons.apache.org/proper/commons-math/)
JFreeChart - for displaying charts (http://www.jfree.org/jfreechart/)
Ucanaccess - for working with the local MSAccess database (http://ucanaccess.sourceforge.net/site.html)

How to build NeqSim from source code
NeqSim can easylly be built using the Maven build system (https://maven.apache.org/). All NeqSim build dependencies are given in the pom.xml file.


