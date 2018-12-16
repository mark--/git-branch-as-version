package de.qwizzle.tool.branchinversion;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

@Mojo(name = "set", defaultPhase = LifecyclePhase.PRE_CLEAN, threadSafe = false, requiresProject = false,
    inheritByDefault = false)
public class SetMavenVersionFromGitBranch extends AbstractMojo
{
    private static final int MAX_LEVENSHTEIN_EDIT_DISTANCE = 10;

    static final String ISSUE_KEY_REGEX = "\\w\\w+\\-\\d+";

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Component
    private BuildPluginManager pluginManager;

    @Parameter(name = "branchPattern", defaultValue = "(\\w+\\-\\w+).*")
    private String branchPattern;

    @Parameter(name = "versionPattern", defaultValue = "(\\d+\\.\\d+\\.\\d+).*")
    private String versionPattern;

    @Parameter(name = "branchFromEnvironment", defaultValue = "true")
    private boolean branchFromEnvironment;

    @Parameter(name = "branchEnvironmentVariable", defaultValue = "GIT_BRANCH")
    private String branchEnvironmentVariable;

    @Override
    public void execute() throws MojoExecutionException
    {
        String version = getVersion();
        String branch = getBranch();

        if (branch.equals("develop"))
        {
            setVersion(String.format("%s-SNAPSHOT", version));
            return;
        }

        if (branch.equals("master"))
        {
            setVersion(version);
            return;
        }

        Matcher branchMatcher = Pattern.compile(branchPattern).matcher(branch);

        if (!branchMatcher.matches())
        {
            throw new MojoExecutionException("Could not parse branch name: " + branch);
        }

        if (branch.contains("release"))
        {
            String issue = branchMatcher.group(1);
            setVersion(String.format("%s-%s-RC-SNAPSHOT", version, issue));
            return;
        }
        else
        {
            String issue = branchMatcher.group(1);
            setVersion(String.format("%s-%s-SNAPSHOT", version, issue));
            return;
        }

    }

    /**
     * @return the first capturing group matched by the regex {@link #versionPattern}, as default
     *         the MAJOR.MINOR.MICRO part of the version, where all three parts are expected to be
     *         numbers
     * @throws MojoExecutionException if the version does not match or no capturing group was
     *         defined
     */
    String getVersion() throws MojoExecutionException
    {
        Matcher versionMatcher = Pattern.compile(versionPattern).matcher(project.getVersion());
        if (!versionMatcher.matches())
        {
            throw new MojoExecutionException("Could not parse Maven version: " + project.getVersion());
        }

        try
        {
            return versionMatcher.group(1);
        }
        catch (IndexOutOfBoundsException e)
        {
            throw new MojoExecutionException(
                "Could not parse Maven version due to missing capturing group in regex: " + versionPattern,
                e);
        }
    }

    void setVersion(String newVersion) throws MojoExecutionException
    {
        getLog().info("Setting version: " + newVersion);
        executeMojo(
            plugin(
                groupId("org.codehaus.mojo"),
                artifactId("versions-maven-plugin"),
                version("2.3")),
            goal("set"),
            configuration(
                element(name("generateBackupPoms"), "false"),
                element(name("newVersion"), newVersion)),
            executionEnvironment(
                project,
                mavenSession,
                pluginManager));
    }

    String getBranch() throws MojoExecutionException
    {
        if (branchFromEnvironment)
        {
            getLog().info("Taking branch name from environment variable: " + branchEnvironmentVariable);
            String branch = System.getenv(branchEnvironmentVariable);
            if (branch == null)
            {
                getLog().warn("Environment variable not defined: " + branchEnvironmentVariable);
                List<String> possibleVariables = getPossibleVariables(branchEnvironmentVariable);
                if (!possibleVariables.isEmpty())
                {
                    getLog()
                        .warn("Did you mean one of: "
                            + possibleVariables.stream().sorted().collect(joining(", ")));
                }

            }
            else
            {
                return lastPartOfBranch(branch);
            }
        }

        String branch = lastPartOfBranch(getBranchOrRaiseException());

        getLog().info("Found branch: " + branch);
        return branch;
    }

    List<String> getPossibleVariables(String gitBranchEnvironmentVariable2)
    {
        List<String> result = new LinkedList<>();
        for (String env : System.getenv().keySet())
        {
            if (LevenshteinDistance.getDefaultInstance().apply(env,
                gitBranchEnvironmentVariable2) <= MAX_LEVENSHTEIN_EDIT_DISTANCE)
            {
                result.add(env);
            }
        }

        return result;
    }

    String extractIssue(Pattern pattern, String branchName)
    {
        Matcher matcher = pattern.matcher(branchName);
        if (matcher.find())
        {
            String issue = matcher.group(0);
            return issue;
        }
        return null;

    }

    String getBranchOrRaiseException() throws MojoExecutionException
    {
        try
        {
            Repository repo = new FileRepositoryBuilder().setGitDir(new File(".git")).build();
            String branch = repo.getBranch();
            if (!isObjectId(branch))
            {
                return branch;
            }

            List<String> branchesForCommit = branchesForCommit(repo, repo.resolve(branch));

            if (branchesForCommit.size() != 1)
            {
                throw new MojoExecutionException("Ambigious branches detected: " + branchesForCommit
                    + ". Consider using the 'branchFromEnvironment' and 'branchEnvironmentVariable' options.");
            }

            return branchesForCommit.get(0);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException(
                "Could not get git branch from repository. Maybe no git repository present?", e);
        }
    }

    boolean isObjectId(String branch)
    {
        return branch.toLowerCase().matches("[0123456789abcdef]+");
    }

    List<String> branchesForCommit(Repository repo, ObjectId objectId) throws MojoExecutionException
    {
        return repo.getAllRefs().values().stream()
            .filter(ref -> ref.getObjectId().equals(objectId))
            .map(ref -> ref.getName())
            .map(this::lastPartOfBranch)
            .filter(ref -> !"HEAD".equals(ref))
            .collect(toList());
    }

    String lastPartOfBranch(String branch)
    {
        String[] split = branch.split("/");
        return split[split.length - 1];
    }

}
