package com.example.mukonongomap.fooddistribution.domain.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pure domain model. No Android dependencies.
 */
public final class Beneficiary {

    private final String id;
    private final String name;
    private final int householdSize;
    private final List<String> dietaryRestrictions;

    public Beneficiary(String id, String name, int householdSize, List<String> dietaryRestrictions) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.householdSize = householdSize;
        this.dietaryRestrictions = dietaryRestrictions == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(dietaryRestrictions);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getHouseholdSize() { return householdSize; }
    public List<String> getDietaryRestrictions() { return dietaryRestrictions; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Beneficiary)) return false;
        Beneficiary that = (Beneficiary) o;
        return householdSize == that.householdSize
                && id.equals(that.id)
                && name.equals(that.name)
                && dietaryRestrictions.equals(that.dietaryRestrictions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, householdSize, dietaryRestrictions);
    }
}