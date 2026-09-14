package org.awesoma.monitoring.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.awesoma.monitoring.domain.enums.HttpMethod;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "monitors")
@Getter
@Setter
@NoArgsConstructor
public class Monitor extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String name;

    @NotBlank
    @Size(max = 2048)
    @Pattern(regexp = "^https?://.+", message = "must be an http or https URL")
    @Column(nullable = false, length = 2048)
    private String url;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", nullable = false, length = 10)
    private HttpMethod httpMethod = HttpMethod.GET;

    @Min(10)
    @Max(86400)
    @Column(name = "interval_sec", nullable = false)
    private int intervalSec;

    @Min(100)
    @Max(60000)
    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Min(100)
    @Max(599)
    @Column(name = "expected_status", nullable = false)
    private int expectedStatus = 200;

    @Column(nullable = false)
    private boolean active = true;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "current_state", nullable = false, length = 20)
    private MonitorState currentState = MonitorState.UNKNOWN;

    /**
     * Plain many-to-many: the join table carries nothing but the two keys.
     *
     * <p>Batched so that rendering a page of monitors costs one extra query for all their
     * tags instead of one per monitor, while the page itself is still cut by the database.
     * The size matches the largest page the API hands out.
     */
    @BatchSize(size = 50)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "monitor_tags",
            joinColumns = @JoinColumn(name = "monitor_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();
}
