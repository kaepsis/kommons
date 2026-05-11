package dev.kaepsis.kommons.database.interfaces;

@FunctionalInterface
public interface SupplierWithException<T> {
    T get() throws Exception;
}