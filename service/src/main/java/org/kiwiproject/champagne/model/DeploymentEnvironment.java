package org.kiwiproject.champagne.model;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;
import lombok.With;

/**
 * The core model used to track environments in the system (e.g. dev, test, production).
 * 
 * @implNote This model will be soft deletable AND hard deletable. The default "delete" process will soft delete to ensure we maintain
 *           data integrity and don't have to delete ALL linked data (might be needed for historical purposes). We will allow an explicit
 *           hard delete option that will clean up all linked data. This can be used if an entire environment and all related data purge
 *           is required.
 */
@Value
@Builder
public class DeploymentEnvironment {

    Long id;

    @NotBlank
    String name;

    Instant createdAt;
    Instant updatedAt;

    boolean deleted;

    /**
     * The Deployable System that this env is tied to
     */
    @With
    Long deployableSystemId;

}
