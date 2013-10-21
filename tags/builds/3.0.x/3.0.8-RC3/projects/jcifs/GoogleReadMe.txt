Googlers,

If you modify JCIFS here, please follow these guidelines:


0) Make sure you define a build.bootclasspath property in your
   ${user.home}/google-enterprise-connector-filesystem.properties
   file that points at a Java 1.5 boot classpath.  This allows
   the jcifs.jar file to load correctly in deployments running
   Java 1.5 (which we currently still support).

1) Bump the version at the top of build.xml
   (for instance 1.3.15.2 -> 1.3.15.3).
   I've been using the 4th segment of the
   version string to track Google modifications.

2) Add a couple of words describing your change
   to the "Google-Modified" manifest entry in
   the "jar" target of build.xml

3) Commit your changes, and the above, to the
   subversion repository from the projects/jcifs
   directory.  This isolates commits to the
   jcifs project from commits to the connector
   project.

4) Copy the the new jcifs-{$version}.jar file to
   projects/file-system-connector/third-party/prod/jcifs.jar
   and commit that.  (Dropping the version from the
   jar file name makes it easier to drop-in upgrade
   the connectors.)

5) Also add the new jcifs.jar file to the Sharepoint
   projects/sharepoint/lib directory and commit it.
   Both connectors use jcifs.jar and they need to be
   kept in sync.
   
The version string and manifest entries especially
make it easier to track Google modifications to 
third party libraries.

You can easily see all the commits to the jcifs
project from the codesite:

http://code.google.com/p/google-enterprise-connector-file-system/source/list?path=trunk/projects/jcifs

