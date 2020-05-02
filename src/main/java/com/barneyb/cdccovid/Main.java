package com.barneyb.cdccovid;

import lombok.val;

public class Main {

    public static void main(String[] args) {
        val store = new Store();

//        new CDC(java.time.LocalDate.of(2020, 5, 2))
//                .update(store);
//        store.flush();

        new TsvEmitter().emit(store, System.out);
    }

}
