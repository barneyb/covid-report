package com.barneyb.covid;

import java.io.OutputStream;

public interface Emitter {

    void emit(OutputStream out, Store store);

}
