/*
 * COPYRIGHT (C) 2014 Volusion Inc. All Rights Reserved.
 */

package com.mozu.jobs.scheduler;

import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Properties;
import java.util.TimeZone;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
import org.quartz.CronScheduleBuilder;
import org.quartz.DateBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mozu.encryptor.EncryptionUtil;
import com.mozu.encryptor.PropertyEncryptionUtil;
import com.mozu.jobs.handlers.JobHandler;

/**
 * Class to start and manage interactions with the Qrtz job scheduler package.
 * The scheduler is started by invoking the initialize method. Scheduling is not
 * active until this method is invoked. This class should be treated as a
 * singleton and instantiated once and maintained by the project container
 * (generally Spring)
 * 
 * @author john_gatti
 *
 */
@Component
public class JobScheduler {
    private static final Logger logger = LoggerFactory.getLogger(JobScheduler.class);

    private static final int HOUR_OF_DAY = 23;

    private static final String TRIGGER = "_Trigger";
    private static final String JOB = "_Job";
    private static final String TENANT_ID_KEY = "tenantId";
    private static final String SITE_ID_KEY = "siteId";
    private static final String JOB_NAME = "jobName";
    private static final String QRTZ_TABLE_PREFIX = "QrtzJobs.QRTZ_";

    private static Scheduler scheduler = null;

    private static final String QRTZ_INSTANCE_NAME_PROP = "org.quartz.scheduler.instanceName";
    private static final String QRTZ_JOB_STORE_PROP = "org.quartz.jobStore.class";
    private static final String QRTZ_DRIVER_DELEGATE_PROP = "org.quartz.jobStore.driverDelegateClass";
    private static final String QRTZ_DATASOURCE_PROP = "org.quartz.jobStore.dataSource";
    private static final String QRTZ_THREAD_POOL_PROP = "org.quartz.threadPool.class";
    private static final String QRTZ_THREAD_COUNT_PROP = "org.quartz.threadPool.threadCount";
    private static final String QRTZ_DRIVER_PROP = "org.quartz.dataSource.mozu.driver";
    private static final String QRTZ_URL_PROP = "org.quartz.dataSource.mozu.URL";
    private static final String QRTZ_USER_PROP = "org.quartz.dataSource.mozu.user";
    private static final String QRTZ_PASSWORD_PROP = "org.quartz.dataSource.mozu.password";
    private static final String QRTZ_MAX_CONNECTIONS_PROP = "org.quartz.dataSource.mozu.maxConnections";
    private static final String QRTZ_INSTANCE_ID = "org.quartz.scheduler.instanceId";
    private static final String QRTZ_IS_CLUSTERED = "org.quartz.jobStore.isClustered";
    private static final String QRTZ_AQUIRE_TRIGGERS_IN_LOCK = "org.quartz.jobStore.acquireTriggersWithinLock";
    private static final String QRTZ_ISOLATION_SERIALIZABLE = "org.quartz.jobStore.txIsolationLevelSerializable";
    private static final String QRTZ_LOCK_CLASS = "org.quartz.jobStore.lockHandler.class";
    private static final String QRTZ_TABLE_PREFIX_PROP = "org.quartz.jobStore.tablePrefix";

    @Value("${org.quartz.scheduler.instanceName}")
    String qrtz_instanceName;

    @Value("${org.quartz.jobStore.class}")
    String qrtz_jobStore;

    @Value("${org.quartz.jobStore.driverDelegateClass}")
    String qrtz_driverDelegate;

    @Value("${org.quartz.jobStore.dataSource}")
    String qrtz_dataSource;

    @Value("${org.quartz.threadPool.class}")
    String qrtz_threadPool;

    @Value("${org.quartz.threadPool.threadCount}")
    String qrtz_threadCount;

    @Value("${org.quartz.dataSource.mozu.driver}")
    String qrtz_driver;

    @Value("${org.quartz.dataSource.mozu.URL}")
    String qrtz_URL;

    @Value("${org.quartz.dataSource.mozu.user}")
    String qrtz_user;

    @Value("${org.quartz.dataSource.mozu.password}")
    String qrtz_password;

    @Value("${org.quartz.dataSource.mozu.maxConnections}")
    String qrtz_maxConnections;

    @Value("${org.quartz.jobStore.lockHandler.class}")
    String qrtz_lockHandlerClass;
    
    @Value ("${spice: }")
    String spiceKey;

    @Autowired
    JobHandler jobHandler;

    public JobScheduler() {
    }

