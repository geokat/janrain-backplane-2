package com.janrain.backplane;

import com.janrain.backplane2.server.InvalidRequestException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * @author Johnny Bufu
 */
public class DateTimeUtils {

    // http://fahdshariff.blogspot.ca/2010/08/dateformat-with-multiple-threads.html
    public static final ThreadLocal<DateFormat> ISO8601 = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") {{
                setTimeZone(TimeZone.getTimeZone("GMT"));
            }};
        }
    };
    public static final ThreadLocal<DateFormat> INTERNETDATE = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'") {{
                setTimeZone(TimeZone.getTimeZone("UTC"));
            }};
        }
    };

    public static int getExpireSeconds(String messageId, String expire, boolean sticky) {
        long seconds = (getExpireTime(messageId, expire, sticky).getTime() - System.currentTimeMillis()) / 1000;
        if (seconds < 0 || seconds>Integer.MAX_VALUE) {
            logger.warn("invalid expiration seconds: " + seconds);
            return sticky ? MAX_RETENTION_SECONDS : DEFAULT_RETENTION_SECONDS;
        } else {
            return (int)seconds;
        }
    }

    public static Date getExpireTime(String messageId, String expire, boolean sticky) {
        try {
            return StringUtils.isEmpty(expire) ? null : INTERNETDATE.get().parse(expire);
        } catch (ParseException e) {
            Date defaultExpiration = sticky ? new Date(System.currentTimeMillis() + MAX_RETENTION_SECONDS * 1000) : new Date(System.currentTimeMillis() + DEFAULT_RETENTION_SECONDS * 1000);
            logger.warn("invalid expiration for message " + messageId + " : " + expire + " returning hardcoded default: " + INTERNETDATE.get().format(defaultExpiration));
            return defaultExpiration;
        }
    }

    public static String processExpireTime(Object requestSticky, Object requestedExpireTime, int defaultRetentionSeconds, int maxRetentionSeconds) {
        try {
            boolean sticky = requestSticky != null && Boolean.parseBoolean(requestSticky.toString());
            if (requestedExpireTime == null) {
                return INTERNETDATE.get().format(new Date(System.currentTimeMillis() + (sticky ? maxRetentionSeconds : defaultRetentionSeconds) * 1000));
            } else {
                Date requestedExpire = INTERNETDATE.get().parse(requestedExpireTime.toString());
                if (requestedExpire.before(new Date(System.currentTimeMillis() + maxRetentionSeconds * 1000))) {
                    return requestedExpireTime.toString();
                } else {
                    throw new InvalidRequestException("Requested expiration time " + requestedExpireTime + " is too far in the future, max is " + maxRetentionSeconds + " seconds");
                }
            }
        } catch (ParseException pe) {
            throw new InvalidRequestException("Requested exipration time not in internet date/time format: " + pe.getMessage());
        }
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(DateTimeUtils.class);

    private static final int DEFAULT_RETENTION_SECONDS = 60;
    private static final int MAX_RETENTION_SECONDS = 3600;

    private DateTimeUtils() { }
}
