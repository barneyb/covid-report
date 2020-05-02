package com.barneyb.cdccovid;

import lombok.val;

public class Main {

    public static void main(String[] args) {
        val store = new Store();

//        new CDC().update(store, LocalDate.of(2020, Month.MAY, 2));
//        store.flush();

        new TsvEmitter().emit(store, System.out);
    }

}
