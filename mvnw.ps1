$javaHome = "C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot"
$mavenHome = "C:\Users\ADMIN\.m2\wrapper\dists\apache-maven-3.9.6\apache-maven-3.9.6"
$launcherJar = "$mavenHome\boot\plexus-classworlds-2.7.0.jar"
$m2Conf = "$mavenHome\bin\m2.conf"
$basedir = $PSScriptRoot

& "$javaHome\bin\java.exe" `
  "-Dclassworlds.conf=$m2Conf" `
  "-Dmaven.home=$mavenHome" `
  "-Dmaven.multiModuleProjectDirectory=$basedir" `
  -classpath $launcherJar `
  org.codehaus.plexus.classworlds.launcher.Launcher @args
