/*
 * Copyright (c) 2015  Mike Nimer & 11:58 Labs
 */

package com.familydam.core.api.userManager;

import com.familydam.core.FamilyDAM;
import com.familydam.core.FamilyDAMConstants;
import com.familydam.core.api.fileManager.RootDirTest;
import com.familydam.core.services.AuthenticatedHelper;
import junit.framework.Assert;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.QueryBuilder;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.jcr.session.SessionImpl;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Value;
import java.util.Iterator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by mnimer on 9/19/14.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = FamilyDAM.class)
@WebAppConfiguration
public class UserManagerTests
{

    Logger logger = LoggerFactory.getLogger(RootDirTest.class);

    @Autowired
    public WebApplicationContext wac;

    @Autowired
    private Repository repository;

    @Autowired private AuthenticatedHelper authenticatedHelper;

    private MockMvc mockMvc;

    //@Value("${server.port}")
    public String port;
    public String rootUrl;


    @Before
    public void setupMock() throws Exception
    {
        port = "9000";//wac.getEnvironment().getProperty("server.port");
        rootUrl = "http://localhost:" + port;

        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }


    @Test
    public void loginUsers() throws Exception
    {
        MvcResult result = this.mockMvc
                .perform(post(rootUrl + "/api/users/login").param("username", "admin").param("password", "admin"))
                .andExpect(status().isOk())
                .andReturn();

        String jcrToken = result.getResponse().getHeader("token");
        Assert.assertTrue(jcrToken.length() > 0);
        String resultJson = result.getResponse().getContentAsString();
        logger.debug(resultJson);
    }


    @Test
    public void loginUserWithToken() throws Exception
    {
        MvcResult result = this.mockMvc
                .perform(post(rootUrl + "/api/users/login").param("username", "admin").param("password", "admin"))
                .andExpect(status().isOk())
                .andReturn();

        String jcrToken = result.getResponse().getHeader(FamilyDAMConstants.XAUTHTOKEN);

        MvcResult result2 = this.mockMvc
                .perform(get(rootUrl + "/api/users/admin").header(FamilyDAMConstants.XAUTHTOKEN, jcrToken))
                .andExpect(status().isOk())
                .andReturn();

        String resultJson = result2.getResponse().getContentAsString();
        logger.debug(resultJson);
    }


    @Test
    public void listUsers() throws Exception
    {

        MvcResult userList = this.mockMvc
                .perform(get(rootUrl + "/api/search").param("type", "rep:User").param(" ", "jcr:name").param("limit", "0"))
                .andExpect(status().isOk())
                .andReturn();

        String resultJson = userList.getResponse().getContentAsString();
        logger.debug(resultJson);
    }


    public Iterator<Authorizable>  listUsersRaw() throws Exception
    {

        Session session = null;
        try {
            session = repository.login(new SimpleCredentials(FamilyDAM.adminUserId, FamilyDAM.adminPassword.toCharArray()));

            UserManager userManager = ((SessionImpl) session).getUserManager();

            final Value anonymousValue = session.getValueFactory().createValue("anonymous");
            //final Value adminValue = session.getValueFactory().createValue("admin");

            Iterator<Authorizable> users = userManager.findAuthorizables(new org.apache.jackrabbit.api.security.user.Query()
            {
                public <T> void build(QueryBuilder<T> builder)
                {
                    builder.setCondition(builder.
                            not(builder.eq("@rep:principalName", anonymousValue)));

                    builder.setSortOrder("@rep:principalName", QueryBuilder.Direction.ASCENDING);
                    builder.setSelector(User.class);
                }
            });
            //Iterator<Authorizable> users = userManager.findAuthorizables("/", "rep:principalName", UserManager.SEARCH_TYPE_USER);

            Assert.assertTrue(users.hasNext());


            while (users.hasNext()) {

                Authorizable user = users.next();
                System.out.println("***********************");
                System.out.println(user.getPrincipal());
                System.out.println(user.getID());
                System.out.println(user.getPath());
                System.out.println("***********************");

                Iterator<String> propertyNames = user.getPropertyNames();
                while(propertyNames.hasNext()) {
                    String key = propertyNames.next();
                    System.out.println(key +"=" +user.getProperty(key));
                }

            }

            return users;

        }
        finally {
            session.logout();
        }
    }


    @Ignore
    @Test
    public void createLoginAndRemoveUserRaw() throws Exception
    {
        Session session = null;
        Session session2 = null;
        try {
            session = authenticatedHelper.getAdminSession();

            UserManager userManager = ((SessionImpl) session).getUserManager();

            // Create User
            String username = "test-" +System.currentTimeMillis();
            User _user = userManager.createUser(username, "test");
            _user.setProperty("foo", session.getValueFactory().createValue("bar"));
            session.save();


            Iterator<Authorizable> users = listUsersRaw();
            org.junit.Assert.assertEquals(2, users);


            // test login
            session2 = repository.login(new SimpleCredentials(username, "test".toCharArray()));
            org.junit.Assert.assertNotNull(session2);
            session2.logout();


            // remove the user
            ((SessionImpl) session).getUserManager().getAuthorizable(username).remove();
            session.save();

        }catch(Exception ex){
            ex.printStackTrace();
            Assert.fail(ex.getMessage());
        }
        finally {
            if( session != null) session.logout();
            if( session2 != null) session2.logout();
        }
    }


    @Ignore
    @Test
    public void createUsers() throws Exception
    {
        /*
        MvcResult loginResult = this.mockMvc
                .perform(post(rootUrl + "/api/users/login").param("userid", "admin").param("password", "admin"))
                .andExpect(status().isOk())
                .andReturn();

        String authHeader = loginResult.getResponse().getHeader("Authorization");
        Assert.assertNotNull(authHeader);
        */

        MvcResult createUserResult = this.mockMvc
                .perform(post(rootUrl + "/api/users")
                        .param("_userid", "admin").param("_password", "admin")
                        .param("userid", "mnimer").param("password", "foobar"))
                .andExpect(status().isNoContent())
                .andReturn();
        //String resultJson1 = createUserResult.getResponse().getContentAsString();

        MvcResult userList = this.mockMvc
                .perform(get(rootUrl + "/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", Matchers.hasSize(1)))
                .andReturn();


        String resultJson2 = userList.getResponse().getContentAsString();
        logger.debug(resultJson2);
    }
}
