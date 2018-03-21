You may be able to avoid this initial setup if the build job is copied from an
existing build. The genisis build system is edinburgh_buildtest. Look there for
examples.

1. You must create a job for each build stream entitled
        dependency_calculator.<build_name>
   All the groovy scripts and the dependencies.properties file must be copied
   here. The idea is that there is a single place to make updates to the
   dependencies as well as to fix the scripts if they need. Once a change is
   made to any one of these files the dependency_calculator.<build_name> must
   be run so that the changes are propagated to the reset of the build.

2. The dependency.properties file will have to modified as the dependencies
   change with each build. Currently this is a manual process but it is hoped
   that we will be able to automate this in the future.

3. The dependencies are fairly straightforward represenations of what appears in
   either the maven dependencies or Jenkins copy artifact. The exceptions are
   as follows.

   A. Dependencies that arise because of copying artifacts directly in the pom
      files. This has been taken into account in the current dependencies file
      but will have to modified if we want to automate the process.
   B. The parent pom files will have dependencies on any job that the children
      are dependent that are not in the source tree for the parent pom. The
      reason that this was done is that a build will not continue if there
      are no changes recognized in the parent pom job. We must account for
      lower level jobs that are dependent on external jobs.

4. All jobs that are calculated as not needed will be marked as ABORTED with a
   wrapper after the job to compensate for this status. As will all things there
   is an exception. When the parent job is calculated as not needed it will set
   the status to NOT_BUILT which in turn will cause the children jobs to not
   execute. That is why there are some jobs that have an fix_not_built_status
   as opposed to fix_abort_status wrapper.

5. As much as possible I tried to encapsulate the groovy jobs that fix status
   with the jobs for which may have been calculated as not needed. Also the
   build_avoidance.groovy file is encapsulated with the job that copies the
   script into the current work space. It's just a logical construct and
   will aid when we have to rearrange jobs.

6. The build_conversion_notes.txt is not complete but I'm including it here
   in the hopes that I can update it at the end of the build conversion
   process. I also intend to turn this content into a wiki page for
   future reference.