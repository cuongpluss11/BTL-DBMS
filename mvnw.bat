@echo off
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot"
set "MAVEN_HOME=C:\Users\ADMIN\.m2\wrapper\dists\apache-maven-3.9.6\apache-maven-3.9.6"
set "LAUNCHER_JAR=%MAVEN_HOME%\boot\plexus-classworlds-2.7.0.jar"
set "M2_CONF=%MAVEN_HOME%\bin\m2.conf"
set "BASEDIR=%~dp0"

"%JAVA_HOME%\bin\java.exe" ^
  "-Dclassworlds.conf=%M2_CONF%" ^
  "-Dmaven.home=%MAVEN_HOME%" ^
  "-Dmaven.multiModuleProjectDirectory=%BASEDIR%" ^
  -classpath "%LAUNCHER_JAR%" ^
  org.codehaus.plexus.classworlds.launcher.Launcher %*
