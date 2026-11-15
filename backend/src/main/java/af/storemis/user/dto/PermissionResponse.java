package af.storemis.user.dto;

import af.storemis.common.security.Permission;

public record PermissionResponse(Permission code, String description, boolean defaultForEntryUser) {}
