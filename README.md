![alt tag](http://www.andresoviedo.org/google-drive-ftp-adapter/icon.jpeg)

News
====

**New version** v1.2.2 - 27 October 2015
- https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/build/google-drive-ftp-adapter-jar-with-dependencies.jar
- https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/build/google-drive-ftp-adapter-bundle.zip

**New features**:
- ftp configuration


About
=====

- With google-drive-ftp-adapter you can connect to your Google Drive through the FTP protocol.
- You can use it in conjunction with any FTP client: shell ftp, Beyond Compare, FileZilla, ...


Features
========

- Standalone JAVA application. Just have JAVA installed & double click!
- Apache Mina FTP Server as a gateway to your google drive files
- Internal SQLite Cache for fast access to data
- Google drive cache sinchronization by polling (10 seconds)
- Supported FTP commands:
  - List folders, subfolders and files
  - Renaming files
  - Make new dirs
  - Upload new files (includes gdoc conversion)
  - Download files to local PC (includes gdoc and gsheet conversion)
  - Touch remote timestamps
  - Trash files or folders


Screenshots
===========

![alt tag](https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/doc/images/screenshot-win32-start.jpg)
![alt tag](https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/doc/images/screenshot-beyond-compare.jpg)
![alt tag](https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/doc/images/screenshot-filezilla.png)
![alt tag](https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/doc/images/screenshot-shell-ftp.png)
![alt tag](https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/doc/images/screenshot-google-dialog.png)
![alt tag](https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/doc/images/screenshot-chrome.png)


Notes
=====

- The google-drive-ftp-adapter DOES NOT synch your files to/from google drive. If you want to synch your files,
  you should do it with your FTP tool.
- Google drive supports repeated filenames in same folder and illegal file names in contrast to linux & windows. 
  But don't worry because this is supported! These files will appear with chars encoded to _ (underscore) and an ID
  to keep track of the file. 


Source Code
===========

https://github.com/andresoviedo/google-drive-ftp-adapter


Web Site
========

http://www.andresoviedo.org/google-drive-ftp-adapter


Download
========

Latest version 1.2.2 (27/10/2015)
- https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/build/google-drive-ftp-adapter-bundle.zip
- https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/build/google-drive-ftp-adapter-jar-with-dependencies.jar


Buid It!
=======

The project is packaged with maven. If you want to build it just download project, compile it and run java with "org.andresoviedo.apps.gdrive_ftp_adapter.Main"


Run it!
======

- If you have already JAVA installed download jar-with-dependencies.jar and double click on it or run from command line:

    $ java -jar google-drive-ftp-adapter-jar-with-dependencies.jar
    
- If you don't have JAVA installed and you are in Windows:
  - Download the https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/build/google-drive-ftp-adapter-win32-jre7-bundle.zip
  - Unzip it
  - Execute windows-install-java.cmd
  - Execute start.cmd
- Once the application is started, a Google authorization dialog in your Internet browser will ask you to allow
google drive ftp application to acces your files. Click "OK". 


	
Test it!
========

- Open ftp://user:user@localhost:1821/ in your browser to connect to your google drive.

- Or open terminal and type "ftp localhost 1821": Type "user" as the username and "user" as password. Once in FTP, type "dir" to see your drive files.

Ftp example:

    $ ftp localhost 1821
    Connected to localhost.
    220 Service ready for new user.
    Name (localhost:andres): user
    331 User name okay, need password for user.
    Password:
    230 User logged in, proceed.
    Remote system type is UNIX.
    $ ftp> dir
    200 Command PORT okay.
    150 File status okay; about to open data connection.
    drwx------   0 uknown no_group            0 Nov 16  2013 SOFTWARE
    drwx------   0 uknown no_group            0 Oct 29  2013 NEXUS7
    drwx------   0 uknown no_group            0 Oct 19  2013 MUSIC
    -rw-------   0 uknown no_group      5348582 Apr 30 22:15 Dr. Toast - Light.mp3
    -rw-------   0 uknown no_group      1936326 Dec 21  2014 avatar2.jpg
    226 Closing data connection
    $ ftp>


Application Configuration
=========================

The application works fine without configuration.  However, a default configuration.properties is provided
in case you want to customize them.

If you want, you can also customize the path of the configuration.properties when launching the app:

    $ java -jar google-drive-ftp-adapter.jar [propertiesFilename] 
    
Here are the application parameters you can customize:

    # log4j.fileId if you have more than 1 instance of the app running
    log4j.fileId=
    
    # account name associated to cache & google credentials 
    account=default
    
    # FTP port listening for incoming connections
    port=1821
    
    # FTP Enable anonymous login?
    ftp.anonymous.enabled=false
    
    # FTP default user credentials
    ftp.user=user
    ftp.pass=user
    
    # Illegal characters for your file system so file copying works fine  
    os.illegalCharacters=\\/|[\\x00-\\x1F\\x7F]|\\`|\\?|\\*|\\\\|\\<|\\>|\\||\\"|\\:


**account**

This is the name associated to the cache & google credentials, so the next time you run the application you don't
have to re-login or resynchronize all the application cache. This is also the name of the subfolder under "/data"
where information is going to be stored. Default value is "default".

**port**

TCP port number where the ftp adapter is going to listen for ftp clients. Default FTP port is 21, but In Linux 
this is a reserved port (below 1024 are privileged ports), so we better work with a port like 1821. Default is 1821.
There is another start2.cmd as example (windows) that start an ftp adapter at port 22. 


- Note: If you have different google drive accounts, you can launch multiple google-drive-ftp-adapter 
  in the same machine, each listening at different port. Just put a different fileId so they write logs in different files.


Known Issues
============

- For some type of files, the size of files reported by google differs from what the local operating system does (txt, 3gp). I'll think how to fix it.
- If you have timeout problems, maybe it's because you have a slow internet connection. Try to increment timeout in your FTP tool

  
Disclaimer
==========
	
- This application is released under the LGPL license. Use this application at your own risk.
- This application uses a Google Drive API Key with a courtesy of 10 requests/second/user and 10 million
  request/day. When it reaches the quota the application may stop working.
  
  
Project info
============ 

This application lets you connect your FTP applications to your Google Drive files through the FTP protocol 
rather than using the Official's Google Drive client.

This custom google drive client was created because official's one can't be reinstalled in a new PC without 
having to redownload all your drive files again, from the cloud to your local PC. So if you have hundred of Gigas 
and a regular ADSL it would take weeks to complete. Also, because the official client does not support FAT32
partitions and I used to have all my files in one of this partitions.

So this application basically starts a FTP server in your local machine emulating that it is hosting your 
google drive files, acting as a gateway. Once this setup is done, you can connect any FTP client to connect 
to your google drive files. I use it in conjunction with Beyond compare to compare my local files 
(stored anywhere in my cloud ;) and compare them to ones I have in the google drive cloud.

You are free to use this program while you keep this file and the authoring comments in the code. Any comments 
and suggestions are welcome.


Contact Information
===================

http://www.andresoviedo.org


ChangeLog
=========

(f) fixed, (i) improved, (n) new feature

- v1.2.2 (27/10/2015)
 - (f) fixes a issue with Windows (8.1) Explorer FTP, which sends CWD commands with trailing path separator
 - (f) decoding an encoded filename did result in a different name on Windows as filename was made lower case, so use the lower case name just internally.
- v1.2.1 (12/12/2015)
 - (n) "Hack" to force cache to refetch folder info (refresh folder or type "dir" 3 times in ftp) 
 - (i) Updated to latest version 1.20.0 of google-api-services-drive.jar
 - (f) Removed not used permissions for the google authorization. Now only DRIVE & DRIVE_METADATA used
- v1.2.0 (10/10/2015)
 - (n) support for assembly application into jar-with-dependencies.jar
 - (n) support for configuration.properties
 - (n) new properties can be configured like "ftp.user", "ftp.pass" "os.illegalCharacters"
- v1.1.0 (09/10/2015)
 - (i) Complete refactoring to simplify design and to allow adding more features    
 - (f) Illegal filename handling
 - (f) Fixed several issues. Tested with Beyond Compare, FileZilla & Telnet
- v1.0.1
 - (f) Changed google drive updater task from 10 minute to 10 seconds polling 
  
  
  
