/*
 ****************************************************************************
 * Copyright(c) 2014 NXP Semiconductors                                     *
 * All rights are reserved.                                                 *
 *                                                                          *
 * Software that is described herein is for illustrative purposes only.     *
 * This software is supplied "AS IS" without any warranties of any kind,    *
 * and NXP Semiconductors disclaims any and all warranties, express or      *
 * implied, including all implied warranties of merchantability,            *
 * fitness for a particular purpose and non-infringement of intellectual    *
 * property rights.  NXP Semiconductors assumes no responsibility           *
 * or liability for the use of the software, conveys no license or          *
 * rights under any patent, copyright, mask work right, or any other        *
 * intellectual property rights in or to any products. NXP Semiconductors   *
 * reserves the right to make changes in the software without notification. *
 * NXP Semiconductors also makes no representation or warranty that such    *
 * application will be suitable for the specified use without further       *
 * testing or modification.                                                 *
 *                                                                          *
 * Permission to use, copy, modify, and distribute this software and its    *
 * documentation is hereby granted, under NXP Semiconductors' relevant      *
 * copyrights in the software, without fee, provided that it is used in     *
 * conjunction with NXP Semiconductor products(UCODE I2C, NTAG I2C).        *
 * This  copyright, permission, and disclaimer notice must appear in all    *
 * copies of this code.                                                     *
 ****************************************************************************
 */
package com.nxp.nfc_demo.reader;

import java.io.IOException;

import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import org.ndeftools.EmptyRecord;
import org.ndeftools.Message;
import org.ndeftools.MimeRecord;
import org.ndeftools.Record;
import org.ndeftools.externaltype.AndroidApplicationRecord;
import org.ndeftools.wellknown.SmartPosterRecord;
import org.ndeftools.wellknown.TextRecord;
import org.ndeftools.wellknown.UriRecord;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.nxp.nfc_demo.activities.AuthActivity.AuthStatus;
import com.nxp.nfc_demo.activities.FlashMemoryActivity;
import com.nxp.nfc_demo.activities.MainActivity;
import com.nxp.nfc_demo.activities.RegisterConfigActivity;
import com.nxp.nfc_demo.activities.RegisterSessionActivity;
import com.nxp.nfc_demo.activities.VersionInfoActivity;
import com.nxp.nfc_demo.crypto.CRC32Calculator;
import com.nxp.nfc_demo.exceptions.CommandNotSupportedException;
import com.nxp.nfc_demo.exceptions.NotPlusTagException;
import com.nxp.nfc_demo.fragments.LedFragment;
import com.nxp.nfc_demo.fragments.NdefFragment;
import com.nxp.nfc_demo.fragments.SpeedTestFragment;
import com.nxp.nfc_demo.listeners.WriteEEPROMListener;
import com.nxp.nfc_demo.listeners.WriteSRAMListener;
import com.nxp.nfc_demo.reader.I2C_Enabled_Commands.Access_Offset;
import com.nxp.nfc_demo.reader.I2C_Enabled_Commands.CR_Offset;
import com.nxp.nfc_demo.reader.I2C_Enabled_Commands.NC_Reg_Func;
import com.nxp.nfc_demo.reader.I2C_Enabled_Commands.NS_Reg_Func;
import com.nxp.nfc_demo.reader.I2C_Enabled_Commands.PT_I2C_Offset;
import com.nxp.nfc_demo.reader.I2C_Enabled_Commands.R_W_Methods;
import com.nxp.nfc_demo.reader.I2C_Enabled_Commands.SR_Offset;
import com.nxp.nfc_demo.reader.Ntag_Get_Version.Prod;
import com.nxp.nfc_demo.reader.Ntag_I2C_Commands.Register;
import com.nxp.ntagi2cdemo.R;

/**
 * Class for the different Demos
 * 
 * @author NXP67729
 * 
 */
public class Ntag_I2C_Demo implements WriteEEPROMListener, WriteSRAMListener {
	I2C_Enabled_Commands reader;
	Activity main;
	Tag tag;
	String answer;

	// Taskreferences
	LedTask lTask;
	SRAMSpeedtestTask sramspeedtask;
	EEPROMSpeedtestTask eepromspeedtask;
	WriteEmptyNdefTask emptyNdeftask;
	WriteDefaultNdefTask defaultNdeftask;
	NDEFReadTask ndefreadtask;

	/**
	 * Constructor
	 * 
	 * @param tag
	 *            Tag with which the Demos should be performed
	 * @param main
	 *            MainActivity
	 */
	public Ntag_I2C_Demo(Tag tag, final Activity main, final byte[] passwd, final int authStatus) {
		try {
			if (tag == null) {
				this.main = null;
				this.tag = null;
				return;
			}
			this.main = main;
			this.tag = tag;

			reader = I2C_Enabled_Commands.get(tag);

			if (reader == null) {
				String message = "The Tag could not be identified or this NFC device does not support the NFC Forum commands needed to access this tag";
				String title = "Communication failed";
				showAlert(message, title);
			} else {
				reader.connect();
			}
			
			Ntag_Get_Version.Prod prod = reader.getProduct();
						
			if (!prod.equals(Ntag_Get_Version.Prod.Unknown)) {
				if (prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_1k_Plus) || prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_2k_Plus)) {
					// Auth status gets lost after resetting the demo when we obtain the product we are dealing with
					if(authStatus == AuthStatus.Authenticated.getValue())
						reader.authenticatePlus(passwd);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void showAlert(final String message, final String title) {
		main.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				new AlertDialog.Builder(main)
						.setMessage(message)
						.setTitle(title)
						.setPositiveButton("OK",
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int which) {

									}
								}).show();
			}
		});

	}

	/**
	 * Checks if the tag is still connected based on the previously detected reader
	 * 
	 * @return Boolean indicating tag connection
	 * 
	 */
	public boolean isConnected() {
		return reader.isConnected();
	}
	
	/**
	 * Checks if the tag is still connected based on the tag
	 * 
	 * @return Boolean indicating tag presence
	 * 
	 */
	public static boolean isTagPresent(Tag tag) {
		final Ndef ndef = Ndef.get(tag);
		if (ndef != null && ndef.getType().equals("android.ndef.unknown") == false) {
			try {
				ndef.connect();
				final boolean isConnected = ndef.isConnected();
				ndef.close();
				
				return isConnected;
			} catch (final IOException e) {
				e.printStackTrace();
				return false;
			}
		} else {
			final NfcA nfca = NfcA.get(tag);
			
			if (nfca != null) {
				try {
					nfca.connect();
					final boolean isConnected = nfca.isConnected();
					nfca.close();

					return isConnected;
				} catch (final IOException e) {
					e.printStackTrace();
					return false;
				}
			} else {
				final NfcB nfcb = NfcB.get(tag);
				
				if (nfcb != null) {
					try {
						nfcb.connect();
						final boolean isConnected = nfcb.isConnected();
						nfcb.close();

						return isConnected;
					} catch (final IOException e) {
						e.printStackTrace();
						return false;
					}
				} else {
					final NfcF nfcf = NfcF.get(tag);
					
					if (nfcf != null) {
						try {
							nfcf.connect();
							final boolean isConnected = nfcf.isConnected();
							nfcf.close();

							return isConnected;
						} catch (final IOException e) {
							e.printStackTrace();
							return false;
						}
					} else {
						final NfcV nfcv = NfcV.get(tag);
						
						if (nfcv != null) {
							try {
								nfcv.connect();
								final boolean isConnected = nfcv.isConnected();
								nfcv.close();
								
								return isConnected;
							} catch (final IOException e) {
								e.printStackTrace();
								return false;
							}
						} else {
							return false;
						}
					}
				}
			}
		}
	}

	public void FinishAllTasks() {
		LEDFinish();
		SRAMSpeedFinish();
		EEPROMSpeedFinish();
		WriteEmptyNdefFinish();
		NDEFReadFinish();
	}

	/**
	 * Checks if the demo is ready to be executed
	 * 
	 * @return Boolean indicating demo readiness
	 * 
	 */
	public boolean isReady() {
		if (tag != null && reader != null)
			return true;
		else
			return false;
	}

	public Prod getProduct() throws IOException {
		return reader.getProduct();
	}

	public void getBoardVersion() throws IOException, FormatException,
			CommandNotSupportedException {

		byte[] DataTx = new byte[reader.getSRAMSize()];
		byte[] DataRx = new byte[reader.getSRAMSize()];

		DataTx[reader.getSRAMSize() - 4] = 'V';

		if (!((reader.getSessionRegister(SR_Offset.NC_REG) & NC_Reg_Func.PTHRU_ON_OFF
				.getValue()) == NC_Reg_Func.PTHRU_ON_OFF.getValue())) {
			VersionInfoActivity.setBoardVersion("No Board attached");
			VersionInfoActivity.setBoardFWVersion("No Board attached");
			return;
		}

		try {
			reader.waitforI2Cread(100);
		} catch (TimeoutException e1) {
			e1.printStackTrace();

			VersionInfoActivity.setBoardVersion("No Board attached");
			VersionInfoActivity.setBoardFWVersion("No Board attached");
			return;
		}

		reader.writeSRAMBlock(DataTx, null);

		for (int i = 0; i < 300; i++) {
			if (((reader.getSessionRegister(SR_Offset.NS_REG) & NS_Reg_Func.SRAM_RF_READY
					.getValue()) == NS_Reg_Func.SRAM_RF_READY.getValue())) {
				break;
			}
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		if (!((reader.getSessionRegister(SR_Offset.NS_REG) & NS_Reg_Func.SRAM_RF_READY
				.getValue()) == NS_Reg_Func.SRAM_RF_READY.getValue())) {
			VersionInfoActivity.setBoardVersion("1.0");
			VersionInfoActivity.setBoardFWVersion("1.0");
			return;
		}

		DataRx = reader.readSRAMBlock();

		String Board_Version;
		String Board_FW_Version;
		// Check if Data was send, else it is a ExplorerBoard FW
		if (DataRx[12] == 0) {
			String Version = Integer.toHexString((DataRx[63] >> 4)
					& (byte) 0x0F)
					+ "." + Integer.toHexString(DataRx[63] & (byte) 0x0F);
			Board_Version = Version;
			Board_FW_Version = Version;
		} else {
			Board_Version = new String(DataRx, 12, 3);
			Board_FW_Version = new String(DataRx, 28, 3);
		}

		VersionInfoActivity.setBoardVersion(Board_Version);
		VersionInfoActivity.setBoardFWVersion(Board_FW_Version);
	}

	/**
	 * Resets the tag to its delivery values (including config registers)
	 * 
	 * @return Boolean indicating success or error
	 * 
	 */
	public int resetTagMemory() {
		int bytesWritten = 0;
		
		try {
			bytesWritten = reader.writeDeliveryNdef();
		} catch (Exception e) {
			e.printStackTrace();
			bytesWritten = -1;
		}
		
		if(bytesWritten == 0)
			showDemoNotSupportedAlert();
		else {
			byte NC_REG = (byte) 0x01; 
			byte LD_Reg = (byte) 0x00;
			byte SM_Reg = (byte) 0xF8;
			byte WD_LS_Reg = (byte) 0x48; 
			byte WD_MS_Reg = (byte) 0x08;
			byte I2C_CLOCK_STR = (byte) 0x01;
			
			// If we could reset the memory map, we should be able to write the config registers
			try {
				reader.writeConfigRegisters(NC_REG, LD_Reg, SM_Reg, WD_LS_Reg, WD_MS_Reg, I2C_CLOCK_STR);
			} catch (Exception e) {
//				Toast.makeText(main, "Error writing configuration registers", Toast.LENGTH_LONG).show();
				
				e.printStackTrace();
				bytesWritten = -1;
			}
			
			
			try {
				Ntag_Get_Version.Prod prod = reader.getProduct();
				
				if (prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_1k_Plus) ||
						prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_2k_Plus)) {
					byte AUTH0 = (byte) 0xFF;
					byte ACCESS = (byte) 0x00;
					byte PT_I2C = (byte) 0x00;
					
					reader.writeAuthRegisters(AUTH0, ACCESS, PT_I2C);
				}
			} catch (Exception e) {
//				Toast.makeText(main, "Error writing authentication registers", Toast.LENGTH_LONG).show();
				
				e.printStackTrace();
				bytesWritten = -1;
			}
		}
				
		return bytesWritten;
	}

	/**
	 * Not implemented in the Firmware side
	 * Code just in case for future releases
	 */
