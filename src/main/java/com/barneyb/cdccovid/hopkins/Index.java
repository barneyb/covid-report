package com.barneyb.cdccovid.hopkins;

import lombok.val;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Index<K, T> {

    private final Map<K, Collection<T>> index;

    Index(Stream<T> stream, Function<T, K> extractor) {
        index = new HashMap<>();
        stream.forEach(it -> {
            val k = extractor.apply(it);
            if (index.containsKey(k)) {
                index.get(k).add(it);
            } else {
                index.put(k, new LinkedList<>(List.of(it)));
            }
        });
    }

    Index(Iterable<T> items, Function<T, K> extractor) {
        this(StreamSupport.stream(items.spliterator(), false),
                extractor);
    }

    public Collection<T> get(K key) {
        return index.getOrDefault(key, Collections.emptySet());
    }

    public <P> Collection<T> get(P first, P second) {
        //noinspection unchecked
        return get((K) new Pair<>(first, second));
    }

}
