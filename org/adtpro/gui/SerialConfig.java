package org.adtpro.gui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.adtpro.resources.Messages;
import org.adtpro.transport.SerialTransport;
import org.adtpro.utilities.Log;
import org.adtpro.ADTProperties;

public class SerialConfig extends JDialog implements ActionListener
{
  /**
   * 
   */
  private static SerialConfig _theSingleton = null;

  private static final long serialVersionUID = 1L;

  private JLabel labelComPort;

  private JComboBox comboComPort;

  private JLabel labelSpeed;

  private JComboBox comboSpeed;

  private JCheckBox iicCheckBox;

  private org.adtpro.ADTProperties _properties = null;

  private int exitStatus = CANCEL;
  
  public JButton okButton = new JButton(Messages.getString("Gui.Ok"));
  public JButton cancelButton = new JButton(Messages.getString("Gui.Cancel"));
  
  public static final int CANCEL = 0, OK = 1;

  /**
   * 
   * Private constructor - use the <code>getSingleton</code> to instantiate.
   * 
   */
  private SerialConfig()
  {
    this.setTitle(Messages.getString("Gui.SerialConfig"));
    JPanel buttonPanel = new JPanel(new GridBagLayout());
    okButton.addActionListener(this);
    cancelButton.addActionListener(this);

    GridBagUtil.constrain(buttonPanel, okButton, 1, 1, // X, Y Coordinates
        1, 1, // Grid width, height
        GridBagConstraints.NONE, // Fill value
        GridBagConstraints.WEST, // Anchor value
        0.0, 0.0, // Weight X, Y
        5, 5, 5, 5); // Top, left, bottom, right insets
    GridBagUtil.constrain(buttonPanel, cancelButton, 2, 1, // X, Y Coordinates
        1, 1, // Grid width, height
        GridBagConstraints.NONE, // Fill value
        GridBagConstraints.WEST, // Anchor value
        0.0, 0.0, // Weight X, Y
        5, 5, 5, 5); // Top, left, bottom, right insets
    JPanel configPanel = new JPanel();
    configPanel.setLayout(new GridBagLayout());
    this.getContentPane().setLayout(new BorderLayout());    
    this.getContentPane().add(configPanel,BorderLayout.CENTER);
    this.getContentPane().add(buttonPanel,BorderLayout.SOUTH);
    Log.getSingleton();
    comboComPort = new JComboBox();
    try
    {
      Log.println(false, "SerialConfig Constructor about to attempt to instantiate rxtx library.");
      Log.print(true, Messages.getString("Gui.RXTX")); //$NON-NLS-1$
      String[] portNames = SerialTransport.getPortNames();
      for (int i = 0; i < portNames.length; i++)
      {
        String nextName = portNames[i];
        if (nextName == null) continue;
        if (!nextName.startsWith("LPT")) // Get rid of LPTx ports, since we're
                                         // not likely to run on parallel
                                         // hardware...
        comboComPort.addItem(nextName);
      }
    }
    catch (Throwable t)
    {
      Log.println(true, Messages.getString("Gui.NoRXTX")); //$NON-NLS-1$
    }
    Log.println(false, "SerialConfig Constructor completed instantiating rxtx library.");

    comboSpeed = new JComboBox();
    comboSpeed.addItem("9600"); //$NON-NLS-1$
    comboSpeed.addItem("19200"); //$NON-NLS-1$
    comboSpeed.addItem("115200"); //$NON-NLS-1$

    iicCheckBox = new JCheckBox(Messages.getString("Gui.IIc"));

    labelComPort = new JLabel(Messages.getString("Gui.Port"), SwingConstants.LEFT); //$NON-NLS-1$
    labelSpeed = new JLabel(Messages.getString("Gui.Speed"), SwingConstants.LEFT); //$NON-NLS-1$

    GridBagUtil.constrain(configPanel, labelComPort, 1, 1, // X, Y Coordinates
        1, 1, // Grid width, height
        GridBagConstraints.NONE, // Fill value
        GridBagConstraints.WEST, // Anchor value
        0.0, 0.0, // Weight X, Y
        5, 5, 0, 0); // Top, left, bottom, right insets
    GridBagUtil.constrain(configPanel, comboComPort, 1, 2, // X, Y Coordinates
        1, 1, // Grid width, height
        GridBagConstraints.HORIZONTAL, // Fill value
        GridBagConstraints.WEST, // Anchor value
        0.0, 0.0, // Weight X, Y
        0, 5, 5, 5); // Top, left, bottom, right insets
    GridBagUtil.constrain(configPanel, labelSpeed, 2, 1, // X, Y Coordinates
        1, 1, // Grid width, height
        GridBagConstraints.NONE, // Fill value
        GridBagConstraints.WEST, // Anchor value
        0.0, 0.0, // Weight X, Y
        5, 5, 0, 0); // Top, left, bottom, right insets
    GridBagUtil.constrain(configPanel, comboSpeed, 2, 2, // X, Y Coordinates
        1, 1, // Grid width, height
        GridBagConstraints.HORIZONTAL, // Fill value
        GridBagConstraints.WEST, // Anchor value
        1.0, 0.0, // Weight X, Y
        0, 0, 5, 5); // Top, left, bottom, right insets

    GridBagUtil.constrain(configPanel, iicCheckBox, 1, 3, // X, Y Coordinates
        2, 1, // Grid width, height
        GridBagConstraints.HORIZONTAL, // Fill value
        GridBagConstraints.WEST, // Anchor value
        1.0, 0.0, // Weight X, Y
        0, 0, 5, 5); // Top, left, bottom, right insets
    /*
    dialog.getContentPane().add(SerialConfig.getSingleton());
    SerialConfig.setProperties(_properties);
    dialog.pack();
    dialog.setBounds(FrameUtils.center(dialog.getSize()));
    dialog.show();
    */
    this.pack();
    this.setBounds(FrameUtils.center(this.getSize()));
    //this.show();
    Log.println(false,"SerialConfig Constructor exit.");
  }

