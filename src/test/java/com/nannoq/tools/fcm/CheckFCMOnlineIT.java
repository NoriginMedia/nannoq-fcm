/*
 * MIT License
 *
 * Copyright (c) 2017 Anders Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.nannoq.tools.fcm;

import com.nannoq.tools.fcm.server.FcmServer;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.*;
import org.junit.runner.RunWith;
import redis.embedded.RedisServer;

import java.util.Properties;

import static junit.framework.TestCase.assertTrue;

/**
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
@RunWith(VertxUnitRunner.class)
public class CheckFCMOnlineIT {
    private static final Logger logger = LoggerFactory.getLogger(CheckFCMOnlineIT.class.getSimpleName());

    private static Properties p = new Properties();
    private RedisServer redisServer;
    private FcmServer fcmServer;
    private DefaultDataMessageHandler defaultDataMessageHandler;

    @Rule
    public RunTestOnContext rule = new RunTestOnContext();

    @BeforeClass
    public static void setUpClass() throws Exception {
        p.load(ClassLoader.getSystemResourceAsStream("fcm.properties"));
    }

    @Before
    public void setUp(TestContext testContext) throws Exception {
        redisServer = new RedisServer(Integer.parseInt(System.getProperty("redis.port")));
        redisServer.start();

        defaultDataMessageHandler = new DefaultDataMessageHandler();
        fcmServer = FcmCreator.createFcm(defaultDataMessageHandler);

        rule.vertx().deployVerticle(fcmServer, fcmconfig(), testContext.asyncAssertSuccess());
    }

    private DeploymentOptions fcmconfig() {
        return new DeploymentOptions()
                .setConfig(new JsonObject()
                        .put("basePackageNameFcm", p.getProperty("fcm.api.app"))
                        .put("gcmSenderId", p.getProperty("fcm.api.id"))
                        .put("gcmApiKey", p.getProperty("fcm.api.key"))
                        .put("redis_host", System.getProperty("redis.endpoint"))
                        .put("redis_port", Integer.parseInt(System.getProperty("redis.port"))));
    }

    @Test
    public void fcmRunning() {
        assertTrue(fcmServer.isOnline());
    }

    @After
    public void tearDown(TestContext testContext) throws Exception {
        redisServer.stop();
        rule.vertx().undeploy(fcmServer.deploymentID(), testContext.asyncAssertSuccess());
    }
}
