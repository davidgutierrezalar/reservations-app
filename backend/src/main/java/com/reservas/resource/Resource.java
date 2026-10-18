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

    protected Resource() {
    }

    public Resource(String name, String description, ResourceType type, int capacity) {
        update(name, description, type, capacity);
    }

    public void update(String name, String description, ResourceType type, int capacity) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.capacity = capacity;
    }

    public void deactivate() { this.active = false; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ResourceType getType() { return type; }
    public int getCapacity() { return capacity; }
    public boolean isActive() { return active; }
}
