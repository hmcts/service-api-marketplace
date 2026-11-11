package uk.gov.hmcts.amp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.amp.config.NotifyProperties;
import uk.gov.hmcts.amp.mappers.NotifyMapper;
import uk.gov.hmcts.amp.domain.ProductionAccessDetail;
import uk.gov.hmcts.amp.domain.ProductionAccessRequest;
import uk.gov.hmcts.amp.entity.ApplicationApiEntity;
import uk.gov.hmcts.amp.entity.ApplicationEntity;
import uk.gov.hmcts.amp.entity.MarketplaceApiEntity;
import uk.gov.hmcts.amp.entity.ProductionRequestEntity;
import uk.gov.hmcts.amp.entity.UserEntity;
import uk.gov.hmcts.amp.repository.ApplicationApiRepository;
import uk.gov.hmcts.amp.repository.ApplicationRepository;
import uk.gov.hmcts.amp.repository.MarketplaceApiRepository;
import uk.gov.hmcts.amp.repository.ProductionRequestRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionRequestServiceTest {

    private static final String OID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String CLIENT_ID = "22222222-2222-2222-2222-222222222222";
    private static final List<String> REVIEWERS =
        List.of("panel@justice.gov.uk", "owner@justice.gov.uk", "team@hmcts.net");
    private static final List<String> AMP_TEAM = List.of("APIMarketplace@hmcts.net");

    @Mock
    private ProductionRequestRepository requests;

    @Mock
    private ApplicationRepository applications;

    @Mock
    private UserService users;

    @Mock
    private ApplicationApiRepository applicationApis;

    @Mock
    private MarketplaceApiRepository apis;

    @Mock
    private NotifyService notify;

    @Mock
    private NotifyProperties notifyProperties;

    @Mock
    private NotifyMapper notifyMapper;

    @Mock
    private ClockService clockService;

    @InjectMocks
    private ProductionRequestService service;


    @Test
    void references_that_keep_colliding_should_give_up_rather_than_loop() {
        UserEntity user = signedIn();
        when(users.requireSignedIn(OID)).thenReturn(user);
        when(applications.findByClientId(CLIENT_ID)).thenReturn(Optional.of(owned(user)));
        when(requests.existsByReference(any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(OID, form()))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));

        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void reading_a_request_should_return_what_was_asked_for() {
        UserEntity user = signedIn();
        when(users.requireSignedIn(OID)).thenReturn(user);
        when(requests.findByUserIdAndReference(user.getId(), "PCR-ABCD2345"))
            .thenReturn(Optional.of(stored(user)));

        ProductionAccessDetail detail = service.get(OID, "PCR-ABCD2345");

        assertThat(detail.reference()).isEqualTo("PCR-ABCD2345");
        assertThat(detail.organisation()).isEqualTo("Example Legal Ltd");
        assertThat(detail.dataRequest()).isEqualTo("Hearing outcomes for listed cases.");
        assertThat(detail.proposedOutputs()).isEqualTo("A weekly outcomes report.");
        assertThat(detail.applicationName()).isEqualTo("Case Viewer");
        assertThat(detail.clientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void another_users_reference_should_be_not_found_rather_than_refused() {
        UserEntity user = signedIn();
        when(users.requireSignedIn(OID)).thenReturn(user);
        // The lookup is keyed on the owner too, so someone else's reference simply is not there
        // and the caller cannot tell it apart from one that never existed.
        when(requests.findByUserIdAndReference(user.getId(), "PCR-SOMEONE1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(OID, "PCR-SOMEONE1"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleted_application_should_leave_the_request_readable() {
        UserEntity user = signedIn();
        ProductionRequestEntity request = stored(user);
        request.setApplication(null);
        when(users.requireSignedIn(OID)).thenReturn(user);
        when(requests.findByUserIdAndReference(user.getId(), "PCR-ABCD2345")).thenReturn(Optional.of(request));

        ProductionAccessDetail detail = service.get(OID, "PCR-ABCD2345");

        assertThat(detail.clientId()).isNull();
        assertThat(detail.applicationName()).isEqualTo("Case Viewer");
    }

    @Test
    void the_subscribed_apis_should_be_named_by_title_for_whichever_template_wants_them() {
        UserEntity user = signedIn();
        givenRequestWillBeRecorded(user);
        when(apis.findAll()).thenReturn(List.of(api("slc", "Scheduling and Listing Court Schedule"),
            api("rcc", "Refdata Court Hearing Courthouses")));
        when(applicationApis.findByApplicationId(10L))
            .thenReturn(List.of(subscription("slc"), subscription("rcc")));

        service.create(OID, form());

        verify(notifyMapper).toRequesterEmail(any(), any(),
            eq(List.of("Refdata Court Hearing Courthouses", "Scheduling and Listing Court Schedule")));
    }

    @Test
    void subscription_to_an_api_we_cannot_name_should_still_appear() {
        UserEntity user = signedIn();
        givenRequestWillBeRecorded(user);
        when(applicationApis.findByApplicationId(10L)).thenReturn(List.of(subscription("slc")));

        service.create(OID, form());

        verify(notifyMapper).toRequesterEmail(any(), any(), eq(List.of("slc")));
    }

    @Test
    void the_requester_the_panel_and_the_marketplace_team_should_each_be_emailed() {
        UserEntity user = signedIn();
        givenRequestWillBeRecorded(user);

        service.create(OID, form());

        verify(notifyMapper).toRequesterEmail(any(), any(), any());
        REVIEWERS.forEach(reviewer ->
            verify(notifyMapper).toReviewEmail(any(), any(), any(), eq(reviewer)));
        AMP_TEAM.forEach(inbox ->
            verify(notifyMapper).toTeamEmail(any(), any(), any(), eq(inbox)));
        verify(notify, times(REVIEWERS.size() + AMP_TEAM.size() + 1)).send(any());
    }

    @Test
    void outside_production_both_emails_should_go_to_whoever_made_the_request() {
        // The templates still get exercised; the Data Access and Marketplace teams do not.
        UserEntity user = signedIn();
        givenRequestWillBeRecorded(user);
        when(notifyProperties.redirectingToRequester()).thenReturn(true);

        service.create(OID, form());

        verify(notifyMapper).toReviewEmail(any(), any(), any(), eq(user.getEmail()));
        verify(notifyMapper).toTeamEmail(any(), any(), any(), eq(user.getEmail()));
        REVIEWERS.forEach(reviewer ->
            verify(notifyMapper, never()).toReviewEmail(any(), any(), any(), eq(reviewer)));
        // One copy each, however many addresses are configured, plus the requester's own.
        verify(notify, times(3)).send(any());
    }

    @Test
    void redirecting_should_change_where_a_mail_goes_and_not_whether_one_is_sent() {
        UserEntity user = signedIn();
        givenRequestWillBeRecorded(user);
        // Never consulted: an environment with no inboxes stays silent, redirecting or not.
        lenient().when(notifyProperties.redirectingToRequester()).thenReturn(true);
        when(notifyProperties.reviewers()).thenReturn(List.of());
        when(notifyProperties.ampTeam()).thenReturn(List.of());

        service.create(OID, form());

        verify(notifyMapper, never()).toReviewEmail(any(), any(), any(), any());
        verify(notifyMapper, never()).toTeamEmail(any(), any(), any(), any());
        verify(notify).send(any());
    }

    @Test
    void no_reviewers_configured_should_leave_the_requester_emailed_and_nobody_else() {
        UserEntity user = signedIn();
        givenRequestWillBeRecorded(user);
        when(notifyProperties.reviewers()).thenReturn(List.of());
        when(notifyProperties.ampTeam()).thenReturn(List.of());

        service.create(OID, form());

        verify(notifyMapper, never()).toReviewEmail(any(), any(), any(), any());
        verify(notifyMapper, never()).toTeamEmail(any(), any(), any(), any());
        verify(notifyMapper).toRequesterEmail(any(), any(), any());
        verify(notify).send(any());
    }

    @Test
    void refused_send_should_leave_the_request_recorded_and_the_other_email_sent() {
        UserEntity user = signedIn();
        givenRequestWillBeRecorded(user);
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY)).when(notify).send(any());

        assertThat(service.create(OID, form()).reference()).startsWith("PCR-");

        verify(requests).saveAndFlush(any());
        // Notify has no CC, so one reviewer's address failing must not cost the others theirs.
        verify(notify, times(REVIEWERS.size() + AMP_TEAM.size() + 1)).send(any());
    }

    private void givenRequestWillBeRecorded(final UserEntity user) {
        // Field mapping is NotifyMapperTest's job; what matters here is which template is asked for.
        lenient().when(notifyProperties.reviewers()).thenReturn(REVIEWERS);
        lenient().when(notifyProperties.ampTeam()).thenReturn(AMP_TEAM);
        when(users.requireSignedIn(OID)).thenReturn(user);
        when(applications.findByClientId(CLIENT_ID)).thenReturn(Optional.of(owned(user)));
        when(clockService.now()).thenReturn(Instant.parse("2026-10-20T11:00:00Z"));
        when(requests.saveAndFlush(any())).thenAnswer(saved -> saved.getArgument(0));
    }

    private static MarketplaceApiEntity api(final String shortCode, final String title) {
        MarketplaceApiEntity entity = new MarketplaceApiEntity();
        entity.setShortCode(shortCode);
        entity.setTitle(title);
        return entity;
    }

    private static ApplicationApiEntity subscription(final String shortCode) {
        ApplicationApiEntity entity = new ApplicationApiEntity();
        entity.setShortCode(shortCode);
        return entity;
    }

    private ProductionRequestEntity stored(final UserEntity user) {
        ProductionRequestEntity request = new ProductionRequestEntity();
        request.setUser(user);
        request.setApplication(owned(user));
        request.setApplicationName("Case Viewer");
        request.setReference("PCR-ABCD2345");
        request.setStatus(ProductionRequestEntity.SUBMITTED);
        request.setSubmittedAt(LocalDateTime.of(2026, 10, 19, 11, 0));
        request.setOrganisation("Example Legal Ltd");
        request.setGoLiveDate(LocalDate.of(2027, 3, 27));
        request.setCallVolume("low");
        request.setDataRequest("Hearing outcomes for listed cases.");
        request.setProposedOutputs("A weekly outcomes report.");
        return request;
    }

    private UserEntity signedIn() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("ada.lovelace@hmcts.net");
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        return user;
    }

    private ApplicationEntity owned(final UserEntity user) {
        ApplicationEntity application = new ApplicationEntity();
        application.setId(10L);
        application.setUser(user);
        application.setClientId(CLIENT_ID);
        application.setName("Case Viewer");
        return application;
    }

    private ProductionAccessRequest form() {
        return new ProductionAccessRequest(CLIENT_ID, "Example Legal Ltd", LocalDate.of(2027, 3, 27),
            "low", "Hearing outcomes for listed cases.", null,
            "external", List.of("own-team", "supplier"),
            "Our delivery partner builds the case view on our behalf.", "no", null);
    }
}
