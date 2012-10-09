/*
 * Copyright 2012 Janrain, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.janrain.oauth2;

import com.janrain.backplane.DateTimeUtils;
import com.janrain.backplane.server.ExternalizableCore;
import com.janrain.backplane2.server.BackplaneServerException;
import com.janrain.backplane2.server.InvalidRequestException;
import com.janrain.backplane2.server.config.Client;
import com.janrain.backplane2.server.dao.ClientDAO;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.commons.supersimpledb.message.MessageField;
import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * @author Johnny Bufu
 */
public class AuthorizationRequest extends ExternalizableCore {

    // - PUBLIC

    /**
     * Empty default constructor for AWS to use
     */
    public AuthorizationRequest() { }

    public AuthorizationRequest(String cookie, Map parameterMap) throws SimpleDBException {
        Map<String,String> data = new LinkedHashMap<String, String>();
        for(Field f: EnumSet.allOf(Field.class)) {
            Object value = parameterMap.get(f.getFieldName().toLowerCase());
            if ( value != null && ((String[]) value).length > 0 && StringUtils.isNotEmpty(((String[])value)[0])) {
                data.put(f.getFieldName(), ((String[])value)[0]);
            }
        }
        data.put(Field.COOKIE.getFieldName(), cookie);
        data.put(Field.EXPIRES.getFieldName(), DateTimeUtils.ISO8601.get().format(new Date(System.currentTimeMillis() + AUTH_REQUEST_TIMEOUT_SECONDS * 1000)));
        super.init(cookie, data);
    }

    @Override
    public String getIdValue() {
        return get(Field.COOKIE);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(Field.class);
    }
    
    public String getRedirectUri(ClientDAO clientDAO) throws AuthorizationException {
        String client_id = get(Field.CLIENT_ID);

        Client client = null;
        try {
            client = clientDAO.get(client_id);
        } catch (BackplaneServerException e) {
            // ignore
        }
        if(client == null) {
            throw new AuthorizationException(OAuth2.OAUTH2_AUTHZ_DIRECT_ERROR, "invalid client_id: " + client_id , this);
        }
        String requestRedirectUri = get(Field.REDIRECT_URI);

        if (StringUtils.isNotEmpty(requestRedirectUri)) {
            try {
                OAuth2.validateRedirectUri(requestRedirectUri, client.get(Client.ClientField.REDIRECT_URI));
            } catch (ValidationException ve) {
                throw new AuthorizationException(OAuth2.OAUTH2_AUTHZ_DIRECT_ERROR, "invalid redirect_uri: " + ve.getMessage() , this);
            }
            return requestRedirectUri;
        } else {
            return client.get(Client.ClientField.REDIRECT_URI);
        }
    }

    public static enum Field implements MessageField {

        // - PUBLIC

        COOKIE,
        EXPIRES,
        CLIENT_ID,
        RESPONSE_TYPE {
            @Override
            public void validate(String value) throws SimpleDBException {
                super.validate(value);
                if ( ! OAuth2.OAUTH2_TOKEN_RESPONSE_TYPE_CODE.equals(value)) {
                    throw new IllegalArgumentException("Unsupported OAuth2 response_type: " + value);
                }
            }
        },
        REDIRECT_URI(false) {
            @Override
            public void validate(String value) throws SimpleDBException {
                super.validate(value);
                try {
                    OAuth2.validateRedirectUri(value);
                } catch (ValidationException e) {
                    throw new InvalidRequestException(e.getMessage());
                }
            }
        },
        SCOPE(false),
        STATE(false);

        @Override
        public String getFieldName() {
            return name();
        }

        @Override
        public boolean isRequired() {
            return required;
        }

        @Override
        public void validate(String value) throws SimpleDBException {
            if (isRequired()) validateNotBlank(getFieldName().toLowerCase(), value);
        }

        // - PRIVATE

        private final boolean required;

        private Field() {
            this(true);
        }

        private Field(boolean required) {
            this.required = required;
        }
    }

    // - PRIVATE

    private static final long serialVersionUID = 5105927572907870325L;

    private static final long AUTH_REQUEST_TIMEOUT_SECONDS = 1200l;
}
