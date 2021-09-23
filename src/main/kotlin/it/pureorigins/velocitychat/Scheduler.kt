package it.pureorigins.velocitychat

import com.velocitypowered.api.scheduler.ScheduledTask
import java.util.concurrent.TimeUnit

fun VelocityChat.scheduleAfter(time: Long, unit: TimeUnit, block: () -> Unit): ScheduledTask =
    server.scheduler.buildTask(this, block).delay(time, unit).schedule()
