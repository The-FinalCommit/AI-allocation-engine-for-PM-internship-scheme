package in.pragati.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed configuration for PRAGATI runtime settings. */
@ConfigurationProperties(prefix = "pragati")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Ai ai = new Ai();
    private final Optimizer optimizer = new Optimizer();
    private final Web web = new Web();
    private final Uploads uploads = new Uploads();
    private final RateLimit rateLimit = new RateLimit();

    public Jwt getJwt() { return jwt; }
    public Ai getAi() { return ai; }
    public Optimizer getOptimizer() { return optimizer; }
    public Web getWeb() { return web; }
    public Uploads getUploads() { return uploads; }
    public RateLimit getRateLimit() { return rateLimit; }

    public static class Jwt {
        private String secret;
        private long expiryMinutes = 720;
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getExpiryMinutes() { return expiryMinutes; }
        public void setExpiryMinutes(long expiryMinutes) { this.expiryMinutes = expiryMinutes; }
    }

    public static class Ai {
        private String serviceUrl = "http://127.0.0.1:8000";
        private int timeoutSeconds = 30;
        public String getServiceUrl() { return serviceUrl; }
        public void setServiceUrl(String serviceUrl) { this.serviceUrl = serviceUrl; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class Optimizer {
        private int timeoutSeconds = 180;
        private int maxWallSeconds = 120;
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getMaxWallSeconds() { return maxWallSeconds; }
        public void setMaxWallSeconds(int maxWallSeconds) { this.maxWallSeconds = maxWallSeconds; }
    }

    public static class Web {
        private String staticDir = "./frontend-dist";
        private String devOrigins = "";
        public String getStaticDir() { return staticDir; }
        public void setStaticDir(String staticDir) { this.staticDir = staticDir; }
        public String getDevOrigins() { return devOrigins; }
        public void setDevOrigins(String devOrigins) { this.devOrigins = devOrigins; }
    }

    public static class Uploads {
        private String dir = "./data/uploads";
        public String getDir() { return dir; }
        public void setDir(String dir) { this.dir = dir; }
    }

    public static class RateLimit {
        private int loginPerMinute = 8;
        public int getLoginPerMinute() { return loginPerMinute; }
        public void setLoginPerMinute(int loginPerMinute) { this.loginPerMinute = loginPerMinute; }
    }
}
