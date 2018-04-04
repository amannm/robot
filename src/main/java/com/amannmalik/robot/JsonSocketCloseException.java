package com.amannmalik.robot;

public class JsonSocketCloseException extends Exception {

    private final int closeCode;

    public JsonSocketCloseException(int closeCode, String closeReasonPhrase) {
        super(closeReasonPhrase);
        this.closeCode = closeCode;
    }

    public int getCloseCode() {
        return closeCode;
    }
}
