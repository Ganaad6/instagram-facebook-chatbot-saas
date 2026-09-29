package com.chatbot.saas.service;

import com.chatbot.saas.entity.Business;
import com.chatbot.saas.entity.StaffToken;
import com.chatbot.saas.entity.StaffUser;
import com.chatbot.saas.exception.AccountDisabledException;
import com.chatbot.saas.exception.AccountLockedException;
import com.chatbot.saas.exception.InvalidCredentialsException;
import com.chatbot.saas.exception.ValidationException;
import com.chatbot.saas.repository.BusinessRepository;
import com.chatbot.saas.repository.StaffTokenRepository;
import com.chatbot.saas.repository.StaffUserRepository;
import com.chatbot.saas.security.StaffPrincipal;
import com.chatbot.saas.util.ApiKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Dashboard sign-in: email + password logins for a shop's owner and staff, self-serve signup,
 * and the one-time invite / password-reset links. Staff are onboarded by link rather than
 * email, so the app needs no mail server: the owner (or the admin, for owners) hands the link
 * over themselves.
 */
@Service
@Slf4j
public class StaffAuthService {

    static final int MAX_FAILED_LOGINS = 10;
    static final int LOCK_MINUTES = 15;
    static final int MIN_PASSWORD_LENGTH = 8;
    /** BCrypt only uses the first 72 bytes; longer passwords would be silently truncated. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final StaffUserRepository staffUserRepository;
    private final StaffTokenRepository staffTokenRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;
    /** Compared against when the email is unknown, so response time doesn't reveal which emails exist. */
    private final String dummyPasswordHash;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${auth.invite-ttl-hours:72}")
    private long inviteTtlHours = 72;

    @Value("${auth.reset-ttl-hours:24}")
    private long resetTtlHours = 24;

    @Value("${auth.signup-enabled:true}")
    private boolean signupEnabled = true;

    public StaffAuthService(StaffUserRepository staffUserRepository, StaffTokenRepository staffTokenRepository,
                            BusinessRepository businessRepository, PasswordEncoder passwordEncoder) {
        this.staffUserRepository = staffUserRepository;
        this.staffTokenRepository = staffTokenRepository;
        this.businessRepository = businessRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("not-a-real-password");
    }

    /** A one-time link for a person to set their password. */
    public record IssuedLink(String url, LocalDateTime expiresAt) {
    }

    /** What the invite/reset page shows before the person sets a password. */
    public record LinkInfo(String email, String name, String businessName, StaffToken.Purpose purpose) {
    }

    // ─── Sign-in ─────────────────────────────────────────────────────────────

