package com.tunindex.market_tool.api.repository;

import com.tunindex.market_tool.api.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional
public interface UserRepository extends JpaRepository<User, Integer>, JpaSpecificationExecutor<User> {

    // JPQL query
    @Query(value = "select u from User u where u.email = :email")
    Optional<User> findUserByEmail(@Param("email") String email);

    @Query("select u from User u join u.roles r where u.numTel = :numTel and r.roleName = 'CUSTOMER'")
    Optional<User> findUserByNumTel(@Param("numTel") String numTel);

    boolean existsByEmail(String email);

    @Query("SELECT u.id FROM User u WHERE u.email = :email")
    Optional<Integer> findUserIdByEmail(@Param("email") String email);

    @Modifying
    @Query("DELETE FROM User u WHERE u.id = :id")
    void deleteByIdCustom(@Param("id") Integer id);

    @Query("select case when count(u) > 0 then true else false end from User u join u.roles r where u.email = :email and r.roleName = 'USER'")
    boolean existsUserByEmail(@Param("email") String email);

    @Query("select case when count(u) > 0 then true else false end from User u join u.roles r where u.numTel = :numTel and r.roleName = 'USER'")
    boolean existsUserByNumTel(@Param("numTel") String numTel);




}
