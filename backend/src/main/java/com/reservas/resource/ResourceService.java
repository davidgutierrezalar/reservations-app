package com.reservas.resource;

import com.reservas.common.ApiException;
import com.reservas.resource.ResourceDtos.ResourceRequest;
import com.reservas.resource.ResourceDtos.ResourceResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@Transactional
public class ResourceService {

    private final ResourceRepository resources;

    public ResourceService(ResourceRepository resources) {
        this.resources = resources;
    }

    @Transactional(readOnly = true)
    public Page<ResourceResponse> search(String name, ResourceType type, Boolean active, Pageable pageable) {
        Specification<Resource> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.trim().toLowerCase() + "%"));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return resources.findAll(spec, pageable).map(ResourceResponse::from);
    }

    @Transactional(readOnly = true)
    public ResourceResponse get(Long id) {
        return ResourceResponse.from(find(id));
    }

    public ResourceResponse create(ResourceRequest req) {
        return ResourceResponse.from(resources.save(new Resource(req.name(), req.description(), req.type(), req.capacity())));
    }

    public ResourceResponse update(Long id, ResourceRequest req) {
        Resource resource = find(id);
        resource.update(req.name(), req.description(), req.type(), req.capacity());
        return ResourceResponse.from(resource);
    }

    // Soft delete: past reservations keep pointing at the resource
    public void deactivate(Long id) {
        find(id).deactivate();
    }

    private Resource find(Long id) {
        return resources.findById(id).orElseThrow(() -> ApiException.notFound("Resource not found"));
    }
}
