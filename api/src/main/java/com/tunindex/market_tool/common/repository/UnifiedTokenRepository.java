package com.tunindex.market_tool.common.repository;

import com.tunindex.market_tool.common.entities.UnifiedToken;
import com.tunindex.market_tool.common.entities.enums.TokenType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UnifiedTokenRepository extends JpaRepository<UnifiedToken, Long> {

    // ===== JWT TOKEN METHODS (from TokenRepository) =====
    
    @Query(value = """
      select t from UnifiedToken t inner join User u\s
      on t.user.id = u.id\s
      where u.id = :id and t.tokenType = 'JWT' and (t.expired = false or t.revoked = false)\s
      """)
    List<UnifiedToken> findAllValidTokenByUser(Integer id);

    @Query(value = """
      select COUNT(t) from UnifiedToken t inner join User u\s
      on t.user.id = u.id\s
      where u.id = :id and t.tokenType = 'JWT' and (t.expired = false or t.revoked = false)\s
      """)
    Integer findCountAllValidTokenByUser(Integer id);

    @Query("SELECT t FROM UnifiedToken t WHERE t.token = :token AND t.tokenType = 'JWT'")
    Optional<UnifiedToken> findByToken(String token);

    @Modifying
    @Query("DELETE FROM UnifiedToken t WHERE t.user.id = :userId AND t.tokenType = 'JWT'")
    void deleteAllByUserId(@Param("userId") Integer userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UnifiedToken t WHERE t.user.email = :email AND t.tokenType = 'JWT'")
    void deleteAllByUserEmail(@Param("email") String email);

    @Query("SELECT t FROM UnifiedToken t WHERE t.user.id = :userId AND t.ipHash = :ipHash AND t.tokenType = 'JWT'")
    Optional<UnifiedToken> findValidTokenForUserAndIp(
            @Param("userId") Integer userId,
            @Param("ipHash") String ipHash
    );

    @Query("SELECT t FROM UnifiedToken t WHERE t.token = :token AND t.ipHash = :ipHash AND t.tokenType = 'JWT'")
    Optional<UnifiedToken> findByTokenAndIpHash(String token, String ipHash);

    // ===== PASSWORD RESET TOKEN METHODS (from PasswordResetTokenRepository) =====
    
    @Query("SELECT t FROM UnifiedToken t WHERE t.token = :token AND t.tokenType = 'PASSWORD_RESET'")
    Optional<UnifiedToken> findPasswordResetTokenByToken(String token);

    @Modifying
    @Query("DELETE FROM UnifiedToken t WHERE t.userEmail = :email AND t.tokenType = 'PASSWORD_RESET'")
    void deletePasswordResetTokenByUserEmail(String email);

    @Query("SELECT t FROM UnifiedToken t WHERE t.userEmail = :userEmail AND t.tokenType = 'PASSWORD_RESET' ORDER BY t.creationDate DESC")
    Optional<UnifiedToken> findTopPasswordResetTokenByUserEmailOrderByCreationDateDesc(String userEmail);

    // ===== TWO FACTOR AUTH TOKEN METHODS (from TwoFactorAuthRepository) =====
    
    @Query("SELECT t FROM UnifiedToken t WHERE t.token = :token AND t.userEmail = :userEmail AND t.tokenType = 'TWO_FACTOR'")
    Optional<UnifiedToken> findByTokenAndUserEmail(String token, String userEmail);

    @Modifying
    @Query("DELETE FROM UnifiedToken t WHERE t.userEmail = :email AND t.tokenType = 'TWO_FACTOR'")
    void deleteTwoFactorTokenByUserEmail(String email);

    @Query("SELECT t FROM UnifiedToken t WHERE t.userEmail = :userEmail AND t.tokenType = 'TWO_FACTOR' ORDER BY t.creationDate DESC")
    Optional<UnifiedToken> findTopTwoFactorTokenByUserEmailOrderByCreationDateDesc(String userEmail);

    @Query("SELECT t FROM UnifiedToken t WHERE t.expirationDate < :date AND t.tokenType = 'TWO_FACTOR'")
    List<UnifiedToken> findExpiredTwoFactorTokensByExpirationDateBefore(LocalDateTime date);

    @Query("SELECT t FROM UnifiedToken t WHERE t.verificationToken = :verificationToken AND t.tokenType = 'TWO_FACTOR'")
    Optional<UnifiedToken> findByVerificationToken(String verificationToken);

    @Modifying
    @Query("DELETE FROM UnifiedToken t WHERE t.verificationToken = :verificationToken AND t.tokenType = 'TWO_FACTOR'")
    void deleteByVerificationToken(String verificationToken);

    Long countByUserEmailAndTokenTypeAndExpiredFalseAndRevokedFalse(
            String email, TokenType tokenType);

    Optional<UnifiedToken>findByTokenAndIpHashAndTokenType(String token, String ipHash, TokenType tokenType);
    // ===== GENERIC METHODS FOR ALL TOKEN TYPES =====
    
    @Query("SELECT t FROM UnifiedToken t WHERE t.token = :token AND t.tokenType = :tokenType")
    Optional<UnifiedToken> findByTokenAndType(String token, TokenType tokenType);

    @Query("SELECT t FROM UnifiedToken t WHERE t.userEmail = :userEmail AND t.tokenType = :tokenType")
    List<UnifiedToken> findByUserEmailAndType(String userEmail, TokenType tokenType);

    @Modifying
    @Query("DELETE FROM UnifiedToken t WHERE t.userEmail = :email AND t.tokenType = :tokenType")
    void deleteByUserEmailAndType(String email, TokenType tokenType);

    @Query("SELECT t FROM UnifiedToken t WHERE t.expirationDate < :date AND t.tokenType = :tokenType")
    List<UnifiedToken> findExpiredTokensByTypeAndDate(TokenType tokenType, LocalDateTime date);
} 