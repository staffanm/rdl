import sys
from fabric.api import *
from fabric.contrib.files import exists
from fabfile.util import venv, exit_on_error
from fabfile.app import local_lib_rinfo_pkg, _deploy_war_norestart
from fabfile.target import _needs_targetenv
from fabfile.server import restart_apache
from fabfile.server import restart_tomcat
from fabfile.server import tomcat_stop
from fabfile.server import tomcat_start
from fabfile.util import msg_sleep


##
# Local build
@task
@runs_once
def package(deps="1", test="1"):
    if int(deps):
        local_lib_rinfo_pkg(test)
    _needs_targetenv()
    flags = "" if int(test) else "-Dmaven.test.skip=true"
    local("cd %(java_packages)s/rinfo-checker/ && "
          "mvn %(flags)s -P%(target)s clean package war:war" % venv(), capture=False)


##
# Server deploy
@task
@runs_once
@roles('checker')
def setup():
    if not exists(env.dist_dir):
        run("mkdir %(dist_dir)s" % env)
    if not exists(env.target_config_dir):
        sudo("mkdir %(target_config_dir)s" % env)
    put("%(java_packages)s/rinfo-main/src/environments/%(target)s/rinfo-main.properties"  % env,"%(target_config_dir)srinfo-main.properties"  % env, use_sudo=True)


@task
@roles('checker')
@exit_on_error
def deploy(headless="0"):
    setup()
    _deploy_war_norestart("%(java_packages)s/rinfo-checker/target/rinfo-checker-%(target)s.war" % env,
                          "rinfo-checker", int(headless))


@task
@roles('checker')
def all(deps="1", test="1", headless="0"):
    package(deps, test)
    deploy(headless)


@task
@roles('checker')
def test():
    """Test functions of checker"""
    url = "http://"+env.roledefs['checker'][0]
    with lcd(env.projectroot+"/packages/java/rinfo-checker/src/regression"):
        local("casperjs test . --xunit=%(projectroot)s/testreport/checker_test_report.log --url=%(url)s"
              " --target=%(target)s --output=%(projectroot)s/testreport/" % venv())


@task
@roles('checker')
def clean():
    tomcat_stop()
    sudo("rm -rf %(tomcat_webapps)s/rinfo-checker" % venv())
    sudo("rm -rf %(tomcat_webapps)s/rinfo-checker.war" % venv())
    sudo("rm -rf %(tomcat)s/logs/rinfo-checker*.*" % venv())
    tomcat_start()


@task
@roles('admin')
def test_all():
    all(deps="0", test="0")
    restart_tomcat()
    restart_apache()
    msg_sleep(20, "restart and tomcat apache")
    try:
        test()
    except:
        e = sys.exc_info()[0]
        print e
        sys.exit(1)
    finally:
        clean()