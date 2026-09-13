package in.pragati.domain;

import jakarta.persistence.*;
import in.pragati.domain.enums.SkillSource;

@Entity
@Table(name = "candidate_skills", indexes = {@Index(name = "idx_skill_candidate", columnList = "candidateId")})
public class CandidateSkill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public Long candidateId;
    @Column(nullable = false, length = 80)
    public String rawLabel;
    @Column(nullable = false, length = 80)
    public String canonical;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public SkillSource source = SkillSource.PROFILE;
    @Column(nullable = false)
    public boolean validated = true;
}
