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
package org.omnaest.pi.domain;

import com.hopding.jrpicam.enums.Exposure;

public class CameraSnapshotOptions
{
	private int			width;
	private int			height;
	private int			brightness;
	private Exposure	exposure;
	private int			timeout;
	private boolean		rawBayer;

	public CameraSnapshotOptions(int width, int height, int brightness, Exposure exposure, int timeout, boolean rawBayer)
	{
		super();
		this.width = width;
		this.height = height;
		this.brightness = brightness;
		this.exposure = exposure;
		this.timeout = timeout;
		this.rawBayer = rawBayer;
	}

	public CameraSnapshotOptions()
	{
		super();
	}

	public void setWidth(int width)
	{
		this.width = width;
	}

	public void setHeight(int height)
	{
		this.height = height;
	}

	public void setBrightness(int brightness)
	{
		this.brightness = brightness;
	}

	public void setExposure(Exposure exposure)
	{
		this.exposure = exposure;
	}

	public void setTimeout(int timeout)
	{
		this.timeout = timeout;
	}

	public void setRawBayer(boolean rawBayer)
	{
		this.rawBayer = rawBayer;
	}

	public int getWidth()
	{
		return this.width;
	}

	public int getHeight()
	{
		return this.height;
	}

	public int getBrightness()
	{
		return this.brightness;
	}

	public Exposure getExposure()
	{
		return this.exposure;
	}

	public int getTimeout()
	{
		return this.timeout;
	}

	public boolean isRawBayer()
	{
		return this.rawBayer;
	}

}
