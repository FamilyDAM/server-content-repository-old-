/*
 * This file is part of FamilyDAM Project.
 *
 *     The FamilyDAM Project is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     The FamilyDAM Project is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the FamilyDAM Project.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.familydam.core.services;

import com.familydam.core.FamilyDAMConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jackrabbit.api.security.authentication.token.TokenCredentials;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.security.SecurityProviderImpl;
import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.jackrabbit.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.jcr.Credentials;
import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.security.sasl.AuthenticationException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Created by mnimer on 9/23/14.
 */
@Service
public class AuthenticatedHelper
{
    Log log = LogFactory.getLog(this.getClass());

    @Autowired private Repository repository;

    private SecurityProvider securityProvider;


    /**
     * Return the content root
     * @param session
     * @return
     * @throws RepositoryException
     */
    public Node getContentRoot(Session session) throws RepositoryException
    {
        Node root = session.getRootNode();
        return root.getNode(FamilyDAMConstants.CONTENT_ROOT);
    }


    /**
     * Get a relative node under the root content folder 
     * @param session
     * @param relativePath_
     * @return
     * @throws RepositoryException
     */
    public Node getContentRoot(Session session, String relativePath_) throws RepositoryException
    {
        Node root = session.getRootNode();
        Node contentRoot = root.getNode(FamilyDAMConstants.CONTENT_ROOT);
        
        relativePath_ = relativePath_.replace("/~/", "").replace("/"+FamilyDAMConstants.CONTENT_ROOT, "");
        if (relativePath_ != null && relativePath_.length() > 1) {
            if (relativePath_.startsWith("/")) {
                relativePath_ = relativePath_.substring(1);
            }
            contentRoot = contentRoot.getNode(relativePath_);
        }
        return contentRoot;
    }


    public SecurityProvider getSecurityProvider()
    {
        if (securityProvider == null) {
            securityProvider = new SecurityProviderImpl(ConfigurationParameters.EMPTY);
        }
        return securityProvider;
    }



    public Session getSession(HttpServletRequest request, HttpServletResponse response) throws RepositoryException, AuthenticationException
    {
        Credentials credentials = null;

        Optional _tokenHeader = Optional.ofNullable(request.getHeader(FamilyDAMConstants.XAUTHTOKEN));
        Optional _tokenUrlParam = Optional.ofNullable(request.getParameter(FamilyDAMConstants.XAUTHTOKENPARAM));

        String token = null;

        if (_tokenHeader.isPresent() ) {
            log.debug("Trying to authenticate user by X-Auth-Token method. Token: " + token);
            token = (String)_tokenHeader.get();
        }else if( _tokenUrlParam.isPresent() ){
            log.debug("Trying to authenticate user by token url parameter. Token: " + token);
            token = (String)_tokenUrlParam.get();
        }


        if (token != null) {
            credentials = new TokenCredentials(token);
        }


        if (credentials == null) {
            throw new AuthenticationException();
        }

        Session session = repository.login(credentials, null);
        return session;
    }


    public Session getSession(Credentials credentials) throws RepositoryException
    {
        Session session = repository.login(credentials, null);
        return session;
    }


    public Session getRepositorySession(HttpServletRequest request, HttpServletResponse response) throws RepositoryException, AuthenticationException
    {
        Credentials credentials = null;

        String authorization = request.getHeader("Authorization");
        Cookie[] cookies = request.getCookies();

        Map<String, String> cookieMap = new HashMap<>();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                cookieMap.put(cookie.getName(), cookie.getValue());
            }
        }

        // Try to pull the auth data a few different ways
        if (authorization != null && authorization.startsWith("Basic ")) {
            String[] basic =
                    Base64.decode(authorization.substring("Basic ".length())).split(":");
            credentials = new SimpleCredentials(basic[0], basic[1].toCharArray());

            Cookie _cookie = new Cookie("x-auth-token", authorization);
            _cookie.setDomain(".localhost");
            _cookie.setHttpOnly(true);
            response.addCookie(_cookie);

            request.getSession().setAttribute("x-auth-token", authorization);
        } else if (request.getParameter("token") != null) {
            String[] basic = Base64.decode(request.getParameter("token").substring("Basic ".length())).split(":");
            credentials = new SimpleCredentials(basic[0], basic[1].toCharArray());
        } else if (cookieMap.containsKey("x-auth-token")) {
            String[] basic = Base64.decode(cookieMap.get("authorization").substring("Basic ".length())).split(":");
            credentials = new SimpleCredentials(basic[0], basic[1].toCharArray());
        } else {
            String username = request.getParameter("j_username");
            String password = request.getParameter("j_password");
            if (username != null && password != null) {
                credentials = new SimpleCredentials(username, password.toCharArray());
            }
        }

        if (credentials == null) {
            throw new AuthenticationException();
        }

        Session session = repository.login(credentials, null);
        return session;
    }


    public UserManager getUserManager(Session session) throws RepositoryException
    {
        ConfigurationParameters defaultConfig = ConfigurationParameters.EMPTY;
        String defaultUserPath = defaultConfig.getConfigValue(UserConstants.PARAM_USER_PATH, UserConstants.DEFAULT_USER_PATH);
        String defaultGroupPath = defaultConfig.getConfigValue(UserConstants.PARAM_GROUP_PATH, UserConstants.DEFAULT_GROUP_PATH);

        Map customOptions = new HashMap<String, Object>();
        //customOptions.put(UserConstants.PARAM_GROUP_PATH, "/home/groups");
        customOptions.put(UserConstants.PARAM_USER_PATH, "/home/users");


        UserManager userMgr = null;//new UserManagerImpl(session.getRootNode(), NamePathMapper.DEFAULT, ConfigurationParameters.EMPTY);
        return userMgr;
    }
}
