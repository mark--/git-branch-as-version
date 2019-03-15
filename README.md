# git-branch-as-version

A Maven plugin for setting the project version according to the current git branch.

It is intended to be used on CI servers to set the version automatically before building and deploying, especially when using Gitflow.

See also (todo: link other plugin)

## Why has the Version to be Changed on Different Branches? 

Let's say the current version on your develop branch is 2.1.4-SNAPSHOT.
Let's further say you created some branches, e.g. feature/TTR-43-Perform-Foo or bugfix/TTR-123-Bar-Not-Printed.
If branches are built automatically and also deployed to a Maven repository, not changing the version in the branches would result in functionally different artifacts with the same version. Obviously, this is bad for testing and keeping track of things in general.

You may change the version manually after creating a new branch, which is error prone or might be forgotten. Additionally, this nearly always leads to merge conflicts which prevent automatic merging in Bitbucket or Gitlab.


## What does this Plugin do for You?

This plugin determines the current branch and sets the maven version accordingly.
The version which is checked in is always in the form 1.2.3-SNAPSHOT. 
If the plugin is activated automatically on your CI server you never have to worry again about the problems mentioned above.
The correct version is set locally before the build proper and not commited to git.

 
## Conventions

The following default behaviour works well with Gitflow and issue-named branches, i.e. branch names which start with a issue number like TR-123.

- The branch master will result in a release version number, e.g. 2.1.4
- The branch develop will result in a snaphot version number, e.g. 2.1.4-SNAPSHOT
- A branch like release/TR-432 will result in a snaphot release candidate version, e.g. 2.1.4-TR-432-RC-SNAPSHOT
- A branch like xyz/TR-123-foo-foo will result in a snapshot version containing the issue number, e.g. 2.1.4-TR-123-SNAPSHOT.
It is not differentiated between feature, hotfix, bugfix, release or other branch types except release branches.


## Usage

This plugin executes internally the version plugin (todo: link) which sets the new version (also for module projects).
Depending on the Maven version and other circumstances this might or might not work with one maven execution. To be sure, it is best to excute maven twice:

````bash
$ mvn clean
$ mvn deploy
````

instead of

````bash
$ mvn clean deploy
````

with the following configuration:


````xml
<plugin>
	<groupId>com.github.mark--</groupId>
	<artifactId>git-branch-as-version</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<executions>
		<execution>
			<id>set git branch version</id>
			<phase>pre-clean</phase>
			<goals>
				<goal>set</goal>
			</goals>
			<inherited>true</inherited>
			<configuration>
				<branchPattern>...</branchPattern>
				<versionPattern>...</versionPattern>
				<branchFromEnvironment>...</branchFromEnvironment>
				<branchEnvironmentVariable>...</branchEnvironmentVariable>
				<releaseBranchPattern>...</releaseBranchPattern>
			</configuration>
		</execution>
	</executions>
</plugin>
````


## Configuration

The plugin supports the follwing configuration options. The default works with JIRA issue IDs in the branch name and Jenkins as build server.


| Property                  | Default                | Description                                                                                                                                                                             |
| ------------------------- | ---------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| branchPattern             | (\\w+\\-\\w+).*        | A Java regex which is matched against the last component of the git branch name. The first capturing group is taken as the branch identifier which is used for the final Maven version. |
| versionPattern            | (\\d+\\.\\d+\\.\\d+).* | A Java regex which is matched against the project version. The first capturing group is taken as the version for the final Maven version.                                               |
| branchFromEnvironment     | true                   | Detect the current branch by some environment variable. If set to false, the plugin tries to detect the version by looking into the git repository.                                     |
| branchEnvironmentVariable | GIT_BRANCH             | The environment variable which contains the current branch. If the environment variable is not present, the plugin tries to detect the version by looking into the git repository.      |
| releaseBranchPattern      | ^release/.*            | Regex which is used to detect release branches.                                                                                                                                         |

