package uk.gov.hmcts.amp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.amp.entity.MarketplaceApiEntity;
import uk.gov.hmcts.amp.repository.MarketplaceApiRepository;

import static org.assertj.core.api.Assertions.assertThat;

/** The entity against the real migration, so a column the two disagree on fails here. */
@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
class MarketplaceApiRepositoryIntegrationTest {

    @Autowired
    private MarketplaceApiRepository apis;

    @Test
    void every_column_should_read_back_through_the_entity() {
        MarketplaceApiEntity slc = apis.findById("slc").orElseThrow();

        assertThat(slc.getTitle()).isEqualTo("Scheduling and Listing Court Schedule");
        assertThat(slc.getCatalogueName()).isEqualTo("api-cp-crime-schedulingandlisting-courtschedule");
        assertThat(slc.getProductId()).isEqualTo("try-it-now-cp-crime-schedulingandlisting");
        assertThat(slc.getTryItNowProductId()).isEqualTo("try-it-now-cp-crime-schedulingandlisting");
        assertThat(slc.getGatewayPath()).isEqualTo("amp/try/slc");
        assertThat(slc.getRole()).isEqualTo("app.read");
    }
}
