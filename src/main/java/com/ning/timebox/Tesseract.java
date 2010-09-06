package com.ning.timebox;

public class Tesseract<T> {
    
    private T result = null;
    
    protected synchronized void setResult(T result) {
        this.result = result;
    }
    
    synchronized T getResult() {
        return result;
    }

}
