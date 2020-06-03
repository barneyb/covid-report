package com.barneyb.cdccovid.hopkins;

import lombok.val;
import org.springframework.dao.DuplicateKeyException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class UniqueIndex<K, T> {

    private final Map<K, T> index;

    UniqueIndex(Stream<T> stream, Function<T, K> extractor) {
        index = new HashMap<>();
        stream.forEach(it -> {
            val k = extractor.apply(it);
            val prev = index.put(k, it);
            if (prev != null) {
                throw new DuplicateKeyException("Duplicate key '" + k + "' for: " + it);
            }
        });
    }

    UniqueIndex(Iterable<T> items, Function<T, K> extractor) {
        this(StreamSupport.stream(items.spliterator(), false),
                extractor);
    }

    T get(K key) {
        return index.get(key);
    }

}
