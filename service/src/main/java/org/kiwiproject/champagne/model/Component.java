package org.kiwiproject.champagne.model;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;

/**
 * Representation of a deployable component in the system.
 */
@Builder
@Getter
public class Component {
    
    Long id;
    Instant createdAt;
    Instant updatedAt;
    
    /**
     * The name of the component
     */
    String componentName;

    /**
     * A tag for the component used to link up with {@link Host} instances for deployments
     */
    String tag;

}
