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

import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.omnaest.pi.domain.CameraSnapshot;
import org.omnaest.pi.domain.CameraSnapshotOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hopding.jrpicam.RPiCamera;

@Service
public class CameraServicePI implements CameraService
{
	private static Logger LOG = LoggerFactory.getLogger(CameraServicePI.class);

	@Override
	public CameraSnapshot takeSnapshot(CameraSnapshotOptions options)
	{
		try
		{
			RPiCamera piCamera = new RPiCamera();
			piCamera.setWidth(options.getWidth())
					.setHeight(options.getHeight())
					.setBrightness(options.getBrightness())
					.setExposure(options.getExposure())
					.setTimeout(options.getTimeout())
					.setAddRawBayer(options.isRawBayer());

			BufferedImage image = piCamera.takeBufferedStill();
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ImageIO.write(image, "png", outputStream);
			byte[] data = outputStream.toByteArray();
			return new CameraSnapshot(data);
		} catch (Exception e)
		{
			LOG.error("Error taking snapshot", e);
			return new CameraSnapshot("Error: " + e.getMessage());
		}
	}
}
