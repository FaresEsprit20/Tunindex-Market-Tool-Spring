package com.tunindex.market_tool.common.repository;

import com.tunindex.market_tool.common.entities.UnifiedToken;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Repository
public interface JwtTokenRepository extends Repository<UnifiedToken, Integer> {

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
} 