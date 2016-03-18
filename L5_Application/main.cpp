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

#define IMU                     0
static uint8_t recv_buffer;
QueueHandle_t receive_queue1;

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

    scheduler_add_task(new terminalTask(PRIORITY_HIGH));

    scheduler_add_task(new UART_Task(PRIORITY_HIGH));

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