//	public boolean resetTag() {
//		byte[] DataTx = new byte[reader.getSRAMSize()];
//		byte[] DataRx = new byte[reader.getSRAMSize()];
//		long RegTimeOutStart = System.currentTimeMillis();
//
//		try {
//			do {
//
//				if (reader.checkPTwritePossible()) {
//					break;
//				}
//
//				long RegTimeOut = System.currentTimeMillis();
//				RegTimeOut = RegTimeOut - RegTimeOutStart;
//				if (!(RegTimeOut < 5000))
//					return false;
//
//			} while (true);
//
//			DataTx[reader.getSRAMSize() - 4] = 'R';
//
//			// wait to prevent that a RF communication is
//			// at the same time as �C I2C
//			Thread.sleep(10);
//
//			reader.waitforI2Cread(100);
//
//			reader.writeSRAMBlock(DataTx);
//
//			// wait to give the �C time to reset the Tag
//			Thread.sleep(50);
//
//			reader.waitforI2Cwrite(100);
//
//			DataRx = reader.readSRAMBlock();
//
//			if (DataRx[reader.getSRAMSize() - 4] == 'A'
//					&& DataRx[reader.getSRAMSize() - 3] == 'C'
//					&& DataRx[reader.getSRAMSize() - 2] == 'K')
//				return true;
//			else
//				return false;
//		} catch (Exception e) {
//			e.printStackTrace();
//			return false;
//		}
//	}

	/**
	 * Builds a String array for the Registers
	 * 
	 * @param register
	 *            Byte Array of the Registers
	 * @return String Array
	 * @throws IOException
	 * @throws FormatException
	 */
	private Ntag_I2C_Registers getRegister_Settings(byte[] register)
			throws IOException, FormatException {
		Ntag_I2C_Registers answer = new Ntag_I2C_Registers();

		Ntag_Get_Version.Prod prod = reader.getProduct();

		if (!prod.equals(Ntag_Get_Version.Prod.Unknown)) {
			if (prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_1k))
				answer.Manufacture = main.getString(R.string.ic_prod_ntagi2c_1k);
			else if (prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_2k))
				answer.Manufacture = main.getString(R.string.ic_prod_ntagi2c_2k);
			else if (prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_1k_Plus))
				answer.Manufacture = main.getString(R.string.ic_prod_ntagi2c_1k_Plus);
			else if (prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_2k_Plus))
				answer.Manufacture = main.getString(R.string.ic_prod_ntagi2c_2k_Plus);

			answer.Mem_size = prod.getMemsize();
		} else {
			answer.Manufacture = "";
			answer.Mem_size = 0;
		}

		byte NC_Reg = register[SR_Offset.NC_REG.getValue()];

		// check I2C_RST_ON_OFF
		if ((NC_Reg & NC_Reg_Func.I2C_RST_ON_OFF.getValue()) == NC_Reg_Func.I2C_RST_ON_OFF
				.getValue())
			answer.I2C_RST_ON_OFF = true;
		else
			answer.I2C_RST_ON_OFF = false;

		// check FD_OFF
		byte tmpReg = (byte) (NC_Reg & NC_Reg_Func.FD_OFF.getValue());
		if (tmpReg == (0x30))
			answer.FD_OFF = main.getString(R.string.FD_OFF_ON_11);
		if (tmpReg == (0x20))
			answer.FD_OFF = main.getString(R.string.FD_OFF_ON_10);
		if (tmpReg == (0x10))
			answer.FD_OFF = main.getString(R.string.FD_OFF_ON_01);
		if (tmpReg == (0x00))
			answer.FD_OFF = main.getString(R.string.FD_OFF_ON_00);

		// check FD_ON
		tmpReg = (byte) (NC_Reg & NC_Reg_Func.FD_ON.getValue());
		if (tmpReg == (0x0c))
			answer.FD_ON = main.getString(R.string.FD_OFF_ON_11);
		if (tmpReg == (0x08))
			answer.FD_ON = main.getString(R.string.FD_OFF_ON_10);
		if (tmpReg == (0x04))
			answer.FD_ON = main.getString(R.string.FD_OFF_ON_01);
		if (tmpReg == (0x00))
			answer.FD_ON = main.getString(R.string.FD_OFF_ON_00);

		// Last NDEF Page
		answer.LAST_NDEF_PAGE = (0x00000FF & register[SR_Offset.LAST_NDEF_PAGE
				.getValue()]);

		byte NS_Reg = register[SR_Offset.NS_REG.getValue()];

		// check NDEF_DATA_READ
		if ((NS_Reg & NS_Reg_Func.NDEF_DATA_READ.getValue()) == NS_Reg_Func.NDEF_DATA_READ
				.getValue())
			answer.NDEF_DATA_READ = true;
		else
			answer.NDEF_DATA_READ = false;

		// check RF_FIELD
		if ((NS_Reg & NS_Reg_Func.RF_FIELD_PRESENT.getValue()) == NS_Reg_Func.RF_FIELD_PRESENT
				.getValue())
			answer.RF_FIELD_PRESENT = true;
		else
			answer.RF_FIELD_PRESENT = false;

		// check PTHRU_ON_OFF
		if ((NC_Reg & (byte) NC_Reg_Func.PTHRU_ON_OFF.getValue()) == NC_Reg_Func.PTHRU_ON_OFF
				.getValue())
			answer.PTHRU_ON_OFF = true;
		else
			answer.PTHRU_ON_OFF = false;

		// check I2C_LOCKED
		if ((NS_Reg & NS_Reg_Func.I2C_LOCKED.getValue()) == NS_Reg_Func.I2C_LOCKED
				.getValue())
			answer.I2C_LOCKED = true;
		else
			answer.I2C_LOCKED = false;

		// check RF_LOCK
		if ((NS_Reg & NS_Reg_Func.RF_LOCKED.getValue()) == NS_Reg_Func.RF_LOCKED
				.getValue())
			answer.RF_LOCKED = true;
		else
			answer.RF_LOCKED = false;
		;

		// check check SRAM_I2C_Ready
		if ((NS_Reg & NS_Reg_Func.SRAM_I2C_READY.getValue()) == NS_Reg_Func.SRAM_I2C_READY
				.getValue())
			answer.SRAM_I2C_READY = true;
		else
			answer.SRAM_I2C_READY = false;

		// check SRAM_RF_READY
		tmpReg = (byte) (NS_Reg & NS_Reg_Func.SRAM_RF_READY.getValue());
		if ((NS_Reg & NS_Reg_Func.SRAM_RF_READY.getValue()) == NS_Reg_Func.SRAM_RF_READY
				.getValue())
			answer.SRAM_RF_READY = true;
		else
			answer.SRAM_RF_READY = false;

		// check PTHRU_DIR
		tmpReg = (byte) (NC_Reg & (byte) 0x01);
		if (tmpReg == (0x01))
			answer.PTHRU_DIR = true;
		else
			answer.PTHRU_DIR = false;

		// SM_Reg
		answer.SM_Reg = (0x00000FF & register[SR_Offset.SM_REG.getValue()]);

		// WD_LS_Reg
		answer.WD_LS_Reg = (0x00000FF & register[SR_Offset.WDT_LS.getValue()]);

		// WD_MS_Reg
		answer.WD_MS_Reg = (0x00000FF & register[SR_Offset.WDT_MS.getValue()]);

		// check SRAM_MIRROR_ON_OFF
		if ((NC_Reg & NC_Reg_Func.SRAM_MIRROR_ON_OFF.getValue()) == NC_Reg_Func.SRAM_MIRROR_ON_OFF
				.getValue())
			answer.SRAM_MIRROR_ON_OFF = true;
		else
			answer.SRAM_MIRROR_ON_OFF = false;

		// I2C_CLOCK_STR
		if (register[SR_Offset.I2C_CLOCK_STR.getValue()] == 1)
			answer.I2C_CLOCK_STR = true;
		else
			answer.I2C_CLOCK_STR = false;
	
		// read NDEF Message
		try {
			NdefMessage message = reader.readNDEF();
			String NDEFText = new String(message.getRecords()[0].getPayload(),
					"US-ASCII");
			NDEFText = NDEFText.subSequence(3, NDEFText.length()).toString();
			answer.NDEF_Message = NDEFText;
		} catch (Exception e) {
			e.printStackTrace();
			answer.NDEF_Message = main.getString(R.string.No_NDEF);
		}

		return answer;
	}
	
	/**
	 * Builds a String array for the NTAG I2C Plus Auth Register
	 * 
	 * @param register
	 *            Byte Array of the Registers
	 * @param pti2cRegister 
	 * @param accessRegister 
	 * @return String Array
	 * @throws IOException
	 * @throws FormatException
	 */
	private Ntag_I2C_Plus_Registers getPlusAuth_Settings(byte[] auth0register, byte[] accessRegister, byte[] pti2cRegister)
			throws IOException, FormatException {
		Ntag_I2C_Plus_Registers answerPlus = new Ntag_I2C_Plus_Registers();
		
		// Auth0 Register
		answerPlus.auth0 = (0x00000FF & auth0register[3]);
		
		// Access Register
		if ((0x0000080 & accessRegister[0]) >> Access_Offset.NFC_PROT.getValue() == 1)
			answerPlus.nfc_prot = true;
		else
			answerPlus.nfc_prot = false;
		
		if ((0x0000020 & accessRegister[0]) >> Access_Offset.NFC_DIS_SEC1.getValue() == 1)
			answerPlus.nfc_dis_sec1 = true;
		else
			answerPlus.nfc_dis_sec1 = false;
		
		answerPlus.authlim = (0x0000007 & accessRegister[0]);
		
		// PT I2C Register
		if ((0x0000008 & pti2cRegister[0]) >> PT_I2C_Offset.K2_PROT.getValue() == 1)
			answerPlus.k2_prot = true;
		else
			answerPlus.k2_prot = false;
		
		if ((0x0000004 & pti2cRegister[0]) >> PT_I2C_Offset.SRAM_PROT.getValue() == 1)
			answerPlus.sram_prot = true;
		else
			answerPlus.sram_prot = false;
		
		answerPlus.i2c_prot = (0x0000003 & pti2cRegister[0]);
		
		return answerPlus;
	}

	public void readSessionRegisters() throws CommandNotSupportedException {

		try {
			byte[] sessionRegisters = reader.getSessionRegisters();
			Ntag_I2C_Registers answer = getRegister_Settings(sessionRegisters);
			RegisterSessionActivity.SetAnswer(answer, main);

			Toast.makeText(main, "read tag successfully done",
					Toast.LENGTH_LONG).show();
		} catch (CommandNotSupportedException e) {
			e.printStackTrace();
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(main, "read tag failed", Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Reads/writes Config
	 * 
	 * @throws IOException
	 * @throws FormatException
	 */
	public void readWriteConfigRegister() throws CommandNotSupportedException {		
		// Check if the operation is read or write
		if (RegisterConfigActivity.isWriteChosen() == true) {
			try {
				Ntag_Get_Version.Prod prod = reader.getProduct();
				
				if((prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_1k_Plus) || prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_2k_Plus))
						&& (RegisterConfigActivity.getAuth0() & 0xFF) <= 0xEB)
					showAuthWriteConfigAlert();
				else
					writeConfigRegisters();	
			} catch (IOException e) {
				e.printStackTrace();
			}
		} // END if get chosen
		else {
			try {
				byte[] configRegisters = reader.getConfigRegisters();

				Ntag_I2C_Registers answer = getRegister_Settings(configRegisters);
				RegisterConfigActivity.setAnswer(answer, main);
				RegisterConfigActivity.setNC_Reg(configRegisters[CR_Offset.NC_REG.getValue()]);
				RegisterConfigActivity.setLD_Reg(configRegisters[CR_Offset.LAST_NDEF_PAGE.getValue()]);
				RegisterConfigActivity.setSM_Reg(configRegisters[CR_Offset.SM_REG.getValue()]);
				RegisterConfigActivity.setNS_Reg(configRegisters[CR_Offset.REG_LOCK.getValue()]);
				RegisterConfigActivity.setWD_LS_Reg(configRegisters[CR_Offset.WDT_LS.getValue()]);
				RegisterConfigActivity.setWD_MS_Reg(configRegisters[CR_Offset.WDT_MS.getValue()]);
				RegisterConfigActivity.setI2C_CLOCK_STR(configRegisters[CR_Offset.I2C_CLOCK_STR.getValue()]);
				
				Ntag_Get_Version.Prod prod = reader.getProduct();
				if (prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_1k_Plus) ||
						prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_2k_Plus)) {
					byte[] auth0Register = reader.getAuth0Register();
					byte[] accessRegister = reader.getAccessRegister();
					byte[] pti2cRegister = reader.getPTI2CRegister();
					
					Ntag_I2C_Plus_Registers answerPlus = getPlusAuth_Settings(auth0Register, accessRegister, pti2cRegister);
					RegisterConfigActivity.setAnswerPlus(answerPlus, main);
					RegisterConfigActivity.setPlus_Auth0_Reg(auth0Register[3]);
					RegisterConfigActivity.setPlus_Access_Reg(accessRegister[0]);
					RegisterConfigActivity.setPlus_Pti2c_Reg(pti2cRegister[0]);
				}
				
				Toast.makeText(main, "read tag successfully done",
						Toast.LENGTH_LONG).show();
			} catch (CommandNotSupportedException e) {
				e.printStackTrace();
				throw e;
			} catch (Exception e) {
				e.printStackTrace();
				Toast.makeText(main, "read tag failed", Toast.LENGTH_LONG)
						.show();
			}
		}
	}
	
	private void writeConfigRegisters() {
		try {
			byte NC_Reg = (byte) RegisterConfigActivity.getNC_Reg();
			byte LD_Reg = (byte) RegisterConfigActivity.getLD_Reg();
			byte SM_Reg = (byte) RegisterConfigActivity.getSM_Reg();
			byte WD_LS_Reg = (byte) RegisterConfigActivity.getWD_LS_Reg();
			byte WD_MS_Reg = (byte) RegisterConfigActivity.getWD_MS_Reg();
			byte I2C_CLOCK_STR = (byte) RegisterConfigActivity.getI2C_CLOCK_STR();
			reader.writeConfigRegisters(NC_Reg, LD_Reg, SM_Reg, WD_LS_Reg, WD_MS_Reg, I2C_CLOCK_STR);
			
			Ntag_Get_Version.Prod prod = reader.getProduct();
			if (prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_1k_Plus) ||
					prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_2k_Plus)) {
				byte AUTH0 = (byte) RegisterConfigActivity.getAuth0();
				byte ACCESS = (byte) RegisterConfigActivity.getAccess();
				byte PT_I2C = (byte) RegisterConfigActivity.getPTI2C();
				
				reader.writeAuthRegisters(AUTH0, ACCESS, PT_I2C);
			}

			Toast.makeText(main, "write tag successfully done",	Toast.LENGTH_LONG).show();
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(main, "write tag failed", Toast.LENGTH_LONG).show();
		}
	}

	private void showAuthWriteConfigAlert() {
		// Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(main);
        builder.setTitle(main.getString(R.string.Dialog_enable_auth_title));
        builder.setMessage(main.getString(R.string.Dialog_enable_auth_msg));
        
        builder.setPositiveButton("YES", new DialogInterface.OnClickListener() {
           public void onClick(DialogInterface dialog, int index) {
        	   // Write config registers
        	   writeConfigRegisters();	
        	   
               dialog.dismiss();
           }
       });
        
        builder.setNegativeButton("NO", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        
        // Create the AlertDialog object and return it
        builder.create();
        builder.show();
	}
	
	/**
	 * Reads the whole tag memory content
	 * 
	 * @return Boolean indicating success or error
	 */
	public byte[] readTagContent() {
		byte[] bytes = null;

		try {
			// The user memory and the first four pages are displayed
			int memSize = reader.getProduct().getMemsize() + 16;
						
			// Read all the pages using the fast read method
			bytes = reader.readEEPROM(0, memSize / reader.getBlockSize());
		} catch (IOException e) {
			e.printStackTrace();
		} catch (FormatException e) {
			e.printStackTrace();
		} catch (CommandNotSupportedException e) {
			e.printStackTrace();
			showDemoNotSupportedAlert();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return bytes;
	}
	
	private void showTagNotPlusAlert() {
		String message = main.getString(R.string.tag_not_supported);
		String title = main.getString(R.string.tag_not_supported_title);
		showAlert(message, title);
	}

	private void showDemoNotSupportedAlert() {
		String message = main.getString(R.string.demo_not_supported);
		String title = main.getString(R.string.demo_not_supported_title);
		showAlert(message, title);
	}

	/**
	 * Resets the whole tag memory content (Memory to 00000...)
	 * 
	 * @return Boolean indicating success or error
	 */
	public boolean resetTagContent() {
		boolean success = true;

		try {
			byte[] d = new byte[reader.getProduct().getMemsize()];
			reader.writeEEPROM(d, this);
		} catch (IOException e) {
			success = false;
			e.printStackTrace();
		} catch (FormatException e) {
			success = false;
			e.printStackTrace();
		} catch (CommandNotSupportedException e) {
			showDemoNotSupportedAlert();
			e.printStackTrace();
		}

		return success;
	}

	/**
	 * Performs the LED Demo
	 */
	public void LED() throws IOException, FormatException {
		// Reset UI
		LedFragment.setAnswer(main.getResources().getString(R.string.readConf));
		LedFragment.setTemperatureC(0);
		LedFragment.setTemperatureF(0);
		LedFragment.setVoltage(0);

		// The demo is executed in a separate thread to let the GUI run
		lTask = new LedTask();
		lTask.execute();
	}

	private class LedTask extends AsyncTask<Void, Byte[], Void> {
		final byte DeviceToTag = 1;
		final byte TagToDevice = 2;
		final byte No_transfer = 0;
		final byte invalid_transfer = 4;

		public Boolean exit = false;

		@Override
		protected Void doInBackground(Void... params) {
			byte[] DataTx = new byte[reader.getSRAMSize()];
			byte[] DataRx = new byte[reader.getSRAMSize()];
			byte[] Led;
			Byte[][] result; 

			// We have to make sure that the Pass-Through mode is activated
			long RegTimeOutStart = System.currentTimeMillis();
			boolean RTest = false;

			try {
				do {
					if (reader.checkPTwritePossible()) {
						break;
					}

					long RegTimeOut = System.currentTimeMillis();
					RegTimeOut = RegTimeOut - RegTimeOutStart;
					RTest = (RegTimeOut < 5000);

				} while (RTest);

				// Do as long as no Exception is thrown
				while (true) {

					// Get the color to be transmitted
					Led = LedFragment.getOption().getBytes();

					// Write the color into the block to be transmitted to the
					// NTAG board
					DataTx[reader.getSRAMSize() - 4] = Led[0];
					DataTx[reader.getSRAMSize() - 3] = Led[1];
					
					// Indicate whether Temperate and LCD are enabled or not
					if(LedFragment.isTempEnabled() == true)
						DataTx[reader.getSRAMSize() - 9] = 'E';
					else
						DataTx[reader.getSRAMSize() - 9] = 0x00;
					
					if(LedFragment.isLCDEnabled() == true)
						DataTx[reader.getSRAMSize() - 10] = 'E';
					else
						DataTx[reader.getSRAMSize() - 10] = 0x00;

					// NDEF Scrolling activation
					if(LedFragment.isScrollEnabled() == true)
						DataTx[reader.getSRAMSize() - 11] = 'E';
					else
						DataTx[reader.getSRAMSize() - 11] = 0x00;

					double tempC = LedFragment.getTemperatureC();
					double tempF = LedFragment.getTemperatureF();

					if (tempC > 0.0 && tempC < 75.0) {
						DecimalFormat df = new DecimalFormat("00.00");
						byte[] tempB = df.format(tempC).getBytes();

						// The '.' is omitted
						DataTx[reader.getSRAMSize() - 24] = tempB[0];
						DataTx[reader.getSRAMSize() - 23] = tempB[1];
						DataTx[reader.getSRAMSize() - 22] = tempB[3];
						DataTx[reader.getSRAMSize() - 21] = tempB[4];
					}

					if (tempF > 0.0 && tempF < 120.0) {
						DecimalFormat df = new DecimalFormat("000.00");
						byte[] tempB = df.format(tempF).getBytes();

						// The '.' is omitted
						DataTx[reader.getSRAMSize() - 19] = tempB[0];
						DataTx[reader.getSRAMSize() - 18] = tempB[1];
						DataTx[reader.getSRAMSize() - 17] = tempB[2];
						DataTx[reader.getSRAMSize() - 16] = tempB[4];
						DataTx[reader.getSRAMSize() - 15] = tempB[5];
					}

					double voltD = LedFragment.getVoltage();

					if (voltD > 0.0 && voltD < 5.0) {
						DecimalFormat df = new DecimalFormat("0.0");
						byte[] voltB = df.format(voltD).getBytes();

						// The '.' is omitted
						DataTx[reader.getSRAMSize() - 8] = voltB[0];
						DataTx[reader.getSRAMSize() - 7] = voltB[2];
					}
					
					displayTransferDir(DeviceToTag);
					
					// wait to prevent that a RF communication is
					// at the same time as �C I2C
					Thread.sleep(10);

					reader.waitforI2Cread(100);

					reader.writeSRAMBlock(DataTx, null);

					displayTransferDir(TagToDevice);
					// wait to prevent that a RF communication is
					// at the same time as �C I2C
					Thread.sleep(10);

					reader.waitforI2Cwrite(100);

					DataRx = reader.readSRAMBlock();

					if (exit) {
						// switch off the LED on the �C before terminating

						DataTx[reader.getSRAMSize() - 4] = Led[0];
						DataTx[reader.getSRAMSize() - 3] = '0';

						// wait to prevent that a RF communication is
						// at the same time as �C I2C
						Thread.sleep(10);
						reader.waitforI2Cread(100);

						reader.writeSRAMBlock(DataTx, null);

						// wait to prevent that a RF communication is
						// at the same time as �C I2C
						Thread.sleep(10);
						reader.waitforI2Cwrite(100);

						DataRx = reader.readSRAMBlock();

						cancel(true);
						return null;
					}
					
					// Convert byte[] to Byte[]
					Byte[] bytes = new Byte[DataRx.length];
					for (int i = 0; i < DataRx.length; i++) {
						bytes[i] = Byte.valueOf(DataRx[i]);
					}					
					result = new Byte[2][];
					result[0] = new Byte[1];
					result[0][0] = Byte.valueOf((byte) invalid_transfer);
					result[1] = bytes;
					
					// Write the result to the UI thread
					publishProgress(result);

				}
			} catch (FormatException e) {
				displayTransferDir(No_transfer);
				cancel(true);
				e.printStackTrace();
			} catch (IOException e) {
				displayTransferDir(No_transfer);
				cancel(true);
				e.printStackTrace();
			} catch (CommandNotSupportedException e) {
				showDemoNotSupportedAlert();
				displayTransferDir(No_transfer);
				cancel(true);
				e.printStackTrace();
			} catch (Exception e) {
				displayTransferDir(No_transfer);
				cancel(true);
				e.printStackTrace();
			}
			return null;
		}

		private void displayTransferDir(byte dir) {
			Byte[][] result;
			result = new Byte[2][];
			result[0] = new Byte[1];
			result[0][0] = Byte.valueOf((byte) dir);
			publishProgress(result);
		}

		@Override
		protected void onProgressUpdate(Byte[]... bytes) {

			if (bytes[0][0] == No_transfer) {
				LedFragment.setTransferDir("Transfer: non");
			} else if (bytes[0][0] == DeviceToTag) {
				LedFragment.setTransferDir("Transfer: Device --> Tag");
			} else if (bytes[0][0] == TagToDevice) {
				LedFragment.setTransferDir("Transfer: Device <-- Tag"); 
			} else {

				LedFragment.setButton(bytes[1][reader.getSRAMSize() - 2]);

				int Temp = 0;

				// Adding first "Byte"
				Temp = ((bytes[1][reader.getSRAMSize() - 5] >> 5) & 0x00000007);

				// Adding second Byte
				Temp |= ((bytes[1][reader.getSRAMSize() - 6] << 3) & 0x000007F8);

				// Voltage
				int Voltage = 0;
				Voltage = ((bytes[1][reader.getSRAMSize() - 7] << 8) & 0xff00)
						+ (bytes[1][reader.getSRAMSize() - 8] & 0x00ff);

				// if Temp is 0 no Temp sensor is on the �C
				if (Temp != 0) {
					// Set the values on the screen
					LedFragment.setAnswer("Temperature: "
							+ calcTempCelsius(Temp) + " �C / "
							+ calcTempFarenheit(Temp) + " �F"
							+ "\nEnergy Harvesting Voltage: "
							+ calcVoltage(Voltage));
				} else {
					LedFragment.setAnswer("Temperature: " + "Not available"
							+ "\nEnergy Harvesting Voltage: "
							+ calcVoltage(Voltage));
					LedFragment.setTemperatureC(0);
					LedFragment.setTemperatureF(0);
				}

				byte Version = bytes[1][reader.getSRAMSize() - 1];
				if (Version > 0) {
					int uppVersion = (Version >> 4) & 0x0f;
					int lowVersion = Version & 0x0f;

					MainActivity.boardFirmwareVersion = String
							.valueOf(uppVersion)
							+ "."
							+ String.valueOf(lowVersion);
				} else
					MainActivity.boardFirmwareVersion = "1.0";
			}
		}

	}

	/**
	 * Stops the LED Demo
	 */
	public void LEDFinish() {
		if (lTask != null && !lTask.isCancelled()) {
			lTask.exit = true;
			try {
				lTask.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
			lTask = null;
		}
	}

	/**
	 * Stops the NDEFRead Demo
	 */
	public void NDEFReadFinish() {
		if (ndefreadtask != null && !ndefreadtask.isCancelled()) {
			ndefreadtask.exit = true;
			try {
				ndefreadtask.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
			ndefreadtask = null;
			
			// Clean all the fields
			NdefFragment.resetNdefDemo();
		}
	}

	/**
	 * Performs the NDEF Demo
	 */
	public void NDEF() throws IOException {
		// Check if the operation is read or write
		if (NdefFragment.isWriteChosen() == true) {
			// NDEF Message to write in the tag
			NdefMessage msg = null;

			// Get the selected NDEF type since the creation of the NDEF Msg
			// will vary depending on the type
			if (NdefFragment.getNdefType().equalsIgnoreCase(
					main.getResources().getString(R.string.radio_text))) {
				msg = createNdefTextMessage(NdefFragment.getText());
			} else if (NdefFragment.getNdefType().equalsIgnoreCase(
					main.getResources().getString(R.string.radio_uri))) {
				msg = createNdefUriMessage(NdefFragment.getText());
			} else if (NdefFragment.getNdefType().equalsIgnoreCase(
					main.getResources().getString(R.string.radio_btpair))) {
				msg = createNdefBSSPMessage();
			} else if (NdefFragment.getNdefType().equalsIgnoreCase(
					main.getResources().getString(R.string.radio_sp))) {
				msg = createNdefSpMessage(NdefFragment.getSpTitle(), NdefFragment.getSpLink());
			}
			
			if(msg == null) {
				Toast.makeText(main, "Please add correct input values", Toast.LENGTH_LONG).show();
				NdefFragment.setAnswer(main.getResources().getString(R.string.format_error));
			}	

			if(NdefFragment.isAarRecordSelected() == true) {
				NdefRecord aarRecord = NdefRecord.createApplicationRecord(MainActivity.PACKAGE_NAME);
				
				NdefRecord records[] = msg.getRecords();
				records  = Arrays.copyOf(records, records.length + 1);
				records[records.length - 1] = aarRecord;
				
				msg = new NdefMessage(records);
			}
			
			try {
				long timeToWriteNdef = NDEFWrite(msg);

				// Not needed for now
				// Inform the MCU about the new NDEF message that has been
				// stored
				// N: reference for NDEF
//				if (!(reader instanceof MinimalNtag_I2C_Commands)
//						&& reader.checkPTwritePossible()) {
//					byte[] Data = new byte[64];
//					Data[reader.getSRAMSize() - 4] = 'N';
//					reader.writeSRAMBlock(Data, null);
//				}
				
				Toast.makeText(main, "write tag successfully done", Toast.LENGTH_LONG).show();
				NdefFragment.setAnswer("Tag successfully written");
				
				int bytes = msg.toByteArray().length;
				String Message = "";
				
				// Transmission Results
				Message = Message.concat("Speed (" + bytes + " Byte / "
						+ timeToWriteNdef + " ms): "
						+ String.format("%.0f", bytes / (timeToWriteNdef / 1000.0))
						+ " Bytes/s");
				
				NdefFragment.setDatarate(Message);
			} catch (Exception e) {
				Toast.makeText(main, "write tag failed", Toast.LENGTH_LONG).show();
				NdefFragment.setDatarate("Error writing NDEF");
				e.printStackTrace();
			}
		} else {
			ndefreadtask = new NDEFReadTask();
			ndefreadtask.execute();
		}
	}

	private class NDEFReadTask extends AsyncTask<Void, String, Void> {

		public Boolean exit = false;

		@Override
		protected Void doInBackground(Void... params) {
			try {
				while (true) {

					// cancle task if requested
					if (exit) {
						cancel(true);
						return null;
					}
					
					long RegTimeOutStart = System.currentTimeMillis();

					// Get the message Type
					NdefMessage msg = reader.readNDEF();
					
					// NDEF Reading time statistics
					long timeToReadNdef = System.currentTimeMillis() - RegTimeOutStart;
					
					Message highLevelMsg = new Message(msg);

					// Get the message type in order to deal with it
					// appropriately
					String type = "none";
					if (!highLevelMsg.isEmpty())
						type = highLevelMsg.get(0).getClass().getSimpleName();

					String message = "";
					int num_record = 1;

					for (Record rec : highLevelMsg) {
						if (rec instanceof EmptyRecord)
							message += "#" + num_record++ + ": "
									+ "EmptyRecord\n";
						else if (rec instanceof SmartPosterRecord) {
							message += "#" + num_record + " "
									+ rec.getClass().getSimpleName() + ":\n";
							SmartPosterRecord smp = (SmartPosterRecord) rec;
							message += "#" + num_record + ".1 "
									+ smp.getTitle().getClass().getSimpleName()
									+ ":\n" + smp.getTitle().getText() + "\n";
							message += "#" + num_record + ".2 "
									+ smp.getUri().getClass().getSimpleName()
									+ ":\n" + smp.getUri().getUri().getHost()
									+ smp.getUri().getUri().getPath() + "\n\n";
							num_record++;
						} else if (rec instanceof TextRecord)
							message += "#" + num_record++ + " "
									+ rec.getClass().getSimpleName() + ":\n"
									+ ((TextRecord) rec).getText() + "\n\n";
						else if (rec instanceof AndroidApplicationRecord)
							message += "#"
									+ num_record++
									+ " "
									+ rec.getClass().getSimpleName()
									+ ":\n"
									+ ((AndroidApplicationRecord) rec)
											.getPackageName() + "\n\n";
						else if (rec instanceof UriRecord)
							message += "#" + num_record++ + " "
									+ rec.getClass().getSimpleName() + ":\n"
									+ ((UriRecord) rec).getUri().getHost()
									+ ((UriRecord) rec).getUri().getPath()
									+ "\n\n";
						else if (rec instanceof MimeRecord)
							message += "#" + num_record++ + " "
									+ rec.getClass().getSimpleName() + ":\n"
									+ ((MimeRecord) rec).getMimeType() + "\n\n";
						else
							message += "#" + num_record++ + " "
									+ rec.getClass().getSimpleName() + "\n\n";
					}
					
					int bytes = msg.toByteArray().length;
					String readTimeMessage = "";
					
					// Transmission Results
					readTimeMessage = readTimeMessage.concat("Speed (" + bytes + " Byte / "
							+ timeToReadNdef + " ms): "
							+ String.format("%.0f", bytes / (timeToReadNdef / 1000.0))
							+ " Bytes/s");

					// Put the message content on the screen
					publishProgress(type, message, readTimeMessage);

					// sleep 500ms, but check if task was cancelled
					for (int i = 0; i < 5; i++) {
						// cancle task if requested
						if (exit || NdefFragment.isNdefReadLoopSelected() == false) {
							cancel(true);
							return null;
						}
						Thread.sleep(100);
					}
					
					// Don't let the thread repeat
					Thread.sleep(100);
				}
			} catch (CommandNotSupportedException e) {
				e.printStackTrace();
				showAlert(
						"The NDEF Message is to long to read from an NTAG I2C 2K with this Nfc Device",
						"Demo not supported");
			} catch (Exception e) {
				e.printStackTrace();
			}

			return null;
		}

		@Override
		protected void onProgressUpdate(String... progress) {
			NdefFragment.setNdefType(progress[0]);
			NdefFragment.setNdefMessage(progress[1]);
			NdefFragment.setDatarate(progress[2]);
		}

		@Override
		protected void onPostExecute(Void bla) {
			if (!exit)
				Toast.makeText(main, main.getString(R.string.Tag_lost),
						Toast.LENGTH_LONG).show();
			
			NdefFragment.resetNdefDemo();
		}
	}
	
	/**
	 * Performs the flashing of a new bin file from the NFC Device
	 * 
	 * @param bytes_to_flash
	 *            Byte Array containing the new firmware to be flashed
	 * @param dialog 
	 *            ProgressDialog to update as the firmware is flashed
	 * @return Boolean operation result
	 * @throws IOException
	 * @throws FormatException
	 */
	public Boolean Flash(byte[] bytes_to_flash) {
		int sectorSize = 4096;
		
		byte[] Data = null;
		byte[] Flash_Data = null;

		try {
			int length = bytes_to_flash.length;
			int flashes = length / sectorSize + (length % sectorSize == 0 ? 0 : 1);
			int Blocks = (int) Math.ceil(length	/ (float) reader.getSRAMSize());

			// Set the number of writings
			FlashMemoryActivity.setFLashDialogMax(Blocks);
			
			for (int i = 0; i < flashes; i++) {
				int flash_addr = 0x4000 + i * sectorSize;
				int flash_length = 0;
				
				if (length - (i + 1) * sectorSize < 0) {
					flash_length = roundUp(length % sectorSize);
					Flash_Data = new byte[flash_length];
					
					Arrays.fill(Flash_Data, (byte) 0);
					System.arraycopy(bytes_to_flash, i * sectorSize, Flash_Data, 0, length % sectorSize);
				} else {
					flash_length = sectorSize;
					Flash_Data = new byte[flash_length];
					
					System.arraycopy(bytes_to_flash, i * sectorSize, Flash_Data, 0, sectorSize);
				}
				
				Data = new byte[reader.getSRAMSize()];
				Data[reader.getSRAMSize() - 4] = 'F';
				Data[reader.getSRAMSize() - 3] = 'P';
				
				Data[reader.getSRAMSize() - 8] = (byte) (flash_length >> 24 & 0xFF);
				Data[reader.getSRAMSize() - 7] = (byte) (flash_length >> 16 & 0xFF);
				Data[reader.getSRAMSize() - 6] = (byte) (flash_length >> 8 & 0xFF);
				Data[reader.getSRAMSize() - 5] = (byte) (flash_length & 0xFF);

				Data[reader.getSRAMSize() - 12] = (byte) (flash_addr >> 24 & 0xFF);
				Data[reader.getSRAMSize() - 11] = (byte) (flash_addr >> 16 & 0xFF);
				Data[reader.getSRAMSize() - 10] = (byte) (flash_addr >> 8 & 0xFF);
				Data[reader.getSRAMSize() - 9] = (byte) (flash_addr & 0xFF);
				
				Log.d("FLASH", "Flashing to start");
				reader.writeSRAMBlock(Data, null);
				Log.d("FLASH", "Start Block write " + (i + 1) + " out of " + flashes);
				
				reader.waitforI2Cread(100);
				
				Log.d("FLASH", "Starting Block writing");
				reader.writeSRAM(Flash_Data, R_W_Methods.Fast_Mode, this);
				Log.d("FLASH", "All Blocks written");
				
				reader.waitforI2Cwrite(500);
				Thread.sleep(500);
				
				Log.d("FLASH", "Wait finished");
				byte[] response = reader.readSRAMBlock();
				Log.d("FLASH", "Block read");
				
				if (response[reader.getSRAMSize() - 4] != 'A' || response[reader.getSRAMSize() - 3] != 'C' || response[reader.getSRAMSize() - 2] != 'K') {
					Log.d("FLASH", "was nak");
					return false;
				}
								
				Log.d("FLASH", "was ack");
			}
			
			Log.d("FLASH", "Flash completed");
			
			Data = new byte[reader.getSRAMSize()];
			Data[reader.getSRAMSize() - 4] = 'F';
			Data[reader.getSRAMSize() - 3] = 'S';
			
			reader.writeSRAMBlock(Data, null);
			
			// Wait for the I2C to be ready
			reader.waitforI2Cread(100);
			
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (FormatException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		} catch (CommandNotSupportedException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		Log.d("FLASH", "Flash returned");
		
		Data = new byte[reader.getSRAMSize()];
		Data[reader.getSRAMSize() - 4] = 'F';
		Data[reader.getSRAMSize() - 3] = 'F';
		
		try {
			reader.writeSRAMBlock(Data, null);
			
			// Wait for the I2C to be ready
			reader.waitforI2Cread(100);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (FormatException e) {
			e.printStackTrace();
		} catch (CommandNotSupportedException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		} 
		
		return false;
	}
	
	/**
	 * Retrieves the auth status of the tag
	 * 
	 * @return int current auth status
	 * @throws IOException
	 */
	public int ObtainAuthStatus() {
		try {
			Ntag_Get_Version.Prod prod = reader.getProduct();
			
			if (!prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_1k_Plus) &&
					!prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_2k_Plus)) {
				return AuthStatus.Disabled.getValue();
			} else {
				return reader.getProtectionPlus();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} 
	
		return AuthStatus.Disabled.getValue();
	}
	
	/**
	 * Performs the authentication operation on NTAG I2C Plus
	 * 
	 * @param pwd
	 *            Byte Array containing the password
	 * @param authStatus 
	 * 			  Current Authentication Status
	 * @return Boolean operation result
	 * @throws IOException
	 * @throws FormatException
	 */
	public Boolean Auth(byte[] pwd, int authStatus) {
		try {
			if(authStatus == AuthStatus.Unprotected.getValue())
				reader.protectPlus(pwd, Register.Capability_Container.getValue());
			else if(authStatus == AuthStatus.Authenticated.getValue())
				reader.unprotectPlus();
			else if(authStatus == AuthStatus.Protected_W.getValue()
					|| authStatus == AuthStatus.Protected_RW.getValue()
					|| authStatus == AuthStatus.Protected_W_SRAM.getValue()
					|| authStatus == AuthStatus.Protected_RW_SRAM.getValue()) {
				byte[] pack = reader.authenticatePlus(pwd);
				if(pack.length < 2)
					return false;
			}

			return true;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (FormatException e) {
			e.printStackTrace();
		} catch (NotPlusTagException e) {
			showTagNotPlusAlert();
			e.printStackTrace();
		} 
		
		return false;
	}
	
	/***
	 * Helper method to adjusts the number of bytes to be sent during the last flashing sector
	 * 
	 * @param num to round up
	 * @return number roundep up
	 */
	int roundUp(int num) {
		if(num <= 256)
			return 256;
		else if(num > 256 && num <= 512)
			return 512;
		else if(num > 512 && num <= 1024)
			return 1024;
		else
			return 4096;
	}

	/**
	 * Performs the SRAM Speedtest
	 * 
	 * @throws IOException
	 * @throws FormatException
	 */
	public void SRAMSpeedtest() throws IOException, FormatException {
		sramspeedtask = new SRAMSpeedtestTask();
		sramspeedtask.execute();

	}

	private class SRAMSpeedtestTask extends AsyncTask<Void, String, Void> {

		public Boolean exit = false;

		byte[] Data;
		int chMultiplier;
		R_W_Methods method;
		long Reader_Tag_time;
		long Tag_Reade_time;
		boolean isValidRxData = false;
		boolean isValidTxData = false;
		boolean isValidFirmware = false;

		@Override
		protected void onPreExecute() {
			SpeedTestFragment.setAnswer("SRAM Speedtest");
			
			// getting Block multiplier
			String blockMulti = SpeedTestFragment.getrf_ndef_value_charmulti();

			chMultiplier = 1;
			int chMultiLength = blockMulti.length();

			if (chMultiLength == 0) {
				chMultiplier = 1;
			} else {
				chMultiplier = Integer.parseInt(blockMulti);
			}

			// get Read Method
			if (SpeedTestFragment.getReadOptions().equalsIgnoreCase(
					main.getString(R.string.rf_readOptions_fast_mode))) {
				method = R_W_Methods.Fast_Mode;
			} else if (SpeedTestFragment.getReadOptions().equalsIgnoreCase(
					main.getString(R.string.rf_readOptions_polling_mode))) {
				method = R_W_Methods.Polling_Mode;
				// The minimalNtag_I2C_Commands does not use the sector_select,
				// so it cannot access the registers
				if (reader instanceof MinimalNtag_I2C_Commands) {

					new AlertDialog.Builder(main)
							.setMessage(
									"This NFC device does not support the NFC Forum commands needed to use the polling mode")
							.setTitle("Command not supported")
							.setPositiveButton("OK",
									new DialogInterface.OnClickListener() {
										@Override
										public void onClick(
												DialogInterface dialog,
												int which) {

										}
									}).show();

					exit = true;
				}
			} else {
				method = R_W_Methods.Error;
			}

		}

		@Override
		protected Void doInBackground(Void... params) {

			// cancle task if requested
			if (exit) {
				cancel(true);
				return null;
			}

			try {
				Ntag_Get_Version.Prod prod = reader.getProduct();
				
				// With some old NFC Controllers The NTAG I2C Plus might lose the authentication when coming from writing/reading SRAM
				// so it is better to reauthenticate just in case
				if (prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_1k_Plus) || prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_2k_Plus)) {
					if(MainActivity.getAuthStatus() == AuthStatus.Authenticated.getValue())
						reader.authenticatePlus(MainActivity.getPassword());
				}
				
				long RegTimeOutStart = System.currentTimeMillis();

				while (true) {
					if (reader.checkPTwritePossible()) {
						publishProgress("Pass Through Mode is ON");
						break;
					} else {
						publishProgress("Pass Through Mode is OFF");
					}
					if (((System.currentTimeMillis() - RegTimeOutStart) > 500)) {
						publishProgress("Pass Through Mode was OFF, ERROR time out");
						cancel(true);
						return null;
					}
				}
				
				Data = new byte[reader.getSRAMSize()];
				Data[reader.getSRAMSize() - 4] = 'S';
				reader.writeSRAMBlock(Data, null);

				// ///////////////
				// Begin to transmit Data (RF -> I2C)
				// ///////////////

				// create array
				Data = new byte[chMultiplier * reader.getSRAMSize()];

				// adding the finish text
				int last_Block = (Data.length - reader.getSRAMSize());
				byte[] fin = "finish_S_".getBytes();
				System.arraycopy(fin, 0, Data, last_Block, 9);

				long currTime;

				// Append a CRC32 in the last block for the whole message
				Data = appendCRC32(Data);

				// write to SRAM
				currTime = System.currentTimeMillis();

				// Modified reader.writeSRAM() to also get update to the UI
				// -----
				reader.waitforI2Cread(100);
				int Blocks = (int) Math.ceil(Data.length / (float) reader.getSRAMSize());
				for (int i = 0; i < Blocks; i++) {
					byte[] dataBlock = new byte[reader.getSRAMSize()];
					if (Data.length - (i + 1) * reader.getSRAMSize() < 0) {					
						Arrays.fill(dataBlock, (byte) 0);
						System.arraycopy(Data, i * reader.getSRAMSize(), dataBlock, 0, Data.length % reader.getSRAMSize());
					} else {
						System.arraycopy(Data, i * reader.getSRAMSize(), dataBlock, 0, reader.getSRAMSize());
					}
					
					reader.writeSRAMBlock(dataBlock, null);

					// cancle task if requested
					if (exit) {
						// wait for the error detection on the �C
						Thread.sleep(550);
						cancel(true);
						return null;
					}

					// Publish the Progress
					publishProgress(((i + 1) * reader.SRAMSize)
							+ " Bytes written");

					if (method == R_W_Methods.Polling_Mode) {
						reader.waitforI2Cread(100);
					} else {
						try {
							// else wait
							Thread.sleep(6);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
				// -----

				Reader_Tag_time = System.currentTimeMillis() - currTime;

				// force cached sector back to Sector0 to prevent from
				// hidden change of sector due to presents detection
				// reader.readEEPROM(0x04, 0x04);

				Thread.sleep(10);

				String currentDatarateCallback = SpeedTestFragment
						.getDatarateCallback();
				// reading SRAM
				currTime = System.currentTimeMillis();

				// ///////////////
				// Begin to Read Data (I2C -> RF)
				// ///////////////
				
				// Modified reader.readSRAM() to also get update to the UI
				// -----
				byte[] response = new byte[0];
				byte[] temp;

				for (int i = 0; i < chMultiplier; i++) {
					if (method == R_W_Methods.Polling_Mode) {
						reader.waitforI2Cwrite(100);
					} else {
						try {
							// else wait
							Thread.sleep(6);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					temp = reader.readSRAMBlock();

					// cancle task if requested
					if (exit) {
						// wait for the error detection on the �C
						Thread.sleep(550);
						cancel(true);
						return null;
					}

					// Publish the Progress
					publishProgress(currentDatarateCallback + "\n"
							+ ((i + 1) * reader.SRAMSize) + " Bytes read");

					// concat read block to the full response
					response = concat(response, temp);
				}
				// -----

				// if a error is detected [reader.getSRAMSize() - 5] is set to
				// 0x01
				if (response[reader.getSRAMSize() - 5] == 0x01)
					isValidTxData = false;
				else
					isValidTxData = true;

				Tag_Reade_time = System.currentTimeMillis() - currTime;

				isValidFirmware = isCRC32Appended(response);

				if (isValidFirmware == true)
					isValidRxData = isValidCRC32(response);
			} catch (CommandNotSupportedException e) {
				e.printStackTrace();
				showDemoNotSupportedAlert();
				cancel(true);
				return null;
			} catch (Exception e) {
				e.printStackTrace();
				
				// Inform the user about the error
//				String Message = "Phone sends too fast or Tag was lost, try Polling mode";
				String Message = "Phone sends too fast or Tag was lost";
				publishProgress(Message);
				cancel(true);
				return null;
			}

			return null;
		}

		@Override
		protected void onProgressUpdate(String... progress) {
			SpeedTestFragment.setDatarateCallback(progress[0]);
		}

		@Override
		protected void onPostExecute(Void nothing) {

			// ///////////////
			// Show Results
			// ///////////////

			// getting results
			int bytes = (chMultiplier * reader.getSRAMSize());

			String Message = "";

			// Result of the Integrity check based on the calculation of the
			// CRC32
			if (isValidFirmware == true) {
				String Rxresult = "";

				if (isValidRxData == true)
					Rxresult = "OK";
				else
					Rxresult = "Error";

				String Txresult = "";

				if (isValidTxData == true)
					Txresult = "OK";
				else
					Txresult = "Error";

				Message = Message.concat("Integrity of the Send data:  "
						+ Txresult + "\n");
				Message = Message.concat("Integrity of the Received data:  "
						+ Rxresult + "\n");

				// Additionally, if there was an error in the CRC Calculation
				// notify
				// the user
				if (isValidRxData == false) {
					showResultDialogMessage("Data Integrity error detected during message reception");
				}
			} else
				showResultDialogMessage("Please update your NTAG I2C Board firmware for the data integrity check");

			// Transmission Results
			Message = Message.concat("Transfer NFC device to MCU\n");
			Message = Message.concat("Speed (" + bytes + " Byte / "
					+ Reader_Tag_time + " ms): "
					+ String.format("%.0f", bytes / (Reader_Tag_time / 1000.0))
					+ " Bytes/s\n");

			// Reception Results
			Message = Message.concat("Transfer MCU to NFC device\n");
			Message = Message.concat("Speed (" + bytes + " Byte / "
					+ Tag_Reade_time + " ms): "
					+ String.format("%.0f", bytes / (Tag_Reade_time / 1000.0))
					+ " Bytes/s");

			// Show data on the screen
			SpeedTestFragment.setAnswer("Test finished");
			SpeedTestFragment.setDatarateCallback(Message);
		}

	}

	/**
	 * Stops the SRAMSpeed Demo
	 */
	public void SRAMSpeedFinish() {
		if (sramspeedtask != null && !sramspeedtask.isCancelled()) {
			sramspeedtask.exit = true;
			try {
				sramspeedtask.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
			sramspeedtask = null;
		}
	}

	/**
	 * Appends the CRC32 to the message transmitted during the SRAM SpeedTest
	 * 
	 * @param bytes
	 *            The content to be transmitted to the NTAGI2C board
	 * 
	 * @return Byte Array with the CRC32s embedded in it
	 */
	private byte[] appendCRC32(byte[] b) {
		byte[] temp = new byte[b.length - 4];
		System.arraycopy(b, 0, temp, 0, temp.length);
		byte[] crc = CRC32Calculator.CRC32(temp);
		System.arraycopy(crc, 0, b, b.length - crc.length, crc.length);

		return b;
	}

	/**
	 * Checks if the CRC32 value has been appended in the message
	 * 
	 * @param bytes
	 *            The whole message received from the board
	 * 
	 * @return boolean that indicates the presence of the CRC32
	 */
	private boolean isCRC32Appended(byte[] b) {
		for (int i = b.length - 4; i < b.length; i++) {
			if (b[i] != 0x00)
				return true;
		}

		return false;
	}

	/**
	 * Checks the received CRC32 value in the message received from the NTAG I2C
	 * board during the SRAM SpeedTest
	 * 
	 * @param bytes
	 *            The whole message received from the board
	 * 
	 * @return boolean with the result of the comparison between the CRC32
	 *         received and the CRC32 calculated
	 */
	private boolean isValidCRC32(byte[] b) {
		byte[] receivedCRC = { b[b.length - 4], b[b.length - 3],
				b[b.length - 2], b[b.length - 1] };

		byte[] temp = new byte[b.length - 4];
		System.arraycopy(b, 0, temp, 0, b.length - 4);

		byte[] calculatedCRC = CRC32Calculator.CRC32(temp);

		return Arrays.equals(receivedCRC, calculatedCRC);
	}

	/**
	 * Performs the EEPROM Speedtest
	 * 
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 * @throws FormatException
	 */
	public void EEPROMSpeedtest() throws UnsupportedEncodingException,
			IOException, FormatException {

		eepromspeedtask = new EEPROMSpeedtestTask(this);
		eepromspeedtask.execute();
	}

	private class EEPROMSpeedtestTask extends
			AsyncTask<Void, String, NdefMessage> {
		WriteEEPROMListener listener;
		public Boolean exit = false;

		long writeTime = 0;
		long readTime = 0;

		NdefMessage send_message;
		int ndef_message_size;

		public EEPROMSpeedtestTask(Ntag_I2C_Demo ntag_I2C_Demo) {
			this.listener = ntag_I2C_Demo;
		}

		@Override
		protected void onPreExecute() {

			try {
				SpeedTestFragment.setAnswer("EEPROM Speedtest");
				String textCharMulti = SpeedTestFragment
						.getrf_ndef_value_charmulti();

				// getting text multiplier
				int chMultiplier = 1;
				int chMultiLength = textCharMulti.length();
				if (chMultiLength == 0) {
					chMultiplier = 1;
				} else {
					chMultiplier = Integer.parseInt(textCharMulti);
				}

				// building string

				String messageText = "";
				for (int i = 0; i < chMultiplier; i++) {
					messageText = messageText.concat(" ");
				}

				// check Memory Length
				Ntag_Get_Version.Prod prod = reader.getProduct();
				int max_Memsize = prod.getMemsize();

				// creating NDEF

				// NDEF is max Text length + 5 bytes
				send_message = createNdefMessage(messageText);
				ndef_message_size = (send_message.toByteArray().length + 5);
				ndef_message_size = (int) Math.round(ndef_message_size / 4)	* 4;

				// if NDEF is shorter as Memsize write_EEPROM else write an
				// error

				if (ndef_message_size < max_Memsize) {
					SpeedTestFragment
							.setDatarateCallback("writing in progress...");
					// Execute doInBackground
					return;
				} else {
					SpeedTestFragment.setAnswer("NDEF Message too Long");
					SpeedTestFragment
							.setDatarateCallback("NDEF Message length: "
									+ ndef_message_size
									+ "\n\rMaximum allowed: " + max_Memsize);

					cancel(true);
					return;
				}
			} catch (Exception e) {
				e.printStackTrace();
				SpeedTestFragment.setDatarateCallback("Error try again");
				cancel(true);
				return;
			}
		}

		@Override
		protected NdefMessage doInBackground(Void... params) {
			try {
				// The NTAG I2C Plus might lose authentication when coming from writing/reading SRAM
				// so it is better to reauthenticate just in case
				Ntag_Get_Version.Prod prod = reader.getProduct();
				
				if (prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_1k_Plus) || prod.equals(Ntag_Get_Version.Prod.NTAG_I2C_2k_Plus)) {
					// Auth status gets lost after resetting the demo when we obtain the product we are dealing with
					if(MainActivity.getAuthStatus() == AuthStatus.Authenticated.getValue())
						reader.authenticatePlus(MainActivity.getPassword());
				}

				writeTime = System.currentTimeMillis();
				reader.writeNDEF(send_message, listener);
				writeTime = System.currentTimeMillis() - writeTime;

				if (exit) {
					cancel(true);
					return null;
				}

				publishProgress("writing finished \nreading in progress...");
				// Execute Read test
				readTime = System.currentTimeMillis();
				NdefMessage message = reader.readNDEF();
				readTime = System.currentTimeMillis() - readTime;

				if (exit) {
					cancel(true);
					return null;
				}
				publishProgress("writing finished \nreading finished");
				return message;
			} catch (CommandNotSupportedException e) {
				e.printStackTrace();
				showDemoNotSupportedAlert();
				cancel(true);
				return null;
			} catch (Exception e) {
				e.printStackTrace();
				publishProgress("Error while sending, try again");
				cancel(true);
				return null;
			}
		}

		@Override
		protected void onProgressUpdate(String... progress) {
			SpeedTestFragment.setDatarateCallback(progress[0]);
		}

		@Override
		protected void onPostExecute(NdefMessage message) {

			if (message == null) {
				Toast.makeText(main, "NDEF Read not supported",
						Toast.LENGTH_LONG).show();
				return;
			}

			// Note: Write bytes value might be bigger than the Write bytes
			// value. This is because during the read process we read the whole
			// page

			// writing result to GUI
			String Message = "";
			Message = Message.concat("Transfer NFC device to MCU\n");
			Message = Message.concat("Speed (" + ndef_message_size + " Byte / "
					+ writeTime + " ms): "
					+ String.format("%.0f", ndef_message_size / (writeTime / 1000.0))
					+ " Bytes/s\n");

			// Reception Results
			Message = Message.concat("Transfer MCU to NFC device\n");
			Message = Message.concat("Speed (" + ndef_message_size + " Byte / "
					+ readTime + " ms): "
					+ String.format("%.0f", ndef_message_size / (readTime / 1000.0))
					+ " Bytes/s");

			SpeedTestFragment.setAnswer("Test finished");
			SpeedTestFragment.setDatarateCallback(Message);
			
			// Put an empty NDEF Message in the memory
			defaultNdeftask = new WriteDefaultNdefTask();
			defaultNdeftask.execute();
		}
	}

	/**
	 * Stops the EEPROMSpeed Demo
	 */
	public void EEPROMSpeedFinish() {
		if (eepromspeedtask != null && !eepromspeedtask.isCancelled()) {
			eepromspeedtask.exit = true;
			try {
				eepromspeedtask.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
			eepromspeedtask = null;
		}
	}

	private class WriteEmptyNdefTask extends AsyncTask<Void, Void, Void> {

		@SuppressWarnings("unused")
		public Boolean exit = false;

		@Override
		protected Void doInBackground(Void... params) {
			try {

				reader.writeEmptyNdef();
			} catch (Exception e) {
				e.printStackTrace();
				cancel(true);
				return null;
			}
			return null;
		}

	}

	/**
	 * Stops the EEPROMSpeed Demo
	 */
	public void WriteEmptyNdefFinish() {
		if (emptyNdeftask != null && !emptyNdeftask.isCancelled()) {
			emptyNdeftask.exit = true;
			try {
				emptyNdeftask.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
			emptyNdeftask = null;
		}
	}
	
	private class WriteDefaultNdefTask extends AsyncTask<Void, Void, Void> {

		@SuppressWarnings("unused")
		public Boolean exit = false;

		@Override
		protected Void doInBackground(Void... params) {
			try {

				reader.writeDefaultNdef();
			} catch (Exception e) {
				e.printStackTrace();
				cancel(true);
				return null;
			}
			return null;
		}

	}

	/**
	 * Stops the EEPROMSpeed Demo
	 */
	public void WriteDefaultNdefFinish() {
		if (defaultNdeftask != null && !defaultNdeftask.isCancelled()) {
			defaultNdeftask.exit = true;
			try {
				defaultNdeftask.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
			defaultNdeftask = null;
		}
	}

	@SuppressWarnings("unused")
	private NdefMessage creatNdefDefaultMessage()
			throws UnsupportedEncodingException {
		NdefRecord uri_record = NdefRecord
				.createUri("http://www.nxp.com/products/identification_and_security/smart_label_and_tag_ics/ntag/series/NT3H1101_NT3H1201.html");
		String text = "NTAG I2C Demoboard LPC812";
		String lang = "en";
		byte[] textBytes = text.getBytes();
		byte[] langBytes = lang.getBytes("US-ASCII");
		int langLength = langBytes.length;
		int textLength = textBytes.length;
		byte[] payload = new byte[1 + langLength + textLength];
		payload[0] = (byte) langLength;
		System.arraycopy(langBytes, 0, payload, 1, langLength);
		System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);
		NdefRecord text_record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
				NdefRecord.RTD_TEXT, new byte[0], payload);

		NdefRecord[] sp_records = { uri_record, text_record };
		NdefMessage sp_message = new NdefMessage(sp_records);

		NdefRecord sp_record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
				NdefRecord.RTD_SMART_POSTER, new byte[0],
				sp_message.toByteArray());

		NdefRecord aap_record = NdefRecord.createApplicationRecord(main
				.getPackageName());

		NdefRecord[] records = { sp_record, aap_record };
		NdefMessage message = new NdefMessage(records);
		return message;

	}

	/**
	 * Creates a NDEF Text Message
	 * 
	 * @param text
	 *            Text to write
	 * @return NDEF Message
	 * @throws UnsupportedEncodingException
	 */
	private NdefMessage createNdefTextMessage(String text)
			throws UnsupportedEncodingException {
		if(text.length() == 0)
			return null;
		
		String lang = "en";
		byte[] textBytes = text.getBytes();
		byte[] langBytes = lang.getBytes("US-ASCII");
		int langLength = langBytes.length;
		int textLength = textBytes.length;
		byte[] payload = new byte[1 + langLength + textLength];
		payload[0] = (byte) langLength;
		System.arraycopy(langBytes, 0, payload, 1, langLength);
		System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

		NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
				NdefRecord.RTD_TEXT, new byte[0], payload);

		NdefRecord[] records = { record };
		NdefMessage message = new NdefMessage(records);

		return message;
	}

	/**
	 * Creates a NDEF Uri Message
	 * 
	 * @param uri
	 *            Uri to write
	 * @return NDEF Message
	 */
	private NdefMessage createNdefUriMessage(String uri) {
		if(uri.length() == 0 || uri.endsWith("//") || uri.endsWith(":") || uri.endsWith("."))
			return null;
		
		NdefRecord record = NdefRecord.createUri(uri);

		NdefRecord[] records = { record };
		NdefMessage message = new NdefMessage(records);

		return message;
	}

	/**
	 * Creates a Bluetooth Secure Simple Pairing Message
	 * 
	 * @param mac
	 *            Bluetooth MAC Address to Write
	 * @return NDEF Message
	 */
	private NdefMessage createNdefBSSPMessage() {
		byte[] payloadHs = { 0x12, (byte) 0xD1, 0x02, 0x04, 0x61, 0x63, 0x01, 0x01, 0x30, 0x00 };
		NdefRecord recordHs = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
				NdefRecord.RTD_HANDOVER_SELECT, new byte[0], payloadHs);

		byte[] deviceMAC = hexStringToByteArray(NdefFragment.getBtMac().replaceAll(":", ""));
		byte[] deviceName = NdefFragment.getBtName().getBytes();
		byte[] deviceClass = hexStringToByteArray(NdefFragment.getBtClass().replaceAll(":", ""));
		
		if(deviceMAC.length != 6 || deviceName.length == 0 || deviceClass.length != 3)
			return null;

		byte[] payloadBt = new byte[deviceMAC.length + deviceName.length + deviceClass.length + 2 + 4];

		// Payload Size
		payloadBt[0] = (byte) payloadBt.length;

		System.arraycopy(deviceMAC, 0, payloadBt, 2, deviceMAC.length);

		payloadBt[8] = (byte) (deviceName.length + 1);
		payloadBt[9] = 0x09; // Device Name identifier

		System.arraycopy(deviceName, 0, payloadBt, 10, deviceName.length);

		payloadBt[8 + deviceName.length + 2] = (byte) (deviceClass.length + 1);
		payloadBt[8 + deviceName.length + 3] = 0x0D; // Service Name identifier

		System.arraycopy(deviceClass, 0, payloadBt, 8 + deviceName.length + 4,
				deviceClass.length);

		NdefRecord recordBt = new NdefRecord(NdefRecord.TNF_MIME_MEDIA,
				"application/vnd.bluetooth.ep.oob".getBytes(), new byte[0],
				payloadBt);

		NdefRecord[] records = { recordHs, recordBt };
		NdefMessage message = new NdefMessage(records);

		return message;
	}
	
	/**
	 * Creates a NDEF SmartPoster Message
	 * 
	 * @param uri
	 *            Uri to write
	 * @param title
	 *            Text to write
	 * @return NDEF Message
	 * @throws UnsupportedEncodingException 
	 */
	private NdefMessage createNdefSpMessage(String title, String uri) throws UnsupportedEncodingException {
		if(title.length() == 0)
			return null;
		
		if(uri.length() == 0 || uri.endsWith("//") || uri.endsWith(":") || uri.endsWith("."))
			return null;
		
		String lang = "en";
		byte[] textBytes = title.getBytes();
		byte[] langBytes = lang.getBytes("US-ASCII");
		int langLength = langBytes.length;
		int textLength = textBytes.length;
		byte[] payload = new byte[1 + langLength + textLength];
		payload[0] = (byte) langLength;
		System.arraycopy(langBytes, 0, payload, 1, langLength);
		System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

		NdefRecord recordTitle = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], payload);
		NdefRecord recordLink = NdefRecord.createUri(uri);

		NdefRecord[] records = new NdefRecord[] { recordTitle, recordLink };
		NdefMessage messageSp = new NdefMessage(records);
		
		byte[] bytes = messageSp.toByteArray();
		NdefRecord recordSp = new NdefRecord((short) 0x01, NdefRecord.RTD_SMART_POSTER, null, bytes);

		return new NdefMessage( new NdefRecord[] { recordSp });
	}

	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();

		byte[] data = new byte[len / 2];
		for (int i = 0, j = (len - 2) / 2; i < len; i += 2, j--) {
			data[j] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character
					.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	/**
	 * Closes the Reader, Writes the NDEF Message via Android function and
	 * reconnects the Reader
	 * 
	 * @return NDEF Writing time
	 * @throws IOException
	 * @throws FormatException
	 * @throws CommandNotSupportedException 
	 */
	private long NDEFWrite(NdefMessage msg) throws IOException, FormatException, CommandNotSupportedException {
		// Time statistics to return
		long timeNdefWrite = 0;
		long RegTimeOutStart = System.currentTimeMillis();
		
		// Write the NDEF using NfcA commands to avoid problems when dealing with protected tags
		// Calling reader close / connect resets the authenticated status
		reader.writeNDEF(msg, null);
		
		timeNdefWrite = System.currentTimeMillis() - RegTimeOutStart;
		
		// Return the calculated time value
		return timeNdefWrite;
	}

	/**
	 * Calculates the Temperature in Celsius
	 * 
	 * @param temp
	 *            Temperature
	 * @return String of Temperature in Dez
	 */
	private String calcTempCelsius(int temp) {
		double Temp_double = 0;
		String Temp_string = "";

		// If the 11 Bit is 1 it is negative
		if ((temp & (1 << 11)) == (1 << 11)) {
			// Mask out the 11 Bit
			temp &= ~(1 << 11);
			Temp_string += "-";
		}

		Temp_double = 0.125 * temp;

		// Update the value on the Led fragment
		LedFragment.setTemperatureC(Temp_double);

		DecimalFormat df = new DecimalFormat("#.00");
		Temp_string = df.format(Temp_double);

		return Temp_string;
	}

	/**
	 * Calculates the Temperature in Farenheit
	 * 
	 * @param temp
	 *            Temperature
	 * @return String of Temperature in Dez
	 */
	private String calcTempFarenheit(int temp) {
		double Temp_double = 0;
		String Temp_string = "";

		// If the 11 Bit is 1 it is negative
		if ((temp & (1 << 11)) == (1 << 11)) {
			// Mask out the 11 Bit
			temp &= ~(1 << 11);
			Temp_string += "-";
		}

		Temp_double = 0.125 * temp;
		Temp_double = 32 + (1.8 * Temp_double);

		LedFragment.setTemperatureF(Temp_double);

		DecimalFormat df = new DecimalFormat("#.00");
		Temp_string = df.format(Temp_double);

		return Temp_string;
	}

	/**
	 * Calculates the Voltage
	 * 
	 * @param volt
	 *            Voltage
	 * @return String Voltage value
	 */
	private String calcVoltage(int volt) {
		String Volt_string = "0.0";

		if (volt > 0) {
			double Volt_double = round((0x3FF * 2.048) / volt);

			// Update the value on the Led fragment
			LedFragment.setVoltage(Volt_double);

			DecimalFormat df = new DecimalFormat("0.0");
			Volt_string = df.format(Volt_double) + "V";
		} else {
			Volt_string = "Not available";
		}
		return Volt_string;
	}

	/*
	 * Rounds the voltage to one single decimal
	 */
	public double round(double value) {
		return Math.rint(value * 10) / 10;
	}

	/*
	 * Helper function to show messages on the screen
	 * 
	 * @param temp Message
	 */
	protected void showResultDialogMessage(String msg) {
		new AlertDialog.Builder(main).setMessage(msg)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
					}
				}).show();
	}

	/**
	 * Creates a NDEF Message
	 * 
	 * @param text
	 *            Text to write
	 * @return NDEF Message
	 * @throws UnsupportedEncodingException
	 */
	private NdefMessage createNdefMessage(String text)
			throws UnsupportedEncodingException {
		String lang = "en";
		byte[] textBytes = text.getBytes();
		byte[] langBytes = lang.getBytes("US-ASCII");
		int langLength = langBytes.length;
		int textLength = textBytes.length;
		byte[] payload = new byte[1 + langLength + textLength];
		payload[0] = (byte) langLength;
		System.arraycopy(langBytes, 0, payload, 1, langLength);
		System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

		NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
				NdefRecord.RTD_TEXT, new byte[0], payload);

		NdefRecord[] records = { record };
		NdefMessage message = new NdefMessage(records);

		return message;
	}

	protected byte[] concat(byte[] one, byte[] two) {
		if (one == null)
			one = new byte[0];
		if (two == null)
			two = new byte[0];

		byte[] combined = new byte[one.length + two.length];

		System.arraycopy(one, 0, combined, 0, one.length);
		System.arraycopy(two, 0, combined, one.length, two.length);

		return combined;
	}

	@Override
	public void onWriteEEPROM(final int bytes) {
		main.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				SpeedTestFragment.setDatarateCallback(String.valueOf(bytes) + " Bytes written");
			}
		});
	}
	
	@Override
	public void onWriteSRAM() {
		main.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				FlashMemoryActivity.updateFLashDialog();
			}
		});
	}
}
