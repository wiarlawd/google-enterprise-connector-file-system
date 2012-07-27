This is a vendor branch of original JCIFS source distributions.
The source distribution archives are available from 
http://jcifs.samba.org/src/

Download the .tgz versions, rather than .zip versions to minimize
diffs.  The line endings for all the text files are different between
the two archives.

The strategy employed here is loosely based upon vendor branches
as described in the "Third-Party Code" chapter of Mike Mason's book,
"Pragmatic Version Control Using Subversion".
http://books.google.com/books/about/Pragmatic_Version_Control_using_Subversi.html

Once you have checked in a new version of JCIFS into this vendor
branch, you can merge the diffs between the new version and the
last version into our modified JCIFS as follows:

Check out the file system Connector trunk as you would normally.
Include the projects/jcifs directory in the checkout.

svn checkout https://google-enterprise-connector-file-system.googlecode.com/svn/trunk/ google-enterprise-connector-file-system

Change directory to the checked-out working set.
Run "svn merge" to merge the vendor changes into your working set.

svn merge https://google-enterprise-connector-file-system.googlecode.com/svn/branches/vendor/jcifs/1.3.15 https://google-enterprise-connector-file-system.googlecode.com/svn/branches/vendor/jcifs/1.3.17 projects/jcifs

If you are sane, run the svn merge with --dry-run first.
    
You may need to resolve conflicts between the vendor changes and
the Google changes.

Make sure the version at the top of the build.xml reflects
the new vendor version, plus Google additions.  I have been
using the fourth version number to reflect Google mods.
For instance original vendor version is 1.3.15
Google modified version is 1.3.15.3

Commit the changes to the trunk, following the instructions
in projects/jcifs/GoogleReadMe.txt.


