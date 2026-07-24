@echo off
set "M2_HOME=C:\Users\pdup\AppData\Roaming\Code\User\globalStorage\pleiades.java-extension-pack-jdk\maven\latest"
java -classpath "%M2_HOME%\boot\plexus-classworlds-2.9.0.jar" "-Dclassworlds.conf=%M2_HOME%\bin\m2.conf" "-Dmaven.home=%M2_HOME%" "-Dmaven.multiModuleProjectDirectory=%CD%" org.codehaus.plexus.classworlds.launcher.Launcher %* > _mvn_full_output.log 2>&1
set MVN_EXIT=%ERRORLEVEL%
echo ===MAVEN_EXIT_CODE=%MVN_EXIT%===
findstr /C:"Tests run:" _mvn_full_output.log
findstr /C:"BUILD " _mvn_full_output.log
findstr /C:"[ERROR]" _mvn_full_output.log
