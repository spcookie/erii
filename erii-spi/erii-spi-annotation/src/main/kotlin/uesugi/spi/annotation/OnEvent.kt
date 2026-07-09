package uesugi.spi.annotation

import uesugi.common.IntegrationEvent
import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OnEvent(val value: KClass<out IntegrationEvent>)
