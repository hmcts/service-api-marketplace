package uk.gov.hmcts.amp.entity;

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

import java.time.LocalDateTime;

@Entity
@Table(name = "application_api")
@Getter
@Setter
@NoArgsConstructor
public class ApplicationApiEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "application_id")
    private ApplicationEntity application;

    private String shortCode;

    private String productId;

    private String subscriptionName;

    private String keyHint;

    private LocalDateTime subscribedAt;
}
