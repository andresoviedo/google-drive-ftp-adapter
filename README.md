![alt tag](https://github.com/andresoviedo/google-drive-ftp-adapter/blob/master/src/main/resources/images/icon.jpeg?raw=true)

With this application you can:
* Connect any FTP application to your Google Drive through the FTP protocol.
* Use it in conjunction with any comparison tool supporting FTP protocol to synch your files (beyond compare, filezilla, etc)

Features:
* No installation required
* FTP Server
* Cache (for optimizing google drive access)
* List folders, subfolders and files
* Upload new files
* Download files to local PC
* Renaming files
* Delete files
* Changing remote timestamps
* Change notification service

Run it:
* Compile project and then run the Main.class.  
* Use ftp://user:user@localhost/ from your application to to connect to your google drive.  
* A Google authorization dialog will ask you to allow application to acces your files. Click "OK"

Any comments: andresoviedo@gmail.com

WARN: This application is still in an alpha state. This application is currently released under the LGPL license.
      Use this library at your own risk.

WARN: This application uses a Google Drive API Key with a courtesy of 10 requests/second/user and 10 million request/day.
      When it reaches the top it could be unavailable.
