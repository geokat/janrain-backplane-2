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

package com.janrain.backplane2.server;

import com.janrain.backplane.DateTimeUtils;
import com.janrain.backplane.server.ExternalizableCore;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.commons.supersimpledb.message.MessageField;
import com.janrain.crypto.ChannelUtil;
import com.janrain.oauth2.TokenException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.text.ParseException;
import java.util.*;

/**
 * OAuth2 authorization grant
 *
 * @author Tom Raney, Johnny Bufu
 */
public class Grant extends ExternalizableCore {

    /**
     * Empty default constructor for AWS to use.
     * Don't call directly.
     */
    public Grant() {}
    
    @Override
    public String getIdValue() {
        return get(GrantField.ID);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(GrantField.class);
    }

    public GrantType getType() {
        try {
            return GrantType.valueOf(this.get(GrantField.TYPE));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid GrantType on for GrantField.Type, should have been validated on grant creation: " + this.get(GrantField.TYPE));
        }
    }

    public GrantState getState() {
        try {
            return GrantState.valueOf(this.get(GrantField.STATE));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid GrantState on for GrantField.STATE, should have been validated on grant creation: " + this.get(GrantField.STATE));
        }
    }
    public Date getUpdateTimestamp() {
        try {
            return DateTimeUtils.ISO8601.get().parse(get(GrantField.TIME_UPDATE));
        } catch (ParseException e) {
            throw new IllegalStateException("Invalid value on for GrantField.TIME_UPDATE, should have been validated on grant creation: " + this.get(GrantField.TIME_UPDATE));
        }
    }

    /**
     * @return the grant's expiration date, or null if the grant never expires
     */
    public Date getExpirationDate() {
        String value = this.get(GrantField.TIME_EXPIRE);
        try {
            return StringUtils.isNotEmpty(value) ? DateTimeUtils.ISO8601.get().parse(value) : null;
        } catch (ParseException e) {
            throw new IllegalStateException("Invalid ISO8601 date for GrantField.TIME_EXPIRE, should have been validated on grant creation/update: " + value);
        }
    }

    public Scope getAuthorizedScope() {
        try {
            return new Scope(get(GrantField.AUTHORIZED_SCOPES));
        } catch (TokenException e) {
            throw new IllegalStateException("Invalid value on for GrantField.AUTHORIZED_SCOPES, should have been validated on grant creation: " + this.get(GrantField.AUTHORIZED_SCOPES));
        }
    }

    public boolean isExpired() {
        Date expires = getExpirationDate();
        return expires != null && new Date().getTime() > expires.getTime();
    }

    public static enum GrantField implements MessageField {

        // - PUBLIC

        ID("id"), // acts also as the "code" value for authorization_code grants

        TYPE("type") { // GrantType
            @Override
            public void validate(String value) throws SimpleDBException {
                super.validate(value);
                try {
                    GrantType.valueOf(value);
                } catch (IllegalArgumentException e) {
                    throw new SimpleDBException("Invalid grant type: " + value);
                }
            }
        },

        ISSUED_BY_USER_ID("issued_by_user"),

        ISSUED_TO_CLIENT_ID("issued_to_client"),

        AUTHORIZED_SCOPES("authorized_scopes") {
            @Override
            public void validate(String value) throws SimpleDBException {
                super.validate(value);
                try {
                    new Scope(value);
                } catch (TokenException e) {
                    throw new SimpleDBException("Invalid grant scope: " + value);
                }
            }
        },

        STATE("state") { // <GrantState enum value>
            @Override
            public void validate(String value) throws SimpleDBException {
                super.validate(value);
                try {
                    GrantState.valueOf(value);
                } catch (IllegalArgumentException e) {
                    throw new SimpleDBException("Invalid grant value for state: " + value);
                }
            }
        },
        
        TIME_UPDATE("time_update") { // <ISO8601 timestamp>
            @Override
            public void validate(String value) throws SimpleDBException {
                super.validate(value);
                try {
                    DateTimeUtils.ISO8601.get().parse(value);
                } catch (ParseException e) {
                    throw new SimpleDBException("Invalid grant value for time_update: " + value);
                }
            }
        },

