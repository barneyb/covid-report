package com.barneyb.covid;

import java.io.OutputStream;

public interface Emitter<M> {

    void emit(OutputStream out, M model);

}
