//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.javax.tests.quotes;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpoint;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.javax.tests.LocalServer;
import org.eclipse.jetty.websocket.javax.tests.WSEventTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class QuotesDecoderTest
{
    @ServerEndpoint(value = "/quoter", subprotocols = "quotes")
    public static class QuoterServerEndpoint extends WSEventTracker
    {
        public QuoterServerEndpoint()
        {
            super("quoter");
        }

        @OnOpen
        public void onOpen(Session session)
        {
            super.onWsOpen(session);
        }

        @OnClose
        public void onClose(CloseReason closeReason)
        {
            super.onWsClose(closeReason);
        }

        @OnMessage
        public void onOpenResource(String filename)
        {
            super.onWsText(filename);
            QuotesDecoderTest.LOG.debug("onOpenResource({})", filename);
            try
            {
                RemoteEndpoint.Basic remote = session.getBasicRemote();
                List<String> lines = QuotesUtil.loadLines(filename);
                for (String line : lines)
                {
                    remote.sendText(line + "\n", false);
                }
                remote.sendText("", true);
            }
            catch (Exception e)
            {
                QuotesDecoderTest.LOG.warn("Unable to send quotes", e);
            }
        }
    }

    private static final Logger LOG = Log.getLogger(QuotesDecoderTest.class);

    private LocalServer server;
    private WebSocketContainer client;

    @BeforeEach
    public void initClient()
    {
        client = ContainerProvider.getWebSocketContainer();
    }

    @BeforeEach
    public void startServer() throws Exception
    {
        server = new LocalServer();
        server.start();
    }

    @AfterEach
    public void stopServer() throws Exception
    {
        server.stop();
    }

    @Test
    public void testSingleQuotes(TestInfo testInfo) throws Exception
    {
        server.getServerContainer().addEndpoint(QuoterServerEndpoint.class);

        URI wsUri = server.getWsUri().resolve("/quoter");
        QuotesSocket clientSocket = new QuotesSocket(testInfo.getTestMethod().toString());

        try (Session clientSession = client.connectToServer(clientSocket, wsUri))
        {
            clientSession.getAsyncRemote().sendText("quotes-ben.txt");

            Quotes quotes = clientSocket.messageQueue.poll(5, TimeUnit.SECONDS);
            assertThat("Quotes", quotes, notNullValue());
            assertThat("Quotes Author", quotes.getAuthor(), is("Benjamin Franklin"));
            assertThat("Quotes Count", quotes.getQuotes().size(), is(3));
        }
    }

    @Test
    public void testTwoQuotes(TestInfo testInfo) throws Exception
    {
        server.getServerContainer().addEndpoint(QuoterServerEndpoint.class);

        URI wsUri = server.getWsUri().resolve("/quoter");
        QuotesSocket clientSocket = new QuotesSocket(testInfo.getTestMethod().toString());
        try(Session clientSession = client.connectToServer(clientSocket, wsUri))
        {
            clientSession.getAsyncRemote().sendText("quotes-ben.txt");
            clientSession.getAsyncRemote().sendText("quotes-twain.txt");

            Quotes quotes;
            quotes = clientSocket.messageQueue.poll(5, TimeUnit.SECONDS);
            assertThat("Quotes", quotes, notNullValue());
            assertThat("Quotes Author", quotes.getAuthor(), is("Benjamin Franklin"));
            assertThat("Quotes Count", quotes.getQuotes().size(), is(3));

            quotes = clientSocket.messageQueue.poll(5, TimeUnit.SECONDS);
            assertThat("Quotes", quotes, notNullValue());
            assertThat("Quotes Author", quotes.getAuthor(), is("Mark Twain"));
            assertThat("Quotes Count", quotes.getQuotes().size(), is(4));
        }
    }
}
