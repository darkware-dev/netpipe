/*==============================================================================
 =
 = Copyright 2017: darkware.org
 =
 =    Licensed under the Apache License, Version 2.0 (the "License");
 =    you may not use this file except in compliance with the License.
 =    You may obtain a copy of the License at
 =
 =        http://www.apache.org/licenses/LICENSE-2.0
 =
 =    Unless required by applicable law or agreed to in writing, software
 =    distributed under the License is distributed on an "AS IS" BASIS,
 =    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 =    See the License for the specific language governing permissions and
 =    limitations under the License.
 =
 =============================================================================*/

package org.darkware.netpipe;

import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * A {@link PipeService} is an active service which responds to requests from an EFP client.
 *
 * @author jeff@darkware.org
 * @since 2017-05-09
 */
public class PipeService implements Callable<Boolean>, Runnable
{
    protected static final Logger log = LogManager.getLogger("PipeService");

    private final ServerSocketChannel server;
    private final AtomicBoolean active;

    private final Map<InetSocketAddress, Connection> connections;

    private Supplier<String> recordSource;

    public PipeService(final InetAddress addr, final int port)
    {
        super();

        this.connections = Maps.newConcurrentMap();
        this.active = new AtomicBoolean(true);

        try
        {
            this.server = ServerSocketChannel.open();
            this.server.bind(new InetSocketAddress(addr, port));
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("Could not start a pipe service.", e);
        }
    }

    public PipeService(final int port)
    {
        this(EFProtocol.localAddress(), port);
    }

    public void setRecordSource(final Supplier<String> source)
    {
        this.recordSource = source;
    }

    @Override
    public void run()
    {
        try
        {
            this.call();
        }
        catch (Exception e)
        {
            PipeService.log.error("Error while executing pipe service: {}", e.getLocalizedMessage(), e);
        }
    }

    @Override
    public Boolean call() throws Exception
    {
        while (this.active.get())
        {
            try
            {
                SocketChannel clientSocket = this.server.accept();
                InetSocketAddress remoteAddr = (InetSocketAddress)clientSocket.getLocalAddress();
                PipeService.log.debug("Connection from {}:{}", remoteAddr.getAddress().getHostAddress(), remoteAddr.getPort());

                Connection connection = new Connection(clientSocket);
                this.connections.put(connection.remoteAddr, connection);
                connection.start();
            }
            catch (AsynchronousCloseException e)
            {
                // The socket closed while we were waiting for clients.
                // This is a shutdown event
                this.active.set(false);
            }
            catch (SocketException e)
            {
                PipeService.log.error("Error while connecting to client.", e);
            }
        }

        return true;
    }

    public void terminate()
    {
        try
        {
            this.active.set(false);
            this.server.close();
        }
        catch (IOException e)
        {
            PipeService.log.error("Error while shutting down server.", e);
        }
    }

    private class Connection extends Thread
    {
        private final SocketChannel socket;
        private final InetSocketAddress remoteAddr;

        public Connection(final SocketChannel socket)
        {
            super();

            try
            {
                this.socket = socket;
                this.remoteAddr = (InetSocketAddress) socket.getRemoteAddress();
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException("Could not resolve the client address.");
            }
        }

        public InetSocketAddress getRemoteAddr()
        {
            return this.remoteAddr;
        }

        @Override
        public void run()
        {
            PipeService.log.debug("{} : Starting client interaction", this.getRemoteAddr());

            ByteBuffer cmd = ByteBuffer.allocate(20);
            while(this.socket.isConnected())
            {
                try
                {
                    // Read incoming commands
                    cmd.clear();
                    cmd.limit(Character.BYTES);
                    int bytes = this.socket.read(cmd);
                    PipeService.log.debug("{} : Reading pipe command. Size = {}", this.getRemoteAddr(), bytes);

                    cmd.flip();
                    char command = cmd.getChar();
                    PipeService.log.debug("{} : Command = {}", this.getRemoteAddr(), command);

                    switch (command)
                    {
                        case 'I':
                            this.sendInfo();
                            break;
                        case 'N':
                            this.sendRecord();
                            break;
                        default:
                            PipeService.log.error("{} : Unknown command = {}", this.getRemoteAddr(), command);
                    }
                }
                catch (IOException e)
                {
                    PipeService.log.error("Error while reading pipe command.", e);
                }
            }
        }

        protected void sendRecord()
        {
            // Encode the record
            ByteBuffer rawData = StandardCharsets.UTF_8.encode(PipeService.this.recordSource.get());

            // Create the header
            ByteBuffer header = ByteBuffer.allocate(Character.BYTES + Integer.BYTES);
            header.putChar('Z');
            header.putInt(rawData.remaining());
            header.flip();

            // Send the record
            try
            {
                PipeService.log.debug("{} : Sending record, size = {}", this.getRemoteAddr(), rawData.remaining());
                this.socket.write(header);
                this.socket.write(rawData);
            }
            catch (IOException e)
            {
                PipeService.log.error("Error while sending record.", e);
            }
        }

        protected void sendInfo()
        {
            ByteBuffer info = ByteBuffer.allocate(20);

            info.putChar('Z');
            final int lengthPos = info.position();
            info.putInt(0);
            final int headerLen = info.position();

            info.putChar('L');
            info.putInt(Integer.BYTES);
            info.putInt(222);

            int length = info.position() - headerLen;
            info.putInt(lengthPos, length);
            PipeService.log.debug("{} : Response size = {}.", this.getRemoteAddr(), length);

            info.flip();

            try
            {
                PipeService.log.debug("{} : Sending channel information.", this.getRemoteAddr());
                this.socket.write(info);
            }
            catch (IOException e)
            {
                PipeService.log.error("Error while sending channel information.", e);
            }
        }
    }
}
