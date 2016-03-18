/*
 *     SocialLedge.com - Copyright (C) 2013
 *
 *     This file is part of free software framework for embedded processors.
 *     You can use it and/or distribute it as long as this copyright header
 *     remains unmodified.  The code is free for personal use and requires
 *     permission to use in a commercial product.
 *
 *      THIS SOFTWARE IS PROVIDED "AS IS".  NO WARRANTIES, WHETHER EXPRESS, IMPLIED
 *      OR STATUTORY, INCLUDING, BUT NOT LIMITED TO, IMPLIED WARRANTIES OF
 *      MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE APPLY TO THIS SOFTWARE.
 *      I SHALL NOT, IN ANY CIRCUMSTANCES, BE LIABLE FOR SPECIAL, INCIDENTAL, OR
 *      CONSEQUENTIAL DAMAGES, FOR ANY REASON WHATSOEVER.
 *
 *     You can reach the author of this software at :
 *          p r e e t . w i k i @ g m a i l . c o m
 */

#include <string.h>         // memcpy

#include "i2c_base.hpp"
#include "lpc_sys.h"
#include "printf_lib.h"


/**
 * Instead of using a dedicated variable for read vs. write, we just use the LSB of
 * the user address to indicate read or write mode.
 */
#define I2C_SET_READ_MODE(addr)     (addr |= 1)     ///< Set the LSB to indicate read-mode
#define I2C_SET_WRITE_MODE(addr)    (addr &= 0xFE)  ///< Reset the LSB to indicate write-mode
#define I2C_READ_MODE(addr)         (addr & 1)      ///< Read address is ODD
#define I2C_WRITE_ADDR(addr)        (addr & 0xFE)   ///< Write address is EVEN
#define I2C_READ_ADDR(addr)         (addr | 1)      ///< Read address is ODD



void I2C_Base::handleInterrupt()
{
    /* If transfer finished (not busy), then give the signal */
    if (busy != i2cStateMachine()) {
        long higherPriorityTaskWaiting = 0;
        xSemaphoreGiveFromISR(mTransferCompleteSignal, &higherPriorityTaskWaiting);
        portEND_SWITCHING_ISR(higherPriorityTaskWaiting);
    }
}

uint8_t I2C_Base::readReg(uint8_t deviceAddress, uint8_t registerAddress)
{
    uint8_t byte = 0;
    readRegisters(deviceAddress, registerAddress, &byte, 1);
    return byte;
}

bool I2C_Base::readRegisters(uint8_t deviceAddress, uint8_t firstReg, uint8_t* pData, uint32_t bytesToRead)
{
    I2C_SET_READ_MODE(deviceAddress);
    return transfer(deviceAddress, firstReg, pData, bytesToRead);
}

bool I2C_Base::writeReg(uint8_t deviceAddress, uint8_t registerAddress, uint8_t value)
{
    return writeRegisters(deviceAddress, registerAddress, &value, 1);
}

bool I2C_Base::writeRegisters(uint8_t deviceAddress, uint8_t firstReg, uint8_t* pData, uint32_t bytesToWrite)
{
    I2C_SET_WRITE_MODE(deviceAddress);
    return transfer(deviceAddress, firstReg, pData, bytesToWrite);
}

bool I2C_Base::transfer(uint8_t deviceAddress, uint8_t firstReg, uint8_t* pData, uint32_t transferSize)
{
    bool status = false;
    if(mDisableOperation || !pData) {
        return status;
    }

    // If scheduler not running, perform polling transaction
    if(taskSCHEDULER_RUNNING != xTaskGetSchedulerState())
    {
        i2cKickOffTransfer(deviceAddress, firstReg, pData, transferSize);

        // Wait for transfer to finish
        const uint64_t timeout = sys_get_uptime_ms() + I2C_TIMEOUT_MS;
        while (!xSemaphoreTake(mTransferCompleteSignal, 0)) {
            if (sys_get_uptime_ms() > timeout) {
                break;
            }
        }

        status = (0 == mTransaction.error);
    }
    else if (xSemaphoreTake(mI2CMutex, OS_MS(I2C_TIMEOUT_MS)))
    {
        // Clear potential stale signal and start the transfer
        xSemaphoreTake(mTransferCompleteSignal, 0);
        i2cKickOffTransfer(deviceAddress, firstReg, pData, transferSize);

        // Wait for transfer to finish and copy the data if it was read mode
        if (xSemaphoreTake(mTransferCompleteSignal, OS_MS(I2C_TIMEOUT_MS))) {
            status = (0 == mTransaction.error);
        }

        xSemaphoreGive(mI2CMutex);
    }

    return status;
}

