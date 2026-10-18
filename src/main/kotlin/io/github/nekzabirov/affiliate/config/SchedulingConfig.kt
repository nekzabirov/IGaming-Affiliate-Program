package io.github.nekzabirov.affiliate.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** Turns on the @Scheduled jobs; the jobs themselves live beside the services whose rows they clean. */
@Configuration
@EnableScheduling
class SchedulingConfig
