"""
Target Environments
"""
from fabric.api import *


targetenvs = []

def targetenv(f):
    """
    Decorator function that makes sure that the list targetenvs contains all
    available target environments. It does so by adding the decorated function
    to the targetenvs list which is used by the _needs_targetenv function.
    """
    targetenvs.append(f)
    return task(f)

def _needs_targetenv():
    """
    Makes sure that the env dictionary contains a certain set of keys. These
    keys are provided by one of the targetenv functions (decorated with
    @targetenv). Targets calling this function require a target to have been
    invoked.
    """
    require('target', 'roledefs', 'dist_dir', 'tomcat', provided_by=targetenvs)

@targetenv
def dev_unix():
    """Set target env to: dev-unix"""
    # Name env:
    env.target = "dev-unix"
    # Machines:
    env.roledefs = {
        'admin': ['localhost'],
        'main': ['localhost'],
        'service': ['localhost'],
        'examples': ['localhost'],
    }
    # Filesystem paths
    env.rinfo_main_store = "/opt/work/rinfo/depots/rinfo"
    env.examples_store = "/opt/work/rinfo/depots"
    env.dist_dir = '/opt/work/rinfo/rinfo_dist'
    env.rinfo_dir = '/opt/work/rinfo'
    env.rinfo_rdf_repo_dir = '/opt/work/rinfo/aduna'
    env.admin_webroot = "/opt/work/rinfo/admin"
    # Tomcat
    env.tomcat = "/opt/tomcat"
    env.tomcat_webapps = "%(tomcat)s/webapps"%env
    env.tomcat_start = "%(tomcat)s/bin/catalina.sh start"%env
    env.tomcat_stop = "%(tomcat)s/bin/catalina.sh stop"%env
    env.tomcat_user = "tomcat"
    env.tomcat_group = 'tomcat'

@targetenv
def demo():
    """Set target env to: demo"""
    # Name env:
    env.target = "demo"
    # Machines:
    env.user = 'rinfo'
    env.roledefs = {
        'main': ['demo.lagrummet.se'],
        'service': ['demo.lagrummet.se'],
        'checker': ['demo.lagrummet.se'],
        'admin': ['demo.lagrummet.se'],
        'demo': ['demo.lagrummet.se'],
    }
    # Manage
    env.mgr_workdir = "/home/%(user)s/mgr_work" % env
    env.dist_dir = 'rinfo_dist'
    # Filesystem paths
    env.rinfo_dir = '/opt/rinfo'
    env.rinfo_main_store = "/opt/rinfo/store"
    env.rinfo_rdf_repo_dir = '/opt/rinfo/sesame-repo'
    env.demo_data_root = "/opt/rinfo/demo-depots"
    # Apache
    env.admin_webroot = "/var/www/admin"
    env.docs_webroot = "/var/www/dokumentation"
    env.apache_sites = {
        'main': ['rinfo-main', 'admin'],
        'service': ['service'],
        'demo': ['sfs', 'dv', 'prop', 'sou', 'ds'],
        'checker': ['checker'],
    }
    env.apache_jk_tomcat = True
    # Tomcat
    env.custom_tomcat = True
    env.tomcat_version = "6.0.33"
    env.tomcat = "/opt/tomcat"
    env.tomcat_webapps = "%(tomcat)s/webapps"%env
    env.tomcat_start = '/etc/init.d/tomcat start'
    env.tomcat_stop = '/etc/init.d/tomcat stop'
    env.tomcat_user = 'tomcat'
    env.tomcat_group = 'tomcat'

# Integration is a virtual environment that you could setup on your own computer
# See README.txt for more information
@targetenv
def integration():
    """Set target env to: integration"""
    # Name env:
    env.target = "integration"
    # Machines:
    env.user = 'rinfo'
    env.roledefs = {
        'main': ['rinfo-integration'],
        'service': ['rinfo-integration'],
        'checker': ['rinfo-integration'],
        'doc': ['rinfo-integration'],
        'admin': ['rinfo-integration'],
        'demo': ['rinfo-integration'],
    }
    # Manage
    env.mgr_workdir = "/home/%(user)s/mgr_work" % env
    env.dist_dir = 'rinfo_dist'
    # Filesystem paths
    env.rinfo_main_store = "/opt/rinfo/store"
    env.rinfo_dir = '/opt/rinfo'
    env.rinfo_rdf_repo_dir = '/opt/rinfo/sesame-repo'
    env.demo_data_root = "/opt/rinfo/demo-depots"
    # Apache
    env.admin_webroot = "/var/www/admin"
    env.docs_webroot = "/var/www/dokumentation"
    env.apache_sites = {
        'demo': ['sfs', 'dv', 'prop', 'sou', 'ds'],
        'main': ['rinfo-main', 'admin'],
        'service': ['service'],
        'checker': ['checker'],
    }
    env.apache_jk_tomcat = True
    # Tomcat
    env.custom_tomcat = True
    env.tomcat_version = "6.0.33"
    env.tomcat = "/opt/tomcat"
    env.tomcat_webapps = "%(tomcat)s/webapps"%env
    env.tomcat_start = '/etc/init.d/tomcat start'
    env.tomcat_stop = '/etc/init.d/tomcat stop'
    env.tomcat_user = 'tomcat'
    env.tomcat_group = 'tomcat'

@targetenv
def prod():
    """Set target env to: prod"""
    # Name env:
    env.target = "prod"
    # Machines:
    env.user = 'rinfo'
    env.roledefs = {
        'main': ['rinfo.lagrummet.se'],
        'service': ['service.lagrummet.se'],
        'checker': ['checker.lagrummet.se'],
        'doc': ['dev.lagrummet.se'],
        'admin': ['admin.lagrummet.se'],
    }
    # Manage
    env.mgr_workdir = "/home/%(user)s/mgr_work" % env
    env.dist_dir = 'rinfo_dist'
    # Filesystem paths
    env.rinfo_main_store = "/opt/rinfo/store"
    env.rinfo_dir = '/opt/rinfo'
    env.rinfo_rdf_repo_dir = '/opt/rinfo/sesame-repo'
    # Apache
    env.admin_webroot = "/var/www/admin"
    env.docs_webroot = "/var/www/dokumentation"
    env.apache_sites = {
        'main': ['default', 'admin'],
        'service': ['service'],
        'checker': ['checker'],
    }
    env.apache_jk_tomcat = True
    # Tomcat
    env.custom_tomcat = True
    env.tomcat_version = "6.0.33"
    env.tomcat = "/opt/tomcat"
    env.tomcat_webapps = "%(tomcat)s/webapps"%env
    env.tomcat_start = '/etc/init.d/tomcat start'
    env.tomcat_stop = '/etc/init.d/tomcat stop'
    env.tomcat_user = 'tomcat'
    env.tomcat_group = 'tomcat'