bool I2C_Base::checkDeviceResponse(uint8_t deviceAddress)
{
    uint8_t dummyReg = 0;
    uint8_t notUsed = 0;

    // The I2C State machine will not continue after 1st state when length is set to 0
    uint32_t lenZeroToTestDeviceReady = 0;

    return readRegisters(deviceAddress, dummyReg, &notUsed, lenZeroToTestDeviceReady);
}

bool I2C_Base :: initSlave(uint8_t slaveAddr, uint8_t* buffer, uint8_t len)
{
    if(slaveAddr>127){
        return false;
    }
    // P0.10 -> SDA2    P0.11 -> SCL2
#if 0
    LPC_SC->PCONP |= 1<<26; // Power on I2C2 peripheral
    LPC_SC->PCLKSEL1 &= ~(3<<20);   // Setting clock -> CCLK/4
    LPC_PINCON->PINSEL0 &= ~(0xF<<20); // Muxconfig reset to zero
    LPC_PINCON->PINSEL0 |= (0xA<<20);   // Select Mux for SCL2/SDA2
    LPC_PINCON->PINMODE0 &= ~(0xF << 20); // Reset Pin mode configuration
    LPC_PINCON->PINMODE0 |=  (0xA << 20); // Disable both Pull-up and Pull-down Use external pull ups
    LPC_PINCON->PINMODE_OD0 |= 3<<10; // Enable open drain for both pins

#endif
    i2c2_addr = slaveAddr;
    i2c2_reg_base = buffer;
    i2c2_reg_len = len;
    i2c2_reg_index = 0;
    flag_addr_read = false;

    // Set I2C slave address and enable I2C
    LPC_I2C2->I2ADR0 = i2c2_addr;//<<1;
    LPC_I2C2->I2CONCLR = 0x6C;           // 2|3|5|6 Clear ALL I2C Flags
    // Enable I2C Slave and the interrupt for it
    LPC_I2C2->I2CONSET = (1<<6) | (1<<2);
   // NVIC_EnableIRQ(I2C2_IRQn);

    return true;
}

I2C_Base::I2C_Base(LPC_I2C_TypeDef* pI2CBaseAddr) :
        mpI2CRegs(pI2CBaseAddr),
        mDisableOperation(false)
{
    mI2CMutex = xSemaphoreCreateMutex();
    mTransferCompleteSignal = xSemaphoreCreateBinary();

    /// Binary semaphore needs to be taken after creating it
    xSemaphoreTake(mTransferCompleteSignal, 0);

    if((unsigned int)mpI2CRegs == LPC_I2C0_BASE)
    {
        mIRQ = I2C0_IRQn;
    }
    else if((unsigned int)mpI2CRegs == LPC_I2C1_BASE)
    {
        mIRQ = I2C1_IRQn;
    }
    else if((unsigned int)mpI2CRegs == LPC_I2C2_BASE)
    {
        mIRQ = I2C2_IRQn;
    }
    else {
        mIRQ = (IRQn_Type)99; // Using invalid IRQ on purpose
    }
}

bool I2C_Base::init(uint32_t pclk, uint32_t busRateInKhz)
{
    // Power on I2C
    switch(mIRQ) {
        case I2C0_IRQn: lpc_pconp(pconp_i2c0, true);  break;
        case I2C1_IRQn: lpc_pconp(pconp_i2c1, true);  break;
        case I2C2_IRQn: lpc_pconp(pconp_i2c2, true);  break;
        default: return false;
    }

    mpI2CRegs->I2CONCLR = 0x6C;           // Clear ALL I2C Flags

    /**
     * Per I2C high speed mode:
     * HS mode master devices generate a serial clock signal with a HIGH to LOW ratio of 1 to 2.
     * So to be able to optimize speed, we use different duty cycle for high/low
     *
     * Compute the I2C clock dividers.
     * The LOW period can be longer than the HIGH period because the rise time
     * of SDA/SCL is an RC curve, whereas the fall time is a sharper curve.
     */
    const uint32_t percent_high = 40;
    const uint32_t percent_low = (100 - percent_high);
    const uint32_t freq_hz = (busRateInKhz > 1000) ? (100 * 1000) : (busRateInKhz * 1000);
    const uint32_t half_clock_divider = (pclk / freq_hz) / 2;
    mpI2CRegs->I2SCLH = (half_clock_divider * percent_high) / 100;
    mpI2CRegs->I2SCLL = (half_clock_divider * percent_low ) / 100;

    // Set I2C slave address and enable I2C
    mpI2CRegs->I2ADR0 = 0;//0x30;
    mpI2CRegs->I2ADR1 = 0;
    mpI2CRegs->I2ADR2 = 0;
    mpI2CRegs->I2ADR3 = 0;

    // Enable I2C and the interrupt for it
    mpI2CRegs->I2CONSET = 0x40;
    NVIC_EnableIRQ(mIRQ);

    return true;
}



