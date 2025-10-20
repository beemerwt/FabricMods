package com.github.beemerwt.mcrpg.data;

public interface DataTicket<T> extends AutoCloseable {

    T data();
    void close();
}
