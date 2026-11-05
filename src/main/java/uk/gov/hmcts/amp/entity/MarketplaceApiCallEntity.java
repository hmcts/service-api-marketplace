package uk.gov.hmcts.amp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One request a consumer is shown for an API. path is under that API's gateway path. */
@Entity
@Table(name = "marketplace_api_call")
@Getter
@Setter
@NoArgsConstructor
public class MarketplaceApiCallEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String shortCode;

    private int position;

    private String title;

    private String path;
}
