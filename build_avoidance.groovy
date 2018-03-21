import java.io.FileInputStream;
import java.util.Properties;

import hudson.model.*
import hudson.scm.*
import jenkins.model.*

def DEPENDENCY_FILE = 'dependencies.properties'
// I had thought about pulling this in as a parameter but I didn't want
// to pass this down through all of the build jobs.
// It will have to be changed with each new build system.

// Grab any variables defined in the Variable bindings section of the Execute system Groovy script
def HashMap varBindings = binding.variables

def String CHILDREN_JOBS  = varBindings.get('CHILDREN_JOBS')
def String DEPENDENCY_JOB = varBindings.get('DEPENDENCY_JOB')

// Get the information from the currently running build
def thr = Thread.currentThread()
def build = thr?.executable

// Grab all of the environmental variables and put them in a hash
def envVarsMap = build.parent.builds[0].properties.get('envVars')
def config = new HashMap()
config.putAll(envVarsMap)

// To retrieve environmental information use the example below
//config.get('BUILD_NUMBER')

// To print out all of the information use the loop below
//config.keySet().each { key ->
//  out.println key + ': ' + config.get(key)
//}

// Gen environment variables
def jobName                        = config.get('JOB_NAME')
def WORKSPACE                      = config.get('WORKSPACE')
def REBUILD_ALL                    = config.get('REBUILD_ALL')
def SKIP_UPSTREAM_DEPENDENCY_CHECK = config.get('SKIP_UPSTREAM_DEPENDENCY_CHECK')
println '======================================================================================'
println 'Checking to see if ' + jobName + ' really needs to be rebuilt.'
println ''

def skipThisBuild = true
def lastSuccessfulBuild = Jenkins.instance.getJob(jobName).getLastSuccessfulBuild()

if (REBUILD_ALL == 'true') {

    println 'The REBUILD_ALL flag has been set'
    println ''
    println 'Continuing with the build.'
    skipThisBuild = false

} else if (DEPENDENCY_JOB == null) {
    println 'The DEPENDENCY_JOB variable has not been set in the Variable bindings section'
    println 'of the launch script. There is no way to determine dependency.'
    println ''
    println 'Continuing with the build.'
    skipThisBuild = false
} else {

    // Make sure there is at least one successful build.
    if (lastSuccessfulBuild != null) {
        println 'Found at least one successful build of this job.'
        println ''

        // Get the build after the last successful
        def nextBuild = lastSuccessfulBuild.getNextBuild()

        def subsequentFailedBuild = false

        // Loop through all of the builds after the last successful
        // build looking for a build that was not ABORTED
        while ( nextBuild != build ) {
            def result = nextBuild.getResult()
            if (result == Result.ABORTED) {
                nextBuild = nextBuild.getNextBuild()
            } else {
                // Drop out of the loop and set boolean to force rebuild.
                nextBuild = build
                subsequentFailedBuild = true
            }
        }

        // Make sure the last build was a success
        if (subsequentFailedBuild == false) {

            println 'No build failures since last successful build.'
            println ''
            // If there are CMS changes then the build must be run
            def changeSet = build.getChangeSet()
            if (changeSet.isEmptySet()) {
                println 'No CMS file changes to build.'
                println ''

                if (SKIP_UPSTREAM_DEPENDENCY_CHECK == 'true') {
                    println 'The upstream dependency check has been disabled.'
                    println ''
                    if (CHILDREN_JOBS == null)
                        println 'No need to run the build. It will be ABORTED.'
                } else {
                    // Get the workspace on the dependency calculator job workspace
                    def workspace = Jenkins.instance.getJob(DEPENDENCY_JOB).lastBuild.workspace;

                    // As we are always retrieving the file from master we can always assume Unix path separators
                    def dependencyFilePath = workspace.toString() + "/" + DEPENDENCY_FILE
                    def dependencyFile = new File(dependencyFilePath)

                    if (! dependencyFile.exists()) {
                        println "The dependency file " + DEPENDENCY_FILE
                        println "does not exist in the workspace of dependency job " + DEPENDENCY_JOB
                        println ''
                        println 'The safest course of action is to rebuild the job.'
                        println ''
                        println 'Continuing with the build.'
                    } else {
                        // Get the last successful build time of the current job
                        def lastSuccessBuildTimeMillis = lastSuccessfulBuild.getTimeInMillis()

                        // Read the dependencies from a java format properties file
                        Properties dependencies = new Properties();
                        dependencies.load(new FileInputStream(dependencyFile))
                        def dependencyLine = dependencies.getProperty(jobName)

                        if (dependencyLine == null) {
                            println 'Cannot find upstream dependencies for this job name ' + jobName
                            println 'in the dependency file ' + DEPENDENCY_FILE + '.'
                            println 'If there are no dependencies please set the SKIP_UPSTREAM_DEPENDENCY_CHECK'
                            println 'boolean so that the dependency analysis can be skipped'
                            println ''
                            println 'Given that we cannot vouchsafe all upstream dependencies this job will build.'
                            skipThisBuild = false
                        } else {

                            // Split the csv line so that we deal with the build jobs one at a time.
                            ArrayList<String> dependencyList = Arrays.asList(dependencyLine.split(','))

                            // Loop through each of the upstream dependent jobs
                            for (String dependencyString : dependencyList) {
                                def dependency = dependencyString.stripIndent()
                                def dependencyJob = Jenkins.instance.getJob(dependency)

                                if (dependencyJob == null) {
                                    println 'Cannot find the time stamp associated with the upstream dependent build job ' + dependency + '.'
                                    println 'The assumption is that there is something amiss in the dependency.properties file.'
                                    println 'Check to ensure that all of the upstream dependent jobs are spelled correctly'
                                    println ''
                                    println 'Given that we cannot vouchsafe upstream dependencies this job will build.'
                                    skipThisBuild = false
                                    break
                                }

                                def dependencyLastSuccessBuildTimeMillis = dependencyJob.getLastSuccessfulBuild().getTimeInMillis()

                                println 'Checking the upstream dependency build time of ' + dependency

                                if (dependencyLastSuccessBuildTimeMillis > lastSuccessBuildTimeMillis) {
                                    println 'The upstream job ' + dependency + ' was last successfully built after this job.'
                                    println 'As a consequence, this job needs to be rebuilt.'
                                    println ''
                                    println 'Continuing with the build.'
                                    skipThisBuild = false
                                    break
                                }
                                println 'The last successful build time of ' + dependency + ' is before'
                                println 'the last successful build time of this job.'
                                println 'The job ' + dependency + ' will not trigger a new build.'
                                println ''
                            }
                            if (skipThisBuild) {
                                println 'There are no upstream dependency changes.'
                                println ''
                                if (CHILDREN_JOBS == null)
                                    println 'No need to run the build. It will be ABORTED'
                            }
                        }
                    }
                }
            } else {
                println 'There are file changes in the CMS system.'
                println ''
                println 'Continuing with the build.'
                skipThisBuild = false
            }

        } else {
            println 'There has been a build failure since the last success.'
            println ''
            println 'Continuing with the build.'
            skipThisBuild = false
        }

    } else {
        println 'The current job "' + jobName + '" has never built successfully.'
        println ''
        println 'Continuing with the build.'
        skipThisBuild = false
    }
}