    /**
     * Start the Quartz scheduler.
     * 
     * The jobHandler class is called when the trigger fires. The class sets the
     * Spring Batch context for the job.
     */
    @PostConstruct
    public void initialize() {
        // create the properties
        Properties qrtzProperties = new Properties();
        qrtzProperties.put(QRTZ_INSTANCE_NAME_PROP, qrtz_instanceName);
        qrtzProperties.put(QRTZ_JOB_STORE_PROP, qrtz_jobStore);
        qrtzProperties.put(QRTZ_DRIVER_DELEGATE_PROP, qrtz_driverDelegate);
        qrtzProperties.put(QRTZ_DATASOURCE_PROP, qrtz_dataSource);
        qrtzProperties.put(QRTZ_THREAD_POOL_PROP, qrtz_threadPool);
        qrtzProperties.put(QRTZ_THREAD_COUNT_PROP, qrtz_threadCount);
        qrtzProperties.put(QRTZ_DRIVER_PROP, qrtz_driver);
        qrtzProperties.put(QRTZ_URL_PROP, qrtz_URL);
        qrtzProperties.put(QRTZ_USER_PROP, qrtz_user);
        qrtzProperties.put(QRTZ_PASSWORD_PROP, PropertyEncryptionUtil.decryptProperty(spiceKey, qrtz_password));
        qrtzProperties.put(QRTZ_MAX_CONNECTIONS_PROP, qrtz_maxConnections);
        qrtzProperties.put(QRTZ_LOCK_CLASS, qrtz_lockHandlerClass);

        // Force these values, all instances must have these set or it
        // may cause data corruption
        qrtzProperties.put(QRTZ_INSTANCE_ID, "Auto");
        qrtzProperties.put(QRTZ_IS_CLUSTERED, new Boolean(true));
        qrtzProperties.put(QRTZ_AQUIRE_TRIGGERS_IN_LOCK, new Boolean(true));
        qrtzProperties.put(QRTZ_ISOLATION_SERIALIZABLE, new Boolean(true));
        qrtzProperties.put(QRTZ_TABLE_PREFIX_PROP, QRTZ_TABLE_PREFIX);

        // start the scheduler
        StdSchedulerFactory schedulerFactory = new StdSchedulerFactory();
        try {
            schedulerFactory.initialize(qrtzProperties);
            scheduler = schedulerFactory.getScheduler();
            scheduler.getContext().put("jobHandler", jobHandler);
            scheduler.start();
        } catch (SchedulerException e) {
            logger.warn("Error starting Quartz scheduler: " + e.getMessage());
        }
        logger.debug("QrtzScheduler started");
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down scheduler");
        try {
            scheduler.shutdown();
        } catch (SchedulerException e) {
            logger.error("Exception shutting down scheduler: " + e.getMessage());
        }
    }

    /**
     * Add a job to be scheduled. Jobs are scheduled at the offset minutes past
     * the hour at the hour frequency specified. If the hour frequency is 24
     * hours, schedule once per day at 23:00 plus the minute offset.
     * 
     * @param tenantId
     *            Id of the tenant for this job. Used in the trigger and job
     *            keys
     * @param siteId
     *            Id of the site on this tenant (optional)
     * @param schedFrequencyHours
     *            repeat interval in hours.
     * @param offset
     *            Number of minutes after the our that the scedhule should be
     *            offset
     * @param identity
     *            A name used to identify the trigger group
     * @param jobClass
     *            Class to be invoked when the schedule fires. This class must
     *            implement org.qrtz.Job
     */
    public void addJob(Integer tenantId, Integer siteId, Integer schedFrequencyHours,
            Integer offset, String identity, Class jobClass) {

        if (scheduler == null)
            throw new IllegalStateException("Scheduler not initialized");

        if (tenantId == null || schedFrequencyHours == null || offset == null || identity == null
                || jobClass == null)
            throw new IllegalArgumentException();

        if (schedFrequencyHours <= 0 || schedFrequencyHours > 24) {
            String errMsg = "Atttempt to schedule sync at an invalid frequency ";
            logger.warn(errMsg);
            throw new IllegalArgumentException(errMsg);
        }

        String tenantIdString = tenantId.toString()
                + ((siteId != null) ? "_" + siteId.toString() : "");

        JobDetail job = JobBuilder.newJob(jobClass).withIdentity(identity + JOB, tenantIdString)
                .build();

        Trigger trigger = createTrigger(schedFrequencyHours, offset, tenantId, siteId, identity);

        try {
            scheduler.scheduleJob(job, trigger);
            logger.debug("Scheduled job for tenant " + tenantIdString);
        } catch (SchedulerException e) {
            logger.info("Qrtz job schedule job failure for tenant " + tenantIdString
                    + " Error is: " + e.getMessage());
        }
    }