/// Private ///

void I2C_Base::i2cKickOffTransfer(uint8_t devAddr, uint8_t regStart, uint8_t* pBytes, uint32_t len)
{
    mTransaction.error     = 0;
    mTransaction.slaveAddr = devAddr;
    mTransaction.firstReg  = regStart;
    mTransaction.trxSize   = len;
    mTransaction.pMasterData   = pBytes;

    // Send START, I2C State Machine will finish the rest.
    mpI2CRegs->I2CONSET = 0x20;
    //mpI2CRegs->I2CONSET = (1<<6) | (1<<2);
}

/*
 * I2CONSET bits
 * 0x04 AA
 * 0x08 SI
 * 0x10 STOP
 * 0x20 START
 * 0x40 ENABLE
 *
 * I2CONCLR bits
 * 0x04 AA
 * 0x08 SI
 * 0x20 START
 * 0x40 ENABLE
 */
I2C_Base::mStateMachineStatus_t I2C_Base::i2cStateMachine()
{
    enum {
        // General states :
        busError        = 0x00,
        start           = 0x08,
        repeatStart     = 0x10,
        arbitrationLost = 0x38,

        // Master Transmitter States:
        slaveAddressAcked  = 0x18,
        slaveAddressNacked = 0x20,
        dataAckedBySlave   = 0x28,
        dataNackedBySlave  = 0x30,

        // Master Receiver States:
        readAckedBySlave      = 0x40,
        readModeNackedBySlave = 0x48,
        dataAvailableAckSent  = 0x50,
        dataAvailableNackSent = 0x58,

        // Slave Receiver States:
        masterAddressAcked    = 0x60,
        masterAddressNacked   = 0x68,
        dataAckedFromSlave    = 0x80,
        dataNackedFromSlave   = 0x88,
        stopByMaster          = 0xA0,

        // Slave Transmitter States:
        masterReadRequestAcked  = 0xA8,
        masterReadRequestNacked = 0xB0,
        masterReceivedAck       = 0xB8,
        masterReceivedNack      = 0xC0,
    };

    mStateMachineStatus_t state = busy;

    /*
     ***********************************************************************************************************
     * Write-mode state transition :
     * start --> slaveAddressAcked --> dataAckedBySlave --> ... (dataAckedBySlave) --> (stop)
     *
     * Read-mode state transition :
     * start --> slaveAddressAcked --> dataAcked --> repeatStart --> readAckedBySlave
     *  For 2+ bytes:  dataAvailableAckSent --> ... (dataAvailableAckSent) --> dataAvailableNackSent --> (stop)
     *  For 1  byte :  dataAvailableNackSent --> (stop)
     ***********************************************************************************************************
     */

    /* Me being lazy and using #defines instead of inline functions :( */
    #define clearSIFlag()       mpI2CRegs->I2CONCLR = (1<<3)
    #define setSTARTFlag()      mpI2CRegs->I2CONSET = (1<<5)
    #define clearSTARTFlag()    mpI2CRegs->I2CONCLR = (1<<5)
    #define setAckFlag()        mpI2CRegs->I2CONSET = (1<<2)
    #define setNackFlag()       mpI2CRegs->I2CONCLR = (1<<2)

    /* yep ... lazy again */
    #define setStop()           clearSTARTFlag();                           \
                                mpI2CRegs->I2CONSET = (1<<4);               \
                                clearSIFlag();                              \
                                while((mpI2CRegs->I2CONSET&(1<<4)));        \
                                if(I2C_READ_MODE(mTransaction.slaveAddr))   \
                                    state = readComplete;                   \
                                else                                        \
                                    state = writeComplete;

   // u0_dbg_printf("I2C HW state: %u 0x%02x\n", mpI2CRegs->I2STAT, mpI2CRegs->I2STAT);

    switch (mpI2CRegs->I2STAT)
    {
        case start:
            mpI2CRegs->I2DAT = I2C_WRITE_ADDR(mTransaction.slaveAddr);
            clearSIFlag();
            break;
        case repeatStart:
            mpI2CRegs->I2DAT = I2C_READ_ADDR(mTransaction.slaveAddr);
            clearSIFlag();
            break;

        case slaveAddressAcked:
            clearSTARTFlag();
            // No data to transfer, this is used just to test if the slave responds
            if(0 == mTransaction.trxSize) {
                setStop();
            }
            else {
                mpI2CRegs->I2DAT = mTransaction.firstReg;
                clearSIFlag();
            }
            break;

        case dataAckedBySlave:
            if (I2C_READ_MODE(mTransaction.slaveAddr)) {
                setSTARTFlag(); // Send Repeat-start for read-mode
                clearSIFlag();
            }
            else {
                if(0 == mTransaction.trxSize) {
                    setStop();
                }
                else {
                    mpI2CRegs->I2DAT = *(mTransaction.pMasterData);
                    ++mTransaction.pMasterData;
                    --mTransaction.trxSize;
                    clearSIFlag();
                }
            }
            break;

        /* In this state, we are about to initiate the transfer of data from slave to us
         * so we are just setting the ACK or NACK that we'll do AFTER the byte is received.
         */
        case readAckedBySlave:
            clearSTARTFlag();
            if(mTransaction.trxSize > 1) {
                setAckFlag();  // 1+ bytes: Send ACK to receive a byte and transition to dataAvailableAckSent
            }
            else {
                setNackFlag();  //  1 byte : NACK next byte to go to dataAvailableNackSent for 1-byte read.
            }
            clearSIFlag();
            break;
        case dataAvailableAckSent:
            *mTransaction.pMasterData = mpI2CRegs->I2DAT;
            ++mTransaction.pMasterData;
            --mTransaction.trxSize;

            if(1 == mTransaction.trxSize) { // Only 1 more byte remaining
                setNackFlag();// NACK next byte --> Next state: dataAvailableNackSent
            }
            else {
                setAckFlag(); // ACK next byte --> Next state: dataAvailableAckSent(back to this state)
            }

            clearSIFlag();
            break;
        case dataAvailableNackSent: // Read last-byte from Slave
            *mTransaction.pMasterData = mpI2CRegs->I2DAT;
            setStop();
            break;

        case arbitrationLost:
            // We should not issue stop() in this condition, but we still need to end our  transaction.
            state = I2C_READ_MODE(mTransaction.slaveAddr) ? readComplete : writeComplete;
            mTransaction.error = mpI2CRegs->I2STAT;
            break;
#if 1
        /* I2C Slave Receiver States   */
        case masterAddressAcked:
            flag_addr_read = false;
            setAckFlag();
            clearSIFlag();
            break;

        case dataAckedFromSlave:
            if(!flag_addr_read) {
                i2c2_reg_index = mpI2CRegs->I2DAT;
                flag_addr_read = true;
            }
            else {
                i2c2_reg_base[i2c2_reg_index++] = mpI2CRegs->I2DAT;
                i2c2_reg_index %= i2c2_reg_len;
            }
            setAckFlag();
            clearSIFlag();
            break;
#if 0
        case dataNackedFromSlave:
            setAckFlag();
            clearSIFlag();
            break;
#endif
        case stopByMaster:
            //flag_addr_read = false;
            setAckFlag();
            clearSTARTFlag();
            clearSIFlag();
            break;

        /* I2C Slave Transmitter States   */
        case masterReadRequestAcked:
            if(!flag_addr_read) {
                i2c2_reg_index = mpI2CRegs->I2DAT;
                flag_addr_read = true;
            }
            if(flag_addr_read){
                mpI2CRegs->I2DAT = i2c2_reg_base[i2c2_reg_index++];
                i2c2_reg_index %= i2c2_reg_len;
            }
            setAckFlag();
            clearSIFlag();
            break;
#endif
        case slaveAddressNacked:        // no break
        case dataNackedBySlave:         // no break
        case readModeNackedBySlave:     // no break
        case busError:                  // no break
        case masterAddressNacked:       // no break

        case dataNackedFromSlave:       // no break
        case masterReadRequestNacked:   // no break
        case masterReceivedAck:         // no break
        case masterReceivedNack:        // no break

        default:
            mTransaction.error = mpI2CRegs->I2STAT;
            flag_addr_read = false;
            setStop();
            break;
    }

    return state;
}
