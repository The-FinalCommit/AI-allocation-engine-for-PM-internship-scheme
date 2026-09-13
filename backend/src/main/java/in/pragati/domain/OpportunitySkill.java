package in.pragati.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "opportunity_skills", indexes = {@Index(name = "idx_oskill_opp", columnList = "opportunityId")})
public class OpportunitySkill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public Long opportunityId;
    @Column(nullable = false, length = 80)
    public String skill;
    @Column(nullable = false)
    public boolean mandatory;
}
