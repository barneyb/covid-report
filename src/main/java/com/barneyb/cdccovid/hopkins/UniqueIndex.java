package com.barneyb.cdccovid.hopkins;

import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class UniqueIndex<K, V> extends AbstractIndex<K, V, V> {

    UniqueIndex(Stream<V> stream, Function<V, K> extractor) {
        super(stream, extractor, true);
    }

    UniqueIndex(Iterable<V> items, Function<V, K> extractor) {
        this(StreamSupport.stream(items.spliterator(), false), extractor);
    }

    @Override
    protected V getInternal(K key) {
        return index.get(key).iterator().next();
    }
}
