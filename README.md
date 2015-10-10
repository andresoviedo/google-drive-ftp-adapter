![alt tag](http://www.andresoviedo.org/google-drive-ftp-adapter/icon.jpeg)


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

![alt tag](http://www.andresoviedo.org/google-drive-ftp-adapter/screenshot.jpg)
![alt tag](http://www.andresoviedo.org/google-drive-ftp-adapter/screenshot0.jpg)
![alt tag](http://www.andresoviedo.org/google-drive-ftp-adapter/screenshot1.jpg)


Notes
=====

- The google-drive-ftp-adapter DOES NOT synch your files to/from google drive. If you want to synch your files,
  you should do it with another tool supporting FTP.
- Google drive supports repeated filenames in same folder and illegal file names in contrast to linux & windows. 
  But don't worry because this is supported!


Source Code
===========

https://github.com/andresoviedo/google-drive-ftp-adapter


Web Site
========

http://www.andresoviedo.org/google-drive-ftp-adapter


Download
========

Latest version 1.1.0 (10/10/2015)  
- https://github.com/andresoviedo/google-drive-ftp-adapter/raw/master/build/google-drive-ftp-adapter-1.1.0-bundle.zip


Run it!
======

1. Download project & compile source code with maven and the launch the 'org.andresoviedo.apps.gdrive_ftp_adapter.Main.class'.
2. Or:

*Windows:*

1. Download the google-drive-ftp-adapter-1.1.0-bundle.zip
2. Unzip it
3. Execute windows-install-java.cmd
4. Execute start.cmd to start ftp adapter at port 21.
5. Open you FTP application and point it to user@localhost
6. Enjoy :)

*Linux*

1. Download the google-drive-ftp-adapter-1.1.0-bundle.zip
2. Unzip it
3. Double click on google-drive-ftp-adapter.jar or execute start.sh from command line
4. Open you FTP application and point it to user@localhost port:1821
5. Enjoy :)


Once the application is started, a Google authorization dialog will ask you to allow google drive ftp application
to acces your files. Click "OK". Now you can use ftp://user:user@localhost/ from your application to to connect 
to your google drive.

	
	
Test it!
========

Open terminal and type "ftp localhost 1821": Type "user" as the username and "user" as password. Once in FTP, type "dir" to see
your drive files. Example:

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


Configuration
=============

You can customize some application parameters in start script:
 
    $ java -jar google-drive-ftp-adapter.jar [google_account_name ftp_port_number]

Example:

    $ java -jar google-drive-ftp-adapter.jar pk1 1821

**google_account_name**

This is the name associated to the cache & google credentials, so the next time you run the application you don't
have to re-login or resynchronize all the application cache. This is also the name of the subfolder under "/data". 
Default value is "pk1".

**ftp_port_number**

Tcp port number where the ftp adapter is going to listen for ftp clients. Default FTP is 21, but In Linux 
this is a reserved port (below 1024 are privileged ports), so better work with a port like 1821. The start2.cmd  
start ftp adapter at port 22 in windows. 
Default is 1821. 


Known Problems
==============

- For some type of files, the size of files reported by google differs from what the local operating system does (txt, 3gp).
  I'll think how to fix it.
- google-drive ftp adapter does not support FTP authentication. If you have different google drive accounts,
  you can launch multiple google-drive-ftp-adapter in the same machine, each listening at different port.
- If you have timeout problems, it's maybe because you have a slow internet connection. So increment timeout
  in your FTP tool if your internet connection to avoid errors

 
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

- v1.1.0 (09/10/2015)
 - (n) Complete refactoring to simplify design and to allow adding more features    
 - (f) Illegal filename handling
 - (f) Fixed several issues. Tested with Beyond Compare, FileZilla & Telnet
- v1.0.1
 - (f) Changed google drive updater task from 10 minute to 10 seconds polling 
  
  
  