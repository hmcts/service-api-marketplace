package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.ApimProduct;
import uk.gov.hmcts.cp.services.ApimSubscriptionKeyService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApimProductControllerTest {

    @Mock
    private ApimSubscriptionKeyService subscriptionKeys;

    @InjectMocks
    private ApimProductController controller;

    @Test
    void listing_products_should_return_what_the_service_found() {
        ApimProduct product = new ApimProduct("hearing-results", "Hearing Results", null, "published");
        when(subscriptionKeys.listProducts()).thenReturn(List.of(product));

        assertThat(controller.listProducts()).containsExactly(product);
    }
}