    /**
     * Add a job to be scheduled. The job is scheduled at a repeatable frequency
     * in minutes with start time/offset immediately.
     * 
     * @param tenantId
     *            Id of the tenant for this job. Used in the trigger and job
     *            keys
     * @param siteId
     *            Id of the site on this tenant (optional)
     * @param schedFrequencyMinutes
     *            repeat interval in minutes.
     * @param identity
     *            A name used to identify the trigger group
     * @param jobClass
     *            Class to be invoked when the schedule fires. This class must
     *            implement org.qrtz.Job
     */
    public void addJob(Integer tenantId, Integer siteId, Integer schedFrequencyMinutes,
            String identity, Class jobClass) {
        addJob(tenantId, siteId, schedFrequencyMinutes, identity, jobClass, false);
    }    

    /**
     * Add a job to be scheduled. The job is scheduled at a repeatable frequency
     * in minutes with start time/offset immediately.
     * 
     * @param tenantId
     *            Id of the tenant for this job. Used in the trigger and job
     *            keys
     * @param siteId
     *            Id of the site on this tenant (optional)
     * @param schedFrequency
     *            repeat interval in minutes or seconds.
     * @param identity
     *            A name used to identify the trigger group
     * @param jobClass
     *            Class to be invoked when the schedule fires. This class must
     *            implement org.qrtz.Job
     * @param isFrequencyInSeconds whether the schedFrequency is in minutes or seconds           
     */    
    public void addJob(Integer tenantId, Integer siteId, Integer schedFrequency,
            String identity, Class jobClass, boolean isFrequencyInSeconds) {

        if (scheduler == null)
            throw new IllegalStateException("Scheduler not initialized");

        if (tenantId == null || schedFrequency == null || identity == null
                || jobClass == null)
            throw new IllegalArgumentException();

        if (schedFrequency <= 0) {
            String errMsg = "Atttempt to schedule sync at an invalid frequency. It must be a positive number ";
            logger.warn(errMsg);
            throw new IllegalArgumentException(errMsg);
        }

        String tenantIdString = tenantId.toString()
                + ((siteId != null) ? "_" + siteId.toString() : "");

        JobDetail job = JobBuilder.newJob(jobClass).withIdentity(identity + JOB, tenantIdString)
                .build();

        Trigger trigger = createTrigger(schedFrequency, tenantId, siteId, identity, isFrequencyInSeconds);

        try {
            scheduler.scheduleJob(job, trigger);
            logger.debug("Scheduled job for tenant " + tenantIdString);
        } catch (SchedulerException e) {
            logger.info("Qrtz job schedule job failure for tenant " + tenantIdString
                    + " Error is: " + e.getMessage());
        }
    }

    /**
     * Add a job to be scheduled. The job is scheduled at a repeatable frequency
     * in minutes with start time/offset immediately.
     * 
     * @param tenantId
     *            Id of the tenant for this job. Used in the trigger and job
     *            keys
     * @param siteId
     *            Id of the site on this tenant (optional)
     * @param schedFrequencySeconds
     *            repeat interval in minutes.
     * @param identity
     *            A name used to identify the trigger group
     * @param jobClass
     *            Class to be invoked when the schedule fires. This class must
     *            implement org.qrtz.Job
     */
    public void addJobFrequencyInSeconds(Integer tenantId, Integer siteId,
            Integer schedFrequencySeconds, String identity, Class jobClass) {
        addJob(tenantId, siteId, schedFrequencySeconds, identity, jobClass, true);
    }

