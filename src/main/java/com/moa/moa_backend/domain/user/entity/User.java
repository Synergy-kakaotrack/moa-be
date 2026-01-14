package com.moa.moa_backend.domain.user.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    protected User(){}

    public Long getId() {
        return id;
    }
}