        TIME_EXPIRE("time_expire", false) { // <ISO8601 timestamp> when the grant's current state expires
            @Override
            public void validate(String value) throws SimpleDBException {
                super.validate(value);
                try {
                    if (StringUtils.isNotEmpty(value)) {
                        DateTimeUtils.ISO8601.get().parse(value);
                    }
                } catch (ParseException e) {
                    throw new SimpleDBException("Invalid grant value for time_expire: " + value);
                }
            }
        };

        @Override
        public String getFieldName() {
            return fieldName;
        }

        @Override
        public boolean isRequired() {
            return required;
        }

        @Override
        public void validate(String value) throws SimpleDBException {
            if (isRequired()) validateNotBlank(getFieldName(), value);
        }

        // - PRIVATE

        private String fieldName;
        private boolean required = true;

        private GrantField(String fieldName) {
            this(fieldName, true);
        }

        private GrantField(String fieldName, boolean required) {
            this.fieldName = fieldName;
            this.required = required;
        }
    }
    
    public static final class Builder {
        
        public Builder(GrantType type, GrantState state, String issuedById, String issuedToClientId, String scopes) {
            data.put(GrantField.TYPE.getFieldName(), type.toString());
            if (GrantType.AUTHORIZATION_CODE == type) {
                expireSeconds = CODE_EXPIRATION_SECONDS_DEFAULT;
            }
            data.put(GrantField.STATE.getFieldName(), state.toString());
            data.put(GrantField.ISSUED_BY_USER_ID.getFieldName(), issuedById);
            data.put(GrantField.ISSUED_TO_CLIENT_ID.getFieldName(), issuedToClientId);
            data.put(GrantField.AUTHORIZED_SCOPES.getFieldName(), scopes);
        }

        public Builder(Grant other, GrantState state) {
            data.putAll(other);
            data.put(GrantField.STATE.getFieldName(), state.toString());
        }

        public Builder expires(int seconds) {
            expireSeconds = seconds;
            return this;
        }

        public Builder scope(Scope updatedScope) {
            data.put(GrantField.AUTHORIZED_SCOPES.getFieldName(), updatedScope.toString());
            return this;
        }

        public Grant buildGrant() throws SimpleDBException {
            String id = data.get(GrantField.ID.getFieldName());
            if ( id == null) {
                id = ChannelUtil.randomString(CODE_ID_LENGTH);
                data.put(GrantField.ID.getFieldName(), id);
            }

            // grant is issued/updated now
            Date now = new Date();
            data.put(GrantField.TIME_UPDATE.getFieldName(), DateTimeUtils.ISO8601.get().format(now));

            // ignore expireSeconds fields overrides data entry
            if (expireSeconds != null) {
                data.put(GrantField.TIME_EXPIRE.getFieldName(), DateTimeUtils.ISO8601.get().format(new Date(now.getTime() + expireSeconds.longValue() * 1000 )));
            } else {
                data.remove(GrantField.TIME_EXPIRE.getFieldName());
            }
            
            return new Grant(id, data);
        }
        
        private Map<String,String> data = new HashMap<String, String>();
        
        private Integer expireSeconds = null;

    }

    // - PRIVATE

    private static final long serialVersionUID = -968555655007824298L;
    
    private static final Logger logger = Logger.getLogger(Grant.class);

    private static final int CODE_ID_LENGTH = 20;

    private static final int CODE_EXPIRATION_SECONDS_DEFAULT = 600; // 10 minutes

    private Grant(String id, Map<String,String> data) throws SimpleDBException {
        super.init(id, data);
        logger.info("Grant created: " + get(GrantField.ISSUED_BY_USER_ID) + " authorized client " + get(GrantField.ISSUED_TO_CLIENT_ID) + " for scopes: " + get(GrantField.AUTHORIZED_SCOPES));
    }
}
