![alt tag](https://github.com/andresoviedo/google-drive-ftp-adapter/blob/master/src/main/resources/images/icon.jpeg?raw=true)

With this application you can:
* Connect any FTP application to your Google Drive through the FTP protocol.
* Use it in conjunction with any comparison tool supporting FTP protocol to synch your files (beyond compare, filezilla, etc)

Features:
* Standalone application
* Apache Mina FTP Server
* SQLite Cache
* Cache sinchronization from Drive (polling)
* List folders, subfolders and files
* Upload new files (included to gdoc conversion)
* Download files to local PC (included gdoc and gsheet conversion)
* Renaming files
* Touch remote timestamps
* Trashing files or folders


Run it:
* Compile maven project and then run the 'org.andresoviedo.apps.gdrive_ftp_adapter.Main.class'.  
* Use ftp://user:user@localhost/ from your application to to connect to your google drive.  
* A Google authorization dialog will ask you to allow application to acces your files. Click "OK".

Any comments: andresoviedo@gmail.com

WARN: This application is still in an BETA state, so bugs may appear. This application is currently released under the LGPL license.
      Use this application at your own risk.

WARN: This application uses a Google Drive API Key with a courtesy of 10 requests/second/user and 10 million request/day.
      When it reaches the quota it could be unavailable.