  /**
   * Retrieve the single instance of this class.
   * 
   * @return Log
   */
  public static SerialConfig getSingleton()
  {
    if (null == _theSingleton)
      SerialConfig.allocateSingleton();
    return _theSingleton;
  }

  /**
   * getSingleton() is not synchronized, so we must check in this method to make
   * sure a concurrent getSingleton() didn't already allocate the Singleton
   * 
   * synchronized on a static method locks the class
   */
  private synchronized static void allocateSingleton()
  {
    if (null == _theSingleton) _theSingleton = new SerialConfig();
  }

  public static void setProperties(ADTProperties properties)
  {
    _theSingleton._properties = properties;
    _theSingleton.comboSpeed.setSelectedItem(properties.getProperty("CommPortSpeed", "115200")); //$NON-NLS-1$ //$NON-NLS-2$
    _theSingleton.iicCheckBox.setSelected(properties.getProperty("HardwareHandshaking", "false").compareTo("true") == 0); //$NON-NLS-1$ //$NON-NLS-2$
    _theSingleton.comboComPort.setSelectedItem(properties.getProperty("CommPort", "COM1")); //$NON-NLS-1$ //$NON-NLS-2$
  }

  public static String getPort()
  {
    return (String)_theSingleton.comboComPort.getSelectedItem();
  }

  public static String getSpeed()
  {
    return (String)_theSingleton.comboSpeed.getSelectedItem();
  }

  public static boolean getHardware()
  {
    return (boolean)_theSingleton.iicCheckBox.isSelected();
  }

  public static void showSingleton()
  {
    _theSingleton.setModal(true);
    _theSingleton.show();
  }

  /* OK action: */
  /*
  {
    _commsThread.setHardwareHandshaking(_iicMenuItem.isSelected());
  */

  public void actionPerformed(ActionEvent e)
  {
    Log.println(false,"SerialConfig.actionPerformed() entry, responding to "+e.getActionCommand());
    if (e.getSource() == okButton)
    {
      _properties.setProperty("CommPort", (String) comboComPort.getSelectedItem());
      _properties.setProperty("CommPortSpeed", (String) comboSpeed.getSelectedItem());
      if (iicCheckBox.isSelected())
        _properties.setProperty("HardwareHandshaking","true");
      else
        _properties.setProperty("HardwareHandshaking","false");
      _properties.save();
      _theSingleton.exitStatus = OK;
      this.setVisible(false);
    }
    else if (e.getSource() == cancelButton)
    {
      _theSingleton.comboSpeed.setSelectedItem(_properties.getProperty("CommPortSpeed", "115200")); //$NON-NLS-1$ //$NON-NLS-2$
      _theSingleton.iicCheckBox.setSelected(_properties.getProperty("HardwareHandshaking", "false").compareTo("true") == 0); //$NON-NLS-1$ //$NON-NLS-2$
      _theSingleton.comboComPort.setSelectedItem(_properties.getProperty("CommPort", "COM1")); //$NON-NLS-1$ //$NON-NLS-2$
      _theSingleton.exitStatus = CANCEL;
      this.setVisible(false);
    }
    Log.println(false,"SerialConfig.actionPerformed() exit.");
  }

  public int getExitStatus()
  {
    return _theSingleton.exitStatus;
  }
}
