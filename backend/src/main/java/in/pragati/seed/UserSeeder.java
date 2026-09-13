package in.pragati.seed;

import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import in.pragati.domain.Provider;
import in.pragati.domain.User;
import in.pragati.domain.enums.Role;
import in.pragati.repo.ProviderRepository;
import in.pragati.repo.UserRepository;

/**
 * Creates the fixed demonstration accounts. All accounts are synthetic.
 * The demo candidate account is linked to the first seeded candidate
 * profile by ScenarioSeeder.
 */
@Configuration
public class UserSeeder {

    public static final String ADMIN_EMAIL = "admin@pragati.gov.in";
    public static final String PROVIDER_EMAIL = "provider@pragati.gov.in";
    public static final String CANDIDATE_EMAIL = "candidate@pragati.gov.in";
    public static final String ADMIN_PASSWORD = "Admin@123";
    public static final String PROVIDER_PASSWORD = "Provider@123";
    public static final String CANDIDATE_PASSWORD = "Candidate@123";

    @Bean
    @Order(10)
    public ApplicationRunner seedUsers(UserRepository users, ProviderRepository providers, PasswordEncoder encoder) {
        return args -> {
            if (users.count() > 0) return;
            users.save(user(ADMIN_EMAIL, ADMIN_PASSWORD, "Asha Iyer", Role.ADMIN, encoder));
            users.save(user(PROVIDER_EMAIL, PROVIDER_PASSWORD, "Dr. R. K. Malhotra", Role.PROVIDER, encoder));
            users.save(user(CANDIDATE_EMAIL, CANDIDATE_PASSWORD, "Aarav Sharma", Role.CANDIDATE, encoder));
            User providerUser = users.findByEmail(PROVIDER_EMAIL).orElseThrow();
            Provider org = new Provider();
            org.userId = providerUser.id;
            org.orgName = "National Technology Institute";
            org.orgType = "Education & Training Institution";
            org.state = "Delhi";
            org.about = "National Technology Institute runs internship programmes in software, "
                    + "data and public policy across India. This is a synthetic demonstration "
                    + "organisation.";
            providers.save(org);
        };
    }

    private User user(String email, String password, String name, Role role, PasswordEncoder encoder) {
        User u = new User();
        u.email = email;
        u.passwordHash = encoder.encode(password);
        u.name = name;
        u.role = role;
        return u;
    }
}
