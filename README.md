Tombstoning note:

This repository have been archived and exists for historical purposes. 
No updates or futher development will go into this repository. The content can be used as is but no support will be given. 

---


# dpaviser-jhove-dk.statsbiblioteket.dpaviser.report
Report for Niels Bønding generated from jhove output.

Run Main with argument 0 being the spreadsheet file to write, and argument 1 being
the top directory to walk.

NOTE:  Changelog.md is in dpaviser-jhove-report-main/for-deployment in order to go into
the deployment tarball.

# Installation:

Copy

   dpaviser-jhove-report-main/target/app-assembler/*

to target location (currently dpaviser@achernar/dpaviser-rapporter) and ask
Jens-Henrik to update pseudoprod.  Remember to chmod +x on run-after-upload.sh

Default setting is to derive the sender address automatically (set system property
"mail.from" to override) and send the mail by contacting an SMTP server running locally
on the default port 25.

(See https://docs.oracle.com/javaee/6/api/javax/mail/package-summary.html for full list
of JavaMail properties which can be set in this application as system properties)
