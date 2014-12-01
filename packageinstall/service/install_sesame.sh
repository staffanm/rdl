#!/bin/bash 

echo '------------- Install Sesame'

mkdir /opt/rinfo
mkdir /opt/rinfo/sesame-repo
chown tomcat7 /opt/rinfo/sesame-repo

mv openrdf-sesame.war /var/lib/tomcat7/webapps/
mv sesame-workbench.war /var/lib/tomcat7/webapps/
