package com.barneyb.covid.util;

import com.barneyb.covid.hopkins.Pair;
import lombok.val;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

abstract class AbstractIndex<K, V, M> {

    protected final MultiValuedMap<K, V> index;
    private final Consumer<V> adder;

    AbstractIndex(Stream<V> stream, Function<V, K> extractor, boolean unique) {
        index = new ArrayListValuedHashMap<>();
        adder = it -> {
            val k = extractor.apply(it);
            if (unique && index.containsKey(k)) {
                throw new DuplicateKeyException("Duplicate key '" + k + "' for: " + it);
            }
            index.get(k).add(it);
        };
        stream.forEach(adder);
    }

    public Stream<V> values() {
        return index.values().stream();
    }

    public void add(V item) {
        adder.accept(item);
    }

    public final M get(K key) {
        if (!index.containsKey(key)) {
            throw new UnknownKeyException("Unknown key: '" + key + "'");
        }
        return getInternal(key);
    }

    public final Optional<M> getOptional(K key) {
        return index.containsKey(key)
                ? Optional.of(getInternal(key))
                : Optional.empty();
    }

    protected abstract M getInternal(K key);

    public final <T> M get(T first, T second) {
        //noinspection unchecked
        return get((K) new Pair<>(first, second));
    }

    public boolean containsKey(K key) {
        return index.containsKey(key);
    }

    public Set<K> keySet() {
        return index.keySet();
    }

}
