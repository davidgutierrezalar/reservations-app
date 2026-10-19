package com.reservas.resource;

import com.reservas.auth.CurrentUser;
import com.reservas.resource.ResourceDtos.ResourceRequest;
import com.reservas.resource.ResourceDtos.ResourceResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
                                         @RequestParam(required = false) @Min(1) Integer minCapacity,
                                         @PageableDefault(sort = "name") Pageable pageable) {
        return service.search(name, type, active, minCapacity, pageable);
    }

    @GetMapping("/{id}")
    public ResourceResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceResponse create(@Valid @RequestBody ResourceRequest req, JwtAuthenticationToken auth) {
        return service.create(req, CurrentUser.from(auth).id());
    }

    @PutMapping("/{id}")
    public ResourceResponse update(@PathVariable Long id, @Valid @RequestBody ResourceRequest req,
                                   JwtAuthenticationToken auth) {
        return service.update(id, req, CurrentUser.from(auth).id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id, JwtAuthenticationToken auth) {
        service.deactivate(id, CurrentUser.from(auth).id());
    }
}
