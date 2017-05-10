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

import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * @author jeff@darkware.org
 * @since 2017-05-09
 */
public class ProtocolTest
{
    @Test
    public void simple() throws InterruptedException
    {
        PipeService service = new PipeService(29999);
        Thread serviceThread = new Thread(service);
        serviceThread.start();

        PipeClient client = new PipeClient(29999);
        client.requestInfo();

        assertThat(client.getLinesAvailable()).isEqualTo(222);

        String testMessage = "This is a test message";
        service.setRecordSource(() -> testMessage);

        for (int i = 0; i < 40; i++)
        {
            String record = client.requestRecord();
            assertThat(record).isEqualTo(testMessage);
        }

        service.terminate();
    }
}
