package com.tengyei.common.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Getter;

import java.util.List;
import java.util.function.Function;

@Getter
public class PageResult<T> {

    private final List<T> records;
    private final long total;
    private final long current;
    private final long size;

    private PageResult(List<T> records, long total, long current, long size) {
        this.records = records;
        this.total = total;
        this.current = current;
        this.size = size;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        return new PageResult<>(records, total, current, size);
    }

    /** Map a MyBatis-Plus IPage of entities into a PageResult of VOs. */
    public static <E, V> PageResult<V> from(IPage<E> page, Function<E, V> mapper) {
        List<V> vos = page.getRecords().stream().map(mapper).toList();
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
