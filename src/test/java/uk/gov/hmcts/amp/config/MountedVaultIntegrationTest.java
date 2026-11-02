package uk.gov.hmcts.amp.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import uk.gov.hmcts.amp.integration.TestContainersInitialise;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chart mounts each vault at /mnt/secrets/&lt;vault&gt;/ and the name differs per environment, so
 * application.yaml imports them by wildcard. Naming them instead reads only the ones listed, and
 * says nothing about the rest: the secret never arrives and the request needing it fails far away.
 */
@SpringBootTest(properties = "spring.config.import=optional:configtree:src/test/resources/vaults/*/")
@ContextConfiguration(initializers = TestContainersInitialise.class)
class MountedVaultIntegrationTest {

    @Autowired
    private EntraProperties entraProperties;

    // Two halves. This one proves the application asks for the wildcard, which is the thing that
    // regressed; the one below proves the wildcard does what we think on this Spring Boot version.
    @Test
    void the_application_should_import_whatever_is_mounted_rather_than_a_list() throws Exception {
        final String yaml = Files.readString(Path.of("src/main/resources/application.yaml"));

        assertThat(yaml).contains("optional:configtree:/mnt/secrets/*/");
    }

    @Test
    void vaults_this_release_has_never_heard_of_should_still_be_read() {
        assertThat(entraProperties.onboardingClientId()).isEqualTo("the-id-from-a-mounted-vault");
        assertThat(entraProperties.onboardingClientSecret()).isEqualTo("the-secret-from-a-mounted-vault");
        assertThat(entraProperties.credentialConfigured()).isTrue();
    }
}
