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

/**
 * @file
 * @brief This is the application entry point.
 * 			FreeRTOS and stdio printf is pre-configured to use uart0_min.h before main() enters.
 * 			@see L0_LowLevel/lpc_sys.h if you wish to override printf/scanf functions.
 *
 */
#include <stdio.h>
#include "tasks.hpp"
#include "examples/examples.hpp"
#include "io.hpp"
#include "fat/disk/spi_flash.h"

const uint8_t switch_pin_no=0;
const uint8_t led_pin_no=2;
const uint8_t flash_select_pin_no = 6;

#define IMU                     0
static uint8_t man_id,device_id;
static uint8_t recv_buffer;
QueueHandle_t receive_queue1;

/***************************** I2C Master task *************************/
class I2CMasterTask: public scheduler_task
{
    private:
        I2C2& i2c2_master = I2C2::getInstance();;
    public:
        I2CMasterTask(uint8_t priority) :
                scheduler_task("I2CMasterTask", 2000, priority)
        {
            // Nothing to init
        }

        bool init(void){
            i2c2_master.init(400);
            return true;
        }

        bool run(void *p){
            return true;
        }
};

/***************************** I2C Master task *************************/
class I2CSlaveTask: public scheduler_task
{
    private:
        I2C2& i2c2_slave = I2C2::getInstance();
        uint8_t slave_buffer[256];
    public:
        I2CSlaveTask(uint8_t priority) :
                scheduler_task("I2CSlaveTask", 2000, priority)
        {
            // Nothing to init
        }

        bool init(void){
            i2c2_slave.initSlave(0x30,slave_buffer,10);
            return true;
        }

        bool run(void *p){
            //LD.setNumber(slave_buffer[2]);
            if(slave_buffer[1]==0x30){
                LE.on(1);
            }
            else if(slave_buffer[1]==0x60){
                LE.off(1);
            }
            else if(slave_buffer[1]==0x50){
                LE.toggle(1);
            }

            if(slave_buffer[2]==0x30){
                LE.on(2);
            }
            else if(slave_buffer[2]==0x60){
                LE.off(2);
            }
            else if(slave_buffer[2]==0x50){
                LE.toggle(2);
            }

            if(slave_buffer[3]==0x30){
                LE.on(3);
            }
            else if(slave_buffer[3]==0x60){
                LE.off(3);
            }
            else if(slave_buffer[3]==0x50){
                LE.toggle(3);
            }

            if(slave_buffer[4]==0x30){
                LE.on(4);
            }
            else if(slave_buffer[4]==0x60){
                LE.off(4);
            }
            else if(slave_buffer[4]==0x50){
                LE.toggle(4);
            }
            vTaskDelay(500);
            return true;
        }
};

/**************************** UART-3 Interrupt Handler ******************/
extern "C"
 {
    void UART3_IRQHandle()
    {
        if((LPC_UART3->LSR & 1<<0))
        {
            recv_buffer = LPC_UART3 -> RBR;
            xQueueSendFromISR(receive_queue1,&recv_buffer,0);
        }
        LE.toggle(1);
    }
 }

/**************************** UART-3 Initialization ******************/
static void uart_init(uint32_t baudrate)
{
    LPC_SC->PCONP |= 1<<25;         //UART3 disabled by default so enable that
    LPC_SC->PCLKSEL1 &= ~(3<<18);      //Peripheral clock select CLK/4 settings
    LPC_PINCON->PINSEL9 &= ~(0xF<<24); //Set zero all bits of TX/RX pins
    LPC_PINCON->PINSEL9 |= (0xF<<24);     //Select TX/RX pins
    LPC_PINCON->PINMODE9 &= ~(0xF<<24); // Pull up all pins
    LPC_UART3->LCR |= 3;    // 8 bit char length
    LPC_UART3->LCR &= ~(1<<2);  // 1 Stop bit
    LPC_UART3->LCR &= ~(1<<3);  // Parity Disable
    LPC_UART3->LCR &= ~(1<<6);  // Disable Break Control
    LPC_UART3->LCR |= (1<<7);   // Enable DLAB for baudrate
    uint16_t baud = ((48000000/4)/(16*baudrate))+0.5;   // Baud calculation example
    LPC_UART3->DLL = baud;          // Set LSB for Baud
    LPC_UART3->DLM = baud>>8;       // Set MSB for Baud
    LPC_UART3->LCR &= ~(1<<7);   // Disable DLAB for status read and interrupt generation
    LPC_UART3->FCR |= 7;            // FIFO Enable - TX/RX FIFO Reset
    LPC_UART3->FCR |= (3<<6);      // RX Trigger level-2(14 chars)
    NVIC_EnableIRQ(UART3_IRQn);     // To enable NVIC interrupt from core_cm3.h
    LPC_UART3->IER |= 5;        // Enable Transmit/Receive Interrupt
    LPC_UART3->TER |= 1;        // Transmitter Enable
}

