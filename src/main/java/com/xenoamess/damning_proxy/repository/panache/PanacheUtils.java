package com.xenoamess.damning_proxy.repository.panache;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import java.util.function.BiConsumer;

public final class PanacheUtils {

    private PanacheUtils() {
    }

    public static <T extends PanacheEntityBase> T saveOrUpdate(T entity, T existing,
                                                                BiConsumer<T, T> fieldCopier) {
        if (existing == null) {
            entity.persistAndFlush();
            return entity;
        }
        fieldCopier.accept(entity, existing);
        existing.persistAndFlush();
        return entity;
    }
}
