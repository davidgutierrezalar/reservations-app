package com.reservas.resource;

import com.reservas.resource.ResourceDtos.ResourceRequest;
import com.reservas.resource.ResourceDtos.ResourceResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceService service;

    public ResourceController(ResourceService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ResourceResponse> search(@RequestParam(required = false) String name,
                                         @RequestParam(required = false) ResourceType type,
                                         @RequestParam(required = false) Boolean active,
                                         @PageableDefault(sort = "name") Pageable pageable) {
        return service.search(name, type, active, pageable);
    }

    @GetMapping("/{id}")
    public ResourceResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceResponse create(@Valid @RequestBody ResourceRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public ResourceResponse update(@PathVariable Long id, @Valid @RequestBody ResourceRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }
}
