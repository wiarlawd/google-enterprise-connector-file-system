Following are the steps to build Google Enterprise Connector for File Systems 2009
========================================================================================
1. Ensure that you have Apache Ant installed on your system. If not, you can get it from http://ant.apache.org/bindownload.cgi

2. Ensure you have Connector Manager on your system. You can get them at
   http://code.google.com/p/google-enterprise-connector-manager/downloads/list.
   Choose connector-manager-2.0.2-src.zip. 
   2b) Unpack the zip file. (unzip connector-manager-2.0.2-src.zip).
   2c) Change directory to connector-manager-2.0.2-src (cd .../connector-manager-2.0.2-src).
   2d) Build the connector manager (ant connector-manager).

3. Create a google-enterprise-connector-filesystem.properties file in your home directory. Add a line to set the
   connector-manager-projects.dir to the connector-manager-2.0.2-src directory from step 2. You can use the
   line below as a template but  must replace ##.MY.PATH.## with the path to connector-manager-2.0.2-src for your 
   system.
   connector-manager-projects.dir=/##.MY.PATH.##/connector-manager-2.0.2-src

   Alternativly you can from the code site using subversion and set connector-manager-projects.dir to the projects
   directory under your subversion client root.

4. From the command prompt, execute the build.xml using "ant jar_prod" command to build the file system
   connector jar file at build/prod/jar/connector-filesystem.jar.

5. To verify your set up run "ant run_tests".

