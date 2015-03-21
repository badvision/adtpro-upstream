/*
 * ADTPro - Apple Disk Transfer ProDOS
 * Copyright (C) 2006, 2009 by David Schmidt
 * david__schmidt at users.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by the 
 * Free Software Foundation; either version 2 of the License, or (at your 
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with this program; if not, write to the Free Software Foundation, Inc., 
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package org.adtpro.utilities;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

public class AppleDump extends Task
{

	public void execute() throws BuildException
	{
		String[] args = { "-infilename", _inFileName, "-outfilename", _outFileName, "-applename", _appleName, "-startaddrhex", _startAddrHex, "-numbyteswide", _numBytesString, "-decoration", _decoration, "-finalline", _finalLine };
		if (checkArgs(args) == true)
		{
			byte datum;
			FileInputStream fis = null;
			try
			{
				fis = new FileInputStream(_inFileName);
				PrintStream ps;
				try
				{
					ps = new PrintStream(new FileOutputStream(_outFileName));
				}
				catch (FileNotFoundException io)
				{
					throw new BuildException(io);
				}
				ps.print("\r\n");
				if ((_decoration.equalsIgnoreCase("yes")) || (_decoration.equalsIgnoreCase("begin")))
				{
					ps.print("CALL -151\r\n");
				}
				else
				{
					ps.print("\r\n");
				}
				int addr = Integer.parseInt(_startAddrHex, 16);
				int max = fis.available();
				int bpl = 24, bpls = 22;
				int lps = 7;
				int currentBpl = 0, currentLine = 1;
				String address = null;
				ps.print("25:0\r\n");
				for (int j = 0; j < max; j++)
				{
					if (currentBpl == 0)
					{
			            address = Integer.toHexString(addr);
			            ps.print(address.toUpperCase() + ":");
					}
					datum = (byte) fis.read();
					addr ++;
					ps.print(UnsignedByte.toShortString(datum));
					currentBpl ++;
					if (currentLine < lps)
					{
						System.err.println("currentLine < lps: "+currentLine+" < "+lps);
						// We're on an interior line
						if (currentBpl < bpl)
							ps.print(" ");
						else
						{
							ps.print("\r\n");
							currentBpl = 0;
							currentLine ++;
						}
					}
					else
					{
						// We're on an exterior line
						if (currentBpl < bpls)
						{
							ps.print(" ");
							System.err.println("currentLine == lps: "+currentLine+" == "+lps+" i="+currentBpl);
						}
						else
						{
							ps.print(" N 25:0\r\n");
							System.err.println("currentLine == lps: "+currentLine+" == "+lps+" Printing end of line.");
							currentBpl = 0;
							currentLine = 1;
						}
					}
				}
				if ((_decoration.equalsIgnoreCase("yes")) || (_decoration.equalsIgnoreCase("end")))
				{
					ps.print("\r\n");
					ps.print(_startAddrHex + "G\r\n");
				}
				if (_finalLine != null)
				{
					ps.print("\r\n");
					ps.print("\r\n");
					ps.print(_finalLine + "\r\n");
				}
				ps.close();
				fis.close();
			}
			catch (FileNotFoundException e)
			{
				throw new BuildException(e);
			}
			catch (IOException e)
			{
				throw new BuildException(e);
			}
		}
		else
		{
			throw new BuildException("AppleDump.execute(): All arguments must be provided.");
		}
	}

	public boolean checkArgs(String[] args)
	{
		boolean rc = false;
		//    if (args.length == 10)
		{
			if ((args[1] != null) && (args[3] != null) && (args[5] != null) && (args[7] != null) && (args[9] != null))
			{
				rc = true;
			}
		}
		return rc;
	}

	public void setInfilename(String filename)
	{
		_inFileName = filename;
	}

	public void setOutfilename(String filename)
	{
		_outFileName = filename;
	}

	public void setApplename(String applename)
	{
		_appleName = applename;
	}

	public void setStartaddrhex(String startaddrhex)
	{
		_startAddrHex = startaddrhex;
	}

	public void setNumbyteswide(String numbytesstring)
	{
		_numBytes = Integer.parseInt(numbytesstring);
	}

	public void setDecoration(String decoration)
	{
		_decoration = decoration;
	}

	public void setFinalLine(String finalLine)
	{
		_finalLine = finalLine;
	}

	String _inFileName = null;

	String _outFileName = null;

	String _appleName = null;

	String _startAddrHex = null;

	String _numBytesString = "32";

	String _decoration = "yes";

	String _finalLine = null;

	int _numBytes = 32;
}