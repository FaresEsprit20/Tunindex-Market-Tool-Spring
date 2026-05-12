package com.tunindex.market_tool.mailing.repository;

import com.tunindex.market_tool.common.entities.BaseUser;
import com.tunindex.market_tool.mailing.dto.UserEmailProjection;
import com.tunindex.market_tool.mailing.dto.UserPhoneProjection;
import com.tunindex.market_tool.mailing.dto.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserEmailRepository extends JpaRepository<BaseUser, Long> {

    // Fetch all user emails using projection
    @Query("SELECT u.email as email FROM BaseUser u")
    List<UserEmailProjection> findAllUserEmails();

    // Fetch emails of users with a specific role
    @Query("SELECT u.email as email FROM BaseUser u JOIN u.roles r WHERE r.roleName = :role")
    List<UserEmailProjection> findEmailsByRole(@Param("role") UserRole role);

    // Fetch all phone numbers
    @Query("SELECT u.numTel as phoneNumber FROM BaseUser u WHERE u.numTel IS NOT NULL")
    List<UserPhoneProjection> findAllPhoneNumbers();

    // Fetch phone numbers by role
    @Query("SELECT u.numTel as phoneNumber FROM BaseUser u JOIN u.roles r WHERE r.roleName = :role AND u.numTel IS NOT NULL")
    List<UserPhoneProjection> findPhoneNumbersByRole(@Param("role") UserRole role);
}