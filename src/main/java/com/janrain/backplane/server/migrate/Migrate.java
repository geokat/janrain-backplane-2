package com.janrain.backplane.server.migrate;

import com.janrain.backplane.server.config.Backplane1Config;
import com.janrain.backplane.server.config.BusConfig1New;
import com.janrain.backplane.server.dao.BusConfig1DAO;
import com.janrain.backplane.server.migrate.legacy.BusConfig1;
import com.janrain.backplane.server.migrate.legacy.User;
import com.janrain.backplane.server.config.UserNew;
import com.janrain.backplane.server.dao.DaoFactory;
import com.janrain.backplane.server.dao.UserNewDAO;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.commons.supersimpledb.SuperSimpleDB;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * @author Tom Raney
 */
public class Migrate {

    private SuperSimpleDB superSimpleDB;
    private Backplane1Config bpConfig;
    private final DaoFactory daoFactory;

    public Migrate(SuperSimpleDB superSimpleDB, Backplane1Config bpConfig, DaoFactory daoFactory) {
        this.superSimpleDB = superSimpleDB;
        this.bpConfig = bpConfig;
        this.daoFactory = daoFactory;
    }

    public void migrate() throws SimpleDBException {

        List<User> users = superSimpleDB.retrieveAll(bpConfig.getTableName(Backplane1Config.SimpleDBTables.BP1_USERS), User.class);
        int recs = 0;

        UserNewDAO userNewDAO = daoFactory.getNewUserDAO();

        for (User user: users) {
            logger.info("(" + ++recs + ") User records imported: " + user.getIdValue());
            userNewDAO.persist(new UserNew(user));
        }

        BusConfig1DAO busConfig1DAO = daoFactory.getNewBusDAO();
        List<BusConfig1> buses = superSimpleDB.retrieveAll(bpConfig.getTableName(Backplane1Config.SimpleDBTables.BP1_BUS_CONFIG), BusConfig1.class);
        recs = 0;
        for (BusConfig1 bus: buses) {
            logger.info("(" + ++recs + ") BusConfig records imported: " + bus.getIdValue());
            busConfig1DAO.persist(new BusConfig1New(bus));
        }

        //TODO existing messages?

        logger.info("migration of SDB data complete");


    }

    private static final Logger logger = Logger.getLogger(Migrate.class);
}