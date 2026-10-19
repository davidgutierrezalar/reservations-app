package com.reservas.resource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ResourceDtos {

    private ResourceDtos() {
    }

    public record ResourceRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @NotNull ResourceType type,
            @Min(1) @Max(1000) int capacity,
            Boolean requiresApproval) {

        // Boxed so older clients that omit the field still work: missing means bookings confirm instantly
        public ResourceRequest {
            requiresApproval = Boolean.TRUE.equals(requiresApproval);
        }
    }

    public record ResourceResponse(Long id, String name, String description, ResourceType type, int capacity,
                                   boolean active, boolean requiresApproval) {
        static ResourceResponse from(Resource r) {
            return new ResourceResponse(r.getId(), r.getName(), r.getDescription(), r.getType(), r.getCapacity(),
                    r.isActive(), r.isRequiresApproval());
        }
    }
}
