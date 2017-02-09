/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package mina.chat;

import java.net.InetSocketAddress;

import org.apache.mina.core.filterchain.DefaultIoFilterChainBuilder;
import mina.chat.BogusSslContextFactory;
import mina.chat.filter.WebSocketCodecFactory;

import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.filter.logging.MdcInjectionFilter;
import org.apache.mina.filter.ssl.SslContextFactory;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

/**
 * (<b>Entry point</b>) Chat server
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class Main {
    /** Choose your favorite port number. */
    private static final int PORT = 1234;

    /** Set this to true if you want to make the server SSL */
    private static final boolean USE_SSL = false;

    public static void main(String[] args) throws Exception {
    	// Create Non-Sync IO Socket Acceptor.
        NioSocketAcceptor acceptor = new NioSocketAcceptor();
        // Get Acceptor's Filter Chain
        DefaultIoFilterChainBuilder chain = acceptor.getFilterChain();

        // MDC( Mapped Diagnostic Context ), Injection some key IoSession properties
        MdcInjectionFilter mdcInjectionFilter = new MdcInjectionFilter();
        // Add to Filter Chain
        chain.addLast("mdc", mdcInjectionFilter);

        // Add SSL filter if USE_SSL is enabled.
        if (USE_SSL) {
            addSSLSupport(chain);
        }

        // Add Codec to Filter
        chain.addLast("codec", new ProtocolCodecFilter(
                new WebSocketCodecFactory()));

        // Add Logger to Filter
        addLogger(chain);

        // set Handler -> ChatProtocolHandler.java
        acceptor.setHandler(new ChatProtocolHandler());
        // Bind
        acceptor.bind(new InetSocketAddress(PORT));

        System.out.println("Listening on port " + PORT);
    }

    private static void addSSLSupport(DefaultIoFilterChainBuilder chain)
            throws Exception {
        SslFilter sslFilter = new SslFilter(BogusSslContextFactory
                .getInstance(true));
        chain.addLast("sslFilter", sslFilter);
        System.out.println("SSL ON");
    }

    private static void addLogger(DefaultIoFilterChainBuilder chain)
            throws Exception {
        chain.addLast("logger", new LoggingFilter());
        System.out.println("Logging ON");
    }
}
