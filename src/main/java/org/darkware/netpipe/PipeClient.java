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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link PipeClient} is an interface object which allows for data to be retrieved from a remote
 * server via the unimpressive Explicit Feed Protocol. Processing for the client is driven by the
 * current thread, rather than having a thread running within the client. This takes advantage of the
 * client-driven, request-for-push nature of EFP.
 *
 * @author jeff@darkware.org
 * @since 2017-05-09
 */
public class PipeClient
{
    /** A logger for the client. */
    protected static final Logger log = LogManager.getLogger("PipeClient");

    /** The address of the connected server. */
    private final InetSocketAddress serverAddr;
    private SocketChannel socket;
    private ByteBuffer request;
    private ByteBuffer response;

    private int linesAvailable;
    private AtomicBoolean connected;

    /**
     * Create a new connected {@link PipeClient}.
     *
     * @param addr The remote server address.
     * @param port The remote server port.
     */
    public PipeClient(final InetAddress addr, final int port)
    {
        super();

        this.connected = new AtomicBoolean(false);

        this.request = ByteBuffer.allocate(20);
        this.response = ByteBuffer.allocate(8192);

        this.serverAddr = new InetSocketAddress(addr, port);
        try
        {
            this.socket = SocketChannel.open(this.serverAddr);
            PipeClient.log.info("Connected to server: {}", this.serverAddr);
            this.connected.set(true);
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("Could not connect to the server.", e);
        }
    }

    /**
     * Create a new connected {@link PipeClient} connected to a server on the same host. A localhost address
     * will be used.
     *
     * @param port The local server port.
     */
    public PipeClient(final int port)
    {
        this(EFProtocol.localAddress(), port);
    }

    /**
     * Fetch the expected number of available lines from the server. The actual number may be larger than the
     * declared count. This number represents a number of <em>guaranteed</em> lines.
     *
     * @return The number of lines.
     */
    public int getLinesAvailable()
    {
        if (this.linesAvailable < 0) this.requestInfo();
        return this.linesAvailable;
    }

    /**
     * Request to have the server supply a set of source information.
     */
    public void requestInfo()
    {
        synchronized (this.request)
        {
            this.sendRequest('I');
            this.readInformation();
        }
    }

    /**
     * Request a single record from the server.
     *
     * @return A record from the server, as a UTF-8 String.
     */
    public String requestRecord()
    {
        synchronized (this.request)
        {
            this.sendRequest('N');
            return this.readRecord();
        }
    }

    /**
     * Shut down the client, preventing further requests.
     */
    public void close()
    {
        try
        {
            this.socket.close();
        }
        catch (IOException e)
        {
            PipeClient.log.error("Error while shutting down client.", e);
        }
        finally
        {
            this.connected.set(false);
        }
    }

    /**
     * Send an EFP client request to the server.
     *
     * @param requestCode The EFP action code.
     */
    protected void sendRequest(final char requestCode)
    {
        this.request.clear();
        this.request.putChar(requestCode);
        this.request.flip();

        try
        {
            this.socket.write(this.request);
            PipeClient.log.debug("Sent request: {}", requestCode);
        }
        catch (IOException e)
        {
            PipeClient.log.error("Error while sending request.", e);
        }
    }

    /**
     * Read a record back from the server.
     *
     * @return The record, as a UTF-8 encoded {@link String}.
     */
    protected String readRecord()
    {
        this.readResponse();

        return StandardCharsets.UTF_8.decode(this.response).toString();
    }

    /**
     * Read source metadata from the server. This consists of one or more descriptive fields that may be
     * useful in determining the amount or type of data the source supplies.
     */
    protected void readInformation()
    {
        this.readResponse();

        while (this.response.hasRemaining())
        {
            char field = this.response.getChar();
            int fieldLen = this.response.getInt();
            int nextField = this.response.position() + fieldLen;

            switch (field)
            {
                case 'L':
                    this.linesAvailable = this.response.getInt();
                    break;
                default:
                    PipeClient.log.debug("Unknown info field: {}", field);
            }

            // Force the position forward, in case the field wasn't fully read.
            this.response.position(nextField);
        }
    }

    /**
     * Read a generic server response.
     */
    protected void readResponse()
    {
        this.response.clear();
        this.response.limit(Character.BYTES + Integer.BYTES);

        try
        {
            this.socket.read(this.response);
            this.response.flip();

            char header = this.response.getChar();
            if (header != 'Z')
            {
                PipeClient.log.error("Unexpected response prefix: {} (0x{})", header, Integer.toHexString(header));
                throw new IllegalStateException("Unexpected response prefix: " + header);
            }
            int responseSize = this.response.getInt();

            // Configure the buffer to read the rest of the response
            this.response.clear();
            this.response.limit(responseSize);

            PipeClient.log.debug("Reading request: Size = {}", responseSize);
            while(this.response.hasRemaining())
            {
                int bytes = this.socket.read(this.response);
                PipeClient.log.debug("Read chunk: {} -> {} of {}", bytes, this.response.position(), responseSize);
            }
            this.response.flip();
            PipeClient.log.debug("Response received: Size = {}", this.response.remaining());
        }
        catch (IOException e)
        {
            PipeClient.log.error("Error while reading response.", e);
        }
    }
}
