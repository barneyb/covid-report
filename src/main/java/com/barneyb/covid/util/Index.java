package com.barneyb.covid.util;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Index<K, V> extends AbstractIndex<K, V, Collection<V>> {

    public Index(Function<V, K> extractor) {
        this(Collections.emptySet(), extractor);
    }

    public Index(Stream<V> stream, Function<V, K> extractor) {
        super(stream, extractor, false);
    }

    public Index(Iterable<V> items, Function<V, K> extractor) {
        this(StreamSupport.stream(items.spliterator(), false), extractor);
    }

    @Override
    public Collection<V> getInternal(K key) {
        return index.get(key);
    }

}
