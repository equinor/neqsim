@echo off
set "M2_HOME=C:\Users\pdup\AppData\Roaming\Code\User\globalStorage\pleiades.java-extension-pack-jdk\maven\latest"
java -classpath "%M2_HOME%\boot\plexus-classworlds-2.9.0.jar" "-Dclassworlds.conf=%M2_HOME%\bin\m2.conf" "-Dmaven.home=%M2_HOME%" "-Dmaven.multiModuleProjectDirectory=%CD%" org.codehaus.plexus.classworlds.launcher.Launcher %*
