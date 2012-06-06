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

package com.janrain.backplane2.server.dao;

import com.janrain.backplane2.server.BackplaneMessage;
import com.janrain.backplane2.server.MessagesResponse;
import com.janrain.backplane2.server.Scope;
import com.janrain.backplane2.server.Token;
import com.janrain.backplane2.server.config.Backplane2Config;
import com.janrain.backplane2.server.config.BusConfig2;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.commons.supersimpledb.SuperSimpleDB;
import com.janrain.commons.util.Pair;
import com.janrain.oauth2.TokenException;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Histogram;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.janrain.backplane2.server.BackplaneMessage.Field.*;
import static com.janrain.backplane2.server.config.Backplane2Config.SimpleDBTables.BP_MESSAGES;
import static com.janrain.backplane2.server.config.BusConfig2.Field.*;

/**
 * @author Tom Raney, Johnny Bufu
 */
public class BackplaneMessageDAO extends DAO<BackplaneMessage> {

    // - PUBLIC

    @Override
    public void persist(final BackplaneMessage message) throws SimpleDBException {
        try {
            v2postTimer.time(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    superSimpleDB.store(bpConfig.getTableName(BP_MESSAGES), BackplaneMessage.class, message);
                    return null;
                }
            });
        } catch (Exception e) {
            if (e instanceof SimpleDBException) {
                throw (SimpleDBException) e;
            } else {
                throw new SimpleDBException(e);
            }
        }
    }

    public BackplaneMessage getLatestMessage() throws SimpleDBException {
        String query = "id IS NOT NULL ORDER BY id DESC LIMIT 1";
        Pair<List<BackplaneMessage>, Boolean> messages=
                superSimpleDB.retrieveSomeWhere(bpConfig.getTableName(BP_MESSAGES), BackplaneMessage.class, query);
        if (messages.getLeft().size() > 0) {
            return messages.getLeft().get(0);
        } else {
            return null;
        }
    }

    @Override
    public void delete(String id) throws SimpleDBException {
        superSimpleDB.delete(bpConfig.getTableName(BP_MESSAGES), id);
    }

    public @NotNull BackplaneMessage retrieveBackplaneMessage(@NotNull final String messageId, @NotNull Token token) throws SimpleDBException, TokenException {
        MessageCache<BackplaneMessage> cache = daoFactory.getMessageCache();
        BackplaneMessage firstCached = cache.getFirstMessage();
        BackplaneMessage lastCached = cache.getLastMessage();
        if ( firstCached != null && lastCached != null &&
             firstCached.getIdValue().compareTo(messageId) <= 0 &&
             lastCached.getIdValue().compareTo(messageId) >= 0) {
            return cache.get(messageId);
        }

        BackplaneMessage message;
        try {
            message = v2singleGetTimer.time(new Callable<BackplaneMessage>() {
                @Override
                public BackplaneMessage call() throws Exception {
                    return superSimpleDB.retrieve(bpConfig.getTableName(BP_MESSAGES), BackplaneMessage.class, messageId);
                }
            });
        } catch (Exception e) {
            if (e instanceof SimpleDBException) {
                throw (SimpleDBException) e;
            } else {
                throw new SimpleDBException(e);
            }
        }

        if ( message == null || ! token.getScope().isMessageInScope(message)) {
            // don't disclose that the messageId exists if not in scope
            throw new TokenException("Message id '" + messageId + "' not found", HttpServletResponse.SC_NOT_FOUND);
        } else {
            return message;
        }
    }

    public boolean isChannelFull(String channel) throws SimpleDBException {
        return ! canTake(channel, 0);
    }

    public boolean canTake(String channel, int msgPostCount) throws SimpleDBException {
        final String whereClause = " channel='" + channel + "'";
        try {
            long count = v2channelCountTimer.time(new Callable<Long>() {
                @Override
                public Long call() throws Exception {
                    return superSimpleDB.retrieveCount(bpConfig.getTableName(BP_MESSAGES), whereClause);
                }
            });
            logger.debug("channel: '" + channel + "' message count: " + count + ", limit: " + bpConfig.getDefaultMaxMessageLimit());
            return count + msgPostCount < bpConfig.getDefaultMaxMessageLimit();
        } catch (Exception e) {
            if (e instanceof SimpleDBException) {
                throw (SimpleDBException) e;
            } else {
                throw new SimpleDBException(e);
            }
        }
    }

    public long countMessages() throws SimpleDBException {
        Long messageCount = superSimpleDB.retrieveCount(bpConfig.getTableName(BP_MESSAGES), null);
        v2messageCount.update(messageCount);
        return messageCount;
    }

    /**
     * Retrieve all messages by per scope in the provided bpResponse object.
     * Guaranteed to delivery results in order of message ID.
     *
     * @param bpResponse backplane message response
     * @param token access token to be used for message retrieval
     */
    public void retrieveMesssagesPerScope(@NotNull final MessagesResponse bpResponse, @NotNull final Token token) throws SimpleDBException {
        final Scope scope = token.getScope();
        filterMessagesPerScope(daoFactory.getMessageCache().getMessagesSince(bpResponse.getLastMessageId()), scope, bpResponse);
        logger.info("Local cache hits for request: " + bpResponse.messageCount() + " messages");
        try {
            v2multiGetTimer.time(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    String query = buildMessageSelectQueryClause(scope);
                    Pair<List<BackplaneMessage>, Boolean> unfilteredMessages;
                    do {
                        // We don't want to use SDB's paginating mechanism and retrieve all available, unfiltered messages
                        unfilteredMessages = superSimpleDB.retrieveSomeWhere(bpConfig.getTableName(BP_MESSAGES), BackplaneMessage.class,
                                ((query.length() > 0) ? query + " AND " : "") +
                                " id > '" + bpResponse.getLastMessageId() + "' ORDER BY id LIMIT " + SuperSimpleDB.MAX_SELECT_PAGINATION_LIMIT);

                        filterMessagesPerScope(unfilteredMessages.getLeft(), scope, bpResponse);

                    } while (unfilteredMessages.getLeft().size() > 0 && !bpResponse.moreMessages());

                    return null;
                }
            });
        } catch (Exception e) {
            if (e instanceof SimpleDBException) {
                throw (SimpleDBException) e;
            } else {
                throw new SimpleDBException(e);
            }
        }
    }

    public List<BackplaneMessage> retrieveMessagesNoScope(String sinceIso8601timestamp) throws SimpleDBException {
        List<BackplaneMessage> messages = new ArrayList<BackplaneMessage>();
        Pair<List<BackplaneMessage>, Boolean> newMessages;
        String since = StringUtils.isEmpty(sinceIso8601timestamp) ? "" : sinceIso8601timestamp;
        while(true) {
            // We don't want to use SDB's paginating mechanism and retrieve all available, unfiltered messages
            newMessages = superSimpleDB.retrieveSomeWhere(bpConfig.getTableName(BP_MESSAGES), BackplaneMessage.class,
                    " id > '" + since + "' ORDER BY id LIMIT " + SuperSimpleDB.MAX_SELECT_PAGINATION_LIMIT);
            if (newMessages.getLeft().isEmpty()) {
                break;
            } else {
                messages.addAll(newMessages.getLeft());
                since = newMessages.getLeft().get(0).getIdValue();
                if (messages.size() >= MAX_MSGS_IN_FRAME) break;
            }
        }
        return messages;
    }

    private void filterMessagesPerScope(List<BackplaneMessage> unfilteredMessages, Scope scope, MessagesResponse bpResponse) {
        // Filter and add to results
        List<BackplaneMessage> filteredMessages = new ArrayList<BackplaneMessage>();
        for (BackplaneMessage unfilteredMessage : unfilteredMessages) {
            if (scope.isMessageInScope(unfilteredMessage)) {
                if (filteredMessages.size() >= MAX_MSGS_IN_FRAME) {
                    bpResponse.moreMessages(true);
                    bpResponse.setLastMessageId(filteredMessages.get(filteredMessages.size() - 1).getIdValue());
                    break;
                }
                filteredMessages.add(unfilteredMessage);
            }
        }

        // update lastMessageId to point to last message in this unfiltered result
        if (unfilteredMessages.size() > 0 && !bpResponse.moreMessages()) {
            bpResponse.setLastMessageId(unfilteredMessages.get(unfilteredMessages.size() - 1).getIdValue());
        }

        bpResponse.addMessages(filteredMessages);
    }

    public void deleteExpiredMessages() {
        try {
            logger.info("Backplane message cleanup task started.");
            String messagesTable = bpConfig.getTableName(BP_MESSAGES);
            for(BusConfig2 busConfig : daoFactory.getBusDao().retrieveBuses()) {
                try {
                    // non-sticky
                    superSimpleDB.deleteWhere(messagesTable, getExpiredMessagesClause(busConfig.get(BUS_NAME), false, busConfig.get(RETENTION_TIME_SECONDS)));
                    // sticky
                    superSimpleDB.deleteWhere(messagesTable, getExpiredMessagesClause(busConfig.get(BUS_NAME), true, busConfig.get(RETENTION_STICKY_TIME_SECONDS)));

                } catch (SimpleDBException sdbe) {
                    logger.error("Error cleaning up expired messages on bus "  + busConfig.get(BUS_NAME) + ", " + sdbe.getMessage(), sdbe);
                }
            }

        } catch (Exception e) {
            // catch-all, else cleanup thread stops
            logger.error("Backplane messages cleanup task error: " + e.getMessage(), e);
        } finally {
            logger.info("Backplane messages cleanup task finished.");
        }

    }

    // - PACKAGE

    BackplaneMessageDAO(SuperSimpleDB superSimpleDB, Backplane2Config bpConfig, DaoFactory daoFactory) {
        super(superSimpleDB, bpConfig);
        this.daoFactory = daoFactory;
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(BackplaneMessageDAO.class);

    private static final int MAX_MSGS_IN_FRAME = 25;

    private final DaoFactory daoFactory;

    private final com.yammer.metrics.core.Timer v2postTimer = Metrics.newTimer(BackplaneMessageDAO.class, "v2_sdb_post_message", TimeUnit.MILLISECONDS, TimeUnit.MINUTES);
    private final com.yammer.metrics.core.Timer v2singleGetTimer = Metrics.newTimer(BackplaneMessageDAO.class, "v2_sdb_get_message", TimeUnit.MILLISECONDS, TimeUnit.MINUTES);
    private final com.yammer.metrics.core.Timer v2multiGetTimer = Metrics.newTimer(BackplaneMessageDAO.class, "v2_sdb_get_messages", TimeUnit.MILLISECONDS, TimeUnit.MINUTES);
    private final com.yammer.metrics.core.Timer v2channelCountTimer = Metrics.newTimer(BackplaneMessageDAO.class, "v2_sdb_channel_count", TimeUnit.MILLISECONDS, TimeUnit.MINUTES);
    private final Histogram v2messageCount = Metrics.newHistogram(BackplaneMessageDAO.class, "v2_sdb_message_count");

    private String getExpiredMessagesClause(String busId, boolean sticky, String retentionTimeSeconds) {
        return BUS.getFieldName() + " = '" + busId + "' AND " +
                // "is (not) null" is low-performance on simpledb apparently
                // http://practicalcloudcomputing.com/post/722621724/simpledb-essentials-for-high-performance-users-part-2
                STICKY.getFieldName() + " = '" + Boolean.toString(sticky) + "' AND " +
                ID.getFieldName() + " < '" +
                Backplane2Config.ISO8601.format(new Date(System.currentTimeMillis() - Long.valueOf(retentionTimeSeconds) * 1000))
                + "'";
    }

    private static String buildMessageSelectQueryClause(Scope scope) {
        StringBuilder query = new StringBuilder();
        if (scope != null) {
            for(BackplaneMessage.Field scopeKey : scope.getScopeMap().keySet()) {
                Set<String> scopeValues = scope.getScopeFieldValues(scopeKey);
                if (scopeValues != null && scopeValues.size() == 1) {
                    if (query.length() > 0) query.append(" AND ");
                    query.append(scopeKey.getFieldName()).append("='").append(scopeValues.iterator().next()).append("'");
                }
            }
        }
        return query.toString();
    }
}
