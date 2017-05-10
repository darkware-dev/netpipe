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

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * The {@link EFProtocol} class is a simple static method library which abstracts the impressively
 * minimalistic Explicit Feed Protocol. This is a protocol designed to implement a one-directional input
 * pipe of character data with control messages flowing in the opposite directions. The control messages
 * serve to allow the receiving side to explicitly request for records or metadata to be delivered. This
 * lets the receiver connect to a data source which may supply far more data than the receiver desires or
 * is capable of handling at one time.
 * <p>
 * A good example of this situation would be an application which processes infinite data sets like sensor
 * readings. In cases where you don't want occasional, instantaneous reads rather than a time series of data,
 * EFP will let you set up a pipe that passes explicit requests for a data reading that are serviced immediately
 * without having the channel be flooded with records when there is no request for them.
 *
 * @author jeff@darkware.org
 * @since 2017-05-10
 */
public class EFProtocol
{
    public static InetAddress localAddress()
    {
        return InetAddress.getLoopbackAddress();
    }
}
