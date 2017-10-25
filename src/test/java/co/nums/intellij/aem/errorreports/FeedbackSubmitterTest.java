package co.nums.intellij.aem.errorreports;

import co.nums.intellij.aem.service.PluginInfoProvider;
import co.nums.intellij.aem.test.junit.extensions.MockitoExtension;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.diagnostic.SubmittedReportInfo.SubmissionStatus;
import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.Label;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackSubmitterTest {

    private static final int TEST_ISSUE_NUMBER = 1;
    private static final String TEST_ISSUE_TITLE = "Test error [hash]";
    private static final String TEST_ISSUE_BODY = "Test issue body";
    private static final String TEST_ISSUE_URL = "https://github.com/issues/1";

    @Mock
    private PluginInfoProvider pluginInfoProvider;

    @Mock
    private GitHubIssueService gitHubIssueService;

    @Mock
    private GitHubErrorBean errorBean;

    @Captor
    private ArgumentCaptor<Issue> issueCaptor;

    private FeedbackSubmitter sut;

    @BeforeEach
    void setUp() {
        sut = FeedbackSubmitter.INSTANCE;
    }

    @Test
    void shouldSubmitNewGitHubIssueWhenErrorIsNotReportedYet() {
        // given
        when(errorBean.getIssueTitle()).thenReturn(TEST_ISSUE_TITLE);
        when(errorBean.getIssueDetails()).thenReturn(TEST_ISSUE_BODY);
        when(gitHubIssueService.findAutoGeneratedIssueByTitle(TEST_ISSUE_TITLE)).thenReturn(null);
        Issue submittedIssue = mock(Issue.class);
        when(submittedIssue.getHtmlUrl()).thenReturn(TEST_ISSUE_URL);
        when(submittedIssue.getNumber()).thenReturn(TEST_ISSUE_NUMBER);
        when(gitHubIssueService.submitIssue(any(Issue.class))).thenReturn(submittedIssue);

        // when
        SubmittedReportInfo submittedReportInfo = sut.submitFeedback(pluginInfoProvider, gitHubIssueService, errorBean);

        // then
        assertThat(submittedReportInfo.getStatus()).isEqualTo(SubmissionStatus.NEW_ISSUE);
        assertThat(submittedReportInfo.getLinkText())
                .isEqualTo("Created GitHub issue: <a href=\"https://github.com/issues/1\">#1</a>.<br>" +
                        "Thank you for your feedback!");
        assertThat(submittedReportInfo.getURL()).isEqualTo(TEST_ISSUE_URL);

        verify(gitHubIssueService).submitIssue(issueCaptor.capture());
        Issue issue = issueCaptor.getValue();
        assertThat(issue).isNotNull();
        assertThat(issue.getTitle()).isEqualTo(TEST_ISSUE_TITLE);
        assertThat(issue.getBody()).isEqualTo(TEST_ISSUE_BODY);
        assertThat(issue.getLabels()).extracting(Label::getName).containsOnly("auto-generated");
    }

    @Test
    void shouldAddNewCommentInGitHubIssueWhenErrorIsReportedAlready() {
        // given
        when(errorBean.getIssueTitle()).thenReturn(TEST_ISSUE_TITLE);
        when(errorBean.getIssueDetailsWithoutException()).thenReturn(TEST_ISSUE_BODY);
        Issue existingIssue = mock(Issue.class);
        when(existingIssue.getHtmlUrl()).thenReturn(TEST_ISSUE_URL);
        when(existingIssue.getNumber()).thenReturn(TEST_ISSUE_NUMBER);
        when(gitHubIssueService.findAutoGeneratedIssueByTitle(TEST_ISSUE_TITLE)).thenReturn(existingIssue);

        // when
        SubmittedReportInfo submittedReportInfo = sut.submitFeedback(pluginInfoProvider, gitHubIssueService, errorBean);

        // then
        assertThat(submittedReportInfo.getStatus()).isEqualTo(SubmissionStatus.DUPLICATE);
        assertThat(submittedReportInfo.getLinkText())
                .isEqualTo("Issue was already reported on GitHub: <a href=\"https://github.com/issues/1\">#1</a>.<br>" +
                        "Thank you for your feedback!");
        assertThat(submittedReportInfo.getURL()).isEqualTo(TEST_ISSUE_URL);
        verify(gitHubIssueService).addComment(TEST_ISSUE_NUMBER, TEST_ISSUE_BODY);
    }

    @Test
    void shouldReturnInfoThatIssueIsFixedAlreadyWhenMatchingGitHubIssueHasFixVersionHigherThanRunningPlugin() {
        // given
        String fixedVersion = "0.9.3";
        when(errorBean.getIssueTitle()).thenReturn(TEST_ISSUE_TITLE);
        when(pluginInfoProvider.runningVersionIsOlderThan(fixedVersion)).thenReturn(true);
        Issue existingIssue = mock(Issue.class);
        when(existingIssue.getBody()).thenReturn("fixed:" + fixedVersion);
        when(existingIssue.getHtmlUrl()).thenReturn(TEST_ISSUE_URL);
        when(existingIssue.getNumber()).thenReturn(TEST_ISSUE_NUMBER);
        when(gitHubIssueService.findAutoGeneratedIssueByTitle(TEST_ISSUE_TITLE)).thenReturn(existingIssue);

        // when
        SubmittedReportInfo submittedReportInfo = sut.submitFeedback(pluginInfoProvider, gitHubIssueService, errorBean);

        // then
        assertThat(submittedReportInfo.getStatus()).isEqualTo(SubmissionStatus.DUPLICATE);
        assertThat(submittedReportInfo.getLinkText())
                .isEqualTo("Error is already fixed in version 0.9.3. " +
                        "See GitHub issue: <a href=\"https://github.com/issues/1\">#1</a>.<br>" +
                        "Please update plugin.");
        assertThat(submittedReportInfo.getURL()).isEqualTo(TEST_ISSUE_URL);
        verify(gitHubIssueService).findAutoGeneratedIssueByTitle(TEST_ISSUE_TITLE);
        verifyNoMoreInteractions(gitHubIssueService);
    }

    @Test
    void shouldSubmitNewGitHubIssueWhenErrorIsReportedAndFixedButRunningVersionIsTheSameAsNewerThanFixed() {
        // given
        String fixedVersion = "0.9.3";
        when(errorBean.getIssueTitle()).thenReturn(TEST_ISSUE_TITLE);
        when(errorBean.getIssueDetails()).thenReturn(TEST_ISSUE_BODY);
        Issue existingIssue = mock(Issue.class);
        when(existingIssue.getBody()).thenReturn("fixed:" + fixedVersion);
        when(existingIssue.getHtmlUrl()).thenReturn(TEST_ISSUE_URL);
        when(existingIssue.getNumber()).thenReturn(TEST_ISSUE_NUMBER);
        when(gitHubIssueService.findAutoGeneratedIssueByTitle(TEST_ISSUE_TITLE)).thenReturn(existingIssue);
        Issue submittedIssue = mock(Issue.class);
        when(submittedIssue.getHtmlUrl()).thenReturn("https://github.com/issues/2");
        when(submittedIssue.getNumber()).thenReturn(2);
        when(gitHubIssueService.submitIssue(any(Issue.class))).thenReturn(submittedIssue);

        // when
        SubmittedReportInfo submittedReportInfo = sut.submitFeedback(pluginInfoProvider, gitHubIssueService, errorBean);

        // then
        assertThat(submittedReportInfo.getStatus()).isEqualTo(SubmissionStatus.NEW_ISSUE);
        assertThat(submittedReportInfo.getLinkText())
                .isEqualTo("Created GitHub issue: <a href=\"https://github.com/issues/2\">#2</a>.<br>" +
                        "Thank you for your feedback!");
        assertThat(submittedReportInfo.getURL()).isEqualTo("https://github.com/issues/2");

        verify(gitHubIssueService).submitIssue(issueCaptor.capture());
        Issue issue = issueCaptor.getValue();
        assertThat(issue).isNotNull();
        assertThat(issue.getTitle()).isEqualTo(TEST_ISSUE_TITLE);
        assertThat(issue.getBody()).isEqualTo(TEST_ISSUE_BODY);
        assertThat(issue.getLabels()).extracting(Label::getName).containsOnly("auto-generated");
        verify(gitHubIssueService).addComment(submittedIssue.getNumber(), "The same as in #1.");
    }

    @Test
    void shouldReturnFailStatusWhenExceptionThrownDuringCheckingIfErrorReportedAlready() {
        // given
        when(errorBean.getIssueTitle()).thenReturn(TEST_ISSUE_TITLE);
        when(gitHubIssueService.findAutoGeneratedIssueByTitle(TEST_ISSUE_TITLE)).thenThrow(RuntimeException.class);

        // when
        SubmittedReportInfo submittedReportInfo = sut.submitFeedback(pluginInfoProvider, gitHubIssueService, errorBean);

        // then
        assertThat(submittedReportInfo.getStatus()).isEqualTo(SubmissionStatus.FAILED);
        assertThat(submittedReportInfo.getLinkText()).isEqualTo("Could not communicate with GitHub");
        assertThat(submittedReportInfo.getURL()).isNull();
    }

    @Test
    void shouldReturnFailStatusWhenExceptionThrownDuringCreatingIssue() {
        // given
        when(errorBean.getIssueTitle()).thenReturn(TEST_ISSUE_TITLE);
        when(errorBean.getIssueDetails()).thenReturn(TEST_ISSUE_BODY);
        when(gitHubIssueService.findAutoGeneratedIssueByTitle(TEST_ISSUE_TITLE)).thenReturn(null);
        when(gitHubIssueService.submitIssue(any(Issue.class))).thenThrow(RuntimeException.class);

        // when
        SubmittedReportInfo submittedReportInfo = sut.submitFeedback(pluginInfoProvider, gitHubIssueService, errorBean);

        // then
        assertThat(submittedReportInfo.getStatus()).isEqualTo(SubmissionStatus.FAILED);
        assertThat(submittedReportInfo.getLinkText()).isEqualTo("Could not communicate with GitHub");
        assertThat(submittedReportInfo.getURL()).isNull();
    }

    @Test
    void shouldReturnFailStatusWhenExceptionThrownDuringAddingComment() {
        // given
        when(errorBean.getIssueTitle()).thenReturn(TEST_ISSUE_TITLE);
        when(errorBean.getIssueDetailsWithoutException()).thenReturn(TEST_ISSUE_BODY);
        Issue existingIssue = mock(Issue.class);
        when(existingIssue.getHtmlUrl()).thenReturn(TEST_ISSUE_URL);
        when(existingIssue.getNumber()).thenReturn(TEST_ISSUE_NUMBER);
        when(gitHubIssueService.findAutoGeneratedIssueByTitle(TEST_ISSUE_TITLE)).thenReturn(existingIssue);
        when(gitHubIssueService.addComment(TEST_ISSUE_NUMBER, TEST_ISSUE_BODY)).thenThrow(RuntimeException.class);

        // when
        SubmittedReportInfo submittedReportInfo = sut.submitFeedback(pluginInfoProvider, gitHubIssueService, errorBean);

        // then
        assertThat(submittedReportInfo.getStatus()).isEqualTo(SubmissionStatus.FAILED);
        assertThat(submittedReportInfo.getLinkText()).isEqualTo("Could not communicate with GitHub");
        assertThat(submittedReportInfo.getURL()).isNull();
    }

}
