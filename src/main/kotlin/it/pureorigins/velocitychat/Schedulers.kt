package it.pureorigins.velocitychat

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import java.util.concurrent.TimeUnit

fun ProxyServer.scheduleAfter(time: Long, unit: TimeUnit, block: () -> Unit): ScheduledTask = scheduler.buildTask(this, block).delay(time, unit).schedule()