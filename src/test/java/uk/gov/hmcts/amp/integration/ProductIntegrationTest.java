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

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class})
class ProductIntegrationTest {

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
    void the_gateway_path_and_role_should_both_be_built_from_the_short_code() throws Exception {
        mockMvc.perform(get(PATH))
            .andExpect(jsonPath("$[1].gatewayPath").value("amp/try/slc"))
            .andExpect(jsonPath("$[1].role").value("slc.read"))
            .andExpect(jsonPath("$[0].gatewayPath").value("amp/try/rcc"))
            .andExpect(jsonPath("$[0].role").value("rcc.read"));
    }

    @Test
    void the_catalogue_should_need_no_caller_since_it_holds_nothing_of_anyones() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isOk());
    }
}
