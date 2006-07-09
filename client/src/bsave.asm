*
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
*

*---------------------------------------------------------
* BSAVE - Save a copy of ADTPro in memory
*---------------------------------------------------------
BSAVE
	lda LENGTH+1
	lsr
	lsr
	lsr
	lsr
	clc
	cmp #$09
	bcc BS.1	A is greater than 9
	adc #$B6
	jmp BS.2
BS.1
	ora #$B0
BS.2
	sta NYBBLE1
	lda LENGTH+1
	and #$0F
	clc
	cmp #$09
	bcc BS.3	A is greater than 9
	adc #$B6
	jmp BS.4
BS.3
	ora #$B0
BS.4
	sta NYBBLE2

	lda LENGTH
	lsr
	lsr
	lsr
	lsr
	clc
	cmp #$09
	bcc BS.5	A is greater than 9
	adc #$B6
	jmp BS.6
BS.5
	ora #$B0
BS.6
	sta NYBBLE3
	lda LENGTH
	and #$0F
	clc
	cmp #$09
	bcc BS.7	A is greater than 9
	adc #$B6
	jmp BS.8
BS.7
	ora #$B0
BS.8
	sta NYBBLE4

	ldx #$00
LEWP
	lda COMMAND,X
	sta $0200,X
	inx
	cpx CMDEND-COMMAND
	bne LEWP
	lda #$00
	sta $BE0F	Clear ProDOS error code
	jsr $BE03	Execute the input buffer

	lda #$00	Prepare to print ProDOS error message
	sta <CH
	lda #$15
	jsr TABV

	lda $BE0F	Grab the error code out of ProDOS
	beq BSAVEOK	If no problem - exit
	jsr $BE0C	Print ProDOS error message
	jmp BSAVEDONE
BSAVEOK
	lda #$16
	jsr TABV
	ldy #PMSG14
	jsr SHOWMSG
BSAVEDONE
	jsr PAUSE
	rts

*---------------------------------------------------------
* DRVSLOT - Save initial drive/slot combination
*---------------------------------------------------------

DRVSLOT
	lda $BE3C
	ora #$B0
*	sta CMDSLOT
	lda $BE3D
	ora #$B0
*	sta CMDDRV
	rts

*COMMAND	.as -'BSAVE ADTPRO,S'
*CMDSLOT	.as -'6,D'
*CMDDRV	.as -'1,A$0803,L$'
COMMAND	.as -'BSAVE ADTPRO,A$0803,L$'
NYBBLE1	.db $00
NYBBLE2	.db $00
NYBBLE3	.db $00
NYBBLE4	.db $00
CMDEND	.db $8D
LENGTH	.da PEND-PBEGIN