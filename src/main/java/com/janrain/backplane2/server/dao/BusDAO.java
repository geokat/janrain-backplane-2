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

import com.janrain.backplane2.server.config.Backplane2Config;
import com.janrain.backplane2.server.config.BusConfig2;
import com.janrain.cache.CachedMemcached;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.commons.supersimpledb.SuperSimpleDB;
import com.janrain.oauth2.TokenException;
import com.yammer.metrics.Metrics;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.janrain.backplane2.server.config.Backplane2Config.SimpleDBTables.BP_BUS_CONFIG;

/**
 * @author Johnny Bufu
 */
public class BusDAO extends DAO<BusConfig2> {

    BusDAO(SuperSimpleDB superSimpleDB, Backplane2Config bpConfig, DaoFactory daoFactory) {
        super(superSimpleDB, bpConfig);
        this.daoFactory = daoFactory;
    }

    @Override
    public void persist(BusConfig2 bus) throws SimpleDBException {
        superSimpleDB.store(bpConfig.getTableName(BP_BUS_CONFIG), BusConfig2.class, bus);
    }

    @Override
    public void delete(String id) throws SimpleDBException {
        superSimpleDB.delete(bpConfig.getTableName(BP_BUS_CONFIG), id);
    }

    public List<BusConfig2> retrieveBuses() throws SimpleDBException {
        return superSimpleDB.retrieveAll(bpConfig.getTableName(BP_BUS_CONFIG), BusConfig2.class);
    }

    public BusConfig2 retrieveBus(String busId) throws SimpleDBException {
        Object obj = null;
        obj = CachedMemcached.getInstance().getObject(busId);
        if (obj == null) {
            BusConfig2 busConfig2 = superSimpleDB.retrieve(bpConfig.getTableName(BP_BUS_CONFIG), BusConfig2.class, busId);
            if (busConfig2 != null) {
                CachedMemcached.getInstance().setObject(busId, 3600, busConfig2);
            }
            return busConfig2;
        } else {
            return (BusConfig2)obj;
        }
    }

    public List<BusConfig2> retrieveByOwner(String busOwner) throws SimpleDBException {
        return superSimpleDB.retrieveWhere(
                bpConfig.getTableName(BP_BUS_CONFIG), BusConfig2.class,
                BusConfig2.Field.OWNER.getFieldName() + "='" + busOwner +"'", true);
    }

    /** Associated grants and tokens are deleted/revoked. */
    public void deleteByOwner(String busOwner) throws SimpleDBException, TokenException {
        List<BusConfig2> busConfigs = retrieveByOwner(busOwner);
        List<String> buses = new ArrayList<String>();
        for (BusConfig2 busConfig : busConfigs) {
            buses.add(busConfig.getIdValue());
        }
        superSimpleDB.deleteWhere(bpConfig.getTableName(BP_BUS_CONFIG), BusConfig2.Field.OWNER.getFieldName() + "='" + busOwner +"'");
        daoFactory.getGrantDao().deleteByBuses(buses);
    }

    // - PRIVATE

    private final DaoFactory daoFactory;

}
