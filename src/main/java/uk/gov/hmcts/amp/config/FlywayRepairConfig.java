package uk.gov.hmcts.amp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Temporary. Remove once AAT has started once with this in place.
 *
 * <p>Preview and AAT share one database, so a preview can record a migration that master later
 * changes. V1.015 and V1.016 were both edited after a preview had applied them, and the recorded
 * checksums no longer match the files:
 *
 * <pre>
 * Migration checksum mismatch for migration version 1.015
 *   -&gt; Applied to database : -335367391
 *   -&gt; Resolved locally    : -472128093
 * </pre>
 *
 * <p>Flyway refuses to start on that, and nobody on the team can reach the database to repair it by
 * hand: the server takes private access only, and the cluster account has no exec or port-forward.
 * So the repair ships with the application instead.
 *
 * <p>repair() rewrites the recorded checksums to match the files, which is a one-off correction,
 * not an ongoing need. Once AAT has started with this, plain migrate() validates cleanly and this
 * class goes - leaving Flyway free to object to an altered migration again, which it should.
 */
@Slf4j
@Configuration
public class FlywayRepairConfig {

    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            log.info("Repairing the Flyway schema history before migrating");
            flyway.repair();
            flyway.migrate();
        };
    }
}
