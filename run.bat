@echo off
set "JAVA_BIN=C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot\bin\java.exe"
set "M2=C:\Users\ADMIN\.m2\repository"

set "CP=%~dp0target\classes;%M2%\com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar;%M2%\com\zaxxer\HikariCP\5.0.1\HikariCP-5.0.1.jar;%M2%\org\slf4j\slf4j-api\2.0.13\slf4j-api-2.0.13.jar;%M2%\ch\qos\logback\logback-classic\1.5.6\logback-classic-1.5.6.jar;%M2%\ch\qos\logback\logback-core\1.5.6\logback-core-1.5.6.jar;%M2%\org\hdrhistogram\HdrHistogram\2.2.2\HdrHistogram-2.2.2.jar;%M2%\org\mongodb\mongodb-driver-sync\5.1.0\mongodb-driver-sync-5.1.0.jar;%M2%\org\mongodb\mongodb-driver-core\5.1.0\mongodb-driver-core-5.1.0.jar;%M2%\org\mongodb\bson\5.1.0\bson-5.1.0.jar;%M2%\redis\clients\jedis\5.1.2\jedis-5.1.2.jar;%M2%\org\apache\commons\commons-pool2\2.12.0\commons-pool2-2.12.0.jar;%M2%\com\fasterxml\jackson\core\jackson-databind\2.17.1\jackson-databind-2.17.1.jar;%M2%\com\fasterxml\jackson\core\jackson-core\2.17.1\jackson-core-2.17.1.jar;%M2%\com\fasterxml\jackson\core\jackson-annotations\2.17.1\jackson-annotations-2.17.1.jar"

"%JAVA_BIN%" -Dfile.encoding=UTF-8 -cp "%CP%" com.btl.dbms.Main %*

