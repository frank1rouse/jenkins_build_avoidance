import hudson.model.*
import hudson.scm.*
import jenkins.model.*
import java.util.ArrayList
import java.util.HashMap

def debug = false

// Grab any variables defined in the Variable bindings section of the Execute system Groovy script
def HashMap varBindings = binding.variables

// Get the information from the currently running build
def thr = Thread.currentThread()
def build = thr?.executable
def Result newResult = Result.SUCCESS
def modified_result = false

def String JOB_LIST = varBindings.get('JOBS_LIST')
if (JOB_LIST == null) {

    println ''
    println 'Missing JOB_LIST variable in the Variable bindings section of the launch script'
    println 'Aborting the build until this corrected.'
    build.doStop()
}

println '======================================================================================================'
println 'The "abort_status_fix.groovy" script will update the current build status based on the latest status'
println 'of build jobs supplied in the JOB_LIST variable in the Variable bindings section of the launch script.'
println 'Any status of ABORT will be ignored as this indicates that the build was skipped.'

def String PARENT_POM = varBindings.get('PARENT_POM')

if (PARENT_POM != null) {
    println ''
    println 'This is a parent pom job.'

    // The outcome of this job should always be recorded.
    modified_result = true

    // Remove any leading and trailing spaces.
    parentJobString = JOB_LIST.stripIndent()
    // Remove any double quotes just in case someone added them.
    parentJobString = parentJobString.replace("\"","")
    def parent_job = Jenkins.instance.getJob(parentJobString)

    if (parent_job == null) {
        println ''
        println 'Misconfigured JOB_LIST. When the PARENT_POM setting is enabled there should be a single.'
        println 'entry in the JOBS_LIST referencing the job creating the parent pom.xml artifact'
        println 'Aborting the build until this corrected.'
        build.doStop()
    }
    def lastBuildResult = parent_job.getLastBuild().getResult()
    if (lastBuildResult == Result.ABORTED) {
        println ''
        println 'It seems that the parent pom build was avoided. This implies that there are no'
        println 'changes in the entire build.'
        println 'Setting the build result to NOT_BUILT'
        newResult = Result.NOT_BUILT
    }
} else {
    // Split the csv line so that we deal with the build jobs one at a time.
    ArrayList<String> jobList = Arrays.asList(JOB_LIST.split(','))

    println ''
    println 'Initial build status set to "' + newResult.toString() + '"'

    for (String jobString : jobList) {
        // Remove any leading and trailing spaces.
        jobString = jobString.stripIndent()
        // Remove any double quotes just in case someone added them.
        jobString = jobString.replace("\"","")
        println ''
        println 'Examining the latest status of job "' + jobString + '".'
        def job = Jenkins.instance.getJob(jobString)
        if (job == null) {
            println 'The job "' + jobString + '" does not exist.'
            println 'Possible misspelling of the job name?'
            println 'Job "' + jobString + '" will not be considered when setting status.'
        } else {
            // Now that we know the job exists lets get the last build information
            def lastBuild = job.getLastBuild()
            if (debug)
                println 'Now working build "' + lastBuild.toString() + '".'
            def lastBuildResult = lastBuild.getResult()
            println 'The last build status of this job is "' + lastBuildResult.toString() + '"'

            if (debug)
                println 'The status of newResult prior to the combine is "' + newResult.toString() + '"'
            // We are ignoring the ABORTED result as this means the build was skipped
            if (lastBuildResult == Result.ABORTED) {
                println 'Not updating the build status.'
            } else {
                newResult = newResult.combine(lastBuildResult)
                println 'Build status has been updated to "' + newResult.toString() + '"'
                modified_result = true

            }
            if (debug)
                println 'New build result = "' + newResult.toString() + '"'
        }
    }
}
println ''
if (modified_result) {
    println 'Finished and updating the current build status to "' + newResult.toString() + '"'
    build.setResult(newResult)
} else {
    println 'No job(s) were actually run.'
    println 'Leaving the result as "' + build.getResult() + '"'

}
println '======================================================================================================'