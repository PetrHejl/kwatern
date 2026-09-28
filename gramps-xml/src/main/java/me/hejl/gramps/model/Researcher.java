package me.hejl.gramps.model;

public record Researcher(
        String name,
        String address,
        String locality,
        String city,
        String state,
        String country,
        String postal,
        String phone,
        String email) {}
