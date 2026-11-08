$javaCmd = "C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot\bin\java.exe"
if (!(Test-Path $javaCmd)) {
    $javaCmd = "java"
}

$cp = "$PSScriptRoot\target\classes;$PSScriptRoot\lib\*"

& $javaCmd "-Dfile.encoding=UTF-8" -cp $cp com.btl.dbms.Main @args
