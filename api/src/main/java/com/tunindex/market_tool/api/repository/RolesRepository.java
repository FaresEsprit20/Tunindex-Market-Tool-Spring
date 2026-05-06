package com.tunindex.market_tool.api.repository;

import com.tunindex.market_tool.api.entities.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public interface RolesRepository extends JpaRepository<Roles, Integer> {

    @Modifying
    @Query("DELETE FROM Roles r WHERE r.id IN (SELECT r2.id FROM User u JOIN u.roles r2 WHERE u.id = :userId)")
    void deleteAllRolesForUser(@Param("userId") Integer userId);

}