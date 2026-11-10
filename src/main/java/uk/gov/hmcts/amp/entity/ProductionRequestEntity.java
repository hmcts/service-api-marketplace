package uk.gov.hmcts.amp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "production_request")
@Getter
@Setter
@NoArgsConstructor
public class ProductionRequestEntity {

    public static final String SUBMITTED = "SUBMITTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ManyToOne
    @JoinColumn(name = "application_id")
    private ApplicationEntity application;

    private String applicationName;

    private String reference;

    private String status;

    private LocalDateTime submittedAt;

    private String organisation;

    private LocalDate goLiveDate;

    private String callVolume;

    private String dataRequest;

    private String proposedOutputs;

    private String organisationType;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> dataRecipients;

    private String dataRecipientsExplanation;

    private String dataCombined;

    private String dataCombinedDescription;
}
