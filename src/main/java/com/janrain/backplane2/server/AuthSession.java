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

import java.util.*;

/**
 * @author Johnny Bufu
 */
public class AuthSession extends ExternalizableCore {

    // - PUBLIC

    /**
     * Empty default constructor for AWS to use
     */
    public AuthSession() { }
    
    public AuthSession(String authUser, String cookie) throws SimpleDBException {
        Map<String,String> data = new LinkedHashMap<String, String>();
        data.put(Field.AUTH_USER.getFieldName(), authUser);
        data.put(Field.COOKIE.getFieldName(), cookie);
        data.put(Field.EXPIRES.getFieldName(), DateTimeUtils.ISO8601.get().format(new Date(System.currentTimeMillis() + AUTH_SESSION_TIMEOUT_SECONDS * 1000)));
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

    public static enum Field implements MessageField {

        // - PUBLIC

        COOKIE,
        AUTH_USER,
        EXPIRES;

        @Override
        public String getFieldName() {
            return name();
        }

        @Override
        public boolean isRequired() {
            return true;
        }

        @Override
        public void validate(String value) throws SimpleDBException {
            if (isRequired()) validateNotBlank(name(), value);
        }
    }

    // - PRIVATE

    private static final long serialVersionUID = -8416685362228294237L;

    private static final long AUTH_SESSION_TIMEOUT_SECONDS = 3600l;
}
