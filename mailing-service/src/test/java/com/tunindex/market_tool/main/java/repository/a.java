package com.tunindex.market_tool.main.java.repository;

import com.tunindex.market_tool.mailing.entity.EmailLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class EmailLogRepositoryTest {

    @Autowired
    private UserEmailRepository emailLogRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void saveEmailLog_ShouldPersist() {
        // Given
        EmailLog log = EmailLog.builder()
                .recipient("test@example.com")
                .subject("Test Subject")
                .emailType("HTML")
                .status("SUCCESS")
                .sentAt(LocalDateTime.now())
                .build();

        // When
        EmailLog savedLog = emailLogRepository.save(log);
        entityManager.flush();

        // Then
        assertNotNull(savedLog.getId());
        assertEquals("test@example.com", savedLog.getRecipient());
        assertEquals("SUCCESS", savedLog.getStatus());
    }

    @Test
    void findById_ShouldReturnLog() {
        // Given
        EmailLog log = EmailLog.builder()
                .recipient("find@example.com")
                .subject("Find Test")
                .emailType("SIMPLE")
                .status("SUCCESS")
                .sentAt(LocalDateTime.now())
                .build();
        EmailLog savedLog = emailLogRepository.save(log);

        // When
        EmailLog foundLog = emailLogRepository.findById(savedLog.getId()).orElse(null);

        // Then
        assertNotNull(foundLog);
        assertEquals("find@example.com", foundLog.getRecipient());
    }

    @Test
    void findAll_ShouldReturnAllLogs() {
        // Given
        EmailLog log1 = EmailLog.builder().recipient("user1@example.com").subject("Test1").emailType("HTML").status("SUCCESS").sentAt(LocalDateTime.now()).build();
        EmailLog log2 = EmailLog.builder().recipient("user2@example.com").subject("Test2").emailType("HTML").status("FAILED").sentAt(LocalDateTime.now()).build();
        emailLogRepository.save(log1);
        emailLogRepository.save(log2);

        // When
        List<EmailLog> logs = emailLogRepository.findAll();

        // Then
        assertTrue(logs.size() >= 2);
    }
}