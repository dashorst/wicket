#!/bin/bash
set -e
echo -n "Reverting release 11.0.0

Actions about to be performed:
------------------------------

$(cat $0 | tail -n +15)

------------------------------------------
Press enter to continue or CTRL-C to abort"

read

# clean up local repository
git checkout master
git branch -D build/wicket-11.0.0
git tag -d rel/wicket-11.0.0

# clean up staging repository
git push staging --delete refs/heads/build/wicket-11.0.0
git push staging --delete rel/wicket-11.0.0

# clean up staging dist area
svn rm https://dist.apache.org/repos/dist/dev/wicket/11.0.0 -m "Release vote has failed"

# clean up staging maven repository
mvn org.sonatype.plugins:nexus-staging-maven-plugin:LATEST:rc-drop -DstagingRepositoryId=orgapachewicket-1284 -DnexusUrl=https://repository.apache.org -DserverId=apache.releases.https -Ddescription="Release vote has failed"

# clean up remaining release files
find . -name "*.releaseBackup" -exec rm {} +
rm -f release.properties
