package com.chatbot.saas.service;

import com.chatbot.saas.dto.response.AdminUserResponse;
import com.chatbot.saas.dto.response.StaffUserResponse;
import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.StaffToken;
import com.chatbot.saas.entity.StaffUser;
import com.chatbot.saas.exception.ValidationException;
import com.chatbot.saas.repository.StaffUserRepository;
import com.chatbot.saas.service.StaffAuthService.IssuedLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** A shop's owner managing who can sign in to its dashboard. Callers check owner access. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffService {

    private final StaffUserRepository staffUserRepository;
    private final StaffAuthService staffAuthService;
    private final BusinessService businessService;

    public record Invitation(StaffUserResponse user, IssuedLink link) {
    }

    @Transactional(readOnly = true)
    public List<StaffUserResponse> list(Long businessId) {
        return staffUserRepository.findAllByBusinessIdOrderByIdAsc(businessId).stream()
                .map(StaffUserResponse::from)
                .toList();
    }

    /** Every login of every shop, newest first - for the platform admin. */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> listAll() {
        LocalDateTime now = LocalDateTime.now();
        return staffUserRepository.findAllWithBusiness().stream()
                .map(user -> AdminUserResponse.from(user, now))
                .toList();
    }

    /**
     * Invites someone to the shop. Inviting an email that was already invited here and never
     * accepted just issues a fresh link.
     */
    @Transactional
    public Invitation invite(Long businessId, String email, String name, StaffUser.Role role) {
        String normalizedEmail = email.trim().toLowerCase();
        Optional<StaffUser> existing = staffUserRepository.findByEmailIgnoreCase(normalizedEmail);
        StaffUser user;
        if (existing.isPresent()) {
            user = existing.get();
            if (!user.getBusiness().getId().equals(businessId) || user.getPasswordHash() != null) {
                throw new ValidationException("Энэ имэйл бүртгэлтэй байна");
            }
            user.setRole(role);
            user.setName(name);
            user.setActive(true);
        } else {
            Business business = businessService.findBusinessById(businessId);
            user = StaffUser.builder().business(business).email(normalizedEmail).name(name).role(role).build();
        }
        user = staffUserRepository.save(user);
        log.info("Business {} invited staff user {} as {}", businessId, user.getId(), role);
        return new Invitation(StaffUserResponse.from(user), staffAuthService.issueLink(user, StaffToken.Purpose.INVITE));
    }

    /** A link for a staff member who forgot their password (or never used their invite). */
    @Transactional
    public IssuedLink passwordLink(Long businessId, Long userId) {
        StaffUser user = find(businessId, userId);
        StaffToken.Purpose purpose = user.getPasswordHash() == null ? StaffToken.Purpose.INVITE : StaffToken.Purpose.RESET;
        return staffAuthService.issueLink(user, purpose);
    }

    @Transactional
    public StaffUserResponse update(Long businessId, Long userId, StaffUser.Role role, Boolean active, Long actingUserId) {
        StaffUser user = find(businessId, userId);
        boolean demotingOwner = role != null && role != StaffUser.Role.OWNER && user.getRole() == StaffUser.Role.OWNER;
        boolean deactivating = Boolean.FALSE.equals(active) && user.isActive();
        if ((demotingOwner || deactivating) && user.getId().equals(actingUserId)) {
            throw new ValidationException("Өөрийн эрхийг хасах боломжгүй");
        }
        if ((demotingOwner || (deactivating && user.getRole() == StaffUser.Role.OWNER)) && isLastOwner(user)) {
            throw new ValidationException("Дэлгүүрт дор хаяж нэг эзэмшигч байх ёстой");
        }
        if (role != null) {
            user.setRole(role);
        }
        if (active != null && active != user.isActive()) {
            user.setActive(active);
            user.invalidateSessions();
        }
        return StaffUserResponse.from(staffUserRepository.save(user));
    }

    @Transactional
    public void remove(Long businessId, Long userId, Long actingUserId) {
        StaffUser user = find(businessId, userId);
        if (user.getId().equals(actingUserId)) {
            throw new ValidationException("Өөрийгөө устгах боломжгүй");
        }
        if (user.getRole() == StaffUser.Role.OWNER && user.isActive() && isLastOwner(user)) {
            throw new ValidationException("Дэлгүүрт дор хаяж нэг эзэмшигч байх ёстой");
        }
        staffUserRepository.delete(user);
    }

    /**
     * The admin onboarding an owner for a shop (e.g. one registered through the API): creates
     * the owner if needed and returns a link to set their password.
     */
    @Transactional
    public Invitation inviteOwnerAsAdmin(Long businessId, String email, String name) {
        businessService.findBusinessById(businessId);
        Optional<StaffUser> existing = staffUserRepository.findByEmailIgnoreCase(email.trim());
        if (existing.isPresent() && existing.get().getBusiness().getId().equals(businessId)) {
            StaffUser user = existing.get();
            user.setRole(StaffUser.Role.OWNER);
            user.setActive(true);
            staffUserRepository.save(user);
            StaffToken.Purpose purpose = user.getPasswordHash() == null ? StaffToken.Purpose.INVITE : StaffToken.Purpose.RESET;
            return new Invitation(StaffUserResponse.from(user), staffAuthService.issueLink(user, purpose));
        }
        return invite(businessId, email, name, StaffUser.Role.OWNER);
    }

    private StaffUser find(Long businessId, Long userId) {
        return staffUserRepository.findByIdAndBusinessId(userId, businessId)
                .orElseThrow(() -> new ValidationException("Хэрэглэгч олдсонгүй"));
    }

    private boolean isLastOwner(StaffUser user) {
        return staffUserRepository.countByBusinessIdAndRoleAndActiveTrue(user.getBusiness().getId(), StaffUser.Role.OWNER) <= 1;
    }
}
