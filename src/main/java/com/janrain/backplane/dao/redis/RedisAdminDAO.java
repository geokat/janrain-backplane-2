package com.janrain.backplane.dao.redis;

import com.janrain.backplane.common.BackplaneServerException;
import com.janrain.backplane.config.Admin;
import com.janrain.backplane.dao.DAO;
import com.janrain.backplane.redis.Redis;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.SerializationUtils;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * @author Tom Raney
 */
public class RedisAdminDAO implements DAO<Admin> {

    public static byte[] getAdminAdminKey(String userId) {
        return ("v2_admin_" + userId).getBytes();
    }

    @Override
    public void persist(Admin user) throws BackplaneServerException {
        byte[] key = getAdminAdminKey(user.getIdValue());
        logger.info("writing key to redis: " + new String(key));
        Redis.getInstance().set(getAdminAdminKey(user.getIdValue()), SerializationUtils.serialize(user));
    }

    @Override
    public void delete(String id) throws BackplaneServerException {
        byte[] key = getAdminAdminKey(id);
        Redis.getInstance().del(key);
    }

    @Override
    public Admin get(String key) {
        byte[] bytes = Redis.getInstance().get(getAdminAdminKey(key));
        if (bytes != null) {
            return (Admin) SerializationUtils.deserialize(bytes);
        } else {
            return null;
        }
    }

    @Override
    public List<Admin> getAll() throws BackplaneServerException {
        throw new NotImplementedException();
    }

    private static final Logger logger = Logger.getLogger(RedisAdminDAO.class);

}