    /** Checks the password; failed attempts count towards a temporary lock of the account. */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public StaffUser authenticate(String email, String password) {
        Optional<StaffUser> found = staffUserRepository.findByEmailIgnoreCase(email.trim());
        if (found.isEmpty() || found.get().getPasswordHash() == null) {
            passwordEncoder.matches(password, dummyPasswordHash);
            throw new InvalidCredentialsException("Имэйл эсвэл нууц үг буруу байна");
        }
        StaffUser user = found.get();
        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new AccountLockedException("Олон удаа буруу оролдсон тул " + LOCK_MINUTES
                    + " минутын дараа дахин оролдоно уу");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.setFailedLogins(user.getFailedLogins() + 1);
            if (user.getFailedLogins() >= MAX_FAILED_LOGINS) {
                user.setFailedLogins(0);
                user.setLockedUntil(now.plusMinutes(LOCK_MINUTES));
                log.warn("Staff user {} locked after {} failed logins", user.getId(), MAX_FAILED_LOGINS);
            }
            staffUserRepository.save(user);
            throw new InvalidCredentialsException("Имэйл эсвэл нууц үг буруу байна");
        }
        // Only revealed once the password is right
        if (!user.isActive()) {
            throw new AccountDisabledException("Энэ хэрэглэгчийн эрх хаагдсан байна");
        }
        if (user.getBusiness().getStatus() != Business.Status.ACTIVE) {
            throw new AccountDisabledException("Дэлгүүрийн эрх түр хаагдсан байна. Үйлчилгээ үзүүлэгчтэй холбогдоно уу");
        }
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        return staffUserRepository.save(user);
    }

    /** The session's user, if they may still use it; empty signs the session out. */
    @Transactional(readOnly = true)
    public Optional<StaffPrincipal> resolveSession(Long userId, int sessionVersion) {
        return staffUserRepository.findWithBusinessById(userId)
                .filter(StaffUser::isActive)
                .filter(u -> u.getSessionVersion() == sessionVersion)
                .filter(u -> u.getBusiness().getStatus() == Business.Status.ACTIVE)
                .map(u -> new StaffPrincipal(u.getId(), u.getBusiness().getId(), u.getRole()));
    }

    @Transactional(readOnly = true)
    public StaffUser getUser(Long userId) {
        return staffUserRepository.findWithBusinessById(userId).orElseThrow();
    }

    // ─── Signup ──────────────────────────────────────────────────────────────

    /** Self-serve signup: a new shop and its owner. The shop's API key is issued later, on request. */
    @Transactional
    public StaffUser signup(String businessName, String name, String email, String password) {
        if (!signupEnabled) {
            throw new ValidationException("Шинэ бүртгэл одоогоор хаалттай байна");
        }
        String normalizedEmail = email.trim().toLowerCase();
        validatePassword(password);
        if (staffUserRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()
                || businessRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ValidationException("Энэ имэйл бүртгэлтэй байна");
        }
        Business business = businessRepository.save(Business.builder()
                .name(businessName.trim())
                .email(normalizedEmail)
                .status(Business.Status.ACTIVE)
                .build());
        StaffUser owner = staffUserRepository.save(StaffUser.builder()
                .business(business)
                .email(normalizedEmail)
                .name(name.trim())
                .role(StaffUser.Role.OWNER)
                .passwordHash(passwordEncoder.encode(password))
                .lastLoginAt(LocalDateTime.now())
                .build());
        log.info("Business {} signed up with owner {}", business.getId(), owner.getId());
        return owner;
    }

    // ─── Passwords and one-time links ────────────────────────────────────────

    @Transactional
    public StaffUser changePassword(Long userId, String currentPassword, String newPassword) {
        StaffUser user = staffUserRepository.findById(userId).orElseThrow();
        if (user.getPasswordHash() == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ValidationException("Одоогийн нууц үг буруу байна");
        }
        validatePassword(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.invalidateSessions();
        return staffUserRepository.save(user);
    }

    /** Issues a new link for the user, retiring any earlier unused ones. */
    @Transactional
    public IssuedLink issueLink(StaffUser user, StaffToken.Purpose purpose) {
        LocalDateTime now = LocalDateTime.now();
        staffTokenRepository.retireUnused(user.getId(), now);
        String rawToken = ApiKeyGenerator.randomToken();
        LocalDateTime expiresAt = now.plusHours(purpose == StaffToken.Purpose.INVITE ? inviteTtlHours : resetTtlHours);
        staffTokenRepository.save(StaffToken.builder()
                .staffUser(user)
                .tokenHash(ApiKeyGenerator.hash(rawToken))
                .purpose(purpose)
                .expiresAt(expiresAt)
                .build());
        return new IssuedLink(StringUtils.trimTrailingCharacter(baseUrl, '/') + "/invite/" + rawToken, expiresAt);
    }

    @Transactional(readOnly = true)
    public LinkInfo describeLink(String rawToken) {
        StaffToken token = usableToken(rawToken);
        StaffUser user = token.getStaffUser();
        return new LinkInfo(user.getEmail(), user.getName(), user.getBusiness().getName(), token.getPurpose());
    }

    /** Sets the password from an invite or reset link; the link is then used up. */
    @Transactional
    public StaffUser acceptLink(String rawToken, String name, String password) {
        StaffToken token = usableToken(rawToken);
        validatePassword(password);
        StaffUser user = token.getStaffUser();
        if (!user.isActive()) {
            throw new AccountDisabledException("Энэ хэрэглэгчийн эрх хаагдсан байна");
        }
        if (StringUtils.hasText(name)) {
            user.setName(name.trim());
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        user.invalidateSessions();
        token.setUsedAt(LocalDateTime.now());
        staffTokenRepository.save(token);
        return staffUserRepository.save(user);
    }

    private StaffToken usableToken(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new InvalidCredentialsException("Холбоос хүчингүй байна");
        }
        return staffTokenRepository.findByTokenHash(ApiKeyGenerator.hash(rawToken))
                .filter(t -> t.isUsable(LocalDateTime.now()))
                .orElseThrow(() -> new InvalidCredentialsException(
                        "Холбоосын хугацаа дууссан эсвэл ашиглагдсан байна. Шинэ холбоос авна уу"));
    }

    static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Нууц үг дор хаяж " + MIN_PASSWORD_LENGTH + " тэмдэгт байх ёстой");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ValidationException("Нууц үг хэт урт байна");
        }
    }
}
