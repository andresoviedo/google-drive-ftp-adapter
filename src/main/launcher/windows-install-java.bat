@ECHO OFF

SET CWD=%~dp0%
SET JAVA_HOME=%CWD%\jre7
SET PATH=%JAVA_HOME%\bin;%PATH%

ECHO Checking JAVA Installation...
IF EXIST "%JAVA_HOME%" GOTO INSTALLED

:INSTALL_JDK
ECHO.
ECHO Uncompressing JDK... Please wait.
IF NOT EXIST "%JAVA_HOME%" MKDIR "%JAVA_HOME%"
"%CWD%setup\7z.exe" x "%CWD%setup\jre7.7z" -y -o"%JAVA_HOME%"
if %errorlevel% neq 0 pause

:INSTALLED

ECHO.
ECHO Java installed at "%JAVA_HOME%". Good bye!

pause