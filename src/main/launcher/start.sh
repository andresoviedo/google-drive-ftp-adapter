#!/bin/bash

echo "Launching google-drive-ftp-adapter..."
set PROXY_CONF=
# set PROXY_CONF=-Dhttps.proxyHost=PROXY_HOST -Dhttps.proxyPort=PROXY_PORT
java $PROXY_CONF -cp "lib/*" -jar "google-drive-ftp-adapter.jar"

echo "Thank you for using google-drive-ftp-adapter. Good Bye."