/**************************** UART-3 Transmit function ******************/
static void uart_transmit(uint8_t arg){
    // This function can handle continuous 16 characters transmission as FIFO is enable
    // But for still safety side added while loop
    while(!(LPC_UART3->LSR & 1<<5));    // Wait if previous character is in transmission
    LPC_UART3->THR = arg;
}

/*************** Not used this function as receive interrupt is enabled *******/
static uint8_t uart_receive(void){
    while(!(LPC_UART3->LSR & 1<<0));    // Wait until character is not there
    return LPC_UART3->RBR;
}

class UART_Task: public scheduler_task
{
    public:
        UART_Task(uint8_t priority) :
                scheduler_task("UART_Task", 2000, priority)
        {
            // Nothing to init
        }

        bool init(void)
        {
            receive_queue1 = xQueueCreate(20, 1*sizeof(char));    //It will create queue of 10 characters
            uart_init(9600);
            return true;
        }

        bool run(void *p)
        {
            uint8_t rec;
            uint8_t buffer[16];
            uint8_t trans_char = 'A';
            for(int i=1;i<=1;i++){
                if(xQueueReceiveFromISR(receive_queue1,&rec,0)){
                    #if IMU
                        // Save all received data to the buffer
                        buffer[i-1] = rec;
                    #else
                        // Received data
                        printf("Rec[%d]: %c\n",i,rec);
                    #endif
                }
            }
            #if IMU
                // Print Received yaw angle from IMU
                printf("IMU: %s\n",buffer);
                // Yaw Reading command to IMU sensor
                uart_transmit('#');
                uart_transmit('f');
            #else
                // Send 16 bytes
                for(int i=1;i<=1;i++){
                    uart_transmit(++rec);
                }
            #endif

            vTaskDelay(500);
            return true;
        }
};

class LETask: public scheduler_task
{
    public:
        LETask(uint8_t priority) :
                scheduler_task("LE_Task", 2000, priority)
        {
            // Nothing to init
        }

        bool init(void)
        {
            SW.init();
            LE.init();
            LPC_GPIO2->FIODIR &= ~(1<<switch_pin_no);
            LPC_GPIO2->FIODIR |= 1<<led_pin_no;
            return true;
        }

        bool run(void *p)
        {
            if ((LPC_GPIO2->FIOPIN) & 1<<switch_pin_no )    // if switch is pressed
            {
                // Turn on ext LED
                LPC_GPIO2->FIOSET |= 1<<led_pin_no;
                //printf("ON\n");
            }
            else
            {
                // Turn off ext LED
                LPC_GPIO2->FIOCLR |= (1<<led_pin_no);
                //printf("OFF\n");
            }
            vTaskDelay(500);
            return true;
        }
};

static inline void flash_select(void){
    // Set low chip select pin
    LPC_GPIO0->FIOCLR |= (1<<flash_select_pin_no);
}

static inline void flash_deselect(void){
    // Set high chip select pin
    LPC_GPIO0->FIOSET |= (1<<flash_select_pin_no);
}

static uint8_t flash_transfer_byte(uint8_t data){
    LPC_SSP1->DR = data;
    while(! (LPC_SSP1->SR & 1<<4)); // Wait until SSP is busy
    data = LPC_SSP1->DR;
    return data;
}

int nbytes_sector=0;
int nsector_cluster=0;
int nsector=0;
uint8_t tmp_data[512];

static void flash_page_read(void) {
    printf("\n");
    flash_select();
    flash_transfer_byte(0x1B);
    flash_transfer_byte(0x00);          // 3 byte address
    flash_transfer_byte(0x00);
    flash_transfer_byte(0x00);
    flash_transfer_byte(0x00);          // 2 dummy bytes
    flash_transfer_byte(0x00);
    //flash_transfer_byte(0x00);
    //flash_transfer_byte(0x00);


    for(int i=0;i<512;i++){
        tmp_data[i] = flash_transfer_byte(0x00);
        printf("%d:%d  ",i,tmp_data[i]);
    }
    flash_deselect();
    nsector = tmp_data[19] | (tmp_data[20]<<8);
    nbytes_sector = tmp_data[11] | (tmp_data[12]<<8);
    nsector_cluster = tmp_data[13];
    printf("\n");

}

void flash_status_read(){
    uint8_t data;
    printf("\nFlash Status bytes: ");
    flash_select();
    data = flash_transfer_byte(0xD7);
    //printf("%x: ",data);
    data = flash_transfer_byte(0x00);
    printf("0x%x: ",data);
    data = flash_transfer_byte(0x00);
    printf("0x%x: ",data);
    flash_deselect();
    printf("\n");
}

