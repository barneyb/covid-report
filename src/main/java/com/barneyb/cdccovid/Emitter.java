package com.barneyb.cdccovid;

import java.io.OutputStream;

public interface Emitter {

    void emit(Store store, OutputStream out);

}
