package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// rcc is blanked rather than left to the application default, so the case below stays a case:
// every API having a resource app is the goal, and the catalogue still has to cope with one that
// does not yet.
@SpringBootTest(properties = {
    "apim.portal-url=" + WireMockPortalInitialise.PORTAL,
    "entra.resource-app-ids.slc=" + CatalogueIntegrationTest.SLC_RESOURCE_APP_ID,
    "entra.resource-app-ids.rcc="})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class, WireMockPortalInitialise.class})
class CatalogueIntegrationTest {

    static final String SLC_RESOURCE_APP_ID = "11111111-1111-1111-1111-111111111111";

    private static final String PATH = "/catalogue";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void the_list_should_carry_what_the_catalogue_pages_show() throws Exception {
        mockMvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[3].shortCode").value("slc"))
            .andExpect(jsonPath("$[3].title").value("Scheduling and Listing Court Schedule"))
            // Derived from the published specification, not stored.
            .andExpect(jsonPath("$[3].summary").value(containsString("court hearing schedule")))
            .andExpect(jsonPath("$[3].team").value("api-marketplace"))
            .andExpect(jsonPath("$[3].domain").value("Scheduling and listing"))
            .andExpect(jsonPath("$[3].platform").value("Common Platform"))
            .andExpect(jsonPath("$[3].subscribable").value(true));
    }

    @Test
    void the_list_should_be_ordered_by_title_so_the_page_needs_no_sort() throws Exception {
        mockMvc.perform(get(PATH))
            .andExpect(jsonPath("$[0].title").value("Crime Defendant Details"))
            .andExpect(jsonPath("$[1].title").value("Crime Prosecution Case Details"))
            .andExpect(jsonPath("$[2].title").value("Refdata Court Hearing Courthouses"))
            .andExpect(jsonPath("$[3].title").value("Scheduling and Listing Court Schedule"));
    }

    @Test
    void the_list_should_leave_out_what_the_detail_page_is_for() throws Exception {
        mockMvc.perform(get(PATH))
            .andExpect(jsonPath("$[3].description").doesNotExist())
            .andExpect(jsonPath("$[3].docsUrl").doesNotExist())
            .andExpect(jsonPath("$[3].gatewayPath").doesNotExist())
            .andExpect(jsonPath("$[3].resourceAppId").doesNotExist())
            .andExpect(jsonPath("$[3].role").doesNotExist());
    }

    @Test
    void one_api_should_add_the_description_and_the_links() throws Exception {
        mockMvc.perform(get(PATH + "/slc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shortCode").value("slc"))
            .andExpect(jsonPath("$.catalogueName").value("api-cp-crime-schedulingandlisting-courtschedule"))
            .andExpect(jsonPath("$.description").value(containsString("listed to take place")))
            .andExpect(jsonPath("$.docsUrl")
                .value("https://hmcts.github.io/api-cp-crime-schedulingandlisting-courtschedule/"))
            .andExpect(jsonPath("$.repoUrl")
                .value("https://github.com/hmcts/api-cp-crime-schedulingandlisting-courtschedule"))
            .andExpect(jsonPath("$.gatewayPath").value("amp/try/slc"));
    }

    @Test
    void one_api_should_link_to_its_page_in_the_developer_portal() throws Exception {
        // The portal is keyed on the APIM api name, which names the real API rather than
        // the Try It Now one the marketplace subscribes to.
        mockMvc.perform(get(PATH + "/slc"))
            .andExpect(jsonPath("$.portalUrl").value(WireMockPortalInitialise.PORTAL
                + "/api-details#api=common-platform-api-scheduling-and-listing-court-schedule"));

        mockMvc.perform(get(PATH + "/rcc"))
            .andExpect(jsonPath("$.portalUrl").value(WireMockPortalInitialise.PORTAL
                + "/api-details#api=common-platform-api-refdata-court-hearing-court-houses"));
    }

    @Test
    void one_api_should_carry_what_a_consumer_asks_for_a_token_on() throws Exception {
        mockMvc.perform(get(PATH + "/slc"))
            .andExpect(jsonPath("$.resourceAppId").value(SLC_RESOURCE_APP_ID))
            .andExpect(jsonPath("$.role").value("app.read"));
    }

    @Test
    void an_api_with_no_resource_app_should_be_published_without_one_rather_than_hidden() throws Exception {
        mockMvc.perform(get(PATH + "/rcc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resourceAppId").doesNotExist())
            .andExpect(jsonPath("$.role").value("app.read"));
    }

    @Test
    void an_api_nobody_publishes_should_be_not_found() throws Exception {
        mockMvc.perform(get(PATH + "/nonsense"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value(containsString("nonsense")));
    }

    @Test
    void the_catalogue_should_need_no_requesting_user() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isOk());
        mockMvc.perform(get(PATH + "/slc")).andExpect(status().isOk());
    }
}
