package com.sismics.reader.rest.userRegistration.products;

import org.codehaus.jettison.json.JSONException;

import javax.ws.rs.core.Response;

public interface UserRegistration {
    public Response register(String username, String password, String localeId, String email) throws JSONException;
}