package com.evops.aqua.maint.config;

import com.evops.aqua.maint.service.MaintSchedulerService;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean;

/**
 * 设备保养定时调度：每日扫描生效计划生成到期工单，并对逾期未开工工单自动升级。
 * 业务幂等由计划期数键、未关闭工单唯一与升级等级保证——重启重注册、补跑都不会多写/重复升级；
 * misfire 不补发风暴：错过即等下一周期。
 */
@Configuration
public class MaintQuartzConfig {

    @Bean
    public MethodInvokingJobDetailFactoryBean maintScanJobDetail(MaintSchedulerService service) {
        MethodInvokingJobDetailFactoryBean factory = new MethodInvokingJobDetailFactoryBean();
        factory.setTargetObject(service);
        factory.setTargetMethod("runScheduledScan");
        factory.setName("maintScanJob");
        factory.setGroup("aqua-maint");
        factory.setConcurrent(false);
        return factory;
    }

    @Bean
    public CronTriggerFactoryBean maintScanTrigger(
            @Qualifier("maintScanJobDetail") JobDetail maintScanJobDetail,
            @Value("${aqua.maint.scan-cron:0 30 2 * * ?}") String cron) {
        CronTriggerFactoryBean factory = new CronTriggerFactoryBean();
        factory.setJobDetail(maintScanJobDetail);
        factory.setName("maintScanTrigger");
        factory.setGroup("aqua-maint");
        factory.setCronExpression(cron);
        factory.setMisfireInstruction(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        return factory;
    }
}
