package com.valaryrpg.core.command;

import java.util.List;

/**
 * Paginazione della lista comandi (/command list).
 */
final class CommandListPages {

    private CommandListPages() {
    }

    static int pageCount(int size, int pageSize) {
        if (size <= 0) {
            return 1;
        }
        return (size + pageSize - 1) / pageSize;
    }

    static int clampPage(int page, int pageCount) {
        if (page < 1) {
            return 1;
        }
        return Math.min(page, pageCount);
    }

    static <T> List<T> slice(List<T> items, int page, int pageSize) {
        int count = pageCount(items.size(), pageSize);
        int safePage = clampPage(page, count);
        int from = (safePage - 1) * pageSize;
        if (from >= items.size()) {
            return List.of();
        }
        int to = Math.min(from + pageSize, items.size());
        return items.subList(from, to);
    }
}
