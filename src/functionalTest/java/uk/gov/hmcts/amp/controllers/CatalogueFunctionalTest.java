package uk.gov.hmcts.amp.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.amp.integration.TestContainersInitialise;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The catalogue against the real developer portal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {TestContainersInitialise.class})
class CatalogueFunctionalTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void every_listed_api_should_return_summary() throws Exception {
        mockMvc.perform(get("/catalogue"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].summary").value(not(blankOrNullString())))
            .andExpect(jsonPath("$[1].summary").value(not(blankOrNullString())));
    }

    @Test
    void slc_should_return_the_description() throws Exception {
        mockMvc.perform(get("/catalogue/slc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value(not(blankOrNullString())));
    }

    @Test
    void rcc_should_return_the_description() throws Exception {
        mockMvc.perform(get("/catalogue/rcc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value(not(blankOrNullString())));
    }
}
