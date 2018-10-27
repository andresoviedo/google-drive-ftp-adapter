google-drive-ftp-adapter
========================

![codeship badge](https://codeship.com/projects/955a2600-646c-0134-321c-46895dbeddb7/status?branch=master)

![alt tag](https://raw.github.com/andresoviedo/google-drive-ftp-adapter/master/doc/images/google-drive-logo.png)


News
====

**Latest Release** v1.6.2 - 27/10/2018
- [google-drive-ftp-adapter-jar-with-dependencies.jar](build/google-drive-ftp-adapter-jar-with-dependencies.jar)

**Latest fixes**:
- Latest version of apache ftp server core 1.1.1 
- Google Drive API v3
- Complete code refactoring & cleaning
- Upload & Download using streams
- Moved to Java 8
- Improved performance
- Bug fixing

**Online service (ftp/ftps)** - 17/08/2018
- New: [google-drive-ftp-adapter-online](https://github.com/andresoviedo/google-drive-ftp-adapter-online)

About
=====

- With google-drive-ftp-adapter you can access your Google Drive through FTP protocols.
- You can use it in conjunction with any FTP client: shell FTP, Beyond Compare, FileZilla, etc.


Features
========

- Standalone Java application (Java 8)
- Apache Mina FTP Server 
- Google Drive API v3
- SQLite Index Cache
- Index Cache Synchronisation every 10 seconds
- User Management with permissions
- All FTP commands supported:
  - List folders and files
  - Rename files
  - Create or delete directories
  - Upload new files
  - Download files to local PC
  - Edit remote timestamps
  - Trash files or folders
- Google Docs Support


Screenshots
===========

![alt tag](https://raw.github.com/andresoviedo/google-drive-ftp-adapter/master/doc/images/screenshot-win32-start.jpg)
![alt tag](https://raw.github.com/andresoviedo/google-drive-ftp-adapter/master/doc/images/screenshot-beyond-compare.jpg)
![alt tag](https://raw.github.com/andresoviedo/google-drive-ftp-adapter/master/doc/images/screenshot-filezilla.png)
![alt tag](https://raw.github.com/andresoviedo/google-drive-ftp-adapter/master/doc/images/screenshot-shell-ftp.png)
![alt tag](https://raw.github.com/andresoviedo/google-drive-ftp-adapter/master/doc/images/screenshot-google-dialog.png)
![alt tag](https://raw.github.com/andresoviedo/google-drive-ftp-adapter/master/doc/images/screenshot-chrome.png)


Notes
=====

- The google-drive-ftp-adapter DOES NOT sync your files to / from Google Drive. If you want to sync your files,
  you should do it with your FTP tool.
- Google drive supports repeated filenames in same folder and illegal file names in contrast to many operating systems. 
  But don't worry because this is supported! These files will appear with chars encoded to _ (underscore) and an ID
  to keep track of the file. 

Downloads
=========

Latest Release 1.6.2 - 27/10/2018

- /¡\ Java 8 Required          : [jar-with-dependencies.jar](build/google-drive-ftp-adapter-jar-with-dependencies.jar)

Download Java 8 from http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html


Buid It!
=======

Install java 8 and maven 3. Then execute the following commands in the terminal:

    git clone https://github.com/andresoviedo/google-drive-ftp-adapter.git
    cd google-drive-ftp-adapter
    mvn clean package


Run it!
======

Double click on file google-drive-ftp-adapter-jar-with-dependencies.jar or execute the following command in the terminal:

    java -jar google-drive-ftp-adapter-jar-with-dependencies.jar

Once the application is started, Google with request authorization through your browser to allow Google Drive FTP access to your data. Click "OK". 
	

Test it!
========

Open ftp://user:user@localhost:1821/ in your browser to connect to your Google Drive.

Or open terminal and type "ftp localhost 1821": Type "user" as the username and "user" as password. Once in FTP, type "dir" to see your drive files.

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
in case you want to configure application.

For the full list of parameters and example check file : [configuration.properties](build/configuration.properties)

Here are the main application parameters you can customize:

    # TCP port where the application will listen for incoming connections 
    port=1821
    
    # FTP anonymous login
    ftp.anonymous.enabled=false
    ftp.anonymous.home=
    ftp.anonymous.rights=pwd|cd|dir|put|get|rename|delete|mkdir|rmdir|append
    
    # FTP users credentials
    ftp.user=user
    ftp.pass=user
    ftp.home=
    ftp.rights=pwd|cd|dir|put|get|rename|delete|mkdir|rmdir|append


To configure application just put 'configuration.properties' in the same folder.
If you want, you can also customize the path of the 'configuration.properties' when launching the app:

    $ java -jar google-drive-ftp-adapter.jar [propertiesFilename] 


F.A.Q.
======

 - Question: When I type in the username and password on the FTP client (e.g. in the FileZilla) it tells me "Authentication Failed":
   - Answer: The user credentials to login to the ftp server are not the same user credentials that you use for loging into your google account.
     Check in the README.md for the default user credentials to use.
  
 - Question: In my ftp client (e.g. FileZilla), when I download my google documents and I click on it they don't open.
   - Answer: If they are documents like word or excel (either if it's google filetype or not), your filenames should end with a proper
     filename extesion like "my_document.doc" or "my_google_sheet.xls" so your operating system can open it with your deafult installed applications.
 
 - Question: How can I start a secondary server?
   - Answer: You have 2 options so far:
   	 * You can launch the app passing applications arguments like "my-account" "1234" which corresponds to accountId and port.    	 
     * You can launch the app passing an application argument like "/path/to/secondary.properties" 

 - Question: When I launch the application nothing happens.
   - Answer:  Check that in the console or file log there is no errors. Check that your default Internet browser doesn't have any plugin
     that is causing the google authentication dialog to have any problems.  
 

Known Issues
============

- If you are in Windows 10 and you an error like the following, try to run program or terminal as administrator: 
  java.lang.UnsatisfiedLinkError: C:\Users\Zeo\AppData\Local\Temp\sqlite-3.7.151-x86-sqlitejdbc.dll: Access is denied
- For some type of files, the size of files reported by Google differs from what the local operating system does (for example: .txt and .3gp)
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
to your Google Drive files. I use it in conjunction with Beyond Compare to compare my local files and compare
them to ones I have in the Google Drive cloud.

You are free to use this program while you keep this file and the authoring comments in the code. Any comments 
and suggestions are welcome.


Get Involved
============

If you want to contribute you can start by solving the the current issues or opening a new one.


Contact Information
===================

[Contact](http://www.andresoviedo.org)


Donations
=========

If you like this project, please consider buying me a beer :)

[<img src="https://www.paypalobjects.com/webstatic/en_US/i/btn/png/btn_donate_92x26.png">](https://www.paypal.me/andresoviedo)


Change Log
==========

(f) fixed, (i) improved, (n) new feature

- v1.6.2 (27/10/2018)
  - Fixes #31 : anonymous user not working 
- v1.6.1 (17/08/2018)
  - Latest version of apache ftp server core 1.1.1 
- v1.6.0 (15/08/2018)
  - Google Drive API v3
  - Complete code refactoring & cleaning
  - Upload & Download using streams
  - Moved to Java 8
  - Improved performance
  - Bug fixing
  - File log removed
- v1.5.0 (04/08/2018)
  - (i) Code refactoring. Decoupled components
- v1.4.1 (08/12/2017)
  - (n) #17 Multiple ftp users
- v1.4.0 (02/12/2017)
  - (n) #16 Configurable user permissions
- v1.3.0 (01/12/2017)
  - (n) #16 Configurable binding address
  - (n) #15 Configurable user home directory
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
  
  
  
