![alt tag](http://www.andresoviedo.org/google-drive-ftp-adapter/icon.jpeg)

Project info
============ 

http://www.andresoviedo.org/google-drive-ftp-adapter


Getting Started
===============

*Windows:*

1. Download the google-drive-ftp-adapter-1.1.0-bundle.zip
2. Unzip it
3. Execute windows-install-java.cmd
4. Execute start.cmd to start ftp adapter at port 21.  The start2.cmd start ftp adapter at port 22
5. Open you FTP application and point it to user@localhost
6. Enjoy :)

*Linux*

1. Download the google-drive-ftp-adapter-1.1.0-bundle.zip
2. Unzip it
3. Double click on google-drive-ftp-adapter.jar or execute start.sh from command line
4. Open you FTP application and point it to user@localhost port:1821
5. Enjoy :)


Configuration
=============

You can customize some application parameters in start script:
 
    $ java -jar google-drive-ftp-adapter.jar [google_account_name ftp_port_number]

Example:

    $ java -jar google-drive-ftp-adapter.jar pk1 1821

**google_account_name**

You can have multiple ftp adapter listening at different ports to connect to the google drive account that is going to
be associated to this *google_account_name* when the browser opens with the google dialog confirmation. Default is "pk1".

**ftp_port_number**

Tcp port number where the ftp adapter is going to listen for ftp clients. Default FTP is 21, but In Linux 
this is a reserved port (below 1024 are privileged ports), so better work with a port like 1821.


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


ChangeLog
=========

* v1.1.0 (09/10/2015)
  * (n) Complete refactoring to simplify design and to allow adding more features    
  * (f) Illegal filename handling. Property "illegalCharacters" to override
  * (f) Fixed several issues. Tested with Beyond Compare, FileZilla & Telnet
* v1.0.1
  * (f) Changed google drive updater task from 10 minute to 10 seconds polling 