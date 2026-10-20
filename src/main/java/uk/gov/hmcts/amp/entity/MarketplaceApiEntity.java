package uk.gov.hmcts.amp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "marketplace_api")
@Getter
@Setter
@NoArgsConstructor
public class MarketplaceApiEntity {

    @Id
    private String shortCode;

    private String title;

    private String catalogueName;

    private String productId;

    private String tryItNowProductId;

    private String gatewayPath;

    @Column(name = "role")
    private String role;
}