void flash_change_page_512(void){
    flash_select();
    flash_transfer_byte(0x3D);
    flash_transfer_byte(0x2A);
    flash_transfer_byte(0x80);
    flash_transfer_byte(0xA6);
    flash_deselect();
}

void flash_change_page_528(void){
    flash_select();
    flash_transfer_byte(0x3D);
    flash_transfer_byte(0x2A);
    flash_transfer_byte(0x80);
    flash_transfer_byte(0xA7);
    flash_deselect();
}

void flash_reset(void){
    flash_select();
    flash_transfer_byte(0xF0);
    flash_transfer_byte(0x00);
    flash_transfer_byte(0x00);
    flash_transfer_byte(0x00);
    flash_deselect();
}

void flash_sector_protect(bool arg){
    flash_select();
    flash_transfer_byte(0x3D);
    flash_transfer_byte(0x2A);
    flash_transfer_byte(0x7F);
    if(arg){
        flash_transfer_byte(0xA9);
    }
    else{
        flash_transfer_byte(0x9A);
    }
    flash_deselect();
}

static void flash_transfer_data(uint8_t* data, uint8_t len){
    const uint8_t fifo_size = 8;
    const uint8_t half_fifo_size = fifo_size / 2;
    uint8_t *dataOut = data;
    uint8_t *dataIn = data;
    while(len>0){
        if(len>=fifo_size){
            for(int i=0;i<fifo_size;i++){
                LPC_SSP1->DR = *dataOut++;
            }
            len -= fifo_size;
            while(!(LPC_SSP1->RIS & 1<<2)); // Check if RX FIFO is at least half full
            for(int i=0;i<half_fifo_size;i++){
                *dataIn++ = LPC_SSP1->DR;
            }
            while(!(LPC_SSP1->SR & 1<<4));  // Wait until SSP is busy
            for(int i=0;i<fifo_size/2;i++){
                *dataIn++ = LPC_SSP1->DR;
            }
        }
        else if(len>=half_fifo_size){
            for(int i=0;i<half_fifo_size;i++){
                LPC_SSP1->DR = *dataOut++;
            }
            len -= half_fifo_size;
            while(!(LPC_SSP1->SR & 1<<4));  // Wait until SSP is busy
            for(int i=0;i<half_fifo_size;i++){
                *dataIn++ = LPC_SSP1->DR;
            }
        }
        else {
            LPC_SSP1->DR = *dataOut++;
            --len;
            while(LPC_SSP1->SR & 1<<4);
            *dataIn++ = LPC_SSP1->DR;
        }
    }
}

class FlashTask: public scheduler_task
{
    public:
        FlashTask(uint8_t priority) :
                scheduler_task("FlashTask", 2000, priority)
        {
            // Nothing to init
        }

        bool init(void){
            LPC_GPIO0->FIODIR |= 1<<flash_select_pin_no;

            LPC_SC->PCONP |= 1<<10;         //PCSSP1 enabled by default
            LPC_SC->PCLKSEL1 |= 1<<20;      //Peripheral clock select for SSP1 -> by default clock/4 would be there. Here we are setting as clock/1
            LPC_PINCON->PINSEL0 &= ~(0x3F<<14); //Set zero all bits of ssp1 pins
            LPC_PINCON->PINSEL0 |= (0x2A<<14);     //Select ssp1 pins
            LPC_PINCON->PINMODE0 &= ~(0xFF<<12); // Pull up all pins
            LPC_SSP1->IMSC |= (3<<2); //Enable transmit and receive interrupts
            LPC_SSP1->CR0 &= ~(0xFF<<0);
            LPC_SSP1->CR0 |= 7;  // 8 bit data transfer
            // FRF->SPI, CPOL=0, CPHA=0, SCR=0
            LPC_SSP1->CR1 &= ~(0xF<<0);
            //LBM=0 -> during normal mode, MS=0 ->Master mode, SOD=0
            LPC_SSP1->CR1 |= 1<<1; // SSP1 enable
            LPC_SSP1->CPSR |= 2;    //Clock divider is 2
            flash_select();
            flash_transfer_byte(0x9F);
            man_id = flash_transfer_byte(0x00);
            device_id = flash_transfer_byte(0x00);
            flash_deselect();
            //for(int i=0;i<10000;i++);
            //flash_change_page_528();
            for(int i=0;i<10000;i++);
            flash_page_read();
            //flash_read_sectors(tmp_data,0,0);
            printf("\n");
            for(int i=0;i<512;i++){
                printf("%d:%x ",i,tmp_data[i]);
            }
            printf("\n");
            return true;
        }