    /**
     * Add a job to be scheduled. Jobs are scheduled at 15 minutes past the hour
     * at the hour frequency specified. If the hour frequency is 24 hours,
     * schedule once per day at 11:15 pm.
     * 
     * @param tenantId
     *            Id of the tenant for this job. Used in the trigger and job
     *            keys
     * @param siteId
     *            Id of the site on this tenant (optional)
     * @param hours
     *            hour of the day the job is to run
     * @param minutes
     *            minutes after the hour that the job will run
     * @param identity
     *            A name used to identify the trigger group
     * @param jobClass
     *            Class to be invoked when the schedule fires. This class must
     *            implement org.qrtz.Job
     */
    public void addDailyJob(Integer tenantId, Integer siteId, String hours, String minutes,
            String timezone, String identity, Class jobClass) {

        if (scheduler == null)
            throw new IllegalStateException("Scheduler not initialized");

        if (tenantId == null || hours == null || minutes == null || identity == null
                || jobClass == null)
            throw new IllegalArgumentException();

        String tenantIdString = tenantId.toString()
                + ((siteId != null) ? "_" + siteId.toString() : "");

        JobDetail job = JobBuilder.newJob(jobClass).withIdentity(identity + JOB, tenantIdString)
                .build();

        Trigger trigger = createCronTrigger(hours, minutes, timezone, tenantId, siteId, identity);

        try {
            scheduler.scheduleJob(job, trigger);
            logger.debug("Scheduled job for tenant " + tenantIdString);
        } catch (SchedulerException e) {
            logger.info("Qrtz job schedule job failure for tenant " + tenantIdString
                    + " Error is: " + e.getMessage());
        }
    }

    /**
     * Delete a previously scheduled sync job. If the job does not exist, log it
     * but do not throw an error
     * 
     * @param tenantId
     *            Id of the tenant for this job. Used in the trigger and job
     *            keys
     * @param siteId
     *            Id of the site on this tenant (optional)
     * @param identity
     *            A name used to identify the trigger group
     */
    public void delJob(Integer tenantId, Integer siteId, String identity) {
        String tenantIdString = tenantId.toString()
                + ((siteId != null) ? "_" + siteId.toString() : "");

        if (scheduler == null)
            throw new IllegalStateException("Scheduler not initialized");

        if (tenantId == null || identity == null)
            throw new IllegalArgumentException();

        JobKey jobKey = new JobKey(identity + JOB, tenantIdString);
        try {
            scheduler.deleteJob(jobKey);
            logger.debug("Deleted scheduled job for tenant " + tenantIdString);
        } catch (SchedulerException e) {
            logger.info("Qrtz job not deleted for tenant " + tenantIdString + " Error is: "
                    + e.getMessage());
        }
    }

    /**
     * Update an existing scheduled job's repeat interval.
     * 
     * Update consists of deleting the old schedule and adding it again with the
     * new trigger
     * 
     * @param tenantId
     *            Id of the tenant for this job. Used in the trigger and job
     *            keys
     * @param siteId
     *            Id of the site on this tenant
     * @param schedFrequencyMinutes
     *            repeat interval in minutes.
     * @param offset
     *            Number of minutes after the our that the scedhule should be
     *            offset
     * @param identity
     *            A name used to identify the trigger group
     * @param jobClass
     *            Class to be invoked when the schedule fires. This class must
     *            implement org.qrtz.Job
     */
    public void updateJob(Integer tenantId, Integer siteId, Integer schedFrequencyMinutes,
            String identity, Class jobClass) {
        updateJob(tenantId, siteId, schedFrequencyMinutes, identity, jobClass, false);
    }    

    /**
     * Update an existing scheduled job's repeat interval.
     * 
     * Update consists of deleting the old schedule and adding it again with the
     * new trigger
     * 
     * @param tenantId
     *            Id of the tenant for this job. Used in the trigger and job
     *            keys
     * @param siteId
     *            Id of the site on this tenant
     * @param schedFrequencySeconds
     *            repeat interval in seconds.
     * @param offset
     *            Number of minutes after the our that the scedhule should be
     *            offset
     * @param identity
     *            A name used to identify the trigger group
     * @param jobClass
     *            Class to be invoked when the schedule fires. This class must
     *            implement org.qrtz.Job
     */
    public void updateJobFrequencySeconds(Integer tenantId, Integer siteId, Integer schedFrequencySeconds,
            String identity, Class jobClass) {
        updateJob(tenantId, siteId, schedFrequencySeconds, identity, jobClass, true);
    }
    
    protected void updateJob (Integer tenantId, Integer siteId, Integer schedFrequency,
            String identity, Class jobClass, boolean isInSeconds) {
    
        if (scheduler == null)
            throw new IllegalStateException("Scheduler not initialized");

        if (tenantId == null || schedFrequency == null || identity == null
                || jobClass == null)
            throw new IllegalArgumentException();

        if (schedFrequency <= 0) {
            String errMsg = "Attempt to schedule sync at an invalid frequency ";
            logger.warn(errMsg);
            throw new IllegalArgumentException(errMsg);
        }

        String tenantIdString = tenantId.toString()
                + ((siteId != null) ? "_" + siteId.toString() : "");

        // read the old job trigger
        try {
            Trigger trigger = scheduler.getTrigger(new TriggerKey(identity + TRIGGER,
                    tenantIdString));
            if (trigger != null) {
                delJob(tenantId, siteId, identity);
            }
        } catch (SchedulerException e) {
            logger.info("Previous trigger not found for " + tenantId);
        }

        addJob(tenantId, siteId, schedFrequency, identity, jobClass, isInSeconds);
    }

