package af.storemis.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** IP address and user agent of the current request (nginx forwards X-Forwarded-For). */
public final class ClientInfo {

    private ClientInfo() {}

    public static String ip() {
        HttpServletRequest req = current();
        return req == null ? null : truncate(req.getRemoteAddr(), 45);
    }

    public static String userAgent() {
        HttpServletRequest req = current();
        return req == null ? null : truncate(req.getHeader("User-Agent"), 255);
    }

    private static HttpServletRequest current() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs ? attrs.getRequest() : null;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
