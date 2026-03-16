@echo off
setlocal
set MAVEN_PROJECTBASEDIR=%~dp0
set WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar

if not exist "%WRAPPER_JAR%" (
  echo Maven wrapper JAR not found: %WRAPPER_JAR%
  exit /b 1
)

if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java
)

if not defined MAVEN_USER_HOME (
  set MAVEN_USER_HOME=%MAVEN_PROJECTBASEDIR%\.m2
)

"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" "-Dmaven.user.home=%MAVEN_USER_HOME%" org.apache.maven.wrapper.MavenWrapperMain %*
endlocal
