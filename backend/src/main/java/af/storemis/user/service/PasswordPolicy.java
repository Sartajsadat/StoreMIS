package af.storemis.user.service;

import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;

/** At least 8 characters with at least one letter and one digit (kept simple for shop staff). */
public final class PasswordPolicy {

    private PasswordPolicy() {}

    public static void check(String password) {
        if (password == null || password.length() < 8 || password.length() > 100 || !password.matches(".*\\p{L}.*")
                || !password.matches(".*\\d.*")) {
            throw new BusinessException(ErrorCode.PASSWORD_WEAK);
        }
    }
}
