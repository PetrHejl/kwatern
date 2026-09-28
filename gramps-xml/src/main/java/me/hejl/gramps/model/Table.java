package me.hejl.gramps.model;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** All objects of one type, in file order, indexed by handle and by Gramps ID. */
public final class Table<T extends PrimaryObject> {

    private final Map<String, T> byHandle;
    private final Map<String, T> byId;

    Table(List<T> objects) {
        Map<String, T> handles = LinkedHashMap.newLinkedHashMap(objects.size());
        Map<String, T> ids = HashMap.newHashMap(objects.size());
        for (T object : objects) {
            if (handles.putIfAbsent(object.handle(), object) != null) {
                throw new IllegalArgumentException("Duplicate handle " + object.handle());
            }
            if (object.id() != null) {
                ids.putIfAbsent(object.id(), object);
            }
        }
        this.byHandle = Collections.unmodifiableMap(handles);
        this.byId = Collections.unmodifiableMap(ids);
    }

    public Optional<T> get(String handle) {
        return Optional.ofNullable(handle == null ? null : byHandle.get(handle));
    }

    public Optional<T> byId(String id) {
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    public boolean contains(String handle) {
        return handle != null && byHandle.containsKey(handle);
    }

    public Collection<T> all() {
        return byHandle.values();
    }

    public int size() {
        return byHandle.size();
    }
}
