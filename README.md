![alt tag](./doc/images/google-drive-logo.png)

News
====

**Newest Version** v1.2.3 - 07 April 2016
- [jar-with-dependencies.jar](https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/build/google-drive-ftp-adapter-jar-with-dependencies.jar)
- [adapter-bundle.zip](https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/build/google-drive-ftp-adapter-bundle.zip)

**New features**:
- Updated documentation
- Control user connection limit (fixes issue #6)
- FTP configuration

About
=====

- With google-drive-ftp-adapter you can access your Google Drive through FTP protocols.
- You can use it in conjunction with any FTP client: shell FTP, Beyond Compare, FileZilla, etc.

Features
========

- Standalone Java application
- Apache Mina FTP Server as a gateway to your google drive files
- Internal SQLite Cache for fast access to data
- Google Drive cache synchronisation by polling every 10 seconds
- Supported FTP commands:
  - List folders, subfolders and files
  - Renaming files
  - Make new directories
  - Upload new files (includes gdoc conversion)
  - Download files to local PC (includes gdoc and gsheet conversion)
  - Touch remote timestamps
  - Trash files or folders

**Ideas for the future**:
- implement the apache commons-vfs interface [(Link)](https://commons.apache.org/proper/commons-vfs/)
- implement linux cif vfs protocol [(Link)](http://www.ubiqx.org/cifs/Intro.html)

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

- The google-drive-ftp-adapter DOES NOT sync your files to / from Google Drive. If you want to sync your files,
  you should do it with your FTP tool.
- Google drive supports repeated filenames in same folder and illegal file names in contrast to many operating systems. 
  But don't worry because this is supported! These files will appear with chars encoded to _ (underscore) and an ID
  to keep track of the file. 

Source Code
===========

[GitHub](https://github.com/andresoviedo/google-drive-ftp-adapter)

Web Site
========

[www.andresoviedo.org](http://www.andresoviedo.org/google-drive-ftp-adapter)

Download
========

Latest version 1.2.3 - 07 April 2016
- [jar-with-dependencies.jar](https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/build/google-drive-ftp-adapter-jar-with-dependencies.jar)
- [adapter-bundle.zip](https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/build/google-drive-ftp-adapter-bundle.zip)

Buid It!
=======

The project is packaged with Maven. If you want to build it just download the project, compile it and run Java with "org.andresoviedo.apps.gdrive_ftp_adapter.Main"

Run it!
======

- If you have Java installed already all you have to do is download jar-with-dependencies.jar and double click on it or run from command line:

    $ java -jar google-drive-ftp-adapter-jar-with-dependencies.jar
    
- If you don't have Java installed and you are in Windows:
  - Download from [here](https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/build/google-drive-ftp-adapter-win32-jre7-bundle.zip)
  - Unzip it
  - Execute windows-install-java.cmd
  - Execute start.cmd
- Once the application is started, Google with request authorization through your browser to allow Google Drive FTP access to your data. Click "OK". 
	
Test it!
========

- Open ftp://user:user@localhost:1821/ in your browser to connect to your Google Drive.

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

The application works fine without configuration. However, a default 'configuration.properties' file is provided
in case you want to customize them.

If you want, you can also customize the path of the 'configuration.properties' when launching the app:

    $ java -jar google-drive-ftp-adapter.jar [propertiesFilename] 
    
Here are the application parameters you can customize:

    # log4j.fileId if you have more than 1 instance of the app running
    log4j.fileId=
    
    # account name associated to cache and Google credentials 
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

This is the name associated to the cache and Google credentials, so the next time you run the application you don't
have to re-login or resynchronize all of the application cache. This is also the name of the subfolder under "/data"
where information is going to be stored. Default value is "default".

**port**

TCP port number where the ftp adapter is going to listen for ftp clients. Default FTP port is 21, but In Linux 
this is a reserved port and ports below 1024 are privileged, so we use port 1821. Default is 1821.
There is another start2.cmd as example (Windows) that start an FTP adapter at port 22. 

- Note: If you have different Google Drive accounts, you can launch multiple google-drive-ftp-adapter 
  in the same machine, each listening at a different port. Just put a different fileId so they write logs in different files.

Known Issues
============

- For some type of files, the size of files reported by Google differs from what the local operating system does (for example: .txt and .3gp) (Fix TBA)
- If you have timeout problems because of slow internet connectivity, try incrementing the timeout in your FTP client.
  
Disclaimer
==========
	
- This application is released under the LGPL license. Use this application at your own risk.
- This application uses a Google Drive API Key with a courtesy of 10 requests/second/user and 10 million
  request/day. When it reaches the quota the application may stop working.
  
Project Info
============ 

This application lets you connect your FTP applications to your Google Drive files through the FTP protocol 
rather than using the official Google Drive client.

This custom Google Drive client was created because the official client can't be reinstalled on a new PC without 
having to download all your drive files again. Also, because the official client does not support FAT32
partitions and I used to have all my files in one of this partitions.

So this application basically starts a FTP server in your local machine emulating that it is hosting your 
Google Drive files, acting as a gateway. Once this setup is done, you can connect any FTP client to connect 
to your Google Drive files. I use it in conjunction with Beyond Compare to compare my local files 
(stored anywhere in my cloud ;) and compare them to ones I have in the Google Drive cloud.

You are free to use this program while you keep this file and the authoring comments in the code. Any comments 
and suggestions are welcome.

Contact Information
===================

[Contact](http://www.andresoviedo.org)

Change Log
==========

(f) fixed, (i) improved, (n) new feature

- v1.2.3 (07/04/2016)
 - (f) Controlling of google drive service user rate limit. Set to 5/req/user/sec Fixes issue #6
 - (f) Fixed bug when receiving CWD command we were removing first character of folder
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
  
  
  
