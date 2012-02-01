/*
 * Copyright 2011 Janrain, Inc.
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

import com.janrain.backplane2.server.Access;
import com.janrain.backplane2.server.Grant;
import com.janrain.backplane2.server.GrantTokenRel;
import com.janrain.backplane2.server.Token;
import com.janrain.backplane2.server.config.Backplane2Config;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.commons.supersimpledb.SuperSimpleDB;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.janrain.backplane2.server.config.Backplane2Config.SimpleDBTables.BP_ACCESS_TOKEN;
import static com.janrain.backplane2.server.config.Backplane2Config.SimpleDBTables.BP_AUTHTOKEN_REL;


/**
 * @author Tom Raney
 */

public class TokenDAO extends DAO {

    TokenDAO(SuperSimpleDB superSimpleDB, Backplane2Config bpConfig) {
        super(superSimpleDB, bpConfig);
    }

    public void persistToken(Token token) throws SimpleDBException {
        superSimpleDB.store(bpConfig.getTableName(BP_ACCESS_TOKEN), Token.class, token, true);
        // if any grants exist for this token be sure to persist
        // the relationships, but delete them first
        deleteRelsByTokenId(token.getIdValue());
        if (!token.getGrants().isEmpty()) {
            for (Grant grant : token.getGrants()) {
                superSimpleDB.store(bpConfig.getTableName(Backplane2Config.SimpleDBTables.BP_AUTHTOKEN_REL),
                        GrantTokenRel.class, new GrantTokenRel(grant.getIdValue(), token.getIdValue()));
            }
        }
    }

    public Token retrieveToken(String tokenId) throws SimpleDBException {
        return superSimpleDB.retrieve(bpConfig.getTableName(BP_ACCESS_TOKEN), Token.class, tokenId);
    }

    public Token retrieveTokenByChannel(String channel) throws SimpleDBException {
        List<Token> tokens = superSimpleDB.retrieveWhere(bpConfig.getTableName(BP_ACCESS_TOKEN), Token.class, "channel='" + channel + "'", false);
        if (tokens.isEmpty()) {
            return null;
        }
        return tokens.get(0);
    }

    public void deleteToken(Token token) throws SimpleDBException {
        deleteTokenById(token.getIdValue());
    }

    public void deleteTokenById(String tokenId) throws SimpleDBException {
        superSimpleDB.delete(bpConfig.getTableName(BP_ACCESS_TOKEN), tokenId);
        deleteRelsByTokenId(tokenId);
    }

    public void deleteExpiredTokens() throws SimpleDBException {
        try {
            logger.info("Backplane token cleanup task started.");
            String expiredClause = Access.Field.EXPIRES.getFieldName() + " < '" + Backplane2Config.ISO8601.format(new Date(System.currentTimeMillis())) + "'";
            superSimpleDB.deleteWhere(bpConfig.getTableName(BP_ACCESS_TOKEN), expiredClause);
        } catch (Exception e) {
            // catch-all, else cleanup thread stops
            logger.error("Backplane token cleanup task error: " + e.getMessage(), e);
        } finally {
            logger.info("Backplane token cleanup task finished.");
        }
    }

    public List<Token> retrieveTokensByGrant(String grantId) throws SimpleDBException {
        ArrayList<Token> tokens = new ArrayList<Token>();

        try {
            List<GrantTokenRel> rels = superSimpleDB.retrieveWhere(bpConfig.getTableName(BP_AUTHTOKEN_REL),
                    GrantTokenRel.class, "auth_id='" + grantId + "'", true);
            for (GrantTokenRel rel : rels) {
                tokens.add(retrieveToken(rel.getTokenId()));
            }
        } catch (SimpleDBException sdbe) {
            // ignore -
        }
        return tokens;
    }

    public void revokeTokenByGrant(String grantId) throws SimpleDBException {
        List<Token> tokens = retrieveTokensByGrant(grantId);
        for (Token token : tokens) {
            deleteToken(token);
            logger.info("revoked token " + token.getIdValue());
        }
        logger.info("all tokens for grant " + grantId + " have been revoked");
    }

    // - PRIVATE

    private void deleteRelsByTokenId(String tokenId)  {
        List<GrantTokenRel> rels;
        try {
            rels = superSimpleDB.retrieveWhere(bpConfig.getTableName(BP_AUTHTOKEN_REL), GrantTokenRel.class, "token_id='" + tokenId + "'", true);
            for (GrantTokenRel rel: rels) {
                superSimpleDB.delete(bpConfig.getTableName(BP_AUTHTOKEN_REL), rel.getIdValue());
            }
        } catch (SimpleDBException e) {
            // do nothing
        }
    }

    private static final Logger logger = Logger.getLogger(TokenDAO.class);

}