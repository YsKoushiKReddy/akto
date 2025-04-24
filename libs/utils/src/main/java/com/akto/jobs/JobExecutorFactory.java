package com.akto.jobs;

import com.akto.dto.jobs.JobParams;
import com.akto.dto.jobs.JobType;
import com.akto.jobs.executors.JiraTicketJobExecutor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class JobExecutorFactory {
    private static final Map<JobType, JobExecutor<? extends JobParams>> registry;

    static {
        Map<JobType, JobExecutor<? extends JobParams>> map = new HashMap<>();
        map.put(JobType.JIRA_AUTO_CREATE_TICKETS, JiraTicketJobExecutor.INSTANCE);

        registry = Collections.unmodifiableMap(map);
    }

    public static JobExecutor<? extends JobParams> getExecutor(JobType jobType) {
        return registry.get(jobType);
    }
}
