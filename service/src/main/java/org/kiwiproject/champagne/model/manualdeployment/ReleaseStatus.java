package org.kiwiproject.champagne.model.manualdeployment;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

/**
 * Defines the status of manual tasks for a release in a specific deployment environment.
 */
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReleaseStatus {
    
    Long id;
    Instant createdAt;
    Instant updatedAt;

    @NotNull
    Long releaseId;

    @NotNull
    Long environmentId;

    @NotNull
    DeploymentTaskStatus status;
}
