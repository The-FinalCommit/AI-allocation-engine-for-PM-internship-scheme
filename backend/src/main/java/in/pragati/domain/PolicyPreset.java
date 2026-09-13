package in.pragati.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "policy_presets", uniqueConstraints = @UniqueConstraint(name = "uq_policy_key", columnNames = "key"))
public class PolicyPreset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, name = "preset_key")
    public String key;
    @Column(nullable = false, length = 120)
    public String name;
    @Column(length = 400)
    public String description;
    @Column(nullable = false, length = 500)
    public String weightsJson;
    @Column(nullable = false)
    public boolean fullCoverage;
    public Integer fairnessFloorPct;
    @Column(nullable = false)
    public boolean isDefault;
    @Column(nullable = false, length = 20)
    public String version;
}
