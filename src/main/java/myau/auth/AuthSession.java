package myau.auth;

import org.apache.commons.lang3.StringUtils;

public class AuthSession {
    private String username;
    private String accessToken;
    private String hwidRaw;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getHwidRaw() {
        return hwidRaw;
    }

    public void setHwidRaw(String hwidRaw) {
        this.hwidRaw = hwidRaw;
    }

    public boolean hasToken() {
        return StringUtils.isNotBlank(accessToken);
    }
}
