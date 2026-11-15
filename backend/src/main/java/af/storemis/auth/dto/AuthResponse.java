package af.storemis.auth.dto;

import af.storemis.user.dto.CurrentUserResponse;

/** Access token for the Authorization header; the refresh token is set separately as an httpOnly cookie. */
public record AuthResponse(String accessToken, long expiresIn, CurrentUserResponse user) {}
