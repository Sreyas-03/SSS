package com.sismics.reader.rest.userRegistration.factory;

import com.sismics.reader.rest.userRegistration.products.*;

public class SimpleUserRegistrationFactory extends UserRegistrationFactory {

    @Override
    public UserRegistration createUser() {
        return new SimpleUserRegistration();
    }
}