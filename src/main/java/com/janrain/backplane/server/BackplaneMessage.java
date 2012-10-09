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

package com.janrain.backplane.server;

import com.janrain.backplane.DateTimeUtils;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.commons.supersimpledb.message.MessageField;
import com.janrain.commons.util.Pair;
import com.janrain.crypto.ChannelUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.*;

/**
 * @author Johnny Bufu
 */
public class BackplaneMessage extends ExternalizableCore {

    // - PUBLIC

    public BackplaneMessage() { }

    public BackplaneMessage(String bus, String channel, int defaultExpireSeconds, int maxExpireSeconds, Map<String, Object> data) throws BackplaneServerException, SimpleDBException {
        Map<String,String> d = new LinkedHashMap<String, String>(toStringMap(data));
        String id = generateMessageId(new Date());
        d.put(Field.ID.getFieldName(), id);
        d.put(Field.BUS.getFieldName(), bus);
        d.put(Field.CHANNEL_NAME.getFieldName(), channel);
        d.put(Field.PAYLOAD.getFieldName(), extractFieldValueAsJsonString(Field.PAYLOAD, data));
        Object sticky = data.get(Field.STICKY.getFieldName());
        d.put(Field.STICKY.getFieldName(), sticky != null ? sticky.toString() : Boolean.FALSE.toString());
        d.put(Field.EXPIRE.getFieldName(), DateTimeUtils.processExpireTime(sticky, data.get(Field.EXPIRE.getFieldName()), defaultExpireSeconds, maxExpireSeconds));
        super.init(id, d);
    }

    @Override
    public String getIdValue() {
        return get(Field.ID);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(Field.class);
    }

    public Pair<String, Date> updateId(Pair<String, Date> lastIdAndDate) {
        long thisTime = getDateFromId(getIdValue()).getTime();
        long lastTime = 0;
        if (StringUtils.isNotEmpty(lastIdAndDate.getLeft())) {
            lastTime = getDateFromId(lastIdAndDate.getLeft()).getTime();
        }

        if (thisTime <= lastTime) {
            logger.warn("message has an id " + getIdValue() + " that is not > the latest id of " + lastIdAndDate.getLeft());
            Date newDate = new Date(lastIdAndDate.getRight().getTime() + 1);
            put(Field.ID.getFieldName(), generateMessageId(newDate));
            logger.warn("fixed");
            return new Pair<String, Date>(getIdValue(), newDate);
        } else {
            return lastIdAndDate;
        }
    }

    public String getBus() {
        return this.get(Field.BUS);
    }

    public String getChannel() {
        return this.get(Field.CHANNEL_NAME);
    }

    public boolean isSticky() {
        return "true".equalsIgnoreCase(get(Field.STICKY));
    }

    public HashMap<String, Object> asFrame() throws BackplaneServerException {

        HashMap<String, Object> frame = new LinkedHashMap<String, Object>();

        frame.put(Field.ID.getFieldName(), get(BackplaneMessage.Field.ID.getFieldName()));
        frame.put(Field.CHANNEL_NAME.getFieldName(), get(BackplaneMessage.Field.CHANNEL_NAME.getFieldName()));

        // don't copy to frame ID, BUS, CHANNEL_NAME verbatim from Message data structure
        // (and any other, non-Message fields, like ssdb_update_version)
        Map <String,Object> msg = new LinkedHashMap<String, Object>();

        // only add SOURCE, TYPE, STICKY, PAYLOAD to 'message: {...}'
        msg.put(Field.SOURCE.getFieldName(), get(Field.SOURCE));
        msg.put(Field.TYPE.getFieldName(), get(Field.TYPE));
        String sticky = get(Field.STICKY.getFieldName());
        if (sticky != null) {
            // print sticky as a (json) boolean
            msg.put(Field.STICKY.getFieldName(), Boolean.valueOf(sticky));
        }
        msg.put(Field.EXPIRE.getFieldName(), get(Field.EXPIRE));
        try {
            msg.put(
                BackplaneMessage.Field.PAYLOAD.getFieldName(),
                (new ObjectMapper()).readValue(get(BackplaneMessage.Field.PAYLOAD), Object.class) ); // un-quote the value
        } catch (IOException e) {
            String errMsg = "Error deserializing message payload: " + e.getMessage();
            logger.error(errMsg);
            throw new BackplaneServerException(errMsg, e);
        }
        frame.put("message", msg);

        return frame;
    }

    public static enum Field implements MessageField {
        ID("id"),
        CHANNEL_NAME("channel_name"),
        BUS("bus"),
        STICKY("sticky", false) {
            @Override
            public void validate(String value) throws SimpleDBException {
                super.validate(value);
                if (value != null && ! Boolean.TRUE.toString().equalsIgnoreCase(value) && ! Boolean.FALSE.toString().equalsIgnoreCase(value)) {
                    throw new IllegalArgumentException("Invalid boolean value for " + getFieldName() + ": " + value);
                }
            }},
        EXPIRE("expire", true) {
            @Override
            public void validate(String value) throws SimpleDBException {
                super.validate(value);
                try {
                    DateTimeUtils.INTERNETDATE.get().parse(value);
                } catch (ParseException e) {
                    throw new InvalidRequestException("Invalid Internet Date/Time value for " + getFieldName() + ": " + value);
                }
            }},
        SOURCE("source") {
            @Override
            public void validate(String value) throws SimpleDBException {
                super.validate(value);
                try {
                    new URL(value);
                } catch (MalformedURLException e) {
                    throw new IllegalArgumentException("Invalid URL for " + getFieldName() + ": " + value, e);
                }
            }},

        TYPE("type"),
        PAYLOAD("payload");

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

        private Field(String fieldName) {
            this(fieldName, true);
        }

        private Field(String fieldName, boolean required) {
            this.fieldName = fieldName;
            this.required = required;
        }
    }

    public static Date getDateFromId(String backplaneMessageId) {
        if (backplaneMessageId == null) {
            return null;
        }

        try {
            return DateTimeUtils.ISO8601.get().parse(backplaneMessageId.substring(0, backplaneMessageId.indexOf("Z") + 1));
        } catch (ParseException e) {
            logger.warn(e);
        }
        return null;
    }

    // - PRIVATE

    private static final long serialVersionUID = -4601517360705157923L;

    private static final Logger logger = Logger.getLogger(BackplaneMessage.class);

    /**
     * @return a time-based, lexicographically comparable message ID.
     */
    private static String generateMessageId(Date date) {
        return DateTimeUtils.ISO8601.get().format(date) + "-" + ChannelUtil.randomString(10);
    }

    private String extractFieldValueAsJsonString(Field field, Map<String,Object> data) throws BackplaneServerException {
        try {
            Object value = data.get(field.getFieldName());
            return value == null ? null : (new ObjectMapper()).writeValueAsString(value);
        } catch (IOException e) {
            String errMsg = "Error serializing message payload: " + e.getMessage();
            logger.error(errMsg);
            throw new BackplaneServerException(errMsg, e);
        }
    }

}