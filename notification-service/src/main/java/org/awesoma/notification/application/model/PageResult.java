package org.awesoma.notification.application.model;

import java.util.List;
public record PageResult<T>(List<T> items, long total, boolean hasNext) {
    public PageResult { items = List.copyOf(items); }
}
