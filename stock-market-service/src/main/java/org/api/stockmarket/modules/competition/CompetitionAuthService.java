package org.api.stockmarket.modules.competition;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class CompetitionAuthService {
    private final CompetitionTeamRepository teamRepository;
    private final SecureRandom random = new SecureRandom();

    @Value("${competition.admin-key:change-me-now}")
    private String adminKey;

    public void requireAdmin(String suppliedKey) {
        if (suppliedKey == null || !MessageDigest.isEqual(
                hash(adminKey).getBytes(StandardCharsets.UTF_8),
                hash(suppliedKey).getBytes(StandardCharsets.UTF_8))) {
            throw new CompetitionException(HttpStatus.UNAUTHORIZED, "Invalid administrator credentials");
        }
    }

    public String newAccessCode() {
        byte[] bytes = new byte[6];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).toUpperCase();
    }

    public String newSessionToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public CompetitionDtos.LoginResponse login(CompetitionDtos.LoginRequest request) {
        CompetitionTeam team = teamRepository.findByCompetitionIdAndNameIgnoreCase(request.competitionId(), request.teamName())
                .orElseThrow(() -> new CompetitionException(HttpStatus.UNAUTHORIZED, "Invalid team or access code"));
        if (!team.isActive() || !MessageDigest.isEqual(
                team.getAccessCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(request.accessCode()).getBytes(StandardCharsets.UTF_8))) {
            throw new CompetitionException(HttpStatus.UNAUTHORIZED, "Invalid team or access code");
        }
        String token = newSessionToken();
        team.setSessionTokenHash(hash(token));
        teamRepository.save(team);
        return new CompetitionDtos.LoginResponse(token, team.getCompetition().getId(), team.getId(), team.getName());
    }

    public CompetitionTeam requireTeam(String authorization, Long competitionId) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new CompetitionException(HttpStatus.UNAUTHORIZED, "A team bearer token is required");
        }
        CompetitionTeam team = teamRepository.findBySessionTokenHash(hash(authorization.substring(7)))
                .orElseThrow(() -> new CompetitionException(HttpStatus.UNAUTHORIZED, "Invalid or expired team token"));
        if (!team.isActive() || !team.getCompetition().getId().equals(competitionId)) {
            throw new CompetitionException(HttpStatus.FORBIDDEN, "This token cannot access that competition");
        }
        return team;
    }
}
