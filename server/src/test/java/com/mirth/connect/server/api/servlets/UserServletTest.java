/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.api.servlets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.client.core.api.servlets.UserServletInterface;
import com.mirth.connect.model.User;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.controllers.EventController;

public class UserServletTest extends ServletTestBase {

    private static Method updateUserMethod;

    @BeforeClass
    public static void setup() throws Exception {
        ServletTestBase.setup();
        EventController eventController = mock(EventController.class);
        when(controllerFactory.createEventController()).thenReturn(eventController);
        updateUserMethod = UserServletInterface.class.getMethod("updateUser", Integer.class, User.class);
    }

    @Before
    public void resetMocks() throws Exception {
        reset(userController);
        when(authorizationController.isUserAuthorized(anyInt(), any(), any(), any(), anyBoolean())).thenReturn(false);
    }

    private UserServlet newUserServletForUser(int currentUserId) {
        HttpSession userSession = mock(HttpSession.class);
        when(userSession.getAttribute("user")).thenReturn(Integer.toString(currentUserId));
        when(userSession.getAttribute("authorized")).thenReturn(Boolean.TRUE);

        HttpServletRequest userRequest = mock(HttpServletRequest.class);
        when(userRequest.getSession()).thenReturn(userSession);

        return new UserServlet(userRequest, mock(SecurityContext.class), controllerFactory);
    }

    private void assertBadRequestInvocation(Object proxy, Method method, Object[] args) {
        Throwable caught = null;
        try {
            ih.invoke(proxy, method, args);
        } catch (Throwable t) {
            caught = t;
        }
        if (caught == null) {
            fail("Expected BAD_REQUEST MirthApiException");
        }
        if (caught instanceof InvocationTargetException) {
            caught = caught.getCause();
        }
        if (!(caught instanceof MirthApiException)) {
            fail("Expected MirthApiException, got " + caught);
        }
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ((MirthApiException) caught).getResponse().getStatus());
    }

    @Test
    public void testUpdateUserRejectsMismatchedBodyIdForSelf() throws Throwable {
        User target = new User();
        target.setId(12);
        target.setUsername("renamed-victim");

        assertBadRequestInvocation(newUserServletForUser(6), updateUserMethod, new Object[] { 6, target });
        verify(userController, never()).updateUser(any(User.class));
    }

    @Test
    public void testUpdateUserRejectsMismatchedBodyIdForAuthorizedAdmin() throws Throwable {
        when(authorizationController.isUserAuthorized(anyInt(), any(), any(), any(), anyBoolean())).thenReturn(true);

        User target = new User();
        target.setId(12);
        target.setUsername("renamed-victim");

        assertBadRequestInvocation(newUserServletForUser(1), updateUserMethod, new Object[] { 6, target });
        verify(userController, never()).updateUser(any(User.class));
    }

    @Test
    public void testUpdateUserSetsPathIdWhenBodyIdIsNull() throws Throwable {
        User self = new User();
        self.setUsername("self-user");

        ih.invoke(newUserServletForUser(6), updateUserMethod, new Object[] { 6, self });

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userController).updateUser(captor.capture());
        assertEquals(Integer.valueOf(6), captor.getValue().getId());
    }

    @Test
    public void testUpdateUserAllowsMatchingBodyIdForSelf() throws Throwable {
        User self = new User();
        self.setId(6);
        self.setUsername("self-user");

        ih.invoke(newUserServletForUser(6), updateUserMethod, new Object[] { 6, self });

        verify(userController).updateUser(self);
    }

    @Test
    public void testUpdateUserForbiddenForOtherUserId() throws Throwable {
        User other = new User();
        other.setId(12);
        other.setUsername("victim");

        assertForbiddenInvocation(newUserServletForUser(6), updateUserMethod, new Object[] { 12, other });
        verify(userController, never()).updateUser(any(User.class));
    }
}
