@ECHO OFF

SET CWD=%~dp0%
SET JAVA_HOME=%CWD%\jre7
SET PATH=%JAVA_HOME%\bin;%PATH%

ECHO Launching google-drive-ftp-adapter...
SET PROXY_CONF=
REM SET PROXY_CONF=-Dhttps.proxyHost=PROXY_HOST -Dhttps.proxyPort=PROXY_PORT
java.exe %PROXY_CONF% -cp "%CWD%lib/*" -jar "%CWD%google-drive-ftp-adapter.jar" user2 1822
if %errorlevel% neq 0 pause

ECHO.
ECHO Thank you for using google-drive-ftp-adapter. Good Bye.

pause