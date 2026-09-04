package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import uk.gov.hmcts.cp.domain.RequestType;
import uk.gov.hmcts.cp.domain.PublishRequest;
import uk.gov.hmcts.cp.domain.PublishResponse;
import uk.gov.hmcts.cp.entity.PublishRequestEntity;
import uk.gov.hmcts.cp.entity.UserEntity;
import uk.gov.hmcts.cp.mappers.PublishMapper;
import uk.gov.hmcts.cp.repository.PublishRepository;
import uk.gov.hmcts.cp.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublishService {

    private final PublishRepository publishRepository;
    private final UserRepository userRepository;
    private final PublishMapper publishMapper;
    private final ClockService clockService;
    private final ReferenceGenerator referenceGenerator;

    public List<PublishResponse> getAll() {
        return publishRepository.findAll().stream()
            .flatMap(entity -> userRepository.findByEmail(entity.getUserEmail()).stream()
                .map(userEntity -> publishMapper.fromEntity(userEntity, entity)))
            .toList();
    }

    public void delete(final String reference) {
        PublishRequestEntity entity = publishRepository.findById(reference)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publish request not found."));
        publishRepository.delete(entity);
        log.info("Publish request {} deleted", reference);
    }

    public PublishResponse submit(final int userId, final PublishRequest request) {
        log.info("Publish request submitted by userId:{}", userId);
        UserEntity userEntity = userRepository.findById(userId).orElseThrow();
        PublishRequestEntity entity = publishMapper.toEntity(
                userId,
                userEntity.getOrganisation().getName(),
                userEntity.getEmail(),
                userEntity.getFirstName(),
                userEntity.getLastName(),
                request
            )
            .toBuilder()
            .reference(referenceGenerator.generate(RequestType.PUBLISH))
            // Stamped from ClockService rather than the entity so tests can fix the time.
            .submittedAt(LocalDateTime.ofInstant(clockService.now(), ZoneOffset.UTC))
            .build();
        PublishRequestEntity saved = publishRepository.save(entity);
        return publishMapper.fromEntity(userEntity, saved);
    }
}
