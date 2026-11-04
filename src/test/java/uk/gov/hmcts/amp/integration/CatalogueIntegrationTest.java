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

@SpringBootTest(properties = "apim.portal-url=" + WireMockPortalInitialise.PORTAL)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class, WireMockPortalInitialise.class})
class CatalogueIntegrationTest {

    private static final String PATH = "/catalogue";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void the_list_should_carry_what_the_catalogue_pages_show() throws Exception {
        mockMvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[1].shortCode").value("slc"))
            .andExpect(jsonPath("$[1].title").value("Scheduling and Listing Court Schedule"))
            // Derived from the published specification, not stored.
            .andExpect(jsonPath("$[1].summary").value(containsString("court hearing schedule")))
            .andExpect(jsonPath("$[1].team").value("api-marketplace"))
            .andExpect(jsonPath("$[1].domain").value("Scheduling and listing"))
            .andExpect(jsonPath("$[1].platform").value("Common Platform"))
            .andExpect(jsonPath("$[1].subscribable").value(true));
    }

    @Test
    void the_list_should_be_ordered_by_title_so_the_page_needs_no_sort() throws Exception {
        mockMvc.perform(get(PATH))
            .andExpect(jsonPath("$[0].title").value("Refdata Court Hearing Courthouses"))
            .andExpect(jsonPath("$[1].title").value("Scheduling and Listing Court Schedule"));
    }

    @Test
    void the_list_should_leave_out_what_the_detail_page_is_for() throws Exception {
        mockMvc.perform(get(PATH))
            .andExpect(jsonPath("$[1].description").doesNotExist())
            .andExpect(jsonPath("$[1].docsUrl").doesNotExist())
            .andExpect(jsonPath("$[1].gatewayPath").doesNotExist());
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
