package com.filesync.common.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

public final class VersionVector {
    private final Map<String, Integer> versions;

    public VersionVector() {
        this.versions = new ConcurrentHashMap<>();
    }

    public VersionVector(Map<String, Integer> versions) {
        this.versions = new ConcurrentHashMap<>(versions);
    }

    public Map<String, Integer> getVersions() {
        return Map.copyOf(versions);
    }

    public VersionVector increment(String clientId) {
        Map<String, Integer> newVersions = new ConcurrentHashMap<>(this.versions);
        newVersions.merge(clientId, 1, Integer::sum);
        return new VersionVector(newVersions);
    }

    public int compare(VersionVector other) {
        boolean thisGreater = false;
        boolean otherGreater = false;
        for (Map.Entry<String, Integer> entry : versions.entrySet()) {
            int otherVal = other.versions.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() > otherVal) thisGreater = true;
            else if (entry.getValue() < otherVal) otherGreater = true;
        }
        for (String key : other.versions.keySet()) {
            if (!versions.containsKey(key)) otherGreater = true;
        }
        if (thisGreater && otherGreater) return 2; // conflict
        if (thisGreater) return 1;
        if (otherGreater) return -1;
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VersionVector that = (VersionVector) o;
        return Objects.equals(versions, that.versions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(versions);
    }
}