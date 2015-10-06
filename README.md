![alt tag](https://googledrive.com/host/0BxpnQDC5hjw-RVlQTFM4ZzNWOVk/google-drive-ftp-adapter/icon.jpeg)


Project info
============ 

http://gdriv.es/andresoviedo/google-drive-ftp-adapter


Getting Started
===============

Run application:

    $ java -jar google-drive-ftp-adapter-1.0.1.jar [google_account_name ftp_port_number]

Program arguments:

* google_account_name: choose a name for your account. For example "Pep"
* ftp_port_number: on Linux choose a port over 1024 (below 1024 are privileged ports). For example 1820

    $ java -jar google-drive-ftp-adapter-1.0.1.jar [google_account_name ftp_port_number]


ChangeLog
=========

* v2.0.0
  * (n) Complete refactoring to simplify design and to allow adding more features that are just coming:
    * FTP configurable users
  * (f) Illegal filename handling. Property "illegalCharacters" to override.
  * (f) Now FTP server really supports FileZilla 
* v1.0.1
  * (f) Changed google drive updater task from 10 minute to 10 seconds polling. 