    /**
     * Update an existing scheduled job's repeat interval and offset.
     * 
     * Update consists of deleting the old schedule and adding it again with the
     * new trigger
     * 
     * @param tenantId
     *            Id of the tenant for this job. Used in the trigger and job
     *            keys
     * @param siteId
     *            Id of the site on this tenant
     * @param schedFrequencyHours
     *            repeat interval in hours.
     * @param offset
     *            Number of minutes after the our that the scedhule should be
     *            offset
     * @param identity
     *            A name used to identify the trigger group
     * @param jobClass
     *            Class to be invoked when the schedule fires. This class must
     *            implement org.qrtz.Job
     */
    public void updateJob(Integer tenantId, Integer siteId, Integer schedFrequencyHours,
            Integer offset, String identity, Class jobClass) {

        if (scheduler == null)
            throw new IllegalStateException("Scheduler not initialized");

        if (tenantId == null || schedFrequencyHours == null || offset == null || identity == null
                || jobClass == null)
            throw new IllegalArgumentException();

        if (schedFrequencyHours <= 0 || schedFrequencyHours > 24) {
            String errMsg = "Atttempt to schedule sync at an invalid frequency ";
            logger.warn(errMsg);
            throw new IllegalArgumentException(errMsg);
        }

        String tenantIdString = tenantId.toString()
                + ((siteId != null) ? "_" + siteId.toString() : "");

        // read the old job trigger
        try {
            Trigger trigger = scheduler.getTrigger(new TriggerKey(identity + TRIGGER,
                    tenantIdString));
            if (trigger != null) {
                delJob(tenantId, siteId, identity);
            }
        } catch (SchedulerException e) {
            logger.info("Previous trigger not found for " + tenantId);
        }

        addJob(tenantId, siteId, schedFrequencyHours, offset, identity, jobClass);
    }

    /**
     * Update an existing scheduled job's repeat interval if the interval has
     * changed. The action defaults to not update. If there is not an existing
     * schedule, the app has not been enabled yet and therefore a trigger/job is
     * not defined.
     * 
     * Update consists of deleting the old schedule and adding it again with the
     * new trigger
     * 
     * @param tenantId
     *            Id of the tenant for this job. Used in the trigger and job
     *            keys
     * @param siteId
     *            Id of the site on this tenant (optional)
     * @param hours
     *            hour of the day the job is to run
     * @param minutes
     *            minutes after the hour that the job will run
     * @param identity
     *            A name used to identify the trigger group
     * @param jobClass
     *            Class to be invoked when the schedule fires. This class must
     */
    public void updateJob(Integer tenantId, Integer siteId, String hours, String minutes,
            String timezone, String identity, Class jobClass) {
        if (StringUtils.isBlank(hours) || StringUtils.isBlank(minutes)) {
            String errMsg = "Atttempt to update sync schedule to an invalid time";
            logger.warn(errMsg);
            throw new IllegalArgumentException(errMsg);
        }

        if (scheduler == null)
            throw new IllegalStateException("Scheduler not initialized");

        if (tenantId == null || hours == null || minutes == null || identity == null
                || jobClass == null)
            throw new IllegalArgumentException();

        String tenantIdString = tenantId.toString()
                + ((siteId != null) ? "_" + siteId.toString() : "");

        // read the old job trigger
        try {
            Trigger trigger = scheduler.getTrigger(new TriggerKey(identity + TRIGGER,
                    tenantIdString));
            if (trigger != null) {
                delJob(tenantId, siteId, identity);
            }
        } catch (SchedulerException e) {
            logger.info("Previous trigger not found for " + tenantId);
        }

        addDailyJob(tenantId, siteId, hours, minutes, timezone, identity, jobClass);
    }

