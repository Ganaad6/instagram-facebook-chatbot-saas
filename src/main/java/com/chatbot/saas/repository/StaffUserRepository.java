package com.chatbot.saas.repository;

import com.chatbot.saas.entity.StaffUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {

    @Query("SELECT u FROM StaffUser u JOIN FETCH u.business WHERE lower(u.email) = lower(:email)")
    Optional<StaffUser> findByEmailIgnoreCase(@Param("email") String email);

    @Query("SELECT u FROM StaffUser u JOIN FETCH u.business WHERE u.id = :id")
    Optional<StaffUser> findWithBusinessById(@Param("id") Long id);

    @Query("SELECT u FROM StaffUser u JOIN FETCH u.business ORDER BY u.createdAt DESC, u.id DESC")
    List<StaffUser> findAllWithBusiness();

    List<StaffUser> findAllByBusinessIdOrderByIdAsc(Long businessId);

    Optional<StaffUser> findByIdAndBusinessId(Long id, Long businessId);

    long countByBusinessIdAndRoleAndActiveTrue(Long businessId, StaffUser.Role role);
}
