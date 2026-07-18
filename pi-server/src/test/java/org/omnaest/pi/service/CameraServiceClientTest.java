/*

	Copyright 2017 Danny Kunz

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.


*/
package org.omnaest.pi.service;

import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;

public class CameraServiceClientTest
{
    @Test
    @Ignore
    public void test() throws IOException, InterruptedException
    {
        //        int counter = 0;
        //        while (true)
        //        {
        //            try
        //            {
        //                RestTemplate restTemplate = new RestTemplate();
        //
        //                int width = 3280;
        //                int height = 2464;
        //                int brightness = 50;
        //                Exposure exposure = Exposure.NIGHT;
        //                int timeout = 2;
        //                boolean rawBayer = true;
        //                CameraSnapshot snapshot = restTemplate.postForEntity("http://192.168.1.246:8080/snapshot",
        //                                                                     new CameraSnapshotOptions(width, height, brightness, exposure, timeout, rawBayer),
        //                                                                     CameraSnapshot.class)
        //                                                      .getBody();
        //
        //                byte[] data = snapshot.getData();
        //
        //                BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        //
        //                FileUtils.forceMkdir(new File("F://pictures"));
        //                File outputfile = new File("F://pictures/image" + String.format("%08d", counter++) + ".png");
        //                ImageIO.write(image, "png", outputfile);
        //                Thread.sleep(10000);
        //            }
        //            catch (Exception e)
        //            {
        //                e.printStackTrace();
        //            }
        //        }

    }
}
