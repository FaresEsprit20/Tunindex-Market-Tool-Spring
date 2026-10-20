package com.tunindex.market_tool.repository;

import com.tunindex.market_tool.mailing.entity.EmailLog;
import com.tunindex.market_tool.mailing.repository.EmailLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class EmailLogRepositoryTest {

    private final EmailLogRepository emailLogRepository;

    @Autowired
    private TestEntityManager entityManager;

    private EmailLog emailLog;

    EmailLogRepositoryTest(EmailLogRepository emailLogRepository) {
        this.emailLogRepository = emailLogRepository;
    }

    @BeforeEach
    void setUp() {
        emailLog = EmailLog.builder()
                .recipient("test@example.com")
                .subject("Test Subject")
                .emailType("HTML")
                .status("SUCCESS")
                .sentAt(LocalDateTime.now())
                .build();
    }

    @Test
    void save_ShouldPersistEmailLog() {
        // When
        EmailLog savedLog = emailLogRepository.save(emailLog);
        entityManager.flush();

        // Then
        assertThat(savedLog.getId()).isNotNull();
        assertThat(savedLog.getRecipient()).isEqualTo("test@example.com");
        assertThat(savedLog.getSubject()).isEqualTo("Test Subject");
        assertThat(savedLog.getEmailType()).isEqualTo("HTML");
        assertThat(savedLog.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void findById_ShouldReturnEmailLog() {
        // Given
        EmailLog savedLog = emailLogRepository.save(emailLog);
        entityManager.flush();

        // When
        Optional<EmailLog> foundLog = emailLogRepository.findById(savedLog.getId());

        // Then
        assertThat(foundLog).isPresent();
        assertThat(foundLog.get().getRecipient()).isEqualTo(savedLog.getRecipient());
        assertThat(foundLog.get().getSubject()).isEqualTo(savedLog.getSubject());
    }

    @Test
    void findAll_ShouldReturnAllEmailLogs() {
        // Given
        EmailLog log2 = EmailLog.builder()
                .recipient("user2@example.com")
                .subject("Another Subject")
                .emailType("SIMPLE")
                .status("FAILED")
                .sentAt(LocalDateTime.now())
                .build();

        emailLogRepository.save(emailLog);
        emailLogRepository.save(log2);
        entityManager.flush();

        // When
        List<EmailLog> logs = emailLogRepository.findAll();

        // Then
        assertThat(logs).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void deleteById_ShouldRemoveEmailLog() {
        // Given
        EmailLog savedLog = emailLogRepository.save(emailLog);
        entityManager.flush();

        // When
        emailLogRepository.deleteById(savedLog.getId());
        entityManager.flush();

        // Then
        Optional<EmailLog> foundLog = emailLogRepository.findById(savedLog.getId());
        assertThat(foundLog).isNotPresent();
    }


}