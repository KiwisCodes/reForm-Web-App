package com.reForm.backend.core.exception;


import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ResourceNotAccessedException extends RuntimeException{

    public ResourceNotAccessedException(String message) {
        super(message);
    }


}
