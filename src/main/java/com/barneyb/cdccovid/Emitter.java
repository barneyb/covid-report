package com.barneyb.cdccovid;

import com.barneyb.cdccovid.model.Jurisdiction;

import java.io.OutputStream;
import java.util.List;

public interface Emitter {

    void emit(List<Jurisdiction> database, OutputStream out);

}
