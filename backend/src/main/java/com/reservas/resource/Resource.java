package com.reservas.resource;

import jakarta.persistence.*;

@Entity
@Table(name = "resources")
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceType type;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean requiresApproval;

    protected Resource() {
    }

    public Resource(String name, String description, ResourceType type, int capacity) {
        this(name, description, type, capacity, false);
    }

    public Resource(String name, String description, ResourceType type, int capacity, boolean requiresApproval) {
        update(name, description, type, capacity, requiresApproval);
    }

    public void update(String name, String description, ResourceType type, int capacity, boolean requiresApproval) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.capacity = capacity;
        this.requiresApproval = requiresApproval;
    }

    public void deactivate() { this.active = false; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ResourceType getType() { return type; }
    public int getCapacity() { return capacity; }
    public boolean isActive() { return active; }
    public boolean isRequiresApproval() { return requiresApproval; }
}
