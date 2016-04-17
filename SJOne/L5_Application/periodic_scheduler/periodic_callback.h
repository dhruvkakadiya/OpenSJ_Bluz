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
 * @ingroup Utilities
 */
#ifndef PRD_CALLBACKS_H__
#define PRD_CALLBACKS_H__
#ifdef __cplusplus
extern "C" {
#endif



/// @{ @see period_callbacks.cpp for more info
extern const uint32_t PERIOD_TASKS_STACK_SIZE_BYTES;
extern const uint32_t PERIOD_DISPATCHER_TASK_STACK_SIZE_BYTES;
/// @}

bool period_init(void);
bool period_reg_tlm(void);

void period_1Hz(void);
void period_10Hz(void);
void period_100Hz(void);
void period_1000Hz(void);

#ifdef __cplusplus
}
#endif
#endif /* PRD_CALLBACKS_H__ */
