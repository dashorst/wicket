#!/bin/sh
echo "Promoting release 8.0.0-M1

Actions about to be performed:
------------------------------

$(cat $0 | tail -n +14)

------------------------------------------"

read --prompt "Press enter to continue or CTRL-C to abort"

# push the release tag to ASF git repo

git push origin rel/wicket-8.0.0-M1

# promote the source distribution by moving it from the staging area to the release area

svn mv https://dist.apache.org/repos/dist/dev/wicket/8.0.0-M1 https://dist.apache.org/repos/dist/release/wicket -m "Upload release to the mirrors"

mvn org.sonatype.plugins:nexus-staging-maven-plugin:LATEST:rc-release -DstagingRepositoryId=orgapachewicket-1070 -DnexusUrl=https://repository.apache.org -DserverId=apache.releases.https -Ddescription="Release vote has passed"

# Renumber the next development iteration 8.0.0-SNAPSHOT:

git checkout master
mvn release:update-versions --batch-mode
mvn versions:set versions:commit -DnewVersion=8.0.0-SNAPSHOT
git add ` find . ! ( -type d -name "target" -prune ) -name pom.xml `

echo "
Check the new versions and commit and push them to origin:

  git commit -m "Start next development version"
  git push

Remove the previous version of Wicket using this command:

  svn rm https://dist.apache.org/repos/dist/release/wicket/8.0.0-SNAPSHOT -m \"Remove previous version from mirrors\"

"
