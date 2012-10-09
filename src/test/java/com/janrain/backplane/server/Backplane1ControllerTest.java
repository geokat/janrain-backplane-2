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

import com.janrain.backplane.server.config.Backplane1Config;
import com.janrain.backplane.server.dao.DaoFactory;
import com.janrain.backplane2.server.config.User;
import junit.framework.TestCase;
import org.apache.catalina.util.Base64;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.servlet.HandlerAdapter;

import javax.inject.Inject;
import java.util.Map;

/**
 * @author Tom Raney
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:/spring/app-config.xml", "classpath:/spring/mvc-config.xml" })
public class Backplane1ControllerTest extends TestCase {

    private static final Logger logger = Logger.getLogger(Backplane1ControllerTest.class);

    @Inject
    private Backplane1Config bpConfig;

    @Inject
	private ApplicationContext applicationContext;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private HandlerAdapter handlerAdapter;

    private static final int DEFAULT_MESSAGE_RETENTION_SECONDS = 60;
    private static final int MAX_MESSAGE_RETENTION_SECONDS = 300;

    @Inject
	private Backplane1Controller controller;

    private static final String TEST_MSG = "{\n" +
            "        \"source\": \"ftp://bla_source/\",\n" +
            "        \"type\": \"bla_type\",\n" +
            "        \"sticky\": \"false\",\n" +
            "        \"payload\": {\n" +
            "            \"payload1\": \"bla1\",\n" +
            "            \"payload2\": \"bla2\"\n" +
            "        }\n" +
            "    }\n";

    /**
	 * Initialize before every individual test method
	 */
	@Before
	public void init() {
        assertNotNull(applicationContext);
        handlerAdapter = applicationContext.getBean("handlerAdapter", HandlerAdapter.class);
		refreshRequestAndResponse();
	}


    private void refreshRequestAndResponse() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    public void testGetChannel() throws Exception {

        refreshRequestAndResponse();
        request.setRequestURI("/bus/test/channel/test");
        request.setMethod("GET");

        handlerAdapter.handle(request, response, controller);
        logger.debug("testGetChannel() => " + response.getContentAsString());
        assertTrue(response.getContentAsString().contains("[]"));

    }

    @Test
    public void testGetBus() throws Exception {

        refreshRequestAndResponse();
        BackplaneMessage message = null;

        try {

            DaoFactory.getUserDAO().persist(new User("testBusOwner", "busOwnerSecret"));
            DaoFactory.getBusDAO().persist(new BusConfig1("test", "testBusOwner", "60", "28800"));

            // encode un:pw
            String credentials = "testBusOwner:busOwnerSecret";

            Map<String,Object> msg = new ObjectMapper().readValue(TEST_MSG, new TypeReference<Map<String,Object>>() {});
            message = new BackplaneMessage("test", "test", DEFAULT_MESSAGE_RETENTION_SECONDS, MAX_MESSAGE_RETENTION_SECONDS, msg);
            DaoFactory.getBackplaneMessageDAO().persist(message);

            Thread.sleep(1000);

            String encodedCredentials = new String(Base64.encode(credentials.getBytes()));
            request.setAuthType("BASIC");
            request.addHeader("Authorization", "Basic " + encodedCredentials);
            request.setRequestURI("/bus/test");
            request.setMethod("GET");

            handlerAdapter.handle(request, response, controller);
            logger.info("testGetBus() => " + response.getContentAsString());
            assertFalse("passed", response.getContentAsString().contains("[]"));

        } finally {
            DaoFactory.getUserDAO().delete("testBusOwner");
            DaoFactory.getBusDAO().delete("test");
            if (message != null) {
                DaoFactory.getBackplaneMessageDAO().delete(message.getIdValue());
            }
        }

    }
}
