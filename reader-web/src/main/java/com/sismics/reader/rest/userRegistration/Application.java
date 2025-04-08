package com.sismics.reader.rest.userRegistration;

import org.codehaus.jettison.json.JSONException;

import com.sismics.reader.rest.userRegistration.factory.UserRegistrationFactory;
import com.sismics.reader.rest.userRegistration.products.UserRegistration;

import javax.ws.rs.core.Response;

public class Application {

    private UserRegistration userRegistration;

    public Application(UserRegistrationFactory urf)
    {
        userRegistration = urf.createUser();
    }

    public Response createUser(String username, String password, String localeId, String email) throws JSONException
    {
        return userRegistration.register(username, password, localeId, email);
    }

}
