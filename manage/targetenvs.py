from fabric.api import *


targetenvs = []
def _targetenv(f): targetenvs.append(f); return f

def _needs_targetenv():
    require('target', 'roledefs', 'dist_dir', 'tomcat',
            provided_by=targetenvs)

##
# Target environments

@_targetenv
def tg_dev_unix():
    "Set target env to: dev-unix"
    # Name env:
    env.target = "dev-unix"
    # Machines:
    env.roledefs = {
        'main': ['localhost'],
        'service': ['localhost'],
        'examples': ['localhost'],
    }
    # Filesystem paths
    env.rinfo_main_store = "/opt/_workapps/rinfo/depots/rinfo"
    env.examples_store = "/opt/_workapps/rinfo/depots"
    env.dist_dir = '/opt/_workapps/rinfo/rinfo_dist'
    env.rinfo_dir = '/opt/_workapps/rinfo'
    env.rinfo_rdf_repo_dir = '/opt/_workapps/rinfo/aduna'
    # Tomcat
    env.tomcat = "/opt/tomcat"
    env.tomcat_webapps = "%(tomcat)s/webapps"%env
    env.tomcat_start = "%(tomcat)s/bin/catalina.sh start"%env
    env.tomcat_stop = "%(tomcat)s/bin/catalina.sh stop"%env
    env.tomcat_user = "tomcat"

@_targetenv
def tg_integration():
    "Set target env to: integration"
    # Name env:
    env.target = "integration"
    # Machines:
    env.roledefs = {
        'main': ['rinfo-main'],
        'service': ['rinfo-service'],
        #'examples': ['rinfo-sources'],
        'doc': ['rinfo-main'],
    }
    # Filesystem paths
    env.rinfo_main_store = "/opt/rinfo/store"
    env.example_stores = "/opt/rinfo/depots"
    env.dist_dir = 'rinfo_dist'
    env.rinfo_dir = '/opt/rinfo'
    env.rinfo_rdf_repo_dir = '/opt/rinfo/rdf'
    env.webdocroot = "/var/www/dokumentation/"
    # Tomcat (Ubuntu)
    env.tomcat = "/var/lib/tomcat6"
    env.tomcat_webapps = "%(tomcat)s/webapps"%env
    env.tomcat_start = '/etc/init.d/tomcat6 start'
    env.tomcat_stop = '/etc/init.d/tomcat6 stop'
    env.tomcat_user = 'tomcat6'

@_targetenv
def tg_stg():
    "Set target env to: stg"
    # Name env:
    env.target = "stg"
    # Machines:
    env.user = 'rinfo'
    env.roledefs = {
        'main': ['rinfo-main.statskontoret.se'],
        'service': ['rinfo-service.statskontoret.se'],
        'examples': ['rinfo-sources.statskontoret.se'],
    }
    # Filesystem paths
    env.rinfo_main_store = "/opt/rinfo/store"
    env.example_stores = "/opt/rinfo/depots"
    env.dist_dir = 'rinfo_dist'
    env.rinfo_dir = '/opt/rinfo' # TODO: remove if base is packaged in
    env.rinfo_rdf_repo_dir = '/opt/rinfo/rdf'
    # Tomcat (SuSE):
    env.tomcat = "/usr/share/tomcat6"
    env.tomcat_webapps = "%(tomcat)s/webapps"%env
    env.tomcat_start = 'dtomcat6 start'
    env.tomcat_stop = 'dtomcat6 stop'
    env.tomcat_user = 'tomcat6'

@_targetenv
def tg_prod():
    "Set target env to: prod"
    # Name env:
    env.target = "prod"
    # Machines:
    env.roledefs = {
        'main': ['94.247.169.66'],
        'service': ['94.247.169.67'],
        'doc': ['dev.lagrummet.se'],
    }
    # Manage
    env.mgr_workdir = "mgr_work"
    env.dist_dir = 'rinfo_dist'
    # Filesystem paths
    env.rinfo_main_store = "/opt/rinfo/store"
    env.example_stores = "/opt/rinfo/depots"
    env.rinfo_dir = '/opt/rinfo'
    env.rinfo_rdf_repo_dir = '/opt/rinfo/rdf'
    env.webdocroot = "/var/www/dokumentation/v0" # TODO: change to official when OK:d
    # Tomcat
    env.tomcat_version = "6.0.20"
    env.tomcat = "/opt/tomcat"
    env.tomcat_webapps = "%(tomcat)s/webapps"%env
    env.tomcat_start = '/etc/init.d/tomcat start'
    env.tomcat_stop = '/etc/init.d/tomcat stop'
    env.tomcat_user = 'root' # FIXME: tomcat

