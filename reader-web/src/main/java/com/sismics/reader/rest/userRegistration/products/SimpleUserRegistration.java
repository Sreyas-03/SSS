package com.sismics.reader.rest.userRegistration.products;

import com.sismics.reader.core.constant.Constants;
import com.sismics.reader.core.dao.jpa.*;
import com.sismics.reader.core.event.UserCreatedEvent;
import com.sismics.reader.core.model.context.AppContext;
import com.sismics.reader.core.model.jpa.Category;
import com.sismics.reader.core.model.jpa.User;
import com.sismics.rest.exception.ServerException;
import com.sismics.rest.util.ValidationUtil;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.ws.rs.core.Response;

import java.util.Date;

public class SimpleUserRegistration implements UserRegistration {

    private String username;
    private String password;
    private String localeId;
    private String email;

    @Override
    public Response register(String username, String password, String localeId, String email) throws JSONException {
        this.username = username;
        this.password = password;
        this.localeId = localeId;
        this.email = email;

        return(UserCreation());
    }

    private Response UserCreation() throws JSONException {

            // Validate the input data
        username = ValidationUtil.validateLength(username, "username", 3, 50);
        ValidationUtil.validateAlphanumeric(username, "username");
        password = ValidationUtil.validateLength(password, "password", 8, 50);
        email = ValidationUtil.validateLength(email, "email", 3, 50);
        ValidationUtil.validateEmail(email, "email");

        // Create the user
        User user = new User();
        user.setRoleId(Constants.DEFAULT_USER_ROLE);
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(email);
        user.setDisplayTitleWeb(false);
        user.setDisplayTitleMobile(true);
        user.setDisplayUnreadWeb(true);
        user.setDisplayUnreadMobile(true);
        user.setCreateDate(new Date());

        user.setLocaleId(localeId);

        // Create the user
        UserDao userDao = new UserDao();
        String userId;
        try {
            userId = userDao.create(user);
        } catch (Exception e) {
            System.out.println("---------------------------------------------");
            System.out.println("---------------------------------------------");
            System.out.println(e.getMessage());
            System.out.println("---------------------------------------------");
            System.out.println("---------------------------------------------");
            if ("AlreadyExistingUsername".equals(e.getMessage())) {
                throw new ServerException("AlreadyExistingUsername", "Username already used", e);
            }
            else if ("AlreadyExistingEmail".equals(e.getMessage())) {
                throw new ServerException("AlreadyExistingEmail", "Email already used", e);
            }
            else {
                throw new ServerException("UnknownError", "Unknown Server Error", e);
            }
        }

        // Create the root category for this user
        Category category = new Category();
        category.setUserId(userId);
        category.setOrder(0);

        CategoryDao categoryDao = new CategoryDao();
        categoryDao.create(category);

        // Raise a user creation event
        UserCreatedEvent userCreatedEvent = new UserCreatedEvent();
        userCreatedEvent.setUser(user);
        AppContext.getInstance().getMailEventBus().post(userCreatedEvent);

        // Always return OK
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        return Response.ok().entity(response).build();
    }
}
