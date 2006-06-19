/*
 * ADTPro - Apple Disk Transfer ProDOS
 * Copyright (C) 2006 by David Schmidt
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

package org.adtpro;

import java.io.*;

import org.adtpro.resources.Messages;
import org.adtpro.transport.SerialTransport;

import org.adtpro.gui.Gui;
import org.adtpro.utilities.UnsignedByte;

public class CommsThread extends Thread
{
  private boolean _shouldRun = true;

  private SerialTransport _transport;

  private Gui _parent;

  private static final byte ACK = 0x06, NAK = 0x15;

  private int[] CRCTABLE = new int[256];

  private int _maxRetries = 3;

  public CommsThread(Gui parent, SerialTransport transport)
  {
    // System.out.println("CommsThread constructor.");
    _parent = parent;
    _transport = transport;
  }

  public void run()
  {
    makeCrcTable();
    commandLoop();
  }

  public void commandLoop()
  {
    byte oneByte = (byte) 0x00;
    while (_shouldRun)
    {
      System.out.println("Command from Apple: "); //$NON-NLS-1$
      oneByte = waitForData();
      _parent.setProgressMaximum(0);
      System.out.println("Back from read... "); //$NON-NLS-1$
      switch (oneByte)
      {
        case (byte) 195: // CD
          _parent.setMainText(Messages.getString("CommsThread.2")); //$NON-NLS-1$
          _parent.setSecondaryText(""); //$NON-NLS-1$
          System.out.println("CD..."); //$NON-NLS-1$
          changeDirectory();
          _parent.setSecondaryText(_parent.getWorkingDirectory()); //$NON-NLS-1$
          break;
        case (byte) 196: // DIR
          _parent.setMainText(Messages.getString("CommsThread.1")); //$NON-NLS-1$
          _parent.setSecondaryText(_parent.getWorkingDirectory()); //$NON-NLS-1$
          System.out.println("Dir..."); //$NON-NLS-1$
          sendDirectory();
          break;
        case (byte) 208: // Put (Send)
          _parent.setMainText(Messages.getString("CommsThread.16")); //$NON-NLS-1$
          _parent.setSecondaryText(""); //$NON-NLS-1$
          System.out.println("Put/Send..."); //$NON-NLS-1$
          receiveDisk();
          break;
        case (byte) 199: // Get (Receive)
          _parent.setMainText(Messages.getString("CommsThread.11")); //$NON-NLS-1$
          _parent.setSecondaryText(""); //$NON-NLS-1$
          System.out.println("Get/Receive..."); //$NON-NLS-1$
          sendDisk();
          break;
        case (byte) 218: // Size
          _parent.setMainText(Messages.getString("CommsThread.14")); //$NON-NLS-1$
          _parent.setSecondaryText(""); //$NON-NLS-1$
          System.out.println("queryFileSize..."); //$NON-NLS-1$
          queryFileSize();
          break;
        case (byte) 210: // Receive (Legacy ADT style)
          _parent.setMainText(Messages.getString("CommsThread.0")); //$NON-NLS-1$
          _parent.setSecondaryText(""); //$NON-NLS-1$
          System.out.println("Legacy receive..."); //$NON-NLS-1$
          send140kDisk();
          break;
        case (byte) 211: // Send (Legacy ADT style)
          _parent.setMainText(Messages.getString("CommsThread.15")); //$NON-NLS-1$
          _parent.setSecondaryText(""); //$NON-NLS-1$
          System.out.println("Legacy send..."); //$NON-NLS-1$
          receive140kDisk();
          break;
        default:
          // System.out.println("not understood... received: " +
          // UnsignedByte.toString(oneByte));
          break;
      }
    }
  }

  public void sendDirectory()
  {
    int i, j, line;
    try
    {
      _transport.writeBytes("DIRECTORY OF "); //$NON-NLS-1$
      _transport.writeBytes(_parent.getWorkingDirectory());
      if (((_parent.getWorkingDirectory().length() + 13) % 40) != 0) _transport.writeByte('\r');
      // 40 dashes separates the wheat from the chaff
      _transport.writeBytes("----------------------------------------"); //$NON-NLS-1$

      File[] files = _parent.getFiles();
      if (files.length > 0)
      {
        i = 0;
        j = 0;
        line = (_parent.getWorkingDirectory().length() + 13) / 40 + 2;
        while (_shouldRun)
        {
          if ((i > 0) && (i + files[j].getName().length() > 40))
          {
            while (i++ < 40)
              _transport.writeByte(' ');
            i = 0;
            line++;
          }
          if (line > 20)
          {
            line = 0;
            _transport.writeByte('\0');
            _transport.writeByte('\1');
            if (waitForData() == '\0') break;
          }
          line += (files[j].getName().length() / 40);
          i += (files[j].getName().length() % 40);
          _transport.writeBytes(files[j].getName());
          j++;
          if (j + 1 > files.length) break;
          do
          {
            if (i == 40)
            {
              i = 0;
              line++;
              break;
            }
            _transport.writeByte(' ');
          }
          while ((++i % 14) > 0);
        }
      }
      else
        _transport.writeBytes("NO FILES"); //$NON-NLS-1$
      _transport.writeByte('\0');
      _transport.writeByte('\0');
    }
    catch (Throwable t1)
    {
      System.out.println("sendDirectory exception:"); //$NON-NLS-1$
      System.out.println(t1);
    }
  }

  public void queryFileSize()
  {
    long length;
    byte sizeLo = 0, sizeHi = 0, rc = (byte) 0xff;
    File requestedFile;
    String requestedFileName = receiveName();

    requestedFile = new File(requestedFileName);
    if (!requestedFile.canRead())
    {
      System.out.println("can't read file: " + requestedFileName + "; checking absolute path."); //$NON-NLS-1$ //$NON-NLS-2$
      requestedFileName = _parent.getWorkingDirectory() + File.separator + requestedFileName;
      requestedFile = new File(requestedFileName);
    }
    if (requestedFile.isFile())
    {
      System.out.println("queryFileSize found file " + requestedFileName); //$NON-NLS-1$
      length = requestedFile.length();
      if (length / 512 > 65535)
      {
        rc = 0x4a; // Unrecognized file format
      }
      else
        if ((length % 512) > 0)
        {
          System.out.println("queryFileSize not a ProDOS file size."); //$NON-NLS-1$
          rc = 0x4a;
        }
        else
        {
          sizeLo = UnsignedByte.loByte(length / 512);
          System.out.println("loByte of " + requestedFileName + " is: " + UnsignedByte.intValue(sizeLo)); //$NON-NLS-1$ //$NON-NLS-2$
          sizeHi = UnsignedByte.hiByte(length / 512);
          System.out.println("hiByte of " + requestedFileName + " is: " + UnsignedByte.intValue(sizeHi)); //$NON-NLS-1$ //$NON-NLS-2$
          rc = 0;
        }
    }
    else
    {
      System.out.println("can't read file: " + requestedFileName + "."); //$NON-NLS-1$ //$NON-NLS-2$
      rc = 0x46; // Unable to open file
    }
    System.out.println("queryFileSize lo:" + UnsignedByte.toString(sizeLo) + " hi:" + UnsignedByte.toString(sizeHi)); //$NON-NLS-1$ //$NON-NLS-2$
    _transport.writeByte(sizeLo);
    _transport.writeByte(sizeHi);
    _transport.writeByte(rc);
  }

  public void changeDirectory()
  {
    byte rc = 0x48;

    String requestedDirectory = receiveName();
    if (_shouldRun)
    {
      rc = _parent.setWorkingDirectory(requestedDirectory);
      _transport.writeByte(rc);
    }
  }

  public void receiveDisk()
  /* Main receive routine - Host <- Apple (Apple sends) */
  {
    System.out.print("Waiting for name..."); //$NON-NLS-1$
    String name = receiveName();
    System.out.println(" received name: " + name); //$NON-NLS-1$
    File f = new File(name);
    FileOutputStream fos = null;
    byte[] buffer = new byte[20480];
    int part, length;
    byte report, sizelo, sizehi;
    boolean receiveSuccess = false, isDosOrder = false;
    int halfBlock;
    int blocksDone = 0;

    // New ADT protcol - file size to expect
    System.out.print("Waiting for sizeLo..."); //$NON-NLS-1$
    sizelo = waitForData();
    System.out.println(" received sizeLo: " + UnsignedByte.intValue(sizelo)); //$NON-NLS-1$
    System.out.print("Waiting for sizeHi..."); //$NON-NLS-1$
    sizehi = waitForData();
    System.out.println(" received sizeHi: " + UnsignedByte.intValue(sizehi)); //$NON-NLS-1$
    length = UnsignedByte.intValue(sizelo, sizehi);

    try
    {
      // System.out.print("Attempting to open file stream...");
      fos = new FileOutputStream(f);
      // System.out.println(" opened file stream.");
    }
    catch (FileNotFoundException ex)
    {
      // System.out.println(" received a FileNotFoundException, as expected.");
      // We expect a file not found exception
    }
    try
    {
      if (fos != null)
      {
        // System.out.println("fos is not null.");
        // ready for transfer
        _transport.writeByte(0x00);
        while (waitForData() != ACK)
        {
          System.out.println("Hrm, not getting an ACK from the Apple..."); //$NON-NLS-1$
        }
        _parent.setProgressMaximum((int) length * 2); // Half-blocks
        int numParts = (int) length / 40;
        int remainder = (int) length % 40;
        for (part = 0; part < numParts; part++)
        {
          System.out.print("Receiving part " + (part + 1) + " of " + numParts + "; "); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
          for (halfBlock = 0; halfBlock < 80; halfBlock++)
          {
            receiveSuccess = receivePacket(buffer, halfBlock * 256);
            if (!receiveSuccess) break;
            blocksDone++;
            _parent.setProgressValue(blocksDone);
          }
          if (receiveSuccess)
          {
            if (isDosOrder) buffer = makeProDosOrder(buffer);
            System.out.println("Writing part " + (part + 1) + " of " + numParts + "."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            fos.write(buffer);
          }
        }
        if ((numParts == 0) && (remainder > 0))
        {
          // Seed the system in case we have a really short device
          System.out.println("Really short device, so we're setting receiveSuccess to true..."); //$NON-NLS-1$
          receiveSuccess = true;
        }
        if (receiveSuccess && (remainder > 0))
        {
          // System.out.println(" ... read " + charsRead + " chars.");
          System.out.println("Receiving remainder part."); //$NON-NLS-1$
          for (halfBlock = 0; halfBlock < (remainder * 2); halfBlock++)
          {
            receiveSuccess = receivePacket(buffer, halfBlock * 256);
            if (!receiveSuccess) break;
            blocksDone++;
            _parent.setProgressValue(blocksDone);
          }
          if (receiveSuccess)
          {
            System.out.println("Writing remainder " + remainder + " blocks."); //$NON-NLS-1$ //$NON-NLS-2$
            fos.write(buffer, 0, remainder * 512);
          }
          else
            System.out.println(" Didn't have luck receiving packets."); //$NON-NLS-1$
        }
        else
          System.out.println("Decided not to do a remainder."); //$NON-NLS-1$
        fos.close();
        if (receiveSuccess)
        {
          report = waitForData();
          if (report == 0x00)
          {
            _parent.setSecondaryText(Messages.getString("CommsThread.19"));
            System.out.println("Received disk image " + name + " successfully."); //$NON-NLS-1$ //$NON-NLS-2$
          }
          else
          {
            _parent.setSecondaryText(Messages.getString("CommsThread.20"));
            System.out.println("Received disk image " + name + " with errors."); //$NON-NLS-1$ //$NON-NLS-2$
          }
        }
        else
          _parent.setSecondaryText(Messages.getString("CommsThread.21"));
      }
      else
        System.out.println("fos is null!"); //$NON-NLS-1$

    }
    catch (IOException ex2)
    {
      _transport.writeByte(0x46); // New ADT protocol - unable to write file
    }
  }

  public void sendDisk()
  {
    int bufSize = 20480;
    byte[] buffer = new byte[bufSize]; // 40 (*512b) ProDOS blocks
    int rc = 0, halfBlock, charsRead, blocksDone = 0;
    FileInputStream fis = null;
    byte ack, report;
    /*
     * ADT PROTOCOL: receive the requested file name
     */
    String name = receiveName();
    File f = new File(name);
    long length;
    boolean isDosOrder = false, sendSuccess = false;

    if (!f.isFile())
    {
      f = new File(_parent.getWorkingDirectory() + File.separator + name);
      if (!f.isFile())
      {
        // New ADT protocol - can't open the file
        _transport.writeByte(0x46);
        rc = -1;
      }
    }
    if (rc == 0)
    {
      if (f.exists())
      {
        /*
         * ADT PROTOCOL: send trigger
         */
        // If the file exists, then...
        _transport.writeByte(0x00); // Tell Apple ][ we're ready to go

        /*
         * ADT PROTOCOL: receive acknowledgement for "previous" sector
         */
        ack = waitForData();
        System.out.println("Received initial reply from Apple: " + ack); //$NON-NLS-1$
        if (ack == 0x06)
        {
          try
          {
            fis = new FileInputStream(f);
            length = f.length() / 512; // measured in blocks, not bytes
            _parent.setProgressMaximum((int) length * 2); // Half-blocks
            int numParts = (int) length / 40;
            int remainder = (int) length % 40;
            System.out
                .println("Length is " + length + ".  There are " + (numParts + 1) + " buffers, and " + remainder + " blocks in the remainder."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            for (int part = 0; part < numParts; part++)
            {
              System.out.print("Reading part " + (part + 1) + " of " + numParts); //$NON-NLS-1$ //$NON-NLS-2$
              charsRead = fis.read(buffer);
              System.out.println(" ... read " + charsRead + " chars."); //$NON-NLS-1$ //$NON-NLS-2$
              if (isDosOrder) buffer = makeProDosOrder(buffer);
              for (halfBlock = 0; halfBlock < 80; halfBlock++)
              {
                sendSuccess = sendPacket(buffer, halfBlock * 256);
                if (sendSuccess)
                {
                  blocksDone++;
                  _parent.setProgressValue(blocksDone);
                }
                else
                  break;
              }
              if (!sendSuccess) break;
            }
            if (sendSuccess && (remainder > 0))
            {
              System.out.print("Reading remainder - " + remainder + " blocks"); //$NON-NLS-1$ //$NON-NLS-2$
              charsRead = fis.read(buffer, 0, remainder * 512);
              System.out.println(" ... read " + charsRead + " chars."); //$NON-NLS-1$ //$NON-NLS-2$
              for (halfBlock = 0; halfBlock < (remainder * 2); halfBlock++)
              {
                sendSuccess = sendPacket(buffer, halfBlock * 256);
                if (sendSuccess)
                {
                  blocksDone++;
                  _parent.setProgressValue(blocksDone);
                }
                else
                  break;
              }
            }
            fis.close();
          }
          catch (IOException ex)
          {}
          if (sendSuccess)
          {
            report = waitForData();
            if (report == 0x00)
            {
              _parent.setSecondaryText(Messages.getString("CommsThread.17"));
              System.out.println("Send disk image " + name + " successfully."); //$NON-NLS-1$ //$NON-NLS-2$
            }
            else
            {
              _parent.setSecondaryText(Messages.getString("CommsThread.18"));
              System.out.println("Send disk image " + name + " with " + report + " errors."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
          }
          else
            _parent.setSecondaryText(Messages.getString("CommsThread.21"));
        }
        else
          System.out.print("No ACK received from the Apple..."); //$NON-NLS-1$
      }
    }
  }

  /**
   * send140kDisk - legacy ADT protocol send 140k disk function
   * 
   * Note these images will be coming in and going out in DOS order.
   */
  public void send140kDisk()
  {
    /*
     * ADT PROTOCOL: receive the requested file name
     */
    String name = receiveName();
    byte ack;
    int bufSize = 28672;
    byte[] buffer = new byte[bufSize];
    File f = new File(name);
    int rc = 0;
    int sectorsDone = 0;
    FileInputStream fis = null;
    boolean sendSuccess = false;

    if (!f.isFile())
    {
      f = new File(_parent.getWorkingDirectory() + File.separator + name);
      if (!f.isFile())
      {
        _transport.writeByte(26); // can't open
        rc = -1;
      }
    }
    if (rc == 0)
    {
      long length = f.length();
      if (length != (long) 143360)
      {
        /*
         * ADT PROTOCOL: send error (message) number
         */
        System.out.println("Not a 140k image"); //$NON-NLS-1$
        _transport.writeByte(30); // not a 140k image
        rc = -1;
      }
      else
      {
        /*
         * ADT PROTOCOL: send trigger
         */
        // If the file exists, is pristine, etc., then...
        _transport.writeByte(0x00);
        try
        {
          /*
           * ADT PROTOCOL: receive acknowledgement for "previous" sector
           */
          ack = waitForData();
          if (ack == 0x06)
          {
            _parent.setProgressMaximum(560); // Sectors
            fis = new FileInputStream(f);
            for (int part = 0; part < 5; part++)
            {
              // System.out.print("Reading track " + track);
              // System.out.println(" ... read " + charsRead + " chars.");
              int charsRead = fis.read(buffer);
              for (int track = 0; track < 7; track++)
              {
                for (int sector = 15; sector >= 0; sector--)
                {
                  System.out.println("Sending track " + (track + (part * 7)) + " sector " + sector + "."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                  sendSuccess = sendPacket(buffer, (track * 4096 + sector * 256));
                  if (!sendSuccess) break;
                  sectorsDone++;
                  _parent.setProgressValue(sectorsDone);
                }
                if (!sendSuccess) break;
              }
              if (!sendSuccess) break;
            }
            fis.close();
            if (sendSuccess)
            {
              /*
               * ADT PROTOCOL: receive final error report
               */
              byte report = waitForData();
              if (report == 0x00)
              {
                _parent.setSecondaryText(Messages.getString("CommsThread.19"));
                System.out.println("Received disk image " + name + " successfully."); //$NON-NLS-1$ //$NON-NLS-2$
              }
              else
              {
                _parent.setSecondaryText(Messages.getString("CommsThread.20"));
                System.out.println("Received disk image " + name + " with errors."); //$NON-NLS-1$ //$NON-NLS-2$
              }
            }
            else
              _parent.setSecondaryText(Messages.getString("CommsThread.21"));
          }
          else
            System.out.print("No ACK received from the Apple."); //$NON-NLS-1$
        }
        catch (IOException ex)
        {
          System.out.print("Disk send aborted."); //$NON-NLS-1$
        }
      }
    }
  }

  public boolean sendPacket(byte[] buffer, int offset)
  {
    boolean rc = false;

    int byteCount, crc, ok = NAK, currentRetries = 0;
    byte data, prev, newprev;

    // System.out.println("sendPacket entry; offset "+offset+".");
    /*
     * for (byteCount = 0; byteCount < 256;byteCount++) { if (byteCount % 32 ==
     * 0) System.out.println("");
     * System.out.print(UnsignedByte.toString(buffer[byteCount]) + " "); }
     */
    do
    {
      // System.out.print(" top of sendPacket loop.");
      prev = 0;
      for (byteCount = 0; byteCount < 256;)
      {
        newprev = buffer[offset + byteCount];
        data = (byte) (UnsignedByte.intValue(newprev) - UnsignedByte.intValue(prev));
        prev = newprev;
        _transport.writeByte(data);
        if (UnsignedByte.intValue(data) > 0) byteCount++;
        else
        {
          while ((_shouldRun == true) && byteCount < 256 && buffer[offset + byteCount] == newprev)
          {
            byteCount++;
          }
          _transport.writeByte((byte) (byteCount & 0xFF)); // 256 becomes 0
        }
        if (!_shouldRun)
        {
          rc = false;
          break;
        }
      }
      if (_shouldRun)
      {
        crc = doCrc(buffer, offset, 256);
        _transport.writeByte((byte) (crc & 0xff));
        _transport.writeByte((byte) (((crc & 0xff00) >> 8) & 0xff));
        // System.out.println("");
        System.out.println("Locally calculated CRC: " + (crc & 0xffff)); //$NON-NLS-1$
        ok = waitForData();
        // System.out.println("ack from Apple: " + ok);
        if (ok == ACK) rc = true;
        else
          currentRetries++;
      }
    }
    while ((ok != ACK) && (_shouldRun == true) && (currentRetries < _maxRetries));

    return rc;
  }

  public void receive140kDisk()
  {
    String name = receiveName();
    File f = new File(name);
    FileOutputStream fos = null;
    byte[] buffer = new byte[28672];
    int i, part, track, sector;
    int sectorsDone = 0;
    byte report;
    boolean receiveSuccess = false;

    if (f.exists()) _transport.writeByte(0x1c); // File exists
    else
    {
      try
      {
        fos = new FileOutputStream(f);
      }
      catch (FileNotFoundException ex)
      {
        // We expect a file not found exception
      }
      if (fos != null)
      {
        for (i = 0; i < buffer.length; i++)
          buffer[i] = 0x00;
        try
        {
          for (i = 0; i < 7; i++)
            fos.write(buffer);
          fos.close();
          _transport.writeByte(0x00); // File is now ready
          fos = new FileOutputStream(f);
          while (waitForData() != ACK)
          {
            // TODO: What needs to happen here? Original ADT talked about
            // a bad header message...
            System.out.println("hrm, not getting an ACK from the Apple..."); //$NON-NLS-1$
          }
          _parent.setProgressMaximum(560); // sectors
          for (part = 0; part < 5; part++)
          {
            for (track = 0; track < 7; track++)
            {
              for (sector = 15; sector >= 0; sector--)
              {
                receiveSuccess = receivePacket(buffer, (track * 4096) + (sector * 256));
                System.out.println("Received track " + ((7 * part) + track) + ", sector " + (15 - sector) + ": " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    + (receiveSuccess ? "Success." : "Failure.")); //$NON-NLS-1$ //$NON-NLS-2$
                if (!receiveSuccess) break;
                sectorsDone++;
                _parent.setProgressValue(sectorsDone);
              }
            }
            fos.write(buffer);
          }
          fos.close();
          if (receiveSuccess)
          {
            report = waitForData();
            if (report == 0x00)
            {
              _parent.setSecondaryText(Messages.getString("CommsThread.19"));
              System.out.println("Received disk image " + name + " successfully."); //$NON-NLS-1$ //$NON-NLS-2$
            }
            else
            {
              _parent.setSecondaryText(Messages.getString("CommsThread.20"));
              System.out.println("Received disk image " + name + " with errors."); //$NON-NLS-1$ //$NON-NLS-2$
            }
          }
          else
            _parent.setSecondaryText(Messages.getString("CommsThread.21"));

        }
        catch (IOException ex2)
        {
          _transport.writeByte(0x1a); // Unable to write file
        }
      }
    }
  }

  public boolean receivePacket(byte[] buffer, int offset)
  // Receive a packet with RLE compression
  {
    int byteCount;
    int received_crc = -1, computed_crc = 0;
    byte data = 0x00, prev, crc1 = 0, crc2 = 0;
    boolean rc = false;

    // System.out.println("receivePacket entry; offset "+offset+".");
    do
    {
      // System.out.print(" top of receivePacket loop.");
      prev = 0;
      for (byteCount = 0; byteCount < 256;)
      {
        data = waitForData();
        if (UnsignedByte.intValue(data) > 0)
        {
          prev += UnsignedByte.intValue(data);
          // if (byteCount % 32 == 0) System.out.println("");
          buffer[offset + byteCount++] = prev;
          // System.out.print(UnsignedByte.toString(buffer[(offset + byteCount)
          // -1]) + " ");
        }
        else
        {
          data = waitForData();
          do
          {
            // if (byteCount % 32 == 0) System.out.println("");
            buffer[offset + byteCount++] = prev;
            // System.out.print(UnsignedByte.toString(buffer[offset + byteCount
            // - 1]) + " ");
          }
          while (_shouldRun && byteCount < 256 && byteCount != UnsignedByte.intValue(data));
        }
        if (!_shouldRun)
        {
          rc = false;
          break;
        }
      }
      if (_shouldRun)
      {
        // System.out.println("");
        // System.out.print("Receiving CRC bytes...");
        crc1 = waitForData();
        crc2 = waitForData();
        received_crc = UnsignedByte.intValue(crc1, crc2);
        computed_crc = doCrc(buffer, offset, 256);
        if (received_crc != computed_crc)
        {
          System.out.println("Incorrect CRC.  Computed: " + computed_crc + " Received: " + received_crc); //$NON-NLS-1$ //$NON-NLS-2$
          _transport.writeByte(NAK);
        }
        else
        {
          System.out.println("Correct CRC.  Computed: " + computed_crc + " Received: " + received_crc); //$NON-NLS-1$ //$NON-NLS-2$
          rc = true;
        }
      }
    }
    while ((received_crc != computed_crc) && (_shouldRun == true));
    if (_shouldRun) _transport.writeByte(ACK);
    // System.out.println("receivePacket exit.");

    return rc;
  }

  public byte waitForData()
  {
    byte oneByte = 0;
    boolean readYet = false;
    while ((readYet == false) && (_shouldRun == true))
    {
      // System.out.println("Top of waitForData while...");
      try
      {
        // System.out.println(" waitForData try...");
        if (_transport != null)
        {
          // System.out.println(" waitForData transport non-null...");
          oneByte = _transport.readByte();
          // System.out.println(" waitForData back from readByte()...");
          readYet = true;
        }
        else
        {
          // System.out.println(" waitForData else clause...");
          _shouldRun = false;
        }
        // System.out.println(" waitForData bottom of try...");
      }
      catch (IOException ex)
      {
        System.out.println(ex);
      }
      // System.out.println(" waitForData bottom of while...");
    }
    return oneByte;
  }

  public String receiveName()
  {
    byte oneByte;
    StringBuffer buf = new StringBuffer();

    for (int i = 0; i < 256; i++)
    {
      if (!_shouldRun) break;
      oneByte = waitForData();
      if (oneByte != (byte) 0x00)
      {
        buf.append((char) (UnsignedByte.intValue(oneByte) & 0x7f));
      }
      else
        break;
    }
    return new String(buf);
  }

  public int doCrc(byte[] buffer, int offset, int count)
  {
    /* Return the CRC of ptr[0]..ptr[count-1] */
    int crc = 0;
    for (int i = 0; i < count; i++)
    {
      crc = ((crc << 8) & 0xff00) ^ (CRCTABLE[(((crc & 0xff00) >> 8) ^ buffer[offset + i]) & 0xff] & 0xffff);
    }
    return crc;
  }

  void makeCrcTable()
  /* Fill the crctable[] array needed by doCrc */
  {
    int oneByte, bit;
    int crc;

    for (oneByte = 0; oneByte < 256; oneByte++)
    {
      crc = oneByte << 8;
      for (bit = 0; bit < 8; bit++)
      {
        crc = (((crc & 0x8000) != 0) ? (crc << 1) ^ 0x1021 : crc << 1);
      }
      CRCTABLE[oneByte] = crc;
    }
  }

  byte[] makeProDosOrder(byte[] inputBuf)
  {
    /*
     * This function will really only be necessary for 140k diskette images -
     * nobody else puts stuff in DOS order. So, our division by 4096 should
     * always have zero remainder.
     */
    byte[] outputBuf;
    /*
     * Mapping from DOS sectors to ProDOS half-tracks
     */
    int dosSectorMap[] =
    { 0, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 15 };
    outputBuf = new byte[inputBuf.length];
    // For each track... should be 35 of them
    for (int track = 0; track < inputBuf.length; track += 4096)
    {
      // Swizzle the sectors
      for (int sector = 0; sector < 16; sector++)
      {
        // Copy the sector bytes
        for (int i = 0; i < 256; i++)
        {
          outputBuf[track + (sector * 256) + i] = inputBuf[track + (dosSectorMap[sector] * 256) + i];
        }
      }
    }
    return outputBuf;
  }

  public void requestStop()
  {
    _shouldRun = false;
    try
    {
      _transport.close();
    }
    catch (Exception ex)
    {}
  }
}
