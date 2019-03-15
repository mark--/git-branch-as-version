package de.markschaefer.maven.branchinversion;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ReleaseBranchDetectionTest {

    /**
     * @see "https://github.com/mark--/git-branch-as-version/issues/1"
     */
    @Test
    public void releaseBranchPatternIsHonored() {
	SetMavenVersionFromGitBranch cut = new SetMavenVersionFromGitBranch();
	cut.releaseBranchPattern = "^release/.*";

	assertThat(cut.isReleaseBranch("relea/blub"), is(false));
	assertThat(cut.isReleaseBranch("relea/blub-fdsfds-release"), is(false));
	assertThat(cut.isReleaseBranch(" release/blub"), is(false));
	assertThat(cut.isReleaseBranch("release/blub"), is(true));
    }

}
