@echo off
set AKKA_HOME=%~dp0..
set JAVA_OPTS=-Xms1024M -Xmx1024M -Xss1M -XX:MaxPermSize=256M -XX:+UseParallelGC
set AKKA_CLASSPATH=%AKKA_HOME%\lib\scala-library.jar;%AKKA_HOME%\lib\akka\*;%AKKA_HOME%\config

java %JAVA_OPTS% -cp "%AKKA_CLASSPATH%" -Dakka.home="%AKKA_HOME%" akka.kernel.Main
