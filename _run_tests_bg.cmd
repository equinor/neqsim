@echo off
echo BUILD STARTED AT: %DATE% %TIME%
set "M2_HOME=C:\Users\pdup\AppData\Roaming\Code\User\globalStorage\pleiades.java-extension-pack-jdk\maven\latest"
java -classpath "%M2_HOME%\boot\plexus-classworlds-2.9.0.jar" "-Dclassworlds.conf=%M2_HOME%\bin\m2.conf" "-Dmaven.home=%M2_HOME%" "-Dmaven.multiModuleProjectDirectory=%~1" org.codehaus.plexus.classworlds.launcher.Launcher test "-Dtest=neqsim.thermo.util.componentmapping.GcComponentMapTest,neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops.PhaseEnvelopeZeroFractionRegressionTest" -DfailIfNoTests=false >> "%~1\_mvn_full_output.log" 2>&1
echo BUILD FINISHED AT: %DATE% %TIME% EXIT CODE: %ERRORLEVEL% >> "%~1\_mvn_full_output.log"
