package org.awesoma.monitoring.application.model;

import java.util.List;
import java.util.function.Function;

public record Page<T>(List<T> content, int page, int size, long totalElements) {
    public static <T> Page<T> empty() {
        return new Page<>(List.of(),0,1,0);
    }
    public Page {
        content = List.copyOf(content);
    }
    public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
        return new Page<>(content.stream().<R>map(mapper).toList(), page, size, totalElements);
    }
    public List<T> getContent() {
        return content;
    }
    public long getTotalElements() {
        return totalElements;
    }
}
