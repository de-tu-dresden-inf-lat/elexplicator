package de.tu_dresden.lat.api;

public class ErrorResponse {
    private String errorMsg;

    public ErrorResponse(String errorMsg){
        this.errorMsg = errorMsg;
    }

    public String getErrorMsg(){
        return this.errorMsg;
    }
}
