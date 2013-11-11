@ECHO OFF

SET CWD=%~dp0%
SET JAVA_HOME=%CWD%\jre7
SET PATH=%JAVA_HOME%\bin;%PATH%

ECHO Checking JAVA Installation...
IF EXIST "%JAVA_HOME%" GOTO LAUNCH_INSTALLER

:INSTALL_JDK
ECHO.
ECHO Uncompressing JDK... Please wait.
IF NOT EXIST "%JAVA_HOME%" MKDIR "%JAVA_HOME%"
"%CWD%setup\7z.exe" x "%CWD%setup\jre7.7z" -y -o"%JAVA_HOME%"
if %errorlevel% neq 0 pause

:LAUNCH_INSTALLER
ECHO Launching google-drive-ftp-adapter...
SET PROXY_CONF=
REM SET PROXY_CONF=-Dhttps.proxyHost=PROXY_HOST -Dhttps.proxyPort=PROXY_PORT
java.exe %PROXY_CONF% -cp "%CWD%lib/*" -jar "%CWD%lib\google-drive-ftp-adapter-1.0-SNAPSHOT.jar"
if %errorlevel% neq 0 pause

ECHO.
ECHO Thank you for using google-drive-ftp-adapter. Good Bye.

pause