    /**
     * Get the named Qrtz trigger
     * 
     * @param tenantId
     *            Id of the tenant for this job. Used in the trigger and job
     *            keys
     * @param siteId
     *            Id of the site on this tenant (optional)
     * @param identity
     *            A name used to identify the trigger group
     * @return Qrtz trigger
     * @throws SchedulerException
     */
    public Trigger getTrigger(Integer tenantId, Integer siteId, String identity)
            throws SchedulerException {
        String tenantIdString = tenantId.toString()
                + ((siteId != null) ? "_" + siteId.toString() : "");

        if (scheduler == null) {
            throw new IllegalStateException("Scheduler not initialized");
        }

        return scheduler.getTrigger(new TriggerKey(identity + TRIGGER, tenantIdString));
    }

    /**
     * Get the named Qrtz job
     * 
     * @param tenantId
     *            Id of the tenant for this job. Used in the trigger and job
     *            keys
     * @param siteId
     *            Id of the site on this tenant (optional)
     * @param identity
     *            A name used to identify the trigger group
     * @return Qrtz JobDetail
     * @throws SchedulerException
     */
    public JobDetail getJob(Integer tenantId, Integer siteId, String identity)
            throws SchedulerException {
        String tenantIdString = tenantId.toString()
                + ((siteId != null) ? "_" + siteId.toString() : "");

        if (scheduler == null) {
            throw new IllegalStateException("Scheduler not initialized");
        }

        return scheduler.getJobDetail(new JobKey(identity + JOB, tenantIdString));
    }

    private Trigger createTrigger(Integer schedFrequencyHours, Integer offset, Integer tenantId,
            Integer siteId, String identity) {
        String tenantIdString = tenantId.toString()
                + ((siteId != null) ? "_" + siteId.toString() : "");

        Trigger trigger = null;
        if (schedFrequencyHours.intValue() == 24) {
            // use a Cron scheduler so it runs daily at 23:15 and is
            // not thrown by DST changes.
            return createCronTrigger(new Integer(HOUR_OF_DAY).toString(),
                    new Integer(offset).toString(), Calendar.getInstance().getTimeZone().getID(),
                    tenantId, siteId, identity);
        } else {
            // use a simple schedule at 15 minutes past the hour
            trigger = TriggerBuilder.newTrigger().withIdentity(identity + TRIGGER, tenantIdString)
                    .withSchedule(SimpleScheduleBuilder.repeatHourlyForever(schedFrequencyHours))
                    .startAt(DateBuilder.newDate().atMinute(offset).build())
                    .usingJobData(TENANT_ID_KEY, tenantId).usingJobData(SITE_ID_KEY, siteId)
                    .usingJobData(JOB_NAME, identity).build();
        }
        return trigger;
    }

    private Trigger createTrigger(Integer schedFrequency, Integer tenantId, Integer siteId,
            String identity, boolean frequencyInSeconds) {
        String tenantIdString = tenantId.toString()
                + ((siteId != null) ? "_" + siteId.toString() : "");

        Trigger trigger = null;
        TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger()
                .withIdentity(identity + TRIGGER, tenantIdString)
                .startAt(DateBuilder.newDate().build()).usingJobData(TENANT_ID_KEY, tenantId)
                .usingJobData(SITE_ID_KEY, siteId).usingJobData(JOB_NAME, identity);
        if (frequencyInSeconds) {
            triggerBuilder.withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(schedFrequency));
        } else {
            triggerBuilder.withSchedule(SimpleScheduleBuilder.repeatMinutelyForever(schedFrequency));
        }

        trigger = triggerBuilder.build();
        return trigger;
    }

    private Trigger createCronTrigger(String hours, String minutes, String timezone,
            Integer tenantId, Integer siteId, String identity) {
        String tenantIdString = tenantId.toString()
                + ((siteId != null) ? "_" + siteId.toString() : "");

        TimeZone tz = TimeZone.getTimeZone(timezone);
        
        int randomNumber=getRandomNumber();
        int updatedMinutes=Integer.parseInt(minutes )+randomNumber / 60;
        int seconds=randomNumber % 60;
        
        // Create a cron trigger
        Trigger trigger = TriggerBuilder
                .newTrigger()
                .withIdentity(identity + TRIGGER, tenantIdString)
                .withSchedule(
                        CronScheduleBuilder.cronSchedule( seconds+ " " + updatedMinutes + " " + hours + " * * ?")
                                .inTimeZone(tz)).usingJobData(TENANT_ID_KEY, tenantId)
                .usingJobData(SITE_ID_KEY, siteId).usingJobData(JOB_NAME, identity).build();
        return trigger;
    }
    
    private int getRandomNumber(){
    	SecureRandom sc= new SecureRandom();
        return sc.nextInt(300);
  }
}
