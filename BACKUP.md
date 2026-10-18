Develop new feature database backup, refresh and download application complete log. the log file called
1. Create or develop an responsive user interface, which will have access to only Admin role user, called Database Backup and Refresh
2. It will have two process, 1.  Database Backup 2. Database refresh.
3. During Database backup or Refresh process i.e when user hit any of the button, all the current running process will be allowed to complete and any new request to database or application will be on hold or stopped. also System will take a 5-10 seconds pause incase any running user reqeust are pending to be finish then start backup or refresh process.
4. During Database backup or refresh or as soon user hit the backup or refresh process, all the user accessed to the application whether logged in or not will be warned i.e. a running warning message on top of the page will run that will tell to user that Database backup or Refresh is going on and advice them to stay away new transaction until the process is over .
5.  Once backup or refresh process is over, automatically running warning message will be stopped and dissapear from screen.
6. currently the database is located as per configuration is "url: jdbc:sqlite:${user.home}/portfolio.db"
7. the uploading process is like the backup the database datafile. The database datafile "portfolio.db" will be uploaded into server and replace it with existing database datafile there. it will have upload button and file upload from local desk operation.
8. Similarlly, the download process is, it will download the database datafile from server to local system.

9. Download application log.  same above page will have another process called download applicatoin log which is in server in location user.home. it does not need application warning or message to user. Only Admin can access it. the log file name is springboot.log or just *.log as its only log file in server.

10. the download log process will download the log also clean the log file in server too i.e. it will remove logs from the file and make it zero size at that point of time.