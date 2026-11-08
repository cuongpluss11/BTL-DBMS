$javaCmd = "C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot\bin\java.exe"
$m2 = "C:\Users\ADMIN\.m2\repository"

$cp = @(
  "$PSScriptRoot\target\classes",
  "$m2\com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar",
  "$m2\com\zaxxer\HikariCP\5.0.1\HikariCP-5.0.1.jar",
  "$m2\org\slf4j\slf4j-api\2.0.13\slf4j-api-2.0.13.jar",
  "$m2\ch\qos\logback\logback-classic\1.5.6\logback-classic-1.5.6.jar",
  "$m2\ch\qos\logback\logback-core\1.5.6\logback-core-1.5.6.jar",
  "$m2\org\hdrhistogram\HdrHistogram\2.2.2\HdrHistogram-2.2.2.jar",
  "$m2\org\mongodb\mongodb-driver-sync\5.1.0\mongodb-driver-sync-5.1.0.jar",
  "$m2\org\mongodb\mongodb-driver-core\5.1.0\mongodb-driver-core-5.1.0.jar",
  "$m2\org\mongodb\bson\5.1.0\bson-5.1.0.jar",
  "$m2\redis\clients\jedis\5.1.2\jedis-5.1.2.jar",
  "$m2\org\apache\commons\commons-pool2\2.12.0\commons-pool2-2.12.0.jar",
  "$m2\com\fasterxml\jackson\core\jackson-databind\2.17.1\jackson-databind-2.17.1.jar",
  "$m2\com\fasterxml\jackson\core\jackson-core\2.17.1\jackson-core-2.17.1.jar",
  "$m2\com\fasterxml\jackson\core\jackson-annotations\2.17.1\jackson-annotations-2.17.1.jar"
) -join ";"

& $javaCmd "-Dfile.encoding=UTF-8" -cp $cp com.btl.dbms.Main @args
