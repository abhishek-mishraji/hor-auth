package com.handsoff.hor.auth.common;

import java.util.List;

public record PageResult<T>(List<T> items, long total, int limit, int offset) {
    public PageResult {
        items = List.copyOf(items);
    }
}