        bool run(void *p){
            printf("Number of sectors : %d\n",nsector);
            printf("Number of sectors per cluster: %d\n",nsector_cluster);
            printf("Number of bytes per sector : %d\n",nbytes_sector);
            printf("Manufacturer id: 0x%x\n",man_id);
            printf("Device id: 0x%x\n",device_id);
            flash_status_read();
            vTaskDelay(1000);
            return true;
        }
};

/**
 * The main() creates tasks or "threads".  See the documentation of scheduler_task class at scheduler_task.hpp
 * for details.  There is a very simple example towards the beginning of this class's declaration.
 *
 * @warning SPI #1 bus usage notes (interfaced to SD & Flash):
 *      - You can read/write files from multiple tasks because it automatically goes through SPI semaphore.
 *      - If you are going to use the SPI Bus in a FreeRTOS task, you need to use the API at L4_IO/fat/spi_sem.h
 *
 * @warning SPI #0 usage notes (Nordic wireless)
 *      - This bus is more tricky to use because if FreeRTOS is not running, the RIT interrupt may use the bus.
 *      - If FreeRTOS is running, then wireless task may use it.
 *        In either case, you should avoid using this bus or interfacing to external components because
 *        there is no semaphore configured for this bus and it should be used exclusively by nordic wireless.
 */
int main(void)
{
    /**
     * A few basic tasks for this bare-bone system :
     *      1.  Terminal task provides gateway to interact with the board through UART terminal.
     *      2.  Remote task allows you to use remote control to interact with the board.
     *      3.  Wireless task responsible to receive, retry, and handle mesh network.
     *
     * Disable remote task if you are not using it.  Also, it needs SYS_CFG_ENABLE_TLM
     * such that it can save remote control codes to non-volatile memory.  IR remote
     * control codes can be learned by typing the "learn" terminal command.
     */
#if 1
    scheduler_add_task(new terminalTask(PRIORITY_HIGH));
#endif
#if 0
    scheduler_add_task(new I2CSlaveTask(PRIORITY_HIGH));
#endif
    //scheduler_add_task(new LETask(PRIORITY_HIGH));
    //scheduler_add_task(new FlashTask(PRIORITY_HIGH));
    //scheduler_add_task(new UART_Task(PRIORITY_HIGH));

    /* Consumes very little CPU, but need highest priority to handle mesh network ACKs */
    //scheduler_add_task(new wirelessTask(PRIORITY_CRITICAL));

    /* Change "#if 0" to "#if 1" to run period tasks; @see period_callbacks.cpp */
    #if 0
    scheduler_add_task(new periodicSchedulerTask());
    #endif

    /* The task for the IR receiver */
    // scheduler_add_task(new remoteTask  (PRIORITY_LOW));

    /* Your tasks should probably used PRIORITY_MEDIUM or PRIORITY_LOW because you want the terminal
     * task to always be responsive so you can poke around in case something goes wrong.
     */

    /**
     * This is a the board demonstration task that can be used to test the board.
     * This also shows you how to send a wireless packets to other boards.
     */
    #if 0
        scheduler_add_task(new example_io_demo());
    #endif

    /**
     * Change "#if 0" to "#if 1" to enable examples.
     * Try these examples one at a time.
     */
    #if 0
        scheduler_add_task(new example_task());
        scheduler_add_task(new example_alarm());
        scheduler_add_task(new example_logger_qset());
        scheduler_add_task(new example_nv_vars());
    #endif

    /**
	 * Try the rx / tx tasks together to see how they queue data to each other.
	 */
    #if 0
        scheduler_add_task(new queue_tx());
        scheduler_add_task(new queue_rx());
    #endif

    /**
     * Another example of shared handles and producer/consumer using a queue.
     * In this example, producer will produce as fast as the consumer can consume.
     */
    #if 0
        scheduler_add_task(new producer());
        scheduler_add_task(new consumer());
    #endif

    /**
     * If you have RN-XV on your board, you can connect to Wifi using this task.
     * This does two things for us:
     *   1.  The task allows us to perform HTTP web requests (@see wifiTask)
     *   2.  Terminal task can accept commands from TCP/IP through Wifly module.
     *
     * To add terminal command channel, add this at terminal.cpp :: taskEntry() function:
     * @code
     *     // Assuming Wifly is on Uart3
     *     addCommandChannel(Uart3::getInstance(), false);
     * @endcode
     */
    #if 0
        Uart3 &u3 = Uart3::getInstance();
        u3.init(WIFI_BAUD_RATE, WIFI_RXQ_SIZE, WIFI_TXQ_SIZE);
        scheduler_add_task(new wifiTask(Uart3::getInstance(), PRIORITY_LOW));
    #endif

    scheduler_start(); ///< This shouldn't return
    return -1;
}
