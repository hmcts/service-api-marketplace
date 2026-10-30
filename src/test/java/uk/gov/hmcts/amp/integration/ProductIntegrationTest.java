package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// rcc is blanked rather than left to the application default, so the case below stays a case:
// every API having a resource app is the goal, and the catalogue still has to cope with one that
// does not yet.
@SpringBootTest(properties = {
    "entra.tenant-id=" + ProductIntegrationTest.TENANT_ID,
    "entra.resource-app-ids.slc=" + ProductIntegrationTest.SLC_RESOURCE_APP_ID,
    "entra.resource-app-ids.rcc="})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class})
class ProductIntegrationTest {

    static final String TENANT_ID = "d44f885c-4fac-47bf-afde-d7d861ec4d7b";
    static final String SLC_RESOURCE_APP_ID = "11111111-1111-1111-1111-111111111111";

    private static final String PATH = "/products";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void the_catalogue_should_come_back_keyed_by_short_code_in_order() throws Exception {
        mockMvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].shortCode").value("rcc"))
            .andExpect(jsonPath("$[1].shortCode").value("slc"));
    }

    @Test
    void the_product_id_should_be_the_one_apim_knows_not_the_catalogue_name_stripped() throws Exception {
        mockMvc.perform(get(PATH))
            .andExpect(jsonPath("$[1].catalogueName").value("api-cp-crime-schedulingandlisting-courtschedule"))
            // Stripping api- off that would give a product APIM answers "Product not found" for.
            .andExpect(jsonPath("$[1].productId").value("try-it-now-cp-crime-schedulingandlisting"))
            .andExpect(jsonPath("$[0].productId").value("try-it-now-cp-refdata-courthearing"));
    }

    @Test
    void each_api_should_carry_its_try_it_now_product() throws Exception {
        mockMvc.perform(get(PATH))
            .andExpect(jsonPath("$[1].tryItNowProductId")
                .value("try-it-now-cp-crime-schedulingandlisting"))
            .andExpect(jsonPath("$[0].tryItNowProductId")
                .value("try-it-now-cp-refdata-courthearing"));
    }

    @Test
    void the_gateway_path_should_be_built_from_the_short_code() throws Exception {
        mockMvc.perform(get(PATH))
            .andExpect(jsonPath("$[1].gatewayPath").value("amp/try/slc"))
            .andExpect(jsonPath("$[0].gatewayPath").value("amp/try/rcc"));
    }

    @Test
    void the_role_should_not_name_the_api_because_the_audience_already_does() throws Exception {
        mockMvc.perform(get(PATH))
            .andExpect(jsonPath("$[0].role").value("app.read"))
            .andExpect(jsonPath("$[1].role").value("app.read"));
    }

    @Test
    void the_catalogue_should_publish_what_a_consumer_cannot_deduce() throws Exception {
        // Without the tenant and the resource app id a consumer cannot build a token request.
        mockMvc.perform(get(PATH))
            .andExpect(jsonPath("$[1].tenantId").value(TENANT_ID))
            .andExpect(jsonPath("$[1].resourceAppId").value(SLC_RESOURCE_APP_ID));
    }

    @Test
    void an_api_with_no_resource_app_here_should_be_listed_without_one() throws Exception {
        mockMvc.perform(get(PATH))
            .andExpect(jsonPath("$[0].shortCode").value("rcc"))
            .andExpect(jsonPath("$[0].resourceAppId").doesNotExist());
    }

    @Test
    void the_catalogue_should_need_no_caller_since_it_holds_nothing_of_anyones() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isOk());
    }
}