// Addtional test for parent pom's to ensure that if children jobs have failures that are
// after the last successful build time of the parent job that the build will continue
if (skipThisBuild && CHILDREN_JOBS != null) {
    println 'This is a parent pom. I need to check the status of the children jobs.'
    println ''

    // Get the last successful build time for comparison later
    def lastSuccessfulParentBuildInMillis = lastSuccessfulBuild.getTimeInMillis()

    // Split the csv line so that we deal with the build jobs one at a time.
    ArrayList<String> childJobList = Arrays.asList(CHILDREN_JOBS.split(','))

    for (String childJobString : childJobList) {
        // Remove any leading and trailing spaces.
        childJobString = childJobString.stripIndent()
        // Remove any double quotes just in case someone added them.
        childJobString = childJobString.replace("\"","")
        println 'Looking for failures of child job "' + childJobString + '".'
        def childJob = Jenkins.instance.getJob(childJobString)
        if (childJob == null) {
            println 'The child job "' + childJobString + '" does not exist.'
            println 'Possible misspelling of the job name?'
            println 'Job "' + childJobString + '" will not be considered in determining if the build should run.'
            println ''
        } else {
            // Now that we know the job exists lets get the last failed build information
            def lastFailedChildBuild = childJob.getLastFailedBuild()
            if (lastFailedChildBuild == null) {
                println 'There are no failed builds for this child job.'
                println ''
            } else {
                def lastFailedChildBuildTimeMillis = lastFailedChildBuild.getTimeInMillis()
                if (lastFailedChildBuildTimeMillis > lastSuccessfulParentBuildInMillis) {
                    println 'Found a child build failure after last parent success.'
                    println ''
                    println 'Continuing with the build.'
                    skipThisBuild = false
                    break
                } else {
                    println 'No child build failures after last successful parent build.'
                    println ''
                }
            }
        }
    }
    if (skipThisBuild) {
        println 'No failed children builds after the last successful parent build.'
        println ''
        println 'No need to run the build. It will be ABORTED.'
    }
}

if (skipThisBuild) {
    // The setResult is just to have additional info in the build log
    // The build will still identify as ABORTED
    try {
        build.doStop()
        throw new InterruptedException("No need to continue this build.")
    } catch (IOException e) {
        listener.getLogger().println(e.getMessage())
    }

}
println '======================================================================================'
