;
; ADTPro - Apple Disk Transfer ProDOS
; Copyright (C) 2008 by David Schmidt
; david__schmidt at users.sourceforge.net
;
; This program is free software; you can redistribute it and/or modify it 
; under the terms of the GNU General Public License as published by the 
; Free Software Foundation; either version 2 of the License, or (at your 
; option) any later version.
;
; This program is distributed in the hope that it will be useful, but 
; WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
; or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
; for more details.
;
; You should have received a copy of the GNU General Public License along 
; with this program; if not, write to the Free Software Foundation, Inc., 
; 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
;

; Serial bootstrap loader
;
; This code is sent by the ADTPro GUI a the user's request to a (hopefully)
; waiting Apple /// that has typed in the Grub loader.  The Grub loads this
; into $a100 and executes it, pulling in the (serial modified) SOS kernel.

.export 	Message, ACIAINIT, IIIPUT

KBDSTROBE	:= $C010
E_REG		:= $FFDF
BANK_REG	:= $FFEF
BUF_P		:= $7e

ACIADR		:= $c0f0	; Data register. $c0f0 for ///, $c088+S0 for SSC
ACIASR		:= $c0f1	; Status register. $c0f1 for ///, $c089+S0 for SSC
ACIAMR		:= $c0f2	; Command mode register. $c0f2 for ///, $c08a+S0 for SSC
ACIACR		:= $c0f3	; Control register.  $c0f3 for ///, $c08b+S0 for SSC

GRUBIIIGET	:= $a040	; Borrow the Grub's IIIGET

Signature:
	.byte	$47		; The first byte that grub will see: a "G" character
	.org $a100
Entry:
	ldx	#$FB
	txs			; Some nonsense about .CONSOLE mucking with the stack
	bit	KBDSTROBE
	lda	#$40
	sta	$FFCA		; Disable interrupts
	lda	#$07
	sta	BANK_REG
	ldx	#$00
BankTest:			; Find highest writable bank
	dec	BANK_REG
	stx	$2000
	lda	$2000
	bne	BankTest

	jsr	ACIAINIT	; Get the environment all set up

; Set up our pointers
	lda	#$00
	sta	BUF_P
	lda	#$1e		; SOS.KERNEL initially occupies $1e00 to $73ff
	sta	BUF_P+1

; Say we're active
	ldx	#<message_1
	jsr	Message

; Ask for the kernel
	lda #179
	jsr IIIPUT	; Send a "3" to trigger the SOS.KERNEL download

; Poll the port until we get a magic incantation
	ldy #$00
Poll:
	jsr	GRUBIIIGET
	cmp	#$53		; First character will be "S" from "SOS" in SOS.KERNEL
	bne	Poll
	sta	(BUF_P),y	; Save that first "S"
	iny

; We got the magic signature; start reading data
Read:	
	jsr	GRUBIIIGET	; Pull a byte
	sta	(BUF_P),y	; Save it
	sta	$0410		; Print it in the status area
	iny
	bne	Read
	inc	BUF_P+1		; Increment another page
	lda	BUF_P+1
	cmp	#$74		; Are we done? (SOS.KERNEL v1.3 is $56 pages long; $1E+$56=$74)
	bne	Read		; If not... go back for more

; Go fast again
	lda	E_REG	; Read the environment register
	and	#$7f	; Set 2MHz switch
	sta	E_REG	; Write the environment register

; Say we're done
	ldx	#<message_4
	jsr	Message

; Call SOSLDR entry point
	jmp	$1e70	; SOSLDR v1.3 Entry point

ACIAINIT:
; Set up the environment
	lda	E_REG		; Read the environment register
	ora	#$F2		; Set 1MHz switch, Video + I/O space
	sta	E_REG		; Write the environment register
; Set up the serial port
	lda	#$0b		; No parity, etc.
	sta	ACIAMR		; Store via ACIA mode register.
	lda	#$1e		; $16=300, $1e=9600, $1f=19200, $10=115k
	sta	ACIACR		; Store via ACIA control register.
	rts

IIIPUT:
	pha			; Push 'character to send' onto the stack
IIIPUTC1:
	lda	$C0F1		; Check status bits
	and	#$70
	cmp	#$10
	bne	IIIPUTC1	; Output register is full, so loop
	pla			; Pull 'character to send' back off the stack
	sta	$C0F0		; Put character
	rts

Message:
	stx	SelfMod+1
	ldy	#$0e
SelfMod:
	lda	message_1,y
	sta	$400,y
	dey
	bpl SelfMod
	rts

RESTORE:
	lda	#$32
	sta	E_REG
	rts

message_1:
	.byte	"LOADING KERNEL:"

message_2:
	.byte	"LOADING INTERP:"

message_3:
	.byte	"LOADING DRIVER:"

message_4:
	.byte	$CF, $cb, $a1, $a0, $a0, $a0, $a0, $a0	; "OK!"
	.byte	$a0, $a0, $a0, $a0, $a0, $a0

.align	256
	.byte	$00
.assert	* = $a200, error, "Code got too big to fit in a block!"
