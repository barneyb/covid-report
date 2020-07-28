package com.barneyb.covid.util;

import org.springframework.dao.NonTransientDataAccessException;

public class UnknownKeyException extends NonTransientDataAccessException {

    public UnknownKeyException(String msg) {
        super(msg);
    }

    public UnknownKeyException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
