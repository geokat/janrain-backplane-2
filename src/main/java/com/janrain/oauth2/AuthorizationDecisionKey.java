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
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.commons.supersimpledb.message.MessageField;
import com.janrain.crypto.ChannelUtil;

import java.util.*;

/**
 * @author Johnny Bufu
 */
public class AuthorizationDecisionKey extends ExternalizableCore {

    // - PUBLIC

    /**
     * Empty default constructor for AWS to use
     */
    public AuthorizationDecisionKey() { }

    public AuthorizationDecisionKey(String authCookie) throws SimpleDBException {
        Map<String,String> data = new LinkedHashMap<String, String>();
        String key = ChannelUtil.randomString(AUTHORIZATION_DECISION_KEY_LENGTH);
        data.put(Field.KEY.getFieldName(), key);
        data.put(Field.AUTH_COOKIE.getFieldName(), authCookie);
        data.put(Field.EXPIRES.getFieldName(), DateTimeUtils.ISO8601.get().format(new Date(System.currentTimeMillis() + AUTHORIZATION_DECISION_TIMEOUT_SECONDS * 1000)));
        super.init(key, data);
    }

    @Override
    public String getIdValue() {
        return get(Field.KEY);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(Field.class);
    }

    public static enum Field implements MessageField {

        KEY,
        AUTH_COOKIE,
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

    private static final long serialVersionUID = -3343912147061904056L;

    private static final int AUTHORIZATION_DECISION_KEY_LENGTH = 30;
    private static final long AUTHORIZATION_DECISION_TIMEOUT_SECONDS = 300l;
}
