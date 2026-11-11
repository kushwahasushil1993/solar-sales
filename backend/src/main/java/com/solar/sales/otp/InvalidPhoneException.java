package com.solar.sales.otp;

public class InvalidPhoneException extends RuntimeException {
    public InvalidPhoneException() {
        super("Enter a valid mobile number, e.g. 98765 43210 or +91 98765 43210");
    }
}
